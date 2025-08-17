package org.llm4s.runner.tools

import scala.annotation.nowarn
import org.llm4s.runner.{ MetalsServerManager, SimpleTool, SimpleToolResult }
import upickle.default._
import org.eclipse.lsp4j._
import org.eclipse.lsp4j.services.LanguageServer

import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._
import scala.util.Try

// Request/Response case classes for comprehensive code analysis
case class CodeAnalysisRequest(
  filePath: String,
  line: Int,
  character: Int
)

object CodeAnalysisRequest {
  implicit val rw: ReadWriter[CodeAnalysisRequest] = macroRW
}

case class SymbolDefinition(
  uri: String,
  line: Int,
  character: Int,
  endLine: Int,
  endCharacter: Int
)

object SymbolDefinition {
  implicit val rw: ReadWriter[SymbolDefinition] = macroRW
}

case class TypeInformation(
  symbolName: String,
  symbolType: String,
  documentation: Option[String],
  signature: Option[String]
)

object TypeInformation {
  implicit val rw: ReadWriter[TypeInformation] = macroRW
}

case class CodeAnalysisResult(
  success: Boolean,
  typeInfo: Option[TypeInformation],
  definition: Option[SymbolDefinition],
  referencesCount: Int,
  error: Option[String]
)

object CodeAnalysisResult {
  implicit val rw: ReadWriter[CodeAnalysisResult] = macroRW
}

/**
 * Tool for comprehensive code analysis at a specific position.
 * Combines hover, definition, and reference information for agent understanding.
 */
class CodeAnalyzer(metalsServerManager: MetalsServerManager) extends SimpleTool {

  override def name: String = "code_analyzer"

  override def description: String =
    "Analyze code at a specific position to get comprehensive symbol information including type, definition location, and usage count. Essential for understanding code structure."

  override def parameterSchema: ujson.Value = ujson.Obj(
    "type" -> "object",
    "properties" -> ujson.Obj(
      "filePath" -> ujson.Obj(
        "type"        -> "string",
        "description" -> "Path to the source file to analyze"
      ),
      "line" -> ujson.Obj(
        "type"        -> "integer",
        "description" -> "Line number (0-based) of the symbol to analyze"
      ),
      "character" -> ujson.Obj(
        "type"        -> "integer",
        "description" -> "Character position (0-based) of the symbol to analyze"
      )
    ),
    "required" -> ujson.Arr("filePath", "line", "character")
  )

