package com.callaudit.analytics.service;

import com.callaudit.analytics.model.DashboardMetrics;
import com.callaudit.analytics.repository.AgentPerformanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class DashboardService {

    private final AgentPerformanceRepository agentPerformanceRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String COUNTER_PREFIX = "analytics:counter:";
    private static final String CHURN_RISK_PREFIX = "analytics:churn:";
    private static final String ISSUES_PREFIX = "analytics:issues:";
    private static final String TOPICS_PREFIX = "analytics:topics:";

    @Cacheable(value = "dashboard", key = "'metrics'")
    public DashboardMetrics getDashboardMetrics() {
        log.info("Generating dashboard metrics");

        LocalDateTime since = LocalDateTime.now().minusDays(30);

        // Get aggregated data from database
        Long totalCalls = agentPerformanceRepository.getTotalCallsProcessed(since);
        Double avgQuality = agentPerformanceRepository.getAverageQualityScore(since);
        Double complianceRate = agentPerformanceRepository.getAverageCompliancePassRate(since);
        Double avgSentiment = agentPerformanceRepository.getAverageSentimentScore(since);
        Double avgSatisfaction = agentPerformanceRepository.getAverageCustomerSatisfaction(since);

        // Get real-time counters from Valkey
        Long realtimeCalls = getCounter("total_calls");
        if (realtimeCalls != null && realtimeCalls > 0) {
            totalCalls = (totalCalls != null ? totalCalls : 0L) + realtimeCalls;
        }

        // Get churn risk distribution
        DashboardMetrics.ChurnRiskDistribution churnRisk = getChurnRiskDistribution();

        // Get top issues and topics
        Map<String, Integer> topIssues = getTopItems(ISSUES_PREFIX, 10);
        Map<String, Integer> topTopics = getTopItems(TOPICS_PREFIX, 10);

        return DashboardMetrics.builder()
                .totalCallsProcessed(totalCalls != null ? totalCalls : 0L)
                .averageQualityScore(avgQuality != null ? avgQuality : 0.0)
                .compliancePassRate(complianceRate != null ? complianceRate : 0.0)
                .averageSentimentScore(avgSentiment != null ? avgSentiment : 0.0)
                .customerSatisfactionScore(avgSatisfaction != null ? avgSatisfaction : 0.0)
                .churnRiskDistribution(churnRisk)
                .topIssues(topIssues)
                .topTopics(topTopics)
                .generatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }

    public DashboardMetrics.ChurnRiskDistribution getChurnRiskDistribution() {
        Integer low = getCounterAsInt(CHURN_RISK_PREFIX + "low");
        Integer medium = getCounterAsInt(CHURN_RISK_PREFIX + "medium");
        Integer high = getCounterAsInt(CHURN_RISK_PREFIX + "high");

        return DashboardMetrics.ChurnRiskDistribution.builder()
                .low(low != null ? low : 0)
                .medium(medium != null ? medium : 0)
                .high(high != null ? high : 0)
                .build();
    }

    public void incrementCounter(String counterName) {
        try {
            redisTemplate.opsForValue().increment(COUNTER_PREFIX + counterName);
        } catch (Exception e) {
            log.error("Error incrementing counter: {}", counterName, e);
        }
    }

    public void incrementChurnRisk(String level) {
        try {
            redisTemplate.opsForValue().increment(CHURN_RISK_PREFIX + level.toLowerCase());
        } catch (Exception e) {
            log.error("Error incrementing churn risk: {}", level, e);
        }
    }

    public void trackIssue(String issue) {
        try {
            redisTemplate.opsForZSet().incrementScore(ISSUES_PREFIX + "all", issue, 1);
        } catch (Exception e) {
            log.error("Error tracking issue: {}", issue, e);
        }
    }

    public void trackTopic(String topic) {
        try {
            redisTemplate.opsForZSet().incrementScore(TOPICS_PREFIX + "all", topic, 1);
        } catch (Exception e) {
            log.error("Error tracking topic: {}", topic, e);
        }
    }

    private Long getCounter(String counterName) {
        try {
            Object value = redisTemplate.opsForValue().get(COUNTER_PREFIX + counterName);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        } catch (Exception e) {
            log.error("Error getting counter: {}", counterName, e);
        }
        return null;
    }

    private Integer getCounterAsInt(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (Exception e) {
            log.error("Error getting counter as int: {}", key, e);
        }
        return 0;
    }

    private Map<String, Integer> getTopItems(String prefix, int limit) {
        Map<String, Integer> result = new HashMap<>();
        try {
            var items = redisTemplate.opsForZSet().reverseRangeWithScores(prefix + "all", 0, limit - 1);
            if (items != null) {
                items.forEach(item -> {
                    if (item.getValue() != null && item.getScore() != null) {
                        result.put(String.valueOf(item.getValue()), item.getScore().intValue());
                    }
                });
            }
        } catch (Exception e) {
            log.error("Error getting top items for prefix: {}", prefix, e);
        }
        return result;
    }
}
