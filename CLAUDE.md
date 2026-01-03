# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Genesis is a modern event-driven microservices platform for call auditing with Voice of the Customer (VoC) analytics. The system uses 100% free and open-source technologies, implementing event sourcing patterns across polyglot services (Java/Spring Boot + Python/FastAPI).

**Currently Implemented Services**: call-ingestion-service, transcription-service, monitor-service

## Common Commands

### Building & Running Services

**Spring Boot Services** (call-ingestion-service, monitor-service):
```bash
cd <service-directory>

# Build
./mvnw clean package

# Build without tests (faster)
./mvnw clean package -DskipTests

# Run tests
./mvnw test

# Run locally (requires infrastructure running)
./mvnw spring-boot:run

# Rebuild Docker image
docker compose build <service-name>
```

**Python Services** (transcription-service):
```bash
cd <service-directory>

# Install dependencies
pip install -r requirements.txt

# Run locally (requires infrastructure running)
uvicorn main:app --reload --port 8082

# Rebuild Docker image
docker compose build <service-name>
```

### Infrastructure Management

```bash
# Start all services
docker compose up -d

# Start only infrastructure (for local service development)
docker compose up -d kafka postgres minio

# View logs
docker compose logs -f <service-name>

# Stop all services
docker compose down

# Stop and remove volumes (clean slate)
docker compose down -v

# Rebuild and restart a service
docker compose up -d --build <service-name>
```

### Database Operations

```bash
# Initialize database schema
docker compose exec -T postgres psql -U postgres -d call_auditing < schema.sql

# Connect to database
docker compose exec postgres psql -U postgres -d call_auditing

# Drop views that block schema updates (common issue)
docker compose exec -T postgres psql -U postgres -d call_auditing <<'EOF'
DROP VIEW IF EXISTS call_summary CASCADE;
DROP VIEW IF EXISTS agent_summary CASCADE;
DROP VIEW IF EXISTS caller_summary CASCADE;
EOF
```

### MinIO Setup

```bash
# Create MinIO alias and bucket
docker compose exec minio mc alias set local http://localhost:9000 minioadmin minioadmin
docker compose exec minio mc mb local/calls
```

### Kafka Inspection

**Using monitor-service (preferred - REST API)**:
```bash
# Get first 10 messages from beginning
curl "http://localhost:8088/api/consume/calls.received/from-beginning?limit=10"

# Get 5 messages starting from offset 100
curl "http://localhost:8088/api/consume/calls.received/from-offset/100?limit=5"

# API documentation
curl http://localhost:8088/api/consume/info
```

**Using Kafka CLI tools**:
```bash
# List topics
docker compose exec kafka kafka-topics --list --bootstrap-server localhost:9092

# Consume messages from beginning
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

## Architecture

### Event-Driven Flow

The system follows an event sourcing pattern where state changes are captured as immutable events in Kafka:

```
1. Call Ingestion → publishes CallReceived (topic: calls.received)
2. Transcription → consumes CallReceived, publishes CallTranscribed (topic: calls.transcribed)
3. Future services will consume CallTranscribed for sentiment analysis, VoC, and auditing
```

### Event Schema Standard

All events follow this structure:

```json
{
  "eventId": "uuid",              // Unique event identifier
  "eventType": "CallReceived",    // Event type name
  "aggregateId": "call-id",       // Resource ID (e.g., callId)
  "aggregateType": "Call",        // Resource type
  "timestamp": "ISO-8601",
  "version": 1,
  "causationId": "uuid",          // Event that directly caused this event
  "correlationId": "uuid",        // Trace ID across entire call lifecycle
  "metadata": {
    "userId": "system",
    "service": "call-ingestion-service"
  },
  "payload": {
    // Event-specific data
  }
}
```

**Important**: Use `correlationId` to trace entire call lifecycle, `causationId` for direct cause-and-effect relationships.

### Service Communication

- **Kafka Dual Listeners**:
  - `kafka:9092` (INTERNAL) - Docker-to-Docker communication
  - `localhost:29092` (EXTERNAL) - Local IDE/development
  - Services automatically connect to the correct endpoint

- **Event-driven architecture** - services communicate asynchronously through Kafka events

### Database Architecture

- **PostgreSQL 16 with TimescaleDB** - Primary data store with time-series optimization
- **Enum Handling**: JPA entities use `@Enumerated(EnumType.STRING)` which maps to `VARCHAR(255)`, NOT PostgreSQL custom enum types
- **Event Store**: Events can be persisted to `event_store` table for audit trail

### Critical Configuration Notes

#### TimescaleDB Compatibility (Spring Boot Services)

Always include in `application.yml`:
```yaml
spring:
  datasource:
    hikari:
      connection-test-query: SELECT 1
      connection-timeout: 30000
  jpa:
    properties:
      hibernate:
        jdbc.lob.non_contextual_creation: true
        temp.use_jdbc_metadata_defaults: false  # CRITICAL for TimescaleDB
