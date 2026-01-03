package com.callaudit.notification.websocket;

/**
 * WebSocket message types matching the frontend protocol.
 */
public enum WebSocketMessageType {
    // Connection messages
    CONNECT,
    CONNECTED,
    DISCONNECT,
    ERROR,

    // Transcription progress messages
    TRANSCRIPTION_STARTED,
    TRANSCRIPTION_PROGRESS,
    TRANSCRIPTION_SEGMENT,
    TRANSCRIPTION_COMPLETED,
    TRANSCRIPTION_FAILED,

    // Call status messages
    CALL_STATUS_UPDATE
}
