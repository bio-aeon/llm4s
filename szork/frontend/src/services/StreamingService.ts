/**
 * Service for handling Server-Sent Events (SSE) streaming from the game server.
 * Manages streaming text responses for a better user experience.
 */

export interface StreamingCallbacks {
  onChunk: (text: string) => void;
  onComplete: (data: CompleteResponse) => void;
  onError: (error: string) => void;
}

export interface CompleteResponse {
  complete: boolean;
  messageIndex: number;
  hasImage?: boolean;
  hasMusic?: boolean;
  scene?: {
    locationName: string;
    exits: Array<{
      direction: string;
      description: string;
    }>;
    items?: string[];
    npcs?: string[];
  };
  audio?: string; // Base64 encoded audio
}

export class StreamingService {
  private abortController: AbortController | null = null;
  private decoder = new TextDecoder();
  
  /**
   * Stream a command to the server and receive text chunks as they're generated.
   */
  async streamCommand(
    sessionId: string,
    command: string,
    imageGenerationEnabled: boolean,
    callbacks: StreamingCallbacks
  ): Promise<void> {
    // Clean up any existing stream
    this.close();
    
    // Create new abort controller for this stream
    this.abortController = new AbortController();
    
    const body = JSON.stringify({
      sessionId,
      command,
      imageGenerationEnabled
    });
    
    try {
      const response = await fetch('/api/game/stream', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body,
        signal: this.abortController.signal
      });
      
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      
      const reader = response.body?.getReader();
      if (!reader) {
        throw new Error('No response body');
      }
      
      await this.processStream(reader, callbacks);
      
    } catch (error) {
      if (error instanceof Error) {
        if (error.name === 'AbortError') {
          console.log('Stream aborted');
        } else {
          console.error('Streaming error:', error);
          callbacks.onError(error.message);
        }
      }
    }
  }
  
  /**
   * Process the SSE stream from the server.
   */
  private async processStream(
    reader: ReadableStreamDefaultReader<Uint8Array>,
    callbacks: StreamingCallbacks
  ): Promise<void> {
    let buffer = '';
    
    try {
      while (true) {
        const { done, value } = await reader.read();
        
        if (done) {
          break;
        }
        
        // Decode the chunk and add to buffer
        buffer += this.decoder.decode(value, { stream: true });
        
        // Process complete SSE events in the buffer
        const lines = buffer.split('\n');
        buffer = lines.pop() || ''; // Keep incomplete line in buffer
        
        let currentEvent = '';
        let currentData = '';
        
        for (const line of lines) {
          if (line.startsWith('event: ')) {
            currentEvent = line.substring(7).trim();
          } else if (line.startsWith('data: ')) {
            currentData = line.substring(6);
          } else if (line === '' && currentEvent && currentData) {
            // Empty line marks end of event
            this.handleSSEEvent(currentEvent, currentData, callbacks);
            currentEvent = '';
            currentData = '';
          }
        }
      }
    } catch (error) {
      console.error('Error processing stream:', error);
      if (error instanceof Error) {
        callbacks.onError(error.message);
      }
    } finally {
      reader.releaseLock();
    }
  }
  
  /**
   * Handle individual SSE events from the server.
   */
  private handleSSEEvent(
    eventType: string,
    data: string,
    callbacks: StreamingCallbacks
  ): void {
    try {
      const parsedData = JSON.parse(data);
      
      switch (eventType) {
        case 'chunk':
          // Text chunk to display progressively
          if (parsedData.text) {
            callbacks.onChunk(parsedData.text);
          }
          break;
          
        case 'complete':
          // Streaming complete, with final metadata
          callbacks.onComplete(parsedData as CompleteResponse);
          break;
          
        case 'error':
          // Error occurred during streaming
          callbacks.onError(parsedData.error || 'Unknown error');
          break;
          
        default:
          console.warn('Unknown SSE event type:', eventType);
      }
    } catch (error) {
      console.error('Error parsing SSE data:', error);
      callbacks.onError('Failed to parse server response');
    }
  }
  
  /**
   * Close the current streaming connection.
   */
  close(): void {
    if (this.abortController) {
      this.abortController.abort();
      this.abortController = null;
    }
  }
  
  /**
   * Check if a stream is currently active.
   */
  isStreaming(): boolean {
    return this.abortController !== null;
  }
}