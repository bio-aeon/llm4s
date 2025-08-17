# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# LLM4S Development Guidelines

## Build & Test Commands
```bash
# Build the project (Scala 3)
sbt compile

# Build for all Scala versions (2.13 and 3)
sbt +compile

# Build and test all versions
sbt buildAll

# Run a specific sample 
sbt "samples/runMain org.llm4s.samples.basic.BasicLLMCallingExample"

# Run a sample with Scala 2.13
sbt ++2.13.16 "samples/runMain org.llm4s.samples.basic.BasicLLMCallingExample"

# Run tests for the current Scala version
sbt test

# Run tests for all Scala versions
sbt +test

# Run a single test
sbt "testOnly org.llm4s.shared.WorkspaceAgentInterfaceTest"

# Format code
sbt scalafmtAll

# Cross-compilation testing
sbt testCross
sbt fullCrossTest

# Docker workspace commands
sbt docker:publishLocal
sbt "samples/runMain org.llm4s.samples.workspace.ContainerisedWorkspaceDemo"

# Install pre-commit hooks
./hooks/install.sh
```

## Project Architecture

### Module Structure
- **Main Project** (`src/main/scala/org/llm4s/`): Core LLM framework with provider integrations
- **Shared** (`shared/`): Protocol definitions for workspace agent communication
- **Workspace Runner** (`workspaceRunner/`): Containerized execution service with Bloop BSP integration
- **Samples** (`samples/`): Comprehensive usage examples
- **Cross Test** (`crossTest/`): Cross-compilation verification against published artifacts

### Core Architecture Components

**LLM Provider Abstraction**
- `LLMClient` trait unifies OpenAI, Anthropic, Azure, OpenRouter
- `LLMConnect` factory with environment-driven configuration
- Format: `LLM_MODEL=provider/model-name` (e.g., `anthropic/claude-3-5-sonnet-latest`)

**Tool System**
- `ToolFunction[T, R]` for type-safe tool definitions with JSON schema validation
- `ToolRegistry` manages collections and execution
- `SafeParameterExtractor` handles argument parsing
- Unified interface for local and MCP remote tools

**Agent Framework**
- `Agent` class implements stateful multi-step reasoning
- `AgentState` tracks conversation, tools, status
- Built-in trace logging with configurable backends (Langfuse, console, none)

**MCP Integration**
- `MCPClient` for Model Context Protocol remote tool servers
- Runtime tool discovery with caching
- Seamless integration with local tool system

**Containerized Workspace**
- `ContainerisedWorkspace` provides isolated execution environments
- WebSocket-based communication with real-time streaming
- `WorkspaceAgentInterface` defines file operations and command execution

**Bloop BSP Integration**
- `BloopServerManager` handles automatic server startup and lifecycle
- `BloopCompiler` tool for incremental Scala compilation via BSP
- `SbtProjectAnalyzer` tool for project structure analysis
- Health monitoring with automatic restart on failure
- Graceful shutdown with proper BSP protocol cleanup

**Metals LSP Integration**
- `MetalsServerManager` handles automatic Metals Language Server startup and lifecycle
- `CodeAnalyzer` tool for comprehensive symbol analysis at any position (type info, definition, references count)
- `SymbolSearcher` tool for workspace-wide symbol search and discovery
- `DocumentAnalyzer` tool for extracting file structure, symbols, and organization
- `ReferencesFinder` tool for finding all symbol usages across the workspace (impact analysis)
- `DiagnosticsProvider` tool for accessing compilation errors, warnings, and hints
- Integration with LSP publishDiagnostics for real-time error reporting
- Health monitoring with automatic restart on failure
- Graceful shutdown with proper LSP protocol cleanup

### Key Directories
- `llmconnect/`: LLM provider implementations and abstractions
- `agent/`: Multi-step reasoning framework
- `toolapi/`: Type-safe tool calling system
- `mcp/`: Model Context Protocol integration
- `workspace/`: Containerized execution system
- `trace/`: Execution observability (Langfuse, console, no-op)
- `imagegeneration/`: Image generation support
- `tokens/`: Token counting utilities

## Cross Compilation Guidelines
- The project supports both Scala 2.13.16 and Scala 3.7.1
- Common code goes in `src/main/scala`
- Scala 2.13-specific code goes in `src/main/scala-2.13`
- Scala 3-specific code goes in `src/main/scala-3`
- Always test with both versions: `sbt +test`
- Use the cross-building commands: `buildAll`, `testAll`, `compileAll`
- Cross-test against published artifacts: `sbt testCross`

