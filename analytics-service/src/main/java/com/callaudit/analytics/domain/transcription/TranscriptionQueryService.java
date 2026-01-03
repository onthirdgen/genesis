package com.callaudit.analytics.domain.transcription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Service for querying transcriptions.
 * Part of the transcription bounded context within analytics-service.
 *
 * This service owns the transcription read model, which is built from
 * CallTranscribed events published by the transcription-service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TranscriptionQueryService {

    private final TranscriptionRepository transcriptionRepository;

    /**
     * Find transcription by call ID.
     *
     * @param callId the call ID
     * @return Optional containing the transcription if found
     */
    @Transactional(readOnly = true)
    public Optional<Transcription> findByCallId(UUID callId) {
        log.debug("Finding transcription for call ID: {}", callId);
        return transcriptionRepository.findByCallIdWithSegments(callId);
    }

    /**
     * Check if transcription exists for a call ID.
     *
     * @param callId the call ID
     * @return true if transcription exists, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean existsByCallId(UUID callId) {
        return transcriptionRepository.existsByCallId(callId);
    }

    /**
     * Get transcription by call ID, throwing exception if not found.
     *
     * @param callId the call ID
     * @return the transcription
     * @throws TranscriptionNotFoundException if transcription not found
     */
    @Transactional(readOnly = true)
    public Transcription getByCallId(UUID callId) {
        return findByCallId(callId)
                .orElseThrow(() -> new TranscriptionNotFoundException(callId));
    }

    /**
     * Exception thrown when transcription is not found.
     */
    public static class TranscriptionNotFoundException extends RuntimeException {
        public TranscriptionNotFoundException(UUID callId) {
            super("Transcription not found for call ID: " + callId);
        }
    }
}
