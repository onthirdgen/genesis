package com.callaudit.notification.service;

import com.callaudit.notification.model.NotificationPriority;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AlertRuleEngine
 */
@ExtendWith(MockitoExtension.class)
class AlertRuleEngineTest {

    private AlertRuleEngine alertRuleEngine;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        alertRuleEngine = new AlertRuleEngine();
        objectMapper = new ObjectMapper();

        // Set configuration values using reflection
        ReflectionTestUtils.setField(alertRuleEngine, "churnRiskThreshold", 0.7);
        ReflectionTestUtils.setField(alertRuleEngine, "escalationEnabled", true);
    }

    // Sentiment Analysis Alert Tests

    @Test
    void shouldAlert_SentimentWithEscalation_ReturnsTrue() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "escalationDetected": true,
                "overallSentiment": "NEGATIVE",
                "predictedChurnRisk": 0.5
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        boolean result = alertRuleEngine.shouldAlert("SentimentAnalyzed", payload);

        // Assert
        assertTrue(result);
    }

    @Test
    void shouldAlert_SentimentWithHighChurnRisk_ReturnsTrue() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "escalationDetected": false,
                "predictedChurnRisk": 0.85
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        boolean result = alertRuleEngine.shouldAlert("SentimentAnalyzed", payload);

        // Assert
        assertTrue(result);
    }

    @Test
    void shouldAlert_SentimentAtThreshold_ReturnsTrue() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "predictedChurnRisk": 0.7
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        boolean result = alertRuleEngine.shouldAlert("SentimentAnalyzed", payload);

        // Assert
        assertTrue(result);
    }

    @Test
    void shouldAlert_SentimentBelowThreshold_ReturnsFalse() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "escalationDetected": false,
                "predictedChurnRisk": 0.5
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        boolean result = alertRuleEngine.shouldAlert("SentimentAnalyzed", payload);

        // Assert
        assertFalse(result);
    }

    @Test
    void shouldAlert_SentimentWithEscalationDisabled_IgnoresEscalation() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(alertRuleEngine, "escalationEnabled", false);

        String payloadJson = """
            {
                "escalationDetected": true,
                "predictedChurnRisk": 0.5
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        boolean result = alertRuleEngine.shouldAlert("SentimentAnalyzed", payload);

        // Assert
        assertFalse(result);
    }

    // VoC Analysis Alert Tests

    @Test
    void shouldAlert_VocFlaggedForReview_ReturnsTrue() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "flagsForReview": true,
                "criticalThemes": []
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        boolean result = alertRuleEngine.shouldAlert("VoCAAnalyzed", payload);

        // Assert
        assertTrue(result);
    }

    @Test
    void shouldAlert_VocWithCriticalThemes_ReturnsTrue() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "flagsForReview": false,
                "criticalThemes": ["Cancellation", "Billing Dispute"]
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        boolean result = alertRuleEngine.shouldAlert("VoCAAnalyzed", payload);

        // Assert
        assertTrue(result);
    }

    @Test
    void shouldAlert_VocNoCriticalIssues_ReturnsFalse() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "flagsForReview": false,
                "criticalThemes": []
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        boolean result = alertRuleEngine.shouldAlert("VoCAAnalyzed", payload);

        // Assert
        assertFalse(result);
    }

    // Audit Alert Tests

    @Test
    void shouldAlert_AuditLowComplianceScore_ReturnsTrue() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "complianceScore": 0.45,
                "violations": []
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        boolean result = alertRuleEngine.shouldAlert("CallAudited", payload);

        // Assert
        assertTrue(result);
    }

    @Test
    void shouldAlert_AuditCriticalViolation_ReturnsTrue() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "complianceScore": 0.8,
                "violations": [
                    {
                        "severity": "CRITICAL",
                        "rule": "DATA_PRIVACY"
                    }
                ]
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        boolean result = alertRuleEngine.shouldAlert("CallAudited", payload);

        // Assert
        assertTrue(result);
    }

    @Test
    void shouldAlert_AuditHighViolation_ReturnsTrue() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "complianceScore": 0.75,
                "violations": [
                    {
                        "severity": "HIGH",
                        "rule": "SCRIPT_DEVIATION"
                    }
                ]
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        boolean result = alertRuleEngine.shouldAlert("CallAudited", payload);

        // Assert
        assertTrue(result);
    }

    @Test
    void shouldAlert_AuditFlaggedForReview_ReturnsTrue() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "complianceScore": 0.7,
                "flagsForReview": true,
                "violations": []
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        boolean result = alertRuleEngine.shouldAlert("CallAudited", payload);

        // Assert
        assertTrue(result);
    }

    @Test
    void shouldAlert_AuditGoodCompliance_ReturnsFalse() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "complianceScore": 0.95,
                "violations": [
                    {
                        "severity": "LOW",
                        "rule": "MINOR_ISSUE"
                    }
                ],
                "flagsForReview": false
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        boolean result = alertRuleEngine.shouldAlert("CallAudited", payload);

        // Assert
        assertFalse(result);
    }

    @Test
    void shouldAlert_UnknownEventType_ReturnsFalse() throws Exception {
        // Arrange
        String payloadJson = "{}";
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        boolean result = alertRuleEngine.shouldAlert("UnknownEvent", payload);

        // Assert
        assertFalse(result);
    }

    // Priority Tests - Sentiment

    @Test
    void getPriority_SentimentWithEscalation_ReturnsUrgent() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "escalationDetected": true,
                "predictedChurnRisk": 0.5
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        NotificationPriority priority = alertRuleEngine.getPriority("SentimentAnalyzed", payload);

        // Assert
        assertEquals(NotificationPriority.URGENT, priority);
    }

    @Test
    void getPriority_SentimentVeryHighChurnRisk_ReturnsUrgent() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "escalationDetected": false,
                "predictedChurnRisk": 0.95
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        NotificationPriority priority = alertRuleEngine.getPriority("SentimentAnalyzed", payload);

        // Assert
        assertEquals(NotificationPriority.URGENT, priority);
    }

    @Test
    void getPriority_SentimentHighChurnRisk_ReturnsHigh() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "predictedChurnRisk": 0.85
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        NotificationPriority priority = alertRuleEngine.getPriority("SentimentAnalyzed", payload);

        // Assert
        assertEquals(NotificationPriority.HIGH, priority);
    }

    @Test
    void getPriority_SentimentMediumChurnRisk_ReturnsNormal() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "predictedChurnRisk": 0.5
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        NotificationPriority priority = alertRuleEngine.getPriority("SentimentAnalyzed", payload);

        // Assert
        assertEquals(NotificationPriority.NORMAL, priority);
    }

    // Priority Tests - VoC

    @Test
    void getPriority_VocManyCriticalThemes_ReturnsHigh() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "criticalThemes": ["Theme1", "Theme2", "Theme3"]
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        NotificationPriority priority = alertRuleEngine.getPriority("VoCAAnalyzed", payload);

        // Assert
        assertEquals(NotificationPriority.HIGH, priority);
    }

    @Test
    void getPriority_VocFewCriticalThemes_ReturnsNormal() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "criticalThemes": ["Theme1"]
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        NotificationPriority priority = alertRuleEngine.getPriority("VoCAAnalyzed", payload);

        // Assert
        assertEquals(NotificationPriority.NORMAL, priority);
    }

    @Test
    void getPriority_VocNoCriticalThemes_ReturnsNormal() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "criticalThemes": []
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        NotificationPriority priority = alertRuleEngine.getPriority("VoCAAnalyzed", payload);

        // Assert
        assertEquals(NotificationPriority.NORMAL, priority);
    }

    // Priority Tests - Audit

    @Test
    void getPriority_AuditCriticalViolation_ReturnsUrgent() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "complianceScore": 0.5,
                "violations": [
                    {
                        "severity": "CRITICAL",
                        "rule": "TCPA_VIOLATION"
                    }
                ]
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        NotificationPriority priority = alertRuleEngine.getPriority("CallAudited", payload);

        // Assert
        assertEquals(NotificationPriority.URGENT, priority);
    }

    @Test
    void getPriority_AuditVeryLowComplianceScore_ReturnsUrgent() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "complianceScore": 0.3,
                "violations": []
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        NotificationPriority priority = alertRuleEngine.getPriority("CallAudited", payload);

        // Assert
        assertEquals(NotificationPriority.URGENT, priority);
    }

    @Test
    void getPriority_AuditLowComplianceScore_ReturnsHigh() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "complianceScore": 0.55,
                "violations": []
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        NotificationPriority priority = alertRuleEngine.getPriority("CallAudited", payload);

        // Assert
        assertEquals(NotificationPriority.HIGH, priority);
    }

    @Test
    void getPriority_AuditGoodCompliance_ReturnsNormal() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "complianceScore": 0.85,
                "violations": []
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        NotificationPriority priority = alertRuleEngine.getPriority("CallAudited", payload);

        // Assert
        assertEquals(NotificationPriority.NORMAL, priority);
    }

    @Test
    void getPriority_UnknownEventType_ReturnsNormal() throws Exception {
        // Arrange
        String payloadJson = "{}";
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        NotificationPriority priority = alertRuleEngine.getPriority("UnknownEvent", payload);

        // Assert
        assertEquals(NotificationPriority.NORMAL, priority);
    }

    // Recipients Tests

    @Test
    void getRecipients_NormalPriority_ReturnsSupervisor() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "complianceScore": 0.8
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        List<String> recipients = alertRuleEngine.getRecipients("CallAudited", payload);

        // Assert
        assertThat(recipients).containsExactly("supervisor@company.com");
    }

    @Test
    void getRecipients_UrgentPriority_IncludesManager() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "complianceScore": 0.3,
                "violations": [
                    {
                        "severity": "CRITICAL",
                        "rule": "DATA_PRIVACY"
                    }
                ]
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        List<String> recipients = alertRuleEngine.getRecipients("CallAudited", payload);

        // Assert
        assertThat(recipients).contains("supervisor@company.com", "manager@company.com");
    }

    @Test
    void getRecipients_HighPriorityChurnRisk_IncludesManager() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "predictedChurnRisk": 0.95
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        List<String> recipients = alertRuleEngine.getRecipients("SentimentAnalyzed", payload);

        // Assert
        assertThat(recipients).contains("supervisor@company.com", "manager@company.com");
    }

    @Test
    void getRecipients_EscalationDetected_IncludesManager() throws Exception {
        // Arrange
        String payloadJson = """
            {
                "escalationDetected": true
            }
            """;
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        List<String> recipients = alertRuleEngine.getRecipients("SentimentAnalyzed", payload);

        // Assert
        assertThat(recipients).contains("supervisor@company.com", "manager@company.com");
    }

    @Test
    void getRecipients_AlwaysIncludesSupervisor() throws Exception {
        // Arrange
        String payloadJson = "{}";
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Act
        List<String> recipients = alertRuleEngine.getRecipients("SentimentAnalyzed", payload);

        // Assert
        assertThat(recipients).contains("supervisor@company.com");
    }
}
