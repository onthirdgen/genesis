# Auto-Ingestion Options for Call-Ingestion-Service

## Overview

This document outlines options for automating phone call ingestion into the call-ingestion-service, eliminating the need for manual uploads via the REST API.

**Current State:** Manual upload via `POST /api/calls/upload` endpoint with multipart form data.

**Goal:** Automatic ingestion when phone calls are recorded by telephony systems.

---

## Option 1: MinIO Event Notifications (Recommended for MinIO/S3 Storage)

### Use Case
Best when calls are already being recorded directly to MinIO or S3-compatible storage.

### How It Works
MinIO supports webhook notifications that trigger when files are uploaded to a bucket. Configure MinIO to send HTTP POST requests to a new endpoint in call-ingestion-service.

### Implementation Steps

#### 1. Add Webhook Endpoint

Create new endpoint in `CallIngestionController.java`:

```java
@PostMapping("/api/calls/webhook/minio")
@Operation(summary = "Receive MinIO bucket event notifications")
public ResponseEntity<String> handleMinioEvent(@RequestBody MinioEvent event) {
    log.info("Received MinIO event: {}", event);

    for (MinioEventRecord record : event.getRecords()) {
        String objectKey = record.getS3().getObject().getKey();
        String bucketName = record.getS3().getBucket().getName();
        long fileSize = record.getS3().getObject().getSize();

        // Parse metadata from object key or tags
        // Example: "2025/01/caller-555-0123_agent-001_20250101120000.wav"
        CallMetadata metadata = parseObjectKey(objectKey);

        // Process the file that's already in MinIO
        callIngestionService.processExistingMinioFile(
            objectKey,
            metadata.getCallerId(),
            metadata.getAgentId(),
            metadata.getChannel()
        );
    }

    return ResponseEntity.ok("Events processed");
}
```

#### 2. Create MinIO Event Model

```java
@Data
public class MinioEvent {
    private List<MinioEventRecord> records;
}

@Data
public class MinioEventRecord {
    private String eventName; // "s3:ObjectCreated:Put"
    private S3Info s3;
}

@Data
public class S3Info {
    private BucketInfo bucket;
    private ObjectInfo object;
}
```

#### 3. Add Service Method

Add to `CallIngestionService.java`:

```java
@Transactional
public CallUploadResponse processExistingMinioFile(
    String objectKey,
    String callerId,
    String agentId,
    CallChannel channel) {

    // File already in MinIO, just create database record and publish event
    UUID correlationId = UUID.randomUUID();
    Instant startTime = Instant.now();

    Call call = new Call();
    call.setCallerId(callerId);
    call.setAgentId(agentId);
    call.setChannel(channel);
    call.setStartTime(startTime);
    call.setCorrelationId(correlationId);
    call.setStatus(CallStatus.PENDING);

    call = callRepository.save(call);

    // Construct MinIO URL
    String audioFileUrl = String.format("%s/%s/%s",
        minioEndpoint, bucketName, objectKey);
    call.setAudioFileUrl(audioFileUrl);
    callRepository.save(call);

    // Publish event
    publishCallReceivedEvent(call, correlationId);

    return new CallUploadResponse(call.getId(), call.getStatus());
}
```

#### 4. Configure MinIO Webhook

```bash
# Create webhook configuration pointing to your service
docker compose exec minio mc admin config set local notify_webhook:primary \
  endpoint="http://call-ingestion-service:8081/api/calls/webhook/minio" \
  queue_limit="1000"

# Restart MinIO
docker compose restart minio

# Add event notification to bucket
docker compose exec minio mc event add local/calls \
  arn:minio:sqs::primary:webhook \
  --event put \
  --suffix .wav \
  --suffix .mp3 \
  --suffix .m4a
```

### Pros
- Real-time processing (triggered immediately on upload)
- No polling overhead
- Leverages existing MinIO infrastructure
- Scalable (MinIO handles event delivery)

### Cons
- Requires MinIO webhook configuration
- Need to parse caller/agent metadata from filename or object tags
- Webhook endpoint must be accessible from MinIO container

---

## Option 2: File System Watcher

### Use Case
Best when calls are recorded to a local directory (e.g., on-premise PBX writing to NFS/local disk).

### How It Works
Monitor a directory using Java's WatchService API. When new audio files appear, automatically process them.

### Implementation Steps

#### 1. Create File Watcher Service

Create new file: `call-ingestion-service/src/main/java/com/callaudit/ingestion/service/FileWatcherService.java`

