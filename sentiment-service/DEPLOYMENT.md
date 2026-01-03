# Sentiment Service - Deployment Guide

## Quick Start

### 1. Start Infrastructure Services

```bash
cd /Users/jon/AI/genesis
docker compose up -d kafka postgres
```

Wait for Kafka to be ready (~30 seconds):
```bash
docker compose logs -f kafka | grep "Kafka Server started"
```

### 2. Build and Start Sentiment Service

```bash
docker compose build sentiment-service
docker compose up -d sentiment-service
```

### 3. Verify Service is Running

```bash
# Check container status
docker compose ps sentiment-service

# Check logs
docker compose logs -f sentiment-service

# Test health endpoint
curl http://localhost:8083/health
```

Expected response:
```json
{
  "status": "healthy",
  "service": "sentiment-service",
  "version": "1.0.0",
  "uptime_seconds": 45.21,
  "model_loaded": true,
  "kafka_connected": true
}
```

## Configuration

The service reads configuration from environment variables defined in `docker-compose.yml`:

```yaml
environment:
  KAFKA_BOOTSTRAP_SERVERS: kafka:9092
  MODEL_NAME: cardiffnlp/twitter-roberta-base-sentiment-latest
  USE_GPU: "false"
  LOG_LEVEL: INFO
```

To override:
1. Create `.env` file in the service directory
2. Add custom values (see `.env.example`)

## First Run Behavior

On first startup, the service will:
1. Download RoBERTa model (~500MB) - this takes 2-5 minutes
2. Cache model in container
3. Initialize Kafka consumer and producer
4. Start processing events

**Note**: Subsequent starts are faster as the model is cached.

## Monitoring

### Health Checks

- **Liveness**: `GET /health/live` - Returns 200 if process is alive
- **Readiness**: `GET /health/ready` - Returns ready=true when model loaded and Kafka connected
- **Health**: `GET /health` - Detailed health information

### Logs

```bash
# Follow logs
docker compose logs -f sentiment-service

# Last 100 lines
docker compose logs --tail=100 sentiment-service

# With timestamps
docker compose logs -f -t sentiment-service
```

### Metrics

The service logs processing metrics:
- Processing time per call (in milliseconds)
- Sentiment scores and classifications
- Escalation detection events

Example log:
```
2024-12-29 12:34:56 - INFO - Successfully processed call call-123:
sentiment=positive, score=0.723, escalation=false, time=145.32ms
```

## Testing

### Manual Event Testing

Create a test CallTranscribed event:

```bash
# Create test event JSON
cat > /tmp/test_event.json <<'EOF'
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "CallTranscribed",
  "aggregateId": "test-call-001",
  "aggregateType": "Call",
  "timestamp": "2024-12-29T12:00:00Z",
  "version": 1,
  "correlationId": "550e8400-e29b-41d4-a716-446655440001",
  "metadata": {
    "service": "transcription-service"
  },
  "payload": {
    "callId": "test-call-001",
    "transcription": "Hello, I need help with my order. This is very frustrating!",
    "segments": [
      {
        "startTime": 0.0,
        "endTime": 5.0,
        "text": "Hello, I need help with my order.",
        "speaker": "customer"
      },
      {
        "startTime": 5.0,
        "endTime": 10.0,
        "text": "This is very frustrating!",
        "speaker": "customer"
      }
    ],
    "duration": 10.0,
    "language": "en"
  }
}
EOF

# Send to Kafka
docker compose exec -T kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic calls.transcribed < /tmp/test_event.json

# Consume results
docker compose exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic calls.sentiment-analyzed \
  --from-beginning \
  --max-messages 1
```

### Verify Processing

After sending test event, check logs:
```bash
docker compose logs sentiment-service | grep "test-call-001"
```

You should see:
- "Received event: CallTranscribed for call test-call-001"
- "Processing sentiment analysis for call test-call-001"
- "Successfully processed call test-call-001"

## Troubleshooting

### Service Won't Start

**Issue**: Container exits immediately
```bash
# Check logs for errors
docker compose logs sentiment-service

# Common causes:
# 1. Kafka not running - start Kafka first
# 2. Port 8083 in use - change port in docker-compose.yml
# 3. Memory limit - increase Docker memory allocation
```

