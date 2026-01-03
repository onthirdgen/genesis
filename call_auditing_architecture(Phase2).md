# Call Auditing Application with Voice of the Customer - Architecture Design

## Executive Summary

This document outlines the architecture for a **prototype** call auditing application with Voice of the Customer (VoC) capabilities, built using microservices and event sourcing patterns. **This architecture uses only free and open-source technologies** to minimize costs during the prototype phase. The system processes phone conversations, extracts insights, and provides comprehensive audit trails for compliance and quality assurance.

## Architecture Overview

### Core Principles

- **Microservices**: Independent, loosely-coupled services with single responsibilities
- **Event Sourcing**: Immutable event log as the source of truth
- **CQRS**: Separate read and write models for optimized performance
- **Asynchronous Processing**: Event-driven communication between services
- **Scalability**: Horizontal scaling for high-volume call processing

### High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                          API Gateway / BFF                          │
│                    (Authentication, Rate Limiting)                  │
└────────────────────────────┬────────────────────────────────────────┘
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
    ┌────▼─────┐      ┌─────▼──────┐     ┌─────▼──────┐
    │  Call    │      │   Audit    │     │    VoC     │
    │ Ingestion│      │  Service   │     │  Service   │
    │ Service  │      │            │     │            │
    └────┬─────┘      └─────┬──────┘     └─────┬──────┘
         │                  │                   │
         │                  │                   │
         └──────────────────┼───────────────────┘
                            │
                    ┌───────▼────────┐
                    │  Event Store   │
                    │   (Kafka)      │
                    └───────┬────────┘
                            │
         ┌──────────────────┼──────────────────┐
         │                  │                  │
    ┌────▼─────┐     ┌─────▼──────┐    ┌─────▼──────┐
    │Transcription│   │ Analytics  │    │ Sentiment  │
    │  Service   │   │  Service   │    │  Service   │
    └────┬───────┘   └─────┬──────┘    └─────┬──────┘
         │                 │                  │
         └─────────────────┼──────────────────┘
                           │
                    ┌──────▼───────┐
                    │  Query Store │
                    │ (PostgreSQL/ │
                    │  OpenSearch) │
                    └──────────────┘
```

## Quick Start Guide

### Prerequisites

- **Docker** and **Docker Compose** installed
- **Minimum Hardware**: 4 CPU cores, 8GB RAM, 50GB disk
- **Recommended**: 8 CPU cores, 16GB RAM, GPU for faster transcription

### Initial Setup

```bash
# 1. Clone the repository
git clone <repository-url>
cd call-auditing-platform

# 2. Start all services
docker-compose up -d

# 3. Wait for services to be healthy (2-3 minutes)
docker-compose ps

# 4. Initialize MinIO buckets
docker-compose exec minio mc alias set local http://localhost:9000 minioadmin minioadmin
docker-compose exec minio mc mb local/calls

# 5. Create database schema
docker-compose exec postgres psql -U postgres -d call_auditing < schema.sql

# 6. Access the services
# - API Gateway (Traefik): http://localhost:80
# - Traefik Dashboard: http://localhost:8080
# - MinIO Console: http://localhost:9001 (minioadmin/minioadmin)
# - Grafana: http://localhost:3000 (admin/admin)
# - OpenSearch Dashboards: http://localhost:5601
# - Prometheus: http://localhost:9090
```

### Test the System

```bash
# Upload a test audio file
curl -X POST http://localhost/api/calls/upload \
  -F "file=@test-call.wav" \
  -F "callerId=test-001" \
  -F "agentId=agent-001"

# Check processing status
curl http://localhost/api/calls/{callId}/status

# View VoC insights (after processing completes)
curl http://localhost/api/voc/insights/{callId}

