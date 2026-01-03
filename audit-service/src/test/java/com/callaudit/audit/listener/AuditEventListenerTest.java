package com.callaudit.audit.listener;

import com.callaudit.audit.event.CallTranscribedEvent;
import com.callaudit.audit.event.SentimentAnalyzedEvent;
import com.callaudit.audit.event.VocAnalyzedEvent;
import com.callaudit.audit.model.AuditResult;
import com.callaudit.audit.model.ComplianceStatus;
import com.callaudit.audit.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuditEventListener
 */
@ExtendWith(MockitoExtension.class)
class AuditEventListenerTest {

    @Mock
    private AuditService auditService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuditEventListener auditEventListener;

    private UUID testCallId;
    private CallTranscribedEvent callTranscribedEvent;
    private SentimentAnalyzedEvent sentimentAnalyzedEvent;
    private VocAnalyzedEvent vocAnalyzedEvent;

    @BeforeEach
    void setUp() {
        testCallId = UUID.randomUUID();

        // Create test events
        callTranscribedEvent = createCallTranscribedEvent();
        sentimentAnalyzedEvent = createSentimentAnalyzedEvent();
        vocAnalyzedEvent = createVocAnalyzedEvent();

        // Reset mocks
        reset(auditService, objectMapper);
    }

    @Test
    void handleCallTranscribed_ValidEvent_StoresEventInMemory() throws Exception {
        // Arrange
        String eventJson = "{\"eventId\":\"test\"}";
        when(objectMapper.readValue(eventJson, CallTranscribedEvent.class))
            .thenReturn(callTranscribedEvent);

        // Act
        auditEventListener.handleCallTranscribed(eventJson);

        // Assert
        verify(objectMapper).readValue(eventJson, CallTranscribedEvent.class);
        verifyNoInteractions(auditService);  // Should not process yet (waiting for other events)
    }

    @Test
    void handleSentimentAnalyzed_ValidEvent_StoresEventInMemory() throws Exception {
        // Arrange
        String eventJson = "{\"eventId\":\"test\"}";
        when(objectMapper.readValue(eventJson, SentimentAnalyzedEvent.class))
            .thenReturn(sentimentAnalyzedEvent);

        // Act
        auditEventListener.handleSentimentAnalyzed(eventJson);

        // Assert
        verify(objectMapper).readValue(eventJson, SentimentAnalyzedEvent.class);
        verifyNoInteractions(auditService);  // Should not process yet
    }

    @Test
    void handleVocAnalyzed_ValidEvent_StoresEventInMemory() throws Exception {
        // Arrange
        String eventJson = "{\"eventId\":\"test\"}";
        when(objectMapper.readValue(eventJson, VocAnalyzedEvent.class))
            .thenReturn(vocAnalyzedEvent);

        // Act
        auditEventListener.handleVocAnalyzed(eventJson);

        // Assert
        verify(objectMapper).readValue(eventJson, VocAnalyzedEvent.class);
        verifyNoInteractions(auditService);  // Should not process yet
    }

    @Test
    void handleAllThreeEvents_TriggersAuditProcessing() throws Exception {
        // Arrange
        String transcribedJson = "{\"eventId\":\"1\"}";
        String sentimentJson = "{\"eventId\":\"2\"}";
        String vocJson = "{\"eventId\":\"3\"}";

        when(objectMapper.readValue(transcribedJson, CallTranscribedEvent.class))
            .thenReturn(callTranscribedEvent);
        when(objectMapper.readValue(sentimentJson, SentimentAnalyzedEvent.class))
            .thenReturn(sentimentAnalyzedEvent);
        when(objectMapper.readValue(vocJson, VocAnalyzedEvent.class))
            .thenReturn(vocAnalyzedEvent);

        AuditResult mockResult = createMockAuditResult();
        when(auditService.auditCall(any(), any(), any())).thenReturn(mockResult);

        // Act - receive all three events
        auditEventListener.handleCallTranscribed(transcribedJson);
        auditEventListener.handleSentimentAnalyzed(sentimentJson);
        auditEventListener.handleVocAnalyzed(vocJson);  // This should trigger processing

        // Assert
        verify(auditService).auditCall(
            eq(callTranscribedEvent.getPayload()),
            eq(sentimentAnalyzedEvent.getPayload()),
            eq(vocAnalyzedEvent.getPayload())
        );
    }

