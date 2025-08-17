# Comprehensive Guide to Langfuse Tracing via the Ingestion API

Based on extensive research of Langfuse documentation and implementation patterns, this guide provides everything developers need to integrate Langfuse tracing into RAG, chat, and agent applications using the ingestion API directly.

## Core API fundamentals and event architecture

The Langfuse ingestion API uses a simple yet powerful event-based architecture. Every interaction with the API follows an "event envelope" pattern where events are batched and sent to a single endpoint. The API accepts events at `POST /api/public/ingestion` with Basic Authentication using your project's public and secret keys. All events must include a unique ID for deduplication, a timestamp, an event type (like `trace-create` or `generation-create`), and the actual data payload in the body field.

The data model consists of four primary observation types that form a hierarchy. **Traces** represent complete executions of a feature or request and serve as the root container. **Spans** track duration-based operations like retrieval or processing steps. **Generations** specifically track LLM calls with model parameters and token usage. **Events** capture discrete occurrences without duration. These observations link together through `traceId` and `parentObservationId` fields, creating a tree structure that represents your application's execution flow.

### Authentication and request structure

```json
// Basic request structure
{
  "batch": [
    {
      "id": "evt_2024_unique_id",  // Unique event ID for deduplication
      "timestamp": "2024-07-14T10:00:00.000Z",
      "type": "trace-create",
      "body": {
        "id": "trace_123",  // The actual trace ID
        "name": "rag-pipeline",
        "userId": "user_456",
        "sessionId": "session_789",
        "metadata": {"version": "1.0"}
      }
    }
  ]
}
```

The API returns a 207 Multi-Status response, allowing partial success. This design ensures that individual event failures don't block the entire batch. Each event in the response indicates whether it succeeded (201) or failed (4xx) with detailed error messages.

## Implementing RAG workflows with proper event hierarchy

RAG implementations require careful structuring to capture the retrieval, context preparation, and generation phases. The key is creating a clear hierarchy that reflects your pipeline's actual execution flow while capturing enough detail for debugging and optimization.

Start with a root trace representing the entire RAG request. Under this, create spans for major phases: query preprocessing, retrieval, context preparation, and response generation. Within the retrieval span, nest additional observations for embedding generation (as a generation event), vector search (as a span), and document ranking (as an event or span depending on complexity).

```python
# Example RAG workflow structure
def track_rag_pipeline(query, user_id):
    # Create root trace
    trace_event = {
        "id": f"evt_{uuid.uuid4()}",
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "type": "trace-create",
        "body": {
            "id": f"trace_{uuid.uuid4()}",
            "name": "rag-pipeline",
            "userId": user_id,
            "input": {"query": query},
            "metadata": {"pipeline_version": "2.0"}
        }
    }
    
    # Create retrieval span
    retrieval_span = {
        "id": f"evt_{uuid.uuid4()}",
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "type": "span-create",
        "body": {
            "id": f"span_{uuid.uuid4()}",
            "traceId": trace_event["body"]["id"],
            "name": "document-retrieval",
            "startTime": datetime.utcnow().isoformat() + "Z",
            "input": {"query": query, "top_k": 10}
        }
    }
    
    # Track embedding generation
    embedding_generation = {
        "id": f"evt_{uuid.uuid4()}",
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "type": "generation-create",
        "body": {
            "id": f"gen_{uuid.uuid4()}",
            "traceId": trace_event["body"]["id"],
            "parentObservationId": retrieval_span["body"]["id"],
            "name": "query-embedding",
            "model": "text-embedding-ada-002",
            "startTime": datetime.utcnow().isoformat() + "Z",
            "input": {"text": query},
            "usage": {"input": len(query), "unit": "CHARACTERS"}
        }
    }
    
    return [trace_event, retrieval_span, embedding_generation]
```

For metadata, include retrieval-specific information like similarity scores, document sources, chunk sizes, and reranking details. This metadata proves invaluable when optimizing retrieval quality or debugging issues. Track both successful retrievals and cases where retrieval fails or returns low-quality results.

## Tracking multi-turn chat conversations

Chat applications require session management to group related messages and maintain conversation context. Use the `sessionId` field to link all traces within a conversation, enabling conversation replay and analysis. Each message exchange becomes a new trace within the session, preserving the full interaction history.

Structure each turn as a separate trace containing the user input processing, any intermediate steps (like intent classification or context retrieval), and the response generation. Link these traces with a consistent `sessionId` and increment metadata like turn numbers or context length to track conversation progression.

