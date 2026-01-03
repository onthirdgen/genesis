package com.callaudit.voc.service;

import com.callaudit.voc.model.Intent;
import com.callaudit.voc.model.Satisfaction;
import com.callaudit.voc.model.VocInsight;
import com.callaudit.voc.repository.VocInsightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for CRUD operations on VoC insights
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InsightService {

    private final VocInsightRepository repository;

    /**
     * Save a VoC insight
     */
    @Transactional
    public VocInsight saveInsight(VocInsight insight) {
        log.info("Saving VoC insight for call: {}", insight.getCallId());
        return repository.save(insight);
    }

    /**
     * Get insight by call ID
     */
    public Optional<VocInsight> getInsightByCallId(UUID callId) {
        return repository.findByCallId(callId);
    }

    /**
     * Get all insights
     */
    public List<VocInsight> getAllInsights() {
        return repository.findAll();
    }

    /**
     * Get insights by intent
     */
    public List<VocInsight> getInsightsByIntent(Intent intent) {
        return repository.findByPrimaryIntent(intent);
    }

    /**
     * Get insights by satisfaction level
     */
    public List<VocInsight> getInsightsBySatisfaction(Satisfaction satisfaction) {
        return repository.findByCustomerSatisfaction(satisfaction);
    }

    /**
     * Get high churn risk customers
     */
    public List<VocInsight> getHighChurnRiskCustomers(double threshold) {
        return repository.findByPredictedChurnRiskGreaterThanEqual(BigDecimal.valueOf(threshold));
    }

    /**
     * Get insights within date range
     */
    public List<VocInsight> getInsightsByDateRange(LocalDateTime start, LocalDateTime end) {
        return repository.findByCreatedAtBetween(start, end);
    }

    /**
     * Get top keywords across all insights
     */
    public Map<String, Long> getTopKeywords(int limit) {
        List<Object[]> results = repository.findTopKeywords(limit);
        Map<String, Long> keywords = new HashMap<>();

        for (Object[] result : results) {
            keywords.put((String) result[0], ((Number) result[1]).longValue());
        }

        return keywords;
    }

    /**
     * Get top topics across all insights
     */
    public Map<String, Long> getTopTopics(int limit) {
        List<Object[]> results = repository.findTopTopics(limit);
        Map<String, Long> topics = new HashMap<>();

        for (Object[] result : results) {
            topics.put((String) result[0], ((Number) result[1]).longValue());
        }

        return topics;
    }

    /**
     * Get intent distribution
     */
    public Map<Intent, Long> getIntentDistribution() {
        List<Object[]> results = repository.countByIntent();
        Map<Intent, Long> distribution = new HashMap<>();

        for (Object[] result : results) {
            distribution.put((Intent) result[0], ((Number) result[1]).longValue());
        }

        return distribution;
    }

    /**
     * Get satisfaction distribution
     */
    public Map<Satisfaction, Long> getSatisfactionDistribution() {
        List<Object[]> results = repository.countBySatisfaction();
        Map<Satisfaction, Long> distribution = new HashMap<>();

        for (Object[] result : results) {
            distribution.put((Satisfaction) result[0], ((Number) result[1]).longValue());
        }

        return distribution;
    }

    /**
     * Get average churn risk
     */
    public Double getAverageChurnRisk() {
        Double avgRisk = repository.getAverageChurnRisk();
        return avgRisk != null ? avgRisk : 0.0;
    }

    /**
     * Get aggregate trends
     */
    public Map<String, Object> getAggregateTrends() {
        Map<String, Object> trends = new HashMap<>();

        trends.put("intentDistribution", getIntentDistribution());
        trends.put("satisfactionDistribution", getSatisfactionDistribution());
        trends.put("averageChurnRisk", getAverageChurnRisk());
        trends.put("totalInsights", repository.count());
        trends.put("highRiskCount", getHighChurnRiskCustomers(0.7).size());

        return trends;
    }
}
