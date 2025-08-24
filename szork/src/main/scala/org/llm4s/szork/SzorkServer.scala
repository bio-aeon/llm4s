package org.llm4s.szork

import cask._
import scala.collection.mutable
import org.slf4j.LoggerFactory
import java.util.Base64
import scala.concurrent.{Future, ExecutionContext}
import java.util.concurrent.Executors

case class GameSession(
  id: String,
  gameId: String,  // Unique game ID for persistence
  engine: GameEngine,
  theme: Option[GameTheme] = None,
  artStyle: Option[ArtStyle] = None,
  pendingImages: mutable.Map[Int, Option[String]] = mutable.Map.empty,
  pendingMusic: mutable.Map[Int, Option[(String, String)]] = mutable.Map.empty,  // (base64, mood)
  autoSaveEnabled: Boolean = true,
  imageGenerationEnabled: Boolean = true
)

case class GameTheme(
  id: String,
  name: String,
  prompt: String
)

case class ArtStyle(
  id: String,
  name: String
)

case class CommandRequest(command: String)
case class CommandResponse(response: String, sessionId: String)

object SzorkServer extends cask.Main with cask.Routes {
  
  private val logger = LoggerFactory.getLogger("SzorkServer")
  
  // Load and validate configuration
  private val config = SzorkConfig.instance
  
  // Log the configuration
  config.logConfiguration(logger)
  
  // Validate configuration
  config.validate() match {
    case Left(errors) =>
      logger.error("Configuration validation failed:")
      errors.split("\n").foreach(error => logger.error(s"  - $error"))
      logger.warn("Server will continue but some features may not work")
    case Right(_) =>
      logger.info("Configuration validation successful")
  }
  
  // Verify LLM client can be created
  config.llmConfig match {
    case Some(_) =>
      try {
        import org.llm4s.llmconnect.LLMConnect
        LLMConnect.getClient()
        logger.info(s"LLM client initialized successfully")
      } catch {
        case e: Exception =>
          logger.error(s"Failed to initialize LLM client: ${e.getMessage}")
          logger.error("Server will continue but LLM features will not work")
      }
    case None =>
      logger.error("No LLM configuration found - text generation will not work")
  }
  
  @post("/api/game/validate-theme")
  def validateTheme(request: Request) = {
    val json = ujson.read(request.text())
    val themeDescription = json("theme").str
    
    logger.info(s"Validating custom theme: ${themeDescription.take(100)}...")
    
    // Use LLM to validate and enhance the theme
    import org.llm4s.llmconnect.LLM
    import org.llm4s.llmconnect.model.{SystemMessage, UserMessage, Conversation}
    
    val client = LLM.client()
    val validationPrompt = s"""Analyze this adventure game theme idea and determine if it would work well for a text-based adventure game:
    |
    |Theme: $themeDescription
    |
    |Please respond with a JSON object:
    |{
    |  "valid": true/false,
    |  "message": "Brief explanation if not valid",
    |  "enhancedTheme": "Enhanced version of the theme if valid, focusing on adventure elements"
    |}
    |
    |A good theme should have:
    |- Clear setting and atmosphere
    |- Potential for exploration and discovery
    |- Opportunities for puzzles, challenges, or mysteries
    |- Interesting locations to visit
    |""".stripMargin
    
    try {
      val conversation = Conversation(
        Seq(
          SystemMessage("You are an expert game designer specializing in text adventure games. Evaluate theme ideas and enhance them."),
          UserMessage(validationPrompt)
        )
      )
      
      client.complete(conversation) match {
        case Right(response) =>
          val responseText = response.message.content
          // Try to parse JSON from response
          val jsonStart = responseText.indexOf('{')
          val jsonEnd = responseText.lastIndexOf('}')
          if (jsonStart >= 0 && jsonEnd > jsonStart) {
            val jsonStr = responseText.substring(jsonStart, jsonEnd + 1)
            try {
              val parsed = ujson.read(jsonStr)
              ujson.Obj(
                "valid" -> parsed("valid"),
                "message" -> parsed.obj.getOrElse("message", ujson.Str("")).str,
                "enhancedTheme" -> parsed.obj.getOrElse("enhancedTheme", ujson.Str(themeDescription)).str
              )
            } catch {
              case _: Exception =>
                // If parsing fails, assume it's valid
                ujson.Obj(
                  "valid" -> true,
                  "message" -> "",
                  "enhancedTheme" -> themeDescription
                )
            }
          } else {
            // Default to valid if no JSON found
            ujson.Obj(
              "valid" -> true,
              "message" -> "",
              "enhancedTheme" -> themeDescription
            )
          }
        case Left(error) =>
          logger.error(s"Failed to validate theme: $error")
          ujson.Obj(
            "valid" -> true,
            "message" -> "",
            "enhancedTheme" -> themeDescription
          )
      }
    } catch {
      case e: Exception =>
        logger.error(s"Error validating theme", e)
        ujson.Obj(
          "valid" -> true,
          "message" -> "",
          "enhancedTheme" -> themeDescription
        )
    }
  }
  
