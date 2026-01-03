package com.callaudit.analytics.listener;

import com.callaudit.analytics.event.*;
import com.callaudit.analytics.service.AgentPerformanceService;
import com.callaudit.analytics.service.DashboardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class AnalyticsEventListener {

    private final ObjectMapper objectMapper;
    private final DashboardService dashboardService;
    private final AgentPerformanceService agentPerformanceService;

    @KafkaListener(topics = "${kafka.topics.calls-received}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleCallReceived(String message, Acknowledgment acknowledgment) {
        try {
            log.debug("Received CallReceived event: {}", message);
            CallReceivedEvent event = objectMapper.readValue(message, CallReceivedEvent.class);

            // Increment total calls counter
            dashboardService.incrementCounter("total_calls");

            log.info("Processed CallReceived event for callId: {}", event.getPayload().getCallId());
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing CallReceived event", e);
            // Acknowledge anyway to prevent stuck messages
            acknowledgment.acknowledge();
        }
    }

    @KafkaListener(topics = "${kafka.topics.calls-transcribed}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleCallTranscribed(String message, Acknowledgment acknowledgment) {
        try {
            log.debug("Received CallTranscribed event: {}", message);
            CallTranscribedEvent event = objectMapper.readValue(message, CallTranscribedEvent.class);

            // Track transcription completion
            dashboardService.incrementCounter("transcribed_calls");

            log.info("Processed CallTranscribed event for callId: {}", event.getPayload().getCallId());
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing CallTranscribed event", e);
            acknowledgment.acknowledge();
        }
    }

    @KafkaListener(topics = "${kafka.topics.calls-sentiment-analyzed}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleSentimentAnalyzed(String message, Acknowledgment acknowledgment) {
        try {
            log.debug("Received SentimentAnalyzed event: {}", message);
            SentimentAnalyzedEvent event = objectMapper.readValue(message, SentimentAnalyzedEvent.class);

            SentimentAnalyzedEvent.Payload payload = event.getPayload();

            // Track sentiment
            dashboardService.incrementCounter("sentiment_analyzed");

            // Extract agent ID from metadata if available
            String agentId = extractAgentId(event.getMetadata());

            if (agentId != null) {
                // Update agent metrics with sentiment data
                agentPerformanceService.updateAgentMetrics(
                        agentId,
                        null, // quality score - will come from audit event
                        payload.getSentimentScore(),
                        payload.getCustomerSatisfactionScore(),
                        null, // compliance - will come from audit event
                        null  // churn risk - will come from VoC event
                );
            }

            log.info("Processed SentimentAnalyzed event for callId: {}, sentiment: {}, score: {}",
                    payload.getCallId(), payload.getOverallSentiment(), payload.getSentimentScore());
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing SentimentAnalyzed event", e);
            acknowledgment.acknowledge();
        }
    }

    @KafkaListener(topics = "${kafka.topics.calls-voc-analyzed}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleVocAnalyzed(String message, Acknowledgment acknowledgment) {
        try {
            log.debug("Received VocAnalyzed event: {}", message);
            VocAnalyzedEvent event = objectMapper.readValue(message, VocAnalyzedEvent.class);

            VocAnalyzedEvent.Payload payload = event.getPayload();

            // Track VoC analysis
            dashboardService.incrementCounter("voc_analyzed");

            // Track churn risk distribution
            if (payload.getChurnRisk() != null) {
                String churnLevel = payload.getChurnRisk().getLevel();
                if (churnLevel != null) {
                    dashboardService.incrementChurnRisk(churnLevel);
                }
            }

            // Track topics
            if (payload.getTopics() != null) {
                payload.getTopics().forEach(dashboardService::trackTopic);
            }

            // Track issues
            if (payload.getIssues() != null) {
                payload.getIssues().forEach(issue -> {
                    if (issue.getCategory() != null) {
                        dashboardService.trackIssue(issue.getCategory());
                    }
                });
            }

            // Extract agent ID from metadata
            String agentId = extractAgentId(event.getMetadata());

            if (agentId != null && payload.getChurnRisk() != null) {
                // Update agent metrics with churn risk
                agentPerformanceService.updateAgentMetrics(
                        agentId,
                        null, // quality score
                        null, // sentiment score
                        null, // customer satisfaction
                        null, // compliance
                        payload.getChurnRisk().getScore()
                );
            }

            log.info("Processed VocAnalyzed event for callId: {}, churn risk: {}, topics: {}",
                    payload.getCallId(),
                    payload.getChurnRisk() != null ? payload.getChurnRisk().getLevel() : "N/A",
                    payload.getTopics() != null ? payload.getTopics().size() : 0);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing VocAnalyzed event", e);
            acknowledgment.acknowledge();
        }
    }

    @KafkaListener(topics = "${kafka.topics.calls-audited}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleCallAudited(String message, Acknowledgment acknowledgment) {
        try {
            log.debug("Received CallAudited event: {}", message);
            CallAuditedEvent event = objectMapper.readValue(message, CallAuditedEvent.class);

            CallAuditedEvent.Payload payload = event.getPayload();

            // Track audit completion
            dashboardService.incrementCounter("calls_audited");

            // Track compliance
            if (payload.getComplianceCheck() != null && payload.getComplianceCheck().getPassed() != null) {
                if (payload.getComplianceCheck().getPassed()) {
                    dashboardService.incrementCounter("compliance_passed");
                } else {
                    dashboardService.incrementCounter("compliance_failed");
                }
            }

            // Extract agent ID from metadata
            String agentId = extractAgentId(event.getMetadata());

            if (agentId != null) {
                // Calculate compliance rate (1.0 if passed, 0.0 if failed)
                Double complianceRate = null;
                if (payload.getComplianceCheck() != null && payload.getComplianceCheck().getPassed() != null) {
                    complianceRate = payload.getComplianceCheck().getPassed() ? 1.0 : 0.0;
                }

                // Update agent metrics with audit data
                agentPerformanceService.updateAgentMetrics(
                        agentId,
                        payload.getQualityScore(),
                        null, // sentiment score
                        null, // customer satisfaction
                        complianceRate,
                        null  // churn risk
                );
            }

            log.info("Processed CallAudited event for callId: {}, quality score: {}, compliance: {}",
                    payload.getCallId(),
                    payload.getQualityScore(),
                    payload.getComplianceCheck() != null ? payload.getComplianceCheck().getPassed() : "N/A");
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing CallAudited event", e);
            acknowledgment.acknowledge();
        }
    }

    private String extractAgentId(Object metadata) {
        try {
            if (metadata instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> metadataMap = (java.util.Map<String, Object>) metadata;
                Object agentId = metadataMap.get("agentId");
                if (agentId != null) {
                    return agentId.toString();
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract agentId from metadata", e);
        }
        return null;
    }
}
