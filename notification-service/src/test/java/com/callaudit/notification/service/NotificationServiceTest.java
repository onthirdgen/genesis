package com.callaudit.notification.service;

import com.callaudit.notification.model.*;
import com.callaudit.notification.repository.NotificationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationService
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final UUID CALL_ID_123 = UUID.fromString("00000000-0000-0000-0000-000000000123");
    private static final UUID CALL_ID_456 = UUID.fromString("00000000-0000-0000-0000-000000000456");
    private static final UUID CALL_ID_789 = UUID.fromString("00000000-0000-0000-0000-000000000789");
    private static final UUID CALL_ID_999 = UUID.fromString("00000000-0000-0000-0000-000000000999");
    private static final UUID CALL_ID_ESCALATION = UUID.fromString("00000000-0000-0000-0000-000000001000");
    private static final UUID CALL_ID_ESC_2 = UUID.fromString("00000000-0000-0000-0000-000000001001");
    private static final UUID CALL_ID_MULTI = UUID.fromString("00000000-0000-0000-0000-000000001002");
    private static final UUID CALL_ID_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CALL_ID_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private AlertRuleEngine alertRuleEngine;

    @InjectMocks
    private NotificationService notificationService;

    @Captor
    private ArgumentCaptor<Notification> notificationCaptor;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void createNotification_ValidParameters_CreatesAndSavesNotification() {
        // Arrange
        Notification savedNotification = Notification.builder()
            .id(UUID.randomUUID())
            .callId(CALL_ID_123)
            .notificationType(NotificationType.COMPLIANCE_VIOLATION)
            .recipient("supervisor@company.com")
            .channel(NotificationChannel.EMAIL)
            .subject("COMPLIANCE VIOLATION")
            .body("Compliance issues detected")
            .priority(NotificationPriority.HIGH)
            .status(NotificationStatus.PENDING)
            .createdAt(Instant.now())
            .build();

        when(notificationRepository.save(any(Notification.class)))
            .thenReturn(savedNotification);

        // Act
        Notification result = notificationService.createNotification(
            CALL_ID_123,
            NotificationType.COMPLIANCE_VIOLATION,
            "supervisor@company.com",
            NotificationChannel.EMAIL,
            "COMPLIANCE VIOLATION",
            "Compliance issues detected",
            NotificationPriority.HIGH
        );

        // Assert
        assertNotNull(result);
        assertEquals(CALL_ID_123, result.getCallId());
        assertEquals(NotificationType.COMPLIANCE_VIOLATION, result.getNotificationType());
        assertEquals("supervisor@company.com", result.getRecipient());
        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    @Test
    void createNotification_SetsPendingStatus() {
        // Arrange
        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> {
                Notification arg = invocation.getArgument(0);
                // Clone the notification to capture its state at the time of save
                return Notification.builder()
                    .id(arg.getId())
                    .callId(arg.getCallId())
                    .notificationType(arg.getNotificationType())
                    .recipient(arg.getRecipient())
                    .channel(arg.getChannel())
                    .subject(arg.getSubject())
                    .body(arg.getBody())
                    .priority(arg.getPriority())
                    .status(arg.getStatus())
                    .sentAt(arg.getSentAt())
                    .errorMessage(arg.getErrorMessage())
                    .createdAt(arg.getCreatedAt())
                    .build();
            });

        // Act
        notificationService.createNotification(
            CALL_ID_123,
            NotificationType.HIGH_CHURN_RISK,
            "manager@company.com",
            NotificationChannel.SLACK,
            "HIGH CHURN RISK",
            "Churn risk detected",
            NotificationPriority.URGENT
        );

        // Assert - The notification is created with PENDING status, then immediately sent to SENT status
        // Verify that the first save has PENDING status, the second has SENT status
        verify(notificationRepository, atLeast(2)).save(notificationCaptor.capture());
        List<Notification> savedNotifications = notificationCaptor.getAllValues();
        // First save should be PENDING
        assertEquals(NotificationStatus.PENDING, savedNotifications.get(0).getStatus());
        // Second save should be SENT (after sendNotification is called)
        assertEquals(NotificationStatus.SENT, savedNotifications.get(1).getStatus());
    }

    @Test
    void sendNotification_EmailChannel_UpdatesStatusToSent() {
        // Arrange
        Notification notification = Notification.builder()
            .id(UUID.randomUUID())
            .callId(CALL_ID_123)
            .notificationType(NotificationType.COMPLIANCE_VIOLATION)
            .recipient("supervisor@company.com")
            .channel(NotificationChannel.EMAIL)
            .subject("Test")
            .body("Test body")
            .priority(NotificationPriority.HIGH)
            .status(NotificationStatus.PENDING)
            .createdAt(Instant.now())
            .build();

        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        notificationService.sendNotification(notification);

        // Assert
        verify(notificationRepository).save(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertEquals(NotificationStatus.SENT, saved.getStatus());
        assertNotNull(saved.getSentAt());
    }

    @Test
    void sendNotification_SlackChannel_UpdatesStatusToSent() {
        // Arrange
        Notification notification = Notification.builder()
            .id(UUID.randomUUID())
            .callId(CALL_ID_456)
            .notificationType(NotificationType.ESCALATION)
            .recipient("#escalations")
            .channel(NotificationChannel.SLACK)
            .subject("ESCALATION")
            .body("Escalation detected")
            .priority(NotificationPriority.URGENT)
            .status(NotificationStatus.PENDING)
            .createdAt(Instant.now())
            .build();

        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        notificationService.sendNotification(notification);

        // Assert
        verify(notificationRepository).save(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertEquals(NotificationStatus.SENT, saved.getStatus());
    }

    @Test
    void sendNotification_WebhookChannel_UpdatesStatusToSent() {
        // Arrange
        Notification notification = Notification.builder()
            .id(UUID.randomUUID())
            .callId(CALL_ID_789)
            .notificationType(NotificationType.REVIEW_REQUIRED)
            .recipient("https://webhook.example.com")
            .channel(NotificationChannel.WEBHOOK)
            .subject("Review Required")
            .body("Review needed")
            .priority(NotificationPriority.NORMAL)
            .status(NotificationStatus.PENDING)
            .createdAt(Instant.now())
            .build();

        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        notificationService.sendNotification(notification);

        // Assert
        verify(notificationRepository).save(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertEquals(NotificationStatus.SENT, saved.getStatus());
    }

    @Test
    void processComplianceViolation_WithViolations_CreatesNotifications() throws Exception {
        // Arrange
        UUID callId = CALL_ID_123;
        String payloadJson = """
            {
                "complianceScore": 0.45,
                "violations": [
                    {
                        "severity": "CRITICAL",
                        "rule": "TCPA_VIOLATION",
                        "description": "Failed to obtain consent"
                    },
                    {
                        "severity": "HIGH",
                        "rule": "SCRIPT_DEVIATION",
                        "description": "Major script deviation"
                    }
                ]
            }
            """;

        JsonNode payload = objectMapper.readTree(payloadJson);

        when(alertRuleEngine.getPriority("CallAudited", payload))
            .thenReturn(NotificationPriority.URGENT);
        when(alertRuleEngine.getRecipients("CallAudited", payload))
            .thenReturn(List.of("supervisor@company.com", "manager@company.com"));

        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        notificationService.processComplianceViolation(callId, payload);

        // Assert
        verify(notificationRepository, atLeast(4)).save(notificationCaptor.capture());
        List<Notification> notifications = notificationCaptor.getAllValues();

        Notification firstNotification = notifications.get(0);
        assertEquals(NotificationType.COMPLIANCE_VIOLATION, firstNotification.getNotificationType());
        assertTrue(firstNotification.getBody().contains("CRITICAL"));
        assertTrue(firstNotification.getBody().contains("TCPA_VIOLATION"));
    }

    @Test
    void processComplianceViolation_LowComplianceScore_IncludesScoreInBody() throws Exception {
        // Arrange
        UUID callId = CALL_ID_456;
        String payloadJson = """
            {
                "complianceScore": 0.35,
                "violations": []
            }
            """;

        JsonNode payload = objectMapper.readTree(payloadJson);

        when(alertRuleEngine.getPriority("CallAudited", payload))
            .thenReturn(NotificationPriority.HIGH);
        when(alertRuleEngine.getRecipients("CallAudited", payload))
            .thenReturn(List.of("supervisor@company.com"));

        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        notificationService.processComplianceViolation(callId, payload);

        // Assert
        verify(notificationRepository, atLeast(2)).save(notificationCaptor.capture());
        Notification notification = notificationCaptor.getValue();
        assertTrue(notification.getBody().contains("0.35"));
    }

    @Test
    void processHighChurnRisk_HighRiskScore_CreatesNotifications() throws Exception {
        // Arrange
        UUID callId = CALL_ID_789;
        String payloadJson = """
            {
                "predictedChurnRisk": 0.85,
                "overallSentiment": "NEGATIVE",
                "customerSentiment": "FRUSTRATED"
            }
            """;

        JsonNode payload = objectMapper.readTree(payloadJson);

        when(alertRuleEngine.getPriority("SentimentAnalyzed", payload))
            .thenReturn(NotificationPriority.HIGH);
        when(alertRuleEngine.getRecipients("SentimentAnalyzed", payload))
            .thenReturn(List.of("supervisor@company.com"));

        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        notificationService.processHighChurnRisk(callId, payload);

        // Assert
        verify(notificationRepository, atLeast(2)).save(notificationCaptor.capture());
        Notification notification = notificationCaptor.getValue();
        assertEquals(NotificationType.HIGH_CHURN_RISK, notification.getNotificationType());
        assertTrue(notification.getBody().contains("85.0%"));
        assertTrue(notification.getBody().contains("NEGATIVE"));
    }

    @Test
    void processHighChurnRisk_IncludesActionRequired() throws Exception {
        // Arrange
        UUID callId = CALL_ID_999;
        String payloadJson = """
            {
                "predictedChurnRisk": 0.9,
                "overallSentiment": "NEGATIVE"
            }
            """;

        JsonNode payload = objectMapper.readTree(payloadJson);

        when(alertRuleEngine.getPriority("SentimentAnalyzed", payload))
            .thenReturn(NotificationPriority.URGENT);
        when(alertRuleEngine.getRecipients("SentimentAnalyzed", payload))
            .thenReturn(List.of("manager@company.com"));

        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        notificationService.processHighChurnRisk(callId, payload);

        // Assert
        verify(notificationRepository, atLeast(2)).save(notificationCaptor.capture());
        Notification notification = notificationCaptor.getValue();
        assertTrue(notification.getBody().contains("Action Required"));
        assertTrue(notification.getBody().contains("retention"));
    }

    @Test
    void processEscalation_CreatesUrgentSlackNotification() throws Exception {
        // Arrange
        UUID callId = CALL_ID_ESCALATION;
        String payloadJson = """
            {
                "escalationTimestamp": "2025-12-31T10:00:00Z",
                "overallSentiment": "NEGATIVE",
                "emotionTrend": "INCREASING_FRUSTRATION"
            }
            """;

        JsonNode payload = objectMapper.readTree(payloadJson);

        when(alertRuleEngine.getRecipients("SentimentAnalyzed", payload))
            .thenReturn(List.of("supervisor@company.com"));

        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        notificationService.processEscalation(callId, payload);

        // Assert
        verify(notificationRepository, atLeast(2)).save(notificationCaptor.capture());
        Notification notification = notificationCaptor.getValue();
        assertEquals(NotificationType.ESCALATION, notification.getNotificationType());
        assertEquals(NotificationChannel.SLACK, notification.getChannel());
        assertEquals(NotificationPriority.URGENT, notification.getPriority());
        assertTrue(notification.getBody().contains("supervisor review"));
    }

    @Test
    void processEscalation_IncludesEscalationDetails() throws Exception {
        // Arrange
        UUID callId = CALL_ID_ESC_2;
        String payloadJson = """
            {
                "escalationTimestamp": "2025-12-31T15:30:00Z",
                "overallSentiment": "VERY_NEGATIVE",
                "emotionTrend": "ANGER"
            }
            """;

        JsonNode payload = objectMapper.readTree(payloadJson);

        when(alertRuleEngine.getRecipients("SentimentAnalyzed", payload))
            .thenReturn(List.of("supervisor@company.com", "manager@company.com"));

        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        notificationService.processEscalation(callId, payload);

        // Assert
        verify(notificationRepository, atLeast(4)).save(any(Notification.class));
        verify(notificationRepository, atLeast(2)).save(notificationCaptor.capture());
        Notification notification = notificationCaptor.getValue();
        assertTrue(notification.getBody().contains("2025-12-31T15:30:00Z"));
        assertTrue(notification.getBody().contains("VERY_NEGATIVE"));
        assertTrue(notification.getBody().contains("ANGER"));
    }

    @Test
    void getNotificationsForCall_ExistingCall_ReturnsNotifications() {
        // Arrange
        UUID callId = CALL_ID_123;
        List<Notification> notifications = List.of(
            Notification.builder()
                .id(UUID.randomUUID())
                .callId(callId)
                .notificationType(NotificationType.COMPLIANCE_VIOLATION)
                .recipient("supervisor@company.com")
                .channel(NotificationChannel.EMAIL)
                .subject("Test")
                .body("Test body")
                .priority(NotificationPriority.HIGH)
                .status(NotificationStatus.SENT)
                .createdAt(Instant.now())
                .build()
        );

        when(notificationRepository.findByCallId(callId)).thenReturn(notifications);

        // Act
        List<Notification> result = notificationService.getNotificationsForCall(callId);

        // Assert
        assertEquals(1, result.size());
        assertEquals(callId, result.get(0).getCallId());
        verify(notificationRepository).findByCallId(callId);
    }

    @Test
    void getNotificationById_ExistingId_ReturnsNotification() {
        // Arrange
        UUID id = UUID.randomUUID();
        Notification notification = Notification.builder()
            .id(id)
            .callId(CALL_ID_123)
            .notificationType(NotificationType.HIGH_CHURN_RISK)
            .recipient("manager@company.com")
            .channel(NotificationChannel.EMAIL)
            .subject("Test")
            .body("Test body")
            .priority(NotificationPriority.URGENT)
            .status(NotificationStatus.SENT)
            .createdAt(Instant.now())
            .build();

        when(notificationRepository.findById(id)).thenReturn(Optional.of(notification));

        // Act
        Optional<Notification> result = notificationService.getNotificationById(id);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
        verify(notificationRepository).findById(id);
    }

    @Test
    void getNotificationById_NonExistingId_ReturnsEmpty() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(notificationRepository.findById(id)).thenReturn(Optional.empty());

        // Act
        Optional<Notification> result = notificationService.getNotificationById(id);

        // Assert
        assertFalse(result.isPresent());
        verify(notificationRepository).findById(id);
    }

    @Test
    void getNotificationsByStatus_SentStatus_ReturnsMatchingNotifications() {
        // Arrange
        List<Notification> notifications = List.of(
            Notification.builder()
                .id(UUID.randomUUID())
                .callId(CALL_ID_1)
                .notificationType(NotificationType.COMPLIANCE_VIOLATION)
                .recipient("supervisor@company.com")
                .channel(NotificationChannel.EMAIL)
                .subject("Test")
                .body("Test body")
                .priority(NotificationPriority.HIGH)
                .status(NotificationStatus.SENT)
                .createdAt(Instant.now())
                .build()
        );

        when(notificationRepository.findByStatus(NotificationStatus.SENT))
            .thenReturn(notifications);

        // Act
        List<Notification> result = notificationService.getNotificationsByStatus(NotificationStatus.SENT);

        // Assert
        assertEquals(1, result.size());
        assertEquals(NotificationStatus.SENT, result.get(0).getStatus());
        verify(notificationRepository).findByStatus(NotificationStatus.SENT);
    }

    @Test
    void resendNotification_ExistingNotification_ResetsStatusAndResends() {
        // Arrange
        UUID id = UUID.randomUUID();
        Notification notification = Notification.builder()
            .id(id)
            .callId(CALL_ID_123)
            .notificationType(NotificationType.COMPLIANCE_VIOLATION)
            .recipient("supervisor@company.com")
            .channel(NotificationChannel.EMAIL)
            .subject("Test")
            .body("Test body")
            .priority(NotificationPriority.HIGH)
            .status(NotificationStatus.FAILED)
            .errorMessage("Previous error")
            .createdAt(Instant.now())
            .build();

        when(notificationRepository.findById(id)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        notificationService.resendNotification(id);

        // Assert
        verify(notificationRepository).findById(id);
        verify(notificationRepository, atLeast(2)).save(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertEquals(NotificationStatus.SENT, saved.getStatus());
        assertNull(saved.getErrorMessage());
    }

    @Test
    void resendNotification_NonExistingNotification_ThrowsException() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(notificationRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            notificationService.resendNotification(id);
        });

        verify(notificationRepository).findById(id);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void getAllNotifications_ReturnsAllNotifications() {
        // Arrange
        List<Notification> notifications = List.of(
            Notification.builder()
                .id(UUID.randomUUID())
                .callId(CALL_ID_1)
                .notificationType(NotificationType.COMPLIANCE_VIOLATION)
                .recipient("supervisor@company.com")
                .channel(NotificationChannel.EMAIL)
                .subject("Test 1")
                .body("Body 1")
                .priority(NotificationPriority.HIGH)
                .status(NotificationStatus.SENT)
                .createdAt(Instant.now())
                .build(),
            Notification.builder()
                .id(UUID.randomUUID())
                .callId(CALL_ID_2)
                .notificationType(NotificationType.HIGH_CHURN_RISK)
                .recipient("manager@company.com")
                .channel(NotificationChannel.SLACK)
                .subject("Test 2")
                .body("Body 2")
                .priority(NotificationPriority.URGENT)
                .status(NotificationStatus.PENDING)
                .createdAt(Instant.now())
                .build()
        );

        when(notificationRepository.findAll()).thenReturn(notifications);

        // Act
        List<Notification> result = notificationService.getAllNotifications();

        // Assert
        assertEquals(2, result.size());
        verify(notificationRepository).findAll();
    }

    @Test
    void processComplianceViolation_MultipleRecipients_CreatesMultipleNotifications() throws Exception {
        // Arrange
        UUID callId = CALL_ID_MULTI;
        String payloadJson = """
            {
                "complianceScore": 0.3,
                "violations": [
                    {
                        "severity": "CRITICAL",
                        "rule": "DATA_PRIVACY",
                        "description": "PII disclosure"
                    }
                ]
            }
            """;

        JsonNode payload = objectMapper.readTree(payloadJson);

        when(alertRuleEngine.getPriority("CallAudited", payload))
            .thenReturn(NotificationPriority.URGENT);
        when(alertRuleEngine.getRecipients("CallAudited", payload))
            .thenReturn(List.of("supervisor@company.com", "manager@company.com", "compliance@company.com"));

        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        notificationService.processComplianceViolation(callId, payload);

        // Assert
        verify(notificationRepository, atLeast(6)).save(any(Notification.class));
    }
}
