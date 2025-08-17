package org.llm4s.runner.tools

import org.llm4s.runner.{ BloopServerManager, SimpleTool, SimpleToolResult }
import upickle.default._
import ch.epfl.scala.bsp4j._

import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._
import scala.util.Try

// Request/Response case classes for the tool
case class ProjectAnalysisRequest(
  projectPath: String
)

object ProjectAnalysisRequest {
  implicit val rw: ReadWriter[ProjectAnalysisRequest] = macroRW
}

case class BuildTargetInfo(
  id: String,
  displayName: String,
  baseDirectory: String,
  tags: List[String],
  languageIds: List[String],
  dependencies: List[String]
)

object BuildTargetInfo {
  implicit val rw: ReadWriter[BuildTargetInfo] = macroRW
}

case class ProjectInfo(
  projectPath: String,
  buildTargets: List[BuildTargetInfo],
  totalTargets: Int,
  scalaTargets: Int,
  javaTargets: Int,
  testTargets: Int
)

object ProjectInfo {
  implicit val rw: ReadWriter[ProjectInfo] = macroRW
}

/**
 * Tool for analyzing SBT projects using Bloop's BSP interface.
 * Provides comprehensive project structure information for the SWE agent.
 */
class SbtProjectAnalyzer(bloopServerManager: BloopServerManager) extends SimpleTool {

  override def name: String = "sbt_project_analyzer"

  override def description: String =
    "Analyzes SBT project structure using Bloop BSP to understand build targets, dependencies, and project layout"

  override def parameterSchema: ujson.Value = ujson.Obj(
    "type" -> "object",
    "properties" -> ujson.Obj(
      "projectPath" -> ujson.Obj(
        "type"        -> "string",
        "description" -> "Path to the SBT project to analyze"
      )
    ),
    "required" -> ujson.Arr("projectPath")
  )

  override def execute(arguments: ujson.Value): SimpleToolResult =
    Try {
      val projectPath = arguments("projectPath").str

      // Get BSP client from server manager
      bloopServerManager.getBspClient() match {
        case Some(bspClient) =>
          // Analyze using actual BSP connection
          analyzeWithBsp(bspClient, projectPath)
        case None =>
          // Fallback if Bloop server is not ready
          ProjectInfo(
            projectPath = projectPath,
            buildTargets = List.empty,
            totalTargets = 0,
            scalaTargets = 0,
            javaTargets = 0,
            testTargets = 0
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
        error = Some(s"Project analysis failed: ${ex.getMessage}")
      )
    }.get

  private def analyzeWithBsp(bspClient: BuildServer, projectPath: String): ProjectInfo =
    try {
      // Get workspace build targets
      val workspaceTargetsResult = bspClient.workspaceBuildTargets().get(30, TimeUnit.SECONDS)
      val buildTargets           = workspaceTargetsResult.getTargets.asScala.toList

      // Convert BSP build targets to our format
      val targetInfos = buildTargets.map { target =>
        val languageIds = Option(target.getLanguageIds)
          .map(_.asScala.toList)
          .getOrElse(List.empty)

        val tags = Option(target.getTags)
          .map(_.asScala.toList.map(_.toString))
          .getOrElse(List.empty)

        BuildTargetInfo(
          id = target.getId.getUri,
          displayName = target.getDisplayName,
          baseDirectory = Option(target.getBaseDirectory).getOrElse(""),
          tags = tags,
          languageIds = languageIds,
          dependencies = List.empty // Would need buildTargetDependencies call for this
        )
      }

      // Count targets by type
      val totalTargets = buildTargets.size
      val scalaTargets = targetInfos.count(_.languageIds.contains("scala"))
      val javaTargets  = targetInfos.count(_.languageIds.contains("java"))
      val testTargets =
        targetInfos.count(_.tags.exists(tag => tag.toLowerCase.contains("test") || tag.toLowerCase.contains("spec")))

      ProjectInfo(
        projectPath = projectPath,
        buildTargets = targetInfos,
        totalTargets = totalTargets,
        scalaTargets = scalaTargets,
        javaTargets = javaTargets,
        testTargets = testTargets
      )

    } catch {
      case _: Exception =>
        // Return empty project info on error, but log the issue
        ProjectInfo(
          projectPath = projectPath,
          buildTargets = List.empty,
          totalTargets = 0,
          scalaTargets = 0,
          javaTargets = 0,
          testTargets = 0
        )
    }
}
