package org.llm4s.llmconnect.config

object ModelDimensionRegistry {

  private val dimensions: Map[String, Map[String, Int]] = Map(
    "openai" -> Map(
      "text-embedding-3-small" -> 1536,
      "text-embedding-3-large" -> 3072
    ),
    "voyage" -> Map(
      "voyage-2"       -> 1024,
      "voyage-3-large" -> 1536
    )
    // Add more providers and models here
  )

  def getDimension(provider: String, model: String): Int =
    dimensions
      .getOrElse(provider.toLowerCase, Map.empty)
      .getOrElse(
        model,
        throw new IllegalArgumentException(
<<<<<<< HEAD
          s"\n[ModelDimensionRegistry] Unknown model '$model' for provider '$provider'"
||||||| parent of ad62d21 (Add dynamic chunking and logging to embedding pipeline)
  private val VoyageModels: Map[String, Int] = Map(
    "voyage-3-large"   -> 1024,
    "voyage-3.5"       -> 1024,
    "voyage-3.5-lite"  -> 1024,
    "voyage-code-3"    -> 1024,
    "voyage-finance-2" -> 1024,
    "voyage-law-2"     -> 1024,
    "voyage-code-2"    -> 1536,
    "voyage-context-3" -> 1024
  )

  def getDimensions(provider: String, model: String): Int =
    provider match {
      case "openai" =>
        OpenAIModels.getOrElse(
          model,
          throw new RuntimeException(s"Unknown model: [$model] for provider: [$provider]")
=======
          s"[ModelDimensionRegistry] Unknown model '$model' for provider '$provider'"
>>>>>>> ad62d21 (Add dynamic chunking and logging to embedding pipeline)
        )
      )
}
