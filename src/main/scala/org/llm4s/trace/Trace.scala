package org.llm4s.trace

import java.time.Instant
import scala.concurrent.{ ExecutionContext, Future }

/**
 * Represents a complete execution flow with hierarchical spans.
 * A trace is the root container for all related operations.
 */
trait Trace {

  /**
   * The unique identifier for this trace.
   */
  def traceId: String

  /**
   * The name of this trace operation.
   */
  def name: String

  /**
   * Execute an operation within a new span.
   *
   * @param name The name of the span operation
   * @param operation The operation to execute, receiving the span as parameter
   * @return The result of the operation
   */
  def span[T](name: String)(operation: Span => T): T

  /**
   * Execute an async operation within a new span.
   *
   * @param name The name of the span operation
   * @param operation The async operation to execute, receiving the span as parameter
   * @return A Future containing the result of the operation
   */
  def spanAsync[T](name: String)(operation: Span => Future[T])(implicit ec: ExecutionContext): Future[T]

  /**
   * Add metadata to this trace.
   *
   * @param key The metadata key
   * @param value The metadata value
   */
  def addMetadata(key: String, value: Any): Unit

  /**
   * Add multiple metadata entries to this trace.
   *
   * @param metadata Map of metadata key-value pairs
   */
  def addMetadata(metadata: Map[String, Any]): Unit

  /**
   * Add a tag to this trace.
   *
   * @param tag The tag to add
   */
  def addTag(tag: String): Unit

  /**
   * Add multiple tags to this trace.
   *
   * @param tags The tags to add
   */
  def addTags(tags: String*): Unit

  /**
   * Set the input for this trace.
   *
   * @param input The input data
   */
  def setInput(input: Any): Unit

  /**
   * Set the output for this trace.
   *
   * @param output The output data
   */
  def setOutput(output: Any): Unit

  /**
   * Record an error that occurred during trace execution.
   *
   * @param error The error that occurred
   */
  def recordError(error: Throwable): Unit

  /**
   * Get the current active span within this trace, if any.
   */
  def currentSpan: Option[Span]

  /**
   * Get the trace context for propagation.
   */
  def context: TraceContext

  /**
   * Complete this trace and flush all collected events.
   * This should be called when the trace execution is finished.
   */
  def finish(): Unit

  /**
   * Check if this trace has been finished.
   */
  def isFinished: Boolean

  /**
   * Record an LLM generation event within this trace.
   * This is a convenience method for tracking LLM API calls.
   *
   * @param name The name of the generation event
   * @param model The model used for generation
   * @param startTime The start time of the generation
   * @param endTime The end time of the generation (optional)
   * @param modelParameters Parameters used for the model (e.g., temperature, max_tokens)
   * @param input The input to the generation (optional)
   * @param output The output from the generation (optional)
   * @param usage Token usage information (optional)
   * @param metadata Additional metadata for the generation (optional)
   * @param spanId Parent span ID if this generation is part of a span (optional)
   */
  def recordGeneration(
    name: String,
    model: String,
    startTime: Instant,
    endTime: Option[Instant] = None,
    modelParameters: Map[String, Any] = Map.empty,
    input: Option[Any] = None,
    output: Option[Any] = None,
    usage: Option[TokenUsage] = None,
    metadata: Map[String, Any] = Map.empty,
    spanId: Option[String] = None
  ): Unit

  /**
   * Record a tool call event within this trace.
   * This is a convenience method for tracking tool/function calls.
   *
   * @param name The name of the tool call event
   * @param toolName The name of the tool being called
   * @param startTime The start time of the tool call
   * @param endTime The end time of the tool call (optional)
   * @param input The input to the tool (optional)
   * @param output The output from the tool (optional)
   * @param metadata Additional metadata for the tool call (optional)
   * @param spanId Parent span ID if this tool call is part of a span (optional)
   */
  def recordToolCall(
    name: String,
    toolName: String,
    startTime: Instant,
    endTime: Option[Instant] = None,
    input: Option[Any] = None,
    output: Option[Any] = None,
    metadata: Map[String, Any] = Map.empty,
    spanId: Option[String] = None
  ): Unit
}

/**
 * Status of a trace or span.
 */
sealed trait TraceStatus
object TraceStatus {
  case object Ok        extends TraceStatus
  case object Error     extends TraceStatus
  case object Cancelled extends TraceStatus
}
