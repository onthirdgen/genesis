package com.callaudit.analytics.service;

import com.callaudit.analytics.model.AgentMetrics;
import com.callaudit.analytics.model.AgentPerformance;
import com.callaudit.analytics.repository.AgentPerformanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgentPerformanceService {

    private final AgentPerformanceRepository agentPerformanceRepository;

    @Value("${analytics.agent.top-limit:10}")
    private int topLimit;

    @Cacheable(value = "agent", key = "#agentId")
    public AgentMetrics getAgentPerformance(String agentId) {
        log.info("Fetching performance metrics for agent: {}", agentId);

        LocalDateTime since = LocalDateTime.now().minusDays(30);
        List<AgentPerformance> performanceRecords = agentPerformanceRepository
                .findByAgentIdAndTimeBetweenOrderByTimeDesc(agentId, since, LocalDateTime.now());

        if (performanceRecords.isEmpty()) {
            log.warn("No performance data found for agent: {}", agentId);
            return createEmptyMetrics(agentId);
        }

        // Calculate aggregated metrics
        int totalCalls = performanceRecords.stream()
                .mapToInt(p -> p.getCallsProcessed() != null ? p.getCallsProcessed() : 0)
                .sum();

        double avgQuality = performanceRecords.stream()
                .filter(p -> p.getAvgQualityScore() != null)
                .mapToDouble(AgentPerformance::getAvgQualityScore)
                .average()
                .orElse(0.0);

        double avgSatisfaction = performanceRecords.stream()
                .filter(p -> p.getAvgCustomerSatisfaction() != null)
                .mapToDouble(AgentPerformance::getAvgCustomerSatisfaction)
                .average()
                .orElse(0.0);

        double complianceRate = performanceRecords.stream()
                .filter(p -> p.getCompliancePassRate() != null)
                .mapToDouble(AgentPerformance::getCompliancePassRate)
                .average()
                .orElse(0.0);

        double avgSentiment = performanceRecords.stream()
                .filter(p -> p.getAvgSentimentScore() != null)
                .mapToDouble(AgentPerformance::getAvgSentimentScore)
                .average()
                .orElse(0.0);

        double avgChurnRisk = performanceRecords.stream()
                .filter(p -> p.getAvgChurnRisk() != null)
                .mapToDouble(AgentPerformance::getAvgChurnRisk)
                .average()
                .orElse(0.0);

        // Build quality trend
        List<AgentMetrics.TrendPoint> qualityTrend = performanceRecords.stream()
                .filter(p -> p.getAvgQualityScore() != null)
                .map(p -> AgentMetrics.TrendPoint.builder()
                        .timestamp(p.getTime().format(DateTimeFormatter.ISO_DATE_TIME))
                        .value(p.getAvgQualityScore())
                        .build())
                .collect(Collectors.toList());

        return AgentMetrics.builder()
                .agentId(agentId)
                .agentName("Agent " + agentId)
                .totalCalls(totalCalls)
                .averageQualityScore(avgQuality)
                .averageCustomerSatisfaction(avgSatisfaction)
                .compliancePassRate(complianceRate)
                .averageSentimentScore(avgSentiment)
                .averageChurnRisk(avgChurnRisk)
                .qualityTrend(qualityTrend)
                .lastUpdated(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }

    @Transactional
    public void updateAgentMetrics(String agentId, Double qualityScore, Double sentimentScore,
                                   Double customerSatisfaction, Double complianceRate, Double churnRisk) {
        log.info("Updating metrics for agent: {}", agentId);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime timeSlot = now.withMinute(0).withSecond(0).withNano(0);

        Optional<AgentPerformance> existingOpt = agentPerformanceRepository
                .findFirstByAgentIdOrderByTimeDesc(agentId);

        AgentPerformance performance;
        if (existingOpt.isPresent() && existingOpt.get().getTime().equals(timeSlot)) {
            // Update existing record for this time slot
            performance = existingOpt.get();
            performance.setCallsProcessed(performance.getCallsProcessed() + 1);

            // Update running averages
            if (qualityScore != null) {
                performance.setAvgQualityScore(calculateNewAverage(
                        performance.getAvgQualityScore(), qualityScore, performance.getCallsProcessed()));
            }
            if (sentimentScore != null) {
                performance.setAvgSentimentScore(calculateNewAverage(
                        performance.getAvgSentimentScore(), sentimentScore, performance.getCallsProcessed()));
            }
            if (customerSatisfaction != null) {
                performance.setAvgCustomerSatisfaction(calculateNewAverage(
                        performance.getAvgCustomerSatisfaction(), customerSatisfaction, performance.getCallsProcessed()));
            }
            if (complianceRate != null) {
                performance.setCompliancePassRate(calculateNewAverage(
                        performance.getCompliancePassRate(), complianceRate, performance.getCallsProcessed()));
            }
            if (churnRisk != null) {
                performance.setAvgChurnRisk(calculateNewAverage(
                        performance.getAvgChurnRisk(), churnRisk, performance.getCallsProcessed()));
            }
        } else {
            // Create new record
            performance = AgentPerformance.builder()
                    .time(timeSlot)
                    .agentId(agentId)
                    .callsProcessed(1)
                    .avgQualityScore(qualityScore)
                    .avgSentimentScore(sentimentScore)
                    .avgCustomerSatisfaction(customerSatisfaction)
                    .compliancePassRate(complianceRate)
                    .avgChurnRisk(churnRisk)
                    .build();
        }

        agentPerformanceRepository.save(performance);
    }

    public List<AgentMetrics> getTopAgents(Integer limit) {
        log.info("Fetching top {} agents", limit);

        int actualLimit = limit != null ? limit : topLimit;
        LocalDateTime since = LocalDateTime.now().minusDays(7);

        List<AgentPerformance> topPerformers = agentPerformanceRepository
                .findTopAgentsByQualityScore(since);

        return topPerformers.stream()
                .limit(actualLimit)
                .map(p -> getAgentPerformance(p.getAgentId()))
                .collect(Collectors.toList());
    }

    private AgentMetrics createEmptyMetrics(String agentId) {
        return AgentMetrics.builder()
                .agentId(agentId)
                .agentName("Agent " + agentId)
                .totalCalls(0)
                .averageQualityScore(0.0)
                .averageCustomerSatisfaction(0.0)
                .compliancePassRate(0.0)
                .averageSentimentScore(0.0)
                .averageChurnRisk(0.0)
                .qualityTrend(new ArrayList<>())
                .lastUpdated(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }

    private Double calculateNewAverage(Double currentAvg, Double newValue, int count) {
        if (currentAvg == null) {
            return newValue;
        }
        if (newValue == null) {
            return currentAvg;
        }
        return ((currentAvg * (count - 1)) + newValue) / count;
    }
}
