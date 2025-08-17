package org.llm4s.trace

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import scala.concurrent.duration._
import java.time.Instant

class LangfuseIntegrationTest extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  // Test configuration with mock Langfuse settings
  val testLangfuseConfig = LangfuseConfig(
    publicKey = "test-public-key",
    secretKey = "test-secret-key",
    host = "https://test.langfuse.com"
  )

  val testTraceConfig = TraceManagerConfig(
    enabled = true,
    batchSize = 5,
    flushInterval = 1.second,
    maxRetries = 2,
    circuitBreakerThreshold = 3
  )

  "LangfuseConfig" should "validate required fields" in {
    val validConfig = LangfuseConfig(
      publicKey = "pk-test",
      secretKey = "sk-test",
      host = "https://test.langfuse.com"
    )

    validConfig.isValid shouldBe true
    validConfig.publicKey shouldBe "pk-test"
    validConfig.secretKey shouldBe "sk-test"
    validConfig.host shouldBe "https://test.langfuse.com"
  }

  it should "detect invalid configurations" in {
    val invalidConfigs = Seq(
      LangfuseConfig("", "sk-test", "https://test.com"), // empty public key
      LangfuseConfig("pk-test", "", "https://test.com"), // empty secret key
      LangfuseConfig("pk-test", "sk-test", ""),          // empty host
      LangfuseConfig("", "", "")                         // all empty
    )

    invalidConfigs.foreach(_.isValid shouldBe false)
  }

  it should "have sensible defaults" in {
    val defaultConfig = LangfuseConfig()

    // Should use environment variables or defaults
    defaultConfig.host shouldBe "https://cloud.langfuse.com"
  }

  "LangfuseTraceManager" should "initialize with valid configuration" in {
    val traceManager = new LangfuseTraceManager(testTraceConfig, testLangfuseConfig)

    traceManager should not be null
    traceManager.shutdown()
  }

  it should "create Langfuse-specific traces" in {
    val traceManager = new LangfuseTraceManager(testTraceConfig, testLangfuseConfig)

    val trace = traceManager.createTrace(
      name = "langfuse-test-trace",
      userId = Some("test-user-123"),
      sessionId = Some("test-session-456"),
      metadata = Map(
        "environment" -> "test",
        "version"     -> "1.0.0"
      )
    )

    trace shouldBe a[LangfuseTrace]
    trace.finish()
    traceManager.shutdown()
  }

  it should "handle LLM generation tracking" in {
    val traceManager = new LangfuseTraceManager(testTraceConfig, testLangfuseConfig)

    val result = traceManager.withTrace("llm-generation-test") { trace =>
      trace.addMetadata("model", "gpt-4")
      trace.addMetadata("temperature", 0.7)

      // Simulate LLM generation span
      trace.span("llm-completion") { span =>
        if (span.isInstanceOf[LangfuseSpan]) {
          val langfuseSpan = span.asInstanceOf[LangfuseSpan]

          langfuseSpan.recordGeneration(
            name = "Test LLM Generation",
            model = "gpt-4",
            startTime = Instant.now().minusSeconds(2),
            endTime = Some(Instant.now()),
            input = Some("Test prompt"),
            output = Some("Test response"),
            usage = Some(TokenUsage(promptTokens = 10, completionTokens = 20, totalTokens = 30))
          )
        }

        "llm-generation-recorded"
      }
    }

    result shouldBe "llm-generation-recorded"
    traceManager.shutdown()
  }

  it should "handle tool call tracking" in {
    val traceManager = new LangfuseTraceManager(testTraceConfig, testLangfuseConfig)

    val result = traceManager.withTrace("tool-call-test") { trace =>
      trace.addMetadata("operation", "tool_execution")

      trace.span("tool-execution") { span =>
        if (span.isInstanceOf[LangfuseSpan]) {
          val langfuseSpan = span.asInstanceOf[LangfuseSpan]

          langfuseSpan.recordToolCall(
            name = "Weather Tool",
            toolName = "get_weather",
            startTime = Instant.now().minusSeconds(1),
            endTime = Some(Instant.now()),
            input = Some("""{"location": "San Francisco", "unit": "celsius"}"""),
            output = Some("""{"temperature": 18, "condition": "sunny"}""")
          )
        }

        "tool-call-recorded"
      }
    }

    result shouldBe "tool-call-recorded"
    traceManager.shutdown()
  }

  it should "handle hierarchical trace structure" in {
    val traceManager = new LangfuseTraceManager(testTraceConfig, testLangfuseConfig)

    val result = traceManager.withTrace("hierarchical-test") { trace =>
      trace.setInput("User query: What's the weather?")

      trace.span("query-processing") { processingSpan =>
        processingSpan.addMetadata("step", "processing")

        processingSpan.span("intent-recognition") { intentSpan =>
          intentSpan.addMetadata("intent", "weather_query")
          intentSpan.addMetadata("confidence", 0.95)
        }

        processingSpan.span("tool-selection") { toolSpan =>
          toolSpan.addMetadata("selected_tool", "weather_api")
          toolSpan.addMetadata("reasoning", "weather query detected")
        }
      }

      trace.span("response-generation") { responseSpan =>
        responseSpan.addMetadata("step", "generation")
        responseSpan.setOutput("The weather in San Francisco is 18°C and sunny.")
      }

      trace.setOutput("The weather in San Francisco is 18°C and sunny.")
      "hierarchical-trace-completed"
    }

    result shouldBe "hierarchical-trace-completed"
    traceManager.shutdown()
  }

  it should "handle batch processing with Langfuse events" in {
    val traceManager = new LangfuseTraceManager(testTraceConfig, testLangfuseConfig)

    // Create multiple traces to test batching
    val results = (1 to 8).map { i =>
      traceManager.withTrace(s"batch-trace-$i") { trace =>
        trace.addMetadata("batch_number", i)
        trace.addMetadata("test_type", "batch_processing")

        trace.span(s"batch-operation-$i") { span =>
          span.addMetadata("operation_id", i)
          span.recordEvent(
            "batch_event",
            Map(
              "batch_id"  -> i,
              "timestamp" -> Instant.now(),
              "data"      -> s"batch-data-$i"
            )
          )

          s"batch-result-$i"
        }
      }
    }

    (results should have).length(8)
    results.head shouldBe "batch-result-1"
    results.last shouldBe "batch-result-8"

    traceManager.shutdown()
  }

  it should "handle error scenarios with circuit breaker" in {
    val errorProneConfig = testTraceConfig.copy(
      circuitBreakerThreshold = 2, // Low threshold for testing
      maxRetries = 1
    )

    val traceManager = new LangfuseTraceManager(errorProneConfig, testLangfuseConfig)

    // This test simulates error conditions that would trigger circuit breaker
    val result = traceManager.withTrace("circuit-breaker-test") { trace =>
      trace.addMetadata("test_type", "circuit_breaker")

      trace.span("potentially-failing-span") { span =>
        span.addMetadata("error_simulation", true)

        // Record error to test error handling
        span.recordError(new RuntimeException("Simulated Langfuse API error"))
        span.setStatus(SpanStatus.Error)

        "circuit-breaker-handled"
      }
    }

    result shouldBe "circuit-breaker-handled"
    traceManager.shutdown()
  }

  it should "handle concurrent Langfuse operations" in {
    val traceManager = new LangfuseTraceManager(testTraceConfig, testLangfuseConfig)

    val futures = (1 to 3).map { threadId =>
      scala.concurrent.Future {
        traceManager.withTrace(s"concurrent-langfuse-$threadId") { trace =>
          trace.addMetadata("thread_id", threadId)
          trace.addMetadata("concurrent_test", true)

          (1 to 5).map { i =>
            trace.span(s"concurrent-span-$threadId-$i") { span =>
              span.addMetadata("span_index", i)

              if (span.isInstanceOf[LangfuseSpan]) {
                val langfuseSpan = span.asInstanceOf[LangfuseSpan]
                langfuseSpan.recordEvent(
                  name = "concurrent_event",
                  attributes = Map(
                    "thread_id" -> threadId,
                    "event_id"  -> i,
                    "timestamp" -> Instant.now()
                  )
                )
              }

              Thread.sleep(10) // Brief delay
              s"concurrent-$threadId-$i"
            }
          }
        }
      }(scala.concurrent.ExecutionContext.global)
    }

    implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
    val results = scala.concurrent.Await.result(
      scala.concurrent.Future.sequence(futures),
      10.seconds
    )

    (results should have).length(3)
    results.foreach(threadResult => (threadResult should have).length(5))

    traceManager.shutdown()
  }

  it should "handle graceful shutdown with pending Langfuse events" in {
    val traceManager = new LangfuseTraceManager(testTraceConfig, testLangfuseConfig)

    // Create some traces with events
    (1 to 3).foreach { i =>
      traceManager.withTrace(s"shutdown-test-$i") { trace =>
        trace.addMetadata("shutdown_test", true)

        trace.span(s"shutdown-span-$i") { span =>
          span.recordEvent("shutdown_event", Map("event_id" -> i))
          span.addMetadata("will_be_flushed_on_shutdown", true)
        }
      }
    }

    // Shutdown should complete gracefully and flush pending events
    noException should be thrownBy traceManager.shutdown()
  }

  it should "handle configuration edge cases" in {
    // Test with minimal configuration
    val minimalConfig = TraceManagerConfig(
      enabled = true,
      batchSize = 1,
      flushInterval = 100.millis
    )

    val traceManager = new LangfuseTraceManager(minimalConfig, testLangfuseConfig)

    traceManager.withTrace("minimal-config-test") { trace =>
      trace.addMetadata("config_type", "minimal")
      trace.span("minimal-span") { span =>
        span.addMetadata("test", "minimal_configuration")
        "minimal-test-completed"
      }
    }

    traceManager.shutdown()
  }
}
