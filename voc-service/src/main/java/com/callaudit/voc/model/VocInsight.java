package com.callaudit.voc.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Voice of Customer Insight Entity
 * Stores analyzed insights extracted from call transcriptions
 */
@Entity
@Table(name = "voc_insights")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VocInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID callId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Intent primaryIntent;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "jsonb")
    private List<String> topics;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "jsonb")
    private List<String> keywords;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Satisfaction customerSatisfaction;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal predictedChurnRisk;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "jsonb")
    private List<String> actionableItems;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
