package org.llm4s.trace

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.llm4s.trace.{ TokenUsage => TraceTokenUsage }
import java.time.Instant

/**
 * Tests for the enhanced Langfuse features including cost tracking,
 * scores, generation updates, and improved field support.
 */
class LangfuseEnhancedFeaturesTest extends AnyFlatSpec with Matchers {

  private val traceManager = LangfuseTraceManager.create()

  // Access the private convertToLangfuseEvent method
  private def convertToLangfuseEvent(event: TraceEvent): ujson.Obj = {
    val method = classOf[LangfuseTraceManager].getDeclaredMethod("convertToLangfuseEvent", classOf[TraceEvent])
    method.setAccessible(true)
    method.invoke(traceManager, event).asInstanceOf[ujson.Obj]
  }

  "LangfuseTraceManager" should "handle TokenUsage with cost tracking fields" in {
    val usage = TraceTokenUsage(
      promptTokens = 100,
      completionTokens = 50,
      totalTokens = 150,
      unit = Some("TOKENS"),
      inputCost = Some(0.003),
      outputCost = Some(0.006),
      totalCost = Some(0.009)
    )

    val event = GenerationEvent(
      id = "test-event",
      timestamp = Instant.parse("2024-01-15T10:30:45.123Z"),
      traceId = "trace-123",
      spanId = None,
      name = "Cost Test",
      startTime = Instant.parse("2024-01-15T10:30:45.123Z"),
      model = "gpt-4",
      usage = Some(usage)
    )

    val result   = convertToLangfuseEvent(event)
    val body     = result("body").obj
    val usageObj = body("usage").obj

    // Verify all cost tracking fields are present
    usageObj("input").num should be(100)
    usageObj("output").num should be(50)
    usageObj("total").num should be(150)
    usageObj("unit").str should be("TOKENS")
    usageObj("input_cost").num should be(0.003)
    usageObj("output_cost").num should be(0.006)
    usageObj("total_cost").num should be(0.009)
  }

  it should "handle TokenUsage with optional cost fields missing" in {
    val usage = TraceTokenUsage(
      promptTokens = 75,
      completionTokens = 25,
      totalTokens = 100
      // No optional fields provided
    )

    val event = GenerationEvent(
      id = "test-event",
      timestamp = Instant.parse("2024-01-15T10:30:45.123Z"),
      traceId = "trace-123",
      spanId = None,
      name = "Minimal Usage Test",
      startTime = Instant.parse("2024-01-15T10:30:45.123Z"),
      model = "gpt-3.5-turbo",
      usage = Some(usage)
    )

    val result   = convertToLangfuseEvent(event)
    val body     = result("body").obj
    val usageObj = body("usage").obj

    // Verify basic fields
    usageObj("input").num should be(75)
    usageObj("output").num should be(25)
    usageObj("total").num should be(100)
    usageObj("unit").str should be("TOKENS") // Default value

    // Verify optional fields are null
    usageObj("input_cost") should be(ujson.Null)
    usageObj("output_cost") should be(ujson.Null)
    usageObj("total_cost") should be(ujson.Null)
  }

  it should "generate score-create events correctly" in {
    val event = ScoreEvent(
      id = "score-event-123",
      timestamp = Instant.parse("2024-01-15T10:30:45.123Z"),
      traceId = "trace-123",
      observationId = Some("gen-456"),
      name = "relevance",
      value = 0.85,
      source = "annotation",
      comment = Some("High relevance score"),
      metadata = Map("evaluator" -> "human", "criteria" -> "accuracy")
    )

    val result = convertToLangfuseEvent(event)
    val body   = result("body").obj

    // Verify envelope
    result("type").str should be("score-create")
    result("id").str should be("score-event-123")

    // Verify body structure
    body("id").str should startWith("trace-123_score_")
    body("traceId").str should be("trace-123")
    body("observationId").str should be("gen-456")
    body("name").str should be("relevance")
    body("value").num should be(0.85)
    body("source").str should be("annotation")
    body("comment").str should be("High relevance score")

    // Verify metadata
    val metadata = body("metadata").obj
    metadata("evaluator").str should be("human")
    metadata("criteria").str should be("accuracy")
  }

  it should "generate generation-update events correctly" in {
    val usage = TraceTokenUsage(
      promptTokens = 200,
      completionTokens = 100,
      totalTokens = 300,
      totalCost = Some(0.015)
    )

    val event = GenerationUpdateEvent(
      id = "update-event-123",
      timestamp = Instant.parse("2024-01-15T10:35:45.123Z"),
      traceId = "trace-123",
      generationId = "gen-456",
      endTime = Some(Instant.parse("2024-01-15T10:35:00.000Z")),
      output = Some("Updated completion response"),
      usage = Some(usage),
      metadata = Map("updated_by" -> "system", "reason" -> "correction")
    )

    val result = convertToLangfuseEvent(event)
    val body   = result("body").obj

    // Verify envelope
    result("type").str should be("generation-update")
    result("id").str should be("update-event-123")

    // Verify body structure
    body("id").str should be("gen-456") // Uses the provided generationId
    body("endTime").str should be("2024-01-15T10:35:00Z")
    body("output").str should be("Updated completion response")

    // Verify updated usage
    val usageObj = body("usage").obj
    usageObj("input").num should be(200)
    usageObj("output").num should be(100)
    usageObj("total").num should be(300)
    usageObj("total_cost").num should be(0.015)
  }

