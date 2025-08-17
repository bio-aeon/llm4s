package org.llm4s.llmconnect

import org.llm4s.llmconnect.model._
import org.llm4s.trace.{ NoOpSpan, Span }

trait LLMClient {

  /** Complete a conversation and get a response */
  def complete(
    conversation: Conversation,
    options: CompletionOptions = CompletionOptions(),
    span: Span = NoOpSpan
  ): Either[LLMError, Completion]

  /** Stream a completion with callback for chunks */
  def streamComplete(
    conversation: Conversation,
    options: CompletionOptions = CompletionOptions(),
    onChunk: StreamedChunk => Unit,
    span: Span = NoOpSpan
  ): Either[LLMError, Completion]
}
