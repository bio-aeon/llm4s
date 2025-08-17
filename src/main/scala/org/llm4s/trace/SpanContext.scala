package org.llm4s.trace

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Context for propagating trace and span information across async boundaries.
 * This enables automatic context inheritance in async operations.
 */
trait SpanContext {

  /**
   * The trace ID this context belongs to.
   */
  def traceId: String

  /**
   * The span ID this context belongs to.
   */
  def spanId: String

  /**
   * Execute an operation within this span context.
   *
   * @param operation The operation to execute
   * @return The result of the operation
   */
  def withContext[T](operation: => T): T

  /**
   * Execute an async operation within this span context.
   *
   * @param operation The async operation to execute
   * @return A Future containing the result of the operation
   */
  def withContextAsync[T](operation: => Future[T])(implicit ec: ExecutionContext): Future[T]

  /**
   * Create a child span within this context.
   *
   * @param name The name of the child span
   * @param operation The operation to execute within the child span
   * @return The result of the operation
   */
  def childSpan[T](name: String)(operation: Span => T): T

  /**
   * Create a child span within this context for async operations.
   *
   * @param name The name of the child span
   * @param operation The async operation to execute within the child span
   * @return A Future containing the result of the operation
   */
  def childSpanAsync[T](name: String)(operation: Span => Future[T])(implicit ec: ExecutionContext): Future[T]

  /**
   * Get the current active span, if any.
   */
  def currentSpan: Option[Span]

  /**
   * Get the trace this context belongs to.
   */
  def trace: Trace
}

/**
 * Context for propagating trace information across async boundaries.
 */
trait TraceContext {

  /**
   * The trace ID this context belongs to.
   */
  def traceId: String

  /**
   * Execute an operation within this trace context.
   *
   * @param operation The operation to execute
   * @return The result of the operation
   */
  def withContext[T](operation: => T): T

  /**
   * Execute an async operation within this trace context.
   *
   * @param operation The async operation to execute
   * @return A Future containing the result of the operation
   */
  def withContextAsync[T](operation: => Future[T])(implicit ec: ExecutionContext): Future[T]

  /**
   * Create a span within this trace context.
   *
   * @param name The name of the span
   * @param operation The operation to execute within the span
   * @return The result of the operation
   */
  def span[T](name: String)(operation: Span => T): T

  /**
   * Create a span within this trace context for async operations.
   *
   * @param name The name of the span
   * @param operation The async operation to execute within the span
   * @return A Future containing the result of the operation
   */
  def spanAsync[T](name: String)(operation: Span => Future[T])(implicit ec: ExecutionContext): Future[T]

  /**
   * Get the current active span, if any.
   */
  def currentSpan: Option[Span]

  /**
   * Get the trace this context belongs to.
   */
  def trace: Trace
}

/**
 * Companion object for context management utilities.
 */
object SpanContext {

  /**
   * Execute an operation with no active span context.
   */
  def withoutContext[T](operation: => T): T = operation

  /**
   * Get the current active span context, if any.
   */
  def current: Option[SpanContext] = ThreadLocalContext.currentSpan

  /**
   * Get the current active trace context, if any.
   */
  def currentTrace: Option[TraceContext] = ThreadLocalContext.currentTrace
}

/**
 * Thread-local storage for span and trace context.
 * This enables context propagation across method calls within the same thread.
 */
private object ThreadLocalContext {
  private val spanContextHolder  = new ThreadLocal[SpanContext]()
  private val traceContextHolder = new ThreadLocal[TraceContext]()

  def currentSpan: Option[SpanContext]   = Option(spanContextHolder.get())
  def currentTrace: Option[TraceContext] = Option(traceContextHolder.get())

  def setSpanContext(context: SpanContext): Unit   = spanContextHolder.set(context)
  def setTraceContext(context: TraceContext): Unit = traceContextHolder.set(context)

  def clearSpanContext(): Unit  = spanContextHolder.remove()
  def clearTraceContext(): Unit = traceContextHolder.remove()

  /**
   * Execute an operation with a specific span context.
   */
  def withSpanContext[T](context: SpanContext)(operation: => T): T = {
    val previous = spanContextHolder.get()
    try {
      spanContextHolder.set(context)
      operation
    } finally
      if (previous != null) {
        spanContextHolder.set(previous)
      } else {
        spanContextHolder.remove()
      }
  }

  /**
   * Execute an operation with a specific trace context.
   */
  def withTraceContext[T](context: TraceContext)(operation: => T): T = {
    val previous = traceContextHolder.get()
    try {
      traceContextHolder.set(context)
      operation
    } finally
      if (previous != null) {
        traceContextHolder.set(previous)
      } else {
        traceContextHolder.remove()
      }
  }

  /**
   * Execute an async operation with a specific span context.
   */
  def withSpanContextAsync[T](
    context: SpanContext
  )(operation: => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val previous = spanContextHolder.get()
    try {
      spanContextHolder.set(context)
      val future = operation
      future.andThen { case _ =>
        if (previous != null) {
          spanContextHolder.set(previous)
        } else {
          spanContextHolder.remove()
        }
      }
      future
    } catch {
      case e: Throwable =>
        if (previous != null) {
          spanContextHolder.set(previous)
        } else {
          spanContextHolder.remove()
        }
        throw e
    }
  }

  /**
   * Execute an async operation with a specific trace context.
   */
  def withTraceContextAsync[T](
    context: TraceContext
  )(operation: => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val previous = traceContextHolder.get()
    try {
      traceContextHolder.set(context)
      val future = operation
      future.andThen { case _ =>
        if (previous != null) {
          traceContextHolder.set(previous)
        } else {
          traceContextHolder.remove()
        }
      }
      future
    } catch {
      case e: Throwable =>
        if (previous != null) {
          traceContextHolder.set(previous)
        } else {
          traceContextHolder.remove()
        }
        throw e
    }
  }
}
