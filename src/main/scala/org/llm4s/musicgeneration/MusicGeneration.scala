package org.llm4s.musicgeneration

import java.nio.file.Path
import scala.concurrent.duration.Duration

/** Error types for music generation operations */
sealed trait MusicGenerationError extends Throwable {
  def message: String
  override def getMessage: String = message
}

object MusicGenerationError {
  case class InvalidParameters(message: String)   extends MusicGenerationError
  case class GenerationFailed(message: String)    extends MusicGenerationError
  case class ProviderError(message: String)       extends MusicGenerationError
  case class NetworkError(message: String)        extends MusicGenerationError
  case class RateLimitError(message: String)      extends MusicGenerationError
  case class AuthenticationError(message: String) extends MusicGenerationError
  case class TimeoutError(message: String)        extends MusicGenerationError
  case class UnknownError(message: String)        extends MusicGenerationError
}

/** Audio format for generated music */
sealed trait AudioFormat {
  def extension: String
  def mimeType: String
}

object AudioFormat {
  case object MP3 extends AudioFormat {
    val extension = "mp3"
    val mimeType  = "audio/mpeg"
  }
  case object WAV extends AudioFormat {
    val extension = "wav"
    val mimeType  = "audio/wav"
  }
  case object OGG extends AudioFormat {
    val extension = "ogg"
    val mimeType  = "audio/ogg"
  }
  case object FLAC extends AudioFormat {
    val extension = "flac"
    val mimeType  = "audio/flac"
  }
  case object M4A extends AudioFormat {
    val extension = "m4a"
    val mimeType  = "audio/mp4"
  }

  def fromString(s: String): Option[AudioFormat] = s.toLowerCase match {
    case "mp3"  => Some(MP3)
    case "wav"  => Some(WAV)
    case "ogg"  => Some(OGG)
    case "flac" => Some(FLAC)
    case "m4a"  => Some(M4A)
    case _      => None
  }
}

/** Musical genre for generation */
sealed trait MusicGenre {
  def name: String
  def description: String
}

object MusicGenre {
  case object Ambient extends MusicGenre {
    val name        = "ambient"
    val description = "Atmospheric and environmental soundscapes"
  }
  case object Orchestral extends MusicGenre {
    val name        = "orchestral"
    val description = "Classical orchestral arrangements"
  }
  case object Electronic extends MusicGenre {
    val name        = "electronic"
    val description = "Electronic and synthesized music"
  }
  case object Rock extends MusicGenre {
    val name        = "rock"
    val description = "Rock music with guitars and drums"
  }
  case object Jazz extends MusicGenre {
    val name        = "jazz"
    val description = "Jazz with improvisation and swing"
  }
  case object Classical extends MusicGenre {
    val name        = "classical"
    val description = "Traditional classical music"
  }
  case object Folk extends MusicGenre {
    val name        = "folk"
    val description = "Traditional and acoustic folk music"
  }
  case object WorldMusic extends MusicGenre {
    val name        = "world"
    val description = "World music from various cultures"
  }
  case object Cinematic extends MusicGenre {
    val name        = "cinematic"
    val description = "Epic cinematic and film music"
  }
  case object GameMusic extends MusicGenre {
    val name        = "game"
    val description = "Video game soundtrack music"
  }

  def fromString(s: String): Option[MusicGenre] = s.toLowerCase match {
    case "ambient"    => Some(Ambient)
    case "orchestral" => Some(Orchestral)
    case "electronic" => Some(Electronic)
    case "rock"       => Some(Rock)
    case "jazz"       => Some(Jazz)
    case "classical"  => Some(Classical)
    case "folk"       => Some(Folk)
    case "world"      => Some(WorldMusic)
    case "cinematic"  => Some(Cinematic)
    case "game"       => Some(GameMusic)
    case _            => None
  }

