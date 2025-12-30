# Docker Compose Setup - Complete Project Structure

## Overview

This document describes the complete project structure that has been created for the Call Auditing Platform with Voice of the Customer features.

## 📦 Files Created

### Core Configuration Files

#### `docker-compose.yml`
Complete orchestration file with 16 services:
- **Infrastructure**: Kafka (KRaft), PostgreSQL, MinIO, OpenSearch, Valkey
- **Microservices**: 8 application services
- **Observability**: Prometheus, Grafana, Jaeger, OpenTelemetry Collector
- **UI Tools**: OpenSearch Dashboards, MinIO Console

#### `README.md`
Comprehensive project documentation including:
- Architecture overview
- Quick start guide
- Service access URLs
- Testing instructions
- Troubleshooting tips

#### `.gitignore`
Comprehensive ignore rules for:
- Java/Maven artifacts
- Python cache files
- IDE configurations
- Docker volumes
- Audio files
- ML model caches

#### `.env.example`
Environment variable template for:
- Database credentials
- MinIO configuration
- Grafana credentials
- Whisper model size
- OpenTelemetry settings

#### `CONTRIBUTING.md`
Development guidelines covering:
- Development setup
- Code standards
- Testing procedures
- Commit message conventions
- Pull request process

### Monitoring Configuration

#### `monitoring/prometheus.yml`
Prometheus scraping configuration for:
- All Spring Boot services (via `/actuator/prometheus`)
- Python services (FastAPI metrics)
- OpenSearch metrics
- OpenTelemetry Collector metrics

#### `monitoring/otel-collector-config.yaml`
OpenTelemetry Collector configuration:
- **Receivers**: OTLP (gRPC and HTTP)
- **Processors**: Batch processing, memory limiting
- **Exporters**: Jaeger (traces), Prometheus (metrics)
- **Pipelines**: Separate traces and metrics pipelines

#### `monitoring/grafana/datasources/datasources.yml`
Auto-configured Grafana datasources:
- Prometheus (default datasource)
- Jaeger (for distributed tracing)

### Microservices Structure

#### Spring Boot Services (6 services)

Each with identical Dockerfile structure:
- Multi-stage build (build + runtime)
- OpenTelemetry Java Agent auto-instrumentation
- Java 21 runtime
- Maven build process

**Services**:
1. `call-ingestion-service/` - Upload and store audio files
2. `voc-service/` - Voice of Customer analytics
3. `audit-service/` - Compliance auditing
4. `analytics-service/` - Metrics and KPIs
5. `notification-service/` - Alerts and notifications
6. `api-gateway/` - Spring Cloud Gateway

#### Python Services (2 services)

**1. `transcription-service/`**
- Dockerfile with Python 3.12
- FFmpeg for audio processing
- OpenAI Whisper installation
- FastAPI for REST API
- `requirements.txt` with dependencies:
  - fastapi, uvicorn
  - kafka-python
  - minio
  - openai-whisper

**2. `sentiment-service/`**
- Dockerfile with Python 3.12
- Transformers and PyTorch
- VADER sentiment analysis
- FastAPI for REST API
- `requirements.txt` with dependencies:
  - fastapi, uvicorn
  - kafka-python
  - transformers
  - torch
  - vaderSentiment

## 📁 Complete Project Structure

