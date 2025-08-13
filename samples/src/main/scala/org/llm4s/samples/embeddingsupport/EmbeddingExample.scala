package org.llm4s.samples.embeddingsupport

<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
||||||| parent of 29e3c07 (PR3: Extended Voyage model support and improved ModelSelector logic)
import org.llm4s.llmconnect.EmbeddingClient
import org.llm4s.llmconnect.config.{EmbeddingConfig, EmbeddingModelConfig}
=======
import org.llm4s.llmconnect.EmbeddingClient
>>>>>>> 29e3c07 (PR3: Extended Voyage model support and improved ModelSelector logic)
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
import org.llm4s.llmconnect.EmbeddingClient
=======
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
import org.llm4s.llmconnect.config.EmbeddingConfig
import org.llm4s.llmconnect.extractors.UniversalExtractor
<<<<<<< HEAD
<<<<<<< HEAD
import org.llm4s.llmconnect.model.{EmbeddingRequest, ExtractorError}
import org.llm4s.llmconnect.utils.{ChunkingUtils, SimilarityUtils}
||||||| parent of 8bd3f68 (update: embedx-v2 on multimedia data)
import org.llm4s.llmconnect.config.EmbeddingConfig
import org.llm4s.llmconnect.extractors.UniversalExtractor
import org.llm4s.llmconnect.model.{EmbeddingRequest, ExtractorError}
import org.llm4s.llmconnect.utils.{ChunkingUtils, SimilarityUtils}
=======
>>>>>>> 8bd3f68 (update: embedx-v2 on multimedia data)
import org.llm4s.llmconnect.EmbeddingClient
import org.llm4s.llmconnect.config.EmbeddingConfig
import org.llm4s.llmconnect.model._
import org.llm4s.llmconnect.utils.{ ModelSelector, SimilarityUtils }
import org.slf4j.LoggerFactory
||||||| parent of 29e3c07 (PR3: Extended Voyage model support and improved ModelSelector logic)
import org.llm4s.llmconnect.model.EmbeddingRequest
import org.llm4s.llmconnect.utils.SimilarityUtils
=======
import org.llm4s.llmconnect.model.EmbeddingRequest
import org.llm4s.llmconnect.utils.{ ModelSelector, SimilarityUtils }
>>>>>>> 29e3c07 (PR3: Extended Voyage model support and improved ModelSelector logic)
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
import org.llm4s.llmconnect.model.EmbeddingRequest
import org.llm4s.llmconnect.utils.{ ModelSelector, SimilarityUtils }
=======
import org.llm4s.llmconnect.model.{EmbeddingRequest, ExtractorError}
import org.llm4s.llmconnect.utils.{ChunkingUtils, SimilarityUtils}
import org.llm4s.llmconnect.EmbeddingClient
<<<<<<< HEAD
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
||||||| parent of 0013d53 (LoggerUtils to SLf4J logger)
=======
import org.slf4j.LoggerFactory
>>>>>>> 0013d53 (LoggerUtils to SLf4J logger)

import java.nio.file.{ Files, Path, Paths }
import java.time.{ ZonedDateTime, ZoneId }
import scala.jdk.CollectionConverters._
import scala.util.Try