### Model Download Fails

**Issue**: "Failed to load RoBERTa model"
```bash
# Service will fall back to VADER automatically
# Check logs:
docker compose logs sentiment-service | grep -i "model"

# To force redownload:
docker compose down sentiment-service
docker compose build --no-cache sentiment-service
docker compose up -d sentiment-service
```

### Kafka Connection Issues

**Issue**: "Failed to initialize Kafka"
```bash
# Verify Kafka is running
docker compose ps kafka

# Check Kafka logs
docker compose logs kafka | grep -i error

# Verify topic exists
docker compose exec kafka kafka-topics --list --bootstrap-server localhost:9092

# Create topic manually if needed
docker compose exec kafka kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic calls.transcribed \
  --partitions 3 \
  --replication-factor 1
```

### High Memory Usage

**Issue**: Container using >2GB RAM

RoBERTa model requires ~1.5GB. To reduce:

Option 1: Use VADER only (edit `Dockerfile`):
```dockerfile
# Comment out transformers and torch
# RUN pip install --no-cache-dir transformers torch
```

Option 2: Limit container memory:
```yaml
# In docker-compose.yml
services:
  sentiment-service:
    deploy:
      resources:
        limits:
          memory: 2G
```

### Processing is Slow

**Issue**: >5 seconds per call

CPU processing is slower. Options:
1. Enable GPU if available (set `USE_GPU=true`)
2. Reduce segment size in transcription service
3. Use VADER instead of RoBERTa

## Performance Tuning

### For Development
```yaml
# In docker-compose.yml
environment:
  LOG_LEVEL: DEBUG  # More verbose logs
  USE_GPU: "false"
```

### For Production
```yaml
environment:
  LOG_LEVEL: WARNING  # Less verbose
  USE_GPU: "true"     # If GPU available
  KAFKA_ENABLE_AUTO_COMMIT: "false"  # Manual commit for reliability
```

### Scaling

To process more calls in parallel:

```bash
# Run multiple instances (Kafka will distribute load)
docker compose up -d --scale sentiment-service=3
```

Each instance will join the same consumer group and process different partitions.

## Integration with Other Services

### Upstream: Transcription Service
- Publishes to: `calls.transcribed`
- Ensure transcription service is running first

### Downstream: VoC Service, Audit Service, Analytics Service
- They consume from: `calls.sentiment-analyzed`
- Can run independently

### Event Flow
```
Transcription Service → calls.transcribed
                             ↓
                    Sentiment Service
                             ↓
                   calls.sentiment-analyzed
                             ↓
            ┌────────────────┼────────────────┐
            ↓                ↓                ↓
      VoC Service    Audit Service    Analytics Service
```

## Backup and Recovery

### State Management
- Service is stateless
- All state in Kafka topics
- Consumer group offset stored in Kafka

### Disaster Recovery
```bash
# Stop service
docker compose stop sentiment-service

# Service can be restarted anytime
# Will resume from last committed offset
docker compose start sentiment-service
```

### Reprocess Events
```bash
# Reset consumer group to beginning
docker compose exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group sentiment-service \
  --reset-offsets \
  --to-earliest \
  --topic calls.transcribed \
  --execute

# Restart service to reprocess
docker compose restart sentiment-service
```

## Maintenance

### Update Model
```bash
# In docker-compose.yml or .env
MODEL_NAME: cardiffnlp/new-model-name

# Rebuild and restart
docker compose up -d --build sentiment-service
```

### View Consumer Lag
```bash
docker compose exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group sentiment-service \
  --describe
```

### Clear Logs
```bash
docker compose logs sentiment-service > sentiment-service-$(date +%Y%m%d).log
```

## Security Considerations

1. **Kafka Authentication**: Not currently enabled (add SASL/SSL for production)
2. **API Security**: No authentication on health endpoints (consider adding)
3. **Network**: Service exposed on port 8083 (restrict in production)

## Next Steps

After deploying sentiment service:
1. Deploy VoC Service (consumes sentiment events)
2. Deploy Audit Service (consumes sentiment events)
3. Configure Grafana dashboards for monitoring
4. Set up alerts for escalation events
