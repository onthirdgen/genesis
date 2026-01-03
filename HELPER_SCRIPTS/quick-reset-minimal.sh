#!/bin/bash
# quick-reset-minimal.sh - Complete data reset for Call Auditing Platform (without observability)
# This script resets all data while preserving configurations
# NOTE: This version excludes prometheus, grafana, jaeger, otel-collector

set -e  # Exit on error

echo "=========================================="
echo "Call Auditing Platform - Data Reset"
echo "(Minimal - No Observability Services)"
echo "=========================================="
echo ""

# Confirmation prompt
read -p "This will delete ALL data (Kafka, DB, MinIO, etc.). Continue? (yes/no): " confirm
if [ "$confirm" != "yes" ]; then
    echo "Reset cancelled."
    exit 0
fi

echo ""
echo "[1/10] Stopping all services..."
docker compose down -v

echo ""
echo "[2/10] Removing local logs..."
rm -rf logs/ */logs/ 2>/dev/null || true
echo "Local logs removed."

echo ""
echo "[3/10] Starting infrastructure services..."
docker compose up -d kafka postgres minio opensearch valkey

echo ""
echo "[4/10] Waiting for PostgreSQL to be ready..."
MAX_ATTEMPTS=30
ATTEMPT=0
until docker compose exec -T postgres pg_isready -U postgres > /dev/null 2>&1; do
    ATTEMPT=$((ATTEMPT + 1))
    if [ $ATTEMPT -ge $MAX_ATTEMPTS ]; then
        echo "ERROR: PostgreSQL did not become ready after $MAX_ATTEMPTS attempts"
        exit 1
    fi
    echo "  Waiting for PostgreSQL... ($ATTEMPT/$MAX_ATTEMPTS)"
    sleep 2
done
echo "PostgreSQL is ready."

echo ""
echo "[5/10] Initializing database schema..."
# Find schema.sql - check current dir first, then one level up
if [ -f schema.sql ]; then
    SCHEMA_FILE="schema.sql"
elif [ -f ../schema.sql ]; then
    SCHEMA_FILE="../schema.sql"
else
    echo "ERROR: schema.sql not found in current directory or parent directory."
    exit 1
fi
docker compose exec -T postgres psql -U postgres -d call_auditing < "$SCHEMA_FILE"
echo "Database schema applied successfully from $SCHEMA_FILE"

echo ""
echo "[6/10] Initializing MinIO..."
docker compose exec minio mc alias set local http://localhost:9000 minioadmin minioadmin 2>/dev/null || true
docker compose exec minio mc mb local/calls 2>/dev/null || echo "Bucket may already exist."
echo "MinIO initialized."

echo ""
echo "[7/10] Creating Kafka topics..."
for topic in calls.received calls.transcribed calls.sentiment-analyzed calls.voc-analyzed calls.audited test.publish; do
  echo "  Creating topic: $topic"
  docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --create \
    --bootstrap-server localhost:9092 \
    --topic $topic \
    --partitions 3 \
    --replication-factor 1 2>/dev/null || echo "  Topic $topic may already exist."
done
echo "Kafka topics created."

echo ""
echo "[8/10] Starting all application services..."
docker compose up -d

echo ""
echo "[9/10] Waiting for services to start (60 seconds)..."
sleep 60

echo ""
echo "[10/10] Verifying system health..."
echo ""

# Function to check health endpoint
check_health() {
    local service=$1
    local url=$2
    echo -n "  $service: "

    if curl -s -f "$url" > /dev/null 2>&1; then
        echo "✓ UP"
    else
        echo "✗ DOWN (check logs: docker compose logs $service)"
    fi
}

# Check Spring Boot services
check_health "api-gateway" "http://localhost:8080/actuator/health"
check_health "call-ingestion-service" "http://localhost:8081/actuator/health"
check_health "voc-service" "http://localhost:8084/actuator/health"
check_health "audit-service" "http://localhost:8085/actuator/health"
check_health "analytics-service" "http://localhost:8086/actuator/health"
check_health "notification-service" "http://localhost:8087/actuator/health"
check_health "monitor-service" "http://localhost:8088/api/publish/health"

# Check Python services
check_health "transcription-service" "http://localhost:8082/health"
check_health "sentiment-service" "http://localhost:8083/health"

echo ""
echo "=========================================="
echo "Reset Complete!"
echo "=========================================="
echo ""
echo "System Status:"
echo "  - All data volumes cleared"
echo "  - Database schema initialized"
echo "  - MinIO bucket created"
echo "  - Kafka topics created"
echo "  - All services started"
echo "  - Observability services SKIPPED (prometheus, grafana, jaeger, otel-collector)"
echo ""
echo "Next Steps:"
echo "  1. Check service logs: docker compose logs -f"
echo "  2. Test with: curl http://localhost:8081/actuator/health"
echo ""
echo "To enable observability, see TODO.md 'Restore Observability Stack'"
echo ""
echo "For detailed information, see RESET_DATA_GUIDE.md"
echo ""
