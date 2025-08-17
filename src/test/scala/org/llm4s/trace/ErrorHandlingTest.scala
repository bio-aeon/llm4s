package org.llm4s.trace

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

class ErrorHandlingTest extends AnyFlatSpec with Matchers with BeforeAndAfterEach {
  implicit val ec: ExecutionContext = ExecutionContext.global

  "Error Handling" should "record exceptions in spans" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)

    val result = traceManager.withTrace("error-recording-test") { trace =>
      trace.span("error-span") { span =>
        val exception = new RuntimeException("Test error message")

        span.recordError(exception)
        span.setStatus(SpanStatus.Error)
        span.addMetadata("error_recorded", true)

        // Span should continue functioning after error
        span.addMetadata("post_error_metadata", "added")

        "error-recorded-successfully"
      }
    }

    result shouldBe "error-recorded-successfully"
    traceManager.shutdown()
  }

  it should "handle errors in nested spans" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)

    val result = traceManager.withTrace("nested-error-test") { trace =>
      trace.span("parent-span") { parentSpan =>
        parentSpan.addMetadata("parent_status", "running")

        try
          parentSpan.span("failing-child-span") { childSpan =>
            childSpan.addMetadata("child_status", "about_to_fail")

            val error = new IllegalArgumentException("Child span error")
            childSpan.recordError(error)
            childSpan.setStatus(SpanStatus.Error)

            throw error
          }
        catch {
          case e: IllegalArgumentException =>
            parentSpan.addMetadata("child_error_handled", true)
            parentSpan.addMetadata("error_message", e.getMessage)
        }

        parentSpan.setStatus(SpanStatus.Ok)
        "nested-error-handled"
      }
    }

    result shouldBe "nested-error-handled"
    traceManager.shutdown()
  }

  it should "handle multiple errors in the same span" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)

    traceManager.withTrace("multiple-errors-test") { trace =>
      trace.span("multi-error-span") { span =>
        // Record multiple errors
        span.recordError(new RuntimeException("First error"))
        span.recordError(new IllegalStateException("Second error"))
        span.recordError(new IllegalArgumentException("Third error"))

        span.setStatus(SpanStatus.Error)
        span.addMetadata("error_count", 3)

        "multiple-errors-recorded"
      }
    }

    traceManager.shutdown()
  }

  it should "handle errors in async operations" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)

    val futureResult = traceManager.withTraceAsync("async-error-test") { trace =>
      trace.span("async-error-span") { span =>
        Future {
          Thread.sleep(50)

          try
            throw new RuntimeException("Async operation failed")
          catch {
            case e: RuntimeException =>
              span.recordError(e)
              span.setStatus(SpanStatus.Error)
              span.addMetadata("async_error_handled", true)
              "async-error-handled"
          }
        }
      }
    }

    val result = scala.concurrent.Await.result(futureResult, 5.seconds)
    result shouldBe "async-error-handled"

    traceManager.shutdown()
  }

  it should "maintain span status consistency" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)

    traceManager.withTrace("status-consistency-test") { trace =>
      trace.span("status-span") { span =>
        // Start with ok status
        span.setStatus(SpanStatus.Ok)

        // Change to ok (demonstrating status changes)
        span.setStatus(SpanStatus.Ok)
        span.addMetadata("status_change", "running")

        // Simulate error condition
        span.recordError(new RuntimeException("Status change error"))
        span.setStatus(SpanStatus.Error)
        span.addMetadata("final_status", "error")

        "status-changes-recorded"
      }
    }

    traceManager.shutdown()
  }

  it should "handle trace-level errors" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)

    val result = traceManager.withTrace("trace-error-test") { trace =>
      trace.addMetadata("trace_status", "running")

      // Record error at trace level
      val traceError = new RuntimeException("Trace-level error")
      trace.recordError(traceError)
      trace.addMetadata("trace_error_recorded", true)

      trace.span("post-error-span") { span =>
        span.addMetadata("after_trace_error", true)
        "trace-error-handled"
      }
    }

    result shouldBe "trace-error-handled"
    traceManager.shutdown()
  }

  it should "handle errors during span creation" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)

    val result = traceManager.withTrace("span-creation-error-test") { trace =>
      try
        trace.span("problematic-span") { _ =>
          // Simulate immediate error during span setup
          throw new IllegalStateException("Span initialization failed")
        }
      catch {
        case e: IllegalStateException =>
          trace.recordError(e)
          trace.addMetadata("span_creation_error_handled", true)
          "span-creation-error-handled"
      }
    }

    result shouldBe "span-creation-error-handled"
    traceManager.shutdown()
  }

  it should "handle resource cleanup errors" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)

    // Test that errors during cleanup don't break the system
    val result = traceManager.withTrace("cleanup-error-test") { trace =>
      trace.span("resource-span") { span =>
        span.addMetadata("resource", "database_connection")

        // Simulate work
        Thread.sleep(10)

        // Simulate cleanup error (recorded but doesn't fail the operation)
        span.recordError(new RuntimeException("Cleanup failed"))
        span.addMetadata("cleanup_attempted", true)

        "resource-work-completed"
      }
    }

    result shouldBe "resource-work-completed"
    traceManager.shutdown()
  }

  it should "handle cascading errors across spans" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)

    val result = traceManager.withTrace("cascading-error-test") { trace =>
      trace.span("level-1") { span1 =>
        span1.addMetadata("level", 1)

        try
          span1.span("level-2") { span2 =>
            span2.addMetadata("level", 2)

            try
              span2.span("level-3") { span3 =>
                span3.addMetadata("level", 3)

                // Deepest level fails
                val deepError = new RuntimeException("Deep failure")
                span3.recordError(deepError)
                span3.setStatus(SpanStatus.Error)
                throw deepError
              }
            catch {
              case e: RuntimeException =>
                // Level 2 handles and re-throws
                span2.recordError(e)
                span2.setStatus(SpanStatus.Error)
                span2.addMetadata("propagated_error", true)
                throw new RuntimeException("Level 2 cascade", e)
            }
          }
        catch {
          case e: RuntimeException =>
            // Level 1 handles final error
            span1.recordError(e)
            span1.setStatus(SpanStatus.Error)
            span1.addMetadata("final_error_handler", true)
            "cascading-errors-handled"
        }
      }
    }

    result shouldBe "cascading-errors-handled"
    traceManager.shutdown()
  }

  it should "handle concurrent error scenarios" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)

    val futures = (1 to 3).map { i =>
      traceManager.withTraceAsync(s"concurrent-error-$i") { trace =>
        Future {
          trace.span(s"error-span-$i") { span =>
            Thread.sleep(10 * i) // Different timings

            if (i % 2 == 0) {
              // Even numbered traces fail
              val error = new RuntimeException(s"Concurrent error $i")
              span.recordError(error)
              span.setStatus(SpanStatus.Error)
              s"error-$i"
            } else {
              // Odd numbered traces succeed
              span.setStatus(SpanStatus.Ok)
              s"success-$i"
            }
          }
        }
      }
    }

    val results = scala.concurrent.Await.result(
      Future.sequence(futures),
      5.seconds
    )

    (results should contain).allOf("success-1", "error-2", "success-3")
    traceManager.shutdown()
  }
}
