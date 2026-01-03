package com.callaudit.analytics.controller;

import com.callaudit.analytics.model.AgentMetrics;
import com.callaudit.analytics.model.DashboardMetrics;
import com.callaudit.analytics.service.AgentPerformanceService;
import com.callaudit.analytics.service.DashboardService;
import com.callaudit.analytics.service.TrendService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {

    private final DashboardService dashboardService;
    private final AgentPerformanceService agentPerformanceService;
    private final TrendService trendService;

    /**
     * Get main dashboard metrics
     */
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardMetrics> getDashboard() {
        log.info("GET /api/analytics/dashboard");
        DashboardMetrics metrics = dashboardService.getDashboardMetrics();
        return ResponseEntity.ok(metrics);
    }

    /**
     * Get performance metrics for a specific agent
     */
    @GetMapping("/agents/{agentId}/performance")
    public ResponseEntity<AgentMetrics> getAgentPerformance(@PathVariable String agentId) {
        log.info("GET /api/analytics/agents/{}/performance", agentId);
        AgentMetrics metrics = agentPerformanceService.getAgentPerformance(agentId);
        return ResponseEntity.ok(metrics);
    }

    /**
     * Get top performing agents
     */
    @GetMapping("/agents/top")
    public ResponseEntity<List<AgentMetrics>> getTopAgents(
            @RequestParam(required = false) Integer limit) {
        log.info("GET /api/analytics/agents/top?limit={}", limit);
        List<AgentMetrics> topAgents = agentPerformanceService.getTopAgents(limit);
        return ResponseEntity.ok(topAgents);
    }

    /**
     * Get compliance summary
     */
    @GetMapping("/compliance/summary")
    public ResponseEntity<ComplianceSummary> getComplianceSummary(
            @RequestParam(required = false) Integer periodDays) {
        log.info("GET /api/analytics/compliance/summary?periodDays={}", periodDays);

        DashboardMetrics dashboard = dashboardService.getDashboardMetrics();
        TrendService.TrendData complianceTrend = trendService.getComplianceTrends(periodDays);

        ComplianceSummary summary = ComplianceSummary.builder()
                .overallPassRate(dashboard.getCompliancePassRate())
                .totalCallsAudited(dashboard.getTotalCallsProcessed())
                .periodDays(complianceTrend.getPeriodDays())
                .trend(complianceTrend.getDataPoints())
                .build();

        return ResponseEntity.ok(summary);
    }

    /**
     * Get customer satisfaction metrics
     */
    @GetMapping("/customer-satisfaction")
    public ResponseEntity<CustomerSatisfactionMetrics> getCustomerSatisfaction(
            @RequestParam(required = false) Integer periodDays) {
        log.info("GET /api/analytics/customer-satisfaction?periodDays={}", periodDays);

        DashboardMetrics dashboard = dashboardService.getDashboardMetrics();
        TrendService.TrendData sentimentTrend = trendService.getSentimentTrends(periodDays);

        CustomerSatisfactionMetrics metrics = CustomerSatisfactionMetrics.builder()
                .averageScore(dashboard.getCustomerSatisfactionScore())
                .averageSentiment(dashboard.getAverageSentimentScore())
                .churnRiskDistribution(dashboard.getChurnRiskDistribution())
                .periodDays(sentimentTrend.getPeriodDays())
                .sentimentTrend(sentimentTrend.getDataPoints())
                .build();

        return ResponseEntity.ok(metrics);
    }

    /**
     * Get trends for specific metric
     */
    @GetMapping("/trends")
    public ResponseEntity<TrendService.TrendData> getTrends(
            @RequestParam String metric,
            @RequestParam(required = false) Integer periodDays) {
        log.info("GET /api/analytics/trends?metric={}&periodDays={}", metric, periodDays);

        TrendService.TrendData trendData = switch (metric.toLowerCase()) {
            case "sentiment" -> trendService.getSentimentTrends(periodDays);
            case "compliance" -> trendService.getComplianceTrends(periodDays);
            case "quality" -> trendService.getQualityTrends(periodDays);
            default -> throw new IllegalArgumentException("Invalid metric: " + metric);
        };

        return ResponseEntity.ok(trendData);
    }

    /**
     * Get all trends
     */
    @GetMapping("/trends/all")
    public ResponseEntity<Map<String, TrendService.TrendData>> getAllTrends(
            @RequestParam(required = false) Integer periodDays) {
        log.info("GET /api/analytics/trends/all?periodDays={}", periodDays);
        Map<String, TrendService.TrendData> trends = trendService.getAllTrends(periodDays);
        return ResponseEntity.ok(trends);
    }

    /**
     * Get call volume metrics
     */
    @GetMapping("/volume")
    public ResponseEntity<TrendService.VolumeMetrics> getVolumeMetrics(
            @RequestParam(required = false) Integer periodDays) {
        log.info("GET /api/analytics/volume?periodDays={}", periodDays);
        TrendService.VolumeMetrics metrics = trendService.getVolumeMetrics(periodDays);
        return ResponseEntity.ok(metrics);
    }

    /**
     * Get issues summary
     */
    @GetMapping("/issues")
    public ResponseEntity<IssuesSummary> getIssues() {
        log.info("GET /api/analytics/issues");
        DashboardMetrics dashboard = dashboardService.getDashboardMetrics();

        IssuesSummary summary = IssuesSummary.builder()
                .topIssues(dashboard.getTopIssues())
                .topTopics(dashboard.getTopTopics())
                .build();

        return ResponseEntity.ok(summary);
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<HealthStatus> health() {
        return ResponseEntity.ok(HealthStatus.builder()
                .status("UP")
                .service("analytics-service")
                .build());
    }

    // DTOs
    @Data
    @Builder
    @AllArgsConstructor
    public static class ComplianceSummary {
        private Double overallPassRate;
        private Long totalCallsAudited;
        private Integer periodDays;
        private List<TrendService.DataPoint> trend;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class CustomerSatisfactionMetrics {
        private Double averageScore;
        private Double averageSentiment;
        private DashboardMetrics.ChurnRiskDistribution churnRiskDistribution;
        private Integer periodDays;
        private List<TrendService.DataPoint> sentimentTrend;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class IssuesSummary {
        private Map<String, Integer> topIssues;
        private Map<String, Integer> topTopics;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class HealthStatus {
        private String status;
        private String service;
    }

    // Exception handler
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.error("Invalid request: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.builder()
                        .error("Bad Request")
                        .message(e.getMessage())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Internal error", e);
        return ResponseEntity.internalServerError()
                .body(ErrorResponse.builder()
                        .error("Internal Server Error")
                        .message("An unexpected error occurred")
                        .build());
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
        private String message;
    }
}