  val all: Seq[MusicGenre] = Seq(
    Ambient,
    Orchestral,
    Electronic,
    Rock,
    Jazz,
    Classical,
    Folk,
    WorldMusic,
    Cinematic,
    GameMusic
  )
}

/** Mood for music generation */
case class MusicMood(
  name: String,
  description: String,
  energy: Double = 0.5,   // 0.0 (calm) to 1.0 (energetic)
  valence: Double = 0.5,  // 0.0 (sad) to 1.0 (happy)
  intensity: Double = 0.5 // 0.0 (soft) to 1.0 (intense)
)

object MusicMood {
  // Predefined moods for common use cases
  val Happy      = MusicMood("happy", "Upbeat and cheerful", energy = 0.8, valence = 0.9, intensity = 0.6)
  val Sad        = MusicMood("sad", "Melancholic and emotional", energy = 0.3, valence = 0.2, intensity = 0.4)
  val Energetic  = MusicMood("energetic", "High energy and exciting", energy = 0.95, valence = 0.7, intensity = 0.9)
  val Calm       = MusicMood("calm", "Peaceful and relaxing", energy = 0.2, valence = 0.6, intensity = 0.2)
  val Mysterious = MusicMood("mysterious", "Enigmatic and intriguing", energy = 0.4, valence = 0.5, intensity = 0.5)
  val Epic       = MusicMood("epic", "Grand and heroic", energy = 0.8, valence = 0.7, intensity = 0.95)
  val Tense      = MusicMood("tense", "Suspenseful and anxious", energy = 0.6, valence = 0.3, intensity = 0.8)
  val Romantic   = MusicMood("romantic", "Loving and emotional", energy = 0.4, valence = 0.8, intensity = 0.5)
  val Dark       = MusicMood("dark", "Ominous and foreboding", energy = 0.5, valence = 0.1, intensity = 0.7)
  val Playful    = MusicMood("playful", "Light and fun", energy = 0.7, valence = 0.85, intensity = 0.4)

  // Game-specific moods
  val Combat      = MusicMood("combat", "Intense battle music", energy = 0.9, valence = 0.4, intensity = 0.95)
  val Exploration = MusicMood("exploration", "Adventurous discovery", energy = 0.6, valence = 0.7, intensity = 0.5)
  val Victory     = MusicMood("victory", "Triumphant celebration", energy = 0.85, valence = 0.95, intensity = 0.8)
  val Defeat      = MusicMood("defeat", "Loss and disappointment", energy = 0.2, valence = 0.1, intensity = 0.3)
  val Boss        = MusicMood("boss", "Epic boss battle", energy = 0.95, valence = 0.3, intensity = 1.0)
  val Stealth     = MusicMood("stealth", "Quiet and sneaky", energy = 0.3, valence = 0.4, intensity = 0.6)
  val Puzzle      = MusicMood("puzzle", "Thoughtful problem-solving", energy = 0.4, valence = 0.6, intensity = 0.3)
  val Town        = MusicMood("town", "Peaceful settlement", energy = 0.5, valence = 0.75, intensity = 0.3)
  val Dungeon     = MusicMood("dungeon", "Dark underground", energy = 0.4, valence = 0.2, intensity = 0.6)
  val Forest      = MusicMood("forest", "Natural and mystical", energy = 0.45, valence = 0.65, intensity = 0.4)
}

/** Instrument to include in generation */
sealed trait Instrument {
  def name: String
}

object Instrument {
  // String instruments
  case object Piano  extends Instrument { val name = "piano"  }
  case object Guitar extends Instrument { val name = "guitar" }
  case object Violin extends Instrument { val name = "violin" }
  case object Cello  extends Instrument { val name = "cello"  }
  case object Bass   extends Instrument { val name = "bass"   }
  case object Harp   extends Instrument { val name = "harp"   }

  // Wind instruments
  case object Flute     extends Instrument { val name = "flute"     }
  case object Saxophone extends Instrument { val name = "saxophone" }
  case object Trumpet   extends Instrument { val name = "trumpet"   }
  case object Clarinet  extends Instrument { val name = "clarinet"  }