# View audit results
curl http://localhost/api/audit/calls/{callId}
```

### Monitoring

- **View Logs**: `docker-compose logs -f transcription-service`
- **Check Kafka Topics**: `docker-compose exec kafka kafka-topics --list --bootstrap-server localhost:9092`
- **Monitor Metrics**: Open Grafana at http://localhost:3000
- **View Traces**: Grafana Tempo at http://localhost:3000 (integrated in Grafana)
- **Traefik Dashboard**: http://localhost:8080

## Why These Technologies? (2025 Modern Stack)

This architecture uses the **latest popular open-source alternatives** to reduce costs and avoid licensing issues:

| Component | Modern Choice | Why? | Replaces |
|-----------|--------------|------|----------|
| **Backend** | Quarkus 3.x | 10x faster startup, 70% less memory than Spring Boot | Spring Boot |
| **Message Broker** | Kafka with KRaft | No Zookeeper dependency (simpler architecture) | Kafka + Zookeeper |
| **Search** | OpenSearch 2.x | Fully Apache 2.0, no licensing concerns | Elasticsearch |
| **Cache** | Valkey 7.2+ | Open-source Redis fork by Linux Foundation | Redis |
| **API Gateway** | Traefik 3.x | Auto-discovery, modern cloud-native design | Spring Cloud Gateway |
| **Tracing** | Tempo | Free, integrates with Grafana, OpenTelemetry native | Jaeger |
| **Speech-to-Text** | Whisper v3 Large | Latest model, best accuracy | Whisper v2 |
| **Sentiment** | RoBERTa-latest | SOTA accuracy (2024) | VADER only |

**Key Benefits**:
- **Zero cost** at any scale
- **No licensing concerns** (all Apache 2.0 or similar)
- **Better performance** (Quarkus, Valkey, Traefik)
- **Simpler operations** (KRaft Kafka, Traefik auto-discovery)
- **Modern observability** (OpenTelemetry, Tempo)

## Microservices Architecture

### 1. Call Ingestion Service

**Responsibility**: Accept and store audio files, initiate processing pipeline

**Technology**: Quarkus 3.x (or Spring Boot 3.2+), Java 21+

**Key Features**:
- REST API for audio file upload
- Support multiple formats (WAV, MP3, M4A)
- Metadata extraction (caller ID, timestamp, duration)
- File validation (format, size limits)
- Store audio in MinIO (S3-compatible object storage)

**Events Published**:
```json
{
  "eventType": "CallReceived",
  "eventId": "uuid",
  "timestamp": "ISO-8601",
  "payload": {
    "callId": "uuid",
    "audioFileUrl": "minio://calls/2024/01/call-uuid.wav",
    "duration": 480,
    "metadata": {
      "callerId": "123",
      "agentId": "456",
      "timestamp": "ISO-8601",
      "channel": "inbound"
    }
  }
}
```

**Endpoints**:
- `POST /api/calls/upload` - Upload audio file
- `GET /api/calls/{callId}/status` - Check processing status
- `GET /api/calls/{callId}/audio` - Retrieve audio file

### 2. Transcription Service

**Responsibility**: Convert audio to text using speech-to-text APIs

**Technology**: Python/FastAPI with OpenAI Whisper (open-source, self-hosted)

**Key Features**:
- Asynchronous audio processing using open-source Whisper models
- Multiple model sizes (tiny, base, small, medium, large) for different accuracy/speed tradeoffs
- Speaker diarization using pyannote.audio (open-source)
- Timestamp mapping (align text with audio timeline)
- Language detection (99+ languages supported)
- Retry mechanism for failed transcriptions
- GPU acceleration support (optional, CPU-compatible)

**Events Consumed**:
- `CallReceived`

**Events Published**:
```json
{
  "eventType": "CallTranscribed",
  "eventId": "uuid",
  "timestamp": "ISO-8601",
  "payload": {
    "callId": "uuid",
    "transcription": {
      "fullText": "complete transcription",
      "segments": [
        {
          "speaker": "agent",
          "startTime": 0.0,
          "endTime": 5.2,
          "text": "Hello, how can I help you today?"
        }
      ],
      "language": "en-US",
      "confidence": 0.95
    }
  }
}
```

### 3. Sentiment Analysis Service

**Responsibility**: Analyze emotional tone and sentiment from transcriptions

**Technology**: Python/FastAPI, NLP libraries (Hugging Face, spaCy)

**Key Features**:
- Real-time sentiment scoring per segment
- Emotion detection (frustrated, satisfied, neutral, etc.)
- Sentiment trend analysis across call duration
- Escalation detection (increasing frustration)

**Events Consumed**:
- `CallTranscribed`

**Events Published**:
```json
{
  "eventType": "SentimentAnalyzed",
  "eventId": "uuid",
  "timestamp": "ISO-8601",
  "payload": {
    "callId": "uuid",
    "overallSentiment": "negative",
    "sentimentScore": -0.65,
    "segments": [
      {
        "startTime": 0.0,
        "endTime": 5.2,
        "sentiment": "neutral",
        "score": 0.1,
        "emotions": ["neutral"]
      }
    ],
    "escalationDetected": true
  }
}
```

### 4. Voice of the Customer (VoC) Service

**Responsibility**: Extract customer insights, themes, and actionable intelligence

**Technology**: Quarkus 3.x (or Spring Boot 3.2+), Hugging Face Transformers

**Key Features**:
- Topic extraction (complaints, product issues, feature requests)
- Keyword and phrase extraction
- Intent classification (inquiry, complaint, compliment)
- Customer satisfaction prediction
- Root cause analysis
- Trend identification across calls

**Events Consumed**:
- `CallTranscribed`
- `SentimentAnalyzed`

**Events Published**:
```json
{
  "eventType": "VoCAAnalyzed",
  "eventId": "uuid",
  "timestamp": "ISO-8601",
  "payload": {
    "callId": "uuid",
    "insights": {
      "primaryIntent": "complaint",
      "topics": ["billing", "unexpected_charges"],
      "keywords": ["overcharged", "refund", "cancel"],
      "customerSatisfaction": "low",
      "actionableItems": [
        {
          "category": "billing_issue",
          "priority": "high",
          "description": "Customer disputes billing amount"
        }
      ],
      "predictedChurnRisk": 0.78
    }
  }
}
```

**Endpoints**:
- `GET /api/voc/insights/{callId}` - Get VoC insights for a call
- `GET /api/voc/trends` - Aggregate trends across time period
- `GET /api/voc/topics` - Top topics and themes
- `GET /api/voc/actionable-items` - High-priority action items

### 5. Audit Service

**Responsibility**: Compliance checking, quality scoring, and audit trail management

**Technology**: Quarkus 3.x, Easy Rules (lightweight rule engine)

**Key Features**:
- Compliance rule evaluation (regulatory requirements)
- Script adherence checking
- Quality scoring based on configurable criteria
- Automated flagging for manual review
- Audit trail generation
- Compliance reporting

**Events Consumed**:
- `CallTranscribed`
- `SentimentAnalyzed`
- `VoCAAnalyzed`

**Events Published**:
```json
{
  "eventType": "CallAudited",
  "eventId": "uuid",
  "timestamp": "ISO-8601",
  "payload": {
    "callId": "uuid",
    "auditResults": {
      "overallScore": 85,
      "complianceStatus": "passed",
      "violations": [],
      "qualityMetrics": {
        "scriptAdherence": 90,
        "customerService": 80,
        "resolutionEffectiveness": 85
      },
      "flagsForReview": false,
      "reviewReason": null
    }
  }
}
```

**Endpoints**:
- `GET /api/audit/calls/{callId}` - Get audit results
- `GET /api/audit/reports` - Generate compliance reports
- `POST /api/audit/rules` - Configure audit rules
- `GET /api/audit/violations` - List compliance violations

### 6. Analytics Service

**Responsibility**: Aggregate metrics, KPIs, and dashboard data

**Technology**: Spring Boot (batch processing with scheduled jobs)

**Key Features**:
- Real-time metrics calculation
- Historical trend analysis
- Agent performance metrics
- Customer satisfaction trends
- Compliance rate tracking
- Custom report generation

**Events Consumed**:
- All events (subscribes to event store)

**Materialized Views**:
- Agent performance dashboard
- Compliance metrics
- Customer sentiment trends
- VoC theme evolution
- Call volume and duration statistics

**Endpoints**:
- `GET /api/analytics/dashboard` - Main dashboard data
- `GET /api/analytics/agents/{agentId}/performance` - Agent KPIs
- `GET /api/analytics/compliance/summary` - Compliance overview
- `GET /api/analytics/customer-satisfaction` - CSAT trends

### 7. Notification Service

**Responsibility**: Send alerts and notifications for critical events

**Technology**: Spring Boot with free notification options

**Key Features**:
- Real-time alerts for compliance violations
- Escalation notifications
- Daily/weekly summary reports
- Configurable notification rules

**Free Notification Options**:
- Console/Log-based notifications (prototype default)
- SMTP email (using Gmail free tier or local SMTP server)
- Webhook notifications (Slack free tier, Discord webhooks)
- Future: SMS via Twilio free trial

**Events Consumed**:
- `CallAudited` (violations)
- `SentimentAnalyzed` (escalations)
- `VoCAAnalyzed` (high-priority items)

## Event Sourcing Architecture

### Event Store Implementation

**Technology**: Apache Kafka (Docker-based)

**Configuration**:
- Topic per event type pattern
- Retention: 7 days (configurable, limited by disk space)
- Replication factor: 1 (single broker for prototype)
- Partitioning: By callId for ordering guarantees

**Event Schema Management** (Prototype):
- JSON format for simplicity (no schema registry needed initially)
- Document event schemas in code and documentation
- Future: Add Confluent Schema Registry or Apicurio for Avro schemas

**Topics**:
```
calls.received
calls.transcribed
calls.sentiment-analyzed
calls.voc-analyzed
calls.audited
```

### Event Structure

**Standard Event Envelope**:
```json
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
  "payload": { }
}
```

### CQRS Pattern

**Write Side**:
- Services publish events to Kafka
- Event store is append-only
- No direct database writes from commands

**Read Side**:
- Separate read databases optimized for queries
- Materialized views built from event stream
- PostgreSQL for relational queries
- Elasticsearch for full-text search and analytics

**Projections**:
- Call summary projection (PostgreSQL)
- Audit report projection (PostgreSQL)
- Search index projection (Elasticsearch)
- Analytics projection (TimescaleDB/ClickHouse)

## Data Architecture

### Data Stores

**1. Event Store**
- **Technology**: Apache Kafka
- **Purpose**: Immutable source of truth
- **Retention**: 90 days

**2. Object Storage**
- **Technology**: MinIO (self-hosted, S3-compatible)
- **Purpose**: Audio file storage
- **Deployment**: Docker container
- **Storage**: Local disk or mounted volume

**3. Relational Database**
- **Technology**: PostgreSQL
- **Purpose**: Materialized views, audit trails
- **Tables**:
  - `calls` - Call summary information
  - `transcriptions` - Stored transcription text
  - `audit_results` - Audit outcomes
  - `compliance_violations` - Violation records
  - `agent_performance` - Aggregated metrics

**4. Search & Analytics**
- **Technology**: OpenSearch 2.x (fully open-source Elasticsearch fork)
- **Purpose**: Full-text search, real-time analytics
- **Benefits**: No licensing concerns, fully Apache 2.0 licensed
- **Indices**:
  - `calls` - Searchable call data
  - `transcriptions` - Searchable transcripts
  - `voc-insights` - VoC themes and topics

**5. Time-Series Data**
- **Technology**: PostgreSQL with TimescaleDB extension (already included)
- **Purpose**: High-volume metrics and trends
- **Data**: Sentiment scores over time, call volumes, performance metrics
- **Note**: TimescaleDB is already included in the postgres Docker image (timescale/timescaledb)

### Data Flow

```
Audio Upload → Object Storage (MinIO)
     ↓
