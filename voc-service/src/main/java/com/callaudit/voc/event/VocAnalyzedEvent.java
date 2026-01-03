package com.callaudit.voc.event;

import com.callaudit.voc.model.Intent;
import com.callaudit.voc.model.Satisfaction;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Event published when VoC analysis has been completed
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VocAnalyzedEvent {

    private String eventId;
    private String eventType;
    private String aggregateId;  // callId
    private String aggregateType;
    private Instant timestamp;
    private Integer version;
    private String causationId;
    private String correlationId;
    private Map<String, String> metadata;
    private VocPayload payload;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VocPayload {
        private String callId;
        private Intent primaryIntent;
        private List<String> topics;
        private List<String> keywords;
        private Satisfaction customerSatisfaction;
        private List<String> actionableItems;
        private Double predictedChurnRisk;
        private String summary;
    }

    /**
     * Factory method to create a VocAnalyzedEvent
     */
    public static VocAnalyzedEvent create(String callId, VocPayload payload, String correlationId) {
        return VocAnalyzedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("VocAnalyzed")
                .aggregateId(callId)
                .aggregateType("Call")
                .timestamp(Instant.now())
                .version(1)
                .correlationId(correlationId)
                .metadata(Map.of(
                        "service", "voc-service",
                        "userId", "system"
                ))
                .payload(payload)
                .build();
    }
}
