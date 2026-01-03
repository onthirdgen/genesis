package com.callaudit.notification.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriTemplate;

import java.time.Instant;
import java.util.Map;

/**
 * WebSocket handler for real-time call transcription updates.
 *
 * Handles WebSocket connections at /ws/calls/{callId} and manages
 * message routing between Kafka events and connected clients.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallUpdatesWebSocketHandler extends TextWebSocketHandler {

    private static final UriTemplate URI_TEMPLATE = new UriTemplate("/ws/calls/{callId}");

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String callId = extractCallId(session);
        if (callId == null) {
            log.warn("No callId found in WebSocket URI: {}", session.getUri());
            session.close(CloseStatus.BAD_DATA.withReason("Missing callId in path"));
            return;
        }

        sessionManager.registerSession(callId, session);

        // Send connected confirmation
        WebSocketMessage connectedMessage = WebSocketMessage.builder()
                .type(WebSocketMessageType.CONNECTED)
                .callId(callId)
                .payload(Map.of(
                        "callId", callId,
                        "message", "Connected to call updates"
                ))
                .timestamp(Instant.now().toString())
                .build();

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(connectedMessage)));
        log.info("WebSocket connection established for call {}, session {}", callId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessionManager.removeSession(session);
        log.info("WebSocket connection closed: {}, status: {}", session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("Received WebSocket message: {}", payload);

        try {
            WebSocketMessage wsMessage = objectMapper.readValue(payload, WebSocketMessage.class);
            handleIncomingMessage(session, wsMessage);
        } catch (Exception e) {
            log.error("Failed to parse WebSocket message: {}", e.getMessage());
            sendError(session, "PARSE_ERROR", "Invalid message format: " + e.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
        sessionManager.removeSession(session);
    }

    /**
     * Handle incoming WebSocket messages from clients.
     */
    private void handleIncomingMessage(WebSocketSession session, WebSocketMessage message) throws Exception {
        switch (message.getType()) {
            case CONNECT:
                // Client is acknowledging connection - already handled in afterConnectionEstablished
                log.debug("Client sent CONNECT message for session {}", session.getId());
                break;

            case DISCONNECT:
                // Client wants to disconnect
                session.close(CloseStatus.NORMAL);
                break;

            default:
                log.debug("Unhandled message type: {}", message.getType());
        }
    }

    /**
     * Send an error message to a session.
     */
    private void sendError(WebSocketSession session, String code, String errorMessage) {
        try {
            WebSocketMessage error = WebSocketMessage.builder()
                    .type(WebSocketMessageType.ERROR)
                    .payload(Map.of(
                            "code", code,
                            "message", errorMessage
                    ))
                    .timestamp(Instant.now().toString())
                    .build();

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
        } catch (Exception e) {
            log.error("Failed to send error message: {}", e.getMessage());
        }
    }

    /**
     * Extract callId from WebSocket session URI.
     */
    private String extractCallId(WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }

        String path = session.getUri().getPath();
        Map<String, String> variables = URI_TEMPLATE.match(path);
        return variables != null ? variables.get("callId") : null;
    }
}