  // Percussion
  case object Drums      extends Instrument { val name = "drums"      }
  case object Percussion extends Instrument { val name = "percussion" }

  // Electronic
  case object Synthesizer    extends Instrument { val name = "synthesizer"     }
  case object ElectricGuitar extends Instrument { val name = "electric guitar" }

  // Orchestral sections
  case object Strings   extends Instrument { val name = "strings"   }
  case object Brass     extends Instrument { val name = "brass"     }
  case object Woodwinds extends Instrument { val name = "woodwinds" }
  case object Orchestra extends Instrument { val name = "orchestra" }

  def fromString(s: String): Option[Instrument] = s.toLowerCase match {
    case "piano"                 => Some(Piano)
    case "guitar"                => Some(Guitar)
    case "violin"                => Some(Violin)
    case "cello"                 => Some(Cello)
    case "bass"                  => Some(Bass)
    case "harp"                  => Some(Harp)
    case "flute"                 => Some(Flute)
    case "saxophone" | "sax"     => Some(Saxophone)
    case "trumpet"               => Some(Trumpet)
    case "clarinet"              => Some(Clarinet)
    case "drums"                 => Some(Drums)
    case "percussion"            => Some(Percussion)
    case "synthesizer" | "synth" => Some(Synthesizer)
    case "electric guitar"       => Some(ElectricGuitar)
    case "strings"               => Some(Strings)
    case "brass"                 => Some(Brass)
    case "woodwinds"             => Some(Woodwinds)
    case "orchestra"             => Some(Orchestra)
    case _                       => None
  }
}

/** Options for music generation */
case class MusicGenerationOptions(
  duration: Duration = Duration(30, "seconds"),
  format: AudioFormat = AudioFormat.MP3,
  genre: Option[MusicGenre] = None,
  mood: Option[MusicMood] = None,
  instruments: Seq[Instrument] = Seq.empty,
  tempo: Option[Int] = None,            // BPM (beats per minute)
  key: Option[String] = None,           // Musical key (e.g., "C major", "A minor")
  timeSignature: Option[String] = None, // Time signature (e.g., "4/4", "3/4")
  temperature: Double = 1.0,            // Creativity/randomness (0.0 to 2.0)
  topK: Int = 250,                      // Top-K sampling parameter
  topP: Double = 0.95,                  // Top-P (nucleus) sampling parameter
  seed: Option[Long] = None             // Random seed for reproducibility
)

/** Result of music generation */
case class GeneratedMusic(
  audioData: Array[Byte],
  format: AudioFormat,
  duration: Duration,
  metadata: MusicMetadata
)

/** Metadata about generated music */
case class MusicMetadata(
  prompt: String,
  genre: Option[MusicGenre],
  mood: Option[MusicMood],
  instruments: Seq[Instrument],
  tempo: Option[Int],
  key: Option[String],
  generatedAt: java.time.Instant,
  modelVersion: Option[String] = None,
  additionalInfo: Map[String, String] = Map.empty
)

/** Service status information */
case class ServiceStatus(
  available: Boolean,
  message: Option[String] = None,
  modelVersion: Option[String] = None,
  limits: Option[ServiceLimits] = None
)

/** Service limits and quotas */
case class ServiceLimits(
  maxDuration: Duration,
  supportedFormats: Seq[AudioFormat],
  rateLimit: Option[Int] = None, // requests per minute
  quotaRemaining: Option[Int] = None
)

/** Main trait for music generation clients */
trait MusicGenerationClient {

  /** Generate music from a text prompt */
  def generateFromPrompt(
    prompt: String,
    options: MusicGenerationOptions = MusicGenerationOptions()
  ): Either[MusicGenerationError, GeneratedMusic]

  /** Generate music from a structured description */
  def generateFromDescription(
    genre: MusicGenre,
    mood: MusicMood,
    options: MusicGenerationOptions = MusicGenerationOptions()
  ): Either[MusicGenerationError, GeneratedMusic]

