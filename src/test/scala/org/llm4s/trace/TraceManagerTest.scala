package org.llm4s.trace

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

class TraceManagerTest extends AnyFlatSpec with Matchers with BeforeAndAfterEach {
  implicit val ec: ExecutionContext = ExecutionContext.global

  "NoOpTraceManager" should "create no-op traces" in {
    val traceManager = NoOpTraceManager
    val trace        = traceManager.createTrace("test-trace")

    trace shouldBe a[NoOpTrace.type]
    traceManager.currentTrace shouldBe None
  }

  it should "handle withTrace operations" in {
    val traceManager = NoOpTraceManager
    val result = traceManager.withTrace("test-trace") { trace =>
      trace.addMetadata("key", "value")
      "result"
    }

    result shouldBe "result"
  }

  it should "handle async withTrace operations" in {
    val traceManager = NoOpTraceManager
    val future = traceManager.withTraceAsync("test-trace") { trace =>
      trace.addMetadata("key", "value")
      Future.successful("async-result")
    }

    future.map(result => result shouldBe "async-result")
  }

  it should "handle shutdown gracefully" in {
    val traceManager = NoOpTraceManager
    noException should be thrownBy traceManager.shutdown()
  }

  "PrintTraceManager" should "create print traces" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = TracingFactory.createTraceManager(TracingFactory.PRINT_MODE, config)

    val trace = traceManager.createTrace("test-trace", Some("user123"))
    trace shouldBe a[PrintTrace]

    traceManager.shutdown()
  }

  it should "handle trace lifecycle" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = TracingFactory.createTraceManager(TracingFactory.PRINT_MODE, config)

    val result = traceManager.withTrace("test-trace", Some("user123")) { trace =>
      trace.addMetadata("operation", "test")
      trace.span("sub-operation") { span =>
        span.addMetadata("step", "1")
        "success"
      }
    }

    result shouldBe "success"
    traceManager.shutdown()
  }

  it should "handle async operations" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = TracingFactory.createTraceManager(TracingFactory.PRINT_MODE, config)

    val future = traceManager.withTraceAsync("async-trace") { trace =>
      trace.addMetadata("async", true)
      Future {
        Thread.sleep(10)
        "async-complete"
      }
    }

    future.map { result =>
      result shouldBe "async-complete"
      traceManager.shutdown()
    }
  }

  "BaseTraceManager" should "manage trace context" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = TracingFactory.createTraceManager(TracingFactory.PRINT_MODE, config)

    traceManager.currentTrace shouldBe None

    traceManager.withTrace("test-trace") { trace =>
      traceManager.currentTrace shouldBe Some(trace)

      traceManager.withTrace("nested-trace")(nestedTrace => traceManager.currentTrace shouldBe Some(nestedTrace))

      traceManager.currentTrace shouldBe Some(trace)
    }

    traceManager.currentTrace shouldBe None
    traceManager.shutdown()
  }

  it should "handle trace configuration" in {
    val config = TraceManagerConfig(
      enabled = true,
      batchSize = 50,
      flushInterval = 10.seconds,
      maxRetries = 5
    )
    val traceManager = TracingFactory.createTraceManager(TracingFactory.PRINT_MODE, config)

    traceManager.createTrace("test-trace")
    traceManager.shutdown()
  }

  it should "handle errors gracefully" in {
    val config       = TraceManagerConfig(enabled = true)
    val traceManager = TracingFactory.createTraceManager(TracingFactory.PRINT_MODE, config)

    val result = traceManager.withTrace("error-trace") { trace =>
      trace.recordError(new RuntimeException("test error"))
      "handled"
    }

    result shouldBe "handled"
    traceManager.shutdown()
  }

  it should "handle disabled tracing" in {
    val config       = TraceManagerConfig(enabled = false)
    val traceManager = TracingFactory.createTraceManager(TracingFactory.PRINT_MODE, config)

    val trace = traceManager.createTrace("disabled-trace")
    trace shouldBe a[NoOpTrace.type]

    traceManager.shutdown()
  }
}
