package com.callaudit.notification.service;

import com.callaudit.notification.model.*;
import com.callaudit.notification.repository.NotificationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for creating and sending notifications
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final AlertRuleEngine alertRuleEngine;

    /**
     * Creates and saves a notification
     */
    @Transactional
    public Notification createNotification(
            UUID callId,
            NotificationType type,
            String recipient,
            NotificationChannel channel,
            String subject,
            String body,
            NotificationPriority priority) {

        Notification notification = Notification.builder()
                .callId(callId)
                .notificationType(type)
                .recipient(recipient)
                .channel(channel)
                .subject(subject)
                .body(body)
                .priority(priority)
                .status(NotificationStatus.PENDING)
                .build();

        notification = notificationRepository.save(notification);
        log.info("Created notification: {} for call: {} with type: {}",
                notification.getId(), callId, type);

        // Attempt to send immediately
        sendNotification(notification);

        return notification;
    }

    /**
     * Sends a notification via the configured channel
     * Stub implementation - logs only. In production would integrate with:
     * - Email service (SMTP, SendGrid, AWS SES)
     * - Slack API
     * - Webhook endpoints
     */
    @Transactional
    public void sendNotification(Notification notification) {
        try {
            log.info("Sending notification {} via {}: {}",
                    notification.getId(),
                    notification.getChannel(),
                    notification.getSubject());

            switch (notification.getChannel()) {
                case EMAIL -> sendEmail(notification);
                case SLACK -> sendSlack(notification);
                case WEBHOOK -> sendWebhook(notification);
            }

            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
            notificationRepository.save(notification);

            log.info("Successfully sent notification: {}", notification.getId());
        } catch (Exception e) {
            log.error("Failed to send notification: {}", notification.getId(), e);
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage(e.getMessage());
            notificationRepository.save(notification);
        }
    }

    /**
     * Processes a compliance violation event and creates appropriate notification
     */
    @Transactional
    public void processComplianceViolation(UUID callId, JsonNode payload) {
        log.debug("Processing compliance violation for call: {}", callId);

        StringBuilder body = new StringBuilder();
        body.append("Compliance issues detected for call: ").append(callId).append("\n\n");

        if (payload.has("complianceScore")) {
            double score = payload.get("complianceScore").asDouble();
            body.append("Compliance Score: ").append(String.format("%.2f", score)).append("\n");
        }

        if (payload.has("violations")) {
            body.append("\nViolations:\n");
            JsonNode violations = payload.get("violations");
            if (violations.isArray()) {
                for (JsonNode violation : violations) {
                    String severity = violation.has("severity") ?
                            violation.get("severity").asText() : "UNKNOWN";
                    String rule = violation.has("rule") ?
                            violation.get("rule").asText() : "Unknown rule";
                    String description = violation.has("description") ?
                            violation.get("description").asText() : "";

                    if ("CRITICAL".equalsIgnoreCase(severity) || "HIGH".equalsIgnoreCase(severity)) {
                        body.append("- [").append(severity).append("] ")
                            .append(rule).append(": ").append(description).append("\n");
                    }
                }
            }
        }

        NotificationPriority priority = alertRuleEngine.getPriority("CallAudited", payload);
        List<String> recipients = alertRuleEngine.getRecipients("CallAudited", payload);

        for (String recipient : recipients) {
            createNotification(
                    callId,
                    NotificationType.COMPLIANCE_VIOLATION,
                    recipient,
                    NotificationChannel.EMAIL,
                    "COMPLIANCE VIOLATION - Call " + callId,
                    body.toString(),
                    priority
            );
        }
    }

    /**
     * Processes high churn risk event and creates notification
     */
    @Transactional
    public void processHighChurnRisk(UUID callId, JsonNode payload) {
        log.debug("Processing high churn risk for call: {}", callId);

        double churnRisk = payload.has("predictedChurnRisk") ?
                payload.get("predictedChurnRisk").asDouble() : 0.0;

        StringBuilder body = new StringBuilder();
        body.append("High customer churn risk detected for call: ").append(callId).append("\n\n");
        body.append("Predicted Churn Risk: ").append(String.format("%.1f%%", churnRisk * 100))
            .append("\n\n");

        if (payload.has("overallSentiment")) {
            body.append("Overall Sentiment: ").append(payload.get("overallSentiment").asText())
                .append("\n");
        }

        if (payload.has("customerSentiment")) {
            body.append("Customer Sentiment: ").append(payload.get("customerSentiment").asText())
                .append("\n");
        }

        body.append("\nAction Required: Review call and consider customer retention outreach.");

        NotificationPriority priority = alertRuleEngine.getPriority("SentimentAnalyzed", payload);
        List<String> recipients = alertRuleEngine.getRecipients("SentimentAnalyzed", payload);

        for (String recipient : recipients) {
            createNotification(
                    callId,
                    NotificationType.HIGH_CHURN_RISK,
                    recipient,
                    NotificationChannel.EMAIL,
                    "HIGH CHURN RISK - Call " + callId,
                    body.toString(),
                    priority
            );
        }
    }

    /**
     * Processes escalation event and creates notification
     */
    @Transactional
    public void processEscalation(UUID callId, JsonNode payload) {
        log.debug("Processing escalation for call: {}", callId);

        StringBuilder body = new StringBuilder();
        body.append("Call escalation detected for call: ").append(callId).append("\n\n");

        if (payload.has("escalationTimestamp")) {
            body.append("Escalation Time: ").append(payload.get("escalationTimestamp").asText())
                .append("\n");
        }

        if (payload.has("overallSentiment")) {
            body.append("Overall Sentiment: ").append(payload.get("overallSentiment").asText())
                .append("\n");
        }

        if (payload.has("emotionTrend")) {
            body.append("Emotion Trend: ").append(payload.get("emotionTrend").asText())
                .append("\n");
        }

        body.append("\nAction Required: Immediate supervisor review recommended.");

        NotificationPriority priority = NotificationPriority.URGENT;
        List<String> recipients = alertRuleEngine.getRecipients("SentimentAnalyzed", payload);

        for (String recipient : recipients) {
            createNotification(
                    callId,
                    NotificationType.ESCALATION,
                    recipient,
                    NotificationChannel.SLACK,  // Escalations go to Slack for immediate visibility
                    "ESCALATION DETECTED - Call " + callId,
                    body.toString(),
                    priority
            );
        }
    }

    /**
     * Gets all notifications for a specific call
     */
    public List<Notification> getNotificationsForCall(UUID callId) {
        return notificationRepository.findByCallId(callId);
    }

    /**
     * Gets a notification by ID
     */
    public Optional<Notification> getNotificationById(UUID id) {
        return notificationRepository.findById(id);
    }

    /**
     * Gets notifications by status
     */
    public List<Notification> getNotificationsByStatus(NotificationStatus status) {
        return notificationRepository.findByStatus(status);
    }

    /**
     * Retries a failed notification
     */
    @Transactional
    public void resendNotification(UUID notificationId) {
        Optional<Notification> optNotification = notificationRepository.findById(notificationId);
        if (optNotification.isPresent()) {
            Notification notification = optNotification.get();
            notification.setStatus(NotificationStatus.PENDING);
            notification.setErrorMessage(null);
            notificationRepository.save(notification);
            sendNotification(notification);
        } else {
            throw new IllegalArgumentException("Notification not found: " + notificationId);
        }
    }

    /**
     * Gets all notifications
     */
    public List<Notification> getAllNotifications() {
        return notificationRepository.findAll();
    }

    // Stub implementations for different channels

    private void sendEmail(Notification notification) {
        // Stub: In production, integrate with SMTP or email service provider
        log.info("[EMAIL] To: {}, Subject: {}, Body: {}",
                notification.getRecipient(),
                notification.getSubject(),
                notification.getBody().substring(0, Math.min(100, notification.getBody().length())));
    }

    private void sendSlack(Notification notification) {
        // Stub: In production, use Slack Web API or Incoming Webhooks
        log.info("[SLACK] To: {}, Message: {} - {}",
                notification.getRecipient(),
                notification.getSubject(),
                notification.getBody().substring(0, Math.min(100, notification.getBody().length())));
    }

    private void sendWebhook(Notification notification) {
        // Stub: In production, make HTTP POST to configured webhook URL
        log.info("[WEBHOOK] To: {}, Payload: {}",
                notification.getRecipient(),
                notification.getSubject());
    }
}
