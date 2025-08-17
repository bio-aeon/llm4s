package org.llm4s.samples.basic

import org.llm4s.llmconnect.LLM
import org.llm4s.llmconnect.model._
import org.llm4s.trace.TracingFactory

object BasicLLMCallingWithTrace {
  def main(args: Array[String]): Unit = {
    val traceManager = TracingFactory.create()
    
    // Create a trace for the entire conversation
    val trace = traceManager.createTrace(
      name = "llm-conversation",
      metadata = Map("sample" -> "BasicLLMCallingWithTrace")
    )

    // Create a conversation with messages
    val conversation = Conversation(
      Seq(
        SystemMessage("You are a helpful assistant. You will talk like a pirate."),
        UserMessage("Please write a scala function to add two integers"),
        AssistantMessage("Of course, me hearty! What can I do for ye?"),
        UserMessage("What's the best way to train a parrot?")
      )
    )

    // Get a client using environment variables
    val client = LLM.client()

    // Complete the conversation within a span for automatic generation tracking
    trace.span("llm-completion") { span =>
      client.complete(conversation, span = span)
    } match {
      case Right(completion) =>
        println(s"Model ID=${completion.id} is created at ${completion.created}")
        println(s"Model: ${completion.model}")
        println(s"Chat Role: ${completion.message.role}")
        println("Message:")
        println(completion.message.content)

        // Track tool calls if present
        if (completion.message.toolCalls.nonEmpty) {
          trace.span("tool-calls") { toolSpan =>
            completion.message.toolCalls.foreach { tc =>
              trace.recordToolCall(
                name = s"Tool: ${tc.name}",
                toolName = tc.name,
                startTime = java.time.Instant.now(),
                endTime = Some(java.time.Instant.now()),
                input = Some(tc.arguments),
                metadata = Map("tool_id" -> tc.id),
                spanId = Some(toolSpan.spanId)
              )
            }
          }
        }

      case Left(error) =>
        error match {
          case org.llm4s.llmconnect.model.UnknownError(throwable) =>
            println(s"Error: ${throwable.getMessage}")
            throwable.printStackTrace()
          case _ =>
            println(s"Error: ${error.message}")
        }
    }
    
    // Finish the trace to ensure all events are sent
    trace.finish()
  }
} 