```java
package com.callaudit.ingestion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileWatcherService {

    private final CallIngestionService ingestionService;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // Configure via application.yml
    @Value("${file-watcher.directory:/var/calls/incoming}")
    private String watchDirectoryPath;

    @Value("${file-watcher.enabled:false}")
    private boolean enabled;

    @EventListener(ApplicationReadyEvent.class)
    public void startWatcher() {
        if (!enabled) {
            log.info("File watcher disabled");
            return;
        }

        Path watchDirectory = Paths.get(watchDirectoryPath);
        if (!Files.exists(watchDirectory)) {
            log.error("Watch directory does not exist: {}", watchDirectoryPath);
            return;
        }

        executorService.submit(() -> watchDirectory(watchDirectory));
        log.info("File watcher started for directory: {}", watchDirectoryPath);
    }

    private void watchDirectory(Path directory) {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            directory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

            while (true) {
                WatchKey key = watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        Path filePath = directory.resolve((Path) event.context());
                        processFile(filePath);
                    }
                }

                if (!key.reset()) {
                    log.error("Watch key no longer valid");
                    break;
                }
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error watching directory", e);
        }
    }

    private void processFile(Path filePath) {
        try {
            // Wait for file to be fully written
            Thread.sleep(1000);

            String filename = filePath.getFileName().toString();

            // Parse filename pattern: "2025-01-01_555-0123_agent-001_inbound.wav"
            FileMetadata metadata = parseFilename(filename);

            // Convert to MultipartFile equivalent or process directly
            byte[] fileContent = Files.readAllBytes(filePath);

            ingestionService.processFileFromBytes(
                fileContent,
                metadata.getCallerId(),
                metadata.getAgentId(),
                metadata.getChannel(),
                metadata.getFormat()
            );

            // Archive or delete processed file
            Files.move(filePath, Paths.get(watchDirectoryPath, "processed", filename));

            log.info("Successfully processed file: {}", filename);

        } catch (Exception e) {
            log.error("Error processing file: {}", filePath, e);
            // Move to error directory
            try {
                Files.move(filePath,
                    Paths.get(watchDirectoryPath, "error", filePath.getFileName().toString()));
            } catch (IOException ex) {
                log.error("Failed to move error file", ex);
            }
        }
    }

    private FileMetadata parseFilename(String filename) {
        // Example pattern: "20250101_120530_555-0123_agent-001_inbound.wav"
        // Implement your specific naming convention
        String[] parts = filename.split("_");

        return FileMetadata.builder()
            .callerId(parts[2])
            .agentId(parts[3])
            .channel(CallChannel.valueOf(parts[4].toUpperCase()))
            .format(filename.substring(filename.lastIndexOf('.') + 1))
            .build();
    }
}

@Data
@Builder
class FileMetadata {
    private String callerId;
    private String agentId;
    private CallChannel channel;
    private String format;
}
```

#### 2. Add Configuration

In `application.yml`:

```yaml
file-watcher:
  enabled: ${FILE_WATCHER_ENABLED:false}
  directory: ${FILE_WATCHER_DIRECTORY:/var/calls/incoming}
```

#### 3. Update CallIngestionService

Add method to process byte arrays:

```java
public CallUploadResponse processFileFromBytes(
    byte[] fileContent,
    String callerId,
    String agentId,
    CallChannel channel,
    String format) {

    // Create temp MultipartFile from bytes
    MultipartFile multipartFile = new MockMultipartFile(
        "file",
        "call." + format,
        "audio/" + format,
        fileContent
    );

    return processUpload(multipartFile, callerId, agentId, channel);
}
```

### Pros
- Works with local file systems
- No external dependencies
- Simple to implement
- Can process files from network shares (NFS, SMB)

### Cons
- Must define filename convention for metadata
- Requires file system access from container
- No built-in retry mechanism
- Single point of failure (if service down, files queue up)

---

## Option 3: Scheduled Polling from External Storage

### Use Case
Best when calls are stored in external S3, FTP, or network locations that don't support webhooks.

### How It Works
Use Spring's `@Scheduled` annotation to periodically check for new files and process them.

### Implementation Steps

#### 1. Create Scheduled Polling Service

