# Transcription Service

Python-based microservice for transcribing audio files using OpenAI Whisper. Part of the Call Auditing Platform with Voice of the Customer (VoC) system.

## Overview

This service:
1. Consumes `CallReceived` events from Kafka topic `calls.received`
2. Downloads audio files from MinIO object storage
3. Transcribes audio using OpenAI Whisper
4. Performs basic speaker diarization (agent/customer identification)
5. Publishes `CallTranscribed` events to Kafka topic `calls.transcribed`

## Technology Stack

- **FastAPI 0.110.0** - Web framework
- **OpenAI Whisper** - Speech-to-text transcription
- **Kafka** - Event streaming
- **MinIO** - Object storage for audio files
- **Pydantic** - Data validation and settings management

## Architecture

```
[Kafka: calls.received]
    ↓
[Download audio from MinIO]
    ↓
[Whisper transcription]
    ↓
[Speaker diarization]
    ↓
[Kafka: calls.transcribed]
```

## Project Structure

```
transcription-service/
├── main.py                      # FastAPI application entry point
├── config.py                    # Configuration settings
├── models/
│   ├── events.py               # Event schemas (CallReceived, CallTranscribed)
│   └── transcription.py        # Transcription result models
├── services/
│   ├── kafka_service.py        # Kafka consumer/producer
│   ├── minio_service.py        # MinIO client for audio downloads
│   └── whisper_service.py      # Whisper transcription engine
├── routers/
│   └── health.py               # Health check endpoints
├── requirements.txt             # Python dependencies
├── Dockerfile                   # Container image definition
└── README.md                    # This file
```

## Configuration

Configuration is managed via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:9092` | Kafka broker address |
| `MINIO_ENDPOINT` | `minio:9000` | MinIO server endpoint |
| `MINIO_ACCESS_KEY` | `minioadmin` | MinIO access key |
| `MINIO_SECRET_KEY` | `minioadmin` | MinIO secret key |
| `MINIO_BUCKET` | `calls` | MinIO bucket for audio files |
| `MODEL_SIZE` | `base` | Whisper model size (tiny, base, small, medium, large) |

## Whisper Model Sizes

| Model | Parameters | VRAM Required | Relative Speed |
|-------|-----------|---------------|----------------|
| tiny | 39M | ~1 GB | ~32x |
| base | 74M | ~1 GB | ~16x |
| small | 244M | ~2 GB | ~6x |
| medium | 769M | ~5 GB | ~2x |
| large | 1550M | ~10 GB | 1x |

**Recommendation**: Use `base` for development, `small` or `medium` for production.

## Event Schemas

### Input: CallReceived Event

```json
{
  "eventId": "uuid",
  "eventType": "CallReceived",
  "aggregateId": "call-id",
  "aggregateType": "Call",
  "timestamp": "2024-12-29T10:30:00Z",
  "version": 1,
  "causationId": null,
  "correlationId": "uuid",
  "metadata": {
    "userId": "system",
    "service": "call-ingestion-service"
  },
  "payload": {
    "callId": "call-id",
    "audioFileUrl": "2024/12/call-id.wav",
    "timestamp": "2024-12-29T10:30:00Z",
    "phoneNumber": "+1234567890",
    "agentId": "agent-123",
    "duration": 180
  }
}
```

### Output: CallTranscribed Event

```json
{
  "eventId": "uuid",
  "eventType": "CallTranscribed",
  "aggregateId": "call-id",
  "aggregateType": "Call",
  "timestamp": "2024-12-29T10:31:00Z",
  "version": 1,
  "causationId": "call-received-event-id",
  "correlationId": "uuid",
  "metadata": {
    "userId": "system",
    "service": "transcription-service"
  },
  "payload": {
    "callId": "call-id",
    "transcription": {
      "fullText": "Hello, this is customer service...",
      "segments": [
        {
          "speaker": "agent",
          "startTime": 0.0,
          "endTime": 2.5,
          "text": "Hello, this is customer service. How can I help you today?"
        },
        {
          "speaker": "customer",
          "startTime": 3.0,
          "endTime": 5.5,
          "text": "I have a question about my account."
        }
      ],
      "language": "en",
      "confidence": 0.92
    }
  }
}
```

## Speaker Diarization

The service implements **basic speaker diarization** using pause detection:

- Alternates between `agent` and `customer` labels
- Detects speaker changes based on silence gaps (1.5 seconds threshold)
- Assumes the agent speaks first
- Works reasonably well for two-speaker conversations

**Note**: For advanced diarization, integrate pyannote.audio or similar.

## API Endpoints

### Health Checks

- `GET /health` - Basic health check (always returns 200)
- `GET /ready` - Readiness check (validates Kafka and MinIO connectivity)
- `GET /` - Service information

### Example Response

```json
{
  "status": "ready",
  "service": "transcription-service",
  "dependencies": {
    "minio": true,
    "kafka": true
  }
}
```

## Running Locally

### Prerequisites

- Python 3.12+
- ffmpeg installed
- Access to Kafka and MinIO

### Setup

```bash
# Create virtual environment
python -m venv venv
source venv/bin/activate  # Mac/Linux
# venv\Scripts\activate   # Windows

