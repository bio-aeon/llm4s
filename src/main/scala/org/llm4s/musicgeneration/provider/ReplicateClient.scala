package org.llm4s.musicgeneration.provider

import org.llm4s.musicgeneration._
import java.nio.file.{ Path, Files }
import scala.concurrent.duration.Duration
import requests.{ Response, post, get }
import ujson._
import java.util.Base64
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.net.URI

/** Configuration for Replicate client */
case class ReplicateConfig(
  apiKey: String,
  modelVersion: Option[String] = None
) {
  // MusicGen model by default
  val effectiveModelVersion: String = modelVersion.getOrElse(
    "671ac645ce5e552cc63a54a2bbff63fcf798043055d2dac5fc9e36a837eedcfb"
  )
}

/** Replicate music generation client */
class ReplicateClient(config: ReplicateConfig) extends MusicGenerationClient {
  private val logger  = LoggerFactory.getLogger(getClass)
  private val baseUrl = "https://api.replicate.com/v1"

  override def generateFromPrompt(
    prompt: String,
    options: MusicGenerationOptions = MusicGenerationOptions()
  ): Either[MusicGenerationError, GeneratedMusic] = {
    logger.info(s"Generating music from prompt: $prompt")

    try {
      // Create prediction
      val createResponse = post(
        s"$baseUrl/predictions",
        headers = Map(
          "Authorization" -> s"Bearer ${config.apiKey}",
          "Content-Type"  -> "application/json"
        ),
        data = buildPredictionRequest(prompt, options).toString
      )

      if (createResponse.statusCode != 201) {
        return handleErrorResponse(createResponse)
      }

      val predictionId = read(createResponse.text())("id").str
      logger.info(s"Created prediction: $predictionId")

      // Poll for completion
      pollPrediction(predictionId, prompt, options)
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
  ): Either[MusicGenerationError, GeneratedMusic] =
    // Note: MusicGen doesn't directly support variations, but we can use continuation
    continueMusic(referenceAudio, variationPrompt, options)

  override def continueMusic(
    audioData: Array[Byte],
    continuationPrompt: String,
    options: MusicGenerationOptions = MusicGenerationOptions()
  ): Either[MusicGenerationError, GeneratedMusic] = {
    logger.info(s"Continuing music with prompt: $continuationPrompt")

    try {
      // Upload audio data as base64 in the request
      val base64Audio = Base64.getEncoder.encodeToString(audioData)
      val dataUri     = s"data:audio/wav;base64,$base64Audio"

      val requestBody = Obj(
        "version" -> config.effectiveModelVersion,
        "input" -> Obj(
          "prompt"                   -> continuationPrompt,
          "melody"                   -> dataUri, // MusicGen can use melody as reference
          "duration"                 -> options.duration.toSeconds.toInt,
          "temperature"              -> options.temperature,
          "top_k"                    -> options.topK,
          "top_p"                    -> options.topP,
          "classifier_free_guidance" -> 3        // Default CFG for MusicGen
        )
      )

      options.seed.foreach(seed => requestBody("input")("seed") = seed.toInt)

      val createResponse = post(
        s"$baseUrl/predictions",
        headers = Map(
          "Authorization" -> s"Bearer ${config.apiKey}",
          "Content-Type"  -> "application/json"
        ),
        data = requestBody.toString
      )

      if (createResponse.statusCode != 201) {
        return handleErrorResponse(createResponse)
      }

      val predictionId = read(createResponse.text())("id").str
      logger.info(s"Created continuation prediction: $predictionId")

      pollPrediction(predictionId, continuationPrompt, options)
    } catch {
      case e: Exception =>
        logger.error(s"Error continuing music: ${e.getMessage}", e)
        Left(MusicGenerationError.UnknownError(s"Failed to continue music: ${e.getMessage}"))
    }
  }

  override def health(): Either[MusicGenerationError, ServiceStatus] =
    try {
      // Check if we can access the model
      val response = get(
        s"$baseUrl/models/meta/musicgen",
        headers = Map("Authorization" -> s"Bearer ${config.apiKey}")
      )

      if (response.statusCode == 200) {
        Right(
          ServiceStatus(
            available = true,
            message = Some("Replicate MusicGen is available"),
            modelVersion = Some(config.effectiveModelVersion),
            limits = Some(
              ServiceLimits(
                maxDuration = Duration(30, "seconds"),
                supportedFormats = Seq(AudioFormat.WAV, AudioFormat.MP3),
                rateLimit = None,
                quotaRemaining = None
              )
            )
          )
        )
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

  private def buildPredictionRequest(prompt: String, options: MusicGenerationOptions): Value = {
    val input = Obj(
      "prompt"                   -> enrichPrompt(prompt, options),
      "duration"                 -> options.duration.toSeconds.toInt,
      "temperature"              -> options.temperature,
      "top_k"                    -> options.topK,
      "top_p"                    -> options.topP,
      "classifier_free_guidance" -> 3 // Default CFG for MusicGen
    )

    options.seed.foreach(seed => input("seed") = seed.toInt)

    Obj(
      "version" -> config.effectiveModelVersion,
      "input"   -> input
    )
  }

  private def enrichPrompt(basePrompt: String, options: MusicGenerationOptions): String = {
    val parts = scala.collection.mutable.ListBuffer[String](basePrompt)

    // Add genre if specified
    options.genre.foreach(genre => parts += genre.description)

    // Add mood descriptors
    options.mood.foreach { mood =>
      if (mood.energy > 0.7) parts += "energetic"
      if (mood.energy < 0.3) parts += "calm"
      if (mood.valence > 0.7) parts += "uplifting"
      if (mood.valence < 0.3) parts += "melancholic"
      if (mood.intensity > 0.7) parts += "intense"
      if (mood.intensity < 0.3) parts += "soft"
    }

    // Add instruments
    if (options.instruments.nonEmpty) {
      parts += s"featuring ${options.instruments.map(_.name).mkString(", ")}"
    }

    // Add tempo
    options.tempo.foreach(bpm => parts += s"${bpm} BPM")

    // Add key
    options.key.foreach(key => parts += s"in ${key}")

    parts.mkString(", ")
  }

  private def buildPromptFromDescription(
    genre: MusicGenre,
    mood: MusicMood,
    options: MusicGenerationOptions
  ): String = {
    val parts = scala.collection.mutable.ListBuffer[String]()

    // Start with mood description
    parts += mood.description

    // Add genre
    parts += s"${genre.name} music"

    // Add energy/valence/intensity descriptors
    if (mood.energy > 0.7) parts += "high energy"
    else if (mood.energy < 0.3) parts += "low energy"

    if (mood.valence > 0.7) parts += "upbeat"
    else if (mood.valence < 0.3) parts += "somber"

    if (mood.intensity > 0.7) parts += "powerful"
    else if (mood.intensity < 0.3) parts += "gentle"

    // Add instruments if specified
    if (options.instruments.nonEmpty) {
      parts += s"with ${options.instruments.map(_.name).mkString(", ")}"
    }

    // Add tempo if specified
    options.tempo.foreach(bpm => parts += s"at ${bpm} BPM")

    // Add key if specified
    options.key.foreach(key => parts += s"in ${key}")

    parts.mkString(", ")
  }

  private def pollPrediction(
    predictionId: String,
    prompt: String,
    options: MusicGenerationOptions,
    maxAttempts: Int = 60
  ): Either[MusicGenerationError, GeneratedMusic] = {
    var attempts = 0

    while (attempts < maxAttempts) {
      val response = get(
        s"$baseUrl/predictions/$predictionId",
        headers = Map("Authorization" -> s"Bearer ${config.apiKey}")
      )

      if (response.statusCode == 200) {
        val json   = read(response.text())
        val status = json("status").str

        logger.debug(s"Prediction status: $status")

        status match {
          case "succeeded" =>
            return processSuccessfulPrediction(json, prompt, options)

          case "failed" =>
            val error = json.obj.get("error").map(_.str).getOrElse("Unknown error")
            logger.error(s"Music generation failed: $error")
            return Left(MusicGenerationError.GenerationFailed(s"Generation failed: $error"))

          case "processing" | "starting" =>
            Thread.sleep(2000) // Wait 2 seconds before polling again
            attempts += 1

          case _ =>
            return Left(MusicGenerationError.UnknownError(s"Unknown status: $status"))
        }
      } else {
        return handleErrorResponse(response)
      }
    }

    Left(MusicGenerationError.TimeoutError("Timeout waiting for music generation"))
  }

  private def processSuccessfulPrediction(
    json: Value,
    prompt: String,
    options: MusicGenerationOptions
  ): Either[MusicGenerationError, GeneratedMusic] =
    try {
      val audioUrl = json("output").str
      logger.info(s"Music generation succeeded: $audioUrl")

      // Download the audio
      downloadAudio(audioUrl) match {
        case Right(audioData) =>
          val metadata = MusicMetadata(
            prompt = prompt,
            genre = options.genre,
            mood = options.mood,
            instruments = options.instruments,
            tempo = options.tempo,
            key = options.key,
            generatedAt = java.time.Instant.now(),
            modelVersion = Some(config.effectiveModelVersion),
            additionalInfo = Map("replicate_url" -> audioUrl)
          )

          Right(
            GeneratedMusic(
              audioData = audioData,
              format = AudioFormat.WAV, // MusicGen outputs WAV by default
              duration = options.duration,
              metadata = metadata
            )
          )

        case Left(error) =>
          Left(error)
      }
    } catch {
      case e: Exception =>
        logger.error(s"Failed to process successful prediction: ${e.getMessage}", e)
        Left(MusicGenerationError.UnknownError(s"Failed to process result: ${e.getMessage}"))
    }

  private def downloadAudio(audioUrl: String): Either[MusicGenerationError, Array[Byte]] =
    try {
      logger.info(s"Downloading audio from: $audioUrl")

      val uri         = new URI(audioUrl)
      val url         = uri.toURL()
      val connection  = url.openConnection()
      val inputStream = connection.getInputStream

      val outputStream = new ByteArrayOutputStream()
      val buffer       = new Array[Byte](4096)
      var bytesRead    = 0

      while ({ bytesRead = inputStream.read(buffer); bytesRead != -1 })
        outputStream.write(buffer, 0, bytesRead)

      inputStream.close()
      val audioBytes = outputStream.toByteArray
      outputStream.close()

      logger.info(s"Downloaded audio, size: ${audioBytes.length} bytes")
      Right(audioBytes)

    } catch {
      case e: Exception =>
        logger.error("Error downloading audio", e)
        Left(MusicGenerationError.NetworkError(s"Failed to download audio: ${e.getMessage}"))
    }

  private def handleErrorResponse(response: Response): Left[MusicGenerationError, Nothing] = {
    val errorMessage =
      try {
        val json = read(response.text())
        json.obj.get("detail").map(_.str).getOrElse(response.text())
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