## Code Style Guidelines
- **Formatting**: Follow `.scalafmt.conf` settings (120 char line length)
- **Imports**: Use curly braces for imports (`import { x, y }`)
- **Error Handling**: Use `Either[LLMError, T]` for operations that may fail
- **Types**: Prefer immutable data structures and pure functions
- **Naming**: Use camelCase for variables/methods, PascalCase for classes/objects
- **Documentation**: Use Asterisk style (`/** ... */`) for ScalaDoc comments
- **Code Organization**: Keep consistent with existing package structure
- **Functional Style**: Prefer pattern matching over if/else statements

## Development Best Practices
- Always fix compilation issues before committing, and run scalafmt

## Environment Setup

### Using .env Files (Recommended)
LLM4S automatically loads configuration from `.env` files in your project root. Create a `.env` file with your settings:

```bash
# .env file example
LLM_MODEL=anthropic/claude-3-5-sonnet-latest
ANTHROPIC_API_KEY=sk-ant-your-key-here

# Tracing configuration
TRACING_MODE=langfuse
LANGFUSE_PUBLIC_KEY=pk-lf-your-key
LANGFUSE_SECRET_KEY=sk-lf-your-secret
LANGFUSE_HOST=https://cloud.langfuse.com
```

### LLM Provider Configuration
Choose one of these LLM provider configurations in your `.env` file or environment variables:

```bash
# OpenAI
LLM_MODEL=openai/gpt-4o
OPENAI_API_KEY=<your_key>

# Anthropic
LLM_MODEL=anthropic/claude-3-5-sonnet-latest
ANTHROPIC_API_KEY=<your_key>

# OpenRouter
LLM_MODEL=openai/gpt-4o
OPENAI_API_KEY=<your_key>
OPENAI_BASE_URL=https://openrouter.ai/api/v1

# Azure OpenAI
LLM_MODEL=azure/gpt-4
AZURE_OPENAI_API_KEY=<your_key>
AZURE_OPENAI_ENDPOINT=<your_endpoint>
```

**Note**: Environment variables take precedence over `.env` file values, so you can override specific settings when needed.

### Tracing Configuration
```bash
# Langfuse (default)
TRACING_MODE=langfuse
LANGFUSE_PUBLIC_KEY=pk-lf-your-key
LANGFUSE_SECRET_KEY=sk-lf-your-secret
LANGFUSE_HOST=https://cloud.langfuse.com  # Optional, defaults to https://cloud.langfuse.com

# Console output
TRACING_MODE=print

# Disable tracing
TRACING_MODE=none
```

## Common Development Patterns

### Tool Function Definition
```scala
val weatherTool = ToolFunction.define[WeatherRequest, WeatherResponse](
  name = "get_weather",
  description = "Get current weather for a location"
) { request =>
  // Implementation
  Right(WeatherResponse(...))
}
```

### Agent Creation
```scala
// Create trace manager
val traceManager = TracingFactory.create()

// Create agent with tracing
val agent = Agent(
  llmClient = llmClient,
  tools = ToolRegistry(weatherTool),
  traceManager = traceManager
)
```

### Tracing Usage
```scala
import org.llm4s.trace.TracingFactory

// Create trace manager based on TRACING_MODE
val traceManager = TracingFactory.create()

// Create a trace for an operation
val trace = traceManager.createTrace(
  name = "agent-operation",
  userId = Some("user123"),
  metadata = Map("operation" -> "weather-query")
)

// Execute with hierarchical spans
trace.span("llm-completion") { span =>
  span.addMetadata("model", "gpt-4")
  span.addMetadata("temperature", 0.7)
  
  // Nested spans for tool calls
  span.span("tool-execution") { toolSpan =>
    toolSpan.addMetadata("tool_name", "weather_tool")
    toolSpan.setInput(weatherRequest)
    val result = weatherTool.execute(weatherRequest)
    toolSpan.setOutput(result)
    result
  }
}

// Finish to ensure events are sent
trace.finish()
```

### Error Types
- `LLMError`: LLM provider communication errors
- `ToolCallError`: Tool execution failures  
- `WorkspaceAgentException`: Workspace operation errors
- `EmbeddingError`: Embedding provider errors
```