package org.llm4s.llmconnect.model

import upickle.default._

/**
 * Represents a completion response from an LLM.
 *  This includes the ID, creation timestamp, the assistant's message, model used, and optional token usage statistics.
 *
 * @param id Unique identifier for the completion.
 * @param created Timestamp of when the completion was created.
 * @param message The assistant's message in response to the user's input.
 * @param model The model that generated this completion.
 * @param usage Optional token usage statistics for the completion.
 */
case class Completion(
  id: String,
  created: Long,
  message: AssistantMessage,
  model: String,
  usage: Option[TokenUsage] = None
)

object Completion {
  implicit val rw: ReadWriter[Completion] = macroRW
}

case class TokenUsage(
  promptTokens: Int,
  completionTokens: Int,
  totalTokens: Int
)

object TokenUsage {
  implicit val rw: ReadWriter[TokenUsage] = macroRW
}

case class StreamedChunk(
  id: String,
  content: Option[String],
  toolCall: Option[ToolCall] = None,
  finishReason: Option[String] = None
)

object StreamedChunk {
  implicit val rw: ReadWriter[StreamedChunk] = macroRW
}
