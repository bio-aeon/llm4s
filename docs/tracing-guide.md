# LLM4S Tracing Guide

## Overview

LLM4S provides a powerful hierarchical tracing system for monitoring, debugging, and analyzing LLM applications. The tracing system supports multiple backends (Langfuse, console, no-op) and provides automatic timing, context propagation, and error handling.

## Quick Start

```scala
import org.llm4s.trace.TracingFactory

// Create trace manager based on TRACING_MODE environment variable
val traceManager = TracingFactory.create()

// Create a trace for an operation
val trace = traceManager.createTrace(
  name = "my-operation",
  userId = Some("user123"),
  sessionId = Some("session456"),
  metadata = Map("version" -> "1.0")
)

// Execute operations within spans
trace.span("llm-completion") { span =>
  span.addMetadata("model", "gpt-4")
  span.addMetadata("temperature", 0.7)
  
  // Your operation here
  val result = performOperation()
  
  span.setOutput(result)
  result
}

// Finish the trace to ensure all events are sent
trace.finish()
```

## Configuration

### Environment Variables

Configure tracing behavior using environment variables or `.env` file:

```bash
# Langfuse backend (default)
TRACING_MODE=langfuse
LANGFUSE_PUBLIC_KEY=pk-lf-your-key
LANGFUSE_SECRET_KEY=sk-lf-your-secret
LANGFUSE_HOST=https://cloud.langfuse.com  # Optional

# Console output for debugging
TRACING_MODE=print

# Disable tracing
TRACING_MODE=none
```

### Programmatic Configuration

```scala
import org.llm4s.trace._

// Custom configuration
val config = TraceManagerConfig(
  enabled = true,
  batchSize = 100,
  flushInterval = 5.seconds,
  maxRetries = 3,
  circuitBreakerThreshold = 5,
  environment = "production",
  release = "1.0.0"
)

// Create specific trace manager type
val langfuseManager = TracingFactory.createLangfuseTraceManager(config)
val printManager = TracingFactory.createPrintTraceManager(config)
val noOpManager = TracingFactory.createNoOpTraceManager()
```

## Core Concepts

### Traces

A trace represents a complete execution flow, such as an entire API request or agent execution:

```scala
val trace = traceManager.createTrace(
  name = "api-request",
  userId = Some("user123"),
  sessionId = Some("session456"),
  metadata = Map(
    "endpoint" -> "/api/chat",
    "version" -> "2.0"
  )
)

// Add metadata during execution
trace.addMetadata("request_id", "req_789")
trace.addTag("production")

// Set input/output for the entire trace
trace.setInput("User question: What's the weather?")
trace.setOutput("The weather is sunny and 72°F")

// Record errors at trace level
trace.recordError(new RuntimeException("API rate limit exceeded"))

// Always finish traces
trace.finish()
```

### Spans

Spans represent individual operations within a trace. They automatically track timing and can be nested:

```scala
trace.span("database-query") { dbSpan =>
  dbSpan.addMetadata("query_type", "select")
  dbSpan.addMetadata("table", "users")
  
  // Nested spans for sub-operations
  val userData = dbSpan.span("fetch-user") { fetchSpan =>
    fetchSpan.addMetadata("user_id", "123")
    database.getUser("123")
  }
  
  val permissions = dbSpan.span("fetch-permissions") { permSpan =>
    permSpan.addMetadata("user_id", "123")
    database.getPermissions("123")
  }
  
  UserWithPermissions(userData, permissions)
}
```

### Error Handling

The tracing system provides comprehensive error tracking:

```scala
trace.span("risky-operation") { span =>
  try {
    val result = performRiskyOperation()
    span.setStatus(SpanStatus.Ok)
    result
  } catch {
    case e: Exception =>
      span.recordError(e)
      span.setStatus(SpanStatus.Error)
      span.addMetadata("error_type", e.getClass.getSimpleName)
      throw e
  }
}
```

### Events

Record discrete events within spans:

