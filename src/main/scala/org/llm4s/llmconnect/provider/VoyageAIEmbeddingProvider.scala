package org.llm4s.llmconnect.provider

import sttp.client4._
import ujson.{ Obj, Arr, read }
import org.llm4s.llmconnect.config.EmbeddingConfig
import org.llm4s.llmconnect.model._
<<<<<<< HEAD
import org.slf4j.LoggerFactory
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
=======
import org.llm4s.llmconnect.utils.LoggerUtils
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)

object VoyageAIEmbeddingProvider extends EmbeddingProvider {

  private val backend = DefaultSyncBackend()
<<<<<<< HEAD
  private val logger  = LoggerFactory.getLogger(getClass)
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
=======
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)

  override def embed(request: EmbeddingRequest): Either[EmbeddingError, EmbeddingResponse] = {
    val cfg   = EmbeddingConfig.voyage
    val model = request.model.name
    val input = request.input

    val payload = Obj(
      "input" -> Arr.from(input),
      "model" -> model
    )

    val url = uri"${cfg.baseUrl}/v1/embeddings"

<<<<<<< HEAD
    logger.info(
      s"\n[VoyageAIEmbeddingProvider] Sending embedding request to $url with model=$model and ${input.size} input(s)"
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
    val cfg     = EmbeddingConfig.voyage
    val backend = DefaultSyncBackend()
=======
    LoggerUtils.info(
      s"[VoyageAIEmbeddingProvider] Sending embedding request to $url with model=$model and ${input.size} input(s)"
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
    )

    val response = basicRequest
      .post(url)
      .header("Authorization", s"Bearer ${cfg.apiKey}")
      .header("Content-Type", "application/json")
      .body(payload.render())
      .send(backend)

    response.body match {
      case Right(body) =>
        try {
<<<<<<< HEAD
          val json = read(body)
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
        val json    = read(body)
        val vectors = json("data").arr.map(record => record("embedding").arr.map(_.num.toDouble).toVector).toSeq
=======
          val json    = read(body)
          val vectors = json("data").arr.map(record => record("embedding").arr.map(_.num.toDouble).toVector).toSeq
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)

<<<<<<< HEAD
          val vectors = json("data").arr.map(record => record("embedding").arr.map(_.num).toVector).toSeq

||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
        Right(EmbeddingResponse(vectors))

      case Left(error) =>
        Left(
          EmbeddingError(
            code = None,
            message = error,
            provider = "voyage"
=======
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
          val metadata = Map(
            "provider" -> "voyage",
            "model"    -> model,
            "count"    -> input.size.toString
          )

<<<<<<< HEAD
          logger.info(s"\n[VoyageAIEmbeddingProvider] Successfully received ${vectors.size} embeddings.")
          Right(EmbeddingResponse(embeddings = vectors, metadata = metadata))
        } catch {
          case ex: Exception =>
            logger.error(s"\n[VoyageAIEmbeddingProvider] Failed to parse Voyage AI response: ${ex.getMessage}")
            Left(EmbeddingError(None, s"Parsing error: ${ex.getMessage}", "voyage"))
        }

      case Left(errorMsg) =>
        logger.error(s"\n[VoyageAIEmbeddingProvider] HTTP error from Voyage AI: $errorMsg")
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
        )
=======
          LoggerUtils.info(s"[VoyageAIEmbeddingProvider] Successfully received ${vectors.size} embeddings.")
          Right(EmbeddingResponse(embeddings = vectors, metadata = metadata))
        } catch {
          case ex: Exception =>
            LoggerUtils.error(s"[VoyageAIEmbeddingProvider] Failed to parse Voyage AI response: ${ex.getMessage}")
            Left(EmbeddingError(None, s"Parsing error: ${ex.getMessage}", "voyage"))
        }

      case Left(errorMsg) =>
        LoggerUtils.error(s"[VoyageAIEmbeddingProvider] HTTP error from Voyage AI: $errorMsg")
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
        Left(EmbeddingError(None, errorMsg, "voyage"))
    }
  }
}
