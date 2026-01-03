package com.callaudit.notification.service;

import com.callaudit.notification.model.NotificationPriority;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Alert rule engine that determines when to send notifications
 * and calculates priority levels based on event data
 */
@Service
@Slf4j
public class AlertRuleEngine {

    @Value("${notification.thresholds.churnRiskThreshold:0.7}")
    private double churnRiskThreshold;

    @Value("${notification.thresholds.escalationEnabled:true}")
    private boolean escalationEnabled;

    /**
     * Determines if an event should trigger an alert
     */
    public boolean shouldAlert(String eventType, JsonNode payload) {
        return switch (eventType) {
            case "SentimentAnalyzed" -> shouldAlertOnSentiment(payload);
            case "VoCAAnalyzed" -> shouldAlertOnVoC(payload);
            case "CallAudited" -> shouldAlertOnAudit(payload);
            default -> false;
        };
    }

    /**
     * Determines priority level based on event type and severity
     */
    public NotificationPriority getPriority(String eventType, JsonNode payload) {
        return switch (eventType) {
            case "SentimentAnalyzed" -> getSentimentPriority(payload);
            case "VoCAAnalyzed" -> getVoCPriority(payload);
            case "CallAudited" -> getAuditPriority(payload);
            default -> NotificationPriority.NORMAL;
        };
    }

    /**
     * Returns list of recipients for the notification
     * Stub implementation - returns default supervisor email
     */
    public List<String> getRecipients(String eventType, JsonNode payload) {
        List<String> recipients = new ArrayList<>();
        // Stub implementation - in production, this would query a configuration
        // database or directory service to get appropriate recipients based on:
        // - Event type
        // - Severity level
        // - Agent involved
        // - Department/team
        // - Time of day (for escalation)
        recipients.add("supervisor@company.com");

        // For URGENT priority, add manager
        NotificationPriority priority = getPriority(eventType, payload);
        if (priority == NotificationPriority.URGENT) {
            recipients.add("manager@company.com");
        }

        return recipients;
    }

    private boolean shouldAlertOnSentiment(JsonNode payload) {
        if (!escalationEnabled) {
            return false;
        }

        // Alert on escalation detected
        if (payload.has("escalationDetected") && payload.get("escalationDetected").asBoolean()) {
            log.debug("Alert triggered: Escalation detected in sentiment analysis");
            return true;
        }

        // Alert on high churn risk
        if (payload.has("predictedChurnRisk")) {
            double churnRisk = payload.get("predictedChurnRisk").asDouble();
            if (churnRisk >= churnRiskThreshold) {
                log.debug("Alert triggered: High churn risk detected: {}", churnRisk);
                return true;
            }
        }

        return false;
    }

    private boolean shouldAlertOnVoC(JsonNode payload) {
        // Alert if VoC analysis flags issues for review
        if (payload.has("flagsForReview") && payload.get("flagsForReview").asBoolean()) {
            log.debug("Alert triggered: VoC analysis flagged for review");
            return true;
        }

        // Alert on critical themes
        if (payload.has("criticalThemes")) {
            JsonNode themes = payload.get("criticalThemes");
            if (themes.isArray() && themes.size() > 0) {
                log.debug("Alert triggered: Critical themes detected in VoC analysis");
                return true;
            }
        }

        return false;
    }

    private boolean shouldAlertOnAudit(JsonNode payload) {
        // Alert on compliance violations
        if (payload.has("complianceScore")) {
            double complianceScore = payload.get("complianceScore").asDouble();
            if (complianceScore < 0.6) {
                log.debug("Alert triggered: Low compliance score: {}", complianceScore);
                return true;
            }
        }

        // Alert on high/critical severity violations
        if (payload.has("violations")) {
            JsonNode violations = payload.get("violations");
            if (violations.isArray()) {
                for (JsonNode violation : violations) {
                    if (violation.has("severity")) {
                        String severity = violation.get("severity").asText();
                        if ("CRITICAL".equalsIgnoreCase(severity) || "HIGH".equalsIgnoreCase(severity)) {
                            log.debug("Alert triggered: Critical/High severity violation detected");
                            return true;
                        }
                    }
                }
            }
        }

        // Alert if flagged for review
        if (payload.has("flagsForReview") && payload.get("flagsForReview").asBoolean()) {
            log.debug("Alert triggered: Audit flagged for review");
            return true;
        }

        return false;
    }

    private NotificationPriority getSentimentPriority(JsonNode payload) {
        boolean escalation = payload.has("escalationDetected") &&
                           payload.get("escalationDetected").asBoolean();

        if (escalation) {
            return NotificationPriority.URGENT;
        }

        if (payload.has("predictedChurnRisk")) {
            double churnRisk = payload.get("predictedChurnRisk").asDouble();
            if (churnRisk >= 0.9) {
                return NotificationPriority.URGENT;
            } else if (churnRisk >= 0.8) {
                return NotificationPriority.HIGH;
            }
        }

        return NotificationPriority.NORMAL;
    }

    private NotificationPriority getVoCPriority(JsonNode payload) {
        if (payload.has("criticalThemes")) {
            JsonNode themes = payload.get("criticalThemes");
            if (themes.isArray() && themes.size() > 2) {
                return NotificationPriority.HIGH;
            }
        }

        return NotificationPriority.NORMAL;
    }

    private NotificationPriority getAuditPriority(JsonNode payload) {
        if (payload.has("violations")) {
            JsonNode violations = payload.get("violations");
            if (violations.isArray()) {
                for (JsonNode violation : violations) {
                    if (violation.has("severity")) {
                        String severity = violation.get("severity").asText();
                        if ("CRITICAL".equalsIgnoreCase(severity)) {
                            return NotificationPriority.URGENT;
                        }
                    }
                }
            }
        }

        if (payload.has("complianceScore")) {
            double complianceScore = payload.get("complianceScore").asDouble();
            if (complianceScore < 0.4) {
                return NotificationPriority.URGENT;
            } else if (complianceScore < 0.6) {
                return NotificationPriority.HIGH;
            }
        }

        return NotificationPriority.NORMAL;
    }
}
