package org.llm4s.trace

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.llm4s.llmconnect.model._
import org.llm4s.trace.{ TokenUsage => TraceTokenUsage }
import java.time.Instant

/**
 * Comprehensive test suite for Langfuse event format compliance.
 * Tests all event types defined in the Langfuse API specification.
 */
class LangfuseEventFormatTest extends AnyFlatSpec with Matchers {

  private val traceManager = LangfuseTraceManager.create()

  // Access the private convertToLangfuseEvent method
  private def convertToLangfuseEvent(event: TraceEvent): ujson.Obj = {
    val method = classOf[LangfuseTraceManager].getDeclaredMethod("convertToLangfuseEvent", classOf[TraceEvent])
    method.setAccessible(true)
    method.invoke(traceManager, event).asInstanceOf[ujson.Obj]
  }

  // Helper methods for creating test data
  private def testTimestamp: Instant = Instant.parse("2024-01-15T10:30:45.123Z")
  private def testEventId: String    = "test-event-123"
  private def testTraceId: String    = "trace-123"
  private def testSpanId: String     = "span-456"

  "LangfuseTraceManager" should "generate trace-create events with correct format" in {
    val event = TraceCreateEvent(
      id = testEventId,
      timestamp = testTimestamp,
      traceId = testTraceId,
      name = "API Request Handler",
      userId = Some("user-123"),
      sessionId = Some("session-456"),
      metadata = Map("experiment_id" -> "exp-123", "version" -> "1.0.0"),
      tags = Set("production", "v2"),
      input = Some(
        Conversation(
          Seq(
            SystemMessage("You are a helpful assistant"),
            UserMessage("Hello")
          )
        )
      )
    )

    val result = convertToLangfuseEvent(event)

    // Verify envelope structure
    result("id").str should be("test-event-123")
    result("timestamp").str should be("2024-01-15T10:30:45.123Z")
    result("type").str should be("trace-create")

    // Verify body structure
    val body = result("body").obj
    body("id").str should be(testTraceId)
    body("timestamp").str should be("2024-01-15T10:30:45.123Z")
    body("name").str should be("API Request Handler")
    body("userId").str should be("user-123")
    body("sessionId").str should be("session-456")

    // Verify metadata structure
    val metadata = body("metadata").obj
    metadata("experiment_id").str should be("exp-123")
    metadata("version").str should be("1.0.0")

    // Verify tags
    val tags = body("tags").arr.map(_.str)
    (tags should contain).allOf("production", "v2")

    // Verify input messages have correct format (no $type fields)
    val input = body("input").arr
    input.length should be(2)
    input(0).obj("role").str should be("system")
    input(0).obj("content").str should be("You are a helpful assistant")
    input(1).obj("role").str should be("user")
    input(1).obj("content").str should be("Hello")

    // Ensure no $type fields are present
    input(0).obj.keys should not contain "$type"
    input(1).obj.keys should not contain "$type"
  }

  it should "generate span-create events with correct format" in {
    val event = SpanCreateEvent(
      id = testEventId,
      timestamp = testTimestamp,
      traceId = testTraceId,
      spanId = testSpanId,
      parentSpanId = Some("parent-span-789"),
      name = "Database Query",
      startTime = testTimestamp,
      metadata = Map("duration_ms" -> "1333", "query_type" -> "SELECT"),
      tags = Set("database"),
      input = Some(Map("sql" -> "SELECT * FROM users"))
    )

    val result = convertToLangfuseEvent(event)

    // Verify envelope
    result("type").str should be("span-create")

    // Verify body
    val body = result("body").obj
    body("id").str should be(testSpanId)
    body("traceId").str should be(testTraceId)
    body("parentObservationId").str should be("parent-span-789")
    body("name").str should be("Database Query")
    body("startTime").str should be("2024-01-15T10:30:45.123Z")

    // Verify metadata
    val metadata = body("metadata").obj
    metadata("duration_ms").str should be("1333")
    metadata("query_type").str should be("SELECT")
  }

  it should "generate span-update events with correct format" in {
    val event = SpanUpdateEvent(
      id = testEventId,
      timestamp = testTimestamp,
      traceId = testTraceId,
      spanId = testSpanId,
      endTime = Some(Instant.parse("2024-01-15T10:30:46.456Z")),
      metadata = Map("rowCount" -> "42"),
      tags = Set("completed"),
      input = Some(Map("sql" -> "SELECT * FROM users")),
      output = Some(Map("rowCount" -> 42)),
      status = Some(SpanStatus.Ok),
      error = None
    )

    val result = convertToLangfuseEvent(event)

    // Verify envelope
    result("type").str should be("span-update")

    // Verify body
    val body = result("body").obj
    body("id").str should be(testSpanId)
    // Allow for timestamp variations
    body("endTime").str should startWith("2024-01-15T10:30:46")
    body("level").str should be("DEFAULT")
    body("statusMessage") should be(ujson.Null)
  }

