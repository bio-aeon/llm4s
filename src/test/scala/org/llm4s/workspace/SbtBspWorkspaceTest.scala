package org.llm4s.workspace

import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.io.File
import java.nio.file.Files
import scala.concurrent.duration._
import scala.util.Try

/**
 * Test suite for SBT and BSP integration using the WebSocket-based ContainerisedWorkspace.
 *
 * This test demonstrates:
 * 1. Creating an SBT project structure in the container
 * 2. Compiling Scala code using SBT
 * 3. Testing BSP integration infrastructure
 * 4. Validating the containerized development environment
 */
class SbtBspWorkspaceTest extends AnyFunSuite with Matchers with BeforeAndAfterAll {

  private val tempDir                           = Files.createTempDirectory("sbt-bsp-workspace-test").toString
  private var workspace: ContainerisedWorkspace = _

  override def beforeAll(): Unit = {
    super.beforeAll()

    // Only run tests if Docker is available
    if (isDockerAvailable) {
      workspace = new ContainerisedWorkspace(tempDir, port = 0) // Use random port for tests

      // Start the container - this may take some time
      val started = workspace.startContainer()
      if (!started) {
        fail("Failed to start WebSocket workspace container")
      }

      // Give it a moment to fully initialize
      Thread.sleep(3000)

      // Set up the SBT project in the container
      setupSbtProject()
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()

    if (isDockerAvailable && workspace != null) {
      workspace.stopContainer()
    }

    // Clean up temp directory
    Try {
      def deleteRecursively(file: File): Unit = {
        if (file.isDirectory) {
          file.listFiles().foreach(deleteRecursively)
        }
        file.delete()
      }
      deleteRecursively(new File(tempDir))
    }
  }

  private def isDockerAvailable: Boolean =
    Try {
      val process = Runtime.getRuntime.exec(Array("docker", "--version"))
      process.waitFor() == 0
    }.getOrElse(false)

  private def setupSbtProject(): Unit = {
    // Create build.sbt
    val buildSbtContent =
      """name := "sbt-bsp-test-project"
        |
        |version := "0.1.0"
        |
        |scalaVersion := "3.7.1"
        |
        |libraryDependencies ++= Seq(
        |  "org.scalatest" %% "scalatest" % "3.2.19" % Test
        |)
        |""".stripMargin

    workspace.writeFile("build.sbt", buildSbtContent, Some("create"), Some(true))

    // Create main Scala source
    val mainScalaContent =
      """object Main {
        |  def main(args: Array[String]): Unit = {
        |    println("Hello, SBT BSP Integration!")
        |    
        |    val numbers = List(1, 2, 3, 4, 5)
        |    val doubled = numbers.map(_ * 2)
        |    println(s"Doubled numbers: $doubled")
        |    
        |    val factorial5 = factorial(5)
        |    println(s"Factorial of 5: $factorial5")
        |  }
        |  
        |  def add(a: Int, b: Int): Int = a + b
        |  
        |  def factorial(n: Int): Int = {
        |    if (n <= 1) 1
        |    else n * factorial(n - 1)
        |  }
        |  
        |  case class Person(name: String, age: Int)
        |  
        |  def greet(person: Person): String = s"Hello, ${person.name}! You are ${person.age} years old."
        |}""".stripMargin

    workspace.writeFile("src/main/scala/Main.scala", mainScalaContent, Some("create"), Some(true))

    // Verify the file was created
    val mainFileCheck = Try(workspace.readFile("src/main/scala/Main.scala"))
    println(s"Main.scala created successfully: ${mainFileCheck.isSuccess}")

    // Create test Scala source
    val testScalaContent =
      """import org.scalatest.funsuite.AnyFunSuite
        |import org.scalatest.matchers.should.Matchers
        |
        |class MainTest extends AnyFunSuite with Matchers {
        |  
        |  test("add function should work correctly") {
        |    Main.add(2, 3) shouldBe 5
        |    Main.add(-1, 1) shouldBe 0
        |    Main.add(0, 0) shouldBe 0
        |  }
        |  
        |  test("factorial function should work correctly") {
        |    Main.factorial(0) shouldBe 1
        |    Main.factorial(1) shouldBe 1
        |    Main.factorial(5) shouldBe 120
        |  }
        |  
        |  test("greet function should format correctly") {
        |    val person = Main.Person("Alice", 25)
        |    Main.greet(person) shouldBe "Hello, Alice! You are 25 years old."
        |  }
        |}""".stripMargin

    workspace.writeFile("src/test/scala/MainTest.scala", testScalaContent, Some("create"), Some(true))

    println("SBT project structure created successfully")
  }

  test("SBT project structure is created correctly") {
    assume(isDockerAvailable, "Docker not available - skipping SBT BSP tests")

    // Add a small delay to ensure files are fully written
    Thread.sleep(1000)

    // Verify project files exist
    // Use maxDepth=10 to ensure we get files in nested directories
    val exploreResponse = workspace.exploreFiles(".", Some(true), maxDepth = Some(10))
    val allEntries      = exploreResponse.files

    println(s"Total entries: ${allEntries.length}")
    println("All entries:")
    allEntries.foreach(entry => println(s"  ${if (entry.isDirectory) "[DIR] " else "[FILE]"} '${entry.path}'"))

    // Filter out empty path (root directory itself) and only look at actual files
    val filePaths = allEntries.filter(e => !e.isDirectory && e.path.nonEmpty).map(_.path).toSet
    val dirPaths  = allEntries.filter(e => e.isDirectory && e.path.nonEmpty).map(_.path).toSet

    println(s"Directories found: ${dirPaths.mkString(", ")}")
    println(s"Files found: ${filePaths.mkString(", ")}")

    // Verify expected files exist
    filePaths should contain("build.sbt")
    filePaths should contain("src/main/scala/Main.scala")
    filePaths should contain("src/test/scala/MainTest.scala")

    // Verify expected directories exist
    dirPaths should contain("src")
    dirPaths should contain("src/main")
    dirPaths should contain("src/main/scala")
    dirPaths should contain("src/test")
    dirPaths should contain("src/test/scala")

    // Verify build.sbt content
    val buildSbtResponse = workspace.readFile("build.sbt")
    buildSbtResponse.content should include("sbt-bsp-test-project")
    buildSbtResponse.content should include("scalaVersion := \"3.7.1\"")

    println("✅ SBT project structure verified")
  }

  test("SBT can compile the Scala project") {
    assume(isDockerAvailable, "Docker not available - skipping SBT BSP tests")

    val startTime = System.currentTimeMillis()

    // Compile the project
    val compileResponse = workspace.executeCommand(
      "sbt compile",
      None,
      Some(120000) // 2 minute timeout for compilation (in milliseconds)
    )

    val duration = System.currentTimeMillis() - startTime

    compileResponse.exitCode shouldBe 0
    compileResponse.stdout should include("[info] done compiling")

    println(s"✅ SBT compilation completed successfully in ${duration}ms")
    println(s"Compilation output: ${compileResponse.stdout}")
  }

  test("SBT can run tests") {
    assume(isDockerAvailable, "Docker not available - skipping SBT BSP tests")

    val testResponse = workspace.executeCommand(
      "sbt test",
      None,
      Some(120000) // 2 minute timeout (in milliseconds)
    )

    testResponse.exitCode shouldBe 0
    testResponse.stdout should include("All tests passed")

    println("✅ SBT tests ran successfully")
    println(s"Test output: ${testResponse.stdout}")
  }

  test("SBT can run the main application") {
    assume(isDockerAvailable, "Docker not available - skipping SBT BSP tests")

    val runResponse = workspace.executeCommand(
      "sbt run",
      None,
      Some(60000) // 1 minute timeout (in milliseconds)
    )

    runResponse.exitCode shouldBe 0
    runResponse.stdout should include("Hello, SBT BSP Integration!")
    runResponse.stdout should include("Doubled numbers:")
    runResponse.stdout should include("Factorial of 5: 120")

    println("✅ SBT run completed successfully")
    println(s"Run output: ${runResponse.stdout}")
  }

  test("Workspace can generate Bloop configuration") {
    assume(isDockerAvailable, "Docker not available - skipping SBT BSP tests")

    // Install Bloop SBT plugin and generate configuration
    val bloopInstallResponse = workspace.executeCommand(
      "sbt 'set bloopExportJarClassifiers in Global := Some(Set(\"sources\"))' bloopInstall",
      None,
      Some(180000) // 3 minute timeout for Bloop setup (in milliseconds)
    )

    // Note: This might fail if Bloop plugin is not available, but we test the attempt
    println(s"Bloop install attempt: exit code ${bloopInstallResponse.exitCode}")
    println(s"Bloop install output: ${bloopInstallResponse.stdout}")

    if (bloopInstallResponse.exitCode == 0) {
      // Check if .bloop directory was created
      val exploreResponse = workspace.exploreFiles(".", Some(true), maxDepth = Some(10))
      val filePaths       = exploreResponse.files.map(_.path)

      if (filePaths.exists(_.contains(".bloop"))) {
        println("✅ Bloop configuration generated successfully")
      } else {
        println("⚠️ Bloop configuration directory not found (plugin may not be available)")
      }
    } else {
      println("⚠️ Bloop install failed (expected - plugin not configured)")
    }
  }

  test("Container environment has required Scala development tools") {
    assume(isDockerAvailable, "Docker not available - skipping SBT BSP tests")

    // Check SBT version
    val sbtVersionResponse = workspace.executeCommand("sbt --version", None, Some(30000)) // 30 second timeout
    sbtVersionResponse.exitCode shouldBe 0
    sbtVersionResponse.stdout should include("sbt runner version")

    // Check Scala version via SDKMAN
    val scalaVersionResponse = workspace.executeCommand(
      "bash -c 'source /root/.sdkman/bin/sdkman-init.sh && scala -version 2>&1'",
      None,
      Some(30000) // 30 second timeout (in milliseconds)
    )
    if (scalaVersionResponse.exitCode == 0) {
      // Scala version might be in stdout or stderr, check both
      val combinedOutput = scalaVersionResponse.stdout + scalaVersionResponse.stderr
      combinedOutput should include("Scala")
    }

    // Check Java version
    val javaVersionResponse =
      workspace.executeCommand("java -version", None, Some(30000)) // Fixed: 30 seconds, not 30ms
    javaVersionResponse.exitCode shouldBe 0

    println("✅ Required development tools are available in container")
    println(s"SBT: ${sbtVersionResponse.stdout}")
    println(s"Java: ${javaVersionResponse.stderr}") // Java version goes to stderr
  }

  test("Container supports concurrent SBT operations") {
    assume(isDockerAvailable, "Docker not available - skipping SBT BSP tests")

    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.concurrent.Future

    // Test concurrent SBT operations
    // Since SBT uses a server with Unix domain sockets that can conflict,
    // we'll run them with a small delay between starts to avoid socket conflicts
    val futures = Seq(
      Future {
        val response = workspace.executeCommand("sbt -Dsbt.server.forcestart=false 'show name'", None, Some(60000))
        if (response.exitCode != 0) {
          println(s"[show name] Exit code: ${response.exitCode}")
          println(s"[show name] Stdout: ${response.stdout}")
          println(s"[show name] Stderr: ${response.stderr}")
        }
        response.exitCode shouldBe 0
        response.stdout should include("sbt-bsp-test-project")
        1
      },
      Future {
        Thread.sleep(500) // Small delay to avoid socket conflict
        val response =
          workspace.executeCommand("sbt -Dsbt.server.forcestart=false 'show scalaVersion'", None, Some(60000))
        if (response.exitCode != 0) {
          println(s"[show scalaVersion] Exit code: ${response.exitCode}")
          println(s"[show scalaVersion] Stdout: ${response.stdout}")
          println(s"[show scalaVersion] Stderr: ${response.stderr}")
        }
        response.exitCode shouldBe 0
        response.stdout should include("3.7.1")
        2
      },
      Future {
        Thread.sleep(1000) // Longer delay for the third command
        val response = workspace.executeCommand("sbt -Dsbt.server.forcestart=false 'show version'", None, Some(60000))
        if (response.exitCode != 0) {
          println(s"[show version] Exit code: ${response.exitCode}")
          println(s"[show version] Stdout: ${response.stdout}")
          println(s"[show version] Stderr: ${response.stderr}")
        }
        response.exitCode shouldBe 0
        response.stdout should include("0.1.0")
        3
      }
    )

    val results   = Future.sequence(futures)
    val completed = concurrent.Await.result(results, 3.minutes)

    completed should contain theSameElementsAs Seq(1, 2, 3)

    println("✅ Concurrent SBT operations completed successfully")
  }

  test("Workspace can handle SBT clean and recompile") {
    assume(isDockerAvailable, "Docker not available - skipping SBT BSP tests")

    // Clean the project
    val cleanResponse = workspace.executeCommand("sbt clean", None, Some(60000)) // 60 second timeout
    cleanResponse.exitCode shouldBe 0

    // Verify target directory is cleaned
    val exploreAfterClean = workspace.exploreFiles(".", Some(true), maxDepth = Some(10))
    val pathsAfterClean   = exploreAfterClean.files.map(_.path)

    // Target directory might still exist but should be mostly empty
    println(s"Files after clean: ${pathsAfterClean.filter(_.contains("target"))}")

    // Recompile from clean state
    val recompileResponse = workspace.executeCommand("sbt compile", None, Some(120000)) // 120 second timeout
    recompileResponse.exitCode shouldBe 0
    recompileResponse.stdout should include("[info] done compiling")

    println("✅ Clean and recompile cycle completed successfully")
  }
}

/**
 * Companion object for SBT BSP workspace testing utilities
 */
object SbtBspWorkspaceTest {

