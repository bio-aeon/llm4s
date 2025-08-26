package org.llm4s.llmconnect

import org.llm4s.llmconnect.config.EmbeddingConfig
<<<<<<< HEAD
import org.llm4s.llmconnect.model.{ EmbeddingRequest, EmbeddingResponse, EmbeddingError, EmbeddingVector }
import org.llm4s.llmconnect.provider.{ EmbeddingProvider, OpenAIEmbeddingProvider, VoyageAIEmbeddingProvider }
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
||||||| parent of 8bd3f68 (update: embedx-v2 on multimedia data)
=======
||||||| parent of e65bcd6 (Embedx-v2: CLI report; non-text marked as stubs)
import org.llm4s.llmconnect.model.{ EmbeddingRequest, EmbeddingResponse, EmbeddingError, EmbeddingVector }
import org.llm4s.llmconnect.provider.{ EmbeddingProvider, OpenAIEmbeddingProvider, VoyageAIEmbeddingProvider }
=======
>>>>>>> e65bcd6 (Embedx-v2: CLI report; non-text marked as stubs)
import org.llm4s.llmconnect.encoding.UniversalEncoder
<<<<<<< HEAD
>>>>>>> 8bd3f68 (update: embedx-v2 on multimedia data)
||||||| parent of e65bcd6 (Embedx-v2: CLI report; non-text marked as stubs)
=======
import org.llm4s.llmconnect.model.{ EmbeddingError, EmbeddingRequest, EmbeddingResponse, EmbeddingVector }
import org.llm4s.llmconnect.provider.{ EmbeddingProvider, OpenAIEmbeddingProvider, VoyageAIEmbeddingProvider }
>>>>>>> e65bcd6 (Embedx-v2: CLI report; non-text marked as stubs)
import org.slf4j.LoggerFactory

import java.nio.file.Path

class EmbeddingClient(provider: EmbeddingProvider) {
  private val logger = LoggerFactory.getLogger(getClass)

  /** Text embeddings via the configured HTTP provider. */
  def embed(request: EmbeddingRequest): Either[EmbeddingError, EmbeddingResponse] = {
<<<<<<< HEAD
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
||||||| parent of e65bcd6 (Embedx-v2: CLI report; non-text marked as stubs)
    logger.info(s"[EmbeddingClient] Embedding input with model ${request.model.name}")
=======
    logger.debug(s"[EmbeddingClient] Embedding with model=${request.model.name}, inputs=${request.input.size}")
>>>>>>> e65bcd6 (Embedx-v2: CLI report; non-text marked as stubs)
    provider.embed(request)
  }

  /** Unified API to encode any supported file into vectors. */
  def encodePath(path: Path): Either[EmbeddingError, Seq[EmbeddingVector]] =
    UniversalEncoder.encodeFromPath(path, this)
}

object EmbeddingClient {
<<<<<<< HEAD
<<<<<<< HEAD
  private val logger = LoggerFactory.getLogger(getClass)

<<<<<<< HEAD
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
  def fromConfig(): EmbeddingProvider =
    EmbeddingConfig.activeProvider match {
=======
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
||||||| parent of 0013d53 (LoggerUtils to SLf4J logger)
=======
  private val logger = LoggerFactory.getLogger(getClass)

>>>>>>> 0013d53 (LoggerUtils to SLf4J logger)
||||||| parent of e65bcd6 (Embedx-v2: CLI report; non-text marked as stubs)
=======
  /**
   * Legacy factory (back-compat): throws on unsupported provider.
   * Prefer [[fromConfigEither]] in new code to avoid exceptions on misconfig.
   */
>>>>>>> e65bcd6 (Embedx-v2: CLI report; non-text marked as stubs)
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

  /**
   * Safe factory: returns Either instead of throwing on misconfiguration.
   * Useful for samples/CLIs where we want a clean error path.
   */
  def fromConfigEither(): Either[EmbeddingError, EmbeddingClient] = {
    val providerName = EmbeddingConfig.activeProvider.toLowerCase
    val providerOpt: Option[EmbeddingProvider] = providerName match {
      case "openai" => Some(OpenAIEmbeddingProvider)
      case "voyage" => Some(VoyageAIEmbeddingProvider)
      case _        => None
    }

    providerOpt match {
      case Some(p) =>
        logger.info(s"[EmbeddingClient] Initialized with provider: $providerName")
        Right(new EmbeddingClient(p))
      case None =>
        Left(
          EmbeddingError(
            code = Some("400"),
            message = s"Unsupported embedding provider: $providerName",
            provider = "config"
          )
        )
    }
  }
}
