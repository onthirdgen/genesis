package com.callaudit.analytics.service;

import com.callaudit.analytics.model.DashboardMetrics;
import com.callaudit.analytics.repository.AgentPerformanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DashboardService
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class DashboardServiceTest {

    @Mock
    private AgentPerformanceRepository agentPerformanceRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    @InjectMocks
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    void getDashboardMetrics_WithData_ReturnsCompleteMetrics() {
        // Arrange
        when(agentPerformanceRepository.getTotalCallsProcessed(any(LocalDateTime.class))).thenReturn(1000L);
        when(agentPerformanceRepository.getAverageQualityScore(any(LocalDateTime.class))).thenReturn(0.85);
        when(agentPerformanceRepository.getAverageCompliancePassRate(any(LocalDateTime.class))).thenReturn(0.92);
        when(agentPerformanceRepository.getAverageSentimentScore(any(LocalDateTime.class))).thenReturn(0.65);
        when(agentPerformanceRepository.getAverageCustomerSatisfaction(any(LocalDateTime.class))).thenReturn(0.78);

        when(valueOperations.get("analytics:counter:total_calls")).thenReturn(50);
        when(valueOperations.get("analytics:churn:low")).thenReturn(500);
        when(valueOperations.get("analytics:churn:medium")).thenReturn(300);
        when(valueOperations.get("analytics:churn:high")).thenReturn(200);

        // Act
        DashboardMetrics metrics = dashboardService.getDashboardMetrics();

        // Assert
        assertNotNull(metrics);
        assertEquals(1050L, metrics.getTotalCallsProcessed()); // 1000 + 50
        assertEquals(0.85, metrics.getAverageQualityScore());
        assertEquals(0.92, metrics.getCompliancePassRate());
        assertEquals(0.65, metrics.getAverageSentimentScore());
        assertEquals(0.78, metrics.getCustomerSatisfactionScore());

        assertNotNull(metrics.getChurnRiskDistribution());
        assertEquals(500, metrics.getChurnRiskDistribution().getLow());
        assertEquals(300, metrics.getChurnRiskDistribution().getMedium());
        assertEquals(200, metrics.getChurnRiskDistribution().getHigh());

        verify(agentPerformanceRepository).getTotalCallsProcessed(any(LocalDateTime.class));
        verify(agentPerformanceRepository).getAverageQualityScore(any(LocalDateTime.class));
    }

    @Test
    void getDashboardMetrics_WithNullValues_ReturnsDefaultValues() {
        // Arrange
        when(agentPerformanceRepository.getTotalCallsProcessed(any(LocalDateTime.class))).thenReturn(null);
        when(agentPerformanceRepository.getAverageQualityScore(any(LocalDateTime.class))).thenReturn(null);
        when(agentPerformanceRepository.getAverageCompliancePassRate(any(LocalDateTime.class))).thenReturn(null);
        when(agentPerformanceRepository.getAverageSentimentScore(any(LocalDateTime.class))).thenReturn(null);
        when(agentPerformanceRepository.getAverageCustomerSatisfaction(any(LocalDateTime.class))).thenReturn(null);

        when(valueOperations.get(anyString())).thenReturn(null);

        // Act
        DashboardMetrics metrics = dashboardService.getDashboardMetrics();

        // Assert
        assertNotNull(metrics);
        assertEquals(0L, metrics.getTotalCallsProcessed());
        assertEquals(0.0, metrics.getAverageQualityScore());
        assertEquals(0.0, metrics.getCompliancePassRate());
        assertEquals(0.0, metrics.getAverageSentimentScore());
        assertEquals(0.0, metrics.getCustomerSatisfactionScore());
    }

    @Test
    void getChurnRiskDistribution_WithData_ReturnsDistribution() {
        // Arrange
        when(valueOperations.get("analytics:churn:low")).thenReturn(500);
        when(valueOperations.get("analytics:churn:medium")).thenReturn(300);
        when(valueOperations.get("analytics:churn:high")).thenReturn(200);

        // Act
        DashboardMetrics.ChurnRiskDistribution distribution = dashboardService.getChurnRiskDistribution();

        // Assert
        assertNotNull(distribution);
        assertEquals(500, distribution.getLow());
        assertEquals(300, distribution.getMedium());
        assertEquals(200, distribution.getHigh());
    }

    @Test
    void getChurnRiskDistribution_WithNullValues_ReturnsZeros() {
        // Arrange
        when(valueOperations.get(anyString())).thenReturn(null);

        // Act
        DashboardMetrics.ChurnRiskDistribution distribution = dashboardService.getChurnRiskDistribution();

        // Assert
        assertNotNull(distribution);
        assertEquals(0, distribution.getLow());
        assertEquals(0, distribution.getMedium());
        assertEquals(0, distribution.getHigh());
    }

    @Test
    void incrementCounter_ValidCounter_IncrementsSuccessfully() {
        // Arrange
        when(valueOperations.increment("analytics:counter:total_calls")).thenReturn(1L);

        // Act
        dashboardService.incrementCounter("total_calls");

        // Assert
        verify(valueOperations).increment("analytics:counter:total_calls");
    }

    @Test
    void incrementCounter_RedisException_DoesNotThrowException() {
        // Arrange
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("Redis error"));

        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> dashboardService.incrementCounter("total_calls"));
    }

    @Test
    void incrementChurnRisk_ValidLevel_IncrementsSuccessfully() {
        // Arrange
        when(valueOperations.increment("analytics:churn:low")).thenReturn(1L);

        // Act
        dashboardService.incrementChurnRisk("LOW");

        // Assert
        verify(valueOperations).increment("analytics:churn:low");
    }

    @Test
    void incrementChurnRisk_MixedCaseLevel_ConvertsToLowerCase() {
        // Arrange
        when(valueOperations.increment("analytics:churn:medium")).thenReturn(1L);

        // Act
        dashboardService.incrementChurnRisk("Medium");

        // Assert
        verify(valueOperations).increment("analytics:churn:medium");
    }

    @Test
    void trackIssue_ValidIssue_TracksSuccessfully() {
        // Arrange
        when(zSetOperations.incrementScore("analytics:issues:all", "billing", 1)).thenReturn(1.0);

        // Act
        dashboardService.trackIssue("billing");

        // Assert
        verify(zSetOperations).incrementScore("analytics:issues:all", "billing", 1);
    }

    @Test
    void trackIssue_RedisException_DoesNotThrowException() {
        // Arrange
        when(zSetOperations.incrementScore(anyString(), anyString(), anyDouble()))
                .thenThrow(new RuntimeException("Redis error"));

        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> dashboardService.trackIssue("billing"));
    }

    @Test
    void trackTopic_ValidTopic_TracksSuccessfully() {
        // Arrange
        when(zSetOperations.incrementScore("analytics:topics:all", "Customer Service", 1)).thenReturn(1.0);

        // Act
        dashboardService.trackTopic("Customer Service");

        // Assert
        verify(zSetOperations).incrementScore("analytics:topics:all", "Customer Service", 1);
    }

    @Test
    void trackTopic_RedisException_DoesNotThrowException() {
        // Arrange
        when(zSetOperations.incrementScore(anyString(), anyString(), anyDouble()))
                .thenThrow(new RuntimeException("Redis error"));

        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> dashboardService.trackTopic("Customer Service"));
    }

    @Test
    void getDashboardMetrics_WithTopIssuesAndTopics_ReturnsCompleteData() {
        // Arrange
        when(agentPerformanceRepository.getTotalCallsProcessed(any(LocalDateTime.class))).thenReturn(1000L);
        when(agentPerformanceRepository.getAverageQualityScore(any(LocalDateTime.class))).thenReturn(0.85);
        when(agentPerformanceRepository.getAverageCompliancePassRate(any(LocalDateTime.class))).thenReturn(0.92);
        when(agentPerformanceRepository.getAverageSentimentScore(any(LocalDateTime.class))).thenReturn(0.65);
        when(agentPerformanceRepository.getAverageCustomerSatisfaction(any(LocalDateTime.class))).thenReturn(0.78);

        when(valueOperations.get(anyString())).thenReturn(null);

        // Mock ZSet for issues
        Set<ZSetOperations.TypedTuple<Object>> issuesSet = new HashSet<>();
        issuesSet.add(createTypedTuple("billing", 50.0));
        issuesSet.add(createTypedTuple("technical", 30.0));
        when(zSetOperations.reverseRangeWithScores("analytics:issues:all", 0, 9)).thenReturn(issuesSet);

        // Mock ZSet for topics
        Set<ZSetOperations.TypedTuple<Object>> topicsSet = new HashSet<>();
        topicsSet.add(createTypedTuple("Customer Service", 75.0));
        topicsSet.add(createTypedTuple("Product Quality", 45.0));
        when(zSetOperations.reverseRangeWithScores("analytics:topics:all", 0, 9)).thenReturn(topicsSet);

        // Act
        DashboardMetrics metrics = dashboardService.getDashboardMetrics();

        // Assert
        assertNotNull(metrics);
        assertNotNull(metrics.getTopIssues());
        assertEquals(2, metrics.getTopIssues().size());
        assertEquals(50, metrics.getTopIssues().get("billing"));
        assertEquals(30, metrics.getTopIssues().get("technical"));

        assertNotNull(metrics.getTopTopics());
        assertEquals(2, metrics.getTopTopics().size());
        assertEquals(75, metrics.getTopTopics().get("Customer Service"));
        assertEquals(45, metrics.getTopTopics().get("Product Quality"));
    }

    @Test
    void getDashboardMetrics_RedisException_ReturnsEmptyMaps() {
        // Arrange
        when(agentPerformanceRepository.getTotalCallsProcessed(any(LocalDateTime.class))).thenReturn(1000L);
        when(agentPerformanceRepository.getAverageQualityScore(any(LocalDateTime.class))).thenReturn(0.85);
        when(agentPerformanceRepository.getAverageCompliancePassRate(any(LocalDateTime.class))).thenReturn(0.92);
        when(agentPerformanceRepository.getAverageSentimentScore(any(LocalDateTime.class))).thenReturn(0.65);
        when(agentPerformanceRepository.getAverageCustomerSatisfaction(any(LocalDateTime.class))).thenReturn(0.78);

        when(valueOperations.get(anyString())).thenReturn(null);
        when(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                .thenThrow(new RuntimeException("Redis error"));

        // Act
        DashboardMetrics metrics = dashboardService.getDashboardMetrics();

        // Assert
        assertNotNull(metrics);
        assertNotNull(metrics.getTopIssues());
        assertTrue(metrics.getTopIssues().isEmpty());
        assertNotNull(metrics.getTopTopics());
        assertTrue(metrics.getTopTopics().isEmpty());
    }

    // Helper method to create TypedTuple
    private ZSetOperations.TypedTuple<Object> createTypedTuple(String value, Double score) {
        return new ZSetOperations.TypedTuple<Object>() {
            @Override
            public Object getValue() {
                return value;
            }

            @Override
            public Double getScore() {
                return score;
            }

            @Override
            public int compareTo(ZSetOperations.TypedTuple<Object> o) {
                return Double.compare(this.getScore(), o.getScore());
            }
        };
    }
}
