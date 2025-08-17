package org.llm4s.trace

import java.time.Instant

/**
 * Langfuse-specific implementation of Trace.
 * This trace implementation is designed to work with the Langfuse ingestion API.
 */
class LangfuseTrace(
  traceId: String,
  name: String,
  userId: Option[String],
  sessionId: Option[String],
  initialMetadata: Map[String, Any],
  manager: LangfuseTraceManager
) extends BaseTrace(traceId, name, userId, sessionId, initialMetadata, manager) {

  override protected def createSpanImpl(
    spanId: String,
    name: String,
    parentSpan: Option[BaseSpan]
  ): BaseSpan =
    new LangfuseSpan(
      spanId = spanId,
      traceId = traceId,
      parentSpanId = parentSpan.map(_.spanId),
      name = name,
      startTime = Instant.now(),
      trace = this
    )

  /**
   * Record an LLM generation event within this trace.
   * This is a convenience method for tracking LLM API calls.
   */
  override def recordGeneration(
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
  ): Unit = {
    val event = GenerationEvent(
      id = manager.generateEventId(),
      timestamp = Instant.now(),
      traceId = traceId,
      spanId = spanId,
      name = name,
      startTime = startTime,
      endTime = endTime,
      model = model,
      modelParameters = modelParameters,
      input = input,
      output = output,
      usage = usage,
      metadata = metadata
    )
    manager.emitEvent(event)
  }

  /**
   * Record a tool call event within this trace.
   * This is a convenience method for tracking tool executions.
   */
  override def recordToolCall(
    name: String,
    toolName: String,
    startTime: Instant,
    endTime: Option[Instant] = None,
    input: Option[Any] = None,
    output: Option[Any] = None,
    metadata: Map[String, Any] = Map.empty,
    spanId: Option[String] = None
  ): Unit = {
    val event = ToolCallEvent(
      id = manager.generateEventId(),
      timestamp = Instant.now(),
      traceId = traceId,
      spanId = spanId,
      name = name,
      startTime = startTime,
      endTime = endTime,
      toolName = toolName,
      input = input,
      output = output,
      metadata = metadata
    )
    manager.emitEvent(event)
  }

  /**
   * Create a span specifically for tracking LLM generations.
   * This automatically records generation events.
   */
  def generationSpan[T](
    name: String,
    model: String,
    modelParameters: Map[String, Any] = Map.empty,
    input: Option[Any] = None
  )(operation: (LangfuseSpan, GenerationTracker) => T): T =
    span(name) { span =>
      val langfuseSpan = span.asInstanceOf[LangfuseSpan]
      val tracker      = new GenerationTracker(langfuseSpan, model, modelParameters, input)

      try {
        val result = operation(langfuseSpan, tracker)
        tracker.finish()
        result
      } catch {
        case e: Throwable =>
          tracker.finish(Some(e))
          throw e
      }
    }

  /**
   * Create a span specifically for tracking tool calls.
   * This automatically records tool call events.
   */
  def toolCallSpan[T](
    name: String,
    toolName: String,
    input: Option[Any] = None
  )(operation: (LangfuseSpan, ToolCallTracker) => T): T =
    span(name) { span =>
      val langfuseSpan = span.asInstanceOf[LangfuseSpan]
      val tracker      = new ToolCallTracker(langfuseSpan, toolName, input)

      try {
        val result = operation(langfuseSpan, tracker)
        tracker.finish()
        result
      } catch {
        case e: Throwable =>
          tracker.finish(Some(e))
          throw e
      }
    }
}

/**
 * Helper class for tracking LLM generation events within a span.
 */
class GenerationTracker(
  span: LangfuseSpan,
  model: String,
  modelParameters: Map[String, Any],
  input: Option[Any]
) {
  private val startTime = Instant.now()
  private var finished  = false

  def finish(
    output: Option[Any] = None,
    usage: Option[TokenUsage] = None,
    metadata: Map[String, Any] = Map.empty,
    error: Option[Throwable] = None
  ): Unit =
    if (!finished) {
      finished = true

      span.trace
        .asInstanceOf[LangfuseTrace]
        .recordGeneration(
          name = s"${span.name} - Generation",
          model = model,
          startTime = startTime,
          endTime = Some(Instant.now()),
          modelParameters = modelParameters,
          input = input,
          output = output,
          usage = usage,
          metadata = metadata + ("spanId" -> span.spanId),
          spanId = Some(span.spanId)
        )

      if (error.isDefined) {
        span.recordError(error.get)
      }
    }
}

/**
 * Helper class for tracking tool call events within a span.
 */
class ToolCallTracker(
  span: LangfuseSpan,
  toolName: String,
  input: Option[Any]
) {
  private val startTime = Instant.now()
  private var finished  = false

  def finish(
    output: Option[Any] = None,
    metadata: Map[String, Any] = Map.empty,
    error: Option[Throwable] = None
  ): Unit =
    if (!finished) {
      finished = true

      span.trace
        .asInstanceOf[LangfuseTrace]
        .recordToolCall(
          name = s"${span.name} - Tool Call",
          toolName = toolName,
          startTime = startTime,
          endTime = Some(Instant.now()),
          input = input,
          output = output,
          metadata = metadata + ("spanId" -> span.spanId),
          spanId = Some(span.spanId)
        )

      if (error.isDefined) {
        span.recordError(error.get)
      }
    }
}
