package com.callaudit.voc.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Event received when sentiment analysis has been completed
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SentimentAnalyzedEvent {

    private String eventId;
    private String eventType;
    private String aggregateId;  // callId
    private String aggregateType;
    private Instant timestamp;
    private Integer version;
    private String causationId;
    private String correlationId;
    private Map<String, String> metadata;
    private SentimentPayload payload;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SentimentPayload {
        private String callId;
        private String overallSentiment;  // POSITIVE, NEGATIVE, NEUTRAL
        private Double sentimentScore;     // -1.0 to 1.0
        private Double positiveScore;
        private Double negativeScore;
        private Double neutralScore;
        private Map<String, Object> emotions;
    }
}
