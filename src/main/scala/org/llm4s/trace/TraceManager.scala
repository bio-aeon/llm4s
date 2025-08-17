package org.llm4s.trace

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

/**
 * Factory for creating traces with proper initialization and lifecycle management.
 */
trait TraceManager {

  /**
   * Create a new root trace for a complete execution flow.
   *
   * @param name The name of the trace operation
   * @param userId Optional user identifier
   * @param sessionId Optional session identifier for grouping related traces
   * @param metadata Additional metadata to attach to the trace
   * @return A new Trace instance
   */
  def createTrace(
    name: String,
    userId: Option[String] = None,
    sessionId: Option[String] = None,
    metadata: Map[String, Any] = Map.empty
  ): Trace

  /**
   * Get the current active trace context, if any.
   */
  def currentTrace: Option[Trace]

  /**
   * Execute an operation within a new trace context.
   */
  def withTrace[T](
    name: String,
    userId: Option[String] = None,
    sessionId: Option[String] = None,
    metadata: Map[String, Any] = Map.empty
  )(operation: Trace => T): T

  /**
   * Execute an async operation within a new trace context.
   */
  def withTraceAsync[T](
    name: String,
    userId: Option[String] = None,
    sessionId: Option[String] = None,
    metadata: Map[String, Any] = Map.empty
  )(operation: Trace => Future[T])(implicit ec: ExecutionContext): Future[T]

  /**
   * Emit a trace event immediately (for real-time tracing).
   */
  def emitEvent(event: TraceEvent): Unit

  /**
   * Shutdown the trace manager and flush any pending events.
   */
  def shutdown(): Unit
}

/**
 * Configuration for trace manager behavior.
 */
case class TraceManagerConfig(
  enabled: Boolean = true,
  batchSize: Int = 100,
  flushInterval: FiniteDuration = 5.seconds,
  maxRetries: Int = 3,
  circuitBreakerThreshold: Int = 5,
  environment: String = "production",
  release: String = "1.0.0",
  version: String = "1.0.0"
)
