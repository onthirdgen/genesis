package com.callaudit.ingestion.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "calls")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Call {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String callerId;

    @Column(nullable = false)
    private String agentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallChannel channel;

    @Column(nullable = false)
    private Instant startTime;

    @Column
    private Integer duration; // in seconds

    @Column(nullable = false)
    private String audioFileUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CallStatus status = CallStatus.PENDING;

    @Column(nullable = false)
    private UUID correlationId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
