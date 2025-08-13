package org.llm4s.llmconnect.extractors

import org.llm4s.llmconnect.model.ExtractorError
<<<<<<< HEAD
import org.slf4j.LoggerFactory

import java.io.File
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.{ Try, Success, Failure, Using }

import org.apache.tika.Tika
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
=======
import scala.util.Try
import java.nio.file.Files
import java.io.File
import org.apache.tika.Tika
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
<<<<<<< HEAD
import scala.io.Source
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
||||||| parent of 8bd3f68 (update: embedx-v2 on multimedia data)
=======
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
>>>>>>> 8bd3f68 (update: embedx-v2 on multimedia data)

object UniversalExtractor {

<<<<<<< HEAD
  private val logger = LoggerFactory.getLogger(getClass)
  private val tika   = new Tika()

  // ----- ADT for multimedia extraction -----
  sealed trait Extracted
  final case class TextContent(text: String)                            extends Extracted
  final case class ImageContent(image: BufferedImage)                   extends Extracted
  final case class AudioContent(samples: Array[Float], sampleRate: Int) extends Extracted
  final case class VideoContent(frames: Seq[BufferedImage], fps: Int)   extends Extracted

  // ================================= TEXT-ONLY API (unchanged) =================================
  def extract(inputPath: String): Either[ExtractorError, String] = {
    val file = new File(inputPath)
    if (!file.exists() || !file.isFile) {
      val error = ExtractorError(
        message = s"File not found or invalid: $inputPath",
        `type` = "FileNotFound",
        path = Some(inputPath)
      )
      logger.error(s"[FileNotFound] ${error.message}")
      return Left(error)
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
    } else if (pathOrUrl.endsWith(".pdf")) {
      PDFExtractor.extractText(pathOrUrl)

    } else if (pathOrUrl.endsWith(".docx")) {
      DocxExtractor.extractText(pathOrUrl)

    } else if (pathOrUrl.endsWith(".xlsx")) {
      ExcelExtractor.extractText(pathOrUrl)

    } else if (
      pathOrUrl.endsWith(".txt") ||
      pathOrUrl.endsWith(".md")
    ) {
      TextExtractor.extractText(pathOrUrl)

    } else {
      throw new RuntimeException(s"Unsupported file type: $pathOrUrl")
=======
  def extract(inputPath: String): Either[ExtractorError, String] = {
    val file = new File(inputPath)
    if (!file.exists() || !file.isFile) {
      return Left(
        ExtractorError(
          message = s"File not found or invalid: $inputPath",
          `type` = "FileNotFound",
          path = Some(inputPath)
        )
      )
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
    }

<<<<<<< HEAD
    val tika     = new Tika()
    val mimeType = tika.detect(file)
<<<<<<< HEAD
    logger.info(s"\nDetected MIME type: $mimeType")
||||||| parent of 8bd3f68 (update: embedx-v2 on multimedia data)
    val tika     = new Tika()
    val mimeType = tika.detect(file)
    logger.info(s"\nDetected MIME type: $mimeType")
=======
    val mimeType = Try(tika.detect(file)).getOrElse("application/octet-stream")
    logger.info(s"Detected MIME type: $mimeType")
>>>>>>> 8bd3f68 (update: embedx-v2 on multimedia data)

    mimeType match {
      case "application/pdf" =>
        extractPDF(file) match {
          case Success(text) => Right(text)
          case Failure(ex) =>
            val err = ExtractorError(ex.getMessage, "PDF", Some(inputPath))
            logger.error(s"[PDF ExtractorError] ${err.message}")
            Left(err)
        }

      case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" =>
        extractDocx(file) match {
          case Success(text) => Right(text)
          case Failure(ex) =>
            val err = ExtractorError(ex.getMessage, "DOCX", Some(inputPath))
            logger.error(s"[DOCX ExtractorError] ${err.message}")
            Left(err)
        }

      case "text/plain" =>
        extractText(file) match {
          case Success(text) => Right(text)
          case Failure(ex) =>
            val err = ExtractorError(ex.getMessage, "PlainText", Some(inputPath))
            logger.error(s"[PlainText ExtractorError] ${err.message}")
            Left(err)
        }

      case other =>
        val error = ExtractorError(
          message = s"Unsupported file type for text extraction: $other",
          `type` = "UnsupportedType",
          path = Some(inputPath)
        )
        logger.warn(s"[UnsupportedType] ${error.message}")
        Left(error)
    }
  }

  // ================================= MULTIMEDIA API =================================
  def extractAny(inputPath: String): Either[ExtractorError, Extracted] = {
    val file = new File(inputPath)
    if (!file.exists() || !file.isFile) {
      val err = ExtractorError(
        message = s"File not found or invalid: $inputPath",
        `type` = "FileNotFound",
        path = Some(inputPath)
      )
      logger.error(s"[FileNotFound] ${err.message}")
      return Left(err)
    }

    val mime = Try(tika.detect(file)).getOrElse("application/octet-stream")
    logger.info(s"[UniversalExtractor] MIME detected: $mime")

    if (isTextLike(mime)) {
      extract(inputPath).map(TextContent.apply)
    } else if (mime.startsWith("image/")) {
      extractImage(file)
    } else if (mime.startsWith("audio/")) {
      unsupported("AudioUnsupported", file, s"Audio extraction not implemented for $mime")
    } else if (mime.startsWith("video/")) {
      unsupported("VideoUnsupported", file, s"Video extraction not implemented for $mime")
    } else {
      unsupported("UnsupportedType", file, s"Unsupported file type for multimedia extraction: $mime")
    }
  }

  // ----- helpers -----
  private def isTextLike(mime: String): Boolean =
    mime.startsWith("text/") ||
      mime == "application/pdf" ||
      mime.endsWith("wordprocessingml.document")

  private def extractPDF(file: File): Try[String] = Try {
<<<<<<< HEAD
    val document = Loader.loadPDF(file)
    try
      new PDFTextStripper().getText(document)
    finally
      document.close()
||||||| parent of 8bd3f68 (update: embedx-v2 on multimedia data)
    val document = PDDocument.load(file)
    try
      new PDFTextStripper().getText(document)
    finally
      document.close()
=======
    val document = PDDocument.load(file)
    try new PDFTextStripper().getText(document)
    finally document.close()
>>>>>>> 8bd3f68 (update: embedx-v2 on multimedia data)
  }

  private def extractDocx(file: File): Try[String] = Try {
    val document = new XWPFDocument(Files.newInputStream(file.toPath))
    try document.getParagraphs.toArray.map(_.toString).mkString("\n")
    finally document.close()
  }

  private def extractText(file: File): Try[String] = Try {
    Using.resource(Source.fromFile(file, StandardCharsets.UTF_8.name()))(source => source.getLines().mkString("\n"))
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
=======

    mimeType match {
      case "application/pdf" =>
        extractPDF(file).toEither.left.map(err => ExtractorError(err.getMessage, "PDF", Some(inputPath)))

      case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" =>
        extractDocx(file).toEither.left.map(err => ExtractorError(err.getMessage, "DOCX", Some(inputPath)))

      case "text/plain" =>
        extractText(file).toEither.left.map(err => ExtractorError(err.getMessage, "PlainText", Some(inputPath)))

      case _ =>
        Left(
          ExtractorError(
            message = s"Unsupported file type: $mimeType",
            `type` = "UnsupportedType",
            path = Some(inputPath)
          )
        )
    }
  }

  private def extractPDF(file: File): Try[String] = Try {
    val document = PDDocument.load(file)
    try {
      val stripper = new PDFTextStripper()
      stripper.getText(document)
    } finally document.close()
  }

  private def extractDocx(file: File): Try[String] = Try {
    val document = new XWPFDocument(Files.newInputStream(file.toPath))
    try {
      val paragraphs = document.getParagraphs
      paragraphs.toArray.map(_.toString).mkString("\n")
    } finally document.close()
  }

  private def extractText(file: File): Try[String] = Try {
    Source.fromFile(file).getLines().mkString("\n")
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
  }

  private def extractImage(file: File): Either[ExtractorError, Extracted] =
    Try(ImageIO.read(file)) match {
      case Failure(ex) =>
        Left(
          ExtractorError(
            message = s"Failed to read image: ${ex.getMessage}",
            `type` = "ImageReadError",
            path = Some(file.getAbsolutePath)
          )
        )
      case Success(null) =>
        Left(
          ExtractorError(
            message = s"Unsupported or corrupted image: ${file.getName}",
            `type` = "ImageReadError",
            path = Some(file.getAbsolutePath)
          )
        )
      case Success(img) =>
        Right(ImageContent(img))
    }

  private def unsupported(kind: String, file: File, msg: String): Either[ExtractorError, Nothing] = {
    val err = ExtractorError(
      message = msg,
      `type` = kind,
      path = Some(file.getAbsolutePath)
    )
    logger.warn(s"[$kind] ${err.message}")
    Left(err)
  }
}
