#!/bin/bash
# rebuild-and-deploy.sh - Rebuild and deploy all Call Auditing Platform services
# This script rebuilds both backend and frontend services and deploys them

set -e  # Exit on error

echo "=========================================="
echo "Call Auditing Platform - Rebuild & Deploy"
echo "=========================================="
echo ""

# Configuration
BACKEND_SERVICES=(
    "api-gateway"
    "call-ingestion-service"
    "transcription-service"
    "sentiment-service"
    "voc-service"
    "audit-service"
    "analytics-service"
    "notification-service"
    "monitor-service"
)

FRONTEND_SERVICES=(
    "call-auditing-ui"
)

ALL_SERVICES=("${BACKEND_SERVICES[@]}" "${FRONTEND_SERVICES[@]}")

# Function to print step header
step() {
    echo ""
    echo "[$1] $2"
    echo "----------------------------------------"
}

# Parse command line arguments
REBUILD_ALL=false
REBUILD_BACKEND=false
REBUILD_FRONTEND=false
NO_CACHE=false
KEEP_DATA=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --all)
            REBUILD_ALL=true
            shift
            ;;
        --backend)
            REBUILD_BACKEND=true
            shift
            ;;
        --frontend)
            REBUILD_FRONTEND=true
            shift
            ;;
        --no-cache)
            NO_CACHE=true
            shift
            ;;
        --keep-data)
            KEEP_DATA=true
            shift
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --all          Rebuild all services (backend + frontend)"
            echo "  --backend      Rebuild only backend services"
            echo "  --frontend     Rebuild only frontend services"
            echo "  --no-cache     Build without using Docker cache"
            echo "  --keep-data    Keep data volumes (don't use -v flag)"
            echo "  --help         Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0 --all                    # Rebuild everything"
            echo "  $0 --backend                # Rebuild only backend"
            echo "  $0 --frontend --no-cache    # Rebuild frontend without cache"
            echo "  $0 --all --keep-data        # Rebuild all, preserve data"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Default to all if no specific rebuild option specified
if [ "$REBUILD_ALL" = false ] && [ "$REBUILD_BACKEND" = false ] && [ "$REBUILD_FRONTEND" = false ]; then
    REBUILD_ALL=true
fi

# Determine which services to rebuild
SERVICES_TO_REBUILD=()

if [ "$REBUILD_ALL" = true ]; then
    SERVICES_TO_REBUILD=("${ALL_SERVICES[@]}")
else
    if [ "$REBUILD_BACKEND" = true ]; then
        SERVICES_TO_REBUILD+=("${BACKEND_SERVICES[@]}")
    fi
    if [ "$REBUILD_FRONTEND" = true ]; then
        SERVICES_TO_REBUILD+=("${FRONTEND_SERVICES[@]}")
    fi
fi

echo "Services to rebuild: ${SERVICES_TO_REBUILD[*]}"
echo ""

# Confirmation
read -p "Continue with rebuild and deployment? (yes/no): " confirm
if [ "$confirm" != "yes" ]; then
    echo "Deployment cancelled."
    exit 0
fi

# Step 1: Stop all services
step "1/5" "Stopping all services..."
if [ "$KEEP_DATA" = true ]; then
    docker compose down
    echo "Services stopped (data volumes preserved)"
else
    docker compose down -v
    echo "Services stopped (data volumes removed)"
fi

# Step 2: Rebuild services
step "2/5" "Rebuilding services..."

BUILD_ARGS=""
if [ "$NO_CACHE" = true ]; then
    BUILD_ARGS="--no-cache"
fi

