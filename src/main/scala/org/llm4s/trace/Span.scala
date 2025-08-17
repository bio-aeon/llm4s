package org.llm4s.trace

import java.time.Instant
import scala.concurrent.{ ExecutionContext, Future }

/**
 * Represents a single operation with duration within a trace.
 * Spans can be nested to create hierarchical operation trees.
 */
trait Span {

  /**
   * The unique identifier for this span.
   */
  def spanId: String

  /**
   * The identifier of the trace this span belongs to.
   */
  def traceId: String

  /**
   * The identifier of the parent span, if this is a child span.
   */
  def parentSpanId: Option[String]

  /**
   * The name of this span operation.
   */
  def name: String

  /**
   * The start time of this span.
   */
  def startTime: Instant

  /**
   * The end time of this span, if it has finished.
   */
  def endTime: Option[Instant]

  /**
   * Execute an operation within a child span.
   *
   * @param name The name of the child span operation
   * @param operation The operation to execute, receiving the child span as parameter
   * @return The result of the operation
   */
  def span[T](name: String)(operation: Span => T): T

  /**
   * Execute an async operation within a child span.
   *
   * @param name The name of the child span operation
   * @param operation The async operation to execute, receiving the child span as parameter
   * @return A Future containing the result of the operation
   */
  def spanAsync[T](name: String)(operation: Span => Future[T])(implicit ec: ExecutionContext): Future[T]

  /**
   * Add metadata to this span.
   *
   * @param key The metadata key
   * @param value The metadata value
   */
  def addMetadata(key: String, value: Any): Unit

  /**
   * Add multiple metadata entries to this span.
   *
   * @param metadata Map of metadata key-value pairs
   */
  def addMetadata(metadata: Map[String, Any]): Unit

  /**
   * Add a tag to this span.
   *
   * @param tag The tag to add
   */
  def addTag(tag: String): Unit

  /**
   * Add multiple tags to this span.
   *
   * @param tags The tags to add
   */
  def addTags(tags: String*): Unit

  /**
   * Set the input for this span.
   *
   * @param input The input data
   */
  def setInput(input: Any): Unit

  /**
   * Set the output for this span.
   *
   * @param output The output data
   */
  def setOutput(output: Any): Unit

  /**
   * Set the status of this span.
   *
   * @param status The status to set
   */
  def setStatus(status: SpanStatus): Unit

  /**
   * Record an error that occurred during span execution.
   * This automatically sets the status to Error.
   *
   * @param error The error that occurred
   */
  def recordError(error: Throwable): Unit

  /**
   * Record an event that occurred during span execution.
   *
   * @param name The name of the event
   * @param attributes Optional attributes for the event
   */
  def recordEvent(name: String, attributes: Map[String, Any] = Map.empty): Unit

  /**
   * Get the current status of this span.
   */
  def status: SpanStatus

  /**
   * Check if this span has finished.
   */
  def isFinished: Boolean

  /**
   * Get the span context for propagation.
   */
  def context: SpanContext

  /**
   * Record an LLM generation event within this span.
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
    metadata: Map[String, Any] = Map.empty
  ): Unit

  /**
   * Record a tool call event within this span.
   * This is a convenience method for tracking tool/function calls.
   *
   * @param name The name of the tool call event
   * @param toolName The name of the tool being called
   * @param startTime The start time of the tool call
   * @param endTime The end time of the tool call (optional)
   * @param input The input to the tool (optional)
   * @param output The output from the tool (optional)
   * @param metadata Additional metadata for the tool call (optional)
   */
  def recordToolCall(
    name: String,
    toolName: String,
    startTime: Instant,
    endTime: Option[Instant] = None,
    input: Option[Any] = None,
    output: Option[Any] = None,
    metadata: Map[String, Any] = Map.empty
  ): Unit
}

/**
 * Status of a span.
 */
sealed trait SpanStatus
object SpanStatus {
  case object Ok        extends SpanStatus
  case object Error     extends SpanStatus
  case object Cancelled extends SpanStatus
}
