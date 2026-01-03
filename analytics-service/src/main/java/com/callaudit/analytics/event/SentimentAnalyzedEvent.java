package com.callaudit.analytics.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentAnalyzedEvent {

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
        private String overallSentiment;
        private Double sentimentScore;
        private List<SegmentSentiment> segmentSentiments;
        private EmotionAnalysis emotionAnalysis;
        private Double customerSatisfactionScore;
        private String analyzedAt;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SegmentSentiment {
            private Integer segmentId;
            private String speaker;
            private String sentiment;
            private Double score;
            private String text;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class EmotionAnalysis {
            private Double joy;
            private Double sadness;
            private Double anger;
            private Double fear;
            private Double surprise;
        }
    }
}
