package org.llm4s.llmconnect.config

import org.llm4s.config.EnvLoader

case class EmbeddingProviderConfig(
  baseUrl: String,
  model: String,
  apiKey: String
)

object EmbeddingConfig {

  // --- helpers ---
  /** Required env var; throws with a clear message if missing. */
  def loadEnv(name: String): String =
    EnvLoader.get(name).getOrElse(throw new RuntimeException(s"Missing env variable: $name"))

  def loadOptionalEnv(name: String, default: String): String =
    EnvLoader.get(name).getOrElse(default)

  /** Optional env var with default. */
  def loadOptionalEnv(name: String, default: String): String =
    sys.env.getOrElse(name, default)

  /** Optional getter that trims and drops empty strings. */
  def loadOpt(name: String): Option[String] =
    sys.env.get(name).map(_.trim).filter(_.nonEmpty)

  // ---------- Providers (TEXT embeddings over HTTP) ----------
  // NOTE: switched from 'val' to 'def' so these are only evaluated when actually used.
  def openAI: EmbeddingProviderConfig = EmbeddingProviderConfig(
    baseUrl = loadEnv("OPENAI_EMBEDDING_BASE_URL"),
    model = loadEnv("OPENAI_EMBEDDING_MODEL"),
    apiKey = loadEnv("OPENAI_API_KEY")
  )

  def voyage: EmbeddingProviderConfig = EmbeddingProviderConfig(
    baseUrl = loadEnv("VOYAGE_EMBEDDING_BASE_URL"),
    model = loadEnv("VOYAGE_EMBEDDING_MODEL"),
    apiKey = loadEnv("VOYAGE_API_KEY")
  )

  // Still required: you must choose the text provider
  val activeProvider: String = loadEnv("EMBEDDING_PROVIDER")

  // ---------- Paths & query (now OPTIONAL to avoid static init failures) ----------
  // These were previously required and caused crashes when using EMBEDDING_INPUT_PATHS only.
  // Default to empty; sample code or callers should read sys.env directly or handle the empty case.
  val inputPath: String  = loadOptionalEnv("EMBEDDING_INPUT_PATH", "")
  val inputPaths: String = loadOptionalEnv("EMBEDDING_INPUT_PATHS", "")
  val query: String      = loadOptionalEnv("EMBEDDING_QUERY", "")

  // ---------- Text chunking ----------
  val chunkSize: Int           = loadOptionalEnv("CHUNK_SIZE", "1000").toInt
  val chunkOverlap: Int        = loadOptionalEnv("CHUNK_OVERLAP", "100").toInt
  val chunkingEnabled: Boolean = loadOptionalEnv("CHUNKING_ENABLED", "true").toBoolean

  // ---------- Local models for non-text (used by UniversalEncoder) ----------
  val imageModel: String = loadOptionalEnv("IMAGE_MODEL", "openclip-vit-b32")
  val audioModel: String = loadOptionalEnv("AUDIO_MODEL", "wav2vec2-base")
  val videoModel: String = loadOptionalEnv("VIDEO_MODEL", "timesformer-base")

  // ---------- Multimedia defaults (future encoders / windowing) ----------
  val videoFps: Int             = loadOptionalEnv("VIDEO_FPS", "2").toInt
  val videoClipSeconds: Int     = loadOptionalEnv("VIDEO_CLIP_SECONDS", "8").toInt
  val audioSampleRate: Int      = loadOptionalEnv("AUDIO_SAMPLE_RATE", "16000").toInt
  val audioWindowSeconds: Int   = loadOptionalEnv("AUDIO_WINDOW_SECONDS", "5").toInt
  val chunkOverlapRatio: Double = loadOptionalEnv("CHUNK_OVERLAP_RATIO", "0.25").toDouble
}
