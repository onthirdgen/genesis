package com.callaudit.notification.repository;

import com.callaudit.notification.model.Notification;
import com.callaudit.notification.model.NotificationStatus;
import com.callaudit.notification.model.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Notification entities
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * Find all notifications for a specific call
     */
    List<Notification> findByCallId(UUID callId);

    /**
     * Find notifications by status
     */
    List<Notification> findByStatus(NotificationStatus status);

    /**
     * Find notifications by type
     */
    List<Notification> findByNotificationType(NotificationType notificationType);

    /**
     * Find notifications by call ID and type
     */
    List<Notification> findByCallIdAndNotificationType(UUID callId, NotificationType notificationType);
}