  private val sessions = mutable.Map[String, GameSession]()
  private val imageExecutor = Executors.newFixedThreadPool(4)
  private implicit val imageEC: ExecutionContext = ExecutionContext.fromExecutor(imageExecutor)


  @get("/api/health")
  def health() = {
    logger.debug("Health check requested")
    ujson.Obj(
      "status" -> "healthy",
      "service" -> "szork-server"
    )
  }
  
  @get("/api/games")
  def listSavedGames() = {
    logger.info("Listing saved games")
    val games = GamePersistence.listGames()
    val gamesJson = games.map { metadata =>
      ujson.Obj(
        "gameId" -> metadata.gameId,
        "title" -> metadata.adventureTitle,
        "theme" -> metadata.theme,
        "artStyle" -> metadata.artStyle,
        "createdAt" -> metadata.createdAt,
        "lastPlayed" -> metadata.lastPlayed,
        "totalPlayTime" -> metadata.totalPlayTime
      )
    }
    ujson.Obj(
      "status" -> "success",
      "games" -> gamesJson
    )
  }

  @post("/api/game/generate-adventure")
  def generateAdventure(request: Request) = {
    logger.info("Generating adventure outline")
    val json = ujson.read(request.text())
    
    val theme = json.obj.get("theme").map { t =>
      GameTheme(
        id = t("id").str,
        name = t("name").str,
        prompt = t("prompt").str
      )
    }
    
    val artStyle = json.obj.get("artStyle").map { s =>
      ArtStyle(
        id = s("id").str,
        name = s("name").str
      )
    }
    
    val themePrompt = theme.map(_.prompt).getOrElse("classic fantasy adventure")
    val artStyleId = artStyle.map(_.id).getOrElse("fantasy")
    
    logger.info(s"Generating adventure for theme: ${theme.map(_.name).getOrElse("default")}")
    
    AdventureGenerator.generateAdventureOutline(themePrompt, artStyleId) match {
      case Right(outline) =>
        logger.info(s"Adventure outline generated: ${outline.title}")
        ujson.Obj(
          "status" -> "success",
          "outline" -> AdventureGenerator.outlineToJson(outline)
        )
      case Left(error) =>
        logger.error(s"Failed to generate adventure outline: $error")
        ujson.Obj(
          "status" -> "error",
          "message" -> error
        )
    }
  }
  
