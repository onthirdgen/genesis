package com.callaudit.audit.repository;

import com.callaudit.audit.model.ComplianceViolation;
import com.callaudit.audit.model.ViolationSeverity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComplianceViolationRepository extends JpaRepository<ComplianceViolation, UUID> {

    List<ComplianceViolation> findByAuditResultId(UUID auditResultId);

    List<ComplianceViolation> findByRuleId(String ruleId);

    List<ComplianceViolation> findBySeverity(ViolationSeverity severity);

    List<ComplianceViolation> findBySeverityIn(List<ViolationSeverity> severities);
}
