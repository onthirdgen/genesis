# Call Auditing Platform - Complete Installation Guide

This guide provides step-by-step instructions for setting up the Call Auditing Platform on a fresh Windows or Mac machine.

## Table of Contents

1. [System Requirements](#system-requirements)
2. [Prerequisites Installation](#prerequisites-installation)
3. [Project Setup](#project-setup)
4. [Initial Configuration](#initial-configuration)
5. [Starting the Platform](#starting-the-platform)
6. [Verification](#verification)
7. [Troubleshooting](#troubleshooting)
8. [Next Steps](#next-steps)

---

## System Requirements

### Minimum Requirements
- **CPU**: 4 cores
- **RAM**: 8GB (16GB recommended)
- **Disk Space**: 50GB free
- **OS**: Windows 10/11 or macOS 11+

### Recommended for Production
- **CPU**: 8+ cores
- **RAM**: 16GB+
- **Disk Space**: 100GB+ SSD
- **GPU**: Optional (NVIDIA GPU for 10x faster transcription)

---

## Prerequisites Installation

### 1. Install Docker Desktop

Docker Desktop includes both Docker Engine and Docker Compose, which are required to run the platform.

#### **Windows Installation**

1. **Download Docker Desktop**
   - Visit: https://www.docker.com/products/docker-desktop/
   - Download "Docker Desktop for Windows"

2. **System Requirements Check**
   - Ensure Windows 10/11 (64-bit)
   - Enable WSL 2 (Windows Subsystem for Linux)

   ```powershell
   # Run in PowerShell as Administrator
   wsl --install
   wsl --set-default-version 2
   ```

3. **Install Docker Desktop**
   - Run the installer `Docker Desktop Installer.exe`
   - Follow the installation wizard
   - Check "Use WSL 2 instead of Hyper-V" (recommended)
   - Restart your computer when prompted

4. **Configure Docker Resources**
   - Open Docker Desktop
   - Go to Settings > Resources
   - Set memory to at least 8GB (12GB recommended)
   - Set CPUs to at least 4
   - Click "Apply & Restart"

5. **Verify Installation**
   ```powershell
   docker --version
   # Expected output: Docker version 24.x.x or higher

   docker compose version
   # Expected output: Docker Compose version v2.x.x or higher
   ```

#### **Mac Installation**

1. **Download Docker Desktop**
   - Visit: https://www.docker.com/products/docker-desktop/
   - Download the appropriate version:
     - **Apple Silicon (M1/M2/M3)**: "Docker Desktop for Mac with Apple chip"
     - **Intel Chip**: "Docker Desktop for Mac with Intel chip"

2. **Install Docker Desktop**
   - Open the downloaded `.dmg` file
   - Drag Docker.app to Applications folder
   - Open Docker from Applications
   - Grant necessary permissions when prompted

3. **Configure Docker Resources**
   - Click Docker icon in menu bar > Preferences
   - Go to Resources
   - Set Memory to at least 8GB (12GB recommended)
   - Set CPUs to at least 4
   - Click "Apply & Restart"

4. **Verify Installation**
   ```bash
   docker --version
   # Expected output: Docker version 24.x.x or higher

   docker compose version
   # Expected output: Docker Compose version v2.x.x or higher
   ```

**If Docker Already Installed**: Verify the version and update if older than version 24.x:
- Docker Desktop > Check for Updates
- Or download the latest version from the website

---

### 2. Install Git (Optional - for version control)

#### **Windows**

```powershell
# Using winget (Windows 11 or Windows 10 with App Installer)
winget install --id Git.Git -e --source winget

# OR download from: https://git-scm.com/download/win
```

#### **Mac**

```bash
# Using Homebrew (install Homebrew first if needed: https://brew.sh/)
brew install git

# OR Git comes pre-installed on macOS, verify with:
git --version
```

**If Git Already Installed**: Update to the latest version
```bash
# Mac
brew upgrade git

# Windows - download latest installer from https://git-scm.com/
```

---

### 3. Install Java 21 (For Development Only)

**Note**: Only needed if you plan to build/modify Spring Boot services locally. Docker handles Java for containerized services.

#### **Windows**

```powershell
# Using winget
winget install Microsoft.OpenJDK.21

# OR download from: https://learn.microsoft.com/en-us/java/openjdk/download#openjdk-21
```

#### **Mac**

```bash
# Using Homebrew
brew install openjdk@21

# Add to PATH (add to ~/.zshrc or ~/.bash_profile)
echo 'export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

**Verify Installation**:
```bash
java -version
# Expected: openjdk version "21.x.x"
```

**If Java Already Installed**: Check version and ensure it's Java 21
```bash
java -version

# If different version, install Java 21 and set JAVA_HOME
# Mac:
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Windows (PowerShell):
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.x.x"
```

---

### 4. Install Python 3.11+ (For Development Only)

**Note**: Only needed if you plan to build/modify Python services locally. Docker handles Python for containerized services.

#### **Windows**

```powershell
# Using winget
winget install Python.Python.3.11

# OR download from: https://www.python.org/downloads/
# IMPORTANT: Check "Add Python to PATH" during installation
```

#### **Mac**

```bash
# Using Homebrew
brew install python@3.11

# Verify it's in PATH
python3 --version
```

**Verify Installation**:
```bash
python --version  # Windows
python3 --version # Mac
# Expected: Python 3.11.x or 3.12.x
```

**If Python Already Installed**: Ensure version is 3.11+
```bash
python3 --version

# If older, install Python 3.11+ alongside existing installation
# Both versions can coexist
```

---

### 5. Install Maven (For Development Only)

**Note**: Only needed if you want to build Spring Boot services outside Docker. Each service includes Maven wrapper (`./mvnw`).

#### **Windows**

```powershell
# Using winget
winget install Apache.Maven

# OR download from: https://maven.apache.org/download.cgi
```

#### **Mac**

```bash
# Using Homebrew
brew install maven
```

**Verify Installation**:
```bash
mvn -version
# Expected: Apache Maven 3.9.x or higher
```

**If Maven Already Installed**: Ensure version 3.9+
```bash
mvn -version

# Update if needed:
# Mac: brew upgrade maven
# Windows: download latest from https://maven.apache.org/
```

---

## Project Setup

### Option 1: Clone from Git Repository

If the project is hosted on GitHub/GitLab/Bitbucket:

```bash
# Clone the repository
git clone https://github.com/your-org/call-auditing-platform.git

# Navigate to project directory
cd call-auditing-platform
```

### Option 2: Download ZIP Archive

If you received the project as a ZIP file:

1. Extract the ZIP file to your desired location
2. Open terminal/command prompt
3. Navigate to the extracted directory

**Windows (PowerShell)**:
```powershell
cd C:\Users\YourName\Projects\call-auditing-platform
```

**Mac/Linux**:
```bash
cd ~/Projects/call-auditing-platform
```

---

## Initial Configuration

### 1. Verify Project Structure

Ensure you're in the correct directory:

```bash
# List files - you should see:
ls
# Expected: docker-compose.yml, README.md, monitoring/, services/, etc.
```

### 2. Create Data Directories (Optional)

Docker will create these automatically, but you can pre-create them:

**Windows (PowerShell)**:
```powershell
# Not necessary - Docker handles this
```

**Mac/Linux**:
```bash
# Not necessary - Docker handles this
```

### 3. Configure Environment Variables (Optional)

For production or custom configurations, create a `.env` file:

```bash
# Create .env file (optional - defaults work for development)
cat > .env << 'EOF'
# Kafka Configuration
KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# Database Configuration
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
POSTGRES_DB=call_auditing

# MinIO Configuration
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin

# Monitoring
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318
EOF
```

**Note**: For development, the defaults in `docker-compose.yml` are sufficient. Skip this step unless deploying to production.

---

## Starting the Platform

### 1. Pull Required Docker Images

This downloads all base images (first time only):

```bash
docker compose pull
```

**Expected output**: Progress bars showing downloads for Kafka, Postgres, MinIO, etc.

**Time estimate**: 5-15 minutes depending on internet speed.

### 2. Build Application Services

Build the custom Docker images for our services:

```bash
docker compose build
```

**Expected output**: Build logs for each service (call-ingestion-service, transcription-service, etc.)

**Time estimate**: 10-20 minutes (builds Java and Python services)

### 3. Start Infrastructure Services First (Recommended)

Start core infrastructure before application services:

```bash
docker compose up -d kafka postgres minio opensearch valkey
```

**Wait 30 seconds** for services to initialize, then verify:

```bash
docker compose ps

# Expected output: All listed services should show "running"
```

### 4. Start Application Services

```bash
docker compose up -d
```

This starts all remaining services (API Gateway, processing services, monitoring).

**Time estimate**: 2-3 minutes for all services to become healthy

### 5. Monitor Startup Logs

Watch logs to ensure services start correctly:

```bash
# View all logs
docker compose logs -f

# Or specific service
docker compose logs -f api-gateway

# Press Ctrl+C to stop following logs (services keep running)
```

**Look for**:
- `Started [ServiceName]Application in X seconds` (Spring Boot services)
- `Uvicorn running on http://0.0.0.0:8000` (Python services)
- No repeated ERROR messages

---

## Verification

### 1. Check Service Health

```bash
# View running containers
docker compose ps

# All services should show "Up" or "healthy" status
```

Expected services (16 total):
- kafka
- postgres
- minio
- opensearch
- opensearch-dashboards
- valkey
- call-ingestion-service
- transcription-service
- sentiment-service
- voc-service
- audit-service
- analytics-service
- notification-service
- api-gateway
- prometheus
- grafana
- jaeger
- otel-collector

### 2. Test Service Endpoints

**Test API Gateway**:
```bash
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

**Test Call Ingestion Service**:
```bash
curl http://localhost:8081/actuator/health
# Expected: {"status":"UP"}
```

### 3. Access Web Interfaces

Open these URLs in your browser:

| Service | URL | Credentials |
|---------|-----|-------------|
| API Gateway Health | http://localhost:8080/actuator/health | N/A |
| MinIO Console | http://localhost:9001 | minioadmin / minioadmin |
| Grafana | http://localhost:3000 | admin / admin |
| OpenSearch Dashboards | http://localhost:5601 | N/A (no auth) |
| Jaeger Tracing UI | http://localhost:16686 | N/A |
| Prometheus | http://localhost:9090 | N/A |

**If any interface doesn't load**: Wait 1-2 minutes (services may still be initializing) or check logs.

### 4. Initialize MinIO Storage

Create the bucket for storing audio files:

```bash
# Set up MinIO alias
docker compose exec minio mc alias set local http://localhost:9000 minioadmin minioadmin

# Create bucket
docker compose exec minio mc mb local/calls

# Verify bucket exists
docker compose exec minio mc ls local
# Expected: [timestamp] calls/
```

### 5. Verify Kafka Topics (Optional)

```bash
# List Kafka topics (note: use full path to kafka-topics.sh)
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092

# Expected topics (if services created them):
# calls.received
# calls.transcribed
# calls.sentiment-analyzed
# calls.voc-analyzed
# calls.audited
```

### 6. Initialize Database Schema

The project includes a comprehensive database schema (`schema.sql`) that creates all necessary tables, indexes, and views.

```bash
# Apply the database schema (from project root directory)
docker compose exec -T postgres psql -U postgres -d call_auditing < schema.sql
```

**Note**: If you see "already exists" errors, the schema has already been applied. This is safe to ignore.

**Verify the schema was applied correctly**:

```bash
# Check tables (should list 14 tables)
docker compose exec postgres psql -U postgres -d call_auditing -c "\dt"

# Check TimescaleDB hypertables (should list 3)
docker compose exec postgres psql -U postgres -d call_auditing -c "SELECT hypertable_name FROM timescaledb_information.hypertables;"

# Check sample compliance rules (should list 5 rules)
docker compose exec postgres psql -U postgres -d call_auditing -c "SELECT id, name, severity FROM compliance_rules;"
```

**Schema includes**:
- **14 tables**: calls, transcriptions, segments, sentiment_results, segment_sentiments, voc_insights, audit_results, compliance_violations, compliance_rules, agent_performance, compliance_metrics, sentiment_trends, event_store, notifications
- **3 TimescaleDB hypertables**: agent_performance, compliance_metrics, sentiment_trends
- **8 ENUM types**: call_status, call_channel, sentiment_type, speaker_type, compliance_status, violation_severity, satisfaction_level, intent_type
- **2 views**: call_summary, agent_summary
- **5 sample compliance rules**: Pre-configured audit rules for testing

---

## Troubleshooting

### Issue: Docker Commands Not Recognized

**Symptoms**: `docker: command not found` or `docker-compose: command not found`

**Solutions**:
- **Windows**: Restart PowerShell/Command Prompt after Docker Desktop installation
- **Mac**: Ensure Docker Desktop is running (check menu bar)
- **Both**: Verify Docker Desktop is in Applications and started
- Run `docker --version` to confirm installation

### Issue: Port Already in Use

**Symptoms**: `Error: bind: address already in use`

**Solution**:
```bash
# Find what's using the port (example: port 8080)
# Windows:
netstat -ano | findstr :8080

# Mac:
lsof -i :8080

# Stop the conflicting service or change port in docker-compose.yml
```

### Issue: Out of Memory / Services Crashing

**Symptoms**: Services exit with code 137 or 1, `OOMKilled` in logs

**Solutions**:

1. **Increase Docker Memory**:
   - Docker Desktop > Settings/Preferences > Resources
   - Set Memory to 12GB or higher
   - Click Apply & Restart

2. **Reduce Service Memory**:
   Edit `docker-compose.yml`:
   ```yaml
   opensearch:
     environment:
       - OPENSEARCH_JAVA_OPTS=-Xms256m -Xmx256m  # Reduced from 512m
   ```

3. **Start Services Incrementally**:
   ```bash
   # Infrastructure only first
   docker compose up -d kafka postgres minio valkey

   # Wait 1 minute, then add more
   docker compose up -d opensearch prometheus grafana

   # Finally, application services
   docker compose up -d
   ```

### Issue: Services Won't Start - Connection Refused

**Symptoms**: `Connection refused` errors in logs, services restarting

**Solution**:
```bash
# Check if dependent services are healthy
docker compose ps

# Restart services in dependency order
docker compose restart kafka
sleep 30
docker compose restart call-ingestion-service voc-service
```

### Issue: MinIO Bucket Creation Fails

**Symptoms**: `mc: <ERROR> Unable to initialize new alias`

**Solution**:
```bash
# Ensure MinIO is fully started
docker compose logs minio | tail -20

# Wait 30 seconds after starting MinIO, then retry
docker compose exec minio mc alias set local http://localhost:9000 minioadmin minioadmin
```

### Issue: Kafka Topics Not Created

**Symptoms**: Services can't publish events, "Topic not found" errors

**Solution**:
```bash
# Manually create topics (note: use full path to kafka-topics.sh)
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic calls.received \
  --partitions 3 \
  --replication-factor 1

# Repeat for other topics: calls.transcribed, calls.sentiment-analyzed, etc.
```

### Issue: Cannot Access Web Interfaces

**Symptoms**: Browser shows "This site can't be reached"

**Solutions**:
1. Wait 2-3 minutes (services may still be starting)
2. Check service is running: `docker compose ps <service-name>`
3. Check logs: `docker compose logs <service-name>`
4. Try `http://127.0.0.1:PORT` instead of `localhost:PORT`
5. Disable VPN/firewall temporarily

### Issue: Permission Denied (Linux/Mac)

**Symptoms**: Permission errors when accessing volumes

**Solution**:
```bash
# Fix volume permissions
sudo chown -R $USER:$USER .

# Or run Docker commands with sudo (not recommended)
```

### Issue: WSL 2 Installation Failed (Windows)

**Symptoms**: Docker Desktop won't start, WSL errors

**Solution**:
```powershell
# Run as Administrator
wsl --install
wsl --update
wsl --set-default-version 2

# Restart computer
# Install Docker Desktop again
```

### Complete Reset

If nothing works, perform a complete reset:

```bash
# Stop all containers
docker compose down

# Remove all volumes (WARNING: deletes all data)
docker compose down -v

# Remove all images
docker compose down --rmi all

# Restart Docker Desktop

# Start fresh
docker compose pull
docker compose build
docker compose up -d
```

---

## Next Steps

### 1. Verify End-to-End Flow

Once all services are running, test the complete pipeline:

```bash
# Upload a test audio file (when service is implemented)
curl -X POST http://localhost:8080/api/calls/upload \
  -F "file=@sample-call.wav" \
  -F "callerId=test-caller" \
  -F "agentId=test-agent"

# Monitor processing in logs
docker compose logs -f transcription-service sentiment-service
```

### 2. Explore Observability

- **Grafana Dashboards**: http://localhost:3000
  - Create dashboards from Prometheus metrics
  - View service health and performance

- **Jaeger Tracing**: http://localhost:16686
  - Search for traces by service name
  - View end-to-end request flow

- **Prometheus Metrics**: http://localhost:9090
  - Query: `rate(http_server_requests_seconds_count[5m])`
  - Explore service metrics

### 3. Review Architecture

Read these documents to understand the system:
- `call_auditing_architecture.md` - Detailed architecture and design
- `MODERNIZATION_SUMMARY.md` - Technology choices

### 4. Development Workflow

For local development outside Docker:

**Spring Boot Services**:
```bash
cd call-ingestion-service
./mvnw spring-boot:run
```

**Python Services**:
```bash
cd transcription-service
python -m venv venv
source venv/bin/activate  # Mac/Linux
# venv\Scripts\activate   # Windows
pip install -r requirements.txt
uvicorn main:app --reload
```

### 5. Monitor Resource Usage

```bash
# Check resource usage
docker stats

# View disk usage
docker system df
```

### 6. Regular Maintenance

```bash
# View logs periodically
docker compose logs --tail=100

# Restart unhealthy services
docker compose restart <service-name>

# Clean up unused resources
docker system prune -f
```

---

## Quick Command Reference

### Daily Operations

```bash
# Start all services
docker compose up -d

# Stop all services
docker compose down

# Restart a service
docker compose restart <service-name>

# View logs
docker compose logs -f <service-name>

# Check status
docker compose ps

# Rebuild service after code changes
docker compose up -d --build <service-name>
```

### Maintenance

```bash
# Clean restart (preserves data)
docker compose down && docker compose up -d

# Full reset (deletes data)
docker compose down -v && docker compose up -d

# Update images
docker compose pull && docker compose up -d

# View resource usage
docker stats

# Clean unused resources
docker system prune -f
```

---

## Support

### Getting Help

1. Check logs: `docker compose logs <service-name>`
2. Review architecture docs: `call_auditing_architecture.md`
3. Check this guide's Troubleshooting section
4. Inspect service health: `docker compose ps`

### Reporting Issues

When reporting issues, include:
- Operating system (Windows 11, macOS 14, etc.)
- Docker version: `docker --version`
- Error logs: `docker compose logs <service-name>`
- Steps to reproduce
- Output of `docker compose ps`

---

## Additional Resources

- **Docker Documentation**: https://docs.docker.com/
- **Spring Boot Reference**: https://spring.io/projects/spring-boot
- **FastAPI Documentation**: https://fastapi.tiangolo.com/
- **Kafka Documentation**: https://kafka.apache.org/documentation/
- **Project Architecture**: See `call_auditing_architecture.md`

---

**Installation Complete!**

You now have a fully functional Call Auditing Platform running locally. All services are containerized and ready for development or testing.
