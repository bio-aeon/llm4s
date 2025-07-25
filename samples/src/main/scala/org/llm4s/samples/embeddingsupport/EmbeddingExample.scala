package org.llm4s.samples.embeddingsupport

<<<<<<< HEAD
||||||| parent of 29e3c07 (PR3: Extended Voyage model support and improved ModelSelector logic)
import org.llm4s.llmconnect.EmbeddingClient
import org.llm4s.llmconnect.config.{EmbeddingConfig, EmbeddingModelConfig}
=======
import org.llm4s.llmconnect.EmbeddingClient
>>>>>>> 29e3c07 (PR3: Extended Voyage model support and improved ModelSelector logic)
import org.llm4s.llmconnect.config.EmbeddingConfig
import org.llm4s.llmconnect.extractors.UniversalExtractor
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

object EmbeddingExample {

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

  // Step 4: Load embedding provider and get response
  val embeddingProvider = EmbeddingClient.fromConfig()

  embeddingProvider.embed(request) match {
    case Right(response) =>
      val docVec = response.vectors.head
      val queryVec = response.vectors.last
      val score = SimilarityUtils.cosineSimilarity(docVec, queryVec)
>>>>>>> 29e3c07 (PR3: Extended Voyage model support and improved ModelSelector logic)

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

<<<<<<< HEAD
      case Right(text) =>
        val inputs: Seq[String] = if (EmbeddingConfig.chunkingEnabled) {
          logger.info(s"\nChunking enabled. Using size=${EmbeddingConfig.chunkSize}, overlap=${EmbeddingConfig.chunkOverlap}")
          ChunkingUtils.chunkText(text, EmbeddingConfig.chunkSize, EmbeddingConfig.chunkOverlap)
        } else {
          logger.info("\nChunking disabled. Proceeding with full text.")
          Seq(text)
        }

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
  }
}
