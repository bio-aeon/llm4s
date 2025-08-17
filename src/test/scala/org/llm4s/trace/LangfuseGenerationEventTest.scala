package org.llm4s.trace

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.llm4s.llmconnect.model._
import org.llm4s.trace.{ TokenUsage => TraceTokenUsage }
import java.time.Instant

/**
 * Test to verify that LLM calls generate proper generation-create events
 * instead of span-create events, and that model names are correctly passed.
 */
class LangfuseGenerationEventTest extends AnyFlatSpec with Matchers {

  private val traceManager = LangfuseTraceManager.create()

  // Access the private convertToLangfuseEvent method
  private def convertToLangfuseEvent(event: TraceEvent): ujson.Obj = {
    val method = classOf[LangfuseTraceManager].getDeclaredMethod("convertToLangfuseEvent", classOf[TraceEvent])
    method.setAccessible(true)
    method.invoke(traceManager, event).asInstanceOf[ujson.Obj]
  }

  "LangfuseTraceManager" should "generate generation-create events for LLM completions" in {
    val conversation = Conversation(
      Seq(
        SystemMessage("You are a helpful assistant"),
        UserMessage("What is the capital of France?")
      )
    )

    val completion = Completion(
      id = "chatcmpl-BvRGXdZ6Kaqkety4tkWONaHgASU6z",
      created = 1753029205L,
      message = AssistantMessage("The capital of France is Paris."),
      model = "gpt-4o-mini",
      usage = Some(
        org.llm4s.llmconnect.model.TokenUsage(
          promptTokens = 64,
          completionTokens = 257,
          totalTokens = 321
        )
      )
    )

    val event = GenerationEvent(
      id = "gen-event-123",
      timestamp = Instant.parse("2025-07-20T16:33:29.707484Z"),
      traceId = "trace-123",
      spanId = None,
      name = "Agent LLM Completion",
      startTime = Instant.parse("2025-07-20T16:33:29.500Z"),
      endTime = Some(Instant.parse("2025-07-20T16:33:29.700Z")),
      model = completion.model,
      modelParameters = Map(
        "temperature" -> "0.7",
        "top_p"       -> "0.95",
        "max_tokens"  -> "500"
      ),
      input = Some(conversation),
      output = Some(completion),
      usage = Some(
        TraceTokenUsage(
          promptTokens = completion.usage.get.promptTokens,
          completionTokens = completion.usage.get.completionTokens,
          totalTokens = completion.usage.get.totalTokens
        )
      ),
      metadata = Map(
        "tools_available" -> "get_weather,search_web",
        "message_count"   -> "2",
        "has_tool_calls"  -> "false",
        "tool_call_count" -> "0"
      )
    )

    val result = convertToLangfuseEvent(event)

    // Verify event type is generation-create, not span-create
    result("type").str should be("generation-create")

    val body = result("body").obj

    // Verify model is correctly set to the actual model used
    body("model").str should be("gpt-4o-mini")

    // Verify model parameters are included
    val modelParams = body("modelParameters").obj
    modelParams("temperature").str should be("0.7")
    modelParams("top_p").str should be("0.95")
    modelParams("max_tokens").str should be("500")

    // Verify input is properly formatted
    val input = body("input").arr
    input.length should be(2)
    input(0).obj("role").str should be("system")
    input(0).obj("content").str should be("You are a helpful assistant")
    input(1).obj("role").str should be("user")
    input(1).obj("content").str should be("What is the capital of France?")

    // Verify output is just the content string
    body("output").str should be("The capital of France is Paris.")

    // Verify usage is correctly formatted
    val usage = body("usage").obj
    usage("input").num should be(64)
    usage("output").num should be(257)
    usage("total").num should be(321)
    usage("unit").str should be("TOKENS")

    // Verify metadata
    val metadata = body("metadata").obj
    metadata("tools_available").str should be("get_weather,search_web")
    metadata("message_count").str should be("2")
    metadata("has_tool_calls").str should be("false")
  }

  it should "use actual model name from different providers" in {
    // Test with Anthropic model
    val anthropicCompletion = Completion(
      id = "msg-123",
      created = 1753029205L,
      message = AssistantMessage("Hello from Claude!"),
      model = "claude-3-5-sonnet-20241022",
      usage = Some(org.llm4s.llmconnect.model.TokenUsage(100, 50, 150))
    )

    val anthropicEvent = GenerationEvent(
      id = "gen-event-456",
      timestamp = Instant.now(),
      traceId = "trace-456",
      spanId = None,
      name = "Anthropic Completion",
      startTime = Instant.now(),
      model = anthropicCompletion.model,
      input = Some("Hello Claude"),
      output = Some(anthropicCompletion)
    )

    val anthropicResult = convertToLangfuseEvent(anthropicEvent)
    anthropicResult("body").obj("model").str should be("claude-3-5-sonnet-20241022")

    // Test with OpenRouter model
    val openRouterCompletion = Completion(
      id = "or-123",
      created = 1753029205L,
      message = AssistantMessage("Hello from OpenRouter!"),
      model = "openai/gpt-4-turbo",
      usage = Some(org.llm4s.llmconnect.model.TokenUsage(80, 40, 120))
    )

    val openRouterEvent = GenerationEvent(
      id = "gen-event-789",
      timestamp = Instant.now(),
      traceId = "trace-789",
      spanId = None,
      name = "OpenRouter Completion",
      startTime = Instant.now(),
      model = openRouterCompletion.model,
      input = Some("Hello OpenRouter"),
      output = Some(openRouterCompletion)
    )

    val openRouterResult = convertToLangfuseEvent(openRouterEvent)
    openRouterResult("body").obj("model").str should be("openai/gpt-4-turbo")
  }

  it should "extract completion metadata including model to span metadata when used in span update" in {
    val completion = Completion(
      id = "chatcmpl-123",
      created = 1753028530L,
      message = AssistantMessage("Test response"),
      model = "gpt-4-turbo-preview",
      usage = Some(org.llm4s.llmconnect.model.TokenUsage(50, 75, 125))
    )

    val event = SpanUpdateEvent(
      id = "span-update-event",
      timestamp = Instant.parse("2024-01-15T10:30:45.123Z"),
      traceId = "trace-123",
      spanId = "span-456",
      output = Some(completion)
    )

    val result = convertToLangfuseEvent(event)
    val body   = result("body").obj

    // For span updates, we should still see the model in metadata
    val metadata = body("metadata").obj
    metadata("model").str should be("gpt-4-turbo-preview")
    metadata("completion_id").str should be("chatcmpl-123")
  }
}
