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
public class CallTranscribedEvent extends BaseEvent {

    @JsonProperty("payload")
    private TranscriptionPayload payload;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TranscriptionPayload {
        @JsonProperty("callId")
        private UUID callId;

        @JsonProperty("fullText")
        private String fullText;

        @JsonProperty("language")
        private String language;

        @JsonProperty("confidence")
        private BigDecimal confidence;

        @JsonProperty("wordCount")
        private Integer wordCount;

        @JsonProperty("segments")
        private List<Segment> segments;

        @JsonProperty("processingTimeMs")
        private Integer processingTimeMs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Segment {
        @JsonProperty("speaker")
        private String speaker;

        @JsonProperty("startTime")
        private BigDecimal startTime;

        @JsonProperty("endTime")
        private BigDecimal endTime;

        @JsonProperty("text")
        private String text;

        @JsonProperty("confidence")
        private BigDecimal confidence;
    }
}