  it should "generate generation-create events with correct usage details format" in {
    val usage = TraceTokenUsage(
      promptTokens = 150,
      completionTokens = 50,
      totalTokens = 200
    )

    val input = Conversation(
      Seq(
        SystemMessage("You are a helpful assistant"),
        UserMessage("What is the capital of France?")
      )
    )

    val event = GenerationEvent(
      id = testEventId,
      timestamp = testTimestamp,
      traceId = testTraceId,
      spanId = Some(testSpanId),
      name = "GPT-4 Completion",
      startTime = testTimestamp,
      endTime = Some(Instant.parse("2024-01-15T10:30:50.789Z")),
      model = "gpt-4",
      modelParameters = Map(
        "temperature" -> "0.7",
        "max_tokens"  -> "500",
        "top_p"       -> "0.9"
      ),
      input = Some(input),
      output = Some("The capital of France is Paris."),
      usage = Some(usage),
      metadata = Map("request_id" -> "req-789")
    )

    val result = convertToLangfuseEvent(event)

    // Verify envelope
    result("type").str should be("generation-create")

    // Verify body
    val body = result("body").obj
    body("traceId").str should be(testTraceId)
    body("parentObservationId").str should be(testSpanId)
    body("name").str should be("GPT-4 Completion")
    body("startTime").str should be("2024-01-15T10:30:45.123Z")
    body("endTime").str should startWith("2024-01-15T10:30:50")
    body("model").str should be("gpt-4")

    // Verify model parameters
    val modelParams = body("modelParameters").obj
    modelParams("temperature").str should be("0.7")
    modelParams("max_tokens").str should be("500")
    modelParams("top_p").str should be("0.9")

    // Verify input messages format (critical fix)
    val inputMessages = body("input").arr
    inputMessages.length should be(2)
    inputMessages(0).obj("role").str should be("system")
    inputMessages(0).obj("content").str should be("You are a helpful assistant")
    inputMessages(1).obj("role").str should be("user")
    inputMessages(1).obj("content").str should be("What is the capital of France?")

    // Ensure no $type fields
    inputMessages(0).obj.keys should not contain "$type"
    inputMessages(1).obj.keys should not contain "$type"

    // Verify usage format (critical fix)
    val usageObj = body("usage").obj
    usageObj("input").num should be(150)
    usageObj("output").num should be(50)
    usageObj("total").num should be(200)

    // Verify metadata
    val metadata = body("metadata").obj
    metadata("request_id").str should be("req-789")
  }

  it should "generate tool call events with correct format" in {
    val event = ToolCallEvent(
      id = testEventId,
      timestamp = testTimestamp,
      traceId = testTraceId,
      spanId = Some(testSpanId),
      name = "weather-tool",
      startTime = testTimestamp,
      endTime = Some(Instant.parse("2024-01-15T10:30:46.000Z")),
      toolName = "get_weather",
      input = Some(Map("location" -> "Paris", "units" -> "metric")),
      output = Some(Map("temperature" -> "22°C", "condition" -> "sunny")),
      metadata = Map("provider" -> "openweather")
    )

    val result = convertToLangfuseEvent(event)

    // Verify envelope
    result("type").str should be("span-create")

    // Verify body
    val body = result("body").obj
    body("traceId").str should be(testTraceId)
    body("parentObservationId").str should be(testSpanId)
    body("name").str should be("Tool: get_weather")
    body("startTime").str should be("2024-01-15T10:30:45.123Z")
    body("endTime").str should startWith("2024-01-15T10:30:46")

    // Verify metadata includes tool name
    val metadata = body("metadata").obj
    metadata("toolName").str should be("get_weather")
    metadata("provider").str should be("openweather")
  }

  it should "generate event-create events with correct format" in {
    val event = SpanEventEvent(
      id = testEventId,
      timestamp = testTimestamp,
      traceId = testTraceId,
      spanId = testSpanId,
      eventName = "User Clicked Button",
      eventTime = testTimestamp,
      attributes = Map("buttonId" -> "submit-form", "userId" -> "user-123")
    )

    val result = convertToLangfuseEvent(event)

    // Verify envelope
    result("type").str should be("event-create")

    // Verify body
    val body = result("body").obj
    body("traceId").str should be(testTraceId)
    body("parentObservationId").str should be(testSpanId)
    body("name").str should be("User Clicked Button")
    body("startTime").str should be("2024-01-15T10:30:45.123Z")

    // Verify metadata (attributes)
    val metadata = body("metadata").obj
    metadata("buttonId").str should be("submit-form")
    metadata("userId").str should be("user-123")
  }

