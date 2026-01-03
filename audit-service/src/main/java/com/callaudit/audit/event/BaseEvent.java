package com.callaudit.audit.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEvent {

    @JsonProperty("eventId")
    private UUID eventId;

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("aggregateId")
    private UUID aggregateId;

    @JsonProperty("aggregateType")
    private String aggregateType;

    @JsonProperty("timestamp")
    private OffsetDateTime timestamp;

    @JsonProperty("version")
    private Integer version;

    @JsonProperty("causationId")
    private UUID causationId;

    @JsonProperty("correlationId")
    private UUID correlationId;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;
}
