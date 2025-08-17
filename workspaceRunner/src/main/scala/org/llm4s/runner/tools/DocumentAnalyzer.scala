package org.llm4s.runner.tools

import scala.annotation.nowarn
import org.llm4s.runner.{ MetalsServerManager, SimpleTool, SimpleToolResult }
import upickle.default._
import org.eclipse.lsp4j._
import org.eclipse.lsp4j.services.LanguageServer

import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._
import scala.util.Try

// Request/Response case classes for document analysis
case class DocumentAnalysisRequest(
  filePath: String
)

object DocumentAnalysisRequest {
  implicit val rw: ReadWriter[DocumentAnalysisRequest] = macroRW
}

case class DocumentSymbolInfo(
  name: String,
  kind: String,
  range: SymbolDefinition,
  selectionRange: SymbolDefinition,
  detail: Option[String],
  children: List[DocumentSymbolInfo]
)

object DocumentSymbolInfo {
  implicit val rw: ReadWriter[DocumentSymbolInfo] = macroRW
}

case class DocumentStructure(
  packageName: Option[String],
  imports: List[String],
  symbols: List[DocumentSymbolInfo],
  totalSymbols: Int,
  symbolsByKind: Map[String, Int]
)

object DocumentStructure {
  implicit val rw: ReadWriter[DocumentStructure] = macroRW
}

case class DocumentAnalysisResult(
  success: Boolean,
  filePath: String,
  structure: Option[DocumentStructure],
  error: Option[String]
)

object DocumentAnalysisResult {
  implicit val rw: ReadWriter[DocumentAnalysisResult] = macroRW
}

/**
 * Tool for analyzing the structure and symbols within a specific document.
 * Essential for understanding file organization and extracting code structure.
 */
class DocumentAnalyzer(metalsServerManager: MetalsServerManager) extends SimpleTool {

  override def name: String = "document_analyzer"

  override def description: String =
    "Analyze the structure of a Scala source file to extract symbols, imports, package information, and hierarchical organization. Essential for understanding file structure."

  override def parameterSchema: ujson.Value = ujson.Obj(
    "type" -> "object",
    "properties" -> ujson.Obj(
      "filePath" -> ujson.Obj(
        "type"        -> "string",
        "description" -> "Path to the source file to analyze"
      )
    ),
    "required" -> ujson.Arr("filePath")
  )