Event: CallReceived → Kafka (KRaft mode, no Zookeeper)
     ↓
Transcription Service (Whisper v3) → Event: CallTranscribed → Kafka
     ↓                                              ↓
Sentiment Service → Event: SentimentAnalyzed → Kafka
     ↓                                              ↓
VoC Service → Event: VoCAAnalyzed → Kafka
     ↓                                              ↓
Audit Service → Event: CallAudited → Kafka
     ↓
Query Services build materialized views
     ↓
PostgreSQL (with TimescaleDB) / OpenSearch / Valkey
```

## Technology Stack (100% Free & Open-Source - Latest 2025)

### Backend Services (Modern JVM)
- **Primary Option**:
  - **Quarkus 3.x** - Supersonic Subatomic Java (faster startup, lower memory than Spring Boot)
  - **Native compilation** with GraalVM for 10x faster startup
  - **Reactive**: Built-in Mutiny for reactive programming
- **Alternative**: Spring Boot 3.2+ (if team prefers Spring ecosystem)
- **Message Broker**: Apache Kafka 3.7+ with **KRaft** (no Zookeeper needed!) - Free
- **Event Processing**: Quarkus Kafka Streams / Spring Cloud Stream - Free

### Data & Storage
- **RDBMS**: PostgreSQL 16+ with TimescaleDB extension - Free
- **Search**: **OpenSearch 2.x** (fully open-source Elasticsearch fork, no licensing issues) - Free
- **Object Storage**: MinIO (self-hosted S3-compatible) - Free
- **Cache**: **Valkey 7.2+** (open-source Redis fork by Linux Foundation) or Dragonfly - Free

### ML/NLP Services
- **Language**: Python 3.12+ - Free
- **Framework**: FastAPI 0.110+ - Free
- **Speech-to-Text**: OpenAI Whisper v3 (Whisper Large v3, open-source) - Free
- **Speaker Diarization**: **pyannote.audio 3.x** - Free
- **NLP**: Hugging Face Transformers 4.x - Free
- **Sentiment Analysis**:
  - **cardiffnlp/twitter-roberta-base-sentiment-latest** (SOTA accuracy)
  - VADER (lightweight fallback)
  - Free

### Infrastructure (Local Development / Single Server)
- **Container Orchestration**: Docker Compose (for prototype)
- **API Gateway**:
  - **Traefik 3.x** (modern, auto-discovery, better than Nginx for containers) - Free
  - Alternative: Envoy Proxy
- **Observability** (OpenTelemetry Standard):
  - **Logging**: Fluent Bit + OpenSearch + OpenSearch Dashboards - Free
  - **Metrics**: Prometheus + Grafana - Free
  - **Tracing**: **Tempo** (Grafana) or Jaeger - Free
  - **All-in-one**: OpenTelemetry Collector - Free

### Development Tools
- **Version Control**: Git - Free
- **CI/CD**: GitHub Actions (free tier) or GitLab CI - Free
- **Container Registry**: GitHub Container Registry (ghcr.io, free) - Free
- **IDE**: VS Code, IntelliJ Community Edition - Free
- **API Testing**: Bruno (open-source Postman alternative) - Free

## Voice of the Customer (VoC) Features

### Core Capabilities

**1. Theme Detection**
- Automatic categorization of call topics
- Trending theme identification
- Theme evolution over time
- Cross-reference with business metrics

**2. Customer Journey Mapping**
- Track customer interactions across calls
- Identify pain points in customer journey
- Measure resolution effectiveness
- Customer lifetime value correlation

**3. Actionable Insights Dashboard**
- Real-time alerts for critical issues
- Prioritized action items
- Root cause analysis
- Impact assessment (revenue, churn risk)

**4. Sentiment Trends**
- Customer sentiment over time
- Sentiment by product/service
- Sentiment by agent/team
- Correlation with business outcomes

**5. Competitive Intelligence**
- Competitor mentions tracking
- Feature comparison analysis
- Market trends from customer feedback

**6. Product Feedback Loop**
- Feature requests aggregation
- Bug report compilation
- User experience insights
- Product roadmap validation

### VoC Analytics Endpoints

```
GET  /api/voc/themes/trending
GET  /api/voc/themes/{themeId}/calls
GET  /api/voc/sentiment/trends
GET  /api/voc/insights/actionable
GET  /api/voc/customer-journey/{customerId}
POST /api/voc/reports/generate
GET  /api/voc/competitive-intelligence
GET  /api/voc/product-feedback
```

## Security & Compliance

### Authentication & Authorization
- **Authentication**: OAuth 2.0 / JWT tokens
- **Authorization**: Role-Based Access Control (RBAC)
- **Service-to-Service**: mTLS certificates

### Data Protection
- **Encryption at Rest**: AES-256
- **Encryption in Transit**: TLS 1.3
- **PII Protection**:
  - Tokenization of sensitive data
  - PII redaction in transcripts (optional)
  - Data masking in non-production environments

### Compliance
- **Audit Logging**: All API calls and data access logged
- **Data Retention**: Configurable retention policies
- **GDPR/CCPA**: Right to deletion, data export capabilities
- **Call Recording Consent**: Compliance checking and flagging

### Regulatory Requirements
- **HIPAA** (if healthcare): PHI protection
- **PCI-DSS** (if payment data): Cardholder data security
- **SOC 2**: Security controls and audit trails
- **TCPA**: Call recording consent verification

## Scalability & Performance

**Note**: For the prototype, services run on a single machine using Docker Compose. The architecture is designed to scale horizontally when needed.

### Future Horizontal Scaling Strategy

**Stateless Services**: All microservices are stateless and can be scaled independently
- Multiple instances of each service behind load balancer
- No session state stored in services

**Event Processing**:
- Kafka consumer groups for parallel processing
- Partition strategy: callId-based for ordering guarantees
- Add more consumer instances to process events faster

**Database Scaling** (Future):
- PostgreSQL: Read replicas for query services
- Elasticsearch: Multi-node cluster
- TimescaleDB: Automatic chunking and compression

### Performance Optimization

**Caching Strategy**:
- Redis for frequently accessed data
- Cache invalidation via Kafka events
- CDN for static assets and audio files

**Async Processing**:
- Non-blocking I/O (Spring WebFlux)
- Parallel processing of independent tasks
- Batch processing for analytics

**Query Optimization**:
- Materialized views for complex queries
- Database indexes on common query patterns
- Query result caching

### Expected Performance Targets

- **Audio Upload**: < 2 seconds for 10MB file
- **Transcription**: Real-time ratio 1:4 (1 hour audio in 15 minutes)
- **Sentiment Analysis**: < 5 seconds per call
- **VoC Analysis**: < 10 seconds per call
- **Audit Results**: < 3 seconds per call
- **Dashboard Load**: < 500ms
- **Search Queries**: < 200ms

## Deployment Architecture

### Docker Compose Deployment (Prototype)

For the prototype phase, all services run locally using Docker Compose. This provides a simple, free deployment suitable for development and testing.

```yaml
# docker-compose.yml
version: '3.8'

