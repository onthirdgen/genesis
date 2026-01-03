package com.callaudit.voc.controller;

import com.callaudit.voc.model.Intent;
import com.callaudit.voc.model.Satisfaction;
import com.callaudit.voc.model.VocInsight;
import com.callaudit.voc.service.InsightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests using manual MockMvc setup for Spring Boot 4.0.0 compatibility
 */
@SpringBootTest
@ActiveProfiles("test")
class VocControllerTest {

    private static final UUID CALL_ID_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CALL_ID_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID NONEXISTENT_CALL_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public InsightService insightService() {
            return mock(InsightService.class);
        }
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private InsightService insightService;

    private MockMvc mockMvc;

    private VocInsight testInsight;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        reset(insightService);

        testInsight = VocInsight.builder()
            .id(UUID.fromString("00000000-0000-0000-0000-100000000001"))
            .callId(CALL_ID_1)
            .primaryIntent(Intent.complaint)
            .topics(List.of("Billing", "Technical Support"))
            .keywords(List.of("billing", "problem", "charge"))
            .customerSatisfaction(Satisfaction.low)
            .predictedChurnRisk(BigDecimal.valueOf(0.8))
            .actionableItems(List.of("Contact customer", "Review billing"))
            .summary("Customer complaint about billing")
            .createdAt(LocalDateTime.now())
            .build();
    }

    @Test
    void getInsight_ExistingCall_Returns200Ok() throws Exception {
        // Arrange
        when(insightService.getInsightByCallId(CALL_ID_1)).thenReturn(Optional.of(testInsight));

        // Act & Assert
        mockMvc.perform(get("/api/voc/insights/{callId}", CALL_ID_1.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.callId").value(CALL_ID_1.toString()))
            .andExpect(jsonPath("$.primaryIntent").value("complaint"))
            .andExpect(jsonPath("$.customerSatisfaction").value("low"))
            .andExpect(jsonPath("$.predictedChurnRisk").value(0.8));

        verify(insightService).getInsightByCallId(CALL_ID_1);
    }

    @Test
    void getInsight_NonExistingCall_Returns404NotFound() throws Exception {
        // Arrange
        when(insightService.getInsightByCallId(NONEXISTENT_CALL_ID)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/voc/insights/{callId}", NONEXISTENT_CALL_ID.toString()))
            .andExpect(status().isNotFound());

        verify(insightService).getInsightByCallId(NONEXISTENT_CALL_ID);
    }

    @Test
    void getAllInsights_ReturnsAllInsights() throws Exception {
        // Arrange
        VocInsight insight2 = VocInsight.builder()
            .id(UUID.fromString("00000000-0000-0000-0000-100000000002"))
            .callId(CALL_ID_2)
            .primaryIntent(Intent.inquiry)
            .customerSatisfaction(Satisfaction.medium)
            .predictedChurnRisk(BigDecimal.valueOf(0.3))
            .build();

        when(insightService.getAllInsights()).thenReturn(List.of(testInsight, insight2));

        // Act & Assert
        mockMvc.perform(get("/api/voc/insights"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].callId").value(CALL_ID_1.toString()))
            .andExpect(jsonPath("$[1].callId").value(CALL_ID_2.toString()));

        verify(insightService).getAllInsights();
    }

    @Test
    void getInsightsByIntent_ComplaintIntent_ReturnsMatchingInsights() throws Exception {
        // Arrange
        when(insightService.getInsightsByIntent(Intent.complaint))
            .thenReturn(List.of(testInsight));

        // Act & Assert
        mockMvc.perform(get("/api/voc/insights/by-intent/{intent}", "complaint"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].primaryIntent").value("complaint"));

        verify(insightService).getInsightsByIntent(Intent.complaint);
    }

    @Test
    void getInsightsBySatisfaction_LowSatisfaction_ReturnsMatchingInsights() throws Exception {
        // Arrange
        when(insightService.getInsightsBySatisfaction(Satisfaction.low))
            .thenReturn(List.of(testInsight));

        // Act & Assert
        mockMvc.perform(get("/api/voc/insights/by-satisfaction/{satisfaction}", "low"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].customerSatisfaction").value("low"));

        verify(insightService).getInsightsBySatisfaction(Satisfaction.low);
    }

    @Test
    void getHighRiskCustomers_WithDefaultThreshold_ReturnsHighRiskCustomers() throws Exception {
        // Arrange
        when(insightService.getHighChurnRiskCustomers(0.7))
            .thenReturn(List.of(testInsight));

        // Act & Assert
        mockMvc.perform(get("/api/voc/insights/high-risk"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].predictedChurnRisk").value(0.8));

        verify(insightService).getHighChurnRiskCustomers(0.7);
    }

    @Test
    void getHighRiskCustomers_WithCustomThreshold_ReturnsHighRiskCustomers() throws Exception {
        // Arrange
        when(insightService.getHighChurnRiskCustomers(0.5))
            .thenReturn(List.of(testInsight));

        // Act & Assert
        mockMvc.perform(get("/api/voc/insights/high-risk")
                .param("threshold", "0.5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)));

        verify(insightService).getHighChurnRiskCustomers(0.5);
    }

    @Test
    void getInsightsByDateRange_ValidRange_ReturnsInsightsInRange() throws Exception {
        // Arrange
        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 31, 23, 59);

        when(insightService.getInsightsByDateRange(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of(testInsight));

        // Act & Assert
        mockMvc.perform(get("/api/voc/insights/date-range")
                .param("start", "2025-01-01T00:00:00")
                .param("end", "2025-01-31T23:59:00"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)));

        verify(insightService).getInsightsByDateRange(any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void getAggregateTrends_ReturnsCompleteMetrics() throws Exception {
        // Arrange
        Map<String, Object> trends = Map.of(
            "intentDistribution", Map.of(Intent.complaint, 10L),
            "satisfactionDistribution", Map.of(Satisfaction.low, 10L),
            "averageChurnRisk", 0.5,
            "totalInsights", 100L,
            "highRiskCount", 15
        );

        when(insightService.getAggregateTrends()).thenReturn(trends);

        // Act & Assert
        mockMvc.perform(get("/api/voc/trends"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.averageChurnRisk").value(0.5))
            .andExpect(jsonPath("$.totalInsights").value(100))
            .andExpect(jsonPath("$.highRiskCount").value(15));

        verify(insightService).getAggregateTrends();
    }

    @Test
    void getTopTopics_WithDefaultLimit_ReturnsTopTopics() throws Exception {
        // Arrange
        Map<String, Long> topics = Map.of(
            "Billing", 15L,
            "Technical Support", 12L
        );

        when(insightService.getTopTopics(10)).thenReturn(topics);

        // Act & Assert
        mockMvc.perform(get("/api/voc/topics"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.Billing").value(15))
            .andExpect(jsonPath("$.['Technical Support']").value(12));

        verify(insightService).getTopTopics(10);
    }

    @Test
    void getTopTopics_WithCustomLimit_ReturnsTopTopics() throws Exception {
        // Arrange
        Map<String, Long> topics = Map.of("Billing", 15L);

        when(insightService.getTopTopics(5)).thenReturn(topics);

        // Act & Assert
        mockMvc.perform(get("/api/voc/topics")
                .param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.Billing").value(15));

        verify(insightService).getTopTopics(5);
    }

    @Test
    void getTopKeywords_WithDefaultLimit_ReturnsTopKeywords() throws Exception {
        // Arrange
        Map<String, Long> keywords = Map.of(
            "billing", 10L,
            "problem", 8L,
            "charge", 6L
        );

        when(insightService.getTopKeywords(10)).thenReturn(keywords);

        // Act & Assert
        mockMvc.perform(get("/api/voc/keywords"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.billing").value(10))
            .andExpect(jsonPath("$.problem").value(8))
            .andExpect(jsonPath("$.charge").value(6));

        verify(insightService).getTopKeywords(10);
    }

    @Test
    void getTopKeywords_WithCustomLimit_ReturnsTopKeywords() throws Exception {
        // Arrange
        Map<String, Long> keywords = Map.of("billing", 10L);

        when(insightService.getTopKeywords(20)).thenReturn(keywords);

        // Act & Assert
        mockMvc.perform(get("/api/voc/keywords")
                .param("limit", "20"))
            .andExpect(status().isOk());

        verify(insightService).getTopKeywords(20);
    }

    @Test
    void getIntentDistribution_ReturnsIntentCounts() throws Exception {
        // Arrange
        Map<Intent, Long> distribution = Map.of(
            Intent.complaint, 25L,
            Intent.inquiry, 30L,
            Intent.request, 15L
        );

        when(insightService.getIntentDistribution()).thenReturn(distribution);

        // Act & Assert
        mockMvc.perform(get("/api/voc/distribution/intent"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.complaint").value(25))
            .andExpect(jsonPath("$.inquiry").value(30))
            .andExpect(jsonPath("$.request").value(15));

        verify(insightService).getIntentDistribution();
    }

    @Test
    void getSatisfactionDistribution_ReturnsSatisfactionCounts() throws Exception {
        // Arrange
        Map<Satisfaction, Long> distribution = Map.of(
            Satisfaction.high, 40L,
            Satisfaction.medium, 35L,
            Satisfaction.low, 25L
        );

        when(insightService.getSatisfactionDistribution()).thenReturn(distribution);

        // Act & Assert
        mockMvc.perform(get("/api/voc/distribution/satisfaction"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.high").value(40))
            .andExpect(jsonPath("$.medium").value(35))
            .andExpect(jsonPath("$.low").value(25));

        verify(insightService).getSatisfactionDistribution();
    }

    @Test
    void getAverageChurnRisk_ReturnsAverageRisk() throws Exception {
        // Arrange
        when(insightService.getAverageChurnRisk()).thenReturn(0.45);

        // Act & Assert
        mockMvc.perform(get("/api/voc/metrics/churn-risk"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.averageChurnRisk").value(0.45));

        verify(insightService).getAverageChurnRisk();
    }

    @Test
    void health_ReturnsHealthy() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/voc/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.service").value("voc-service"));
    }

    @Test
    void getAllInsights_EmptyList_ReturnsEmptyArray() throws Exception {
        // Arrange
        when(insightService.getAllInsights()).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/voc/insights"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getInsightsByIntent_NoMatches_ReturnsEmptyArray() throws Exception {
        // Arrange
        when(insightService.getInsightsByIntent(Intent.compliment))
            .thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/voc/insights/by-intent/{intent}", "compliment"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getHighRiskCustomers_NoMatches_ReturnsEmptyArray() throws Exception {
        // Arrange
        when(insightService.getHighChurnRiskCustomers(anyDouble()))
            .thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/voc/insights/high-risk"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }
}
