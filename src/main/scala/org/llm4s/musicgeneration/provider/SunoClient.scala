package org.llm4s.musicgeneration.provider

import org.llm4s.musicgeneration._
import java.nio.file.{ Path, Files }
import scala.concurrent.duration.Duration
import requests.{ Response, post, get }
import ujson._
import java.util.Base64
import org.slf4j.LoggerFactory

/** Configuration for Suno AI client */
case class SunoConfig(
  apiKey: String,
  baseUrl: Option[String] = None
) {
  val effectiveBaseUrl: String = baseUrl.getOrElse("https://api.suno.ai/v1")
}

/** Suno AI music generation client */
class SunoClient(config: SunoConfig) extends MusicGenerationClient {
  private val logger = LoggerFactory.getLogger(getClass)

  override def generateFromPrompt(
    prompt: String,
    options: MusicGenerationOptions = MusicGenerationOptions()
  ): Either[MusicGenerationError, GeneratedMusic] = {
    logger.info(s"Generating music from prompt: $prompt")

    try {
      // Build the request body
      val requestBody = buildRequestBody(prompt, options)

      // Make the API request
      val response = post(
        s"${config.effectiveBaseUrl}/generate",
        headers = Map(
          "Authorization" -> s"Bearer ${config.apiKey}",
          "Content-Type"  -> "application/json"
        ),
        data = requestBody.toString
      )

      if (response.statusCode == 200) {
        parseGenerationResponse(response, prompt, options)
      } else {
        handleErrorResponse(response)
      }
    } catch {
      case e: Exception =>
        logger.error(s"Error generating music: ${e.getMessage}", e)
        Left(MusicGenerationError.UnknownError(s"Failed to generate music: ${e.getMessage}"))
    }
  }

  override def generateFromDescription(
    genre: MusicGenre,
    mood: MusicMood,
    options: MusicGenerationOptions = MusicGenerationOptions()
  ): Either[MusicGenerationError, GeneratedMusic] = {
    // Build a prompt from the genre and mood
    val prompt = buildPromptFromDescription(genre, mood, options)
    generateFromPrompt(prompt, options.copy(genre = Some(genre), mood = Some(mood)))
  }

  override def generateToFile(
    prompt: String,
    outputPath: Path,
    options: MusicGenerationOptions = MusicGenerationOptions()
  ): Either[MusicGenerationError, GeneratedMusic] =
    generateFromPrompt(prompt, options) match {
      case Right(music) =>
        try {
          Files.write(outputPath, music.audioData)
          logger.info(s"Saved generated music to: $outputPath")
          Right(music)
        } catch {
          case e: Exception =>
            logger.error(s"Failed to save music to file: ${e.getMessage}", e)
            Left(MusicGenerationError.UnknownError(s"Failed to save file: ${e.getMessage}"))
        }
      case Left(error) => Left(error)
    }

  override def generateVariation(
    referenceAudio: Array[Byte],
    variationPrompt: String,
    options: MusicGenerationOptions = MusicGenerationOptions()
  ): Either[MusicGenerationError, GeneratedMusic] = {
    logger.info(s"Generating variation with prompt: $variationPrompt")

    try {
      val base64Audio = Base64.getEncoder.encodeToString(referenceAudio)

      val requestBody = Obj(
        "reference_audio"  -> base64Audio,
        "variation_prompt" -> variationPrompt,
        "duration"         -> options.duration.toSeconds.toInt,
        "format"           -> options.format.extension,
        "temperature"      -> options.temperature,
        "top_k"            -> options.topK,
        "top_p"            -> options.topP
      )

      options.seed.foreach(seed => requestBody("seed") = seed)

      val response = post(
        s"${config.effectiveBaseUrl}/variation",
        headers = Map(
          "Authorization" -> s"Bearer ${config.apiKey}",
          "Content-Type"  -> "application/json"
        ),
        data = requestBody.toString
      )

      if (response.statusCode == 200) {
        parseGenerationResponse(response, variationPrompt, options)
      } else {
        handleErrorResponse(response)
      }
    } catch {
      case e: Exception =>
        logger.error(s"Error generating variation: ${e.getMessage}", e)
        Left(MusicGenerationError.UnknownError(s"Failed to generate variation: ${e.getMessage}"))
    }
  }

