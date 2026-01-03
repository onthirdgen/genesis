package com.callaudit.voc.listener;

import com.callaudit.voc.event.CallTranscribedEvent;
import com.callaudit.voc.event.SentimentAnalyzedEvent;
import com.callaudit.voc.event.VocAnalyzedEvent;
import com.callaudit.voc.model.VocInsight;
import com.callaudit.voc.service.InsightService;
import com.callaudit.voc.service.VocAnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kafka listener for consuming transcription and sentiment events
 * Aggregates events by callId and processes when both are available
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VocEventListener {

    private final VocAnalysisService vocAnalysisService;
    private final InsightService insightService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // Temporary storage for event aggregation
    private final Map<String, CallTranscribedEvent> transcriptionEvents = new ConcurrentHashMap<>();
    private final Map<String, SentimentAnalyzedEvent> sentimentEvents = new ConcurrentHashMap<>();

    /**
     * Listen for CallTranscribed events
     */
    @KafkaListener(topics = "calls.transcribed", groupId = "${spring.kafka.consumer.group-id}")
    public void handleCallTranscribed(String message) {
        try {
            log.info("Received CallTranscribed event: {}", message);

            CallTranscribedEvent event = objectMapper.readValue(message, CallTranscribedEvent.class);
            String callId = event.getPayload().getCallId();

            transcriptionEvents.put(callId, event);

            // Check if we have both events for this call
            if (sentimentEvents.containsKey(callId)) {
                processVocAnalysis(callId);
            }

        } catch (Exception e) {
            log.error("Error processing CallTranscribed event", e);
        }
    }

    /**
     * Listen for SentimentAnalyzed events
     */
    @KafkaListener(topics = "calls.sentiment-analyzed", groupId = "${spring.kafka.consumer.group-id}")
    public void handleSentimentAnalyzed(String message) {
        try {
            log.info("Received SentimentAnalyzed event: {}", message);

            SentimentAnalyzedEvent event = objectMapper.readValue(message, SentimentAnalyzedEvent.class);
            String callId = event.getPayload().getCallId();

            sentimentEvents.put(callId, event);

            // Check if we have both events for this call
            if (transcriptionEvents.containsKey(callId)) {
                processVocAnalysis(callId);
            }

        } catch (Exception e) {
            log.error("Error processing SentimentAnalyzed event", e);
        }
    }

    /**
     * Process VoC analysis when both events are available
     */
    private void processVocAnalysis(String callId) {
        try {
            log.info("Processing VoC analysis for call: {}", callId);

            CallTranscribedEvent transcriptionEvent = transcriptionEvents.remove(callId);
            SentimentAnalyzedEvent sentimentEvent = sentimentEvents.remove(callId);

            if (transcriptionEvent == null || sentimentEvent == null) {
                log.warn("Missing events for call: {}", callId);
                return;
            }

            // Extract data
            String transcription = transcriptionEvent.getPayload().getTranscriptionText();
            SentimentAnalyzedEvent.SentimentPayload sentiment = sentimentEvent.getPayload();

            // Perform VoC analysis
            VocAnalysisService.VocAnalysisResult result =
                    vocAnalysisService.analyzeTranscription(transcription, sentiment);

            // Save insight to database
            VocInsight insight = VocInsight.builder()
                    .callId(UUID.fromString(callId))
                    .primaryIntent(result.getPrimaryIntent())
                    .topics(result.getTopics())
                    .keywords(result.getKeywords())
                    .customerSatisfaction(result.getCustomerSatisfaction())
                    .predictedChurnRisk(BigDecimal.valueOf(result.getPredictedChurnRisk()))
                    .actionableItems(result.getActionableItems())
                    .summary(result.getSummary())
                    .build();

            insightService.saveInsight(insight);
            log.info("Saved VoC insight for call: {}", callId);

            // Publish VocAnalyzed event
            publishVocAnalyzedEvent(callId, result, sentimentEvent.getCorrelationId());

        } catch (Exception e) {
            log.error("Error processing VoC analysis for call: {}", callId, e);
        }
    }

    /**
     * Publish VocAnalyzed event to Kafka
     */
    private void publishVocAnalyzedEvent(String callId, VocAnalysisService.VocAnalysisResult result,
                                        String correlationId) {
        try {
            VocAnalyzedEvent.VocPayload payload = VocAnalyzedEvent.VocPayload.builder()
                    .callId(callId)
                    .primaryIntent(result.getPrimaryIntent())
                    .topics(result.getTopics())
                    .keywords(result.getKeywords())
                    .customerSatisfaction(result.getCustomerSatisfaction())
                    .actionableItems(result.getActionableItems())
                    .predictedChurnRisk(result.getPredictedChurnRisk())
                    .summary(result.getSummary())
                    .build();

            VocAnalyzedEvent event = VocAnalyzedEvent.create(callId, payload, correlationId);

            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("calls.voc-analyzed", callId, eventJson);

            log.info("Published VocAnalyzed event for call: {}", callId);

        } catch (Exception e) {
            log.error("Error publishing VocAnalyzed event for call: {}", callId, e);
        }
    }
}