if [ ${#SERVICES_TO_REBUILD[@]} -eq ${#ALL_SERVICES[@]} ]; then
    # Rebuild all services
    echo "Building all services..."
    docker compose build $BUILD_ARGS
else
    # Rebuild specific services
    for service in "${SERVICES_TO_REBUILD[@]}"; do
        echo "Building $service..."
        docker compose build $BUILD_ARGS "$service"
    done
fi

echo "All services rebuilt successfully"

# Step 3: Start infrastructure services first
step "3/5" "Starting infrastructure services..."
docker compose up -d kafka postgres minio opensearch valkey prometheus jaeger otel-collector grafana opensearch-dashboards

echo "Waiting for infrastructure to be ready (30 seconds)..."
sleep 30

# Step 4: Initialize database and MinIO (if data was cleared)
if [ "$KEEP_DATA" = false ]; then
    step "4/5" "Initializing database and MinIO..."

    # Initialize database schema
    if [ -f ../schema.sql ]; then
        docker compose exec -T postgres psql -U postgres -d call_auditing < ../schema.sql
        echo "✓ Database schema initialized"
    elif [ -f schema.sql ]; then
        docker compose exec -T postgres psql -U postgres -d call_auditing < schema.sql
        echo "✓ Database schema initialized"
    else
        echo "⚠ WARNING: schema.sql not found, skipping database initialization"
    fi

    # Initialize MinIO
    docker compose exec minio mc alias set local http://localhost:9000 minioadmin minioadmin 2>/dev/null || true
    docker compose exec minio mc mb local/calls 2>/dev/null || echo "✓ MinIO bucket ready"

    # Create Kafka topics
    echo "Creating Kafka topics..."
    for topic in calls.received calls.transcribed calls.sentiment-analyzed calls.voc-analyzed calls.audited test.publish; do
        docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --create \
            --bootstrap-server localhost:9092 \
            --topic $topic \
            --partitions 3 \
            --replication-factor 1 2>/dev/null || echo "  ✓ Topic $topic ready"
    done
else
    step "4/5" "Skipping data initialization (--keep-data flag used)..."
fi

# Step 5: Start all application services
step "5/5" "Starting all application services..."
docker compose up -d

echo "Waiting for services to start (60 seconds)..."
sleep 60

# Verify system health
echo ""
echo "=========================================="
echo "Verifying Service Health"
echo "=========================================="
echo ""

# Function to check health endpoint
check_health() {
    local service=$1
    local url=$2
    echo -n "  $service: "

    if curl -s -f "$url" > /dev/null 2>&1; then
        echo "✓ UP"
        return 0
    else
        echo "✗ DOWN (check logs: docker compose logs $service)"
        return 1
    fi
}

# Check backend services
echo "Backend Services:"
check_health "api-gateway" "http://localhost:8080/actuator/health"
check_health "call-ingestion-service" "http://localhost:8081/actuator/health"
check_health "transcription-service" "http://localhost:8082/health"
check_health "sentiment-service" "http://localhost:8083/health"
check_health "voc-service" "http://localhost:8084/actuator/health"
check_health "audit-service" "http://localhost:8085/actuator/health"
check_health "analytics-service" "http://localhost:8086/actuator/health"
check_health "notification-service" "http://localhost:8087/actuator/health"
check_health "monitor-service" "http://localhost:8088/api/publish/health"

# Check frontend services
echo ""
echo "Frontend Services:"
if docker compose ps call-auditing-ui 2>/dev/null | grep -q "Up"; then
    check_health "call-auditing-ui" "http://localhost:3001" || echo "  (UI may be starting, check: docker compose logs call-auditing-ui)"
else
    echo "  call-auditing-ui: ⚠ Not deployed in docker-compose.yml"
fi

# Summary
echo ""
echo "=========================================="
echo "Deployment Complete!"
echo "=========================================="
echo ""

# Count services
total_services=${#SERVICES_TO_REBUILD[@]}
echo "Services rebuilt: $total_services"
echo "  ${SERVICES_TO_REBUILD[*]}"
echo ""

echo "System Endpoints:"
echo "  Backend API:        http://localhost:8080"
echo "  Frontend UI:        http://localhost:3001"
echo "  Grafana:            http://localhost:3000 (admin/admin)"
echo "  Prometheus:         http://localhost:9090"
echo "  Jaeger:             http://localhost:16686"
echo "  OpenSearch:         http://localhost:9200"
echo "  OpenSearch Dash:    http://localhost:5601"
echo ""

echo "Useful Commands:"
echo "  View logs:          docker compose logs -f [service-name]"
echo "  Check status:       docker compose ps"
echo "  Restart service:    docker compose restart [service-name]"
echo "  Stop all:           docker compose down"
echo ""

if [ "$KEEP_DATA" = false ]; then
    echo "Note: All data was cleared during this deployment"
else
    echo "Note: Existing data was preserved"
fi

echo ""