```java
package com.callaudit.ingestion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledIngestionService {

    private final CallIngestionService ingestionService;
    private final ExternalStorageClient storageClient; // S3, FTP, etc.
    private final ProcessedFileRepository processedFileRepository;

    @Scheduled(fixedDelay = 60000) // Every 60 seconds
    public void pollForNewCalls() {
        log.debug("Polling for new call recordings...");

        try {
            List<RemoteFile> files = storageClient.listFiles("/recordings/new");

            for (RemoteFile file : files) {
                if (processedFileRepository.existsByExternalId(file.getId())) {
                    continue; // Already processed
                }

                processRemoteFile(file);
            }

        } catch (Exception e) {
            log.error("Error polling for new calls", e);
        }
    }

    private void processRemoteFile(RemoteFile file) {
        try {
            // Download file
            byte[] content = storageClient.downloadFile(file.getPath());

            // Parse metadata
            FileMetadata metadata = parseMetadata(file);

            // Process
            CallUploadResponse response = ingestionService.processFileFromBytes(
                content,
                metadata.getCallerId(),
                metadata.getAgentId(),
                metadata.getChannel(),
                metadata.getFormat()
            );

            // Mark as processed
            processedFileRepository.save(new ProcessedFile(
                file.getId(),
                file.getPath(),
                response.callId(),
                Instant.now()
            ));

            // Optionally move/delete remote file
            storageClient.moveFile(file.getPath(), "/recordings/processed/" + file.getName());

        } catch (Exception e) {
            log.error("Error processing remote file: {}", file.getPath(), e);
        }
    }
}
```

#### 2. Enable Scheduling

Add to `CallIngestionApplication.java`:

```java
@SpringBootApplication
@EnableScheduling  // Add this annotation
public class CallIngestionApplication {
    // ...
}
```

#### 3. Create Tracking Entity

```java
@Entity
@Table(name = "processed_files")
@Data
public class ProcessedFile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String externalId;

    private String externalPath;
    private UUID callId;
    private Instant processedAt;
}
```

### Pros
- Works with any storage system
- Can handle batches efficiently
- Simple retry logic
- Good for systems without event notifications

### Cons
- Polling delay (not real-time)
- Higher resource usage (regular polling)
- Need to track processed files to avoid duplicates
- Can miss files if processing fails

---

## Option 4: Kafka Consumer for External Events

### Use Case
Best when integrating with existing telephony systems that publish call events to Kafka.

### How It Works
Consume events from a Kafka topic published by your phone system when calls complete.

### Implementation Steps

#### 1. Create Kafka Consumer

```java
package com.callaudit.ingestion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelephonyEventConsumer {

    private final CallIngestionService ingestionService;

    @KafkaListener(
        topics = "${kafka.topics.telephony-call-completed:telephony.call.completed}",
        groupId = "call-ingestion-telephony"
    )
    public void handleCallCompleted(TelephonyCallCompletedEvent event) {
        log.info("Received call completed event: {}", event.getCallId());

        try {
            // Download audio file from URL in event
            byte[] audioContent = downloadAudio(event.getRecordingUrl());

            // Process call
            ingestionService.processFileFromBytes(
                audioContent,
                event.getCallerId(),
                event.getAgentId(),
                mapChannel(event.getDirection()),
                event.getAudioFormat()
            );

            log.info("Successfully processed call from telephony event: {}", event.getCallId());

        } catch (Exception e) {
            log.error("Error processing telephony event", e);
            // Event will be retried based on Kafka consumer config
            throw new RuntimeException("Failed to process call", e);
        }
    }

    private byte[] downloadAudio(String recordingUrl) {
        // Use RestTemplate or WebClient to download
        RestTemplate restTemplate = new RestTemplate();
        return restTemplate.getForObject(recordingUrl, byte[].class);
    }

    private CallChannel mapChannel(String direction) {
        return switch(direction.toLowerCase()) {
            case "inbound" -> CallChannel.INBOUND;
            case "outbound" -> CallChannel.OUTBOUND;
            default -> CallChannel.INTERNAL;
        };
    }
}
```

#### 2. Define Event Model

```java
@Data
public class TelephonyCallCompletedEvent {
    private String callId;
    private String callerId;
    private String agentId;
    private String direction; // "inbound", "outbound", "internal"
    private String recordingUrl;
    private String audioFormat;
    private Long duration;
    private Instant startTime;
    private Instant endTime;
}
```

#### 3. Configure Consumer

In `application.yml`:

```yaml
kafka:
  topics:
    telephony-call-completed: ${TELEPHONY_TOPIC:telephony.call.completed}
  consumer:
    auto-offset-reset: earliest
    enable-auto-commit: false # Manual commit after successful processing
```

### Pros
- Event-driven (real-time)
- Kafka handles reliability and retry
- Scalable (multiple consumers)
- Decoupled from telephony system
- Built-in exactly-once semantics

### Cons
- Requires telephony system to publish to Kafka
- Need to download audio separately
- Depends on external system's event format
- Requires Kafka consumer group management

---

## Option 5: REST API Webhooks for Cloud PBX

