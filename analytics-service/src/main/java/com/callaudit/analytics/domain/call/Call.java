package com.callaudit.analytics.domain.call;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Call entity - Read model for call metadata.
 * Part of the call bounded context within analytics-service.
 *
 * Data is populated from CallReceived Kafka events.
 */
@Entity
@Table(name = "calls")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Call {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "caller_id", nullable = false)
    private String callerId;

    @Column(name = "agent_id", nullable = false)
    private String agentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private Channel channel;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "duration")
    private Integer duration;

    @Column(name = "audio_file_url", nullable = false, columnDefinition = "TEXT")
    private String audioFileUrl;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "file_format", length = 20)
    private String fileFormat;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (correlationId == null) {
            correlationId = UUID.randomUUID();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Call channel enum.
     */
    public enum Channel {
        INBOUND,
        OUTBOUND,
        INTERNAL,
        PHONE,
        EMAIL,
        CHAT
    }

    /**
     * Call status enum.
     */
    public enum Status {
        PENDING,
        TRANSCRIBING,
        ANALYZING,
        COMPLETED,
        FAILED
    }
}
