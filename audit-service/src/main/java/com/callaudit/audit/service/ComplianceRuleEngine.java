package com.callaudit.audit.service;

import com.callaudit.audit.event.CallTranscribedEvent;
import com.callaudit.audit.event.SentimentAnalyzedEvent;
import com.callaudit.audit.model.ComplianceRule;
import com.callaudit.audit.model.ComplianceViolation;
import com.callaudit.audit.model.ViolationSeverity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ComplianceRuleEngine {

    private final ObjectMapper objectMapper;

    public ComplianceViolation evaluateRule(
            ComplianceRule rule,
            CallTranscribedEvent.TranscriptionPayload transcription,
            SentimentAnalyzedEvent.SentimentPayload sentiment) {

        try {
            Map<String, Object> ruleDefinition = objectMapper.readValue(
                    rule.getRuleDefinition(),
                    new TypeReference<Map<String, Object>>() {}
            );

            String ruleType = (String) ruleDefinition.get("type");

            return switch (ruleType) {
                case "keyword_check" -> evaluateKeywordCheck(rule, transcription, ruleDefinition);
                case "prohibited_words" -> evaluateProhibitedWords(rule, transcription, ruleDefinition);
                case "sentiment_response" -> evaluateSentimentResponse(rule, transcription, sentiment, ruleDefinition);
                default -> {
                    log.warn("Unknown rule type: {}", ruleType);
                    yield null;
                }
            };
        } catch (Exception e) {
            log.error("Error evaluating rule {}: {}", rule.getId(), e.getMessage(), e);
            return null;
        }
    }

    private ComplianceViolation evaluateKeywordCheck(
            ComplianceRule rule,
            CallTranscribedEvent.TranscriptionPayload transcription,
            Map<String, Object> ruleDefinition) {

        @SuppressWarnings("unchecked")
        List<String> keywords = (List<String>) ruleDefinition.get("keywords");
        String speaker = (String) ruleDefinition.get("speaker");

        @SuppressWarnings("unchecked")
        Map<String, Object> timeWindow = (Map<String, Object>) ruleDefinition.get("time_window");

        // Filter segments by speaker if specified
        List<CallTranscribedEvent.Segment> relevantSegments = transcription.getSegments().stream()
                .filter(s -> speaker == null || speaker.equalsIgnoreCase(s.getSpeaker()))
                .toList();

        // Apply time window if specified
        if (timeWindow != null) {
            BigDecimal startTime = getBigDecimal(timeWindow.get("start"));
            BigDecimal endTime = getBigDecimal(timeWindow.get("end"));

            relevantSegments = relevantSegments.stream()
                    .filter(s -> {
                        if (startTime != null && startTime.compareTo(BigDecimal.ZERO) < 0) {
                            // Negative start means from end of call
                            return true; // Handle negative time windows
                        }
                        if (startTime != null && s.getStartTime().compareTo(startTime) < 0) {
                            return false;
                        }
                        if (endTime != null && endTime.compareTo(BigDecimal.ZERO) > 0) {
                            return s.getEndTime().compareTo(endTime) <= 0;
                        }
                        return true;
                    })
                    .toList();
        }

        // Check if any keyword is found
        boolean keywordFound = relevantSegments.stream()
                .anyMatch(segment -> keywords.stream()
                        .anyMatch(keyword -> segment.getText().toLowerCase().contains(keyword.toLowerCase())));

        if (!keywordFound) {
            return ComplianceViolation.builder()
                    .ruleId(rule.getId())
                    .ruleName(rule.getName())
                    .severity(rule.getSeverity())
                    .description("Required keyword not found: " + String.join(", ", keywords))
                    .evidence("No matching keywords in " + speaker + " segments")
                    .build();
        }

        return null; // No violation
    }

    private ComplianceViolation evaluateProhibitedWords(
            ComplianceRule rule,
            CallTranscribedEvent.TranscriptionPayload transcription,
            Map<String, Object> ruleDefinition) {

        @SuppressWarnings("unchecked")
        List<String> prohibitedWords = (List<String>) ruleDefinition.get("words");
        String speaker = (String) ruleDefinition.get("speaker");

        // Filter segments by speaker if specified
        List<CallTranscribedEvent.Segment> relevantSegments = transcription.getSegments().stream()
                .filter(s -> speaker == null || speaker.equalsIgnoreCase(s.getSpeaker()))
                .toList();

        // Check for prohibited words
        for (CallTranscribedEvent.Segment segment : relevantSegments) {
            for (String prohibitedWord : prohibitedWords) {
                if (segment.getText().toLowerCase().contains(prohibitedWord.toLowerCase())) {
                    return ComplianceViolation.builder()
                            .ruleId(rule.getId())
                            .ruleName(rule.getName())
                            .severity(rule.getSeverity())
                            .description("Prohibited word detected: " + prohibitedWord)
                            .timestampInCall(segment.getStartTime())
                            .evidence(segment.getText())
                            .build();
                }
            }
        }

        return null; // No violation
    }

    private ComplianceViolation evaluateSentimentResponse(
            ComplianceRule rule,
            CallTranscribedEvent.TranscriptionPayload transcription,
            SentimentAnalyzedEvent.SentimentPayload sentiment,
            Map<String, Object> ruleDefinition) {

        String triggerSentiment = (String) ruleDefinition.get("trigger_sentiment");
        @SuppressWarnings("unchecked")
        List<String> requiredKeywords = (List<String>) ruleDefinition.get("required_keywords");
        String speaker = (String) ruleDefinition.get("speaker");

        if (sentiment == null || sentiment.getSegmentSentiments() == null) {
            return null;
        }

        // Find segments with negative sentiment
        List<SentimentAnalyzedEvent.SegmentSentiment> negativeSegments = sentiment.getSegmentSentiments().stream()
                .filter(s -> triggerSentiment.equalsIgnoreCase(s.getSentiment()))
                .toList();

        if (negativeSegments.isEmpty()) {
            return null; // No negative sentiment, rule doesn't apply
        }

        // Find agent responses after negative customer sentiment
        for (SentimentAnalyzedEvent.SegmentSentiment negSeg : negativeSegments) {
            // Find agent segments after this negative segment
            List<CallTranscribedEvent.Segment> agentResponses = transcription.getSegments().stream()
                    .filter(s -> speaker == null || speaker.equalsIgnoreCase(s.getSpeaker()))
                    .filter(s -> s.getStartTime().compareTo(negSeg.getEndTime()) >= 0)
                    .limit(3) // Check next 3 agent segments
                    .toList();

            // Check if empathy keywords are present
            boolean empathyFound = agentResponses.stream()
                    .anyMatch(segment -> requiredKeywords.stream()
                            .anyMatch(keyword -> segment.getText().toLowerCase().contains(keyword.toLowerCase())));

            if (!empathyFound) {
                return ComplianceViolation.builder()
                        .ruleId(rule.getId())
                        .ruleName(rule.getName())
                        .severity(rule.getSeverity())
                        .description("Agent did not express empathy when customer showed negative sentiment")
                        .timestampInCall(negSeg.getStartTime())
                        .evidence("Customer negative sentiment detected but no empathy response")
                        .build();
            }
        }

        return null; // No violation
    }

    private BigDecimal getBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        if (value instanceof String) {
            try {
                return new BigDecimal((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
