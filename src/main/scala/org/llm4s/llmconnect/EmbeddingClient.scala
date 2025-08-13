package org.llm4s.llmconnect

import org.llm4s.llmconnect.config.EmbeddingConfig
import org.llm4s.llmconnect.model.{ EmbeddingRequest, EmbeddingResponse, EmbeddingError, EmbeddingVector }
import org.llm4s.llmconnect.provider.{ EmbeddingProvider, OpenAIEmbeddingProvider, VoyageAIEmbeddingProvider }
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
||||||| parent of 8bd3f68 (update: embedx-v2 on multimedia data)
=======
import org.llm4s.llmconnect.encoding.UniversalEncoder
>>>>>>> 8bd3f68 (update: embedx-v2 on multimedia data)
import org.slf4j.LoggerFactory

import java.nio.file.Path

class EmbeddingClient(provider: EmbeddingProvider) {
  private val logger = LoggerFactory.getLogger(getClass)

  def embed(request: EmbeddingRequest): Either[EmbeddingError, EmbeddingResponse] = {
    logger.info(s"[EmbeddingClient] Embedding input with model ${request.model.name}")
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
=======
import org.llm4s.llmconnect.utils.LoggerUtils
||||||| parent of 0013d53 (LoggerUtils to SLf4J logger)
import org.llm4s.llmconnect.utils.LoggerUtils
=======
import org.slf4j.LoggerFactory
>>>>>>> 0013d53 (LoggerUtils to SLf4J logger)

class EmbeddingClient(provider: EmbeddingProvider) {
  private val logger = LoggerFactory.getLogger(getClass)

  def embed(request: EmbeddingRequest): Either[EmbeddingError, EmbeddingResponse] = {
<<<<<<< HEAD
    LoggerUtils.info(s"[EmbeddingClient] Embedding input with model ${request.model.name}")
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
||||||| parent of 0013d53 (LoggerUtils to SLf4J logger)
    LoggerUtils.info(s"[EmbeddingClient] Embedding input with model ${request.model.name}")
=======
    logger.info(s"[EmbeddingClient] Embedding input with model ${request.model.name}")
>>>>>>> 0013d53 (LoggerUtils to SLf4J logger)
    provider.embed(request)
  }

  /** New: unified API to encode any supported file into vectors. */
  def encodePath(path: Path): Either[EmbeddingError, Seq[EmbeddingVector]] =
    UniversalEncoder.encodeFromPath(path, this)
}

object EmbeddingClient {
<<<<<<< HEAD
<<<<<<< HEAD
  private val logger = LoggerFactory.getLogger(getClass)

||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
  def fromConfig(): EmbeddingProvider =
    EmbeddingConfig.activeProvider match {
=======
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
||||||| parent of 0013d53 (LoggerUtils to SLf4J logger)
=======
  private val logger = LoggerFactory.getLogger(getClass)

>>>>>>> 0013d53 (LoggerUtils to SLf4J logger)
  def fromConfig(): EmbeddingClient = {
    val providerName = EmbeddingConfig.activeProvider.toLowerCase

    val provider: EmbeddingProvider = providerName match {
      case "openai" => OpenAIEmbeddingProvider
      case "voyage" => VoyageAIEmbeddingProvider
      case unknown =>
        val msg = s"[EmbeddingClient] Unsupported embedding provider: $unknown"
<<<<<<< HEAD
<<<<<<< HEAD
        logger.error(msg)
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
      case other    => throw new RuntimeException(s"Unknown embedding provider: $other")
=======
        LoggerUtils.error(msg)
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
||||||| parent of 0013d53 (LoggerUtils to SLf4J logger)
        LoggerUtils.error(msg)
=======
        logger.error(msg)
>>>>>>> 0013d53 (LoggerUtils to SLf4J logger)
        throw new RuntimeException(msg)
    }

<<<<<<< HEAD
<<<<<<< HEAD
    logger.info(s"[EmbeddingClient] Initialized with provider: $providerName")
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
=======
    LoggerUtils.info(s"[EmbeddingClient] Initialized with provider: $providerName")
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
||||||| parent of 0013d53 (LoggerUtils to SLf4J logger)
    LoggerUtils.info(s"[EmbeddingClient] Initialized with provider: $providerName")
=======
    logger.info(s"[EmbeddingClient] Initialized with provider: $providerName")
>>>>>>> 0013d53 (LoggerUtils to SLf4J logger)
    new EmbeddingClient(provider)
  }
}
