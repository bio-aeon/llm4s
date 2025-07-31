package org.llm4s.samples.embeddingsupport

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
import org.llm4s.llmconnect.EmbeddingClient
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
  def main(args: Array[String]): Unit = {
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

      case Right(text) =>
        val inputs: Seq[String] = if (EmbeddingConfig.chunkingEnabled) {
          logger.info(s"\nChunking enabled. Using size=${EmbeddingConfig.chunkSize}, overlap=${EmbeddingConfig.chunkOverlap}")
          ChunkingUtils.chunkText(text, EmbeddingConfig.chunkSize, EmbeddingConfig.chunkOverlap)
        } else {
          logger.info("\nChunking disabled. Proceeding with full text.")
          Seq(text)
        }

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
}