  @post("/api/game/start")
  def startGame(request: Request) = {
    logger.info("Starting new game session")
    val sessionId = java.util.UUID.randomUUID().toString
    val gameId = java.util.UUID.randomUUID().toString.take(8)  // Use first 8 chars of UUID for shorter game IDs
    
    // Parse theme and art style from request
    val json = ujson.read(request.text())
    val theme = json.obj.get("theme").map { t =>
      GameTheme(
        id = t("id").str,
        name = t("name").str,
        prompt = t("prompt").str
      )
    }
    val artStyle = json.obj.get("artStyle").map { s =>
      ArtStyle(
        id = s("id").str,
        name = s("name").str
      )
    }
    
    // Parse adventure outline if provided
    val adventureOutline = json.obj.get("outline").flatMap { outlineJson =>
      try {
        Some(AdventureOutline(
          title = outlineJson("title").str,
          mainQuest = outlineJson("mainQuest").str,
          subQuests = outlineJson("subQuests").arr.map(_.str).toList,
          keyLocations = outlineJson("keyLocations").arr.map { loc =>
            LocationOutline(
              id = loc("id").str,
              name = loc("name").str,
              description = loc("description").str,
              significance = loc("significance").str
            )
          }.toList,
          importantItems = outlineJson("importantItems").arr.map { item =>
            ItemOutline(
              name = item("name").str,
              description = item("description").str,
              purpose = item("purpose").str
            )
          }.toList,
          keyCharacters = outlineJson("keyCharacters").arr.map { char =>
            CharacterOutline(
              name = char("name").str,
              role = char("role").str,
              description = char("description").str
            )
          }.toList,
          adventureArc = outlineJson("adventureArc").str,
          specialMechanics = outlineJson.obj.get("specialMechanics").flatMap {
            case ujson.Null => None
            case s => Some(s.str)
          }
        ))
      } catch {
        case e: Exception =>
          logger.warn(s"Failed to parse adventure outline: ${e.getMessage}")
          None
      }
    }
    
    // Parse imageGenerationEnabled preference (default to true)
    val imageGenerationEnabled = json.obj.get("imageGenerationEnabled").map(_.bool).getOrElse(true)
    
    logger.info(s"Starting game with theme: ${theme.map(_.name).getOrElse("default")}, style: ${artStyle.map(_.name).getOrElse("default")}, outline: ${adventureOutline.map(_.title).getOrElse("none")}, imageGeneration: $imageGenerationEnabled")
    
    val engine = new GameEngine(sessionId, theme.map(_.prompt), artStyle.map(_.id), adventureOutline)
    
    // Set the adventure title in the engine
    adventureOutline.foreach(outline => engine.setAdventureTitle(outline.title))
    
    engine.initialize() match {
      case Right(initialMessage) =>
        val session = GameSession(
          id = sessionId,
          gameId = gameId,
          engine = engine,
          theme = theme,
          artStyle = artStyle,
          imageGenerationEnabled = imageGenerationEnabled
        )
        
        sessions(sessionId) = session
        logger.info(s"[$sessionId] Game session started")
        
        val messageIndex = engine.getMessageCount - 1
        val result = ujson.Obj(
          "sessionId" -> sessionId,
          "gameId" -> gameId,  // Include game ID for frontend routing
          "message" -> initialMessage,
          "status" -> "started",
          "messageIndex" -> messageIndex,
          "adventureTitle" -> ujson.Str(adventureOutline.map(_.title).getOrElse("Generative Adventuring"))
        )
        
        // Add scene data if available (without revealing location IDs)
        engine.getCurrentScene.foreach { scene =>
          result("scene") = ujson.Obj(
            "locationName" -> scene.locationName,
            "exits" -> scene.exits.map(exit => ujson.Obj(
              "direction" -> exit.direction,
              "description" -> ujson.Str(exit.description.getOrElse(""))
            )),
            "items" -> scene.items,
            "npcs" -> scene.npcs
          )
        }
        
        // Generate audio for the initial message
        try {
          val audioStartTime = System.currentTimeMillis()
          logger.info(s"[$sessionId] Generating audio for initial scene (${initialMessage.length} chars)")
          val tts = TextToSpeech()
          tts.synthesizeToBase64(initialMessage, TextToSpeech.VOICE_NOVA) match {
            case Right(audio) => 
              val audioTime = System.currentTimeMillis() - audioStartTime
              logger.info(s"[$sessionId] Initial audio generated in ${audioTime}ms, base64: ${audio.length}")
              result("audio") = audio
            case Left(error) => 
              logger.error(s"[$sessionId] Failed to generate initial audio: $error")
          }
        } catch {
          case e: Exception =>
            logger.error(s"[$sessionId] Error generating initial audio", e)
        }
        
        // Check if the initial scene should have an image (and if image generation is enabled)
        if (session.imageGenerationEnabled && engine.shouldGenerateSceneImage(initialMessage)) {
          result("hasImage") = true
          // Generate image asynchronously for initial scene
          Future {
            logger.info(s"[$sessionId] Starting async image generation for initial scene, message $messageIndex")
            val imageOpt = engine.generateSceneImage(initialMessage, Some(gameId))
            session.pendingImages(messageIndex) = imageOpt
            logger.info(s"[$sessionId] Async image generation completed for initial scene, message $messageIndex")
          }(imageEC)
        } else {
          result("hasImage") = false
        }
        
        // Check if the initial scene should have background music
        if (engine.shouldGenerateBackgroundMusic(initialMessage)) {
          result("hasMusic") = true
          // Generate music asynchronously for initial scene
          Future {
            logger.info(s"[$sessionId] Starting async music generation for initial scene, message $messageIndex")
            val musicOpt = engine.generateBackgroundMusic(initialMessage, Some(gameId))
            session.pendingMusic(messageIndex) = musicOpt
            logger.info(s"[$sessionId] Async music generation completed for initial scene, message $messageIndex")
          }(imageEC)
        } else {
          result("hasMusic") = false
        }
        
        result
        
      case Left(error) =>
        logger.error(s"[$sessionId] Failed to start game: $error")
        ujson.Obj(
          "status" -> "error",
          "error" -> error,
          "message" -> s"Failed to start game: $error"
        )
    }
  }

