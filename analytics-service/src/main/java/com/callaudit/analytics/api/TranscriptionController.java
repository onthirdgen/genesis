package com.callaudit.analytics.api;

import com.callaudit.analytics.api.dto.TranscriptionDTO;
import com.callaudit.analytics.api.dto.TranscriptionMapper;
import com.callaudit.analytics.domain.transcription.Transcription;
import com.callaudit.analytics.domain.transcription.TranscriptionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for transcription queries.
 * Part of the transcription bounded context within analytics-service.
 *
 * This controller serves transcription data from the read model,
 * which is populated by consuming CallTranscribed events from Kafka.
 */
@RestController
@RequestMapping("/api/transcriptions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Transcriptions", description = "Transcription query endpoints")
public class TranscriptionController {

    private final TranscriptionQueryService queryService;
    private final TranscriptionMapper mapper;

    /**
     * Get transcription by call ID.
     *
     * @param callId the call ID
     * @return the transcription with segments
     */
    @GetMapping("/{callId}")
    @Operation(
            summary = "Get transcription by call ID",
            description = "Retrieves the full transcription including speaker-separated segments for a specific call"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Transcription found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TranscriptionDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Transcription not found for the given call ID",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid call ID format",
                    content = @Content
            )
    })
    public ResponseEntity<TranscriptionDTO> getTranscription(
            @Parameter(description = "Call ID (UUID format)", required = true)
            @PathVariable UUID callId) {

        log.info("Received request for transcription with call ID: {}", callId);

        try {
            Transcription transcription = queryService.getByCallId(callId);
            TranscriptionDTO dto = mapper.toDTO(transcription);

            log.info("Successfully retrieved transcription for call ID: {} with {} segments",
                    callId, dto.getSegments().size());

            return ResponseEntity.ok(dto);

        } catch (TranscriptionQueryService.TranscriptionNotFoundException e) {
            log.warn("Transcription not found for call ID: {}", callId);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Check if transcription exists for a call ID.
     *
     * @param callId the call ID
     * @return 200 if exists, 404 if not
     */
    @GetMapping("/{callId}/exists")
    @Operation(
            summary = "Check if transcription exists",
            description = "Checks whether a transcription exists for the given call ID"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Transcription exists",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Transcription does not exist",
                    content = @Content
            )
    })
    public ResponseEntity<Void> checkTranscriptionExists(
            @Parameter(description = "Call ID (UUID format)", required = true)
            @PathVariable UUID callId) {

        log.debug("Checking if transcription exists for call ID: {}", callId);

        boolean exists = queryService.existsByCallId(callId);

        return exists ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}
