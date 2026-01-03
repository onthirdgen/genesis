package com.callaudit.voc.controller;

import com.callaudit.voc.model.Intent;
import com.callaudit.voc.model.Satisfaction;
import com.callaudit.voc.model.VocInsight;
import com.callaudit.voc.service.InsightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/voc")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Voice of Customer (VoC)", description = "API for extracting and querying customer insights, themes, keywords, and churn risk predictions from call transcriptions")
public class VocController {

    private final InsightService insightService;

    @Operation(summary = "Get VoC insight by call ID", description = "Retrieve Voice of Customer insight for a specific call")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Insight found", content = @Content(schema = @Schema(implementation = VocInsight.class))),
        @ApiResponse(responseCode = "404", description = "Insight not found")
    })
    @GetMapping("/insights/{callId}")
    public ResponseEntity<VocInsight> getInsight(
            @Parameter(description = "Call ID", required = true) @PathVariable UUID callId) {
        log.info("Fetching VoC insight for call: {}", callId);

        return insightService.getInsightByCallId(callId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get all VoC insights", description = "Retrieve all Voice of Customer insights")
    @ApiResponse(responseCode = "200", description = "List of insights retrieved")
    @GetMapping("/insights")
    public ResponseEntity<List<VocInsight>> getAllInsights() {
        log.info("Fetching all VoC insights");
        return ResponseEntity.ok(insightService.getAllInsights());
    }

    /**
     * Get insights by intent
     */
    @GetMapping("/insights/by-intent/{intent}")
    public ResponseEntity<List<VocInsight>> getInsightsByIntent(@PathVariable Intent intent) {
        log.info("Fetching insights by intent: {}", intent);
        return ResponseEntity.ok(insightService.getInsightsByIntent(intent));
    }

    /**
     * Get insights by satisfaction level
     */
    @GetMapping("/insights/by-satisfaction/{satisfaction}")
    public ResponseEntity<List<VocInsight>> getInsightsBySatisfaction(@PathVariable Satisfaction satisfaction) {
        log.info("Fetching insights by satisfaction: {}", satisfaction);
        return ResponseEntity.ok(insightService.getInsightsBySatisfaction(satisfaction));
    }

    /**
     * Get high churn risk customers
     */
    @GetMapping("/insights/high-risk")
    public ResponseEntity<List<VocInsight>> getHighRiskCustomers(
            @RequestParam(defaultValue = "0.7") double threshold) {
        log.info("Fetching high churn risk customers with threshold: {}", threshold);
        return ResponseEntity.ok(insightService.getHighChurnRiskCustomers(threshold));
    }

    /**
     * Get insights within date range
     */
    @GetMapping("/insights/date-range")
    public ResponseEntity<List<VocInsight>> getInsightsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        log.info("Fetching insights between {} and {}", start, end);
        return ResponseEntity.ok(insightService.getInsightsByDateRange(start, end));
    }

    /**
     * Get aggregate trends
     */
    @GetMapping("/trends")
    public ResponseEntity<Map<String, Object>> getAggregateTrends() {
        log.info("Fetching aggregate VoC trends");
        return ResponseEntity.ok(insightService.getAggregateTrends());
    }

    /**
     * Get top topics
     */
    @GetMapping("/topics")
    public ResponseEntity<Map<String, Long>> getTopTopics(
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Fetching top {} topics", limit);
        return ResponseEntity.ok(insightService.getTopTopics(limit));
    }

    /**
     * Get top keywords
     */
    @GetMapping("/keywords")
    public ResponseEntity<Map<String, Long>> getTopKeywords(
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Fetching top {} keywords", limit);
        return ResponseEntity.ok(insightService.getTopKeywords(limit));
    }

    /**
     * Get intent distribution
     */
    @GetMapping("/distribution/intent")
    public ResponseEntity<Map<Intent, Long>> getIntentDistribution() {
        log.info("Fetching intent distribution");
        return ResponseEntity.ok(insightService.getIntentDistribution());
    }

    /**
     * Get satisfaction distribution
     */
    @GetMapping("/distribution/satisfaction")
    public ResponseEntity<Map<Satisfaction, Long>> getSatisfactionDistribution() {
        log.info("Fetching satisfaction distribution");
        return ResponseEntity.ok(insightService.getSatisfactionDistribution());
    }

    /**
     * Get average churn risk
     */
    @GetMapping("/metrics/churn-risk")
    public ResponseEntity<Map<String, Double>> getAverageChurnRisk() {
        log.info("Fetching average churn risk");
        Double avgRisk = insightService.getAverageChurnRisk();
        return ResponseEntity.ok(Map.of("averageChurnRisk", avgRisk));
    }

    @Operation(summary = "Health check", description = "Check if VoC Service is running")
    @ApiResponse(responseCode = "200", description = "Service is healthy")
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "voc-service"
        ));
    }
}
