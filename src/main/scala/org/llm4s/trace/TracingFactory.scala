package org.llm4s.trace

import org.llm4s.config.EnvLoader
import org.slf4j.LoggerFactory

/**
 * Factory for creating TraceManager instances based on configuration.
 * This replaces the old Tracing interface with the new hierarchical tracing system.
 */
object TracingFactory {
  private val logger = LoggerFactory.getLogger(getClass)

  // Constants for tracing modes
  val LANGFUSE_MODE = "langfuse"
  val PRINT_MODE    = "print"
  val NONE_MODE     = "none"

  /**
   * Creates a TraceManager instance based on the TRACING_MODE environment variable.
   *
   * Supported modes:
   * - "langfuse": Uses LangfuseTraceManager to send traces to Langfuse
   * - "print": Uses PrintTraceManager to print traces to console
   * - "none" (default): Uses NoOpTraceManager (no tracing)
   */
  def createTraceManager(): TraceManager = {
    val mode = EnvLoader.getOrElse("TRACING_MODE", NONE_MODE).toLowerCase
    createTraceManager(mode)
  }

  /**
   * Creates a TraceManager instance for a specific tracing mode.
   * This is particularly useful for tests where you want to specify the mode explicitly.
   *
   * @param mode The tracing mode to use (use constants: LANGFUSE_MODE, PRINT_MODE, NONE_MODE)
   */
  def createTraceManager(mode: String): TraceManager =
    mode.toLowerCase match {
      case LANGFUSE_MODE =>
        val config         = TraceManagerConfig()
        val langfuseConfig = LangfuseConfig()

        if (!langfuseConfig.isValid) {
          logger.warn("Langfuse configuration is invalid (missing keys), falling back to NoOpTraceManager")
          NoOpTraceManager
        } else {
          new LangfuseTraceManager(config, langfuseConfig)
        }

      case PRINT_MODE =>
        val config = TraceManagerConfig()
        new PrintTraceManager(config)

      case NONE_MODE =>
        NoOpTraceManager

      case other =>
        logger.warn(
          s"Unknown TRACING_MODE: '$other'. Valid options: $LANGFUSE_MODE, $PRINT_MODE, $NONE_MODE. Falling back to NoOpTraceManager"
        )
        NoOpTraceManager
    }

  /**
   * Creates a TraceManager instance with custom configuration.
   */
  def createTraceManager(config: TraceManagerConfig): TraceManager = {
    val mode = EnvLoader.getOrElse("TRACING_MODE", NONE_MODE).toLowerCase
    createTraceManager(mode, config)
  }

  /**
   * Creates a TraceManager instance for a specific tracing mode with custom configuration.
   * This is particularly useful for tests where you want to specify both the mode and config explicitly.
   *
   * @param mode The tracing mode to use (use constants: LANGFUSE_MODE, PRINT_MODE, NONE_MODE)
   * @param config The custom configuration to use
   */
  def createTraceManager(mode: String, config: TraceManagerConfig): TraceManager =
    mode.toLowerCase match {
      case LANGFUSE_MODE =>
        val langfuseConfig = LangfuseConfig()

        if (!langfuseConfig.isValid) {
          logger.warn("Langfuse configuration is invalid (missing keys), falling back to NoOpTraceManager")
          NoOpTraceManager
        } else {
          new LangfuseTraceManager(config, langfuseConfig)
        }

      case PRINT_MODE =>
        new PrintTraceManager(config)

      case NONE_MODE =>
        NoOpTraceManager

      case other =>
        logger.warn(
          s"Unknown TRACING_MODE: '$other'. Valid options: $LANGFUSE_MODE, $PRINT_MODE, $NONE_MODE. Falling back to NoOpTraceManager"
        )
        NoOpTraceManager
    }

  /**
   * Creates a LangfuseTraceManager with specific configuration.
   */
  def createLangfuseTraceManager(
    config: TraceManagerConfig = TraceManagerConfig(),
    langfuseConfig: LangfuseConfig = LangfuseConfig()
  ): LangfuseTraceManager =
    new LangfuseTraceManager(config, langfuseConfig)

  /**
   * Creates a PrintTraceManager with specific configuration.
   */
  def createPrintTraceManager(
    config: TraceManagerConfig = TraceManagerConfig()
  ): PrintTraceManager =
    new PrintTraceManager(config)

  /**
   * Creates a NoOpTraceManager (no tracing).
   */
  def createNoOpTraceManager(): NoOpTraceManager.type =
    NoOpTraceManager

  /**
   * Convenience method to create a TraceManager using default configuration.
   * This is the primary entry point for most applications.
   */
  def create(): TraceManager = createTraceManager()
}

/**
 * Global singleton for easy access to the default trace manager.
 * This provides a convenient way to access tracing without manually creating managers.
 */
object GlobalTraceManager {
  private lazy val _traceManager: TraceManager = TracingFactory.createTraceManager()

  /**
   * Get the global trace manager instance.
   */
  def traceManager: TraceManager = _traceManager

  /**
   * Create a new trace using the global trace manager.
   */
  def createTrace(
    name: String,
    userId: Option[String] = None,
    sessionId: Option[String] = None,
    metadata: Map[String, Any] = Map.empty
  ): Trace =
    _traceManager.createTrace(name, userId, sessionId, metadata)

  /**
   * Execute an operation within a new trace context using the global trace manager.
   */
  def withTrace[T](
    name: String,
    userId: Option[String] = None,
    sessionId: Option[String] = None,
    metadata: Map[String, Any] = Map.empty
  )(operation: Trace => T): T =
    _traceManager.withTrace(name, userId, sessionId, metadata)(operation)

  /**
   * Shutdown the global trace manager.
   * This should be called when the application shuts down.
   */
  def shutdown(): Unit =
    _traceManager.shutdown()
}
