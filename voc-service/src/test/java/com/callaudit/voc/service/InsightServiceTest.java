package com.callaudit.voc.service;

import com.callaudit.voc.model.Intent;
import com.callaudit.voc.model.Satisfaction;
import com.callaudit.voc.model.VocInsight;
import com.callaudit.voc.repository.VocInsightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InsightService
 */
@ExtendWith(MockitoExtension.class)
class InsightServiceTest {

    private static final UUID CALL_ID_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CALL_ID_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID NONEXISTENT_CALL_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    @Mock
    private VocInsightRepository repository;

    @InjectMocks
    private InsightService insightService;

    private VocInsight testInsight;

    @BeforeEach
    void setUp() {
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
    void saveInsight_ValidInsight_ReturnsSavedInsight() {
        // Arrange
        when(repository.save(any(VocInsight.class))).thenReturn(testInsight);

        // Act
        VocInsight result = insightService.saveInsight(testInsight);

        // Assert
        assertNotNull(result);
        assertEquals(testInsight.getId(), result.getId());
        assertEquals(testInsight.getCallId(), result.getCallId());
        verify(repository).save(testInsight);
    }

    @Test
    void getInsightByCallId_ExistingCall_ReturnsInsight() {
        // Arrange
        when(repository.findByCallId(CALL_ID_1)).thenReturn(Optional.of(testInsight));

        // Act
        Optional<VocInsight> result = insightService.getInsightByCallId(CALL_ID_1);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(CALL_ID_1, result.get().getCallId());
        verify(repository).findByCallId(CALL_ID_1);
    }

    @Test
    void getInsightByCallId_NonExistingCall_ReturnsEmpty() {
        // Arrange
        when(repository.findByCallId(NONEXISTENT_CALL_ID)).thenReturn(Optional.empty());

        // Act
        Optional<VocInsight> result = insightService.getInsightByCallId(NONEXISTENT_CALL_ID);

        // Assert
        assertFalse(result.isPresent());
        verify(repository).findByCallId(NONEXISTENT_CALL_ID);
    }

    @Test
    void getAllInsights_MultipleInsights_ReturnsAllInsights() {
        // Arrange
        VocInsight insight2 = VocInsight.builder()
            .id(UUID.fromString("00000000-0000-0000-0000-100000000002"))
            .callId(CALL_ID_2)
            .primaryIntent(Intent.inquiry)
            .customerSatisfaction(Satisfaction.medium)
            .predictedChurnRisk(BigDecimal.valueOf(0.3))
            .build();

        when(repository.findAll()).thenReturn(List.of(testInsight, insight2));

        // Act
        List<VocInsight> results = insightService.getAllInsights();

        // Assert
        assertEquals(2, results.size());
        verify(repository).findAll();
    }

    @Test
    void getInsightsByIntent_ComplaintIntent_ReturnsMatchingInsights() {
        // Arrange
        when(repository.findByPrimaryIntent(Intent.complaint))
            .thenReturn(List.of(testInsight));

        // Act
        List<VocInsight> results = insightService.getInsightsByIntent(Intent.complaint);

        // Assert
        assertEquals(1, results.size());
        assertEquals(Intent.complaint, results.get(0).getPrimaryIntent());
        verify(repository).findByPrimaryIntent(Intent.complaint);
    }

    @Test
    void getInsightsByIntent_NoMatches_ReturnsEmptyList() {
        // Arrange
        when(repository.findByPrimaryIntent(Intent.compliment))
            .thenReturn(List.of());

        // Act
        List<VocInsight> results = insightService.getInsightsByIntent(Intent.compliment);

        // Assert
        assertTrue(results.isEmpty());
    }

    @Test
    void getInsightsBySatisfaction_LowSatisfaction_ReturnsMatchingInsights() {
        // Arrange
        when(repository.findByCustomerSatisfaction(Satisfaction.low))
            .thenReturn(List.of(testInsight));

        // Act
        List<VocInsight> results = insightService.getInsightsBySatisfaction(Satisfaction.low);

        // Assert
        assertEquals(1, results.size());
        assertEquals(Satisfaction.low, results.get(0).getCustomerSatisfaction());
        verify(repository).findByCustomerSatisfaction(Satisfaction.low);
    }

    @Test
    void getHighChurnRiskCustomers_WithThreshold_ReturnsHighRiskCustomers() {
        // Arrange
        double threshold = 0.7;
        when(repository.findByPredictedChurnRiskGreaterThanEqual(any(BigDecimal.class)))
            .thenReturn(List.of(testInsight));

        // Act
        List<VocInsight> results = insightService.getHighChurnRiskCustomers(threshold);

        // Assert
        assertEquals(1, results.size());
        assertThat(results.get(0).getPredictedChurnRisk()).isGreaterThanOrEqualTo(BigDecimal.valueOf(threshold));
        verify(repository).findByPredictedChurnRiskGreaterThanEqual(any(BigDecimal.class));
    }

    @Test
    void getHighChurnRiskCustomers_LowerThreshold_ReturnsMoreCustomers() {
        // Arrange
        VocInsight mediumRisk = VocInsight.builder()
            .id(UUID.fromString("00000000-0000-0000-0000-100000000002"))
            .callId(CALL_ID_2)
            .predictedChurnRisk(BigDecimal.valueOf(0.5))
            .build();

        when(repository.findByPredictedChurnRiskGreaterThanEqual(any(BigDecimal.class)))
            .thenReturn(List.of(testInsight, mediumRisk));

        // Act
        List<VocInsight> results = insightService.getHighChurnRiskCustomers(0.4);

        // Assert
        assertEquals(2, results.size());
    }

    @Test
    void getInsightsByDateRange_ValidRange_ReturnsInsightsInRange() {
        // Arrange
        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();

        when(repository.findByCreatedAtBetween(start, end))
            .thenReturn(List.of(testInsight));

        // Act
        List<VocInsight> results = insightService.getInsightsByDateRange(start, end);

        // Assert
        assertEquals(1, results.size());
        verify(repository).findByCreatedAtBetween(start, end);
    }

    @Test
    void getTopKeywords_ReturnsKeywordFrequencyMap() {
        // Arrange
        Object[] row1 = {"billing", 10L};
        Object[] row2 = {"problem", 8L};
        Object[] row3 = {"charge", 6L};

        when(repository.findTopKeywords(10))
            .thenReturn(List.of(row1, row2, row3));

        // Act
        Map<String, Long> results = insightService.getTopKeywords(10);

        // Assert
        assertEquals(3, results.size());
        assertEquals(10L, results.get("billing"));
        assertEquals(8L, results.get("problem"));
        assertEquals(6L, results.get("charge"));
        verify(repository).findTopKeywords(10);
    }

    @Test
    void getTopKeywords_EmptyResults_ReturnsEmptyMap() {
        // Arrange
        when(repository.findTopKeywords(10)).thenReturn(List.of());

        // Act
        Map<String, Long> results = insightService.getTopKeywords(10);

        // Assert
        assertTrue(results.isEmpty());
    }

    @Test
    void getTopTopics_ReturnsTopicFrequencyMap() {
        // Arrange
        Object[] row1 = {"Billing", 15L};
        Object[] row2 = {"Technical Support", 12L};

        when(repository.findTopTopics(10))
            .thenReturn(List.of(row1, row2));

        // Act
        Map<String, Long> results = insightService.getTopTopics(10);

        // Assert
        assertEquals(2, results.size());
        assertEquals(15L, results.get("Billing"));
        assertEquals(12L, results.get("Technical Support"));
    }

    @Test
    void getIntentDistribution_ReturnsIntentCounts() {
        // Arrange
        Object[] row1 = {Intent.complaint, 25L};
        Object[] row2 = {Intent.inquiry, 30L};
        Object[] row3 = {Intent.request, 15L};

        when(repository.countByIntent())
            .thenReturn(List.of(row1, row2, row3));

        // Act
        Map<Intent, Long> results = insightService.getIntentDistribution();

        // Assert
        assertEquals(3, results.size());
        assertEquals(25L, results.get(Intent.complaint));
        assertEquals(30L, results.get(Intent.inquiry));
        assertEquals(15L, results.get(Intent.request));
    }

    @Test
    void getSatisfactionDistribution_ReturnsSatisfactionCounts() {
        // Arrange
        Object[] row1 = {Satisfaction.high, 40L};
        Object[] row2 = {Satisfaction.medium, 35L};
        Object[] row3 = {Satisfaction.low, 25L};

        when(repository.countBySatisfaction())
            .thenReturn(List.of(row1, row2, row3));

        // Act
        Map<Satisfaction, Long> results = insightService.getSatisfactionDistribution();

        // Assert
        assertEquals(3, results.size());
        assertEquals(40L, results.get(Satisfaction.high));
        assertEquals(35L, results.get(Satisfaction.medium));
        assertEquals(25L, results.get(Satisfaction.low));
    }

    @Test
    void getAverageChurnRisk_WithData_ReturnsAverage() {
        // Arrange
        when(repository.getAverageChurnRisk()).thenReturn(0.45);

        // Act
        Double result = insightService.getAverageChurnRisk();

        // Assert
        assertEquals(0.45, result);
    }

    @Test
    void getAverageChurnRisk_NoData_ReturnsZero() {
        // Arrange
        when(repository.getAverageChurnRisk()).thenReturn(null);

        // Act
        Double result = insightService.getAverageChurnRisk();

        // Assert
        assertEquals(0.0, result);
    }

    @Test
    void getAggregateTrends_ReturnsCompleteMetrics() {
        // Arrange
        Object[] intentRow = new Object[]{Intent.complaint, 10L};
        when(repository.countByIntent()).thenReturn(
            java.util.Collections.singletonList(intentRow)
        );
        Object[] satisfactionRow = new Object[]{Satisfaction.low, 10L};
        when(repository.countBySatisfaction()).thenReturn(
            java.util.Collections.singletonList(satisfactionRow)
        );
        when(repository.getAverageChurnRisk()).thenReturn(0.5);
        when(repository.count()).thenReturn(100L);
        when(repository.findByPredictedChurnRiskGreaterThanEqual(any(BigDecimal.class)))
            .thenReturn(List.of(testInsight));

        // Act
        Map<String, Object> trends = insightService.getAggregateTrends();

        // Assert
        assertNotNull(trends);
        assertTrue(trends.containsKey("intentDistribution"));
        assertTrue(trends.containsKey("satisfactionDistribution"));
        assertTrue(trends.containsKey("averageChurnRisk"));
        assertTrue(trends.containsKey("totalInsights"));
        assertTrue(trends.containsKey("highRiskCount"));

        assertEquals(0.5, trends.get("averageChurnRisk"));
        assertEquals(100L, trends.get("totalInsights"));
        assertEquals(1, trends.get("highRiskCount"));
    }

    @Test
    void getAggregateTrends_AllFields_ArePopulated() {
        // Arrange
        when(repository.countByIntent()).thenReturn(List.of());
        when(repository.countBySatisfaction()).thenReturn(List.of());
        when(repository.getAverageChurnRisk()).thenReturn(0.0);
        when(repository.count()).thenReturn(0L);
        when(repository.findByPredictedChurnRiskGreaterThanEqual(any(BigDecimal.class)))
            .thenReturn(List.of());

        // Act
        Map<String, Object> trends = insightService.getAggregateTrends();

        // Assert
        assertEquals(5, trends.size());
        verify(repository).countByIntent();
        verify(repository).countBySatisfaction();
        verify(repository).getAverageChurnRisk();
        verify(repository).count();
        verify(repository).findByPredictedChurnRiskGreaterThanEqual(any(BigDecimal.class));
    }
}
