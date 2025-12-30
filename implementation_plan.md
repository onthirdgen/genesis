# Call Auditing Platform - Implementation Plan

## Overview

This document outlines the implementation plan for the Call Auditing Platform with Voice of Customer (VoC) analytics. The system uses an event-driven microservices architecture with event sourcing.

## Current Status

| Component | Status | Notes |
|-----------|--------|-------|
| Infrastructure (Kafka, PostgreSQL, MinIO, etc.) | Complete | All services running |
| Kafka Topics | Complete | 5 topics created |
| Database Schema | Complete | TimescaleDB hypertables configured |
| Call Ingestion Service | Complete | Spring Boot, port 8081 |
| Transcription Service | Complete | Python/FastAPI, port 8082 |
| Sentiment Service | Complete | Python/FastAPI, port 8083 |
| VoC Service | Complete | Spring Boot, port 8084 |
| Audit Service | Complete | Spring Boot, port 8085 |
| Analytics Service | Complete | Spring Boot, port 8086 |
| Notification Service | Complete | Spring Boot, port 8087 |
| API Gateway | Complete | Spring Cloud Gateway, port 8080 |

**ALL SERVICES IMPLEMENTED - Platform is ready for deployment and testing.**

---

## Architecture

### Event Flow

```
User uploads audio
  │
  ▼
┌─────────────────────────┐
│  Call Ingestion Service │ ──► Kafka: calls.received
│  (Port 8081)            │
└─────────────────────────┘
            │
            ▼
┌─────────────────────────┐
│  Transcription Service  │ ──► Kafka: calls.transcribed
│  (Port 8082)            │
└─────────────────────────┘
            │
            ▼ (parallel processing)
     ┌──────┴──────┬────────────────┐
     │             │                │
     ▼             ▼                ▼
┌─────────┐  ┌──────────┐  ┌─────────────┐
│Sentiment│  │   VoC    │  │   Audit     │
│ Service │  │ Service  │  │  Service    │
│ (8083)  │  │ (8084)   │  │  (8085)     │
└────┬────┘  └────┬─────┘  └──────┬──────┘
     │            │               │
     ▼            ▼               ▼
calls.sentiment  calls.voc     calls.audited
  -analyzed      -analyzed
     │            │               │
     └────────────┼───────────────┘
                  ▼
        ┌─────────────────┐
        │Analytics Service│
        │    (8086)       │
        └─────────────────┘
```

### Kafka Topics

| Topic | Partitions | Producer | Consumers |
|-------|------------|----------|-----------|
| `calls.received` | 3 | Call Ingestion | Transcription, Analytics |
| `calls.transcribed` | 3 | Transcription | Sentiment, VoC, Audit, Analytics |
| `calls.sentiment-analyzed` | 3 | Sentiment | VoC, Audit, Notification, Analytics |
| `calls.voc-analyzed` | 3 | VoC | Audit, Notification, Analytics |
| `calls.audited` | 3 | Audit | Notification, Analytics |

---

## Service Specifications

### Service 1: Call Ingestion Service (COMPLETE)

**Location:** `/call-ingestion-service/`
**Type:** Spring Boot 3.2.5, Java 21
**Port:** 8081

**Files Implemented:**
```
call-ingestion-service/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/
├── src/main/resources/application.yml
└── src/main/java/com/callaudit/ingestion/
    ├── CallIngestionApplication.java
    ├── config/
    │   ├── MinioConfig.java
    │   └── KafkaConfig.java
    ├── controller/
    │   └── CallIngestionController.java
    ├── service/
    │   ├── CallIngestionService.java
    │   └── StorageService.java
    ├── repository/
    │   └── CallRepository.java
    ├── model/
    │   ├── Call.java
    │   ├── CallStatus.java
    │   └── CallChannel.java
    └── event/
        └── CallReceivedEvent.java
```

**API Endpoints:**
- `POST /api/calls/upload` - Upload audio (multipart)
- `GET /api/calls/{callId}/status` - Check processing status
- `GET /api/calls/{callId}/audio` - Stream audio file

---

### Service 2: Transcription Service (COMPLETE)

**Location:** `/transcription-service/`
**Type:** Python 3.12, FastAPI
**Port:** 8082

**Files Implemented:**
```
transcription-service/
├── Dockerfile
├── requirements.txt
├── main.py
├── config.py
├── models/
│   ├── events.py
│   └── transcription.py
├── services/
│   ├── kafka_service.py
│   ├── minio_service.py
│   └── whisper_service.py
└── routers/
    └── health.py
```

**Processing:**
1. Consumes `CallReceived` events
2. Downloads audio from MinIO
3. Transcribes with OpenAI Whisper
4. Performs basic speaker diarization
5. Publishes `CallTranscribed` events

---

### Service 3: Sentiment Service (COMPLETE)

