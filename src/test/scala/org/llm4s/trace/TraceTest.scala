package org.llm4s.trace

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class TraceTest extends AnyFlatSpec with Matchers with BeforeAndAfterEach {
  implicit val ec: ExecutionContext = ExecutionContext.global

  "NoOpTrace" should "handle all operations without effect" in {
    val trace = NoOpTrace

    // All operations should complete without error
    trace.addMetadata("key", "value")
    trace.addTag("test-tag")
    trace.setInput("input")
    trace.setOutput("output")
    trace.recordError(new RuntimeException("test error"))

    // Span operations should return values correctly
    val result = trace.span("test-span") { span =>
      span.addMetadata("nested", "value")
      "span-result"
    }
    result shouldBe "span-result"

    // Context operations
    trace.currentSpan shouldBe None

    // Finish should complete without error
    trace.finish()
  }

  "PrintTrace" should "manage metadata correctly" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)
    val trace        = traceManager.createTrace("test-trace", Some("user123"))

    trace.addMetadata("operation", "test")
    trace.addMetadata("version", "1.0")
    trace.addTag("integration-test")

    trace.setInput("test input")
    trace.setOutput("test output")

    trace.finish()
    traceManager.shutdown()
  }

  it should "handle span lifecycle" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)
    val trace        = traceManager.createTrace("span-lifecycle-trace")

    val result = trace.span("parent-span") { parentSpan =>
      parentSpan.addMetadata("level", "parent")

      val nestedResult = parentSpan.span("child-span") { childSpan =>
        childSpan.addMetadata("level", "child")
        childSpan.addTag("nested")
        "child-result"
      }

      nestedResult shouldBe "child-result"
      "parent-result"
    }

    result shouldBe "parent-result"
    trace.finish()
    traceManager.shutdown()
  }

  it should "track current span context" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = TracingFactory.createTraceManager(TracingFactory.PRINT_MODE, config)
    val trace        = traceManager.createTrace("context-trace")

    trace.currentSpan shouldBe None

    trace.span("outer-span") { outerSpan =>
      trace.currentSpan shouldBe Some(outerSpan)

      outerSpan.span("inner-span") { innerSpan =>
        trace.currentSpan shouldBe Some(innerSpan)
        innerSpan.addMetadata("depth", "inner")
      }

      trace.currentSpan shouldBe Some(outerSpan)
      outerSpan.addMetadata("depth", "outer")
    }

    trace.currentSpan shouldBe None
    trace.finish()
    traceManager.shutdown()
  }

  it should "handle errors gracefully" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)
    val trace        = traceManager.createTrace("error-trace")

    val exception = new RuntimeException("test error")
    trace.recordError(exception)

    trace.span("error-span") { span =>
      span.recordError(exception)
      span.setStatus(SpanStatus.Error)
      "completed-with-error"
    }

    trace.finish()
    traceManager.shutdown()
  }

  it should "support trace input/output" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)
    val trace        = traceManager.createTrace("io-trace")

    trace.setInput("User request: Hello world")

    trace.span("processing") { span =>
      span.addMetadata("step", "processing")
      span.setInput("Hello world")
      span.setOutput("Processed: Hello world")
    }

    trace.setOutput("Bot response: Processed: Hello world")
    trace.finish()
    traceManager.shutdown()
  }

  it should "handle multiple tags" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)
    val trace        = traceManager.createTrace("tags-trace")

    trace.addTag("llm-call")
    trace.addTag("production")
    trace.addTag("user-facing")

    trace.span("tagged-span") { span =>
      span.addTag("tool-call")
      span.addTag("weather-api")
      "tagged-result"
    }

    trace.finish()
    traceManager.shutdown()
  }

  it should "support complex metadata types" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)
    val trace        = traceManager.createTrace("metadata-trace")

    trace.addMetadata("string", "value")
    trace.addMetadata("number", 42)
    trace.addMetadata("boolean", true)
    trace.addMetadata("map", Map("key1" -> "value1", "key2" -> 123))
    trace.addMetadata("list", List("item1", "item2", "item3"))

    trace.span("complex-metadata") { span =>
      span.addMetadata("timestamp", Instant.now())
      span.addMetadata("duration", 1.5.seconds)
      span.addMetadata(
        "config",
        Map(
          "model"       -> "gpt-4",
          "temperature" -> 0.7,
          "maxTokens"   -> 1000
        )
      )
    }

    trace.finish()
    traceManager.shutdown()
  }
}
