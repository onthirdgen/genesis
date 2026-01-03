package com.callaudit.notification.controller;

import com.callaudit.notification.model.*;
import com.callaudit.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for NotificationController using Spring Boot 4.0.0 compatible patterns
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationControllerTest {

    private static final UUID CALL_ID_123 = UUID.fromString("00000000-0000-0000-0000-000000000123");
    private static final UUID CALL_ID_456 = UUID.fromString("00000000-0000-0000-0000-000000000456");
    private static final UUID CALL_ID_789 = UUID.fromString("00000000-0000-0000-0000-000000000789");
    private static final UUID CALL_ID_999 = UUID.fromString("00000000-0000-0000-0000-000000000999");
    private static final UUID CALL_ID_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CALL_ID_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CALL_ID_3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID CALL_ID_NONEXISTENT = UUID.fromString("00000000-0000-0000-0000-000000001005");

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public NotificationService notificationService() {
            return mock(NotificationService.class);
        }
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private NotificationService notificationService;

    private MockMvc mockMvc;

    private Notification testNotification;
    private UUID testId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        reset(notificationService);

        testId = UUID.randomUUID();
        testNotification = Notification.builder()
            .id(testId)
            .callId(CALL_ID_123)
            .notificationType(NotificationType.COMPLIANCE_VIOLATION)
            .recipient("supervisor@company.com")
            .channel(NotificationChannel.EMAIL)
            .subject("COMPLIANCE VIOLATION - Call call-123")
            .body("Compliance issues detected")
            .priority(NotificationPriority.HIGH)
            .status(NotificationStatus.SENT)
            .sentAt(Instant.now())
            .createdAt(Instant.now())
            .build();
    }

    @Test
    void getAllNotifications_ReturnsAllNotifications() throws Exception {
        // Arrange
        Notification notification2 = Notification.builder()
            .id(UUID.randomUUID())
            .callId(CALL_ID_456)
            .notificationType(NotificationType.HIGH_CHURN_RISK)
            .recipient("manager@company.com")
            .channel(NotificationChannel.SLACK)
            .subject("HIGH CHURN RISK")
            .body("High churn risk detected")
            .priority(NotificationPriority.URGENT)
            .status(NotificationStatus.PENDING)
            .createdAt(Instant.now())
            .build();

        when(notificationService.getAllNotifications()).thenReturn(List.of(testNotification, notification2));

        // Act & Assert
        mockMvc.perform(get("/api/notifications"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].callId").value(CALL_ID_123.toString()))
            .andExpect(jsonPath("$[0].notificationType").value("COMPLIANCE_VIOLATION"))
            .andExpect(jsonPath("$[1].callId").value(CALL_ID_456.toString()))
            .andExpect(jsonPath("$[1].notificationType").value("HIGH_CHURN_RISK"));

        verify(notificationService).getAllNotifications();
    }

    @Test
    void getAllNotifications_EmptyList_ReturnsEmptyArray() throws Exception {
        // Arrange
        when(notificationService.getAllNotifications()).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/notifications"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));

        verify(notificationService).getAllNotifications();
    }

    @Test
    void getNotificationById_ExistingId_Returns200Ok() throws Exception {
        // Arrange
        when(notificationService.getNotificationById(testId)).thenReturn(Optional.of(testNotification));

        // Act & Assert
        mockMvc.perform(get("/api/notifications/{id}", testId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testId.toString()))
            .andExpect(jsonPath("$.callId").value(CALL_ID_123.toString()))
            .andExpect(jsonPath("$.status").value("SENT"));

        verify(notificationService).getNotificationById(testId);
    }

    @Test
    void getNotificationById_NonExistingId_Returns404NotFound() throws Exception {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();
        when(notificationService.getNotificationById(nonExistentId)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/notifications/{id}", nonExistentId))
            .andExpect(status().isNotFound());

        verify(notificationService).getNotificationById(nonExistentId);
    }

    @Test
    void getNotificationsByCallId_ExistingCall_ReturnsNotifications() throws Exception {
        // Arrange
        when(notificationService.getNotificationsForCall(CALL_ID_123))
            .thenReturn(List.of(testNotification));

        // Act & Assert
        mockMvc.perform(get("/api/notifications/call/{callId}", CALL_ID_123.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].callId").value(CALL_ID_123.toString()));

        verify(notificationService).getNotificationsForCall(CALL_ID_123);
    }

    @Test
    void getNotificationsByCallId_NonExistingCall_ReturnsEmptyArray() throws Exception {
        // Arrange
        when(notificationService.getNotificationsForCall(CALL_ID_NONEXISTENT)).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/notifications/call/{callId}", CALL_ID_NONEXISTENT.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));

        verify(notificationService).getNotificationsForCall(CALL_ID_NONEXISTENT);
    }

    @Test
    void getNotificationsByStatus_SentStatus_ReturnsMatchingNotifications() throws Exception {
        // Arrange
        when(notificationService.getNotificationsByStatus(NotificationStatus.SENT))
            .thenReturn(List.of(testNotification));

        // Act & Assert
        mockMvc.perform(get("/api/notifications/status/{status}", "SENT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].status").value("SENT"));

        verify(notificationService).getNotificationsByStatus(NotificationStatus.SENT);
    }

    @Test
    void getNotificationsByStatus_PendingStatus_ReturnsMatchingNotifications() throws Exception {
        // Arrange
        Notification pendingNotification = Notification.builder()
            .id(UUID.randomUUID())
            .callId(CALL_ID_789)
            .notificationType(NotificationType.ESCALATION)
            .recipient("supervisor@company.com")
            .channel(NotificationChannel.EMAIL)
            .subject("ESCALATION")
            .body("Escalation detected")
            .priority(NotificationPriority.URGENT)
            .status(NotificationStatus.PENDING)
            .createdAt(Instant.now())
            .build();

        when(notificationService.getNotificationsByStatus(NotificationStatus.PENDING))
            .thenReturn(List.of(pendingNotification));

        // Act & Assert
        mockMvc.perform(get("/api/notifications/status/{status}", "PENDING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].status").value("PENDING"));

        verify(notificationService).getNotificationsByStatus(NotificationStatus.PENDING);
    }

    @Test
    void getNotificationsByStatus_FailedStatus_ReturnsMatchingNotifications() throws Exception {
        // Arrange
        Notification failedNotification = Notification.builder()
            .id(UUID.randomUUID())
            .callId(CALL_ID_999)
            .notificationType(NotificationType.REVIEW_REQUIRED)
            .recipient("manager@company.com")
            .channel(NotificationChannel.WEBHOOK)
            .subject("REVIEW REQUIRED")
            .body("Review required")
            .priority(NotificationPriority.NORMAL)
            .status(NotificationStatus.FAILED)
            .errorMessage("Connection timeout")
            .createdAt(Instant.now())
            .build();

        when(notificationService.getNotificationsByStatus(NotificationStatus.FAILED))
            .thenReturn(List.of(failedNotification));

        // Act & Assert
        mockMvc.perform(get("/api/notifications/status/{status}", "FAILED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].status").value("FAILED"))
            .andExpect(jsonPath("$[0].errorMessage").value("Connection timeout"));

        verify(notificationService).getNotificationsByStatus(NotificationStatus.FAILED);
    }

    @Test
    void getNotificationsByStatus_NoMatches_ReturnsEmptyArray() throws Exception {
        // Arrange
        when(notificationService.getNotificationsByStatus(NotificationStatus.PENDING))
            .thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/notifications/status/{status}", "PENDING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));

        verify(notificationService).getNotificationsByStatus(NotificationStatus.PENDING);
    }

    @Test
    void resendNotification_ExistingNotification_Returns200Ok() throws Exception {
        // Arrange
        doNothing().when(notificationService).resendNotification(testId);

        // Act & Assert
        mockMvc.perform(post("/api/notifications/{id}/resend", testId))
            .andExpect(status().isOk())
            .andExpect(content().string("Notification resend initiated"));

        verify(notificationService).resendNotification(testId);
    }

    @Test
    void resendNotification_NonExistingNotification_Returns404NotFound() throws Exception {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Notification not found: " + nonExistentId))
            .when(notificationService).resendNotification(nonExistentId);

        // Act & Assert
        mockMvc.perform(post("/api/notifications/{id}/resend", nonExistentId))
            .andExpect(status().isNotFound());

        verify(notificationService).resendNotification(nonExistentId);
    }

    @Test
    void resendNotification_ServiceError_Returns500InternalServerError() throws Exception {
        // Arrange
        doThrow(new RuntimeException("Database error"))
            .when(notificationService).resendNotification(testId);

        // Act & Assert
        mockMvc.perform(post("/api/notifications/{id}/resend", testId))
            .andExpect(status().isInternalServerError())
            .andExpect(content().string(containsString("Error resending notification")));

        verify(notificationService).resendNotification(testId);
    }

    @Test
    void getNotificationsByCallId_MultipleNotifications_ReturnsAll() throws Exception {
        // Arrange
        Notification notification2 = Notification.builder()
            .id(UUID.randomUUID())
            .callId(CALL_ID_123)
            .notificationType(NotificationType.HIGH_CHURN_RISK)
            .recipient("manager@company.com")
            .channel(NotificationChannel.EMAIL)
            .subject("HIGH CHURN RISK")
            .body("Churn risk detected")
            .priority(NotificationPriority.HIGH)
            .status(NotificationStatus.SENT)
            .createdAt(Instant.now())
            .build();

        when(notificationService.getNotificationsForCall(CALL_ID_123))
            .thenReturn(List.of(testNotification, notification2));

        // Act & Assert
        mockMvc.perform(get("/api/notifications/call/{callId}", CALL_ID_123.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].notificationType").value("COMPLIANCE_VIOLATION"))
            .andExpect(jsonPath("$[1].notificationType").value("HIGH_CHURN_RISK"));

        verify(notificationService).getNotificationsForCall(CALL_ID_123);
    }

    @Test
    void getAllNotifications_VariousChannels_ReturnsAll() throws Exception {
        // Arrange
        Notification emailNotification = Notification.builder()
            .id(UUID.randomUUID())
            .callId(CALL_ID_1)
            .notificationType(NotificationType.COMPLIANCE_VIOLATION)
            .recipient("supervisor@company.com")
            .channel(NotificationChannel.EMAIL)
            .subject("Test Email")
            .body("Email body")
            .priority(NotificationPriority.HIGH)
            .status(NotificationStatus.SENT)
            .createdAt(Instant.now())
            .build();

        Notification slackNotification = Notification.builder()
            .id(UUID.randomUUID())
            .callId(CALL_ID_2)
            .notificationType(NotificationType.ESCALATION)
            .recipient("#escalations")
            .channel(NotificationChannel.SLACK)
            .subject("Test Slack")
            .body("Slack body")
            .priority(NotificationPriority.URGENT)
            .status(NotificationStatus.SENT)
            .createdAt(Instant.now())
            .build();

        Notification webhookNotification = Notification.builder()
            .id(UUID.randomUUID())
            .callId(CALL_ID_3)
            .notificationType(NotificationType.REVIEW_REQUIRED)
            .recipient("https://webhook.example.com")
            .channel(NotificationChannel.WEBHOOK)
            .subject("Test Webhook")
            .body("Webhook body")
            .priority(NotificationPriority.NORMAL)
            .status(NotificationStatus.SENT)
            .createdAt(Instant.now())
            .build();

        when(notificationService.getAllNotifications())
            .thenReturn(List.of(emailNotification, slackNotification, webhookNotification));

        // Act & Assert
        mockMvc.perform(get("/api/notifications"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[0].channel").value("EMAIL"))
            .andExpect(jsonPath("$[1].channel").value("SLACK"))
            .andExpect(jsonPath("$[2].channel").value("WEBHOOK"));

        verify(notificationService).getAllNotifications();
    }
}
