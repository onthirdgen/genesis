# Call Auditing Platform - Quick Start Guide

**For detailed installation instructions, see [INSTALLATION.md](INSTALLATION.md)**

## Prerequisites

- Docker Desktop (with 8GB+ RAM allocated)
- 50GB free disk space
- Windows 10/11 or macOS 11+

## Installation (Fresh Machine)

### 1. Install Docker Desktop

**Windows**: https://www.docker.com/products/docker-desktop/ (enable WSL 2)
**Mac**: https://www.docker.com/products/docker-desktop/ (choose Apple Silicon or Intel)

Configure Docker: Settings > Resources > Set Memory to 8GB+

### 2. Get the Project

```bash
# Clone repository
git clone <repository-url>
cd call-auditing-platform

# OR extract ZIP and cd to directory
```

### 3. Start the Platform

```bash
# Pull images (first time only)
docker compose pull

# Build services (first time only)
docker compose build

# Start all services
docker compose up -d

# Initialize MinIO storage
docker compose exec minio mc alias set local http://localhost:9000 minioadmin minioadmin
docker compose exec minio mc mb local/calls

# Initialize database schema
docker compose exec -T postgres psql -U postgres -d call_auditing < schema.sql
```

### 4. Verify Installation

```bash
# Check services are running
docker compose ps

# Test Call Ingestion Service
curl http://localhost:8081/actuator/health
```

## Access Points

| Service | URL | Credentials |
|---------|-----|-------------|
| Call Ingestion | http://localhost:8081 | - |
| Transcription | http://localhost:8082 | - |
| Monitor Service | http://localhost:8088 | - |
| MinIO Console | http://localhost:9001 | minioadmin / minioadmin |
| Grafana | http://localhost:3000 | admin / admin |
| Jaeger | http://localhost:16686 | - |
| Prometheus | http://localhost:9090 | - |

## Common Commands

```bash
# Start all services
docker compose up -d

# Stop all services
docker compose down

# View logs (all services)
docker compose logs -f

# View logs (specific service)
docker compose logs -f call-ingestion-service

# Restart a service
docker compose restart call-ingestion-service

# Rebuild service after code changes
docker compose up -d --build transcription-service

# Check service status
docker compose ps

# Clean restart (keep data)
docker compose down && docker compose up -d

# Full reset (delete all data)
docker compose down -v && docker compose up -d
```

## Kafka Commands

```bash
# List topics (note: use full path to kafka-topics.sh)
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092

# Create topic
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic calls.received \
  --partitions 3 \
  --replication-factor 1

# Consume messages (note: use full path to kafka-console-consumer.sh)
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic calls.received \
  --from-beginning
```

## MinIO Commands

```bash
# List buckets
docker compose exec minio mc ls local

# List files in bucket
docker compose exec minio mc ls local/calls

# Access MinIO Console: http://localhost:9001
```

## Quick Troubleshooting

### Services won't start
```bash
# Check Docker is running
docker ps

# Check logs
docker compose logs <service-name>

# Increase Docker memory: Docker Desktop > Settings > Resources > 8GB+
```

### Port conflicts
```bash
# Find what's using port 8080
# Windows:
netstat -ano | findstr :8080

# Mac:
lsof -i :8080
```

### Out of memory
```bash
# Increase Docker Desktop memory allocation (Settings > Resources)
# OR reduce service memory in docker-compose.yml
# OR start services incrementally

docker compose up -d kafka postgres minio
# Wait 1 minute
docker compose up -d prometheus jaeger otel-collector
# Wait 1 minute
docker compose up -d call-ingestion-service transcription-service monitor-service
```

### Complete reset
```bash
docker compose down -v
docker system prune -f
docker compose pull
docker compose build --no-cache
docker compose up -d
```

## Development Commands

### Spring Boot Services

```bash
cd call-ingestion-service

# Build
./mvnw clean package

# Run tests
./mvnw test

# Run locally (outside Docker)
./mvnw spring-boot:run
```

### Python Services

```bash
cd transcription-service

# Setup virtual environment
python -m venv venv
source venv/bin/activate  # Mac/Linux
# venv\Scripts\activate   # Windows

# Install dependencies
pip install -r requirements.txt

# Run locally
uvicorn main:app --reload
```

## Resource Monitoring

```bash
# Real-time resource usage
docker stats

# Disk usage
docker system df

# Clean up unused resources
docker system prune -f
```

## Service Ports

### Implemented Services
| Service | Port | Type |
|---------|------|------|
| call-ingestion-service | 8081 | Spring Boot |
| transcription-service | 8082 | FastAPI |
| monitor-service | 8088 | Spring Boot |

### Infrastructure
| Service | Port | Type |
|---------|------|------|
| kafka | 9092/29092 | Kafka |
| postgres | 5432 | PostgreSQL |
| minio | 9000 | MinIO API |
| minio-console | 9001 | MinIO UI |
| opensearch | 9200 | OpenSearch |
| opensearch-dashboards | 5601 | OpenSearch UI |
| valkey | 6379 | Valkey (Redis) |
| prometheus | 9090 | Prometheus |
| grafana | 3000 | Grafana |
| jaeger | 16686 | Jaeger UI |
| otel-collector | 4318 | OTLP HTTP |

## Next Steps

1. **Read Architecture**: See `call_auditing_architecture.md`
2. **Development Guide**: See `CLAUDE.md`
3. **Full Install Guide**: See `INSTALLATION.md`
4. **Test Upload**: Upload sample audio file when service is implemented

## Getting Help

- Detailed troubleshooting: See [INSTALLATION.md](INSTALLATION.md#troubleshooting)
- Architecture details: See `call_auditing_architecture.md`
- Development patterns: See `CLAUDE.md`
