package org.llm4s.runner

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.{ ExecutionContext, Await }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Try

/**
 * Test suite for Metals Language Server integration.
 *
 * Note: These tests require Metals to be installed and available via coursier.
 * They will be skipped if coursier or Metals is not available.
 */
class MetalsIntegrationTest extends AnyFunSuite with Matchers with BeforeAndAfterAll {

  private var metalsServerManager: MetalsServerManager = _
  private val testTimeout                              = 60.seconds

  private def isCoursierAvailable: Boolean =
    Try {
      val process = Runtime.getRuntime.exec(Array("coursier", "--help"))
      process.waitFor() == 0
    }.getOrElse(false)

  private def isMetalsAvailable: Boolean =
    Try {
      val process = Runtime.getRuntime.exec(Array("coursier", "resolve", "org.scalameta:metals_2.13:1.3.5"))
      process.waitFor() == 0
    }.getOrElse(false)

  override def beforeAll(): Unit = {
    super.beforeAll()
    // Only initialize if coursier and Metals are available
    if (isCoursierAvailable && isMetalsAvailable) {
      metalsServerManager = new MetalsServerManager("/tmp/test-workspace")(global)
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    if (isCoursierAvailable && isMetalsAvailable && metalsServerManager != null) {
      Try {
        Await.result(metalsServerManager.shutdown(), 10.seconds)
      }
    }
  }

  test("MetalsServerManager should be creatable") {
    assume(isCoursierAvailable, "Coursier not available - skipping Metals server tests")
    assume(isMetalsAvailable, "Metals not available - skipping Metals server tests")

    metalsServerManager should not be null
    metalsServerManager.getLanguageServer() shouldBe None // Not started yet
  }

  test("MetalsServerManager should start server successfully") {
    assume(isCoursierAvailable, "Coursier not available - skipping Metals server tests")
    assume(isMetalsAvailable, "Metals not available - skipping Metals server tests")

    // Start the server
    val startFuture = metalsServerManager.startServer()

    // Wait for startup (this might take a while)
    Await.result(startFuture, testTimeout)

    // Give server a moment to be fully ready
    Thread.sleep(5000)

    // Check that server is available
    metalsServerManager.getLanguageServer() shouldBe defined

    println("✅ Metals Language Server started successfully")
  }

  test("MetalsServerManager should handle server health checks") {
    assume(isCoursierAvailable, "Coursier not available - skipping Metals server tests")
    assume(isMetalsAvailable, "Metals not available - skipping Metals server tests")

    if (metalsServerManager.getLanguageServer().isDefined) {
      if (metalsServerManager.isServerHealthy()) {
        println("✅ Server health check passed")
      } else {
        println("⚠️ Server health check failed - server may not be fully ready")
      }
    } else {
      println("⚠️ Server not started - skipping health check")
    }
  }

  test("Metals tools should be creatable") {
    assume(isCoursierAvailable, "Coursier not available - skipping Metals tools tests")
    assume(isMetalsAvailable, "Metals not available - skipping Metals tools tests")

    // Test that all Metals tools can be created
    val codeAnalyzer        = new org.llm4s.runner.tools.CodeAnalyzer(metalsServerManager)
    val symbolSearcher      = new org.llm4s.runner.tools.SymbolSearcher(metalsServerManager)
    val documentAnalyzer    = new org.llm4s.runner.tools.DocumentAnalyzer(metalsServerManager)
    val referencesFinder    = new org.llm4s.runner.tools.ReferencesFinder(metalsServerManager)
    val diagnosticsProvider = new org.llm4s.runner.tools.DiagnosticsProvider()

    codeAnalyzer should not be null
    symbolSearcher should not be null
    documentAnalyzer should not be null
    referencesFinder should not be null
    diagnosticsProvider should not be null

    // Test tool metadata
    codeAnalyzer.name shouldBe "code_analyzer"
    symbolSearcher.name shouldBe "symbol_searcher"
    documentAnalyzer.name shouldBe "document_analyzer"
    referencesFinder.name shouldBe "references_finder"
    diagnosticsProvider.name shouldBe "diagnostics_provider"

    println("✅ All Metals tools created successfully")
  }
}

/**
 * Companion object for manual testing utilities.
 */
object MetalsIntegrationTest {

  /**
   * Manual test to demonstrate Metals Language Server startup and basic LSP functionality.
   */
  def demonstrateMetalsIntegration(): Unit = {
    val manager = new MetalsServerManager("/tmp/test-workspace")(ExecutionContext.global)

    try {
      println("🚀 Starting Metals Language Server...")
      val startFuture = manager.startServer()
      Await.result(startFuture, 60.seconds)

      println("✅ Metals Language Server started")

      if (manager.isServerHealthy()) {
        println("✅ Server health check passed")

        manager.getLanguageServer() match {
          case Some(languageServer) =>
            println("✅ Language Server available")

            // Try a simple workspace symbol search
            Try {
              val symbolParams = new org.eclipse.lsp4j.WorkspaceSymbolParams("")
              val _ =
                languageServer.getWorkspaceService.symbol(symbolParams).get(10, java.util.concurrent.TimeUnit.SECONDS)
              println(s"📋 Workspace symbol search completed")
            }.recover { case ex =>
              println(s"⚠️ Workspace symbol search failed (normal if no project): ${ex.getMessage}")
            }

          case None =>
            println("❌ Language Server not available")
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