  @post("/api/game/stream")
  def streamCommand(request: Request): cask.model.Response.Raw = {
    val json = ujson.read(request.text())
    val sessionId = json("sessionId").str
    val command = json("command").str
    val imageGenerationEnabled = json.obj.get("imageGenerationEnabled").map(_.bool).getOrElse(true)
    
    logger.info(s"""
    |================================================================================
    |USER COMMAND RECEIVED [Session: $sessionId]
    |Command: "$command"
    |Image Generation: $imageGenerationEnabled
    |================================================================================
    """.stripMargin)
    
    sessions.get(sessionId) match {
      case Some(session) =>
        // Update session's imageGenerationEnabled if provided
        val updatedSession = imageGenerationEnabled match {
          case enabled if enabled != session.imageGenerationEnabled =>
            val newSession = session.copy(imageGenerationEnabled = enabled)
            sessions(sessionId) = newSession
            newSession
          case _ => session
        }
        
        // Create a queue for SSE events
        val eventQueue = new java.util.concurrent.LinkedBlockingQueue[String]()
        @volatile var streamingComplete = false
        
        // Start streaming in background
        import scala.concurrent.Future
        
        Future {
          try {
            updatedSession.engine.processCommandStreaming(
              command,
              onTextChunk = chunk => {
                // Send text chunk as SSE event
                val eventData = ujson.Obj("text" -> chunk).render()
                val event = s"event: chunk\ndata: $eventData\n\n"
                eventQueue.offer(event)
              }
            ) match {
              case Right(response) =>
                // Send completion event with scene data and metadata
                val completeData = ujson.Obj(
                  "complete" -> true,
                  "messageIndex" -> updatedSession.engine.getMessageCount,
                  "hasImage" -> (updatedSession.imageGenerationEnabled && 
                                 updatedSession.engine.shouldGenerateSceneImage(response.text)),
                  "hasMusic" -> updatedSession.engine.shouldGenerateBackgroundMusic(response.text)
                )
                
                // Add scene data if available
                response.scene.foreach { scene =>
                  completeData("scene") = ujson.Obj(
                    "locationName" -> scene.locationName,
                    "exits" -> scene.exits.map(exit => ujson.Obj(
                      "direction" -> exit.direction,
                      "description" -> ujson.Str(exit.description.getOrElse(""))
                    )),
                    "items" -> scene.items,
                    "npcs" -> scene.npcs
                  )
                }
                
                // Add audio if available
                response.audioBase64.foreach { audio =>
                  completeData("audio") = audio
                }
                
                val completeEvent = s"event: complete\ndata: ${completeData.render()}\n\n"
                eventQueue.offer(completeEvent)
                
                logger.info(s"Streaming response completed for session $sessionId")
                
                // Handle async image generation
                if (completeData("hasImage").bool) {
                  val messageIdx = updatedSession.engine.getMessageCount - 1
                  Future {
                    logger.info(s"Starting async image generation for streaming session $sessionId, message $messageIdx")
                    val imageOpt = updatedSession.engine.generateSceneImage(response.text, Some(updatedSession.gameId))
                    updatedSession.pendingImages(messageIdx) = imageOpt
                    logger.info(s"Async image generation completed for streaming session $sessionId, message $messageIdx")
                  }(imageEC)
                }
                
                // Handle async music generation
                if (completeData("hasMusic").bool) {
                  val messageIdx = updatedSession.engine.getMessageCount - 1
                  Future {
                    logger.info(s"Starting async music generation for streaming session $sessionId, message $messageIdx")
                    val musicOpt = updatedSession.engine.generateBackgroundMusic(response.text, Some(updatedSession.gameId))
                    updatedSession.pendingMusic(messageIdx) = musicOpt
                    logger.info(s"Async music generation completed for streaming session $sessionId, message $messageIdx")
                  }(imageEC)
                }
                
                // Auto-save if enabled
                if (updatedSession.autoSaveEnabled) {
                  val gameState = updatedSession.engine.getGameState(updatedSession.gameId, updatedSession.theme, updatedSession.artStyle)
                  GamePersistence.saveGame(gameState) match {
                    case Right(_) =>
                      logger.debug(s"Auto-saved game ${updatedSession.gameId} after streaming")
                    case Left(error) =>
                      logger.warn(s"Auto-save failed for game ${updatedSession.gameId}: $error")
                  }
                }
                
              case Left(error) =>
                logger.error(s"Streaming command failed: $error")
                val errorEvent = s"event: error\ndata: ${ujson.Obj("error" -> error.toString).render()}\n\n"
                eventQueue.offer(errorEvent)
            }
          } catch {
            case e: Exception =>
              logger.error(s"Exception during streaming", e)
              val errorEvent = s"event: error\ndata: ${ujson.Obj("error" -> e.getMessage).render()}\n\n"
              eventQueue.offer(errorEvent)
          } finally {
            streamingComplete = true
          }
        }(imageEC)
        
        // Create an iterator that reads from the queue
        val streamIterator = new Iterator[String] {
          def hasNext: Boolean = !streamingComplete || !eventQueue.isEmpty
          def next(): String = {
            var event = eventQueue.poll()
            while (event == null && !streamingComplete) {
              Thread.sleep(10) // Small delay to avoid busy waiting
              event = eventQueue.poll()
            }
            Option(event).getOrElse("")
          }
        }
        
        // Return SSE response using a streaming approach
        cask.model.Response(
          new java.io.InputStream {
            private val buffer = new java.io.ByteArrayOutputStream()
            private var currentBytes: Array[Byte] = Array.empty
            private var position = 0
            
            override def read(): Int = {
              if (position >= currentBytes.length) {
                // Need to get more data
                if (streamIterator.hasNext) {
                  val event = streamIterator.next()
                  if (event.nonEmpty) {
                    currentBytes = event.getBytes("UTF-8")
                    position = 0
                  } else {
                    return read() // Skip empty events
                  }
                } else {
                  return -1 // End of stream
                }
              }
              
              val byte = currentBytes(position) & 0xFF
              position += 1
              byte
            }
          },
          statusCode = 200,
          headers = Seq(
            "Content-Type" -> "text/event-stream",
            "Cache-Control" -> "no-cache",
            "Connection" -> "keep-alive",
            "X-Accel-Buffering" -> "no" // Disable nginx buffering
          )
        )
        
      case None =>
        logger.warn(s"Session not found for streaming: $sessionId")
        val errorMessage = s"event: error\ndata: ${ujson.Obj("error" -> "Session not found").render()}\n\n"
        cask.model.Response(
          errorMessage,
          statusCode = 404,
          headers = Seq("Content-Type" -> "text/event-stream")
        )
    }
  }
  
