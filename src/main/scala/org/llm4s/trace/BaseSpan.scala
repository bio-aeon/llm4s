package org.llm4s.trace

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }
import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters._

/**
 * Base implementation of Span with common functionality.
 * Concrete implementations should extend this and implement the abstract methods.
 */
abstract class BaseSpan(
  val spanId: String,
  val traceId: String,
  val parentSpanId: Option[String],
  val name: String,
  val startTime: Instant,
  val trace: BaseTrace
) extends Span {

  // Thread-safe storage for metadata, tags, and state
  private val metadata     = new TrieMap[String, Any]()
  private val tags         = new TrieMap[String, Unit]()
  private val inputRef     = new AtomicReference[Any]()
  private val outputRef    = new AtomicReference[Any]()
  private val statusRef    = new AtomicReference[SpanStatus](SpanStatus.Ok)
  private val endTimeRef   = new AtomicReference[Instant]()
  private val finishedFlag = new AtomicBoolean(false)
  private val errorRef     = new AtomicReference[Throwable]()

  // Thread-local storage for current child span
  private val currentChildSpanHolder = new ThreadLocal[BaseSpan]()

  // Track active child spans
  private val activeChildSpans = new ConcurrentHashMap[String, BaseSpan]()

  // Track events that occur during span execution
  private val events = new TrieMap[String, SpanEvent]()

  // Emit the initial span create event immediately
  // Using lazy val to avoid initialization issues
  private lazy val initialEvent = {
    val event = TraceEventFactory.createSpanCreateEvent(this, trace.getManager.generateEventId(), startTime)
    trace.getManager.emitEvent(event)
    event
  }

  // Ensure the event is created during initialization
  initialEvent

  /**
   * Abstract method for creating child span implementations.
   * Concrete implementations should override this to create their specific span type.
   */
  protected def createChildSpanImpl(
    spanId: String,
    name: String,
    parentSpan: BaseSpan
  ): BaseSpan

  override def endTime: Option[Instant] = Option(endTimeRef.get())

  override def span[T](name: String)(operation: Span => T): T = {
    val childSpanId = trace.getManager.generateSpanId()
    val childSpan   = createChildSpanImpl(childSpanId, name, this)

    activeChildSpans.put(childSpanId, childSpan)
    val previousChildSpan = currentChildSpanHolder.get()

    try {
      currentChildSpanHolder.set(childSpan)
      // Also update the trace's current span to reflect the nested context
      trace.asInstanceOf[BaseTrace].updateCurrentSpan(childSpan)
      val result = operation(childSpan)
      childSpan.finish()
      result
    } catch {
      case e: Throwable =>
        childSpan.recordError(e)
        childSpan.finish()
        throw e
    } finally {
      currentChildSpanHolder.set(previousChildSpan)
      // Restore the trace's current span to this span
      trace.asInstanceOf[BaseTrace].updateCurrentSpan(this)
    }
  }

  override def spanAsync[T](name: String)(operation: Span => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val childSpanId = trace.getManager.generateSpanId()
    val childSpan   = createChildSpanImpl(childSpanId, name, this)

    activeChildSpans.put(childSpanId, childSpan)
    val previousChildSpan = currentChildSpanHolder.get()

    try {
      currentChildSpanHolder.set(childSpan)
      val future = operation(childSpan)

      future.andThen {
        case Success(_) =>
          childSpan.finish()
          currentChildSpanHolder.set(previousChildSpan)
        case Failure(e) =>
          childSpan.recordError(e)
          childSpan.finish()
          currentChildSpanHolder.set(previousChildSpan)
      }

      future
    } catch {
      case e: Throwable =>
        childSpan.recordError(e)
        childSpan.finish()
        currentChildSpanHolder.set(previousChildSpan)
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

  override def setStatus(status: SpanStatus): Unit =
    statusRef.set(status)

  override def recordError(error: Throwable): Unit = {
    errorRef.set(error)
    statusRef.set(SpanStatus.Error)
  }

  override def recordEvent(name: String, attributes: Map[String, Any] = Map.empty): Unit = {
    val eventId   = trace.getManager.generateEventId()
    val eventTime = Instant.now()
    val event     = SpanEvent(eventId, name, eventTime, attributes)
    events.put(eventId, event)

    // Emit the event immediately
    val traceEvent = SpanEventEvent(
      id = trace.getManager.generateEventId(),
      timestamp = eventTime,
      traceId = traceId,
      spanId = spanId,
      eventName = name,
      eventTime = eventTime,
      attributes = attributes
    )
    trace.getManager.emitEvent(traceEvent)
  }

  override def status: SpanStatus = statusRef.get()

  override def isFinished: Boolean = finishedFlag.get()

  override def context: SpanContext = new BaseSpanContext(this)

  /**
   * Finish this span and set the end time.
   */
  def finish(): Unit =
    if (finishedFlag.compareAndSet(false, true)) {
      endTimeRef.set(Instant.now())

      // Finish any remaining active child spans
      activeChildSpans.values().asScala.foreach { childSpan =>
        if (!childSpan.isFinished) {
          childSpan.finish()
        }
      }

      // Emit final span update event immediately
      val finalEvent = TraceEventFactory.createSpanUpdateEvent(this, trace.getManager.generateEventId(), Instant.now())
      trace.getManager.emitEvent(finalEvent)

      // Clear thread-local storage
      currentChildSpanHolder.remove()

      // Notify the trace that this span is finished
      trace.onSpanFinished(this)
    }

  /**
   * Called by child spans when they finish to handle cleanup.
   */
  def onChildSpanFinished(childSpan: BaseSpan): Unit =
    activeChildSpans.remove(childSpan.spanId)

  // Accessors for internal state (for implementations)
  def getMetadata: Map[String, Any]              = metadata.toMap
  def getTags: Set[String]                       = tags.keySet.toSet
  def getInput: Option[Any]                      = Option(inputRef.get())
  def getOutput: Option[Any]                     = Option(outputRef.get())
  def getError: Option[Throwable]                = Option(errorRef.get())
  def getEvents: Map[String, SpanEvent]          = events.toMap
  def getActiveChildSpans: Map[String, BaseSpan] = activeChildSpans.asScala.toMap

  override def recordGeneration(
    name: String,
    model: String,
    startTime: Instant,
    endTime: Option[Instant] = None,
    modelParameters: Map[String, Any] = Map.empty,
    input: Option[Any] = None,
    output: Option[Any] = None,
    usage: Option[TokenUsage] = None,
    metadata: Map[String, Any] = Map.empty
  ): Unit =
    // Delegate to trace's recordGeneration with this span's ID
    trace.recordGeneration(
      name = name,
      model = model,
      startTime = startTime,
      endTime = endTime,
      modelParameters = modelParameters,
      input = input,
      output = output,
      usage = usage,
      metadata = metadata,
      spanId = Some(spanId)
    )

  override def recordToolCall(
    name: String,
    toolName: String,
    startTime: Instant,
    endTime: Option[Instant] = None,
    input: Option[Any] = None,
    output: Option[Any] = None,
    metadata: Map[String, Any] = Map.empty
  ): Unit =
    // Delegate to trace's recordToolCall with this span's ID
    trace.recordToolCall(
      name = name,
      toolName = toolName,
      startTime = startTime,
      endTime = endTime,
      input = input,
      output = output,
      metadata = metadata,
      spanId = Some(spanId)
    )
}

/**
 * Base implementation of SpanContext.
 */
class BaseSpanContext(span: BaseSpan) extends SpanContext {
  override def traceId: String = span.traceId
  override def spanId: String  = span.spanId

  override def withContext[T](operation: => T): T =
    ThreadLocalContext.withSpanContext(this)(operation)

  override def withContextAsync[T](operation: => Future[T])(implicit ec: ExecutionContext): Future[T] =
    ThreadLocalContext.withSpanContextAsync(this)(operation)

  override def childSpan[T](name: String)(operation: Span => T): T =
    span.span(name)(operation)

  override def childSpanAsync[T](name: String)(operation: Span => Future[T])(implicit ec: ExecutionContext): Future[T] =
    span.spanAsync(name)(operation)

  override def currentSpan: Option[Span] = Some(span)

  override def trace: Trace = span.trace
}

/**
 * Represents an event that occurred during span execution.
 */
case class SpanEvent(
  id: String,
  name: String,
  timestamp: Instant,
  attributes: Map[String, Any]
)
