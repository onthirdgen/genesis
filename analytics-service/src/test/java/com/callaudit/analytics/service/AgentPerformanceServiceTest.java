package com.callaudit.analytics.service;

import com.callaudit.analytics.model.AgentMetrics;
import com.callaudit.analytics.model.AgentPerformance;
import com.callaudit.analytics.repository.AgentPerformanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AgentPerformanceService
 */
@ExtendWith(MockitoExtension.class)
class AgentPerformanceServiceTest {

    @Mock
    private AgentPerformanceRepository agentPerformanceRepository;

    @InjectMocks
    private AgentPerformanceService agentPerformanceService;

    @Captor
    private ArgumentCaptor<AgentPerformance> performanceCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(agentPerformanceService, "topLimit", 10);
    }

    @Test
    void getAgentPerformance_WithData_ReturnsAggregatedMetrics() {
        // Arrange
        String agentId = "agent-001";
        List<AgentPerformance> performanceRecords = List.of(
                createPerformance(agentId, 50, 0.85, 0.80, 0.90, 0.70, 0.25),
                createPerformance(agentId, 30, 0.88, 0.82, 0.92, 0.72, 0.28),
                createPerformance(agentId, 20, 0.90, 0.85, 0.95, 0.75, 0.22)
        );

        when(agentPerformanceRepository.findByAgentIdAndTimeBetweenOrderByTimeDesc(
                eq(agentId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(performanceRecords);

        // Act
        AgentMetrics metrics = agentPerformanceService.getAgentPerformance(agentId);

        // Assert
        assertNotNull(metrics);
        assertEquals(agentId, metrics.getAgentId());
        assertEquals(100, metrics.getTotalCalls()); // 50 + 30 + 20
        assertEquals((0.85 + 0.88 + 0.90) / 3, metrics.getAverageQualityScore(), 0.01);
        assertEquals((0.80 + 0.82 + 0.85) / 3, metrics.getAverageCustomerSatisfaction(), 0.01);
        assertEquals((0.90 + 0.92 + 0.95) / 3, metrics.getCompliancePassRate(), 0.01);
        assertEquals((0.70 + 0.72 + 0.75) / 3, metrics.getAverageSentimentScore(), 0.01);
        assertEquals((0.25 + 0.28 + 0.22) / 3, metrics.getAverageChurnRisk(), 0.01);
        assertNotNull(metrics.getQualityTrend());
        assertEquals(3, metrics.getQualityTrend().size());
    }

    @Test
    void getAgentPerformance_NoData_ReturnsEmptyMetrics() {
        // Arrange
        String agentId = "agent-999";
        when(agentPerformanceRepository.findByAgentIdAndTimeBetweenOrderByTimeDesc(
                eq(agentId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        // Act
        AgentMetrics metrics = agentPerformanceService.getAgentPerformance(agentId);

        // Assert
        assertNotNull(metrics);
        assertEquals(agentId, metrics.getAgentId());
        assertEquals(0, metrics.getTotalCalls());
        assertEquals(0.0, metrics.getAverageQualityScore());
        assertEquals(0.0, metrics.getAverageCustomerSatisfaction());
        assertTrue(metrics.getQualityTrend().isEmpty());
    }

    @Test
    void getAgentPerformance_WithNullValues_HandlesGracefully() {
        // Arrange
        String agentId = "agent-002";
        List<AgentPerformance> performanceRecords = List.of(
                createPerformance(agentId, null, null, null, null, null, null),
                createPerformance(agentId, 30, 0.88, null, 0.92, null, null)
        );

        when(agentPerformanceRepository.findByAgentIdAndTimeBetweenOrderByTimeDesc(
                eq(agentId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(performanceRecords);

        // Act
        AgentMetrics metrics = agentPerformanceService.getAgentPerformance(agentId);

        // Assert
        assertNotNull(metrics);
        assertEquals(30, metrics.getTotalCalls()); // Only counts non-null values
        assertEquals(0.88, metrics.getAverageQualityScore()); // Only one non-null value
        assertEquals(0.0, metrics.getAverageCustomerSatisfaction()); // No non-null values
        assertEquals(0.92, metrics.getCompliancePassRate());
    }

    @Test
    void updateAgentMetrics_NewAgent_CreatesNewRecord() {
        // Arrange
        String agentId = "agent-003";
        when(agentPerformanceRepository.findFirstByAgentIdOrderByTimeDesc(agentId))
                .thenReturn(Optional.empty());
        when(agentPerformanceRepository.save(any(AgentPerformance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        agentPerformanceService.updateAgentMetrics(agentId, 0.85, 0.70, 0.80, 0.90, 0.25);

        // Assert
        verify(agentPerformanceRepository).save(performanceCaptor.capture());
        AgentPerformance saved = performanceCaptor.getValue();

        assertNotNull(saved);
        assertEquals(agentId, saved.getAgentId());
        assertEquals(1, saved.getCallsProcessed());
        assertEquals(0.85, saved.getAvgQualityScore());
        assertEquals(0.70, saved.getAvgSentimentScore());
        assertEquals(0.80, saved.getAvgCustomerSatisfaction());
        assertEquals(0.90, saved.getCompliancePassRate());
        assertEquals(0.25, saved.getAvgChurnRisk());
    }

    @Test
    void updateAgentMetrics_ExistingAgent_UpdatesExistingRecord() {
        // Arrange
        String agentId = "agent-004";
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime timeSlot = now.withMinute(0).withSecond(0).withNano(0);

        AgentPerformance existing = AgentPerformance.builder()
                .id(1L)
                .agentId(agentId)
                .time(timeSlot)
                .callsProcessed(5)
                .avgQualityScore(0.80)
                .avgSentimentScore(0.65)
                .avgCustomerSatisfaction(0.75)
                .compliancePassRate(0.85)
                .avgChurnRisk(0.30)
                .build();

        when(agentPerformanceRepository.findFirstByAgentIdOrderByTimeDesc(agentId))
                .thenReturn(Optional.of(existing));
        when(agentPerformanceRepository.save(any(AgentPerformance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        agentPerformanceService.updateAgentMetrics(agentId, 0.90, 0.75, 0.85, 0.95, 0.20);

        // Assert
        verify(agentPerformanceRepository).save(performanceCaptor.capture());
        AgentPerformance updated = performanceCaptor.getValue();

        assertNotNull(updated);
        assertEquals(6, updated.getCallsProcessed()); // Incremented from 5 to 6
        // Running average calculation: ((old_avg * (count-1)) + new_value) / count
        // Quality: ((0.80 * 5) + 0.90) / 6 = 4.90 / 6 = 0.8166...
        assertTrue(updated.getAvgQualityScore() > 0.80 && updated.getAvgQualityScore() < 0.85);
    }

    @Test
    void updateAgentMetrics_WithNullValues_OnlyUpdatesNonNull() {
        // Arrange
        String agentId = "agent-005";
        when(agentPerformanceRepository.findFirstByAgentIdOrderByTimeDesc(agentId))
                .thenReturn(Optional.empty());
        when(agentPerformanceRepository.save(any(AgentPerformance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        agentPerformanceService.updateAgentMetrics(agentId, 0.85, null, null, 0.90, null);

        // Assert
        verify(agentPerformanceRepository).save(performanceCaptor.capture());
        AgentPerformance saved = performanceCaptor.getValue();

        assertNotNull(saved);
        assertEquals(0.85, saved.getAvgQualityScore());
        assertNull(saved.getAvgSentimentScore());
        assertNull(saved.getAvgCustomerSatisfaction());
        assertEquals(0.90, saved.getCompliancePassRate());
        assertNull(saved.getAvgChurnRisk());
    }

    @Test
    void getTopAgents_WithDefaultLimit_ReturnsTopAgents() {
        // Arrange
        List<AgentPerformance> topPerformers = List.of(
                createPerformance("agent-001", 100, 0.95, 0.90, 0.98, 0.80, 0.15),
                createPerformance("agent-002", 80, 0.92, 0.88, 0.96, 0.78, 0.18)
        );

        when(agentPerformanceRepository.findTopAgentsByQualityScore(any(LocalDateTime.class)))
                .thenReturn(topPerformers);

        // Mock the recursive call to getAgentPerformance
        when(agentPerformanceRepository.findByAgentIdAndTimeBetweenOrderByTimeDesc(
                anyString(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(topPerformers.get(0)));

        // Act
        List<AgentMetrics> result = agentPerformanceService.getTopAgents(null);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(agentPerformanceRepository).findTopAgentsByQualityScore(any(LocalDateTime.class));
    }

    @Test
    void getTopAgents_WithCustomLimit_ReturnsLimitedAgents() {
        // Arrange
        List<AgentPerformance> topPerformers = List.of(
                createPerformance("agent-001", 100, 0.95, 0.90, 0.98, 0.80, 0.15),
                createPerformance("agent-002", 80, 0.92, 0.88, 0.96, 0.78, 0.18),
                createPerformance("agent-003", 75, 0.90, 0.85, 0.94, 0.75, 0.20),
                createPerformance("agent-004", 70, 0.88, 0.82, 0.92, 0.72, 0.22),
                createPerformance("agent-005", 65, 0.85, 0.80, 0.90, 0.70, 0.25)
        );

        when(agentPerformanceRepository.findTopAgentsByQualityScore(any(LocalDateTime.class)))
                .thenReturn(topPerformers);

        // Mock the recursive call to getAgentPerformance
        when(agentPerformanceRepository.findByAgentIdAndTimeBetweenOrderByTimeDesc(
                anyString(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    String agentId = invocation.getArgument(0);
                    return topPerformers.stream()
                            .filter(p -> p.getAgentId().equals(agentId))
                            .limit(1)
                            .toList();
                });

        // Act
        List<AgentMetrics> result = agentPerformanceService.getTopAgents(3);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size()); // Limited to 3
    }

    @Test
    void getTopAgents_NoData_ReturnsEmptyList() {
        // Arrange
        when(agentPerformanceRepository.findTopAgentsByQualityScore(any(LocalDateTime.class)))
                .thenReturn(List.of());

        // Act
        List<AgentMetrics> result = agentPerformanceService.getTopAgents(null);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void updateAgentMetrics_CalculatesRunningAverageCorrectly() {
        // Arrange
        String agentId = "agent-006";
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime timeSlot = now.withMinute(0).withSecond(0).withNano(0);

        AgentPerformance existing = AgentPerformance.builder()
                .id(1L)
                .agentId(agentId)
                .time(timeSlot)
                .callsProcessed(3)
                .avgQualityScore(0.80)  // Average of 3 calls
                .build();

        when(agentPerformanceRepository.findFirstByAgentIdOrderByTimeDesc(agentId))
                .thenReturn(Optional.of(existing));
        when(agentPerformanceRepository.save(any(AgentPerformance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        agentPerformanceService.updateAgentMetrics(agentId, 1.0, null, null, null, null);

        // Assert
        verify(agentPerformanceRepository).save(performanceCaptor.capture());
        AgentPerformance updated = performanceCaptor.getValue();

        // Running average: ((0.80 * 3) + 1.0) / 4 = 3.4 / 4 = 0.85
        assertEquals(0.85, updated.getAvgQualityScore(), 0.001);
        assertEquals(4, updated.getCallsProcessed());
    }

    // Helper method
    private AgentPerformance createPerformance(String agentId, Integer callsProcessed,
                                               Double qualityScore, Double customerSatisfaction,
                                               Double complianceRate, Double sentimentScore,
                                               Double churnRisk) {
        return AgentPerformance.builder()
                .id(1L)
                .agentId(agentId)
                .time(LocalDateTime.now().minusDays(1))
                .callsProcessed(callsProcessed)
                .avgQualityScore(qualityScore)
                .avgCustomerSatisfaction(customerSatisfaction)
                .compliancePassRate(complianceRate)
                .avgSentimentScore(sentimentScore)
                .avgChurnRisk(churnRisk)
                .build();
    }
}
