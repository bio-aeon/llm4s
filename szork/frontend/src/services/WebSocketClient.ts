/**
 * WebSocket client for game communication
 */

import type { 
  ClientMessage, 
  ServerMessage,
  ConnectedMessage,
  GameStartedMessage,
  GameLoadedMessage,
  CommandResponseMessage,
  TextChunkMessage,
  StreamCompleteMessage,
  TranscriptionMessage,
  ImageReadyMessage,
  MusicReadyMessage,
  GamesListMessage,
  ErrorMessage
} from '@/types/WebSocketProtocol';

export type MessageHandler<T extends ServerMessage> = (message: T) => void;

export class WebSocketClient {
  private ws: WebSocket | null = null;
  private url: string;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 1000;
  private messageHandlers = new Map<string, Set<MessageHandler<any>>>();
  private isConnecting = false;
  private isConnected = false;
  private messageQueue: ClientMessage[] = [];
  private pingInterval: NodeJS.Timeout | null = null;
  
  constructor(url: string = 'ws://localhost:9002') {
    this.url = url;
  }
  
  /**
   * Connect to the WebSocket server
   */
  async connect(): Promise<void> {
    if (this.isConnected || this.isConnecting) {
      console.log('[WebSocketClient] Already connected or connecting');
      return;
    }
    
    this.isConnecting = true;
    
    return new Promise((resolve, reject) => {
      try {
        console.log(`[WebSocketClient] Connecting to ${this.url}`);
        this.ws = new WebSocket(this.url);
        
        this.ws.onopen = () => {
          console.log('[WebSocketClient] Connected');
          this.isConnecting = false;
          this.isConnected = true;
          this.reconnectAttempts = 0;
          
          // Send any queued messages
          while (this.messageQueue.length > 0) {
            const message = this.messageQueue.shift();
            if (message) {
              this.send(message);
            }
          }
          
          // Start ping interval to keep connection alive
          this.startPingInterval();
          
          resolve();
        };
        
        this.ws.onmessage = (event) => {
          this.handleMessage(event.data);
        };
        
        this.ws.onerror = (error) => {
          console.error('[WebSocketClient] Error:', error);
          this.isConnecting = false;
          this.isConnected = false;
        };
        
        this.ws.onclose = () => {
          console.log('[WebSocketClient] Connection closed');
          this.isConnecting = false;
          this.isConnected = false;
          this.stopPingInterval();
          
          // Attempt to reconnect
          if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            console.log(`[WebSocketClient] Reconnecting... (attempt ${this.reconnectAttempts})`);
            setTimeout(() => {
              this.connect();
            }, this.reconnectDelay * this.reconnectAttempts);
          }
        };
        
      } catch (error) {
        console.error('[WebSocketClient] Failed to connect:', error);
        this.isConnecting = false;
        reject(error);
      }
    });
  }
  
  /**
   * Disconnect from the server
   */
  disconnect(): void {
    if (this.ws) {
      this.stopPingInterval();
      this.ws.close();
      this.ws = null;
      this.isConnected = false;
    }
  }
  
  /**
   * Send a message to the server
   */
  send(message: ClientMessage): void {
    if (!this.isConnected || !this.ws) {
      console.log('[WebSocketClient] Not connected, queueing message');
      this.messageQueue.push(message);
      return;
    }
    
    const json = JSON.stringify(message);
    console.log('[WebSocketClient] Sending:', message.type);
    this.ws.send(json);
  }
  
  /**
   * Register a handler for a specific message type
   */
  on<T extends ServerMessage>(type: T['type'], handler: MessageHandler<T>): void {
    if (!this.messageHandlers.has(type)) {
      this.messageHandlers.set(type, new Set());
    }
    this.messageHandlers.get(type)?.add(handler);
  }
  
  /**
   * Remove a handler for a specific message type
   */
  off<T extends ServerMessage>(type: T['type'], handler: MessageHandler<T>): void {
    this.messageHandlers.get(type)?.delete(handler);
  }
  
  /**
   * Handle incoming messages from the server
   */
  private handleMessage(data: string): void {
    try {
      const message = JSON.parse(data) as ServerMessage;
      console.log('[WebSocketClient] Received:', message.type);
      
      // Call registered handlers for this message type
      const handlers = this.messageHandlers.get(message.type);
      if (handlers) {
        handlers.forEach(handler => handler(message));
      }
      
      // Also call generic handlers (registered with '*')
      const genericHandlers = this.messageHandlers.get('*');
      if (genericHandlers) {
        genericHandlers.forEach(handler => handler(message));
      }
      
    } catch (error) {
      console.error('[WebSocketClient] Failed to parse message:', error);
    }
  }
  
  /**
   * Start sending periodic ping messages to keep connection alive
   */
  private startPingInterval(): void {
    this.pingInterval = setInterval(() => {
      if (this.isConnected) {
        this.send({ type: 'ping', timestamp: Date.now() });
      }
    }, 30000); // Ping every 30 seconds
  }
  
  /**
   * Stop sending ping messages
   */
  private stopPingInterval(): void {
    if (this.pingInterval) {
      clearInterval(this.pingInterval);
      this.pingInterval = null;
    }
  }
  
  /**
   * Check if connected
   */
  get connected(): boolean {
    return this.isConnected;
  }
  
  // ============= Convenience methods for common operations =============
  
  /**
   * Start a new game
   */
  newGame(theme?: string, artStyle?: string, imageGeneration = true): void {
    this.send({
      type: 'newGame',
      theme,
      artStyle,
      imageGeneration
    });
  }
  
  /**
   * Load an existing game
   */
  loadGame(gameId: string): void {
    this.send({
      type: 'loadGame',
      gameId
    });
  }
  
  /**
   * Send a regular command
   */
  sendCommand(command: string): void {
    this.send({
      type: 'command',
      command
    });
  }
  
  /**
   * Send a streaming command
   */
  sendStreamCommand(command: string, imageGeneration?: boolean): void {
    this.send({
      type: 'streamCommand',
      command,
      imageGeneration
    });
  }
  
  /**
   * Send audio for transcription and processing
   */
  sendAudio(audioBase64: string): void {
    this.send({
      type: 'audioCommand',
      audio: audioBase64
    });
  }
  
  /**
   * Request list of saved games
   */
  listGames(): void {
    this.send({
      type: 'listGames'
    });
  }
  
  /**
   * Request image for a specific message
   */
  getImage(messageIndex: number): void {
    this.send({
      type: 'getImage',
      messageIndex
    });
  }
  
  /**
   * Request music for a specific message
   */
  getMusic(messageIndex: number): void {
    this.send({
      type: 'getMusic',
      messageIndex
    });
  }
}