package org.llm4s.trace

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

/**
 * Base implementation of TraceManager with common functionality.
 * Concrete implementations should extend this and implement the abstract methods.
 */
abstract class BaseTraceManager(config: TraceManagerConfig) extends TraceManager {

  // Track active traces
  private val activeTraces = new ConcurrentHashMap[String, BaseTrace]()

  // Thread-local storage for current trace
  private val currentTraceHolder = new ThreadLocal[BaseTrace]()

  /**
   * Abstract method for creating the actual trace implementation.
   * Concrete implementations should override this to create their specific trace type.
   */
  protected def createTraceImpl(
    traceId: String,
    name: String,
    userId: Option[String],
    sessionId: Option[String],
    metadata: Map[String, Any]
  ): BaseTrace

  /**
   * Abstract method for immediate event emission.
   * This is called whenever an event should be sent immediately.
   */
  protected def emitEventImpl(event: TraceEvent): Unit

  override def createTrace(
    name: String,
    userId: Option[String] = None,
    sessionId: Option[String] = None,
    metadata: Map[String, Any] = Map.empty
  ): Trace = {
    if (!config.enabled) {
      return NoOpTrace
    }

    val traceId = generateTraceId()
    val trace   = createTraceImpl(traceId, name, userId, sessionId, metadata)
    activeTraces.put(traceId, trace)
    trace
  }

  override def currentTrace: Option[Trace] = Option(currentTraceHolder.get())

  override def withTrace[T](
    name: String,
    userId: Option[String] = None,
    sessionId: Option[String] = None,
    metadata: Map[String, Any] = Map.empty
  )(operation: Trace => T): T = {
    val trace         = createTrace(name, userId, sessionId, metadata)
    val previousTrace = currentTraceHolder.get()

    try {
      currentTraceHolder.set(trace.asInstanceOf[BaseTrace])
      val result = operation(trace)
      trace.finish()
      result
    } catch {
      case e: Throwable =>
        trace.recordError(e)
        trace.finish()
        throw e
    } finally currentTraceHolder.set(previousTrace)
  }

  override def withTraceAsync[T](
    name: String,
    userId: Option[String] = None,
    sessionId: Option[String] = None,
    metadata: Map[String, Any] = Map.empty
  )(operation: Trace => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val trace         = createTrace(name, userId, sessionId, metadata)
    val previousTrace = currentTraceHolder.get()

    try {
      currentTraceHolder.set(trace.asInstanceOf[BaseTrace])
      val future = operation(trace)

      future.andThen {
        case Success(_) =>
          trace.finish()
          currentTraceHolder.set(previousTrace)
        case Failure(e) =>
          trace.recordError(e)
          trace.finish()
          currentTraceHolder.set(previousTrace)
      }

      future
    } catch {
      case e: Throwable =>
        trace.recordError(e)
        trace.finish()
        currentTraceHolder.set(previousTrace)
        throw e
    }
  }

  override def shutdown(): Unit = {
    // Complete any remaining active traces
    activeTraces.values().forEach { trace =>
      if (!trace.isFinished) {
        trace.finish()
      }
    }
    activeTraces.clear()

    // Clear thread-local storage
    currentTraceHolder.remove()
  }

  /**
   * Called by traces when they finish to handle cleanup.
   */
  def onTraceFinished(trace: BaseTrace): Unit =
    activeTraces.remove(trace.traceId)

  /**
   * Emit an event immediately.
   */
  override def emitEvent(event: TraceEvent): Unit =
    if (config.enabled) {
      emitEventImpl(event)
    }

  /**
   * Generate a unique trace ID.
   */
  def generateTraceId(): String = s"trace_${UUID.randomUUID().toString.replace("-", "")}"

  /**
   * Generate a unique span ID.
   */
  def generateSpanId(): String = s"span_${UUID.randomUUID().toString.replace("-", "")}"

  /**
   * Generate a unique event ID.
   */
  def generateEventId(): String = s"evt_${UUID.randomUUID().toString.replace("-", "")}"

  /**
   * Get the current timestamp in ISO format.
   */
  protected def currentTimestamp(): String = Instant.now().toString

  /**
   * Get the configuration for this trace manager.
   */
  def getConfig: TraceManagerConfig = config
}
