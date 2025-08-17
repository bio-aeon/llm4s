package org.llm4s.runner.tools

import org.llm4s.runner.{ SimpleTool, SimpleToolResult }
import upickle.default._
import org.eclipse.lsp4j._

import scala.jdk.CollectionConverters._
import scala.util.Try

// Request/Response case classes for diagnostics
case class DiagnosticsRequest(
  filePath: Option[String] = None,      // If None, get diagnostics for all files
  severityFilter: Option[String] = None // "Error", "Warning", "Information", "Hint"
)

object DiagnosticsRequest {
  implicit val rw: ReadWriter[DiagnosticsRequest] = macroRW
}

case class DiagnosticInfo(
  message: String,
  severity: String,
  line: Int,
  character: Int,
  endLine: Int,
  endCharacter: Int,
  source: Option[String],
  code: Option[String],
  relatedInformation: List[String]
)

object DiagnosticInfo {
  implicit val rw: ReadWriter[DiagnosticInfo] = macroRW
}

case class FileDiagnostics(
  filePath: String,
  diagnostics: List[DiagnosticInfo],
  errorCount: Int,
  warningCount: Int,
  infoCount: Int,
  hintCount: Int
)

object FileDiagnostics {
  implicit val rw: ReadWriter[FileDiagnostics] = macroRW
}

case class DiagnosticsResult(
  success: Boolean,
  filesDiagnostics: List[FileDiagnostics],
  totalErrors: Int,
  totalWarnings: Int,
  totalFiles: Int,
  error: Option[String]
)

object DiagnosticsResult {
  implicit val rw: ReadWriter[DiagnosticsResult] = macroRW
}

/**
 * Tool for retrieving compilation diagnostics (errors, warnings, etc.).
 * Essential for understanding what needs to be fixed in the codebase.
 */
class DiagnosticsProvider() extends SimpleTool {

  override def name: String = "diagnostics_provider"

  override def description: String =
    "Get compilation diagnostics (errors, warnings, hints) for files in the workspace. Essential for understanding what needs to be fixed."

  override def parameterSchema: ujson.Value = ujson.Obj(
    "type" -> "object",
    "properties" -> ujson.Obj(
      "filePath" -> ujson.Obj(
        "type" -> "string",
        "description" -> "Optional: specific file to get diagnostics for. If not provided, gets diagnostics for all files."
      ),
      "severityFilter" -> ujson.Obj(
        "type"        -> "string",
        "description" -> "Optional: filter by severity level",
        "enum"        -> ujson.Arr("Error", "Warning", "Information", "Hint")
      )
    ),
    "required" -> ujson.Arr()
  )

  // Store diagnostics received from publishDiagnostics
  @volatile private var storedDiagnostics: Map[String, List[Diagnostic]] = Map.empty

  override def execute(arguments: ujson.Value): SimpleToolResult =
    Try {
      val request = read[DiagnosticsRequest](arguments)

      // Diagnostics are populated via LSP callbacks, so we work with stored data
      getDiagnosticsFromStorage(request)
    }.map { result =>
      SimpleToolResult(
        success = result.success,
        result = writeJs(result)
      )
    }.recover { case ex =>
      SimpleToolResult(
        success = false,
        result = ujson.Str(""),
        error = Some(s"Diagnostics retrieval failed: ${ex.getMessage}")
      )
    }.get

