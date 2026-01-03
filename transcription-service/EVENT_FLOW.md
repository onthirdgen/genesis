# Transcription Service Event Flow

## Overview

This document describes the complete event processing flow for the Transcription Service.

## Event Processing Pipeline

```
┌─────────────────────────────────────────────────────────────────┐
│                     TRANSCRIPTION SERVICE                        │
└─────────────────────────────────────────────────────────────────┘

    1. CONSUME EVENT
    ┌──────────────────────────────────────┐
    │ Kafka Topic: calls.received          │
    │ Consumer Group: transcription-service│
    └──────────────────────────────────────┘
                    ↓
    2. DOWNLOAD AUDIO
    ┌──────────────────────────────────────┐
    │ MinIO Bucket: calls                  │
    │ Object: {year}/{month}/{callId}.ext  │
    │ Temp File: /tmp/audio_xxxxx.wav      │
    └──────────────────────────────────────┘
                    ↓
    3. TRANSCRIBE
    ┌──────────────────────────────────────┐
    │ Whisper Model: base (default)        │
    │ Output: text + segments              │
    │ Language: auto-detected              │
    └──────────────────────────────────────┘
                    ↓
    4. SPEAKER DIARIZATION
    ┌──────────────────────────────────────┐
    │ Algorithm: pause-based detection     │
    │ Threshold: 1.5 seconds silence       │
    │ Labels: agent / customer             │
    └──────────────────────────────────────┘
                    ↓
    5. BUILD EVENT
    ┌──────────────────────────────────────┐
    │ Event Type: CallTranscribed          │
    │ Correlation ID: from input event     │
    │ Causation ID: input event ID         │
    └──────────────────────────────────────┘
                    ↓
    6. PUBLISH EVENT
    ┌──────────────────────────────────────┐
    │ Kafka Topic: calls.transcribed       │
    │ Partition: auto-assigned             │
    └──────────────────────────────────────┘
                    ↓
    7. CLEANUP
    ┌──────────────────────────────────────┐
    │ Delete temporary audio file          │
    │ Log completion status                │
    └──────────────────────────────────────┘
```

## Detailed Steps

### 1. Event Consumption

**Class**: `KafkaService.consume_call_received()`
**Location**: `/Users/jon/AI/genesis/transcription-service/services/kafka_service.py`

- Connects to Kafka cluster
- Subscribes to `calls.received` topic
- Consumer group: `transcription-service` (enables horizontal scaling)
- Auto-offset management (at-least-once delivery)
- Deserializes JSON to `CallReceivedEvent` Pydantic model

**Input Event Schema**:
```python
{
    "eventId": str,              # UUID
    "eventType": "CallReceived",
    "aggregateId": str,          # Call ID
    "aggregateType": "Call",
    "timestamp": str,            # ISO-8601
    "version": int,
    "causationId": str | None,
    "correlationId": str,        # UUID for distributed tracing
    "metadata": {
        "userId": str,
        "service": str
    },
    "payload": {
        "callId": str,
        "audioFileUrl": str,     # MinIO object key
        "timestamp": str,
        "phoneNumber": str | None,
        "agentId": str | None,
        "duration": int | None
    }
}
```

### 2. Audio Download

**Class**: `MinioService.download_file()`
**Location**: `/Users/jon/AI/genesis/transcription-service/services/minio_service.py`

- Parses audio URL from event payload
- Extracts MinIO object key (handles both URLs and plain keys)
- Creates temporary file with matching extension
- Downloads via MinIO SDK (`fget_object`)
- Returns local file path

**Supported Formats**: WAV, MP3, M4A, FLAC, OGG (any format ffmpeg can decode)

**Example Object Keys**:
- `2024/12/29/call-abc123.wav`
- `http://minio:9000/calls/2024/12/29/call-abc123.wav`

### 3. Transcription

**Class**: `WhisperService.transcribe()`
**Location**: `/Users/jon/AI/genesis/transcription-service/services/whisper_service.py`

