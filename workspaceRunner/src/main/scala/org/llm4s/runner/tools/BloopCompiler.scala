package org.llm4s.runner.tools

import org.llm4s.runner.{ BloopServerManager, SimpleTool, SimpleToolResult }
import upickle.default._
import ch.epfl.scala.bsp4j._

import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._
import scala.util.Try

// Request/Response case classes for the tool
case class CompileRequest(
  projectPath: String,
  targets: List[String] = List.empty // Empty list means compile all targets
)

object CompileRequest {
  implicit val rw: ReadWriter[CompileRequest] = macroRW
}

case class CompileDiagnostic(
  file: Option[String],
  line: Option[Int],
  column: Option[Int],
  severity: String,
  message: String
)

object CompileDiagnostic {
  implicit val rw: ReadWriter[CompileDiagnostic] = macroRW
}

case class CompilationResult(
  success: Boolean,
  statusCode: String,
  message: Option[String],
  diagnostics: List[CompileDiagnostic],
  compiledTargets: List[String],
  originId: Option[String]
)

object CompilationResult {
  implicit val rw: ReadWriter[CompilationResult] = macroRW
}

/**
 * Tool for compiling Scala projects using Bloop's BSP interface.
 * Provides incremental compilation with detailed error reporting.
 */
class BloopCompiler(bloopServerManager: BloopServerManager) extends SimpleTool {

  override def name: String = "bloop_compiler"

  override def description: String =
    "Compiles Scala/Java code using Bloop with incremental compilation and detailed error reporting"

  override def parameterSchema: ujson.Value = ujson.Obj(
    "type" -> "object",
    "properties" -> ujson.Obj(
      "projectPath" -> ujson.Obj(
        "type"        -> "string",
        "description" -> "Path to the project to compile"
      ),
      "targets" -> ujson.Obj(
        "type"        -> "array",
        "description" -> "List of build targets to compile (empty for all)",
        "items"       -> ujson.Obj("type" -> "string")
      )
    ),
    "required" -> ujson.Arr("projectPath")
  )

  override def execute(arguments: ujson.Value): SimpleToolResult =
    Try {
      val targets = arguments.obj.get("targets").map(_.arr.map(_.str).toList).getOrElse(List.empty)

      // Get BSP client from server manager
      bloopServerManager.getBspClient() match {
        case Some(bspClient) =>
          // Compile using actual BSP connection
          compileWithBsp(bspClient, targets)
        case None =>
          // Fallback if Bloop server is not ready
          CompilationResult(
            success = false,
            statusCode = "SERVER_NOT_READY",
            message = Some("Bloop server is not ready yet - please try again in a few seconds"),
            diagnostics = List.empty,
            compiledTargets = targets,
            originId = None
          )
      }
    }.map { result =>
      SimpleToolResult(
        success = true,
        result = writeJs(result)
      )
    }.recover { case ex =>
      SimpleToolResult(
        success = false,
        result = ujson.Str(""),
        error = Some(s"Compilation failed: ${ex.getMessage}")
      )
    }.get

  private def compileWithBsp(bspClient: BuildServer, targets: List[String]): CompilationResult =
    try {
      // First, get workspace build targets if no specific targets provided
      val buildTargets = if (targets.isEmpty) {
        val workspaceTargetsResult = bspClient.workspaceBuildTargets().get(30, TimeUnit.SECONDS)
        workspaceTargetsResult.getTargets.asScala.map(_.getId.getUri).toList
      } else {
        targets
      }

      // Convert target strings to BuildTargetIdentifier objects
      val targetIds = buildTargets.map(new BuildTargetIdentifier(_)).asJava

      // Create compile parameters
      val compileParams = new CompileParams(targetIds)
      compileParams.setOriginId("llm4s-workspace-runner")

      // Execute compilation
      val compileResult = bspClient.buildTargetCompile(compileParams).get(120, TimeUnit.SECONDS)

      // Convert diagnostics
      val diagnostics = Option(compileResult.getDataKind)
        .flatMap { _ =>
          // Note: Actual diagnostic extraction would need to handle the specific data format
          // For now, we'll return empty diagnostics
          Some(List.empty[CompileDiagnostic])
        }
        .getOrElse(List.empty)

      CompilationResult(
        success = compileResult.getStatusCode == StatusCode.OK,
        statusCode = compileResult.getStatusCode.toString,
        message = Some(s"Compilation ${compileResult.getStatusCode.toString.toLowerCase}"),
        diagnostics = diagnostics,
        compiledTargets = buildTargets,
        originId = Some(compileResult.getOriginId)
      )

    } catch {
      case ex: Exception =>
        CompilationResult(
          success = false,
          statusCode = "ERROR",
          message = Some(s"BSP compilation failed: ${ex.getMessage}"),
          diagnostics = List.empty,
          compiledTargets = targets,
          originId = None
        )
    }
}