**Location:** `/sentiment-service/`
**Type:** Python 3.12, FastAPI
**Port:** 8083

**Files Implemented:**
```
sentiment-service/
├── Dockerfile
├── requirements.txt
├── main.py
├── config.py
├── models/
│   └── events.py
├── services/
│   ├── kafka_service.py
│   └── sentiment_service.py
└── routers/
    └── health.py
```

**Models Used:**
- Primary: `cardiffnlp/twitter-roberta-base-sentiment-latest`
- Fallback: VADER sentiment analyzer

**Output:**
- Overall sentiment: positive/negative/neutral
- Sentiment score: -1.0 to 1.0
- Per-segment analysis
- Escalation detection

---

### Service 4: VoC Service (IN PROGRESS)

**Location:** `/voc-service/`
**Type:** Spring Boot 3.2.5, Java 21
**Port:** 8084

**Files to Create:**
```
voc-service/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/
├── src/main/resources/application.yml
└── src/main/java/com/callaudit/voc/
    ├── VocServiceApplication.java
    ├── config/
    │   ├── KafkaConfig.java
    │   └── OpenSearchConfig.java
    ├── controller/
    │   └── VocController.java
    ├── service/
    │   ├── VocAnalysisService.java
    │   └── InsightService.java
    ├── repository/
    │   └── VocInsightRepository.java
    ├── model/
    │   ├── VocInsight.java
    │   ├── Intent.java
    │   └── Satisfaction.java
    ├── listener/
    │   └── VocEventListener.java
    └── event/
        ├── CallTranscribedEvent.java
        ├── SentimentAnalyzedEvent.java
        └── VocAnalyzedEvent.java
```

**API Endpoints:**
- `GET /api/voc/insights/{callId}` - Get insights for a call
- `GET /api/voc/trends` - Aggregate trends
- `GET /api/voc/topics` - Top topics

**Analysis Logic:**
- Extract keywords (TF-IDF / frequency)
- Classify intent: INQUIRY, COMPLAINT, COMPLIMENT, REQUEST, OTHER
- Map sentiment to satisfaction: LOW, MEDIUM, HIGH
- Calculate churn risk: 0.0 - 1.0
- Generate actionable items

---

### Service 5: Audit Service (IN PROGRESS)

**Location:** `/audit-service/`
**Type:** Spring Boot 3.2.5, Java 21
**Port:** 8085

**Files to Create:**
```
audit-service/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/
├── src/main/resources/application.yml
└── src/main/java/com/callaudit/audit/
    ├── AuditServiceApplication.java
    ├── config/
    │   └── KafkaConfig.java
    ├── controller/
    │   └── AuditController.java
    ├── service/
    │   ├── AuditService.java
    │   └── ComplianceRuleEngine.java
    ├── repository/
    │   ├── AuditResultRepository.java
    │   ├── ComplianceViolationRepository.java
    │   └── ComplianceRuleRepository.java
    ├── model/
    │   ├── AuditResult.java
    │   ├── ComplianceViolation.java
    │   ├── ComplianceRule.java
    │   ├── ComplianceStatus.java
    │   └── ViolationSeverity.java
    ├── listener/
    │   └── AuditEventListener.java
    └── event/
        └── CallAuditedEvent.java
```

**API Endpoints:**
- `GET /api/audit/calls/{callId}` - Get audit results
- `GET /api/audit/reports` - Generate reports
- `GET /api/audit/violations` - List violations
- `POST /api/audit/rules` - Create audit rule

**Compliance Rules (from schema.sql):**
| Rule ID | Name | Severity |
|---------|------|----------|
| GREETING_REQUIRED | Agent greets within 10s | MEDIUM |
| DISCLOSURE_REQUIRED | Recording disclosure within 30s | CRITICAL |
| NO_PROFANITY | No prohibited words | HIGH |
| EMPATHY_CHECK | Express empathy when negative | LOW |
| CLOSING_REQUIRED | Proper call closing | MEDIUM |

**Quality Scoring:**
- `scriptAdherence`: % of required phrases used (30% weight)
- `customerService`: sentiment + empathy (40% weight)
- `resolutionEffectiveness`: VoC satisfaction (30% weight)
- `overallScore`: weighted average (0-100)

---

### Service 6: Analytics Service (COMPLETE)

**Location:** `/analytics-service/`
**Type:** Spring Boot 3.2.5, Java 21
**Port:** 8086

**Files to Create:**
```
analytics-service/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/
├── src/main/resources/application.yml
└── src/main/java/com/callaudit/analytics/
    ├── AnalyticsServiceApplication.java
    ├── config/
    │   ├── KafkaConfig.java
    │   ├── OpenSearchConfig.java
    │   └── ValkeyConfig.java
    ├── controller/
    │   └── AnalyticsController.java
    ├── service/
    │   ├── DashboardService.java
    │   ├── AgentPerformanceService.java
    │   └── TrendService.java
    ├── repository/
    │   └── AgentPerformanceRepository.java
    ├── model/
    │   └── AgentPerformance.java
    └── listener/
        └── AnalyticsEventListener.java
```