  override def execute(arguments: ujson.Value): SimpleToolResult =
    Try {
      val request = read[DocumentAnalysisRequest](arguments)

      metalsServerManager.getLanguageServer() match {
        case Some(languageServer) =>
          analyzeWithLsp(languageServer, request)
        case None =>
          DocumentAnalysisResult(
            success = false,
            filePath = request.filePath,
            structure = None,
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
        error = Some(s"Document analysis failed: ${ex.getMessage}")
      )
    }.get

  private def analyzeWithLsp(languageServer: LanguageServer, request: DocumentAnalysisRequest): DocumentAnalysisResult =
    try {
      val textDocument = new TextDocumentIdentifier(filePathToUri(request.filePath))

      // Get document symbols
      val symbolParams = new DocumentSymbolParams(textDocument)
      val symbolResult = languageServer.getTextDocumentService
        .documentSymbol(symbolParams)
        .get(30, TimeUnit.SECONDS)

      val structure = analyzeDocumentStructure(symbolResult, request.filePath)

      DocumentAnalysisResult(
        success = true,
        filePath = request.filePath,
        structure = Some(structure),
        error = None
      )

    } catch {
      case ex: Exception =>
        DocumentAnalysisResult(
          success = false,
          filePath = request.filePath,
          structure = None,
          error = Some(s"LSP document analysis failed: ${ex.getMessage}")
        )
    }

  private def analyzeDocumentStructure(
    result: java.util.List[org.eclipse.lsp4j.jsonrpc.messages.Either[SymbolInformation, DocumentSymbol]],
    filePath: String
  ): DocumentStructure = {

    val symbols = result.asScala.toList.map { either =>
      if (either.isRight) {
        // DocumentSymbol format (hierarchical)
        convertDocumentSymbol(either.getRight)
      } else {
        // SymbolInformation format (flat)
        convertSymbolInformation(either.getLeft)
      }
    }

    // Extract package and imports by analyzing top-level structure
    val (packageName, imports) = extractPackageAndImports(symbols, filePath)

    // Calculate statistics
    val totalSymbols  = countTotalSymbols(symbols)
    val symbolsByKind = countSymbolsByKind(symbols)

    DocumentStructure(
      packageName = packageName,
      imports = imports,
      symbols = symbols,
      totalSymbols = totalSymbols,
      symbolsByKind = symbolsByKind
    )
  }

  private def convertDocumentSymbol(symbol: DocumentSymbol): DocumentSymbolInfo = {
    val range          = convertRangeToDefinition(symbol.getRange, "")
    val selectionRange = convertRangeToDefinition(symbol.getSelectionRange, "")
    val children = Option(symbol.getChildren)
      .map(_.asScala.toList.map(convertDocumentSymbol))
      .getOrElse(List.empty)

    DocumentSymbolInfo(
      name = symbol.getName,
      kind = symbol.getKind.toString,
      range = range,
      selectionRange = selectionRange,
      detail = Option(symbol.getDetail).filter(_.nonEmpty),
      children = children
    )
  }

  @nowarn("cat=deprecation")
  private def convertSymbolInformation(symbolInfo: SymbolInformation): DocumentSymbolInfo = {
    val location = convertLocationToDefinition(symbolInfo.getLocation)

    DocumentSymbolInfo(
      name = symbolInfo.getName,
      kind = symbolInfo.getKind.toString,
      range = location,
      selectionRange = location, // Same as range for SymbolInformation
      detail = None,
      children = List.empty // SymbolInformation is flat
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

  private def convertRangeToDefinition(range: Range, uri: String): SymbolDefinition =
    SymbolDefinition(
      uri = uri,
      line = range.getStart.getLine,
      character = range.getStart.getCharacter,
      endLine = range.getEnd.getLine,
      endCharacter = range.getEnd.getCharacter
    )

  private def extractPackageAndImports(
    symbols: List[DocumentSymbolInfo],
    @annotation.nowarn("cat=unused") _filePath: String
  ): (Option[String], List[String]) = {
    // This is a simplified extraction - in a real implementation, we might need to read the file content
    // or use additional LSP calls to get accurate package/import information

    // Look for package-like symbols
    val packageSymbol = symbols.find(s => s.kind == "Package" || s.name.contains("package"))
    val packageName   = packageSymbol.map(_.name.replaceAll("package\\s+", ""))

    // Look for import-like symbols (this is heuristic-based)
    val imports = symbols
      .filter(s => s.kind == "Namespace" || s.name.startsWith("import"))
      .map(_.name)
      .distinct

    (packageName, imports)
  }

  private def countTotalSymbols(symbols: List[DocumentSymbolInfo]): Int = {
    def countRecursive(syms: List[DocumentSymbolInfo]): Int =
      syms.map(s => 1 + countRecursive(s.children)).sum
    countRecursive(symbols)
  }

  private def countSymbolsByKind(symbols: List[DocumentSymbolInfo]): Map[String, Int] = {
    def collectKinds(syms: List[DocumentSymbolInfo]): List[String] =
      syms.flatMap(s => s.kind :: collectKinds(s.children))

    collectKinds(symbols)
      .groupBy(identity)
      .view
      .mapValues(_.size)
      .toMap
  }

  private def filePathToUri(filePath: String): String =
    if (filePath.startsWith("file://")) {
      filePath
    } else {
      val path = if (filePath.startsWith("/")) filePath else s"/$filePath"
      s"file://$path"
    }
}