```scala
span.recordEvent("checkpoint_reached", Map(
  "checkpoint" -> "data_validation",
  "records_processed" -> 1000,
  "validation_errors" -> 0
))
```

## Common Patterns

### LLM Operations

```scala
def trackedLLMCall(prompt: String): String = {
  traceManager.withTrace("llm-operation") { trace =>
    trace.setInput(prompt)
    
    val response = trace.span("llm-completion") { span =>
      span.addMetadata("model", "gpt-4")
      span.addMetadata("max_tokens", 1000)
      span.addMetadata("temperature", 0.7)
      
      val completion = llmClient.complete(prompt)
      
      // Track token usage
      completion.usage.foreach { usage =>
        span.addMetadata("prompt_tokens", usage.promptTokens)
        span.addMetadata("completion_tokens", usage.completionTokens)
        span.addMetadata("total_tokens", usage.totalTokens)
      }
      
      completion.message.content
    }
    
    trace.setOutput(response)
    response
  }
}
```

### Tool Execution

```scala
def executeToolWithTracing(toolName: String, input: String): String = {
  trace.span(s"tool-$toolName") { toolSpan =>
    toolSpan.addMetadata("tool_name", toolName)
    toolSpan.addTag("tool_call")
    toolSpan.setInput(input)
    
    val startTime = System.currentTimeMillis()
    
    val result = toolRegistry.execute(toolName, input)
    
    val duration = System.currentTimeMillis() - startTime
    toolSpan.addMetadata("execution_time_ms", duration)
    toolSpan.setOutput(result)
    
    result
  }
}
```

### Agent Execution

```scala
class TracedAgent(llmClient: LLMClient, tools: ToolRegistry, traceManager: TraceManager) {
  
  def execute(query: String, userId: String): AgentResult = {
    val trace = traceManager.createTrace(
      name = "agent-execution",
      userId = Some(userId),
      metadata = Map("agent_version" -> "1.0")
    )
    
    try {
      trace.setInput(query)
      
      var iteration = 0
      var result: AgentResult = null
      
      while (iteration < maxIterations && result == null) {
        result = trace.span(s"iteration-$iteration") { iterSpan =>
          iterSpan.addMetadata("iteration", iteration)
          
          // Planning phase
          val plan = iterSpan.span("planning") { planSpan =>
            planSpan.addMetadata("strategy", "cot")
            generatePlan(query)
          }
          
          // Execution phase
          val output = iterSpan.span("execution") { execSpan =>
            executePlan(plan, execSpan)
          }
          
          // Evaluation phase
          iterSpan.span("evaluation") { evalSpan =>
            if (isComplete(output)) {
              AgentResult.success(output)
            } else {
              iteration += 1
              null
            }
          }
        }
      }
      
      trace.setOutput(result.toString)
      result
      
    } catch {
      case e: Exception =>
        trace.recordError(e)
        throw e
    } finally {
      trace.finish()
    }
  }
}
```

### RAG Pipeline

```scala
def ragPipeline(query: String): String = {
  trace.span("rag-pipeline") { ragSpan =>
    ragSpan.setInput(query)
    
    // Embedding generation
    val embedding = ragSpan.span("generate-embedding") { embSpan =>
      embSpan.addMetadata("model", "text-embedding-ada-002")
      embeddingClient.embed(query)
    }
    
    // Vector search
    val documents = ragSpan.span("vector-search") { searchSpan =>
      searchSpan.addMetadata("index", "knowledge_base")
      searchSpan.addMetadata("top_k", 5)
      searchSpan.addMetadata("similarity_threshold", 0.8)
      
      val results = vectorDB.search(embedding, topK = 5)
      searchSpan.addMetadata("results_found", results.length)
      results
    }
    
    // Context preparation
    val context = ragSpan.span("prepare-context") { ctxSpan =>
      ctxSpan.addMetadata("documents_count", documents.length)
      ctxSpan.addMetadata("max_context_length", 4000)
      prepareContext(documents)
    }
    
    // Response generation
    val response = ragSpan.span("generate-response") { genSpan =>
      genSpan.addMetadata("model", "gpt-4")
      genSpan.addMetadata("context_tokens", countTokens(context))
      
      llmClient.complete(
        systemPrompt = "Answer based on the provided context",
        userPrompt = s"Context: $context\n\nQuestion: $query"
      )
    }
    
    ragSpan.setOutput(response)
    response
  }
}
```

