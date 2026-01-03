package com.callaudit.notification.listener;

import com.callaudit.notification.service.AlertRuleEngine;
import com.callaudit.notification.service.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationEventListener
 */
@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private AlertRuleEngine alertRuleEngine;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private NotificationEventListener eventListener;

    @BeforeEach
    void setUp() {
        reset(notificationService, alertRuleEngine);
    }

    // Sentiment Analyzed Event Tests

    @Test
    void handleSentimentAnalyzed_EscalationDetected_ProcessesEscalation() throws Exception {
        // Arrange
        String eventJson = """
            {
                "eventId": "event-123",
                "eventType": "SentimentAnalyzed",
                "aggregateId": "00000000-0000-0000-0000-000000000123",
                "payload": {
                    "escalationDetected": true,
                    "predictedChurnRisk": 0.5,
                    "overallSentiment": "NEGATIVE"
                }
            }
            """;

        when(alertRuleEngine.shouldAlert(eq("SentimentAnalyzed"), any(JsonNode.class)))
            .thenReturn(true);

        // Act
        eventListener.handleSentimentAnalyzed(eventJson);

        // Assert
        verify(alertRuleEngine).shouldAlert(eq("SentimentAnalyzed"), any(JsonNode.class));
        verify(notificationService).processEscalation(any(UUID.class), any(JsonNode.class));
    }

    @Test
    void handleSentimentAnalyzed_HighChurnRisk_ProcessesChurnRisk() throws Exception {
        // Arrange
        String eventJson = """
            {
                "eventId": "event-456",
                "eventType": "SentimentAnalyzed",
                "aggregateId": "00000000-0000-0000-0000-000000000456",
                "payload": {
                    "escalationDetected": false,
                    "predictedChurnRisk": 0.85,
                    "overallSentiment": "NEGATIVE"
                }
            }
            """;

        when(alertRuleEngine.shouldAlert(eq("SentimentAnalyzed"), any(JsonNode.class)))
            .thenReturn(true);

        // Act
        eventListener.handleSentimentAnalyzed(eventJson);

        // Assert
        verify(alertRuleEngine).shouldAlert(eq("SentimentAnalyzed"), any(JsonNode.class));
        verify(notificationService).processHighChurnRisk(any(UUID.class), any(JsonNode.class));
    }

    @Test
    void handleSentimentAnalyzed_BothEscalationAndChurnRisk_ProcessesBoth() throws Exception {
        // Arrange
        String eventJson = """
            {
                "eventId": "event-789",
                "eventType": "SentimentAnalyzed",
                "aggregateId": "00000000-0000-0000-0000-000000000789",
                "payload": {
                    "escalationDetected": true,
                    "predictedChurnRisk": 0.9,
                    "overallSentiment": "NEGATIVE"
                }
            }
            """;

        when(alertRuleEngine.shouldAlert(eq("SentimentAnalyzed"), any(JsonNode.class)))
            .thenReturn(true);

        // Act
        eventListener.handleSentimentAnalyzed(eventJson);

        // Assert
        verify(alertRuleEngine).shouldAlert(eq("SentimentAnalyzed"), any(JsonNode.class));
        verify(notificationService).processEscalation(any(UUID.class), any(JsonNode.class));
        verify(notificationService).processHighChurnRisk(any(UUID.class), any(JsonNode.class));
    }

    @Test
    void handleSentimentAnalyzed_NoAlertTriggered_DoesNotProcess() throws Exception {
        // Arrange
        String eventJson = """
            {
                "eventId": "event-999",
                "eventType": "SentimentAnalyzed",
                "aggregateId": "00000000-0000-0000-0000-000000000999",
                "payload": {
                    "escalationDetected": false,
                    "predictedChurnRisk": 0.3,
                    "overallSentiment": "POSITIVE"
                }
            }
            """;

        when(alertRuleEngine.shouldAlert(eq("SentimentAnalyzed"), any(JsonNode.class)))
            .thenReturn(false);

        // Act
        eventListener.handleSentimentAnalyzed(eventJson);

        // Assert
        verify(alertRuleEngine).shouldAlert(eq("SentimentAnalyzed"), any(JsonNode.class));
        verifyNoInteractions(notificationService);
    }

    @Test
    void handleSentimentAnalyzed_InvalidJson_DoesNotThrowException() {
        // Arrange
        String invalidJson = "invalid json";

        // Act & Assert
        assertDoesNotThrow(() -> eventListener.handleSentimentAnalyzed(invalidJson));
        verifyNoInteractions(notificationService);
    }

    @Test
    void handleSentimentAnalyzed_ChurnRiskAtThreshold_ProcessesChurnRisk() throws Exception {
        // Arrange
        String eventJson = """
            {
                "eventId": "event-threshold",
                "eventType": "SentimentAnalyzed",
                "aggregateId": "00000000-0000-0000-0000-000000001000",
                "payload": {
                    "predictedChurnRisk": 0.7,
                    "overallSentiment": "NEGATIVE"
                }
            }
            """;

        when(alertRuleEngine.shouldAlert(eq("SentimentAnalyzed"), any(JsonNode.class)))
            .thenReturn(true);

        // Act
        eventListener.handleSentimentAnalyzed(eventJson);

        // Assert
        verify(notificationService).processHighChurnRisk(any(UUID.class), any(JsonNode.class));
    }

    @Test
    void handleSentimentAnalyzed_ChurnRiskBelowThreshold_DoesNotProcessChurnRisk() throws Exception {
        // Arrange
        String eventJson = """
            {
                "eventId": "event-below",
                "eventType": "SentimentAnalyzed",
                "aggregateId": "00000000-0000-0000-0000-000000001001",
                "payload": {
                    "predictedChurnRisk": 0.5,
                    "escalationDetected": true,
                    "overallSentiment": "NEGATIVE"
                }
            }
            """;

        when(alertRuleEngine.shouldAlert(eq("SentimentAnalyzed"), any(JsonNode.class)))
            .thenReturn(true);

        // Act
        eventListener.handleSentimentAnalyzed(eventJson);

        // Assert
        verify(notificationService).processEscalation(any(UUID.class), any(JsonNode.class));
        verify(notificationService, never()).processHighChurnRisk(any(), any());
    }

    // VoC Analyzed Event Tests

    @Test
    void handleVoCAnalyzed_FlaggedForReview_ProcessesReview() throws Exception {
        // Arrange
        String eventJson = """
            {
                "eventId": "event-voc-1",
                "eventType": "VoCAAnalyzed",
                "aggregateId": "00000000-0000-0000-0000-000000002001",
                "payload": {
                    "flagsForReview": true,
                    "criticalThemes": ["Cancellation", "Billing Dispute"],
                    "keyPhrases": ["cancel subscription", "overcharged"]
                }
            }
            """;

        when(alertRuleEngine.shouldAlert(eq("VoCAAnalyzed"), any(JsonNode.class)))
            .thenReturn(true);
        when(alertRuleEngine.getPriority(eq("VoCAAnalyzed"), any(JsonNode.class)))
            .thenReturn(com.callaudit.notification.model.NotificationPriority.HIGH);
        when(alertRuleEngine.getRecipients(eq("VoCAAnalyzed"), any(JsonNode.class)))
            .thenReturn(java.util.List.of("supervisor@company.com"));

        // Act
        eventListener.handleVoCAnalyzed(eventJson);

        // Assert
        verify(alertRuleEngine).shouldAlert(eq("VoCAAnalyzed"), any(JsonNode.class));
        verify(notificationService).createNotification(
            any(UUID.class),
            eq(com.callaudit.notification.model.NotificationType.REVIEW_REQUIRED),
            eq("supervisor@company.com"),
            eq(com.callaudit.notification.model.NotificationChannel.EMAIL),
            any(String.class),
            any(String.class),
            eq(com.callaudit.notification.model.NotificationPriority.HIGH)
        );
    }

    @Test
    void handleVoCAnalyzed_NotFlaggedForReview_DoesNotProcess() throws Exception {
        // Arrange
        String eventJson = """
            {
                "eventId": "event-voc-2",
                "eventType": "VoCAAnalyzed",
                "aggregateId": "00000000-0000-0000-0000-000000002002",
                "payload": {
                    "flagsForReview": false,
                    "criticalThemes": []
                }
            }
            """;

        when(alertRuleEngine.shouldAlert(eq("VoCAAnalyzed"), any(JsonNode.class)))
            .thenReturn(false);

        // Act
        eventListener.handleVoCAnalyzed(eventJson);

        // Assert
        verify(alertRuleEngine).shouldAlert(eq("VoCAAnalyzed"), any(JsonNode.class));
        verifyNoInteractions(notificationService);
    }

    @Test
    void handleVoCAnalyzed_WithCriticalThemes_IncludesThemesInNotification() throws Exception {
        // Arrange
        String eventJson = """
            {
                "eventId": "event-voc-3",
                "eventType": "VoCAAnalyzed",
                "aggregateId": "00000000-0000-0000-0000-000000002003",
                "payload": {
                    "flagsForReview": true,
                    "criticalThemes": ["Cancellation", "Poor Service", "Pricing Issue"]
                }
            }
            """;

        when(alertRuleEngine.shouldAlert(eq("VoCAAnalyzed"), any(JsonNode.class)))
            .thenReturn(true);
        when(alertRuleEngine.getPriority(eq("VoCAAnalyzed"), any(JsonNode.class)))
            .thenReturn(com.callaudit.notification.model.NotificationPriority.NORMAL);
        when(alertRuleEngine.getRecipients(eq("VoCAAnalyzed"), any(JsonNode.class)))
            .thenReturn(java.util.List.of("supervisor@company.com"));

        // Act
        eventListener.handleVoCAnalyzed(eventJson);

        // Assert
        verify(notificationService).createNotification(
            any(UUID.class),
            any(),
            any(),
            any(),
            any(),
            contains("Cancellation"),
            any()
        );
    }

    @Test
    void handleVoCAnalyzed_InvalidJson_DoesNotThrowException() {
        // Arrange
        String invalidJson = "invalid json";

        // Act & Assert
        assertDoesNotThrow(() -> eventListener.handleVoCAnalyzed(invalidJson));
        verifyNoInteractions(notificationService);
    }

    // Call Audited Event Tests

    @Test
    void handleCallAudited_WithViolations_ProcessesComplianceViolation() throws Exception {
        // Arrange
        String eventJson = """
            {
                "eventId": "event-audit-1",
                "eventType": "CallAudited",
                "aggregateId": "00000000-0000-0000-0000-000000003001",
                "payload": {
                    "complianceScore": 0.45,
                    "violations": [
                        {
                            "severity": "CRITICAL",
                            "rule": "TCPA_VIOLATION",
                            "description": "Failed to obtain consent"
                        }
                    ]
                }
            }
            """;

        when(alertRuleEngine.shouldAlert(eq("CallAudited"), any(JsonNode.class)))
            .thenReturn(true);

        // Act
        eventListener.handleCallAudited(eventJson);

        // Assert
        verify(alertRuleEngine).shouldAlert(eq("CallAudited"), any(JsonNode.class));
        verify(notificationService).processComplianceViolation(any(UUID.class), any(JsonNode.class));
    }

    @Test
    void handleCallAudited_FlaggedForReview_ProcessesReview() throws Exception {
        // Arrange
        String eventJson = """
            {
                "eventId": "event-audit-2",
                "eventType": "CallAudited",
                "aggregateId": "00000000-0000-0000-0000-000000003002",
                "payload": {
                    "complianceScore": 0.65,
                    "violations": [],
                    "flagsForReview": true,
                    "reviewReason": "Unusual pattern detected"
                }
            }
            """;

        when(alertRuleEngine.shouldAlert(eq("CallAudited"), any(JsonNode.class)))
            .thenReturn(true);
        when(alertRuleEngine.getPriority(eq("CallAudited"), any(JsonNode.class)))
            .thenReturn(com.callaudit.notification.model.NotificationPriority.NORMAL);
        when(alertRuleEngine.getRecipients(eq("CallAudited"), any(JsonNode.class)))
            .thenReturn(java.util.List.of("supervisor@company.com"));

        // Act
        eventListener.handleCallAudited(eventJson);

        // Assert
        verify(notificationService).createNotification(
            any(UUID.class),
            eq(com.callaudit.notification.model.NotificationType.REVIEW_REQUIRED),
            eq("supervisor@company.com"),
            eq(com.callaudit.notification.model.NotificationChannel.EMAIL),
            any(String.class),
            any(String.class),
            eq(com.callaudit.notification.model.NotificationPriority.NORMAL)
        );
    }

    @Test
    void handleCallAudited_BothViolationsAndReview_ProcessesBoth() throws Exception {
        // Arrange
        String eventJson = """
            {
                "eventId": "event-audit-3",
                "eventType": "CallAudited",
                "aggregateId": "00000000-0000-0000-0000-000000003003",
                "payload": {
                    "complianceScore": 0.35,
                    "violations": [
                        {
                            "severity": "HIGH",
                            "rule": "SCRIPT_DEVIATION"
                        }
                    ],
                    "flagsForReview": true
                }
            }
            """;

        when(alertRuleEngine.shouldAlert(eq("CallAudited"), any(JsonNode.class)))
            .thenReturn(true);
        when(alertRuleEngine.getPriority(eq("CallAudited"), any(JsonNode.class)))
            .thenReturn(com.callaudit.notification.model.NotificationPriority.URGENT);
        when(alertRuleEngine.getRecipients(eq("CallAudited"), any(JsonNode.class)))
            .thenReturn(java.util.List.of("supervisor@company.com", "manager@company.com"));

        // Act
        eventListener.handleCallAudited(eventJson);

        // Assert
        verify(notificationService).processComplianceViolation(any(UUID.class), any(JsonNode.class));
        verify(notificationService, times(2)).createNotification(
            any(UUID.class),
            eq(com.callaudit.notification.model.NotificationType.REVIEW_REQUIRED),
            any(),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void handleCallAudited_NoViolationsNoReview_DoesNotProcess() throws Exception {
        // Arrange
        String eventJson = """
            {
                "eventId": "event-audit-4",
                "eventType": "CallAudited",
                "aggregateId": "00000000-0000-0000-0000-000000003004",
                "payload": {
                    "complianceScore": 0.95,
                    "violations": [],
                    "flagsForReview": false
                }
            }
            """;

        when(alertRuleEngine.shouldAlert(eq("CallAudited"), any(JsonNode.class)))
            .thenReturn(false);

        // Act
        eventListener.handleCallAudited(eventJson);

        // Assert
        verify(alertRuleEngine).shouldAlert(eq("CallAudited"), any(JsonNode.class));
        verifyNoInteractions(notificationService);
    }

    @Test
    void handleCallAudited_EmptyViolationsArray_DoesNotProcessViolations() throws Exception {
        // Arrange
        String eventJson = """
            {
                "eventId": "event-audit-5",
                "eventType": "CallAudited",
                "aggregateId": "00000000-0000-0000-0000-000000003005",
                "payload": {
                    "complianceScore": 0.55,
                    "violations": [],
                    "flagsForReview": false
                }
            }
            """;

        when(alertRuleEngine.shouldAlert(eq("CallAudited"), any(JsonNode.class)))
            .thenReturn(true);

        // Act
        eventListener.handleCallAudited(eventJson);

        // Assert
        verify(alertRuleEngine).shouldAlert(eq("CallAudited"), any(JsonNode.class));
        verify(notificationService, never()).processComplianceViolation(any(), any());
    }

    @Test
    void handleCallAudited_InvalidJson_DoesNotThrowException() {
        // Arrange
        String invalidJson = "invalid json";

        // Act & Assert
        assertDoesNotThrow(() -> eventListener.handleCallAudited(invalidJson));
        verifyNoInteractions(notificationService);
    }

    // Extract Call ID Tests

    @Test
    void extractCallId_FromAggregateId_ReturnsCallId() throws Exception {
        // Arrange
        String eventJson = """
            {
                "aggregateId": "00000000-0000-0000-0000-000000004001",
                "eventType": "SentimentAnalyzed",
                "payload": {}
            }
            """;

        when(alertRuleEngine.shouldAlert(any(), any())).thenReturn(false);

        // Act
        eventListener.handleSentimentAnalyzed(eventJson);

        // Assert - No exception thrown, event processed correctly
        verify(alertRuleEngine).shouldAlert(eq("SentimentAnalyzed"), any(JsonNode.class));
    }

    @Test
    void extractCallId_FromPayload_ReturnsCallId() throws Exception {
        // Arrange
        String eventJson = """
            {
                "eventType": "SentimentAnalyzed",
                "payload": {
                    "callId": "00000000-0000-0000-0000-000000004002"
                }
            }
            """;

        when(alertRuleEngine.shouldAlert(any(), any())).thenReturn(false);

        // Act
        eventListener.handleSentimentAnalyzed(eventJson);

        // Assert - No exception thrown, event processed correctly
        verify(alertRuleEngine).shouldAlert(eq("SentimentAnalyzed"), any(JsonNode.class));
    }

    @Test
    void handleSentimentAnalyzed_ServiceThrowsException_DoesNotThrowException() throws Exception {
        // Arrange
        String eventJson = """
            {
                "aggregateId": "00000000-0000-0000-0000-000000005001",
                "eventType": "SentimentAnalyzed",
                "payload": {
                    "escalationDetected": true
                }
            }
            """;

        when(alertRuleEngine.shouldAlert(eq("SentimentAnalyzed"), any(JsonNode.class)))
            .thenReturn(true);
        doThrow(new RuntimeException("Database error"))
            .when(notificationService).processEscalation(any(), any());

        // Act & Assert
        assertDoesNotThrow(() -> eventListener.handleSentimentAnalyzed(eventJson));
        verify(notificationService).processEscalation(any(UUID.class), any(JsonNode.class));
    }

    @Test
    void handleVoCAnalyzed_ServiceThrowsException_DoesNotThrowException() throws Exception {
        // Arrange
        String eventJson = """
            {
                "aggregateId": "00000000-0000-0000-0000-000000005002",
                "eventType": "VoCAAnalyzed",
                "payload": {
                    "flagsForReview": true
                }
            }
            """;

        when(alertRuleEngine.shouldAlert(eq("VoCAAnalyzed"), any(JsonNode.class)))
            .thenReturn(true);
        when(alertRuleEngine.getPriority(any(), any()))
            .thenReturn(com.callaudit.notification.model.NotificationPriority.NORMAL);
        when(alertRuleEngine.getRecipients(any(), any()))
            .thenReturn(java.util.List.of("supervisor@company.com"));
        doThrow(new RuntimeException("Notification error"))
            .when(notificationService).createNotification(any(), any(), any(), any(), any(), any(), any());

        // Act & Assert
        assertDoesNotThrow(() -> eventListener.handleVoCAnalyzed(eventJson));
    }

    @Test
    void handleCallAudited_ServiceThrowsException_DoesNotThrowException() throws Exception {
        // Arrange
        String eventJson = """
            {
                "aggregateId": "00000000-0000-0000-0000-000000005003",
                "eventType": "CallAudited",
                "payload": {
                    "violations": [
                        {"severity": "CRITICAL", "rule": "TEST"}
                    ]
                }
            }
            """;

        when(alertRuleEngine.shouldAlert(eq("CallAudited"), any(JsonNode.class)))
            .thenReturn(true);
        doThrow(new RuntimeException("Processing error"))
            .when(notificationService).processComplianceViolation(any(), any());

        // Act & Assert
        assertDoesNotThrow(() -> eventListener.handleCallAudited(eventJson));
        verify(notificationService).processComplianceViolation(any(UUID.class), any(JsonNode.class));
    }
}