```
call-auditing-platform/
├── docker-compose.yml                    # Main orchestration file
├── README.md                             # Project documentation
├── CONTRIBUTING.md                       # Development guidelines
├── .gitignore                            # Git ignore rules
├── .env.example                          # Environment variables template
│
├── call_auditing_architecture.md        # Detailed architecture docs
├── MODERNIZATION_SUMMARY.md             # Technology rationale
├── voice_to_text_costs.md               # Cost analysis
│
├── monitoring/                           # Observability configuration
│   ├── prometheus.yml                    # Prometheus scraping config
│   ├── otel-collector-config.yaml        # OpenTelemetry config
│   └── grafana/
│       ├── dashboards/                   # (Ready for custom dashboards)
│       └── datasources/
│           └── datasources.yml           # Auto-configured datasources
│
├── call-ingestion-service/               # Spring Boot - Audio upload
│   └── Dockerfile                        # Multi-stage build with OTel
│
├── transcription-service/                # Python/FastAPI - Whisper
│   ├── Dockerfile                        # Python 3.12 + FFmpeg
│   └── requirements.txt                  # Python dependencies
│
├── sentiment-service/                    # Python/FastAPI - Sentiment
│   ├── Dockerfile                        # Python 3.12 + ML libs
│   └── requirements.txt                  # Python dependencies
│
├── voc-service/                          # Spring Boot - VoC analytics
│   └── Dockerfile                        # Multi-stage build with OTel
│
├── audit-service/                        # Spring Boot - Compliance
│   └── Dockerfile                        # Multi-stage build with OTel
│
├── analytics-service/                    # Spring Boot - Metrics
│   └── Dockerfile                        # Multi-stage build with OTel
│
├── notification-service/                 # Spring Boot - Alerts
│   └── Dockerfile                        # Multi-stage build with OTel
│
└── api-gateway/                          # Spring Cloud Gateway
    └── Dockerfile                        # Multi-stage build with OTel
```

## 🚀 Installation & Setup

### Prerequisites

1. **Docker Desktop**
   - Download: https://www.docker.com/products/docker-desktop/
   - Includes: Docker Engine, Docker Compose
   - Minimum: 8GB RAM allocated to Docker
   - Recommended: 12-16GB RAM

2. **System Requirements**
   - CPU: 4 cores minimum, 8 cores recommended
   - RAM: 8GB minimum, 16GB recommended
   - Disk: 50GB free space
   - GPU: Optional (for faster transcription)

### Verify Installation

```bash
# Check Docker
docker --version
# Expected: Docker version 24.0.0 or higher

# Check Docker Compose
docker compose version
# Expected: Docker Compose version v2.20.0 or higher

# Check Docker is running
docker ps
# Should show empty list (no errors)
```

## 🔧 First-Time Setup

### Step 1: Start Infrastructure Services

Start core infrastructure first (Kafka, databases, storage):

```bash
# Start infrastructure only
docker compose up -d kafka postgres minio opensearch valkey

# Check status (wait until all are "healthy" or "running")
docker compose ps

# View logs if needed
docker compose logs -f kafka postgres
```

### Step 2: Initialize MinIO

Create the bucket for storing audio files:

```bash
# Set up MinIO CLI alias
docker compose exec minio mc alias set local http://localhost:9000 minioadmin minioadmin

# Create bucket for audio files
docker compose exec minio mc mb local/calls

# Verify bucket was created
docker compose exec minio mc ls local/
```

### Step 3: Initialize Database (When Ready)

After implementing your database schema:

```bash
# Copy your schema file to container
docker compose cp schema.sql postgres:/tmp/schema.sql

# Execute schema
docker compose exec postgres psql -U postgres -d call_auditing -f /tmp/schema.sql

# Verify tables
docker compose exec postgres psql -U postgres -d call_auditing -c "\dt"
```

### Step 4: Start Observability Stack

```bash
# Start monitoring services
docker compose up -d prometheus grafana jaeger otel-collector opensearch-dashboards

# Verify services
docker compose ps prometheus grafana jaeger
```

## 📊 Service Access

After services are running, access them at:

| Service | URL | Credentials | Purpose |
|---------|-----|-------------|---------|
| **API Gateway** | http://localhost:8080 | - | Main application entry point |
| **MinIO Console** | http://localhost:9001 | minioadmin / minioadmin | Object storage management |
| **Grafana** | http://localhost:3000 | admin / admin | Metrics dashboards |
| **Jaeger UI** | http://localhost:16686 | - | Distributed tracing |
| **OpenSearch Dashboards** | http://localhost:5601 | - | Search and analytics |
| **Prometheus** | http://localhost:9090 | - | Metrics database |

### Individual Service Ports

| Service | Port | Technology |
|---------|------|------------|
| call-ingestion-service | 8081 | Spring Boot |
| transcription-service | 8082 | Python/FastAPI |
| sentiment-service | 8083 | Python/FastAPI |
| voc-service | 8084 | Spring Boot |
| audit-service | 8085 | Spring Boot |
| analytics-service | 8086 | Spring Boot |
| notification-service | 8087 | Spring Boot |
| api-gateway | 8080 | Spring Cloud Gateway |