services:
  # Event Store - Kafka with KRaft (no Zookeeper!)
  kafka:
    image: apache/kafka:3.7.0
    ports:
      - "9092:9092"
      - "9093:9093"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_LOG_DIRS: /var/lib/kafka/data
      CLUSTER_ID: 'MkU3OEVBNTcwNTJENDM2Qk'
    volumes:
      - kafka-data:/var/lib/kafka/data

  # Object Storage
  minio:
    image: minio/minio:latest
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    command: server /data --console-address ":9001"
    volumes:
      - minio-data:/data

  # Database
  postgres:
    image: timescale/timescaledb:latest-pg15
    ports:
      - "5432:5432"
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      POSTGRES_DB: call_auditing
    volumes:
      - postgres-data:/var/lib/postgresql/data

  # Search - OpenSearch (fully open-source Elasticsearch fork)
  opensearch:
    image: opensearchproject/opensearch:2.12.0
    ports:
      - "9200:9200"
      - "9600:9600"
    environment:
      - discovery.type=single-node
      - OPENSEARCH_JAVA_OPTS=-Xms512m -Xmx512m
      - DISABLE_SECURITY_PLUGIN=true
      - DISABLE_INSTALL_DEMO_CONFIG=true
    volumes:
      - opensearch-data:/usr/share/opensearch/data

  # OpenSearch Dashboards (Kibana alternative)
  opensearch-dashboards:
    image: opensearchproject/opensearch-dashboards:2.12.0
    ports:
      - "5601:5601"
    environment:
      OPENSEARCH_HOSTS: '["http://opensearch:9200"]'
      DISABLE_SECURITY_DASHBOARDS_PLUGIN: true
    depends_on:
      - opensearch

  # Cache - Valkey (Redis fork by Linux Foundation)
  valkey:
    image: valkey/valkey:7.2-alpine
    ports:
      - "6379:6379"
    volumes:
      - valkey-data:/data
    command: valkey-server --save 60 1 --loglevel warning

  # Microservices (Quarkus-based)
  call-ingestion-service:
    build: ./call-ingestion-service
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.ingestion.rule=PathPrefix(`/api/calls`)"
    environment:
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      MINIO_ENDPOINT: http://minio:9000
      QUARKUS_DATASOURCE_JDBC_URL: jdbc:postgresql://postgres:5432/call_auditing
      QUARKUS_DATASOURCE_USERNAME: postgres
      QUARKUS_DATASOURCE_PASSWORD: postgres
    depends_on:
      - kafka
      - minio
      - postgres

  transcription-service:
    build: ./transcription-service
    ports:
      - "8082:8000"
    environment:
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      MINIO_ENDPOINT: http://minio:9000
      MODEL_SIZE: base  # tiny, base, small, medium, large
    volumes:
      - whisper-models:/root/.cache/whisper
    depends_on:
      - kafka
      - minio
    # Uncomment for GPU support
    # deploy:
    #   resources:
    #     reservations:
    #       devices:
    #         - driver: nvidia
    #           count: 1
    #           capabilities: [gpu]

  sentiment-service:
    build: ./sentiment-service
    ports:
      - "8083:8000"
    environment:
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    depends_on:
      - kafka

  voc-service:
    build: ./voc-service
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.voc.rule=PathPrefix(`/api/voc`)"
    environment:
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      QUARKUS_DATASOURCE_JDBC_URL: jdbc:postgresql://postgres:5432/call_auditing
      OPENSEARCH_HOST: opensearch:9200
    depends_on:
      - kafka
      - postgres
      - opensearch

  audit-service:
    build: ./audit-service
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.audit.rule=PathPrefix(`/api/audit`)"
    environment:
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      QUARKUS_DATASOURCE_JDBC_URL: jdbc:postgresql://postgres:5432/call_auditing
    depends_on:
      - kafka
      - postgres

  analytics-service:
    build: ./analytics-service
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.analytics.rule=PathPrefix(`/api/analytics`)"
    environment:
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      QUARKUS_DATASOURCE_JDBC_URL: jdbc:postgresql://postgres:5432/call_auditing
      OPENSEARCH_HOST: opensearch:9200
      VALKEY_HOST: valkey:6379
    depends_on:
      - kafka
      - postgres
      - opensearch
      - valkey

  notification-service:
    build: ./notification-service
    environment:
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    depends_on:
      - kafka

  # API Gateway - Traefik (modern, auto-discovery)
  traefik:
    image: traefik:v3.0
    ports:
      - "80:80"
      - "8080:8080"  # Traefik dashboard
    command:
      - "--api.insecure=true"
      - "--providers.docker=true"
      - "--providers.docker.exposedbydefault=false"
      - "--entrypoints.web.address=:80"
      - "--metrics.prometheus=true"
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock:ro
    depends_on:
      - call-ingestion-service
      - voc-service
      - audit-service
      - analytics-service

  # Monitoring
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus-data:/prometheus

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
    volumes:
      - grafana-data:/var/lib/grafana
      - ./monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards
      - ./monitoring/grafana/datasources:/etc/grafana/provisioning/datasources
    depends_on:
      - prometheus
      - tempo

  # Distributed Tracing - Tempo
  tempo:
    image: grafana/tempo:latest
    ports:
      - "3200:3200"   # Tempo
      - "4317:4317"   # OTLP gRPC
      - "4318:4318"   # OTLP HTTP
    command: [ "-config.file=/etc/tempo.yaml" ]
    volumes:
      - ./monitoring/tempo.yaml:/etc/tempo.yaml
      - tempo-data:/tmp/tempo

  # OpenTelemetry Collector (optional, for advanced setups)
  otel-collector:
    image: otel/opentelemetry-collector-contrib:latest
    ports:
      - "4319:4317"   # OTLP gRPC
      - "4320:4318"   # OTLP HTTP
    volumes:
      - ./monitoring/otel-collector-config.yaml:/etc/otel-collector-config.yaml
    command: ["--config=/etc/otel-collector-config.yaml"]
    depends_on:
      - tempo

