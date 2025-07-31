package org.llm4s.llmconnect

import org.llm4s.llmconnect.config.EmbeddingConfig
import org.llm4s.llmconnect.model.{ EmbeddingRequest, EmbeddingResponse, EmbeddingError }
import org.llm4s.llmconnect.provider.{ EmbeddingProvider, OpenAIEmbeddingProvider, VoyageAIEmbeddingProvider }
<<<<<<< HEAD
import org.slf4j.LoggerFactory

class EmbeddingClient(provider: EmbeddingProvider) {
  private val logger = LoggerFactory.getLogger(getClass)

  def embed(request: EmbeddingRequest): Either[EmbeddingError, EmbeddingResponse] = {
    logger.info(s"[EmbeddingClient] Embedding input with model ${request.model.name}")
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
=======
import org.llm4s.llmconnect.utils.LoggerUtils

class EmbeddingClient(provider: EmbeddingProvider) {
  def embed(request: EmbeddingRequest): Either[EmbeddingError, EmbeddingResponse] = {
    LoggerUtils.info(s"[EmbeddingClient] Embedding input with model ${request.model.name}")
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
    provider.embed(request)
  }
}

object EmbeddingClient {
<<<<<<< HEAD
  private val logger = LoggerFactory.getLogger(getClass)

||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
  def fromConfig(): EmbeddingProvider =
    EmbeddingConfig.activeProvider match {
=======
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
  def fromConfig(): EmbeddingClient = {
    val providerName = EmbeddingConfig.activeProvider.toLowerCase

    val provider: EmbeddingProvider = providerName match {
      case "openai" => OpenAIEmbeddingProvider
      case "voyage" => VoyageAIEmbeddingProvider
      case unknown =>
        val msg = s"[EmbeddingClient] Unsupported embedding provider: $unknown"
<<<<<<< HEAD
        logger.error(msg)
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
      case other    => throw new RuntimeException(s"Unknown embedding provider: $other")
=======
        LoggerUtils.error(msg)
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
        throw new RuntimeException(msg)
    }

<<<<<<< HEAD
    logger.info(s"[EmbeddingClient] Initialized with provider: $providerName")
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
=======
    LoggerUtils.info(s"[EmbeddingClient] Initialized with provider: $providerName")
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
    new EmbeddingClient(provider)
  }
}
