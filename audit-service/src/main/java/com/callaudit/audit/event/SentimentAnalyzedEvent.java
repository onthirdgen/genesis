package com.callaudit.audit.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class SentimentAnalyzedEvent extends BaseEvent {

    @JsonProperty("payload")
    private SentimentPayload payload;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SentimentPayload {
        @JsonProperty("callId")
        private UUID callId;

        @JsonProperty("overallSentiment")
        private String overallSentiment;

        @JsonProperty("sentimentScore")
        private BigDecimal sentimentScore;

        @JsonProperty("escalationDetected")
        private Boolean escalationDetected;

        @JsonProperty("segmentSentiments")
        private List<SegmentSentiment> segmentSentiments;

        @JsonProperty("processingTimeMs")
        private Integer processingTimeMs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SegmentSentiment {
        @JsonProperty("startTime")
        private BigDecimal startTime;

        @JsonProperty("endTime")
        private BigDecimal endTime;

        @JsonProperty("sentiment")
        private String sentiment;

        @JsonProperty("score")
        private BigDecimal score;

        @JsonProperty("emotions")
        private List<String> emotions;
    }
}
