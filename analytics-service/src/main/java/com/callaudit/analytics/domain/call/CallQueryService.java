package com.callaudit.analytics.domain.call;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for querying calls.
 * Part of the call bounded context within analytics-service.
 *
 * This service owns the call read model, which is built from
 * CallReceived events published by the call-ingestion-service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CallQueryService {

    private final CallRepository callRepository;

    /**
     * Find all calls with pagination.
     *
     * @param pageable pagination parameters
     * @return page of calls
     */
    @Transactional(readOnly = true)
    public Page<Call> findAll(Pageable pageable) {
        log.debug("Finding all calls with pagination: {}", pageable);
        return callRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * Find calls by status with pagination.
     *
     * @param status the call status
     * @param pageable pagination parameters
     * @return page of calls
     */
    @Transactional(readOnly = true)
    public Page<Call> findByStatus(Call.Status status, Pageable pageable) {
        log.debug("Finding calls with status: {} and pagination: {}", status, pageable);
        return callRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    /**
     * Find calls by agent ID with pagination.
     *
     * @param agentId the agent ID
     * @param pageable pagination parameters
     * @return page of calls
     */
    @Transactional(readOnly = true)
    public Page<Call> findByAgentId(String agentId, Pageable pageable) {
        log.debug("Finding calls for agent: {} with pagination: {}", agentId, pageable);
        return callRepository.findByAgentIdOrderByCreatedAtDesc(agentId, pageable);
    }

    /**
     * Find calls by caller ID with pagination.
     *
     * @param callerId the caller ID
     * @param pageable pagination parameters
     * @return page of calls
     */
    @Transactional(readOnly = true)
    public Page<Call> findByCallerId(String callerId, Pageable pageable) {
        log.debug("Finding calls for caller: {} with pagination: {}", callerId, pageable);
        return callRepository.findByCallerIdOrderByCreatedAtDesc(callerId, pageable);
    }

    /**
     * Find calls within a date range.
     *
     * @param startDate start date
     * @param endDate end date
     * @param pageable pagination parameters
     * @return page of calls
     */
    @Transactional(readOnly = true)
    public Page<Call> findByDateRange(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        log.debug("Finding calls between {} and {} with pagination: {}", startDate, endDate, pageable);
        return callRepository.findByDateRange(startDate, endDate, pageable);
    }

    /**
     * Find call by ID.
     *
     * @param id the call ID
     * @return optional call
     */
    @Transactional(readOnly = true)
    public Optional<Call> findById(UUID id) {
        log.debug("Finding call by ID: {}", id);
        return callRepository.findById(id);
    }

    /**
     * Get call by ID, throwing exception if not found.
     *
     * @param id the call ID
     * @return the call
     * @throws CallNotFoundException if call not found
     */
    @Transactional(readOnly = true)
    public Call getById(UUID id) {
        return findById(id)
                .orElseThrow(() -> new CallNotFoundException(id));
    }

    /**
     * Exception thrown when call is not found.
     */
    public static class CallNotFoundException extends RuntimeException {
        public CallNotFoundException(UUID id) {
            super("Call not found with ID: " + id);
        }
    }
}
