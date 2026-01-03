package com.callaudit.analytics.domain.call;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Call entities.
 * Part of the call bounded context within analytics-service.
 */
@Repository
public interface CallRepository extends JpaRepository<Call, UUID> {

    /**
     * Find all calls with pagination.
     *
     * @param pageable pagination parameters
     * @return page of calls
     */
    Page<Call> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Find calls by status with pagination.
     *
     * @param status the call status
     * @param pageable pagination parameters
     * @return page of calls
     */
    Page<Call> findByStatusOrderByCreatedAtDesc(Call.Status status, Pageable pageable);

    /**
     * Find calls by agent ID with pagination.
     *
     * @param agentId the agent ID
     * @param pageable pagination parameters
     * @return page of calls
     */
    Page<Call> findByAgentIdOrderByCreatedAtDesc(String agentId, Pageable pageable);

    /**
     * Find calls by caller ID with pagination.
     *
     * @param callerId the caller ID
     * @param pageable pagination parameters
     * @return page of calls
     */
    Page<Call> findByCallerIdOrderByCreatedAtDesc(String callerId, Pageable pageable);

    /**
     * Find calls within a date range.
     *
     * @param startDate start date
     * @param endDate end date
     * @param pageable pagination parameters
     * @return page of calls
     */
    @Query("SELECT c FROM Call c WHERE c.startTime >= :startDate AND c.startTime <= :endDate ORDER BY c.createdAt DESC")
    Page<Call> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    /**
     * Check if call exists by correlation ID.
     *
     * @param correlationId correlation ID
     * @return true if exists
     */
    boolean existsByCorrelationId(UUID correlationId);

    /**
     * Find call by correlation ID.
     *
     * @param correlationId correlation ID
     * @return optional call
     */
    Optional<Call> findByCorrelationId(UUID correlationId);
}
