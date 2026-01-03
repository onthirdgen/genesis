package com.callaudit.voc.repository;

import com.callaudit.voc.model.Intent;
import com.callaudit.voc.model.Satisfaction;
import com.callaudit.voc.model.VocInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for VoC Insights
 */
@Repository
public interface VocInsightRepository extends JpaRepository<VocInsight, UUID> {

    /**
     * Find insight by call ID
     */
    Optional<VocInsight> findByCallId(UUID callId);

    /**
     * Find insights by primary intent
     */
    List<VocInsight> findByPrimaryIntent(Intent intent);

    /**
     * Find insights by customer satisfaction level
     */
    List<VocInsight> findByCustomerSatisfaction(Satisfaction satisfaction);

    /**
     * Find high churn risk customers
     */
    List<VocInsight> findByPredictedChurnRiskGreaterThanEqual(BigDecimal threshold);

    /**
     * Find insights created within a date range
     */
    List<VocInsight> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Get top keywords across all insights
     */
    @Query(value = "SELECT keyword, COUNT(*) as frequency " +
                   "FROM voc.voc_insights, jsonb_array_elements_text(keywords) as keyword " +
                   "GROUP BY keyword " +
                   "ORDER BY frequency DESC " +
                   "LIMIT :limit", nativeQuery = true)
    List<Object[]> findTopKeywords(@Param("limit") int limit);

    /**
     * Get top topics across all insights
     */
    @Query(value = "SELECT topic, COUNT(*) as frequency " +
                   "FROM voc.voc_insights, jsonb_array_elements_text(topics) as topic " +
                   "GROUP BY topic " +
                   "ORDER BY frequency DESC " +
                   "LIMIT :limit", nativeQuery = true)
    List<Object[]> findTopTopics(@Param("limit") int limit);

    /**
     * Count insights by intent
     */
    @Query("SELECT v.primaryIntent, COUNT(v) FROM VocInsight v GROUP BY v.primaryIntent")
    List<Object[]> countByIntent();

    /**
     * Count insights by satisfaction
     */
    @Query("SELECT v.customerSatisfaction, COUNT(v) FROM VocInsight v GROUP BY v.customerSatisfaction")
    List<Object[]> countBySatisfaction();

    /**
     * Get average churn risk
     */
    @Query("SELECT AVG(v.predictedChurnRisk) FROM VocInsight v")
    Double getAverageChurnRisk();
}