volumes:
  kafka-data:
  minio-data:
  postgres-data:
  opensearch-data:
  valkey-data:
  whisper-models:
  prometheus-data:
  grafana-data:
  tempo-data:
```

### Running the Prototype

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop all services
docker-compose down

# Stop and remove volumes (clean slate)
docker-compose down -v
```

### Resource Requirements (Minimum)

**With Modern Stack (Quarkus + Valkey + OpenSearch)**:
- **CPU**: 4 cores
- **RAM**: 6-8 GB (Quarkus uses 70% less memory than Spring Boot)
- **Disk**: 50 GB (for audio storage and databases)
- **GPU**: Optional, significantly speeds up transcription

**Performance Improvements vs Traditional Stack**:
- **Startup Time**: 2-3 seconds (vs 20-30s with Spring Boot)
- **Memory Usage**: ~4GB total (vs ~6-8GB with Spring + Redis + Elasticsearch)
- **Request Latency**: 30% faster with Quarkus native mode
- **Container Size**: 150MB (vs 500MB+ with traditional JVM apps)

## Monitoring & Observability

### Key Metrics

**Business Metrics**:
- Calls processed per day
- Average processing time
- Compliance pass rate
- Customer satisfaction trends
- Top VoC themes

**Technical Metrics**:
- Service response times (p95, p99)
- Error rates by service
- Kafka lag per consumer group
- Database query performance
- Cache hit rates