### Use Case
Best when using cloud-based telephony providers (Twilio, Vonage, RingCentral, etc.)

### How It Works
Telephony provider calls your webhook endpoint when call recording is available.

### Implementation Steps

#### 1. Add Webhook Endpoints

```java
@RestController
@RequestMapping("/api/calls/webhook")
@RequiredArgsConstructor
@Slf4j
public class TelephonyWebhookController {

    private final CallIngestionService ingestionService;

    // Twilio webhook
    @PostMapping(value = "/twilio", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> handleTwilioRecording(
        @RequestParam String RecordingUrl,
        @RequestParam String CallSid,
        @RequestParam(required = false) String From,
        @RequestParam(required = false) String To,
        @RequestParam(required = false) String Direction) {

        log.info("Received Twilio recording: {}", CallSid);

        try {
            // Download recording
            byte[] audioContent = downloadFromUrl(RecordingUrl);

            // Map phone numbers to caller/agent
            String callerId = Direction.equals("inbound") ? From : To;
            String agentId = Direction.equals("inbound") ? To : From;

            ingestionService.processFileFromBytes(
                audioContent,
                callerId,
                agentId,
                mapDirection(Direction),
                "mp3" // Twilio default
            );

            return ResponseEntity.ok("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response></Response>");

        } catch (Exception e) {
            log.error("Error processing Twilio webhook", e);
            return ResponseEntity.status(500).body("Error processing recording");
        }
    }

    // Vonage (Nexmo) webhook
    @PostMapping("/vonage")
    public ResponseEntity<Map<String, String>> handleVonageRecording(
        @RequestBody VonageRecordingEvent event) {

        log.info("Received Vonage recording: {}", event.getConversationUuid());

        try {
            byte[] audioContent = downloadFromUrl(event.getRecordingUrl());

            ingestionService.processFileFromBytes(
                audioContent,
                event.getFrom(),
                event.getTo(),
                CallChannel.INBOUND,
                "mp3"
            );

            return ResponseEntity.ok(Map.of("status", "success"));

        } catch (Exception e) {
            log.error("Error processing Vonage webhook", e);
            return ResponseEntity.status(500).body(Map.of("status", "error"));
        }
    }

    private byte[] downloadFromUrl(String url) {
        RestTemplate restTemplate = new RestTemplate();
        return restTemplate.getForObject(url, byte[].class);
    }
}
```

#### 2. Configure Webhook URLs in Provider

**Twilio:**
```
Configure in Twilio Console:
Recording Status Callback: https://your-domain.com/api/calls/webhook/twilio
```

**Vonage:**
```
Configure in Vonage Dashboard:
Event Webhook: https://your-domain.com/api/calls/webhook/vonage
```

#### 3. Add Security (Signature Validation)

```java
private boolean validateTwilioSignature(String signature, String url, Map<String, String> params) {
    String authToken = twilioAuthToken;

    // Twilio signature validation
    String data = url + params.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(e -> e.getKey() + e.getValue())
        .collect(Collectors.joining());

    String expectedSignature = Hashing.hmacSha1(authToken.getBytes())
        .hashString(data, StandardCharsets.UTF_8)
        .toString();

    return expectedSignature.equals(signature);
}
```

### Pros
- Cloud-native solution
- Provider handles reliability
- Real-time processing
- No infrastructure management
- Easy to test with provider tools

### Cons
- Requires public endpoint (or ngrok for dev)
- Must implement provider-specific logic
- Need webhook signature validation
- Dependent on provider's webhook reliability

---

## Decision Matrix

| Scenario | Best Option | Pros | Cons |
|----------|-------------|------|------|
| **Calls recorded to MinIO/S3** | Option 1: MinIO Events | Real-time, no polling, scalable | Requires webhook config |
| **Local PBX writing to disk** | Option 2: File Watcher | Simple, works with NFS | Requires filename convention |
| **Cloud PBX (Twilio/Vonage)** | Option 5: Webhooks | Native integration, real-time | Public endpoint required |
| **FTP/Network share** | Option 3: Scheduled Polling | Works anywhere, simple | Polling delay, resource usage |
| **Existing Kafka infrastructure** | Option 4: Kafka Consumer | Event-driven, reliable | Requires Kafka integration |

---

## Implementation Checklist

### Common Requirements (All Options)

- [ ] Add metadata parsing logic (caller ID, agent ID extraction)
- [ ] Update `CallIngestionService` to support byte array input
- [ ] Add error handling and retry logic
- [ ] Create processed file tracking (to avoid duplicates)
- [ ] Add monitoring/metrics for auto-ingestion
- [ ] Update API documentation
- [ ] Add integration tests
- [ ] Configure logging for debugging
- [ ] Set up alerts for failures