  override def continueMusic(
    audioData: Array[Byte],
    continuationPrompt: String,
    options: MusicGenerationOptions = MusicGenerationOptions()
  ): Either[MusicGenerationError, GeneratedMusic] = {
    logger.info(s"Continuing music with prompt: $continuationPrompt")

    try {
      val base64Audio = Base64.getEncoder.encodeToString(audioData)

      val requestBody = Obj(
        "input_audio"         -> base64Audio,
        "continuation_prompt" -> continuationPrompt,
        "duration"            -> options.duration.toSeconds.toInt,
        "format"              -> options.format.extension,
        "temperature"         -> options.temperature,
        "top_k"               -> options.topK,
        "top_p"               -> options.topP
      )

      options.seed.foreach(seed => requestBody("seed") = seed)

      val response = post(
        s"${config.effectiveBaseUrl}/continue",
        headers = Map(
          "Authorization" -> s"Bearer ${config.apiKey}",
          "Content-Type"  -> "application/json"
        ),
        data = requestBody.toString
      )

      if (response.statusCode == 200) {
        parseGenerationResponse(response, continuationPrompt, options)
      } else {
        handleErrorResponse(response)
      }
    } catch {
      case e: Exception =>
        logger.error(s"Error continuing music: ${e.getMessage}", e)
        Left(MusicGenerationError.UnknownError(s"Failed to continue music: ${e.getMessage}"))
    }
  }

  override def health(): Either[MusicGenerationError, ServiceStatus] =
    try {
      val response = get(
        s"${config.effectiveBaseUrl}/health",
        headers = Map("Authorization" -> s"Bearer ${config.apiKey}")
      )

      if (response.statusCode == 200) {
        val json         = read(response.text())
        val available    = json.obj.get("available").map(_.bool).getOrElse(true)
        val message      = json.obj.get("message").map(_.str)
        val modelVersion = json.obj.get("model_version").map(_.str)

        val limits = json.obj.get("limits").map { limitsJson =>
          ServiceLimits(
            maxDuration = Duration(limitsJson("max_duration").num.toInt, "seconds"),
            supportedFormats = limitsJson("supported_formats").arr
              .map(f => AudioFormat.fromString(f.str).getOrElse(AudioFormat.MP3))
              .toSeq,
            rateLimit = limitsJson.obj.get("rate_limit").map(_.num.toInt),
            quotaRemaining = limitsJson.obj.get("quota_remaining").map(_.num.toInt)
          )
        }

        Right(ServiceStatus(available, message, modelVersion, limits))
      } else if (response.statusCode == 401) {
        Left(MusicGenerationError.AuthenticationError("Invalid API key"))
      } else {
        Left(MusicGenerationError.ProviderError(s"Health check failed: ${response.statusCode}"))
      }
    } catch {
      case e: Exception =>
        logger.error(s"Health check failed: ${e.getMessage}", e)
        Left(MusicGenerationError.NetworkError(s"Health check failed: ${e.getMessage}"))
    }

  private def buildRequestBody(prompt: String, options: MusicGenerationOptions): Value = {
    val body = Obj(
      "prompt"      -> prompt,
      "duration"    -> options.duration.toSeconds.toInt,
      "format"      -> options.format.extension,
      "temperature" -> options.temperature,
      "top_k"       -> options.topK,
      "top_p"       -> options.topP
    )

    // Add optional parameters
    options.genre.foreach(genre => body("genre") = genre.name)
    options.mood.foreach { mood =>
      body("mood") = mood.name
      body("energy") = mood.energy
      body("valence") = mood.valence
      body("intensity") = mood.intensity
    }

    if (options.instruments.nonEmpty) {
      body("instruments") = options.instruments.map(_.name)
    }

    options.tempo.foreach(tempo => body("tempo") = tempo)
    options.key.foreach(key => body("key") = key)
    options.timeSignature.foreach(sig => body("time_signature") = sig)
    options.seed.foreach(seed => body("seed") = seed)

    body
  }

  private def buildPromptFromDescription(
    genre: MusicGenre,
    mood: MusicMood,
    options: MusicGenerationOptions
  ): String = {
    val parts = scala.collection.mutable.ListBuffer[String]()

    // Add genre
    parts += s"${genre.description} music"

    // Add mood description
    parts += s"with a ${mood.description} feeling"

    // Add instruments if specified
    if (options.instruments.nonEmpty) {
      parts += s"featuring ${options.instruments.map(_.name).mkString(", ")}"
    }

    // Add tempo if specified
    options.tempo.foreach(bpm => parts += s"at ${bpm} BPM")

    // Add key if specified
    options.key.foreach(key => parts += s"in ${key}")

    parts.mkString(", ")
  }

