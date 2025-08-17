package org.llm4s.trace

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.llm4s.llmconnect.model._

class LangfuseMessageFormatTest extends AnyFlatSpec with Matchers {

  private val traceManager = LangfuseTraceManager.create()

  // Access the private method for testing
  private def convertMessageToLangfuseFormat(message: Message): ujson.Value = {
    val method = classOf[LangfuseTraceManager].getDeclaredMethod("convertMessageToLangfuseFormat", classOf[Message])
    method.setAccessible(true)
    method.invoke(traceManager, message).asInstanceOf[ujson.Value]
  }

  "LangfuseTraceManager" should "convert UserMessage to correct Langfuse format" in {
    val userMessage = UserMessage("Hello, how are you?")
    val result      = convertMessageToLangfuseFormat(userMessage)

    result shouldBe ujson.Obj(
      "role"    -> "user",
      "content" -> "Hello, how are you?"
    )
  }

  it should "convert SystemMessage to correct Langfuse format" in {
    val systemMessage = SystemMessage("You are a helpful assistant.")
    val result        = convertMessageToLangfuseFormat(systemMessage)

    result shouldBe ujson.Obj(
      "role"    -> "system",
      "content" -> "You are a helpful assistant."
    )
  }

  it should "convert AssistantMessage to correct Langfuse format" in {
    val assistantMessage = AssistantMessage("Hi there! I'm doing well, thank you.")
    val result           = convertMessageToLangfuseFormat(assistantMessage)

    result shouldBe ujson.Obj(
      "role"    -> "assistant",
      "content" -> "Hi there! I'm doing well, thank you."
    )
  }

  it should "convert ToolMessage to correct Langfuse format" in {
    val toolMessage = ToolMessage("tool_123", """{"result": "success"}""")
    val result      = convertMessageToLangfuseFormat(toolMessage)

    result shouldBe ujson.Obj(
      "role"         -> "tool",
      "tool_call_id" -> "tool_123",
      "content"      -> """{"result": "success"}"""
    )
  }

  it should "convert Conversation to array of messages with correct format" in {
    val conversation = Conversation(
      Seq(
        SystemMessage("You are a helpful assistant."),
        UserMessage("Hello"),
        AssistantMessage("Hi there!")
      )
    )

    // Access the private convertToJson method
    val method = classOf[LangfuseTraceManager].getDeclaredMethod("convertToJson", classOf[Any])
    method.setAccessible(true)
    val result = method.invoke(traceManager, conversation).asInstanceOf[ujson.Value]

    result shouldBe ujson.Arr(
      ujson.Obj("role" -> "system", "content"    -> "You are a helpful assistant."),
      ujson.Obj("role" -> "user", "content"      -> "Hello"),
      ujson.Obj("role" -> "assistant", "content" -> "Hi there!")
    )
  }
}
