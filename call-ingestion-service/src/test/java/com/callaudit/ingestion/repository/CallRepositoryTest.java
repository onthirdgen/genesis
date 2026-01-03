package com.callaudit.ingestion.repository;

import com.callaudit.ingestion.model.Call;
import com.callaudit.ingestion.model.CallChannel;
import com.callaudit.ingestion.model.CallStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for CallRepository
 *
 * @SpringBootTest provides full application context for testing.
 * @Transactional ensures each test rolls back after execution.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Tag("integration")
class CallRepositoryTest {

    @Autowired
    private CallRepository callRepository;

    private Call testCall;

    @BeforeEach
    void setUp() {
        callRepository.deleteAll();

        testCall = Call.builder()
            .callerId("555-0123")
            .agentId("agent-001")
            .channel(CallChannel.INBOUND)
            .startTime(Instant.now())
            .audioFileUrl("http://localhost:9000/calls/2025/01/test-id.wav")
            .status(CallStatus.PENDING)
            .correlationId(UUID.randomUUID())
            .build();
    }

    @Test
    void save_NewCall_GeneratesId() {
        // Act
        Call savedCall = callRepository.save(testCall);

        // Assert
        assertNotNull(savedCall.getId());
        assertNotNull(savedCall.getCreatedAt());
        assertNotNull(savedCall.getUpdatedAt());
        assertEquals(testCall.getCallerId(), savedCall.getCallerId());
        assertEquals(testCall.getAgentId(), savedCall.getAgentId());
        assertEquals(testCall.getChannel(), savedCall.getChannel());
    }

