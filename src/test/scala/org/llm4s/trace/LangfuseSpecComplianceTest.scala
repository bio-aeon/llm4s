package org.llm4s.trace

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.llm4s.llmconnect.model._
import org.llm4s.trace.{ TokenUsage => TraceTokenUsage }
import java.time.Instant

/**
 * Additional tests to ensure strict compliance with Langfuse API specification.
 * Covers edge cases, validation rules, and format requirements from langfuse_spec.md.
 */
class LangfuseSpecComplianceTest extends AnyFlatSpec with Matchers {

  private val traceManager = LangfuseTraceManager.create()

  // Access the private convertToLangfuseEvent method
  private def convertToLangfuseEvent(event: TraceEvent): ujson.Obj = {
    val method = classOf[LangfuseTraceManager].getDeclaredMethod("convertToLangfuseEvent", classOf[TraceEvent])
    method.setAccessible(true)
    method.invoke(traceManager, event).asInstanceOf[ujson.Obj]
  }

  "LangfuseTraceManager" should "generate events with required envelope structure" in {
    val event = TraceCreateEvent(
      id = "test-event-123",
      timestamp = Instant.parse("2024-01-15T10:30:45.123Z"),
      traceId = "trace-123",
      name = "Test Trace"
    )

    val result = convertToLangfuseEvent(event)

    // Verify all required envelope fields are present
    result.obj.keys should contain("id")
    result.obj.keys should contain("timestamp")
    result.obj.keys should contain("type")
    result.obj.keys should contain("body")

    // Verify field types
    result("id") shouldBe a[ujson.Str]
    result("timestamp") shouldBe a[ujson.Str]
    result("type") shouldBe a[ujson.Str]
    result("body") shouldBe a[ujson.Obj]
  }

  it should "format timestamps in ISO 8601 format" in {
    val testTimestamp = Instant.parse("2024-01-15T10:30:45.123Z")
    val event = SpanCreateEvent(
      id = "test-event",
      timestamp = testTimestamp,
      traceId = "trace-123",
      spanId = "span-456",
      parentSpanId = None,
      name = "Test Span",
      startTime = testTimestamp
    )

    val result = convertToLangfuseEvent(event)
    val body   = result("body").obj

    // Check envelope timestamp is ISO 8601
    (result("timestamp").str should fullyMatch).regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z")

    // Check body timestamp is ISO 8601
    (body("startTime").str should fullyMatch).regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z")
  }

  it should "handle OpenAI-compatible message format without $type fields" in {
    val messages = Seq(
      SystemMessage("You are a helpful assistant."),
      UserMessage("Hello, world!"),
      AssistantMessage("Hi! How can I help you today?"),
      ToolMessage("call_123", """{"result": "success"}""")
    )

    val conversation = Conversation(messages)
    val event = TraceCreateEvent(
      id = "test-event",
      timestamp = Instant.parse("2024-01-15T10:30:45.123Z"),
      traceId = "trace-123",
      name = "OpenAI Format Test",
      input = Some(conversation)
    )

    val result = convertToLangfuseEvent(event)
    val body   = result("body").obj
    val input  = body("input").arr

    // Verify OpenAI-compatible format
    (input(0).obj should contain).key("role")
    (input(0).obj should contain).key("content")
    input(0).obj("role").str should be("system")
    input(0).obj("content").str should be("You are a helpful assistant.")

    input(1).obj("role").str should be("user")
    input(1).obj("content").str should be("Hello, world!")

    input(2).obj("role").str should be("assistant")
    input(2).obj("content").str should be("Hi! How can I help you today?")

    input(3).obj("role").str should be("tool")
    input(3).obj("tool_call_id").str should be("call_123")
    input(3).obj("content").str should be("""{"result": "success"}""")

    // Critical: Ensure NO $type fields anywhere
    input.foreach(msg => msg.obj.keys should not contain "$type")
  }