**API Endpoints:**
- `GET /api/analytics/dashboard` - Main dashboard KPIs
- `GET /api/analytics/agents/{agentId}/performance` - Agent metrics
- `GET /api/analytics/compliance/summary` - Compliance overview
- `GET /api/analytics/customer-satisfaction` - CSAT trends

**Aggregations:**
- Uses TimescaleDB hypertables for time-series
- Caches hot data in Valkey
- Indexes searchable data in OpenSearch

---

### Service 7: Notification Service (COMPLETE)

**Location:** `/notification-service/`
**Type:** Spring Boot 3.2.5, Java 21
**Port:** 8087

**Purpose:** Send alerts for compliance violations, high churn risk

**Files to Create:**
```
notification-service/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/
├── src/main/resources/application.yml
└── src/main/java/com/callaudit/notification/
    ├── NotificationServiceApplication.java
    ├── config/
    │   └── KafkaConfig.java
    ├── service/
    │   ├── NotificationService.java
    │   └── AlertRuleEngine.java
    ├── model/
    │   └── Notification.java
    └── listener/
        └── NotificationEventListener.java
```

**Alert Triggers:**
- Compliance violation (severity: CRITICAL, HIGH)
- High churn risk (> 0.7)
- Negative sentiment escalation
- Flags for review

---

### Service 8: API Gateway (COMPLETE)

**Location:** `/api-gateway/`
**Type:** Spring Cloud Gateway
**Port:** 8080

**Files to Create:**
```
api-gateway/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/
├── src/main/resources/application.yml
└── src/main/java/com/callaudit/gateway/
    ├── ApiGatewayApplication.java
    └── config/
        └── RouteConfig.java
```

**Routes:**
| Path | Target |
|------|--------|
| `/api/calls/**` | call-ingestion-service:8080 |
| `/api/voc/**` | voc-service:8080 |
| `/api/audit/**` | audit-service:8080 |
| `/api/analytics/**` | analytics-service:8080 |

---

## Event Schemas

### Event Envelope (All Events)

```json
{
  "eventId": "uuid",
  "eventType": "CallReceived|CallTranscribed|SentimentAnalyzed|VoCAAnalyzed|CallAudited",
  "aggregateId": "callId (uuid)",
  "aggregateType": "Call",
  "timestamp": "2024-01-15T10:30:00Z",
  "version": 1,
  "causationId": "uuid (parent event)",
  "correlationId": "uuid (trace across all events)",
  "metadata": {
    "userId": "system",
    "service": "service-name"
  },
  "payload": { }
}
```

### CallReceived Payload

```json
{
  "callId": "uuid",
  "callerId": "string",
  "agentId": "string",
  "channel": "INBOUND|OUTBOUND|INTERNAL",
  "startTime": "ISO-8601",
  "audioFileUrl": "minio://calls/2024/01/uuid.wav",
  "audioFormat": "wav",
  "audioFileSize": 1024000
}
```

### CallTranscribed Payload

```json
{
  "callId": "uuid",
  "transcription": {
    "fullText": "complete transcription",
    "segments": [
      {
        "speaker": "agent|customer",
        "startTime": 0.0,
        "endTime": 5.2,
        "text": "Hello, how can I help you?",
        "confidence": 0.95
      }
    ],
    "language": "en-US",
    "confidence": 0.95,
    "wordCount": 150
  }
}
```

### SentimentAnalyzed Payload

```json
{
  "callId": "uuid",
  "overallSentiment": "positive|negative|neutral",
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
  "escalationDetected": true,
  "escalationDetails": {
    "maxDrop": 0.7,
    "fromTime": 10.5,
    "toTime": 45.2
  }
}
```

### VoCAAnalyzed Payload

```json
{
  "callId": "uuid",
  "insights": {
    "primaryIntent": "complaint",
    "topics": ["billing", "unexpected_charges"],
    "keywords": ["overcharged", "refund", "cancel"],
    "customerSatisfaction": "low",
    "predictedChurnRisk": 0.78,
    "actionableItems": [
      {
        "category": "billing_issue",
        "priority": "high",
        "description": "Customer disputes billing amount"
      }
    ],
    "summary": "Customer called about unexpected charges"
  }
}
```

### CallAudited Payload

