package com.callaudit.analytics.service;

import com.callaudit.analytics.model.AgentPerformance;
import com.callaudit.analytics.repository.AgentPerformanceRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TrendService {

    private final AgentPerformanceRepository agentPerformanceRepository;

    @Value("${analytics.trends.default-period-days:7}")
    private int defaultPeriodDays;

    @Cacheable(value = "trends", key = "'sentiment_' + #periodDays")
    public TrendData getSentimentTrends(Integer periodDays) {
        log.info("Fetching sentiment trends for period: {} days", periodDays);

        int days = periodDays != null ? periodDays : defaultPeriodDays;
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(days);

        List<AgentPerformance> data = agentPerformanceRepository.getTrendData(startTime, endTime);

        List<DataPoint> dataPoints = data.stream()
                .filter(ap -> ap.getAvgSentimentScore() != null)
                .map(ap -> DataPoint.builder()
                        .timestamp(ap.getTime().format(DateTimeFormatter.ISO_DATE_TIME))
                        .value(ap.getAvgSentimentScore())
                        .label("Sentiment Score")
                        .build())
                .collect(Collectors.toList());

        return TrendData.builder()
                .metric("sentiment")
                .periodDays(days)
                .dataPoints(dataPoints)
                .startTime(startTime.format(DateTimeFormatter.ISO_DATE_TIME))
                .endTime(endTime.format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }

    @Cacheable(value = "trends", key = "'compliance_' + #periodDays")
    public TrendData getComplianceTrends(Integer periodDays) {
        log.info("Fetching compliance trends for period: {} days", periodDays);

        int days = periodDays != null ? periodDays : defaultPeriodDays;
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(days);

        List<AgentPerformance> data = agentPerformanceRepository.getTrendData(startTime, endTime);

        List<DataPoint> dataPoints = data.stream()
                .filter(ap -> ap.getCompliancePassRate() != null)
                .map(ap -> DataPoint.builder()
                        .timestamp(ap.getTime().format(DateTimeFormatter.ISO_DATE_TIME))
                        .value(ap.getCompliancePassRate())
                        .label("Compliance Rate")
                        .build())
                .collect(Collectors.toList());

        return TrendData.builder()
                .metric("compliance")
                .periodDays(days)
                .dataPoints(dataPoints)
                .startTime(startTime.format(DateTimeFormatter.ISO_DATE_TIME))
                .endTime(endTime.format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }

    @Cacheable(value = "trends", key = "'volume_' + #periodDays")
    public VolumeMetrics getVolumeMetrics(Integer periodDays) {
        log.info("Fetching volume metrics for period: {} days", periodDays);

        int days = periodDays != null ? periodDays : defaultPeriodDays;
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(days);

        List<AgentPerformance> data = agentPerformanceRepository.getTrendData(startTime, endTime);

        // Calculate total volume
        Long totalCalls = data.stream()
                .mapToLong(ap -> ap.getCallsProcessed() != null ? ap.getCallsProcessed() : 0L)
                .sum();

        // Volume by time
        List<DataPoint> volumeByTime = data.stream()
                .map(ap -> DataPoint.builder()
                        .timestamp(ap.getTime().format(DateTimeFormatter.ISO_DATE_TIME))
                        .value(ap.getCallsProcessed() != null ? ap.getCallsProcessed().doubleValue() : 0.0)
                        .label("Call Volume")
                        .build())
                .collect(Collectors.toList());

        // Average daily volume
        double avgDailyVolume = days > 0 ? totalCalls.doubleValue() / days : 0.0;

        return VolumeMetrics.builder()
                .totalCalls(totalCalls)
                .periodDays(days)
                .averageDailyVolume(avgDailyVolume)
                .volumeByTime(volumeByTime)
                .startTime(startTime.format(DateTimeFormatter.ISO_DATE_TIME))
                .endTime(endTime.format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }

    @Cacheable(value = "trends", key = "'quality_' + #periodDays")
    public TrendData getQualityTrends(Integer periodDays) {
        log.info("Fetching quality trends for period: {} days", periodDays);

        int days = periodDays != null ? periodDays : defaultPeriodDays;
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(days);

        List<AgentPerformance> data = agentPerformanceRepository.getTrendData(startTime, endTime);

        List<DataPoint> dataPoints = data.stream()
                .filter(ap -> ap.getAvgQualityScore() != null)
                .map(ap -> DataPoint.builder()
                        .timestamp(ap.getTime().format(DateTimeFormatter.ISO_DATE_TIME))
                        .value(ap.getAvgQualityScore())
                        .label("Quality Score")
                        .build())
                .collect(Collectors.toList());

        return TrendData.builder()
                .metric("quality")
                .periodDays(days)
                .dataPoints(dataPoints)
                .startTime(startTime.format(DateTimeFormatter.ISO_DATE_TIME))
                .endTime(endTime.format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }

    public Map<String, TrendData> getAllTrends(Integer periodDays) {
        log.info("Fetching all trends for period: {} days", periodDays);

        Map<String, TrendData> trends = new HashMap<>();
        trends.put("sentiment", getSentimentTrends(periodDays));
        trends.put("compliance", getComplianceTrends(periodDays));
        trends.put("quality", getQualityTrends(periodDays));

        return trends;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class TrendData {
        private String metric;
        private Integer periodDays;
        private List<DataPoint> dataPoints;
        private String startTime;
        private String endTime;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class DataPoint {
        private String timestamp;
        private Double value;
        private String label;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class VolumeMetrics {
        private Long totalCalls;
        private Integer periodDays;
        private Double averageDailyVolume;
        private List<DataPoint> volumeByTime;
        private String startTime;
        private String endTime;
    }
}