  @post("/api/game/command")
  def processCommand(request: Request) = {
    val json = ujson.read(request.text())
    val sessionId = json("sessionId").str
    val command = json("command").str
    
    // Parse imageGenerationEnabled preference if provided
    val imageGenerationEnabled = json.obj.get("imageGenerationEnabled").map(_.bool)
    
    logger.info(s"Processing command for session $sessionId: $command")
    
    sessions.get(sessionId) match {
      case Some(session) =>
        // Update session's imageGenerationEnabled if provided
        val updatedSession = imageGenerationEnabled match {
          case Some(enabled) => 
            val newSession = session.copy(imageGenerationEnabled = enabled)
            sessions(sessionId) = newSession
            newSession
          case None => session
        }
        logger.debug(s"Running game engine for session $sessionId")
        val startTime = System.currentTimeMillis()
        
        updatedSession.engine.processCommand(command) match {
          case Right(gameResponse) =>
            val processingTime = System.currentTimeMillis() - startTime
            logger.info(s"Command processed successfully for session $sessionId in ${processingTime}ms")
            
            val messageIndex = updatedSession.engine.getMessageCount - 1
            val result = ujson.Obj(
              "sessionId" -> sessionId,
              "response" -> gameResponse.text,
              "status" -> "success",
              "messageIndex" -> messageIndex
            )
            
            // Add scene data if available (without revealing location IDs)
            gameResponse.scene.foreach { scene =>
              result("scene") = ujson.Obj(
                "locationName" -> scene.locationName,
                "exits" -> scene.exits.map(exit => ujson.Obj(
                  "direction" -> exit.direction,
                  "description" -> ujson.Str(exit.description.getOrElse(""))
                )),
                "items" -> scene.items,
                "npcs" -> scene.npcs
              )
            }
            
            // Add audio if generated
            gameResponse.audioBase64.foreach { audio =>
              result("audio") = audio
            }
            
            // Check if this is a scene that needs an image (and if image generation is enabled)
            if (updatedSession.imageGenerationEnabled && updatedSession.engine.shouldGenerateSceneImage(gameResponse.text)) {
              result("hasImage") = true
              // Generate image asynchronously
              Future {
                logger.info(s"Starting async image generation for session $sessionId, message $messageIndex")
                val imageOpt = updatedSession.engine.generateSceneImage(gameResponse.text, Some(updatedSession.gameId))
                updatedSession.pendingImages(messageIndex) = imageOpt
                logger.info(s"Async image generation completed for session $sessionId, message $messageIndex")
              }(imageEC)
            } else {
              result("hasImage") = false
            }
            
            // Check if this is a scene that needs background music
            if (updatedSession.engine.shouldGenerateBackgroundMusic(gameResponse.text)) {
              result("hasMusic") = true
              // Generate music asynchronously
              Future {
                logger.info(s"Starting async music generation for session $sessionId, message $messageIndex")
                val musicOpt = updatedSession.engine.generateBackgroundMusic(gameResponse.text, Some(updatedSession.gameId))
                updatedSession.pendingMusic(messageIndex) = musicOpt
                logger.info(s"Async music generation completed for session $sessionId, message $messageIndex")
              }(imageEC)
            } else {
              result("hasMusic") = false
            }
            
            // Auto-save if enabled
            if (updatedSession.autoSaveEnabled) {
              val gameState = updatedSession.engine.getGameState(updatedSession.gameId, updatedSession.theme, updatedSession.artStyle)
              GamePersistence.saveGame(gameState) match {
                case Right(_) =>
                  logger.debug(s"Auto-saved game ${updatedSession.gameId}")
                case Left(error) =>
                  logger.warn(s"Auto-save failed for game ${updatedSession.gameId}: $error")
              }
            }
            
            result
            
          case Left(error) =>
            logger.error(s"Error processing command for session $sessionId: $error")
            ujson.Obj(
              "sessionId" -> sessionId,
              "error" -> error.toString,
              "status" -> "error"
            )
        }
        
      case None =>
        logger.warn(s"Session not found: $sessionId")
        ujson.Obj(
          "error" -> "Session not found",
          "status" -> "error"
        )
    }
  }

