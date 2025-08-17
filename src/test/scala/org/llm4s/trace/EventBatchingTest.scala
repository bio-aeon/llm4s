package org.llm4s.trace

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import java.time.Instant

class EventBatchingTest extends AnyFlatSpec with Matchers with BeforeAndAfterEach {
  implicit val ec: ExecutionContext = ExecutionContext.global

  "Event Batching" should "handle small batches efficiently" in {
    val config = TraceManagerConfig(
      enabled = true,
      batchSize = 5, // Small batch size for testing
      flushInterval = 1.second
    )
    val traceManager = new PrintTraceManager(config)

    // Create a trace with multiple spans to generate events
    val result = traceManager.withTrace("small-batch-test") { trace =>
      trace.addMetadata("batch_test", "small")

      // Create multiple spans to generate batching
      (1 to 3).map { i =>
        trace.span(s"batch-span-$i") { span =>
          span.addMetadata("span_number", i)
          span.addMetadata("timestamp", Instant.now())
          span.recordEvent("span_event", Map("event_id" -> i))

          // Nested spans for more events
          span.span(s"nested-span-$i") { nestedSpan =>
            nestedSpan.addMetadata("nested", true)
            nestedSpan.addTag(s"nested-$i")
            s"batch-result-$i"
          }
        }
      }
    }

    (result should have).length(3)
    traceManager.shutdown()
  }

  it should "handle large batches with multiple traces" in {
    val config = TraceManagerConfig(
      enabled = true,
      batchSize = 10,
      flushInterval = 2.seconds
    )
    val traceManager = new PrintTraceManager(config)

    // Create multiple traces to test batching across traces
    val traces = (1 to 5).map(traceId => traceManager.createTrace(s"batch-trace-$traceId"))

    traces.foreach { trace =>
      trace.addMetadata("batch_test", "large")

      (1 to 4).foreach { spanId =>
        trace.span(s"span-$spanId") { span =>
          span.addMetadata("span_id", spanId)
          span.addTag("batch-test")
          span.recordEvent(
            "batch_event",
            Map(
              "span_id"   -> spanId,
              "timestamp" -> Instant.now(),
              "data"      -> s"batch-data-$spanId"
            )
          )
        }
      }

      trace.finish()
    }

    traceManager.shutdown()
  }

  it should "handle rapid event generation" in {
    val config = TraceManagerConfig(
      enabled = true,
      batchSize = 20,
      flushInterval = 500.millis
    )
    val traceManager = new PrintTraceManager(config)

    val result = traceManager.withTrace("rapid-events-test") { trace =>
      trace.addMetadata("test_type", "rapid_events")

      // Generate many events rapidly
      trace.span("rapid-span") { span =>
        (1 to 50).foreach { i =>
          span.recordEvent(
            s"rapid_event_$i",
            Map(
              "event_number"   -> i,
              "timestamp"      -> Instant.now(),
              "batch_position" -> (i % 20) // Track position within batch
            )
          )

          // Add some metadata intermittently
          if (i % 10 == 0) {
            span.addMetadata(s"checkpoint_$i", s"reached_$i")
          }
        }

        "rapid-events-generated"
      }
    }

    result shouldBe "rapid-events-generated"
    traceManager.shutdown()
  }

  it should "handle batch flushing with time intervals" in {
    val config = TraceManagerConfig(
      enabled = true,
      batchSize = 100,           // Large batch size, should flush by time
      flushInterval = 100.millis // Short interval for testing
    )
    val traceManager = new PrintTraceManager(config)

    val result = traceManager.withTrace("timed-flush-test") { trace =>
      trace.addMetadata("flush_type", "timed")

      trace.span("timed-span") { span =>
        // Add events with delays to test time-based flushing
        (1 to 5).foreach { i =>
          span.recordEvent(s"timed_event_$i", Map("event_id" -> i))

          // Sleep to allow time-based flushing
          if (i < 5) Thread.sleep(150) // Longer than flush interval
        }

        "timed-flush-completed"
      }
    }

    result shouldBe "timed-flush-completed"
    traceManager.shutdown()
  }

