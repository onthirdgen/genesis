package com.callaudit.voc.listener;

import com.callaudit.voc.event.CallTranscribedEvent;
import com.callaudit.voc.event.SentimentAnalyzedEvent;
import com.callaudit.voc.event.VocAnalyzedEvent;
import com.callaudit.voc.model.Intent;
import com.callaudit.voc.model.Satisfaction;
import com.callaudit.voc.model.VocInsight;
import com.callaudit.voc.service.InsightService;
import com.callaudit.voc.service.VocAnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for VocEventListener
 */
@ExtendWith(MockitoExtension.class)
class VocEventListenerTest {

    @Mock
    private VocAnalysisService vocAnalysisService;

    @Mock
    private InsightService insightService;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private VocEventListener vocEventListener;

    @Captor
    private ArgumentCaptor<VocInsight> insightCaptor;

    @Captor
    private ArgumentCaptor<String> topicCaptor;

    @Captor
    private ArgumentCaptor<String> keyCaptor;

    @Captor
    private ArgumentCaptor<String> eventJsonCaptor;

    private static final String TEST_CALL_ID = "00000000-0000-0000-0000-000000000123";
    private static final String TEST_CALL_ID_2 = "00000000-0000-0000-0000-000000000124";
    private static final String CORRELATION_ID = "correlation-456";

    @BeforeEach
    void setUp() {
        // Reset mocks between tests
        reset(vocAnalysisService, insightService, kafkaTemplate, objectMapper);
    }

    @Test
    void handleCallTranscribed_ValidEvent_StoresEventInMemory() throws Exception {
        // Arrange
        String eventJson = createCallTranscribedEventJson();
        CallTranscribedEvent event = createCallTranscribedEvent();

        when(objectMapper.readValue(eventJson, CallTranscribedEvent.class)).thenReturn(event);

        // Act
        vocEventListener.handleCallTranscribed(eventJson);

        // Assert
        verify(objectMapper).readValue(eventJson, CallTranscribedEvent.class);
        verifyNoInteractions(vocAnalysisService);
        verifyNoInteractions(insightService);
    }

    @Test
    void handleSentimentAnalyzed_ValidEvent_StoresEventInMemory() throws Exception {
        // Arrange
        String eventJson = createSentimentAnalyzedEventJson();
        SentimentAnalyzedEvent event = createSentimentAnalyzedEvent();

        when(objectMapper.readValue(eventJson, SentimentAnalyzedEvent.class)).thenReturn(event);

        // Act
        vocEventListener.handleSentimentAnalyzed(eventJson);

        // Assert
        verify(objectMapper).readValue(eventJson, SentimentAnalyzedEvent.class);
        verifyNoInteractions(vocAnalysisService);
        verifyNoInteractions(insightService);
    }

