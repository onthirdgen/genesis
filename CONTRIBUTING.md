# Contributing to Call Auditing Platform

Thank you for your interest in contributing to the Call Auditing Platform! This document provides guidelines and information for developers working on this project.

## Project Overview

The **Call Auditing Platform with Voice of the Customer (VoC)** is an event-driven microservices system for processing phone call recordings, extracting transcriptions, analyzing sentiment, and generating customer insights.

**Current Status**: Infrastructure complete, service implementations in progress.

**Key Technologies**:
- Backend: Spring Boot 4.0, FastAPI (Python)
- Frontend: Next.js 15, React 19, TypeScript
- Event Store: Apache Kafka (KRaft mode)
- Database: PostgreSQL 16 + TimescaleDB
- Search: OpenSearch 2.x
- Cache: Valkey 7.2+
- Storage: MinIO (S3-compatible)
- Observability: OpenTelemetry, Jaeger, Prometheus, Grafana

## Architecture Pattern: Event Sourcing

The system uses **event sourcing** as its core architectural pattern:

1. **Call Ingestion** → Stores audio in MinIO → Publishes \`CallReceived\` event to Kafka
2. **Transcription Service** → Consumes \`CallReceived\` → Transcribes → Publishes \`CallTranscribed\`
3. **Sentiment Service** → Consumes \`CallTranscribed\` → Analyzes → Publishes \`SentimentAnalyzed\`
4. **VoC Service** → Consumes events → Extracts insights → Publishes \`VoCAAnalyzed\`
5. **Audit Service** → Consumes events → Evaluates compliance → Publishes \`CallAudited\`
6. **Analytics Service** → Consumes all events → Builds materialized views

### Event Flow Principles

- All events flow through **Kafka** (topic-based pub/sub)
- Services are **stateless** - state is reconstructed from events
- No service calls another service's REST API directly (except through API Gateway for queries)
- Complete audit trail - all state changes are events
- Temporal queries possible - rebuild state at any point in time

## Development Guidelines

### Version Management Policy

**⚠️ NEVER downgrade component or library versions without explicit approval.**

When encountering version-related issues:

1. **DO NOT** automatically downgrade dependencies
2. **ALWAYS** discuss version changes before implementing
3. **EXPLAIN** why a version change might be needed and present alternatives
4. **PREFER** fixing compatibility issues without downgrading (update code, use different APIs)

**Rationale**: Older versions often contain security vulnerabilities. Version downgrades should only occur with full awareness.

### Code Standards

#### General Principles
- **KISS (Keep It Simple)**: Favor simplicity over cleverness
- **DRY (Don't Repeat Yourself)**: Extract common logic into reusable components
- **YAGNI (You Aren't Gonna Need It)**: Don't add features until they're needed
- **Single Responsibility**: Each class/function should have one clear purpose

#### Java/Spring Boot Services
- Use **constructor injection** (not field injection with \`@Autowired\`)
- Follow **REST API conventions** (proper HTTP verbs, status codes)
- Use **Lombok** to reduce boilerplate (\`@Data\`, \`@Builder\`, \`@Slf4j\`)
- Write **defensive code** - validate inputs, handle errors gracefully
- Use **meaningful names** for variables, methods, and classes
- Add **Javadoc** for public APIs and complex logic

#### Python Services
- Follow **PEP 8** style guide
- Use **type hints** for function signatures
- Write **docstrings** for all public functions
- Use **async/await** for I/O-bound operations
- Handle exceptions explicitly (don't use bare \`except:\`)

#### Frontend (Next.js/React)
- Use **TypeScript strict mode** - no \`any\` types
- Follow **React hooks** best practices
- Use **server components** by default, client components only when needed
- Write **semantic HTML** with proper accessibility
- Use **Tailwind CSS** utility classes (avoid custom CSS when possible)

### Event Schema Pattern

All events follow this structure:

\`\`\`json
{
  "eventId": "uuid",
  "eventType": "CallTranscribed",
  "aggregateId": "callId",
  "aggregateType": "Call",
  "timestamp": "ISO-8601",
  "version": 1,
  "causationId": "uuid",
  "correlationId": "uuid",
  "metadata": {
    "userId": "system",
    "service": "transcription-service"
  },
  "payload": { /* event-specific data */ }
}
\`\`\`

**Key Kafka Topics**:
- \`calls.received\` - Audio uploaded
- \`calls.transcribed\` - Transcription complete
- \`calls.sentiment-analyzed\` - Sentiment analysis done
- \`calls.voc-analyzed\` - VoC insights extracted
- \`calls.audited\` - Compliance audit complete

## Testing Requirements

### Spring Boot 4.0.0 Testing

**Critical**: Spring Boot 4.0 removed several testing annotations. Follow these guidelines:

1. **DO NOT use**: \`@WebMvcTest\`, \`@DataJpaTest\`, \`@MockBean\`, \`@AutoConfigureMockMvc\`
2. **DO use**: \`@SpringBootTest\` with manual MockMvc setup
3. **DO use**: \`@Primary\` beans with Mockito mocks instead of \`@MockBean\`
4. **ALWAYS add**: \`@ActiveProfiles("test")\` to integration tests

**Test Configuration**:
- All services MUST have \`src/test/resources/application-test.yml\` with H2 configuration
- Controller tests: Use H2 (service layer mocked)
- Repository tests: Use H2 for simple tests, Testcontainers for PostgreSQL-specific features
- Service tests: Pure unit tests with Mockito mocks

**Reference**: See \`SPRING_BOOT_4_TESTING_GUIDE.md\` for detailed testing patterns.

### Test Coverage Goals
- Unit tests: Aim for 80%+ coverage of business logic
- Integration tests: Cover critical paths and error cases
- Contract tests: Ensure event schemas match between producers/consumers

### Python Testing
- Use **pytest** with fixtures
- Mock Kafka producers/consumers in unit tests
- Use **Testcontainers** for integration tests
- Test both happy paths and error scenarios

### Frontend Testing
- Use **Vitest** for unit tests
- Use **Playwright** for E2E tests
- Test accessibility with automated tools
- Test mobile responsiveness

## Development Workflow

### Setting Up Your Environment

#### Prerequisites
- Docker Desktop (for infrastructure services)
- Java 21+ (for Spring Boot services)
- Python 3.11+ (for Python services)
- Node.js 20+ (for frontend)

#### First-Time Setup

\`\`\`bash
# Start infrastructure services
docker compose up -d kafka postgres minio opensearch valkey

# Initialize MinIO bucket
docker compose exec minio mc alias set local http://localhost:9000 minioadmin minioadmin
docker compose exec minio mc mb local/calls

# Initialize database (if schema.sql exists)
docker compose exec postgres psql -U postgres -d call_auditing < schema.sql
\`\`\`

### Running Services Locally

#### Spring Boot Service
\`\`\`bash
cd <service-directory>
./mvnw clean package
./mvnw spring-boot:run
\`\`\`

#### Python Service
\`\`\`bash
cd <service-directory>
python -m venv venv
source venv/bin/activate  # Mac/Linux
pip install -r requirements.txt
uvicorn main:app --reload
\`\`\`

#### Frontend
\`\`\`bash
cd call-auditing-ui
npm install
npm run dev
\`\`\`

### Kafka Dual Listeners for Local Development

Kafka is configured with dual listeners:
- **INTERNAL (kafka:9092)**: Used by Docker services
- **EXTERNAL (localhost:29092)**: Used by local/IDE development

When running services locally, they automatically connect to \`localhost:29092\`.

### Implementing a New Service

When creating a new service:

1. **Add dependencies** (Spring Boot):
   - spring-boot-starter-web
   - spring-boot-starter-actuator
   - spring-kafka or spring-cloud-stream-binder-kafka
   - postgresql driver, lombok, micrometer-registry-prometheus

2. **Configuration** (application.yml):
   \`\`\`yaml
   spring:
     kafka:
       bootstrap-servers: \${KAFKA_BOOTSTRAP_SERVERS:localhost:29092}
     datasource:
       url: \${SPRING_DATASOURCE_URL}

   management:
     endpoints:
       web:
         exposure:
           include: health,prometheus
   \`\`\`

3. **Kafka Consumer** example:
   \`\`\`java
   @KafkaListener(topics = "calls.received", groupId = "transcription-service")
   public void handleCallReceived(String event) {
       // Process event, publish new event
   }
   \`\`\`

4. **OpenTelemetry**: Already configured via Java Agent in Dockerfile

### Debugging and Monitoring

#### Viewing Logs
\`\`\`bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f transcription-service
\`\`\`

#### Inspecting Kafka Messages
Use the monitor-service REST endpoints:
\`\`\`bash
# Get messages from beginning
curl "http://localhost:8088/api/consume/calls.received/from-beginning?limit=10"

# Get messages from specific offset
curl "http://localhost:8088/api/consume/calls.received/from-offset/100?limit=5"
\`\`\`

#### Accessing MinIO
- Console: http://localhost:9001 (minioadmin/minioadmin)
- Files stored in \`calls\` bucket by call ID

#### Observability Tools
- **Grafana**: http://localhost:3000 (admin/admin) - Dashboards
- **Jaeger**: http://localhost:16686 - Distributed tracing
- **Prometheus**: http://localhost:9090 - Metrics queries
- **OpenSearch Dashboards**: http://localhost:5601 - Search analytics

### Commit Guidelines

Use **conventional commits** format:

\`\`\`
<type>(<scope>): <subject>

<body>

<footer>
\`\`\`

**Types**:
- \`feat\`: New feature
- \`fix\`: Bug fix
- \`docs\`: Documentation changes
- \`refactor\`: Code refactoring
- \`test\`: Adding/updating tests
- \`chore\`: Maintenance tasks

**Examples**:
\`\`\`
feat(transcription): add speaker diarization support

Implement speaker identification using pyannote.audio.
Segments now include speakerId field.

Closes #42
\`\`\`

\`\`\`
fix(call-ingestion): validate audio file format before upload

Add validation for WAV, MP3, M4A formats.
Return 400 Bad Request for unsupported formats.
\`\`\`

## Database Schema

Uses **PostgreSQL 16** with **TimescaleDB** extension for time-series data.

### Key Tables
- \`calls\` - Call metadata
- \`transcriptions\` - Full transcription text
- \`segments\` - Speaker-separated segments
- \`sentiment_results\` - Overall sentiment per call
- \`voc_insights\` - Extracted themes and keywords
- \`audit_results\` - Compliance outcomes
- \`event_store\` - Event sourcing audit trail

### TimescaleDB Hypertables
- \`agent_performance\` - Agent metrics over time
- \`compliance_metrics\` - Daily compliance rates
- \`sentiment_trends\` - Sentiment trends

See \`schema.sql\` for complete schema definition.

## Service Ports

| Service | Port | Type |
|---------|------|------|
| api-gateway | 8080 | Spring Cloud Gateway |
| call-ingestion-service | 8081 | Spring Boot |
| transcription-service | 8082 | FastAPI |
| sentiment-service | 8083 | FastAPI |
| voc-service | 8084 | Spring Boot |
| audit-service | 8085 | Spring Boot |
| analytics-service | 8086 | Spring Boot |
| notification-service | 8087 | Spring Boot |
| monitor-service | 8088 | Spring Boot |

## Common Issues and Solutions

### Port Conflicts
If port 3000 is already in use (Grafana):
\`\`\`bash
# Option 1: Stop Grafana
docker compose stop grafana

# Option 2: Run frontend on different port
npm run dev -- -p 3001
\`\`\`

### Out of Memory
Increase Docker Desktop memory allocation:
- Docker Desktop > Settings > Resources > Memory
- Recommended: 8GB minimum, 16GB for optimal performance

### Kafka Connection Issues
\`\`\`bash
# Check Kafka is running
docker compose ps kafka

# List topics
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092
\`\`\`

### TypeScript Errors (Frontend)
\`\`\`bash
# Clear Next.js cache
rm -rf .next

# Reinstall dependencies
rm -rf node_modules
npm install

# Run type check
npm run type-check
\`\`\`

## Documentation

### Key Documentation Files
- \`README.md\` - Project overview and quick start
- \`ARCHITECTURE.md\` - Detailed system architecture
- \`INSTALLATION.md\` - Complete installation guide
- \`QUICK_START.md\` - Quick reference for common commands
- \`MODERNIZATION_SUMMARY.md\` - Technology choices and rationale
- \`SPRING_BOOT_4_TESTING_GUIDE.md\` - Testing patterns for Spring Boot 4

### API Documentation
All services include OpenAPI/Swagger documentation:
- API Gateway: http://localhost:8080/swagger-ui.html
- Spring Boot services: http://localhost:808X/swagger-ui.html
- FastAPI services: http://localhost:808X/docs

## Getting Help

- **Architecture questions**: See \`call_auditing_architecture.md\`
- **Troubleshooting**: See \`INSTALLATION.md#troubleshooting\`
- **Testing patterns**: See \`SPRING_BOOT_4_TESTING_GUIDE.md\`
- **Technology rationale**: See \`MODERNIZATION_SUMMARY.md\`

## Project Conventions

- **Use UUIDs for call IDs** (not sequential integers)
- **Audio files in MinIO**: Store as \`{year}/{month}/{callId}.{extension}\`
- **Correlation IDs**: Pass through all events for the same call (enables trace linking)
- **Idempotency**: Services must handle duplicate Kafka messages gracefully
- **Error handling**: Use proper HTTP status codes and error messages
- **Logging**: Use structured logging with correlation IDs

## What's Not Yet Implemented

This is a **prototype/skeleton project**. The following are in progress:

- Service business logic (partially implemented)
- Comprehensive unit/integration tests (in progress)
- CI/CD pipelines
- Service-to-service authentication
- Production deployment configurations

**What IS implemented**:
- ✅ Docker Compose orchestration (16 services)
- ✅ Infrastructure services (Kafka, PostgreSQL, MinIO, OpenSearch, Valkey)
- ✅ Monitoring stack (Prometheus, Grafana, Jaeger, OpenTelemetry)
- ✅ Database schema (TimescaleDB support)
- ✅ API documentation (OpenAPI/Swagger)
- ✅ Frontend foundation (Next.js with authentication)

## Contributing

We welcome contributions! Please:

1. **Fork** the repository
2. **Create** a feature branch (\`git checkout -b feature/amazing-feature\`)
3. **Commit** your changes using conventional commits
4. **Test** your changes thoroughly
5. **Push** to the branch (\`git push origin feature/amazing-feature\`)
6. **Open** a Pull Request

### Pull Request Guidelines

- Include a clear description of changes
- Reference any related issues
- Ensure all tests pass
- Update documentation if needed
- Follow the code standards outlined above

## License

[Your License Here]

---

**Last Updated**: 2026-01-01
