package com.callaudit.analytics.listener;

import com.callaudit.analytics.event.*;
import com.callaudit.analytics.service.AgentPerformanceService;
import com.callaudit.analytics.service.DashboardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AnalyticsEventListener
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsEventListenerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private DashboardService dashboardService;

    @Mock
    private AgentPerformanceService agentPerformanceService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private AnalyticsEventListener listener;

    private static final String TEST_CALL_ID = "call-123";
    private static final String TEST_AGENT_ID = "agent-001";

    @BeforeEach
    void setUp() {
        reset(objectMapper, dashboardService, agentPerformanceService, acknowledgment);
    }

    @Test
    void handleCallReceived_ValidEvent_IncrementsCounter() throws Exception {
        // Arrange
        String eventJson = "{\"eventType\":\"CallReceived\"}";
        CallReceivedEvent event = createCallReceivedEvent();

        when(objectMapper.readValue(eventJson, CallReceivedEvent.class)).thenReturn(event);

        // Act
        listener.handleCallReceived(eventJson, acknowledgment);

        // Assert
        verify(objectMapper).readValue(eventJson, CallReceivedEvent.class);
        verify(dashboardService).incrementCounter("total_calls");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleCallReceived_InvalidJson_AcknowledgesAnyway() throws Exception {
        // Arrange
        String invalidJson = "invalid json";
        when(objectMapper.readValue(invalidJson, CallReceivedEvent.class))
                .thenThrow(new RuntimeException("JSON parse error"));

        // Act
        assertDoesNotThrow(() -> listener.handleCallReceived(invalidJson, acknowledgment));

        // Assert
        verify(acknowledgment).acknowledge();
        verifyNoInteractions(dashboardService);
    }

    @Test
    void handleCallTranscribed_ValidEvent_IncrementsCounter() throws Exception {
        // Arrange
        String eventJson = "{\"eventType\":\"CallTranscribed\"}";
        CallTranscribedEvent event = createCallTranscribedEvent();

        when(objectMapper.readValue(eventJson, CallTranscribedEvent.class)).thenReturn(event);

        // Act
        listener.handleCallTranscribed(eventJson, acknowledgment);

        // Assert
        verify(dashboardService).incrementCounter("transcribed_calls");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleCallTranscribed_InvalidJson_AcknowledgesAnyway() throws Exception {
        // Arrange
        String invalidJson = "invalid json";
        when(objectMapper.readValue(invalidJson, CallTranscribedEvent.class))
                .thenThrow(new RuntimeException("JSON parse error"));

        // Act
        assertDoesNotThrow(() -> listener.handleCallTranscribed(invalidJson, acknowledgment));

        // Assert
        verify(acknowledgment).acknowledge();
        verifyNoInteractions(dashboardService);
    }

    @Test
    void handleSentimentAnalyzed_ValidEventWithAgentId_UpdatesMetrics() throws Exception {
        // Arrange
        String eventJson = "{\"eventType\":\"SentimentAnalyzed\"}";
        SentimentAnalyzedEvent event = createSentimentAnalyzedEvent();

        when(objectMapper.readValue(eventJson, SentimentAnalyzedEvent.class)).thenReturn(event);

        // Act
        listener.handleSentimentAnalyzed(eventJson, acknowledgment);

        // Assert
        verify(dashboardService).incrementCounter("sentiment_analyzed");
        verify(agentPerformanceService).updateAgentMetrics(
                eq(TEST_AGENT_ID),
                isNull(),
                eq(0.75),
                eq(0.82),
                isNull(),
                isNull()
        );
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleSentimentAnalyzed_NoAgentId_OnlyIncrementsCounter() throws Exception {
        // Arrange
        String eventJson = "{\"eventType\":\"SentimentAnalyzed\"}";
        SentimentAnalyzedEvent event = createSentimentAnalyzedEventWithoutAgent();

        when(objectMapper.readValue(eventJson, SentimentAnalyzedEvent.class)).thenReturn(event);

        // Act
        listener.handleSentimentAnalyzed(eventJson, acknowledgment);

        // Assert
        verify(dashboardService).incrementCounter("sentiment_analyzed");
        verifyNoInteractions(agentPerformanceService);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleSentimentAnalyzed_InvalidJson_AcknowledgesAnyway() throws Exception {
        // Arrange
        String invalidJson = "invalid json";
        when(objectMapper.readValue(invalidJson, SentimentAnalyzedEvent.class))
                .thenThrow(new RuntimeException("JSON parse error"));

        // Act
        assertDoesNotThrow(() -> listener.handleSentimentAnalyzed(invalidJson, acknowledgment));

        // Assert
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleVocAnalyzed_ValidEvent_TracksChurnRiskAndTopics() throws Exception {
        // Arrange
        String eventJson = "{\"eventType\":\"VocAnalyzed\"}";
        VocAnalyzedEvent event = createVocAnalyzedEvent();

        when(objectMapper.readValue(eventJson, VocAnalyzedEvent.class)).thenReturn(event);

        // Act
        listener.handleVocAnalyzed(eventJson, acknowledgment);

        // Assert
        verify(dashboardService).incrementCounter("voc_analyzed");
        verify(dashboardService).incrementChurnRisk("HIGH");
        verify(dashboardService, times(2)).trackTopic(anyString());
        verify(dashboardService).trackTopic("Billing");
        verify(dashboardService).trackTopic("Customer Service");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleVocAnalyzed_WithIssues_TracksIssueCategories() throws Exception {
        // Arrange
        String eventJson = "{\"eventType\":\"VocAnalyzed\"}";
        VocAnalyzedEvent event = createVocAnalyzedEventWithIssues();

        when(objectMapper.readValue(eventJson, VocAnalyzedEvent.class)).thenReturn(event);

        // Act
        listener.handleVocAnalyzed(eventJson, acknowledgment);

        // Assert
        verify(dashboardService).trackIssue("billing");
        verify(dashboardService).trackIssue("technical");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleVocAnalyzed_WithAgentId_UpdatesAgentChurnRisk() throws Exception {
        // Arrange
        String eventJson = "{\"eventType\":\"VocAnalyzed\"}";
        VocAnalyzedEvent event = createVocAnalyzedEventWithAgent();

        when(objectMapper.readValue(eventJson, VocAnalyzedEvent.class)).thenReturn(event);

        // Act
        listener.handleVocAnalyzed(eventJson, acknowledgment);

        // Assert
        verify(agentPerformanceService).updateAgentMetrics(
                eq(TEST_AGENT_ID),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(0.85)
        );
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleVocAnalyzed_InvalidJson_AcknowledgesAnyway() throws Exception {
        // Arrange
        String invalidJson = "invalid json";
        when(objectMapper.readValue(invalidJson, VocAnalyzedEvent.class))
                .thenThrow(new RuntimeException("JSON parse error"));

        // Act
        assertDoesNotThrow(() -> listener.handleVocAnalyzed(invalidJson, acknowledgment));

        // Assert
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleCallAudited_ValidEvent_TracksCompliance() throws Exception {
        // Arrange
        String eventJson = "{\"eventType\":\"CallAudited\"}";
        CallAuditedEvent event = createCallAuditedEvent(true);

        when(objectMapper.readValue(eventJson, CallAuditedEvent.class)).thenReturn(event);

        // Act
        listener.handleCallAudited(eventJson, acknowledgment);

        // Assert
        verify(dashboardService).incrementCounter("calls_audited");
        verify(dashboardService).incrementCounter("compliance_passed");
        verify(dashboardService, never()).incrementCounter("compliance_failed");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleCallAudited_FailedCompliance_TracksFailure() throws Exception {
        // Arrange
        String eventJson = "{\"eventType\":\"CallAudited\"}";
        CallAuditedEvent event = createCallAuditedEvent(false);

        when(objectMapper.readValue(eventJson, CallAuditedEvent.class)).thenReturn(event);

        // Act
        listener.handleCallAudited(eventJson, acknowledgment);

        // Assert
        verify(dashboardService).incrementCounter("calls_audited");
        verify(dashboardService).incrementCounter("compliance_failed");
        verify(dashboardService, never()).incrementCounter("compliance_passed");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleCallAudited_WithAgentId_UpdatesAgentMetrics() throws Exception {
        // Arrange
        String eventJson = "{\"eventType\":\"CallAudited\"}";
        CallAuditedEvent event = createCallAuditedEventWithAgent(true);

        when(objectMapper.readValue(eventJson, CallAuditedEvent.class)).thenReturn(event);

        // Act
        listener.handleCallAudited(eventJson, acknowledgment);

        // Assert
        verify(agentPerformanceService).updateAgentMetrics(
                eq(TEST_AGENT_ID),
                eq(0.88),
                isNull(),
                isNull(),
                eq(1.0), // Compliance passed = 1.0
                isNull()
        );
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleCallAudited_WithAgentIdFailedCompliance_UpdatesWithZero() throws Exception {
        // Arrange
        String eventJson = "{\"eventType\":\"CallAudited\"}";
        CallAuditedEvent event = createCallAuditedEventWithAgent(false);

        when(objectMapper.readValue(eventJson, CallAuditedEvent.class)).thenReturn(event);

        // Act
        listener.handleCallAudited(eventJson, acknowledgment);

        // Assert
        verify(agentPerformanceService).updateAgentMetrics(
                eq(TEST_AGENT_ID),
                eq(0.88),
                isNull(),
                isNull(),
                eq(0.0), // Compliance failed = 0.0
                isNull()
        );
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleCallAudited_InvalidJson_AcknowledgesAnyway() throws Exception {
        // Arrange
        String invalidJson = "invalid json";
        when(objectMapper.readValue(invalidJson, CallAuditedEvent.class))
                .thenThrow(new RuntimeException("JSON parse error"));

        // Act
        assertDoesNotThrow(() -> listener.handleCallAudited(invalidJson, acknowledgment));

        // Assert
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleVocAnalyzed_NullChurnRisk_DoesNotTrackChurnRisk() throws Exception {
        // Arrange
        String eventJson = "{\"eventType\":\"VocAnalyzed\"}";
        VocAnalyzedEvent event = createVocAnalyzedEventWithoutChurnRisk();

        when(objectMapper.readValue(eventJson, VocAnalyzedEvent.class)).thenReturn(event);

        // Act
        listener.handleVocAnalyzed(eventJson, acknowledgment);

        // Assert
        verify(dashboardService).incrementCounter("voc_analyzed");
        verify(dashboardService, never()).incrementChurnRisk(anyString());
        verify(acknowledgment).acknowledge();
    }

    // Helper methods

    private CallReceivedEvent createCallReceivedEvent() {
        CallReceivedEvent.Payload payload = CallReceivedEvent.Payload.builder()
                .callId(TEST_CALL_ID)
                .customerId("customer-123")
                .agentId(TEST_AGENT_ID)
                .build();

        return CallReceivedEvent.builder()
                .eventId("event-123")
                .eventType("CallReceived")
                .aggregateId(TEST_CALL_ID)
                .payload(payload)
                .build();
    }

    private CallTranscribedEvent createCallTranscribedEvent() {
        CallTranscribedEvent.Transcription transcription = CallTranscribedEvent.Transcription.builder()
                .fullText("This is a test transcription")
                .language("en")
                .confidence(0.95)
                .build();

        CallTranscribedEvent.Payload payload = CallTranscribedEvent.Payload.builder()
                .callId(TEST_CALL_ID)
                .transcription(transcription)
                .build();

        return CallTranscribedEvent.builder()
                .eventId("event-123")
                .eventType("CallTranscribed")
                .aggregateId(TEST_CALL_ID)
                .payload(payload)
                .build();
    }

    private SentimentAnalyzedEvent createSentimentAnalyzedEvent() {
        SentimentAnalyzedEvent.Payload payload = SentimentAnalyzedEvent.Payload.builder()
                .callId(TEST_CALL_ID)
                .overallSentiment("POSITIVE")
                .sentimentScore(0.75)
                .customerSatisfactionScore(0.82)
                .build();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agentId", TEST_AGENT_ID);

        return SentimentAnalyzedEvent.builder()
                .eventId("event-123")
                .eventType("SentimentAnalyzed")
                .aggregateId(TEST_CALL_ID)
                .metadata(metadata)
                .payload(payload)
                .build();
    }

    private SentimentAnalyzedEvent createSentimentAnalyzedEventWithoutAgent() {
        SentimentAnalyzedEvent.Payload payload = SentimentAnalyzedEvent.Payload.builder()
                .callId(TEST_CALL_ID)
                .overallSentiment("POSITIVE")
                .sentimentScore(0.75)
                .customerSatisfactionScore(0.82)
                .build();

        return SentimentAnalyzedEvent.builder()
                .eventId("event-123")
                .eventType("SentimentAnalyzed")
                .aggregateId(TEST_CALL_ID)
                .metadata(new HashMap<>())
                .payload(payload)
                .build();
    }

    private VocAnalyzedEvent createVocAnalyzedEvent() {
        VocAnalyzedEvent.Payload.ChurnRisk churnRisk = VocAnalyzedEvent.Payload.ChurnRisk.builder()
                .level("HIGH")
                .score(0.85)
                .build();

        VocAnalyzedEvent.Payload payload = VocAnalyzedEvent.Payload.builder()
                .callId(TEST_CALL_ID)
                .topics(List.of("Billing", "Customer Service"))
                .churnRisk(churnRisk)
                .build();

        return VocAnalyzedEvent.builder()
                .eventId("event-123")
                .eventType("VocAnalyzed")
                .aggregateId(TEST_CALL_ID)
                .payload(payload)
                .build();
    }

    private VocAnalyzedEvent createVocAnalyzedEventWithIssues() {
        VocAnalyzedEvent.Payload.Issue issue1 = VocAnalyzedEvent.Payload.Issue.builder()
                .category("billing")
                .description("Billing problem")
                .build();

        VocAnalyzedEvent.Payload.Issue issue2 = VocAnalyzedEvent.Payload.Issue.builder()
                .category("technical")
                .description("Technical issue")
                .build();

        VocAnalyzedEvent.Payload payload = VocAnalyzedEvent.Payload.builder()
                .callId(TEST_CALL_ID)
                .issues(List.of(issue1, issue2))
                .build();

        return VocAnalyzedEvent.builder()
                .eventId("event-123")
                .eventType("VocAnalyzed")
                .aggregateId(TEST_CALL_ID)
                .payload(payload)
                .build();
    }

    private VocAnalyzedEvent createVocAnalyzedEventWithAgent() {
        VocAnalyzedEvent.Payload.ChurnRisk churnRisk = VocAnalyzedEvent.Payload.ChurnRisk.builder()
                .level("HIGH")
                .score(0.85)
                .build();

        VocAnalyzedEvent.Payload payload = VocAnalyzedEvent.Payload.builder()
                .callId(TEST_CALL_ID)
                .churnRisk(churnRisk)
                .build();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agentId", TEST_AGENT_ID);

        return VocAnalyzedEvent.builder()
                .eventId("event-123")
                .eventType("VocAnalyzed")
                .aggregateId(TEST_CALL_ID)
                .metadata(metadata)
                .payload(payload)
                .build();
    }

    private VocAnalyzedEvent createVocAnalyzedEventWithoutChurnRisk() {
        VocAnalyzedEvent.Payload payload = VocAnalyzedEvent.Payload.builder()
                .callId(TEST_CALL_ID)
                .topics(List.of("Billing"))
                .churnRisk(null)
                .build();

        return VocAnalyzedEvent.builder()
                .eventId("event-123")
                .eventType("VocAnalyzed")
                .aggregateId(TEST_CALL_ID)
                .payload(payload)
                .build();
    }

    private CallAuditedEvent createCallAuditedEvent(boolean passed) {
        CallAuditedEvent.Payload.ComplianceCheck complianceCheck = CallAuditedEvent.Payload.ComplianceCheck.builder()
                .passed(passed)
                .score(passed ? 0.95 : 0.45)
                .build();

        CallAuditedEvent.Payload payload = CallAuditedEvent.Payload.builder()
                .callId(TEST_CALL_ID)
                .qualityScore(0.88)
                .complianceCheck(complianceCheck)
                .build();

        return CallAuditedEvent.builder()
                .eventId("event-123")
                .eventType("CallAudited")
                .aggregateId(TEST_CALL_ID)
                .payload(payload)
                .build();
    }

    private CallAuditedEvent createCallAuditedEventWithAgent(boolean passed) {
        CallAuditedEvent.Payload.ComplianceCheck complianceCheck = CallAuditedEvent.Payload.ComplianceCheck.builder()
                .passed(passed)
                .score(passed ? 0.95 : 0.45)
                .build();

        CallAuditedEvent.Payload payload = CallAuditedEvent.Payload.builder()
                .callId(TEST_CALL_ID)
                .qualityScore(0.88)
                .complianceCheck(complianceCheck)
                .build();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agentId", TEST_AGENT_ID);

        return CallAuditedEvent.builder()
                .eventId("event-123")
                .eventType("CallAudited")
                .aggregateId(TEST_CALL_ID)
                .metadata(metadata)
                .payload(payload)
                .build();
    }
}
