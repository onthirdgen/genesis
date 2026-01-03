package com.callaudit.analytics.service;

import com.callaudit.analytics.model.AgentPerformance;
import com.callaudit.analytics.repository.AgentPerformanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TrendService
 */
@ExtendWith(MockitoExtension.class)
class TrendServiceTest {

    @Mock
    private AgentPerformanceRepository agentPerformanceRepository;

    @InjectMocks
    private TrendService trendService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(trendService, "defaultPeriodDays", 7);
    }

    @Test
    void getSentimentTrends_WithData_ReturnsTrendData() {
        // Arrange
        List<AgentPerformance> performanceData = List.of(
                createPerformance(0.70, 0.85, 0.90, 50),
                createPerformance(0.72, 0.87, 0.92, 55),
                createPerformance(0.75, 0.88, 0.94, 60)
        );

        when(agentPerformanceRepository.getTrendData(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(performanceData);

        // Act
        TrendService.TrendData result = trendService.getSentimentTrends(7);

        // Assert
        assertNotNull(result);
        assertEquals("sentiment", result.getMetric());
        assertEquals(7, result.getPeriodDays());
        assertEquals(3, result.getDataPoints().size());

        TrendService.DataPoint firstPoint = result.getDataPoints().get(0);
        assertEquals(0.70, firstPoint.getValue());
        assertEquals("Sentiment Score", firstPoint.getLabel());
    }

    @Test
    void getSentimentTrends_WithNullPeriod_UsesDefaultPeriod() {
        // Arrange
        when(agentPerformanceRepository.getTrendData(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        // Act
        TrendService.TrendData result = trendService.getSentimentTrends(null);

        // Assert
        assertNotNull(result);
        assertEquals(7, result.getPeriodDays()); // Default period
        verify(agentPerformanceRepository).getTrendData(any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void getSentimentTrends_FiltersOutNullSentimentScores() {
        // Arrange
        List<AgentPerformance> performanceData = List.of(
                createPerformance(0.70, 0.85, 0.90, 50),
                createPerformance(null, 0.87, 0.92, 55), // Null sentiment
                createPerformance(0.75, 0.88, 0.94, 60)
        );

        when(agentPerformanceRepository.getTrendData(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(performanceData);

        // Act
        TrendService.TrendData result = trendService.getSentimentTrends(7);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getDataPoints().size()); // Only 2 non-null values
    }

    @Test
    void getComplianceTrends_WithData_ReturnsTrendData() {
        // Arrange
        List<AgentPerformance> performanceData = List.of(
                createPerformance(0.70, 0.85, 0.90, 50),
                createPerformance(0.72, 0.87, 0.92, 55),
                createPerformance(0.75, 0.88, 0.94, 60)
        );

        when(agentPerformanceRepository.getTrendData(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(performanceData);

        // Act
        TrendService.TrendData result = trendService.getComplianceTrends(7);

        // Assert
        assertNotNull(result);
        assertEquals("compliance", result.getMetric());
        assertEquals(7, result.getPeriodDays());
        assertEquals(3, result.getDataPoints().size());

        TrendService.DataPoint firstPoint = result.getDataPoints().get(0);
        assertEquals(0.90, firstPoint.getValue());
        assertEquals("Compliance Rate", firstPoint.getLabel());
    }

    @Test
    void getComplianceTrends_FiltersOutNullComplianceRates() {
        // Arrange
        List<AgentPerformance> performanceData = List.of(
                createPerformance(0.70, 0.85, 0.90, 50),
                createPerformance(0.72, 0.87, null, 55), // Null compliance
                createPerformance(0.75, 0.88, 0.94, 60)
        );

        when(agentPerformanceRepository.getTrendData(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(performanceData);

        // Act
        TrendService.TrendData result = trendService.getComplianceTrends(7);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getDataPoints().size());
    }

    @Test
    void getQualityTrends_WithData_ReturnsTrendData() {
        // Arrange
        List<AgentPerformance> performanceData = List.of(
                createPerformance(0.70, 0.85, 0.90, 50),
                createPerformance(0.72, 0.87, 0.92, 55),
                createPerformance(0.75, 0.88, 0.94, 60)
        );

        when(agentPerformanceRepository.getTrendData(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(performanceData);

        // Act
        TrendService.TrendData result = trendService.getQualityTrends(14);

        // Assert
        assertNotNull(result);
        assertEquals("quality", result.getMetric());
        assertEquals(14, result.getPeriodDays());
        assertEquals(3, result.getDataPoints().size());

        TrendService.DataPoint firstPoint = result.getDataPoints().get(0);
        assertEquals(0.85, firstPoint.getValue());
        assertEquals("Quality Score", firstPoint.getLabel());
    }

    @Test
    void getQualityTrends_FiltersOutNullQualityScores() {
        // Arrange
        List<AgentPerformance> performanceData = List.of(
                createPerformance(0.70, 0.85, 0.90, 50),
                createPerformance(0.72, null, 0.92, 55), // Null quality
                createPerformance(0.75, 0.88, 0.94, 60)
        );

        when(agentPerformanceRepository.getTrendData(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(performanceData);

        // Act
        TrendService.TrendData result = trendService.getQualityTrends(7);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getDataPoints().size());
    }

    @Test
    void getVolumeMetrics_WithData_ReturnsVolumeMetrics() {
        // Arrange
        List<AgentPerformance> performanceData = List.of(
                createPerformance(0.70, 0.85, 0.90, 50),
                createPerformance(0.72, 0.87, 0.92, 55),
                createPerformance(0.75, 0.88, 0.94, 60)
        );

        when(agentPerformanceRepository.getTrendData(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(performanceData);

        // Act
        TrendService.VolumeMetrics result = trendService.getVolumeMetrics(7);

        // Assert
        assertNotNull(result);
        assertEquals(165L, result.getTotalCalls()); // 50 + 55 + 60
        assertEquals(7, result.getPeriodDays());
        assertEquals(165.0 / 7, result.getAverageDailyVolume(), 0.01);
        assertEquals(3, result.getVolumeByTime().size());

        TrendService.DataPoint firstPoint = result.getVolumeByTime().get(0);
        assertEquals(50.0, firstPoint.getValue());
        assertEquals("Call Volume", firstPoint.getLabel());
    }

    @Test
    void getVolumeMetrics_WithNullCallsProcessed_HandlesGracefully() {
        // Arrange
        List<AgentPerformance> performanceData = List.of(
                createPerformance(0.70, 0.85, 0.90, 50),
                createPerformance(0.72, 0.87, 0.92, null), // Null calls
                createPerformance(0.75, 0.88, 0.94, 60)
        );

        when(agentPerformanceRepository.getTrendData(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(performanceData);

        // Act
        TrendService.VolumeMetrics result = trendService.getVolumeMetrics(7);

        // Assert
        assertNotNull(result);
        assertEquals(110L, result.getTotalCalls()); // 50 + 0 + 60
        assertEquals(3, result.getVolumeByTime().size());

        // The null value should be converted to 0.0
        TrendService.DataPoint secondPoint = result.getVolumeByTime().get(1);
        assertEquals(0.0, secondPoint.getValue());
    }

    @Test
    void getVolumeMetrics_EmptyData_ReturnsZeroMetrics() {
        // Arrange
        when(agentPerformanceRepository.getTrendData(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        // Act
        TrendService.VolumeMetrics result = trendService.getVolumeMetrics(7);

        // Assert
        assertNotNull(result);
        assertEquals(0L, result.getTotalCalls());
        assertEquals(0.0, result.getAverageDailyVolume());
        assertTrue(result.getVolumeByTime().isEmpty());
    }

    @Test
    void getAllTrends_ReturnsAllThreeTrends() {
        // Arrange
        List<AgentPerformance> performanceData = List.of(
                createPerformance(0.70, 0.85, 0.90, 50)
        );

        when(agentPerformanceRepository.getTrendData(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(performanceData);

        // Act
        Map<String, TrendService.TrendData> result = trendService.getAllTrends(7);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.containsKey("sentiment"));
        assertTrue(result.containsKey("compliance"));
        assertTrue(result.containsKey("quality"));

        assertEquals("sentiment", result.get("sentiment").getMetric());
        assertEquals("compliance", result.get("compliance").getMetric());
        assertEquals("quality", result.get("quality").getMetric());

        // Verify repository was called 3 times (once for each trend)
        verify(agentPerformanceRepository, times(3)).getTrendData(any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void getAllTrends_WithNullPeriod_UsesDefaultPeriod() {
        // Arrange
        when(agentPerformanceRepository.getTrendData(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        // Act
        Map<String, TrendService.TrendData> result = trendService.getAllTrends(null);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());

        result.values().forEach(trendData -> {
            assertEquals(7, trendData.getPeriodDays()); // Default period
        });
    }

    @Test
    void getSentimentTrends_CustomPeriod30Days_UsesCorrectPeriod() {
        // Arrange
        when(agentPerformanceRepository.getTrendData(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        // Act
        TrendService.TrendData result = trendService.getSentimentTrends(30);

        // Assert
        assertNotNull(result);
        assertEquals(30, result.getPeriodDays());
    }

    @Test
    void getVolumeMetrics_ZeroDays_HandlesGracefully() {
        // Arrange
        when(agentPerformanceRepository.getTrendData(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        // Act
        TrendService.VolumeMetrics result = trendService.getVolumeMetrics(0);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getPeriodDays());
        assertEquals(0.0, result.getAverageDailyVolume()); // Avoids division by zero
    }

    // Helper method
    private AgentPerformance createPerformance(Double sentimentScore, Double qualityScore,
                                               Double complianceRate, Integer callsProcessed) {
        return AgentPerformance.builder()
                .id(1L)
                .time(LocalDateTime.now().minusDays(1))
                .agentId("agent-001")
                .avgSentimentScore(sentimentScore)
                .avgQualityScore(qualityScore)
                .compliancePassRate(complianceRate)
                .callsProcessed(callsProcessed)
                .build();
    }
}