  it should "handle concurrent event generation" in {
    val config = TraceManagerConfig(
      enabled = true,
      batchSize = 15,
      flushInterval = 1.second
    )
    val traceManager = new PrintTraceManager(config)

    val futures = (1 to 3).map { threadId =>
      Future {
        traceManager.withTrace(s"concurrent-batch-$threadId") { trace =>
          trace.addMetadata("thread_id", threadId)
          trace.addMetadata("test_type", "concurrent_batching")

          (1 to 10).map { i =>
            trace.span(s"concurrent-span-$threadId-$i") { span =>
              span.addMetadata("span_index", i)
              span.recordEvent(
                "concurrent_event",
                Map(
                  "thread_id" -> threadId,
                  "event_id"  -> i,
                  "timestamp" -> Instant.now()
                )
              )

              Thread.sleep(5) // Small delay to interleave operations
              s"concurrent-$threadId-$i"
            }
          }
        }
      }
    }

    val results = scala.concurrent.Await.result(
      Future.sequence(futures),
      10.seconds
    )

    (results should have).length(3)
    results.foreach(threadResult => (threadResult should have).length(10))

    traceManager.shutdown()
  }

  it should "handle batch overflow scenarios" in {
    val config = TraceManagerConfig(
      enabled = true,
      batchSize = 3,            // Very small batch for testing overflow
      flushInterval = 5.seconds // Long interval to force size-based flushing
    )
    val traceManager = new PrintTraceManager(config)

    val result = traceManager.withTrace("overflow-test") { trace =>
      trace.addMetadata("test_type", "batch_overflow")

      // Generate more events than batch size
      trace.span("overflow-span") { span =>
        (1 to 10).foreach { i =>
          span.recordEvent(
            s"overflow_event_$i",
            Map(
              "event_id"             -> i,
              "should_trigger_flush" -> (i % 3 == 0),
              "timestamp"            -> Instant.now()
            )
          )

          // Brief pause between events
          Thread.sleep(10)
        }

        "overflow-handled"
      }
    }

    result shouldBe "overflow-handled"
    traceManager.shutdown()
  }

  it should "handle empty and single-event batches" in {
    val config = TraceManagerConfig(
      enabled = true,
      batchSize = 10,
      flushInterval = 1.second
    )
    val traceManager = new PrintTraceManager(config)

    // Test empty batch scenario
    val emptyResult = traceManager.withTrace("empty-batch-test") { trace =>
      trace.addMetadata("test_type", "empty_batch")
      // No events generated
      "empty-batch-completed"
    }

    // Test single event batch
    val singleResult = traceManager.withTrace("single-event-test") { trace =>
      trace.addMetadata("test_type", "single_event")
      trace.span("single-span") { span =>
        span.recordEvent("single_event", Map("lonely" -> true))
        "single-event-completed"
      }
    }

    emptyResult shouldBe "empty-batch-completed"
    singleResult shouldBe "single-event-completed"

    traceManager.shutdown()
  }

  it should "handle complex nested events with batching" in {
    val config = TraceManagerConfig(
      enabled = true,
      batchSize = 8,
      flushInterval = 2.seconds
    )
    val traceManager = new PrintTraceManager(config)

    val result = traceManager.withTrace("complex-nested-test") { trace =>
      trace.addMetadata("complexity", "high")

      trace.span("parent-operations") { parentSpan =>
        parentSpan.recordEvent("parent_start", Map("level" -> "parent"))

        (1 to 3).foreach { i =>
          parentSpan.span(s"child-$i") { childSpan =>
            childSpan.recordEvent(s"child_${i}_start", Map("child_id" -> i))

            childSpan.span(s"grandchild-$i") { grandchildSpan =>
              grandchildSpan.recordEvent(
                s"grandchild_${i}_event",
                Map(
                  "generation" -> 3,
                  "child_id"   -> i,
                  "data"       -> s"nested-data-$i"
                )
              )

              grandchildSpan.addMetadata("depth", 3)
            }

            childSpan.recordEvent(s"child_${i}_end", Map("child_id" -> i))
          }
        }

        parentSpan.recordEvent("parent_end", Map("level" -> "parent"))
        "complex-nested-completed"
      }
    }

    result shouldBe "complex-nested-completed"
    traceManager.shutdown()
  }

  it should "handle shutdown with pending batches" in {
    val config = TraceManagerConfig(
      enabled = true,
      batchSize = 100,           // Large batch size
      flushInterval = 30.seconds // Long interval
    )
    val traceManager = new PrintTraceManager(config)

    // Generate some events but don't fill the batch
    traceManager.withTrace("shutdown-pending-test") { trace =>
      trace.addMetadata("test_type", "shutdown_with_pending")

      trace.span("pending-span") { span =>
        (1 to 5).foreach(i => span.recordEvent(s"pending_event_$i", Map("event_id" -> i)))

        "pending-events-created"
      }
    }

    // Shutdown should flush any pending events
    traceManager.shutdown()

    // Should complete without hanging or errors
    succeed
  }
}
