package com.callaudit.analytics.controller;

import com.callaudit.analytics.model.AgentMetrics;
import com.callaudit.analytics.model.DashboardMetrics;
import com.callaudit.analytics.service.AgentPerformanceService;
import com.callaudit.analytics.service.DashboardService;
import com.callaudit.analytics.service.TrendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for AnalyticsController
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private AgentPerformanceService agentPerformanceService;

    @MockBean
    private TrendService trendService;

    @BeforeEach
    void setUp() {
        // MockBean automatically resets between tests
    }

    @Test
    void getDashboard_ReturnsCompleteMetrics() throws Exception {
        // Arrange
        DashboardMetrics metrics = createTestDashboardMetrics();
        when(dashboardService.getDashboardMetrics()).thenReturn(metrics);

        // Act & Assert
        mockMvc.perform(get("/api/analytics/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCallsProcessed").value(1000))
                .andExpect(jsonPath("$.averageQualityScore").value(0.85))
                .andExpect(jsonPath("$.compliancePassRate").value(0.92))
                .andExpect(jsonPath("$.averageSentimentScore").value(0.65))
                .andExpect(jsonPath("$.customerSatisfactionScore").value(0.78));

        verify(dashboardService).getDashboardMetrics();
    }

    @Test
    void getAgentPerformance_ExistingAgent_ReturnsMetrics() throws Exception {
        // Arrange
        AgentMetrics metrics = createTestAgentMetrics("agent-001");
        when(agentPerformanceService.getAgentPerformance("agent-001")).thenReturn(metrics);

        // Act & Assert
        mockMvc.perform(get("/api/analytics/agents/{agentId}/performance", "agent-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value("agent-001"))
                .andExpect(jsonPath("$.totalCalls").value(100))
                .andExpect(jsonPath("$.averageQualityScore").value(0.88));

        verify(agentPerformanceService).getAgentPerformance("agent-001");
    }

    @Test
    void getTopAgents_WithDefaultLimit_ReturnsTopAgents() throws Exception {
        // Arrange
        List<AgentMetrics> topAgents = List.of(
                createTestAgentMetrics("agent-001"),
                createTestAgentMetrics("agent-002")
        );
        when(agentPerformanceService.getTopAgents(null)).thenReturn(topAgents);

        // Act & Assert
        mockMvc.perform(get("/api/analytics/agents/top"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].agentId").value("agent-001"))
                .andExpect(jsonPath("$[1].agentId").value("agent-002"));

        verify(agentPerformanceService).getTopAgents(null);
    }

    @Test
    void getTopAgents_WithCustomLimit_ReturnsTopAgents() throws Exception {
        // Arrange
        List<AgentMetrics> topAgents = List.of(
                createTestAgentMetrics("agent-001")
        );
        when(agentPerformanceService.getTopAgents(5)).thenReturn(topAgents);

        // Act & Assert
        mockMvc.perform(get("/api/analytics/agents/top")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(agentPerformanceService).getTopAgents(5);
    }

    @Test
    void getComplianceSummary_WithDefaultPeriod_ReturnsSummary() throws Exception {
        // Arrange
        DashboardMetrics dashboard = createTestDashboardMetrics();
        TrendService.TrendData trendData = createTestTrendData("compliance", 7);

        when(dashboardService.getDashboardMetrics()).thenReturn(dashboard);
        when(trendService.getComplianceTrends(null)).thenReturn(trendData);

        // Act & Assert
        mockMvc.perform(get("/api/analytics/compliance/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallPassRate").value(0.92))
                .andExpect(jsonPath("$.totalCallsAudited").value(1000))
                .andExpect(jsonPath("$.periodDays").value(7))
                .andExpect(jsonPath("$.trend", hasSize(3)));

        verify(dashboardService).getDashboardMetrics();
        verify(trendService).getComplianceTrends(null);
    }

    @Test
    void getComplianceSummary_WithCustomPeriod_ReturnsSummary() throws Exception {
        // Arrange
        DashboardMetrics dashboard = createTestDashboardMetrics();
        TrendService.TrendData trendData = createTestTrendData("compliance", 30);

        when(dashboardService.getDashboardMetrics()).thenReturn(dashboard);
        when(trendService.getComplianceTrends(30)).thenReturn(trendData);

        // Act & Assert
        mockMvc.perform(get("/api/analytics/compliance/summary")
                        .param("periodDays", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodDays").value(30));

        verify(trendService).getComplianceTrends(30);
    }

    @Test
    void getCustomerSatisfaction_ReturnsMetrics() throws Exception {
        // Arrange
        DashboardMetrics dashboard = createTestDashboardMetrics();
        TrendService.TrendData sentimentTrend = createTestTrendData("sentiment", 7);

        when(dashboardService.getDashboardMetrics()).thenReturn(dashboard);
        when(trendService.getSentimentTrends(null)).thenReturn(sentimentTrend);

        // Act & Assert
        mockMvc.perform(get("/api/analytics/customer-satisfaction"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageScore").value(0.78))
                .andExpect(jsonPath("$.averageSentiment").value(0.65))
                .andExpect(jsonPath("$.churnRiskDistribution.low").value(500))
                .andExpect(jsonPath("$.churnRiskDistribution.medium").value(300))
                .andExpect(jsonPath("$.churnRiskDistribution.high").value(200));

        verify(dashboardService).getDashboardMetrics();
        verify(trendService).getSentimentTrends(null);
    }

    @Test
    void getTrends_SentimentMetric_ReturnsTrendData() throws Exception {
        // Arrange
        TrendService.TrendData trendData = createTestTrendData("sentiment", 7);
        when(trendService.getSentimentTrends(null)).thenReturn(trendData);

        // Act & Assert
        mockMvc.perform(get("/api/analytics/trends")
                        .param("metric", "sentiment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metric").value("sentiment"))
                .andExpect(jsonPath("$.periodDays").value(7))
                .andExpect(jsonPath("$.dataPoints", hasSize(3)));

        verify(trendService).getSentimentTrends(null);
    }

    @Test
    void getTrends_ComplianceMetric_ReturnsTrendData() throws Exception {
        // Arrange
        TrendService.TrendData trendData = createTestTrendData("compliance", 7);
        when(trendService.getComplianceTrends(null)).thenReturn(trendData);

        // Act & Assert
        mockMvc.perform(get("/api/analytics/trends")
                        .param("metric", "compliance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metric").value("compliance"));

        verify(trendService).getComplianceTrends(null);
    }

    @Test
    void getTrends_QualityMetric_ReturnsTrendData() throws Exception {
        // Arrange
        TrendService.TrendData trendData = createTestTrendData("quality", 7);
        when(trendService.getQualityTrends(null)).thenReturn(trendData);

        // Act & Assert
        mockMvc.perform(get("/api/analytics/trends")
                        .param("metric", "quality"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metric").value("quality"));

        verify(trendService).getQualityTrends(null);
    }

    @Test
    void getTrends_InvalidMetric_Returns400BadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/analytics/trends")
                        .param("metric", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid metric: invalid"));
    }

    @Test
    void getAllTrends_ReturnsAllTrends() throws Exception {
        // Arrange
        Map<String, TrendService.TrendData> allTrends = new HashMap<>();
        allTrends.put("sentiment", createTestTrendData("sentiment", 7));
        allTrends.put("compliance", createTestTrendData("compliance", 7));
        allTrends.put("quality", createTestTrendData("quality", 7));

        when(trendService.getAllTrends(null)).thenReturn(allTrends);

        // Act & Assert
        mockMvc.perform(get("/api/analytics/trends/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentiment.metric").value("sentiment"))
                .andExpect(jsonPath("$.compliance.metric").value("compliance"))
                .andExpect(jsonPath("$.quality.metric").value("quality"));

        verify(trendService).getAllTrends(null);
    }

    @Test
    void getVolumeMetrics_ReturnsVolumeData() throws Exception {
        // Arrange
        TrendService.VolumeMetrics volumeMetrics = TrendService.VolumeMetrics.builder()
                .totalCalls(1000L)
                .periodDays(7)
                .averageDailyVolume(142.86)
                .volumeByTime(List.of(
                        TrendService.DataPoint.builder()
                                .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                                .value(150.0)
                                .label("Call Volume")
                                .build()
                ))
                .startTime(LocalDateTime.now().minusDays(7).format(DateTimeFormatter.ISO_DATE_TIME))
                .endTime(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                .build();

        when(trendService.getVolumeMetrics(null)).thenReturn(volumeMetrics);

        // Act & Assert
        mockMvc.perform(get("/api/analytics/volume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCalls").value(1000))
                .andExpect(jsonPath("$.periodDays").value(7))
                .andExpect(jsonPath("$.averageDailyVolume").value(142.86));

        verify(trendService).getVolumeMetrics(null);
    }

    @Test
    void getIssues_ReturnsIssuesSummary() throws Exception {
        // Arrange
        DashboardMetrics dashboard = createTestDashboardMetrics();
        when(dashboardService.getDashboardMetrics()).thenReturn(dashboard);

        // Act & Assert
        mockMvc.perform(get("/api/analytics/issues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topIssues.billing").value(50))
                .andExpect(jsonPath("$.topTopics.['Customer Service']").value(75));

        verify(dashboardService).getDashboardMetrics();
    }

    @Test
    void health_ReturnsHealthy() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/analytics/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("analytics-service"));
    }

    // Helper methods

    private DashboardMetrics createTestDashboardMetrics() {
        Map<String, Integer> topIssues = new HashMap<>();
        topIssues.put("billing", 50);
        topIssues.put("technical", 30);

        Map<String, Integer> topTopics = new HashMap<>();
        topTopics.put("Customer Service", 75);
        topTopics.put("Product Quality", 45);

        DashboardMetrics.ChurnRiskDistribution churnRisk = DashboardMetrics.ChurnRiskDistribution.builder()
                .low(500)
                .medium(300)
                .high(200)
                .build();

        return DashboardMetrics.builder()
                .totalCallsProcessed(1000L)
                .averageQualityScore(0.85)
                .compliancePassRate(0.92)
                .averageSentimentScore(0.65)
                .customerSatisfactionScore(0.78)
                .churnRiskDistribution(churnRisk)
                .topIssues(topIssues)
                .topTopics(topTopics)
                .generatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }

    private AgentMetrics createTestAgentMetrics(String agentId) {
        List<AgentMetrics.TrendPoint> qualityTrend = List.of(
                AgentMetrics.TrendPoint.builder()
                        .timestamp(LocalDateTime.now().minusDays(2).format(DateTimeFormatter.ISO_DATE_TIME))
                        .value(0.85)
                        .build(),
                AgentMetrics.TrendPoint.builder()
                        .timestamp(LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ISO_DATE_TIME))
                        .value(0.88)
                        .build()
        );

        return AgentMetrics.builder()
                .agentId(agentId)
                .agentName("Agent " + agentId)
                .totalCalls(100)
                .averageQualityScore(0.88)
                .averageCustomerSatisfaction(0.82)
                .compliancePassRate(0.95)
                .averageSentimentScore(0.68)
                .averageChurnRisk(0.25)
                .qualityTrend(qualityTrend)
                .lastUpdated(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }

    private TrendService.TrendData createTestTrendData(String metric, int periodDays) {
        List<TrendService.DataPoint> dataPoints = List.of(
                TrendService.DataPoint.builder()
                        .timestamp(LocalDateTime.now().minusDays(2).format(DateTimeFormatter.ISO_DATE_TIME))
                        .value(0.80)
                        .label(metric)
                        .build(),
                TrendService.DataPoint.builder()
                        .timestamp(LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ISO_DATE_TIME))
                        .value(0.85)
                        .label(metric)
                        .build(),
                TrendService.DataPoint.builder()
                        .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                        .value(0.88)
                        .label(metric)
                        .build()
        );

        return TrendService.TrendData.builder()
                .metric(metric)
                .periodDays(periodDays)
                .dataPoints(dataPoints)
                .startTime(LocalDateTime.now().minusDays(periodDays).format(DateTimeFormatter.ISO_DATE_TIME))
                .endTime(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }
}
