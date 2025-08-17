package org.llm4s.runner.tools

import org.llm4s.runner.{ MetalsServerManager, SimpleTool, SimpleToolResult }
import upickle.default._
import org.eclipse.lsp4j._
import org.eclipse.lsp4j.services.LanguageServer

import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._
import scala.util.Try

// Request/Response case classes for symbol search
case class SymbolSearchRequest(
  query: String,
  maxResults: Option[Int] = Some(50)
)

object SymbolSearchRequest {
  implicit val rw: ReadWriter[SymbolSearchRequest] = macroRW
}

case class WorkspaceSymbolInfo(
  name: String,
  kind: String,
  containerName: Option[String],
  location: SymbolDefinition,
  detail: Option[String]
)

object WorkspaceSymbolInfo {
  implicit val rw: ReadWriter[WorkspaceSymbolInfo] = macroRW
}

case class SymbolSearchResult(
  success: Boolean,
  symbols: List[WorkspaceSymbolInfo],
  totalFound: Int,
  query: String,
  error: Option[String]
)

object SymbolSearchResult {
  implicit val rw: ReadWriter[SymbolSearchResult] = macroRW
}

/**
 * Tool for searching symbols across the entire workspace.
 * Essential for understanding codebase structure and finding relevant symbols.
 */
@annotation.nowarn("cat=deprecation")
class SymbolSearcher(metalsServerManager: MetalsServerManager) extends SimpleTool {

  override def name: String = "symbol_searcher"

  override def description: String =
    "Search for symbols (classes, methods, variables, etc.) across the entire workspace. Essential for understanding codebase structure and finding specific symbols."

  override def parameterSchema: ujson.Value = ujson.Obj(
    "type" -> "object",
    "properties" -> ujson.Obj(
      "query" -> ujson.Obj(
        "type"        -> "string",
        "description" -> "Search query - can be partial symbol name, pattern, or full name"
      ),
      "maxResults" -> ujson.Obj(
        "type"        -> "integer",
        "description" -> "Maximum number of results to return (default: 50)",
        "default"     -> 50
      )
    ),
    "required" -> ujson.Arr("query")
  )

  override def execute(arguments: ujson.Value): SimpleToolResult =
    Try {
      val request = read[SymbolSearchRequest](arguments)

      metalsServerManager.getLanguageServer() match {
        case Some(languageServer) =>
          searchWithLsp(languageServer, request)
        case None =>
          SymbolSearchResult(
            success = false,
            symbols = List.empty,
            totalFound = 0,
            query = request.query,
            error = Some("Metals Language Server is not ready - please try again")
          )
      }
    }.map { result =>
      SimpleToolResult(
        success = result.success,
        result = writeJs(result)
      )
    }.recover { case ex =>
      SimpleToolResult(
        success = false,
        result = ujson.Str(""),
        error = Some(s"Symbol search failed: ${ex.getMessage}")
      )
    }.get

  private def searchWithLsp(languageServer: LanguageServer, request: SymbolSearchRequest): SymbolSearchResult =
    try {
      val symbolParams = new WorkspaceSymbolParams(request.query)

      val symbolResults = languageServer.getWorkspaceService
        .symbol(symbolParams)
        .get(30, TimeUnit.SECONDS)

      val symbols = Option(symbolResults)
        .map { result =>
          if (result.isLeft) {
            // List of SymbolInformation (legacy)
            result.getLeft.asScala.toList.map(convertSymbolInformation)
          } else {
            // List of WorkspaceSymbol (newer)
            result.getRight.asScala.toList.map(convertWorkspaceSymbol)
          }
        }
        .getOrElse(List.empty)

      val limitedResults = request.maxResults match {
        case Some(limit) => symbols.take(limit)
        case None        => symbols
      }

      SymbolSearchResult(
        success = true,
        symbols = limitedResults,
        totalFound = symbols.size,
        query = request.query,
        error = None
      )

    } catch {
      case ex: Exception =>
        SymbolSearchResult(
          success = false,
          symbols = List.empty,
          totalFound = 0,
          query = request.query,
          error = Some(s"LSP symbol search failed: ${ex.getMessage}")
        )
    }

  private def convertSymbolInformation(symbolInfo: SymbolInformation): WorkspaceSymbolInfo = {
    val location = convertLocationToDefinition(symbolInfo.getLocation)

    WorkspaceSymbolInfo(
      name = symbolInfo.getName,
      kind = symbolInfo.getKind.toString,
      containerName = Option(symbolInfo.getContainerName).filter(_.nonEmpty),
      location = location,
      detail = None // SymbolInformation doesn't include detail
    )
  }

  private def convertWorkspaceSymbol(workspaceSymbol: WorkspaceSymbol): WorkspaceSymbolInfo = {
    val locationEither = workspaceSymbol.getLocation
    val location = if (locationEither.isLeft) {
      convertLocationToDefinition(locationEither.getLeft)
    } else {
      // WorkspaceSymbolLocation case - convert to basic location
      val wsLocation = locationEither.getRight
      SymbolDefinition(
        uri = wsLocation.getUri,
        line = 0, // WorkspaceSymbolLocation doesn't have range info
        character = 0,
        endLine = 0,
        endCharacter = 0
      )
    }

    WorkspaceSymbolInfo(
      name = workspaceSymbol.getName,
      kind = workspaceSymbol.getKind.toString,
      containerName = Option(workspaceSymbol.getContainerName).filter(_.nonEmpty),
      location = location,
      detail = Option(workspaceSymbol.getData).map(_.toString)
    )
  }

  private def convertLocationToDefinition(location: Location): SymbolDefinition = {
    val range = location.getRange
    SymbolDefinition(
      uri = location.getUri,
      line = range.getStart.getLine,
      character = range.getStart.getCharacter,
      endLine = range.getEnd.getLine,
      endCharacter = range.getEnd.getCharacter
    )
  }
}
