package com.callaudit.audit.controller;

import com.callaudit.audit.model.AuditResult;
import com.callaudit.audit.model.ComplianceRule;
import com.callaudit.audit.model.ComplianceStatus;
import com.callaudit.audit.model.ComplianceViolation;
import com.callaudit.audit.repository.AuditResultRepository;
import com.callaudit.audit.repository.ComplianceRuleRepository;
import com.callaudit.audit.repository.ComplianceViolationRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Slf4j
public class AuditController {

    private final AuditResultRepository auditResultRepository;
    private final ComplianceViolationRepository violationRepository;
    private final ComplianceRuleRepository ruleRepository;

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "audit-service");
        response.put("timestamp", OffsetDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/calls/{callId}")
    public ResponseEntity<AuditResult> getAuditResult(@PathVariable UUID callId) {
        log.info("Retrieving audit result for call ID: {}", callId);

        return auditResultRepository.findByCallId(callId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/calls/{callId}/violations")
    public ResponseEntity<List<ComplianceViolation>> getViolationsByCall(@PathVariable UUID callId) {
        log.info("Retrieving violations for call ID: {}", callId);

        Optional<AuditResult> auditResult = auditResultRepository.findByCallId(callId);
        if (auditResult.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<ComplianceViolation> violations = violationRepository.findByAuditResultId(auditResult.get().getId());
        return ResponseEntity.ok(violations);
    }

    @GetMapping("/violations")
    public ResponseEntity<List<ComplianceViolation>> getViolations(
            @RequestParam(required = false) String ruleId,
            @RequestParam(required = false) String severity) {

        log.info("Retrieving violations - ruleId: {}, severity: {}", ruleId, severity);

        List<ComplianceViolation> violations;

        if (ruleId != null) {
            violations = violationRepository.findByRuleId(ruleId);
        } else if (severity != null) {
            try {
                violations = violationRepository.findBySeverity(
                        com.callaudit.audit.model.ViolationSeverity.valueOf(severity.toLowerCase())
                );
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        } else {
            violations = violationRepository.findAll();
        }

        return ResponseEntity.ok(violations);
    }

    @GetMapping("/reports")
    public ResponseEntity<Map<String, Object>> generateReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate) {

        log.info("Generating audit report - startDate: {}, endDate: {}", startDate, endDate);

        List<AuditResult> results;

        if (startDate != null && endDate != null) {
            results = auditResultRepository.findByDateRange(startDate, endDate);
        } else {
            results = auditResultRepository.findAll();
        }

        Map<String, Object> report = new HashMap<>();
        report.put("totalCalls", results.size());
        report.put("dateRange", Map.of(
                "start", startDate != null ? startDate : "N/A",
                "end", endDate != null ? endDate : "N/A"
        ));

        // Compliance status breakdown
        long passedCount = results.stream()
                .filter(r -> r.getComplianceStatus() == ComplianceStatus.passed)
                .count();
        long failedCount = results.stream()
                .filter(r -> r.getComplianceStatus() == ComplianceStatus.failed)
                .count();
        long reviewCount = results.stream()
                .filter(r -> r.getComplianceStatus() == ComplianceStatus.review_required)
                .count();

        report.put("complianceBreakdown", Map.of(
                "passed", passedCount,
                "failed", failedCount,
                "reviewRequired", reviewCount
        ));

        // Score statistics
        OptionalDouble avgScore = results.stream()
                .filter(r -> r.getOverallScore() != null)
                .mapToInt(AuditResult::getOverallScore)
                .average();

        report.put("averageScore", avgScore.isPresent() ? avgScore.getAsDouble() : 0);

        // Flagged for review
        long flaggedCount = results.stream()
                .filter(r -> Boolean.TRUE.equals(r.getFlagsForReview()))
                .count();
        report.put("flaggedForReview", flaggedCount);

        // Quality metrics averages
        OptionalDouble avgScriptAdherence = results.stream()
                .filter(r -> r.getScriptAdherence() != null)
                .mapToInt(AuditResult::getScriptAdherence)
                .average();
        OptionalDouble avgCustomerService = results.stream()
                .filter(r -> r.getCustomerService() != null)
                .mapToInt(AuditResult::getCustomerService)
                .average();
        OptionalDouble avgResolution = results.stream()
                .filter(r -> r.getResolutionEffectiveness() != null)
                .mapToInt(AuditResult::getResolutionEffectiveness)
                .average();

        report.put("qualityMetrics", Map.of(
                "avgScriptAdherence", avgScriptAdherence.isPresent() ? avgScriptAdherence.getAsDouble() : 0,
                "avgCustomerService", avgCustomerService.isPresent() ? avgCustomerService.getAsDouble() : 0,
                "avgResolutionEffectiveness", avgResolution.isPresent() ? avgResolution.getAsDouble() : 0
        ));

        return ResponseEntity.ok(report);
    }

    @GetMapping("/rules")
    public ResponseEntity<List<ComplianceRule>> getRules(
            @RequestParam(required = false) Boolean isActive) {

        log.info("Retrieving compliance rules - isActive: {}", isActive);

        List<ComplianceRule> rules;
        if (isActive != null) {
            rules = ruleRepository.findByIsActive(isActive);
        } else {
            rules = ruleRepository.findAll();
        }

        return ResponseEntity.ok(rules);
    }

    @GetMapping("/rules/{ruleId}")
    public ResponseEntity<ComplianceRule> getRule(@PathVariable String ruleId) {
        log.info("Retrieving compliance rule: {}", ruleId);

        return ruleRepository.findById(ruleId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/rules")
    public ResponseEntity<ComplianceRule> createRule(@Valid @RequestBody ComplianceRule rule) {
        log.info("Creating new compliance rule: {}", rule.getId());

        if (ruleRepository.existsById(rule.getId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        ComplianceRule savedRule = ruleRepository.save(rule);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedRule);
    }

    @PutMapping("/rules/{ruleId}")
    public ResponseEntity<ComplianceRule> updateRule(
            @PathVariable String ruleId,
            @Valid @RequestBody ComplianceRule rule) {

        log.info("Updating compliance rule: {}", ruleId);

        if (!ruleRepository.existsById(ruleId)) {
            return ResponseEntity.notFound().build();
        }

        rule.setId(ruleId);
        ComplianceRule savedRule = ruleRepository.save(rule);
        return ResponseEntity.ok(savedRule);
    }

    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<Void> deleteRule(@PathVariable String ruleId) {
        log.info("Deleting compliance rule: {}", ruleId);

        if (!ruleRepository.existsById(ruleId)) {
            return ResponseEntity.notFound().build();
        }

        ruleRepository.deleteById(ruleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<AuditResult>> getAuditsByStatus(@PathVariable String status) {
        log.info("Retrieving audits by status: {}", status);

        try {
            ComplianceStatus complianceStatus = ComplianceStatus.valueOf(status.toLowerCase());
            List<AuditResult> results = auditResultRepository.findByComplianceStatus(complianceStatus);
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/flagged")
    public ResponseEntity<List<AuditResult>> getFlaggedAudits() {
        log.info("Retrieving flagged audits");

        List<AuditResult> results = auditResultRepository.findByFlagsForReview(true);
        return ResponseEntity.ok(results);
    }
}
