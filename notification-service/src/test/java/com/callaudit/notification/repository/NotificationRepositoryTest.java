package com.callaudit.notification.repository;

import com.callaudit.notification.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for NotificationRepository using Spring Boot 4.0.0 compatible patterns
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationRepositoryTest {

    private static final UUID CALL_ID_123 = UUID.fromString("00000000-0000-0000-0000-000000000123");
    private static final UUID CALL_ID_456 = UUID.fromString("00000000-0000-0000-0000-000000000456");
    private static final UUID CALL_ID_789 = UUID.fromString("00000000-0000-0000-0000-000000000789");
    private static final UUID CALL_ID_MULTI = UUID.fromString("00000000-0000-0000-0000-000000001002");
    private static final UUID CALL_ID_OTHER = UUID.fromString("00000000-0000-0000-0000-000000001003");
    private static final UUID CALL_ID_SPECIFIC = UUID.fromString("00000000-0000-0000-0000-000000001004");
    private static final UUID CALL_ID_NONEXISTENT = UUID.fromString("00000000-0000-0000-0000-000000001005");
    private static final UUID CALL_ID_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CALL_ID_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CALL_ID_3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID CALL_ID_4 = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID CALL_ID_FAILED = UUID.fromString("00000000-0000-0000-0000-000000001006");
    private static final UUID CALL_ID_ERROR = UUID.fromString("00000000-0000-0000-0000-000000001007");
    private static final UUID CALL_ID_SENT = UUID.fromString("00000000-0000-0000-0000-000000001008");
    private static final UUID CALL_ID_EMAIL = UUID.fromString("00000000-0000-0000-0000-000000001009");
    private static final UUID CALL_ID_SLACK = UUID.fromString("00000000-0000-0000-0000-000000001010");
    private static final UUID CALL_ID_WEBHOOK = UUID.fromString("00000000-0000-0000-0000-000000001011");

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
    }

    @Test
    void save_NewNotification_GeneratesId() {
        // Arrange
        Notification notification = Notification.builder()
            .callId(CALL_ID_123)
            .notificationType(NotificationType.COMPLIANCE_VIOLATION)
            .recipient("supervisor@company.com")
            .channel(NotificationChannel.EMAIL)
            .subject("COMPLIANCE VIOLATION")
            .body("Compliance issues detected")
            .priority(NotificationPriority.HIGH)
            .status(NotificationStatus.PENDING)
            .build();

        // Act
        Notification saved = notificationRepository.save(notification);

        // Assert
        assertNotNull(saved.getId());
        assertEquals(CALL_ID_123, saved.getCallId());
        // Note: @CreationTimestamp works differently in H2, so we don't assert on createdAt in tests
    }

    @Test
    void save_UpdateExisting_PreservesId() {
        // Arrange
        Notification notification = createAndSaveNotification(CALL_ID_456, NotificationStatus.PENDING);
        UUID originalId = notification.getId();

        // Act
        notification.setStatus(NotificationStatus.SENT);
        notification.setSentAt(Instant.now());
        Notification updated = notificationRepository.save(notification);

        // Assert
        assertEquals(originalId, updated.getId());
        assertEquals(NotificationStatus.SENT, updated.getStatus());
        assertNotNull(updated.getSentAt());
    }

    @Test
    void findById_ExistingNotification_ReturnsNotification() {
        // Arrange
        Notification notification = createAndSaveNotification(CALL_ID_789, NotificationStatus.SENT);

        // Act
        Optional<Notification> found = notificationRepository.findById(notification.getId());

        // Assert
        assertTrue(found.isPresent());
        assertEquals(notification.getId(), found.get().getId());
        assertEquals(CALL_ID_789, found.get().getCallId());
    }

    @Test
    void findById_NonExistingNotification_ReturnsEmpty() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();

        // Act
        Optional<Notification> found = notificationRepository.findById(nonExistentId);

        // Assert
        assertFalse(found.isPresent());
    }

    @Test
    void findByCallId_ExistingCall_ReturnsNotifications() {
        // Arrange
        UUID callId = CALL_ID_MULTI;
        createAndSaveNotification(callId, NotificationStatus.SENT);
        createAndSaveNotification(callId, NotificationStatus.PENDING);
        createAndSaveNotification(CALL_ID_OTHER, NotificationStatus.SENT);

        // Act
        List<Notification> notifications = notificationRepository.findByCallId(callId);

        // Assert
        assertEquals(2, notifications.size());
        assertTrue(notifications.stream().allMatch(n -> n.getCallId().equals(callId)));
    }

    @Test
    void findByCallId_NonExistingCall_ReturnsEmptyList() {
        // Arrange
        createAndSaveNotification(CALL_ID_123, NotificationStatus.SENT);

        // Act
        List<Notification> notifications = notificationRepository.findByCallId(CALL_ID_NONEXISTENT);

        // Assert
        assertTrue(notifications.isEmpty());
    }

    @Test
    void findByStatus_SentStatus_ReturnsMatchingNotifications() {
        // Arrange
        createAndSaveNotification(CALL_ID_1, NotificationStatus.SENT);
        createAndSaveNotification(CALL_ID_2, NotificationStatus.SENT);
        createAndSaveNotification(CALL_ID_3, NotificationStatus.PENDING);
        createAndSaveNotification(CALL_ID_4, NotificationStatus.FAILED);

        // Act
        List<Notification> notifications = notificationRepository.findByStatus(NotificationStatus.SENT);

        // Assert
        assertEquals(2, notifications.size());
        assertTrue(notifications.stream().allMatch(n -> n.getStatus() == NotificationStatus.SENT));
    }

    @Test
    void findByStatus_PendingStatus_ReturnsMatchingNotifications() {
        // Arrange
        createAndSaveNotification(CALL_ID_1, NotificationStatus.PENDING);
        createAndSaveNotification(CALL_ID_2, NotificationStatus.SENT);

        // Act
        List<Notification> notifications = notificationRepository.findByStatus(NotificationStatus.PENDING);

        // Assert
        assertEquals(1, notifications.size());
        assertEquals(NotificationStatus.PENDING, notifications.get(0).getStatus());
    }

    @Test
    void findByStatus_FailedStatus_ReturnsMatchingNotifications() {
        // Arrange
        Notification failedNotification = Notification.builder()
            .callId(CALL_ID_FAILED)
            .notificationType(NotificationType.ESCALATION)
            .recipient("supervisor@company.com")
            .channel(NotificationChannel.EMAIL)
            .subject("Test")
            .body("Test body")
            .priority(NotificationPriority.URGENT)
            .status(NotificationStatus.FAILED)
            .errorMessage("Connection timeout")
            .build();
        notificationRepository.save(failedNotification);

        // Act
        List<Notification> notifications = notificationRepository.findByStatus(NotificationStatus.FAILED);

        // Assert
        assertEquals(1, notifications.size());
        assertEquals(NotificationStatus.FAILED, notifications.get(0).getStatus());
        assertEquals("Connection timeout", notifications.get(0).getErrorMessage());
    }

    @Test
    void findByStatus_NoMatches_ReturnsEmptyList() {
        // Arrange
        createAndSaveNotification(CALL_ID_1, NotificationStatus.SENT);

        // Act
        List<Notification> notifications = notificationRepository.findByStatus(NotificationStatus.PENDING);

        // Assert
        assertTrue(notifications.isEmpty());
    }

    @Test
    void findByNotificationType_ComplianceViolation_ReturnsMatchingNotifications() {
        // Arrange
        createNotificationWithType(CALL_ID_1, NotificationType.COMPLIANCE_VIOLATION);
        createNotificationWithType(CALL_ID_2, NotificationType.COMPLIANCE_VIOLATION);
        createNotificationWithType(CALL_ID_3, NotificationType.HIGH_CHURN_RISK);

        // Act
        List<Notification> notifications = notificationRepository
            .findByNotificationType(NotificationType.COMPLIANCE_VIOLATION);

        // Assert
        assertEquals(2, notifications.size());
        assertTrue(notifications.stream()
            .allMatch(n -> n.getNotificationType() == NotificationType.COMPLIANCE_VIOLATION));
    }

    @Test
    void findByNotificationType_HighChurnRisk_ReturnsMatchingNotifications() {
        // Arrange
        createNotificationWithType(CALL_ID_1, NotificationType.HIGH_CHURN_RISK);
        createNotificationWithType(CALL_ID_2, NotificationType.ESCALATION);

        // Act
        List<Notification> notifications = notificationRepository
            .findByNotificationType(NotificationType.HIGH_CHURN_RISK);

        // Assert
        assertEquals(1, notifications.size());
        assertEquals(NotificationType.HIGH_CHURN_RISK, notifications.get(0).getNotificationType());
    }

    @Test
    void findByNotificationType_Escalation_ReturnsMatchingNotifications() {
        // Arrange
        createNotificationWithType(CALL_ID_1, NotificationType.ESCALATION);
        createNotificationWithType(CALL_ID_2, NotificationType.REVIEW_REQUIRED);

        // Act
        List<Notification> notifications = notificationRepository
            .findByNotificationType(NotificationType.ESCALATION);

        // Assert
        assertEquals(1, notifications.size());
        assertEquals(NotificationType.ESCALATION, notifications.get(0).getNotificationType());
    }

    @Test
    void findByNotificationType_ReviewRequired_ReturnsMatchingNotifications() {
        // Arrange
        createNotificationWithType(CALL_ID_1, NotificationType.REVIEW_REQUIRED);

        // Act
        List<Notification> notifications = notificationRepository
            .findByNotificationType(NotificationType.REVIEW_REQUIRED);

        // Assert
        assertEquals(1, notifications.size());
        assertEquals(NotificationType.REVIEW_REQUIRED, notifications.get(0).getNotificationType());
    }

    @Test
    void findByCallIdAndNotificationType_ExistingMatch_ReturnsNotifications() {
        // Arrange
        UUID callId = CALL_ID_SPECIFIC;
        createNotificationWithType(callId, NotificationType.COMPLIANCE_VIOLATION);
        createNotificationWithType(callId, NotificationType.HIGH_CHURN_RISK);
        createNotificationWithType(CALL_ID_OTHER, NotificationType.COMPLIANCE_VIOLATION);

        // Act
        List<Notification> notifications = notificationRepository
            .findByCallIdAndNotificationType(callId, NotificationType.COMPLIANCE_VIOLATION);

        // Assert
        assertEquals(1, notifications.size());
        assertEquals(callId, notifications.get(0).getCallId());
        assertEquals(NotificationType.COMPLIANCE_VIOLATION, notifications.get(0).getNotificationType());
    }

    @Test
    void findByCallIdAndNotificationType_NoMatch_ReturnsEmptyList() {
        // Arrange
        createNotificationWithType(CALL_ID_1, NotificationType.COMPLIANCE_VIOLATION);

        // Act
        List<Notification> notifications = notificationRepository
            .findByCallIdAndNotificationType(CALL_ID_1, NotificationType.ESCALATION);

        // Assert
        assertTrue(notifications.isEmpty());
    }

    @Test
    void deleteAll_RemovesAllNotifications() {
        // Arrange
        createAndSaveNotification(CALL_ID_1, NotificationStatus.SENT);
        createAndSaveNotification(CALL_ID_2, NotificationStatus.PENDING);
        createAndSaveNotification(CALL_ID_3, NotificationStatus.FAILED);

        // Act
        notificationRepository.deleteAll();
        List<Notification> remaining = notificationRepository.findAll();

        // Assert
        assertTrue(remaining.isEmpty());
    }

    @Test
    void save_DifferentChannels_PersistsCorrectly() {
        // Arrange & Act
        Notification emailNotification = createNotificationWithChannel(
            CALL_ID_EMAIL, NotificationChannel.EMAIL);
        Notification slackNotification = createNotificationWithChannel(
            CALL_ID_SLACK, NotificationChannel.SLACK);
        Notification webhookNotification = createNotificationWithChannel(
            CALL_ID_WEBHOOK, NotificationChannel.WEBHOOK);

        // Assert
        List<Notification> all = notificationRepository.findAll();
        assertEquals(3, all.size());
        assertThat(all).extracting(Notification::getChannel)
            .containsExactlyInAnyOrder(
                NotificationChannel.EMAIL,
                NotificationChannel.SLACK,
                NotificationChannel.WEBHOOK
            );
    }

    @Test
    void save_DifferentPriorities_PersistsCorrectly() {
        // Arrange & Act
        Notification urgentNotification = createNotificationWithPriority(
            CALL_ID_1, NotificationPriority.URGENT);
        Notification highNotification = createNotificationWithPriority(
            CALL_ID_2, NotificationPriority.HIGH);
        Notification normalNotification = createNotificationWithPriority(
            CALL_ID_3, NotificationPriority.NORMAL);

        // Assert
        List<Notification> all = notificationRepository.findAll();
        assertEquals(3, all.size());
        assertThat(all).extracting(Notification::getPriority)
            .containsExactlyInAnyOrder(
                NotificationPriority.URGENT,
                NotificationPriority.HIGH,
                NotificationPriority.NORMAL
            );
    }

    @Test
    void save_WithErrorMessage_PersistsCorrectly() {
        // Arrange
        Notification notification = Notification.builder()
            .callId(CALL_ID_ERROR)
            .notificationType(NotificationType.ESCALATION)
            .recipient("supervisor@company.com")
            .channel(NotificationChannel.EMAIL)
            .subject("Test")
            .body("Test body")
            .priority(NotificationPriority.HIGH)
            .status(NotificationStatus.FAILED)
            .errorMessage("SMTP connection failed")
            .build();

        // Act
        Notification saved = notificationRepository.save(notification);

        // Assert
        Optional<Notification> found = notificationRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("SMTP connection failed", found.get().getErrorMessage());
    }

    @Test
    void save_WithSentAt_PersistsCorrectly() {
        // Arrange
        Instant sentTime = Instant.now();
        Notification notification = Notification.builder()
            .callId(CALL_ID_SENT)
            .notificationType(NotificationType.COMPLIANCE_VIOLATION)
            .recipient("supervisor@company.com")
            .channel(NotificationChannel.EMAIL)
            .subject("Test")
            .body("Test body")
            .priority(NotificationPriority.HIGH)
            .status(NotificationStatus.SENT)
            .sentAt(sentTime)
            .build();

        // Act
        Notification saved = notificationRepository.save(notification);

        // Assert
        Optional<Notification> found = notificationRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertNotNull(found.get().getSentAt());
    }

    @Test
    void findAll_MultipleNotifications_ReturnsAll() {
        // Arrange
        createAndSaveNotification(CALL_ID_1, NotificationStatus.SENT);
        createAndSaveNotification(CALL_ID_2, NotificationStatus.PENDING);
        createAndSaveNotification(CALL_ID_3, NotificationStatus.FAILED);

        // Act
        List<Notification> all = notificationRepository.findAll();

        // Assert
        assertEquals(3, all.size());
    }

    @Test
    void findAll_EmptyRepository_ReturnsEmptyList() {
        // Act
        List<Notification> all = notificationRepository.findAll();

        // Assert
        assertTrue(all.isEmpty());
    }

    // Helper methods

    private Notification createAndSaveNotification(UUID callId, NotificationStatus status) {
        Notification notification = Notification.builder()
            .callId(callId)
            .notificationType(NotificationType.COMPLIANCE_VIOLATION)
            .recipient("supervisor@company.com")
            .channel(NotificationChannel.EMAIL)
            .subject("Test Notification")
            .body("Test body")
            .priority(NotificationPriority.HIGH)
            .status(status)
            .build();

        if (status == NotificationStatus.SENT) {
            notification.setSentAt(Instant.now());
        } else if (status == NotificationStatus.FAILED) {
            notification.setErrorMessage("Test error");
        }

        return notificationRepository.save(notification);
    }

    private Notification createNotificationWithType(UUID callId, NotificationType type) {
        Notification notification = Notification.builder()
            .callId(callId)
            .notificationType(type)
            .recipient("supervisor@company.com")
            .channel(NotificationChannel.EMAIL)
            .subject("Test Notification")
            .body("Test body")
            .priority(NotificationPriority.HIGH)
            .status(NotificationStatus.SENT)
            .build();

        return notificationRepository.save(notification);
    }

    private Notification createNotificationWithChannel(UUID callId, NotificationChannel channel) {
        Notification notification = Notification.builder()
            .callId(callId)
            .notificationType(NotificationType.COMPLIANCE_VIOLATION)
            .recipient(channel == NotificationChannel.SLACK ? "#channel" : "user@company.com")
            .channel(channel)
            .subject("Test Notification")
            .body("Test body")
            .priority(NotificationPriority.HIGH)
            .status(NotificationStatus.SENT)
            .build();

        return notificationRepository.save(notification);
    }

    private Notification createNotificationWithPriority(UUID callId, NotificationPriority priority) {
        Notification notification = Notification.builder()
            .callId(callId)
            .notificationType(NotificationType.COMPLIANCE_VIOLATION)
            .recipient("supervisor@company.com")
            .channel(NotificationChannel.EMAIL)
            .subject("Test Notification")
            .body("Test body")
            .priority(priority)
            .status(NotificationStatus.SENT)
            .build();

        return notificationRepository.save(notification);
    }
}
