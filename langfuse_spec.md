# Langfuse Ingestion API Technical Specification

## API Endpoint Overview

The Langfuse ingestion API provides a unified endpoint for sending observability data from LLM applications. This specification covers all aspects needed to implement a compliant client, generate test cases, and handle edge cases properly.

**Base Endpoint**: `POST https://cloud.langfuse.com/api/public/ingestion`
- **EU Region**: `https://cloud.langfuse.com/api/public/ingestion`
- **US Region**: `https://us.cloud.langfuse.com/api/public/ingestion`

## Authentication Requirements

The API uses HTTP Basic Authentication with Langfuse API keys:

```http
Authorization: Basic <base64-encoded-credentials>
```

Where credentials are formatted as `public_key:secret_key`:
- **Public Key Format**: `pk-lf-...` (username)
- **Secret Key Format**: `sk-lf-...` (password)

## Message Format and Structure

### Batch Request Structure

Every request must follow this exact structure:

```json
{
  "batch": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "timestamp": "2024-01-15T10:30:45.123Z",
      "type": "trace-create",
      "metadata": {},
      "body": {
        "id": "trace-123",
        // Event-specific fields
      }
    }
  ],
  "metadata": {}  // Optional SDK debugging information
}
```

### Event Envelope Fields

Each event in the batch requires:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | string | Yes | Unique UUID v4 for event deduplication |
| `timestamp` | string | Yes | ISO 8601 timestamp for event ordering |
| `type` | string | Yes | Event type discriminator |
| `metadata` | object | No | Optional debugging metadata |
| `body` | object | Yes | Event-specific payload |

### ID Management Rules

Understanding the two-ID system is critical:
- **Event ID** (`id`): Used for deduplication - must be unique per request
- **Body ID** (`body.id`): The actual entity ID visible in UI - reuse for updates

## Supported Event Types

### 1. trace-create

Creates or updates a trace (top-level container for observations).

**Required Fields**:
```json
{
  "id": "trace-id-123"
}
```

**Optional Fields**:
```json
{
  "name": "API Request Handler",              // Max 1000 characters
  "userId": "user-123",                       // User identifier
  "sessionId": "session-456",                 // Session grouping
  "input": {"query": "test"},                 // Any JSON value
  "output": {"response": "result"},           // Any JSON value
  "metadata": {"key": "value"},               // Arbitrary metadata
  "tags": ["production", "v2"],               // String array
  "public": false,                            // Boolean visibility
  "environment": "production",                // Max 40 chars, pattern: ^[a-zA-Z0-9_-]+$
  "release": "v1.2.3",                       // Release identifier
  "version": "1.0.0"                         // Version identifier
}
```

### 2. span-create / span-update

Creates or updates a span (time-duration observation).

**Required Fields**:
```json
{
  "id": "span-id-123",
  "traceId": "trace-id-123"
}
```

**Optional Fields**:
```json
{
  "name": "Database Query",
  "startTime": "2024-01-15T10:30:45.123Z",
  "endTime": "2024-01-15T10:30:46.456Z",
  "input": {"sql": "SELECT * FROM users"},
  "output": {"rowCount": 42},
  "metadata": {"duration_ms": 1333},
  "level": "DEFAULT",                         // Enum: DEBUG, DEFAULT, WARNING, ERROR
  "statusMessage": "Query executed successfully",
  "parentObservationId": "parent-span-123",
  "version": "1.0.0"
}
```

### 3. generation-create / generation-update

Creates or updates an LLM generation (model interaction).

**Required Fields**:
```json
{
  "id": "generation-id-123",
  "traceId": "trace-id-123"
}
```

**Optional Fields**:
```json
{
  "name": "GPT-4 Completion",
  "startTime": "2024-01-15T10:30:45.123Z",
  "endTime": "2024-01-15T10:30:50.789Z",
  "completionStartTime": "2024-01-15T10:30:45.500Z",
  "model": "gpt-4",
  "modelParameters": {
    "temperature": 0.7,
    "max_tokens": 500,
    "top_p": 0.9
  },
  "input": [
    {"role": "system", "content": "You are a helpful assistant"},
    {"role": "user", "content": "Hello"}
  ],
  "output": "Hello! How can I help you today?",
  "usage": {
    "input": 15,
    "output": 8,
    "total": 23,
    "unit": "TOKENS",
    "input_cost": 0.00045,
    "output_cost": 0.00032,
    "total_cost": 0.00077
  },
  "metadata": {"request_id": "req-789"},
  "level": "DEFAULT",
  "statusMessage": "Completed successfully",
  "parentObservationId": "parent-span-123",
  "promptName": "customer-support-v1",
  "promptVersion": "1.2.0"
}
```

