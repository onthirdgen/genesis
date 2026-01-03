package com.callaudit.analytics.domain.transcription;

import com.callaudit.analytics.event.CallTranscribedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka event handler for CallTranscribed events.
 * Part of the transcription bounded context within analytics-service.
 *
 * Builds and maintains the transcription read model from events published
 * by the transcription-service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TranscriptionEventHandler {

    private final TranscriptionRepository transcriptionRepository;
    private final ObjectMapper objectMapper;

    /**
     * Handle CallTranscribed event from Kafka.
     * Idempotent processing - skips if transcription already exists for the call ID.
     *
     * @param eventJson the event JSON string
     * @param acknowledgment manual acknowledgment for Kafka consumer
     */
    @KafkaListener(
            topics = "${kafka.topics.calls-transcribed}",
            groupId = "analytics-service-transcription",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleCallTranscribed(String eventJson, Acknowledgment acknowledgment) {
        log.info("Received CallTranscribed event: {}", eventJson);

        try {
            // Parse event
            CallTranscribedEvent event = objectMapper.readValue(eventJson, CallTranscribedEvent.class);
            CallTranscribedEvent.Payload payload = event.getPayload();

            UUID callId = UUID.fromString(payload.getCallId());

            // Idempotency check - skip if already processed
            if (transcriptionRepository.existsByCallId(callId)) {
                log.info("Transcription already exists for call ID: {} (idempotent skip)", callId);
                acknowledgment.acknowledge();
                return;
            }

            // Extract nested transcription data from payload
            CallTranscribedEvent.Transcription transcriptionData = payload.getTranscription();
            if (transcriptionData == null) {
                log.error("CallTranscribed event missing transcription data for call ID: {}", callId);
                throw new RuntimeException("Missing transcription data in event payload");
            }

            // Build Transcription entity
            Transcription transcription = Transcription.builder()
                    .callId(callId)
                    .fullText(transcriptionData.getFullText())
                    .language(transcriptionData.getLanguage() != null ? transcriptionData.getLanguage() : "en")
                    .confidence(transcriptionData.getConfidence() != null ?
                            BigDecimal.valueOf(transcriptionData.getConfidence()) : null)
                    .wordCount(calculateWordCount(transcriptionData.getFullText()))
                    .modelVersion(extractModelVersion(event.getMetadata()))
                    .processingTimeMs(extractProcessingTime(event.getMetadata()))
                    .createdAt(LocalDateTime.now())
                    .build();

            // Build segments from nested transcription data
            if (transcriptionData.getSegments() != null) {
                for (CallTranscribedEvent.Segment segmentData : transcriptionData.getSegments()) {
                    TranscriptionSegment segment = TranscriptionSegment.builder()
                            .speaker(mapSpeakerType(segmentData.getSpeaker()))
                            .startTime(BigDecimal.valueOf(segmentData.getStartTime()))
                            .endTime(BigDecimal.valueOf(segmentData.getEndTime()))
                            .text(segmentData.getText())
                            .confidence(null) // Segment-level confidence not provided by transcription-service
                            .wordCount(calculateWordCount(segmentData.getText()))
                            .createdAt(LocalDateTime.now())
                            .build();

                    transcription.addSegment(segment);
                }
            }

            // Save to database
            Transcription saved = transcriptionRepository.save(transcription);

            log.info("Successfully processed CallTranscribed event for call ID: {} with {} segments",
                    callId, saved.getSegments().size());

            // Acknowledge message
            acknowledgment.acknowledge();

        } catch (JsonProcessingException e) {
            log.error("Failed to parse CallTranscribed event JSON: {}", eventJson, e);
            // Don't acknowledge - message will be retried
            throw new RuntimeException("Failed to parse CallTranscribed event", e);

        } catch (Exception e) {
            log.error("Failed to process CallTranscribed event: {}", eventJson, e);
            // Don't acknowledge - message will be retried
            throw new RuntimeException("Failed to process CallTranscribed event", e);
        }
    }

    /**
     * Map speaker string to SpeakerType enum.
     */
    private TranscriptionSegment.SpeakerType mapSpeakerType(String speaker) {
        if (speaker == null) {
            return TranscriptionSegment.SpeakerType.unknown;
        }

        return switch (speaker.toLowerCase()) {
            case "agent" -> TranscriptionSegment.SpeakerType.agent;
            case "customer" -> TranscriptionSegment.SpeakerType.customer;
            default -> TranscriptionSegment.SpeakerType.unknown;
        };
    }

    /**
     * Calculate word count from text.
     */
    private Integer calculateWordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    /**
     * Extract model version from event metadata.
     */
    private String extractModelVersion(java.util.Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }
        Object modelVersion = metadata.get("modelVersion");
        return modelVersion != null ? modelVersion.toString() : null;
    }

    /**
     * Extract processing time from event metadata.
     */
    private Integer extractProcessingTime(java.util.Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }
        Object processingTime = metadata.get("processingTimeMs");
        if (processingTime instanceof Number number) {
            return number.intValue();
        }
        return null;
    }
}