- Loads Whisper model (cached after first load)
- Runs transcription with auto language detection
- Returns:
  - `text`: Full transcription
  - `segments`: Time-stamped segments with metadata
  - `language`: Detected language code (ISO 639-1)

**Whisper Segment Output**:
```python
{
    "id": int,
    "start": float,              # seconds
    "end": float,                # seconds
    "text": str,
    "avg_logprob": float,        # used for confidence
    "no_speech_prob": float,     # used for confidence
    "compression_ratio": float
}
```

### 4. Speaker Diarization

**Class**: `WhisperService._add_speaker_diarization()`
**Location**: `/Users/jon/AI/genesis/transcription-service/services/whisper_service.py`

**Algorithm**:
1. Start with speaker = "agent"
2. For each segment:
   - Calculate pause since last segment
   - If pause > 1.5 seconds: toggle speaker
   - Assign speaker label to segment
3. Return segments with speaker labels

**Limitations**:
- Works best for 2-speaker conversations
- Pause-based detection (no voice characteristics)
- No overlap detection
- First speaker assumed to be agent

**Future Improvements**:
- Integrate pyannote.audio for advanced diarization
- Add voice embedding clustering
- Support multi-speaker scenarios (>2 speakers)

### 5. Confidence Calculation

**Class**: `WhisperService._calculate_confidence()`
**Location**: `/Users/jon/AI/genesis/transcription-service/services/whisper_service.py`

**Formula**:
```
For each segment:
  logprob_conf = (avg_logprob + 2) / 2        # Normalize -2..0 to 0..1
  speech_conf = 1 - no_speech_prob            # Invert probability
  segment_conf = (logprob_conf + speech_conf) / 2

Overall confidence = weighted average by segment duration
```

**Interpretation**:
- `0.9 - 1.0`: Excellent quality, clear audio
- `0.8 - 0.9`: Good quality, minor background noise
- `0.7 - 0.8`: Acceptable quality, some unclear sections
- `< 0.7`: Poor quality, consider re-recording

### 6. Event Construction

**Class**: `CallTranscribedEvent`
**Location**: `/Users/jon/AI/genesis/transcription-service/models/events.py`

**Event Linking**:
- `aggregateId`: Copied from input event (call ID)
- `causationId`: Set to input event's `eventId` (direct causality)
- `correlationId`: Copied from input event (distributed trace)

**Payload Schema**:
```python
{
    "callId": str,
    "transcription": {
        "fullText": str,
        "segments": [
            {
                "speaker": "agent" | "customer",
                "startTime": float,
                "endTime": float,
                "text": str
            }
        ],
        "language": str,          # e.g., "en", "es", "fr"
        "confidence": float       # 0.0 - 1.0
    }
}
```

### 7. Event Publishing

**Class**: `KafkaService.publish_call_transcribed()`
**Location**: `/Users/jon/AI/genesis/transcription-service/services/kafka_service.py`

- Serializes event to JSON
- Publishes to `calls.transcribed` topic
- Waits for acknowledgment from all replicas (`acks=all`)
- Retries up to 3 times on failure
- Returns success/failure status

### 8. Cleanup

**Location**: `/Users/jon/AI/genesis/transcription-service/main.py` (finally block)

- Deletes temporary audio file (`os.remove`)
- Logs cleanup completion
- Executes even if transcription fails (finally block)

## Error Handling

### Kafka Consumer Errors

**Strategy**: Log and continue processing other messages

```python
try:
    event = CallReceivedEvent(**event_data)
    yield event
except Exception as e:
    logger.error(f"Error parsing event: {e}")
    continue  # Skip malformed event
```

### MinIO Download Errors

**Strategy**: Log error, skip message, it remains in Kafka for retry

```python
try:
    audio_path = minio_service.download_file(audio_url)
except S3Error as e:
    logger.error(f"MinIO download failed: {e}")
    continue  # Message stays in Kafka
```

### Transcription Errors

**Strategy**: Log error, skip message, continue processing

```python
try:
    transcription = whisper_service.transcribe(audio_path)
except Exception as e:
    logger.error(f"Transcription failed: {e}")
    continue  # Skip this call
finally:
    os.remove(audio_path)  # Always cleanup
```

