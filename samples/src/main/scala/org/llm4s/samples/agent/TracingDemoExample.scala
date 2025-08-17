package org.llm4s.samples.agent

import org.llm4s.agent.Agent
import org.llm4s.llmconnect.LLM
import org.llm4s.toolapi.ToolRegistry
import org.llm4s.toolapi.tools.WeatherTool
import org.llm4s.trace.TracingFactory

/**
 * Example demonstrating different tracing modes for agent execution.
 * 
 * This sample shows how to observe agent execution with different tracing backends:
 * - Print mode: Outputs trace data to console
 * - Langfuse mode: Sends traces to Langfuse for web-based analysis
 * - None mode: Disables tracing for production use
 * 
 * Run with different tracing modes:
 * - TRACING_MODE=print sbt "samples/runMain org.llm4s.samples.agent.TracingDemoExample"
 * - TRACING_MODE=langfuse sbt "samples/runMain org.llm4s.samples.agent.TracingDemoExample"
 * - TRACING_MODE=none sbt "samples/runMain org.llm4s.samples.agent.TracingDemoExample"
 */
object TracingDemoExample {
  def main(args: Array[String]): Unit = {
    println("=== Agent Tracing Demo ===")
    
    // Get current tracing mode from environment
    val tracingMode = sys.env.getOrElse("TRACING_MODE", "none")
    println(s"Current tracing mode: $tracingMode")
    
    // Provide mode-specific guidance
    tracingMode match {
      case "print" =>
        println("📝 Console tracing enabled - trace data will be output to console")
      case "langfuse" =>
        println("🔍 Langfuse tracing enabled - traces will be sent to Langfuse")
        println("   Make sure you have LANGFUSE_* environment variables set")
      case "none" =>
        println("🚫 No tracing enabled - agent will run without observability")
      case other =>
        println(s"⚠️  Unknown tracing mode: $other (falling back to none)")
    }
    
    println()
    
    // Get a client using environment variables
    val client = LLM.client()
    
    // Create a tool registry
    val toolRegistry = new ToolRegistry(Seq(WeatherTool.tool))
    
    // Create trace manager (will respect TRACING_MODE env var)
    val traceManager = TracingFactory.create()
    
    // Create an agent with tracing
    val agent = new Agent(client, traceManager)
    
    // Define a simple query
    val query = "What's the weather like in Tokyo?"
    println(s"Query: $query")
    println()
    
    // Run the agent with tracing
    println("🚀 Starting agent execution...")
    val startTime = System.currentTimeMillis()
    
    agent.run(
      query = query,
      tools = toolRegistry,
      maxSteps = Some(5),
      traceMetadata = Map(
        "demo" -> "tracing_example",
        "tracing_mode" -> tracingMode,
        "sample_type" -> "weather_query"
      )
    ) match {
      case Right(finalState) =>
        val duration = System.currentTimeMillis() - startTime
        println(s"✅ Agent completed in ${duration}ms")
        println(s"Final status: ${finalState.status}")
        
        // Show final answer if available
        finalState.conversation.messages.reverse
          .collectFirst { case msg if msg.role == "assistant" && msg.content != null => msg.content }
          .foreach { answer =>
            println(s"📋 Answer: $answer")
          }
        
        // Show execution statistics
        println(s"📊 Execution stats:")
        println(s"   - Total messages: ${finalState.conversation.messages.size}")
        println(s"   - Total steps: ${finalState.logs.size}")
        
        // Show tools used
        val toolCalls = finalState.logs.filter(_.contains("[tool]"))
        if (toolCalls.nonEmpty) {
          println(s"   - Tools used: ${toolCalls.size}")
          toolCalls.foreach { log =>
            val parts = log.split("\\s+")
            if (parts.length >= 2) {
              val toolName = parts(1)
              println(s"     * $toolName")
            }
          }
        }
        
      case Left(error) =>
        println(s"❌ Agent failed: $error")
    }
    
    println()
    println("=== Demo Complete ===")
    
    // Provide next steps based on tracing mode
    tracingMode match {
      case "print" =>
        println("💡 Check the console output above for trace information")
      case "langfuse" =>
        println("💡 Check your Langfuse dashboard for detailed trace analysis")
        println("   Traces should appear with name 'agent-execution'")
      case "none" =>
        println("💡 Try running with TRACING_MODE=print to see trace data")
      case _ =>
        println("💡 Set TRACING_MODE=print or TRACING_MODE=langfuse to enable tracing")
    }
  }
}