## 🧪 Testing the Setup

### Test Infrastructure Services

```bash
# Test Kafka
docker compose exec kafka kafka-topics --list --bootstrap-server localhost:9092

# Test PostgreSQL
docker compose exec postgres psql -U postgres -c "SELECT version();"

# Test MinIO
docker compose exec minio mc ls local/

# Test OpenSearch
curl http://localhost:9200

# Test Valkey
docker compose exec valkey valkey-cli PING
```

### Test Observability Stack

```bash
# Test Prometheus targets
curl http://localhost:9090/api/v1/targets

# Access Grafana
open http://localhost:3000

# Access Jaeger
open http://localhost:16686

# Test OpenTelemetry Collector
curl http://localhost:8888/metrics
```

## 🛠️ Development Workflow

### Building Services

When you implement a service, you'll need to:

#### Spring Boot Services

1. **Initialize Project**
   ```bash
   cd call-ingestion-service

   # Using Spring Initializr CLI
   spring init \
     --dependencies=web,kafka,postgresql,actuator,lombok \
     --build=maven \
     --java-version=21 \
     --type=maven-project \
     .
   ```

2. **Build**
   ```bash
   ./mvnw clean package
   ```

3. **Run Locally** (for development)
   ```bash
   ./mvnw spring-boot:run
   ```

4. **Build Docker Image**
   ```bash
   docker compose build call-ingestion-service
   ```

#### Python Services

1. **Set Up Virtual Environment**
   ```bash
   cd transcription-service
   python -m venv venv
   source venv/bin/activate  # Linux/Mac
   # or
   venv\Scripts\activate  # Windows
   ```

2. **Install Dependencies**
   ```bash
   pip install -r requirements.txt
   ```

3. **Run Locally** (for development)
   ```bash
   uvicorn main:app --reload
   ```

4. **Build Docker Image**
   ```bash
   docker compose build transcription-service
   ```

### Starting Individual Services

```bash
# Start a specific service
docker compose up -d call-ingestion-service

# View logs
docker compose logs -f call-ingestion-service

# Restart after code changes
docker compose restart call-ingestion-service

# Rebuild and start
docker compose up -d --build call-ingestion-service
```

### Starting All Application Services

After implementing all services:

```bash
# Build all services
docker compose build

# Start everything
docker compose up -d

# View all logs
docker compose logs -f

# View specific service logs
docker compose logs -f transcription-service voc-service
```

## 🔍 Monitoring & Debugging

### View Service Logs

```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f transcription-service

# Last 100 lines
docker compose logs --tail=100 voc-service

# Follow logs from specific timestamp
docker compose logs --since 2024-01-01T00:00:00
```

### Check Service Health

```bash
# List all services
docker compose ps

# Check specific service
docker compose ps kafka

# Inspect service
docker compose inspect call-ingestion-service
```

### Access Service Shells

```bash
# Spring Boot service
docker compose exec call-ingestion-service bash

# Python service
docker compose exec transcription-service sh

# Database
docker compose exec postgres psql -U postgres -d call_auditing

# Kafka
docker compose exec kafka bash
```

### Monitor Resources

```bash
# View resource usage
docker stats

# View specific services
docker stats call-ingestion-service transcription-service
```

## 🐛 Troubleshooting

### Services Won't Start

**Problem**: `docker compose up` fails

**Solutions**:
```bash
# Check Docker is running
docker ps

# Check disk space
df -h

# Check Docker Desktop memory allocation
# Settings > Resources > Memory (increase to 8GB+)

# View service logs for errors
docker compose logs <service-name>

# Remove old containers and volumes
docker compose down -v
docker compose up -d
```

### Out of Memory

**Problem**: Services crash or are slow

**Solutions**:
```bash
# Increase Docker Desktop memory
# Docker Desktop > Settings > Resources > Memory

# Reduce OpenSearch memory
# Edit docker-compose.yml:
# OPENSEARCH_JAVA_OPTS=-Xms256m -Xmx256m

# Start services gradually
docker compose up -d kafka postgres minio
# Wait 30 seconds
docker compose up -d opensearch valkey
# Wait 30 seconds
docker compose up -d <application-services>
```

### Kafka Connection Issues

**Problem**: Services can't connect to Kafka

