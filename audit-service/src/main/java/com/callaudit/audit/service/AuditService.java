package com.callaudit.audit.service;

import com.callaudit.audit.event.*;
import com.callaudit.audit.model.*;
import com.callaudit.audit.repository.AuditResultRepository;
import com.callaudit.audit.repository.ComplianceRuleRepository;
import com.callaudit.audit.repository.ComplianceViolationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditService {

    private final AuditResultRepository auditResultRepository;
    private final ComplianceViolationRepository violationRepository;
    private final ComplianceRuleRepository ruleRepository;
    private final ComplianceRuleEngine ruleEngine;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${audit.kafka.topics.audited:calls.audited}")
    private String auditedTopic;

    @Value("${audit.scoring.weights.script-adherence:0.30}")
    private double scriptAdherenceWeight;

    @Value("${audit.scoring.weights.customer-service:0.40}")
    private double customerServiceWeight;

    @Value("${audit.scoring.weights.resolution-effectiveness:0.30}")
    private double resolutionEffectivenessWeight;

    @Value("${audit.scoring.thresholds.pass-score:70}")
    private int passScoreThreshold;

    @Value("${audit.scoring.thresholds.review-score:50}")
    private int reviewScoreThreshold;

    @Transactional
    public AuditResult auditCall(
            CallTranscribedEvent.TranscriptionPayload transcription,
            SentimentAnalyzedEvent.SentimentPayload sentiment,
            VocAnalyzedEvent.VocPayload voc) {

        long startTime = System.currentTimeMillis();
        log.info("Starting audit for call ID: {}", transcription.getCallId());

        // Evaluate compliance
        List<ComplianceViolation> violations = evaluateCompliance(transcription, sentiment);

        // Calculate quality metrics
        int scriptAdherence = calculateScriptAdherence(transcription);
        int customerService = calculateCustomerService(transcription, sentiment);
        int resolutionEffectiveness = calculateResolutionEffectiveness(voc);

        // Calculate overall score
        int overallScore = (int) Math.round(
                scriptAdherence * scriptAdherenceWeight +
                customerService * customerServiceWeight +
                resolutionEffectiveness * resolutionEffectivenessWeight
        );

        // Determine compliance status
        ComplianceStatus complianceStatus = determineComplianceStatus(violations, overallScore);

        // Determine if review is needed
        boolean flagsForReview = shouldFlagForReview(violations, overallScore, sentiment);
        String reviewReason = generateReviewReason(violations, overallScore, sentiment);

        // Create audit result
        AuditResult auditResult = AuditResult.builder()
                .callId(transcription.getCallId())
                .overallScore(overallScore)
                .complianceStatus(complianceStatus)
                .scriptAdherence(scriptAdherence)
                .customerService(customerService)
                .resolutionEffectiveness(resolutionEffectiveness)
                .flagsForReview(flagsForReview)
                .reviewReason(reviewReason)
                .processingTimeMs((int) (System.currentTimeMillis() - startTime))
                .build();

        auditResult = auditResultRepository.save(auditResult);
        log.info("Saved audit result for call ID: {} with overall score: {}",
                transcription.getCallId(), overallScore);

        // Save violations
        for (ComplianceViolation violation : violations) {
            violation.setAuditResultId(auditResult.getId());
            violationRepository.save(violation);
        }

        // Publish CallAudited event
        publishCallAuditedEvent(auditResult, violations, transcription.getCallId());

        return auditResult;
    }

    public List<ComplianceViolation> evaluateCompliance(
            CallTranscribedEvent.TranscriptionPayload transcription,
            SentimentAnalyzedEvent.SentimentPayload sentiment) {

        List<ComplianceViolation> violations = new ArrayList<>();
        List<ComplianceRule> activeRules = ruleRepository.findByIsActive(true);

        log.info("Evaluating {} active compliance rules", activeRules.size());

        for (ComplianceRule rule : activeRules) {
            ComplianceViolation violation = ruleEngine.evaluateRule(rule, transcription, sentiment);
            if (violation != null) {
                violations.add(violation);
                log.warn("Violation detected: {} - {}", rule.getId(), rule.getName());
            }
        }

        return violations;
    }

    public int calculateScriptAdherence(CallTranscribedEvent.TranscriptionPayload transcription) {
        // Define required script phrases
        List<String> requiredPhrases = Arrays.asList(
                "hello", "hi", "welcome", "good morning", "good afternoon",  // Greeting
                "how can I help", "how may I assist",  // Opening
                "thank you for calling", "have a great day", "is there anything else"  // Closing
        );

        String fullText = transcription.getFullText().toLowerCase();

        long foundPhrases = requiredPhrases.stream()
                .filter(fullText::contains)
                .count();

        // Calculate percentage
        double adherencePercentage = (foundPhrases / (double) requiredPhrases.size()) * 100;

        return Math.min(100, (int) Math.round(adherencePercentage));
    }

    public int calculateCustomerService(
            CallTranscribedEvent.TranscriptionPayload transcription,
            SentimentAnalyzedEvent.SentimentPayload sentiment) {

        int baseScore = 70;

        // Adjust based on sentiment
        if (sentiment != null) {
            if ("positive".equalsIgnoreCase(sentiment.getOverallSentiment())) {
                baseScore += 20;
            } else if ("negative".equalsIgnoreCase(sentiment.getOverallSentiment())) {
                baseScore -= 20;
            }

            // Penalty for escalation
            if (Boolean.TRUE.equals(sentiment.getEscalationDetected())) {
                baseScore -= 15;
            }
        }

        // Check for empathy words
        String fullText = transcription.getFullText().toLowerCase();
        List<String> empathyWords = Arrays.asList(
                "understand", "sorry", "apologize", "appreciate", "help"
        );

        long empathyCount = empathyWords.stream()
                .filter(fullText::contains)
                .count();

        if (empathyCount >= 3) {
            baseScore += 10;
        }

        return Math.max(0, Math.min(100, baseScore));
    }

    public int calculateResolutionEffectiveness(VocAnalyzedEvent.VocPayload voc) {
        int baseScore = 60;

        if (voc == null) {
            return baseScore;
        }

        // Adjust based on customer satisfaction
        if (voc.getCustomerSatisfaction() != null) {
            switch (voc.getCustomerSatisfaction().toLowerCase()) {
                case "high" -> baseScore += 30;
                case "medium" -> baseScore += 10;
                case "low" -> baseScore -= 20;
            }
        }

        // Adjust based on intent resolution
        if ("compliment".equalsIgnoreCase(voc.getPrimaryIntent())) {
            baseScore += 10;
        } else if ("complaint".equalsIgnoreCase(voc.getPrimaryIntent())) {
            // Check if actionable items exist (indicates attempt to resolve)
            if (voc.getActionableItems() != null && !voc.getActionableItems().isEmpty()) {
                baseScore += 10;
            } else {
                baseScore -= 10;
            }
        }

        // Penalty for high churn risk
        if (voc.getPredictedChurnRisk() != null) {
            if (voc.getPredictedChurnRisk().compareTo(BigDecimal.valueOf(0.7)) > 0) {
                baseScore -= 15;
            }
        }

        return Math.max(0, Math.min(100, baseScore));
    }

    private ComplianceStatus determineComplianceStatus(List<ComplianceViolation> violations, int overallScore) {
        // Check for critical violations
        boolean hasCriticalViolation = violations.stream()
                .anyMatch(v -> v.getSeverity() == ViolationSeverity.critical);

        if (hasCriticalViolation) {
            return ComplianceStatus.failed;
        }

        // Check score thresholds
        if (overallScore < reviewScoreThreshold) {
            return ComplianceStatus.failed;
        } else if (overallScore < passScoreThreshold) {
            return ComplianceStatus.review_required;
        }

        // Check for high severity violations
        boolean hasHighViolation = violations.stream()
                .anyMatch(v -> v.getSeverity() == ViolationSeverity.high);

        if (hasHighViolation) {
            return ComplianceStatus.review_required;
        }

        return ComplianceStatus.passed;
    }

    private boolean shouldFlagForReview(
            List<ComplianceViolation> violations,
            int overallScore,
            SentimentAnalyzedEvent.SentimentPayload sentiment) {

        // Flag for review if score is borderline
        if (overallScore < passScoreThreshold) {
            return true;
        }

        // Flag if there are any critical or high violations
        boolean hasSevereViolation = violations.stream()
                .anyMatch(v -> v.getSeverity() == ViolationSeverity.critical ||
                        v.getSeverity() == ViolationSeverity.high);

        if (hasSevereViolation) {
            return true;
        }

        // Flag if escalation was detected
        if (sentiment != null && Boolean.TRUE.equals(sentiment.getEscalationDetected())) {
            return true;
        }

        return false;
    }

    private String generateReviewReason(
            List<ComplianceViolation> violations,
            int overallScore,
            SentimentAnalyzedEvent.SentimentPayload sentiment) {

        List<String> reasons = new ArrayList<>();

        if (overallScore < reviewScoreThreshold) {
            reasons.add("Low overall score: " + overallScore);
        }

        long criticalCount = violations.stream()
                .filter(v -> v.getSeverity() == ViolationSeverity.critical)
                .count();
        if (criticalCount > 0) {
            reasons.add(criticalCount + " critical violation(s)");
        }

        long highCount = violations.stream()
                .filter(v -> v.getSeverity() == ViolationSeverity.high)
                .count();
        if (highCount > 0) {
            reasons.add(highCount + " high severity violation(s)");
        }

        if (sentiment != null && Boolean.TRUE.equals(sentiment.getEscalationDetected())) {
            reasons.add("Customer escalation detected");
        }

        return reasons.isEmpty() ? null : String.join("; ", reasons);
    }

    private void publishCallAuditedEvent(
            AuditResult auditResult,
            List<ComplianceViolation> violations,
            UUID callId) {

        CallAuditedEvent event = new CallAuditedEvent();
        event.setEventId(UUID.randomUUID());
        event.setEventType("CallAudited");
        event.setAggregateId(callId);
        event.setAggregateType("Call");
        event.setTimestamp(OffsetDateTime.now());
        event.setVersion(1);
        event.setCorrelationId(callId);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("service", "audit-service");
        metadata.put("userId", "system");
        event.setMetadata(metadata);

        // Build payload
        CallAuditedEvent.AuditPayload payload = new CallAuditedEvent.AuditPayload();
        payload.setCallId(callId);
        payload.setOverallScore(auditResult.getOverallScore());
        payload.setComplianceStatus(auditResult.getComplianceStatus());
        payload.setFlagsForReview(auditResult.getFlagsForReview());
        payload.setReviewReason(auditResult.getReviewReason());
        payload.setProcessingTimeMs(auditResult.getProcessingTimeMs());

        // Build quality metrics
        CallAuditedEvent.QualityMetrics qualityMetrics = new CallAuditedEvent.QualityMetrics();
        qualityMetrics.setScriptAdherence(auditResult.getScriptAdherence());
        qualityMetrics.setCustomerService(auditResult.getCustomerService());
        qualityMetrics.setResolutionEffectiveness(auditResult.getResolutionEffectiveness());
        payload.setQualityMetrics(qualityMetrics);

        // Build violations
        List<CallAuditedEvent.ViolationInfo> violationInfos = violations.stream()
                .map(v -> {
                    CallAuditedEvent.ViolationInfo info = new CallAuditedEvent.ViolationInfo();
                    info.setRuleId(v.getRuleId());
                    info.setRuleName(v.getRuleName());
                    info.setSeverity(v.getSeverity());
                    info.setDescription(v.getDescription());
                    info.setTimestampInCall(v.getTimestampInCall());
                    info.setEvidence(v.getEvidence());
                    return info;
                })
                .toList();
        payload.setViolations(violationInfos);

        event.setPayload(payload);

        // Publish to Kafka
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(auditedTopic, callId.toString(), eventJson);
            log.info("Published CallAudited event for call ID: {}", callId);
        } catch (Exception e) {
            log.error("Failed to publish CallAudited event: {}", e.getMessage(), e);
        }
    }
}
