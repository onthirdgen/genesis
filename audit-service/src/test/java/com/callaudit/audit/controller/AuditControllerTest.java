package com.callaudit.audit.controller;

import com.callaudit.audit.model.AuditResult;
import com.callaudit.audit.model.ComplianceRule;
import com.callaudit.audit.model.ComplianceStatus;
import com.callaudit.audit.model.ComplianceViolation;
import com.callaudit.audit.model.ViolationSeverity;
import com.callaudit.audit.repository.AuditResultRepository;
import com.callaudit.audit.repository.ComplianceRuleRepository;
import com.callaudit.audit.repository.ComplianceViolationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for AuditController
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditResultRepository auditResultRepository;

    @MockBean
    private ComplianceViolationRepository violationRepository;

    @MockBean
    private ComplianceRuleRepository ruleRepository;

    private AuditResult testAuditResult;
    private ComplianceViolation testViolation;
    private ComplianceRule testRule;

    @BeforeEach
    void setUp() {
        UUID callId = UUID.randomUUID();
        UUID auditId = UUID.randomUUID();

        testAuditResult = AuditResult.builder()
            .id(auditId)
            .callId(callId)
            .overallScore(85)
            .complianceStatus(ComplianceStatus.passed)
            .scriptAdherence(80)
            .customerService(90)
            .resolutionEffectiveness(85)
            .flagsForReview(false)
            .processingTimeMs(1500)
            .createdAt(OffsetDateTime.now())
            .build();

        testViolation = ComplianceViolation.builder()
            .id(UUID.randomUUID())
            .auditResultId(auditId)
            .ruleId("RULE-001")
            .ruleName("Greeting Required")
            .severity(ViolationSeverity.medium)
            .description("Agent did not greet customer")
            .build();

        testRule = ComplianceRule.builder()
            .id("RULE-001")
            .name("Greeting Required")
            .description("Agent must greet customer at beginning of call")
            .category("Script Adherence")
            .severity(ViolationSeverity.medium)
            .isActive(true)
            .ruleDefinition("{\"type\":\"keyword_check\"}")
            .build();
    }

    @Test
    void health_ReturnsHealthy() throws Exception {
        mockMvc.perform(get("/api/audit/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.service").value("audit-service"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void getAuditResult_ExistingCall_Returns200Ok() throws Exception {
        UUID callId = testAuditResult.getCallId();
        when(auditResultRepository.findByCallId(callId)).thenReturn(Optional.of(testAuditResult));

        mockMvc.perform(get("/api/audit/calls/{callId}", callId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.callId").value(callId.toString()))
            .andExpect(jsonPath("$.overallScore").value(85))
            .andExpect(jsonPath("$.complianceStatus").value("passed"))
            .andExpect(jsonPath("$.scriptAdherence").value(80))
            .andExpect(jsonPath("$.customerService").value(90));

        verify(auditResultRepository).findByCallId(callId);
    }

    @Test
    void getAuditResult_NonExistingCall_Returns404NotFound() throws Exception {
        UUID callId = UUID.randomUUID();
        when(auditResultRepository.findByCallId(callId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/audit/calls/{callId}", callId))
            .andExpect(status().isNotFound());

        verify(auditResultRepository).findByCallId(callId);
    }

    @Test
    void getViolationsByCall_ExistingCall_ReturnsViolations() throws Exception {
        UUID callId = testAuditResult.getCallId();
        when(auditResultRepository.findByCallId(callId)).thenReturn(Optional.of(testAuditResult));
        when(violationRepository.findByAuditResultId(testAuditResult.getId()))
            .thenReturn(List.of(testViolation));

        mockMvc.perform(get("/api/audit/calls/{callId}/violations", callId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].ruleId").value("RULE-001"))
            .andExpect(jsonPath("$[0].severity").value("medium"));

        verify(auditResultRepository).findByCallId(callId);
        verify(violationRepository).findByAuditResultId(testAuditResult.getId());
    }

    @Test
    void getViolationsByCall_NonExistingCall_Returns404NotFound() throws Exception {
        UUID callId = UUID.randomUUID();
        when(auditResultRepository.findByCallId(callId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/audit/calls/{callId}/violations", callId))
            .andExpect(status().isNotFound());

        verify(auditResultRepository).findByCallId(callId);
        verifyNoInteractions(violationRepository);
    }

    @Test
    void getViolations_NoFilters_ReturnsAllViolations() throws Exception {
        when(violationRepository.findAll()).thenReturn(List.of(testViolation));

        mockMvc.perform(get("/api/audit/violations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].ruleId").value("RULE-001"));

        verify(violationRepository).findAll();
    }

    @Test
    void getViolations_FilterByRuleId_ReturnsMatchingViolations() throws Exception {
        when(violationRepository.findByRuleId("RULE-001")).thenReturn(List.of(testViolation));

        mockMvc.perform(get("/api/audit/violations")
                .param("ruleId", "RULE-001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].ruleId").value("RULE-001"));

        verify(violationRepository).findByRuleId("RULE-001");
    }

    @Test
    void getViolations_FilterBySeverity_ReturnsMatchingViolations() throws Exception {
        when(violationRepository.findBySeverity(ViolationSeverity.medium))
            .thenReturn(List.of(testViolation));

        mockMvc.perform(get("/api/audit/violations")
                .param("severity", "MEDIUM"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].severity").value("medium"));

        verify(violationRepository).findBySeverity(ViolationSeverity.medium);
    }

    @Test
    void getViolations_InvalidSeverity_Returns400BadRequest() throws Exception {
        mockMvc.perform(get("/api/audit/violations")
                .param("severity", "INVALID"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(violationRepository);
    }

    @Test
    void generateReport_NoDateRange_ReturnsFullReport() throws Exception {
        when(auditResultRepository.findAll()).thenReturn(List.of(testAuditResult));

        mockMvc.perform(get("/api/audit/reports"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCalls").value(1))
            .andExpect(jsonPath("$.complianceBreakdown.passed").value(1))
            .andExpect(jsonPath("$.complianceBreakdown.failed").value(0))
            .andExpect(jsonPath("$.averageScore").value(85.0));

        verify(auditResultRepository).findAll();
    }

    @Test
    void generateReport_WithDateRange_ReturnsFilteredReport() throws Exception {
        OffsetDateTime startDate = OffsetDateTime.now().minusDays(7);
        OffsetDateTime endDate = OffsetDateTime.now();

        when(auditResultRepository.findByDateRange(any(OffsetDateTime.class), any(OffsetDateTime.class)))
            .thenReturn(List.of(testAuditResult));

        mockMvc.perform(get("/api/audit/reports")
                .param("startDate", startDate.toString())
                .param("endDate", endDate.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCalls").value(1));

        verify(auditResultRepository).findByDateRange(any(OffsetDateTime.class), any(OffsetDateTime.class));
    }

    @Test
    void generateReport_FlaggedForReview_IncludesInReport() throws Exception {
        AuditResult flaggedResult = AuditResult.builder()
            .id(UUID.randomUUID())
            .callId(UUID.randomUUID())
            .overallScore(55)
            .complianceStatus(ComplianceStatus.review_required)
            .flagsForReview(true)
            .reviewReason("Low score")
            .build();

        when(auditResultRepository.findAll()).thenReturn(List.of(flaggedResult));

        mockMvc.perform(get("/api/audit/reports"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.flaggedForReview").value(1))
            .andExpect(jsonPath("$.complianceBreakdown.reviewRequired").value(1));
    }

    @Test
    void getRules_NoFilter_ReturnsAllRules() throws Exception {
        when(ruleRepository.findAll()).thenReturn(List.of(testRule));

        mockMvc.perform(get("/api/audit/rules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value("RULE-001"));

        verify(ruleRepository).findAll();
    }

    @Test
    void getRules_FilterByIsActive_ReturnsActiveRules() throws Exception {
        when(ruleRepository.findByIsActive(true)).thenReturn(List.of(testRule));

        mockMvc.perform(get("/api/audit/rules")
                .param("isActive", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].isActive").value(true));

        verify(ruleRepository).findByIsActive(true);
    }

    @Test
    void getRule_ExistingRule_Returns200Ok() throws Exception {
        when(ruleRepository.findById("RULE-001")).thenReturn(Optional.of(testRule));

        mockMvc.perform(get("/api/audit/rules/{ruleId}", "RULE-001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("RULE-001"))
            .andExpect(jsonPath("$.name").value("Greeting Required"));

        verify(ruleRepository).findById("RULE-001");
    }

    @Test
    void getRule_NonExistingRule_Returns404NotFound() throws Exception {
        when(ruleRepository.findById("NONEXISTENT")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/audit/rules/{ruleId}", "NONEXISTENT"))
            .andExpect(status().isNotFound());

        verify(ruleRepository).findById("NONEXISTENT");
    }

    @Test
    void createRule_NewRule_Returns201Created() throws Exception {
        when(ruleRepository.existsById("RULE-002")).thenReturn(false);
        when(ruleRepository.save(any(ComplianceRule.class))).thenReturn(testRule);

        String ruleJson = """
            {
                "id": "RULE-002",
                "name": "Closing Required",
                "description": "Agent must close call properly",
                "category": "Script Adherence",
                "severity": "medium",
                "isActive": true,
                "ruleDefinition": "{\\"type\\":\\"keyword_check\\"}"
            }
            """;

        mockMvc.perform(post("/api/audit/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ruleJson))
            .andExpect(status().isCreated());

        verify(ruleRepository).existsById("RULE-002");
        verify(ruleRepository).save(any(ComplianceRule.class));
    }

    @Test
    void createRule_DuplicateId_Returns409Conflict() throws Exception {
        when(ruleRepository.existsById("RULE-001")).thenReturn(true);

        String ruleJson = """
            {
                "id": "RULE-001",
                "name": "Duplicate Rule",
                "description": "This is a duplicate",
                "category": "Test",
                "severity": "low",
                "isActive": true,
                "ruleDefinition": "{}"
            }
            """;

        mockMvc.perform(post("/api/audit/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ruleJson))
            .andExpect(status().isConflict());

        verify(ruleRepository).existsById("RULE-001");
        verify(ruleRepository, never()).save(any(ComplianceRule.class));
    }

    @Test
    void updateRule_ExistingRule_Returns200Ok() throws Exception {
        when(ruleRepository.existsById("RULE-001")).thenReturn(true);
        when(ruleRepository.save(any(ComplianceRule.class))).thenReturn(testRule);

        String ruleJson = """
            {
                "id": "RULE-001",
                "name": "Updated Rule",
                "description": "Updated description",
                "category": "Script Adherence",
                "severity": "high",
                "isActive": true,
                "ruleDefinition": "{\\"type\\":\\"keyword_check\\"}"
            }
            """;

        mockMvc.perform(put("/api/audit/rules/{ruleId}", "RULE-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ruleJson))
            .andExpect(status().isOk());

        verify(ruleRepository).existsById("RULE-001");
        verify(ruleRepository).save(any(ComplianceRule.class));
    }

    @Test
    void updateRule_NonExistingRule_Returns404NotFound() throws Exception {
        when(ruleRepository.existsById("NONEXISTENT")).thenReturn(false);

        String ruleJson = """
            {
                "id": "NONEXISTENT",
                "name": "Nonexistent Rule",
                "description": "This does not exist",
                "category": "Test",
                "severity": "low",
                "isActive": true,
                "ruleDefinition": "{}"
            }
            """;

        mockMvc.perform(put("/api/audit/rules/{ruleId}", "NONEXISTENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ruleJson))
            .andExpect(status().isNotFound());

        verify(ruleRepository).existsById("NONEXISTENT");
        verify(ruleRepository, never()).save(any(ComplianceRule.class));
    }

    @Test
    void deleteRule_ExistingRule_Returns204NoContent() throws Exception {
        when(ruleRepository.existsById("RULE-001")).thenReturn(true);

        mockMvc.perform(delete("/api/audit/rules/{ruleId}", "RULE-001"))
            .andExpect(status().isNoContent());

        verify(ruleRepository).existsById("RULE-001");
        verify(ruleRepository).deleteById("RULE-001");
    }

    @Test
    void deleteRule_NonExistingRule_Returns404NotFound() throws Exception {
        when(ruleRepository.existsById("NONEXISTENT")).thenReturn(false);

        mockMvc.perform(delete("/api/audit/rules/{ruleId}", "NONEXISTENT"))
            .andExpect(status().isNotFound());

        verify(ruleRepository).existsById("NONEXISTENT");
        verify(ruleRepository, never()).deleteById(anyString());
    }

    @Test
    void getAuditsByStatus_ValidStatus_ReturnsMatchingAudits() throws Exception {
        when(auditResultRepository.findByComplianceStatus(ComplianceStatus.passed))
            .thenReturn(List.of(testAuditResult));

        mockMvc.perform(get("/api/audit/status/{status}", "PASSED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].complianceStatus").value("passed"));

        verify(auditResultRepository).findByComplianceStatus(ComplianceStatus.passed);
    }

    @Test
    void getAuditsByStatus_InvalidStatus_Returns400BadRequest() throws Exception {
        mockMvc.perform(get("/api/audit/status/{status}", "INVALID"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(auditResultRepository);
    }

    @Test
    void getFlaggedAudits_ReturnsFlaggedResults() throws Exception {
        AuditResult flaggedResult = AuditResult.builder()
            .id(UUID.randomUUID())
            .callId(UUID.randomUUID())
            .overallScore(55)
            .complianceStatus(ComplianceStatus.review_required)
            .flagsForReview(true)
            .reviewReason("Low score")
            .build();

        when(auditResultRepository.findByFlagsForReview(true))
            .thenReturn(List.of(flaggedResult));

        mockMvc.perform(get("/api/audit/flagged"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].flagsForReview").value(true));

        verify(auditResultRepository).findByFlagsForReview(true);
    }

    @Test
    void generateReport_EmptyResults_ReturnsZeroMetrics() throws Exception {
        when(auditResultRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/audit/reports"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCalls").value(0))
            .andExpect(jsonPath("$.averageScore").value(0.0));

        verify(auditResultRepository).findAll();
    }
}