    @Test
    void save_ExistingCall_UpdatesTimestamp() throws InterruptedException {
        // Arrange
        Call savedCall = callRepository.save(testCall);
        Instant originalUpdatedAt = savedCall.getUpdatedAt();

        // Wait to ensure timestamp difference (1ms minimum for Instant precision)
        Thread.sleep(2);

        // Act
        savedCall.setStatus(CallStatus.ANALYZING);
        Call updatedCall = callRepository.save(savedCall);

        // Assert
        assertEquals(savedCall.getId(), updatedCall.getId());
        assertEquals(CallStatus.ANALYZING, updatedCall.getStatus());
        assertThat(updatedCall.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
    }

    @Test
    void findById_ExistingCall_ReturnsCall() {
        // Arrange
        Call savedCall = callRepository.save(testCall);

        // Act
        Optional<Call> foundCall = callRepository.findById(savedCall.getId());

        // Assert
        assertTrue(foundCall.isPresent());
        assertEquals(savedCall.getId(), foundCall.get().getId());
        assertEquals(savedCall.getCallerId(), foundCall.get().getCallerId());
        assertEquals(savedCall.getAgentId(), foundCall.get().getAgentId());
    }

    @Test
    void findById_NonExistingCall_ReturnsEmpty() {
        // Arrange
        UUID nonExistingId = UUID.randomUUID();

        // Act
        Optional<Call> foundCall = callRepository.findById(nonExistingId);

        // Assert
        assertFalse(foundCall.isPresent());
    }

    @Test
    void findAll_MultipleCalls_ReturnsAllCalls() {
        // Arrange
        Call call1 = createCall("555-0001", "agent-001", CallChannel.INBOUND);
        Call call2 = createCall("555-0002", "agent-002", CallChannel.OUTBOUND);
        Call call3 = createCall("555-0003", "agent-003", CallChannel.INTERNAL);

        callRepository.save(call1);
        callRepository.save(call2);
        callRepository.save(call3);

        // Act
        List<Call> allCalls = callRepository.findAll();

        // Assert
        assertEquals(3, allCalls.size());
    }

    @Test
    void delete_ExistingCall_RemovesFromDatabase() {
        // Arrange
        Call savedCall = callRepository.save(testCall);
        UUID callId = savedCall.getId();

        // Act
        callRepository.delete(savedCall);
        Optional<Call> deletedCall = callRepository.findById(callId);

        // Assert
        assertFalse(deletedCall.isPresent());
    }

    @Test
    void save_CallWithAllChannels_Success() {
        // Test INBOUND
        Call inboundCall = createCall("555-0001", "agent-001", CallChannel.INBOUND);
        Call savedInbound = callRepository.save(inboundCall);
        assertEquals(CallChannel.INBOUND, savedInbound.getChannel());

        // Test OUTBOUND
        Call outboundCall = createCall("555-0002", "agent-002", CallChannel.OUTBOUND);
        Call savedOutbound = callRepository.save(outboundCall);
        assertEquals(CallChannel.OUTBOUND, savedOutbound.getChannel());

        // Test INTERNAL
        Call internalCall = createCall("555-0003", "agent-003", CallChannel.INTERNAL);
        Call savedInternal = callRepository.save(internalCall);
        assertEquals(CallChannel.INTERNAL, savedInternal.getChannel());

        // Assert all saved
        assertEquals(3, callRepository.findAll().size());
    }

    @Test
    void save_CallWithAllStatuses_Success() {
        // Test PENDING
        Call pendingCall = createCall("555-0001", "agent-001", CallChannel.INBOUND);
        pendingCall.setStatus(CallStatus.PENDING);
        Call savedPending = callRepository.save(pendingCall);
        assertEquals(CallStatus.PENDING, savedPending.getStatus());

        // Test TRANSCRIBING
        Call transcribingCall = createCall("555-0002", "agent-002", CallChannel.INBOUND);
        transcribingCall.setStatus(CallStatus.TRANSCRIBING);
        Call savedTranscribing = callRepository.save(transcribingCall);
        assertEquals(CallStatus.TRANSCRIBING, savedTranscribing.getStatus());

        // Test ANALYZING
        Call analyzingCall = createCall("555-0003", "agent-003", CallChannel.INBOUND);
        analyzingCall.setStatus(CallStatus.ANALYZING);
        Call savedAnalyzing = callRepository.save(analyzingCall);
        assertEquals(CallStatus.ANALYZING, savedAnalyzing.getStatus());

        // Test COMPLETED
        Call completedCall = createCall("555-0004", "agent-004", CallChannel.INBOUND);
        completedCall.setStatus(CallStatus.COMPLETED);
        Call savedCompleted = callRepository.save(completedCall);
        assertEquals(CallStatus.COMPLETED, savedCompleted.getStatus());

        // Test FAILED
        Call failedCall = createCall("555-0005", "agent-005", CallChannel.INBOUND);
        failedCall.setStatus(CallStatus.FAILED);
        Call savedFailed = callRepository.save(failedCall);
        assertEquals(CallStatus.FAILED, savedFailed.getStatus());
    }

    @Test
    void save_CallWithDuration_Success() {
        // Arrange
        testCall.setDuration(300); // 5 minutes

        // Act
        Call savedCall = callRepository.save(testCall);

        // Assert
        assertNotNull(savedCall.getId());
        assertEquals(300, savedCall.getDuration());
    }

    @Test
    void save_CallWithNullDuration_Success() {
        // Arrange
        testCall.setDuration(null);

        // Act
        Call savedCall = callRepository.save(testCall);

        // Assert
        assertNotNull(savedCall.getId());
        assertNull(savedCall.getDuration());
    }

    @Test
    void save_CallWithCorrelationId_Success() {
        // Arrange
        UUID correlationId = UUID.randomUUID();
        testCall.setCorrelationId(correlationId);

        // Act
        Call savedCall = callRepository.save(testCall);

        // Assert
        assertEquals(correlationId, savedCall.getCorrelationId());
    }

    @Test
    void save_MultipleCalls_EachGetsUniqueId() {
        // Arrange & Act
        Call call1 = callRepository.save(createCall("555-0001", "agent-001", CallChannel.INBOUND));
        Call call2 = callRepository.save(createCall("555-0002", "agent-002", CallChannel.OUTBOUND));
        Call call3 = callRepository.save(createCall("555-0003", "agent-003", CallChannel.INTERNAL));

        // Assert
        assertNotEquals(call1.getId(), call2.getId());
        assertNotEquals(call2.getId(), call3.getId());
        assertNotEquals(call1.getId(), call3.getId());
    }

    @Test
    void save_MultipleCalls_EachGetsUniqueTimestamps() throws InterruptedException {
        // Arrange & Act
        Call call1 = callRepository.save(createCall("555-0001", "agent-001", CallChannel.INBOUND));
        Thread.sleep(10);
        Call call2 = callRepository.save(createCall("555-0002", "agent-002", CallChannel.OUTBOUND));

        // Assert
        assertThat(call2.getCreatedAt()).isAfterOrEqualTo(call1.getCreatedAt());
    }

    @Test
    void existsById_ExistingCall_ReturnsTrue() {
        // Arrange
        Call savedCall = callRepository.save(testCall);

        // Act
        boolean exists = callRepository.existsById(savedCall.getId());

        // Assert
        assertTrue(exists);
    }

    @Test
    void existsById_NonExistingCall_ReturnsFalse() {
        // Arrange
        UUID nonExistingId = UUID.randomUUID();

        // Act
        boolean exists = callRepository.existsById(nonExistingId);

        // Assert
        assertFalse(exists);
    }

    @Test
    void count_MultipleCalls_ReturnsCorrectCount() {
        // Arrange
        callRepository.save(createCall("555-0001", "agent-001", CallChannel.INBOUND));
        callRepository.save(createCall("555-0002", "agent-002", CallChannel.OUTBOUND));
        callRepository.save(createCall("555-0003", "agent-003", CallChannel.INTERNAL));

        // Act
        long count = callRepository.count();

        // Assert
        assertEquals(3, count);
    }

    @Test
    void deleteAll_RemovesAllCalls() {
        // Arrange
        callRepository.save(createCall("555-0001", "agent-001", CallChannel.INBOUND));
        callRepository.save(createCall("555-0002", "agent-002", CallChannel.OUTBOUND));
        assertEquals(2, callRepository.count());

        // Act
        callRepository.deleteAll();

        // Assert
        assertEquals(0, callRepository.count());
    }

    @Test
    void save_CallWithLongUrls_Success() {
        // Arrange
        String longUrl = "http://localhost:9000/calls/2025/01/" + "a".repeat(200) + ".wav";
        testCall.setAudioFileUrl(longUrl);

        // Act
        Call savedCall = callRepository.save(testCall);

        // Assert
        assertNotNull(savedCall.getId());
        assertEquals(longUrl, savedCall.getAudioFileUrl());
    }

    // Helper methods

    private Call createCall(String callerId, String agentId, CallChannel channel) {
        return Call.builder()
            .callerId(callerId)
            .agentId(agentId)
            .channel(channel)
            .startTime(Instant.now())
            .audioFileUrl("http://localhost:9000/calls/2025/01/" + UUID.randomUUID() + ".wav")
            .status(CallStatus.PENDING)
            .correlationId(UUID.randomUUID())
            .build();
    }
}
