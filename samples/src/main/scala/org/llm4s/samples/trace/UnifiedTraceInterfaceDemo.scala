package org.llm4s.samples.trace

import org.llm4s.llmconnect.model._
import org.llm4s.llmconnect.model.{TokenUsage => ModelTokenUsage}
import org.llm4s.trace._
import org.llm4s.trace.{TokenUsage => TraceTokenUsage}
import java.time.Instant

/**
 * Demonstrates the unified trace interface working across different trace implementations.
 * Shows that the same code works with Langfuse, Print, and NoOp trace managers.
 */
object UnifiedTraceInterfaceDemo {
  def main(args: Array[String]): Unit = {
    println("=== Unified Trace Interface Demo ===\n")
    
    // Test with different trace managers
    val traceManagers = Seq(
      ("Print", TracingFactory.createPrintTraceManager()),
      ("NoOp", NoOpTraceManager),
      ("Langfuse (if configured)", TracingFactory.createTraceManager("langfuse"))
    )
    
    traceManagers.foreach { case (name, manager) =>
      println(s"\n--- Testing with $name TraceManager ---")
      testWithTraceManager(manager)
      Thread.sleep(500) // Give time for output
    }
  }
  
  private def testWithTraceManager(traceManager: TraceManager): Unit = {
    // Create a trace
    val trace = traceManager.createTrace(
      name = "unified-trace-demo",
      metadata = Map("demo" -> "unified-interface")
    )
    
    // Simulate an LLM call with generation tracking
    val startTime = Instant.now()
    val conversation = Conversation(Seq(
      SystemMessage("You are a helpful assistant."),
      UserMessage("What is the capital of France?")
    ))
    
    Thread.sleep(100) // Simulate processing
    
    val completion = Completion(
      id = "demo-completion-123",
      created = System.currentTimeMillis() / 1000,
      message = AssistantMessage("The capital of France is Paris."),
      model = "gpt-4o-mini",
      usage = Some(ModelTokenUsage(25, 10, 35))
    )
    
    // Record generation using unified interface
    // This works regardless of whether it's Langfuse, Print, or NoOp
    trace.recordGeneration(
      name = "Demo LLM Call",
      model = completion.model,
      startTime = startTime,
      endTime = Some(Instant.now()),
      modelParameters = Map(
        "temperature" -> "0.7",
        "max_tokens" -> "100"
      ),
      input = Some(conversation),
      output = Some(completion),
      usage = completion.usage.map(u => 
        TraceTokenUsage(u.promptTokens, u.completionTokens, u.totalTokens)
      ),
      metadata = Map(
        "completion_id" -> completion.id,
        "demo_type" -> "unified"
      )
    )
    
    // Simulate a tool call
    trace.span("tool-execution") { span =>
      val toolStartTime = Instant.now()
      Thread.sleep(50)
      
      // Record tool call using unified interface
      trace.recordToolCall(
        name = "Weather Tool",
        toolName = "get_weather",
        startTime = toolStartTime,
        endTime = Some(Instant.now()),
        input = Some(ujson.Obj("location" -> "Paris", "units" -> "celsius")),
        output = Some(ujson.Obj("temperature" -> 22, "condition" -> "Sunny")),
        metadata = Map("cache_hit" -> "false"),
        spanId = Some(span.spanId)
      )
      
      span.setOutput("Weather retrieved successfully")
    }
    
    // Finish the trace
    trace.finish()
    
    // Shutdown if it's a real trace manager
    traceManager.shutdown()
  }
}