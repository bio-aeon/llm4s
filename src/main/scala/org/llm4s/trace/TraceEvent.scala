package org.llm4s.trace

import java.time.Instant

/**
 * Represents different types of events that can occur during tracing.
 * These events are collected and then sent to the tracing backend.
 */
sealed trait TraceEvent {
  def id: String
  def timestamp: Instant
  def traceId: String
}

/**
 * Event for creating a new trace.
 */
case class TraceCreateEvent(
  id: String,
  timestamp: Instant,
  traceId: String,
  name: String,
  userId: Option[String] = None,
  sessionId: Option[String] = None,
  metadata: Map[String, Any] = Map.empty,
  tags: Set[String] = Set.empty,
  input: Option[Any] = None
) extends TraceEvent

/**
 * Event for updating an existing trace.
 */
case class TraceUpdateEvent(
  id: String,
  timestamp: Instant,
  traceId: String,
  metadata: Map[String, Any] = Map.empty,
  tags: Set[String] = Set.empty,
  output: Option[Any] = None,
  status: Option[TraceStatus] = None,
  error: Option[Throwable] = None
) extends TraceEvent

/**
 * Event for creating a new span.
 */
case class SpanCreateEvent(
  id: String,
  timestamp: Instant,
  traceId: String,
  spanId: String,
  parentSpanId: Option[String],
  name: String,
  startTime: Instant,
  metadata: Map[String, Any] = Map.empty,
  tags: Set[String] = Set.empty,
  input: Option[Any] = None
) extends TraceEvent

/**
 * Event for updating an existing span.
 */
case class SpanUpdateEvent(
  id: String,
  timestamp: Instant,
  traceId: String,
  spanId: String,
  endTime: Option[Instant] = None,
  metadata: Map[String, Any] = Map.empty,
  tags: Set[String] = Set.empty,
  input: Option[Any] = None,
  output: Option[Any] = None,
  status: Option[SpanStatus] = None,
  error: Option[Throwable] = None
) extends TraceEvent

/**
 * Event for recording a discrete event during span execution.
 */
case class SpanEventEvent(
  id: String,
  timestamp: Instant,
  traceId: String,
  spanId: String,
  eventName: String,
  eventTime: Instant,
  attributes: Map[String, Any] = Map.empty
) extends TraceEvent

/**
 * Event for recording an LLM generation (API call).
 */
case class GenerationEvent(
  id: String,
  timestamp: Instant,
  traceId: String,
  spanId: Option[String],
  name: String,
  startTime: Instant,
  endTime: Option[Instant] = None,
  model: String,
  modelParameters: Map[String, Any] = Map.empty,
  input: Option[Any] = None,
  output: Option[Any] = None,
  usage: Option[TokenUsage] = None,
  metadata: Map[String, Any] = Map.empty,
  promptName: Option[String] = None,
  level: Option[String] = None,
  statusMessage: Option[String] = None
) extends TraceEvent

/**
 * Event for updating an existing LLM generation.
 */
case class GenerationUpdateEvent(
  id: String,
  timestamp: Instant,
  traceId: String,
  generationId: String,
  endTime: Option[Instant] = None,
  output: Option[Any] = None,
  usage: Option[TokenUsage] = None,
  metadata: Map[String, Any] = Map.empty
) extends TraceEvent

/**
 * Event for recording tool calls.
 */
case class ToolCallEvent(
  id: String,
  timestamp: Instant,
  traceId: String,
  spanId: Option[String],
  name: String,
  startTime: Instant,
  endTime: Option[Instant] = None,
  toolName: String,
  input: Option[Any] = None,
  output: Option[Any] = None,
  metadata: Map[String, Any] = Map.empty
) extends TraceEvent

/**
 * Event for recording scores/evaluations.
 */
case class ScoreEvent(
  id: String,
  timestamp: Instant,
  traceId: String,
  observationId: Option[String] = None,
  name: String,
  value: Double,
  source: String = "annotation",
  comment: Option[String] = None,
  metadata: Map[String, Any] = Map.empty
) extends TraceEvent

/**
 * Represents token usage information.
 */
case class TokenUsage(
  promptTokens: Int,
  completionTokens: Int,
  totalTokens: Int,
  unit: Option[String] = None,
  inputCost: Option[Double] = None,
  outputCost: Option[Double] = None,
  totalCost: Option[Double] = None
)

/**
 * Factory for creating trace events with common fields.
 */
object TraceEventFactory {

  /**
   * Create a trace create event.
   */
  def createTraceCreateEvent(
    trace: BaseTrace,
    eventId: String,
    timestamp: Instant
  ): TraceCreateEvent =
    TraceCreateEvent(
      id = eventId,
      timestamp = timestamp,
      traceId = trace.traceId,
      name = trace.name,
      userId = trace.userId,
      sessionId = trace.sessionId,
      metadata = trace.getMetadata,
      tags = trace.getTags,
      input = trace.getInput
    )

  /**
   * Create a trace update event.
   */
  def createTraceUpdateEvent(
    trace: BaseTrace,
    eventId: String,
    timestamp: Instant
  ): TraceUpdateEvent =
    TraceUpdateEvent(
      id = eventId,
      timestamp = timestamp,
      traceId = trace.traceId,
      metadata = trace.getMetadata,
      tags = trace.getTags,
      output = trace.getOutput,
      status = Some(trace.getStatus),
      error = trace.getError
    )

  /**
   * Create a span create event.
   */
  def createSpanCreateEvent(
    span: BaseSpan,
    eventId: String,
    timestamp: Instant
  ): SpanCreateEvent =
    SpanCreateEvent(
      id = eventId,
      timestamp = timestamp,
      traceId = span.traceId,
      spanId = span.spanId,
      parentSpanId = span.parentSpanId,
      name = span.name,
      startTime = span.startTime,
      metadata = span.getMetadata,
      tags = span.getTags,
      input = span.getInput
    )

  /**
   * Create a span update event.
   */
  def createSpanUpdateEvent(
    span: BaseSpan,
    eventId: String,
    timestamp: Instant
  ): SpanUpdateEvent =
    SpanUpdateEvent(
      id = eventId,
      timestamp = timestamp,
      traceId = span.traceId,
      spanId = span.spanId,
      endTime = span.endTime,
      metadata = span.getMetadata,
      tags = span.getTags,
      input = span.getInput,
      output = span.getOutput,
      status = Some(span.status),
      error = span.getError
    )
}
