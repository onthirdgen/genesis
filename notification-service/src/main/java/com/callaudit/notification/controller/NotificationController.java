package com.callaudit.notification.controller;

import com.callaudit.notification.model.Notification;
import com.callaudit.notification.model.NotificationStatus;
import com.callaudit.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for notification management
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Get all notifications
     */
    @GetMapping
    public ResponseEntity<List<Notification>> getAllNotifications() {
        log.debug("Fetching all notifications");
        List<Notification> notifications = notificationService.getAllNotifications();
        return ResponseEntity.ok(notifications);
    }

    /**
     * Get notification by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Notification> getNotificationById(@PathVariable UUID id) {
        log.debug("Fetching notification: {}", id);
        Optional<Notification> notification = notificationService.getNotificationById(id);
        return notification.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get notifications for a specific call
     */
    @GetMapping("/call/{callId}")
    public ResponseEntity<List<Notification>> getNotificationsByCallId(@PathVariable UUID callId) {
        log.debug("Fetching notifications for call: {}", callId);
        List<Notification> notifications = notificationService.getNotificationsForCall(callId);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Get notifications by status
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<Notification>> getNotificationsByStatus(
            @PathVariable NotificationStatus status) {
        log.debug("Fetching notifications with status: {}", status);
        List<Notification> notifications = notificationService.getNotificationsByStatus(status);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Resend a failed notification
     */
    @PostMapping("/{id}/resend")
    public ResponseEntity<String> resendNotification(@PathVariable UUID id) {
        log.info("Resending notification: {}", id);
        try {
            notificationService.resendNotification(id);
            return ResponseEntity.ok("Notification resend initiated");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error resending notification", e);
            return ResponseEntity.internalServerError()
                    .body("Error resending notification: " + e.getMessage());
        }
    }
}
