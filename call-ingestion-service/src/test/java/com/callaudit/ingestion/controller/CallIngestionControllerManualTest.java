package com.callaudit.ingestion.controller;

import com.callaudit.ingestion.model.Call;
import com.callaudit.ingestion.model.CallChannel;
import com.callaudit.ingestion.model.CallStatus;
import com.callaudit.ingestion.service.CallIngestionService;
import com.callaudit.ingestion.service.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests using manual MockMvc setup for Spring Boot 4.0.0 compatibility
 */
@SpringBootTest
@ActiveProfiles("test")
class CallIngestionControllerManualTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public CallIngestionService callIngestionService() {
            return mock(CallIngestionService.class);
        }

        @Bean
        @Primary
        public StorageService storageService() {
            return mock(StorageService.class);
        }
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private CallIngestionService callIngestionService;

    @Autowired
    private StorageService storageService;

    private MockMvc mockMvc;

    private static final String TEST_CALLER_ID = "555-0123";
    private static final String TEST_AGENT_ID = "agent-001";
    private static final String TEST_AUDIO_URL = "http://localhost:9000/calls/2025/01/test-id.wav";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        reset(callIngestionService, storageService);
    }

    @Test
    void uploadCall_ValidWavFile_Returns201Created() throws Exception {
        UUID callId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test-audio.wav",
            "audio/wav",
            "test audio content".getBytes()
        );

        Call mockCall = createMockCall(callId, TEST_CALLER_ID, TEST_AGENT_ID, CallChannel.INBOUND);

        when(callIngestionService.processUpload(any(), anyString(), anyString(), any(CallChannel.class)))
            .thenReturn(mockCall);

        mockMvc.perform(multipart("/api/calls/upload")
                .file(file)
                .param("callerId", TEST_CALLER_ID)
                .param("agentId", TEST_AGENT_ID)
                .param("channel", "INBOUND"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.callId").value(callId.toString()))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getCallStatus_ExistingCall_Returns200Ok() throws Exception {
        UUID callId = UUID.randomUUID();
        Call mockCall = createMockCall(callId, TEST_CALLER_ID, TEST_AGENT_ID, CallChannel.INBOUND);

        when(callIngestionService.getCallStatus(callId)).thenReturn(Optional.of(mockCall));

        mockMvc.perform(get("/api/calls/{callId}/status", callId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.callId").value(callId.toString()))
            .andExpect(jsonPath("$.callerId").value(TEST_CALLER_ID));
    }

    @Test
    void health_ReturnsHealthy() throws Exception {
        mockMvc.perform(get("/api/calls/health"))
            .andExpect(status().isOk())
            .andExpect(content().string("Call Ingestion Service is healthy"));
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
