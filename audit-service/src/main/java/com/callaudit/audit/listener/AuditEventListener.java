package com.callaudit.audit.listener;

import com.callaudit.audit.event.CallTranscribedEvent;
import com.callaudit.audit.event.SentimentAnalyzedEvent;
import com.callaudit.audit.event.VocAnalyzedEvent;
import com.callaudit.audit.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    // Store events by call ID until all three are received
    private final ConcurrentHashMap<UUID, CallTranscribedEvent.TranscriptionPayload> transcriptions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, SentimentAnalyzedEvent.SentimentPayload> sentiments = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, VocAnalyzedEvent.VocPayload> vocInsights = new ConcurrentHashMap<>();

    @KafkaListener(topics = "${audit.kafka.topics.transcribed:calls.transcribed}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleCallTranscribed(String message) {
        try {
            log.debug("Received CallTranscribed event: {}", message);
            CallTranscribedEvent event = objectMapper.readValue(message, CallTranscribedEvent.class);

            UUID callId = event.getPayload().getCallId();
            transcriptions.put(callId, event.getPayload());

            log.info("Stored transcription for call ID: {}", callId);
            tryProcessAudit(callId);
        } catch (Exception e) {
            log.error("Error processing CallTranscribed event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "${audit.kafka.topics.sentiment-analyzed:calls.sentiment-analyzed}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleSentimentAnalyzed(String message) {
        try {
            log.debug("Received SentimentAnalyzed event: {}", message);
            SentimentAnalyzedEvent event = objectMapper.readValue(message, SentimentAnalyzedEvent.class);

            UUID callId = event.getPayload().getCallId();
            sentiments.put(callId, event.getPayload());

            log.info("Stored sentiment analysis for call ID: {}", callId);
            tryProcessAudit(callId);
        } catch (Exception e) {
            log.error("Error processing SentimentAnalyzed event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "${audit.kafka.topics.voc-analyzed:calls.voc-analyzed}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleVocAnalyzed(String message) {
        try {
            log.debug("Received VocAnalyzed event: {}", message);
            VocAnalyzedEvent event = objectMapper.readValue(message, VocAnalyzedEvent.class);

            UUID callId = event.getPayload().getCallId();
            vocInsights.put(callId, event.getPayload());

            log.info("Stored VoC analysis for call ID: {}", callId);
            tryProcessAudit(callId);
        } catch (Exception e) {
            log.error("Error processing VocAnalyzed event: {}", e.getMessage(), e);
        }
    }

    private synchronized void tryProcessAudit(UUID callId) {
        CallTranscribedEvent.TranscriptionPayload transcription = transcriptions.get(callId);
        SentimentAnalyzedEvent.SentimentPayload sentiment = sentiments.get(callId);
        VocAnalyzedEvent.VocPayload voc = vocInsights.get(callId);

        // Check if all three events have been received
        if (transcription != null && sentiment != null && voc != null) {
            log.info("All events received for call ID: {}. Starting audit process.", callId);

            try {
                auditService.auditCall(transcription, sentiment, voc);

                // Clean up stored events
                transcriptions.remove(callId);
                sentiments.remove(callId);
                vocInsights.remove(callId);

                log.info("Completed audit and cleaned up cached events for call ID: {}", callId);
            } catch (Exception e) {
                log.error("Error during audit processing for call ID {}: {}", callId, e.getMessage(), e);
                // Keep events in cache for potential retry
            }
        } else {
            log.debug("Waiting for more events for call ID: {}. Have: transcription={}, sentiment={}, voc={}",
                    callId,
                    transcription != null,
                    sentiment != null,
                    voc != null);
        }
    }
}
