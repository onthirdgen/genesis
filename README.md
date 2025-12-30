# Call Auditing Platform with Voice of the Customer

A modern, event-driven microservices platform for call auditing with Voice of the Customer (VoC) analytics. Built with Spring Boot, uses 100% free and open-source technologies.

## Architecture

- **Event Sourcing**: Apache Kafka (KRaft mode - no Zookeeper!)
- **Backend**: Spring Boot 3.2+ with Spring Cloud Gateway
- **Search**: OpenSearch 2.x (fully open-source)
- **Cache**: Valkey 7.2+ (Redis fork by Linux Foundation)
- **Database**: PostgreSQL 16 with TimescaleDB
- **Object Storage**: MinIO (S3-compatible)
- **Observability**: OpenTelemetry + Jaeger + Prometheus + Grafana
- **ML/NLP**: OpenAI Whisper v3, RoBERTa sentiment analysis

## Features

- **Call Transcription**: Convert audio to text using OpenAI Whisper
- **Sentiment Analysis**: Analyze customer emotions with RoBERTa
- **Voice of Customer**: Extract insights, themes, and actionable intelligence
- **Compliance Auditing**: Automated compliance checking and quality scoring
- **Real-time Analytics**: KPIs, trends, and dashboards
- **Event Sourcing**: Complete audit trail of all operations

## Prerequisites

- Docker Desktop (includes Docker Compose)
- Minimum: 4 CPU cores, 8GB RAM, 50GB disk
- Recommended: 8 CPU cores, 16GB RAM, GPU (for faster transcription)

**New to this project?** See the complete [Installation Guide](INSTALLATION.md) for step-by-step setup instructions (Windows & Mac).

**Already set up?** See [Quick Start Guide](QUICK_START.md) for a command reference.

## Quick Start

### 1. Install Docker Desktop

Download and install Docker Desktop from https://www.docker.com/products/docker-desktop/

Verify installation:
```bash
docker --version
docker compose version
```

### 2. Clone and Start

```bash
# Clone the repository (if from git)
# cd to the project directory

# Start all services
docker compose up -d

# View logs
docker compose logs -f

# Check service status
docker compose ps
```

### 3. Initialize Services

```bash
# Create MinIO bucket for audio files
docker compose exec minio mc alias set local http://localhost:9000 minioadmin minioadmin
docker compose exec minio mc mb local/calls

# Initialize database (when schema.sql is ready)
# docker compose exec postgres psql -U postgres -d call_auditing < schema.sql
```

### 4. Access Services

| Service | URL | Credentials |
|---------|-----|-------------|
| Call Ingestion Service | http://localhost:8081 | - |
| Transcription Service | http://localhost:8082 | - |
| Monitor Service | http://localhost:8088 | - |
| MinIO Console | http://localhost:9001 | minioadmin / minioadmin |
| Grafana | http://localhost:3000 | admin / admin |
| Jaeger UI | http://localhost:16686 | - |
| Prometheus | http://localhost:9090 | - |

## Testing the System

```bash
# Upload a test audio file
curl -X POST http://localhost:8081/api/calls/upload \
  -F "file=@test-call.wav" \
  -F "callerId=test-001" \
  -F "agentId=agent-001"

# Check processing status
curl http://localhost:8081/api/calls/{callId}/status

# Monitor Kafka events
curl "http://localhost:8088/api/consume/calls.received/from-beginning?limit=10"
curl "http://localhost:8088/api/consume/calls.transcribed/from-beginning?limit=10"
```

## Project Structure

```
.
├── docker-compose.yml                 # Main orchestration file
├── monitoring/                        # Observability configs
│   ├── prometheus.yml
│   ├── otel-collector-config.yaml
│   └── grafana/
├── call-ingestion-service/           # Upload and store audio
├── transcription-service/            # Speech-to-text (Whisper)
└── monitor-service/                  # Kafka message inspection/debugging
```

## Development

### Building a Service

Each Spring Boot service:
```bash
cd call-ingestion-service
./mvnw clean package
```

Each Python service:
```bash
cd transcription-service
pip install -r requirements.txt
uvicorn main:app --reload
```

### Running Services Locally for Debugging

Services can run in your IDE while infrastructure runs in Docker:

```bash
# Start Kafka and other infrastructure
docker compose up -d kafka postgres minio

# Run service from IDE or terminal
cd monitor-service
./mvnw spring-boot:run
```

**Kafka Dual Listeners:**
- Docker services connect to `kafka:9092`
- Local/IDE services connect to `localhost:29092`
- No configuration changes needed - automatically detected

### Inspecting Kafka Messages

The monitor-service provides REST endpoints to view Kafka messages:

```bash
# Get first 10 messages from beginning
curl "http://localhost:8088/api/consume/calls.received/from-beginning?limit=10"

# Get 5 messages starting from offset 100
curl "http://localhost:8088/api/consume/calls.received/from-offset/100?limit=5"

# API documentation
curl http://localhost:8088/api/consume/info
```

### Viewing Logs

```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f transcription-service

# Tail last 100 lines
docker compose logs --tail=100 monitor-service
```

### Stopping Services

```bash
# Stop all services
docker compose down

# Stop and remove volumes (clean slate)
docker compose down -v
```

## Monitoring

### Prometheus Metrics
- URL: http://localhost:9090
- All Spring Boot services expose metrics at `/actuator/prometheus`
- Custom application metrics available

