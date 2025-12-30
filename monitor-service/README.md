# Consumer Service

A utility service for consuming and inspecting Kafka messages. Useful for debugging, development, and message inspection.

## Overview

The consumer-service provides REST API endpoints to read messages from any Kafka topic, supporting both:
- Reading from the beginning of a topic
- Reading from a specific offset

This is particularly useful during development to:
- Verify events are being published correctly
- Debug event payloads and metadata
- Inspect message ordering and partitioning
- Validate event schemas

## Running the Service

### In Docker (Production-like)

```bash
cd ..
docker compose up -d consumer-service
```

The service will be available at http://localhost:8088

### Locally in IDE (For Debugging)

```bash
# Ensure Kafka is running in Docker
cd ..
docker compose up -d kafka

# Run the service
cd consumer-service
./mvnw spring-boot:run
```

The service automatically connects to:
- `localhost:29092` when running locally
- `kafka:9092` when running in Docker

## API Endpoints

### 1. Consume from Beginning

Reads messages from the start of a topic.

**Endpoint:**
```
GET /api/consume/{topic}/from-beginning
```

**Parameters:**
- `limit` (query, optional): Maximum number of messages to retrieve (default: 1)
- `timeout` (query, optional): Timeout in milliseconds (default: 30000)

**Example:**
```bash
# Get first message
curl "http://localhost:8088/api/consume/calls.received/from-beginning"

# Get first 10 messages
curl "http://localhost:8088/api/consume/calls.received/from-beginning?limit=10"

# Get first 50 messages with 60-second timeout
curl "http://localhost:8088/api/consume/calls.received/from-beginning?limit=50&timeout=60000"
```

### 2. Consume from Specific Offset

Reads messages starting from a specific offset position.

**Endpoint:**
```
GET /api/consume/{topic}/from-offset/{offset}
```

**Parameters:**
- `offset` (path, required): Starting offset position
- `limit` (query, optional): Maximum number of messages to retrieve (default: 1)
- `timeout` (query, optional): Timeout in milliseconds (default: 30000)

**Example:**
```bash
# Get 1 message from offset 100
curl "http://localhost:8088/api/consume/calls.received/from-offset/100"

# Get 5 messages starting from offset 100
curl "http://localhost:8088/api/consume/calls.received/from-offset/100?limit=5"

# Get 10 messages from offset 0 with custom timeout
curl "http://localhost:8088/api/consume/calls.transcribed/from-offset/0?limit=10&timeout=45000"
```

### 3. API Information

Returns API documentation and examples.

**Endpoint:**
```
GET /api/consume/info
```

**Example:**
```bash
curl http://localhost:8088/api/consume/info
```

## Response Format

All consumption endpoints return the same response structure:

```json
{
  "topic": "calls.received",
  "startPosition": "beginning",
  "limit": 10,
  "timeout": 30000,
  "messagesRetrieved": 10,
  "messages": [
    {
      "offset": 0,
      "partition": 0,
      "timestamp": 1704067200000,
      "key": "call-123",
      "value": "{\"eventId\":\"abc-123\",\"eventType\":\"CallReceived\"}",
      "topic": "calls.received"
    }
  ]
}
```

**Response Fields:**
- `topic`: The Kafka topic name
- `startPosition`: Where reading started ("beginning" or "offset-N")
- `limit`: Maximum messages requested
- `timeout`: Timeout used in milliseconds
- `messagesRetrieved`: Actual number of messages returned
- `messages`: Array of Kafka message objects

**Message Fields:**
- `offset`: Message offset in the partition
- `partition`: Partition number
- `timestamp`: Message timestamp (epoch milliseconds)
- `key`: Message key (null if not set)
- `value`: Message payload as string
- `topic`: Topic name

## Available Topics

The following topics are used in the call auditing platform:

| Topic | Description |
|-------|-------------|
| `calls.received` | Call ingestion events |
| `calls.transcribed` | Transcription completion events |
| `calls.sentiment-analyzed` | Sentiment analysis results |
| `calls.voc-analyzed` | VoC insights extraction results |
| `calls.audited` | Compliance audit results |
| `test.publish` | Test topic for development |

## Configuration

### application.yml

```yaml
server:
  port: 8088

spring:
  kafka:
    # Automatically uses localhost:29092 for local dev, kafka:9092 in Docker
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:29092}

kafka:
  consumer:
    default-limit: 1        # Default message limit
    poll-timeout: 30000     # Default timeout in ms
```

### Customizing Defaults

You can override defaults via environment variables:

```bash
# Override bootstrap servers
KAFKA_BOOTSTRAP_SERVERS=localhost:29092 ./mvnw spring-boot:run

# Override default limit and timeout
# (These are currently only configurable in application.yml)
```

## Development

### Building

```bash
./mvnw clean package
```

### Running Tests

```bash
./mvnw test
```

### Building Docker Image

```bash
cd ..
docker compose build consumer-service
```

## Architecture Notes

### Kafka Configuration

The service creates a **unique consumer group** for each API request:
```java
"consumer-service-api-" + UUID.randomUUID()
```

This ensures that:
- API calls don't interfere with persistent consumers
- Each request reads independently
- No offset commits affect other services

### Dual Listener Support

The service leverages Kafka's dual listener configuration:

- **INTERNAL listener (kafka:9092)**: For Docker-based services
- **EXTERNAL listener (localhost:29092)**: For local/IDE development

This allows seamless debugging without Docker networking complexity.

### Performance Considerations

- Default limit of 1 message prevents accidental large reads
- Configurable timeout prevents indefinite blocking
- Efficient polling with early exit when no more messages available
- Consumer is closed after each request to free resources

## Troubleshooting

### "Connection refused" when running locally

Ensure Kafka is running and the external listener is configured:

```bash
# Check Kafka is running
docker compose ps kafka

# Check port 29092 is listening
lsof -i :29092

# Restart Kafka if needed
docker compose restart kafka
```

### "Topic not found" error

The topic doesn't exist yet. Create it or wait for a producer to create it automatically:

```bash
# List existing topics
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092

# Create topic manually
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --create \
  --topic test-topic \
  --partitions 3 \
  --replication-factor 1 \
  --bootstrap-server localhost:9092
```

### "UnknownHostException: kafka" when running in Docker

This is expected if you're running locally outside Docker. The service is trying to resolve the Docker hostname "kafka". Use `localhost:29092` instead (which is the default).

### Timeout with no messages returned

The topic exists but has no messages, or the offset is beyond the end of the topic:

```bash
# Check topic has messages
docker compose exec kafka /opt/kafka/bin/kafka-run-class.sh kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 \
  --topic calls.received
```

## License

Part of the Call Auditing Platform with Voice of the Customer project.
