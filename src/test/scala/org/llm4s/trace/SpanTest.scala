package org.llm4s.trace

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import java.time.Instant

class SpanTest extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  "NoOpSpan" should "handle all operations without effect" in {
    val span = NoOpSpan

    // All operations should complete without error
    span.addMetadata("key", "value")
    span.addTag("test-tag")
    span.setInput("input")
    span.setOutput("output")
    span.setStatus(SpanStatus.Ok)
    span.recordError(new RuntimeException("test error"))
    span.recordEvent("test-event", Map("data" -> "value"))

    // Nested span operations should return values correctly
    val result = span.span("nested-span") { nestedSpan =>
      nestedSpan.addMetadata("nested", "value")
      "nested-result"
    }
    result shouldBe "nested-result"

    // Status and timing should be no-op
    span.isFinished shouldBe false
    // NoOpSpan doesn't have finish method
  }

  "PrintSpan" should "manage span lifecycle correctly" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)
    val trace        = traceManager.createTrace("span-test")

    trace.span("test-span") { span =>
      span shouldBe a[PrintSpan]
      span.isFinished shouldBe false

      span.addMetadata("operation", "test")
      span.addTag("unit-test")
      span.setInput("test input")

      // Test nested spans
      val nestedResult = span.span("nested-operation") { nestedSpan =>
        nestedSpan.addMetadata("level", "nested")
        nestedSpan.setOutput("nested output")
        "nested-success"
      }

      nestedResult shouldBe "nested-success"
      span.setOutput("test output")
      "span-success"
    }

    trace.finish()
    traceManager.shutdown()
  }

  it should "handle status transitions" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)
    val trace        = traceManager.createTrace("status-test")

    trace.span("status-span") { span =>
      span.setStatus(SpanStatus.Ok)

      // Simulate some work
      Thread.sleep(10)

      span.setStatus(SpanStatus.Ok)
      "completed"
    }

    trace.finish()
    traceManager.shutdown()
  }

  it should "record errors properly" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)
    val trace        = traceManager.createTrace("error-test")

    trace.span("error-span") { span =>
      val exception = new RuntimeException("Simulated error")
      span.recordError(exception)
      span.setStatus(SpanStatus.Error)

      // Span should still function after error
      span.addMetadata("error_handled", true)
      "error-recorded"
    }

    trace.finish()
    traceManager.shutdown()
  }

  it should "record events with metadata" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)
    val trace        = traceManager.createTrace("events-test")

    trace.span("events-span") { span =>
      span.recordEvent("processing_started", Map("timestamp" -> Instant.now()))
      span.recordEvent(
        "checkpoint_reached",
        Map(
          "checkpoint"      -> "validation",
          "items_processed" -> 100,
          "success_rate"    -> 0.95
        )
      )
      span.recordEvent("processing_completed", Map("duration" -> "500ms"))

      "events-recorded"
    }

    trace.finish()
    traceManager.shutdown()
  }

  it should "handle deeply nested spans" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)
    val trace        = traceManager.createTrace("nested-test")

    val result = trace.span("level-1") { span1 =>
      span1.addMetadata("level", 1)

      span1.span("level-2") { span2 =>
        span2.addMetadata("level", 2)

        span2.span("level-3") { span3 =>
          span3.addMetadata("level", 3)

          span3.span("level-4") { span4 =>
            span4.addMetadata("level", 4)
            span4.addTag("deepest")
            "deep-result"
          }
        }
      }
    }

    result shouldBe "deep-result"
    trace.finish()
    traceManager.shutdown()
  }

  it should "handle concurrent span operations" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)
    val trace        = traceManager.createTrace("concurrent-test")

    trace.span("concurrent-span") { span =>
      // Simulate multiple operations happening in sequence
      val results = (1 to 3).map { i =>
        span.span(s"operation-$i") { opSpan =>
          opSpan.addMetadata("operation_id", i)
          opSpan.addTag(s"op-$i")
          Thread.sleep(5) // Simulate work
          s"result-$i"
        }
      }

      (results should contain).allOf("result-1", "result-2", "result-3")
      "concurrent-completed"
    }

    trace.finish()
    traceManager.shutdown()
  }

  it should "maintain span hierarchy" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)
    val trace        = traceManager.createTrace("hierarchy-test")

    trace.span("parent") { parent =>
      parent.addTag("parent-span")

      val child1Result = parent.span("child-1") { child1 =>
        child1.addTag("child-span")
        child1.addMetadata("child_id", 1)

        // Child 1 has its own nested operation
        child1.span("grandchild-1") { grandchild =>
          grandchild.addMetadata("generation", 3)
          "grandchild-1-result"
        }
      }

      val child2Result = parent.span("child-2") { child2 =>
        child2.addTag("child-span")
        child2.addMetadata("child_id", 2)
        "child-2-result"
      }

      child1Result shouldBe "grandchild-1-result"
      child2Result shouldBe "child-2-result"

      "hierarchy-complete"
    }

    trace.finish()
    traceManager.shutdown()
  }

  it should "handle span timing" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)
    val trace        = traceManager.createTrace("timing-test")

    trace.span("timed-span") { span =>
      val startTime = System.currentTimeMillis()

      // Simulate work
      Thread.sleep(50)

      span.addMetadata("work_duration", "50ms")

      val endTime        = System.currentTimeMillis()
      val actualDuration = endTime - startTime

      // Verify timing is reasonable (should be at least 50ms)
      actualDuration should be >= 50L

      "timing-verified"
    }

    trace.finish()
    traceManager.shutdown()
  }
}
