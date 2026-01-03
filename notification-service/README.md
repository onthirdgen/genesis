# Notification Service

Event-driven notification and alerting service for the Call Auditing Platform. Monitors call processing events and sends alerts for compliance violations, high churn risk, escalations, and review requirements.

## Overview

The Notification Service consumes events from three Kafka topics:
- `calls.sentiment-analyzed` - Monitors for escalations and high churn risk
- `calls.voc-analyzed` - Monitors for critical themes and review requirements
- `calls.audited` - Monitors for compliance violations

When alert conditions are met, notifications are created and sent via configured channels (EMAIL, SLACK, WEBHOOK).

## Architecture

```
Kafka Topics → Event Listener → Alert Rule Engine → Notification Service → Channels
                                        ↓
                                  PostgreSQL
```

### Components

1. **Event Listeners** (`NotificationEventListener.java`)
   - Kafka consumers for three topics
   - Extracts event data and evaluates alert conditions

2. **Alert Rule Engine** (`AlertRuleEngine.java`)
   - Determines if events warrant notifications
   - Calculates priority levels
   - Routes to appropriate recipients

3. **Notification Service** (`NotificationService.java`)
   - Creates and persists notifications
   - Sends via configured channels
   - Handles retries for failed deliveries

4. **REST API** (`NotificationController.java`)
   - Query notifications
   - Resend failed notifications
   - Filter by call, status, or type

## Alert Triggers

### 1. Compliance Violations
**Triggered when:**
- Violations have CRITICAL or HIGH severity
- Compliance score < 0.6
- Audit flags call for review

**Source:** `calls.audited` topic

**Example:**
```json
{
  "eventType": "CallAudited",
  "payload": {
    "complianceScore": 0.45,
    "violations": [
      {
        "severity": "CRITICAL",
        "rule": "PCI Compliance",
        "description": "Credit card information disclosed"
      }
    ]
  }
}
```

### 2. High Churn Risk
**Triggered when:**
- Predicted churn risk >= 0.7 (configurable)
- Priority escalates at >= 0.9 (URGENT)

**Source:** `calls.sentiment-analyzed` topic

**Example:**
```json
{
  "eventType": "SentimentAnalyzed",
  "payload": {
    "predictedChurnRisk": 0.85,
    "overallSentiment": "NEGATIVE",
    "customerSentiment": "FRUSTRATED"
  }
}
```

### 3. Escalation Detection
**Triggered when:**
- `escalationDetected` = true in sentiment analysis
- Always URGENT priority
- Routed to Slack for immediate visibility

**Source:** `calls.sentiment-analyzed` topic

**Example:**
```json
{
  "eventType": "SentimentAnalyzed",
  "payload": {
    "escalationDetected": true,
    "escalationTimestamp": "2025-12-29T10:45:32Z",
    "overallSentiment": "VERY_NEGATIVE"
  }
}
```

### 4. Review Required
**Triggered when:**
- VoC or Audit analysis flags for manual review
- Critical themes detected in VoC
- Audit identifies edge cases

**Source:** `calls.voc-analyzed` or `calls.audited` topics

## API Endpoints

All endpoints are prefixed with `/api/notifications`

### Get All Notifications
```bash
GET /api/notifications
```

