package org.llm4s.runner

import ch.epfl.scala.bsp4j._
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.slf4j.LoggerFactory

import java.net.Socket
import java.util.concurrent.{ Executors, ScheduledExecutorService, TimeUnit }
import scala.concurrent.{ ExecutionContext, Future }
import scala.jdk.CollectionConverters._
import scala.sys.process._
import scala.util.Try

/**
 * Manages the lifecycle of a Bloop build server, including startup, health monitoring,
 * and BSP client connections.
 */
class BloopServerManager(implicit ec: ExecutionContext) {

  private val logger                              = LoggerFactory.getLogger(getClass)
  private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(2)

  // Bloop server configuration
  private val bloopHost                  = "127.0.0.1"
  private val bloopPort                  = 8212
  private val maxStartupWaitSeconds      = 60
  private val healthCheckIntervalSeconds = 30

  @volatile private var serverProcess: Option[Process] = None
  @volatile private var bspClient: Option[BuildServer] = None
  @volatile private var isShuttingDown                 = false

  /**
   * Starts the Bloop server and waits for it to be ready for connections.
   */
  def startServer(): Future[Unit] = {
    logger.info("Starting Bloop build server...")

    Future {
      // Start Bloop server process
      val processBuilder = Process("bloop server")
      val process = processBuilder.run(
        ProcessLogger(
          stdout => logger.debug(s"Bloop stdout: $stdout"),
          stderr => logger.debug(s"Bloop stderr: $stderr")
        )
      )

      serverProcess = Some(process)
      logger.info(s"Bloop server process started")

      // Wait for server to be ready
      waitForServerReady()

      // Start health monitoring
      startHealthMonitoring()

      logger.info("Bloop server is ready and monitoring started")
    }.recover { case ex =>
      logger.error("Failed to start Bloop server", ex)
      throw new RuntimeException("Bloop server startup failed", ex)
    }
  }

  /**
   * Creates a BSP client connection to the running Bloop server.
   */
  def createBspConnection(): Future[BuildServer] =
    if (bspClient.isDefined) {
      Future.successful(bspClient.get)
    } else {
      Future {
        logger.info("Creating BSP connection to Bloop server...")

        val socket = new Socket(bloopHost, bloopPort)
        val launcher = new Launcher.Builder[BuildServer]()
          .setRemoteInterface(classOf[BuildServer])
          .setInput(socket.getInputStream)
          .setOutput(socket.getOutputStream)
          .create()

        val client = launcher.getRemoteProxy
        launcher.startListening()

        // Initialize the BSP connection
        val initializeParams = new InitializeBuildParams(
          "llm4s-workspace-runner",
          "1.0.0",
          "2.1.0", // BSP version
          "workspace",
          new BuildClientCapabilities(List("scala", "java").asJava)
        )

        val initializeResult = client.buildInitialize(initializeParams).get(30, TimeUnit.SECONDS)
        client.onBuildInitialized()

        logger.info(s"BSP connection initialized: ${initializeResult.getDisplayName}")

        bspClient = Some(client)
        client
      }.recover { case ex =>
        logger.error("Failed to create BSP connection", ex)
        throw new RuntimeException("BSP connection failed", ex)
      }
    }

  /**
   * Checks if the Bloop server is running and responsive.
   */
  def isServerHealthy(): Boolean =
    Try {
      val socket = new Socket()
      socket.connect(new java.net.InetSocketAddress(bloopHost, bloopPort), 5000)
      socket.close()
      true
    }.getOrElse(false)

  /**
   * Gets the current BSP client, if available.
   */
  def getBspClient(): Option[BuildServer] = bspClient

  /**
   * Gracefully shuts down the Bloop server and cleans up resources.
   */
  def shutdown(): Future[Unit] = {
    logger.info("Shutting down Bloop server manager...")
    isShuttingDown = true

    Future {
      // Shutdown BSP client
      bspClient.foreach { client =>
        try {
          client.buildShutdown().get(10, TimeUnit.SECONDS)
          client.onBuildExit()
          logger.info("BSP client shutdown completed")
        } catch {
          case ex: Exception =>
            logger.warn("Error during BSP client shutdown", ex)
        }
      }

      // Stop server process
      serverProcess.foreach { process =>
        try {
          process.destroy()
          // Wait a moment for graceful shutdown
          Thread.sleep(2000)
          logger.info("Bloop server process terminated")
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
      bspClient = None
      serverProcess = None

      logger.info("Bloop server manager shutdown completed")
    }
  }

  private def waitForServerReady(): Unit = {
    val deadline = System.currentTimeMillis() + (maxStartupWaitSeconds * 1000)

    while (System.currentTimeMillis() < deadline && !isServerHealthy()) {
      Thread.sleep(1000)
      logger.debug("Waiting for Bloop server to be ready...")
    }

    if (!isServerHealthy()) {
      throw new RuntimeException(s"Bloop server failed to start within $maxStartupWaitSeconds seconds")
    }
  }

  private def startHealthMonitoring(): Unit =
    scheduler.scheduleAtFixedRate(
      () =>
        if (!isShuttingDown) {
          try
            if (!isServerHealthy()) {
              logger.warn("Bloop server health check failed - attempting restart")
              restartServer()
            } else {
              logger.debug("Bloop server health check passed")
            }
          catch {
            case ex: Exception =>
              logger.error("Error during Bloop server health check", ex)
          }
        },
      healthCheckIntervalSeconds,
      healthCheckIntervalSeconds,
      TimeUnit.SECONDS
    )

  private def restartServer(): Unit = {
    logger.info("Restarting Bloop server...")

    // Clear current state
    bspClient = None
    serverProcess.foreach(_.destroy())

    // Wait a moment for cleanup
    Thread.sleep(2000)

    // Restart
    startServer().recover { case ex =>
      logger.error("Failed to restart Bloop server", ex)
    }
  }
}

/**
 * Simple BSP client implementation for Bloop communication.
 */
class SimpleBspClient extends BuildClient {
  private val logger = LoggerFactory.getLogger(getClass)

  override def onBuildShowMessage(params: ShowMessageParams): Unit =
    logger.info(s"BSP Message [${params.getType}]: ${params.getMessage}")

  override def onBuildLogMessage(params: LogMessageParams): Unit =
    logger.debug(s"BSP Log [${params.getType}]: ${params.getMessage}")

  override def onBuildTaskStart(params: TaskStartParams): Unit =
    logger.debug(s"BSP Task Started: ${params.getTaskId.getId} - ${params.getMessage}")

  override def onBuildTaskProgress(params: TaskProgressParams): Unit =
    logger.debug(s"BSP Task Progress: ${params.getTaskId.getId} - ${params.getMessage}")

  override def onBuildTaskFinish(params: TaskFinishParams): Unit =
    logger.debug(s"BSP Task Finished: ${params.getTaskId.getId} - ${params.getStatus}")

  override def onBuildPublishDiagnostics(params: PublishDiagnosticsParams): Unit =
    if (params.getDiagnostics.size() > 0) {
      logger.info(s"BSP Diagnostics for ${params.getTextDocument.getUri}: ${params.getDiagnostics.size()} diagnostics")
    }

  override def onBuildTargetDidChange(params: DidChangeBuildTarget): Unit =
    logger.info(s"BSP Build Target Changed: ${params.getChanges.size()} changes")
}
