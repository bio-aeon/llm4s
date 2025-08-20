package org.llm4s.szork

import org.slf4j.LoggerFactory

/**
 * Parses streaming response that has narration text followed by JSON.
 * Format:
 *   Narration text here...
 *   <<<JSON>>>
 *   {"responseType": "fullScene", ...}
 */
class StreamingTextParser {
  private val logger = LoggerFactory.getLogger(getClass)
  
  private val accumulated = new StringBuilder()
  private var jsonStarted = false
  private var narrationComplete = false
  private var lastNarrationPosition = 0
  
  /**
   * Process a new chunk and extract narration text if available.
   * 
   * @param chunk The new chunk
   * @return Option containing any newly available narration text
   */
  def processChunk(chunk: String): Option[String] = {
    accumulated.append(chunk)
    val fullText = accumulated.toString
    
    if (!narrationComplete) {
      // Look for the JSON marker
      val jsonMarkerIndex = fullText.indexOf("<<<JSON>>>")
      
      if (jsonMarkerIndex >= 0) {
        // Found the marker - everything before it is narration
        narrationComplete = true
        jsonStarted = true
        
        val narration = fullText.substring(0, jsonMarkerIndex).trim
        if (narration.length > lastNarrationPosition) {
          val newText = narration.substring(lastNarrationPosition)
          lastNarrationPosition = narration.length
          logger.debug(s"Extracted final narration chunk: ${newText.take(50)}...")
          return Some(newText)
        }
      } else {
        // No marker yet - stream what we have so far, but keep some buffer
        // in case "<<<JSON>>>" is split across chunks
        val safeLength = Math.max(0, fullText.length - 15) // Keep last 15 chars as buffer
        
        if (safeLength > lastNarrationPosition) {
          val newText = fullText.substring(lastNarrationPosition, safeLength)
          lastNarrationPosition = safeLength
          
          if (newText.nonEmpty) {
            logger.debug(s"Streaming narration chunk (${newText.length} chars): ${newText.take(50)}...")
            return Some(newText)
          }
        }
      }
    }
    
    None
  }
  
  /**
   * Get the JSON portion of the response (after <<<JSON>>> marker).
   * 
   * @return The JSON string if available
   */
  def getJson(): Option[String] = {
    if (jsonStarted) {
      val fullText = accumulated.toString
      val jsonMarkerIndex = fullText.indexOf("<<<JSON>>>")
      if (jsonMarkerIndex >= 0) {
        val jsonStart = jsonMarkerIndex + "<<<JSON>>>".length
        val json = fullText.substring(jsonStart).trim
        if (json.nonEmpty) {
          return Some(json)
        }
      }
    }
    None
  }
  
  /**
   * Get the complete narration text.
   */
  def getNarration(): Option[String] = {
    val fullText = accumulated.toString
    val jsonMarkerIndex = fullText.indexOf("<<<JSON>>>")
    
    if (jsonMarkerIndex >= 0) {
      Some(fullText.substring(0, jsonMarkerIndex).trim)
    } else if (fullText.nonEmpty) {
      // No JSON marker found, treat entire text as narration
      Some(fullText.trim)
    } else {
      None
    }
  }
  
  /**
   * Reset the parser for a new response.
   */
  def reset(): Unit = {
    accumulated.clear()
    jsonStarted = false
    narrationComplete = false
    lastNarrationPosition = 0
    logger.debug("StreamingTextParser reset")
  }
}