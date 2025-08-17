# Scala SWE Agent Development Plan

## Overview

This document outlines the development plan for building a sophisticated Scala-focused Software Engineering (SWE) agent based on the existing LLM4S agent framework. The goal is to create an agent capable of effectively navigating, understanding, and modifying Scala codebases with semantic awareness and modern development tool integration.

## Current Foundation Analysis

### Strengths of Existing System
- ✅ **Mature Agent Architecture**: Multi-step reasoning with state management
- ✅ **Containerized Workspace**: Secure, isolated execution environment
- ✅ **WebSocket Communication**: Real-time bidirectional communication
- ✅ **Scala Toolchain**: Pre-installed SBT, Scala 2.13/3.x support
- ✅ **Flexible Tool System**: Type-safe, extensible tool framework
- ✅ **Comprehensive File Operations**: Read, write, modify, search capabilities
- ✅ **Command Execution**: Shell command execution with streaming output

### Current Limitations
- ❌ **No Semantic Code Understanding**: No AST parsing or type information
- ❌ **Basic Build Integration**: Limited SBT project analysis
- ❌ **Missing IDE Features**: No go-to-definition, find-references, refactoring
- ❌ **No Compilation Intelligence**: No error parsing or quick fixes
- ❌ **Limited Testing Integration**: No framework-specific test execution
- ❌ **No Code Quality Tools**: Missing scalafmt, scalafix, linting

## Architecture Design: Bloop + Metals Integration

### Core Components

```
┌─────────────────────────────────────────────────────────────┐
│                    LLM4S SWE Agent                         │
├─────────────────────────────────────────────────────────────┤
│  Agent Core (existing)                                     │
│  ├── Multi-step reasoning                                  │
│  ├── State management                                      │
│  └── Tool orchestration                                    │
├─────────────────────────────────────────────────────────────┤
│  Enhanced Tool System                                      │
│  ├── Existing workspace tools                              │
│  ├── Scala-specific tools (new)                           │
│  └── IDE-feature tools (new)                              │
├─────────────────────────────────────────────────────────────┤
│  Containerized Workspace (enhanced)                        │
│  ├── Bloop Build Server                                    │
│  ├── Metals Language Server                                │
│  ├── Enhanced development tools                            │
│  └── WebSocket communication                               │
└─────────────────────────────────────────────────────────────┘
```

### Bloop Integration Strategy

**Bloop Build Server** provides:
- Fast, incremental compilation
- Build Server Protocol (BSP) support  
- Multi-project workspace support
- Dependency resolution and management
- Test execution with detailed reporting

**Integration Architecture**:
```scala
// New tools to be implemented
trait BloopTools {
  def compileProject(project: String): Either[CompilationError, CompilationResult]
  def runTests(project: String, testClass: Option[String]): Either[TestError, TestResults]
  def analyzeDependencies(project: String): Either[DependencyError, DependencyTree]
  def cleanProject(project: String): Either[CleanError, Unit]
}
```

### Metals Integration Strategy

**Metals Language Server** provides:
- Semantic code analysis
- Symbol navigation (go-to-definition, find-references)
- Code completion and hover information
- Diagnostic reporting (errors, warnings)
- Refactoring capabilities

**Integration Architecture**:
```scala
// New tools to be implemented  
trait MetalsTools {
  def getSymbolInfo(file: String, position: Position): Either[MetalsError, SymbolInfo]
  def findDefinition(file: String, position: Position): Either[MetalsError, Location]
  def findReferences(file: String, position: Position): Either[MetalsError, List[Location]]
  def getCompletions(file: String, position: Position): Either[MetalsError, List[Completion]]
  def performRefactoring(refactoring: RefactoringRequest): Either[MetalsError, RefactoringResult]
}
```

## Implementation Roadmap

### Phase 1: Enhanced Container Environment (Weeks 1-2)

#### Container Setup Enhancement
- **Install Bloop**: Fast Scala build server
- **Install Metals**: Scala Language Server Protocol implementation
- **Install Additional Tools**: Git, coursier, scalafmt, scalafix
- **Configure BSP**: Build Server Protocol for IDE integration

#### Container Configuration
✅ **COMPLETED**: Enhanced Docker configuration with Coursier installation and Scala development tools. See `build.sbt` for current implementation.

### Phase 2: Build System Integration (Weeks 3-4)

#### Bloop Integration Tools
✅ **COMPLETED**: BSP tools implemented using SimpleTool interface with BSP4J and LSP4J dependencies. Core tools include:
- `SbtProjectAnalyzer`: Project structure analysis via BSP
- `BloopCompiler`: Compilation with diagnostic reporting  
- Integrated into workspace runner with WebSocket protocol support
- See `workspaceRunner/src/main/scala/org/llm4s/runner/tools/` for implementations

