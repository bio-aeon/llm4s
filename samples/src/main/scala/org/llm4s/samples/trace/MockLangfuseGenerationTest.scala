package org.llm4s.samples.trace

import org.llm4s.llmconnect.model._
import org.llm4s.trace.{TracingFactory, LangfuseTrace}
import java.time.Instant

/**
 * Mock test for Langfuse generation events without actual LLM calls.
 * This allows us to test the trace generation format in isolation.
 */
object MockLangfuseGenerationTest {
  def main(args: Array[String]): Unit = {
    // Ensure we're using Langfuse tracing
    System.setProperty("TRACING_MODE", "langfuse")
    
    val traceManager = TracingFactory.create()
    
    // Create a trace for the entire conversation
    val trace = traceManager.createTrace(
      name = "mock-llm-conversation",
      metadata = Map(
        "sample" -> "MockLangfuseGenerationTest",
        "test_type" -> "mock"
      )
    )

    println("Starting mock Langfuse generation test...")
    
    // Create a mock conversation
    val conversation = Conversation(
      Seq(
        SystemMessage("You are a helpful assistant."),
        UserMessage("What is the weather in San Francisco?")
      )
    )

    // Test 1: Simple completion without tools
    trace match {
      case langfuseTrace: LangfuseTrace =>
        println("\nTest 1: Simple completion without tools")
        
        val startTime1 = Instant.now()
        Thread.sleep(500) // Simulate LLM latency
        
        // Create mock completion
        val completion1 = Completion(
          id = "chatcmpl-mock-001",
          created = System.currentTimeMillis() / 1000,
          message = AssistantMessage("I don't have access to real-time weather data, but I can help you find weather information for San Francisco."),
          model = "gpt-4o-mini",
          usage = Some(TokenUsage(
            promptTokens = 25,
            completionTokens = 18,
            totalTokens = 43
          ))
        )
        
        // Record generation event
        langfuseTrace.recordGeneration(
          name = "Mock LLM Completion",
          model = completion1.model,
          startTime = startTime1,
          endTime = Some(Instant.now()),
          modelParameters = Map(
            "temperature" -> "0.7",
            "max_tokens" -> "150"
          ),
          input = Some(conversation),
          output = Some(completion1),
          usage = completion1.usage.map(u => 
            org.llm4s.trace.TokenUsage(u.promptTokens, u.completionTokens, u.totalTokens)
          ),
          metadata = Map(
            "completion_id" -> completion1.id,
            "created" -> completion1.created.toString,
            "has_tool_calls" -> "false"
          )
        )
        
        println(s"Recorded generation event for completion: ${completion1.id}")
        
        // Test 2: Completion with tool calls
        println("\nTest 2: Completion with tool calls")
        
        val conversationWithTools = Conversation(
          conversation.messages :+ completion1.message :+ 
          UserMessage("Actually, can you check the weather for me?")
        )
        
        val startTime2 = Instant.now()
        Thread.sleep(300) // Simulate LLM latency
        
        // Create mock completion with tool calls
        val toolCall = ToolCall(
          id = "call_mock_weather_001",
          name = "get_weather",
          arguments = ujson.Obj(
            "location" -> "San Francisco",
            "units" -> "fahrenheit"
          )
        )
        
        val completion2 = Completion(
          id = "chatcmpl-mock-002",
          created = System.currentTimeMillis() / 1000,
          message = AssistantMessage(
            contentOpt = None,
            toolCalls = Seq(toolCall)
          ),
          model = "gpt-4o",
          usage = Some(TokenUsage(
            promptTokens = 85,
            completionTokens = 15,
            totalTokens = 100
          ))
        )
        
        // Record generation event with tool calls
        langfuseTrace.recordGeneration(
          name = "Mock LLM Completion with Tools",
          model = completion2.model,
          startTime = startTime2,
          endTime = Some(Instant.now()),
          modelParameters = Map(
            "temperature" -> "0.5",
            "tool_choice" -> "auto"
          ),
          input = Some(conversationWithTools),
          output = Some(completion2),
          usage = completion2.usage.map(u => 
            org.llm4s.trace.TokenUsage(u.promptTokens, u.completionTokens, u.totalTokens)
          ),
          metadata = Map(
            "completion_id" -> completion2.id,
            "created" -> completion2.created.toString,
            "has_tool_calls" -> "true",
            "tools_available" -> "get_weather,search_web",
            "tools_called" -> toolCall.name
          )
        )
        
        println(s"Recorded generation event with tool call: ${toolCall.name}")
        
        // Simulate tool execution
        Thread.sleep(200)
        langfuseTrace.recordToolCall(
          name = s"Tool: ${toolCall.name}",
          toolName = toolCall.name,
          startTime = Instant.now(),
          endTime = Some(Instant.now().plusMillis(200)),
          input = Some(toolCall.arguments),
          output = Some(ujson.Obj(
            "temperature" -> 68,
            "conditions" -> "Partly cloudy",
            "humidity" -> 65
          )),
          metadata = Map(
            "tool_id" -> toolCall.id,
            "location" -> "San Francisco"
          )
        )
        
        println(s"Recorded tool call execution for: ${toolCall.name}")
        
        // Test 3: Final response after tool execution
        println("\nTest 3: Final response after tool execution")
        
        val finalConversation = Conversation(
          conversationWithTools.messages ++ Seq(
            completion2.message,
            ToolMessage(toolCall.id, """{"temperature": 68, "conditions": "Partly cloudy", "humidity": 65}""")
          )
        )
        
        val startTime3 = Instant.now()
        Thread.sleep(400) // Simulate LLM latency
        
        val completion3 = Completion(
          id = "chatcmpl-mock-003",
          created = System.currentTimeMillis() / 1000,
          message = AssistantMessage("The current weather in San Francisco is 68°F with partly cloudy conditions and 65% humidity."),
          model = "gpt-4o",
          usage = Some(TokenUsage(
            promptTokens = 120,
            completionTokens = 22,
            totalTokens = 142
          ))
        )
        
        // Record final generation
        langfuseTrace.recordGeneration(
          name = "Mock Final Response",
          model = completion3.model,
          startTime = startTime3,
          endTime = Some(Instant.now()),
          modelParameters = Map(
            "temperature" -> "0.7"
          ),
          input = Some(finalConversation),
          output = Some(completion3),
          usage = completion3.usage.map(u => 
            org.llm4s.trace.TokenUsage(u.promptTokens, u.completionTokens, u.totalTokens)
          ),
          metadata = Map(
            "completion_id" -> completion3.id,
            "message_count" -> finalConversation.messages.size.toString,
            "final_response" -> "true"
          )
        )
        
        println(s"Recorded final generation event: ${completion3.id}")
        
        // Test 4: Error case
        println("\nTest 4: Error case")
        
        val errorStartTime = Instant.now()
        Thread.sleep(100)
        
        langfuseTrace.recordGeneration(
          name = "Mock Error Generation",
          model = "gpt-4",
          startTime = errorStartTime,
          endTime = Some(Instant.now()),
          input = Some("Invalid request"),
          metadata = Map(
            "error" -> "Rate limit exceeded",
            "error_type" -> "RateLimitError"
          )
        )
        
        println("Recorded error generation event")
        
      case _ =>
        println("ERROR: Not using LangfuseTrace - check TRACING_MODE environment variable")
    }
    
    // Finish the trace to ensure all events are sent
    println("\nFinishing trace...")
    trace.finish()
    
    // Give time for events to be sent
    Thread.sleep(2000)
    
    println("\nMock test completed. Check Langfuse dashboard for results.")
  }
}