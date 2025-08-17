package org.llm4s.trace

import java.time.Instant

/**
 * Langfuse-specific implementation of Span.
 * This span implementation is designed to work with the Langfuse ingestion API.
 */
class LangfuseSpan(
  spanId: String,
  traceId: String,
  parentSpanId: Option[String],
  name: String,
  startTime: Instant,
  trace: LangfuseTrace
) extends BaseSpan(spanId, traceId, parentSpanId, name, startTime, trace) {

  override protected def createChildSpanImpl(
    spanId: String,
    name: String,
    parentSpan: BaseSpan
  ): BaseSpan =
    new LangfuseSpan(
      spanId = spanId,
      traceId = traceId,
      parentSpanId = Some(parentSpan.spanId),
      name = name,
      startTime = Instant.now(),
      trace = trace
    )

  /**
   * Create a child span specifically for tracking LLM generations.
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
   * Create a child span specifically for tracking tool calls.
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

  /**
   * Helper method to get the trace as a LangfuseTrace.
   */
  def langfuseTrace: LangfuseTrace = trace
}