  private def parseGenerationResponse(
    response: Response,
    prompt: String,
    options: MusicGenerationOptions
  ): Either[MusicGenerationError, GeneratedMusic] =
    try {
      val json = read(response.text())

      // Check if we need to poll for completion
      if (json.obj.contains("task_id")) {
        pollForCompletion(json("task_id").str, prompt, options)
      } else {
        // Direct response with audio data
        parseCompletedResponse(json, prompt, options)
      }
    } catch {
      case e: Exception =>
        logger.error(s"Failed to parse response: ${e.getMessage}", e)
        Left(MusicGenerationError.UnknownError(s"Failed to parse response: ${e.getMessage}"))
    }

  private def pollForCompletion(
    taskId: String,
    prompt: String,
    options: MusicGenerationOptions,
    maxAttempts: Int = 60
  ): Either[MusicGenerationError, GeneratedMusic] = {
    logger.info(s"Polling for task completion: $taskId")

    var attempt                                                      = 0
    var result: Option[Either[MusicGenerationError, GeneratedMusic]] = None

    while (attempt < maxAttempts && result.isEmpty) {
      attempt += 1
      Thread.sleep(2000) // Wait 2 seconds between polls

      try {
        val response = get(
          s"${config.effectiveBaseUrl}/task/$taskId",
          headers = Map("Authorization" -> s"Bearer ${config.apiKey}")
        )

        if (response.statusCode == 200) {
          val json   = read(response.text())
          val status = json("status").str

          status match {
            case "completed" =>
              result = Some(parseCompletedResponse(json, prompt, options))
            case "failed" =>
              val error = json.obj.get("error").map(_.str).getOrElse("Unknown error")
              result = Some(Left(MusicGenerationError.GenerationFailed(s"Generation failed: $error")))
            case "processing" | "pending" =>
              logger.debug(s"Task $taskId still processing (attempt $attempt/$maxAttempts)")
            case _ =>
              result = Some(Left(MusicGenerationError.UnknownError(s"Unknown task status: $status")))
          }
        } else {
          result = Some(handleErrorResponse(response))
        }
      } catch {
        case e: Exception =>
          logger.error(s"Error polling task: ${e.getMessage}", e)
          if (attempt == maxAttempts) {
            result = Some(Left(MusicGenerationError.TimeoutError(s"Timeout waiting for generation: ${e.getMessage}")))
          }
      }
    }

    result.getOrElse(Left(MusicGenerationError.TimeoutError("Generation timed out after maximum attempts")))
  }

  private def parseCompletedResponse(
    json: Value,
    prompt: String,
    options: MusicGenerationOptions
  ): Either[MusicGenerationError, GeneratedMusic] =
    try {
      val audioDataStr = json("audio_data").str
      val audioData    = Base64.getDecoder.decode(audioDataStr)

      val format = json.obj.get("format").flatMap(f => AudioFormat.fromString(f.str)).getOrElse(options.format)
      val duration =
        Duration(json.obj.get("duration").map(_.num.toInt).getOrElse(options.duration.toSeconds.toInt), "seconds")

      val metadata = MusicMetadata(
        prompt = prompt,
        genre = options.genre,
        mood = options.mood,
        instruments = options.instruments,
        tempo = options.tempo,
        key = options.key,
        generatedAt = java.time.Instant.now(),
        modelVersion = json.obj.get("model_version").map(_.str),
        additionalInfo =
          json.obj.get("metadata").map(meta => meta.obj.map { case (k, v) => k -> v.str }.toMap).getOrElse(Map.empty)
      )

      Right(GeneratedMusic(audioData, format, duration, metadata))
    } catch {
      case e: Exception =>
        logger.error(s"Failed to parse completed response: ${e.getMessage}", e)
        Left(MusicGenerationError.UnknownError(s"Failed to parse audio data: ${e.getMessage}"))
    }

  private def handleErrorResponse(response: Response): Left[MusicGenerationError, Nothing] = {
    val errorMessage =
      try {
        val json = read(response.text())
        json.obj.get("error").map(_.str).getOrElse(response.text())
      } catch {
        case _: Exception => response.text()
      }

    response.statusCode match {
      case 401                 => Left(MusicGenerationError.AuthenticationError(errorMessage))
      case 429                 => Left(MusicGenerationError.RateLimitError(errorMessage))
      case 400                 => Left(MusicGenerationError.InvalidParameters(errorMessage))
      case code if code >= 500 => Left(MusicGenerationError.ProviderError(s"Server error: $errorMessage"))
      case _ => Left(MusicGenerationError.UnknownError(s"Request failed (${response.statusCode}): $errorMessage"))
    }
  }
}
