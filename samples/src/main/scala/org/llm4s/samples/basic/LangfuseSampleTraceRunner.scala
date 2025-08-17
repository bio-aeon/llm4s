package org.llm4s.samples.basic

import org.llm4s.agent.AgentStatus
import org.llm4s.trace.{SpanStatus, TracingFactory}
import org.llm4s.llmconnect.model.{AssistantMessage, Conversation, SystemMessage, ToolCall, ToolMessage, UserMessage}

object LangfuseSampleTraceRunner {
  def main(args: Array[String]): Unit = {
    exportSampleTrace()
  }

  def exportSampleTrace(): Unit = {
    val traceManager = TracingFactory.create()
    
    // Create a trace for the agent execution
    val trace = traceManager.createTrace(
      name = "agent-execution",
      userId = Some("sample-user"),
      metadata = Map(
        "sample" -> "LangfuseSampleTraceRunner",
        "description" -> "Demonstration of new TraceManager API"
      )
    )

    // Create sample conversation data
    val toolCall = ToolCall("tool-1", "search", ujson.Obj("query" -> "Scala Langfuse integration"))
    val assistantMsg = AssistantMessage("Let me search for that...", Seq(toolCall))
    val toolMsg = ToolMessage("tool-1", "{\"result\":\"Here is what I found...\"}")
    val userMsg = UserMessage("How do I integrate Scala with Langfuse?")
    val sysMsg = SystemMessage("You are a helpful assistant.")
    val conversation = Conversation(Seq(sysMsg, userMsg, assistantMsg, toolMsg))
    
    // Track the agent execution flow
    trace.span("agent-conversation") { agentSpan =>
      agentSpan.addMetadata("user_query", userMsg.content)
      agentSpan.addMetadata("message_count", conversation.messages.length)
      agentSpan.addTag("sample_trace")
      
      // Track assistant response
      agentSpan.span("assistant-response") { assistantSpan =>
        assistantSpan.addMetadata("content", assistantMsg.content)
        assistantSpan.addMetadata("tool_calls_count", assistantMsg.toolCalls.length)
        
        // Track tool calls
        assistantMsg.toolCalls.foreach { tc =>
          assistantSpan.span(s"tool-call-${tc.name}") { toolSpan =>
            toolSpan.addMetadata("tool_id", tc.id)
            toolSpan.addMetadata("tool_name", tc.name)
            toolSpan.addMetadata("arguments", tc.arguments.render())
            toolSpan.addTag("tool_call")
            
            // Simulate tool execution timing
            Thread.sleep(100) // Simulate 100ms execution
            
            // Track tool response
            toolSpan.addMetadata("result", toolMsg.content)
            toolSpan.setStatus(SpanStatus.Ok)
          }
        }
      }
      
      // Track final state
      agentSpan.span("agent-state") { stateSpan =>
        stateSpan.addMetadata("status", AgentStatus.Complete.toString)
        stateSpan.addMetadata("logs_count", 2)
        stateSpan.addMetadata("log_1", "[assistant] tools: 1 tool calls requested (search)")
        stateSpan.addMetadata("log_2", "[tool] search (100ms): {\"result\":\"Here is what I found...\"}")
      }
    }
    
    // Finish the trace to ensure all events are sent
    trace.finish()
    
    println("Sample trace exported successfully!")
  }
}