```json
{
  "callId": "uuid",
  "auditResults": {
    "overallScore": 85,
    "complianceStatus": "passed|failed|review_required",
    "violations": [
      {
        "ruleId": "GREETING_REQUIRED",
        "ruleName": "Greeting Required",
        "severity": "medium",
        "description": "Agent did not greet within 10 seconds"
      }
    ],
    "qualityMetrics": {
      "scriptAdherence": 90,
      "customerService": 80,
      "resolutionEffectiveness": 85
    },
    "flagsForReview": false,
    "reviewReason": null
  }
}
```

---

## Database Schema

Located at: `/schema.sql`

### Core Tables

| Table | Purpose |
|-------|---------|
| `calls` | Call metadata |
| `transcriptions` | Full transcription text |
| `segments` | Speaker-separated segments |
| `sentiment_results` | Overall sentiment per call |
| `segment_sentiments` | Sentiment per segment |
| `voc_insights` | VoC extracted insights |
| `audit_results` | Compliance audit results |
| `compliance_violations` | Specific violations |
| `compliance_rules` | Configurable rules |
| `notifications` | Alert history |
| `event_store` | Event sourcing audit trail |

### TimescaleDB Hypertables (Time-Series)

| Table | Purpose |
|-------|---------|
| `agent_performance` | Agent metrics over time |
| `compliance_metrics` | Daily compliance rates |
| `sentiment_trends` | Sentiment trends |

---

## Testing Plan

### Per-Service Testing

```bash
# Build and start service
docker compose up -d --build <service-name>

# Check logs
docker compose logs -f <service-name>

# Verify health
curl http://localhost:<port>/actuator/health

# Check Prometheus metrics
curl http://localhost:<port>/actuator/prometheus
```

### End-to-End Test

```bash
# 1. Upload audio file
curl -X POST http://localhost:8080/api/calls/upload \
  -F "file=@test-call.wav" \
  -F "callerId=+1234567890" \
  -F "agentId=agent-001"

# 2. Monitor event flow
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic calls.received \
  --from-beginning

# 3. Check transcription output
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic calls.transcribed

# 4. Verify all events published
for topic in calls.received calls.transcribed calls.sentiment-analyzed calls.voc-analyzed calls.audited; do
  echo "=== $topic ==="
  docker compose exec kafka /opt/kafka/bin/kafka-topics.sh \
    --describe --topic $topic --bootstrap-server localhost:9092
done
```

---

## Deployment Commands

### Build All Services

```bash
docker compose build
```

### Start Infrastructure

```bash
docker compose up -d kafka postgres minio opensearch valkey
docker compose up -d prometheus grafana jaeger otel-collector opensearch-dashboards
```

### Start Application Services

```bash
docker compose up -d call-ingestion-service
docker compose up -d transcription-service
docker compose up -d sentiment-service
docker compose up -d voc-service
docker compose up -d audit-service
docker compose up -d analytics-service
docker compose up -d notification-service
docker compose up -d api-gateway
```

### View All Logs

```bash
docker compose logs -f
```

---

## Monitoring URLs

| Service | URL | Credentials |
|---------|-----|-------------|
| Grafana | http://localhost:3000 | admin/admin |
| Jaeger | http://localhost:16686 | - |
| Prometheus | http://localhost:9090 | - |
| OpenSearch Dashboards | http://localhost:5601 | - |
| MinIO Console | http://localhost:9001 | minioadmin/minioadmin |

---

## Next Steps (Post-Implementation)

All 8 microservices have been implemented. The following are recommended next steps:

1. **Build and Deploy All Services** - `docker compose build && docker compose up -d`
2. **End-to-End Testing** - Upload a test audio file and verify the complete event flow
3. **Add Unit Tests** - JUnit 5 for Java services, pytest for Python services
4. **Add Integration Tests** - Use Testcontainers for Kafka, PostgreSQL
5. **Configure CI/CD** - GitHub Actions for automated builds and tests
6. **Add Authentication** - Spring Security with JWT tokens
7. **Create Grafana Dashboards** - Visualization for metrics and KPIs
8. **Production Hardening** - Rate limiting, HTTPS, secrets management

---

## Implementation Complete

**Date:** December 29, 2024

**Services Implemented:**
- Call Ingestion Service (Spring Boot) - Port 8081
- Transcription Service (Python/FastAPI) - Port 8082
- Sentiment Service (Python/FastAPI) - Port 8083
- VoC Service (Spring Boot) - Port 8084
- Audit Service (Spring Boot) - Port 8085
- Analytics Service (Spring Boot) - Port 8086
- Notification Service (Spring Boot) - Port 8087
- API Gateway (Spring Cloud Gateway) - Port 8080

**Infrastructure:**
- Kafka (5 topics configured)
- PostgreSQL with TimescaleDB (schema applied)
- MinIO (calls bucket created)
- OpenSearch, Valkey, Prometheus, Grafana, Jaeger

**Total Files Created:** ~150+ Java/Python source files across 8 services
