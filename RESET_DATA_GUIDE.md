# Call Auditing Platform - Data Reset Guide

This guide provides step-by-step instructions to reset all non-configuration data in the Call Auditing Platform, returning it to a freshly-installed state while preserving all configurations.

## What Gets Reset

✅ **Data that will be cleared:**
- All Kafka topics and messages (call events, transcriptions, sentiment data)
- PostgreSQL/TimescaleDB data (calls, transcriptions, audit results, VoC insights)
- MinIO objects (uploaded audio files)
- OpenSearch indices (search data, transcriptions)
- Valkey cache data
- Prometheus metrics history
- Grafana dashboards (if not saved to files)
- Jaeger traces
- Service logs

❌ **What stays intact:**
- Docker images
- Application configuration files (application.yml, docker-compose.yml, etc.)
- Source code
- Monitoring configuration (prometheus.yml, otel-collector-config.yaml)
- Database schema definitions

## Prerequisites

- Docker and Docker Compose installed
- No critical data that needs to be backed up
- Terminal access to the project directory

---

## Step-by-Step Reset Procedure

### Step 1: Stop All Running Services

Stop all containers gracefully:

```bash
cd /Users/jon/AI/genesis
docker compose down
```

**Expected output:**
```
Container genesis-transcription-service-1 Stopping
Container genesis-sentiment-service-1 Stopping
...
Container genesis-kafka-1 Removed
Container genesis-postgres-1 Removed
```

**Verification:**
```bash
docker compose ps
```
Should show no running containers.

---

### Step 2: Remove All Data Volumes

Remove all Docker volumes containing data:

```bash
docker compose down -v
```

The `-v` flag removes all named volumes defined in docker-compose.yml.

**Volumes that will be deleted:**
- `kafka-data` - All Kafka topics, messages, and offsets
- `postgres-data` - All database tables and data
- `minio-data` - All uploaded audio files
- `opensearch-data` - All search indices
- `valkey-data` - All cached data
- `whisper-models` - Downloaded Whisper models (will be re-downloaded)
- `prometheus-data` - Metrics history
- `grafana-data` - Dashboard configurations
- `jaeger-data` - Trace data

**Expected output:**
```
Container genesis-kafka-1 Removed
Volume genesis_kafka-data Removed
Volume genesis_postgres-data Removed
Volume genesis_minio-data Removed
...
```

---

### Step 3: Clean Up Local Log Files (Optional)

Remove local log files created by services:

```bash
# Remove all service logs
rm -rf logs/
rm -rf */logs/

# Or remove specific service logs
rm -f call-ingestion-service/logs/*.log
rm -f consumer-service/logs/*.log
rm -f voc-service/logs/*.log
# ... etc for other services
```

**Note:** These will be recreated automatically when services start.

---

### Step 4: Verify Volume Removal

Confirm all data volumes are removed:

```bash
docker volume ls | grep genesis
```

**Expected output:** No volumes should be listed (empty result).

If any volumes remain, remove them manually:
```bash
docker volume rm genesis_kafka-data
docker volume rm genesis_postgres-data
# ... etc
```

---

### Step 5: Restart Infrastructure Services

Start only the infrastructure services first:

```bash
docker compose up -d kafka postgres minio opensearch valkey
```

**Wait for services to be ready (30-60 seconds):**

```bash
# Check service status
docker compose ps kafka postgres minio opensearch valkey
```

All services should show `Up` status.

**Verification:**
```bash
# Test Kafka
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092

# Test PostgreSQL
docker compose exec postgres psql -U postgres -d call_auditing -c "\dt"

# Test MinIO (should be empty)
docker compose exec minio mc alias set local http://localhost:9000 minioadmin minioadmin
docker compose exec minio mc ls local/
```

---

### Step 6: Initialize Database Schema

Apply the database schema:

```bash
docker compose exec -T postgres psql -U postgres -d call_auditing < schema.sql
```

**Expected output:**
```
CREATE TABLE
CREATE TABLE
CREATE INDEX
...
```

**Verification:**
```bash
docker compose exec postgres psql -U postgres -d call_auditing -c "\dt"
```

Should list all tables: `calls`, `transcriptions`, `sentiment_results`, etc.

---

### Step 7: Initialize MinIO Bucket

Create the bucket for storing audio files:

```bash
# Set up MinIO alias
docker compose exec minio mc alias set local http://localhost:9000 minioadmin minioadmin

# Create calls bucket
docker compose exec minio mc mb local/calls

# Verify bucket creation
docker compose exec minio mc ls local/
```

**Expected output:**
```
Bucket created successfully 'local/calls'.
[2025-12-30 01:00:00 UTC]     0B calls/
```

---

### Step 8: Create Kafka Topics

Recreate required Kafka topics:

```bash
# Create calls.received topic
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic calls.received \
  --partitions 3 \
  --replication-factor 1

# Create calls.transcribed topic
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic calls.transcribed \
  --partitions 3 \
  --replication-factor 1

# Create calls.sentiment-analyzed topic
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic calls.sentiment-analyzed \
  --partitions 3 \
  --replication-factor 1

# Create calls.voc-analyzed topic
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic calls.voc-analyzed \
  --partitions 3 \
  --replication-factor 1

# Create calls.audited topic
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic calls.audited \
  --partitions 3 \
  --replication-factor 1

# Create test.publish topic (for consumer-service)
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic test.publish \
  --partitions 3 \
  --replication-factor 1
```

**Verification:**
```bash
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092
```

Should list all created topics.