# Install dependencies
pip install -r requirements.txt

# Set environment variables (optional)
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export MINIO_ENDPOINT=localhost:9000
export MODEL_SIZE=base

# Run the service
uvicorn main:app --reload --port 8082
```

### Running with Docker

```bash
# Build image
docker build -t transcription-service .

# Run container
docker run -p 8082:8000 \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e MINIO_ENDPOINT=minio:9000 \
  transcription-service
```

### Running with Docker Compose

```bash
# From project root
docker compose up -d transcription-service

# View logs
docker compose logs -f transcription-service

# Rebuild after code changes
docker compose up -d --build transcription-service
```

## Development

### Adding New Features

1. **Modify event schemas**: Update `models/events.py`
2. **Add processing logic**: Update `services/whisper_service.py`
3. **Update background processor**: Modify `main.py`

### Testing Locally

```bash
# Start infrastructure services
docker compose up -d kafka minio

# Initialize MinIO bucket
docker compose exec minio mc alias set local http://localhost:9000 minioadmin minioadmin
docker compose exec minio mc mb local/calls

# Run service locally
uvicorn main:app --reload --port 8082

# In another terminal, produce test event to Kafka
docker compose exec kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic calls.received
# Paste JSON event and press Enter
```

### Monitoring Transcriptions

```bash
# Consume CallTranscribed events
docker compose exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic calls.transcribed \
  --from-beginning
```

## Performance Considerations

- **Model loading**: Whisper model is loaded once on startup (~1-10 seconds depending on size)
- **Transcription time**: Varies by model size and audio duration
  - Base model: ~0.1x real-time (1 minute audio = 6 seconds transcription)
  - Small model: ~0.3x real-time
  - Medium model: ~0.5x real-time
- **Memory usage**:
  - Base model: ~2 GB RAM
  - Small model: ~3 GB RAM
  - Medium model: ~6 GB RAM

## Error Handling

The service implements robust error handling:

- **Kafka connection failures**: Retries with exponential backoff
- **MinIO download errors**: Logged and skipped (message remains in Kafka)
- **Transcription failures**: Logged and skipped (prevents blocking)
- **Temporary file cleanup**: Always cleaned up in finally block

## Observability

### Logs

All operations are logged with structured logging:

```
2024-12-29 10:30:15 - services.kafka_service - INFO - Received event - ID: abc123, CallID: call-456
2024-12-29 10:30:16 - services.minio_service - INFO - Downloading audio file: 2024/12/call-456.wav
2024-12-29 10:30:17 - services.whisper_service - INFO - Starting transcription for: /tmp/audio_xyz.wav
2024-12-29 10:30:25 - services.whisper_service - INFO - Transcription complete. Language: en
2024-12-29 10:30:26 - services.kafka_service - INFO - Event published successfully
```

### Metrics (Future)

Planned Prometheus metrics:

- `transcription_duration_seconds` - Histogram of transcription times
- `transcription_total` - Counter of transcriptions completed
- `transcription_errors_total` - Counter of transcription failures
- `audio_duration_seconds` - Histogram of audio file durations

## Troubleshooting

### Service won't start

```bash
# Check logs
docker compose logs transcription-service

# Common issues:
# 1. Kafka not ready - wait for Kafka to start
# 2. Whisper model download - first run downloads model (can take minutes)
# 3. Out of memory - reduce MODEL_SIZE to "tiny" or "base"
```

### No events being processed

```bash
# Check if events are in Kafka
docker compose exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic calls.received \
  --from-beginning

# Check consumer group lag
docker compose exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --group transcription-service
```

### Transcription quality issues

- **Increase model size**: Set `MODEL_SIZE=small` or `MODEL_SIZE=medium`
- **Check audio quality**: Ensure audio files are clear, not corrupted
- **Verify language**: Whisper auto-detects language; check if detection is correct
- **Review confidence scores**: Low confidence (<0.7) indicates poor audio quality

## Contributing

Follow the project's standard development workflow:

1. Create a feature branch
2. Make changes and test locally
3. Ensure code passes linting (future: add flake8/black)
4. Update tests (future: add pytest tests)
5. Submit pull request

## License

Part of the Call Auditing Platform with VoC system.

## Related Services

- **call-ingestion-service** - Uploads audio and publishes CallReceived events
- **sentiment-service** - Consumes CallTranscribed events for sentiment analysis
- **voc-service** - Extracts Voice of Customer insights from transcriptions
- **audit-service** - Performs compliance auditing on transcriptions
