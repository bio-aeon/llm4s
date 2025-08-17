package org.llm4s.llmconnect

import org.llm4s.llmconnect.model._
import org.llm4s.trace.{ NoOpSpan, Span, TokenUsage => TraceTokenUsage }
import java.time.Instant

/**
 * Base implementation of LLMClient that provides automatic generation tracking.
 * Concrete implementations should implement the doComplete and doStreamComplete methods.
 */
abstract class BaseLLMClient extends LLMClient {

  /**
   * Internal method that performs the actual completion without tracing.
   * Implementations should override this method.
   */
  protected def doComplete(
    conversation: Conversation,
    options: CompletionOptions
  ): Either[LLMError, Completion]

  /**
   * Internal method that performs the actual streaming completion without tracing.
   * Implementations should override this method.
   */
  protected def doStreamComplete(
    conversation: Conversation,
    options: CompletionOptions,
    onChunk: StreamedChunk => Unit
  ): Either[LLMError, Completion]

  /**
   * Complete a conversation with automatic generation tracking
   */
  override def complete(
    conversation: Conversation,
    options: CompletionOptions = CompletionOptions(),
    span: Span = NoOpSpan
  ): Either[LLMError, Completion] = {
    val startTime = Instant.now()
    val metadata = Map(
      "message_count" -> conversation.messages.size.toString,
      "has_tools"     -> options.tools.nonEmpty.toString
    ) ++ (if (options.tools.nonEmpty) {
            Map("tools_available" -> options.tools.map(_.name).mkString(", "))
          } else Map.empty)

    val result = doComplete(conversation, options)

    result match {
      case Right(completion) =>
        // Record the generation event
        span.recordGeneration(
          name = "LLM Completion",
          model = completion.model,
          startTime = startTime,
          endTime = Some(Instant.now()),
          modelParameters = Map(
            "temperature" -> options.temperature.toString,
            "top_p"       -> options.topP.toString
          ) ++ options.maxTokens.map(mt => Map("max_tokens" -> mt.toString)).getOrElse(Map.empty),
          input = Some(conversation),
          output = Some(completion),
          usage = completion.usage.map(u => TraceTokenUsage(u.promptTokens, u.completionTokens, u.totalTokens)),
          metadata = metadata ++ Map(
            "completion_id"  -> completion.id,
            "created"        -> completion.created.toString,
            "has_tool_calls" -> completion.message.toolCalls.nonEmpty.toString
          ) ++ (if (completion.message.toolCalls.nonEmpty) {
                  Map(
                    "tool_calls" -> completion.message.toolCalls.map(tc => s"${tc.name}(${tc.id})").mkString(", ")
                  )
                } else Map.empty)
        )

      case Left(error) =>
        // Record error in generation event
        span.recordGeneration(
          name = "LLM Completion",
          model = "unknown",
          startTime = startTime,
          endTime = Some(Instant.now()),
          input = Some(conversation),
          metadata = metadata ++ Map(
            "error"      -> error.message,
            "error_type" -> error.getClass.getSimpleName
          )
        )
    }

    result
  }

  /**
   * Stream a completion with automatic generation tracking
   */
  override def streamComplete(
    conversation: Conversation,
    options: CompletionOptions = CompletionOptions(),
    onChunk: StreamedChunk => Unit,
    span: Span = NoOpSpan
  ): Either[LLMError, Completion] = {
    val startTime = Instant.now()
    val metadata = Map(
      "message_count" -> conversation.messages.size.toString,
      "streaming"     -> "true",
      "has_tools"     -> options.tools.nonEmpty.toString
    ) ++ (if (options.tools.nonEmpty) {
            Map("tools_available" -> options.tools.map(_.name).mkString(", "))
          } else Map.empty)

    val result = doStreamComplete(conversation, options, onChunk)

    result match {
      case Right(completion) =>
        // Record the generation event
        span.recordGeneration(
          name = "LLM Stream Completion",
          model = completion.model,
          startTime = startTime,
          endTime = Some(Instant.now()),
          modelParameters = Map(
            "temperature" -> options.temperature.toString,
            "top_p"       -> options.topP.toString,
            "stream"      -> "true"
          ) ++ options.maxTokens.map(mt => Map("max_tokens" -> mt.toString)).getOrElse(Map.empty),
          input = Some(conversation),
          output = Some(completion),
          usage = completion.usage.map(u => TraceTokenUsage(u.promptTokens, u.completionTokens, u.totalTokens)),
          metadata = metadata ++ Map(
            "completion_id"  -> completion.id,
            "created"        -> completion.created.toString,
            "has_tool_calls" -> completion.message.toolCalls.nonEmpty.toString
          )
        )

      case Left(error) =>
        // Record error in generation event
        span.recordGeneration(
          name = "LLM Stream Completion",
          model = "unknown",
          startTime = startTime,
          endTime = Some(Instant.now()),
          input = Some(conversation),
          metadata = metadata ++ Map(
            "error"      -> error.message,
            "error_type" -> error.getClass.getSimpleName,
            "streaming"  -> "true"
          )
        )
    }

    result
  }
}