## Async Operations

The tracing system fully supports async operations with context propagation:

```scala
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

def asyncOperation(): Future[String] = {
  traceManager.withTraceAsync("async-operation") { trace =>
    trace.addMetadata("async", true)
    
    val future1 = Future {
      trace.span("async-task-1") { span =>
        Thread.sleep(100)
        span.addMetadata("task_id", 1)
        "result-1"
      }
    }
    
    val future2 = Future {
      trace.span("async-task-2") { span =>
        Thread.sleep(150)
        span.addMetadata("task_id", 2)
        "result-2"
      }
    }
    
    for {
      r1 <- future1
      r2 <- future2
    } yield {
      trace.setOutput(s"$r1 and $r2")
      s"$r1 and $r2"
    }
  }
}
```

## Best Practices

### 1. Always Finish Traces
```scala
val trace = traceManager.createTrace("operation")
try {
  // Your operations
} finally {
  trace.finish() // Ensures events are flushed
}
```

### 2. Use Descriptive Names
```scala
// Good
trace.span("fetch-user-permissions") { ... }
trace.span("validate-payment-method") { ... }

// Less descriptive
trace.span("step-1") { ... }
trace.span("process") { ... }
```

### 3. Add Relevant Metadata
```scala
span.addMetadata("user_id", userId)
span.addMetadata("request_id", requestId)
span.addMetadata("retry_count", retries)
span.addMetadata("cache_hit", cacheHit)
```

### 4. Use Tags for Categorization
```scala
trace.addTag("production")
trace.addTag("user-facing")
span.addTag("database-query")
span.addTag("cache-miss")
```

### 5. Track Inputs and Outputs
```scala
span.setInput(request.toJson)
span.setOutput(response.toJson)
```

### 6. Handle Errors Gracefully
```scala
try {
  // Operation
} catch {
  case e: Exception =>
    span.recordError(e)
    span.setStatus(SpanStatus.Error)
    // Re-throw if needed
}
```

## Performance Considerations

### Batching
Events are automatically batched for efficient transmission:
- Default batch size: 100 events
- Default flush interval: 5 seconds
- Automatic flushing on shutdown

### Circuit Breaker
The Langfuse integration includes a circuit breaker to prevent cascading failures:
- Opens after 5 consecutive failures
- Resets after 60 seconds
- Falls back to no-op when open

### Overhead
- No-op mode has zero overhead
- Print mode has minimal overhead (console I/O)
- Langfuse mode uses async batching to minimize impact

## Troubleshooting

### Traces Not Appearing
1. Check environment variables are set correctly
2. Verify Langfuse API keys are valid
3. Ensure `trace.finish()` is called
4. Check logs for circuit breaker activation

### High Memory Usage
- Reduce batch size in configuration
- Ensure traces are finished promptly
- Check for trace leaks in long-running operations

### Performance Impact
- Use no-op mode in performance-critical paths
- Increase batch size for high-volume scenarios
- Consider sampling for very high-traffic applications

## Migration from Old Tracing

If migrating from the old imperative tracing system:

```scala
// Old style (removed)
val tracer = Tracing.create()
tracer.traceEvent("Processing started")
tracer.traceCompletion(completion, "gpt-4")

// New style
val traceManager = TracingFactory.create()
val trace = traceManager.createTrace("operation")
trace.span("processing") { span =>
  // Automatic timing and lifecycle
}
trace.finish()
```

Key differences:
- Declarative API with automatic lifecycle management
- Hierarchical structure with proper parent-child relationships
- Automatic timing and duration tracking
- Better error handling and recovery
- Efficient batching and performance