  override def execute(arguments: ujson.Value): SimpleToolResult =
    Try {
      val request = read[CodeAnalysisRequest](arguments)

      metalsServerManager.getLanguageServer() match {
        case Some(languageServer) =>
          analyzeWithLsp(languageServer, request)
        case None =>
          CodeAnalysisResult(
            success = false,
            typeInfo = None,
            definition = None,
            referencesCount = 0,
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
        error = Some(s"Code analysis failed: ${ex.getMessage}")
      )
    }.get

  private def analyzeWithLsp(languageServer: LanguageServer, request: CodeAnalysisRequest): CodeAnalysisResult =
    try {
      val textDocument = new TextDocumentIdentifier(filePathToUri(request.filePath))
      val position     = new Position(request.line, request.character)

      // Get type information via hover
      val typeInfo = getTypeInformation(languageServer, textDocument, position)

      // Get definition location
      val definition = getDefinitionLocation(languageServer, textDocument, position)

      // Get references count
      val referencesCount = getReferencesCount(languageServer, textDocument, position)

      CodeAnalysisResult(
        success = true,
        typeInfo = typeInfo,
        definition = definition,
        referencesCount = referencesCount,
        error = None
      )

    } catch {
      case ex: Exception =>
        CodeAnalysisResult(
          success = false,
          typeInfo = None,
          definition = None,
          referencesCount = 0,
          error = Some(s"LSP analysis failed: ${ex.getMessage}")
        )
    }

  private def getTypeInformation(
    languageServer: LanguageServer,
    textDocument: TextDocumentIdentifier,
    position: Position
  ): Option[TypeInformation] =
    Try {
      val hoverParams = new HoverParams(textDocument, position)
      val hoverResult = languageServer.getTextDocumentService
        .hover(hoverParams)
        .get(10, TimeUnit.SECONDS)

      Option(hoverResult).map { hover =>
        val contents                   = hover.getContents
        val (documentation, signature) = extractHoverContent(contents)

        TypeInformation(
          symbolName = extractSymbolName(signature.getOrElse("")),
          symbolType = extractSymbolType(signature.getOrElse("")),
          documentation = documentation,
          signature = signature
        )
      }
    }.toOption.flatten

  private def getDefinitionLocation(
    languageServer: LanguageServer,
    textDocument: TextDocumentIdentifier,
    position: Position
  ): Option[SymbolDefinition] =
    Try {
      val definitionParams = new DefinitionParams(textDocument, position)
      val definitionResult = languageServer.getTextDocumentService
        .definition(definitionParams)
        .get(10, TimeUnit.SECONDS)

      convertFirstLocation(definitionResult)
    }.toOption.flatten

  private def getReferencesCount(
    languageServer: LanguageServer,
    textDocument: TextDocumentIdentifier,
    position: Position
  ): Int =
    Try {
      val referencesParams = new ReferenceParams()
      referencesParams.setTextDocument(textDocument)
      referencesParams.setPosition(position)
      referencesParams.setContext(new ReferenceContext(true)) // Include declaration

      val referencesResult = languageServer.getTextDocumentService
        .references(referencesParams)
        .get(10, TimeUnit.SECONDS)

      Option(referencesResult).map(_.size()).getOrElse(0)
    }.getOrElse(0)

  @nowarn("cat=deprecation")
  private def extractHoverContent(
    contents: org.eclipse.lsp4j.jsonrpc.messages.Either[java.util.List[
      org.eclipse.lsp4j.jsonrpc.messages.Either[String, MarkedString]
    ], MarkupContent]
  ): (Option[String], Option[String]) =
    if (contents.isRight) {
      // MarkupContent
      val content    = contents.getRight.getValue
      val (doc, sig) = separateDocumentationAndSignature(content)
      (doc, sig)
    } else {
      // List of Either[String, MarkedString]
      val strings = Option(contents.getLeft)
        .map(_.asScala.toList.map(either => if (either.isRight) either.getRight.getValue else either.getLeft))
        .getOrElse(List.empty)

      val combined   = strings.mkString("\n")
      val (doc, sig) = separateDocumentationAndSignature(combined)
      (doc, sig)
    }

  private def separateDocumentationAndSignature(content: String): (Option[String], Option[String]) = {
    // Simple heuristic: first code block is signature, rest is documentation
    val codeBlockPattern = """```[\w]*\n(.*?)\n```""".r
    val codeBlocks       = codeBlockPattern.findAllMatchIn(content).map(_.group(1)).toList

    val signature     = codeBlocks.headOption
    val documentation = if (content.trim.nonEmpty) Some(content) else None

    (documentation, signature)
  }

  private def extractSymbolName(signature: String): String = {
    val patterns = List(
      """(val|var|def|class|trait|object)\s+(\w+)""".r,
      """(\w+)\s*:""".r,
      """(\w+)""".r
    )

    patterns
      .flatMap(_.findFirstMatchIn(signature))
      .headOption
      .map(m => if (m.groupCount > 1) m.group(2) else m.group(1))
      .getOrElse("unknown")
  }

  private def extractSymbolType(signature: String): String = {
    val typePattern = """:\s*([^=\n]+)""".r
    typePattern
      .findFirstMatchIn(signature)
      .map(_.group(1).trim)
      .getOrElse("unknown")
  }

  private def convertFirstLocation(
    result: org.eclipse.lsp4j.jsonrpc.messages.Either[java.util.List[? <: Location], java.util.List[? <: LocationLink]]
  ): Option[SymbolDefinition] =
    if (result.isLeft) {
      Option(result.getLeft).flatMap(_.asScala.headOption).map(convertLocation)
    } else {
      Option(result.getRight).flatMap(_.asScala.headOption).map(convertLocationLink)
    }

  private def convertLocation(location: Location): SymbolDefinition = {
    val range = location.getRange
    SymbolDefinition(
      uri = location.getUri,
      line = range.getStart.getLine,
      character = range.getStart.getCharacter,
      endLine = range.getEnd.getLine,
      endCharacter = range.getEnd.getCharacter
    )
  }

  private def convertLocationLink(locationLink: LocationLink): SymbolDefinition = {
    val range = locationLink.getTargetRange
    SymbolDefinition(
      uri = locationLink.getTargetUri,
      line = range.getStart.getLine,
      character = range.getStart.getCharacter,
      endLine = range.getEnd.getLine,
      endCharacter = range.getEnd.getCharacter
    )
  }

  private def filePathToUri(filePath: String): String =
    if (filePath.startsWith("file://")) {
      filePath
    } else {
      val path = if (filePath.startsWith("/")) filePath else s"/$filePath"
      s"file://$path"
    }
}
