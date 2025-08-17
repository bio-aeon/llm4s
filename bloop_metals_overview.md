# Programmatically Interacting with Metals LSP and Bloop BSP from Scala

## Launching Metals and Bloop as Separate Processes

### Metals Server Launch

The most reliable way to launch Metals is through **Coursier**, which handles dependency resolution and JVM configuration:

```scala
import scala.sys.process._
import java.io._

class MetalsLauncher {
  def launchMetals(workspaceDir: String): Process = {
    // Recommended JVM options for optimal performance
    val metalsCommand = Seq(
      "coursier", "launch",
      "--java-opt", "-XX:+UseG1GC",
      "--java-opt", "-XX:+UseStringDeduplication",
      "--java-opt", "-Xss4m",
      "--java-opt", "-Xms100m",
      "org.scalameta:metals_2.13:1.6.0",
      "--main", "scala.meta.metals.Main"
    )
    
    val processBuilder = Process(metalsCommand, new File(workspaceDir))
    processBuilder.run()
  }
}
```

### Bloop Server Launch

Bloop provides a sophisticated launch mechanism through **Bloop Rifle**:

```scala
import bloop.rifle.{BloopRifle, BloopRifleConfig}

val config = BloopRifleConfig.default(
  address = BloopRifleConfig.Address.tcp("127.0.0.1", 8212),
  bloopClassPath = version => Right(downloadBloopClasspath(version)),
  workingDir = new File(".")
)

// Check if server is running
val running = BloopRifle.check(config)

// Start server if not running
val server = BloopRifle.start(config)

// Establish BSP connection
val bspConnection = BloopRifle.bsp(config)
```

## LSP Communication with Metals

### Protocol Implementation Using LSP4J

**LSP4J** is the de facto standard for JVM-based LSP implementations:

```scala
// Add dependency: "org.eclipse.lsp4j" % "org.eclipse.lsp4j" % "0.24.0"

import org.eclipse.lsp4j._
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.{LanguageServer, LanguageClient}

class MyLanguageClient extends LanguageClient {
  override def publishDiagnostics(params: PublishDiagnosticsParams): Unit = {
    // Handle diagnostics from server
    params.getDiagnostics.forEach { diagnostic =>
      println(s"${diagnostic.getSeverity}: ${diagnostic.getMessage}")
    }
  }
  
  // Implement other client methods...
}

// Launch and connect
val serverProcess = new ProcessBuilder("metals").start()
val client = new MyLanguageClient()
val launcher = LSPLauncher.createClientLauncher(
  client, 
  serverProcess.getInputStream, 
  serverProcess.getOutputStream
)

val server = launcher.getRemoteProxy
launcher.startListening()
```

### Core LSP Operations

**Find Symbol (textDocument/definition)**:
```scala
val params = new DefinitionParams(
  new TextDocumentIdentifier("file:///path/to/file.scala"),
  new Position(10, 20)
)
val definitions = server.getTextDocumentService.definition(params)
```

**Find Usages (textDocument/references)**:
```scala
val params = new ReferenceParams(
  new TextDocumentIdentifier("file:///path/to/file.scala"),
  new Position(10, 20),
  new ReferenceContext(true) // include declaration
)
val references = server.getTextDocumentService.references(params)
```

**Code Completion**:
```scala
val params = new CompletionParams(
  new TextDocumentIdentifier("file:///path/to/file.scala"),
  new Position(10, 5)
)
val completions = server.getTextDocumentService.completion(params)
```

## BSP Communication with Bloop

### Protocol Implementation Using BSP4J

**BSP4J** provides the standard BSP implementation:

```scala
// Add dependency: "ch.epfl.scala" % "bsp4j" % "2.2.0-M4"

import ch.epfl.scala.bsp4j._
import org.eclipse.lsp4j.jsonrpc.Launcher

class MyBuildClient extends BuildClient {
  def onBuildLogMessage(params: LogMessageParams): Unit = {
    println(s"Build log: ${params.getMessage}")
  }
  
  def onBuildPublishDiagnostics(params: PublishDiagnosticsParams): Unit = {
    params.getDiagnostics.forEach { diagnostic =>
      println(s"Diagnostic: ${diagnostic.getMessage}")
    }
  }
  
  def onBuildTaskStart(params: TaskStartParams): Unit = {
    println(s"Task started: ${params.getMessage}")
  }
  
  def onBuildTaskFinish(params: TaskFinishParams): Unit = {
    println(s"Task finished: ${params.getStatus}")
  }
  
  // Implement other client methods...
}
```

### Build Operations

**Compile**:
```scala
val targets = List(new BuildTargetIdentifier("file:///project?id=main"))
val compileParams = new CompileParams(targets.asJava)
val result = server.buildTargetCompile(compileParams)
```

**Incremental Compile**: Bloop automatically handles incremental compilation using Zinc. Simply send compile requests, and Bloop will detect changed sources and minimize recompilation.

**Import/Build Structure**:
```scala
val targetsResult = server.workspaceBuildTargets()
targetsResult.thenAccept { result =>
  result.getTargets.forEach { target =>
    println(s"Build target: ${target.getDisplayName}")
  }
}
```

