// ================================================================================================================
// M:\GSoC 2025\llm4s\src\main\scala\org\llm4s\llmconnect\utils\ModelSelector.scala
// ================================================================================================================
package org.llm4s.llmconnect.utils

import org.llm4s.llmconnect.config.{ EmbeddingConfig, EmbeddingModelConfig, ModelDimensionRegistry }
<<<<<<< HEAD
<<<<<<< HEAD
||||||| parent of 8bd3f68 (update: embedx-v2 on multimedia data)
=======
import org.llm4s.llmconnect.model.{ Modality, Text, Image, Audio, Video }
>>>>>>> 8bd3f68 (update: embedx-v2 on multimedia data)
import org.slf4j.LoggerFactory
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
import org.llm4s.llmconnect.config.{ EmbeddingModelConfig, ModelDimensionRegistry }
=======
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)

object ModelSelector {

<<<<<<< HEAD
  private val logger = LoggerFactory.getLogger(getClass)
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
  /**
   * Chooses a model configuration dynamically based on provider and text content.
   *
   * @param provider The embedding provider, e.g., "openai", "voyage"
   * @param text The raw input text to be embedded
   * @return An EmbeddingModelConfig containing the chosen model name and vector dimensions
   */
  def selectModel(provider: String, text: String): EmbeddingModelConfig = {
    val tokenCount = estimateTokenCount(text)
=======
  def selectModel(): EmbeddingModelConfig = {
    val provider = EmbeddingConfig.activeProvider.toLowerCase
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)

<<<<<<< HEAD
<<<<<<< HEAD
||||||| parent of 8bd3f68 (update: embedx-v2 on multimedia data)
=======
  /**
   * Legacy selector used by text HTTP providers (OpenAI/Voyage).
   * Chooses model based on EMBEDDING_PROVIDER and provider-specific envs.
   */
>>>>>>> 8bd3f68 (update: embedx-v2 on multimedia data)
  def selectModel(): EmbeddingModelConfig = {
    val provider = EmbeddingConfig.activeProvider.toLowerCase

    val modelName = provider match {
      case "openai" => EmbeddingConfig.openAI.model
      case "voyage" => EmbeddingConfig.voyage.model
      case other =>
        throw new RuntimeException(s"[ModelSelector] Unsupported provider: $other")
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
    provider.toLowerCase match {
      case "openai" =>
        val model = if (tokenCount <= 1000) "text-embedding-3-small" else "text-embedding-3-large"
        val dims  = ModelDimensionRegistry.getDimensions("openai", model)
        EmbeddingModelConfig(model, dims)

      case "voyage" =>
        val model = if (tokenCount <= 1000) "voyage-3.5" else "voyage-code-2"
        val dims  = ModelDimensionRegistry.getDimensions("voyage", model)
        EmbeddingModelConfig(model, dims)

      case other =>
        throw new RuntimeException(s"Unsupported provider: $other")
=======
    val modelName = provider match {
      case "openai" => EmbeddingConfig.openAI.model
      case "voyage" => EmbeddingConfig.voyage.model
      case other    => throw new RuntimeException(s"[ModelSelector] Unsupported provider: $other")
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
    }

<<<<<<< HEAD
<<<<<<< HEAD
    logger.info(s"\n[ModelSelector] Selecting model for provider: $provider, model: $modelName")
||||||| parent of 8bd3f68 (update: embedx-v2 on multimedia data)
    logger.info(s"\n[ModelSelector] Selecting model for provider: $provider, model: $modelName")
=======
    logger.info(s"[ModelSelector] Selecting model for provider: $provider, model: $modelName")
>>>>>>> 8bd3f68 (update: embedx-v2 on multimedia data)

    val dimensions = ModelDimensionRegistry.getDimension(provider, modelName)
<<<<<<< HEAD

    logger.info(s"\n[ModelSelector] Model dimensions: $dimensions")
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
  /**
   * Estimate token count using a naive approximation (split by whitespace).
   * In production, use tokenizer-aware estimation (e.g., tiktoken or sentencepiece).
   */
  def estimateTokenCount(text: String): Int =
    text.split("\\s+").length
=======
    LoggerUtils.info(s"[ModelSelector] Selecting model for provider: $provider, model: $modelName")

    val dimensions = ModelDimensionRegistry.getDimension(provider, modelName)

    LoggerUtils.info(s"[ModelSelector] Model dimensions: $dimensions")
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
||||||| parent of 8bd3f68 (update: embedx-v2 on multimedia data)

    logger.info(s"\n[ModelSelector] Model dimensions: $dimensions")
=======
    logger.info(s"[ModelSelector] Model dimensions: $dimensions")
>>>>>>> 8bd3f68 (update: embedx-v2 on multimedia data)

    EmbeddingModelConfig(name = modelName, dimensions = dimensions)
  }

  /**
   * New overload that selects by modality.
   * - Text uses the legacy provider-based selection.
   * - Image/Audio/Video use static "local" models defined in EmbeddingConfig.
   */
  def selectModel(modality: Modality): EmbeddingModelConfig = modality match {
    case Text =>
      selectModel() // defer to provider-based text selection
    case Image =>
      val name = EmbeddingConfig.imageModel
      val dim  = ModelDimensionRegistry.getDimension("local", name)
      logger.info(s"[ModelSelector] Image model: $name ($dim dims)")
      EmbeddingModelConfig(name, dim)
    case Audio =>
      val name = EmbeddingConfig.audioModel
      val dim  = ModelDimensionRegistry.getDimension("local", name)
      logger.info(s"[ModelSelector] Audio model: $name ($dim dims)")
      EmbeddingModelConfig(name, dim)
    case Video =>
      val name = EmbeddingConfig.videoModel
      val dim  = ModelDimensionRegistry.getDimension("local", name)
      logger.info(s"[ModelSelector] Video model: $name ($dim dims)")
      EmbeddingModelConfig(name, dim)
  }
}