#### SBT Project Understanding
- Parse `build.sbt` files for project structure
- Extract dependency information
- Understand cross-compilation settings
- Identify subprojects and their relationships

### Phase 3: Semantic Code Analysis (Weeks 5-7)

#### Metals Language Server Integration
```scala
// Core dependencies for LSP integration
// Add to build.sbt:
//   "org.eclipse.lsp4j" % "org.eclipse.lsp4j" % "0.24.0"

// Semantic analysis tools using LSP4J
case class SymbolNavigator() extends ToolFunction[NavigationRequest, NavigationResult] {
  // Uses textDocument/definition and textDocument/references
  // Supports cross-project navigation
}

case class TypeInformationProvider() extends ToolFunction[TypeRequest, TypeInfo] {
  // Leverages textDocument/hover for type information
  // Provides signature help and documentation
}

case class CodeCompletionProvider() extends ToolFunction[CompletionRequest, CompletionList] {
  // Uses textDocument/completion with intelligent filtering
  // Supports import completions and snippets
}

case class DiagnosticProvider() extends ToolFunction[DiagnosticRequest, DiagnosticList] {
  // Processes publishDiagnostics notifications
  // Categorizes errors, warnings, and hints
}
```

#### Advanced Code Understanding
- AST-based code analysis
- Symbol resolution and type information
- Import analysis and suggestion
- Code complexity metrics

### Phase 4: Development Workflow Tools (Weeks 8-10)

#### Code Quality Integration
```scala
// Code quality tools
case class ScalafmtFormatter() extends ToolFunction[FormatRequest, FormatResult]
case class ScalafixLinter() extends ToolFunction[LintRequest, LintResult]
case class CodeRefactorer() extends ToolFunction[RefactorRequest, RefactorResult]
```

#### Testing Framework Integration
- ScalaTest integration with detailed reporting
- Test discovery and execution
- Coverage analysis and reporting
- Property-based testing support

### Phase 5: Advanced SWE Capabilities (Weeks 11-12)

#### Intelligent Code Generation
```scala
// Advanced SWE tools
case class TestGenerator() extends ToolFunction[TestGenRequest, TestGenResult]
case class CodeGenerator() extends ToolFunction[CodeGenRequest, CodeGenResult]
case class RefactoringEngine() extends ToolFunction[RefactoringRequest, RefactoringResult]
```

#### Project-Level Operations
- Multi-file refactoring
- Dependency upgrade assistance
- Migration tool assistance
- Documentation generation

## Detailed Todo List

### Phase 1: Container Enhancement ✅ COMPLETED
- [x] **P1.1**: Add Coursier installation to Docker configuration
- [x] **P1.2**: Install Bloop via Coursier (recommended approach)  
- [x] **P1.3**: Install Metals via Coursier with optimal JVM settings
- [x] **P1.4**: Install development tools (git, tree, jq, python3)
- [x] **P1.5**: Configure Bloop server environment variables and directories
- [x] **P1.6**: Add BSP4J and LSP4J dependencies to build.sbt
- [ ] **P1.7**: Create server lifecycle management utilities
- [ ] **P1.8**: Test Metals LSP server startup with proper JVM options
- [ ] **P1.9**: Test Bloop BSP server startup and connection
- [ ] **P1.10**: Create health monitoring and auto-restart mechanisms

### Phase 2: Build System Integration ✅ COMPLETED
- [x] **P2.1**: Implement Bloop Rifle integration for server management (Skipped - using BSP4J directly)
- [x] **P2.2**: Create BSP client wrapper using BSP4J (Simplified tool interface)
- [x] **P2.3**: Implement `SbtProjectAnalyzer` with workspace/buildTargets
- [x] **P2.4**: Create `BloopCompiler` tool using buildTarget/compile
- [x] **P2.5**: Implement automatic Bloop server startup and lifecycle management
- [x] **P2.6**: Add BSP health monitoring with automatic restart
- [x] **P2.7**: Create comprehensive test suite for BSP integration
- [x] **P2.8**: Integrate BSP tools into workspace runner with WebSocket protocol
- [x] **P2.9**: Implement graceful shutdown with proper BSP protocol cleanup
- [ ] **P2.10**: Develop `BloopTester` tool with buildTarget/test (Future enhancement)
- [ ] **P2.11**: Build `DependencyAnalyzer` using buildTarget/dependencySources (Future enhancement)

