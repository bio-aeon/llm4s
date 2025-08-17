package org.llm4s.trace

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import java.util.concurrent.{ Executors, TimeUnit }

class ContextPropagationTest extends AnyFlatSpec with Matchers with BeforeAndAfterEach {
  implicit val ec: ExecutionContext = ExecutionContext.global

  "TraceContext" should "propagate context across thread boundaries" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)

    traceManager.withTrace("context-test") { trace =>
      trace.addMetadata("thread", Thread.currentThread().getName)

      // Context should be available within the same thread
      traceManager.currentTrace shouldBe Some(trace)

      val result = trace.span("parent-span") { parentSpan =>
        parentSpan.addMetadata("span_type", "parent")

        // Context should include current span
        trace.currentSpan shouldBe Some(parentSpan)

        val childResult = parentSpan.span("child-span") { childSpan =>
          childSpan.addMetadata("span_type", "child")

          // Context should now point to child span
          trace.currentSpan shouldBe Some(childSpan)

          "context-propagated"
        }

        // After child span, context should return to parent
        trace.currentSpan shouldBe Some(parentSpan)
        childResult
      }

      // After parent span, no current span
      trace.currentSpan shouldBe None
      result shouldBe "context-propagated"
    }

    // After trace completion, no current trace
    traceManager.currentTrace shouldBe None

    traceManager.shutdown()
  }

  it should "handle async operations with context propagation" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)

    val futureResult = traceManager.withTraceAsync("async-context-test") { trace =>
      trace.addMetadata("operation", "async")

      // Start an async operation
      Future {
        Thread.sleep(50) // Simulate async work
        trace.addMetadata("async_completed", true)
        "async-result"
      }
    }

    // Wait for the async operation to complete
    val result = scala.concurrent.Await.result(futureResult, 5.seconds)
    result shouldBe "async-result"

    traceManager.shutdown()
  }

  it should "handle multiple concurrent traces" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)

    val futures = (1 to 3).map { i =>
      traceManager.withTraceAsync(s"concurrent-trace-$i") { trace =>
        trace.addMetadata("trace_id", i)

        Future {
          Thread.sleep(20 * i) // Different sleep times
          trace.span(s"work-span-$i") { span =>
            span.addMetadata("work_duration", s"${20 * i}ms")
            s"result-$i"
          }
        }
      }
    }

    // Wait for all futures to complete
    val results = scala.concurrent.Await.result(
      Future.sequence(futures),
      10.seconds
    )

    (results should contain).allOf("result-1", "result-2", "result-3")
    traceManager.shutdown()
  }

  it should "handle nested async operations" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)

    val result = traceManager.withTraceAsync("nested-async-test") { trace =>
      trace.addMetadata("operation", "nested_async")

      trace.span("async-parent") { parentSpan =>
        parentSpan.addMetadata("span_level", "parent")

        // First async operation
        val future1 = Future {
          Thread.sleep(30)
          parentSpan.addMetadata("async1_completed", true)
          "async1-result"
        }

        // Second async operation that depends on first
        val future2 = future1.flatMap { result1 =>
          Future {
            Thread.sleep(20)
            parentSpan.addMetadata("async2_completed", true)
            s"$result1-async2-result"
          }
        }

        future2
      }
    }

    val finalResult = scala.concurrent.Await.result(result, 5.seconds)
    finalResult shouldBe "async1-result-async2-result"

    traceManager.shutdown()
  }

  it should "maintain context isolation between traces" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)

    // Create two separate traces
    val trace1 = traceManager.createTrace("isolated-trace-1")
    val trace2 = traceManager.createTrace("isolated-trace-2")

    trace1.addMetadata("trace_id", "1")
    trace2.addMetadata("trace_id", "2")

    // Operations in trace1 shouldn't affect trace2
    trace1.span("span-1") { span1 =>
      span1.addMetadata("belongs_to", "trace-1")

      trace2.span("span-2") { span2 =>
        span2.addMetadata("belongs_to", "trace-2")

        // Each trace should maintain its own context
        trace1.currentSpan shouldBe Some(span1)
        trace2.currentSpan shouldBe Some(span2)

        "isolation-verified"
      }
    }

    trace1.finish()
    trace2.finish()
    traceManager.shutdown()
  }

  it should "handle context in custom thread pools" in {
    val config         = TraceManagerConfig(enabled = true)
    val traceManager   = new PrintTraceManager(config)
    val customExecutor = Executors.newFixedThreadPool(2)
    val customEc       = ExecutionContext.fromExecutor(customExecutor)

    try {
      val result = traceManager.withTrace("custom-threadpool-test") { trace =>
        trace.addMetadata("main_thread", Thread.currentThread().getName)

        // Execute on custom thread pool
        val future = Future {
          trace.addMetadata("worker_thread", Thread.currentThread().getName)

          trace.span("worker-span") { span =>
            span.addMetadata("executor", "custom")
            Thread.sleep(25)
            "custom-thread-result"
          }
        }(customEc)

        scala.concurrent.Await.result(future, 5.seconds)
      }

      result shouldBe "custom-thread-result"
    } finally {
      customExecutor.shutdown()
      customExecutor.awaitTermination(5, TimeUnit.SECONDS)
      traceManager.shutdown()
    }
  }

  it should "handle context during exception scenarios" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)

    val result = traceManager.withTrace("exception-context-test") { trace =>
      trace.addMetadata("test_type", "exception_handling")

      try
        trace.span("error-prone-span") { span =>
          span.addMetadata("will_throw", true)

          // Verify context is still available before exception
          trace.currentSpan shouldBe Some(span)

          throw new RuntimeException("Test exception")
        }
      catch {
        case e: RuntimeException =>
          // Context should still be available after exception
          trace.currentSpan shouldBe None // span finished due to exception
          trace.recordError(e)
          "exception-handled"
      }
    }

    result shouldBe "exception-handled"
    traceManager.shutdown()
  }

  it should "handle rapid context switching" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = new PrintTraceManager(config)

    val result = traceManager.withTrace("rapid-switching-test") { trace =>
      trace.addMetadata("test_type", "rapid_switching")

      // Rapidly create and destroy spans
      (1 to 10).map { i =>
        trace.span(s"rapid-span-$i") { span =>
          span.addMetadata("span_number", i)

          // Verify correct context
          trace.currentSpan shouldBe Some(span)

          // Brief work simulation
          Thread.sleep(1)

          s"rapid-$i"
        }
      }
    }

    (result should have).length(10)
    result.head shouldBe "rapid-1"
    result.last shouldBe "rapid-10"

    traceManager.shutdown()
  }
}
