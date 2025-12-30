package com.callaudit.ingestion.event;

import com.callaudit.ingestion.model.CallChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallReceivedEvent {

    private UUID eventId;
    private String eventType;
    private UUID aggregateId; // callId
    private String aggregateType;
    private Instant timestamp;
    private Integer version;
    private UUID causationId;
    private UUID correlationId;
    private Map<String, Object> metadata;
    private Payload payload;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Payload {
        private UUID callId;
        private String callerId;
        private String agentId;
        private CallChannel channel;
        private Instant startTime;
        private String audioFileUrl;
        private String audioFormat;
        private Long audioFileSize; // in bytes
    }
}
