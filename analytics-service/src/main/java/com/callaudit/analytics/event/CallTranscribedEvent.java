package com.callaudit.analytics.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Event class for CallTranscribed events from transcription-service.
 *
 * Schema matches transcription-service output format:
 * - payload.callId: the call identifier
 * - payload.transcription: nested object containing transcription data
 *   - fullText: complete transcription text
 *   - segments: list of speaker-separated segments
 *   - language: detected language code (e.g., "en")
 *   - confidence: overall confidence score (0.0-1.0)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallTranscribedEvent {

    private String eventId;
    private String eventType;
    private String aggregateId;
    private String aggregateType;
    private String timestamp;
    private Integer version;
    private String causationId;
    private String correlationId;
    private Map<String, Object> metadata;
    private Payload payload;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Payload {
        private String callId;
        private Transcription transcription;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Transcription {
        private String fullText;
        private List<Segment> segments;
        private String language;
        private Double confidence;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Segment {
        private String speaker;
        private String text;
        private Double startTime;
        private Double endTime;
    }
}
