package org.llm4s.samples.basic

import org.llm4s.llmconnect.LLM
import org.llm4s.agent.{Agent, AgentState, AgentStatus}
import org.llm4s.toolapi.ToolRegistry
import org.llm4s.toolapi.tools.CalculatorTool
import org.llm4s.trace.{EnhancedTracing, TracingMode, TraceEvent, TracingComposer}

import org.slf4j.LoggerFactory
import ujson._

/**
 * Enhanced example demonstrating the difference between basic LLM calls and the Agent framework
 * 
 * This example shows:
 * 1. Basic LLM call (simple request → response)
 * 2. Agent framework (complex reasoning → tool usage → enhanced response)
 * 3. Real LLM4S tracing with all modes combined (Langfuse + Console + NoOp)
 * 4. Performance metrics and comparison
 * 
 * To enable Langfuse tracing, set these environment variables:
 * - LANGFUSE_URL: Your Langfuse instance URL (default: https://cloud.langfuse.com/api/public/ingestion)
 * - LANGFUSE_PUBLIC_KEY: Your Langfuse public key
 * - LANGFUSE_SECRET_KEY: Your Langfuse secret key
 * - LANGFUSE_ENV: Environment name (default: production)
 * - LANGFUSE_RELEASE: Release version (default: 1.0.0)
 * - LANGFUSE_VERSION: API version (default: 1.0.0)
 */
object AgentLLMCallingExample {
  private val logger = LoggerFactory.getLogger(getClass)
  
  def main(args: Array[String]): Unit = {
    logger.info("🧮 Calculator Tool Agent Demo with Tracing")
    logger.info("=" * 50)
    
    // Create tracing based on environment variable
    val tracing = createComprehensiveTracing()
    
    // Log the tracing configuration
    logger.info(s"🔍 Tracing Configuration:")
    logger.info(s"   • Mode: ${sys.env.getOrElse("TRACING_MODE", "console")}")
    logger.info(s"   • Langfuse URL: ${sys.env.getOrElse("LANGFUSE_URL", "default")}")
    logger.info(s"   • Langfuse Public Key: ${if (sys.env.contains("LANGFUSE_PUBLIC_KEY")) "SET" else "NOT SET"}")
    logger.info(s"   • Langfuse Secret Key: ${if (sys.env.contains("LANGFUSE_SECRET_KEY")) "SET" else "NOT SET"}")
    
    // Test tracing with a simple event
    logger.info("🧪 Testing tracing...")
    val testResult = tracing.traceEvent(TraceEvent.CustomEvent("calculator_demo_start", ujson.Obj(
      "demo" -> "Calculator Tool Agent",
      "timestamp" -> System.currentTimeMillis()
    )))
    logger.info(s"🧪 Trace test result: ${testResult}")
    
    // Calculator Agent Demo
    demonstrateCalculatorAgent(tracing)
    
    logger.info("=" * 50)
    logger.info("✨ Calculator Demo Complete!")
  }
  
  /**
   * Create comprehensive tracing with all three modes combined
   */
  private def createComprehensiveTracing(): EnhancedTracing = {
    try {
      // Create individual tracers
      val langfuseTracing = EnhancedTracing.create(TracingMode.Langfuse)
      val consoleTracing = EnhancedTracing.create(TracingMode.Console)
      val noOpTracing = EnhancedTracing.create(TracingMode.NoOp)
      
      logger.info("✅ All tracing modes initialized successfully")
      
      // Combine all tracers into one comprehensive tracer
      val combinedTracing = TracingComposer.combine(
        langfuseTracing,  // Primary: sends to Langfuse
        consoleTracing,   // Secondary: shows in console
        noOpTracing       // Tertiary: no-op (for performance monitoring)
      )
      
      logger.info("🔗 Combined tracing modes: Langfuse + Console + NoOp")
      combinedTracing
      
    } catch {
      case e: Exception =>
        logger.warn(s"⚠️  Some tracing modes failed: ${e.getMessage}")
        logger.info("🔄 Falling back to console tracing only")
        EnhancedTracing.create(TracingMode.Console)
    }
  }
  
