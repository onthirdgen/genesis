package com.callaudit.notification.listener;

import com.callaudit.notification.service.AlertRuleEngine;
import com.callaudit.notification.service.NotificationService;
import com.callaudit.notification.websocket.WebSocketNotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka event listener for notification triggers
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final AlertRuleEngine alertRuleEngine;
    private final WebSocketNotificationService webSocketNotificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Listens to sentiment analysis events
     */
    @KafkaListener(topics = "calls.sentiment-analyzed", groupId = "notification-service")
    public void handleSentimentAnalyzed(String message) {
        try {
            log.debug("Received sentiment analyzed event: {}", message);
            JsonNode event = objectMapper.readTree(message);

            UUID callId = extractCallId(event);
            if (callId == null) {
                log.warn("Could not extract valid callId from sentiment analyzed event");
                return;
            }
            JsonNode payload = event.get("payload");

            if (alertRuleEngine.shouldAlert("SentimentAnalyzed", payload)) {
                log.info("Alert triggered for sentiment analysis on call: {}", callId);

                // Check for escalation
                if (payload.has("escalationDetected") &&
                    payload.get("escalationDetected").asBoolean()) {
                    notificationService.processEscalation(callId, payload);
                }

                // Check for high churn risk
                if (payload.has("predictedChurnRisk")) {
                    double churnRisk = payload.get("predictedChurnRisk").asDouble();
                    if (churnRisk >= 0.7) {
                        notificationService.processHighChurnRisk(callId, payload);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing sentiment analyzed event", e);
        }
    }

    /**
     * Listens to VoC analysis events
     */
    @KafkaListener(topics = "calls.voc-analyzed", groupId = "notification-service")
    public void handleVoCAnalyzed(String message) {
        try {
            log.debug("Received VoC analyzed event: {}", message);
            JsonNode event = objectMapper.readTree(message);

            UUID callId = extractCallId(event);
            if (callId == null) {
                log.warn("Could not extract valid callId from VoC analyzed event");
                return;
            }
            JsonNode payload = event.get("payload");

            if (alertRuleEngine.shouldAlert("VoCAAnalyzed", payload)) {
                log.info("Alert triggered for VoC analysis on call: {}", callId);

                // Check if flagged for review
                if (payload.has("flagsForReview") &&
                    payload.get("flagsForReview").asBoolean()) {
                    processVoCReview(callId, payload);
                }
            }
        } catch (Exception e) {
            log.error("Error processing VoC analyzed event", e);
        }
    }

    /**
     * Listens to audit events
     */
    @KafkaListener(topics = "calls.audited", groupId = "notification-service")
    public void handleCallAudited(String message) {
        try {
            log.debug("Received call audited event: {}", message);
            JsonNode event = objectMapper.readTree(message);

            UUID callId = extractCallId(event);
            if (callId == null) {
                log.warn("Could not extract valid callId from call audited event");
                return;
            }
            JsonNode payload = event.get("payload");

            if (alertRuleEngine.shouldAlert("CallAudited", payload)) {
                log.info("Alert triggered for audit on call: {}", callId);

                // Check for compliance violations
                if (payload.has("violations")) {
                    JsonNode violations = payload.get("violations");
                    if (violations.isArray() && violations.size() > 0) {
                        notificationService.processComplianceViolation(callId, payload);
                    }
                }

                // Check if flagged for review
                if (payload.has("flagsForReview") &&
                    payload.get("flagsForReview").asBoolean()) {
                    processAuditReview(callId, payload);
                }
            }
        } catch (Exception e) {
            log.error("Error processing call audited event", e);
        }
    }

    /**
     * Processes VoC review requirement
     */
    private void processVoCReview(UUID callId, JsonNode payload) {
        StringBuilder body = new StringBuilder();
        body.append("VoC analysis flagged for review for call: ").append(callId).append("\n\n");

        if (payload.has("criticalThemes")) {
            body.append("Critical Themes:\n");
            JsonNode themes = payload.get("criticalThemes");
            if (themes.isArray()) {
                for (JsonNode theme : themes) {
                    body.append("- ").append(theme.asText()).append("\n");
                }
            }
        }

        if (payload.has("keyPhrases")) {
            body.append("\nKey Phrases:\n");
            JsonNode phrases = payload.get("keyPhrases");
            if (phrases.isArray()) {
                for (JsonNode phrase : phrases) {
                    body.append("- ").append(phrase.asText()).append("\n");
                }
            }
        }

        body.append("\nAction Required: Review VoC insights for this call.");

        var priority = alertRuleEngine.getPriority("VoCAAnalyzed", payload);
        var recipients = alertRuleEngine.getRecipients("VoCAAnalyzed", payload);

        for (String recipient : recipients) {
            notificationService.createNotification(
                    callId,
                    com.callaudit.notification.model.NotificationType.REVIEW_REQUIRED,
                    recipient,
                    com.callaudit.notification.model.NotificationChannel.EMAIL,
                    "VOC REVIEW REQUIRED - Call " + callId,
                    body.toString(),
                    priority
            );
        }
    }

    /**
     * Processes audit review requirement
     */
    private void processAuditReview(UUID callId, JsonNode payload) {
        StringBuilder body = new StringBuilder();
        body.append("Audit flagged for review for call: ").append(callId).append("\n\n");

        if (payload.has("complianceScore")) {
            double score = payload.get("complianceScore").asDouble();
            body.append("Compliance Score: ").append(String.format("%.2f", score)).append("\n");
        }

        if (payload.has("reviewReason")) {
            body.append("Review Reason: ").append(payload.get("reviewReason").asText())
                .append("\n");
        }

        body.append("\nAction Required: Manual review of audit results required.");

        var priority = alertRuleEngine.getPriority("CallAudited", payload);
        var recipients = alertRuleEngine.getRecipients("CallAudited", payload);

        for (String recipient : recipients) {
            notificationService.createNotification(
                    callId,
                    com.callaudit.notification.model.NotificationType.REVIEW_REQUIRED,
                    recipient,
                    com.callaudit.notification.model.NotificationChannel.EMAIL,
                    "AUDIT REVIEW REQUIRED - Call " + callId,
                    body.toString(),
                    priority
            );
        }
    }

    /**
     * Listens to call received events - notifies clients that processing has started.
     */
    @KafkaListener(topics = "calls.received", groupId = "notification-service-websocket")
    public void handleCallReceived(String message) {
        try {
            log.debug("Received call received event: {}", message);
            JsonNode event = objectMapper.readTree(message);

            UUID callId = extractCallId(event);
            if (callId == null) {
                log.warn("Could not extract valid callId from call received event");
                return;
            }
            String callIdStr = callId.toString();

            // Notify WebSocket clients that transcription is starting
            webSocketNotificationService.notifyTranscriptionStarted(callIdStr, null);
            webSocketNotificationService.notifyCallStatusUpdate(callIdStr, "RECEIVED", "Call received, processing started");

            log.info("WebSocket notification sent for call received: {}", callId);
        } catch (Exception e) {
            log.error("Error processing call received event for WebSocket", e);
        }
    }

    /**
     * Listens to transcription completed events - notifies clients of completion.
     */
    @KafkaListener(topics = "calls.transcribed", groupId = "notification-service-websocket")
    public void handleCallTranscribed(String message) {
        try {
            log.debug("Received call transcribed event: {}", message);
            JsonNode event = objectMapper.readTree(message);

            UUID callId = extractCallId(event);
            if (callId == null) {
                log.warn("Could not extract valid callId from call transcribed event");
                return;
            }
            String callIdStr = callId.toString();
            JsonNode payload = event.get("payload");

            // Extract transcription details
            String transcriptionId = callIdStr; // Using callId as transcription ID
            int segmentCount = 0;
            long duration = 0;

            if (payload != null) {
                if (payload.has("segments") && payload.get("segments").isArray()) {
                    segmentCount = payload.get("segments").size();
                }
                if (payload.has("totalDuration")) {
                    duration = payload.get("totalDuration").asLong();
                }
            }

            // Notify WebSocket clients
            webSocketNotificationService.notifyTranscriptionProgress(callIdStr, 100, segmentCount, segmentCount);
            webSocketNotificationService.notifyTranscriptionCompleted(callIdStr, transcriptionId, segmentCount, duration);
            webSocketNotificationService.notifyCallStatusUpdate(callIdStr, "TRANSCRIBED", "Transcription completed");

            log.info("WebSocket notification sent for call transcribed: {}", callId);
        } catch (Exception e) {
            log.error("Error processing call transcribed event for WebSocket", e);
        }
    }

    /**
     * Extracts call ID from event as UUID
     */
    private UUID extractCallId(JsonNode event) {
        String callIdStr = null;
        if (event.has("aggregateId")) {
            callIdStr = event.get("aggregateId").asText();
        } else if (event.has("payload") && event.get("payload").has("callId")) {
            callIdStr = event.get("payload").get("callId").asText();
        }

        if (callIdStr == null || callIdStr.isEmpty() || "unknown".equals(callIdStr)) {
            return null;
        }

        try {
            return UUID.fromString(callIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format for callId: {}", callIdStr);
            return null;
        }
    }
}
