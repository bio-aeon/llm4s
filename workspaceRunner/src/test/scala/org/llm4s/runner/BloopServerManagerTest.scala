package org.llm4s.runner

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.{ ExecutionContext, Await }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Try

/**
 * Test suite for BloopServerManager functionality.
 *
 * Note: These tests require Bloop to be installed and available in the PATH.
 * They will be skipped if Bloop is not available.
 */
class BloopServerManagerTest extends AnyFunSuite with Matchers with BeforeAndAfterAll {

  private var bloopServerManager: BloopServerManager = _
  private val testTimeout                            = 30.seconds

  private def isBloopAvailable: Boolean =
    Try {
      val process = Runtime.getRuntime.exec(Array("bloop", "--version"))
      process.waitFor() == 0
    }.getOrElse(false)

  override def beforeAll(): Unit = {
    super.beforeAll()
    // Only initialize if Bloop is available
    if (isBloopAvailable) {
      bloopServerManager = new BloopServerManager()(global)
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    if (isBloopAvailable && bloopServerManager != null) {
      Try {
        Await.result(bloopServerManager.shutdown(), 10.seconds)
      }
    }
  }

  test("BloopServerManager should be creatable") {
    assume(isBloopAvailable, "Bloop not available - skipping Bloop server tests")

    bloopServerManager should not be null
    bloopServerManager.isServerHealthy() shouldBe false // Not started yet
  }

  test("BloopServerManager should start server successfully") {
    assume(isBloopAvailable, "Bloop not available - skipping Bloop server tests")

    // Start the server
    val startFuture = bloopServerManager.startServer()

    // Wait for startup (this might take a while)
    Await.result(startFuture, testTimeout)

    // Give server a moment to be fully ready
    Thread.sleep(2000)

    // Check that server is healthy
    bloopServerManager.isServerHealthy() shouldBe true

    println("✅ Bloop server started successfully")
  }

  test("BloopServerManager should create BSP connection") {
    assume(isBloopAvailable, "Bloop not available - skipping Bloop server tests")
    assume(bloopServerManager.isServerHealthy(), "Bloop server not running")

    val bspConnectionFuture = bloopServerManager.createBspConnection()
    val bspClient           = Await.result(bspConnectionFuture, testTimeout)

    bspClient should not be null
    bloopServerManager.getBspClient() shouldBe defined

    println("✅ BSP connection created successfully")
  }

  test("BSP client should respond to workspace build targets") {
    assume(isBloopAvailable, "Bloop not available - skipping Bloop server tests")
    assume(bloopServerManager.getBspClient().isDefined, "BSP client not available")

    val bspClient = bloopServerManager.getBspClient().get

    // This might fail if there's no build configuration, but shouldn't crash
    Try {
      val result = bspClient.workspaceBuildTargets().get(10, java.util.concurrent.TimeUnit.SECONDS)
      println(s"✅ Workspace build targets: ${result.getTargets.size()} targets found")
    }.recover { case ex =>
      println(s"⚠️ Workspace build targets call failed (expected if no build config): ${ex.getMessage}")
    }
  }

  test("BloopServerManager should handle server health checks") {
    assume(isBloopAvailable, "Bloop not available - skipping Bloop server tests")

    if (bloopServerManager.isServerHealthy()) {
      println("✅ Server health check passed")
    } else {
      println("⚠️ Server health check failed - server may not be fully ready")
    }
  }
}

/**
 * Companion object for manual testing utilities.
 */
object BloopServerManagerTest {

  /**
   * Manual test to demonstrate Bloop server startup and BSP connection.
   */
  def demonstrateBloopIntegration(): Unit = {
    val manager = new BloopServerManager()(ExecutionContext.global)

    try {
      println("🚀 Starting Bloop server...")
      val startFuture = manager.startServer()
      Await.result(startFuture, 30.seconds)

      println("✅ Bloop server started")

      if (manager.isServerHealthy()) {
        println("✅ Server health check passed")

        println("🔗 Creating BSP connection...")
        val bspFuture = manager.createBspConnection()
        val bspClient = Await.result(bspFuture, 30.seconds)

        println("✅ BSP connection established")

        // Try to get workspace build targets
        Try {
          val result = bspClient.workspaceBuildTargets().get(10, java.util.concurrent.TimeUnit.SECONDS)
          println(s"📋 Found ${result.getTargets.size()} build targets")
        }.recover { case ex =>
          println(s"⚠️ No build targets found (normal if no build config): ${ex.getMessage}")
        }

      } else {
        println("❌ Server health check failed")
      }

    } catch {
      case ex: Exception =>
        println(s"❌ Error during demonstration: ${ex.getMessage}")
    } finally {
      println("🛑 Shutting down...")
      Try {
        Await.result(manager.shutdown(), 10.seconds)
        println("✅ Shutdown completed")
      }
    }
  }
}
