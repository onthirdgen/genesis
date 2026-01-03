# Sentiment Service

Python/FastAPI microservice for analyzing sentiment in call transcriptions using RoBERTa and VADER sentiment analysis.

## Overview

The Sentiment Service:
1. Consumes `CallTranscribed` events from Kafka topic `calls.transcribed`
2. Analyzes sentiment using RoBERTa transformer model (with VADER as fallback)
3. Calculates per-segment and overall sentiment scores
4. Detects sentiment escalation patterns (when sentiment worsens significantly)
5. Publishes `SentimentAnalyzed` events to Kafka topic `calls.sentiment-analyzed`

## Features

- **RoBERTa Model**: Uses `cardiffnlp/twitter-roberta-base-sentiment-latest` from HuggingFace
- **VADER Fallback**: Falls back to VADER if RoBERTa fails to load
- **Segment Analysis**: Analyzes each transcription segment independently
- **Weighted Scores**: Overall sentiment weighted by segment duration
- **Escalation Detection**: Detects significant sentiment drops (configurable threshold)
- **Event Sourcing**: Fully event-driven with Kafka integration
- **Health Endpoints**: Comprehensive health and readiness checks

## Architecture

```
CallTranscribed Event (Kafka)
        ↓
  Kafka Consumer
        ↓
  Sentiment Analyzer
   ├─ RoBERTa Model (primary)
   └─ VADER (fallback)
        ↓
  Escalation Detector
        ↓
  Kafka Producer
        ↓
SentimentAnalyzed Event (Kafka)
```

## Project Structure

```
sentiment-service/
├── main.py                      # FastAPI application entry point
├── config.py                    # Configuration with pydantic-settings
├── requirements.txt             # Python dependencies
├── Dockerfile                   # Container image definition
├── models/
│   ├── __init__.py
│   └── events.py               # Event schemas (Pydantic models)
├── services/
│   ├── __init__.py
│   ├── sentiment_service.py    # Sentiment analysis logic
│   └── kafka_service.py        # Kafka integration
└── routers/
    ├── __init__.py
    └── health.py               # Health check endpoints
```

## Configuration

Environment variables (see `.env.example`):

| Variable | Default | Description |
|----------|---------|-------------|
| KAFKA_BOOTSTRAP_SERVERS | localhost:9092 | Kafka broker address |
| KAFKA_CONSUMER_GROUP | sentiment-service | Consumer group ID |
| KAFKA_INPUT_TOPIC | calls.transcribed | Input topic name |
| KAFKA_OUTPUT_TOPIC | calls.sentiment-analyzed | Output topic name |
| MODEL_NAME | cardiffnlp/twitter-roberta-base-sentiment-latest | HuggingFace model name |
| USE_GPU | false | Enable GPU acceleration |
| ESCALATION_THRESHOLD | 0.5 | Sentiment drop threshold for escalation |
| LOG_LEVEL | INFO | Logging level |

## Sentiment Scoring

### RoBERTa Model

The RoBERTa model outputs three labels:
- **LABEL_0**: Negative (mapped to -1.0)
- **LABEL_1**: Neutral (mapped to 0.0)
- **LABEL_2**: Positive (mapped to 1.0)

Final score is a weighted average of all label scores.

### VADER Fallback

VADER provides a compound score from -1 to 1:
- **Positive**: compound >= 0.05
- **Negative**: compound <= -0.05
- **Neutral**: -0.05 < compound < 0.05

### Overall Sentiment Calculation

Overall sentiment is calculated as a duration-weighted average of segment scores:

```
overall_score = Σ(segment_score × segment_duration) / total_duration
```

### Escalation Detection

Escalation is detected when:
1. Sentiment drops by >= `ESCALATION_THRESHOLD` (default: 0.5)
2. The drop occurs between any two segments in the call

Example: If sentiment starts at 0.7 (positive) and drops to 0.1 (neutral) or -0.3 (negative), escalation is detected.

## Event Schemas

### Input: CallTranscribedEvent

