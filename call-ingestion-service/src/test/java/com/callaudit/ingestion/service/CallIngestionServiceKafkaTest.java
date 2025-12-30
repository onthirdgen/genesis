package com.callaudit.ingestion.service;

import com.callaudit.ingestion.event.CallReceivedEvent;
import com.callaudit.ingestion.model.Call;
import com.callaudit.ingestion.model.CallChannel;
import com.callaudit.ingestion.model.CallStatus;
import com.callaudit.ingestion.repository.CallRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration test for CallIngestionService that verifies Kafka message publication.
 *
 * This test uses:
 * - @EmbeddedKafka: Creates an in-memory Kafka broker for testing
 * - MockBean for StorageService: Avoids needing MinIO during tests
 * - H2 in-memory database: Replaces PostgreSQL for tests
 * - Kafka consumer: Listens for published messages to verify they were sent
 */
@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = {"calls.received"}
)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CallIngestionServiceKafkaTest {

    /**
     * Test configuration that provides a mocked StorageService
     */
    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public StorageService storageService() {
            return mock(StorageService.class);
        }
    }

    @Autowired
    private CallIngestionService callIngestionService;

    @Autowired
    private CallRepository callRepository;

    @Autowired
    private StorageService storageService;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Value("${kafka.topics.call-received}")
    private String callReceivedTopic;

    private KafkaMessageListenerContainer<String, CallReceivedEvent> container;
    private BlockingQueue<ConsumerRecord<String, CallReceivedEvent>> records;

    @BeforeEach
    void setUp() {
        // Create a blocking queue to collect consumed messages
        records = new LinkedBlockingQueue<>();

        // Configure Kafka consumer
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-group");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CallReceivedEvent.class.getName());

        // Create consumer factory
        DefaultKafkaConsumerFactory<String, CallReceivedEvent> consumerFactory =
            new DefaultKafkaConsumerFactory<>(consumerProps);

        // Create container properties
        ContainerProperties containerProperties = new ContainerProperties(callReceivedTopic);

        // Create message listener container
        container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
        container.setupMessageListener((MessageListener<String, CallReceivedEvent>) record -> {
            records.add(record);
        });

        // Start the container and wait for it to be ready
        container.start();
        ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic());
    }

    @AfterEach
    void tearDown() {
        if (container != null) {
            container.stop();
        }
        callRepository.deleteAll();
    }

    @Test
    void testProcessUpload_PublishesCallReceivedEventToKafka() throws Exception {
        // Arrange
        String callerId = "555-0123";
        String agentId = "agent-001";
        CallChannel channel = CallChannel.INBOUND;
        String audioFileUrl = "http://localhost:9000/calls/2025/01/test-call-id.wav";

        // Mock the StorageService to avoid needing MinIO
        when(storageService.uploadFile(any(UUID.class), any(InputStream.class), anyString(), anyLong(), anyString()))
            .thenReturn(audioFileUrl);

        // Create a mock audio file
        MockMultipartFile mockFile = new MockMultipartFile(
            "file",
            "test-audio.wav",
            "audio/wav",
            "mock audio content".getBytes()
        );

        // Act - Process the upload (this should publish a Kafka message)
        Call result = callIngestionService.processUpload(mockFile, callerId, agentId, channel);

        // Assert - Verify the Call was saved to the database
        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals(callerId, result.getCallerId());
        assertEquals(agentId, result.getAgentId());
        assertEquals(channel, result.getChannel());
        assertEquals(CallStatus.PENDING, result.getStatus());
        assertEquals(audioFileUrl, result.getAudioFileUrl());
        assertNotNull(result.getCorrelationId());

        // Assert - Verify Kafka message was published
        // Wait up to 10 seconds for the message to be consumed
        ConsumerRecord<String, CallReceivedEvent> record = records.poll(10, TimeUnit.SECONDS);
        assertNotNull(record, "Kafka message should have been published");

        // Verify the message key is the call ID
        assertEquals(result.getId().toString(), record.key());

        // Verify the message value (CallReceivedEvent)
        CallReceivedEvent event = record.value();
        assertNotNull(event);

        // Verify event metadata
        assertNotNull(event.getEventId());
        assertEquals("CallReceived", event.getEventType());
        assertEquals(result.getId(), event.getAggregateId());
        assertEquals("Call", event.getAggregateType());
        assertNotNull(event.getTimestamp());
        assertEquals(1, event.getVersion());
        assertNotNull(event.getCausationId());
        assertEquals(result.getCorrelationId(), event.getCorrelationId());

        // Verify metadata map
        Map<String, Object> metadata = event.getMetadata();
        assertNotNull(metadata);
        assertEquals("system", metadata.get("userId"));
        assertEquals("call-ingestion-service", metadata.get("service"));

        // Verify payload
        CallReceivedEvent.Payload payload = event.getPayload();
        assertNotNull(payload);
        assertEquals(result.getId(), payload.getCallId());
        assertEquals(callerId, payload.getCallerId());
        assertEquals(agentId, payload.getAgentId());
        assertEquals(channel, payload.getChannel());
        assertEquals(audioFileUrl, payload.getAudioFileUrl());
        assertEquals("wav", payload.getAudioFormat());
        assertEquals(mockFile.getSize(), payload.getAudioFileSize());
        assertNotNull(payload.getStartTime());
    }

    @Test
    void testProcessUpload_PublishesCorrectEventForMp3File() throws Exception {
        // Arrange
        String callerId = "555-9999";
        String agentId = "agent-002";
        CallChannel channel = CallChannel.OUTBOUND;
        String audioFileUrl = "http://localhost:9000/calls/2025/01/test-call-id.mp3";

        when(storageService.uploadFile(any(UUID.class), any(InputStream.class), anyString(), anyLong(), anyString()))
            .thenReturn(audioFileUrl);

        MockMultipartFile mockFile = new MockMultipartFile(
            "file",
            "test-audio.mp3",
            "audio/mpeg",
            "mock mp3 audio content".getBytes()
        );

        // Act
        Call result = callIngestionService.processUpload(mockFile, callerId, agentId, channel);

        // Assert
        ConsumerRecord<String, CallReceivedEvent> record = records.poll(10, TimeUnit.SECONDS);
        assertNotNull(record);

        CallReceivedEvent event = record.value();
        CallReceivedEvent.Payload payload = event.getPayload();

        assertEquals("mp3", payload.getAudioFormat());
        assertEquals(channel, payload.getChannel());
        assertEquals(CallChannel.OUTBOUND, payload.getChannel());
    }

    @Test
    void testProcessUpload_EachCallGetsUniqueCorrelationId() throws Exception {
        // Arrange
        String audioFileUrl = "http://localhost:9000/calls/2025/01/test-call-id.wav";
        when(storageService.uploadFile(any(UUID.class), any(InputStream.class), anyString(), anyLong(), anyString()))
            .thenReturn(audioFileUrl);

        MockMultipartFile mockFile1 = new MockMultipartFile(
            "file", "test1.wav", "audio/wav", "content1".getBytes()
        );
        MockMultipartFile mockFile2 = new MockMultipartFile(
            "file", "test2.wav", "audio/wav", "content2".getBytes()
        );

        // Act - Process two uploads
        Call call1 = callIngestionService.processUpload(mockFile1, "caller1", "agent1", CallChannel.INBOUND);
        Call call2 = callIngestionService.processUpload(mockFile2, "caller2", "agent2", CallChannel.INBOUND);

        // Assert - Verify both messages were published
        ConsumerRecord<String, CallReceivedEvent> record1 = records.poll(10, TimeUnit.SECONDS);
        ConsumerRecord<String, CallReceivedEvent> record2 = records.poll(10, TimeUnit.SECONDS);

        assertNotNull(record1);
        assertNotNull(record2);

        CallReceivedEvent event1 = record1.value();
        CallReceivedEvent event2 = record2.value();

        // Verify each event has unique IDs
        assertNotEquals(event1.getEventId(), event2.getEventId());
        assertNotEquals(event1.getAggregateId(), event2.getAggregateId());
        assertNotEquals(event1.getCorrelationId(), event2.getCorrelationId());

        // Verify correlation IDs match the calls
        assertEquals(call1.getCorrelationId(), event1.getCorrelationId());
        assertEquals(call2.getCorrelationId(), event2.getCorrelationId());
    }

    @Test
    void testProcessUpload_EventTimestampIsRecent() throws Exception {
        // Arrange
        String audioFileUrl = "http://localhost:9000/calls/2025/01/test-call-id.wav";
        when(storageService.uploadFile(any(UUID.class), any(InputStream.class), anyString(), anyLong(), anyString()))
            .thenReturn(audioFileUrl);

        MockMultipartFile mockFile = new MockMultipartFile(
            "file", "test.wav", "audio/wav", "content".getBytes()
        );

        long beforeUpload = System.currentTimeMillis();

        // Act
        callIngestionService.processUpload(mockFile, "caller", "agent", CallChannel.INBOUND);

        long afterUpload = System.currentTimeMillis();

        // Assert
        ConsumerRecord<String, CallReceivedEvent> record = records.poll(10, TimeUnit.SECONDS);
        assertNotNull(record);

        CallReceivedEvent event = record.value();
        long eventTimestamp = event.getTimestamp().toEpochMilli();

        // Verify the timestamp is within a reasonable range (allow 1 second buffer)
        assertThat(eventTimestamp).isBetween(beforeUpload - 1000, afterUpload + 1000);
    }
}
