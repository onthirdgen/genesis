package com.callaudit.analytics.domain.transcription;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * TranscriptionSegment entity - Speaker-separated segments within a transcription.
 * Part of the transcription bounded context within analytics-service.
 */
@Entity
@Table(name = "segments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptionSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transcription_id", nullable = false)
    private Transcription transcription;

    @Enumerated(EnumType.STRING)
    @Column(name = "speaker", nullable = false)
    private SpeakerType speaker;

    @Column(name = "start_time", nullable = false, precision = 10, scale = 3)
    private BigDecimal startTime;

    @Column(name = "end_time", nullable = false, precision = 10, scale = 3)
    private BigDecimal endTime;

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "word_count")
    private Integer wordCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /**
     * Speaker type enum matching PostgreSQL speaker_type enum
     */
    public enum SpeakerType {
        agent,
        customer,
        unknown
    }
}
