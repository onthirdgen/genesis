package com.callaudit.notification.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Service for sending WebSocket notifications to connected clients.
 *
 * Provides methods for pushing various types of transcription and
 * call status updates to clients watching specific calls.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketNotificationService {

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    /**
     * Notify clients that transcription has started for a call.
     */
    public void notifyTranscriptionStarted(String callId, Integer estimatedDuration) {
        sendMessage(callId, WebSocketMessageType.TRANSCRIPTION_STARTED, Map.of(
                "callId", callId,
                "estimatedDuration", estimatedDuration != null ? estimatedDuration : 0
        ));
    }

    /**
     * Notify clients of transcription progress.
     */
    public void notifyTranscriptionProgress(String callId, int progress, Integer currentSegment, Integer totalSegments) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("callId", callId);
        payload.put("progress", progress);
        if (currentSegment != null) payload.put("currentSegment", currentSegment);
        if (totalSegments != null) payload.put("totalSegments", totalSegments);

        sendMessage(callId, WebSocketMessageType.TRANSCRIPTION_PROGRESS, payload);
    }

    /**
     * Notify clients of a new transcription segment.
     */
    public void notifyTranscriptionSegment(String callId, Object segment) {
        sendMessage(callId, WebSocketMessageType.TRANSCRIPTION_SEGMENT, Map.of(
                "callId", callId,
                "segment", segment
        ));
    }

    /**
     * Notify clients that transcription is completed.
     */
    public void notifyTranscriptionCompleted(String callId, String transcriptionId, int totalSegments, long duration) {
        sendMessage(callId, WebSocketMessageType.TRANSCRIPTION_COMPLETED, Map.of(
                "callId", callId,
                "transcriptionId", transcriptionId,
                "totalSegments", totalSegments,
                "duration", duration
        ));
    }

    /**
     * Notify clients that transcription has failed.
     */
    public void notifyTranscriptionFailed(String callId, String error, String code) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("callId", callId);
        payload.put("error", error);
        if (code != null) payload.put("code", code);

        sendMessage(callId, WebSocketMessageType.TRANSCRIPTION_FAILED, payload);
    }

    /**
     * Notify clients of a call status update.
     */
    public void notifyCallStatusUpdate(String callId, String status, String message) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("callId", callId);
        payload.put("status", status);
        if (message != null) payload.put("message", message);

        sendMessage(callId, WebSocketMessageType.CALL_STATUS_UPDATE, payload);
    }

    /**
     * Send a WebSocket message to all clients watching a specific call.
     */
    private void sendMessage(String callId, WebSocketMessageType type, Object payload) {
        if (!sessionManager.hasSessionsForCall(callId)) {
            log.debug("No WebSocket sessions for call {}, skipping notification", callId);
            return;
        }

        try {
            WebSocketMessage message = WebSocketMessage.builder()
                    .type(type)
                    .callId(callId)
                    .payload(payload)
                    .timestamp(Instant.now().toString())
                    .build();

            String jsonMessage = objectMapper.writeValueAsString(message);
            sessionManager.sendToCall(callId, jsonMessage);
            log.debug("Sent {} notification for call {}", type, callId);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for call {}: {}", callId, e.getMessage());
        }
    }
}