    @Test
    void handleAllThreeEvents_DifferentOrder_TriggersAuditProcessing() throws Exception {
        // Arrange
        String transcribedJson = "{\"eventId\":\"1\"}";
        String sentimentJson = "{\"eventId\":\"2\"}";
        String vocJson = "{\"eventId\":\"3\"}";

        when(objectMapper.readValue(transcribedJson, CallTranscribedEvent.class))
            .thenReturn(callTranscribedEvent);
        when(objectMapper.readValue(sentimentJson, SentimentAnalyzedEvent.class))
            .thenReturn(sentimentAnalyzedEvent);
        when(objectMapper.readValue(vocJson, VocAnalyzedEvent.class))
            .thenReturn(vocAnalyzedEvent);

        AuditResult mockResult = createMockAuditResult();
        when(auditService.auditCall(any(), any(), any())).thenReturn(mockResult);

        // Act - receive events in different order
        auditEventListener.handleSentimentAnalyzed(sentimentJson);
        auditEventListener.handleVocAnalyzed(vocJson);
        auditEventListener.handleCallTranscribed(transcribedJson);  // This should trigger processing

        // Assert
        verify(auditService).auditCall(
            eq(callTranscribedEvent.getPayload()),
            eq(sentimentAnalyzedEvent.getPayload()),
            eq(vocAnalyzedEvent.getPayload())
        );
    }

    @Test
    void handleTwoEvents_DoesNotTriggerProcessing() throws Exception {
        // Arrange
        String transcribedJson = "{\"eventId\":\"1\"}";
        String sentimentJson = "{\"eventId\":\"2\"}";

        when(objectMapper.readValue(transcribedJson, CallTranscribedEvent.class))
            .thenReturn(callTranscribedEvent);
        when(objectMapper.readValue(sentimentJson, SentimentAnalyzedEvent.class))
            .thenReturn(sentimentAnalyzedEvent);

        // Act - receive only two events
        auditEventListener.handleCallTranscribed(transcribedJson);
        auditEventListener.handleSentimentAnalyzed(sentimentJson);

        // Assert
        verifyNoInteractions(auditService);  // Should not process yet
    }

    @Test
    void handleCallTranscribed_InvalidJson_HandlesGracefully() throws Exception {
        // Arrange
        String invalidJson = "{invalid}";
        when(objectMapper.readValue(invalidJson, CallTranscribedEvent.class))
            .thenThrow(new RuntimeException("Invalid JSON"));

        // Act - should not throw exception
        auditEventListener.handleCallTranscribed(invalidJson);

        // Assert
        verifyNoInteractions(auditService);
    }

    @Test
    void handleSentimentAnalyzed_InvalidJson_HandlesGracefully() throws Exception {
        // Arrange
        String invalidJson = "{invalid}";
        when(objectMapper.readValue(invalidJson, SentimentAnalyzedEvent.class))
            .thenThrow(new RuntimeException("Invalid JSON"));

        // Act - should not throw exception
        auditEventListener.handleSentimentAnalyzed(invalidJson);

        // Assert
        verifyNoInteractions(auditService);
    }

    @Test
    void handleVocAnalyzed_InvalidJson_HandlesGracefully() throws Exception {
        // Arrange
        String invalidJson = "{invalid}";
        when(objectMapper.readValue(invalidJson, VocAnalyzedEvent.class))
            .thenThrow(new RuntimeException("Invalid JSON"));

        // Act - should not throw exception
        auditEventListener.handleVocAnalyzed(invalidJson);

        // Assert
        verifyNoInteractions(auditService);
    }

