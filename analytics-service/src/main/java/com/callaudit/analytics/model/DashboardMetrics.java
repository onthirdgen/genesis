package com.callaudit.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardMetrics {

    private Long totalCallsProcessed;
    private Double averageQualityScore;
    private Double compliancePassRate;
    private Double averageSentimentScore;
    private Double customerSatisfactionScore;
    private ChurnRiskDistribution churnRiskDistribution;
    private Map<String, Integer> topIssues;
    private Map<String, Integer> topTopics;
    private String generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChurnRiskDistribution {
        private Integer low;
        private Integer medium;
        private Integer high;
    }
}