  /**
   * Simple Calculator Agent Demo
   */
  private def demonstrateCalculatorAgent(tracing: EnhancedTracing): Unit = {
    logger.info("🧮 Calculator Agent Demo")
    logger.info("Testing calculator tool with agent framework")
    
    val startTime = System.currentTimeMillis()
    
    // Create tool registry with just calculator
    val tools = Seq(CalculatorTool.tool)
    val toolRegistry = new ToolRegistry(tools)
    
    logger.info("🔧 Available Tools:")
    tools.foreach { tool =>
      logger.info(s"• ${tool.name}: ${tool.description}")
    }
    
    // Create agent with LLM client
    val llmClient = LLM.client()
    val agent = new Agent(llmClient)
    
    // Initialize agent state with tools and query
    val query = "Calculate 15 to the power of 3, and then calculate the square root of that result."
    
    val agentState = agent.initialize(
      query = query,
      tools = toolRegistry,
      systemPromptAddition = Some("You have access to a calculator tool. Use it to perform mathematical calculations. IMPORTANT: Make only ONE tool call at a time, wait for the result, then make the next tool call if needed.")
    )
    
    // Trace agent initialization
    tracing.traceEvent(TraceEvent.AgentInitialized(
      query = query,
      tools = tools.map(_.name).toVector
    ))
    
    logger.info("🔄 Running calculator agent...")
    logger.info(s"Query: ${query}")
    
    // Execute agent with real step-by-step execution
    val agentResult = executeAgentWithRealTracing(agent, agentState, tracing)
    
    val endTime = System.currentTimeMillis()
    val duration = endTime - startTime
    
    // Trace the agent execution
    tracing.traceEvent(TraceEvent.CustomEvent(
      "calculator_agent_complete",
      ujson.Obj(
        "duration_ms" -> duration,
        "steps" -> agentResult.steps.length,
        "tools_used" -> agentResult.toolsUsed.length,
        "final_response_length" -> agentResult.finalResponse.length,
        "timestamp" -> System.currentTimeMillis()
      )
    ))
    
    logger.info(s"✅ Calculator agent completed in ${duration}ms")
    
    // Display final response
    logger.info("🎯 Final Agent Response:")
    logger.info(agentResult.finalResponse)
    
    // Performance metrics
    logger.info("📊 Performance Metrics:")
    logger.info(s"• Total Execution Time: ${duration}ms")
    logger.info(s"• Reasoning Steps: ${agentResult.steps.length}")
    logger.info(s"• Tools Used: ${agentResult.toolsUsed.length}")
  }
  
