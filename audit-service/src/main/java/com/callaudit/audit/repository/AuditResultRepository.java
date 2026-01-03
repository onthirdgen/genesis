package com.callaudit.audit.repository;

import com.callaudit.audit.model.AuditResult;
import com.callaudit.audit.model.ComplianceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditResultRepository extends JpaRepository<AuditResult, UUID> {

    Optional<AuditResult> findByCallId(UUID callId);

    List<AuditResult> findByComplianceStatus(ComplianceStatus status);

    List<AuditResult> findByFlagsForReview(Boolean flagsForReview);

    @Query("SELECT ar FROM AuditResult ar WHERE ar.createdAt BETWEEN :startDate AND :endDate")
    List<AuditResult> findByDateRange(OffsetDateTime startDate, OffsetDateTime endDate);

    @Query("SELECT ar FROM AuditResult ar WHERE ar.overallScore < :threshold")
    List<AuditResult> findByScoreBelowThreshold(Integer threshold);
}
