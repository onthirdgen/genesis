package com.callaudit.analytics.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for transcription responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptionDTO {

    private UUID id;
    private UUID callId;
    private String fullText;
    private String language;
    private BigDecimal confidence;
    private Integer wordCount;
    private Integer processingTimeMs;
    private String modelVersion;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    private List<SegmentDTO> segments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SegmentDTO {
        private UUID id;
        private String speaker;
        private BigDecimal startTime;
        private BigDecimal endTime;
        private String text;
        private BigDecimal confidence;
        private Integer wordCount;
    }
}