    @Test
    void handleAllThreeEvents_AuditServiceThrows_KeepsEventsInCache() throws Exception {
        // Arrange
        String transcribedJson = "{\"eventId\":\"1\"}";
        String sentimentJson = "{\"eventId\":\"2\"}";
        String vocJson = "{\"eventId\":\"3\"}";

        when(objectMapper.readValue(transcribedJson, CallTranscribedEvent.class))
            .thenReturn(callTranscribedEvent);
        when(objectMapper.readValue(sentimentJson, SentimentAnalyzedEvent.class))
            .thenReturn(sentimentAnalyzedEvent);
        when(objectMapper.readValue(vocJson, VocAnalyzedEvent.class))
            .thenReturn(vocAnalyzedEvent);

        when(auditService.auditCall(any(), any(), any()))
            .thenThrow(new RuntimeException("Audit processing failed"));

        // Act - should not throw exception
        auditEventListener.handleCallTranscribed(transcribedJson);
        auditEventListener.handleSentimentAnalyzed(sentimentJson);
        auditEventListener.handleVocAnalyzed(vocJson);

        // Assert
        verify(auditService).auditCall(any(), any(), any());
        // Events should remain in cache for potential retry
    }

    @Test
    void handleMultipleCalls_ProcessesEachIndependently() throws Exception {
        // Arrange - First call
        UUID callId1 = UUID.randomUUID();
        CallTranscribedEvent event1 = createCallTranscribedEventWithId(callId1);
        SentimentAnalyzedEvent sentiment1 = createSentimentAnalyzedEventWithId(callId1);
        VocAnalyzedEvent voc1 = createVocAnalyzedEventWithId(callId1);

        // Arrange - Second call
        UUID callId2 = UUID.randomUUID();
        CallTranscribedEvent event2 = createCallTranscribedEventWithId(callId2);
        SentimentAnalyzedEvent sentiment2 = createSentimentAnalyzedEventWithId(callId2);
        VocAnalyzedEvent voc2 = createVocAnalyzedEventWithId(callId2);

        when(objectMapper.readValue(eq("event1"), eq(CallTranscribedEvent.class)))
            .thenReturn(event1);
        when(objectMapper.readValue(eq("sentiment1"), eq(SentimentAnalyzedEvent.class)))
            .thenReturn(sentiment1);
        when(objectMapper.readValue(eq("voc1"), eq(VocAnalyzedEvent.class)))
            .thenReturn(voc1);
        when(objectMapper.readValue(eq("event2"), eq(CallTranscribedEvent.class)))
            .thenReturn(event2);
        when(objectMapper.readValue(eq("sentiment2"), eq(SentimentAnalyzedEvent.class)))
            .thenReturn(sentiment2);
        when(objectMapper.readValue(eq("voc2"), eq(VocAnalyzedEvent.class)))
            .thenReturn(voc2);

        AuditResult mockResult = createMockAuditResult();
        when(auditService.auditCall(any(), any(), any())).thenReturn(mockResult);

        // Act - Process first call
        auditEventListener.handleCallTranscribed("event1");
        auditEventListener.handleSentimentAnalyzed("sentiment1");
        auditEventListener.handleVocAnalyzed("voc1");

        // Process second call
        auditEventListener.handleCallTranscribed("event2");
        auditEventListener.handleSentimentAnalyzed("sentiment2");
        auditEventListener.handleVocAnalyzed("voc2");

        // Assert
        verify(auditService, times(2)).auditCall(any(), any(), any());
    }

    // Helper methods

    private CallTranscribedEvent createCallTranscribedEvent() {
        CallTranscribedEvent event = new CallTranscribedEvent();
        event.setEventId(UUID.randomUUID());
        event.setEventType("CallTranscribed");
        event.setAggregateId(testCallId);
        event.setTimestamp(OffsetDateTime.now());

        CallTranscribedEvent.TranscriptionPayload payload =
            new CallTranscribedEvent.TranscriptionPayload();
        payload.setCallId(testCallId);
        payload.setFullText("Hello! How can I help you?");
        payload.setLanguage("en-US");
        payload.setConfidence(BigDecimal.valueOf(0.95));
        payload.setWordCount(20);
        payload.setSegments(new ArrayList<>());

        event.setPayload(payload);
        return event;
    }