---

### Step 9: Start Application Services

Start all application services:

```bash
docker compose up -d
```

**Wait for services to start (1-2 minutes):**

```bash
# Check all services are running
docker compose ps
```

All services should show `Up` status.

---

### Step 10: Start Monitoring Services

Monitoring services should already be started from Step 9, but verify:

```bash
docker compose ps prometheus grafana jaeger otel-collector
```

**Access monitoring UIs:**
- Grafana: http://localhost:3000 (admin/admin)
- Prometheus: http://localhost:9090
- Jaeger: http://localhost:16686
- OpenSearch Dashboards: http://localhost:5601

---

### Step 11: Verify System Health

Check that all services are healthy:

```bash
# Check call-ingestion-service health
curl http://localhost:8081/actuator/health

# Check transcription-service health
curl http://localhost:8082/health

# Check sentiment-service health
curl http://localhost:8083/health

# Check consumer-service health
curl http://localhost:8088/api/publish/health

# Check other Spring Boot services
curl http://localhost:8084/actuator/health  # voc-service
curl http://localhost:8085/actuator/health  # audit-service
curl http://localhost:8086/actuator/health  # analytics-service
curl http://localhost:8087/actuator/health  # notification-service
```

All should return `{"status":"UP"}` or similar.

---

### Step 12: Verify Kafka Topics Are Empty

Confirm all topics have no messages:

```bash
# Check calls.received topic
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic calls.received \
  --from-beginning \
  --max-messages 1 \
  --timeout-ms 5000

# Should timeout with no messages
```

Repeat for other topics to verify they're empty.

---

### Step 13: Verify Database Is Clean

Check that database tables exist but are empty:

```bash
# Count rows in calls table
docker compose exec postgres psql -U postgres -d call_auditing -c "SELECT COUNT(*) FROM calls;"

# Should return: 0
```

Repeat for other tables: `transcriptions`, `sentiment_results`, `voc_insights`, etc.

---

### Step 14: Verify MinIO Is Empty

Check that the calls bucket exists but has no files:

```bash
docker compose exec minio mc ls local/calls/
```

**Expected output:** Empty (no files listed).

---

## Quick Reset Script

For convenience, here's a one-command reset:

```bash
#!/bin/bash
# quick-reset.sh - Complete data reset

echo "Stopping all services..."
docker compose down -v

echo "Removing local logs..."
rm -rf logs/ */logs/

echo "Starting infrastructure..."
docker compose up -d kafka postgres minio opensearch valkey

echo "Waiting for services to be ready..."
sleep 30

echo "Initializing database..."
docker compose exec -T postgres psql -U postgres -d call_auditing < schema.sql

echo "Initializing MinIO..."
docker compose exec minio mc alias set local http://localhost:9000 minioadmin minioadmin
docker compose exec minio mc mb local/calls

echo "Creating Kafka topics..."
for topic in calls.received calls.transcribed calls.sentiment-analyzed calls.voc-analyzed calls.audited test.publish; do
  docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --create \
    --bootstrap-server localhost:9092 \
    --topic $topic \
    --partitions 3 \
    --replication-factor 1
done

echo "Starting all services..."
docker compose up -d

echo "Reset complete! Waiting for services to start..."
sleep 60

echo "Verifying system health..."
curl -s http://localhost:8081/actuator/health | jq .
curl -s http://localhost:8082/health | jq .
curl -s http://localhost:8083/health | jq .

echo "Done! System is ready to use."
```

**To use:**
```bash
chmod +x quick-reset.sh
./quick-reset.sh
```

---

## Troubleshooting

### Issue: Services won't start after reset

**Solution:**
```bash
# Check service logs
docker compose logs <service-name>

# Common fixes:
docker compose restart <service-name>
docker compose up -d --build <service-name>
```

### Issue: Database schema fails to apply

**Solution:**
```bash
# Drop and recreate database
docker compose exec postgres psql -U postgres -c "DROP DATABASE call_auditing;"
docker compose exec postgres psql -U postgres -c "CREATE DATABASE call_auditing;"
docker compose exec -T postgres psql -U postgres -d call_auditing < schema.sql
```

### Issue: MinIO bucket creation fails

**Solution:**
```bash
# Restart MinIO and try again
docker compose restart minio
sleep 10
docker compose exec minio mc alias set local http://localhost:9000 minioadmin minioadmin
docker compose exec minio mc mb local/calls
```

### Issue: Kafka topics already exist error

**Solution:**
```bash
# Delete existing topics first
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --delete \
  --bootstrap-server localhost:9092 \
  --topic <topic-name>

# Then recreate
```

---

## Post-Reset Checklist

- [ ] All services are running (`docker compose ps` shows all `Up`)
- [ ] Database has empty tables (`SELECT COUNT(*) FROM calls;` returns 0)
- [ ] MinIO bucket exists but is empty
- [ ] Kafka topics exist but have no messages
- [ ] Health endpoints return `{"status":"UP"}`
- [ ] Monitoring UIs are accessible (Grafana, Prometheus, Jaeger)
- [ ] No error messages in logs (`docker compose logs | grep -i error`)

---

## What's Next?

After resetting, you can:

1. **Test the system** by uploading a test audio file via call-ingestion-service
2. **Monitor events** using the consumer-service or Kafka console consumer
3. **Check processing** in service logs (`docker compose logs -f transcription-service`)
4. **View metrics** in Grafana dashboards
5. **Trace requests** through Jaeger UI

The system is now in a fresh state, ready for development and testing!
