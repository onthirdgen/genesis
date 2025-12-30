# Call Auditing Platform - Installation Guide

This guide shows you how to install and run the Call Auditing Platform on your computer. **No programming experience required.**

## What You'll Need

- **Computer**: Windows 10/11 or Mac (Intel or Apple Silicon)
- **RAM**: At least 8GB (16GB recommended)
- **Disk Space**: 50GB free
- **Internet**: For downloading software and images
- **Time**: About 30-45 minutes for first-time setup

## What Gets Installed

You'll install **Docker Desktop** - this is the only software you need. Docker will automatically download and run all 16 services that make up the Call Auditing Platform (databases, message queues, microservices, monitoring tools, etc.).

---

## Step 1: Install Docker Desktop

Docker Desktop bundles everything needed to run the platform. Choose your operating system:

### Windows Installation

1. **Download Docker Desktop**
   - Visit: https://www.docker.com/products/docker-desktop/
   - Click "Download for Windows"

2. **Enable WSL 2** (Windows Subsystem for Linux)
   - Open PowerShell as Administrator (right-click Start menu > "Windows PowerShell (Admin)")
   - Run this command:
   ```powershell
   wsl --install
   ```
   - Restart your computer when prompted

3. **Install Docker Desktop**
   - Run the downloaded file `Docker Desktop Installer.exe`
   - Check "Use WSL 2 instead of Hyper-V" when asked
   - Follow the installation wizard
   - Restart your computer if prompted

4. **Configure Docker Resources**
   - Open Docker Desktop
   - Click the gear icon (Settings)
   - Go to "Resources" in the left menu
   - Set **Memory** to at least **8 GB** (12 GB recommended)
   - Set **CPUs** to at least **4**
   - Click "Apply & Restart"

5. **Verify Installation**
   - Open PowerShell
   - Run these commands:
   ```powershell
   docker --version
   docker compose version
   ```
   - You should see version numbers (24.x or higher for Docker, v2.x for Compose)

### Mac Installation

1. **Download Docker Desktop**
   - Visit: https://www.docker.com/products/docker-desktop/
   - Choose the right version:
     - **Apple Silicon (M1/M2/M3 chip)**: "Mac with Apple chip"
     - **Intel chip**: "Mac with Intel chip"
   - Not sure which? Click Apple menu > "About This Mac" to check

2. **Install Docker Desktop**
   - Open the downloaded `.dmg` file
   - Drag "Docker" to your Applications folder
   - Open Docker from Applications
   - Grant permissions when asked
   - Wait for Docker to start (whale icon appears in menu bar)

3. **Configure Docker Resources**
   - Click the Docker whale icon in the menu bar
   - Select "Preferences" or "Settings"
   - Go to "Resources"
   - Set **Memory** to at least **8 GB** (12 GB recommended)
   - Set **CPUs** to at least **4**
   - Click "Apply & Restart"

4. **Verify Installation**
   - Open Terminal (Applications > Utilities > Terminal)
   - Run these commands:
   ```bash
   docker --version
   docker compose version
   ```
   - You should see version numbers (24.x or higher for Docker, v2.x for Compose)

---

## Step 2: Download the Project

### Option A: If You Have Git

Open Terminal (Mac) or PowerShell (Windows) and run:

```bash
# Clone the repository (replace URL with actual repo)
git clone https://github.com/your-org/call-auditing-platform.git

# Go to the project folder
cd call-auditing-platform
```

### Option B: If You Don't Have Git

1. Download the project as a ZIP file
2. Extract the ZIP to a folder you'll remember (like `Documents/call-auditing-platform`)
3. Open Terminal (Mac) or PowerShell (Windows)
4. Navigate to the extracted folder:

**Windows:**
```powershell
cd C:\Users\YourName\Documents\call-auditing-platform
```

**Mac:**
```bash
cd ~/Documents/call-auditing-platform
```

---

## Step 3: Start the Platform

Make sure Docker Desktop is running (you should see the whale icon in your system tray/menu bar).

### 3.1 Download Required Images

This downloads all the software components (first time only):

```bash
docker compose pull
```

**Wait time**: 5-15 minutes depending on your internet speed. You'll see progress bars.

### 3.2 Build Application Services

This builds the custom microservices:

```bash
docker compose build
```

**Wait time**: 10-20 minutes. You'll see build logs scrolling by.

### 3.3 Start Infrastructure Services

Start the core services first (databases, message queues, storage):

```bash
docker compose up -d kafka postgres minio opensearch valkey
```

**Wait 30-60 seconds** for these to initialize.

### 3.4 Start All Remaining Services

```bash
docker compose up -d
```

This starts all application services and monitoring tools.

**Wait time**: 2-3 minutes for everything to become healthy.

---

## Step 4: Verify Everything Works

### 4.1 Check Service Status

```bash
docker compose ps
```

You should see **18 services** listed, all showing "Up" or "running" status.

### 4.2 Set Up Storage

Create the bucket for storing audio files:

```bash
docker compose exec minio mc alias set local http://localhost:9000 minioadmin minioadmin
docker compose exec minio mc mb local/calls
```

