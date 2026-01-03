package com.callaudit.audit.service;

import com.callaudit.audit.event.CallTranscribedEvent;
import com.callaudit.audit.event.SentimentAnalyzedEvent;
import com.callaudit.audit.model.ComplianceRule;
import com.callaudit.audit.model.ComplianceViolation;
import com.callaudit.audit.model.ViolationSeverity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ComplianceRuleEngine
 */
@ExtendWith(MockitoExtension.class)
class ComplianceRuleEngineTest {

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ComplianceRuleEngine ruleEngine;

    private UUID testCallId;
    private CallTranscribedEvent.TranscriptionPayload transcription;
    private SentimentAnalyzedEvent.SentimentPayload sentiment;

    @BeforeEach
    void setUp() {
        testCallId = UUID.randomUUID();
        transcription = createTranscription();
        sentiment = createSentiment();
    }

    @Test
    void evaluateRule_KeywordCheckPassed_ReturnsNull() throws Exception {
        // Arrange
        ComplianceRule rule = createKeywordCheckRule();
        Map<String, Object> ruleDefinition = Map.of(
            "type", "keyword_check",
            "keywords", List.of("hello", "welcome"),
            "speaker", "agent"
        );

        when(objectMapper.readValue(eq(rule.getRuleDefinition()), any(TypeReference.class)))
            .thenReturn(ruleDefinition);

        // Act
        ComplianceViolation violation = ruleEngine.evaluateRule(rule, transcription, sentiment);

        // Assert
        assertNull(violation);
    }

    @Test
    void evaluateRule_KeywordCheckFailed_ReturnsViolation() throws Exception {
        // Arrange
        ComplianceRule rule = createKeywordCheckRule();
        Map<String, Object> ruleDefinition = Map.of(
            "type", "keyword_check",
            "keywords", List.of("mandatory", "phrase"),
            "speaker", "agent"
        );

        when(objectMapper.readValue(eq(rule.getRuleDefinition()), any(TypeReference.class)))
            .thenReturn(ruleDefinition);

        // Act
        ComplianceViolation violation = ruleEngine.evaluateRule(rule, transcription, sentiment);

        // Assert
        assertNotNull(violation);
        assertEquals("RULE-001", violation.getRuleId());
        assertEquals("Greeting Required", violation.getRuleName());
        assertEquals(ViolationSeverity.medium, violation.getSeverity());
        assertTrue(violation.getDescription().contains("Required keyword not found"));
    }

    @Test
    void evaluateRule_KeywordCheckWithTimeWindow_EvaluatesCorrectly() throws Exception {
        // Arrange
        ComplianceRule rule = createKeywordCheckRule();
        Map<String, Object> ruleDefinition = Map.of(
            "type", "keyword_check",
            "keywords", List.of("goodbye", "thanks"),
            "speaker", "agent",
            "time_window", Map.of(
                "start", 30.0,
                "end", 60.0
            )
        );

        when(objectMapper.readValue(eq(rule.getRuleDefinition()), any(TypeReference.class)))
            .thenReturn(ruleDefinition);

        // Act
        ComplianceViolation violation = ruleEngine.evaluateRule(rule, transcription, sentiment);

        // Assert - Should return violation because no segments in time window have goodbye/thanks
        assertNotNull(violation);
    }

    @Test
    void evaluateRule_ProhibitedWordsNotFound_ReturnsNull() throws Exception {
        // Arrange
        ComplianceRule rule = createProhibitedWordsRule();
        Map<String, Object> ruleDefinition = Map.of(
            "type", "prohibited_words",
            "words", List.of("rude", "inappropriate"),
            "speaker", "agent"
        );

        when(objectMapper.readValue(eq(rule.getRuleDefinition()), any(TypeReference.class)))
            .thenReturn(ruleDefinition);

        // Act
        ComplianceViolation violation = ruleEngine.evaluateRule(rule, transcription, sentiment);

        // Assert
        assertNull(violation);
    }