### Option-Specific Requirements

#### Option 1: MinIO Events
- [ ] Create webhook endpoint
- [ ] Define MinIO event models
- [ ] Configure MinIO webhook in docker-compose
- [ ] Test with manual file upload to MinIO
- [ ] Implement object key parsing strategy

#### Option 2: File Watcher
- [ ] Create FileWatcherService
- [ ] Define filename convention
- [ ] Add configuration properties
- [ ] Create processed/error directories
- [ ] Test with file system events

#### Option 3: Scheduled Polling
- [ ] Create ScheduledIngestionService
- [ ] Implement external storage client (S3/FTP)
- [ ] Create ProcessedFile entity and repository
- [ ] Configure schedule interval
- [ ] Add database migration for tracking table

#### Option 4: Kafka Consumer
- [ ] Create TelephonyEventConsumer
- [ ] Define telephony event models
- [ ] Configure consumer properties
- [ ] Implement audio download logic
- [ ] Add consumer group monitoring

#### Option 5: Cloud Webhooks
- [ ] Create webhook endpoints for each provider
- [ ] Implement signature validation
- [ ] Configure webhook URLs in provider dashboard
- [ ] Add provider-specific models
- [ ] Test with provider webhook testing tools

---

## Testing Strategies

### Unit Testing
```java
@SpringBootTest
class AutoIngestionTest {

    @MockBean
    private StorageService storageService;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void testFileWatcherProcessing() {
        // Mock file processing
        // Verify service calls
    }
}
```

### Integration Testing
```java
@SpringBootTest
@EmbeddedKafka
class KafkaConsumerIntegrationTest {

    @Test
    void testTelephonyEventConsumption() {
        // Publish test event
        // Verify processing
    }
}
```

### Manual Testing Commands

**Option 1 (MinIO):**
```bash
# Upload file directly to MinIO to trigger event
docker compose exec minio mc cp test.wav local/calls/2025/01/test.wav
```

**Option 2 (File Watcher):**
```bash
# Copy file to watched directory
cp test.wav /var/calls/incoming/20250101_555-0123_agent-001.wav
```

**Option 3 (Polling):**
```bash
# Check logs for scheduled execution
docker compose logs -f call-ingestion-service | grep "Polling for new calls"
```

**Option 5 (Webhooks):**
```bash
# Simulate Twilio webhook
curl -X POST http://localhost:8081/api/calls/webhook/twilio \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "RecordingUrl=http://example.com/test.mp3&CallSid=test-123&From=555-0123&To=555-0124&Direction=inbound"
```

---

## Performance Considerations

### Concurrent Processing
- File Watcher: Single thread by default (use ExecutorService for parallel processing)
- Scheduled Polling: Can process batches concurrently
- Kafka Consumer: Configure `concurrency` in `@KafkaListener`
- Webhooks: Spring Boot handles concurrent requests

### Resource Limits
- Set max file size limits (already configured: 100MB)
- Implement rate limiting for webhook endpoints
- Configure Kafka consumer `max.poll.records`
- Add circuit breakers for external downloads

### Monitoring Metrics
```java
@Timed("auto_ingestion.processing.time")
@Counted("auto_ingestion.files.processed")
public void processFile(...) {
    // Processing logic
}
```

---

## Next Steps

1. **Choose your option** based on the decision matrix
2. **Review implementation code** for selected option
3. **Test in development** with sample files
4. **Deploy to staging** and validate with real data
5. **Monitor metrics** and adjust configuration
6. **Document** your specific metadata conventions

---

## Questions to Answer Before Implementation

1. **Where are your phone calls currently recorded?**
   - Local file system
   - MinIO/S3
   - Cloud PBX provider
   - FTP server
   - Other

2. **What metadata is available?**
   - Filename convention
   - File tags/metadata
   - Separate manifest file
   - API lookup required

3. **What's your volume?**
   - Calls per day
   - Average file size
   - Peak hours

4. **What's your latency requirement?**
   - Real-time (seconds)
   - Near real-time (minutes)
   - Batch (hours)

5. **What's your reliability requirement?**
   - Best effort
   - At-least-once delivery
   - Exactly-once processing

---

**Document Version:** 1.0
**Last Updated:** 2026-01-01
**Author:** Claude Code
**Related Files:**
- `/call-ingestion-service/src/main/java/com/callaudit/ingestion/service/CallIngestionService.java`
- `/call-ingestion-service/src/main/java/com/callaudit/ingestion/controller/CallIngestionController.java`
- `/CLAUDE.md` - Project documentation
