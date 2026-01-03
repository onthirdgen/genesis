# Audit Service

## Overview

The Audit Service is a Spring Boot microservice responsible for compliance evaluation and quality scoring of phone call recordings. It consumes events from Kafka, applies configurable compliance rules, calculates quality metrics, and publishes audit results.

## Architecture

This service is part of an event-driven microservices architecture:

- **Consumes**: `CallTranscribed`, `SentimentAnalyzed`, and `VoCAAnalyzed` events from Kafka
- **Produces**: `CallAudited` events to Kafka
- **Storage**: PostgreSQL database for audit results, violations, and compliance rules
- **Pattern**: Event aggregation - waits for all three input events before processing

## Features

### Core Functionality

1. **Compliance Rule Evaluation**
   - Keyword checks (greeting, disclosure, closing)
   - Prohibited word detection
   - Sentiment-based response validation
   - Configurable rules stored in database

2. **Quality Scoring**
   - Script Adherence (30% weight)
   - Customer Service (40% weight)
   - Resolution Effectiveness (30% weight)
   - Overall score: 0-100

3. **Compliance Status**
   - PASSED: No critical violations, score >= 70
   - FAILED: Critical violations or score < 50
   - REVIEW_REQUIRED: High violations or score 50-70

4. **Violation Tracking**
   - Severity levels: CRITICAL, HIGH, MEDIUM, LOW
   - Timestamp and evidence capture
   - Linked to audit results

## Technology Stack

- Java 21
- Spring Boot 3.2.5
- Spring Data JPA
- Spring Kafka
- PostgreSQL 16
- Lombok
- Jackson (JSON processing)
- Micrometer Prometheus

## Configuration

### Environment Variables

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/call_auditing
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres

# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# OpenTelemetry (auto-configured via Java agent)
OTEL_SERVICE_NAME=audit-service
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
```

### Application Configuration

Key settings in `application.yml`:

```yaml
audit:
  kafka:
    topics:
      transcribed: calls.transcribed
      sentiment-analyzed: calls.sentiment-analyzed
      voc-analyzed: calls.voc-analyzed
      audited: calls.audited
  scoring:
    weights:
      script-adherence: 0.30
      customer-service: 0.40
      resolution-effectiveness: 0.30
    thresholds:
      pass-score: 70
      review-score: 50
```

## API Endpoints

### Health Check
```
GET /api/audit/health
```

### Audit Results
```
GET /api/audit/calls/{callId}                    # Get audit result by call ID
GET /api/audit/calls/{callId}/violations          # Get violations for a call
GET /api/audit/status/{status}                    # Get audits by status (PASSED/FAILED/REVIEW_REQUIRED)
GET /api/audit/flagged                            # Get all flagged audits
```

### Reports
```
GET /api/audit/reports?startDate={iso}&endDate={iso}
```

Response includes:
- Total calls audited
- Compliance status breakdown
- Average scores
- Quality metrics averages
- Flagged count

### Violations
```
GET /api/audit/violations?ruleId={id}&severity={severity}
```

### Compliance Rules
```
GET /api/audit/rules                              # List all rules
GET /api/audit/rules/{ruleId}                     # Get specific rule
POST /api/audit/rules                             # Create new rule
PUT /api/audit/rules/{ruleId}                     # Update rule
DELETE /api/audit/rules/{ruleId}                  # Delete rule
```

## Compliance Rules

Pre-configured rules (from `schema.sql`):

1. **GREETING_REQUIRED** (Medium)
   - Agent must greet within first 10 seconds
   - Keywords: hello, hi, good morning, good afternoon, welcome

2. **DISCLOSURE_REQUIRED** (Critical)
   - Recording disclosure within 30 seconds
   - Keywords: "call may be recorded", "recording this call"

3. **NO_PROFANITY** (High)
   - Agent must not use prohibited words
   - Prohibited: damn, hell, crap

4. **EMPATHY_CHECK** (Low)
   - Express empathy when customer is negative
   - Keywords: understand, sorry, apologize, help

5. **CLOSING_REQUIRED** (Medium)
   - Proper call closing in last 30 seconds
   - Keywords: "thank you for calling", "have a great day"

### Rule Definition Format

Rules are stored as JSONB in the database:

```json
{
  "type": "keyword_check",
  "keywords": ["hello", "hi", "welcome"],
  "time_window": {"start": 0, "end": 10},
  "speaker": "agent"
}
```

Supported rule types:
- `keyword_check`: Required keywords in specific time windows
- `prohibited_words`: Words that should not appear
- `sentiment_response`: Required response to customer sentiment

## Quality Metrics Calculation

### Script Adherence (0-100)
- Checks for required phrases (greeting, opening, closing)
- Percentage of required phrases found

### Customer Service (0-100)
- Base score: 70
- Positive sentiment: +20
- Negative sentiment: -20
- Escalation detected: -15
- Empathy words (3+): +10

### Resolution Effectiveness (0-100)
- Base score: 60
- High customer satisfaction: +30
- Medium satisfaction: +10
- Low satisfaction: -20
- Complaint with actionable items: +10
- High churn risk (>0.7): -15

## Event Processing Flow

1. **Event Aggregation**
   - Listens to three Kafka topics concurrently
   - Stores events in memory by call ID
   - Triggers audit when all three events received

2. **Audit Execution**
   - Loads active compliance rules from database
   - Evaluates each rule against transcription/sentiment
   - Calculates quality metrics
   - Determines compliance status
   - Saves audit result and violations

3. **Event Publishing**
   - Publishes `CallAudited` event to Kafka
   - Includes overall score, compliance status, violations, quality metrics
   - Cleans up cached events

## Database Schema

### Tables Used

- `audit_results`: Main audit outcomes
- `compliance_violations`: Specific rule violations
- `compliance_rules`: Configurable audit rules

See `/Users/jon/AI/genesis/schema.sql` for complete schema.

## Building and Running

### Build
```bash
cd /Users/jon/AI/genesis/audit-service
./mvnw clean package
```

### Run Locally
```bash
./mvnw spring-boot:run
```

### Docker
```bash
# Build image
docker compose build audit-service

