package com.callaudit.analytics.domain.transcription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Transcription entities.
 * Part of the transcription bounded context within analytics-service.
 */
@Repository
public interface TranscriptionRepository extends JpaRepository<Transcription, UUID> {

    /**
     * Find transcription by call ID.
     *
     * @param callId the call ID
     * @return Optional containing the transcription if found
     */
    Optional<Transcription> findByCallId(UUID callId);

    /**
     * Check if transcription exists for a call ID.
     *
     * @param callId the call ID
     * @return true if transcription exists, false otherwise
     */
    boolean existsByCallId(UUID callId);

    /**
     * Find transcription by call ID with segments eagerly loaded.
     *
     * @param callId the call ID
     * @return Optional containing the transcription with segments if found
     */
    @Query("SELECT DISTINCT t FROM Transcription t LEFT JOIN FETCH t.segments s WHERE t.callId = :callId")
    Optional<Transcription> findByCallIdWithSegments(@Param("callId") UUID callId);
}
