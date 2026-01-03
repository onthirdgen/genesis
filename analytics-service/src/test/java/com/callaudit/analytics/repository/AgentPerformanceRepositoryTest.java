package com.callaudit.analytics.repository;

import com.callaudit.analytics.model.AgentPerformance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository integration tests using @Transactional for automatic rollback
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AgentPerformanceRepositoryTest {

    @Autowired
    private AgentPerformanceRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void save_NewPerformanceRecord_GeneratesId() {
        // Arrange
        AgentPerformance performance = createPerformance("agent-001", 0.85, 0.90, 0.95, 0.75, 0.25, 50);

        // Act
        AgentPerformance saved = repository.save(performance);

        // Assert
        assertNotNull(saved.getId());
        assertEquals("agent-001", saved.getAgentId());
        assertEquals(0.85, saved.getAvgQualityScore());
    }

    @Test
    void findByAgentIdAndTimeBetweenOrderByTimeDesc_WithinRange_ReturnsRecords() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yesterday = now.minusDays(1);
        LocalDateTime twoDaysAgo = now.minusDays(2);

        repository.save(createPerformanceWithTime("agent-001", yesterday, 0.85));
        repository.save(createPerformanceWithTime("agent-001", twoDaysAgo, 0.80));
        repository.save(createPerformanceWithTime("agent-002", yesterday, 0.90));

        // Act
        List<AgentPerformance> results = repository.findByAgentIdAndTimeBetweenOrderByTimeDesc(
                "agent-001", now.minusDays(3), now);

        // Assert
        assertEquals(2, results.size());
        assertEquals("agent-001", results.get(0).getAgentId());
        assertEquals("agent-001", results.get(1).getAgentId());
        // Verify descending order
        assertTrue(results.get(0).getTime().isAfter(results.get(1).getTime()));
    }

    @Test
    void findByAgentIdAndTimeBetweenOrderByTimeDesc_OutsideRange_ReturnsEmpty() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        repository.save(createPerformanceWithTime("agent-001", now.minusDays(10), 0.85));

        // Act
        List<AgentPerformance> results = repository.findByAgentIdAndTimeBetweenOrderByTimeDesc(
                "agent-001", now.minusDays(2), now);

        // Assert
        assertTrue(results.isEmpty());
    }

    @Test
    void findFirstByAgentIdOrderByTimeDesc_WithMultipleRecords_ReturnsMostRecent() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        repository.save(createPerformanceWithTime("agent-001", now.minusDays(2), 0.80));
        repository.save(createPerformanceWithTime("agent-001", now.minusDays(1), 0.85));
        repository.save(createPerformanceWithTime("agent-001", now, 0.90));

        // Act
        Optional<AgentPerformance> result = repository.findFirstByAgentIdOrderByTimeDesc("agent-001");

        // Assert
        assertTrue(result.isPresent());
        assertEquals(0.90, result.get().getAvgQualityScore()); // Most recent
    }

    @Test
    void findFirstByAgentIdOrderByTimeDesc_NoRecords_ReturnsEmpty() {
        // Act
        Optional<AgentPerformance> result = repository.findFirstByAgentIdOrderByTimeDesc("nonexistent");

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void findTopAgentsByQualityScore_ReturnsOrderedResults() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        repository.save(createPerformanceWithTime("agent-001", now, 0.95));
        repository.save(createPerformanceWithTime("agent-002", now, 0.90));
        repository.save(createPerformanceWithTime("agent-003", now, 0.85));

        // Act
        List<AgentPerformance> results = repository.findTopAgentsByQualityScore(now.minusDays(1));

        // Assert
        assertEquals(3, results.size());
        // Verify descending order by quality score
        assertTrue(results.get(0).getAvgQualityScore() >= results.get(1).getAvgQualityScore());
        assertTrue(results.get(1).getAvgQualityScore() >= results.get(2).getAvgQualityScore());
    }

    @Test
    void findAllByTimeRange_ReturnsRecordsInRange() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(7);
        LocalDateTime end = now;

        repository.save(createPerformanceWithTime("agent-001", now.minusDays(3), 0.85));
        repository.save(createPerformanceWithTime("agent-002", now.minusDays(2), 0.90));
        repository.save(createPerformanceWithTime("agent-003", now.minusDays(10), 0.80)); // Outside range

        // Act
        List<AgentPerformance> results = repository.findAllByTimeRange(start, end);

        // Assert
        assertEquals(2, results.size());
        results.forEach(p -> {
            assertTrue(p.getTime().isAfter(start) || p.getTime().isEqual(start));
            assertTrue(p.getTime().isBefore(end) || p.getTime().isEqual(end));
        });
    }

    @Test
    void getAverageQualityScore_MultipleRecords_ReturnsCorrectAverage() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        repository.save(createPerformanceWithTime("agent-001", now, 0.80));
        repository.save(createPerformanceWithTime("agent-002", now, 0.90));
        repository.save(createPerformanceWithTime("agent-003", now, 1.00));

        // Act
        Double average = repository.getAverageQualityScore(now.minusDays(1));

        // Assert
        assertNotNull(average);
        assertEquals((0.80 + 0.90 + 1.00) / 3, average, 0.001);
    }

    @Test
    void getAverageQualityScore_NoRecords_ReturnsNull() {
        // Act
        Double average = repository.getAverageQualityScore(LocalDateTime.now().minusDays(1));

        // Assert
        assertNull(average);
    }

    @Test
    void getAverageCompliancePassRate_MultipleRecords_ReturnsCorrectAverage() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        repository.save(createPerformanceWithTime("agent-001", now, 0.85, 0.90));
        repository.save(createPerformanceWithTime("agent-002", now, 0.90, 0.95));
        repository.save(createPerformanceWithTime("agent-003", now, 0.95, 1.00));

        // Act
        Double average = repository.getAverageCompliancePassRate(now.minusDays(1));

        // Assert
        assertNotNull(average);
        assertEquals((0.90 + 0.95 + 1.00) / 3, average, 0.001);
    }

    @Test
    void getAverageSentimentScore_MultipleRecords_ReturnsCorrectAverage() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        repository.save(createPerformance("agent-001", 0.85, 0.90, 0.95, 0.70, 0.25, 50, now));
        repository.save(createPerformance("agent-002", 0.90, 0.95, 1.00, 0.80, 0.20, 60, now));

        // Act
        Double average = repository.getAverageSentimentScore(now.minusDays(1));

        // Assert
        assertNotNull(average);
        assertEquals((0.70 + 0.80) / 2, average, 0.001);
    }

    @Test
    void getAverageCustomerSatisfaction_MultipleRecords_ReturnsCorrectAverage() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        repository.save(createPerformance("agent-001", 0.85, 0.90, 0.95, 0.70, 0.80, 50, now));
        repository.save(createPerformance("agent-002", 0.90, 0.95, 1.00, 0.80, 0.90, 60, now));

        // Act
        Double average = repository.getAverageCustomerSatisfaction(now.minusDays(1));

        // Assert
        assertNotNull(average);
        // customerSatisfaction is the 2nd parameter: 0.90 and 0.95
        assertEquals((0.90 + 0.95) / 2, average, 0.001);
    }

    @Test
    void getTotalCallsProcessed_MultipleRecords_ReturnsSumOfCalls() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        repository.save(createPerformanceWithCalls("agent-001", now, 50));
        repository.save(createPerformanceWithCalls("agent-002", now, 75));
        repository.save(createPerformanceWithCalls("agent-003", now, 100));

        // Act
        Long totalCalls = repository.getTotalCallsProcessed(now.minusDays(1));

        // Assert
        assertNotNull(totalCalls);
        assertEquals(225L, totalCalls); // 50 + 75 + 100
    }

    @Test
    void getTotalCallsProcessed_NoRecords_ReturnsNull() {
        // Act
        Long totalCalls = repository.getTotalCallsProcessed(LocalDateTime.now().minusDays(1));

        // Assert
        assertNull(totalCalls);
    }

    @Test
    void getDistinctAgentCount_MultipleAgents_ReturnsCorrectCount() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        repository.save(createPerformanceWithTime("agent-001", now, 0.85));
        repository.save(createPerformanceWithTime("agent-001", now.minusHours(1), 0.88));
        repository.save(createPerformanceWithTime("agent-002", now, 0.90));
        repository.save(createPerformanceWithTime("agent-003", now, 0.92));

        // Act
        Long agentCount = repository.getDistinctAgentCount(now.minusDays(1));

        // Assert
        assertNotNull(agentCount);
        assertEquals(3L, agentCount); // 3 distinct agents
    }

    @Test
    void getTrendData_ReturnsOrderedByTimeAsc() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime t1 = now.minusDays(3);
        LocalDateTime t2 = now.minusDays(2);
        LocalDateTime t3 = now.minusDays(1);

        repository.save(createPerformanceWithTime("agent-001", t2, 0.85));
        repository.save(createPerformanceWithTime("agent-001", t1, 0.80));
        repository.save(createPerformanceWithTime("agent-001", t3, 0.90));

        // Act
        List<AgentPerformance> results = repository.getTrendData(now.minusDays(4), now);

        // Assert
        assertEquals(3, results.size());
        // Verify ascending order
        assertTrue(results.get(0).getTime().isBefore(results.get(1).getTime()));
        assertTrue(results.get(1).getTime().isBefore(results.get(2).getTime()));
    }

    @Test
    void save_AutoSetsCreatedAtAndUpdatedAt() {
        // Arrange
        AgentPerformance performance = createPerformance("agent-001", 0.85, 0.90, 0.95, 0.75, 0.25, 50);

        // Act
        AgentPerformance saved = repository.save(performance);

        // Assert
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        // Check that timestamps are within 1 second of each other
        assertTrue(Math.abs(java.time.Duration.between(saved.getCreatedAt(), saved.getUpdatedAt()).toMillis()) < 1000);
    }

    // Helper methods

    private AgentPerformance createPerformance(String agentId, Double qualityScore, Double customerSatisfaction,
                                               Double complianceRate, Double sentimentScore,
                                               Double churnRisk, Integer callsProcessed) {
        return createPerformance(agentId, qualityScore, customerSatisfaction, complianceRate,
                sentimentScore, churnRisk, callsProcessed, LocalDateTime.now());
    }

    private AgentPerformance createPerformance(String agentId, Double qualityScore, Double customerSatisfaction,
                                               Double complianceRate, Double sentimentScore,
                                               Double churnRisk, Integer callsProcessed, LocalDateTime time) {
        return AgentPerformance.builder()
                .time(time)
                .agentId(agentId)
                .avgQualityScore(qualityScore)
                .avgCustomerSatisfaction(customerSatisfaction)
                .compliancePassRate(complianceRate)
                .avgSentimentScore(sentimentScore)
                .avgChurnRisk(churnRisk)
                .callsProcessed(callsProcessed)
                .build();
    }

    private AgentPerformance createPerformanceWithTime(String agentId, LocalDateTime time, Double qualityScore) {
        return createPerformance(agentId, qualityScore, 0.85, 0.90, 0.75, 0.25, 50, time);
    }

    private AgentPerformance createPerformanceWithTime(String agentId, LocalDateTime time,
                                                       Double qualityScore, Double complianceRate) {
        return createPerformance(agentId, qualityScore, 0.85, complianceRate, 0.75, 0.25, 50, time);
    }

    private AgentPerformance createPerformanceWithCalls(String agentId, LocalDateTime time, Integer callsProcessed) {
        return createPerformance(agentId, 0.85, 0.85, 0.90, 0.75, 0.25, callsProcessed, time);
    }
}