### Phase 3: Semantic Code Analysis ✅ COMPLETED  
- [x] **P3.1**: Implement LSP client using LSP4J with proper launcher
- [x] **P3.2**: Create process management for Metals server lifecycle
- [x] **P3.3**: Implement LSP initialize/initialized handshake
- [x] **P3.4**: Create `CodeAnalyzer` for comprehensive symbol analysis (type info, definition, references)
- [x] **P3.5**: Build `SymbolSearcher` for workspace-wide symbol search
- [x] **P3.6**: Develop `DocumentAnalyzer` for file structure and symbol extraction
- [x] **P3.7**: Implement `DiagnosticsProvider` with publishDiagnostics handling
- [x] **P3.8**: Add `ReferencesFinder` for find-all-references functionality
- [x] **P3.9**: Implement automatic Metals server startup and lifecycle management
- [x] **P3.10**: Add LSP health monitoring with automatic restart
- [x] **P3.11**: Create comprehensive test suite for LSP integration
- [x] **P3.12**: Integrate LSP tools into workspace runner with agent-focused design

### Phase 4: Development Workflow
- [ ] **P4.1**: Integrate scalafmt for code formatting
- [ ] **P4.2**: Add scalafix for linting and style enforcement  
- [ ] **P4.3**: Create automated refactoring tools
- [ ] **P4.4**: Implement ScalaTest integration with detailed reporting
- [ ] **P4.5**: Add test discovery and organization
- [ ] **P4.6**: Create coverage analysis and reporting
- [ ] **P4.7**: Implement property-based testing support
- [ ] **P4.8**: Add benchmark integration and performance testing

### Phase 5: Advanced Features
- [ ] **P5.1**: Create intelligent test generation based on code analysis
- [ ] **P5.2**: Implement code generation templates and scaffolding
- [ ] **P5.3**: Build advanced refactoring engine for large-scale changes
- [ ] **P5.4**: Add migration assistance tools
- [ ] **P5.5**: Create documentation generation from code
- [ ] **P5.6**: Implement dependency upgrade recommendation system
- [ ] **P5.7**: Add code review and quality metrics
- [ ] **P5.8**: Create project health assessment tools

### Infrastructure & Testing
- [ ] **I.1**: Create comprehensive test suite for LSP/BSP integrations
- [ ] **I.2**: Add integration tests with sample Scala projects (SBT, Mill)
- [ ] **I.3**: Implement server health monitoring with circuit breakers
- [ ] **I.4**: Create performance benchmarks for LSP/BSP operations
- [ ] **I.5**: Add graceful shutdown handling following LSP/BSP protocols
- [ ] **I.6**: Implement automatic server restart with exponential backoff
- [ ] **I.7**: Create debugging tools for protocol message inspection
- [ ] **I.8**: Add comprehensive logging for server communication
- [ ] **I.9**: Implement connection pooling for multi-workspace support
- [ ] **I.10**: Create configuration management for server options
- [ ] **I.11**: Add resource monitoring and memory limit enforcement
- [ ] **I.12**: Create documentation with protocol flow diagrams

## Technical Implementation Notes

### Required Dependencies
✅ **COMPLETED**: BSP4J (2.1.1) and LSP4J (0.24.0) dependencies added to workspaceRunner module. See `build.sbt` for current configuration.

### BSP Integration Architecture
✅ **COMPLETED**: Full BSP integration with automatic Bloop server lifecycle management. `BloopServerManager` handles startup, health monitoring, and graceful shutdown. BSP tools (`BloopCompiler`, `SbtProjectAnalyzer`) use live BSP connections via BSP4J. See `workspaceRunner/src/main/scala/org/llm4s/runner/` for implementation.

### Metals Integration Architecture  
✅ **COMPLETED**: Full LSP integration with Metals using LSP4J launcher and coursier process management. `MetalsServerManager` handles automatic startup with optimized JVM settings, health monitoring, and graceful shutdown. Agent-focused LSP tools provide semantic code analysis. See `workspaceRunner/src/main/scala/org/llm4s/runner/tools/` for tool implementations.

### Server Lifecycle Management
✅ **COMPLETED**: Complete server lifecycle management with health monitoring, graceful shutdown, and automatic restart. Implementation includes proper LSP/BSP protocol compliance for initialization and cleanup. Both servers start automatically when workspace runner initializes.

### Tool Integration Pattern
✅ **COMPLETED**: SimpleTool interface fully implemented with live BSP/LSP server integration. All tools use unified architecture with WebSocket protocol support. Registry includes both BSP and LSP tools for comprehensive Scala development capabilities.

## Success Metrics

### Phase 1 Success Criteria
- ✅ Bloop server starts successfully in container
- ✅ Metals language server initializes properly
- ✅ All development tools are accessible
- ✅ BSP connection established and functional

### Phase 2 Success Criteria ✅ COMPLETED
- ✅ Can parse and understand any standard Scala project structure
- ✅ Compilation works with proper error reporting via BSP
- ✅ Automatic Bloop server startup and lifecycle management
- ✅ BSP tools integrated with WebSocket protocol for agent communication
- ✅ Health monitoring with automatic restart capabilities
- ✅ Comprehensive test suite validates BSP integration

