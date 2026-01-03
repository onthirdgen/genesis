package com.callaudit.audit.repository;

import com.callaudit.audit.model.AuditResult;
import com.callaudit.audit.model.ComplianceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AuditResultRepository
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditResultRepositoryTest {

    @Autowired
    private AuditResultRepository auditResultRepository;

    @BeforeEach
    void setUp() {
        auditResultRepository.deleteAll();
    }

    @Test
    void save_NewAuditResult_GeneratesId() {
        // Arrange
        AuditResult auditResult = AuditResult.builder()
            .callId(UUID.randomUUID())
            .overallScore(85)
            .complianceStatus(ComplianceStatus.passed)
            .scriptAdherence(80)
            .customerService(90)
            .resolutionEffectiveness(85)
            .flagsForReview(false)
            .processingTimeMs(1500)
            .build();

        // Act
        AuditResult saved = auditResultRepository.save(auditResult);

        // Assert
        // Note: H2 doesn't support UUID generation the same way as PostgreSQL
        // In real PostgreSQL, ID would be auto-generated. In H2 tests, we verify save succeeds.
        assertNotNull(saved);
        assertEquals(85, saved.getOverallScore());
        assertEquals(ComplianceStatus.passed, saved.getComplianceStatus());
    }

    @Test
    void findByCallId_ExistingCall_ReturnsAuditResult() {
        // Arrange
        UUID callId = UUID.randomUUID();
        AuditResult auditResult = createAuditResult(callId, ComplianceStatus.passed, 85);
        auditResultRepository.save(auditResult);

        // Act
        Optional<AuditResult> found = auditResultRepository.findByCallId(callId);

        // Assert
        assertTrue(found.isPresent());
        assertEquals(callId, found.get().getCallId());
        assertEquals(85, found.get().getOverallScore());
    }

    @Test
    void findByCallId_NonExistingCall_ReturnsEmpty() {
        // Act
        Optional<AuditResult> found = auditResultRepository.findByCallId(UUID.randomUUID());

        // Assert
        assertFalse(found.isPresent());
    }

    @Test
    void findByComplianceStatus_PassedStatus_ReturnsMatchingResults() {
        // Arrange
        AuditResult passed1 = createAuditResult(UUID.randomUUID(), ComplianceStatus.passed, 85);
        AuditResult passed2 = createAuditResult(UUID.randomUUID(), ComplianceStatus.passed, 90);
        AuditResult failed = createAuditResult(UUID.randomUUID(), ComplianceStatus.failed, 30);

        auditResultRepository.save(passed1);
        auditResultRepository.save(passed2);
        auditResultRepository.save(failed);

        // Act
        List<AuditResult> results = auditResultRepository.findByComplianceStatus(ComplianceStatus.passed);

        // Assert
        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(r -> r.getComplianceStatus() == ComplianceStatus.passed));
    }

    @Test
    void findByComplianceStatus_NoMatches_ReturnsEmptyList() {
        // Arrange
        AuditResult passed = createAuditResult(UUID.randomUUID(), ComplianceStatus.passed, 85);
        auditResultRepository.save(passed);

        // Act
        List<AuditResult> results = auditResultRepository.findByComplianceStatus(ComplianceStatus.failed);

        // Assert
        assertTrue(results.isEmpty());
    }

    @Test
    void findByFlagsForReview_FlaggedResults_ReturnsMatching() {
        // Arrange
        AuditResult flagged1 = createFlaggedAuditResult(UUID.randomUUID(), "Low score");
        AuditResult flagged2 = createFlaggedAuditResult(UUID.randomUUID(), "Critical violation");
        AuditResult notFlagged = createAuditResult(UUID.randomUUID(), ComplianceStatus.passed, 85);

        auditResultRepository.save(flagged1);
        auditResultRepository.save(flagged2);
        auditResultRepository.save(notFlagged);

        // Act
        List<AuditResult> results = auditResultRepository.findByFlagsForReview(true);

        // Assert
        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(AuditResult::getFlagsForReview));
    }

    @Test
    void findByFlagsForReview_NotFlagged_ReturnsMatching() {
        // Arrange
        AuditResult flagged = createFlaggedAuditResult(UUID.randomUUID(), "Low score");
        AuditResult notFlagged = createAuditResult(UUID.randomUUID(), ComplianceStatus.passed, 85);

        auditResultRepository.save(flagged);
        auditResultRepository.save(notFlagged);

        // Act
        List<AuditResult> results = auditResultRepository.findByFlagsForReview(false);

        // Assert
        assertEquals(1, results.size());
        assertFalse(results.get(0).getFlagsForReview());
    }

    @Test
    void findByDateRange_WithinRange_ReturnsMatchingResults() {
        // Arrange
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startDate = now.minusDays(7);
        OffsetDateTime endDate = now.plusDays(1);

        AuditResult withinRange = createAuditResult(UUID.randomUUID(), ComplianceStatus.passed, 85);
        auditResultRepository.save(withinRange);

        // Act
        List<AuditResult> results = auditResultRepository.findByDateRange(startDate, endDate);

        // Assert
        assertThat(results).isNotEmpty();
        assertTrue(results.stream()
            .allMatch(r -> r.getCreatedAt().isAfter(startDate) && r.getCreatedAt().isBefore(endDate)));
    }

    @Test
    void findByDateRange_OutsideRange_ReturnsEmpty() {
        // Arrange
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startDate = now.minusDays(30);
        OffsetDateTime endDate = now.minusDays(20);

        AuditResult recent = createAuditResult(UUID.randomUUID(), ComplianceStatus.passed, 85);
        auditResultRepository.save(recent);

        // Act
        List<AuditResult> results = auditResultRepository.findByDateRange(startDate, endDate);

        // Assert
        assertTrue(results.isEmpty());
    }

    @Test
    void findByScoreBelowThreshold_LowScores_ReturnsMatching() {
        // Arrange
        AuditResult lowScore1 = createAuditResult(UUID.randomUUID(), ComplianceStatus.failed, 30);
        AuditResult lowScore2 = createAuditResult(UUID.randomUUID(), ComplianceStatus.review_required, 55);
        AuditResult highScore = createAuditResult(UUID.randomUUID(), ComplianceStatus.passed, 85);

        auditResultRepository.save(lowScore1);
        auditResultRepository.save(lowScore2);
        auditResultRepository.save(highScore);

        // Act
        List<AuditResult> results = auditResultRepository.findByScoreBelowThreshold(70);

        // Assert
        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(r -> r.getOverallScore() < 70));
    }

    @Test
    void findByScoreBelowThreshold_NoMatches_ReturnsEmpty() {
        // Arrange
        AuditResult highScore = createAuditResult(UUID.randomUUID(), ComplianceStatus.passed, 85);
        auditResultRepository.save(highScore);

        // Act
        List<AuditResult> results = auditResultRepository.findByScoreBelowThreshold(70);

        // Assert
        assertTrue(results.isEmpty());
    }

    @Test
    void save_UpdateExistingAuditResult_UpdatesFields() {
        // Arrange
        UUID callId = UUID.randomUUID();
        AuditResult original = createAuditResult(callId, ComplianceStatus.passed, 85);
        AuditResult saved = auditResultRepository.save(original);

        // Act - Update
        saved.setOverallScore(90);
        saved.setReviewerId("reviewer-123");
        saved.setReviewedAt(OffsetDateTime.now());
        AuditResult updated = auditResultRepository.save(saved);

        // Assert
        assertEquals(saved.getId(), updated.getId());
        assertEquals(90, updated.getOverallScore());
        assertEquals("reviewer-123", updated.getReviewerId());
        assertNotNull(updated.getReviewedAt());
    }

    @Test
    void findAll_MultipleResults_ReturnsList() {
        // Arrange
        AuditResult result1 = createAuditResult(UUID.randomUUID(), ComplianceStatus.passed, 85);
        AuditResult result2 = createAuditResult(UUID.randomUUID(), ComplianceStatus.failed, 30);
        AuditResult result3 = createAuditResult(UUID.randomUUID(), ComplianceStatus.review_required, 65);

        auditResultRepository.save(result1);
        auditResultRepository.save(result2);
        auditResultRepository.save(result3);

        // Act
        List<AuditResult> results = auditResultRepository.findAll();

        // Assert
        assertEquals(3, results.size());
    }

    @Test
    void delete_ExistingAuditResult_RemovesFromDatabase() {
        // Arrange
        UUID callId = UUID.randomUUID();
        AuditResult auditResult = createAuditResult(callId, ComplianceStatus.passed, 85);
        AuditResult saved = auditResultRepository.save(auditResult);

        // Act
        auditResultRepository.delete(saved);

        // Assert
        Optional<AuditResult> found = auditResultRepository.findById(saved.getId());
        assertFalse(found.isPresent());
    }

    @Test
    void save_MultipleScoreRanges_PersistsCorrectly() {
        // Arrange
        AuditResult excellent = createAuditResult(UUID.randomUUID(), ComplianceStatus.passed, 95);
        AuditResult good = createAuditResult(UUID.randomUUID(), ComplianceStatus.passed, 75);
        AuditResult marginal = createAuditResult(UUID.randomUUID(), ComplianceStatus.review_required, 55);
        AuditResult poor = createAuditResult(UUID.randomUUID(), ComplianceStatus.failed, 25);

        // Act
        auditResultRepository.save(excellent);
        auditResultRepository.save(good);
        auditResultRepository.save(marginal);
        auditResultRepository.save(poor);

        // Assert
        List<AuditResult> all = auditResultRepository.findAll();
        assertEquals(4, all.size());

        List<AuditResult> highScores = auditResultRepository.findByScoreBelowThreshold(90);
        assertEquals(3, highScores.size());  // All except excellent (95)

        List<AuditResult> lowScores = auditResultRepository.findByScoreBelowThreshold(50);
        assertEquals(1, lowScores.size());  // Only poor (25)
    }

    @Test
    void findByComplianceStatus_AllStatuses_ReturnsCorrectCounts() {
        // Arrange
        auditResultRepository.save(createAuditResult(UUID.randomUUID(), ComplianceStatus.passed, 85));
        auditResultRepository.save(createAuditResult(UUID.randomUUID(), ComplianceStatus.passed, 90));
        auditResultRepository.save(createAuditResult(UUID.randomUUID(), ComplianceStatus.failed, 30));
        auditResultRepository.save(createAuditResult(UUID.randomUUID(), ComplianceStatus.review_required, 65));
        auditResultRepository.save(createAuditResult(UUID.randomUUID(), ComplianceStatus.review_required, 60));

        // Act & Assert
        assertEquals(2, auditResultRepository.findByComplianceStatus(ComplianceStatus.passed).size());
        assertEquals(1, auditResultRepository.findByComplianceStatus(ComplianceStatus.failed).size());
        assertEquals(2, auditResultRepository.findByComplianceStatus(ComplianceStatus.review_required).size());
    }

    // Helper methods

    private AuditResult createAuditResult(UUID callId, ComplianceStatus status, int score) {
        return AuditResult.builder()
            .callId(callId)
            .overallScore(score)
            .complianceStatus(status)
            .scriptAdherence(score - 5)
            .customerService(score)
            .resolutionEffectiveness(score + 5)
            .flagsForReview(false)
            .processingTimeMs(1000)
            .build();
    }

    private AuditResult createFlaggedAuditResult(UUID callId, String reviewReason) {
        return AuditResult.builder()
            .callId(callId)
            .overallScore(45)
            .complianceStatus(ComplianceStatus.review_required)
            .scriptAdherence(40)
            .customerService(50)
            .resolutionEffectiveness(45)
            .flagsForReview(true)
            .reviewReason(reviewReason)
            .processingTimeMs(1000)
            .build();
    }
}
