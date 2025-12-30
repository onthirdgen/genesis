package com.callaudit.ingestion.service;

import com.callaudit.ingestion.event.CallReceivedEvent;
import com.callaudit.ingestion.model.Call;
import com.callaudit.ingestion.model.CallChannel;
import com.callaudit.ingestion.model.CallStatus;
import com.callaudit.ingestion.repository.CallRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CallIngestionService {

    private final CallRepository callRepository;
    private final StorageService storageService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.call-received}")
    private String callReceivedTopic;

    /**
     * Process uploaded audio file:
     * 1. Store file in MinIO
     * 2. Create Call entity in database
     * 3. Publish CallReceived event to Kafka
     *
     * @param file uploaded audio file
     * @param callerId caller's phone number or ID
     * @param agentId agent's ID
     * @param channel call channel (INBOUND, OUTBOUND, INTERNAL)
     * @return created Call entity with UUID
     */
    @Transactional
    public Call processUpload(MultipartFile file, String callerId, String agentId, CallChannel channel) {
        try {
            // Validate file
            validateFile(file);

            // Generate correlation ID and start time
            UUID correlationId = UUID.randomUUID();
            Instant startTime = Instant.now();

            // Extract file extension
            String fileExtension = extractFileExtension(file.getOriginalFilename());
            String contentType = file.getContentType();

            // Create Call entity first (without ID - let JPA generate it)
            Call call = Call.builder()
                .callerId(callerId)
                .agentId(agentId)
                .channel(channel)
                .startTime(startTime)
                .audioFileUrl("pending") // Temporary placeholder
                .status(CallStatus.PENDING)
                .correlationId(correlationId)
                .build();

            // Save to database to get the auto-generated ID
            call = callRepository.save(call);
            UUID callId = call.getId();

            log.info("Processing upload for callId: {}, callerId: {}, agentId: {}, channel: {}",
                     callId, callerId, agentId, channel);

            // Upload file to MinIO with the generated callId
            String audioFileUrl = storageService.uploadFile(
                callId,
                file.getInputStream(),
                contentType,
                file.getSize(),
                fileExtension
            );

            // Update the Call entity with the actual MinIO URL
            call.setAudioFileUrl(audioFileUrl);
            call = callRepository.save(call);
            log.info("Saved call entity to database with MinIO URL: {}", callId);

            // Publish CallReceived event to Kafka
            publishCallReceivedEvent(call, fileExtension, file.getSize());

            return call;

        } catch (IOException e) {
            log.error("Error processing file upload", e);
            throw new RuntimeException("Failed to process file upload", e);
        }
    }

    /**
     * Get call status by call ID
     *
     * @param callId UUID of the call
     * @return Call entity if found
     */
    public Optional<Call> getCallStatus(UUID callId) {
        return callRepository.findById(callId);
    }

    /**
     * Publish CallReceived event to Kafka
     */
    private void publishCallReceivedEvent(Call call, String audioFormat, long audioFileSize) {
        UUID eventId = UUID.randomUUID();

        CallReceivedEvent.Payload payload = CallReceivedEvent.Payload.builder()
            .callId(call.getId())
            .callerId(call.getCallerId())
            .agentId(call.getAgentId())
            .channel(call.getChannel())
            .startTime(call.getStartTime())
            .audioFileUrl(call.getAudioFileUrl())
            .audioFormat(audioFormat)
            .audioFileSize(audioFileSize)
            .build();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("userId", "system");
        metadata.put("service", "call-ingestion-service");

        CallReceivedEvent event = CallReceivedEvent.builder()
            .eventId(eventId)
            .eventType("CallReceived")
            .aggregateId(call.getId())
            .aggregateType("Call")
            .timestamp(Instant.now())
            .version(1)
            .causationId(eventId) // First event, so causationId = eventId
            .correlationId(call.getCorrelationId())
            .metadata(metadata)
            .payload(payload)
            .build();

        log.info("Publishing CallReceived event to Kafka: eventId={}, callId={}", eventId, call.getId());

        kafkaTemplate.send(callReceivedTopic, call.getId().toString(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish CallReceived event for callId: {}", call.getId(), ex);
                } else {
                    log.info("Successfully published CallReceived event for callId: {}", call.getId());
                }
            });
    }

    /**
     * Validate uploaded file
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be null or empty");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }

        // Validate file extension (audio files only)
        String extension = extractFileExtension(filename).toLowerCase();
        if (!isValidAudioExtension(extension)) {
            throw new IllegalArgumentException(
                "Invalid file format. Supported formats: WAV, MP3, M4A, FLAC, OGG"
            );
        }

        // Validate file size (max 100MB as configured in application.yml)
        long maxSize = 100 * 1024 * 1024; // 100MB
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException(
                "File size exceeds maximum allowed size of 100MB"
            );
        }
    }

    /**
     * Extract file extension from filename
     */
    private String extractFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new IllegalArgumentException("Invalid filename: no extension found");
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    /**
     * Check if file extension is a valid audio format
     */
    private boolean isValidAudioExtension(String extension) {
        return extension.matches("(?i)(wav|mp3|m4a|flac|ogg)");
    }
}
