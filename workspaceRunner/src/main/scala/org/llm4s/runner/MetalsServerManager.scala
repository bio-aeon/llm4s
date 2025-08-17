package org.llm4s.runner

import scala.annotation.nowarn
import org.eclipse.lsp4j._
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.services.{ LanguageClient, LanguageServer }
import org.slf4j.LoggerFactory

import java.nio.file.Paths
import java.util.concurrent.{ CompletableFuture, Executors, ScheduledExecutorService, TimeUnit }
import java.util.{ Arrays => JArrays }
import scala.concurrent.{ ExecutionContext, Future }
import scala.sys.process.Process
import scala.util.Try

/**
 * Manages the lifecycle of a Metals Language Server, including startup, health monitoring,
 * and LSP client connections.
 */
class MetalsServerManager(
  workspacePath: String,
  @annotation.nowarn("cat=unused") diagnosticsProvider: Option[org.llm4s.runner.tools.DiagnosticsProvider] = None
)(implicit ec: ExecutionContext) {

  private val logger                              = LoggerFactory.getLogger(getClass)
  private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(2)

  // Metals server configuration
  private val healthCheckIntervalSeconds = 45
  private val metalsClientName           = "llm4s-workspace-runner"

  @volatile private var serverProcess: Option[Process]                 = None
  @volatile private var languageServer: Option[LanguageServer]         = None
  @volatile private var launcher: Option[Launcher[LanguageServer]]     = None
  @volatile private var isShuttingDown                                 = false
  @volatile private var isInitialized                                  = false
  @volatile private var clientCapabilities: Option[ClientCapabilities] = None

  /**
   * Starts the Metals Language Server using coursier and waits for it to be ready.
   */
  def startServer(): Future[Unit] = {
    logger.info("Starting Metals Language Server...")

    Future {
      // Build coursier command to launch Metals with optimal JVM settings
      val metalsCommand = List(
        "coursier",
        "launch",
        "org.scalameta:metals_2.13:1.3.5",
        "--",
        "--client-command",
        metalsClientName
      )

      logger.info(s"Launching Metals with command: ${metalsCommand.mkString(" ")}")

      // For now, create a placeholder setup - full implementation would need proper IO handling
      val process = Process(metalsCommand).run()
      serverProcess = Some(process)
      logger.info("Metals server process started")

      // Note: This is a simplified version - real implementation would need proper stdin/stdout handling
      // For the demo, we'll create a minimal setup
      throw new UnsupportedOperationException(
        "Full Metals integration requires proper IO pipe handling - this is a demo placeholder"
      )
    }.recover { case ex =>
      logger.error("Failed to start Metals Language Server", ex)
      throw new RuntimeException("Metals server startup failed", ex)
    }
  }

  /**
   * Initializes the LSP connection with proper client capabilities.
   */
  @annotation.nowarn("cat=unused")
  private def initializeLspConnection(server: LanguageServer): Unit = {
    logger.info("Initializing LSP connection to Metals...")

    // Set up client capabilities
    val capabilities = new ClientCapabilities()

    // Text document capabilities
    val textDocumentCapabilities = new TextDocumentClientCapabilities()

    // Definition capabilities
    val definitionCapabilities = new DefinitionCapabilities()
    definitionCapabilities.setLinkSupport(true)
    textDocumentCapabilities.setDefinition(definitionCapabilities)

    // Hover capabilities
    val hoverCapabilities = new HoverCapabilities()
    hoverCapabilities.setContentFormat(JArrays.asList(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT))
    textDocumentCapabilities.setHover(hoverCapabilities)

    // Completion capabilities
    val completionCapabilities     = new CompletionCapabilities()
    val completionItemCapabilities = new CompletionItemCapabilities()
    completionItemCapabilities.setSnippetSupport(true)
    completionItemCapabilities.setDocumentationFormat(JArrays.asList(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT))
    completionCapabilities.setCompletionItem(completionItemCapabilities)
    textDocumentCapabilities.setCompletion(completionCapabilities)

    // References capabilities
    textDocumentCapabilities.setReferences(new ReferencesCapabilities())

    // Document symbol capabilities
    textDocumentCapabilities.setDocumentSymbol(new DocumentSymbolCapabilities())

    // Workspace symbol capabilities
    val workspaceCapabilities = new WorkspaceClientCapabilities()
    workspaceCapabilities.setSymbol(new SymbolCapabilities())

    capabilities.setTextDocument(textDocumentCapabilities)
    capabilities.setWorkspace(workspaceCapabilities)

    this.clientCapabilities = Some(capabilities)

    // Initialize request
    val initializeParams = new InitializeParams()
    initializeParams.setRootUri(Paths.get(workspacePath).toUri.toString): @nowarn("cat=deprecation")
    initializeParams.setClientInfo(new ClientInfo(metalsClientName, "1.0.0"))
    initializeParams.setCapabilities(capabilities)

    try {
      val initializeResult = server.initialize(initializeParams).get(60, TimeUnit.SECONDS)
      logger.info(
        s"LSP initialized: ${initializeResult.getServerInfo.getName} ${initializeResult.getServerInfo.getVersion}"
      )

      // Send initialized notification
      server.initialized(new InitializedParams())
      isInitialized = true

      logger.info("Metals LSP connection fully initialized")
    } catch {
      case ex: Exception =>
        logger.error("Failed to initialize LSP connection", ex)
        throw new RuntimeException("LSP initialization failed", ex)
    }
  }

  /**
   * Checks if the Metals server is running and responsive.
   */
  def isServerHealthy(): Boolean =
    languageServer.exists { server =>
      Try {
        // Simple health check - try to get server capabilities
        val request = new WorkspaceSymbolParams("")
        val future  = server.getWorkspaceService.symbol(request)
        future.get(5, TimeUnit.SECONDS)
        true
      }.getOrElse(false)
    }

  /**
   * Gets the current Language Server, if available and initialized.
   */
  def getLanguageServer(): Option[LanguageServer] =
    if (isInitialized) languageServer else None

  /**
   * Gets the client capabilities that were negotiated during initialization.
   */
  def getClientCapabilities(): Option[ClientCapabilities] = clientCapabilities

  /**
   * Gracefully shuts down the Metals server and cleans up resources.
   */
  def shutdown(): Future[Unit] = {
    logger.info("Shutting down Metals server manager...")
    isShuttingDown = true

    Future {
      // Shutdown LSP connection
      languageServer.foreach { server =>
        try {
          val shutdownFuture = server.shutdown()
          shutdownFuture.get(10, TimeUnit.SECONDS)
          server.exit()
          logger.info("LSP server shutdown completed")
        } catch {
          case ex: Exception =>
            logger.warn("Error during LSP server shutdown", ex)
        }
      }

      // Stop launcher
      launcher.foreach { _ =>
        try
          // The launcher should stop automatically when the server exits
          logger.debug("LSP launcher stopped")
        catch {
          case ex: Exception =>
            logger.warn("Error stopping LSP launcher", ex)
        }
      }

      // Stop server process
      serverProcess.foreach { process =>
        try {
          process.destroy()
          // Wait a moment for graceful shutdown
          Thread.sleep(2000)
          logger.info("Metals server process terminated")
        } catch {
          case ex: Exception =>
            logger.warn("Error during server process shutdown", ex)
        }
      }

      // Shutdown scheduler
      scheduler.shutdown()
      if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        scheduler.shutdownNow()
      }

      // Clear state
      languageServer = None
      launcher = None
      serverProcess = None
      isInitialized = false
      clientCapabilities = None

      logger.info("Metals server manager shutdown completed")
    }
  }

  @annotation.nowarn("cat=unused")
  private def startHealthMonitoring(): Unit =
    scheduler.scheduleAtFixedRate(
      () =>
        if (!isShuttingDown) {
          try
            if (!isServerHealthy()) {
              logger.warn("Metals server health check failed - attempting restart")
              restartServer()
            } else {
              logger.debug("Metals server health check passed")
            }
          catch {
            case ex: Exception =>
              logger.error("Error during Metals server health check", ex)
          }
        },
      healthCheckIntervalSeconds,
      healthCheckIntervalSeconds,
      TimeUnit.SECONDS
    )

  private def restartServer(): Unit = {
    logger.info("Restarting Metals server...")

    // Clear current state
    languageServer = None
    launcher = None
    isInitialized = false
    serverProcess.foreach(_.destroy())

    // Wait a moment for cleanup
    Thread.sleep(3000)

    // Restart
    startServer().recover { case ex =>
      logger.error("Failed to restart Metals server", ex)
    }
  }
}

