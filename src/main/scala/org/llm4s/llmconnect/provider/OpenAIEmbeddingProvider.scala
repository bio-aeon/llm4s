package org.llm4s.llmconnect.provider

import sttp.client4._
import ujson.{ Arr, Obj, read }
import org.llm4s.llmconnect.config.{ EmbeddingConfig, EmbeddingProviderConfig }
import org.llm4s.llmconnect.model._
<<<<<<< HEAD
<<<<<<< HEAD
import org.slf4j.LoggerFactory
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
=======
import org.llm4s.llmconnect.utils.LoggerUtils
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
||||||| parent of 0013d53 (LoggerUtils to SLf4J logger)
import org.llm4s.llmconnect.utils.LoggerUtils
=======
import org.slf4j.LoggerFactory
>>>>>>> 0013d53 (LoggerUtils to SLf4J logger)

object OpenAIEmbeddingProvider extends EmbeddingProvider {

  private val backend = DefaultSyncBackend()
<<<<<<< HEAD
<<<<<<< HEAD
  private val logger  = LoggerFactory.getLogger(getClass)
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
=======
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
||||||| parent of 0013d53 (LoggerUtils to SLf4J logger)
=======
  private val logger  = LoggerFactory.getLogger(getClass)
>>>>>>> 0013d53 (LoggerUtils to SLf4J logger)

