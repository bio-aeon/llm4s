package org.llm4s.trace

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Print implementation of TraceManager that outputs traces to console in real-time.
 * Events are printed immediately with proper indentation based on trace depth.
 */
class PrintTraceManager(config: TraceManagerConfig) extends BaseTraceManager(config) {

  private val formatter = DateTimeFormatter.ISO_INSTANT

  // Track active spans by ID to determine depth
  private val activeSpans = new ConcurrentHashMap[String, Int]()            // spanId -> depth
  private val spanParents = new ConcurrentHashMap[String, Option[String]]() // spanId -> parentSpanId

  override protected def createTraceImpl(
    traceId: String,
    name: String,
    userId: Option[String],
    sessionId: Option[String],
    metadata: Map[String, Any]
  ): BaseTrace =
    new PrintTrace(traceId, name, userId, sessionId, metadata, this)

  override protected def emitEventImpl(event: TraceEvent): Unit =
    if (config.enabled) {
      printEvent(event)
    }

  private def printEvent(event: TraceEvent): Unit = {
    val timestamp = formatter.format(event.timestamp)

    event match {
      case TraceCreateEvent(id, _, traceId, name, userId, sessionId, metadata, tags, input) =>
        println(s"[TRACE] [$timestamp] 🚀 TRACE START: $name (ID: $traceId)")
        userId.foreach(u => println(s"[TRACE]   User: $u"))
        sessionId.foreach(s => println(s"[TRACE]   Session: $s"))
        if (metadata.nonEmpty) println(s"[TRACE]   Metadata: $metadata")
        if (tags.nonEmpty) println(s"[TRACE]   Tags: $tags")
        input.foreach(i => println(s"[TRACE]   Input: $i"))

      case TraceUpdateEvent(id, _, traceId, metadata, tags, output, status, error) =>
        println(s"[TRACE] [$timestamp] 🏁 TRACE END: $traceId")
        if (metadata.nonEmpty) println(s"[TRACE]   Metadata: $metadata")
        if (tags.nonEmpty) println(s"[TRACE]   Tags: $tags")
        output.foreach(o => println(s"[TRACE]   Output: $o"))
        status.foreach(s => println(s"[TRACE]   Status: $s"))
        error.foreach(e => println(s"[TRACE]   Error: ${e.getMessage}"))

      case SpanCreateEvent(id, _, traceId, spanId, parentSpanId, name, startTime, metadata, tags, input) =>
        val depth = calculateDepth(parentSpanId)
        activeSpans.put(spanId, depth)
        spanParents.put(spanId, parentSpanId)

        val indent = "  " * depth
        println(s"[TRACE] [$timestamp] ${indent}🔵 SPAN START: $name (ID: $spanId)")
        if (metadata.nonEmpty) println(s"[TRACE]   ${indent}Metadata: $metadata")
        if (tags.nonEmpty) println(s"[TRACE]   ${indent}Tags: $tags")
        input.foreach(i => println(s"[TRACE]   ${indent}Input: $i"))

      case SpanUpdateEvent(id, _, traceId, spanId, endTime, metadata, tags, input, output, status, error) =>
        val depth  = activeSpans.getOrDefault(spanId, 0)
        val indent = "  " * depth

        println(s"[TRACE] [$timestamp] ${indent}🔴 SPAN END: $spanId")
        endTime.foreach(et =>
          println(s"[TRACE]   ${indent}Duration: ${java.time.Duration.between(startTime(spanId), et)}")
        )
        if (metadata.nonEmpty) println(s"[TRACE]   ${indent}Metadata: $metadata")
        if (tags.nonEmpty) println(s"[TRACE]   ${indent}Tags: $tags")
        input.foreach(i => println(s"[TRACE]   ${indent}Input: $i"))
        output.foreach(o => println(s"[TRACE]   ${indent}Output: $o"))
        status.foreach(s => println(s"[TRACE]   ${indent}Status: $s"))
        error.foreach(e => println(s"[TRACE]   ${indent}Error: ${e.getMessage}"))

        // Clean up tracking
        activeSpans.remove(spanId)
        spanParents.remove(spanId)

      case SpanEventEvent(id, _, traceId, spanId, eventName, eventTime, attributes) =>
        val depth  = activeSpans.getOrDefault(spanId, 0)
        val indent = "  " * (depth + 1)
        println(s"[TRACE] [$timestamp] ${indent}📝 EVENT: $eventName")
        if (attributes.nonEmpty) println(s"[TRACE]   ${indent}Attributes: $attributes")

      case GenerationEvent(
            id,
            _,
            traceId,
            spanId,
            name,
            startTime,
            endTime,
            model,
            modelParameters,
            input,
            output,
            usage,
            metadata,
            promptName,
            level,
            statusMessage
          ) =>
        val depth  = spanId.map(activeSpans.getOrDefault(_, 0)).getOrElse(0)
        val indent = "  " * (depth + 1)
        println(s"[TRACE] [$timestamp] ${indent}🤖 GENERATION: $name")
        println(s"[TRACE]   ${indent}Model: $model")
        if (modelParameters.nonEmpty) println(s"[TRACE]   ${indent}Parameters: $modelParameters")
        promptName.foreach(pn => println(s"[TRACE]   ${indent}Prompt: $pn"))
        level.foreach(l => println(s"[TRACE]   ${indent}Level: $l"))
        statusMessage.foreach(sm => println(s"[TRACE]   ${indent}Status: $sm"))
        input.foreach(i => println(s"[TRACE]   ${indent}Input: $i"))
        output.foreach(o => println(s"[TRACE]   ${indent}Output: $o"))
        usage.foreach(u =>
          println(
            s"[TRACE]   ${indent}Usage: ${u.totalTokens} tokens (${u.promptTokens} prompt, ${u.completionTokens} completion)"
          )
        )
        if (metadata.nonEmpty) println(s"[TRACE]   ${indent}Metadata: $metadata")

      case ToolCallEvent(id, _, traceId, spanId, name, startTime, endTime, toolName, input, output, metadata) =>
        val depth  = spanId.map(activeSpans.getOrDefault(_, 0)).getOrElse(0)
        val indent = "  " * (depth + 1)
        println(s"[TRACE] [$timestamp] ${indent}🔧 TOOL: $toolName")
        input.foreach(i => println(s"[TRACE]   ${indent}Input: $i"))
        output.foreach(o => println(s"[TRACE]   ${indent}Output: $o"))
        if (metadata.nonEmpty) println(s"[TRACE]   ${indent}Metadata: $metadata")

      case ScoreEvent(id, _, traceId, observationId, name, value, source, comment, metadata) =>
        println(s"[TRACE] [$timestamp] ⭐ SCORE: $name = $value")
        println(s"[TRACE]   Source: $source")
        observationId.foreach(oid => println(s"[TRACE]   Observation: $oid"))
        comment.foreach(c => println(s"[TRACE]   Comment: $c"))
        if (metadata.nonEmpty) println(s"[TRACE]   Metadata: $metadata")

      case GenerationUpdateEvent(id, _, traceId, generationId, endTime, output, usage, metadata) =>
        println(s"[TRACE] [$timestamp] 🔄 GENERATION UPDATE: $generationId")
        endTime.foreach(et => println(s"[TRACE]   End Time: $et"))
        output.foreach(o => println(s"[TRACE]   Output: $o"))
        usage.foreach(u =>
          println(
            s"[TRACE]   Usage: ${u.totalTokens} tokens (${u.promptTokens} prompt, ${u.completionTokens} completion)"
          )
        )
        if (metadata.nonEmpty) println(s"[TRACE]   Metadata: $metadata")
    }
  }