```python
def track_chat_turn(message, session_id, turn_number, conversation_history):
    # Create trace for this conversation turn
    turn_trace = {
        "id": f"evt_{uuid.uuid4()}",
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "type": "trace-create",
        "body": {
            "id": f"trace_turn_{turn_number}",
            "name": "chat-turn",
            "sessionId": session_id,
            "metadata": {
                "turn_number": turn_number,
                "context_length": sum(len(m["content"]) for m in conversation_history),
                "conversation_stage": "active"
            },
            "input": {"user_message": message}
        }
    }
    
    # Track response generation with full context
    response_generation = {
        "id": f"evt_{uuid.uuid4()}",
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "type": "generation-create",
        "body": {
            "id": f"gen_response_{turn_number}",
            "traceId": turn_trace["body"]["id"],
            "name": "response-generation",
            "model": "gpt-4",
            "startTime": datetime.utcnow().isoformat() + "Z",
            "input": conversation_history + [{"role": "user", "content": message}],
            "modelParameters": {"temperature": 0.7, "max_tokens": 500}
        }
    }
    
    return [turn_trace, response_generation]
```

For maintaining context, include relevant conversation state in metadata: user preferences discovered during the chat, topic changes, emotional tone, or any extracted entities. This contextual information helps analyze conversation quality and user satisfaction patterns.

## Agent loops with tool calling patterns

Agent workflows introduce complexity through iterative decision-making and tool execution. The key to effective agent tracing is capturing both the reasoning process and the tool interactions while maintaining clear parent-child relationships that reflect the actual execution flow.

Structure agent loops with a root trace for the entire agent execution. Within this, create spans for each iteration, containing the planning phase (as a generation), tool selection logic, tool execution (as spans), and result synthesis. For parallel tool execution, create sibling spans under the same parent to show concurrent operations.

```python
def track_agent_iteration(agent_state, iteration_num, trace_id):
    events = []
    
    # Create iteration span
    iteration_span_id = f"span_iter_{iteration_num}"
    iteration_span = {
        "id": f"evt_{uuid.uuid4()}",
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "type": "span-create",
        "body": {
            "id": iteration_span_id,
            "traceId": trace_id,
            "name": f"agent-iteration-{iteration_num}",
            "startTime": datetime.utcnow().isoformat() + "Z",
            "metadata": {"iteration": iteration_num, "state": agent_state}
        }
    }
    events.append(iteration_span)
    
    # Track reasoning/planning
    planning_event = {
        "id": f"evt_{uuid.uuid4()}",
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "type": "generation-create",
        "body": {
            "id": f"gen_planning_{iteration_num}",
            "traceId": trace_id,
            "parentObservationId": iteration_span_id,
            "name": "agent-planning",
            "model": "gpt-4",
            "startTime": datetime.utcnow().isoformat() + "Z",
            "input": {"state": agent_state, "available_tools": ["search", "calculator", "code_interpreter"]},
            "output": {"selected_tool": "search", "reasoning": "User asked about recent events"}
        }
    }
    events.append(planning_event)
    
    # Track tool execution
    tool_execution = {
        "id": f"evt_{uuid.uuid4()}",
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "type": "span-create",
        "body": {
            "id": f"span_tool_{iteration_num}",
            "traceId": trace_id,
            "parentObservationId": iteration_span_id,
            "name": "tool-search",
            "startTime": datetime.utcnow().isoformat() + "Z",
            "input": {"query": "recent AI developments 2024"},
            "metadata": {"tool_type": "web_search", "max_results": 10}
        }
    }
    events.append(tool_execution)
    
    return events
```

For tool tracking, capture not just the tool name and parameters, but also execution context: why the tool was selected, any constraints or filters applied, and how the results will be used. This information proves crucial for understanding agent behavior and debugging unexpected decisions.

## Data model specifications and field requirements

Understanding field requirements prevents validation errors and ensures complete observability. Each observation type has specific required and optional fields that serve different purposes in the tracing hierarchy.

**Traces** require only an `id` and benefit from `name`, `userId`, `sessionId`, and `metadata`. They serve as containers and don't have timing fields. **Spans** must include `id`, `traceId`, and `startTime`, with `endTime` added when the operation completes. **Generations** extend spans with model-specific fields: `model`, `modelParameters`, and `usage` for token tracking. The `completionStartTime` field helps track streaming latency. **Events** are simplified spans without duration, requiring only `id`, `traceId`, and `startTime`.

All observations support `input` and `output` fields accepting any JSON-serializable data. The `metadata` field provides flexibility for custom attributes without schema constraints. Use `level` (DEBUG, DEFAULT, WARNING, ERROR) and `statusMessage` to indicate operation success or failure conditions.

## Parent-child relationships and timing

