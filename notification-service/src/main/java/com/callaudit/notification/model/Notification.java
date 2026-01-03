package com.callaudit.notification.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Notification entity representing an alert or notification to be sent
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_call_id", columnList = "call_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_notification_type", columnList = "notification_type"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "call_id", nullable = false)
    private UUID callId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType;

    @Column(name = "recipient", nullable = false)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private NotificationChannel channel;

    @Column(name = "subject", nullable = false, length = 500)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private NotificationPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
