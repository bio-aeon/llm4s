package org.llm4s.trace

import java.time.Instant
import scala.concurrent.{ ExecutionContext, Future }

/**
 * No-op implementation of TraceManager that does nothing.
 * This is used when tracing is disabled.
 */
object NoOpTraceManager extends TraceManager {

  override def createTrace(
    name: String,
    userId: Option[String] = None,
    sessionId: Option[String] = None,
    metadata: Map[String, Any] = Map.empty
  ): Trace = NoOpTrace

  override def currentTrace: Option[Trace] = None

  override def withTrace[T](
    name: String,
    userId: Option[String] = None,
    sessionId: Option[String] = None,
    metadata: Map[String, Any] = Map.empty
  )(operation: Trace => T): T =
    operation(NoOpTrace)

  override def withTraceAsync[T](
    name: String,
    userId: Option[String] = None,
    sessionId: Option[String] = None,
    metadata: Map[String, Any] = Map.empty
  )(operation: Trace => Future[T])(implicit ec: ExecutionContext): Future[T] =
    operation(NoOpTrace)

  override def emitEvent(event: TraceEvent): Unit = {
    // No-op: do nothing
  }

  override def shutdown(): Unit = {
    // Nothing to shutdown
  }
}

/**
 * No-op implementation of Trace that does nothing.
 */
object NoOpTrace extends Trace {
  override def traceId: String = "noop"
  override def name: String    = "noop"

  override def span[T](name: String)(operation: Span => T): T =
    operation(NoOpSpan)

  override def spanAsync[T](name: String)(operation: Span => Future[T])(implicit ec: ExecutionContext): Future[T] =
    operation(NoOpSpan)

  override def addMetadata(key: String, value: Any): Unit    = {}
  override def addMetadata(metadata: Map[String, Any]): Unit = {}
  override def addTag(tag: String): Unit                     = {}
  override def addTags(tags: String*): Unit                  = {}
  override def setInput(input: Any): Unit                    = {}
  override def setOutput(output: Any): Unit                  = {}
  override def recordError(error: Throwable): Unit           = {}
  override def currentSpan: Option[Span]                     = None
  override def context: TraceContext                         = NoOpTraceContext
  override def finish(): Unit                                = {}
  override def isFinished: Boolean                           = true

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
    // No-op: do nothing
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
    // No-op: do nothing
  }
}

/**
 * No-op implementation of Span that does nothing.
 */
object NoOpSpan extends Span {
  override def spanId: String               = "noop"
  override def traceId: String              = "noop"
  override def parentSpanId: Option[String] = None
  override def name: String                 = "noop"
  override def startTime: Instant           = Instant.now()
  override def endTime: Option[Instant]     = Some(Instant.now())

  override def span[T](name: String)(operation: Span => T): T =
    operation(NoOpSpan)

  override def spanAsync[T](name: String)(operation: Span => Future[T])(implicit ec: ExecutionContext): Future[T] =
    operation(NoOpSpan)

  override def addMetadata(key: String, value: Any): Unit                                = {}
  override def addMetadata(metadata: Map[String, Any]): Unit                             = {}
  override def addTag(tag: String): Unit                                                 = {}
  override def addTags(tags: String*): Unit                                              = {}
  override def setInput(input: Any): Unit                                                = {}
  override def setOutput(output: Any): Unit                                              = {}
  override def setStatus(status: SpanStatus): Unit                                       = {}
  override def recordError(error: Throwable): Unit                                       = {}
  override def recordEvent(name: String, attributes: Map[String, Any] = Map.empty): Unit = {}
  override def status: SpanStatus                                                        = SpanStatus.Ok
  override def isFinished: Boolean                                                       = false
  override def context: SpanContext                                                      = NoOpSpanContext

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
  ): Unit = {}

  override def recordToolCall(
    name: String,
    toolName: String,
    startTime: Instant,
    endTime: Option[Instant] = None,
    input: Option[Any] = None,
    output: Option[Any] = None,
    metadata: Map[String, Any] = Map.empty
  ): Unit = {}
}

/**
 * No-op implementation of TraceContext that does nothing.
 */
object NoOpTraceContext extends TraceContext {
  override def traceId: String = "noop"

  override def withContext[T](operation: => T): T = operation

  override def withContextAsync[T](operation: => Future[T])(implicit ec: ExecutionContext): Future[T] = operation

  override def span[T](name: String)(operation: Span => T): T =
    operation(NoOpSpan)

  override def spanAsync[T](name: String)(operation: Span => Future[T])(implicit ec: ExecutionContext): Future[T] =
    operation(NoOpSpan)

  override def currentSpan: Option[Span] = None
  override def trace: Trace              = NoOpTrace
}

/**
 * No-op implementation of SpanContext that does nothing.
 */
object NoOpSpanContext extends SpanContext {
  override def traceId: String = "noop"
  override def spanId: String  = "noop"

  override def withContext[T](operation: => T): T = operation

  override def withContextAsync[T](operation: => Future[T])(implicit ec: ExecutionContext): Future[T] = operation

  override def childSpan[T](name: String)(operation: Span => T): T =
    operation(NoOpSpan)

  override def childSpanAsync[T](name: String)(operation: Span => Future[T])(implicit ec: ExecutionContext): Future[T] =
    operation(NoOpSpan)

  override def currentSpan: Option[Span] = Some(NoOpSpan)
  override def trace: Trace              = NoOpTrace
}