/**
 * Simple LSP client implementation for Metals communication.
 */
class SimpleLspClient(diagnosticsProvider: Option[org.llm4s.runner.tools.DiagnosticsProvider] = None)
    extends LanguageClient {
  private val logger = LoggerFactory.getLogger(getClass)

  override def showMessage(messageParams: MessageParams): Unit =
    logger.info(s"Metals Message [${messageParams.getType}]: ${messageParams.getMessage}")

  override def showMessageRequest(requestParams: ShowMessageRequestParams): CompletableFuture[MessageActionItem] = {
    logger.info(s"Metals Message Request [${requestParams.getType}]: ${requestParams.getMessage}")
    // For now, just return the first action if available
    val action = Option(requestParams.getActions)
      .flatMap(actions => if (actions.isEmpty) None else Some(actions.get(0)))
      .orNull
    CompletableFuture.completedFuture(action)
  }

  override def logMessage(messageParams: MessageParams): Unit =
    logger.debug(s"Metals Log [${messageParams.getType}]: ${messageParams.getMessage}")

  override def telemetryEvent(obj: Any): Unit =
    logger.debug(s"Metals Telemetry: $obj")

  override def publishDiagnostics(diagnostics: PublishDiagnosticsParams): Unit = {
    val diagnosticCount = diagnostics.getDiagnostics.size()
    if (diagnosticCount > 0) {
      logger.info(s"Received ${diagnosticCount} diagnostics for ${diagnostics.getUri}")
    }

    // Store diagnostics in the provider
    diagnosticsProvider.foreach { provider =>
      import scala.jdk.CollectionConverters._
      provider.updateDiagnostics(diagnostics.getUri, diagnostics.getDiagnostics.asScala.toList)
    }
  }

  override def showDocument(params: ShowDocumentParams): CompletableFuture[ShowDocumentResult] = {
    logger.info(s"Show document request: ${params.getUri}")
    val result = new ShowDocumentResult()
    result.setSuccess(true) // We'll always say we can show documents
    CompletableFuture.completedFuture(result)
  }

  override def createProgress(params: WorkDoneProgressCreateParams): CompletableFuture[Void] = {
    logger.debug(s"Create progress: ${params.getToken}")
    CompletableFuture.completedFuture(null)
  }

  override def notifyProgress(params: ProgressParams): Unit =
    logger.debug(s"Progress notification: ${params.getToken}")
}
