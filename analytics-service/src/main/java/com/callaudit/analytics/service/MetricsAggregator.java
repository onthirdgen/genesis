package com.callaudit.analytics.service;

import com.callaudit.analytics.model.AgentPerformance;
import com.callaudit.analytics.repository.AgentPerformanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Aggregates real-time metrics from events and computes rolling averages.
 * Processes events and updates time-series data in TimescaleDB.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MetricsAggregator {

    private final AgentPerformanceRepository agentPerformanceRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String METRIC_BUFFER_PREFIX = "metrics:buffer:";
    private static final String AGENT_BUFFER_PREFIX = "metrics:agent:";
    private static final int BUFFER_EXPIRY_MINUTES = 60;

    /**
     * Aggregate call metrics from an event
     */
    public void aggregateCallMetrics(String callId, String agentId, MetricValues values) {
        log.debug("Aggregating metrics for call: {}, agent: {}", callId, agentId);

        try {
            // Store in temporary buffer for aggregation
            String bufferKey = AGENT_BUFFER_PREFIX + agentId + ":" + getCurrentHourKey();
            Map<String, Double> metricMap = new HashMap<>();

            if (values.getQualityScore() != null) {
                metricMap.put("qualityScore", values.getQualityScore());
            }
            if (values.getSentimentScore() != null) {
                metricMap.put("sentimentScore", values.getSentimentScore());
            }
            if (values.getCustomerSatisfaction() != null) {
                metricMap.put("customerSatisfaction", values.getCustomerSatisfaction());
            }
            if (values.getComplianceRate() != null) {
                metricMap.put("complianceRate", values.getComplianceRate());
            }
            if (values.getChurnRisk() != null) {
                metricMap.put("churnRisk", values.getChurnRisk());
            }

            // Append to buffer list
            redisTemplate.opsForList().rightPush(bufferKey, metricMap);
            redisTemplate.expire(bufferKey, BUFFER_EXPIRY_MINUTES, TimeUnit.MINUTES);

            log.debug("Buffered metrics for agent {} in key {}", agentId, bufferKey);
        } catch (Exception e) {
            log.error("Error aggregating call metrics for callId: {}", callId, e);
        }
    }

    /**
     * Process buffered metrics and update agent performance records
     * Scheduled to run every 5 minutes
     */
    @Scheduled(fixedRateString = "${analytics.aggregation.interval-minutes:5}", timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void processBufferedMetrics() {
        log.info("Starting scheduled metrics aggregation");

        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime currentHour = now.withMinute(0).withSecond(0).withNano(0);

            // Get all agent buffer keys
            Set<String> agentKeys = redisTemplate.keys(AGENT_BUFFER_PREFIX + "*");

            if (agentKeys == null || agentKeys.isEmpty()) {
                log.debug("No buffered metrics to process");
                return;
            }

            int processedAgents = 0;
            for (String key : agentKeys) {
                try {
                    processAgentBuffer(key, currentHour);
                    processedAgents++;
                } catch (Exception e) {
                    log.error("Error processing buffer for key: {}", key, e);
                }
            }

            log.info("Completed metrics aggregation. Processed {} agents", processedAgents);
        } catch (Exception e) {
            log.error("Error in scheduled metrics aggregation", e);
        }
    }

    /**
     * Process metrics buffer for a specific agent
     */
    private void processAgentBuffer(String bufferKey, LocalDateTime timeSlot) {
        // Extract agent ID from buffer key
        String[] parts = bufferKey.split(":");
        if (parts.length < 3) {
            log.warn("Invalid buffer key format: {}", bufferKey);
            return;
        }
        String agentId = parts[2];

        // Get all buffered metrics
        List<Object> bufferedMetrics = redisTemplate.opsForList().range(bufferKey, 0, -1);

        if (bufferedMetrics == null || bufferedMetrics.isEmpty()) {
            return;
        }

        log.debug("Processing {} buffered metrics for agent {}", bufferedMetrics.size(), agentId);

        // Calculate aggregated values
        AggregatedMetrics aggregated = calculateAggregatedMetrics(bufferedMetrics);

        // Update or create agent performance record
        Optional<AgentPerformance> existingOpt = agentPerformanceRepository
                .findFirstByAgentIdOrderByTimeDesc(agentId);

        AgentPerformance performance;
        if (existingOpt.isPresent() && existingOpt.get().getTime().equals(timeSlot)) {
            // Update existing record for this time slot
            performance = existingOpt.get();
            int previousCount = performance.getCallsProcessed() != null ? performance.getCallsProcessed() : 0;
            int newCount = previousCount + aggregated.getCount();

            performance.setCallsProcessed(newCount);
            performance.setAvgQualityScore(mergeAverage(
                    performance.getAvgQualityScore(), aggregated.getAvgQualityScore(),
                    previousCount, aggregated.getCount()));
            performance.setAvgSentimentScore(mergeAverage(
                    performance.getAvgSentimentScore(), aggregated.getAvgSentimentScore(),
                    previousCount, aggregated.getCount()));
            performance.setAvgCustomerSatisfaction(mergeAverage(
                    performance.getAvgCustomerSatisfaction(), aggregated.getAvgCustomerSatisfaction(),
                    previousCount, aggregated.getCount()));
            performance.setCompliancePassRate(mergeAverage(
                    performance.getCompliancePassRate(), aggregated.getAvgComplianceRate(),
                    previousCount, aggregated.getCount()));
            performance.setAvgChurnRisk(mergeAverage(
                    performance.getAvgChurnRisk(), aggregated.getAvgChurnRisk(),
                    previousCount, aggregated.getCount()));
        } else {
            // Create new record
            performance = AgentPerformance.builder()
                    .time(timeSlot)
                    .agentId(agentId)
                    .callsProcessed(aggregated.getCount())
                    .avgQualityScore(aggregated.getAvgQualityScore())
                    .avgSentimentScore(aggregated.getAvgSentimentScore())
                    .avgCustomerSatisfaction(aggregated.getAvgCustomerSatisfaction())
                    .compliancePassRate(aggregated.getAvgComplianceRate())
                    .avgChurnRisk(aggregated.getAvgChurnRisk())
                    .build();
        }

        agentPerformanceRepository.save(performance);

        // Clear the buffer after processing
        redisTemplate.delete(bufferKey);

        log.debug("Updated performance record for agent {} at time {}", agentId, timeSlot);
    }

    /**
     * Calculate aggregated metrics from buffered data
     */
    private AggregatedMetrics calculateAggregatedMetrics(List<Object> bufferedMetrics) {
        int count = bufferedMetrics.size();

        List<Double> qualityScores = new ArrayList<>();
        List<Double> sentimentScores = new ArrayList<>();
        List<Double> satisfactionScores = new ArrayList<>();
        List<Double> complianceRates = new ArrayList<>();
        List<Double> churnRisks = new ArrayList<>();

        for (Object obj : bufferedMetrics) {
            if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> metricMap = (Map<String, Object>) obj;

                addIfPresent(metricMap, "qualityScore", qualityScores);
                addIfPresent(metricMap, "sentimentScore", sentimentScores);
                addIfPresent(metricMap, "customerSatisfaction", satisfactionScores);
                addIfPresent(metricMap, "complianceRate", complianceRates);
                addIfPresent(metricMap, "churnRisk", churnRisks);
            }
        }

        return AggregatedMetrics.builder()
                .count(count)
                .avgQualityScore(calculateAverage(qualityScores))
                .avgSentimentScore(calculateAverage(sentimentScores))
                .avgCustomerSatisfaction(calculateAverage(satisfactionScores))
                .avgComplianceRate(calculateAverage(complianceRates))
                .avgChurnRisk(calculateAverage(churnRisks))
                .build();
    }

    /**
     * Add metric value to list if present in map
     */
    private void addIfPresent(Map<String, Object> map, String key, List<Double> list) {
        Object value = map.get(key);
        if (value instanceof Number) {
            list.add(((Number) value).doubleValue());
        }
    }

    /**
     * Calculate average from list of values
     */
    private Double calculateAverage(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        return values.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    /**
     * Merge two averages based on their counts
     */
    private Double mergeAverage(Double existingAvg, Double newAvg, int existingCount, int newCount) {
        if (newAvg == null) {
            return existingAvg;
        }
        if (existingAvg == null) {
            return newAvg;
        }
        int totalCount = existingCount + newCount;
        return ((existingAvg * existingCount) + (newAvg * newCount)) / totalCount;
    }

    /**
     * Get current hour key for buffering
     */
    private String getCurrentHourKey() {
        LocalDateTime now = LocalDateTime.now();
        return String.format("%04d%02d%02d%02d",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(), now.getHour());
    }

    /**
     * Calculate rolling average for a metric over a time period
     */
    public RollingAverage calculateRollingAverage(String agentId, String metricName, int windowHours) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(windowHours);

        List<AgentPerformance> records = agentPerformanceRepository
                .findByAgentIdAndTimeBetweenOrderByTimeDesc(agentId, startTime, endTime);

        if (records.isEmpty()) {
            return RollingAverage.builder()
                    .agentId(agentId)
                    .metricName(metricName)
                    .average(0.0)
                    .dataPoints(0)
                    .build();
        }

        List<Double> values = records.stream()
                .map(r -> extractMetricValue(r, metricName))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        double average = values.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        return RollingAverage.builder()
                .agentId(agentId)
                .metricName(metricName)
                .average(average)
                .dataPoints(values.size())
                .build();
    }

    /**
     * Extract metric value from performance record based on metric name
     */
    private Double extractMetricValue(AgentPerformance record, String metricName) {
        return switch (metricName.toLowerCase()) {
            case "quality", "qualityscore" -> record.getAvgQualityScore();
            case "sentiment", "sentimentscore" -> record.getAvgSentimentScore();
            case "satisfaction", "customersatisfaction" -> record.getAvgCustomerSatisfaction();
            case "compliance", "compliancerate" -> record.getCompliancePassRate();
            case "churn", "churnrisk" -> record.getAvgChurnRisk();
            default -> null;
        };
    }

    // DTOs
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class MetricValues {
        private Double qualityScore;
        private Double sentimentScore;
        private Double customerSatisfaction;
        private Double complianceRate;
        private Double churnRisk;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class AggregatedMetrics {
        private Integer count;
        private Double avgQualityScore;
        private Double avgSentimentScore;
        private Double avgCustomerSatisfaction;
        private Double avgComplianceRate;
        private Double avgChurnRisk;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class RollingAverage {
        private String agentId;
        private String metricName;
        private Double average;
        private Integer dataPoints;
    }
}
