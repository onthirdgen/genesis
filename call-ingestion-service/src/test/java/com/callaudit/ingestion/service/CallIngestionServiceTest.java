package com.callaudit.ingestion.service;

import com.callaudit.ingestion.model.Call;
import com.callaudit.ingestion.model.CallChannel;
import com.callaudit.ingestion.model.CallStatus;
import com.callaudit.ingestion.repository.CallRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CallIngestionService
 *
 * These tests use Mockito to mock dependencies and test the business logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class CallIngestionServiceTest {

    @Mock
    private CallRepository callRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private CallIngestionService callIngestionService;

    @Captor
    private ArgumentCaptor<Call> callCaptor;

    private static final String CALL_RECEIVED_TOPIC = "calls.received";
    private static final String TEST_CALLER_ID = "555-0123";
    private static final String TEST_AGENT_ID = "agent-001";
    private static final String TEST_AUDIO_URL = "http://localhost:9000/calls/2025/01/test-id.wav";

    @BeforeEach
    void setUp() {
        // Set the topic name using reflection (simulates @Value injection)
        ReflectionTestUtils.setField(callIngestionService, "callReceivedTopic", CALL_RECEIVED_TOPIC);
    }

    @Test
    void processUpload_ValidWavFile_Success() throws IOException {
        // Arrange
        MockMultipartFile file = createMockAudioFile("test-audio.wav", "audio/wav");
        CallChannel channel = CallChannel.INBOUND;

        UUID generatedCallId = UUID.randomUUID();
        Call savedCall = createMockCall(generatedCallId, TEST_CALLER_ID, TEST_AGENT_ID, channel);

        when(callRepository.save(any(Call.class)))
            .thenAnswer(invocation -> {
                Call call = invocation.getArgument(0);
                call.setId(generatedCallId);
                return call;
            })
            .thenReturn(savedCall);

        when(storageService.uploadFile(any(UUID.class), any(InputStream.class), anyString(), anyLong(), anyString()))
            .thenReturn(TEST_AUDIO_URL);

        when(kafkaTemplate.send(anyString(), anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Call result = callIngestionService.processUpload(file, TEST_CALLER_ID, TEST_AGENT_ID, channel);

        // Assert
        assertNotNull(result);
        assertEquals(TEST_CALLER_ID, result.getCallerId());
        assertEquals(TEST_AGENT_ID, result.getAgentId());
        assertEquals(channel, result.getChannel());
        assertEquals(CallStatus.PENDING, result.getStatus());
        assertEquals(TEST_AUDIO_URL, result.getAudioFileUrl());
        assertNotNull(result.getCorrelationId());

        // Verify repository interactions
        verify(callRepository, times(2)).save(any(Call.class));

        // Verify storage service was called
        verify(storageService).uploadFile(eq(generatedCallId), any(InputStream.class), eq("audio/wav"), eq(file.getSize()), eq("wav"));

        // Verify Kafka message was sent
        verify(kafkaTemplate).send(eq(CALL_RECEIVED_TOPIC), eq(generatedCallId.toString()), any());
    }

    @Test
    void processUpload_ValidMp3File_Success() throws IOException {
        // Arrange
        MockMultipartFile file = createMockAudioFile("test-audio.mp3", "audio/mpeg");
        CallChannel channel = CallChannel.OUTBOUND;

        UUID generatedCallId = UUID.randomUUID();
        Call savedCall = createMockCall(generatedCallId, TEST_CALLER_ID, TEST_AGENT_ID, channel);

        when(callRepository.save(any(Call.class)))
            .thenAnswer(invocation -> {
                Call call = invocation.getArgument(0);
                call.setId(generatedCallId);
                return call;
            })
            .thenReturn(savedCall);

        when(storageService.uploadFile(any(UUID.class), any(InputStream.class), anyString(), anyLong(), anyString()))
            .thenReturn(TEST_AUDIO_URL);

        when(kafkaTemplate.send(anyString(), anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Call result = callIngestionService.processUpload(file, TEST_CALLER_ID, TEST_AGENT_ID, channel);

        // Assert
        assertNotNull(result);
        verify(storageService).uploadFile(any(UUID.class), any(InputStream.class), eq("audio/mpeg"), anyLong(), eq("mp3"));
    }

    @Test
    void processUpload_NullFile_ThrowsException() {
        // Arrange
        MultipartFile nullFile = null;

        // Act & Assert
        assertThatThrownBy(() -> callIngestionService.processUpload(nullFile, TEST_CALLER_ID, TEST_AGENT_ID, CallChannel.INBOUND))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("File cannot be null or empty");

        verify(callRepository, never()).save(any(Call.class));
        verify(storageService, never()).uploadFile(any(), any(), any(), anyLong(), any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void processUpload_EmptyFile_ThrowsException() {
        // Arrange
        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.wav", "audio/wav", new byte[0]);

        // Act & Assert
        assertThatThrownBy(() -> callIngestionService.processUpload(emptyFile, TEST_CALLER_ID, TEST_AGENT_ID, CallChannel.INBOUND))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("File cannot be null or empty");

        verify(callRepository, never()).save(any(Call.class));
    }

    @Test
    void processUpload_InvalidFileExtension_ThrowsException() {
        // Arrange
        MockMultipartFile invalidFile = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            "not an audio file".getBytes()
        );

        // Act & Assert
        assertThatThrownBy(() -> callIngestionService.processUpload(invalidFile, TEST_CALLER_ID, TEST_AGENT_ID, CallChannel.INBOUND))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid file format");

        verify(callRepository, never()).save(any(Call.class));
    }

    @Test
    void processUpload_FileTooLarge_ThrowsException() {
        // Arrange - Create a file that exceeds 100MB
        long fileSize = 101 * 1024 * 1024; // 101MB
        MockMultipartFile largeFile = new MockMultipartFile(
            "file",
            "large-audio.wav",
            "audio/wav",
            new byte[(int) Math.min(fileSize, 1024)]
        ) {
            @Override
            public long getSize() {
                return fileSize;
            }
        };

        // Act & Assert
        assertThatThrownBy(() -> callIngestionService.processUpload(largeFile, TEST_CALLER_ID, TEST_AGENT_ID, CallChannel.INBOUND))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("File size exceeds maximum allowed size");

        verify(callRepository, never()).save(any(Call.class));
    }

    @Test
    void processUpload_NoFileExtension_ThrowsException() {
        // Arrange
        MockMultipartFile noExtFile = new MockMultipartFile(
            "file",
            "audiofile",
            "audio/wav",
            "content".getBytes()
        );

        // Act & Assert
        assertThatThrownBy(() -> callIngestionService.processUpload(noExtFile, TEST_CALLER_ID, TEST_AGENT_ID, CallChannel.INBOUND))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no extension found");

        verify(callRepository, never()).save(any(Call.class));
    }

    @Test
    void processUpload_ValidFlacFile_Success() throws IOException {
        // Arrange
        MockMultipartFile file = createMockAudioFile("test-audio.flac", "audio/flac");

        UUID generatedCallId = UUID.randomUUID();
        Call savedCall = createMockCall(generatedCallId, TEST_CALLER_ID, TEST_AGENT_ID, CallChannel.INBOUND);

        when(callRepository.save(any(Call.class)))
            .thenAnswer(invocation -> {
                Call call = invocation.getArgument(0);
                call.setId(generatedCallId);
                return call;
            })
            .thenReturn(savedCall);

        when(storageService.uploadFile(any(UUID.class), any(InputStream.class), anyString(), anyLong(), anyString()))
            .thenReturn(TEST_AUDIO_URL);

        when(kafkaTemplate.send(anyString(), anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Call result = callIngestionService.processUpload(file, TEST_CALLER_ID, TEST_AGENT_ID, CallChannel.INBOUND);

        // Assert
        assertNotNull(result);
        verify(storageService).uploadFile(any(UUID.class), any(InputStream.class), eq("audio/flac"), anyLong(), eq("flac"));
    }

    @Test
    void processUpload_ValidM4aFile_Success() throws IOException {
        // Arrange
        MockMultipartFile file = createMockAudioFile("test-audio.m4a", "audio/mp4");

        UUID generatedCallId = UUID.randomUUID();
        Call savedCall = createMockCall(generatedCallId, TEST_CALLER_ID, TEST_AGENT_ID, CallChannel.INTERNAL);

        when(callRepository.save(any(Call.class)))
            .thenAnswer(invocation -> {
                Call call = invocation.getArgument(0);
                call.setId(generatedCallId);
                return call;
            })
            .thenReturn(savedCall);

        when(storageService.uploadFile(any(UUID.class), any(InputStream.class), anyString(), anyLong(), anyString()))
            .thenReturn(TEST_AUDIO_URL);

        when(kafkaTemplate.send(anyString(), anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Call result = callIngestionService.processUpload(file, TEST_CALLER_ID, TEST_AGENT_ID, CallChannel.INTERNAL);

        // Assert
        assertNotNull(result);
        assertEquals(CallChannel.INTERNAL, result.getChannel());
        verify(storageService).uploadFile(any(UUID.class), any(InputStream.class), eq("audio/mp4"), anyLong(), eq("m4a"));
    }

    @Test
    void processUpload_ValidOggFile_Success() throws IOException {
        // Arrange
        MockMultipartFile file = createMockAudioFile("test-audio.ogg", "audio/ogg");

        UUID generatedCallId = UUID.randomUUID();
        Call savedCall = createMockCall(generatedCallId, TEST_CALLER_ID, TEST_AGENT_ID, CallChannel.INBOUND);

        when(callRepository.save(any(Call.class)))
            .thenAnswer(invocation -> {
                Call call = invocation.getArgument(0);
                call.setId(generatedCallId);
                return call;
            })
            .thenReturn(savedCall);

        when(storageService.uploadFile(any(UUID.class), any(InputStream.class), anyString(), anyLong(), anyString()))
            .thenReturn(TEST_AUDIO_URL);

        when(kafkaTemplate.send(anyString(), anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Call result = callIngestionService.processUpload(file, TEST_CALLER_ID, TEST_AGENT_ID, CallChannel.INBOUND);

        // Assert
        assertNotNull(result);
        verify(storageService).uploadFile(any(UUID.class), any(InputStream.class), eq("audio/ogg"), anyLong(), eq("ogg"));
    }

    @Test
    void processUpload_StorageServiceThrowsException_PropagatesException() throws IOException {
        // Arrange
        MockMultipartFile file = createMockAudioFile("test-audio.wav", "audio/wav");

        UUID generatedCallId = UUID.randomUUID();
        when(callRepository.save(any(Call.class)))
            .thenAnswer(invocation -> {
                Call call = invocation.getArgument(0);
                call.setId(generatedCallId);
                return call;
            });

        when(storageService.uploadFile(any(UUID.class), any(InputStream.class), anyString(), anyLong(), anyString()))
            .thenThrow(new RuntimeException("MinIO connection failed"));

        // Act & Assert
        // Note: RuntimeExceptions from storageService propagate directly (not wrapped)
        assertThatThrownBy(() -> callIngestionService.processUpload(file, TEST_CALLER_ID, TEST_AGENT_ID, CallChannel.INBOUND))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("MinIO connection failed");

        // Verify Kafka was not called since upload failed
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void processUpload_EachCallGetsUniqueCorrelationId() throws IOException {
        // Arrange
        MockMultipartFile file1 = createMockAudioFile("test1.wav", "audio/wav");
        MockMultipartFile file2 = createMockAudioFile("test2.wav", "audio/wav");

        UUID callId1 = UUID.randomUUID();
        UUID callId2 = UUID.randomUUID();

        Call savedCall1 = createMockCall(callId1, TEST_CALLER_ID, TEST_AGENT_ID, CallChannel.INBOUND);
        Call savedCall2 = createMockCall(callId2, TEST_CALLER_ID, TEST_AGENT_ID, CallChannel.INBOUND);

        when(callRepository.save(any(Call.class)))
            .thenAnswer(invocation -> {
                Call call = invocation.getArgument(0);
                call.setId(callId1);
                return call;
            })
            .thenReturn(savedCall1)
            .thenAnswer(invocation -> {
                Call call = invocation.getArgument(0);
                call.setId(callId2);
                return call;
            })
            .thenReturn(savedCall2);

        when(storageService.uploadFile(any(UUID.class), any(InputStream.class), anyString(), anyLong(), anyString()))
            .thenReturn(TEST_AUDIO_URL);

        when(kafkaTemplate.send(anyString(), anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Call result1 = callIngestionService.processUpload(file1, TEST_CALLER_ID, TEST_AGENT_ID, CallChannel.INBOUND);
        Call result2 = callIngestionService.processUpload(file2, TEST_CALLER_ID, TEST_AGENT_ID, CallChannel.INBOUND);

        // Assert
        assertNotNull(result1.getCorrelationId());
        assertNotNull(result2.getCorrelationId());
        assertNotEquals(result1.getCorrelationId(), result2.getCorrelationId());
    }

    @Test
    void getCallStatus_ExistingCall_ReturnsCall() {
        // Arrange
        UUID callId = UUID.randomUUID();
        Call expectedCall = createMockCall(callId, TEST_CALLER_ID, TEST_AGENT_ID, CallChannel.INBOUND);

        when(callRepository.findById(callId)).thenReturn(Optional.of(expectedCall));

        // Act
        Optional<Call> result = callIngestionService.getCallStatus(callId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(callId, result.get().getId());
        assertEquals(TEST_CALLER_ID, result.get().getCallerId());
        verify(callRepository).findById(callId);
    }

    @Test
    void getCallStatus_NonExistingCall_ReturnsEmpty() {
        // Arrange
        UUID callId = UUID.randomUUID();
        when(callRepository.findById(callId)).thenReturn(Optional.empty());

        // Act
        Optional<Call> result = callIngestionService.getCallStatus(callId);

        // Assert
        assertFalse(result.isPresent());
        verify(callRepository).findById(callId);
    }

    @Test
    void processUpload_CallSavedTwice_FirstWithPendingUrl_ThenWithActualUrl() throws IOException {
        // Arrange
        MockMultipartFile file = createMockAudioFile("test-audio.wav", "audio/wav");

        UUID generatedCallId = UUID.randomUUID();
        List<String> capturedUrls = new ArrayList<>();

        when(callRepository.save(any(Call.class)))
            .thenAnswer(invocation -> {
                Call call = invocation.getArgument(0);
                // Capture the URL at the time of this save call
                capturedUrls.add(call.getAudioFileUrl());

                if (call.getId() == null) {
                    call.setId(generatedCallId);
                }
                // Return the same call to mimic JPA behavior
                return call;
            });

        when(storageService.uploadFile(any(UUID.class), any(InputStream.class), anyString(), anyLong(), anyString()))
            .thenReturn(TEST_AUDIO_URL);

        when(kafkaTemplate.send(anyString(), anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        callIngestionService.processUpload(file, TEST_CALLER_ID, TEST_AGENT_ID, CallChannel.INBOUND);

        // Assert
        verify(callRepository, times(2)).save(any(Call.class));

        // Verify URLs captured at the time of each save
        assertEquals(2, capturedUrls.size());
        assertEquals("pending", capturedUrls.get(0));
        assertEquals(TEST_AUDIO_URL, capturedUrls.get(1));
    }

    // Helper methods

    private MockMultipartFile createMockAudioFile(String filename, String contentType) {
        return new MockMultipartFile(
            "file",
            filename,
            contentType,
            "mock audio content".getBytes()
        );
    }

    private Call createMockCall(UUID id, String callerId, String agentId, CallChannel channel) {
        return Call.builder()
            .id(id)
            .callerId(callerId)
            .agentId(agentId)
            .channel(channel)
            .startTime(Instant.now())
            .audioFileUrl(TEST_AUDIO_URL)
            .status(CallStatus.PENDING)
            .correlationId(UUID.randomUUID())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }
}