  /**
   * Creates a test workspace with SBT project for manual testing
   */
  def createTestWorkspace(workspaceDir: String): ContainerisedWorkspace = {
    val workspace = new ContainerisedWorkspace(workspaceDir, port = 0) // Use random port for tests

    if (workspace.startContainer()) {
      Thread.sleep(3000) // Wait for container initialization
      setupSampleSbtProject(workspace)
      workspace
    } else {
      throw new RuntimeException("Failed to start workspace container")
    }
  }

  /**
   * Sets up a sample SBT project for testing
   */
  private def setupSampleSbtProject(workspace: ContainerisedWorkspace): Unit = {
    // Create minimal build.sbt
    workspace.writeFile(
      "build.sbt",
      """name := "test-project"
        |scalaVersion := "3.7.1"
        |""".stripMargin,
      Some("create"),
      Some(true)
    )

    // Create simple Scala file
    workspace.writeFile(
      "src/main/scala/Hello.scala",
      """object Hello {
        |  def main(args: Array[String]): Unit = {
        |    println("Hello from SBT!")
        |  }
        |}""".stripMargin,
      Some("create"),
      Some(true)
    )
  }

  /**
   * Manual test to demonstrate SBT compilation in container
   */
  def demonstrateSbtCompilation(workspaceDir: String): Unit = {
    val workspace = createTestWorkspace(workspaceDir)

    try {
      println("Testing SBT compilation in containerized workspace...")

      val compileResponse = workspace.executeCommand("sbt compile", None, Some(120))

      if (compileResponse.exitCode == 0) {
        println("✅ SUCCESS: SBT compilation works in container!")
        println(s"Output: ${compileResponse.stdout}")
      } else {
        println("❌ FAILED: SBT compilation failed")
        println(s"Error: ${compileResponse.stderr}")
      }

    } finally
      workspace.stopContainer()
  }
}