  @post("/api/game/audio")
  def processAudioCommand(request: Request) = {
    logger.info("Processing audio command")
    
    try {
      // For simplicity, we'll use JSON with base64 encoded audio
      val json = ujson.read(request.text())
      val sessionId = json("sessionId").str
      val audioBase64 = json("audio").str
      
      // Parse imageGenerationEnabled preference if provided
      val imageGenerationEnabled = json.obj.get("imageGenerationEnabled").map(_.bool)
      
      logger.info(s"Processing audio for session: $sessionId")
      
      // Decode base64 audio
      val audioBytes = Base64.getDecoder.decode(audioBase64)
      
      // Transcribe audio to text
      val speechToText = SpeechToText()
      speechToText.transcribeBytes(audioBytes) match {
        case Right(transcribedText) =>
          logger.info(s"========================================")
          logger.info(s"🎤 VOICE TRANSCRIPTION RESULT: \"$transcribedText\"")
          logger.info(s"========================================")
          
          // Process the transcribed text as a regular command
          sessions.get(sessionId) match {
            case Some(session) =>
              // Update session's imageGenerationEnabled if provided
              val updatedSession = imageGenerationEnabled match {
                case Some(enabled) => 
                  val newSession = session.copy(imageGenerationEnabled = enabled)
                  sessions(sessionId) = newSession
                  newSession
                case None => session
              }
              val startTime = System.currentTimeMillis()
              
              updatedSession.engine.processCommand(transcribedText) match {
                case Right(gameResponse) =>
                  val processingTime = System.currentTimeMillis() - startTime
                  logger.info(s"Audio command processed successfully for session $sessionId in ${processingTime}ms")
                  
                  val messageIndex = updatedSession.engine.getMessageCount - 1
                  val result = ujson.Obj(
                    "sessionId" -> sessionId,
                    "transcription" -> transcribedText,
                    "response" -> gameResponse.text,
                    "status" -> "success",
                    "messageIndex" -> messageIndex
                  )
                  
                  // Add scene data if available (without revealing location IDs)
                  gameResponse.scene.foreach { scene =>
                    result("scene") = ujson.Obj(
                      "locationName" -> scene.locationName,
                      "exits" -> scene.exits.map(exit => ujson.Obj(
                        "direction" -> exit.direction,
                        "description" -> ujson.Str(exit.description.getOrElse(""))
                      )),
                      "items" -> scene.items,
                      "npcs" -> scene.npcs
                    )
                  }
                  
                  // Add audio if generated
                  gameResponse.audioBase64.foreach { audio =>
                    result("audio") = audio
                  }
                  
                  // Check if this is a scene that needs an image (and if image generation is enabled)
                  if (updatedSession.imageGenerationEnabled && updatedSession.engine.shouldGenerateSceneImage(gameResponse.text)) {
                    result("hasImage") = true
                    // Generate image asynchronously
                    Future {
                      logger.info(s"Starting async image generation for session $sessionId, message $messageIndex (audio)")
                      val imageOpt = updatedSession.engine.generateSceneImage(gameResponse.text, Some(updatedSession.gameId))
                      updatedSession.pendingImages(messageIndex) = imageOpt
                      logger.info(s"Async image generation completed for session $sessionId, message $messageIndex (audio)")
                    }(imageEC)
                  } else {
                    result("hasImage") = false
                  }
                  
                  // Check if this is a scene that needs background music
                  if (updatedSession.engine.shouldGenerateBackgroundMusic(gameResponse.text)) {
                    result("hasMusic") = true
                    // Generate music asynchronously
                    Future {
                      logger.info(s"Starting async music generation for session $sessionId, message $messageIndex (audio)")
                      val musicOpt = updatedSession.engine.generateBackgroundMusic(gameResponse.text, Some(updatedSession.gameId))
                      updatedSession.pendingMusic(messageIndex) = musicOpt
                      logger.info(s"Async music generation completed for session $sessionId, message $messageIndex (audio)")
                    }(imageEC)
                  } else {
                    result("hasMusic") = false
                  }
                  
                  // Auto-save if enabled
                  if (updatedSession.autoSaveEnabled) {
                    val gameState = updatedSession.engine.getGameState(updatedSession.gameId, updatedSession.theme, updatedSession.artStyle)
                    GamePersistence.saveGame(gameState) match {
                      case Right(_) =>
                        logger.debug(s"Auto-saved game ${updatedSession.gameId} after audio command")
                      case Left(error) =>
                        logger.warn(s"Auto-save failed for game ${updatedSession.gameId}: $error")
                    }
                  }
                  
                  result
                  
                case Left(error) =>
                  logger.error(s"Error processing transcribed command for session $sessionId: $error")
                  ujson.Obj(
                    "sessionId" -> sessionId,
                    "error" -> error.toString,
                    "status" -> "error"
                  )
              }
              
            case None =>
              logger.warn(s"Session not found: $sessionId")
              ujson.Obj(
                "error" -> "Session not found",
                "status" -> "error"
              )
          }
          
        case Left(error) =>
          logger.error(s"Transcription failed: $error")
          ujson.Obj(
            "error" -> s"Speech recognition failed: $error",
            "status" -> "error"
          )
      }
    } catch {
      case e: Exception =>
        logger.error("Error processing audio", e)
        ujson.Obj(
          "error" -> s"Error processing audio: ${e.getMessage}",
          "status" -> "error"
        )
    }
  }

