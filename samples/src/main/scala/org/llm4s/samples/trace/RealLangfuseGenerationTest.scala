package org.llm4s.samples.trace

import org.llm4s.llmconnect.model._
import org.llm4s.llmconnect.model.{TokenUsage => ModelTokenUsage}
import org.llm4s.trace._
import org.llm4s.trace.{TokenUsage => TraceTokenUsage}
import java.time.Instant

/**
 * Real test that sends generation events to configured Langfuse server.
 * Make sure LANGFUSE_PUBLIC_KEY, LANGFUSE_SECRET_KEY, and LANGFUSE_HOST are configured.
 */
object RealLangfuseGenerationTest {
  def main(args: Array[String]): Unit = {
    // Check if Langfuse is configured
    val langfuseConfig = LangfuseConfig()
    if (!langfuseConfig.isValid) {
      println("ERROR: Langfuse is not configured. Please set the following environment variables:")
      println("  - LANGFUSE_PUBLIC_KEY")
      println("  - LANGFUSE_SECRET_KEY")
      println("  - LANGFUSE_HOST (optional, defaults to https://cloud.langfuse.com)")
      System.exit(1)
    }
    
    println("Starting real Langfuse generation test...")
    println(s"Sending to: ${langfuseConfig.host}")
    println()
    
    // Create trace manager with actual Langfuse configuration
    val traceManager = new LangfuseTraceManager(TraceManagerConfig(), langfuseConfig)
    
    // Create a trace
    val trace = traceManager.createTrace(
      name = "real-generation-test",
      metadata = Map(
        "test_type" -> "real_api",
        "timestamp" -> Instant.now().toString
      )
    )
    
    println(s"Created trace: ${trace.traceId}")
    
    trace match {
      case langfuseTrace: LangfuseTrace =>
        // Test 1: Simple completion
        println("\nTest 1: Simple text completion")
        val startTime1 = Instant.now()
        Thread.sleep(100) // Simulate processing time
        
        langfuseTrace.recordGeneration(
          name = "Simple Text Generation",
          model = "gpt-4o-mini",
          startTime = startTime1,
          endTime = Some(Instant.now()),
          modelParameters = Map(
            "temperature" -> "0.7",
            "max_tokens" -> "100"
          ),
          input = Some("What is the capital of France?"),
          output = Some("The capital of France is Paris."),
          usage = Some(TraceTokenUsage(
            promptTokens = 10,
            completionTokens = 8,
            totalTokens = 18,
            unit = Some("TOKENS")
          )),
          metadata = Map(
            "test_number" -> "1",
            "question_type" -> "geography"
          )
        )
        println("✓ Sent simple text generation event")
        
        Thread.sleep(500) // Give time for API to process
        
        // Test 2: Conversation with assistant response
        println("\nTest 2: Conversation completion")
        val conversation = Conversation(Seq(
          SystemMessage("You are a helpful assistant."),
          UserMessage("What is 2+2?")
        ))
        
        val completion = Completion(
          id = "chatcmpl-test-123",
          created = System.currentTimeMillis() / 1000,
          message = AssistantMessage("2 + 2 equals 4."),
          model = "gpt-3.5-turbo",
          usage = Some(ModelTokenUsage(
            promptTokens = 20,
            completionTokens = 6,
            totalTokens = 26
          ))
        )
        
        val startTime2 = Instant.now()
        Thread.sleep(150)
        
        langfuseTrace.recordGeneration(
          name = "Math Completion",
          model = completion.model,
          startTime = startTime2,
          endTime = Some(Instant.now()),
          modelParameters = Map(
            "temperature" -> "0.0",
            "seed" -> "42"
          ),
          input = Some(conversation),
          output = Some(completion),
          usage = completion.usage.map(u => 
            TraceTokenUsage(
              promptTokens = u.promptTokens,
              completionTokens = u.completionTokens,
              totalTokens = u.totalTokens,
              unit = Some("TOKENS")
            )
          ),
          metadata = Map(
            "test_number" -> "2",
            "completion_id" -> completion.id,
            "has_system_message" -> "true"
          )
        )
        println("✓ Sent conversation completion event")
        
        Thread.sleep(500)
        
        // Test 3: Tool calling completion
        println("\nTest 3: Tool calling completion")
        val toolCall = ToolCall(
          id = "call_weather_123",
          name = "get_weather",
          arguments = ujson.Obj(
            "location" -> "San Francisco",
            "units" -> "celsius"
          )
        )
        
        val toolCompletion = Completion(
          id = "chatcmpl-tool-456",
          created = System.currentTimeMillis() / 1000,
          message = AssistantMessage(
            contentOpt = Some("I'll check the weather for you."),
            toolCalls = Seq(toolCall)
          ),
          model = "gpt-4-turbo",
          usage = Some(ModelTokenUsage(45, 15, 60))
        )
        
        val startTime3 = Instant.now()
        Thread.sleep(200)
        
        langfuseTrace.recordGeneration(
          name = "Weather Tool Call",
          model = toolCompletion.model,
          startTime = startTime3,
          endTime = Some(Instant.now()),
          modelParameters = Map(
            "temperature" -> "0.5",
            "tool_choice" -> "auto",
            "tools" -> "[get_weather, search_web]"
          ),
          input = Some(Conversation(Seq(
            UserMessage("What's the weather in San Francisco?")
          ))),
          output = Some(toolCompletion),
          usage = toolCompletion.usage.map(u => 
            TraceTokenUsage(u.promptTokens, u.completionTokens, u.totalTokens)
          ),
          metadata = Map(
            "test_number" -> "3",
            "has_tool_calls" -> "true",
            "tool_count" -> toolCompletion.message.toolCalls.size.toString,
            "tools_called" -> toolCompletion.message.toolCalls.map(_.name).mkString(",")
          )
        )
        println("✓ Sent tool calling generation event")
        
        // Also record the tool execution
        Thread.sleep(100)
        langfuseTrace.recordToolCall(
          name = "Weather API Call",
          toolName = toolCall.name,
          startTime = Instant.now(),
          endTime = Some(Instant.now().plusMillis(50)),
          input = Some(toolCall.arguments),
          output = Some(ujson.Obj(
            "temperature" -> 18,
            "conditions" -> "Foggy",
            "humidity" -> 75
          )),
          metadata = Map(
            "api_version" -> "v2",
            "cache_hit" -> "false"
          )
        )
        println("✓ Sent tool execution event")
        
        Thread.sleep(500)
        
        // Test 4: Complex multi-turn conversation
        println("\nTest 4: Multi-turn conversation")
        val complexConversation = Conversation(Seq(
          SystemMessage("You are an expert on prime numbers."),
          UserMessage("What are prime numbers?"),
          AssistantMessage("Prime numbers are natural numbers greater than 1 that have no positive divisors other than 1 and themselves."),
          UserMessage("Is 91 a prime number?"),
          AssistantMessage("No, 91 is not a prime number. It can be divided by 7 and 13 (7 × 13 = 91)."),
          UserMessage("What about 97?")
        ))
        
        val finalCompletion = Completion(
          id = "chatcmpl-final-789",
          created = System.currentTimeMillis() / 1000,
          message = AssistantMessage("Yes, 97 is a prime number. It cannot be divided evenly by any number except 1 and 97 itself."),
          model = "claude-3-5-sonnet-20241022",
          usage = Some(ModelTokenUsage(150, 30, 180))
        )
        
        val startTime4 = Instant.now()
        Thread.sleep(300)
        
        langfuseTrace.recordGeneration(
          name = "Multi-turn Prime Discussion",
          model = finalCompletion.model,
          startTime = startTime4,
          endTime = Some(Instant.now()),
          modelParameters = Map(
            "temperature" -> "0.7",
            "max_tokens" -> "200",
            "top_p" -> "0.95",
            "frequency_penalty" -> "0.0",
            "presence_penalty" -> "0.0"
          ),
          input = Some(complexConversation),
          output = Some(finalCompletion),
          usage = finalCompletion.usage.map(u => 
            TraceTokenUsage(
              promptTokens = u.promptTokens,
              completionTokens = u.completionTokens,
              totalTokens = u.totalTokens,
              unit = Some("TOKENS"),
              inputCost = Some(0.0015),
              outputCost = Some(0.0006),
              totalCost = Some(0.0021)
            )
          ),
          metadata = Map(
            "test_number" -> "4",
            "conversation_turns" -> (complexConversation.messages.size / 2).toString,
            "topic" -> "mathematics",
            "subtopic" -> "prime_numbers"
          )
        )
        println("✓ Sent multi-turn conversation event")
        
        Thread.sleep(500)
        
        // Test 5: Error case
        println("\nTest 5: Error generation")
        val errorStart = Instant.now()
        Thread.sleep(50)
        
        langfuseTrace.recordGeneration(
          name = "Failed Generation",
          model = "gpt-4",
          startTime = errorStart,
          endTime = Some(Instant.now()),
          input = Some("Generate a very long story"),
          metadata = Map(
            "test_number" -> "5",
            "error" -> "Context length exceeded",
            "error_code" -> "context_length_exceeded",
            "requested_tokens" -> "10000",
            "max_tokens" -> "8192"
          )
        )
        println("✓ Sent error generation event")
        
        // Create a span to show hierarchy
        println("\nTest 6: Generation within a span")
        val span = langfuseTrace.span("process-request") { span =>
          span.addMetadata("request_type", "complex_analysis")
          
          val genStart = Instant.now()
          Thread.sleep(100)
          
          // Record generation within the span
          langfuseTrace.recordGeneration(
            name = "Nested Generation",
            model = "gpt-4o",
            startTime = genStart,
            endTime = Some(Instant.now()),
            modelParameters = Map("temperature" -> "0.8"),
            input = Some("Analyze this text"),
            output = Some("This is the analysis result."),
            usage = Some(TraceTokenUsage(50, 25, 75)),
            metadata = Map("nested" -> "true"),
            spanId = Some(span.spanId) // Link to parent span
          )
          
          span.setOutput("Analysis complete")
        }
        println("✓ Sent nested generation within span")
        
      case _ =>
        println("ERROR: Not a LangfuseTrace - this shouldn't happen")
    }
    
    // Finish the trace
    println("\nFinishing trace...")
    trace.finish()
    
    // Give time for all events to be sent
    println("Waiting for events to be sent to Langfuse...")
    Thread.sleep(3000)
    
    // Shutdown the trace manager
    traceManager.shutdown()
    
    println("\nTest completed successfully!")
    println(s"Check your Langfuse dashboard at: ${langfuseConfig.host}")
    println(s"Look for trace: ${trace.traceId}")
  }
}