**Response:**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "callId": "call-123",
    "notificationType": "COMPLIANCE_VIOLATION",
    "recipient": "supervisor@company.com",
    "channel": "EMAIL",
    "subject": "COMPLIANCE VIOLATION - Call call-123",
    "body": "Compliance issues detected...",
    "priority": "URGENT",
    "status": "SENT",
    "sentAt": "2025-12-29T10:45:32Z",
    "createdAt": "2025-12-29T10:45:30Z"
  }
]
```

### Get Notification by ID
```bash
GET /api/notifications/{id}
```

### Get Notifications for a Call
```bash
GET /api/notifications/call/{callId}
```

**Example:**
```bash
curl http://localhost:8087/api/notifications/call/call-123
```

### Filter by Status
```bash
GET /api/notifications/status/{status}
```

**Status values:** PENDING, SENT, FAILED

**Example:**
```bash
curl http://localhost:8087/api/notifications/status/FAILED
```

### Resend Failed Notification
```bash
POST /api/notifications/{id}/resend
```

**Example:**
```bash
curl -X POST http://localhost:8087/api/notifications/550e8400-e29b-41d4-a716-446655440000/resend
```

## Configuration

### Application Configuration (`application.yml`)

```yaml
notification:
  thresholds:
    churnRiskThreshold: 0.7  # Trigger alerts when churn risk >= this value
    escalationEnabled: true   # Enable/disable escalation alerts
  channels:
    email:
      enabled: true
      from: noreply@callaudit.com
      smtpHost: ${SMTP_HOST:localhost}
      smtpPort: ${SMTP_PORT:587}
    slack:
      enabled: true
      webhookUrl: ${SLACK_WEBHOOK_URL:}
    webhook:
      enabled: true
      retryAttempts: 3
      timeoutSeconds: 10
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker addresses | `localhost:9092` |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/call_auditing` |
| `SPRING_DATASOURCE_USERNAME` | Database username | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | `postgres` |
| `SMTP_HOST` | Email SMTP server | `localhost` |
| `SMTP_PORT` | Email SMTP port | `587` |
| `SLACK_WEBHOOK_URL` | Slack incoming webhook URL | (empty) |

## Development

### Build the Service

```bash
./mvnw clean package
```

### Run Locally

```bash
./mvnw spring-boot:run
```

The service will start on port 8080.

### Run with Docker

```bash
# Build Docker image
docker compose build notification-service

# Run the service
docker compose up -d notification-service

# View logs
docker compose logs -f notification-service
```

The service is exposed on port **8087** externally (mapped from internal port 8080).

### Database Schema

The service uses JPA with `ddl-auto=update` to automatically create the notifications table:

```sql
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    call_id VARCHAR(255) NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    body TEXT NOT NULL,
    priority VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    sent_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_call_id ON notifications(call_id);
CREATE INDEX idx_status ON notifications(status);
CREATE INDEX idx_notification_type ON notifications(notification_type);
CREATE INDEX idx_created_at ON notifications(created_at);
```

## Notification Channels

### Current Implementation (Stubs)

All channel implementations are **stubs** that log to the console. This allows the service to run and test the event processing logic without requiring external integrations.

**Email Stub:**
```java
log.info("[EMAIL] To: {}, Subject: {}", recipient, subject);
```

**Slack Stub:**
```java
log.info("[SLACK] To: {}, Message: {}", recipient, subject);
```

**Webhook Stub:**
```java
log.info("[WEBHOOK] To: {}, Payload: {}", recipient, subject);
```

### Production Integration

To integrate real notification channels:

#### Email (SMTP)

Add JavaMail dependency to `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

Update `sendEmail()` method in `NotificationService.java`:
```java
@Autowired
private JavaMailSender mailSender;

private void sendEmail(Notification notification) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom("noreply@callaudit.com");
    message.setTo(notification.getRecipient());
    message.setSubject(notification.getSubject());
    message.setText(notification.getBody());
    mailSender.send(message);
}
```

#### Slack (Incoming Webhooks)

Add HTTP client dependency:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

Update `sendSlack()` method:
```java
@Autowired
private WebClient webClient;

@Value("${notification.channels.slack.webhookUrl}")
private String slackWebhookUrl;

private void sendSlack(Notification notification) {
    Map<String, Object> payload = Map.of(
        "text", notification.getSubject(),
        "blocks", List.of(
            Map.of("type", "section", "text",
                Map.of("type", "mrkdwn", "text", notification.getBody()))
        )
    );

    webClient.post()
        .uri(slackWebhookUrl)
        .bodyValue(payload)
        .retrieve()
        .bodyToMono(String.class)
        .block();
}
```