  it should "generate usage details in correct format (not metadata)" in {
    val usage = TraceTokenUsage(
      promptTokens = 150,
      completionTokens = 50,
      totalTokens = 200
    )

    val event = GenerationEvent(
      id = "test-event",
      timestamp = Instant.parse("2024-01-15T10:30:45.123Z"),
      traceId = "trace-123",
      spanId = None,
      name = "Usage Test",
      startTime = Instant.parse("2024-01-15T10:30:45.123Z"),
      model = "gpt-4",
      usage = Some(usage)
    )

    val result = convertToLangfuseEvent(event)
    val body   = result("body").obj

    // Should have usage field (not usage_details or in metadata)
    body.obj.keys should contain("usage")
    body.obj.keys should not contain "usage_details"

    val usageObj = body("usage").obj

    // Verify correct field names and types
    usageObj("input").num should be(150)
    usageObj("output").num should be(50)
    usageObj("total").num should be(200)

    // Should NOT be in metadata
    val metadata = body("metadata").obj
    metadata.keys should not contain "input"
    metadata.keys should not contain "output"
    metadata.keys should not contain "total"
  }

  it should "handle status levels correctly" in {
    val okEvent = SpanUpdateEvent(
      id = "test-event-ok",
      timestamp = Instant.parse("2024-01-15T10:30:45.123Z"),
      traceId = "trace-123",
      spanId = "span-456",
      status = Some(SpanStatus.Ok)
    )

    val errorEvent = SpanUpdateEvent(
      id = "test-event-error",
      timestamp = Instant.parse("2024-01-15T10:30:45.123Z"),
      traceId = "trace-123",
      spanId = "span-456",
      status = Some(SpanStatus.Error),
      error = Some(new RuntimeException("Test error"))
    )

    val cancelledEvent = SpanUpdateEvent(
      id = "test-event-cancelled",
      timestamp = Instant.parse("2024-01-15T10:30:45.123Z"),
      traceId = "trace-123",
      spanId = "span-456",
      status = Some(SpanStatus.Cancelled)
    )

    val okResult        = convertToLangfuseEvent(okEvent)("body").obj
    val errorResult     = convertToLangfuseEvent(errorEvent)("body").obj
    val cancelledResult = convertToLangfuseEvent(cancelledEvent)("body").obj

    // Verify status mappings match Langfuse spec
    okResult("level").str should be("DEFAULT")
    errorResult("level").str should be("ERROR")
    cancelledResult("level").str should be("WARNING")

    // Verify error message handling
    okResult("statusMessage") should be(ujson.Null)
    errorResult("statusMessage").str should be("Test error")
    cancelledResult("statusMessage") should be(ujson.Null)
  }

  it should "handle complex tool calls with proper format" in {
    val toolCall1 = ToolCall(
      id = "call_abc123",
      name = "get_weather",
      arguments = ujson.Obj("location" -> "Paris", "units" -> "metric")
    )

    val toolCall2 = ToolCall(
      id = "call_def456",
      name = "search_web",
      arguments = ujson.Obj("query" -> "Scala programming", "limit" -> 5)
    )

    val assistantMessage = AssistantMessage(
      contentOpt = Some("I'll help you with that information."),
      toolCalls = Seq(toolCall1, toolCall2)
    )

    val conversation = Conversation(
      Seq(
        UserMessage("What's the weather in Paris and can you search for Scala info?"),
        assistantMessage
      )
    )

    val event = TraceCreateEvent(
      id = "test-event",
      timestamp = Instant.parse("2024-01-15T10:30:45.123Z"),
      traceId = "trace-123",
      name = "Multi-Tool Test",
      input = Some(conversation)
    )

    val result = convertToLangfuseEvent(event)
    val body   = result("body").obj
    val input  = body("input").arr

    // Verify assistant message structure
    val assistantMsg = input(1).obj
    assistantMsg("role").str should be("assistant")
    assistantMsg("content").str should be("I'll help you with that information.")

    // Verify tool calls array
    val toolCalls = assistantMsg("tool_calls").arr
    toolCalls.length should be(2)

    // Verify first tool call
    val tool1 = toolCalls(0).obj
    tool1("id").str should be("call_abc123")
    tool1("name").str should be("get_weather")
    val args1 = tool1("arguments").obj
    args1("location").str should be("Paris")
    args1("units").str should be("metric")

    // Verify second tool call
    val tool2 = toolCalls(1).obj
    tool2("id").str should be("call_def456")
    tool2("name").str should be("search_web")
    val args2 = tool2("arguments").obj
    args2("query").str should be("Scala programming")
    args2("limit").num should be(5)

    // Ensure no $type fields in any part
    assistantMsg.keys should not contain "$type"
    tool1.keys should not contain "$type"
    tool2.keys should not contain "$type"
  }

