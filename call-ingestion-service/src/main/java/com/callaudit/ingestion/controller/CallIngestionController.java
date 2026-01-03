package com.callaudit.ingestion.controller;

import com.callaudit.ingestion.model.Call;
import com.callaudit.ingestion.model.CallChannel;
import com.callaudit.ingestion.service.CallIngestionService;
import com.callaudit.ingestion.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/calls")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Call Ingestion", description = "API for uploading call audio files and retrieving call information")
public class CallIngestionController {

    private final CallIngestionService callIngestionService;
    private final StorageService storageService;

    @Operation(
        summary = "Upload call audio file",
        description = "Upload a call audio file for processing. Supported formats: WAV, MP3, M4A, FLAC, OGG. " +
                "The file is stored in MinIO, metadata is saved to PostgreSQL, and a CallReceived event is published to Kafka."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Call audio uploaded successfully",
                content = @Content(schema = @Schema(implementation = CallUploadResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request or unsupported audio format"),
        @ApiResponse(responseCode = "500", description = "Internal server error during upload")
    })
    // CORS handled by API Gateway - do not add @CrossOrigin here to avoid duplicate headers
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CallUploadResponse> uploadCall(
            @Parameter(description = "Audio file (WAV, MP3, M4A, FLAC, OGG)", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Caller's phone number or unique identifier", required = true, example = "555-0123")
            @RequestParam("callerId") @NotBlank String callerId,
            @Parameter(description = "Agent's unique identifier", required = true, example = "agent-001")
            @RequestParam("agentId") @NotBlank String agentId,
            @Parameter(description = "Call channel type", example = "INBOUND")
            @RequestParam(value = "channel", defaultValue = "INBOUND") CallChannel channel) {

        try {
            log.info("Received upload request: callerId={}, agentId={}, channel={}, filename={}",
                     callerId, agentId, channel, file.getOriginalFilename());

            Call call = callIngestionService.processUpload(file, callerId, agentId, channel);

            CallUploadResponse response = CallUploadResponse.builder()
                .callId(call.getId())
                .status(call.getStatus().toString())
                .audioFileUrl(call.getAudioFileUrl())
                .uploadedAt(call.getCreatedAt())
                .message("Call audio uploaded successfully and is being processed")
                .build();

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.error("Validation error during upload", e);
            return ResponseEntity.badRequest()
                .body(CallUploadResponse.builder()
                    .message("Validation error: " + e.getMessage())
                    .build());
        } catch (Exception e) {
            log.error("Error processing upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CallUploadResponse.builder()
                    .message("Failed to process upload: " + e.getMessage())
                    .build());
        }
    }

    @Operation(
        summary = "Get call status",
        description = "Retrieve the current processing status and metadata for a specific call"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Call status retrieved successfully",
                content = @Content(schema = @Schema(implementation = CallStatusResponse.class))),
        @ApiResponse(responseCode = "404", description = "Call not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{callId}/status")
    public ResponseEntity<CallStatusResponse> getCallStatus(
            @Parameter(description = "Unique identifier of the call", required = true)
            @PathVariable UUID callId) {
        try {
            log.info("Fetching status for callId: {}", callId);

            return callIngestionService.getCallStatus(callId)
                .map(call -> {
                    CallStatusResponse response = CallStatusResponse.builder()
                        .callId(call.getId())
                        .status(call.getStatus().toString())
                        .callerId(call.getCallerId())
                        .agentId(call.getAgentId())
                        .channel(call.getChannel().toString())
                        .startTime(call.getStartTime())
                        .audioFileUrl(call.getAudioFileUrl())
                        .createdAt(call.getCreatedAt())
                        .updatedAt(call.getUpdatedAt())
                        .build();
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());

        } catch (Exception e) {
            log.error("Error fetching call status for callId: {}", callId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(
        summary = "Download call audio",
        description = "Stream or download the audio file for a specific call"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Audio file retrieved successfully",
                content = @Content(mediaType = "audio/*")),
        @ApiResponse(responseCode = "404", description = "Call or audio file not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{callId}/audio")
    public ResponseEntity<InputStreamResource> getCallAudio(
            @Parameter(description = "Unique identifier of the call", required = true)
            @PathVariable UUID callId,
            @Parameter(description = "Audio format (optional, defaults to stored format)", example = "mp3")
            @RequestParam(value = "format", required = false) String format) {

        try {
            // Verify call exists
            Call call = callIngestionService.getCallStatus(callId)
                .orElseThrow(() -> new RuntimeException("Call not found"));

            // Extract format from stored audioFileUrl if not specified
            if (format == null || format.isBlank()) {
                String audioFileUrl = call.getAudioFileUrl();
                int lastDot = audioFileUrl.lastIndexOf('.');
                format = (lastDot > 0) ? audioFileUrl.substring(lastDot + 1) : "wav";
            }

            log.info("Fetching audio for callId: {}, format: {}", callId, format);

            // Get audio stream from MinIO
            InputStream audioStream = storageService.downloadFile(callId, format);

            // Determine content type based on format
            String contentType = getContentType(format);

            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header("Content-Disposition", "inline; filename=\"" + callId + "." + format + "\"")
                .body(new InputStreamResource(audioStream));

        } catch (RuntimeException e) {
            log.error("Error fetching audio for callId: {}", callId, e);
            if (e.getMessage().contains("Call not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Unexpected error fetching audio for callId: {}", callId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(
        summary = "Health check",
        description = "Check if the Call Ingestion Service is running and healthy"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Service is healthy")
    })
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Call Ingestion Service is healthy");
    }

    /**
     * Get content type for audio format
     */
    private String getContentType(String format) {
        return switch (format.toLowerCase()) {
            case "wav" -> "audio/wav";
            case "mp3" -> "audio/mpeg";
            case "m4a" -> "audio/mp4";
            case "flac" -> "audio/flac";
            case "ogg" -> "audio/ogg";
            default -> "application/octet-stream";
        };
    }

    // Response DTOs

    @Data
    @Builder(builderClassName = "CallUploadResponseBuilder")
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallUploadResponse {
        private UUID callId;
        private String status;
        private String audioFileUrl;
        private Instant uploadedAt;
        private String message;
    }

    @Data
    @Builder(builderClassName = "CallStatusResponseBuilder")
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallStatusResponse {
        private UUID callId;
        private String status;
        private String callerId;
        private String agentId;
        private String channel;
        private Instant startTime;
        private String audioFileUrl;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