  it should "handle complex assistant messages with tool calls" in {
    val toolCall = ToolCall(
      id = "call_123",
      name = "get_weather",
      arguments = ujson.Obj("location" -> "Paris")
    )

    val assistantMessage = AssistantMessage(
      contentOpt = Some("I'll check the weather for you."),
      toolCalls = Seq(toolCall)
    )

    val conversation = Conversation(
      Seq(
        UserMessage("What's the weather in Paris?"),
        assistantMessage
      )
    )

    val event = TraceCreateEvent(
      id = testEventId,
      timestamp = testTimestamp,
      traceId = testTraceId,
      name = "Tool Calling Example",
      input = Some(conversation)
    )

    val result = convertToLangfuseEvent(event)
    val body   = result("body").obj
    val input  = body("input").arr

    // Verify user message
    input(0).obj("role").str should be("user")
    input(0).obj("content").str should be("What's the weather in Paris?")

    // Verify assistant message with tool calls
    val assistantMsg = input(1).obj
    assistantMsg("role").str should be("assistant")
    assistantMsg("content").str should be("I'll check the weather for you.")

    val toolCalls = assistantMsg("tool_calls").arr
    toolCalls.length should be(1)
    val toolCallJson = toolCalls(0).obj
    toolCallJson("id").str should be("call_123")
    toolCallJson("name").str should be("get_weather")

    // Ensure no $type fields anywhere
    input(0).obj.keys should not contain "$type"
    input(1).obj.keys should not contain "$type"
    toolCallJson.keys should not contain "$type"
  }

  it should "handle tool messages correctly" in {
    val toolMessage = ToolMessage(
      toolCallId = "call_123",
      content = """{"temperature": "22°C", "condition": "sunny"}"""
    )

    val conversation = Conversation(Seq(toolMessage))

    val event = TraceCreateEvent(
      id = testEventId,
      timestamp = testTimestamp,
      traceId = testTraceId,
      name = "Tool Response",
      input = Some(conversation)
    )

    val result = convertToLangfuseEvent(event)
    val body   = result("body").obj
    val input  = body("input").arr

    // Verify tool message format
    input(0).obj("role").str should be("tool")
    input(0).obj("tool_call_id").str should be("call_123")
    input(0).obj("content").str should be("""{"temperature": "22°C", "condition": "sunny"}""")
    input(0).obj.keys should not contain "$type"
  }

  it should "handle empty usage details correctly" in {
    val event = GenerationEvent(
      id = testEventId,
      timestamp = testTimestamp,
      traceId = testTraceId,
      spanId = None,
      name = "Simple Generation",
      startTime = testTimestamp,
      model = "gpt-3.5-turbo",
      usage = None
    )

    val result = convertToLangfuseEvent(event)
    val body   = result("body").obj

    // Should have null usage when no usage provided
    body("usage") should be(ujson.Null)
    body("parentObservationId") should be(ujson.Null)
  }

  it should "handle error status correctly" in {
    val error = new RuntimeException("API timeout")
    val event = SpanUpdateEvent(
      id = testEventId,
      timestamp = testTimestamp,
      traceId = testTraceId,
      spanId = testSpanId,
      status = Some(SpanStatus.Error),
      error = Some(error)
    )

    val result = convertToLangfuseEvent(event)
    val body   = result("body").obj

    body("level").str should be("ERROR")
    body("statusMessage").str should be("API timeout")
  }

  it should "ensure all timestamps are ISO 8601 formatted" in {
    val event = GenerationEvent(
      id = testEventId,
      timestamp = testTimestamp,
      traceId = testTraceId,
      spanId = None,
      name = "Timestamp Test",
      startTime = testTimestamp,
      endTime = Some(Instant.parse("2024-01-15T10:30:50.789Z")),
      model = "test-model"
    )

    val result = convertToLangfuseEvent(event)

    // Check envelope timestamp
    (result("timestamp").str should fullyMatch).regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z")

    // Check body timestamps
    val body = result("body").obj
    (body("startTime").str should fullyMatch).regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z")
    (body("endTime").str should fullyMatch).regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z")
  }

  it should "handle sequence of messages correctly" in {
    val messages = Seq(
      SystemMessage("You are helpful"),
      UserMessage("Hi"),
      AssistantMessage("Hello"),
      ToolMessage("tool_1", "result")
    )

    // Test the private convertToJson method directly
    val method = classOf[LangfuseTraceManager].getDeclaredMethod("convertToJson", classOf[Any])
    method.setAccessible(true)
    val result = method.invoke(traceManager, messages).asInstanceOf[ujson.Value]

    result.arr.length should be(4)

    // Verify each message type conversion
    result.arr(0).obj("role").str should be("system")
    result.arr(1).obj("role").str should be("user")
    result.arr(2).obj("role").str should be("assistant")
    result.arr(3).obj("role").str should be("tool")
    result.arr(3).obj("tool_call_id").str should be("tool_1")

    // Ensure no $type fields in any message
    result.arr.foreach(msg => msg.obj.keys should not contain "$type")
  }
}