## Server Process Lifecycle Management

### Robust Process Management Pattern

```scala
class ServerLifecycleManager {
  private var process: Option[Process] = None
  private val shutdownHook = new Thread(() => gracefulShutdown())
  
  def start(command: Seq[String]): Future[Unit] = Future {
    process = Some(Process(command).run())
    Runtime.getRuntime.addShutdownHook(shutdownHook)
    
    // Monitor process health
    startHealthMonitoring()
  }
  
  private def startHealthMonitoring(): Unit = {
    val scheduler = Executors.newScheduledThreadPool(1)
    scheduler.scheduleAtFixedRate(() => {
      if (!isProcessAlive) {
        logger.warn("Process died, restarting...")
        restartWithBackoff()
      }
    }, 30, 30, TimeUnit.SECONDS)
  }
  
  def gracefulShutdown(): Future[Unit] = {
    // For LSP: shutdown request → exit notification
    // For BSP: build/shutdown → build/exit
    sendShutdownRequest().flatMap { _ =>
      sendExitNotification()
    }.map { _ =>
      process.foreach(_.destroy())
      process = None
    }
  }
}
```

### Health Check Implementation

For LSP servers:
```scala
def checkLSPHealth(server: LanguageServer): Future[Boolean] = {
  // Use initialize request as health check
  val initParams = new InitializeParams()
  server.initialize(initParams)
    .thenApply(_ => true)
    .exceptionally(_ => false)
    .toScala
}
```

For BSP servers:
```scala
def checkBSPHealth(server: BuildServer): Future[Boolean] = {
  // Use workspace/buildTargets as health check
  server.workspaceBuildTargets()
    .thenApply(_ => true)
    .exceptionally(_ => false)
    .toScala
}
```

## Libraries and Integration Patterns

### Core Libraries

1. **LSP4J** (org.eclipse.lsp4j): Standard LSP implementation for JVM
2. **BSP4J** (ch.epfl.scala:bsp4j): Standard BSP implementation
3. **Bloop Rifle** (ch.epfl.scala:bloop-rifle): High-level Bloop launcher
4. **Circe** or **Play JSON**: For custom JSON message handling

### Integration Architecture

The typical integration pattern follows this architecture:

```
Your Scala Process
    ├── LSP Client (LSP4J) ←→ Metals (LSP Server)
    │                              │
    │                              ↓ (BSP Client)
    └── BSP Client (BSP4J) ←→ Bloop (BSP Server)
```

### Complete Integration Example

```scala
class MetalsBloopIntegration {
  private val lspClient = new LSPClient()
  private val bspClient = new BSPClient()
  
  def initialize(projectPath: String): Future[(LanguageServer, BuildServer)] = {
    for {
      // Start Bloop first (build server)
      bloopConfig <- Future.successful(createBloopConfig(projectPath))
      _ <- BloopRifle.start(bloopConfig)
      bspConnection <- Future.successful(BloopRifle.bsp(bloopConfig))
      
      // Start Metals (language server)
      metalsProcess <- startMetalsProcess(projectPath)
      lspConnection <- connectToMetals(metalsProcess)
      
      // Initialize both servers
      _ <- initializeLSP(lspConnection, projectPath)
      _ <- initializeBSP(bspConnection, projectPath)
    } yield (lspConnection, bspConnection)
  }
  
  private def connectToMetals(process: Process): Future[LanguageServer] = {
    val launcher = LSPLauncher.createClientLauncher(
      lspClient,
      process.getInputStream,
      process.getOutputStream
    )
    
    Future {
      launcher.startListening()
      launcher.getRemoteProxy
    }
  }
}
```

## Best Practices and Recommendations

### Process Management
- Use **supervisor patterns** with automatic restart and exponential backoff
- Implement proper **health monitoring** with periodic checks
- Handle **graceful shutdown** following protocol specifications
- Use **NuProcess** library for better process management on Unix systems

### Error Handling
- Implement **circuit breaker** patterns for failing servers
- Log all server stderr output for debugging
- Handle connection failures with automatic reconnection
- Monitor resource usage and implement limits

### Performance Optimization
- Reuse server connections when possible
- Leverage Bloop's incremental compilation capabilities
- Configure appropriate JVM memory settings for both servers
- Use connection pooling for multiple workspace support

### Missing Ecosystem Components

The research reveals that while core protocol implementations are mature, the ecosystem lacks:
- High-level Scala-idiomatic wrapper libraries over LSP4J/BSP4J
- Functional programming-friendly APIs using cats-effect or ZIO
- Comprehensive testing utilities for LSP/BSP integrations

## Example Projects and Resources

1. **scalameta/metals** - Reference implementation of an LSP server
2. **scalacenter/bloop** - Reference implementation of a BSP server
3. **Build-Server-Protocol-Example** - Complete BSP client example
4. **metals-vscode** - Production LSP client implementation
5. **nvim-metals** - Neovim integration showing LSP patterns

The ecosystem is production-ready with strong foundations in LSP4J and BSP4J, though opportunities exist for creating higher-level abstractions to simplify integration efforts.