    @Test
    void evaluateRule_ProhibitedWordsFound_ReturnsViolation() throws Exception {
        // Arrange
        CallTranscribedEvent.TranscriptionPayload badTranscription = createBadTranscription();
        ComplianceRule rule = createProhibitedWordsRule();
        Map<String, Object> ruleDefinition = Map.of(
            "type", "prohibited_words",
            "words", List.of("stupid", "terrible"),
            "speaker", "agent"
        );

        when(objectMapper.readValue(eq(rule.getRuleDefinition()), any(TypeReference.class)))
            .thenReturn(ruleDefinition);

        // Act
        ComplianceViolation violation = ruleEngine.evaluateRule(rule, badTranscription, sentiment);

        // Assert
        assertNotNull(violation);
        assertEquals("RULE-002", violation.getRuleId());
        assertEquals(ViolationSeverity.high, violation.getSeverity());
        assertTrue(violation.getDescription().contains("Prohibited word detected"));
        assertNotNull(violation.getTimestampInCall());
        assertNotNull(violation.getEvidence());
    }

    @Test
    void evaluateRule_ProhibitedWordsAnySpeaker_DetectsViolation() throws Exception {
        // Arrange
        CallTranscribedEvent.TranscriptionPayload badTranscription = createBadTranscription();
        ComplianceRule rule = createProhibitedWordsRule();
        // Note: Map.of() doesn't accept null values, use HashMap for nullable values
        Map<String, Object> ruleDefinition = new java.util.HashMap<>();
        ruleDefinition.put("type", "prohibited_words");
        ruleDefinition.put("words", List.of("terrible"));
        ruleDefinition.put("speaker", null);  // Any speaker

        when(objectMapper.readValue(eq(rule.getRuleDefinition()), any(TypeReference.class)))
            .thenReturn(ruleDefinition);

        // Act
        ComplianceViolation violation = ruleEngine.evaluateRule(rule, badTranscription, sentiment);

        // Assert
        assertNotNull(violation);
    }

    @Test
    void evaluateRule_SentimentResponseNoNegativeSentiment_ReturnsNull() throws Exception {
        // Arrange
        ComplianceRule rule = createSentimentResponseRule();
        Map<String, Object> ruleDefinition = Map.of(
            "type", "sentiment_response",
            "trigger_sentiment", "negative",
            "required_keywords", List.of("sorry", "apologize", "understand"),
            "speaker", "agent"
        );

        when(objectMapper.readValue(eq(rule.getRuleDefinition()), any(TypeReference.class)))
            .thenReturn(ruleDefinition);

        // Act
        ComplianceViolation violation = ruleEngine.evaluateRule(rule, transcription, sentiment);

        // Assert
        assertNull(violation);  // No negative sentiment in test data
    }

    @Test
    void evaluateRule_SentimentResponseWithEmpathy_ReturnsNull() throws Exception {
        // Arrange
        SentimentAnalyzedEvent.SentimentPayload negativeSentiment = createNegativeSentiment();
        CallTranscribedEvent.TranscriptionPayload empatheticTranscription = createEmpatheticTranscription();

        ComplianceRule rule = createSentimentResponseRule();
        Map<String, Object> ruleDefinition = Map.of(
            "type", "sentiment_response",
            "trigger_sentiment", "negative",
            "required_keywords", List.of("sorry", "apologize", "understand"),
            "speaker", "agent"
        );

        when(objectMapper.readValue(eq(rule.getRuleDefinition()), any(TypeReference.class)))
            .thenReturn(ruleDefinition);

        // Act
        ComplianceViolation violation = ruleEngine.evaluateRule(
            rule, empatheticTranscription, negativeSentiment);

        // Assert
        assertNull(violation);
    }