```json
{
  "eventId": "uuid",
  "eventType": "CallTranscribed",
  "aggregateId": "call-123",
  "correlationId": "uuid",
  "payload": {
    "callId": "call-123",
    "transcription": "Full transcription text...",
    "segments": [
      {
        "startTime": 0.0,
        "endTime": 5.2,
        "text": "Hello, how can I help you?",
        "speaker": "agent",
        "confidence": 0.95
      }
    ],
    "duration": 120.5,
    "language": "en"
  }
}
```

### Output: SentimentAnalyzedEvent

```json
{
  "eventId": "uuid",
  "eventType": "SentimentAnalyzed",
  "aggregateId": "call-123",
  "causationId": "uuid",
  "correlationId": "uuid",
  "metadata": {
    "service": "sentiment-service",
    "modelName": "cardiffnlp/twitter-roberta-base-sentiment-latest",
    "usedFallback": false
  },
  "payload": {
    "callId": "call-123",
    "overallSentiment": "positive",
    "sentimentScore": 0.65,
    "segments": [
      {
        "startTime": 0.0,
        "endTime": 5.2,
        "text": "Hello, how can I help you?",
        "sentiment": "positive",
        "score": 0.85,
        "confidence": 0.92,
        "emotions": {
          "LABEL_0": 0.02,
          "LABEL_1": 0.06,
          "LABEL_2": 0.92
        },
        "speaker": "agent"
      }
    ],
    "escalationDetected": false,
    "escalationDetails": null,
    "processingTimeMs": 145.32
  }
}
```

## API Endpoints

### Health Checks

- **GET /health** - Basic health check
- **GET /health/ready** - Readiness check (checks model and Kafka)
- **GET /health/live** - Liveness check
- **GET /** - Service info

### Example Response

```json
{
  "status": "healthy",
  "service": "sentiment-service",
  "version": "1.0.0",
  "uptime_seconds": 125.43,
  "model_loaded": true,
  "kafka_connected": true
}
```

## Development

### Local Development (without Docker)

```bash
# Create virtual environment
python -m venv venv
source venv/bin/activate  # Mac/Linux
# venv\Scripts\activate   # Windows

# Install dependencies
pip install -r requirements.txt

# Set environment variables
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Run the service
uvicorn main:app --reload --port 8083
```

### Docker Development

```bash
# Build image
docker compose build sentiment-service

# Run service
docker compose up -d sentiment-service

# View logs
docker compose logs -f sentiment-service

# Restart after code changes
docker compose up -d --build sentiment-service
```

## Testing

### Manual Testing with Kafka

```bash
# Produce a test CallTranscribed event
docker compose exec kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic calls.transcribed

# Paste JSON event and press Ctrl+D

# Consume SentimentAnalyzed events
docker compose exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic calls.sentiment-analyzed \
  --from-beginning
```

### Health Check

```bash
curl http://localhost:8083/health
curl http://localhost:8083/health/ready
```

## Performance

### Model Loading
- RoBERTa model download: ~500MB (first run only, cached afterward)
- Model load time: 5-15 seconds (CPU), 2-5 seconds (GPU)

### Processing Time
- Per segment (CPU): 50-200ms
- Per segment (GPU): 10-50ms
- 10-segment call: 500ms-2s (CPU), 100-500ms (GPU)

### Memory Usage
- Base service: ~200MB
- With RoBERTa model: ~1.5GB
- With VADER only: ~250MB

## Troubleshooting

### Model Download Issues

If RoBERTa model fails to download:
1. Check internet connectivity
2. Verify HuggingFace is accessible
3. Service will automatically fall back to VADER

### Kafka Connection Issues

```bash
# Check Kafka is running
docker compose ps kafka

# Check topic exists
docker compose exec kafka kafka-topics --list --bootstrap-server localhost:9092
```

### High Memory Usage

To reduce memory usage:
1. Set `USE_GPU=false` (if GPU is not available)
2. Consider VADER-only mode (remove RoBERTa from Dockerfile)
3. Limit Docker container memory

## Dependencies

- **FastAPI**: Web framework
- **Uvicorn**: ASGI server
- **kafka-python**: Kafka client
- **transformers**: HuggingFace transformers
- **torch**: PyTorch (for RoBERTa)
- **vaderSentiment**: VADER sentiment analyzer
- **pydantic**: Data validation

## License

Part of the Call Auditing Platform project.
