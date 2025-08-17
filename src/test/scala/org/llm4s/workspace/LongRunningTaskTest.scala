package org.llm4s.workspace

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.slf4j.LoggerFactory

class LongRunningTaskTest extends AnyFunSuite with Matchers {
  private val logger = LoggerFactory.getLogger(getClass)

  test("workspace should handle short commands correctly") {
    val workspace = new ContainerisedWorkspace("test-workspace-short-command", port = 0) // Use random port

    try {
      logger.info("🚀 Starting workspace container...")
      val started = workspace.startContainer()
      started shouldBe true

      // Give workspace time to fully initialize
      Thread.sleep(2000)

      logger.info("🏃 Executing short command...")
      val result = workspace.executeCommand(
        command = "echo 'Hello from workspace'",
        timeout = Some(5000) // 5 second timeout
      )

      logger.info(s"📊 Exit code: ${result.exitCode}")
      logger.info(s"📊 Stdout: ${result.stdout}")

      // Assertions
      result.exitCode shouldBe 0
      result.stdout.trim shouldBe "Hello from workspace"

      logger.info(s"✅ Short command test passed!")

    } finally {
      logger.info("🛑 Stopping workspace...")
      workspace.stopContainer()
    }
  }

  test("workspace should handle 45-second command") {
    val workspace = new ContainerisedWorkspace("test-workspace-45s-command", port = 0) // Use random port

    try {
      logger.info("🚀 Starting workspace container...")
      val started = workspace.startContainer()
      started shouldBe true

      // Give workspace time to fully initialize
      Thread.sleep(2000)

      logger.info("⏱️ Executing 45-second sleep command...")
      val startTime = System.currentTimeMillis()

      val result = workspace.executeCommand(
        command = "sleep 45 && echo 'Sleep 45 completed'",
        timeout = Some(60000) // 60 second timeout
      )

      val duration = (System.currentTimeMillis() - startTime) / 1000

      logger.info(s"✅ Command completed after $duration seconds")
      logger.info(s"📊 Exit code: ${result.exitCode}")
      logger.info(s"📊 Stdout: ${result.stdout}")

      // Assertions
      result.exitCode shouldBe 0
      result.stdout should include("Sleep 45 completed")

      logger.info(s"✅ 45-second test passed!")

    } finally {
      logger.info("🛑 Stopping workspace...")
      workspace.stopContainer()
    }
  }

  test("workspace should maintain connection during 2-minute long-running command") {
    val workspace = new ContainerisedWorkspace("test-workspace-long-task", port = 0) // Use random port

    try {
      logger.info("🚀 Starting workspace container...")
      val started = workspace.startContainer()
      started shouldBe true

      // Give workspace time to fully initialize
      Thread.sleep(2000)

      logger.info("😴 Executing 2-minute sleep command...")
      val startTime = System.currentTimeMillis()

      // Execute command directly - the method now handles threading internally
      val result = workspace.executeCommand(
        command = "sleep 120 && echo 'Sleep completed successfully'",
        timeout = Some(150000) // 2.5 minutes timeout in milliseconds
      )

      val duration = (System.currentTimeMillis() - startTime) / 1000

      logger.info(s"✅ Command completed after $duration seconds")
      logger.info(s"📊 Exit code: ${result.exitCode}")
      logger.info(s"📊 Stdout: ${result.stdout}")
      logger.info(s"📊 Stderr: ${result.stderr}")

      // Assertions
      result.exitCode shouldBe 0
      result.stdout should include("Sleep completed successfully")

      logger.info(s"✅ Test passed! Connection maintained during long-running task")

    } finally {
      logger.info("🛑 Stopping workspace...")
      workspace.stopContainer()
    }
  }

  test("workspace should handle command timeout gracefully") {
    val workspace = new ContainerisedWorkspace("test-workspace-command-timeout", port = 0) // Use random port

    try {
      logger.info("🚀 Starting workspace for timeout test...")
      workspace.startContainer() shouldBe true

      // Wait for connection to be established
      Thread.sleep(2000)

      logger.info("⏳ Executing command with short timeout...")

      // Execute a command that will timeout
      val result = workspace.executeCommand(
        command = "sleep 10",
        timeout = Some(2000) // 2 second timeout, but command sleeps for 10
      )

      logger.info(s"📊 Exit code: ${result.exitCode}")
      logger.info(s"📊 Stderr: ${result.stderr}")

      // Command should have been killed due to timeout
      result.exitCode shouldBe 124 // Standard timeout exit code
      result.stderr should include("timed out")

      logger.info(s"✅ Timeout handled correctly")

    } finally
      workspace.stopContainer()
  }
}