### 4. event-create

Creates a discrete event (instantaneous occurrence).

**Required Fields**:
```json
{
  "id": "event-id-123",
  "traceId": "trace-id-123"
}
```

**Optional Fields**:
```json
{
  "name": "User Clicked Button",
  "startTime": "2024-01-15T10:30:45.123Z",
  "input": {"buttonId": "submit-form"},
  "output": {"success": true},
  "metadata": {"userId": "user-123"},
  "level": "DEFAULT",
  "statusMessage": "Button click processed",
  "parentObservationId": "parent-span-123",
  "version": "1.0.0"
}
```

### 5. score-create

Creates an evaluation score for traces or observations.

**Required Fields**:
```json
{
  "id": "score-id-123",
  "name": "accuracy",
  "value": 0.95                // Can be number, string, or boolean
}
```

**Optional Fields**:
```json
{
  "traceId": "trace-id-123",           // Required if observationId not provided
  "observationId": "generation-id-123", // Required if traceId not provided
  "comment": "High accuracy on factual questions",
  "dataType": "NUMERIC",               // Enum: NUMERIC, CATEGORICAL, BOOLEAN
  "configId": "score-config-123"       // Reference to score configuration
}
```

### 6. sdk-log

Internal SDK logging event (typically auto-generated).

```json
{
  "id": "log-id-123",
  "traceId": "trace-id-123",
  "log": {
    "level": "ERROR",
    "message": "Failed to flush events",
    "context": {"error": "Network timeout"}
  }
}
```

## Special Field Handling

### Usage Details vs Metadata

**Usage Details** (for token/cost tracking):
```json
{
  "usage": {
    "input": 150,              // Required: prompt tokens
    "output": 50,              // Required: completion tokens
    "total": 200,              // Required: total tokens
    "unit": "TOKENS",          // Optional: unit type
    "input_cost": 0.003,       // Optional: input cost
    "output_cost": 0.002,      // Optional: output cost
    "total_cost": 0.005,       // Optional: total cost
    // Additional provider-specific fields:
    "cached_tokens": 25,
    "audio_tokens": 10,
    "reasoning_tokens": 15
  }
}
```

**Metadata** (for application-specific data):
```json
{
  "metadata": {
    "experiment_id": "exp-123",
    "user_segment": "premium",
    "feature_flags": ["new_ui", "beta_model"],
    "custom_metrics": {
      "response_quality": 0.85,
      "latency_ms": 234
    }
  }
}
```

### Multi-modal Content Handling

For images, audio, and documents, use the LangfuseMedia format:

```json
{
  "input": {
    "messages": [
      {
        "role": "user",
        "content": [
          {"type": "text", "text": "What's in this image?"},
          {"type": "image_url", "image_url": {"url": "https://example.com/image.jpg"}}
        ]
      }
    ]
  }
}
```

Or with base64 encoding:
```json
{
  "content": "@@@langfuseMedia:type=image/png|id=media-123|source=base64_data_uri@@@"
}
```

Supported formats:
- **Images**: .png, .jpg, .webp
- **Audio**: .mpeg, .mp3, .wav
- **Documents**: .pdf, text/plain

### OpenAI Format Compatibility

The API automatically handles OpenAI message formats:

```json
{
  "input": [
    {"role": "system", "content": "You are helpful"},
    {"role": "user", "content": "Hello"},
    {"role": "assistant", "content": "Hi there!"}
  ]
}
```

Usage mapping:
- `prompt_tokens` → `input`
- `completion_tokens` → `output`
- `total_tokens` → `total`

## Validation Rules and Constraints

### Field Constraints

| Field | Constraint | Details |
|-------|-----------|---------|
| Trace name | Max 1000 chars | UTF-8 string |
| Environment | Max 40 chars | Pattern: `^[a-zA-Z0-9_-]+$` |
| Batch size | Max 3.5 MB | Total request size |
| Timestamps | ISO 8601 | Format: `YYYY-MM-DDTHH:MM:SS.sssZ` |
| IDs | String | Recommended: UUID v4 format |

### Required Field Validation

