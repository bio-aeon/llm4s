package org.llm4s.runner.tools

import org.llm4s.runner.{ MetalsServerManager, SimpleTool, SimpleToolResult }
import upickle.default._
import org.eclipse.lsp4j._
import org.eclipse.lsp4j.services.LanguageServer

import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._
import scala.util.Try

// Request/Response case classes for references search
case class ReferencesRequest(
  filePath: String,
  line: Int,
  character: Int,
  includeDeclaration: Boolean = true
)

object ReferencesRequest {
  implicit val rw: ReadWriter[ReferencesRequest] = macroRW
}

case class ReferenceInfo(
  uri: String,
  line: Int,
  character: Int,
  endLine: Int,
  endCharacter: Int,
  context: Option[String] = None
)

object ReferenceInfo {
  implicit val rw: ReadWriter[ReferenceInfo] = macroRW
}

case class ReferencesByFile(
  filePath: String,
  references: List[ReferenceInfo],
  count: Int
)

object ReferencesByFile {
  implicit val rw: ReadWriter[ReferencesByFile] = macroRW
}

case class ReferencesResult(
  success: Boolean,
  symbolName: Option[String],
  totalReferences: Int,
  fileCount: Int,
  referencesByFile: List[ReferencesByFile],
  error: Option[String]
)

object ReferencesResult {
  implicit val rw: ReadWriter[ReferencesResult] = macroRW
}

/**
 * Tool for finding all references to a symbol across the workspace.
 * Essential for impact analysis when modifying or understanding symbol usage.
 */
@annotation.nowarn("cat=deprecation")
class ReferencesFinder(metalsServerManager: MetalsServerManager) extends SimpleTool {

  override def name: String = "references_finder"

  override def description: String =
    "Find all references to a symbol across the workspace. Essential for impact analysis when planning code changes or understanding symbol usage patterns."

  override def parameterSchema: ujson.Value = ujson.Obj(
    "type" -> "object",
    "properties" -> ujson.Obj(
      "filePath" -> ujson.Obj(
        "type"        -> "string",
        "description" -> "Path to the source file containing the symbol"
      ),
      "line" -> ujson.Obj(
        "type"        -> "integer",
        "description" -> "Line number (0-based) of the symbol"
      ),
      "character" -> ujson.Obj(
        "type"        -> "integer",
        "description" -> "Character position (0-based) of the symbol"
      ),
      "includeDeclaration" -> ujson.Obj(
        "type"        -> "boolean",
        "description" -> "Whether to include the symbol declaration in results (default: true)",
        "default"     -> true
      )
    ),
    "required" -> ujson.Arr("filePath", "line", "character")
  )

  override def execute(arguments: ujson.Value): SimpleToolResult =
    Try {
      val request = read[ReferencesRequest](arguments)

      metalsServerManager.getLanguageServer() match {
        case Some(languageServer) =>
          findReferencesWithLsp(languageServer, request)
        case None =>
          ReferencesResult(
            success = false,
            symbolName = None,
            totalReferences = 0,
            fileCount = 0,
            referencesByFile = List.empty,
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
        error = Some(s"References search failed: ${ex.getMessage}")
      )
    }.get

  private def findReferencesWithLsp(languageServer: LanguageServer, request: ReferencesRequest): ReferencesResult =
    try {
      val textDocument = new TextDocumentIdentifier(filePathToUri(request.filePath))
      val position     = new Position(request.line, request.character)

      // Get symbol name first for better reporting
      val symbolName = getSymbolName(languageServer, textDocument, position)

      // Find all references
      val referencesParams = new ReferenceParams()
      referencesParams.setTextDocument(textDocument)
      referencesParams.setPosition(position)
      referencesParams.setContext(new ReferenceContext(request.includeDeclaration))

      val referencesResult = languageServer.getTextDocumentService
        .references(referencesParams)
        .get(30, TimeUnit.SECONDS)

      val references = Option(referencesResult)
        .map(_.asScala.toList)
        .getOrElse(List.empty)

      // Group references by file
      val referencesByFile = groupReferencesByFile(references)

      ReferencesResult(
        success = true,
        symbolName = symbolName,
        totalReferences = references.size,
        fileCount = referencesByFile.size,
        referencesByFile = referencesByFile,
        error = None
      )

    } catch {
      case ex: Exception =>
        ReferencesResult(
          success = false,
          symbolName = None,
          totalReferences = 0,
          fileCount = 0,
          referencesByFile = List.empty,
          error = Some(s"LSP references search failed: ${ex.getMessage}")
        )
    }

  private def getSymbolName(
    languageServer: LanguageServer,
    textDocument: TextDocumentIdentifier,
    position: Position
  ): Option[String] =
    Try {
      val hoverParams = new HoverParams(textDocument, position)
      val hoverResult = languageServer.getTextDocumentService
        .hover(hoverParams)
        .get(5, TimeUnit.SECONDS)

      Option(hoverResult).flatMap { hover =>
        val contents = hover.getContents
        extractSymbolNameFromHover(contents)
      }
    }.toOption.flatten

  private def extractSymbolNameFromHover(
    contents: org.eclipse.lsp4j.jsonrpc.messages.Either[java.util.List[
      org.eclipse.lsp4j.jsonrpc.messages.Either[String, MarkedString]
    ], MarkupContent]
  ): Option[String] = {
    val text = if (contents.isRight) {
      contents.getRight.getValue
    } else {
      Option(contents.getLeft)
        .map(
          _.asScala.toList
            .map(either => if (either.isRight) either.getRight.getValue else either.getLeft)
            .mkString("\n")
        )
        .getOrElse("")
    }

    // Extract symbol name using simple patterns
    val patterns = List(
      """(val|var|def|class|trait|object)\s+(\w+)""".r,
      """(\w+)\s*:""".r,
      """(\w+)""".r
    )

    patterns
      .flatMap(_.findFirstMatchIn(text))
      .headOption
      .map(m => if (m.groupCount > 1) m.group(2) else m.group(1))
  }

  private def groupReferencesByFile(references: List[Location]): List[ReferencesByFile] =
    references
      .groupBy(_.getUri)
      .map { case (uri, locations) =>
        val referenceInfos = locations.map(convertLocationToReferenceInfo)
        ReferencesByFile(
          filePath = uriToFilePath(uri),
          references = referenceInfos,
          count = referenceInfos.size
        )
      }
      .toList
      .sortBy(_.filePath)

  private def convertLocationToReferenceInfo(location: Location): ReferenceInfo = {
    val range = location.getRange
    ReferenceInfo(
      uri = location.getUri,
      line = range.getStart.getLine,
      character = range.getStart.getCharacter,
      endLine = range.getEnd.getLine,
      endCharacter = range.getEnd.getCharacter,
      context = None // Could be enhanced to include surrounding context
    )
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
}
