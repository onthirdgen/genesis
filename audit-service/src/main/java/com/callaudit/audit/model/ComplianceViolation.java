package com.callaudit.audit.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "compliance_violations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceViolation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "audit_result_id", nullable = false)
    private UUID auditResultId;

    @Column(name = "rule_id", nullable = false, length = 100)
    private String ruleId;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Column(name = "severity", nullable = false)
    private ViolationSeverity severity;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "segment_id")
    private UUID segmentId;

    @Column(name = "timestamp_in_call", precision = 10, scale = 3)
    private BigDecimal timestampInCall;

    @Column(name = "evidence", columnDefinition = "TEXT")
    private String evidence;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
