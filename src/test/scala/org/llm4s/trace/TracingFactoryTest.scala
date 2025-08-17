package org.llm4s.trace

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import scala.concurrent.duration._

class TracingFactoryTest extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  override def beforeEach(): Unit = {
    super.beforeEach()
    // Clear any existing environment variables for clean tests
    clearTracingEnvVars()
  }

  override def afterEach(): Unit = {
    super.afterEach()
    // Clean up after each test
    clearTracingEnvVars()
  }

  private def clearTracingEnvVars(): Unit = {
    // Note: In a real environment, you might want to use a test configuration
    // or mock the environment loader instead of relying on system properties
  }

  "TracingFactory" should "create NoOpTraceManager by default when no environment is set" in {
    // When TRACING_MODE is not set or invalid, should fall back to NoOp
    val traceManager = TracingFactory.createTraceManager(TracingFactory.NONE_MODE)

    traceManager shouldBe NoOpTraceManager
  }

  it should "create PrintTraceManager when TRACING_MODE=print" in {
    val traceManager = TracingFactory.createTraceManager(TracingFactory.PRINT_MODE)
    traceManager shouldBe a[PrintTraceManager]
    traceManager.shutdown()
  }

  it should "create NoOpTraceManager when TRACING_MODE=none" in {
    val traceManager = TracingFactory.createTraceManager(TracingFactory.NONE_MODE)
    traceManager shouldBe NoOpTraceManager
  }

  it should "fall back to NoOpTraceManager for invalid TRACING_MODE" in {
    val traceManager = TracingFactory.createTraceManager("invalid-mode")
    traceManager shouldBe NoOpTraceManager
  }

  it should "create TraceManager with custom configuration" in {
    val customConfig = TraceManagerConfig(
      enabled = true,
      batchSize = 25,
      flushInterval = 30.seconds,
      maxRetries = 2,
      circuitBreakerThreshold = 3
    )

    val traceManager = TracingFactory.createTraceManager(TracingFactory.PRINT_MODE, customConfig)
    traceManager shouldBe a[PrintTraceManager]
    traceManager.shutdown()
  }

  it should "provide convenience create method" in {
    val traceManager = TracingFactory.create()
    traceManager should not be null

    // The create method uses environment variables, so we can't guarantee the result
    // This test just verifies the method works
  }

  it should "create specific TraceManager types" in {
    val noOpManager = TracingFactory.createNoOpTraceManager()
    noOpManager shouldBe NoOpTraceManager

    val printManager = TracingFactory.createPrintTraceManager()
    printManager shouldBe a[PrintTraceManager]
    printManager.shutdown()

    val customConfig           = TraceManagerConfig(enabled = true)
    val printManagerWithConfig = TracingFactory.createPrintTraceManager(customConfig)
    printManagerWithConfig shouldBe a[PrintTraceManager]
    printManagerWithConfig.shutdown()
  }

  it should "handle Langfuse configuration validation" in {
    // Note: This test depends on the actual environment configuration
    // If valid Langfuse config is present, it will create a LangfuseTraceManager
    // If not, it will fall back to NoOpTraceManager
    val traceManager = TracingFactory.createTraceManager(TracingFactory.LANGFUSE_MODE)
    traceManager should not be null
    traceManager.shutdown()
  }

  it should "create LangfuseTraceManager with valid configuration" in {
    val langfuseConfig = LangfuseConfig(
      publicKey = "test-public-key",
      secretKey = "test-secret-key",
      host = "https://test.langfuse.com"
    )

    val traceManager = TracingFactory.createLangfuseTraceManager(
      config = TraceManagerConfig(enabled = true),
      langfuseConfig = langfuseConfig
    )

    traceManager shouldBe a[LangfuseTraceManager]
    traceManager.shutdown()
  }

  "GlobalTraceManager" should "provide singleton access" in {
    val manager1 = GlobalTraceManager.traceManager
    val manager2 = GlobalTraceManager.traceManager

    // Should be the same instance
    (manager1 should be).theSameInstanceAs(manager2)
  }

  it should "create traces through global manager" in {
    val trace = GlobalTraceManager.createTrace(
      name = "global-test",
      userId = Some("test-user"),
      metadata = Map("test" -> "value")
    )

    trace should not be null
    trace.finish()
  }

  it should "execute operations with global trace context" in {
    val result = GlobalTraceManager.withTrace(
      name = "global-operation",
      userId = Some("test-user")
    ) { trace =>
      trace.addMetadata("operation", "test")

      trace.span("sub-operation") { span =>
        span.addMetadata("step", "1")
        "global-result"
      }
    }

    result shouldBe "global-result"
  }

  it should "handle shutdown gracefully" in {
    // This shouldn't throw an exception
    noException should be thrownBy GlobalTraceManager.shutdown()
  }

  "TraceManagerConfig" should "have sensible defaults" in {
    val config = TraceManagerConfig()

    config.enabled shouldBe true
    config.batchSize shouldBe 100
    config.flushInterval shouldBe scala.concurrent.duration.Duration("5s")
    config.maxRetries shouldBe 3
    config.circuitBreakerThreshold shouldBe 5
    config.environment shouldBe "production"
    config.release shouldBe "1.0.0"
    config.version shouldBe "1.0.0"
  }

  it should "allow customization" in {
    val config = TraceManagerConfig(
      enabled = false,
      batchSize = 50,
      flushInterval = 10.seconds,
      maxRetries = 5,
      circuitBreakerThreshold = 10,
      environment = "test",
      release = "2.0.0",
      version = "2.0.0"
    )

    config.enabled shouldBe false
    config.batchSize shouldBe 50
    config.flushInterval shouldBe scala.concurrent.duration.Duration("10s")
    config.maxRetries shouldBe 5
    config.circuitBreakerThreshold shouldBe 10
    config.environment shouldBe "test"
    config.release shouldBe "2.0.0"
    config.version shouldBe "2.0.0"
  }
}