#### Webhook (HTTP POST)

```java
@Autowired
private RestTemplate restTemplate;

private void sendWebhook(Notification notification) {
    Map<String, Object> payload = Map.of(
        "callId", notification.getCallId(),
        "type", notification.getNotificationType(),
        "priority", notification.getPriority(),
        "message", notification.getBody()
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
    restTemplate.postForEntity(webhookUrl, request, String.class);
}
```

## Monitoring

### Health Endpoint
```bash
curl http://localhost:8087/actuator/health
```

### Prometheus Metrics
```bash
curl http://localhost:8087/actuator/prometheus
```

Key metrics:
- `kafka_consumer_records_consumed_total` - Events processed
- `notification_created_total` - Notifications created
- `notification_sent_total` - Notifications sent successfully
- `notification_failed_total` - Failed notification attempts

### OpenTelemetry Tracing

The service is instrumented with OpenTelemetry Java Agent. Traces are exported to Jaeger:

- View traces at: http://localhost:16686
- Service name: `notification-service`

## Testing Event Flow

### 1. Start Infrastructure

```bash
docker compose up -d kafka postgres otel-collector
```

### 2. Start Notification Service

```bash
docker compose up -d notification-service
```

### 3. Publish Test Event

Create a test sentiment event with escalation:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic calls.sentiment-analyzed
```

Then paste this JSON:

```json
{
  "eventId": "evt-001",
  "eventType": "SentimentAnalyzed",
  "aggregateId": "call-test-123",
  "timestamp": "2025-12-29T10:00:00Z",
  "payload": {
    "callId": "call-test-123",
    "escalationDetected": true,
    "predictedChurnRisk": 0.85,
    "overallSentiment": "NEGATIVE"
  }
}
```

### 4. Verify Notification Created

```bash
curl http://localhost:8087/api/notifications/call/call-test-123
```

### 5. Check Logs

```bash
docker compose logs notification-service | grep -A 5 "Alert triggered"
```

You should see:
```
Alert triggered for sentiment analysis on call: call-test-123
Created notification: <UUID> for call: call-test-123 with type: ESCALATION
[SLACK] To: supervisor@company.com, Message: ESCALATION DETECTED - Call call-test-123
Successfully sent notification: <UUID>
```

## Troubleshooting

### Notifications Not Being Created

1. **Check Kafka connectivity:**
   ```bash
   docker compose logs notification-service | grep "Kafka"
   ```

2. **Verify topics exist:**
   ```bash
   docker compose exec kafka /opt/kafka/bin/kafka-topics.sh \
     --list --bootstrap-server localhost:9092
   ```

3. **Check consumer group status:**
   ```bash
   docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
     --bootstrap-server localhost:9092 \
     --describe --group notification-service
   ```

### Notifications Stuck in PENDING

- Verify channel configurations in `application.yml`
- Check logs for send errors
- For real integrations, verify SMTP/Slack/Webhook credentials

### Database Connection Errors

1. **Verify PostgreSQL is running:**
   ```bash
   docker compose ps postgres
   ```

2. **Check datasource configuration:**
   ```bash
   docker compose logs notification-service | grep "datasource"
   ```

## Future Enhancements

1. **Recipient Management**
   - Database-driven recipient routing
   - Role-based distribution lists
   - Escalation chains

2. **Advanced Alert Rules**
   - Time-based rules (business hours only)
   - Rate limiting (prevent alert storms)
   - Alert aggregation (batch similar alerts)

3. **Delivery Enhancements**
   - Retry with exponential backoff
   - Dead letter queue for failed notifications
   - Delivery status webhooks

4. **Templates**
   - HTML email templates
   - Slack block kit templates
   - Multi-language support

5. **Analytics**
   - Notification delivery metrics
   - Alert effectiveness tracking
   - Recipient engagement analytics

## License

Part of the Call Auditing Platform - Internal Use Only