  override def embed(request: EmbeddingRequest): Either[EmbeddingError, EmbeddingResponse] = {
    val model = request.model.name
    val input = request.input

<<<<<<< HEAD
    val payload = Obj(
      "input" -> Arr.from(input),
      "model" -> model
    )

    val url = uri"${cfg.baseUrl}/v1/embeddings"

<<<<<<< HEAD
<<<<<<< HEAD
    logger.info(
      s"\n[OpenAIEmbeddingProvider] Sending embedding request to $url with model=$model and ${input.size} input(s)"
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
    val cfg     = EmbeddingConfig.openAI
    val backend = DefaultSyncBackend()
=======
    LoggerUtils.info(
      s"[OpenAIEmbeddingProvider] Sending embedding request to $url with model=$model and ${input.size} input(s)"
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
||||||| parent of 0013d53 (LoggerUtils to SLf4J logger)
    LoggerUtils.info(
      s"[OpenAIEmbeddingProvider] Sending embedding request to $url with model=$model and ${input.size} input(s)"
=======
    logger.info(
      s"\n[OpenAIEmbeddingProvider] Sending embedding request to $url with model=$model and ${input.size} input(s)"
>>>>>>> 0013d53 (LoggerUtils to SLf4J logger)
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
<<<<<<< HEAD
          val json = read(body)
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
        val json    = read(body)
        val vectors = json("data").arr.map(record => record("embedding").arr.map(_.num.toDouble).toVector).toSeq
        Right(EmbeddingResponse(vectors))
=======
          val json    = read(body)
          val vectors = json("data").arr.map(record => record("embedding").arr.map(_.num.toDouble).toVector).toSeq
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
||||||| parent of 0013d53 (LoggerUtils to SLf4J logger)
          val json    = read(body)
          val vectors = json("data").arr.map(record => record("embedding").arr.map(_.num.toDouble).toVector).toSeq
=======
          val json = read(body)

          val vectors = json("data").arr.map(record => record("embedding").arr.map(_.num).toVector).toSeq
>>>>>>> 0013d53 (LoggerUtils to SLf4J logger)

<<<<<<< HEAD
          val vectors = json("data").arr.map(record => record("embedding").arr.map(_.num).toVector).toSeq

||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
      case Left(error) =>
        Left(
          EmbeddingError(
            code = None,
            message = error,
            provider = "openai"
=======
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
          val metadata = Map(
            "provider" -> "openai",
            "model"    -> model,
            "count"    -> input.size.toString
||||||| parent of e65bcd6 (Embedx-v2: CLI report; non-text marked as stubs)
    val payload = Obj(
      "input" -> Arr.from(input),
      "model" -> model
    )

    val url = uri"${cfg.baseUrl}/v1/embeddings"

    logger.info(
      s"\n[OpenAIEmbeddingProvider] Sending embedding request to $url with model=$model and ${input.size} input(s)"
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
          val json = read(body)

          val vectors = json("data").arr.map(record => record("embedding").arr.map(_.num).toVector).toSeq

          val metadata = Map(
            "provider" -> "openai",
            "model"    -> model,
            "count"    -> input.size.toString
=======
    // Lazily read provider config; surface missing envs as a clean EmbeddingError
    val cfgEither: Either[EmbeddingError, EmbeddingProviderConfig] =
      try Right(EmbeddingConfig.openAI)
      catch {
        case e: Throwable =>
          Left(
            EmbeddingError(
              code = Some("400"),
              message = s"Missing OpenAI configuration: ${e.getMessage}",
              provider = "openai"
            )
>>>>>>> e65bcd6 (Embedx-v2: CLI report; non-text marked as stubs)
          )
      }

<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
          logger.info(s"\n[OpenAIEmbeddingProvider] Successfully received ${vectors.size} embeddings.")
          Right(EmbeddingResponse(embeddings = vectors, metadata = metadata))
        } catch {
          case ex: Exception =>
            logger.error(s"\n[OpenAIEmbeddingProvider] Failed to parse OpenAI response: ${ex.getMessage}")
            Left(EmbeddingError(None, s"Parsing error: ${ex.getMessage}", "openai"))
||||||| parent of e65bcd6 (Embedx-v2: CLI report; non-text marked as stubs)
          logger.info(s"\n[OpenAIEmbeddingProvider] Successfully received ${vectors.size} embeddings.")
          Right(EmbeddingResponse(embeddings = vectors, metadata = metadata))
        } catch {
          case ex: Exception =>
            logger.error(s"\n[OpenAIEmbeddingProvider] Failed to parse OpenAI response: ${ex.getMessage}")
            Left(EmbeddingError(None, s"Parsing error: ${ex.getMessage}", "openai"))
=======
    cfgEither.flatMap { cfg =>
      val payload = Obj(
        "input" -> Arr.from(input),
        "model" -> model
      )

      val url = uri"${cfg.baseUrl}/v1/embeddings"

      logger.debug(s"[OpenAIEmbeddingProvider] POST $url model=$model inputs=${input.size}")

      val respEither: Either[EmbeddingError, Response[Either[String, String]]] =
        try
          Right(
            basicRequest
              .post(url)
              .header("Authorization", s"Bearer ${cfg.apiKey}")
              .header("Content-Type", "application/json")
              .body(payload.render())
              .send(backend)
          )
        catch {
          case e: Throwable =>
            Left(
              EmbeddingError(
                code = Some("502"),
                message = s"HTTP request failed: ${e.getMessage}",
                provider = "openai"
              )
            )
>>>>>>> e65bcd6 (Embedx-v2: CLI report; non-text marked as stubs)
        }

<<<<<<< HEAD
      case Left(errorMsg) =>
        logger.error(s"\n[OpenAIEmbeddingProvider] HTTP error from OpenAI: $errorMsg")
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
        )
=======
          LoggerUtils.info(s"[OpenAIEmbeddingProvider] Successfully received ${vectors.size} embeddings.")
||||||| parent of 0013d53 (LoggerUtils to SLf4J logger)
          LoggerUtils.info(s"[OpenAIEmbeddingProvider] Successfully received ${vectors.size} embeddings.")
=======
          logger.info(s"\n[OpenAIEmbeddingProvider] Successfully received ${vectors.size} embeddings.")
>>>>>>> 0013d53 (LoggerUtils to SLf4J logger)
          Right(EmbeddingResponse(embeddings = vectors, metadata = metadata))
        } catch {
          case ex: Exception =>
            logger.error(s"\n[OpenAIEmbeddingProvider] Failed to parse OpenAI response: ${ex.getMessage}")
            Left(EmbeddingError(None, s"Parsing error: ${ex.getMessage}", "openai"))
        }

      case Left(errorMsg) =>
<<<<<<< HEAD
        LoggerUtils.error(s"[OpenAIEmbeddingProvider] HTTP error from OpenAI: $errorMsg")
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
||||||| parent of 0013d53 (LoggerUtils to SLf4J logger)
        LoggerUtils.error(s"[OpenAIEmbeddingProvider] HTTP error from OpenAI: $errorMsg")
=======
        logger.error(s"\n[OpenAIEmbeddingProvider] HTTP error from OpenAI: $errorMsg")
>>>>>>> 0013d53 (LoggerUtils to SLf4J logger)
        Left(EmbeddingError(None, errorMsg, "openai"))
||||||| parent of e65bcd6 (Embedx-v2: CLI report; non-text marked as stubs)
      case Left(errorMsg) =>
        logger.error(s"\n[OpenAIEmbeddingProvider] HTTP error from OpenAI: $errorMsg")
        Left(EmbeddingError(None, errorMsg, "openai"))
=======
      respEither.flatMap { response =>
        response.body match {
          case Right(body) =>
            try {
              val json    = read(body)
              val vectors = json("data").arr.map(r => r("embedding").arr.map(_.num).toVector).toSeq

              val metadata = Map(
                "provider" -> "openai",
                "model"    -> model,
                "count"    -> input.size.toString
              )

              logger.info(s"[OpenAIEmbeddingProvider] Received ${vectors.size} embeddings")
              Right(EmbeddingResponse(embeddings = vectors, metadata = metadata))
            } catch {
              case ex: Exception =>
                logger.error(s"[OpenAIEmbeddingProvider] Parse error: ${ex.getMessage}")
                Left(
                  EmbeddingError(
                    code = Some("502"),
                    message = s"Parsing error: ${ex.getMessage}",
                    provider = "openai"
                  )
                )
            }

          case Left(errorMsg) =>
            logger.error(s"[OpenAIEmbeddingProvider] HTTP error: $errorMsg")
            Left(
              EmbeddingError(
                code = Some("502"),
                message = errorMsg,
                provider = "openai"
              )
            )
        }
      }
>>>>>>> e65bcd6 (Embedx-v2: CLI report; non-text marked as stubs)
    }
  }
}