object EmbeddingExample {

<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
  private val logger = LoggerFactory.getLogger(getClass)
||||||| parent of 29e3c07 (PR3: Extended Voyage model support and improved ModelSelector logic)
  val activeProvider = EmbeddingConfig.activeProvider.toLowerCase
  val model = activeProvider match {
    case "openai"  => EmbeddingModelConfig(EmbeddingConfig.openAI.model, 1536)
    case "voyage"  => EmbeddingModelConfig(EmbeddingConfig.voyage.model, 1024)
    case other     => throw new RuntimeException(s"Unsupported provider: $other")
  }
=======
  val provider = EmbeddingConfig.activeProvider.toLowerCase
>>>>>>> 29e3c07 (PR3: Extended Voyage model support and improved ModelSelector logic)
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
  val provider = EmbeddingConfig.activeProvider.toLowerCase
=======
||||||| parent of 0013d53 (LoggerUtils to SLf4J logger)
=======
  private val logger = LoggerFactory.getLogger(getClass)

<<<<<<< HEAD
>>>>>>> 0013d53 (LoggerUtils to SLf4J logger)
  def main(args: Array[String]): Unit = {
<<<<<<< HEAD
    LoggerUtils.info("Starting embedding example...")
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
||||||| parent of 0013d53 (LoggerUtils to SLf4J logger)
    LoggerUtils.info("Starting embedding example...")
=======
    logger.info("Starting embedding example...")
>>>>>>> 0013d53 (LoggerUtils to SLf4J logger)

<<<<<<< HEAD
<<<<<<< HEAD
||||||| parent of 8bd3f68 (update: embedx-v2 on multimedia data)
=======
  // --------- Output knobs (env-tunable) ----------
  private val MAX_ROWS_PER_FILE: Int =
    sys.env.get("MAX_ROWS_PER_FILE").flatMap(s => Try(s.toInt).toOption).getOrElse(200)
  private val TOP_DIMS_PER_ROW: Int  =
    sys.env.get("TOP_DIMS_PER_ROW").flatMap(s => Try(s.toInt).toOption).getOrElse(6)
  private val GLOBAL_TOPK: Int       =
    sys.env.get("GLOBAL_TOPK").flatMap(s => Try(s.toInt).toOption).getOrElse(10)
  private val SHOW_GLOBAL_TOP: Boolean =
    sys.env.get("SHOW_GLOBAL_TOP").map(_.trim.toLowerCase).contains("true")

>>>>>>> 8bd3f68 (update: embedx-v2 on multimedia data)
  def main(args: Array[String]): Unit = {
<<<<<<< HEAD
    logger.info("Starting embedding example...")
||||||| parent of 29e3c07 (PR3: Extended Voyage model support and improved ModelSelector logic)
  val extractedText = UniversalExtractor.extract(EmbeddingConfig.inputPath)
  val query = EmbeddingConfig.query
=======
  // Step 1: Extract input and query text
  val extractedText = UniversalExtractor.extract(EmbeddingConfig.inputPath)
  val query = EmbeddingConfig.query
>>>>>>> 29e3c07 (PR3: Extended Voyage model support and improved ModelSelector logic)
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
  // Step 1: Extract input and query text
  val extractedText = UniversalExtractor.extract(EmbeddingConfig.inputPath)
  val query = EmbeddingConfig.query
=======
    val inputPath = EmbeddingConfig.inputPath
    val query     = EmbeddingConfig.query
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
||||||| parent of 8bd3f68 (update: embedx-v2 on multimedia data)
    logger.info("Starting embedding example...")

    val inputPath = EmbeddingConfig.inputPath
    val query     = EmbeddingConfig.query
=======
    val targets = parseTargets()
    if (targets.isEmpty) {
      println("[ERR] No inputs. Set EMBEDDING_INPUT_PATH or EMBEDDING_INPUT_PATHS.")
      return
    }

    val client      = EmbeddingClient.fromConfig()
    val query       = EmbeddingConfig.query
    val queryVecOpt = embedQueryOnce(client, query)
>>>>>>> 8bd3f68 (update: embedx-v2 on multimedia data)

<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
    val inputPath = EmbeddingConfig.inputPath
    val query     = EmbeddingConfig.query
||||||| parent of 29e3c07 (PR3: Extended Voyage model support and improved ModelSelector logic)
  val request = EmbeddingRequest(Seq(extractedText, query), model)
  val provider = EmbeddingClient.fromConfig()
=======
  // Step 2: Dynamically select the model based on input text and provider
  val selectedModel = ModelSelector.selectModel(provider, extractedText)
>>>>>>> 29e3c07 (PR3: Extended Voyage model support and improved ModelSelector logic)
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
  // Step 2: Dynamically select the model based on input text and provider
  val selectedModel = ModelSelector.selectModel(provider, extractedText)
=======
    LoggerUtils.info(s"Extracting from: $inputPath")
||||||| parent of 0013d53 (LoggerUtils to SLf4J logger)
    LoggerUtils.info(s"Extracting from: $inputPath")
=======
    logger.info(s"Extracting from: $inputPath")
>>>>>>> 0013d53 (LoggerUtils to SLf4J logger)
    val extractedEither = UniversalExtractor.extract(inputPath)
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)

<<<<<<< HEAD
<<<<<<< HEAD
    logger.info(s"Extracting from: $inputPath")
    val extractedEither = UniversalExtractor.extract(inputPath)
||||||| parent of 29e3c07 (PR3: Extended Voyage model support and improved ModelSelector logic)
  provider.embed(request) match {
    case Right(response) =>
      val docVec = response.vectors.head
      val queryVec = response.vectors.last
      val score = SimilarityUtils.cosineSimilarity(docVec, queryVec)
=======
  // Step 3: Create embedding request
  val request = EmbeddingRequest(Seq(extractedText, query), model = selectedModel)
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
  // Step 3: Create embedding request
  val request = EmbeddingRequest(Seq(extractedText, query), model = selectedModel)
=======
    extractedEither match {
      case Left(error: ExtractorError) =>
        logger.error(s"[ExtractorError] ${error.message} (type: ${error.`type`}, path: ${error.path})")
        return
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
||||||| parent of 8bd3f68 (update: embedx-v2 on multimedia data)
    logger.info(s"Extracting from: $inputPath")
    val extractedEither = UniversalExtractor.extract(inputPath)

    extractedEither match {
      case Left(error: ExtractorError) =>
        logger.error(s"[ExtractorError] ${error.message} (type: ${error.`type`}, path: ${error.path})")
        return
=======
    // accumulate results
    val perFileRows = collection.mutable.ArrayBuffer.empty[(String, Seq[Row])]
    val globalText  = collection.mutable.ArrayBuffer.empty[Row]

    targets.foreach { p =>
      client.encodePath(p) match {
        case Left(err) =>
          println(s"[ERR] ${p.getFileName}: ${err.provider} -> ${err.message}")
        case Right(Nil) =>
          println(s"[WARN] ${p.getFileName}: no embeddings.")
        case Right(vecs) =>
          val rows = toRows(p.getFileName.toString, vecs, queryVecOpt, TOP_DIMS_PER_ROW)
          perFileRows += ((p.getFileName.toString, rows.take(MAX_ROWS_PER_FILE)))
          rows.filter(_.modality == "Text").foreach(globalText += _)
      }
    }
>>>>>>> 8bd3f68 (update: embedx-v2 on multimedia data)

    // ---- print report ----
    println(renderHeader(provider = EmbeddingConfig.activeProvider, query = query))

<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
  embeddingProvider.embed(request) match {
    case Right(response) =>
      val docVec = response.vectors.head
      val queryVec = response.vectors.last
      val score = SimilarityUtils.cosineSimilarity(docVec, queryVec)
>>>>>>> 29e3c07 (PR3: Extended Voyage model support and improved ModelSelector logic)
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
  embeddingProvider.embed(request) match {
    case Right(response) =>
      val docVec = response.vectors.head
      val queryVec = response.vectors.last
      val score = SimilarityUtils.cosineSimilarity(docVec, queryVec)
=======
        LoggerUtils.info(s"Generating embedding for ${inputs.size} input(s)...")
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)

<<<<<<< HEAD
<<<<<<< HEAD
    extractedEither match {
      case Left(error: ExtractorError) =>
        logger.error(s"[ExtractorError] ${error.message} (type: ${error.`type`}, path: ${error.path})")
        return
||||||| parent of 29e3c07 (PR3: Extended Voyage model support and improved ModelSelector logic)
      println(s"Similarity Score: $score")
      println(s"Top 10 values of docVec: ${docVec.take(10).mkString(", ")}")
=======
      println(s"\nProvider: $provider")
      println(s"Model Used: ${selectedModel.name}")
      println(f"Similarity Score: $score%.4f")
      println(s"Top 10 values of docVec: ${docVec.take(10).mkString(", ")}")
>>>>>>> 29e3c07 (PR3: Extended Voyage model support and improved ModelSelector logic)
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
      println(s"\nProvider: $provider")
      println(s"Model Used: ${selectedModel.name}")
      println(f"Similarity Score: $score%.4f")
      println(s"Top 10 values of docVec: ${docVec.take(10).mkString(", ")}")
=======
        val request = EmbeddingRequest(
          input = inputs :+ query,  // include query for similarity
          model = org.llm4s.llmconnect.utils.ModelSelector.selectModel()
        )
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)

<<<<<<< HEAD
<<<<<<< HEAD
      case Right(text) =>
        val inputs: Seq[String] = if (EmbeddingConfig.chunkingEnabled) {
          logger.info(s"\nChunking enabled. Using size=${EmbeddingConfig.chunkSize}, overlap=${EmbeddingConfig.chunkOverlap}")
          ChunkingUtils.chunkText(text, EmbeddingConfig.chunkSize, EmbeddingConfig.chunkOverlap)
        } else {
          logger.info("\nChunking disabled. Proceeding with full text.")
          Seq(text)
        }

||||||| parent of 0013d53 (LoggerUtils to SLf4J logger)
        LoggerUtils.info(s"Generating embedding for ${inputs.size} input(s)...")
=======
>>>>>>> 0013d53 (LoggerUtils to SLf4J logger)
        logger.info(s"\nGenerating embedding for ${inputs.size} input(s)...")
||||||| parent of 8bd3f68 (update: embedx-v2 on multimedia data)
        logger.info(s"\nGenerating embedding for ${inputs.size} input(s)...")
=======
    perFileRows.foreach { case (name, rows) =>
      println(renderFileSection(name, rows))
    }
>>>>>>> 8bd3f68 (update: embedx-v2 on multimedia data)

<<<<<<< HEAD
        val request = EmbeddingRequest(
          input = inputs :+ query,  // include query for similarity
          model = org.llm4s.llmconnect.utils.ModelSelector.selectModel()
        )

        val client = EmbeddingClient.fromConfig()
        val response = client.embed(request)

        response match {
          case Right(result) =>
            logger.info(s"\nEmbedding response metadata:\n${result.metadata}")
<<<<<<< HEAD

            // Log each embedding vector (first 10 dims only for brevity)
            result.embeddings.zipWithIndex.foreach { case (vec, idx) =>
              val label = if (idx < inputs.size) s"Chunk ${idx + 1}" else "Query"
              logger.info(s"\n[$label] Embedding: ${vec.take(10).mkString(", ")} ... [${vec.length} dims]")
            }

            // Log cosine similarity between first chunk and query
            val similarity = SimilarityUtils.cosineSimilarity(
              result.embeddings.head,
              result.embeddings.last
            )
            logger.info(f"\nCosine similarity between first doc chunk and query: $similarity%.4f")

          case Left(err) =>
            logger.error(s"\n[EmbeddingError] ${err.provider}: ${err.message}")
        }
||||||| parent of 8bd3f68 (update: embedx-v2 on multimedia data)
        val request = EmbeddingRequest(
          input = inputs :+ query,  // include query for similarity
          model = org.llm4s.llmconnect.utils.ModelSelector.selectModel()
        )

        val client = EmbeddingClient.fromConfig()
        val response = client.embed(request)

        response match {
          case Right(result) =>
            logger.info(s"\nEmbedding response metadata:\n${result.metadata}")

            // Log each embedding vector (first 10 dims only for brevity)
            result.embeddings.zipWithIndex.foreach { case (vec, idx) =>
              val label = if (idx < inputs.size) s"Chunk ${idx + 1}" else "Query"
              logger.info(s"\n[$label] Embedding: ${vec.take(10).mkString(", ")} ... [${vec.length} dims]")
            }

            // Log cosine similarity between first chunk and query
            val similarity = SimilarityUtils.cosineSimilarity(
              result.embeddings.head,
              result.embeddings.last
            )
            logger.info(f"\nCosine similarity between first doc chunk and query: $similarity%.4f")

          case Left(err) =>
            logger.error(s"\n[EmbeddingError] ${err.provider}: ${err.message}")
        }
=======
    if (SHOW_GLOBAL_TOP && globalText.nonEmpty) {
      val top = globalText.sortBy(r => -r.similarity.getOrElse(Double.NegativeInfinity)).take(GLOBAL_TOPK).toSeq
      println(renderGlobalTop(top))
>>>>>>> 8bd3f68 (update: embedx-v2 on multimedia data)
    }
||||||| parent of 29e3c07 (PR3: Extended Voyage model support and improved ModelSelector logic)
    case Left(error) =>
      println(s"Embedding failed from [${error.provider}]: ${error.message}")
      error.code.foreach(code => println(s"Status code: $code"))
=======
    case Left(error) =>
      println(s"\nEmbedding failed from [${error.provider}]: ${error.message}")
      error.code.foreach(code => println(s"Status code: $code"))
>>>>>>> 29e3c07 (PR3: Extended Voyage model support and improved ModelSelector logic)
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
    case Left(error) =>
      println(s"\nEmbedding failed from [${error.provider}]: ${error.message}")
      error.code.foreach(code => println(s"Status code: $code"))
=======
        val client = EmbeddingClient.fromConfig()
        val response = client.embed(request)

        response match {
          case Right(result) =>
            LoggerUtils.info(s"Embedding response metadata:\n${result.metadata}")
||||||| parent of 0013d53 (LoggerUtils to SLf4J logger)
            LoggerUtils.info(s"Embedding response metadata:\n${result.metadata}")
=======
>>>>>>> 0013d53 (LoggerUtils to SLf4J logger)

            // Log each embedding vector (first 10 dims only for brevity)
            result.embeddings.zipWithIndex.foreach { case (vec, idx) =>
              val label = if (idx < inputs.size) s"Chunk ${idx + 1}" else "Query"
              logger.info(s"\n[$label] Embedding: ${vec.take(10).mkString(", ")} ... [${vec.length} dims]")
            }

            // Log cosine similarity between first chunk and query
            val similarity = SimilarityUtils.cosineSimilarity(
              result.embeddings.head,
              result.embeddings.last
            )
            logger.info(f"\nCosine similarity between first doc chunk and query: $similarity%.4f")

          case Left(err) =>
            logger.error(s"\n[EmbeddingError] ${err.provider}: ${err.message}")
        }
    }
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
  }

  // ---------------- Model for printing ----------------
  final case class Row(
                        file: String,
                        id: String,
                        modality: String,
                        dim: Int,
                        model: String,
                        similarity: Option[Double],
                        topDims: Seq[(Int, Float)],
                        provider: String,
                        mime: String
                      )

  private def toRows(
                      fileName: String,
                      vecs: Seq[EmbeddingVector],
                      qOpt: Option[Seq[Double]],
                      topDims: Int
                    ): Seq[Row] = {
    def topKAbs(values: Array[Float], k: Int): Seq[(Int, Float)] = {
      val withIdx: Array[(Int, Float)] =
        values.iterator.zipWithIndex.map { case (v, i) => (i, math.abs(v)) }.toArray
      scala.util.Sorting.stableSort(withIdx, (a: (Int, Float), b: (Int, Float)) => a._2 > b._2)
      withIdx.take(math.min(k, withIdx.length)).map { case (i, _) => (i, values(i)) }.toSeq
    }

    val modalityStr = vecs.head.modality.toString
    val scored: Seq[(EmbeddingVector, Option[Double])] =
      if (modalityStr == "Text" && qOpt.isDefined) {
        val q = qOpt.get
        vecs.map { v =>
          val d = v.values.map(_.toDouble).toSeq // already L2
          v -> Some(SimilarityUtils.cosineSimilarity(d, q))
        }
      } else vecs.map(v => v -> None)

    val ordered =
      if (modalityStr == "Text") scored.sortBy { case (_, s) => -s.getOrElse(Double.NegativeInfinity) }
      else scored

    ordered.map { case (v, s) =>
      Row(
        file       = fileName,
        id         = v.id,
        modality   = v.modality.toString,
        dim        = v.dim,
        model      = v.model,
        similarity = s,
        topDims    = topKAbs(v.values, topDims),
        provider   = v.meta.getOrElse("provider", "n/a"),
        mime       = v.meta.getOrElse("mime", "n/a")
      )
    }
  }

  // ---------------- Inputs ----------------
  private def parseTargets(): Seq[Path] = {
    val multi  = sys.env.get("EMBEDDING_INPUT_PATHS")
    val single = sys.env.get("EMBEDDING_INPUT_PATH")
    val raw: Seq[String] =
      multi.map(splitList).getOrElse(single.toSeq)

    val paths = raw.map(_.trim).filter(_.nonEmpty).map(Paths.get(_))
    val expanded = paths.flatMap { p =>
      if (Files.isDirectory(p))
        Files.list(p).iterator().asScala.filter(Files.isRegularFile(_)).toSeq
      else Seq(p)
    }
    expanded.foldLeft(Vector.empty[Path]) { (acc, p) => if (acc.contains(p)) acc else acc :+ p }
  }
  private def splitList(s: String): Seq[String] = s.split("[,;]").toSeq

  // ---------------- Query embed cache ----------------
  private def embedQueryOnce(client: EmbeddingClient, query: String): Option[Seq[Double]] = {
    if (query == null || query.trim.isEmpty) return None
    val model = ModelSelector.selectModel(Text)
    val req   = EmbeddingRequest(Seq(query), model)
    client.embed(req) match {
      case Right(resp) if resp.embeddings.nonEmpty =>
        Some(l2Normalize(resp.embeddings.head))
      case _ =>
        println("[WARN] Query embedding unavailable; similarities hidden.")
        None
    }
  }
  private def l2Normalize(v: Seq[Double]): Seq[Double] = {
    val n = math.sqrt(v.map(x => x * x).sum)
    if (n <= 1e-12) v else v.map(_ / n)
  }

  // ---------------- Rendering (plain text) ----------------
  private def renderHeader(provider: String, query: String): String = {
    val time = ZonedDateTime.now(ZoneId.systemDefault()).toString
    val qStr = Option(query).map(_.trim).filter(_.nonEmpty).map(q => s""" | query: "$q"""").getOrElse("")
    s"""
====================================================================================================
Embedding Report  |  provider: $provider$qStr
generated: $time
====================================================================================================
""".stripMargin
  }

  private def renderFileSection(name: String, rows: Seq[Row]): String = {
    if (rows.isEmpty) return s"-- $name: (no rows)\n"
    val head     = rows.head
    val modality = head.modality
    val dim      = head.dim
    val model    = head.model
    val count    = rows.size

    val sb = new StringBuilder
    sb.append(s"\n-- File: $name  |  modality: $modality  |  model: $model  |  dim: $dim  |  chunks: $count\n")
    sb.append(s"-".repeat(100)).append('\n')

    rows.zipWithIndex.foreach { case (r, i) =>
      val simStr = r.similarity.map(s => f"$s%1.4f ${bar(s, 18)}").getOrElse("n/a")
      val tops   = r.topDims.map{ case (idx, v) => s"$idx:${fmt(v)}" }.mkString(", ")
      val idStr  = truncate(r.id, 56)
      sb.append(f"[${i + 1}%3d] id=$idStr%-56s  dim=${r.dim}  sim=$simStr%-28s  top=[$tops]  meta=${r.provider},${r.mime}\n")
    }
    sb.result()
  }

  private def renderGlobalTop(rows: Seq[Row]): String = {
    val sb = new StringBuilder
    sb.append("\n== Global Text Top ==\n")
    sb.append(s"-".repeat(100)).append('\n')
    rows.zipWithIndex.foreach { case (r, i) =>
      val simStr = r.similarity.map(s => f"$s%1.4f ${bar(s, 18)}").getOrElse("n/a")
      val tops   = r.topDims.map{ case (idx, v) => s"$idx:${fmt(v)}" }.mkString(", ")
      val idStr  = truncate(s"${r.file}:${r.id}", 64)
      sb.append(f"[${i + 1}%3d] id=$idStr%-64s  dim=${r.dim}  sim=$simStr%-28s  top=[$tops]  meta=${r.provider},${r.mime}\n")
    }
    sb.result()
  }

  private def bar(value: Double, width: Int): String = {
    // map cosine [-1,1] to [0,width]
    val mag  = math.min(1.0, math.max(-1.0, value))
    val fill = (((mag + 1.0) / 2.0) * width).round.toInt
    val full = "#".repeat(fill) + ".".repeat(math.max(0, width - fill))
    (if (value >= 0) "+" else "-") + "[" + full + "]"
  }

  private def fmt(f: Float): String = f"${f}%1.3f"
  private def truncate(s: String, n: Int): String = if (s.length <= n) s else s.take(n - 3) + "..."
}