  @get("/api/game/image/:sessionId/:messageIndex")
  def getImage(sessionId: String, messageIndex: String) = {
    logger.debug(s"Checking for image: session=$sessionId, message=$messageIndex")
    
    try {
      val index = messageIndex.toInt
      sessions.get(sessionId) match {
        case Some(session) =>
          session.pendingImages.get(index) match {
            case Some(Some(imageBase64)) =>
              logger.info(s"Image found for session $sessionId, message $index")
              ujson.Obj(
                "status" -> "ready",
                "image" -> imageBase64
              )
            case Some(None) =>
              logger.info(s"Image generation failed for session $sessionId, message $index")
              ujson.Obj(
                "status" -> "failed"
              )
            case None =>
              logger.debug(s"Image still generating for session $sessionId, message $index")
              ujson.Obj(
                "status" -> "pending"
              )
          }
        case None =>
          logger.warn(s"Session not found: $sessionId")
          ujson.Obj(
            "status" -> "error",
            "error" -> "Session not found"
          )
      }
    } catch {
      case _: NumberFormatException =>
        ujson.Obj(
          "status" -> "error",
          "error" -> "Invalid message index"
        )
    }
  }
  
  @get("/api/game/music/:sessionId/:messageIndex")
  def getMusic(sessionId: String, messageIndex: String) = {
    logger.debug(s"Checking for music: session=$sessionId, message=$messageIndex")
    
    try {
      val index = messageIndex.toInt
      sessions.get(sessionId) match {
        case Some(session) =>
          session.pendingMusic.get(index) match {
            case Some(Some((musicBase64, mood))) =>
              logger.info(s"Music found for session $sessionId, message $index, mood: $mood")
              ujson.Obj(
                "status" -> "ready",
                "music" -> musicBase64,
                "mood" -> mood
              )
            case Some(None) =>
              logger.info(s"Music generation failed for session $sessionId, message $index")
              ujson.Obj(
                "status" -> "failed"
              )
            case None =>
              logger.debug(s"Music still generating for session $sessionId, message $index")
              ujson.Obj(
                "status" -> "pending"
              )
          }
        case None =>
          logger.warn(s"Session not found: $sessionId")
          ujson.Obj(
            "status" -> "error",
            "error" -> "Session not found"
          )
      }
    } catch {
      case _: NumberFormatException =>
        ujson.Obj(
          "status" -> "error",
          "error" -> "Invalid message index"
        )
    }
  }
  
  @get("/api/game/session/:sessionId")
  def getSession(sessionId: String) = {
    logger.debug(s"Getting session info for: $sessionId")
    sessions.get(sessionId) match {
      case Some(session) =>
        ujson.Obj(
          "sessionId" -> sessionId,
          "messageCount" -> session.engine.getMessageCount,
          "status" -> "active"
        )
      case None =>
        logger.warn(s"Session not found: $sessionId")
        ujson.Obj(
          "error" -> "Session not found",
          "status" -> "error"
        )
    }
  }

  @get("/") 
  def serveApp() = {
    """<!DOCTYPE html>
      |<html>
      |<head><title>Szork</title></head>
      |<body>
      |<h1>Szork Server</h1>
      |<p>Use the frontend at http://localhost:3090 or the API endpoints directly.</p>
      |</body>
      |</html>""".stripMargin
  }

  // Initialize music generation if configured
  // private val musicGen = MusicGeneration() // Commented out - not currently used

  override def port: Int = config.port
  override def host: String = config.host

  logger.info(s"Starting Szork Server on http://$host:$port")

  
  def allRoutes = Seq(this)
  
  @post("/api/game/save/:sessionId")
  def saveGame(sessionId: String) = {
    logger.info(s"Saving game for session: $sessionId")
    
    sessions.get(sessionId) match {
      case Some(session) =>
        val gameState = session.engine.getGameState(session.gameId, session.theme, session.artStyle)
        GamePersistence.saveGame(gameState) match {
          case Right(_) =>
            logger.info(s"Game saved successfully: ${session.gameId}")
            ujson.Obj(
              "status" -> "success",
              "gameId" -> session.gameId
            )
          case Left(error) =>
            logger.error(s"Failed to save game: $error")
            ujson.Obj(
              "status" -> "error",
              "error" -> error
            )
        }
      case None =>
        logger.warn(s"Session not found for save: $sessionId")
        ujson.Obj(
          "status" -> "error",
          "error" -> "Session not found"
        )
    }
  }
  