    private CallTranscribedEvent createCallTranscribedEventWithId(UUID callId) {
        CallTranscribedEvent event = new CallTranscribedEvent();
        event.setEventId(UUID.randomUUID());
        event.setEventType("CallTranscribed");
        event.setAggregateId(callId);
        event.setTimestamp(OffsetDateTime.now());

        CallTranscribedEvent.TranscriptionPayload payload =
            new CallTranscribedEvent.TranscriptionPayload();
        payload.setCallId(callId);
        payload.setFullText("Test transcription");
        payload.setSegments(new ArrayList<>());

        event.setPayload(payload);
        return event;
    }

    private SentimentAnalyzedEvent createSentimentAnalyzedEvent() {
        SentimentAnalyzedEvent event = new SentimentAnalyzedEvent();
        event.setEventId(UUID.randomUUID());
        event.setEventType("SentimentAnalyzed");
        event.setAggregateId(testCallId);
        event.setTimestamp(OffsetDateTime.now());

        SentimentAnalyzedEvent.SentimentPayload payload =
            new SentimentAnalyzedEvent.SentimentPayload();
        payload.setCallId(testCallId);
        payload.setOverallSentiment("positive");
        payload.setSentimentScore(BigDecimal.valueOf(0.8));
        payload.setEscalationDetected(false);
        payload.setSegmentSentiments(new ArrayList<>());

        event.setPayload(payload);
        return event;
    }

    private SentimentAnalyzedEvent createSentimentAnalyzedEventWithId(UUID callId) {
        SentimentAnalyzedEvent event = new SentimentAnalyzedEvent();
        event.setEventId(UUID.randomUUID());
        event.setEventType("SentimentAnalyzed");
        event.setAggregateId(callId);
        event.setTimestamp(OffsetDateTime.now());

        SentimentAnalyzedEvent.SentimentPayload payload =
            new SentimentAnalyzedEvent.SentimentPayload();
        payload.setCallId(callId);
        payload.setOverallSentiment("neutral");
        payload.setSentimentScore(BigDecimal.ZERO);
        payload.setEscalationDetected(false);
        payload.setSegmentSentiments(new ArrayList<>());

        event.setPayload(payload);
        return event;
    }

    private VocAnalyzedEvent createVocAnalyzedEvent() {
        VocAnalyzedEvent event = new VocAnalyzedEvent();
        event.setEventId(UUID.randomUUID());
        event.setEventType("VocAnalyzed");
        event.setAggregateId(testCallId);
        event.setTimestamp(OffsetDateTime.now());

        VocAnalyzedEvent.VocPayload payload = new VocAnalyzedEvent.VocPayload();
        payload.setCallId(testCallId);
        payload.setPrimaryIntent("inquiry");
        payload.setCustomerSatisfaction("high");
        payload.setPredictedChurnRisk(BigDecimal.valueOf(0.2));
        payload.setTopics(List.of("Support"));
        payload.setKeywords(List.of("help", "question"));
        payload.setActionableItems(new ArrayList<>());

        event.setPayload(payload);
        return event;
    }

    private VocAnalyzedEvent createVocAnalyzedEventWithId(UUID callId) {
        VocAnalyzedEvent event = new VocAnalyzedEvent();
        event.setEventId(UUID.randomUUID());
        event.setEventType("VocAnalyzed");
        event.setAggregateId(callId);
        event.setTimestamp(OffsetDateTime.now());

        VocAnalyzedEvent.VocPayload payload = new VocAnalyzedEvent.VocPayload();
        payload.setCallId(callId);
        payload.setPrimaryIntent("inquiry");
        payload.setCustomerSatisfaction("medium");
        payload.setPredictedChurnRisk(BigDecimal.valueOf(0.5));
        payload.setTopics(new ArrayList<>());
        payload.setKeywords(new ArrayList<>());
        payload.setActionableItems(new ArrayList<>());

        event.setPayload(payload);
        return event;
    }

    private AuditResult createMockAuditResult() {
        return AuditResult.builder()
            .id(UUID.randomUUID())
            .callId(testCallId)
            .overallScore(85)
            .complianceStatus(ComplianceStatus.passed)
            .scriptAdherence(80)
            .customerService(90)
            .resolutionEffectiveness(85)
            .flagsForReview(false)
            .processingTimeMs(1000)
            .createdAt(OffsetDateTime.now())
            .build();
    }
}
