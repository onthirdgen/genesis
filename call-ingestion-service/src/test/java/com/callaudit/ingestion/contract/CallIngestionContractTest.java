package com.callaudit.ingestion.contract;

import com.callaudit.ingestion.model.Call;
import com.callaudit.ingestion.model.CallChannel;
import com.callaudit.ingestion.repository.CallRepository;
import com.callaudit.ingestion.service.CallIngestionService;
import com.callaudit.ingestion.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests that verify integration with REAL infrastructure:
 * - PostgreSQL (via Testcontainers)
 * - MinIO (via Testcontainers)
 * - Kafka (via Testcontainers)
 *
 * These tests are excluded from CI/CD and only run locally via:
 * mvn verify -Pintegration
 *
 * Purpose:
 * - Verify actual PostgreSQL queries work (including JSONB, TimescaleDB features)
 * - Verify actual MinIO uploads/downloads work (S3 compatibility)
 * - Verify actual Kafka message publishing works
 */
class CallIngestionContractTest extends ContractTestBase {

    @Autowired
    private CallIngestionService callIngestionService;

    @Autowired
    private CallRepository callRepository;

    @Autowired
    private StorageService storageService;

    @BeforeEach
    void setUp() {
        callRepository.deleteAll();
    }

    @Test
    void testCompleteCallIngestionFlow() throws IOException {
        // Arrange
        byte[] audioContent = "fake audio content".getBytes();
        MockMultipartFile audioFile = new MockMultipartFile(
            "file",
            "test-call.wav",
            "audio/wav",
            audioContent
        );

        // Act - Process upload (saves to real PostgreSQL, uploads to real MinIO)
        Call result = callIngestionService.processUpload(
            audioFile,
            "+1234567890",
            "agent-001",
            CallChannel.INBOUND
        );

        // Assert - Verify database persistence
        assertNotNull(result.getId());
        assertEquals("+1234567890", result.getCallerId());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());

        // Verify saved to real PostgreSQL
        Call savedCall = callRepository.findById(result.getId()).orElseThrow();
        assertThat(savedCall.getAudioFileUrl()).contains("http://");
        assertThat(savedCall.getAudioFileUrl()).contains("/calls/");

        // Verify file uploaded to real MinIO
        assertDoesNotThrow(() ->
            storageService.downloadFile(result.getId(), "wav")
        );
    }

    @Test
    void testMinIOIntegration() {
        // Arrange
        UUID callId = UUID.randomUUID();
        byte[] content = "test audio data".getBytes();
        java.io.ByteArrayInputStream inputStream =
            new java.io.ByteArrayInputStream(content);

        // Act - Upload to real MinIO
        String url = storageService.uploadFile(
            callId,
            inputStream,
            "audio/wav",
            content.length,
            "wav"
        );

        // Assert
        assertNotNull(url);
        assertThat(url).contains("calls");
        assertThat(url).contains(callId.toString());

        // Verify download from real MinIO
        assertDoesNotThrow(() ->
            storageService.downloadFile(callId, "wav")
        );
    }

    @Test
    void testPostgreSQLPersistence() {
        // Arrange
        Call call = Call.builder()
            .callerId("+9876543210")
            .agentId("agent-999")
            .channel(CallChannel.OUTBOUND)
            .startTime(java.time.Instant.now())
            .audioFileUrl("http://test.example.com/audio.wav")
            .status(com.callaudit.ingestion.model.CallStatus.PENDING)
            .correlationId(UUID.randomUUID())
            .build();

        // Act - Save to real PostgreSQL
        Call savedCall = callRepository.save(call);

        // Assert - Verify persistence
        assertNotNull(savedCall.getId());
        assertNotNull(savedCall.getCreatedAt());
        assertNotNull(savedCall.getUpdatedAt());

        // Verify retrieval from real PostgreSQL
        Call retrievedCall = callRepository.findById(savedCall.getId()).orElseThrow();
        assertEquals("+9876543210", retrievedCall.getCallerId());
        assertEquals("agent-999", retrievedCall.getAgentId());
        assertEquals(CallChannel.OUTBOUND, retrievedCall.getChannel());
    }
}