# Run service
docker compose up -d audit-service
```

## Monitoring

### Actuator Endpoints
```
GET /actuator/health       # Health check
GET /actuator/prometheus   # Prometheus metrics
GET /actuator/metrics      # Application metrics
```

### OpenTelemetry

The service is automatically instrumented with OpenTelemetry Java Agent:
- Distributed tracing to Jaeger
- Metrics to Prometheus
- Automatic span creation for HTTP, Kafka, and JDBC

View traces at: http://localhost:16686 (Jaeger UI)

## Development

### Package Structure

```
com.callaudit.audit/
├── AuditServiceApplication.java
├── config/
│   └── KafkaConfig.java
├── controller/
│   └── AuditController.java
├── event/
│   ├── BaseEvent.java
│   ├── CallAuditedEvent.java
│   ├── CallTranscribedEvent.java
│   ├── SentimentAnalyzedEvent.java
│   └── VocAnalyzedEvent.java
├── listener/
│   └── AuditEventListener.java
├── model/
│   ├── AuditResult.java
│   ├── ComplianceRule.java
│   ├── ComplianceStatus.java
│   ├── ComplianceViolation.java
│   └── ViolationSeverity.java
├── repository/
│   ├── AuditResultRepository.java
│   ├── ComplianceRuleRepository.java
│   └── ComplianceViolationRepository.java
└── service/
    ├── AuditService.java
    └── ComplianceRuleEngine.java
```

### Adding New Compliance Rules

1. Define rule in database:
```sql
INSERT INTO compliance_rules (id, name, description, category, severity, rule_definition)
VALUES ('MY_RULE', 'My Rule Name', 'Description', 'category', 'medium',
  '{"type": "keyword_check", "keywords": ["word1", "word2"], "speaker": "agent"}'::jsonb);
```

2. Or use REST API:
```bash
curl -X POST http://localhost:8085/api/audit/rules \
  -H "Content-Type: application/json" \
  -d '{
    "id": "MY_RULE",
    "name": "My Rule Name",
    "description": "Description",
    "category": "quality",
    "severity": "MEDIUM",
    "ruleDefinition": "{\"type\": \"keyword_check\", ...}",
    "isActive": true
  }'
```

### Extending Rule Types

To add a new rule type:

1. Update `ComplianceRuleEngine.evaluateRule()` switch statement
2. Add new evaluation method (e.g., `evaluateMyRuleType()`)
3. Define rule definition schema

## Troubleshooting

### Events Not Processing

Check logs for:
```
Waiting for more events for call ID: xxx
```

Ensure all three upstream services published their events.

### Kafka Connection Issues

Verify Kafka is running:
```bash
docker compose logs kafka
```

Check bootstrap servers configuration.

### Database Connection Issues

Verify PostgreSQL is running and schema is initialized:
```bash
docker compose logs postgres
docker compose exec postgres psql -U postgres -d call_auditing -c "\dt"
```

## Testing

### Manual Testing

1. Upload a call via Call Ingestion Service
2. Wait for Transcription, Sentiment, and VoC services to process
3. Audit Service automatically processes when all three complete
4. Check audit result:
```bash
curl http://localhost:8085/api/audit/calls/{callId}
```

### Kafka Event Testing

Publish test events to Kafka topics:
```bash
docker compose exec kafka kafka-console-producer --topic calls.transcribed --bootstrap-server localhost:9092
```

## License

Part of Call Auditing Platform - Internal Use