  it should "handle null and empty values correctly" in {
    val event = GenerationEvent(
      id = "test-event",
      timestamp = Instant.parse("2024-01-15T10:30:45.123Z"),
      traceId = "trace-123",
      spanId = None, // Should become ujson.Null
      name = "Null Test",
      startTime = Instant.parse("2024-01-15T10:30:45.123Z"),
      endTime = None, // Should become ujson.Null
      model = "test-model",
      input = None,        // Should become ujson.Null
      output = None,       // Should become ujson.Null
      usage = None,        // Should become ujson.Null
      metadata = Map.empty // Should become empty object
    )

    val result = convertToLangfuseEvent(event)
    val body   = result("body").obj

    // Verify null values are properly serialized
    body("parentObservationId") should be(ujson.Null)
    body("endTime") should be(ujson.Null)
    body("input") should be(ujson.Null)
    body("output") should be(ujson.Null)
    body("usage") should be(ujson.Null)

    // Verify empty metadata is empty object (not null)
    body("metadata").obj.keys should be(empty)
  }

  it should "generate unique IDs for nested events" in {
    val event1 = GenerationEvent(
      id = "event-1",
      timestamp = Instant.parse("2024-01-15T10:30:45.123Z"),
      traceId = "trace-123",
      spanId = None,
      name = "Gen 1",
      startTime = Instant.parse("2024-01-15T10:30:45.123Z"),
      model = "test-model"
    )

    val event2 = GenerationEvent(
      id = "event-2",
      timestamp = Instant.parse("2024-01-15T10:30:45.123Z"),
      traceId = "trace-123",
      spanId = None,
      name = "Gen 2",
      startTime = Instant.parse("2024-01-15T10:30:45.123Z"),
      model = "test-model"
    )

    val result1 = convertToLangfuseEvent(event1)
    val result2 = convertToLangfuseEvent(event2)

    val body1 = result1("body").obj
    val body2 = result2("body").obj

    // Event IDs should be different (for deduplication)
    result1("id").str should not be result2("id").str

    // Body IDs should also be different (these are the generated IDs)
    body1("id").str should not be body2("id").str

    // Both should start with trace ID and contain generation identifier
    body1("id").str should startWith("trace-123_gen_")
    body2("id").str should startWith("trace-123_gen_")
  }

  it should "preserve exact field names required by Langfuse API" in {
    val event = SpanCreateEvent(
      id = "test-event",
      timestamp = Instant.parse("2024-01-15T10:30:45.123Z"),
      traceId = "trace-123",
      spanId = "span-456",
      parentSpanId = Some("parent-789"),
      name = "Field Name Test",
      startTime = Instant.parse("2024-01-15T10:30:45.123Z"),
      metadata = Map("custom_field" -> "value"),
      tags = Set("tag1", "tag2")
    )

    val result = convertToLangfuseEvent(event)
    val body   = result("body").obj

    // Verify exact field names match Langfuse spec
    body.obj.keys should contain("id")
    body.obj.keys should contain("traceId")
    body.obj.keys should contain("parentObservationId") // Not parentSpanId
    body.obj.keys should contain("name")
    body.obj.keys should contain("startTime")
    body.obj.keys should contain("metadata")

    // Verify proper parent field name
    body("parentObservationId").str should be("parent-789")
  }
}
