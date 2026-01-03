package com.callaudit.audit.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class VocAnalyzedEvent extends BaseEvent {

    @JsonProperty("payload")
    private VocPayload payload;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VocPayload {
        @JsonProperty("callId")
        private UUID callId;

        @JsonProperty("primaryIntent")
        private String primaryIntent;

        @JsonProperty("topics")
        private List<String> topics;

        @JsonProperty("keywords")
        private List<String> keywords;

        @JsonProperty("customerSatisfaction")
        private String customerSatisfaction;

        @JsonProperty("predictedChurnRisk")
        private BigDecimal predictedChurnRisk;

        @JsonProperty("actionableItems")
        private List<Map<String, Object>> actionableItems;

        @JsonProperty("rootCause")
        private String rootCause;

        @JsonProperty("summary")
        private String summary;

        @JsonProperty("processingTimeMs")
        private Integer processingTimeMs;
    }
}
