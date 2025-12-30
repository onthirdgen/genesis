# Call Ingestion Service

Audio file upload and storage service for the Call Auditing Platform.

## Overview

The Call Ingestion Service handles:
- Audio file uploads (WAV, MP3, M4A formats)
- Storage in MinIO (S3-compatible object storage)
- Publishing `CallReceived` events to Kafka
- Call metadata persistence in PostgreSQL

## Technology Stack

- **Framework**: Spring Boot 4.0.0
- **Language**: Java 21
- **Database**: PostgreSQL 16 + TimescaleDB
- **Object Storage**: MinIO
- **Messaging**: Apache Kafka
- **API Documentation**: OpenAPI 3.0 / Swagger

## Prerequisites

- Java 21
- Maven 3.9+
- Docker (for local infrastructure)

## Configuration

### Application Properties

The service is configured via `src/main/resources/application.yml`:

#### Server Configuration
```yaml
server:
  port: 8080
```

#### Database Configuration
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/call_auditing
    username: postgres
    password: postgres
    hikari:
      connection-test-query: SELECT 1
      connection-timeout: 30000
      maximum-pool-size: 10

  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        jdbc:
          lob:
            non_contextual_creation: true
        temp:
          use_jdbc_metadata_defaults: false  # Critical for TimescaleDB compatibility
```

**Important Notes:**
- `use_jdbc_metadata_defaults: false` is required to prevent SQLSTATE(0A000) errors with TimescaleDB
- HikariCP uses a simple `SELECT 1` test query for connection validation

#### MinIO Configuration
```yaml
minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
  bucket-name: calls
```

#### Kafka Configuration
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

#### Logging Configuration
```yaml
logging:
  level:
    com.callaudit: DEBUG
    org.springframework.kafka: INFO
  file:
    name: ./logs/call-ingestion-service.log
  logback:
    rollingpolicy:
      max-file-size: 10MB
      max-history: 10
      total-size-cap: 100MB
```

Logs are written to `./logs/call-ingestion-service.log` with automatic rotation.

## Dependencies

### Key Dependencies (pom.xml)

```xml
<!-- Spring Boot Core -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- MinIO SDK -->
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>8.6.0</version>
</dependency>

<!-- OkHttp3 (required by MinIO) -->
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>4.12.0</version>
</dependency>

<!-- Spring Kafka -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>

<!-- PostgreSQL Driver -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>
```

**Note**: The OkHttp3 dependency is explicitly required - MinIO SDK doesn't pull it as a transitive dependency in all configurations.

## Database Schema

### Call Entity

The `Call` entity uses `VARCHAR(255)` for enum fields to match JPA `@Enumerated(EnumType.STRING)`:

```java
@Entity
@Table(name = "calls")
public class Call {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Enumerated(EnumType.STRING)
    private CallChannel channel;  // Stored as VARCHAR(255)

    @Enumerated(EnumType.STRING)
    private CallStatus status;    // Stored as VARCHAR(255)

    // Other fields...
}
```

**Important**: Do NOT use PostgreSQL custom enum types (`call_channel`, `call_status`) for these columns. Use VARCHAR instead.

## Building

```bash
# Clean and package
./mvnw clean package

# Skip tests
./mvnw clean package -DskipTests

# Run tests only
./mvnw test
```

## Running Locally

### 1. Start Infrastructure

```bash
cd /Users/jon/AI/genesis
docker compose up -d postgres kafka minio
```

### 2. Initialize MinIO Bucket

```bash
docker compose exec minio mc alias set local http://localhost:9000 minioadmin minioadmin
docker compose exec minio mc mb local/calls
```

### 3. Run the Application

```bash
./mvnw spring-boot:run
```

The service will start on port 8080.

## API Endpoints

### Swagger UI
- http://localhost:8080/swagger-ui.html

### OpenAPI Specification
- http://localhost:8080/api-docs

### Health Check
- http://localhost:8080/actuator/health

### Prometheus Metrics
- http://localhost:8080/actuator/prometheus

## Docker

### Build Image

```bash
docker compose build call-ingestion-service
```

### Run Container

```bash
docker compose up -d call-ingestion-service
```

The service runs on port 8081 when deployed via Docker Compose (mapped from internal 8080).

## Troubleshooting

### Build Error: "cannot access okhttp3.HttpUrl"

**Solution**: Ensure the OkHttp3 dependency is in `pom.xml`:
```xml
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>4.12.0</version>
</dependency>
```

### Runtime Error: "HikariPool Connection marked as broken SQLSTATE(0A000)"

**Solution**: Verify `application.yml` has the TimescaleDB compatibility settings:
```yaml
hibernate.temp.use_jdbc_metadata_defaults: false
```

### DDL Error: "cannot alter type of a column used by a view or rule"

**Solution**: Drop blocking database views:
```bash
docker compose exec -T postgres psql -U postgres -d call_auditing <<'EOF'
DROP VIEW IF EXISTS call_summary CASCADE;
DROP VIEW IF EXISTS agent_summary CASCADE;
EOF
```

### Logs

Check application logs:
```bash
# Local development
tail -f ./logs/call-ingestion-service.log

# Docker container
docker compose logs -f call-ingestion-service
```

## Development

### Project Structure

```
call-ingestion-service/
├── src/
│   ├── main/
│   │   ├── java/com/callaudit/ingestion/
│   │   │   ├── config/          # Configuration classes
│   │   │   ├── controller/      # REST controllers
│   │   │   ├── model/           # JPA entities
│   │   │   ├── repository/      # Spring Data repositories
│   │   │   ├── service/         # Business logic
│   │   │   └── CallIngestionApplication.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/                    # Unit and integration tests
├── logs/                        # Application logs (auto-created)
├── Dockerfile
├── pom.xml
└── README.md
```

### Adding New Features

1. Define JPA entities in `model/`
2. Create repositories in `repository/`
3. Implement business logic in `service/`
4. Expose REST endpoints in `controller/`
5. Update OpenAPI documentation with `@Operation`, `@ApiResponse` annotations

## Integration with Other Services

### Kafka Events Published

**Topic**: `calls.received`

**Event Schema**:
```json
{
  "eventId": "uuid",
  "eventType": "CallReceived",
  "aggregateId": "callId",
  "aggregateType": "Call",
  "timestamp": "ISO-8601",
  "version": 1,
  "correlationId": "uuid",
  "metadata": {
    "service": "call-ingestion-service"
  },
  "payload": {
    "callId": "uuid",
    "audioFileUrl": "string",
    "callerId": "string",
    "agentId": "string",
    "channel": "INBOUND|OUTBOUND|INTERNAL"
  }
}
```

## License

[Your License Here]