Proper relationship modeling creates meaningful trace hierarchies. The `traceId` field links all observations to their root trace, while `parentObservationId` creates the parent-child structure. Root-level observations have only `traceId`, while nested observations include both fields.

Timing follows a logical pattern: traces use a single `timestamp`, while spans and generations use `startTime` and `endTime` to calculate duration. Events use only `startTime` since they represent instantaneous occurrences. Always use ISO 8601 format with timezone information (the Z suffix for UTC).

When updating observations, create new events with the same `body.id` but different `event.id`. This pattern allows progressive updates as operations complete, essential for long-running processes or streaming responses.

## Updating and ending spans

Spans follow a create-update lifecycle using two event types: `span-create` and `span-update`. A span begins with a `span-create` event containing at minimum an `id`, `traceId`, and `startTime`. The span remains active until you send a `span-update` event with an `endTime` field, which marks the span as complete.

### Creating a span

```json
{
  "id": "evt_unique_123",
  "timestamp": "2024-07-14T10:00:00.000Z",
  "type": "span-create",
  "body": {
    "id": "span_retrieval_456",
    "traceId": "trace_main_789",
    "parentObservationId": "span_parent_001",  // Optional
    "name": "document-retrieval",
    "startTime": "2024-07-14T10:00:00.000Z",
    "input": {"query": "machine learning basics", "top_k": 10},
    "metadata": {"index": "production-v2"}
  }
}
```

### Updating a span (intermediate updates)

You can update a span multiple times before ending it. This is useful for adding intermediate results, updating metadata, or recording progress in long-running operations:

```json
{
  "id": "evt_unique_456",  // New event ID
  "timestamp": "2024-07-14T10:00:02.000Z",
  "type": "span-update",
  "body": {
    "id": "span_retrieval_456",  // Same span ID
    "metadata": {"documents_scanned": 1000, "matches_found": 15},
    "statusMessage": "Initial retrieval complete, starting reranking"
  }
}
```

### Ending a span

A span is considered complete when you send a `span-update` event with an `endTime`. This final update typically includes the operation's output and final status:

```json
{
  "id": "evt_unique_789",  // New event ID
  "timestamp": "2024-07-14T10:00:05.000Z",
  "type": "span-update",
  "body": {
    "id": "span_retrieval_456",  // Same span ID
    "endTime": "2024-07-14T10:00:05.000Z",
    "output": {
      "documents": ["doc1", "doc2", "doc3"],
      "scores": [0.95, 0.87, 0.82]
    },
    "level": "DEFAULT",  // or ERROR, WARNING, DEBUG
    "statusMessage": "Successfully retrieved 3 relevant documents"
  }
}
```

### Practical implementation pattern

```python
class SpanTracker:
    def __init__(self, langfuse_client):
        self.client = langfuse_client
        self.active_spans = {}
    
    def start_span(self, span_id, trace_id, name, parent_id=None, **kwargs):
        """Create a new span"""
        event = {
            "id": f"evt_{uuid.uuid4()}",
            "timestamp": datetime.utcnow().isoformat() + "Z",
            "type": "span-create",
            "body": {
                "id": span_id,
                "traceId": trace_id,
                "name": name,
                "startTime": datetime.utcnow().isoformat() + "Z"
            }
        }
        
        if parent_id:
            event["body"]["parentObservationId"] = parent_id
            
        # Add any additional fields
        for key, value in kwargs.items():
            if key in ["input", "metadata", "level", "statusMessage"]:
                event["body"][key] = value
                
        self.active_spans[span_id] = event["body"]["startTime"]
        self.client.send_event(event)
        return span_id
    
    def update_span(self, span_id, **updates):
        """Update an active span without ending it"""
        event = {
            "id": f"evt_{uuid.uuid4()}",
            "timestamp": datetime.utcnow().isoformat() + "Z",
            "type": "span-update",
            "body": {
                "id": span_id
            }
        }
        
        # Add updates (metadata, statusMessage, etc.)
        for key, value in updates.items():
            if key in ["metadata", "statusMessage", "level"]:
                event["body"][key] = value
                
        self.client.send_event(event)
    
    def end_span(self, span_id, output=None, error=None):
        """End a span with final output and status"""
        event = {
            "id": f"evt_{uuid.uuid4()}",
            "timestamp": datetime.utcnow().isoformat() + "Z",
            "type": "span-update",
            "body": {
                "id": span_id,
                "endTime": datetime.utcnow().isoformat() + "Z"
            }
        }
        
        if output is not None:
            event["body"]["output"] = output
            
        if error:
            event["body"]["level"] = "ERROR"
            event["body"]["statusMessage"] = str(error)
        else:
            event["body"]["level"] = "DEFAULT"
            
        if span_id in self.active_spans:
            del self.active_spans[span_id]
            
        self.client.send_event(event)
```