### Grafana Dashboards
- URL: http://localhost:3000 (admin/admin)
- Pre-configured datasources: Prometheus, Jaeger
- Import dashboards or create custom ones

### Jaeger Tracing
- URL: http://localhost:16686
- Distributed tracing across all services
- OpenTelemetry instrumentation

### OpenSearch Dashboards
- URL: http://localhost:5601
- Search and analyze call transcriptions (when implemented)

## Technology Stack

### Why These Technologies?

| Component | Choice | Benefit |
|-----------|--------|---------|
| Kafka KRaft | No Zookeeper | Simpler, fewer resources |
| OpenSearch | vs Elasticsearch | No licensing concerns |
| Valkey | vs Redis | Fully open-source |
| Spring Boot | Familiar | Fast development |
| OpenTelemetry | Industry standard | Vendor-neutral |

See `MODERNIZATION_SUMMARY.md` for detailed comparison.

## Resource Usage

**Minimum**: 8GB RAM, 4 CPU cores
- Kafka: ~500MB
- Postgres: ~200MB
- OpenSearch: ~1GB
- Services: ~3-4GB total
- GPU: Optional (10x faster transcription)

## Troubleshooting

### Services won't start
```bash
# Check Docker is running
docker ps

# Check disk space
df -h

# View service logs
docker compose logs <service-name>
```

### Out of memory
```bash
# Increase Docker Desktop memory allocation
# Docker Desktop > Settings > Resources > Memory

# Or reduce OpenSearch memory
# Edit docker-compose.yml: OPENSEARCH_JAVA_OPTS=-Xms256m -Xmx256m
```

### Kafka connection issues
```bash
# Check Kafka is running
docker compose ps kafka

# Verify Kafka topics
docker compose exec kafka kafka-topics --list --bootstrap-server localhost:9092
```

### Call Ingestion Service Build/Runtime Errors

#### Build Error: "cannot access okhttp3.HttpUrl"
**Symptom**: Maven compilation fails with `class file for okhttp3.HttpUrl not found`

**Solution**: The MinIO SDK requires OkHttp3 dependency. This has been added to `call-ingestion-service/pom.xml`:
```xml
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>4.12.0</version>
</dependency>
```

#### Runtime Error: "HikariPool Connection marked as broken SQLSTATE(0A000)"
**Symptom**: Application fails to connect to PostgreSQL with "feature not supported" error

**Solution**: TimescaleDB compatibility issue. The following Hibernate properties have been configured in `application.yml`:
```yaml
spring:
  datasource:
    hikari:
      connection-test-query: SELECT 1
      connection-timeout: 30000
  jpa:
    properties:
      hibernate:
        jdbc:
          lob:
            non_contextual_creation: true
        temp:
          use_jdbc_metadata_defaults: false
```

#### DDL Error: "cannot alter type of a column used by a view or rule"
**Symptom**: Hibernate DDL fails with error about modifying columns that views depend on

**Root Cause**: Database views (`call_summary`, `agent_summary`) depend on `calls` table columns that Hibernate is trying to alter.

**Solution**: Drop the views before running the application:
```bash
docker compose exec -T postgres psql -U postgres -d call_auditing <<'EOF'
DROP VIEW IF EXISTS call_summary CASCADE;
DROP VIEW IF EXISTS agent_summary CASCADE;
DROP VIEW IF EXISTS caller_summary CASCADE;
EOF
```

The views will be recreated by the schema.sql file when needed.

**Note**: The `calls` table now uses `VARCHAR(255)` for `channel` and `status` columns instead of PostgreSQL enum types to match JPA `@Enumerated(EnumType.STRING)` mapping.

### Log Files

All Spring Boot services write logs to `./logs/` directory:
- `call-ingestion-service/logs/call-ingestion-service.log`
- Logs rotate at 10MB with 10-day retention
- Check logs when troubleshooting service-specific issues

## API Documentation

Implemented services include OpenAPI 3.0 (Swagger) documentation:

| Service | Swagger UI | OpenAPI JSON |
|---------|------------|--------------|
| Call Ingestion | http://localhost:8081/swagger-ui.html | http://localhost:8081/api-docs |
| Transcription (FastAPI) | http://localhost:8082/docs | http://localhost:8082/openapi.json |
| Monitor Service | http://localhost:8088/swagger-ui.html | http://localhost:8088/api-docs |

## Next Steps

1. **Implement Additional Services**: sentiment-service, voc-service, audit-service, analytics-service, notification-service, api-gateway
2. **Database Schema**: ~~Create PostgreSQL schema~~ ✅ **Complete**
3. **API Documentation**: ~~Add OpenAPI/Swagger to implemented services~~ ✅ **Complete**
4. **Tests**: Write unit and integration tests
5. **CI/CD**: Set up GitHub Actions for automated builds

## Documentation

- `CLAUDE.md` - **Development guidelines for AI assistants**
- `INSTALLATION.md` - **Complete installation guide (Windows & Mac)**
- `QUICK_START.md` - **Quick reference for common commands**
- `call_auditing_architecture.md` - Detailed architecture design
- `implementation_plan.md` - Implementation roadmap
- `MODERNIZATION_SUMMARY.md` - Technology choices and rationale
- `voice_to_text_costs.md` - Cost analysis and comparisons

## License

[Your License Here]

## Contributing

[Your Contributing Guidelines Here]
