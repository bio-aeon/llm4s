package org.llm4s.musicgeneration.provider

import org.llm4s.musicgeneration._
import java.nio.file.Path
import org.slf4j.LoggerFactory

/** Configuration for Stability AI client */
case class StabilityConfig(
  apiKey: String,
  baseUrl: Option[String] = None
) {
  val effectiveBaseUrl: String = baseUrl.getOrElse("https://api.stability.ai/v1")
}

/** Stability AI music generation client (stub implementation) */
class StabilityClient(val config: StabilityConfig) extends MusicGenerationClient {
  private val logger = LoggerFactory.getLogger(getClass)

  override def generateFromPrompt(
    prompt: String,
    options: MusicGenerationOptions = MusicGenerationOptions()
  ): Either[MusicGenerationError, GeneratedMusic] = {
    logger.warn("Stability AI music generation is not yet available")
    Left(MusicGenerationError.ProviderError("Stability AI music generation is not yet available"))
  }

  override def generateFromDescription(
    genre: MusicGenre,
    mood: MusicMood,
    options: MusicGenerationOptions = MusicGenerationOptions()
  ): Either[MusicGenerationError, GeneratedMusic] = {
    logger.warn("Stability AI music generation is not yet available")
    Left(MusicGenerationError.ProviderError("Stability AI music generation is not yet available"))
  }

  override def generateToFile(
    prompt: String,
    outputPath: Path,
    options: MusicGenerationOptions = MusicGenerationOptions()
  ): Either[MusicGenerationError, GeneratedMusic] = {
    logger.warn("Stability AI music generation is not yet available")
    Left(MusicGenerationError.ProviderError("Stability AI music generation is not yet available"))
  }

  override def generateVariation(
    referenceAudio: Array[Byte],
    variationPrompt: String,
    options: MusicGenerationOptions = MusicGenerationOptions()
  ): Either[MusicGenerationError, GeneratedMusic] = {
    logger.warn("Stability AI music generation is not yet available")
    Left(MusicGenerationError.ProviderError("Stability AI music generation is not yet available"))
  }

  override def continueMusic(
    audioData: Array[Byte],
    continuationPrompt: String,
    options: MusicGenerationOptions = MusicGenerationOptions()
  ): Either[MusicGenerationError, GeneratedMusic] = {
    logger.warn("Stability AI music generation is not yet available")
    Left(MusicGenerationError.ProviderError("Stability AI music generation is not yet available"))
  }

  override def health(): Either[MusicGenerationError, ServiceStatus] =
    Right(
      ServiceStatus(
        available = false,
        message = Some("Stability AI music generation is not yet available"),
        modelVersion = None,
        limits = None
      )
    )
}
