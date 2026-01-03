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
public class VocAnalyzedEvent {

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
        private List<String> topics;
        private List<String> keywords;
        private List<Issue> issues;
        private List<PainPoint> painPoints;
        private ChurnRisk churnRisk;
        private List<Insight> insights;
        private String analyzedAt;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Issue {
            private String category;
            private String description;
            private String severity;
            private Boolean resolved;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class PainPoint {
            private String description;
            private String category;
            private Double impactScore;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ChurnRisk {
            private String level;
            private Double score;
            private List<String> indicators;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Insight {
            private String type;
            private String description;
            private Double confidence;
        }
    }
}