    @Test
    void evaluateRule_SentimentResponseWithoutEmpathy_ReturnsViolation() throws Exception {
        // Arrange
        SentimentAnalyzedEvent.SentimentPayload negativeSentiment = createNegativeSentiment();

        ComplianceRule rule = createSentimentResponseRule();
        Map<String, Object> ruleDefinition = Map.of(
            "type", "sentiment_response",
            "trigger_sentiment", "negative",
            "required_keywords", List.of("sorry", "apologize", "understand"),
            "speaker", "agent"
        );

        when(objectMapper.readValue(eq(rule.getRuleDefinition()), any(TypeReference.class)))
            .thenReturn(ruleDefinition);

        // Act
        ComplianceViolation violation = ruleEngine.evaluateRule(
            rule, transcription, negativeSentiment);

        // Assert
        assertNotNull(violation);
        assertEquals("RULE-003", violation.getRuleId());
        assertEquals(ViolationSeverity.medium, violation.getSeverity());
        assertTrue(violation.getDescription().contains("did not express empathy"));
    }

    @Test
    void evaluateRule_SentimentResponseNullSentiment_ReturnsNull() throws Exception {
        // Arrange
        ComplianceRule rule = createSentimentResponseRule();
        Map<String, Object> ruleDefinition = Map.of(
            "type", "sentiment_response",
            "trigger_sentiment", "negative",
            "required_keywords", List.of("sorry"),
            "speaker", "agent"
        );

        when(objectMapper.readValue(eq(rule.getRuleDefinition()), any(TypeReference.class)))
            .thenReturn(ruleDefinition);

        // Act
        ComplianceViolation violation = ruleEngine.evaluateRule(rule, transcription, null);

        // Assert
        assertNull(violation);
    }

    @Test
    void evaluateRule_UnknownRuleType_ReturnsNull() throws Exception {
        // Arrange
        ComplianceRule rule = createKeywordCheckRule();
        Map<String, Object> ruleDefinition = Map.of(
            "type", "unknown_type"
        );

        when(objectMapper.readValue(eq(rule.getRuleDefinition()), any(TypeReference.class)))
            .thenReturn(ruleDefinition);

        // Act
        ComplianceViolation violation = ruleEngine.evaluateRule(rule, transcription, sentiment);

        // Assert
        assertNull(violation);
    }

    @Test
    void evaluateRule_InvalidJson_ReturnsNull() throws Exception {
        // Arrange
        ComplianceRule rule = createKeywordCheckRule();

        when(objectMapper.readValue(eq(rule.getRuleDefinition()), any(TypeReference.class)))
            .thenThrow(new RuntimeException("Invalid JSON"));

        // Act
        ComplianceViolation violation = ruleEngine.evaluateRule(rule, transcription, sentiment);

        // Assert
        assertNull(violation);  // Should handle exception gracefully
    }

    // Helper methods

    private CallTranscribedEvent.TranscriptionPayload createTranscription() {
        CallTranscribedEvent.TranscriptionPayload payload = new CallTranscribedEvent.TranscriptionPayload();
        payload.setCallId(testCallId);
        payload.setFullText("Hello! Welcome to our service. How can I help you?");

        CallTranscribedEvent.Segment segment1 = new CallTranscribedEvent.Segment();
        segment1.setSpeaker("agent");
        segment1.setStartTime(BigDecimal.ZERO);
        segment1.setEndTime(BigDecimal.valueOf(5.0));
        segment1.setText("Hello! Welcome to our service.");
        segment1.setConfidence(BigDecimal.valueOf(0.95));

        CallTranscribedEvent.Segment segment2 = new CallTranscribedEvent.Segment();
        segment2.setSpeaker("agent");
        segment2.setStartTime(BigDecimal.valueOf(5.0));
        segment2.setEndTime(BigDecimal.valueOf(10.0));
        segment2.setText("How can I help you?");
        segment2.setConfidence(BigDecimal.valueOf(0.95));

        payload.setSegments(List.of(segment1, segment2));
        return payload;
    }

    private CallTranscribedEvent.TranscriptionPayload createBadTranscription() {
        CallTranscribedEvent.TranscriptionPayload payload = new CallTranscribedEvent.TranscriptionPayload();
        payload.setCallId(testCallId);
        payload.setFullText("That's a stupid question. This is terrible service.");

        CallTranscribedEvent.Segment segment = new CallTranscribedEvent.Segment();
        segment.setSpeaker("agent");
        segment.setStartTime(BigDecimal.ZERO);
        segment.setEndTime(BigDecimal.valueOf(5.0));
        segment.setText("That's a stupid question. This is terrible service.");
        segment.setConfidence(BigDecimal.valueOf(0.9));

        payload.setSegments(List.of(segment));
        return payload;
    }