### Important notes about span lifecycle

1. **Only two event types**: Spans use only `span-create` and `span-update`. There is no `span-end` or `span-delete` event type.

2. **Span completion**: A span is considered complete when it has an `endTime`. Once set, the duration is calculated automatically by Langfuse.

3. **Multiple updates allowed**: You can send multiple `span-update` events for the same span. Each update can add or modify different fields.

4. **Immutable fields**: The `id`, `traceId`, and `startTime` cannot be changed after creation. Updates can only modify other fields.

5. **Update without ending**: You can update a span many times without setting `endTime`, useful for long-running operations where you want to track progress.

6. **Generations follow the same pattern**: The `generation-create` and `generation-update` events work identically, with generations being complete when `endTime` is set.

## Error handling and production resilience

Production deployments require robust error handling to prevent tracing failures from impacting your application. Implement circuit breakers that disable tracing after repeated failures, preventing cascading issues. Use exponential backoff for retries, but only for infrastructure errors (5xx responses or network failures), not validation errors (returned in 207 responses).

```python
class ResilientLangfuseClient:
    def __init__(self, max_failures=5, circuit_reset_timeout=60):
        self.failure_count = 0
        self.max_failures = max_failures
        self.circuit_open = False
        self.last_failure_time = None
        self.circuit_reset_timeout = circuit_reset_timeout
    
    def send_events(self, events):
        # Check circuit breaker
        if self.circuit_open:
            if time.time() - self.last_failure_time > self.circuit_reset_timeout:
                self.circuit_open = False
                self.failure_count = 0
            else:
                # Skip tracing when circuit is open
                return None
        
        try:
            response = self._send_batch(events)
            # Reset on success
            if self.failure_count > 0:
                self.failure_count = 0
            return response
            
        except Exception as e:
            self.failure_count += 1
            self.last_failure_time = time.time()
            
            if self.failure_count >= self.max_failures:
                self.circuit_open = True
                logger.warning(f"Langfuse circuit breaker opened after {self.failure_count} failures")
            
            # Don't propagate tracing errors to application
            logger.error(f"Langfuse tracing error: {e}")
            return None
```

For high-volume applications, implement sampling to control costs and reduce overhead. Use weighted sampling that captures 100% of errors while sampling normal operations at lower rates. This ensures you don't miss critical issues while managing data volume.

## Best practices for implementation

Structure your implementation in layers: a low-level client handling HTTP communication and batching, a mid-level API providing typed methods for creating different observations, and high-level decorators or context managers for easy integration. This separation allows flexibility while maintaining ease of use.

Batch events intelligently based on your environment. Serverless functions need immediate flushing due to short lifecycles, while long-running services benefit from larger batches. Configure batch sizes between 50-200 events and flush intervals of 5-10 seconds for optimal performance.

For metadata, establish conventions early. Use consistent field names across your application and document their meanings. Common patterns include `version` for code versions, `environment` for deployment stages, `feature_flags` for enabled features, and `performance_metrics` for custom timing data.

## Monitoring and debugging strategies

Implement comprehensive health checks covering API connectivity, queue lengths, and data flow. Monitor the ingestion pipeline at each stage: event creation, batching, API transmission, S3 storage, and final ClickHouse insertion. Set up alerts for queue length spikes, high error rates, or missing data patterns.

Use debug logging strategically. Log event IDs and batch summaries in production, with full payloads only in development. Implement correlation IDs that link your application logs to Langfuse traces, enabling full observability across systems.

For troubleshooting missing events, check multiple stages: verify 207 responses from the API, confirm S3 uploads using the predictable path structure (`/{projectId}/{type}/{eventId}/`), check worker logs for processing errors, and query ClickHouse directly if needed. Most issues stem from validation errors (check your 207 response bodies) or timing misconfigurations.

## Implementation patterns for production

The ingestion API's flexibility supports various integration patterns. For new applications, build tracing in from the start using decorators or context managers. For existing applications, add tracing incrementally, starting with critical paths and expanding coverage over time.

Consider creating a thin abstraction layer over the raw API that matches your domain model. This abstraction can handle common patterns like creating nested observations, managing timing, and adding standard metadata, while still allowing direct API access when needed.

Remember that Langfuse tracing should enhance, not complicate, your application. Start simple with basic trace creation, then gradually add more detailed observations as you identify what information proves most valuable for your use case. The goal is comprehensive observability that helps you understand and improve your AI applications' behavior and performance.