**Solutions**:
```bash
# Verify Kafka is running
docker compose ps kafka

# Check Kafka logs
docker compose logs kafka

# List Kafka topics
docker compose exec kafka kafka-topics --list --bootstrap-server localhost:9092

# Restart Kafka
docker compose restart kafka
```

### Database Connection Issues

**Problem**: Services can't connect to PostgreSQL

**Solutions**:
```bash
# Verify PostgreSQL is running
docker compose ps postgres

# Check PostgreSQL logs
docker compose logs postgres

# Test connection
docker compose exec postgres psql -U postgres -c "SELECT 1;"

# Check database exists
docker compose exec postgres psql -U postgres -l
```

### MinIO Issues

**Problem**: Can't upload files or access MinIO

**Solutions**:
```bash
# Verify MinIO is running
docker compose ps minio

# Access MinIO console
open http://localhost:9001

# Check buckets
docker compose exec minio mc ls local/

# Recreate bucket
docker compose exec minio mc mb --ignore-existing local/calls
```

## 📝 Next Steps for Implementation

### 1. Implement Call Ingestion Service

**Create Spring Boot application**:
```bash
cd call-ingestion-service

# Create basic structure
mkdir -p src/main/java/com/callaudit/ingestion
mkdir -p src/main/resources
mkdir -p src/test/java/com/callaudit/ingestion
```

**Required features**:
- REST endpoint: `POST /api/calls/upload`
- Store audio in MinIO
- Publish `CallReceived` event to Kafka
- Return call ID to client

### 2. Implement Transcription Service

**Create FastAPI application**:
```bash
cd transcription-service

# Create main.py
touch main.py
```

**Required features**:
- Consume `CallReceived` events from Kafka
- Download audio from MinIO
- Transcribe with Whisper
- Publish `CallTranscribed` event

### 3. Implement Remaining Services

Follow similar patterns for:
- sentiment-service (consume CallTranscribed, publish SentimentAnalyzed)
- voc-service (consume events, extract insights)
- audit-service (consume events, evaluate compliance)
- analytics-service (aggregate metrics)
- notification-service (send alerts)
- api-gateway (route requests to services)

### 4. Create Database Schema

```sql
-- Example schema structure
CREATE TABLE calls (
    id UUID PRIMARY KEY,
    caller_id VARCHAR(255),
    agent_id VARCHAR(255),
    duration INTEGER,
    audio_url TEXT,
    created_at TIMESTAMP
);

CREATE TABLE transcriptions (
    id UUID PRIMARY KEY,
    call_id UUID REFERENCES calls(id),
    full_text TEXT,
    language VARCHAR(10),
    confidence FLOAT,
    created_at TIMESTAMP
);

-- Add more tables as needed
```

### 5. Add Tests

**Spring Boot**:
```bash
./mvnw test
```

**Python**:
```bash
pytest
```

### 6. Set Up CI/CD

Create `.github/workflows/ci.yml` for automated builds and tests.

## 🎯 Production Readiness Checklist

Before deploying to production:

- [ ] All services implemented and tested
- [ ] Database migrations automated
- [ ] Environment variables externalized
- [ ] Secrets management configured (not in .env)
- [ ] Health checks configured for all services
- [ ] Resource limits set in docker-compose.yml
- [ ] Logging centralized (ELK stack or similar)
- [ ] Monitoring dashboards created in Grafana
- [ ] Alerts configured for critical metrics
- [ ] Backup strategy implemented
- [ ] Disaster recovery plan documented
- [ ] Security scanning performed
- [ ] Performance testing completed
- [ ] Documentation updated

## 📚 Additional Resources

- **Architecture**: See `call_auditing_architecture.md`
- **Technology Rationale**: See `MODERNIZATION_SUMMARY.md`
- **Cost Analysis**: See `voice_to_text_costs.md`
- **Development Guidelines**: See `CONTRIBUTING.md`

## 🤝 Getting Help

- Check the `README.md` for general information
- Review service logs: `docker compose logs <service-name>`
- Open an issue if you encounter problems
- Refer to architecture documentation for design decisions

---

**Project Status**: ✅ Skeleton complete, ready for implementation

**Next Step**: Install Docker Desktop and start implementing services!