```

Without `use_jdbc_metadata_defaults: false`, you'll get `SQLSTATE(0A000)` errors.

#### Database Views Issue

Database views (`call_summary`, `agent_summary`) depend on the `calls` table. If Hibernate DDL attempts to alter columns, it will fail with "cannot alter type of a column used by a view or rule". Solution: Drop views before running application, recreate after schema stabilizes.

#### Kafka Producer Configuration (Spring Boot)

For idempotent event publishing:
```yaml
spring:
  kafka:
    producer:
      acks: all
      retries: 3
      enable.idempotence: true
```

## Service-Specific Patterns

### Spring Boot Services (call-ingestion-service, monitor-service)

**Package Structure**:
```
src/main/java/com/package/
├── config/          # @Configuration classes (KafkaConfig, MinioConfig, OpenApiConfig)
├── controller/      # @RestController - REST endpoints
├── model/           # @Entity - JPA entities
├── repository/      # Spring Data JPA repositories
├── service/         # @Service - Business logic
├── event/           # Event POJOs
└── Application.java # @SpringBootApplication
```

**Key Dependencies**:
- Spring Boot 4.0.0 (Java 21)
- Spring Kafka - event streaming
- Spring Data JPA - database
- MinIO SDK 8.6.0 - object storage (requires OkHttp3 4.12.0 explicitly)
- Springdoc OpenAPI - API documentation
- Micrometer Prometheus - metrics

**Testing**:
- Use `@SpringBootTest` with `@EmbeddedKafka` for integration tests
- Use H2 in-memory database for test isolation
- Mock external services (MinIO, etc.) with `@MockBean`

**Logging**: All services write to `./logs/<service-name>.log` with 10MB rotation, 10-day retention.

### Python Services (transcription-service)

**Module Structure**:
```
service-name/
├── main.py              # FastAPI app with @asynccontextmanager lifespan
├── config.py            # Pydantic BaseSettings
├── models/
│   ├── events.py       # Pydantic event schemas
│   └── [domain].py
├── services/
│   ├── kafka_service.py     # Producer/consumer logic
│   ├── minio_service.py     # MinIO client
│   └── [feature]_service.py
└── routers/
    └── health.py       # /health, /ready endpoints
```

**Key Dependencies**:
- FastAPI 0.110.0
- kafka-python-ng 2.2.2 (fork with better async support)
- pydantic 2.6.1
- minio 7.2.5
- openai-whisper - speech-to-text transcription
- prometheus-client 0.20.0

**Configuration**: Use Pydantic `BaseSettings` with environment variable auto-loading.

**Background Tasks**: Use FastAPI lifespan context manager for Kafka consumers:
```python
@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: start Kafka consumer thread
    kafka_service.start_consuming()
    yield
    # Shutdown: gracefully stop consumer
    kafka_service.stop_consuming()
```

**Logging**: All logs go to stdout (captured by Docker logging driver).

## Development Workflow

### Running Services Locally

1. **Start infrastructure**:
   ```bash
   docker compose up -d kafka postgres minio
   ```

2. **Initialize MinIO** (first time only):
   ```bash
   docker compose exec minio mc alias set local http://localhost:9000 minioadmin minioadmin
   docker compose exec minio mc mb local/calls
   ```

3. **Run service**:
   - Spring Boot: `./mvnw spring-boot:run` (connects to `localhost:29092`)
   - Python: `uvicorn main:app --reload --port 8082`

4. **Verify service is running**:
   - Spring Boot: `curl http://localhost:8080/actuator/health`
   - Python: `curl http://localhost:8082/ready`

### Testing Event Flow

1. **Upload a call** (generates CallReceived event):
   ```bash
   curl -X POST http://localhost:8081/api/calls/upload \
     -F "file=@test-call.wav" \
     -F "callerId=test-001" \
     -F "agentId=agent-001"
   ```

2. **Monitor Kafka events**:
   ```bash
   # Using monitor-service
   curl "http://localhost:8088/api/consume/calls.received/from-beginning?limit=1"
   curl "http://localhost:8088/api/consume/calls.transcribed/from-beginning?limit=1"
   ```

3. **Check processing status**:
   ```bash
   curl http://localhost:8081/api/calls/{callId}/status
   ```

### Observability

**Service URLs**:
| Service | URL | Purpose |
|---------|-----|---------|
| Prometheus | http://localhost:9090 | Metrics |
| Grafana | http://localhost:3000 | Dashboards (admin/admin) |
| Jaeger | http://localhost:16686 | Distributed tracing |
| MinIO Console | http://localhost:9001 | Object storage (minioadmin/minioadmin) |

