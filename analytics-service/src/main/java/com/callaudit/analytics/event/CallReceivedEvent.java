package com.callaudit.analytics.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallReceivedEvent {

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
        private String customerId;
        private String agentId;
        private String audioFileUrl;
        private String audioFormat;
        private Long fileSizeBytes;
        private Integer durationSeconds;
        private String phoneNumber;
        private String direction;
        private String callStartTime;
        private String uploadedAt;
        private Map<String, Object> metadata;
    }
}