  private def calculateDepth(parentSpanId: Option[String]): Int =
    parentSpanId match {
      case None => 1 // Root level span
      case Some(parentId) =>
        val parentDepth = activeSpans.getOrDefault(parentId, 0)
        parentDepth + 1
    }

  private def startTime(@annotation.unused spanId: String): Instant =
    // This is a simplification - in reality we'd need to track start times
    // For now, just return current time
    Instant.now()
}

/**
 * Print-specific implementation of Trace.
 */
class PrintTrace(
  traceId: String,
  name: String,
  userId: Option[String],
  sessionId: Option[String],
  initialMetadata: Map[String, Any],
  manager: PrintTraceManager
) extends BaseTrace(traceId, name, userId, sessionId, initialMetadata, manager) {

  override protected def createSpanImpl(
    spanId: String,
    name: String,
    parentSpan: Option[BaseSpan]
  ): BaseSpan =
    new PrintSpan(
      spanId = spanId,
      traceId = traceId,
      parentSpanId = parentSpan.map(_.spanId),
      name = name,
      startTime = Instant.now(),
      trace = this
    )
}

/**
 * Print-specific implementation of Span.
 */
class PrintSpan(
  spanId: String,
  traceId: String,
  parentSpanId: Option[String],
  name: String,
  startTime: Instant,
  trace: PrintTrace
) extends BaseSpan(spanId, traceId, parentSpanId, name, startTime, trace) {

  override protected def createChildSpanImpl(
    spanId: String,
    name: String,
    parentSpan: BaseSpan
  ): BaseSpan =
    new PrintSpan(
      spanId = spanId,
      traceId = traceId,
      parentSpanId = Some(parentSpan.spanId),
      name = name,
      startTime = Instant.now(),
      trace = trace
    )
}