  /** Generate music and save to file */
  def generateToFile(
    prompt: String,
    outputPath: Path,
    options: MusicGenerationOptions = MusicGenerationOptions()
  ): Either[MusicGenerationError, GeneratedMusic]

  /** Generate variations of existing music */
  def generateVariation(
    referenceAudio: Array[Byte],
    variationPrompt: String,
    options: MusicGenerationOptions = MusicGenerationOptions()
  ): Either[MusicGenerationError, GeneratedMusic]

  /** Continue/extend existing music */
  def continueMusic(
    audioData: Array[Byte],
    continuationPrompt: String,
    options: MusicGenerationOptions = MusicGenerationOptions()
  ): Either[MusicGenerationError, GeneratedMusic]

  /** Check service health and availability */
  def health(): Either[MusicGenerationError, ServiceStatus]
}

/** Factory for creating music generation clients */
object MusicGeneration {

  /** Create a music generation client for the specified provider */
  def forProvider(
    provider: String,
    config: Map[String, String] = Map.empty
  ): Either[MusicGenerationError, MusicGenerationClient] =
    provider.toLowerCase match {
      case "suno" =>
        createSunoClient(config)
      case "replicate" =>
        createReplicateClient(config)
      case "stability" =>
        createStabilityClient(config)
      case _ =>
        Left(MusicGenerationError.InvalidParameters(s"Unknown provider: $provider"))
    }

  /** Create a Suno AI client */
  def suno(apiKey: String, baseUrl: Option[String] = None): Either[MusicGenerationError, MusicGenerationClient] =
    try {
      val config = provider.SunoConfig(apiKey, baseUrl)
      Right(new provider.SunoClient(config))
    } catch {
      case e: Exception =>
        Left(MusicGenerationError.InvalidParameters(s"Failed to create Suno client: ${e.getMessage}"))
    }

  /** Create a Replicate client */
  def replicate(
    apiKey: String,
    modelVersion: Option[String] = None
  ): Either[MusicGenerationError, MusicGenerationClient] =
    try {
      val config = provider.ReplicateConfig(apiKey, modelVersion)
      Right(new provider.ReplicateClient(config))
    } catch {
      case e: Exception =>
        Left(MusicGenerationError.InvalidParameters(s"Failed to create Replicate client: ${e.getMessage}"))
    }

  /** Create a Stability AI client */
  def stability(apiKey: String, baseUrl: Option[String] = None): Either[MusicGenerationError, MusicGenerationClient] =
    try {
      val config = provider.StabilityConfig(apiKey, baseUrl)
      Right(new provider.StabilityClient(config))
    } catch {
      case e: Exception =>
        Left(MusicGenerationError.InvalidParameters(s"Failed to create Stability client: ${e.getMessage}"))
    }

  private def createSunoClient(config: Map[String, String]): Either[MusicGenerationError, MusicGenerationClient] =
    config.get("apiKey") match {
      case Some(apiKey) =>
        suno(apiKey, config.get("baseUrl"))
      case None =>
        Left(MusicGenerationError.InvalidParameters("Suno requires 'apiKey' in config"))
    }

  private def createReplicateClient(config: Map[String, String]): Either[MusicGenerationError, MusicGenerationClient] =
    config.get("apiKey") match {
      case Some(apiKey) =>
        replicate(apiKey, config.get("modelVersion"))
      case None =>
        Left(MusicGenerationError.InvalidParameters("Replicate requires 'apiKey' in config"))
    }

  private def createStabilityClient(config: Map[String, String]): Either[MusicGenerationError, MusicGenerationClient] =
    config.get("apiKey") match {
      case Some(apiKey) =>
        stability(apiKey, config.get("baseUrl"))
      case None =>
        Left(MusicGenerationError.InvalidParameters("Stability requires 'apiKey' in config"))
    }
}