### Phase 3 Success Criteria ✅ COMPLETED
- ✅ Semantic code analysis via Metals Language Server 
- ✅ Symbol search and discovery across entire workspace
- ✅ Comprehensive symbol analysis (type info, definition, references)
- ✅ File structure analysis and symbol extraction
- ✅ Real-time diagnostics integration via LSP publishDiagnostics
- ✅ Agent-focused tool design for programmatic code understanding
- ✅ Automatic Metals server startup and lifecycle management

### Phase 4 Success Criteria
- ✅ Code formatting maintains project style consistently
- ✅ Linting identifies and fixes style violations
- ✅ Refactoring operations maintain code correctness
- ✅ Test generation creates meaningful test cases

### Phase 5 Success Criteria
- ✅ Agent can autonomously fix compilation errors
- ✅ Agent can implement feature requests with tests
- ✅ Agent can perform large-scale refactoring safely
- ✅ Agent can upgrade dependencies and handle breaking changes

## Risk Assessment and Mitigation

### Technical Risks
1. **Bloop/Metals Integration Complexity**: Mitigated by incremental development and thorough testing
2. **Container Resource Usage**: Monitored with resource limits and optimization
3. **LSP Communication Reliability**: Handled with proper error handling and reconnection logic
4. **Tool Performance**: Addressed with caching and optimization strategies

### Development Risks
1. **Scope Creep**: Managed with clear phase boundaries and success criteria
2. **Tool Compatibility Issues**: Mitigated by using stable, well-tested tool versions
3. **Testing Complexity**: Addressed with comprehensive test project setup

## Future Enhancements

### Beyond Phase 5
- **Multi-Repository Support**: Handle complex project dependencies
- **CI/CD Integration**: Integrate with build pipelines and deployment
- **Team Collaboration**: Support for code review and team workflows
- **Performance Optimization**: Advanced caching and incremental processing
- **Custom Rule Sets**: User-defined linting and style rules
- **IDE Integration**: Direct integration with popular IDEs

## Key Implementation Insights from bloop_metals_overview.md

### Critical Technical Details
1. **Coursier is Essential**: Use coursier for launching Metals with proper JVM optimization
2. **Bloop Rifle Recommended**: Higher-level abstraction over raw BSP for server management
3. **Protocol Compliance**: Proper LSP/BSP handshakes and shutdown sequences are crucial
4. **Health Monitoring**: Use protocol-specific health checks (LSP initialize, BSP buildTargets)
5. **Process Lifecycle**: Implement supervisor patterns with graceful shutdown and restart
6. **Performance Tuning**: Specific JVM options for optimal Metals/Bloop performance

### Production-Ready Considerations
- **Error Recovery**: Circuit breaker patterns with exponential backoff
- **Resource Management**: Memory limits and monitoring for server processes
- **Debugging Support**: Protocol message inspection and comprehensive logging
- **Multi-Workspace**: Connection pooling and resource sharing strategies

### Best Practices from Ecosystem Research

**Process Management**:
- Use **Coursier for Metals** launch with optimal JVM settings
- **Bloop Rifle Integration** for robust server management
- **Supervisor patterns** with automatic restart and exponential backoff
- **Health monitoring** using LSP initialize and BSP buildTargets as health checks
- **Graceful shutdown** following LSP (shutdown → exit) and BSP (build/shutdown → build/exit) protocols
- **NuProcess library** for better process management on Unix systems

**Error Handling**:
- **Circuit breaker** patterns for failing servers with automatic recovery
- **Server stderr monitoring** for debugging server issues
- **Protocol compliance** ensuring proper LSP/BSP message handling
- **Resource monitoring** with memory limits and usage tracking

**Performance Optimization**:
- **Connection reuse** for multiple operations
- **Incremental compilation** leveraging Bloop's Zinc integration
- **JVM tuning** with recommended settings for both Metals and Bloop
- **Connection pooling** for multi-workspace scenarios
- **Caching strategies** for frequently accessed data

### Ecosystem Gaps We Can Address
The research identifies opportunities our implementation can fill:
- **High-level Scala-idiomatic wrappers** over LSP4J/BSP4J
- **Functional programming APIs** using cats-effect or ZIO patterns
- **Comprehensive testing utilities** for LSP/BSP integrations
- **Better error handling abstractions** for protocol communication

This plan provides a comprehensive, production-ready roadmap for transforming the existing LLM4S agent into a sophisticated Scala SWE agent. The integration follows proven patterns from the Scala ecosystem and leverages the robust LSP4J/BSP4J foundations while addressing the identified gaps in high-level abstractions and error handling.