  private def getDiagnosticsFromStorage(request: DiagnosticsRequest): DiagnosticsResult =
    try {
      val filteredDiagnostics = request.filePath match {
        case Some(filePath) =>
          val uri = filePathToUri(filePath)
          storedDiagnostics.get(uri) match {
            case Some(diagnostics) => Map(uri -> diagnostics)
            case None              => Map.empty[String, List[Diagnostic]]
          }
        case None =>
          storedDiagnostics
      }

      val severityFilter = request.severityFilter.flatMap(parseSeverity)

      val filesDiagnostics = filteredDiagnostics
        .map { case (uri, diagnostics) =>
          val filtered = severityFilter match {
            case Some(severity) => diagnostics.filter(_.getSeverity == severity)
            case None           => diagnostics
          }

          val filePath        = uriToFilePath(uri)
          val diagnosticInfos = filtered.map(convertDiagnostic)

          FileDiagnostics(
            filePath = filePath,
            diagnostics = diagnosticInfos,
            errorCount = diagnosticInfos.count(_.severity == "Error"),
            warningCount = diagnosticInfos.count(_.severity == "Warning"),
            infoCount = diagnosticInfos.count(_.severity == "Information"),
            hintCount = diagnosticInfos.count(_.severity == "Hint")
          )
        }
        .toList
        .sortBy(_.filePath)

      val totalErrors   = filesDiagnostics.map(_.errorCount).sum
      val totalWarnings = filesDiagnostics.map(_.warningCount).sum

      DiagnosticsResult(
        success = true,
        filesDiagnostics = filesDiagnostics,
        totalErrors = totalErrors,
        totalWarnings = totalWarnings,
        totalFiles = filesDiagnostics.size,
        error = None
      )

    } catch {
      case ex: Exception =>
        DiagnosticsResult(
          success = false,
          filesDiagnostics = List.empty,
          totalErrors = 0,
          totalWarnings = 0,
          totalFiles = 0,
          error = Some(s"Diagnostics processing failed: ${ex.getMessage}")
        )
    }

  private def convertDiagnostic(diagnostic: Diagnostic): DiagnosticInfo = {
    val range = diagnostic.getRange
    val severity = Option(diagnostic.getSeverity)
      .map(severityToString)
      .getOrElse("Unknown")

    val relatedInfo = Option(diagnostic.getRelatedInformation)
      .map(_.asScala.toList.map(_.getMessage))
      .getOrElse(List.empty)

    DiagnosticInfo(
      message = diagnostic.getMessage,
      severity = severity,
      line = range.getStart.getLine,
      character = range.getStart.getCharacter,
      endLine = range.getEnd.getLine,
      endCharacter = range.getEnd.getCharacter,
      source = Option(diagnostic.getSource),
      code = Option(diagnostic.getCode).map(_.getLeft), // Simplified - code can be string or number
      relatedInformation = relatedInfo
    )
  }

  private def severityToString(severity: DiagnosticSeverity): String = severity match {
    case DiagnosticSeverity.Error       => "Error"
    case DiagnosticSeverity.Warning     => "Warning"
    case DiagnosticSeverity.Information => "Information"
    case DiagnosticSeverity.Hint        => "Hint"
    case null                           => "Unknown"
  }

  private def parseSeverity(severityStr: String): Option[DiagnosticSeverity] = severityStr match {
    case "Error"       => Some(DiagnosticSeverity.Error)
    case "Warning"     => Some(DiagnosticSeverity.Warning)
    case "Information" => Some(DiagnosticSeverity.Information)
    case "Hint"        => Some(DiagnosticSeverity.Hint)
    case _             => None
  }

  private def filePathToUri(filePath: String): String =
    if (filePath.startsWith("file://")) {
      filePath
    } else {
      val path = if (filePath.startsWith("/")) filePath else s"/$filePath"
      s"file://$path"
    }

  private def uriToFilePath(uri: String): String =
    if (uri.startsWith("file://")) {
      uri.substring(7) // Remove "file://" prefix
    } else {
      uri
    }

  /**
   * Method to be called by the LSP client when diagnostics are published.
   * This stores the diagnostics for later retrieval.
   */
  def updateDiagnostics(uri: String, diagnostics: List[Diagnostic]): Unit =
    storedDiagnostics = storedDiagnostics + (uri -> diagnostics)

  /**
   * Clear diagnostics for a specific file.
   */
  def clearDiagnostics(uri: String): Unit =
    storedDiagnostics = storedDiagnostics - uri

  /**
   * Clear all stored diagnostics.
   */
  def clearAllDiagnostics(): Unit =
    storedDiagnostics = Map.empty
}