  /**
   * Execute agent with real LLM4S tracing and step-by-step display
   */
  private def executeAgentWithRealTracing(agent: Agent, agentState: AgentState, tracing: EnhancedTracing): AgentExecutionResult = {
    var currentState = agentState
    var steps = Vector.empty[String]
    var toolsUsed = Vector.empty[String]
    var processedToolMessages = 0
    var finalResponse = ""
    
    logger.info("🧠 Agent Reasoning Process:")
    
    // Trace initial agent state
    tracing.traceEvent(TraceEvent.AgentStateUpdated(
      status = currentState.status.toString,
      messageCount = currentState.conversation.messages.length,
      logCount = currentState.logs.length
    ))
    
    // Real agent execution steps
    val maxSteps = 10
    var stepCount = 0
    
    while (currentState.status == AgentStatus.InProgress && stepCount < maxSteps) {
      stepCount += 1
      val stepStart = System.currentTimeMillis()
      
      logger.info(s"${stepCount}. Running agent step...")
      
      // Run the actual agent step
      agent.runStep(currentState) match {
        case Right(newState) =>
          currentState = newState
          
          // Continue running steps until the agent completes or fails
          while (currentState.status == AgentStatus.WaitingForTools || currentState.status == AgentStatus.InProgress) {
            agent.runStep(currentState) match {
              case Right(nextState) =>
                currentState = nextState
                logger.info(s"   ↳ Continued to: ${currentState.status}")
                
                // Check for new tool executions and trace them
                val allToolMessages = nextState.conversation.messages.collect { 
                  case toolMsg: org.llm4s.llmconnect.model.ToolMessage => toolMsg 
                }
                val newToolMessages = allToolMessages.drop(processedToolMessages)
                
                newToolMessages.foreach { toolMsg =>
                  // Parse the tool result to extract useful information
                  try {
                    val result = ujson.read(toolMsg.content)
                    val toolName = result.obj.get("operation").map(_.str).getOrElse("calculator")
                    val toolResult = result.obj.get("result").map(_.num.toString).getOrElse("unknown")
                    val expression = result.obj.get("expression").map(_.str).getOrElse("unknown")
                    
                    logger.info(s"   📊 Tool result captured: $expression = $toolResult")
                    toolsUsed = toolsUsed :+ toolName
                    
                    // Trace the tool execution with real results
                    tracing.traceEvent(TraceEvent.ToolExecuted(
                      name = toolName,
                      input = s"${result.obj.get("operation").map(_.str).getOrElse("")}: a=${result.obj.get("a").map(_.num.toString).getOrElse("")}, b=${result.obj.get("b").map(_.num.toString).getOrElse("")}",
                      output = s"$expression = $toolResult",
                      duration = 10, // We don't have exact timing here
                      success = true
                    ))
                  } catch {
                    case _: Exception =>
                      // If parsing fails, just log the raw content
                      logger.info(s"   📊 Tool result: ${toolMsg.content}")
                  }
                  processedToolMessages += 1
                }
                
              case Left(error) =>
                logger.error(s"   ❌ Continuation failed: ${error.message}")
                currentState = currentState.withStatus(AgentStatus.Failed(error.message))
            }
          }
          
          // Trace the step completion
          tracing.traceEvent(TraceEvent.AgentStateUpdated(
            status = currentState.status.toString,
            messageCount = currentState.conversation.messages.length,
            logCount = currentState.logs.length
          ))
          
                     // Check if tools were used - only AssistantMessage has toolCalls
           val lastMessage = currentState.conversation.messages.lastOption
           lastMessage.foreach { msg =>
             msg match {
               case assistantMsg: org.llm4s.llmconnect.model.AssistantMessage if assistantMsg.toolCalls.nonEmpty =>
                 logger.info(s"   🔧 Tool calls detected: ${assistantMsg.toolCalls.map(_.name).mkString(", ")}")
                 toolsUsed = toolsUsed ++ assistantMsg.toolCalls.map(_.name)
                 
                 // Tool execution will be traced by the agent itself with real results
               case _ => // Not an assistant message or no tool calls
             }
           }
          
          steps = steps :+ s"Step ${stepCount}: ${currentState.status}"
          
         case Left(error) =>
           logger.error(s"   ❌ Step failed: ${error.message}")
           
           // Trace the error - old LLMError is not a Throwable, so we create a custom event
           tracing.traceEvent(TraceEvent.CustomEvent(
             s"agent_step_${stepCount}_error",
             ujson.Obj(
               "error_type" -> "LegacyLLMError",
               "error_message" -> error.message,
               "context" -> s"agent_step_${stepCount}",
               "timestamp" -> System.currentTimeMillis()
             )
           ))
           
           currentState = currentState.withStatus(AgentStatus.Failed(error.message))
           steps = steps :+ s"Step ${stepCount}: Failed - ${error.message}"
      }
      
      val stepDuration = System.currentTimeMillis() - stepStart
      logger.info(s"   ⏱️  Step completed in ${stepDuration}ms")
      
             // If agent is complete, break out of the loop
       if (currentState.status == AgentStatus.Complete) {
         logger.info("   🎯 Agent completed successfully!")
         stepCount = maxSteps // Force loop to end
       }
    }
    
    // Generate final response based on actual agent state
    if (currentState.status == AgentStatus.Complete) {
      finalResponse = currentState.conversation.messages.lastOption.map(_.content).getOrElse("No final response generated")
    } else {
      finalResponse = s"Agent execution stopped with status: ${currentState.status}"
    }
    
    logger.info("🎯 Final Response Generated Successfully!")
    
    AgentExecutionResult(steps, toolsUsed.distinct, finalResponse)
  }
  

  
  /**
   * Case class to hold agent execution results
   */
  case class AgentExecutionResult(
    steps: Vector[String],
    toolsUsed: Vector[String],
    finalResponse: String
  )
}
