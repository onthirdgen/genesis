package com.callaudit.analytics.domain.call;

import com.callaudit.analytics.event.CallReceivedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Kafka event handler for CallReceived events.
 * Part of the call bounded context within analytics-service.
 *
 * Builds and maintains the call read model from events published
 * by the call-ingestion-service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CallEventHandler {

    private final CallRepository callRepository;
    private final ObjectMapper objectMapper;

    /**
     * Handle CallReceived event from Kafka.
     * Idempotent processing - skips if call already exists with the same correlation ID.
     *
     * @param eventJson the event JSON string
     * @param acknowledgment manual acknowledgment for Kafka consumer
     */
    @KafkaListener(
            topics = "${kafka.topics.calls-received}",
            groupId = "analytics-service-calls",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleCallReceived(String eventJson, Acknowledgment acknowledgment) {
        log.info("Received CallReceived event: {}", eventJson);

        try {
            // Parse event
            CallReceivedEvent event = objectMapper.readValue(eventJson, CallReceivedEvent.class);
            CallReceivedEvent.Payload payload = event.getPayload();

            UUID correlationId = UUID.fromString(event.getCorrelationId());

            // Idempotency check - skip if already processed
            if (callRepository.existsByCorrelationId(correlationId)) {
                log.info("Call already exists for correlation ID: {} (idempotent skip)", correlationId);
                acknowledgment.acknowledge();
                return;
            }

            // Build Call entity
            Call call = Call.builder()
                    .callerId(payload.getPhoneNumber() != null ? payload.getPhoneNumber() : payload.getCustomerId())
                    .agentId(payload.getAgentId())
                    .channel(mapChannel(payload.getDirection()))
                    .startTime(parseTimestamp(payload.getCallStartTime() != null ? payload.getCallStartTime() : payload.getUploadedAt()))
                    .duration(payload.getDurationSeconds())
                    .audioFileUrl(payload.getAudioFileUrl())
                    .fileSizeBytes(payload.getFileSizeBytes())
                    .fileFormat(payload.getAudioFormat())
                    .status(Call.Status.PENDING)
                    .correlationId(correlationId)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            // Save to database
            Call saved = callRepository.save(call);

            log.info("Successfully processed CallReceived event for call ID: {} with correlation ID: {}",
                    saved.getId(), correlationId);

            // Acknowledge message
            acknowledgment.acknowledge();

        } catch (JsonProcessingException e) {
            log.error("Failed to parse CallReceived event JSON: {}", eventJson, e);
            // Don't acknowledge - message will be retried
            throw new RuntimeException("Failed to parse CallReceived event", e);

        } catch (Exception e) {
            log.error("Failed to process CallReceived event: {}", eventJson, e);
            // Don't acknowledge - message will be retried
            throw new RuntimeException("Failed to process CallReceived event", e);
        }
    }

    /**
     * Map channel string to Channel enum.
     */
    private Call.Channel mapChannel(String channel) {
        if (channel == null) {
            return Call.Channel.PHONE;
        }

        return switch (channel.toUpperCase()) {
            case "INBOUND" -> Call.Channel.INBOUND;
            case "OUTBOUND" -> Call.Channel.OUTBOUND;
            case "INTERNAL" -> Call.Channel.INTERNAL;
            case "PHONE" -> Call.Channel.PHONE;
            case "EMAIL" -> Call.Channel.EMAIL;
            case "CHAT" -> Call.Channel.CHAT;
            default -> Call.Channel.PHONE;
        };
    }

    /**
     * Parse ISO timestamp to LocalDateTime.
     */
    private LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null) {
            return LocalDateTime.now();
        }

        try {
            return LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            log.warn("Failed to parse timestamp: {}, using current time", timestamp);
            return LocalDateTime.now();
        }
    }
}