  @get("/api/game/load/:gameId")
  def loadGame(gameId: String) = {
    logger.info(s"Loading game: $gameId")
    
    GamePersistence.loadGame(gameId) match {
      case Right(gameState) =>
        // Create new session for loaded game
        val sessionId = java.util.UUID.randomUUID().toString
        val engine = GameEngine.create(sessionId, gameState.theme.map(_.prompt), gameState.artStyle.map(_.id), None)
        
        // Restore the game state
        engine.restoreGameState(gameState)
        
        val session = GameSession(
          id = sessionId,
          gameId = gameId,
          engine = engine,
          theme = gameState.theme,
          artStyle = gameState.artStyle
        )
        
        sessions(sessionId) = session
        logger.info(s"Game loaded successfully: $gameId -> session $sessionId")
        
        // Check for cached image for current scene
        val cachedImage = gameState.currentScene.flatMap { scene =>
          gameState.artStyle.flatMap { artStyle =>
            MediaCache.getCachedImage(
              gameId, 
              scene.locationId, 
              scene.imageDescription, 
              artStyle.id
            )
          }
        }
        
        ujson.Obj(
          "status" -> "success",
          "sessionId" -> sessionId,
          "gameId" -> gameId,
          "adventureTitle" -> gameState.adventureTitle.map(ujson.Str(_)).getOrElse(ujson.Null),
          "scene" -> gameState.currentScene.map { scene =>
            ujson.Obj(
              "locationId" -> scene.locationId,
              "locationName" -> scene.locationName,
              "narrationText" -> scene.narrationText,
              "exits" -> scene.exits.map(exit => ujson.Obj(
                "direction" -> exit.direction,
                "description" -> ujson.Str(exit.description.getOrElse(""))
              )),
              "items" -> scene.items,
              "npcs" -> scene.npcs,
              "imageDescription" -> scene.imageDescription,
              "cachedImage" -> (cachedImage match {
                case Some(img) => ujson.Str(img)
                case None => ujson.Null
              })
            )
          }.getOrElse(ujson.Null),
          "conversationHistory" -> gameState.conversationHistory.map { entry =>
            ujson.Obj(
              "role" -> entry.role,
              "content" -> entry.content,
              "timestamp" -> entry.timestamp
            )
          }
        )
      case Left(error) =>
        logger.error(s"Failed to load game: $error")
        ujson.Obj(
          "status" -> "error",
          "error" -> error
        )
    }
  }
  
  @get("/api/game/list")
  def listGames() = {
    logger.info("Listing saved games")
    
    val games = GamePersistence.listGames()
    ujson.Obj(
      "status" -> "success",
      "games" -> games.map { game =>
        ujson.Obj(
          "gameId" -> game.gameId,
          "theme" -> game.theme,
          "artStyle" -> game.artStyle,
          "createdAt" -> game.createdAt,
          "lastSaved" -> game.lastSaved
        )
      }
    )
  }
  
  @get("/api/game/cache/:gameId")
  def getCacheStats(gameId: String) = {
    logger.info(s"Getting cache stats for game: $gameId")
    
    val stats = MediaCache.getCacheStats(gameId)
    ujson.Obj(
      "status" -> "success",
      "cache" -> ujson.Obj(
        "gameId" -> stats.getOrElse("gameId", "unknown").toString,
        "exists" -> (stats.getOrElse("exists", false) match {
          case b: Boolean => b
          case _ => false
        }),
        "imageCount" -> (stats.getOrElse("imageCount", 0) match {
          case l: Long => l.toInt
          case i: Int => i
          case _ => 0
        }),
        "musicCount" -> (stats.getOrElse("musicCount", 0) match {
          case l: Long => l.toInt
          case i: Int => i
          case _ => 0
        }),
        "totalSizeBytes" -> (stats.getOrElse("totalSizeBytes", 0L) match {
          case l: Long => l
          case i: Int => i.toLong
          case _ => 0L
        })
      )
    )
  }
  
  @delete("/api/game/:gameId")
  def deleteGame(gameId: String) = {
    logger.info(s"Deleting game: $gameId")
    
    // Delete the game save file
    val deleteResult = GamePersistence.deleteGame(gameId)
    
    // Also clear the media cache for this game
    val cacheResult = MediaCache.clearGameCache(gameId)
    
    deleteResult match {
      case Right(_) =>
        logger.info(s"Successfully deleted game: $gameId")
        // Log cache clearing result but don't fail if cache clearing fails
        cacheResult match {
          case Left(error) => logger.warn(s"Failed to clear cache for deleted game $gameId: $error")
          case _ => logger.info(s"Also cleared cache for deleted game: $gameId")
        }
        
        ujson.Obj(
          "status" -> "success",
          "message" -> s"Game deleted: $gameId"
        )
      case Left(error) =>
        logger.error(s"Failed to delete game $gameId: $error")
        ujson.Obj(
          "status" -> "error",
          "error" -> error
        )
    }
  }
  
  @delete("/api/game/cache/:gameId")  
  def clearGameCache(gameId: String) = {
    logger.info(s"Clearing cache for game: $gameId")
    
    MediaCache.clearGameCache(gameId) match {
      case Right(_) =>
        ujson.Obj(
          "status" -> "success",
          "message" -> s"Cache cleared for game: $gameId"
        )
      case Left(error) =>
        ujson.Obj(
          "status" -> "error",
          "error" -> error
        )
    }
  }
  
  // Initialize routes
  initialize()
}