You should see: `Bucket created successfully`

### 4.3 Set Up Database

Load the database schema:

```bash
docker compose exec -T postgres psql -U postgres -d call_auditing < schema.sql
```

You'll see several "CREATE TABLE" and "CREATE INDEX" messages. This is normal.

### 4.4 Access Web Interfaces

Open these URLs in your web browser to verify everything is running:

| Service | URL | Login |
|---------|-----|-------|
| MinIO Console | http://localhost:9001 | minioadmin / minioadmin |
| Grafana (Monitoring) | http://localhost:3000 | admin / admin |
| Jaeger (Tracing) | http://localhost:16686 | (no login) |
| OpenSearch Dashboards | http://localhost:5601 | (no login) |

If any don't load, wait another minute and try again.

---

## Step 5: Daily Usage

### Starting the Platform

```bash
# Make sure Docker Desktop is running, then:
docker compose up -d
```

### Stopping the Platform

```bash
docker compose down
```

This stops all services but keeps your data.

### Viewing Logs

If you want to see what's happening:

```bash
# View all logs
docker compose logs -f

# View specific service (press Ctrl+C to exit)
docker compose logs -f call-ingestion-service
```

### Restarting a Service

If a service stops working:

```bash
docker compose restart <service-name>
```

---

## Troubleshooting

### Problem: "Port already in use"

Another program is using a required port.

**Solution**: Stop that program or restart your computer, then try again.

### Problem: Services keep restarting or crashing

Not enough memory allocated to Docker.

**Solution**:
1. Open Docker Desktop settings
2. Increase Memory to 12GB or higher
3. Click "Apply & Restart"
4. Run `docker compose restart`

### Problem: "Cannot connect to Docker daemon"

Docker Desktop isn't running.

**Solution**:
- **Windows**: Check system tray for Docker whale icon, click to start
- **Mac**: Check menu bar for Docker whale icon, click to start

### Problem: Web interfaces won't load

Services are still starting up.

**Solution**:
1. Wait 2-3 minutes
2. Check services are running: `docker compose ps`
3. Try `http://127.0.0.1:PORT` instead of `localhost:PORT`

### Problem: Download is very slow

Large images are being downloaded.

**Solution**: Be patient. The first setup takes the longest. Subsequent starts will be much faster.

### Complete Reset

If nothing works, start fresh:

```bash
# Stop everything
docker compose down

# Delete all data (WARNING: you'll lose any uploaded calls)
docker compose down -v

# Start over from Step 3
docker compose pull
docker compose build
docker compose up -d
```

---

## What's Running?

After installation, you'll have these services running:

**Infrastructure** (7 services):
- Kafka - Message queue for events
- PostgreSQL + TimescaleDB - Main database
- MinIO - File storage for audio
- OpenSearch - Search engine for transcriptions
- Valkey - Cache
- Prometheus - Metrics collection
- OpenTelemetry Collector - Telemetry aggregation

**Application Services** (8 services):
- API Gateway - Main entry point
- Call Ingestion Service - Handles audio uploads
- Transcription Service - Converts speech to text
- Sentiment Analysis Service - Analyzes call sentiment
- VoC Service - Extracts customer insights
- Audit Service - Compliance checking
- Analytics Service - Reporting and metrics
- Notification Service - Alerts and notifications

**Monitoring** (3 services):
- Grafana - Dashboards
- Jaeger - Distributed tracing
- OpenSearch Dashboards - Search interface

All services run in Docker containers and communicate automatically.

---

## Resource Usage

**Typical resource usage when running:**
- Memory: 6-10 GB
- Disk: 30-40 GB
- CPU: Moderate (spikes during transcription)

**Tip**: Close the platform when not in use to free up resources:
```bash
docker compose down
```

---

## Getting Help

### View Service Logs

If something isn't working:

```bash
# Replace <service-name> with the actual service (e.g., kafka, postgres, call-ingestion-service)
docker compose logs <service-name>
```

### Check Service Health

```bash
docker compose ps
```

Look for any services not showing "Up" status.

### Common Commands Quick Reference

```bash
# Start everything
docker compose up -d

# Stop everything (keeps data)
docker compose down

# Stop everything and delete data
docker compose down -v

# Restart a specific service
docker compose restart <service-name>

# View all running services
docker compose ps

# View logs
docker compose logs -f
```

---

## Next Steps

Now that the platform is running:

1. **Explore the interfaces**: Open Grafana, Jaeger, and MinIO Console
2. **Read the architecture**: Check `call_auditing_architecture.md` to understand how it works
3. **Upload test audio**: Use the API Gateway to upload a sample call recording (when implemented)
4. **Monitor processing**: Watch logs to see events flow through the system

---

## For Developers

If you want to modify or develop services, see **INSTALLATION-developer.md** for instructions on installing Java, Python, Maven, and setting up your development environment.

---

**Installation Complete!**

The Call Auditing Platform is now running on your computer. All 16+ microservices are containerized and ready to process call recordings, analyze sentiment, and generate insights.