    @Test
    void handleBothEvents_ProcessesVocAnalysis() throws Exception {
        // Arrange
        String transcriptionJson = createCallTranscribedEventJson();
        String sentimentJson = createSentimentAnalyzedEventJson();

        CallTranscribedEvent transcriptionEvent = createCallTranscribedEvent();
        SentimentAnalyzedEvent sentimentEvent = createSentimentAnalyzedEvent();

        when(objectMapper.readValue(transcriptionJson, CallTranscribedEvent.class))
            .thenReturn(transcriptionEvent);
        when(objectMapper.readValue(sentimentJson, SentimentAnalyzedEvent.class))
            .thenReturn(sentimentEvent);

        VocAnalysisService.VocAnalysisResult analysisResult = createAnalysisResult();
        when(vocAnalysisService.analyzeTranscription(anyString(), any(SentimentAnalyzedEvent.SentimentPayload.class)))
            .thenReturn(analysisResult);

        when(insightService.saveInsight(any(VocInsight.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VocAnalyzedEvent vocAnalyzedEvent = createVocAnalyzedEvent();
        when(objectMapper.writeValueAsString(any(VocAnalyzedEvent.class)))
            .thenReturn("{\"eventType\":\"VocAnalyzed\"}");

        // Act
        vocEventListener.handleCallTranscribed(transcriptionJson);
        vocEventListener.handleSentimentAnalyzed(sentimentJson);

        // Assert - Analysis should have been triggered
        verify(vocAnalysisService).analyzeTranscription(
            eq("This is a test transcription with billing problem"),
            any(SentimentAnalyzedEvent.SentimentPayload.class)
        );
        verify(insightService).saveInsight(insightCaptor.capture());
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), eventJsonCaptor.capture());

        VocInsight savedInsight = insightCaptor.getValue();
        assertEquals(UUID.fromString(TEST_CALL_ID), savedInsight.getCallId());
        assertEquals(Intent.complaint, savedInsight.getPrimaryIntent());
        assertEquals(Satisfaction.low, savedInsight.getCustomerSatisfaction());

        assertEquals("calls.voc-analyzed", topicCaptor.getValue());
        assertEquals(TEST_CALL_ID, keyCaptor.getValue());
    }

    @Test
    void handleBothEvents_ReverseOrder_ProcessesVocAnalysis() throws Exception {
        // Arrange - This time sentiment arrives first
        String transcriptionJson = createCallTranscribedEventJson();
        String sentimentJson = createSentimentAnalyzedEventJson();

        CallTranscribedEvent transcriptionEvent = createCallTranscribedEvent();
        SentimentAnalyzedEvent sentimentEvent = createSentimentAnalyzedEvent();

        when(objectMapper.readValue(transcriptionJson, CallTranscribedEvent.class))
            .thenReturn(transcriptionEvent);
        when(objectMapper.readValue(sentimentJson, SentimentAnalyzedEvent.class))
            .thenReturn(sentimentEvent);

        VocAnalysisService.VocAnalysisResult analysisResult = createAnalysisResult();
        when(vocAnalysisService.analyzeTranscription(anyString(), any(SentimentAnalyzedEvent.SentimentPayload.class)))
            .thenReturn(analysisResult);

        when(insightService.saveInsight(any(VocInsight.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any(VocAnalyzedEvent.class)))
            .thenReturn("{\"eventType\":\"VocAnalyzed\"}");

        // Act - Sentiment arrives first, then transcription
        vocEventListener.handleSentimentAnalyzed(sentimentJson);
        vocEventListener.handleCallTranscribed(transcriptionJson);

        // Assert - Analysis should still be triggered
        verify(vocAnalysisService).analyzeTranscription(anyString(), any(SentimentAnalyzedEvent.SentimentPayload.class));
        verify(insightService).saveInsight(any(VocInsight.class));
        verify(kafkaTemplate).send(anyString(), anyString(), anyString());
    }

    @Test
    void handleCallTranscribed_InvalidJson_DoesNotThrowException() throws Exception {
        // Arrange
        String invalidJson = "invalid json";
        when(objectMapper.readValue(invalidJson, CallTranscribedEvent.class))
            .thenThrow(new RuntimeException("JSON parse error"));

        // Act - Should not throw exception
        assertDoesNotThrow(() -> vocEventListener.handleCallTranscribed(invalidJson));

        // Assert
        verifyNoInteractions(vocAnalysisService);
        verifyNoInteractions(insightService);
    }

    @Test
    void handleSentimentAnalyzed_InvalidJson_DoesNotThrowException() throws Exception {
        // Arrange
        String invalidJson = "invalid json";
        when(objectMapper.readValue(invalidJson, SentimentAnalyzedEvent.class))
            .thenThrow(new RuntimeException("JSON parse error"));

        // Act - Should not throw exception
        assertDoesNotThrow(() -> vocEventListener.handleSentimentAnalyzed(invalidJson));

        // Assert
        verifyNoInteractions(vocAnalysisService);
        verifyNoInteractions(insightService);
    }

    @Test
    void processVocAnalysis_AnalysisServiceThrowsException_DoesNotThrowException() throws Exception {
        // Arrange
        String transcriptionJson = createCallTranscribedEventJson();
        String sentimentJson = createSentimentAnalyzedEventJson();

        CallTranscribedEvent transcriptionEvent = createCallTranscribedEvent();
        SentimentAnalyzedEvent sentimentEvent = createSentimentAnalyzedEvent();

        when(objectMapper.readValue(transcriptionJson, CallTranscribedEvent.class))
            .thenReturn(transcriptionEvent);
        when(objectMapper.readValue(sentimentJson, SentimentAnalyzedEvent.class))
            .thenReturn(sentimentEvent);

        when(vocAnalysisService.analyzeTranscription(anyString(), any(SentimentAnalyzedEvent.SentimentPayload.class)))
            .thenThrow(new RuntimeException("Analysis error"));

        // Act - Should not throw exception
        vocEventListener.handleCallTranscribed(transcriptionJson);
        assertDoesNotThrow(() -> vocEventListener.handleSentimentAnalyzed(sentimentJson));

        // Assert
        verify(vocAnalysisService).analyzeTranscription(anyString(), any(SentimentAnalyzedEvent.SentimentPayload.class));
        verifyNoInteractions(insightService);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void processVocAnalysis_InsightServiceThrowsException_DoesNotThrowException() throws Exception {
        // Arrange
        String transcriptionJson = createCallTranscribedEventJson();
        String sentimentJson = createSentimentAnalyzedEventJson();

        CallTranscribedEvent transcriptionEvent = createCallTranscribedEvent();
        SentimentAnalyzedEvent sentimentEvent = createSentimentAnalyzedEvent();

        when(objectMapper.readValue(transcriptionJson, CallTranscribedEvent.class))
            .thenReturn(transcriptionEvent);
        when(objectMapper.readValue(sentimentJson, SentimentAnalyzedEvent.class))
            .thenReturn(sentimentEvent);

        VocAnalysisService.VocAnalysisResult analysisResult = createAnalysisResult();
        when(vocAnalysisService.analyzeTranscription(anyString(), any(SentimentAnalyzedEvent.SentimentPayload.class)))
            .thenReturn(analysisResult);

        when(insightService.saveInsight(any(VocInsight.class)))
            .thenThrow(new RuntimeException("Database error"));

        // Act - Should not throw exception
        vocEventListener.handleCallTranscribed(transcriptionJson);
        assertDoesNotThrow(() -> vocEventListener.handleSentimentAnalyzed(sentimentJson));

        // Assert
        verify(vocAnalysisService).analyzeTranscription(anyString(), any(SentimentAnalyzedEvent.SentimentPayload.class));
        verify(insightService).saveInsight(any(VocInsight.class));
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void processVocAnalysis_KafkaPublishThrowsException_DoesNotThrowException() throws Exception {
        // Arrange
        String transcriptionJson = createCallTranscribedEventJson();
        String sentimentJson = createSentimentAnalyzedEventJson();

        CallTranscribedEvent transcriptionEvent = createCallTranscribedEvent();
        SentimentAnalyzedEvent sentimentEvent = createSentimentAnalyzedEvent();

        when(objectMapper.readValue(transcriptionJson, CallTranscribedEvent.class))
            .thenReturn(transcriptionEvent);
        when(objectMapper.readValue(sentimentJson, SentimentAnalyzedEvent.class))
            .thenReturn(sentimentEvent);

        VocAnalysisService.VocAnalysisResult analysisResult = createAnalysisResult();
        when(vocAnalysisService.analyzeTranscription(anyString(), any(SentimentAnalyzedEvent.SentimentPayload.class)))
            .thenReturn(analysisResult);

        when(insightService.saveInsight(any(VocInsight.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any(VocAnalyzedEvent.class)))
            .thenThrow(new RuntimeException("JSON serialization error"));

        // Act - Should not throw exception
        vocEventListener.handleCallTranscribed(transcriptionJson);
        assertDoesNotThrow(() -> vocEventListener.handleSentimentAnalyzed(sentimentJson));

        // Assert
        verify(vocAnalysisService).analyzeTranscription(anyString(), any(SentimentAnalyzedEvent.SentimentPayload.class));
        verify(insightService).saveInsight(any(VocInsight.class));
        verify(objectMapper).writeValueAsString(any(VocAnalyzedEvent.class));
    }

    @Test
    void processVocAnalysis_MultipleCalls_ProcessesIndependently() throws Exception {
        // Arrange
        String callId1 = "00000000-0000-0000-0000-000000000001";
        String callId2 = "00000000-0000-0000-0000-000000000002";
        String call1TranscriptionJson = createCallTranscribedEventJson(callId1);
        String call1SentimentJson = createSentimentAnalyzedEventJson(callId1);
        String call2TranscriptionJson = createCallTranscribedEventJson(callId2);
        String call2SentimentJson = createSentimentAnalyzedEventJson(callId2);

        when(objectMapper.readValue(anyString(), eq(CallTranscribedEvent.class)))
            .thenAnswer(invocation -> {
                String arg = invocation.getArgument(0);
                if (arg.contains(callId1)) {
                    return createCallTranscribedEvent(callId1);
                } else {
                    return createCallTranscribedEvent(callId2);
                }
            });

        when(objectMapper.readValue(anyString(), eq(SentimentAnalyzedEvent.class)))
            .thenAnswer(invocation -> {
                String arg = invocation.getArgument(0);
                if (arg.contains(callId1)) {
                    return createSentimentAnalyzedEvent(callId1);
                } else {
                    return createSentimentAnalyzedEvent(callId2);
                }
            });

        VocAnalysisService.VocAnalysisResult analysisResult = createAnalysisResult();
        when(vocAnalysisService.analyzeTranscription(anyString(), any(SentimentAnalyzedEvent.SentimentPayload.class)))
            .thenReturn(analysisResult);

        when(insightService.saveInsight(any(VocInsight.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any(VocAnalyzedEvent.class)))
            .thenReturn("{\"eventType\":\"VocAnalyzed\"}");

        // Act
        vocEventListener.handleCallTranscribed(call1TranscriptionJson);
        vocEventListener.handleCallTranscribed(call2TranscriptionJson);
        vocEventListener.handleSentimentAnalyzed(call1SentimentJson);
        vocEventListener.handleSentimentAnalyzed(call2SentimentJson);

        // Assert - Both calls should be processed
        verify(vocAnalysisService, times(2)).analyzeTranscription(anyString(), any(SentimentAnalyzedEvent.SentimentPayload.class));
        verify(insightService, times(2)).saveInsight(any(VocInsight.class));
        verify(kafkaTemplate, times(2)).send(anyString(), anyString(), anyString());
    }

    // Helper methods

    private String createCallTranscribedEventJson() {
        return "{\"eventId\":\"event-123\",\"eventType\":\"CallTranscribed\",\"aggregateId\":\"" + TEST_CALL_ID + "\"}";
    }

    private String createCallTranscribedEventJson(String callId) {
        return "{\"eventId\":\"event-123\",\"eventType\":\"CallTranscribed\",\"aggregateId\":\"" + callId + "\"}";
    }

    private String createSentimentAnalyzedEventJson() {
        return "{\"eventId\":\"event-456\",\"eventType\":\"SentimentAnalyzed\",\"aggregateId\":\"" + TEST_CALL_ID + "\"}";
    }

    private String createSentimentAnalyzedEventJson(String callId) {
        return "{\"eventId\":\"event-456\",\"eventType\":\"SentimentAnalyzed\",\"aggregateId\":\"" + callId + "\"}";
    }

    private CallTranscribedEvent createCallTranscribedEvent() {
        return createCallTranscribedEvent(TEST_CALL_ID);
    }

    private CallTranscribedEvent createCallTranscribedEvent(String callId) {
        CallTranscribedEvent.TranscriptionPayload payload = CallTranscribedEvent.TranscriptionPayload.builder()
            .callId(callId)
            .transcriptionText("This is a test transcription with billing problem")
            .language("en")
            .confidence(0.95)
            .durationSeconds(120)
            .build();

        return CallTranscribedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("CallTranscribed")
            .aggregateId(callId)
            .aggregateType("Call")
            .timestamp(Instant.now())
            .version(1)
            .correlationId(CORRELATION_ID)
            .metadata(Map.of("service", "transcription-service"))
            .payload(payload)
            .build();
    }

    private SentimentAnalyzedEvent createSentimentAnalyzedEvent() {
        return createSentimentAnalyzedEvent(TEST_CALL_ID);
    }

    private SentimentAnalyzedEvent createSentimentAnalyzedEvent(String callId) {
        SentimentAnalyzedEvent.SentimentPayload payload = SentimentAnalyzedEvent.SentimentPayload.builder()
            .callId(callId)
            .overallSentiment("NEGATIVE")
            .sentimentScore(-0.8)
            .positiveScore(0.1)
            .negativeScore(0.8)
            .neutralScore(0.1)
            .build();

        return SentimentAnalyzedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("SentimentAnalyzed")
            .aggregateId(callId)
            .aggregateType("Call")
            .timestamp(Instant.now())
            .version(1)
            .correlationId(CORRELATION_ID)
            .metadata(Map.of("service", "sentiment-service"))
            .payload(payload)
            .build();
    }

    private VocAnalysisService.VocAnalysisResult createAnalysisResult() {
        return VocAnalysisService.VocAnalysisResult.builder()
            .primaryIntent(Intent.complaint)
            .topics(List.of("Billing", "Technical Support"))
            .keywords(List.of("billing", "problem", "charge"))
            .customerSatisfaction(Satisfaction.low)
            .predictedChurnRisk(0.8)
            .actionableItems(List.of("Contact customer", "Review billing"))
            .summary("Customer complaint about billing")
            .build();
    }

    private VocAnalyzedEvent createVocAnalyzedEvent() {
        VocAnalyzedEvent.VocPayload payload = VocAnalyzedEvent.VocPayload.builder()
            .callId(TEST_CALL_ID)
            .primaryIntent(Intent.complaint)
            .topics(List.of("Billing", "Technical Support"))
            .keywords(List.of("billing", "problem", "charge"))
            .customerSatisfaction(Satisfaction.low)
            .predictedChurnRisk(0.8)
            .actionableItems(List.of("Contact customer", "Review billing"))
            .summary("Customer complaint about billing")
            .build();

        return VocAnalyzedEvent.create(TEST_CALL_ID, payload, CORRELATION_ID);
    }
}
