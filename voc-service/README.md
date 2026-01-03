# VoC Service (Voice of Customer)

A Spring Boot microservice that extracts customer insights from call transcriptions, including intent classification, topic extraction, keyword analysis, customer satisfaction assessment, and churn risk prediction.

## Features

- **Intent Classification**: Categorizes calls as INQUIRY, COMPLAINT, COMPLIMENT, REQUEST, or OTHER
- **Topic Extraction**: Identifies key topics discussed (Billing, Technical Support, Account Management, etc.)
- **Keyword Extraction**: Uses TF-IDF approach to extract the most relevant keywords from transcriptions
- **Satisfaction Mapping**: Maps sentiment analysis to customer satisfaction levels (LOW, MEDIUM, HIGH)
- **Churn Risk Prediction**: Calculates churn probability based on sentiment, satisfaction, and intent
- **Actionable Items**: Generates specific action items based on analysis results
- **Event Aggregation**: Waits for both transcription and sentiment events before processing
- **OpenSearch Integration**: Ready for full-text search and analytics (client configured)
- **REST API**: Query insights, trends, topics, and keywords

## Architecture

### Event-Driven Processing

```
CallTranscribed Event (Kafka) ──┐
                                 ├──> VoC Analysis ──> VocAnalyzed Event (Kafka)
SentimentAnalyzed Event (Kafka) ─┘                    └──> PostgreSQL (voc_insights)
```

The service aggregates events by `callId` and processes VoC analysis when both events are available.

### Technology Stack

- **Spring Boot 3.2.5** with Java 21
- **Spring Kafka** for event consumption and production
- **Spring Data JPA** with PostgreSQL
- **OpenSearch Java Client** (configured, ready for search integration)
- **Hibernate** with JSONB support for PostgreSQL
- **Lombok** for boilerplate reduction
- **Micrometer + Prometheus** for metrics

## Project Structure

```
voc-service/
├── src/main/java/com/callaudit/voc/
│   ├── VocServiceApplication.java          # Main application class
│   ├── config/
│   │   ├── KafkaConfig.java                # Kafka consumer/producer config
│   │   └── OpenSearchConfig.java           # OpenSearch client configuration
│   ├── controller/
│   │   └── VocController.java              # REST API endpoints
│   ├── event/
│   │   ├── CallTranscribedEvent.java       # Input event from transcription service
│   │   ├── SentimentAnalyzedEvent.java     # Input event from sentiment service
│   │   └── VocAnalyzedEvent.java           # Output event published after analysis
│   ├── listener/
│   │   └── VocEventListener.java           # Kafka listeners and event aggregation
│   ├── model/
│   │   ├── Intent.java                     # Intent enum (INQUIRY, COMPLAINT, etc.)
│   │   ├── Satisfaction.java               # Satisfaction enum (LOW, MEDIUM, HIGH)
│   │   └── VocInsight.java                 # JPA entity for VoC insights
│   ├── repository/
│   │   └── VocInsightRepository.java       # Spring Data JPA repository
│   └── service/
│       ├── VocAnalysisService.java         # Core VoC analysis logic
│       └── InsightService.java             # CRUD operations and aggregations
├── src/main/resources/
│   └── application.yml                      # Application configuration
├── Dockerfile                               # Multi-stage Docker build
└── pom.xml                                  # Maven dependencies
```

## VoC Analysis Logic

### 1. Keyword Extraction
- Tokenizes text and removes stopwords
- Calculates word frequency (TF approach)
- Returns top N keywords (configurable, default: 10)

### 2. Topic Extraction
- Maps keywords to predefined topic categories:
  - **Billing**: bill, charge, payment, invoice, price, cost, fee
  - **Technical Support**: technical, not working, broken, error, bug, crash
  - **Account Management**: account, login, password, username, profile
  - **Product Quality**: quality, product, defective, warranty, return, refund
  - **Customer Service**: service, representative, agent, support, help
  - **Delivery**: delivery, shipping, tracking, arrived, package, order
  - **Cancellation**: cancel, terminate, discontinue, stop, end

### 3. Intent Classification
- Scores text against keyword sets for each intent:
  - **COMPLAINT**: problem, issue, unhappy, dissatisfied, frustrated, angry, etc.
  - **INQUIRY**: question, ask, wondering, how, what, when, where, why, etc.
  - **COMPLIMENT**: great, excellent, wonderful, amazing, love, thank, etc.
  - **REQUEST**: need, want, require, request, please, can you, help me, etc.