### Publishing Errors

**Strategy**: Log error, message NOT committed in Kafka (automatic retry)

```python
success = kafka_service.publish_call_transcribed(event)
if not success:
    logger.error(f"Failed to publish event")
    # Consumer will retry on next poll
```

## Monitoring Points

### Key Metrics to Track

1. **Processing Rate**: Events consumed per minute
2. **Transcription Duration**: Time per audio minute
3. **Error Rate**: Failed transcriptions / total
4. **Queue Lag**: Messages in Kafka waiting to process
5. **Confidence Distribution**: Histogram of confidence scores

### Health Checks

**Endpoint**: `GET /ready`

Verifies:
- Kafka connectivity
- MinIO accessibility
- Whisper model loaded

**Status Codes**:
- `200 OK`: All dependencies healthy
- `503 Service Unavailable`: One or more dependencies down

## Performance Characteristics

### Resource Usage

| Model Size | RAM | VRAM | Startup Time | Speed (vs real-time) |
|-----------|-----|------|--------------|----------------------|
| tiny | 1 GB | - | 5s | 32x faster |
| base | 2 GB | - | 10s | 16x faster |
| small | 3 GB | - | 15s | 6x faster |
| medium | 6 GB | - | 30s | 2x faster |
| large | 12 GB | - | 60s | 1x (real-time) |

### Throughput Estimates

**Assumptions**:
- Base model (16x faster than real-time)
- Average call duration: 3 minutes
- Single instance

**Processing Time**:
- Download: ~1 second
- Transcription: 180 seconds / 16 = 11.25 seconds
- Diarization + Publishing: ~1 second
- **Total**: ~13 seconds per call

**Throughput**: ~275 calls per hour per instance

**Scaling**:
- 5 instances: ~1,375 calls/hour
- 10 instances: ~2,750 calls/hour

## Debugging Workflow

### 1. Check Service Health

```bash
curl http://localhost:8082/ready
```

### 2. Monitor Kafka Consumer Lag

```bash
docker compose exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --group transcription-service
```

### 3. View Service Logs

```bash
docker compose logs -f transcription-service
```

### 4. Manually Consume Output Events

```bash
docker compose exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic calls.transcribed \
  --from-beginning
```

### 5. Test with Sample Event

```bash
# Create test event
cat > test_event.json <<EOF
{
  "eventId": "test-123",
  "eventType": "CallReceived",
  "aggregateId": "call-456",
  "aggregateType": "Call",
  "timestamp": "2024-12-29T10:00:00Z",
  "version": 1,
  "correlationId": "corr-789",
  "metadata": {"userId": "system", "service": "test"},
  "payload": {
    "callId": "call-456",
    "audioFileUrl": "2024/12/call-456.wav",
    "timestamp": "2024-12-29T10:00:00Z"
  }
}
EOF

# Publish to Kafka
docker compose exec -T kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic calls.received < test_event.json
```

## Related Documentation

- **Main README**: `/Users/jon/AI/genesis/transcription-service/README.md`
- **Architecture Guide**: `/Users/jon/AI/genesis/call_auditing_architecture.md`
- **Docker Compose Setup**: `/Users/jon/AI/genesis/docker_compose_1.md`

## Event Sourcing Principles

This service follows event sourcing patterns:

1. **Immutable Events**: Events never modified, only appended
2. **Event Causality**: Each event links to causing event via `causationId`
3. **Distributed Tracing**: `correlationId` tracks entire call lifecycle
4. **Idempotency**: Duplicate events handled gracefully (future: add deduplication)
5. **Replay**: Events can be reprocessed by resetting consumer offset

## Next Steps

1. **Add Tests**: pytest with fixtures for Kafka/MinIO
2. **Add Metrics**: Prometheus instrumentation
3. **Advanced Diarization**: Integrate pyannote.audio
4. **Deduplication**: Track processed event IDs in Valkey
5. **Dead Letter Queue**: Route failed events to DLQ topic
