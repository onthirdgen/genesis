package com.callaudit.analytics.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for call responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallDTO {

    private UUID id;
    private String callerId;
    private String agentId;
    private String channel;
    private Integer duration;
    private String status;
    private String fileFormat;
    private Long fileSizeBytes;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    // Transcription status enrichment
    private Boolean hasTranscription;
    private String transcriptionLanguage;
}
