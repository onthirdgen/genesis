package com.callaudit.voc.repository;

import com.callaudit.voc.model.Intent;
import com.callaudit.voc.model.Satisfaction;
import com.callaudit.voc.model.VocInsight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for VocInsightRepository
 *
 * @SpringBootTest provides full application context for testing.
 * @Transactional ensures each test rolls back after execution.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VocInsightRepositoryTest {

    private static final UUID CALL_ID_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CALL_ID_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CALL_ID_3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID CALL_ID_4 = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID CALL_ID_MINIMAL = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID NONEXISTENT_CALL_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    @Autowired
    private VocInsightRepository repository;

    private VocInsight testInsight;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        testInsight = VocInsight.builder()
            .callId(CALL_ID_1)
            .primaryIntent(Intent.complaint)
            .topics(List.of("Billing", "Technical Support"))
            .keywords(List.of("billing", "problem", "charge"))
            .customerSatisfaction(Satisfaction.low)
            .predictedChurnRisk(BigDecimal.valueOf(0.8))
            .actionableItems(List.of("Contact customer", "Review billing"))
            .summary("Customer complaint about billing")
            .build();
    }

    @Test
    void save_NewInsight_GeneratesId() {
        // Act
        VocInsight savedInsight = repository.save(testInsight);

        // Assert
        assertNotNull(savedInsight.getId());
        assertNotNull(savedInsight.getCreatedAt());
        assertEquals(testInsight.getCallId(), savedInsight.getCallId());
        assertEquals(testInsight.getPrimaryIntent(), savedInsight.getPrimaryIntent());
        assertEquals(testInsight.getCustomerSatisfaction(), savedInsight.getCustomerSatisfaction());
    }

    @Test
    void findByCallId_ExistingCall_ReturnsInsight() {
        // Arrange
        VocInsight savedInsight = repository.save(testInsight);

        // Act
        Optional<VocInsight> foundInsight = repository.findByCallId(CALL_ID_1);

        // Assert
        assertTrue(foundInsight.isPresent());
        assertEquals(CALL_ID_1, foundInsight.get().getCallId());
        assertEquals(savedInsight.getId(), foundInsight.get().getId());
    }

    @Test
    void findByCallId_NonExistingCall_ReturnsEmpty() {
        // Act
        Optional<VocInsight> foundInsight = repository.findByCallId(NONEXISTENT_CALL_ID);

        // Assert
        assertFalse(foundInsight.isPresent());
    }

    @Test
    void findByPrimaryIntent_ComplaintIntent_ReturnsMatchingInsights() {
        // Arrange
        repository.save(testInsight);

        VocInsight inquiryInsight = createInsight(CALL_ID_2, Intent.inquiry, Satisfaction.medium, 0.3);
        repository.save(inquiryInsight);

        // Act
        List<VocInsight> complaints = repository.findByPrimaryIntent(Intent.complaint);

        // Assert
        assertEquals(1, complaints.size());
        assertEquals(Intent.complaint, complaints.get(0).getPrimaryIntent());
        assertEquals(CALL_ID_1, complaints.get(0).getCallId());
    }

    @Test
    void findByPrimaryIntent_MultipleMatches_ReturnsAll() {
        // Arrange
        repository.save(testInsight);

        VocInsight complaint2 = createInsight(CALL_ID_3, Intent.complaint, Satisfaction.low, 0.7);
        repository.save(complaint2);

        // Act
        List<VocInsight> complaints = repository.findByPrimaryIntent(Intent.complaint);

        // Assert
        assertEquals(2, complaints.size());
        assertThat(complaints).allMatch(i -> i.getPrimaryIntent() == Intent.complaint);
    }

    @Test
    void findByCustomerSatisfaction_LowSatisfaction_ReturnsMatchingInsights() {
        // Arrange
        repository.save(testInsight);

        VocInsight highSatisfaction = createInsight(CALL_ID_2, Intent.compliment, Satisfaction.high, 0.1);
        repository.save(highSatisfaction);

        // Act
        List<VocInsight> lowSatisfaction = repository.findByCustomerSatisfaction(Satisfaction.low);

        // Assert
        assertEquals(1, lowSatisfaction.size());
        assertEquals(Satisfaction.low, lowSatisfaction.get(0).getCustomerSatisfaction());
    }

    @Test
    void findByPredictedChurnRiskGreaterThanEqual_HighThreshold_ReturnsHighRiskOnly() {
        // Arrange
        repository.save(testInsight); // 0.8 risk

        VocInsight lowRisk = createInsight(CALL_ID_2, Intent.compliment, Satisfaction.high, 0.2);
        repository.save(lowRisk);

        VocInsight mediumRisk = createInsight(CALL_ID_3, Intent.inquiry, Satisfaction.medium, 0.5);
        repository.save(mediumRisk);

        // Act
        List<VocInsight> highRisk = repository.findByPredictedChurnRiskGreaterThanEqual(BigDecimal.valueOf(0.7));

        // Assert
        assertEquals(1, highRisk.size());
        assertThat(highRisk.get(0).getPredictedChurnRisk()).isGreaterThanOrEqualTo(BigDecimal.valueOf(0.7));
    }

    @Test
    void findByPredictedChurnRiskGreaterThanEqual_LowerThreshold_ReturnsMultiple() {
        // Arrange
        repository.save(testInsight); // 0.8 risk

        VocInsight lowRisk = createInsight(CALL_ID_2, Intent.compliment, Satisfaction.high, 0.2);
        repository.save(lowRisk);

        VocInsight mediumRisk = createInsight(CALL_ID_3, Intent.inquiry, Satisfaction.medium, 0.5);
        repository.save(mediumRisk);

        // Act
        List<VocInsight> atRisk = repository.findByPredictedChurnRiskGreaterThanEqual(BigDecimal.valueOf(0.5));

        // Assert
        assertEquals(2, atRisk.size());
        assertThat(atRisk).allMatch(i -> i.getPredictedChurnRisk().compareTo(BigDecimal.valueOf(0.5)) >= 0);
    }

    @Test
    void findByCreatedAtBetween_ValidRange_ReturnsInsightsInRange() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(7);
        LocalDateTime end = now.plusDays(1);

        repository.save(testInsight);

        // Act
        List<VocInsight> insights = repository.findByCreatedAtBetween(start, end);

        // Assert
        assertEquals(1, insights.size());
        assertThat(insights.get(0).getCreatedAt()).isBetween(start, end);
    }

    @Test
    void findByCreatedAtBetween_OutsideRange_ReturnsEmpty() {
        // Arrange
        repository.save(testInsight);

        LocalDateTime start = LocalDateTime.now().minusDays(30);
        LocalDateTime end = LocalDateTime.now().minusDays(20);

        // Act
        List<VocInsight> insights = repository.findByCreatedAtBetween(start, end);

        // Assert
        assertTrue(insights.isEmpty());
    }

    @Test
    @Disabled("Requires PostgreSQL jsonb_array_elements_text function - not available in H2")
    void findTopKeywords_WithMultipleInsights_ReturnsKeywordFrequencies() {
        // Arrange
        testInsight.setKeywords(List.of("billing", "problem", "charge"));
        repository.save(testInsight);

        VocInsight insight2 = createInsight(CALL_ID_2, Intent.inquiry, Satisfaction.medium, 0.3);
        insight2.setKeywords(List.of("billing", "question", "account"));
        repository.save(insight2);

        VocInsight insight3 = createInsight(CALL_ID_3, Intent.complaint, Satisfaction.low, 0.7);
        insight3.setKeywords(List.of("billing", "problem", "issue"));
        repository.save(insight3);

        // Act
        List<Object[]> topKeywords = repository.findTopKeywords(5);

        // Assert
        assertFalse(topKeywords.isEmpty());
        assertThat(topKeywords.size()).isLessThanOrEqualTo(5);

        // "billing" should appear 3 times
        Object[] topKeyword = topKeywords.get(0);
        assertEquals("billing", topKeyword[0]);
        assertEquals(3L, topKeyword[1]);
    }

    @Test
    @Disabled("Requires PostgreSQL jsonb_array_elements_text function - not available in H2")
    void findTopTopics_WithMultipleInsights_ReturnsTopicFrequencies() {
        // Arrange
        testInsight.setTopics(List.of("Billing", "Technical Support"));
        repository.save(testInsight);

        VocInsight insight2 = createInsight(CALL_ID_2, Intent.inquiry, Satisfaction.medium, 0.3);
        insight2.setTopics(List.of("Billing", "Account Management"));
        repository.save(insight2);

        // Act
        List<Object[]> topTopics = repository.findTopTopics(5);

        // Assert
        assertFalse(topTopics.isEmpty());
        assertThat(topTopics.size()).isLessThanOrEqualTo(5);

        // "Billing" should appear 2 times
        Object[] topTopic = topTopics.get(0);
        assertEquals("Billing", topTopic[0]);
        assertEquals(2L, topTopic[1]);
    }

    @Test
    void countByIntent_MultipleInsights_ReturnsIntentCounts() {
        // Arrange
        repository.save(testInsight); // COMPLAINT

        VocInsight inquiry = createInsight(CALL_ID_2, Intent.inquiry, Satisfaction.medium, 0.3);
        repository.save(inquiry);

        VocInsight complaint2 = createInsight(CALL_ID_3, Intent.complaint, Satisfaction.low, 0.7);
        repository.save(complaint2);

        // Act
        List<Object[]> intentCounts = repository.countByIntent();

        // Assert
        assertEquals(2, intentCounts.size());

        Map<Intent, Long> intentMap = intentCounts.stream()
            .collect(java.util.stream.Collectors.toMap(
                row -> (Intent) row[0],
                row -> (Long) row[1]
            ));

        assertEquals(2L, intentMap.get(Intent.complaint));
        assertEquals(1L, intentMap.get(Intent.inquiry));
    }

    @Test
    void countBySatisfaction_MultipleInsights_ReturnsSatisfactionCounts() {
        // Arrange
        repository.save(testInsight); // LOW

        VocInsight medium = createInsight(CALL_ID_2, Intent.inquiry, Satisfaction.medium, 0.3);
        repository.save(medium);

        VocInsight low2 = createInsight(CALL_ID_3, Intent.complaint, Satisfaction.low, 0.7);
        repository.save(low2);

        VocInsight high = createInsight(CALL_ID_4, Intent.compliment, Satisfaction.high, 0.1);
        repository.save(high);

        // Act
        List<Object[]> satisfactionCounts = repository.countBySatisfaction();

        // Assert
        assertEquals(3, satisfactionCounts.size());

        Map<Satisfaction, Long> satisfactionMap = satisfactionCounts.stream()
            .collect(java.util.stream.Collectors.toMap(
                row -> (Satisfaction) row[0],
                row -> (Long) row[1]
            ));

        assertEquals(2L, satisfactionMap.get(Satisfaction.low));
        assertEquals(1L, satisfactionMap.get(Satisfaction.medium));
        assertEquals(1L, satisfactionMap.get(Satisfaction.high));
    }

    @Test
    void getAverageChurnRisk_MultipleInsights_ReturnsAverage() {
        // Arrange
        repository.save(testInsight); // 0.8

        VocInsight lowRisk = createInsight(CALL_ID_2, Intent.compliment, Satisfaction.high, 0.2);
        repository.save(lowRisk);

        // Expected average: (0.8 + 0.2) / 2 = 0.5

        // Act
        Double avgRisk = repository.getAverageChurnRisk();

        // Assert
        assertNotNull(avgRisk);
        assertEquals(0.5, avgRisk, 0.01);
    }

    @Test
    void getAverageChurnRisk_NoInsights_ReturnsNull() {
        // Arrange
        repository.deleteAll();

        // Act
        Double avgRisk = repository.getAverageChurnRisk();

        // Assert
        assertNull(avgRisk);
    }

    @Test
    void save_InsightWithNullableFields_Success() {
        // Arrange
        VocInsight minimalInsight = VocInsight.builder()
            .callId(CALL_ID_MINIMAL)
            .primaryIntent(Intent.other)
            .customerSatisfaction(Satisfaction.medium)
            .predictedChurnRisk(BigDecimal.valueOf(0.5))
            .build();

        // Act
        VocInsight savedInsight = repository.save(minimalInsight);

        // Assert
        assertNotNull(savedInsight.getId());
        assertNull(savedInsight.getTopics());
        assertNull(savedInsight.getKeywords());
        assertNull(savedInsight.getActionableItems());
        assertNull(savedInsight.getSummary());
    }

    @Test
    void delete_ExistingInsight_RemovesFromDatabase() {
        // Arrange
        VocInsight savedInsight = repository.save(testInsight);
        UUID insightId = savedInsight.getId();

        // Act
        repository.delete(savedInsight);
        Optional<VocInsight> deletedInsight = repository.findById(insightId);

        // Assert
        assertFalse(deletedInsight.isPresent());
    }

    @Test
    void findAll_MultipleInsights_ReturnsAllInsights() {
        // Arrange
        repository.save(testInsight);

        VocInsight insight2 = createInsight(CALL_ID_2, Intent.inquiry, Satisfaction.medium, 0.3);
        repository.save(insight2);

        VocInsight insight3 = createInsight(CALL_ID_3, Intent.compliment, Satisfaction.high, 0.1);
        repository.save(insight3);

        // Act
        List<VocInsight> allInsights = repository.findAll();

        // Assert
        assertEquals(3, allInsights.size());
    }

    @Test
    void count_MultipleInsights_ReturnsCorrectCount() {
        // Arrange
        repository.save(testInsight);
        repository.save(createInsight(CALL_ID_2, Intent.inquiry, Satisfaction.medium, 0.3));
        repository.save(createInsight(CALL_ID_3, Intent.compliment, Satisfaction.high, 0.1));

        // Act
        long count = repository.count();

        // Assert
        assertEquals(3, count);
    }

    // Helper methods

    private VocInsight createInsight(UUID callId, Intent intent, Satisfaction satisfaction, double churnRisk) {
        return VocInsight.builder()
            .callId(callId)
            .primaryIntent(intent)
            .customerSatisfaction(satisfaction)
            .predictedChurnRisk(BigDecimal.valueOf(churnRisk))
            .build();
    }
}