  it should "include promptName, level, and statusMessage in GenerationEvent" in {
    val event = GenerationEvent(
      id = "test-event",
      timestamp = Instant.parse("2024-01-15T10:30:45.123Z"),
      traceId = "trace-123",
      spanId = None,
      name = "Enhanced Generation",
      startTime = Instant.parse("2024-01-15T10:30:45.123Z"),
      model = "gpt-4",
      promptName = Some("qa-prompt-v2"),
      level = Some("INFO"),
      statusMessage = Some("Completed successfully")
    )

    val result = convertToLangfuseEvent(event)
    val body   = result("body").obj

    // Verify new fields
    body("promptName").str should be("qa-prompt-v2")
    body("level").str should be("INFO")
    body("statusMessage").str should be("Completed successfully")
  }

  it should "use default level when not provided in GenerationEvent" in {
    val event = GenerationEvent(
      id = "test-event",
      timestamp = Instant.parse("2024-01-15T10:30:45.123Z"),
      traceId = "trace-123",
      spanId = None,
      name = "Default Level Test",
      startTime = Instant.parse("2024-01-15T10:30:45.123Z"),
      model = "gpt-4"
      // level not provided
    )

    val result = convertToLangfuseEvent(event)
    val body   = result("body").obj

    // Should use default level
    body("level").str should be("DEFAULT")
    body("promptName") should be(ujson.Null)
    body("statusMessage") should be(ujson.Null)
  }

  it should "generate deterministic IDs for update capability" in {
    // Test multiple events with same name to verify deterministic ID generation
    val event1 = GenerationEvent(
      id = "event-1",
      timestamp = Instant.parse("2024-01-15T10:30:45.123Z"),
      traceId = "trace-123",
      spanId = None,
      name = "consistent-operation",
      startTime = Instant.parse("2024-01-15T10:30:45.123Z"),
      model = "test-model"
    )

    val event2 = GenerationEvent(
      id = "event-2",
      timestamp = Instant.parse("2024-01-15T10:31:45.123Z"),
      traceId = "trace-123",
      spanId = None,
      name = "consistent-operation",
      startTime = Instant.parse("2024-01-15T10:31:45.123Z"),
      model = "test-model"
    )

    val result1 = convertToLangfuseEvent(event1)
    val result2 = convertToLangfuseEvent(event2)

    val body1 = result1("body").obj
    val body2 = result2("body").obj

    // IDs should be deterministic based on name
    body1("id").str should be(body2("id").str)
    body1("id").str should be("trace-123_gen_consistent_operation")
  }

  it should "include release and version in trace creation" in {
    val event = TraceCreateEvent(
      id = "test-event",
      timestamp = Instant.parse("2024-01-15T10:30:45.123Z"),
      traceId = "trace-123",
      name = "Release Test"
    )

    val result = convertToLangfuseEvent(event)
    val body   = result("body").obj

    // Verify release and version fields are included
    body.obj.keys should contain("release")
    body.obj.keys should contain("version")

    // Values should come from LangfuseConfig defaults
    body("release").str should be("1.0.0")
    body("version").str should be("1.0.0")
  }

  it should "handle score events with minimal fields" in {
    val event = ScoreEvent(
      id = "minimal-score",
      timestamp = Instant.parse("2024-01-15T10:30:45.123Z"),
      traceId = "trace-123",
      name = "quality",
      value = 0.7
      // observationId, comment, metadata not provided
    )

    val result = convertToLangfuseEvent(event)
    val body   = result("body").obj

    // Verify required fields
    body("traceId").str should be("trace-123")
    body("name").str should be("quality")
    body("value").num should be(0.7)
    body("source").str should be("annotation") // Default value

    // Verify optional fields are null
    body("observationId") should be(ujson.Null)
    body("comment") should be(ujson.Null)

    // Verify empty metadata is empty object
    body("metadata").obj.keys should be(empty)
  }

  it should "handle tool call events with deterministic IDs" in {
    val event = ToolCallEvent(
      id = "tool-event",
      timestamp = Instant.parse("2024-01-15T10:30:45.123Z"),
      traceId = "trace-123",
      spanId = Some("span-456"),
      name = "Tool Execution",
      startTime = Instant.parse("2024-01-15T10:30:45.123Z"),
      toolName = "get_weather",
      input = Some(ujson.Obj("location" -> "Paris")),
      output = Some(ujson.Obj("temperature" -> 22))
    )

    val result = convertToLangfuseEvent(event)
    val body   = result("body").obj

    // Verify deterministic ID based on tool name
    body("id").str should be("trace-123_tool_get_weather")
    body("name").str should be("Tool: get_weather")

    // Verify tool name is in metadata
    val metadata = body("metadata").obj
    metadata("toolName").str should be("get_weather")
  }

  it should "extract usage from Completion objects in span updates" in {
    import org.llm4s.llmconnect.model._

    val completion = Completion(
      id = "chatcmpl-123",
      created = 1753028530L,
      message = AssistantMessage("Test response"),
      model = "gpt-4",
      usage = Some(
        org.llm4s.llmconnect.model.TokenUsage(
          promptTokens = 50,
          completionTokens = 75,
          totalTokens = 125
        )
      )
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

    // Verify usage was extracted to span level
    body.obj.keys should contain("usage")
    val usage = body("usage").obj
    usage("input").num should be(50)
    usage("output").num should be(75)
    usage("total").num should be(125)
    usage("unit").str should be("TOKENS")

    // Verify output contains just the content string (Langfuse format)
    body("output").str should be("Test response")

    // Verify completion metadata is in metadata field
    val metadata = body("metadata").obj
    metadata("completion_id").str should be("chatcmpl-123")
    metadata("created").str should be("1753028530")
    metadata("model").str should be("gpt-4")
    metadata("prompt_tokens").str should be("50")
    metadata("completion_tokens").str should be("75")
    metadata("total_tokens").str should be("125")
  }
}