    private CallTranscribedEvent.TranscriptionPayload createEmpatheticTranscription() {
        CallTranscribedEvent.TranscriptionPayload payload = new CallTranscribedEvent.TranscriptionPayload();
        payload.setCallId(testCallId);
        payload.setFullText("I understand your frustration. I'm sorry about that.");

        CallTranscribedEvent.Segment customerSegment = new CallTranscribedEvent.Segment();
        customerSegment.setSpeaker("customer");
        customerSegment.setStartTime(BigDecimal.ZERO);
        customerSegment.setEndTime(BigDecimal.valueOf(5.0));
        customerSegment.setText("This is frustrating!");
        customerSegment.setConfidence(BigDecimal.valueOf(0.9));

        CallTranscribedEvent.Segment agentSegment = new CallTranscribedEvent.Segment();
        agentSegment.setSpeaker("agent");
        agentSegment.setStartTime(BigDecimal.valueOf(5.0));
        agentSegment.setEndTime(BigDecimal.valueOf(10.0));
        agentSegment.setText("I understand your frustration. I'm sorry about that.");
        agentSegment.setConfidence(BigDecimal.valueOf(0.95));

        payload.setSegments(List.of(customerSegment, agentSegment));
        return payload;
    }

    private SentimentAnalyzedEvent.SentimentPayload createSentiment() {
        SentimentAnalyzedEvent.SentimentPayload payload = new SentimentAnalyzedEvent.SentimentPayload();
        payload.setCallId(testCallId);
        payload.setOverallSentiment("positive");
        payload.setSentimentScore(BigDecimal.valueOf(0.8));
        payload.setEscalationDetected(false);
        payload.setSegmentSentiments(new ArrayList<>());
        return payload;
    }

    private SentimentAnalyzedEvent.SentimentPayload createNegativeSentiment() {
        SentimentAnalyzedEvent.SentimentPayload payload = new SentimentAnalyzedEvent.SentimentPayload();
        payload.setCallId(testCallId);
        payload.setOverallSentiment("negative");
        payload.setSentimentScore(BigDecimal.valueOf(-0.7));
        payload.setEscalationDetected(false);

        SentimentAnalyzedEvent.SegmentSentiment negativeSegment =
            new SentimentAnalyzedEvent.SegmentSentiment();
        negativeSegment.setStartTime(BigDecimal.ZERO);
        negativeSegment.setEndTime(BigDecimal.valueOf(5.0));
        negativeSegment.setSentiment("negative");
        negativeSegment.setScore(BigDecimal.valueOf(-0.8));
        negativeSegment.setEmotions(List.of("frustration", "anger"));

        payload.setSegmentSentiments(List.of(negativeSegment));
        return payload;
    }

    private ComplianceRule createKeywordCheckRule() {
        return ComplianceRule.builder()
            .id("RULE-001")
            .name("Greeting Required")
            .description("Agent must greet customer")
            .category("Script Adherence")
            .severity(ViolationSeverity.medium)
            .isActive(true)
            .ruleDefinition("{\"type\":\"keyword_check\"}")
            .build();
    }

    private ComplianceRule createProhibitedWordsRule() {
        return ComplianceRule.builder()
            .id("RULE-002")
            .name("No Prohibited Words")
            .description("Agent must not use prohibited words")
            .category("Professionalism")
            .severity(ViolationSeverity.high)
            .isActive(true)
            .ruleDefinition("{\"type\":\"prohibited_words\"}")
            .build();
    }

    private ComplianceRule createSentimentResponseRule() {
        return ComplianceRule.builder()
            .id("RULE-003")
            .name("Empathy Response")
            .description("Agent must show empathy when customer is negative")
            .category("Customer Service")
            .severity(ViolationSeverity.medium)
            .isActive(true)
            .ruleDefinition("{\"type\":\"sentiment_response\"}")
            .build();
    }
}