**Generation/Span Usage**:
- If `usage` provided, must include: `input`, `output`, `total`
- All token counts must be integers
- Cost fields cannot be null if provided

**Score Values**:
- Must match dataType if specified
- Categorical scores must match configured categories
- Numeric scores must be within min/max if configured

## Batch Processing Limits

### Size Limits
- **Maximum batch size**: 3.5 MB total
- **Recommended batch size**: 100-500 events
- **Processing**: Asynchronous with 15-30 second typical availability

### Rate Limits (per organization)
- **Hobby tier**: 1,000 requests/minute
- **Core tier**: 5,000 requests/minute
- **Pro tier**: 10,000 requests/minute
- **Enterprise**: Custom limits available

## Response Format

The API returns `207 Multi-Status` for all responses:

### Success Response
```json
{
  "successes": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "status": 201
    }
  ],
  "errors": []
}
```

### Error Response
```json
{
  "successes": [],
  "errors": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "status": 400,
      "message": "Invalid request data",
      "error": "[{\"code\":\"invalid_type\",\"expected\":\"number\",\"received\":\"string\",\"path\":[\"body\",\"usage\",\"input\"],\"message\":\"Expected number, received string\"}]"
    }
  ]
}
```

## Common Pitfalls and Solutions

### Timestamp Issues
**Problem**: Events not appearing in dashboard
**Solution**: Check time filter (default shows last 24 hours) and ensure ISO 8601 format

### Type Validation Errors
**Problem**: "Expected number, received string"
**Solution**: Ensure numeric fields (tokens, costs) are not quoted strings

### Missing Required Fields
**Problem**: "field required" errors
**Solution**: Check usage fields are complete when provided

### Event ID Deduplication
**Problem**: Updates not working
**Solution**: Use unique event IDs but same body IDs for updates

### Batch Size Exceeded
**Problem**: 413 or processing failures
**Solution**: Reduce batch size or split into multiple requests

## Complete Examples

### Simple Trace with Generation
```json
{
  "batch": [
    {
      "id": "evt-001",
      "timestamp": "2024-01-15T10:30:45.123Z",
      "type": "trace-create",
      "body": {
        "id": "trace-001",
        "name": "Chat Completion Request",
        "userId": "user-123",
        "metadata": {"session": "chat-789"}
      }
    },
    {
      "id": "evt-002",
      "timestamp": "2024-01-15T10:30:45.456Z",
      "type": "generation-create",
      "body": {
        "id": "gen-001",
        "traceId": "trace-001",
        "model": "gpt-4",
        "input": [{"role": "user", "content": "Hello"}],
        "output": "Hello! How can I help?",
        "usage": {
          "input": 10,
          "output": 6,
          "total": 16
        }
      }
    }
  ]
}
```

### Complex Multi-Span Trace
```json
{
  "batch": [
    {
      "id": "evt-003",
      "timestamp": "2024-01-15T10:30:45.000Z",
      "type": "trace-create",
      "body": {
        "id": "trace-002",
        "name": "RAG Pipeline"
      }
    },
    {
      "id": "evt-004",
      "timestamp": "2024-01-15T10:30:45.100Z",
      "type": "span-create",
      "body": {
        "id": "span-001",
        "traceId": "trace-002",
        "name": "Vector Search",
        "startTime": "2024-01-15T10:30:45.100Z",
        "endTime": "2024-01-15T10:30:45.500Z"
      }
    },
    {
      "id": "evt-005",
      "timestamp": "2024-01-15T10:30:45.600Z",
      "type": "generation-create",
      "body": {
        "id": "gen-002",
        "traceId": "trace-002",
        "parentObservationId": "span-001",
        "model": "gpt-3.5-turbo",
        "input": {"messages": [{"role": "user", "content": "Summarize"}]},
        "output": "Summary of retrieved documents...",
        "usage": {"input": 500, "output": 150, "total": 650}
      }
    }
  ]
}
```

### Score Creation Example
```json
{
  "batch": [
    {
      "id": "evt-006",
      "timestamp": "2024-01-15T10:31:00.000Z",
      "type": "score-create",
      "body": {
        "id": "score-001",
        "traceId": "trace-002",
        "name": "relevance",
        "value": 0.85,
        "dataType": "NUMERIC",
        "comment": "High relevance to user query"
      }
    }
  ]
}
```

This specification provides all the details needed to implement a Langfuse ingestion client, generate comprehensive test cases, validate messages, and handle edge cases properly.