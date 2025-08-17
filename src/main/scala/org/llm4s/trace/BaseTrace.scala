package org.llm4s.trace

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }
import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters._

/**
 * Base implementation of Trace with common functionality.
 * Concrete implementations should extend this and implement the abstract methods.
 */
abstract class BaseTrace(
  val traceId: String,
  val name: String,
  val userId: Option[String],
  val sessionId: Option[String],
  initialMetadata: Map[String, Any],
  protected val manager: BaseTraceManager
) extends Trace {

  // Thread-safe storage for metadata, tags, and state
  private val metadata     = new TrieMap[String, Any]()
  private val tags         = new TrieMap[String, Unit]()
  private val inputRef     = new AtomicReference[Any]()
  private val outputRef    = new AtomicReference[Any]()
  private val errorRef     = new AtomicReference[Throwable]()
  private val statusRef    = new AtomicReference[TraceStatus](TraceStatus.Ok)
  private val finishedFlag = new AtomicBoolean(false)
  private val createdAt    = Instant.now()

  // Thread-local storage for current span
  private val currentSpanHolder = new ThreadLocal[BaseSpan]()

  // Track active spans
  private val activeSpans = new ConcurrentHashMap[String, BaseSpan]()

  // Initialize with provided metadata
  initialMetadata.foreach { case (key, value) => metadata.put(key, value) }

  // Emit the initial trace create event immediately
  // Using lazy val to avoid initialization issues
  private lazy val initialEvent = {
    val event = TraceEventFactory.createTraceCreateEvent(this, manager.generateEventId(), createdAt)
    manager.emitEvent(event)
    event
  }

  // Ensure the event is created during initialization
  initialEvent

  /**
   * Abstract method for creating the actual span implementation.
   * Concrete implementations should override this to create their specific span type.
   */
  protected def createSpanImpl(
    spanId: String,
    name: String,
    parentSpan: Option[BaseSpan]
  ): BaseSpan

  override def span[T](name: String)(operation: Span => T): T = {
    val spanId     = manager.generateSpanId()
    val parentSpan = Option(currentSpanHolder.get())
    val span       = createSpanImpl(spanId, name, parentSpan)

    activeSpans.put(spanId, span)
    val previousSpan = currentSpanHolder.get()

    try {
      currentSpanHolder.set(span)
      val result = operation(span)
      span.finish()
      result
    } catch {
      case e: Throwable =>
        span.recordError(e)
        span.finish()
        throw e
    } finally currentSpanHolder.set(previousSpan)
  }

  override def spanAsync[T](name: String)(operation: Span => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val spanId     = manager.generateSpanId()
    val parentSpan = Option(currentSpanHolder.get())
    val span       = createSpanImpl(spanId, name, parentSpan)

    activeSpans.put(spanId, span)
    val previousSpan = currentSpanHolder.get()

    try {
      currentSpanHolder.set(span)
      val future = operation(span)

      future.andThen {
        case Success(_) =>
          span.finish()
          currentSpanHolder.set(previousSpan)
        case Failure(e) =>
          span.recordError(e)
          span.finish()
          currentSpanHolder.set(previousSpan)
      }

      future
    } catch {
      case e: Throwable =>
        span.recordError(e)
        span.finish()
        currentSpanHolder.set(previousSpan)
        throw e
    }
  }

  override def addMetadata(key: String, value: Any): Unit =
    metadata.put(key, value)

  override def addMetadata(metadata: Map[String, Any]): Unit =
    metadata.foreach { case (key, value) => this.metadata.put(key, value) }

  override def addTag(tag: String): Unit =
    tags.put(tag, ())

  override def addTags(tags: String*): Unit =
    tags.foreach(tag => this.tags.put(tag, ()))

  override def setInput(input: Any): Unit =
    inputRef.set(input)

  override def setOutput(output: Any): Unit =
    outputRef.set(output)

  override def recordError(error: Throwable): Unit = {
    errorRef.set(error)
    statusRef.set(TraceStatus.Error)
  }

  override def currentSpan: Option[Span] = Option(currentSpanHolder.get())

  /**
   * Update the current span in the trace context.
   * This is used by child spans to ensure currentSpan reflects the nested context.
   */
  def updateCurrentSpan(span: BaseSpan): Unit =
    currentSpanHolder.set(span)

  override def context: TraceContext = new BaseTraceContext(this)

  override def finish(): Unit =
    if (finishedFlag.compareAndSet(false, true)) {
      // Finish any remaining active spans
      activeSpans.values().asScala.foreach { span =>
        if (!span.isFinished) {
          span.finish()
        }
      }

      // Emit final trace update event immediately
      val finalEvent = TraceEventFactory.createTraceUpdateEvent(this, manager.generateEventId(), Instant.now())
      manager.emitEvent(finalEvent)

      // Clear thread-local storage
      currentSpanHolder.remove()

      // Notify the manager that this trace is finished
      manager.onTraceFinished(this)
    }

  override def isFinished: Boolean = finishedFlag.get()

  /**
   * Called by spans when they finish to handle cleanup.
   */
  def onSpanFinished(span: BaseSpan): Unit =
    activeSpans.remove(span.spanId)

  // Accessors for internal state (for implementations)
  def getMetadata: Map[String, Any]         = metadata.toMap
  def getTags: Set[String]                  = tags.keySet.toSet
  def getInput: Option[Any]                 = Option(inputRef.get())
  def getOutput: Option[Any]                = Option(outputRef.get())
  def getError: Option[Throwable]           = Option(errorRef.get())
  def getStatus: TraceStatus                = statusRef.get()
  def getCreatedAt: Instant                 = createdAt
  def getActiveSpans: Map[String, BaseSpan] = activeSpans.asScala.toMap

  // Allow access to manager for child spans
  def getManager: BaseTraceManager = manager

  override def recordGeneration(
    name: String,
    model: String,
    startTime: Instant,
    endTime: Option[Instant] = None,
    modelParameters: Map[String, Any] = Map.empty,
    input: Option[Any] = None,
    output: Option[Any] = None,
    usage: Option[TokenUsage] = None,
    metadata: Map[String, Any] = Map.empty,
    spanId: Option[String] = None
  ): Unit = {
    val event = GenerationEvent(
      id = manager.generateEventId(),
      timestamp = Instant.now(),
      traceId = traceId,
      spanId = spanId,
      name = name,
      startTime = startTime,
      endTime = endTime,
      model = model,
      modelParameters = modelParameters,
      input = input,
      output = output,
      usage = usage,
      metadata = metadata
    )
    manager.emitEvent(event)
  }

  override def recordToolCall(
    name: String,
    toolName: String,
    startTime: Instant,
    endTime: Option[Instant] = None,
    input: Option[Any] = None,
    output: Option[Any] = None,
    metadata: Map[String, Any] = Map.empty,
    spanId: Option[String] = None
  ): Unit = {
    val event = ToolCallEvent(
      id = manager.generateEventId(),
      timestamp = Instant.now(),
      traceId = traceId,
      spanId = spanId,
      name = name,
      toolName = toolName,
      startTime = startTime,
      endTime = endTime,
      input = input,
      output = output,
      metadata = metadata + ("toolName" -> toolName)
    )
    manager.emitEvent(event)
  }
}

/**
 * Base implementation of TraceContext.
 */
class BaseTraceContext(baseTrace: BaseTrace) extends TraceContext {
  override def traceId: String = baseTrace.traceId

  override def withContext[T](operation: => T): T =
    ThreadLocalContext.withTraceContext(this)(operation)

  override def withContextAsync[T](operation: => Future[T])(implicit ec: ExecutionContext): Future[T] =
    ThreadLocalContext.withTraceContextAsync(this)(operation)

  override def span[T](name: String)(operation: Span => T): T =
    baseTrace.span(name)(operation)

  override def spanAsync[T](name: String)(operation: Span => Future[T])(implicit ec: ExecutionContext): Future[T] =
    baseTrace.spanAsync(name)(operation)

  override def currentSpan: Option[Span] = baseTrace.currentSpan

  override def trace: Trace = baseTrace
}
