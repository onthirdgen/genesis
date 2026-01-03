package com.callaudit.analytics.domain.transcription;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Transcription entity - Read model for transcribed calls.
 * Part of the transcription bounded context within analytics-service.
 *
 * Data is populated from CallTranscribed Kafka events.
 */
@Entity
@Table(name = "transcriptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transcription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "call_id", nullable = false, unique = true)
    private UUID callId;

    @Column(name = "full_text", nullable = false, columnDefinition = "TEXT")
    private String fullText;

    @Column(name = "language", nullable = false, length = 10)
    private String language;

    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "word_count")
    private Integer wordCount;

    @Column(name = "processing_time_ms")
    private Integer processingTimeMs;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "transcription", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startTime ASC")
    @Builder.Default
    private List<TranscriptionSegment> segments = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /**
     * Helper method to add a segment to this transcription
     */
    public void addSegment(TranscriptionSegment segment) {
        segments.add(segment);
        segment.setTranscription(this);
    }
}
