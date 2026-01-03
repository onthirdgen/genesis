package com.callaudit.analytics.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_performance")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPerformance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "time", nullable = false)
    private LocalDateTime time;

    @Column(name = "agent_id", nullable = false)
    private String agentId;

    @Column(name = "calls_processed")
    private Integer callsProcessed;

    @Column(name = "avg_quality_score")
    private Double avgQualityScore;

    @Column(name = "avg_customer_satisfaction")
    private Double avgCustomerSatisfaction;

    @Column(name = "compliance_pass_rate")
    private Double compliancePassRate;

    @Column(name = "avg_sentiment_score")
    private Double avgSentimentScore;

    @Column(name = "avg_churn_risk")
    private Double avgChurnRisk;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
