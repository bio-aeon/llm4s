package org.llm4s.llmconnect.config

import org.llm4s.config.{ ConfigKeys, ConfigReader }
import ConfigKeys._

case class EmbeddingProviderConfig(
  baseUrl: String,
  model: String,
  apiKey: String
)

object EmbeddingConfig {

  def loadEnv(name: String)(config: ConfigReader): String =
    config.get(name).getOrElse(throw new RuntimeException(s"Missing env variable: $name"))

  def loadOptionalEnv(name: String, default: String)(config: ConfigReader): String =
    config.get(name).getOrElse(default)

  /** Optional getter that trims and drops empty strings. */
  def loadOpt(name: String)(config: ConfigReader): Option[String] =
    config.get(name).map(_.trim).filter(_.nonEmpty)

  // ---------- Providers (TEXT embeddings over HTTP) ----------
  def openAI(config: ConfigReader): EmbeddingProviderConfig = EmbeddingProviderConfig(
    baseUrl = loadEnv(OPENAI_EMBEDDING_BASE_URL)(config),
    model = loadEnv(OPENAI_EMBEDDING_MODEL)(config),
    apiKey = loadEnv(OPENAI_API_KEY)(config)
  )

  def voyage(config: ConfigReader): EmbeddingProviderConfig = EmbeddingProviderConfig(
    baseUrl = loadEnv(VOYAGE_EMBEDDING_BASE_URL)(config),
    model = loadEnv(VOYAGE_EMBEDDING_MODEL)(config),
    apiKey = loadEnv(VOYAGE_API_KEY)(config)
  )

  def activeProvider(config: ConfigReader): String = loadEnv(EMBEDDING_PROVIDER)(config)

  // ---------- Paths & query (now OPTIONAL to avoid static init failures) ----------
  // These were previously required and caused crashes when using EMBEDDING_INPUT_PATHS only.
  // Default to empty; sample code or callers should read sys.env directly or handle the empty case.
  def inputPath(config: ConfigReader): String  = loadOptionalEnv(EMBEDDING_INPUT_PATH, "")(config)
  def inputPaths(config: ConfigReader): String = loadOptionalEnv("EMBEDDING_INPUT_PATHS", "")(config)
  def query(config: ConfigReader): String      = loadOptionalEnv(EMBEDDING_QUERY, "")(config)

  // ---------- Text chunking ----------
  def chunkSize(config: ConfigReader): Int           = loadOptionalEnv(CHUNK_SIZE, "1000")(config).toInt
  def chunkOverlap(config: ConfigReader): Int        = loadOptionalEnv(CHUNK_OVERLAP, "100")(config).toInt
  def chunkingEnabled(config: ConfigReader): Boolean = loadOptionalEnv(CHUNKING_ENABLED, "true")(config).toBoolean

  // ---------- Local models for non-text (used by UniversalEncoder) ----------
  def imageModel(config: ConfigReader): String = loadOptionalEnv("IMAGE_MODEL", "openclip-vit-b32")(config)
  def audioModel(config: ConfigReader): String = loadOptionalEnv("AUDIO_MODEL", "wav2vec2-base")(config)
  def videoModel(config: ConfigReader): String = loadOptionalEnv("VIDEO_MODEL", "timesformer-base")(config)

  // ---------- Multimedia defaults (future encoders / windowing) ----------
  def videoFps(config: ConfigReader): Int             = loadOptionalEnv("VIDEO_FPS", "2")(config).toInt
  def videoClipSeconds(config: ConfigReader): Int     = loadOptionalEnv("VIDEO_CLIP_SECONDS", "8")(config).toInt
  def audioSampleRate(config: ConfigReader): Int      = loadOptionalEnv("AUDIO_SAMPLE_RATE", "16000")(config).toInt
  def audioWindowSeconds(config: ConfigReader): Int   = loadOptionalEnv("AUDIO_WINDOW_SECONDS", "5")(config).toInt
  def chunkOverlapRatio(config: ConfigReader): Double = loadOptionalEnv("CHUNK_OVERLAP_RATIO", "0.25")(config).toDouble
}