- Adjusts scores based on sentiment (negative → boost COMPLAINT, positive → boost COMPLIMENT)
- Returns highest scoring intent

### 4. Churn Risk Calculation

```
churn_risk = (1 - sentiment_score) × 0.5
           + (satisfaction == LOW ? 0.3 : 0.0)
           + (intent == COMPLAINT ? 0.2 : 0.0)
```

Capped at 1.0 (100% risk).

### 5. Actionable Items Generation
- **High churn risk (≥ 0.7)**: Urgent contact, escalate to retention team, offer compensation
- **Medium churn risk (≥ 0.4)**: Follow up within 3 days, monitor account
- **Intent-based**: Complaint → resolution team, Inquiry → provide info, Request → process request
- **Topic-based**: Billing → review accuracy, Technical → create ticket, Cancellation → initiate save

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/call_auditing` | PostgreSQL connection URL |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | Database password |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:9092` | Kafka broker address |
| `OPENSEARCH_HOST` | `opensearch` | OpenSearch host |
| `OPENSEARCH_PORT` | `9200` | OpenSearch port |

### Application Properties

See `src/main/resources/application.yml` for full configuration including:
- Stopwords list for keyword extraction
- Max keywords limit (default: 10)
- Churn risk thresholds (high: 0.7, medium: 0.4)
- Kafka consumer/producer settings
- JPA/Hibernate settings

## API Endpoints

### VoC Insights

- `GET /api/voc/insights/{callId}` - Get insights for a specific call
- `GET /api/voc/insights` - Get all insights
- `GET /api/voc/insights/by-intent/{intent}` - Filter by intent (INQUIRY, COMPLAINT, etc.)
- `GET /api/voc/insights/by-satisfaction/{satisfaction}` - Filter by satisfaction (LOW, MEDIUM, HIGH)
- `GET /api/voc/insights/high-risk?threshold=0.7` - Get high churn risk customers
- `GET /api/voc/insights/date-range?start=2024-01-01T00:00:00&end=2024-12-31T23:59:59` - Date range query

### Analytics

- `GET /api/voc/trends` - Aggregate trends (intent distribution, satisfaction, avg churn risk)
- `GET /api/voc/topics?limit=10` - Top topics across all insights
- `GET /api/voc/keywords?limit=10` - Top keywords across all insights
- `GET /api/voc/distribution/intent` - Intent distribution counts
- `GET /api/voc/distribution/satisfaction` - Satisfaction distribution counts
- `GET /api/voc/metrics/churn-risk` - Average churn risk

### Health

- `GET /api/voc/health` - Service health check
- `GET /actuator/health` - Spring Boot actuator health
- `GET /actuator/prometheus` - Prometheus metrics

## Database Schema

The service uses JPA with Hibernate to auto-create the schema. The main table:

```sql
CREATE TABLE voc_insights (
    id VARCHAR(255) PRIMARY KEY,
    call_id VARCHAR(255) NOT NULL UNIQUE,
    primary_intent VARCHAR(50) NOT NULL,
    topics JSONB,
    keywords JSONB,
    customer_satisfaction VARCHAR(50) NOT NULL,
    predicted_churn_risk DOUBLE PRECISION NOT NULL,
    actionable_items JSONB,
    summary TEXT,
    created_at TIMESTAMP NOT NULL
);
```

## Kafka Topics

### Consumed Topics
- `calls.transcribed` - Receives transcription events
- `calls.sentiment-analyzed` - Receives sentiment analysis events

### Produced Topics
- `calls.voc-analyzed` - Publishes VoC analysis results

## Event Schema

### VocAnalyzed Event

```json
{
  "eventId": "uuid",
  "eventType": "VocAnalyzed",
  "aggregateId": "callId",
  "aggregateType": "Call",
  "timestamp": "2024-01-15T10:30:00Z",
  "version": 1,
  "correlationId": "uuid",
  "metadata": {
    "service": "voc-service",
    "userId": "system"
  },
  "payload": {
    "callId": "call-123",
    "primaryIntent": "COMPLAINT",
    "topics": ["Billing", "Customer Service"],
    "keywords": ["charge", "incorrect", "refund", "disappointed"],
    "customerSatisfaction": "LOW",
    "actionableItems": [
      "URGENT: Contact customer within 24 hours to address concerns",
      "Escalate to retention team",
      "Review billing accuracy"
    ],
    "predictedChurnRisk": 0.85,
    "summary": "Customer contact classified as complaint with low satisfaction level. HIGH churn risk detected. Primary topics: Billing, Customer Service."
  }
}
```

