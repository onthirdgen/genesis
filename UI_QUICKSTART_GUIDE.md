# Call Auditing UI - Quick Start Guide

**Last Updated**: 2026-01-01

This guide provides step-by-step instructions for running the Call Auditing UI and using the call-ingestion-service functionality.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Step 1: Start Backend Services](#step-1-start-backend-services)
3. [Step 2: Initialize Database](#step-2-initialize-database)
4. [Step 3: Start the UI](#step-3-start-the-ui)
5. [Step 4: Login to the Application](#step-4-login-to-the-application)
6. [Step 5: Upload Call Recordings](#step-5-upload-call-recordings)
7. [Step 6: View and Manage Calls](#step-6-view-and-manage-calls)
8. [Troubleshooting](#troubleshooting)
9. [API Reference](#api-reference)

---

## Prerequisites

### Required Software
- **Docker Desktop** (version 29+) - Running
- **Node.js** 20+ and npm 10+
- **Terminal/Command Line** access

### Verify Docker is Running
```bash
docker version
# Should show Docker version 29+ without errors
```

### Verify Node.js is Installed
```bash
node --version  # Should show v20+
npm --version   # Should show v10+
```

---

## Step 1: Start Backend Services

### 1.1 Navigate to Project Root
```bash
cd /Users/jon/AI/genesis
```

### 1.2 Start Infrastructure Services
Start the core infrastructure services (Postgres, Kafka, MinIO, Valkey):

```bash
docker compose up -d postgres kafka minio valkey
```

**Expected Output:**
```
✔ Container genesis-postgres-1  Started
✔ Container genesis-kafka-1     Started
✔ Container genesis-minio-1     Started
✔ Container genesis-valkey-1    Started
```

### 1.3 Verify Services are Running
```bash
docker compose ps
```

**Expected Output:**
```
NAME                    STATUS      PORTS
genesis-kafka-1         Up          0.0.0.0:9092->9092/tcp
genesis-minio-1         Up          0.0.0.0:9000-9001->9000-9001/tcp
genesis-postgres-1      Up          0.0.0.0:5432->5432/tcp
genesis-valkey-1        Up          0.0.0.0:6379->6379/tcp
```

### 1.4 Wait for PostgreSQL to be Ready
```bash
# Wait about 10 seconds, then verify Postgres is accepting connections
docker compose exec postgres pg_isready -U postgres
```

**Expected Output:**
```
/var/run/postgresql:5432 - accepting connections
```

### 1.5 Start API Gateway
```bash
docker compose up -d api-gateway
```

**Wait 20-30 seconds** for the API Gateway to fully start.

### 1.6 Verify API Gateway is Running
```bash
# Check if API Gateway is running
docker compose logs api-gateway | grep "Started ApiGatewayApplication"

# Test health endpoint
curl http://localhost:8080/actuator/health
```

**Expected Output:**
```json
{"status":"UP"}
```

### 1.7 Start Call Ingestion Service
```bash
docker compose up -d call-ingestion-service
```

**Wait 20-30 seconds** for the service to fully start.

### 1.8 Verify Call Ingestion Service is Running
```bash
# Check if service started
docker compose logs call-ingestion-service | grep "Started CallIngestionApplication"

# Test service health directly
curl http://localhost:8081/actuator/health

# Test health endpoint through API Gateway (requires authentication)
# You'll need to login first to get a JWT token
curl http://localhost:8080/api/calls/health
```

**Expected Output (Direct):**
```json
{"status":"UP","components":{"db":{"status":"UP"},...}}
```

**Expected Output (Through Gateway):**
```
Call Ingestion Service is healthy
```

---

## Step 2: Initialize Database

### 2.1 Create Users Table and Test Users
```bash
docker compose exec -T postgres psql -U postgres -d call_auditing < api-gateway/init-users.sql
```

**Expected Output:**
```
CREATE TABLE
CREATE INDEX
INSERT 0 3
CREATE FUNCTION
DROP TRIGGER
CREATE TRIGGER
```

### 2.2 Verify Users Were Created
```bash
docker compose exec postgres psql -U postgres -d call_auditing -c "SELECT email, full_name, role FROM users;"
```

**Expected Output:**
```
          email          |    full_name     |    role
-------------------------+------------------+------------
 analyst@example.com     | John Analyst     | ANALYST
 admin@example.com       | Admin User       | ADMIN
 supervisor@example.com  | Jane Supervisor  | SUPERVISOR
(3 rows)
```

### 2.3 Verify Calls Table Exists
```bash
docker compose exec postgres psql -U postgres -d call_auditing -c "\d calls"
```

**Expected Output:**
Should show the calls table structure with columns: id, call_id, audio_file_path, channel, etc.

---

## Step 3: Start the UI

### 3.1 Navigate to UI Directory
```bash
cd /Users/jon/AI/genesis/call-auditing-ui
```

### 3.2 Install Dependencies (First Time Only)
```bash
# If you haven't installed dependencies yet
npm install
```

### 3.3 Verify Environment Configuration
```bash
cat .env.local
```

**Should Show:**
```
# API Configuration
NEXT_PUBLIC_API_URL=http://localhost:8080

# NextAuth Configuration
NEXTAUTH_URL=http://localhost:4142
```

### 3.4 Start the Development Server
```bash
npm run dev
```

**Expected Output:**
```
> call-auditing-ui@0.1.0 dev
> next dev -p 4142

  ▲ Next.js 15.1.6
  - Local:        http://localhost:4142
  - Environments: .env.local

 ✓ Starting...
 ✓ Ready in 2.3s
```

### 3.5 Verify UI is Accessible
Open your browser and navigate to:
```
http://localhost:4142
```

You should see the **Call Auditing Platform** home page.

---

## Step 4: Login to the Application

### 4.1 Navigate to Login Page
Click **"Sign In"** or navigate directly to:
```
http://localhost:4142/login
```

### 4.2 Use Test Credentials
**Available Test Users:**

| Email | Password | Role |
|-------|----------|------|
| `analyst@example.com` | `password123` | ANALYST |
| `admin@example.com` | `password123` | ADMIN |
| `supervisor@example.com` | `password123` | SUPERVISOR |

**Login Steps:**
1. Enter email: `analyst@example.com`
2. Enter password: `password123`
3. Click **"Sign In"**

### 4.3 Verify Successful Login
After successful login, you should:
- Be redirected to `/dashboard`
- See the dashboard with stats cards
- See your name in the sidebar: "John Analyst"
- See navigation menu items

### 4.4 Verify JWT Token is Stored
Open browser Developer Tools (F12):
1. Go to **Application** tab (Chrome) or **Storage** tab (Firefox)
2. Navigate to **Local Storage** → `http://localhost:4142`
3. You should see:
   - `auth-storage` - Contains user, token, refreshToken
   - `auth-token` - Contains the JWT token

---

## Step 5: Upload Call Recordings

### 5.1 Navigate to Calls Page
Click **"Calls"** in the sidebar or navigate to:
```
http://localhost:4142/dashboard/calls
```

### 5.2 Prepare a Test Audio File
**Option A: Use an Existing Audio File**
- Use any `.wav`, `.mp3`, or `.m4a` file
- Recommended: Keep file size under 10MB for testing

**Option B: Create a Test Audio File (macOS)**
```bash
# Record a 5-second test audio file
say "This is a test call recording for the call auditing platform" -o ~/test-call.aiff
ffmpeg -i ~/test-call.aiff ~/test-call.wav
```

**Option C: Download Sample Audio**
```bash
# Download a public domain sample (if available)
curl -o ~/sample-call.wav https://www2.cs.uic.edu/~i101/SoundFiles/BabyElephantWalk60.wav
```

### 5.3 Upload Call via API (Manual Test)
Since the UI call upload feature may not be fully implemented, test via API first:

```bash
# Upload call recording (replace YOUR_JWT_TOKEN with actual token)
curl -X POST http://localhost:8080/api/calls/upload \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -F "file=@/path/to/your/audio.wav" \
  -F "callerId=555-0123" \
  -F "agentId=agent-001" \
  -F "customerId=customer-123" \
  -F "duration=180" \
  -F "channel=PHONE"
```

**To get your JWT token:**
1. Open browser Developer Tools (F12)
2. Go to **Application** → **Local Storage**
3. Copy the value from `auth-token`
4. Replace `YOUR_JWT_TOKEN` in the command above

**Expected Response:**
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "callId": "CALL-20260101-001",
  "audioFilePath": "s3://calls/2026/01/123e4567-e89b-12d3-a456-426614174000.wav",
  "channel": "PHONE",
  "status": "RECEIVED",
  "createdAt": "2026-01-01T14:30:00Z"
}
```

### 5.4 Alternative: Upload via UI (if implemented)
1. Click **"Upload Call"** button
2. Select audio file
3. Fill in metadata:
   - **Channel**: Select "Phone", "Web", "Mobile", or "Email"
   - **Agent ID**: Enter agent identifier (e.g., `agent-001`)
   - **Customer ID**: Enter customer identifier (e.g., `customer-123`)
4. Click **"Upload"**
5. Wait for upload confirmation

---

## Step 6: View and Manage Calls

### 6.1 Check Call Status (API)
```bash
# Get call status by ID (replace CALL_ID and YOUR_JWT_TOKEN)
curl http://localhost:8080/api/calls/CALL-20260101-001/status \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

**Expected Response:**
```json
{
  "callId": "CALL-20260101-001",
  "status": "RECEIVED",
  "uploadedAt": "2026-01-01T14:30:00Z",
  "processingStage": "INGESTION_COMPLETE"
}
```

### 6.2 Get Call Audio (API)
```bash
# Get audio file URL or metadata (replace CALL_ID and YOUR_JWT_TOKEN)
curl http://localhost:8080/api/calls/CALL-20260101-001/audio \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

**Note:** The call-ingestion-service currently provides these endpoints:
- `POST /api/calls/upload` - Upload new call
- `GET /api/calls/{callId}/status` - Get call status
- `GET /api/calls/{callId}/audio` - Get call audio
- `GET /api/calls/health` - Service health check

**Listing/searching calls** will be available through the Analytics Service or VoC Service in future releases.

### 6.3 View Calls in UI
1. Navigate to **Calls** page
2. You should see a list of uploaded calls
3. Click on a call to view details
4. View metadata: Call ID, Channel, Status, Upload Time

### 6.4 Access Call Audio
The `/api/calls/{callId}/audio` endpoint provides access to the stored audio file. The exact response format depends on the implementation (could be a presigned S3 URL, direct download, or metadata with access information).

---

## Troubleshooting

### Issue 1: Cannot Connect to API Gateway
**Symptoms:**
- Login fails with network error
- API calls return "Connection refused"

**Solution:**
```bash
# Check if API Gateway is running
docker compose ps api-gateway

# Check API Gateway logs
docker compose logs api-gateway

# Restart API Gateway
docker compose restart api-gateway

# Wait 30 seconds and test
curl http://localhost:8080/actuator/health
```

### Issue 2: Login Fails with 401 Unauthorized
**Symptoms:**
- Login returns "Invalid email or password"
- Users table is empty

**Solution:**
```bash
# Re-run database initialization
docker compose exec -T postgres psql -U postgres -d call_auditing < api-gateway/init-users.sql

# Verify users exist
docker compose exec postgres psql -U postgres -d call_auditing -c "SELECT email FROM users;"
```

### Issue 3: CORS Error in Browser Console
**Symptoms:**
- Browser shows: "Access to fetch at 'http://localhost:8080' has been blocked by CORS policy"

**Solution:**
- Verify API Gateway CORS configuration includes `http://localhost:4142`
- Restart API Gateway: `docker compose restart api-gateway`
- Clear browser cache and reload

### Issue 4: File Upload Fails
**Symptoms:**
- Upload returns 400 Bad Request
- Upload returns 413 Payload Too Large

**Solutions:**

**400 Bad Request:**
```bash
# Check file format is supported (WAV, MP3, M4A)
file /path/to/your/audio.wav

# Check metadata is provided (channel, agentId, customerId)
```

**413 Payload Too Large:**
```bash
# Check file size (should be < 50MB)
ls -lh /path/to/your/audio.wav

# If too large, compress with ffmpeg:
ffmpeg -i large-file.wav -ar 16000 -ac 1 -b:a 64k compressed.wav
```

### Issue 5: UI Not Loading on Port 4142
**Symptoms:**
- Browser shows "This site can't be reached"
- `npm run dev` shows errors

**Solution:**
```bash
# Check if port 4142 is already in use
lsof -i :4142

# Kill process using the port (if needed)
kill -9 <PID>

# Restart UI
cd /Users/jon/AI/genesis/call-auditing-ui
npm run dev
```

### Issue 6: PostgreSQL Connection Refused
**Symptoms:**
- API Gateway logs show: "Connection refused: postgres:5432"

**Solution:**
```bash
# Check PostgreSQL is running
docker compose ps postgres

# Restart PostgreSQL
docker compose restart postgres

# Wait 10 seconds and verify
docker compose exec postgres pg_isready -U postgres
```

---

## API Reference

### Authentication Endpoints

**Login**
```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "analyst@example.com",
  "password": "password123"
}

Response:
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "user": {
    "id": "uuid",
    "email": "analyst@example.com",
    "fullName": "John Analyst",
    "role": "ANALYST"
  }
}
```

**Get Current User**
```http
GET /api/auth/me
Authorization: Bearer <token>

Response:
{
  "id": "uuid",
  "email": "analyst@example.com",
  "fullName": "John Analyst",
  "role": "ANALYST"
}
```

**Refresh Token**
```http
POST /api/auth/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}

Response:
{
  "token": "new-access-token",
  "refreshToken": "new-refresh-token",
  "type": "Bearer",
  "user": { ... }
}
```

### Call Management Endpoints

**Upload Call Recording**
```http
POST /api/calls/upload
Authorization: Bearer <token>
Content-Type: multipart/form-data

Form Data:
- file: (binary) audio file (WAV, MP3, M4A, FLAC, OGG)
- callerId: string (required) - Caller's phone number or identifier
- agentId: string (required) - Agent's unique identifier
- customerId: string (optional) - Customer identifier
- duration: number (optional) - Call duration in seconds
- channel: string (optional) - Call channel (defaults to PHONE)

Response:
{
  "id": "uuid",
  "callId": "CALL-20260101-001",
  "audioFilePath": "s3://calls/2026/01/uuid.wav",
  "channel": "PHONE",
  "status": "RECEIVED",
  "createdAt": "2026-01-01T14:30:00Z",
  "callerId": "555-0123",
  "agentId": "agent-001"
}
```

**Get Call Status**
```http
GET /api/calls/{callId}/status
Authorization: Bearer <token>

Response:
{
  "callId": "CALL-20260101-001",
  "status": "RECEIVED",
  "uploadedAt": "2026-01-01T14:30:00Z",
  "processingStage": "INGESTION_COMPLETE"
}
```

**Get Call Audio**
```http
GET /api/calls/{callId}/audio
Authorization: Bearer <token>

Response:
{
  "callId": "CALL-20260101-001",
  "audioUrl": "presigned-s3-url",
  "expiresAt": "2026-01-01T15:00:00Z"
}
```

**Health Check**
```http
GET /api/calls/health
Authorization: Bearer <token>

Response:
Call Ingestion Service is healthy
```

---

## Service URLs Quick Reference

| Service | URL | Purpose |
|---------|-----|---------|
| **UI** | http://localhost:4142 | Main application interface |
| **API Gateway** | http://localhost:8080 | Backend API entry point |
| **Login** | http://localhost:4142/login | Authentication page |
| **Dashboard** | http://localhost:4142/dashboard | Main dashboard |
| **Calls** | http://localhost:4142/dashboard/calls | Call management |
| **MinIO Console** | http://localhost:9001 | Object storage (minioadmin/minioadmin) |
| **Grafana** | http://localhost:3000 | Monitoring dashboards (admin/admin) |

---

## Next Steps

After successfully running the UI and uploading calls:

1. **Enable Transcription Service** - Start processing audio files
2. **Enable Sentiment Analysis** - Analyze call sentiment
3. **View Analytics Dashboard** - See aggregated metrics
4. **Configure Compliance Rules** - Set up audit criteria
5. **Export Reports** - Generate compliance reports

For more information, see:
- `README.md` - Project overview
- `call_auditing_architecture.md` - Detailed architecture
- `UI_BACKEND_INTEGRATION_STATUS.md` - Integration status

---

**Happy Auditing!** 📞📊