### Alerting Rules

- Transcription service failure rate > 5%
- Kafka consumer lag > 1000 messages
- Compliance violation detected
- Sentiment escalation detected
- Service response time > 2s
- Database connection pool exhaustion

### Dashboards

**Operations Dashboard**:
- Service health status
- Request rates and latencies
- Error rates and types
- Infrastructure metrics

**Business Dashboard**:
- Daily call volume
- Compliance metrics
- Customer sentiment trends
- VoC insights summary
- Agent performance

## Disaster Recovery & Business Continuity (Prototype)

### Backup Strategy
- **Event Store**: Kafka data persisted in Docker volumes
- **Databases**: Use Docker volume snapshots or pg_dump for PostgreSQL backups
- **Object Storage**: MinIO data persisted in Docker volumes
- **Backup Script**: Regular exports of Docker volumes to external storage

### Simple Backup Commands

```bash
# Backup PostgreSQL
docker-compose exec postgres pg_dump -U postgres call_auditing > backup_$(date +%Y%m%d).sql

# Backup MinIO data
docker-compose exec minio mc mirror /data /backup

# Full volume backup
docker run --rm -v genesis_postgres-data:/data -v $(pwd)/backups:/backup alpine tar czf /backup/postgres-data.tar.gz -C /data .
```

