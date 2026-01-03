package com.callaudit.voc.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Event received when a call has been transcribed
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CallTranscribedEvent {

    private String eventId;
    private String eventType;
    private String aggregateId;  // callId
    private String aggregateType;
    private Instant timestamp;
    private Integer version;
    private String causationId;
    private String correlationId;
    private Map<String, String> metadata;
    private TranscriptionPayload payload;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TranscriptionPayload {
        private String callId;
        private String transcriptionText;
        private String language;
        private Double confidence;
        private Integer durationSeconds;
        private Map<String, Object> segments;
    }
}