**Metrics**:
- Spring Boot services: `http://localhost:<port>/actuator/prometheus`
- Python services: `http://localhost:<port>/metrics`

**Distributed Tracing**:
- All services export OpenTelemetry traces to `http://otel-collector:4318`
- View traces in Jaeger UI at http://localhost:16686

### Common Issues & Solutions

**Build Error: "cannot access okhttp3.HttpUrl"**
- MinIO SDK requires explicit OkHttp3 dependency in `pom.xml`
- Add: `com.squareup.okhttp3:okhttp:4.12.0`

**Runtime Error: "HikariPool Connection marked as broken SQLSTATE(0A000)"**
- TimescaleDB compatibility issue
- Add to `application.yml`: `hibernate.temp.use_jdbc_metadata_defaults: false`

**DDL Error: "cannot alter type of a column used by a view"**
- Database views depend on `calls` table columns
- Solution: Drop views before running application with Hibernate DDL auto-update

**Kafka Connection Refused (running locally)**
- Ensure Kafka is running: `docker compose ps kafka`
- Use `localhost:29092` for local connections (default in application.yml)

**Whisper Model Download (transcription-service first run)**
- First run downloads Whisper model (~100MB-1.5GB depending on size)
- Can take several minutes - check logs: `docker compose logs -f transcription-service`

## Testing

### Spring Boot Services

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=CallIngestionServiceTest

# Run with coverage
./mvnw test jacoco:report
```

### Python Services

```bash
# Install test dependencies
pip install pytest pytest-asyncio httpx

# Run tests (when implemented)
pytest

# Run with coverage
pytest --cov=. --cov-report=html
```

### Integration Testing

Use monitor-service to verify event flow:
1. Trigger an action (e.g., upload call)
2. Check events in Kafka: `curl "http://localhost:8088/api/consume/calls.received/from-beginning?limit=10"`
3. Verify downstream events appear in subsequent topics

## API Documentation

**Implemented Services**:

| Service | Swagger UI | OpenAPI JSON |
|---------|------------|--------------|
| Call Ingestion | http://localhost:8081/swagger-ui.html | http://localhost:8081/api-docs |
| Transcription | http://localhost:8082/docs | http://localhost:8082/openapi.json |
| Monitor | http://localhost:8088/swagger-ui.html | http://localhost:8088/api-docs |

Use Swagger UI to explore endpoints and test API calls interactively.

## Code Standards

### Java (Spring Boot)

- Use Java 21 features
- Lombok for boilerplate reduction (`@RequiredArgsConstructor`, `@Getter`, `@Setter`)
- Constructor injection for dependencies (not field injection)
- `@Transactional` for database operations
- OpenAPI annotations on controllers (`@Operation`, `@ApiResponse`)
- Follow conventional commit messages (see CONTRIBUTING.md)

### Python (FastAPI)

- Python 3.12+
- Type hints required on all functions
- Pydantic models for validation
- Async/await for I/O operations
- Follow PEP 8 style guide
- FastAPI automatic OpenAPI generation (use docstrings and type hints)

### Event Publishing

**Java**:
```java
@Service
@RequiredArgsConstructor
public class EventPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(Event event) {
        kafkaTemplate.send("topic-name", event.getAggregateId(), event);
    }
}
```

**Python**:
```python
def publish_event(producer: KafkaProducer, topic: str, event: Event):
    producer.send(
        topic,
        key=event.aggregateId.encode('utf-8'),
        value=event.model_dump_json().encode('utf-8')
    )
    producer.flush()
```

## Project-Specific Patterns

### Transcription Service - Speaker Diarization

The transcription service implements basic pause-based speaker diarization:
- Silence threshold: 1.5 seconds
- Alternates between `agent` (first speaker) and `customer`
- Works well for two-speaker conversations
- For advanced diarization, consider integrating pyannote.audio

### Whisper Model Selection

Configure in `transcription-service/config.py`:
- `tiny` (39M) - Development only, poor accuracy
- `base` (74M) - Default, good balance
- `small` (244M) - Production recommended
- `medium` (769M) - High accuracy, requires 5GB VRAM
- `large` (1550M) - Best accuracy, requires 10GB VRAM

### Monitor Service - Kafka Inspection

The monitor-service provides REST endpoints to inspect Kafka messages without affecting consumer groups:
- Creates unique consumer group per request
- Supports reading from beginning or specific offset
- Useful for debugging event flow during development

## Additional Resources

- `README.md` - Quick start and overview
- `INSTALLATION.md` - Complete installation guide (Windows & Mac)
- `QUICK_START.md` - Command reference
- `call_auditing_architecture.md` - Detailed architecture design
- `CONTRIBUTING.md` - Development workflow and standards
- `schema.sql` - Complete database schema with sample data
- Service-specific READMEs in each service directory