## Building and Running

### Build with Maven

```bash
./mvnw clean package
```

### Run Locally

```bash
./mvnw spring-boot:run
```

### Build Docker Image

```bash
docker compose build voc-service
```

### Run with Docker Compose

```bash
docker compose up -d voc-service
```

### View Logs

```bash
docker compose logs -f voc-service
```

## Testing

### Manual Testing Flow

1. **Start infrastructure services**:
   ```bash
   docker compose up -d kafka postgres
   ```

2. **Start VoC service**:
   ```bash
   docker compose up -d voc-service
   ```

3. **Publish test events** to Kafka topics `calls.transcribed` and `calls.sentiment-analyzed`

4. **Query insights**:
   ```bash
   curl http://localhost:8084/api/voc/insights/{callId}
   curl http://localhost:8084/api/voc/trends
   ```

### Sample Test Event (CallTranscribed)

```bash
docker compose exec kafka kafka-console-producer --broker-list localhost:9092 --topic calls.transcribed
```

Paste this JSON:
```json
{
  "eventId": "evt-123",
  "eventType": "CallTranscribed",
  "aggregateId": "call-456",
  "aggregateType": "Call",
  "timestamp": "2024-01-15T10:30:00Z",
  "version": 1,
  "correlationId": "corr-789",
  "metadata": {"service": "transcription-service"},
  "payload": {
    "callId": "call-456",
    "transcriptionText": "I'm very disappointed with the incorrect charge on my bill. I need a refund immediately.",
    "language": "en",
    "confidence": 0.95
  }
}
```

### Sample Test Event (SentimentAnalyzed)

```bash
docker compose exec kafka kafka-console-producer --broker-list localhost:9092 --topic calls.sentiment-analyzed
```

Paste this JSON:
```json
{
  "eventId": "evt-124",
  "eventType": "SentimentAnalyzed",
  "aggregateId": "call-456",
  "aggregateType": "Call",
  "timestamp": "2024-01-15T10:31:00Z",
  "version": 1,
  "correlationId": "corr-789",
  "metadata": {"service": "sentiment-service"},
  "payload": {
    "callId": "call-456",
    "overallSentiment": "NEGATIVE",
    "sentimentScore": -0.7,
    "positiveScore": 0.1,
    "negativeScore": 0.8,
    "neutralScore": 0.1
  }
}
```

After publishing both events, check the logs and query the API to see the VoC analysis result.

## Observability

### Metrics

Prometheus metrics are exposed at `/actuator/prometheus`:
- JVM metrics (memory, threads, GC)
- HTTP request metrics
- Kafka consumer/producer metrics
- Database connection pool metrics

Configure Prometheus to scrape:
```yaml
- job_name: 'voc-service'
  static_configs:
    - targets: ['voc-service:8080']
  metrics_path: '/actuator/prometheus'
```

### Distributed Tracing

The service is instrumented with OpenTelemetry Java Agent (configured in Dockerfile). Traces are exported to the OpenTelemetry Collector and viewable in Jaeger.

Trace propagation uses `correlationId` from events.

### Logging

Logs are written to stdout in JSON format (can be configured). Key log events:
- Event reception (INFO)
- VoC analysis processing (INFO)
- Event publishing (INFO)
- Errors (ERROR with stack traces)

## Future Enhancements

- [ ] **OpenSearch Integration**: Index insights for full-text search and advanced analytics
- [ ] **ML-Based Topic Modeling**: Replace keyword-based topic extraction with LDA or BERT
- [ ] **Advanced Churn Prediction**: Train ML model using historical churn data
- [ ] **Real-time Alerts**: Integrate with notification service for high-risk alerts
- [ ] **A/B Testing**: Support multiple analysis strategies
- [ ] **Trend Analysis**: Time-series analysis of topics and sentiments
- [ ] **Multi-language Support**: Extend keyword dictionaries for other languages
- [ ] **Custom Taxonomies**: Allow configuration of custom topics and intents per organization

## License

Part of the Call Auditing Platform with Voice of Customer (VoC) system.
