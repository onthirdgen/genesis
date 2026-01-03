package com.callaudit.audit.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_results")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "call_id", nullable = false)
    private UUID callId;

    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "compliance_status", nullable = false)
    private ComplianceStatus complianceStatus;

    @Column(name = "script_adherence")
    private Integer scriptAdherence;

    @Column(name = "customer_service")
    private Integer customerService;

    @Column(name = "resolution_effectiveness")
    private Integer resolutionEffectiveness;

    @Column(name = "flags_for_review", nullable = false)
    private Boolean flagsForReview = false;

    @Column(name = "review_reason", columnDefinition = "TEXT")
    private String reviewReason;

    @Column(name = "reviewer_id")
    private String reviewerId;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "processing_time_ms")
    private Integer processingTimeMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
