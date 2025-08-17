package org.llm4s.trace

import org.llm4s.config.EnvLoader
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }
import scala.util.{ Failure, Success, Try }
import upickle.default._
import java.time.Instant
import org.llm4s.llmconnect.model.Message

/**
 * Langfuse implementation of TraceManager.
 * This manages traces that send events to Langfuse immediately via the ingestion API.
 */
class LangfuseTraceManager(
  config: TraceManagerConfig,
  langfuseConfig: LangfuseConfig
) extends BaseTraceManager(config) {

  private val logger         = LoggerFactory.getLogger(getClass)
  private val langfuseClient = new LangfuseClient(langfuseConfig)
  private val shutdownFlag   = new AtomicBoolean(false)

  override protected def createTraceImpl(
    traceId: String,
    name: String,
    userId: Option[String],
    sessionId: Option[String],
    metadata: Map[String, Any]
  ): BaseTrace =
    new LangfuseTrace(traceId, name, userId, sessionId, metadata, this)

  override protected def emitEventImpl(event: TraceEvent): Unit =
    if (config.enabled && !shutdownFlag.get()) {
      logger.info(s"[Langfuse] Emitting event: ${event.getClass.getSimpleName} with id ${event.id}")

      // Convert to Langfuse format and send immediately
      val langfuseEvent = convertToLangfuseEvent(event)

      logger.info(s"[Langfuse] Generated event JSON: ${langfuseEvent.render()}")

      val success = langfuseClient.sendBatch(Seq(langfuseEvent))

      if (!success) {
        logger.warn(s"[Langfuse] Failed to send event ${event.id}")
      } else {
        logger.info(s"[Langfuse] Successfully sent event ${event.id}")
      }
    }

  override def shutdown(): Unit =
    if (shutdownFlag.compareAndSet(false, true)) {
      logger.info("Shutting down LangfuseTraceManager...")

      // Complete any remaining traces
      super.shutdown()

      logger.info("LangfuseTraceManager shutdown complete")
    }

  /**
   * Extract token usage from an object if it contains usage information.
   */
  private def extractUsage(obj: Any): Option[ujson.Value] = {
    import org.llm4s.llmconnect.model._

    obj match {
      case comp: Completion if comp.usage.isDefined =>
        Some(
          ujson.Obj(
            "input"  -> comp.usage.get.promptTokens,
            "output" -> comp.usage.get.completionTokens,
            "total"  -> comp.usage.get.totalTokens,
            "unit"   -> "TOKENS"
          )
        )
      case _ => None
    }
  }

  /**
   * Extract metadata from Completion objects for span metadata field.
   */
  private def extractCompletionMetadata(obj: Any): Map[String, String] = {
    import org.llm4s.llmconnect.model._

    obj match {
      case comp: Completion =>
        Map(
          "completion_id" -> comp.id,
          "created"       -> comp.created.toString,
          "model"         -> comp.model
        ) ++ comp.usage
          .map(u =>
            Map(
              "prompt_tokens"     -> u.promptTokens.toString,
              "completion_tokens" -> u.completionTokens.toString,
              "total_tokens"      -> u.totalTokens.toString
            )
          )
          .getOrElse(Map.empty)
      case _ => Map.empty
    }
  }

  /**
   * Convert any object to ujson.Value for proper JSON serialization.
   * Uses upickle for known types, falls back to string representation.
   */
  private def convertToJson(obj: Any): ujson.Value = {
    import org.llm4s.llmconnect.model._

    logger.info(s"[Langfuse] Converting object to JSON: ${obj.getClass.getName}")
    logger.info(s"[Langfuse] Object content preview: ${obj.toString.take(200)}...")

    val result = obj match {
      case s: String =>
        logger.info(s"[Langfuse] Converting String: $s")
        ujson.Str(s)
      case n: Int     => ujson.Num(n)
      case n: Long    => ujson.Num(n.toDouble)
      case n: Double  => ujson.Num(n)
      case n: Float   => ujson.Num(n)
      case b: Boolean => ujson.Bool(b)
      case null       => ujson.Null
      case conv: Conversation =>
        logger.info(s"[Langfuse] Converting Conversation with ${conv.messages.length} messages")
        // Convert messages to Langfuse format with role fields instead of $type
        val langfuseMessages = conv.messages.map(convertMessageToLangfuseFormat)
        ujson.Arr(langfuseMessages: _*)
      case comp: Completion =>
        logger.info(s"[Langfuse] Converting Completion: id=${comp.id}")
        // For output field, extract the content based on message type
        import org.llm4s.llmconnect.model._
        comp.message match {
          case msg: AssistantMessage if msg.content.nonEmpty => ujson.Str(msg.content)
          case _                                             => convertMessageToLangfuseFormat(comp.message)
        }
      case msg: Message =>
        logger.info(s"[Langfuse] Converting Message: role=${msg.role}")
        convertMessageToLangfuseFormat(msg)
      case tc: ToolCall =>
        logger.info(s"[Langfuse] Converting ToolCall: name=${tc.name}")
        writeJs(tc)
      case tu: TokenUsage =>
        logger.info(s"[Langfuse] Converting TokenUsage: ${tu.totalTokens} tokens")
        writeJs(tu)
      case messages: Seq[_] if messages.nonEmpty && messages.head.isInstanceOf[Message] =>
        logger.info(s"[Langfuse] Converting Seq[Message] with ${messages.length} messages")
        // Convert sequence of messages to Langfuse format
        val langfuseMessages = messages.asInstanceOf[Seq[Message]].map(convertMessageToLangfuseFormat)
        ujson.Arr(langfuseMessages: _*)
      case _ =>
        logger.info(s"[Langfuse] Converting unknown type with toString, class: ${obj.getClass.getName}")
        // For other objects, try to parse the toString as JSON, fall back to string
        try {
          val jsonStr = obj.toString
          val parsed  = ujson.read(jsonStr)
          logger.info(s"[Langfuse] Successfully parsed toString as JSON: $parsed")
          parsed
        } catch {
          case e: Exception =>
            logger.info(s"[Langfuse] Failed to parse toString as JSON, using string: ${e.getMessage}")
            ujson.Str(obj.toString)
        }
    }

    logger.info(s"[Langfuse] Final JSON result type: ${result.getClass.getSimpleName}")
    result
  }

  /**
   * Convert an Instant to ISO 8601 string format required by Langfuse.
   */
  private def formatTimestamp(instant: Instant): String = instant.toString

  /**
   * Convert a Message to Langfuse format with role field instead of $type.
   */
  private def convertMessageToLangfuseFormat(message: Message): ujson.Value = {
    import org.llm4s.llmconnect.model._

    message match {
      case msg: UserMessage =>
        ujson.Obj("role" -> "user", "content" -> msg.content)
      case msg: SystemMessage =>
        ujson.Obj("role" -> "system", "content" -> msg.content)
      case msg: AssistantMessage =>
        val baseObj = ujson.Obj("role" -> "assistant")
        if (msg.content.nonEmpty) {
          baseObj("content") = msg.content
        }
        if (msg.toolCalls.nonEmpty) {
          baseObj("tool_calls") = ujson.Arr(msg.toolCalls.map(tc => writeJs(tc)): _*)
        }
        baseObj
      case msg: ToolMessage =>
        ujson.Obj(
          "role"         -> "tool",
          "tool_call_id" -> msg.toolCallId,
          "content"      -> msg.content
        )
    }
  }

  private def convertToLangfuseEvent(event: TraceEvent): ujson.Obj = {
    event match {
      case TraceCreateEvent(id, timestamp, traceId, name, userId, sessionId, metadata, tags, input) =>
        ujson.Obj(
          "id"        -> id,
          "timestamp" -> formatTimestamp(timestamp),
          "type"      -> "trace-create",
          "body" -> ujson.Obj(
            "id"        -> traceId,
            "timestamp" -> formatTimestamp(timestamp),
            "name"      -> name,
            "userId"    -> userId.map(ujson.Str.apply).getOrElse(ujson.Null),
            "sessionId" -> sessionId.map(ujson.Str.apply).getOrElse(ujson.Null),
            "metadata"  -> ujson.Obj.from(metadata.map { case (k, v) => k -> ujson.Str(v.toString) }),
            "tags"      -> ujson.Arr(tags.toSeq.map(ujson.Str.apply): _*),
            "input"     -> input.map(convertToJson).getOrElse(ujson.Null),
            "release"   -> ujson.Str(langfuseConfig.release),
            "version"   -> ujson.Str(langfuseConfig.version)
          )
        )

      case TraceUpdateEvent(id, timestamp, traceId, metadata, tags, output, status, error) =>
        ujson.Obj(
          "id"        -> id,
          "timestamp" -> formatTimestamp(timestamp),
          "type"      -> "trace-update",
          "body" -> ujson.Obj(
            "id"       -> traceId,
            "metadata" -> ujson.Obj.from(metadata.map { case (k, v) => k -> ujson.Str(v.toString) }),
            "tags"     -> ujson.Arr(tags.toSeq.map(ujson.Str.apply): _*),
            "output"   -> output.map(convertToJson).getOrElse(ujson.Null),
            "level" -> status
              .map {
                case TraceStatus.Ok        => ujson.Str("DEFAULT")
                case TraceStatus.Error     => ujson.Str("ERROR")
                case TraceStatus.Cancelled => ujson.Str("WARNING")
              }
              .getOrElse(ujson.Null),
            "statusMessage" -> error.map(e => ujson.Str(e.getMessage)).getOrElse(ujson.Null)
          )
        )

      case SpanCreateEvent(id, timestamp, traceId, spanId, parentSpanId, name, startTime, metadata, tags, input) =>
        ujson.Obj(
          "id"        -> id,
          "timestamp" -> formatTimestamp(timestamp),
          "type"      -> "span-create",
          "body" -> ujson.Obj(
            "id"                  -> spanId,
            "traceId"             -> traceId,
            "parentObservationId" -> parentSpanId.map(ujson.Str.apply).getOrElse(ujson.Null),
            "name"                -> name,
            "startTime"           -> formatTimestamp(startTime),
            "metadata"            -> ujson.Obj.from(metadata.map { case (k, v) => k -> ujson.Str(v.toString) }),
            "input"               -> input.map(convertToJson).getOrElse(ujson.Null)
          )
        )

      case SpanUpdateEvent(id, timestamp, traceId, spanId, endTime, metadata, tags, input, output, status, error) =>
        // Extract usage from output if available
        val extractedUsage = output.flatMap(extractUsage)

        // Extract completion metadata and merge with existing metadata
        val completionMetadata = output.map(extractCompletionMetadata).getOrElse(Map.empty)
        val mergedMetadata     = metadata ++ completionMetadata

        ujson.Obj(
          "id"        -> id,
          "timestamp" -> formatTimestamp(timestamp),
          "type"      -> "span-update",
          "body" -> ujson.Obj(
            "id"       -> spanId,
            "endTime"  -> endTime.map(t => ujson.Str(formatTimestamp(t))).getOrElse(ujson.Null),
            "metadata" -> ujson.Obj.from(mergedMetadata.map { case (k, v) => k -> ujson.Str(v.toString) }),
            "input"    -> input.map(convertToJson).getOrElse(ujson.Null),
            "output"   -> output.map(convertToJson).getOrElse(ujson.Null),
            "usage"    -> extractedUsage.getOrElse(ujson.Null),
            "level" -> status
              .map {
                case SpanStatus.Ok        => ujson.Str("DEFAULT")
                case SpanStatus.Error     => ujson.Str("ERROR")
                case SpanStatus.Cancelled => ujson.Str("WARNING")
              }
              .getOrElse(ujson.Null),
            "statusMessage" -> error.map(e => ujson.Str(e.getMessage)).getOrElse(ujson.Null)
          )
        )

      case SpanEventEvent(id, timestamp, traceId, spanId, eventName, eventTime, attributes) =>
        ujson.Obj(
          "id"        -> id,
          "timestamp" -> formatTimestamp(timestamp),
          "type"      -> "event-create",
          "body" -> ujson.Obj(
            "id"                  -> s"${spanId}_event_${eventName.replaceAll("[^a-zA-Z0-9]", "_")}",
            "traceId"             -> traceId,
            "parentObservationId" -> spanId,
            "name"                -> eventName,
            "startTime"           -> formatTimestamp(eventTime),
            "metadata"            -> ujson.Obj.from(attributes.map { case (k, v) => k -> ujson.Str(v.toString) })
          )
        )

      case GenerationEvent(
            id,
            timestamp,
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
        ujson.Obj(
          "id"        -> id,
          "timestamp" -> formatTimestamp(timestamp),
          "type"      -> "generation-create",
          "body" -> ujson.Obj(
            "id"                  -> s"${traceId}_gen_${name.replaceAll("[^a-zA-Z0-9]", "_")}",
            "traceId"             -> traceId,
            "parentObservationId" -> spanId.map(ujson.Str.apply).getOrElse(ujson.Null),
            "name"                -> name,
            "startTime"           -> formatTimestamp(startTime),
            "endTime"             -> endTime.map(t => ujson.Str(formatTimestamp(t))).getOrElse(ujson.Null),
            "model"               -> model,
            "modelParameters"     -> ujson.Obj.from(modelParameters.map { case (k, v) => k -> ujson.Str(v.toString) }),
            "input"               -> input.map(convertToJson).getOrElse(ujson.Null),
            "output"              -> output.map(convertToJson).getOrElse(ujson.Null),
            "usage" -> usage
              .map(u =>
                ujson.Obj(
                  "input"       -> u.promptTokens,
                  "output"      -> u.completionTokens,
                  "total"       -> u.totalTokens,
                  "unit"        -> ujson.Str(u.unit.getOrElse("TOKENS")),
                  "input_cost"  -> u.inputCost.map(ujson.Num.apply).getOrElse(ujson.Null),
                  "output_cost" -> u.outputCost.map(ujson.Num.apply).getOrElse(ujson.Null),
                  "total_cost"  -> u.totalCost.map(ujson.Num.apply).getOrElse(ujson.Null)
                )
              )
              .getOrElse(ujson.Null),
            "metadata"      -> ujson.Obj.from(metadata.map { case (k, v) => k -> ujson.Str(v.toString) }),
            "promptName"    -> promptName.map(ujson.Str.apply).getOrElse(ujson.Null),
            "level"         -> level.map(ujson.Str.apply).getOrElse(ujson.Str("DEFAULT")),
            "statusMessage" -> statusMessage.map(ujson.Str.apply).getOrElse(ujson.Null)
          )
        )

      case ToolCallEvent(id, timestamp, traceId, spanId, name, startTime, endTime, toolName, input, output, metadata) =>
        ujson.Obj(
          "id"        -> id,
          "timestamp" -> formatTimestamp(timestamp),
          "type"      -> "span-create",
          "body" -> ujson.Obj(
            "id"                  -> s"${traceId}_tool_${toolName.replaceAll("[^a-zA-Z0-9]", "_")}",
            "traceId"             -> traceId,
            "parentObservationId" -> spanId.map(ujson.Str.apply).getOrElse(ujson.Null),
            "name"                -> s"Tool: $toolName",
            "startTime"           -> formatTimestamp(startTime),
            "endTime"             -> endTime.map(t => ujson.Str(formatTimestamp(t))).getOrElse(ujson.Null),
            "input"               -> input.map(convertToJson).getOrElse(ujson.Null),
            "output"              -> output.map(convertToJson).getOrElse(ujson.Null),
            "metadata" -> ujson.Obj.from((metadata + ("toolName" -> toolName)).map { case (k, v) =>
              k -> ujson.Str(v.toString)
            })
          )
        )

      case ScoreEvent(id, timestamp, traceId, observationId, name, value, source, comment, metadata) =>
        ujson.Obj(
          "id"        -> id,
          "timestamp" -> formatTimestamp(timestamp),
          "type"      -> "score-create",
          "body" -> ujson.Obj(
            "id"            -> s"${traceId}_score_${name.replaceAll("[^a-zA-Z0-9]", "_")}",
            "traceId"       -> traceId,
            "observationId" -> observationId.map(ujson.Str.apply).getOrElse(ujson.Null),
            "name"          -> name,
            "value"         -> value,
            "source"        -> source,
            "comment"       -> comment.map(ujson.Str.apply).getOrElse(ujson.Null),
            "metadata"      -> ujson.Obj.from(metadata.map { case (k, v) => k -> ujson.Str(v.toString) })
          )
        )

      case GenerationUpdateEvent(id, timestamp, traceId, generationId, endTime, output, usage, metadata) =>
        ujson.Obj(
          "id"        -> id,
          "timestamp" -> formatTimestamp(timestamp),
          "type"      -> "generation-update",
          "body" -> ujson.Obj(
            "id"      -> generationId,
            "endTime" -> endTime.map(t => ujson.Str(formatTimestamp(t))).getOrElse(ujson.Null),
            "output"  -> output.map(convertToJson).getOrElse(ujson.Null),
            "usage" -> usage
              .map(u =>
                ujson.Obj(
                  "input"       -> u.promptTokens,
                  "output"      -> u.completionTokens,
                  "total"       -> u.totalTokens,
                  "unit"        -> ujson.Str(u.unit.getOrElse("TOKENS")),
                  "input_cost"  -> u.inputCost.map(ujson.Num.apply).getOrElse(ujson.Null),
                  "output_cost" -> u.outputCost.map(ujson.Num.apply).getOrElse(ujson.Null),
                  "total_cost"  -> u.totalCost.map(ujson.Num.apply).getOrElse(ujson.Null)
                )
              )
              .getOrElse(ujson.Null),
            "metadata" -> ujson.Obj.from(metadata.map { case (k, v) => k -> ujson.Str(v.toString) })
          )
        )
    }
  }
}

/**
 * Configuration for Langfuse integration.
 */
case class LangfuseConfig(
  host: String = EnvLoader.getOrElse("LANGFUSE_HOST", "https://cloud.langfuse.com"),
  publicKey: String = EnvLoader.getOrElse("LANGFUSE_PUBLIC_KEY", ""),
  secretKey: String = EnvLoader.getOrElse("LANGFUSE_SECRET_KEY", ""),
  environment: String = EnvLoader.getOrElse("LANGFUSE_ENV", "production"),
  release: String = EnvLoader.getOrElse("LANGFUSE_RELEASE", "1.0.0"),
  version: String = EnvLoader.getOrElse("LANGFUSE_VERSION", "1.0.0"),
  connectTimeout: Int = 30000,
  readTimeout: Int = 30000
) {
  def isValid: Boolean = publicKey.nonEmpty && secretKey.nonEmpty && host.nonEmpty
}

/**
 * HTTP client for Langfuse API.
 */
class LangfuseClient(config: LangfuseConfig) {
  private val logger      = LoggerFactory.getLogger(getClass)
  private val langfuseUrl = s"${config.host}/api/public/ingestion"

  // Circuit breaker state
  private val failureCount        = new AtomicInteger(0)
  private val circuitOpen         = new AtomicBoolean(false)
  private val lastFailureTime     = new AtomicInteger(0)
  private val circuitResetTimeout = 60000 // 1 minute

  def sendBatch(events: Seq[ujson.Obj]): Boolean = {
    if (!config.isValid) {
      logger.warn("[Langfuse] Public or secret key not set. Skipping export.")
      return false
    }

    // Check circuit breaker
    if (circuitOpen.get()) {
      val now = System.currentTimeMillis()
      if (now - lastFailureTime.get() > circuitResetTimeout) {
        circuitOpen.set(false)
        failureCount.set(0)
        logger.info("[Langfuse] Circuit breaker reset")
      } else {
        logger.info("[Langfuse] Circuit breaker is open, skipping batch")
        return false
      }
    }

    val batchPayload = ujson.Obj("batch" -> ujson.Arr(events: _*))

    logger.info(s"[Langfuse] Sending batch to URL: $langfuseUrl")
    println("Batch payload:" + batchPayload.render(indent = 2))
    logger.info(s"[Langfuse] Events in batch: ${events.length}")

    Try {
      val response = requests.post(
        langfuseUrl,
        data = batchPayload.render(indent = 2),
        headers = Map(
          "Content-Type" -> "application/json",
          "User-Agent"   -> "llm4s-scala/2.0.0"
        ),
        auth = (config.publicKey, config.secretKey),
        readTimeout = config.readTimeout,
        connectTimeout = config.connectTimeout
      )

      if (response.statusCode == 207 || (response.statusCode >= 200 && response.statusCode < 300)) {
        logger.info(s"[Langfuse] Batch export successful: ${response.statusCode}")
        if (response.statusCode == 207) {
          logger.info(s"[Langfuse] Partial success response: ${response.text()}")
        }

        // Reset circuit breaker on success
        if (failureCount.get() > 0) {
          failureCount.set(0)
        }

        true
      } else {
        logger.error(s"[Langfuse] Batch export failed: ${response.statusCode}")
        logger.error(s"[Langfuse] Response body: ${response.text()}")
        handleFailure()
        false
      }
    } match {
      case Success(result) => result
      case Failure(e) =>
        logger.error(s"[Langfuse] Batch export failed with exception: ${e.getMessage}", e)
        handleFailure()
        false
    }
  }

  private def handleFailure(): Unit = {
    val failures = failureCount.incrementAndGet()
    lastFailureTime.set(System.currentTimeMillis().toInt)

    if (failures >= 5) {
      circuitOpen.set(true)
      logger.warn(s"[Langfuse] Circuit breaker opened after $failures failures")
    }
  }
}

/**
 * Companion object for creating LangfuseTraceManager instances.
 */
object LangfuseTraceManager {

  /**
   * Create a LangfuseTraceManager with default configuration.
   */
  def create(): LangfuseTraceManager = {
    val config         = TraceManagerConfig()
    val langfuseConfig = LangfuseConfig()
    new LangfuseTraceManager(config, langfuseConfig)
  }

  /**
   * Create a LangfuseTraceManager with custom configuration.
   */
  def create(config: TraceManagerConfig, langfuseConfig: LangfuseConfig): LangfuseTraceManager =
    new LangfuseTraceManager(config, langfuseConfig)
}