### Recovery Objectives (Prototype)
- **RTO** (Recovery Time Objective): 4 hours (manual restoration)
- **RPO** (Recovery Point Objective): 24 hours (daily backups)

### Event Replay Capability
- Rebuild read models from event store
- Reprocess events for failed consumers
- Temporal queries (reconstruct state at any point in time)

**Note**: For production, implement automated backups, replication, and monitoring.

## Development Workflow

### Service Development Pattern

1. **Define Event Schema** (Avro)
2. **Implement Event Publisher/Consumer**
3. **Write Business Logic**
4. **Build Materialized Views**
5. **Create REST Endpoints**
6. **Write Tests** (unit, integration, contract)
7. **Deploy to Staging**
8. **Production Deployment** (blue-green or canary)

### Testing Strategy

**Unit Tests**: Service logic, pure functions
**Integration Tests**: Kafka producers/consumers, database interactions
**Contract Tests**: API contracts between services (Pact)
**End-to-End Tests**: Full workflow from upload to audit results
**Performance Tests**: Load testing with JMeter/Gatling

## Future Enhancements

### Phase 2 Features
- Real-time call monitoring and intervention
- Predictive analytics (call outcome prediction)
- Automated agent coaching recommendations
- Multi-language support expansion
- Video call analysis

### Phase 3 Features
- AI-powered compliance automation
- Natural language query interface
- Custom ML model training per organization
- Advanced customer journey analytics
- Integration marketplace (Salesforce, Zendesk, etc.)

