package org.llm4s.samples.trace

import org.llm4s.llmconnect.model._
import org.llm4s.llmconnect.model.{TokenUsage => ModelTokenUsage}
import org.llm4s.trace._
import org.llm4s.trace.{TokenUsage => TraceTokenUsage}
import java.time.Instant

/**
 * Direct test of LangfuseTraceManager generation events.
 * This bypasses TracingFactory to test the raw event generation.
 */
object DirectLangfuseGenerationTest {
  def main(args: Array[String]): Unit = {
    // Create a custom PrintTraceManager that outputs Langfuse-style events
    val config = TraceManagerConfig()
    val langfuseConfig = LangfuseConfig()
    
    // Create a LangfuseTraceManager instance
    val manager = new LangfuseTraceManager(config, langfuseConfig) {
      // Override to print events instead of sending to API
      override protected def emitEventImpl(event: TraceEvent): Unit = {
        val langfuseEvent = convertToLangfuseEvent(event)
        println(s"\n=== LANGFUSE EVENT ===")
        println(ujson.write(langfuseEvent, indent = 2))
        println("===================\n")
      }
      
      // Make the private method accessible for our override
      private def convertToLangfuseEvent(event: TraceEvent): ujson.Obj = {
        val method = classOf[LangfuseTraceManager].getDeclaredMethod("convertToLangfuseEvent", classOf[TraceEvent])
        method.setAccessible(true)
        method.invoke(this, event).asInstanceOf[ujson.Obj]
      }
    }
    
    println("Starting direct Langfuse generation test...\n")
    
    // Create a trace
    val trace = manager.createTrace(
      name = "direct-generation-test",
      metadata = Map("test" -> "direct")
    )
    
    // Test 1: Minimal generation
    println("Test 1: Minimal generation")
    trace match {
      case langfuseTrace: LangfuseTrace =>
        langfuseTrace.recordGeneration(
          name = "Minimal Gen",
          model = "gpt-4",
          startTime = Instant.now(),
          endTime = Some(Instant.now().plusMillis(100))
        )
        
        Thread.sleep(100)
        
        // Test 2: Generation with Conversation and Completion
        println("\nTest 2: Generation with Conversation and Completion")
        
        val conversation = Conversation(Seq(
          UserMessage("What is 2+2?")
        ))
        
        val completion = Completion(
          id = "test-completion-123",
          created = System.currentTimeMillis() / 1000,
          message = AssistantMessage("2+2 equals 4."),
          model = "gpt-3.5-turbo",
          usage = Some(ModelTokenUsage(15, 10, 25))
        )
        
        langfuseTrace.recordGeneration(
          name = "Math Completion",
          model = completion.model,
          startTime = Instant.now().minusMillis(200),
          endTime = Some(Instant.now()),
          modelParameters = Map(
            "temperature" -> "0.0",
            "max_tokens" -> "50"
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
            "completion_id" -> completion.id,
            "question_type" -> "arithmetic"
          )
        )
        
        Thread.sleep(100)
        
        // Test 3: Generation with tool calls
        println("\nTest 3: Generation with tool calls")
        
        val toolCall = ToolCall(
          id = "call_123",
          name = "calculator",
          arguments = ujson.Obj("operation" -> "add", "a" -> 2, "b" -> 2)
        )
        
        val toolCompletion = Completion(
          id = "test-tool-completion",
          created = System.currentTimeMillis() / 1000,
          message = AssistantMessage(
            contentOpt = Some("I'll calculate that for you."),
            toolCalls = Seq(toolCall)
          ),
          model = "gpt-4-turbo",
          usage = Some(ModelTokenUsage(30, 20, 50))
        )
        
        langfuseTrace.recordGeneration(
          name = "Tool Completion",
          model = toolCompletion.model,
          startTime = Instant.now().minusMillis(150),
          endTime = Some(Instant.now()),
          input = Some(Conversation(Seq(UserMessage("Calculate 2+2 using the calculator tool")))),
          output = Some(toolCompletion),
          usage = toolCompletion.usage.map(u => 
            TraceTokenUsage(u.promptTokens, u.completionTokens, u.totalTokens)
          ),
          metadata = Map(
            "has_tool_calls" -> "true",
            "tool_count" -> "1",
            "tools_used" -> toolCall.name
          )
        )
        
        Thread.sleep(100)
        
        // Test 4: Complex conversation
        println("\nTest 4: Complex conversation")
        
        val complexConversation = Conversation(Seq(
          SystemMessage("You are a helpful math tutor."),
          UserMessage("Can you explain what prime numbers are?"),
          AssistantMessage("Prime numbers are natural numbers greater than 1 that have no positive divisors other than 1 and themselves."),
          UserMessage("Is 17 a prime number?")
        ))
        
        val complexCompletion = Completion(
          id = "complex-123",
          created = System.currentTimeMillis() / 1000,
          message = AssistantMessage("Yes, 17 is a prime number. It can only be divided evenly by 1 and 17."),
          model = "claude-3-5-sonnet-20241022",
          usage = Some(ModelTokenUsage(85, 25, 110))
        )
        
        langfuseTrace.recordGeneration(
          name = "Complex Conversation",
          model = complexCompletion.model,
          startTime = Instant.now().minusMillis(300),
          endTime = Some(Instant.now()),
          modelParameters = Map(
            "temperature" -> "0.7",
            "max_tokens" -> "200",
            "top_p" -> "0.95"
          ),
          input = Some(complexConversation),
          output = Some(complexCompletion),
          usage = complexCompletion.usage.map(u => 
            TraceTokenUsage(
              promptTokens = u.promptTokens,
              completionTokens = u.completionTokens,
              totalTokens = u.totalTokens,
              inputCost = Some(0.00085),
              outputCost = Some(0.00025),
              totalCost = Some(0.00110)
            )
          ),
          metadata = Map(
            "topic" -> "mathematics",
            "subtopic" -> "prime_numbers",
            "message_count" -> complexConversation.messages.size.toString
          )
        )
        
      case _ =>
        println("ERROR: Not a LangfuseTrace")
    }
    
    // Finish the trace
    trace.finish()
    
    println("\nDirect test completed. Review the JSON output above.")
    
    // Shutdown
    manager.shutdown()
  }
}