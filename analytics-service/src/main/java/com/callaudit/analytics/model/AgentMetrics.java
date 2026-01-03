package com.callaudit.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMetrics {

    private String agentId;
    private String agentName;
    private Integer totalCalls;
    private Double averageQualityScore;
    private Double averageCustomerSatisfaction;
    private Double compliancePassRate;
    private Double averageSentimentScore;
    private Double averageChurnRisk;
    private Integer rank;
    private List<TrendPoint> qualityTrend;
    private String lastUpdated;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        private String timestamp;
        private Double value;
    }
}