## Prototype to Production Migration Path

When ready to move from prototype to production, consider these upgrades:

### Infrastructure Upgrades

| Component | Prototype (Free) | Production (Paid Options) |
|-----------|------------------|---------------------------|
| **Container Orchestration** | Docker Compose | Kubernetes (GKE/EKS/AKS) or self-managed K8s cluster |
| **Object Storage** | MinIO (self-hosted) | AWS S3, Google Cloud Storage, Azure Blob |
| **Transcription** | Whisper (self-hosted) | Keep Whisper or add Whisper API for overflow |
| **Database** | Single PostgreSQL instance | PostgreSQL with read replicas, managed service option |
| **Kafka** | Single Docker broker | Kafka cluster or Confluent Cloud/AWS MSK |
| **Monitoring** | Self-hosted Prometheus/Grafana | Datadog, New Relic, or keep self-hosted |
| **Search** | Single Elasticsearch node | Elasticsearch cluster or Elastic Cloud |
| **Backups** | Manual scripts | Automated backup services, S3 snapshots |

### Cost Comparison

**Prototype (This Architecture)**:
- **Infrastructure**: $0 (runs on your hardware)
- **Software**: $0 (100% open-source)
- **Total Monthly Cost**: $0 (only electricity/internet)

**Production (Example - Medium Scale)**:
- **Cloud VM** (16 CPU, 32GB RAM): ~$200-400/month
- **Whisper API** (1000 hours): ~$360/month
- **Managed services**: $0-500/month (optional)
- **Total**: $200-1260/month depending on choices

### When to Consider Paid Services

- **High Volume**: >5,000 hours of audio per month (Whisper API becomes cost-effective)
- **High Availability**: Need 99.9%+ uptime (managed Kubernetes, databases)
- **Global Scale**: Multiple regions (cloud object storage, CDNs)
- **Support Requirements**: Need vendor support (managed services)

## Cost Optimization Strategies

### Keep It Free at Scale

1. **Self-host Whisper with GPU**: One-time GPU investment, unlimited transcriptions
2. **Use cloud free tiers**: AWS/GCP/Azure have generous free tiers for databases
3. **Optimize storage**: Compress audio files, implement lifecycle policies
4. **Horizontal scaling**: Add more VMs instead of managed services

### Hybrid Approach

- Keep expensive compute (Whisper) self-hosted
- Use managed services only for critical components (database backups, Kafka)
- Pay for monitoring/alerting, self-host everything else

## Conclusion

This architecture provides a **completely free, production-capable foundation** for a call auditing application with comprehensive Voice of the Customer capabilities. By using only open-source technologies, you can:

- **Validate the business model** with zero infrastructure costs
- **Process thousands of calls** without per-call fees
- **Scale incrementally** as needs grow
- **Migrate to managed services** selectively when needed

The microservices approach enables independent scaling and deployment, while event sourcing ensures complete audit trails and enables powerful analytical capabilities. The separation of concerns between transcription, sentiment analysis, VoC extraction, and audit processes allows for specialized optimization of each component while maintaining loose coupling through the event-driven architecture.

**Start with this free prototype, prove the value, then invest in infrastructure as revenue grows.**
