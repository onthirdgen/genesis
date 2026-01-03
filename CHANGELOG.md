# Changelog

All notable changes to the Call Auditing Platform project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Comprehensive UI testing report documenting login and file upload flows
- Debug endpoint for generating BCrypt password hashes (can be removed in production)
- Resolution summaries in issue documentation

### Changed
- Main README.md updated with latest status and correct port/credentials
- UI port changed from 3000 to 4142 (to avoid Grafana conflict)
- Grafana port changed from 3000 to 3001

### Fixed
- **[CRITICAL]** Authentication BCrypt password mismatch preventing login
- **[CRITICAL]** HTTP 405 Method Not Allowed error on file uploads
- **[CRITICAL]** JWT filter blocking CORS preflight OPTIONS requests
- Hardcoded Content-Type header in frontend uploadClient
- Duplicate CORS configuration between gateway and backend services

## [1.0.0] - 2026-01-02

### Authentication & File Upload Integration

#### Fixed
- **Authentication BCrypt Password Mismatch** - Login failing due to incorrect password hashes
  - Root Cause: BCrypt hashes in schema.sql not generated with Spring's BCryptPasswordEncoder
  - Solution: Generated correct hashes using debug endpoint and updated schema.sql
  - Files Updated:
    - `schema.sql` (lines 519-521)
    - `api-gateway/src/main/java/com/callaudit/gateway/model/User.java`
    - `api-gateway/src/main/java/com/callaudit/gateway/controller/DebugController.java` (created)
  - Verification: All three test users can now log in successfully
  - Status: ✅ RESOLVED

- **File Upload 405 Method Not Allowed** - File uploads failing with HTTP 405 error
  - Root Causes:
    1. JWT Authentication Filter blocking CORS preflight OPTIONS requests
    2. Hardcoded Content-Type header in frontend uploadClient (missing boundary)
    3. Duplicate CORS configuration causing header conflicts
  - Solutions:
    1. Added OPTIONS method bypass in JwtAuthenticationFilter before JWT validation
    2. Removed hardcoded Content-Type from uploadClient (browser auto-sets with boundary)
    3. Removed @CrossOrigin annotation from CallIngestionController (gateway handles CORS)
  - Files Updated:
    - `api-gateway/src/main/java/com/callaudit/gateway/filter/JwtAuthenticationFilter.java` (lines 36-41)
    - `call-auditing-ui/src/lib/api/client.ts` (lines 31-35)
    - `call-ingestion-service/src/main/java/com/callaudit/ingestion/controller/CallIngestionController.java` (line 50)
  - Verification: No 405 errors in logs, file uploads working correctly
  - Status: ✅ RESOLVED
  - Documentation: [file-upload-405-error-analysis.md](ISSUES_AND_SUGGESTIONS/file-upload-405-error-analysis.md)

#### Added
- Test users with working BCrypt password hashes:
  - `analyst@example.com` (ANALYST role) - John Analyst
  - `admin@example.com` (ADMIN role) - Admin User
  - `supervisor@example.com` (SUPERVISOR role) - Jane Supervisor
  - All users have password: `password123`
- Comprehensive UI testing and verification
- Debug endpoints for troubleshooting authentication (can be removed in production)

#### Changed
- Updated all test user password hashes in database to work with Spring Security BCryptPasswordEncoder
- Updated schema.sql with correct BCrypt hashes for future deployments
- User model now specifies schema="gateway" for proper table resolution

#### Documentation
- Created [UI_TESTING_REPORT.md](UI_TESTING_REPORT.md) - Comprehensive test results for login and upload flows
- Updated [UI_BACKEND_INTEGRATION_STATUS.md](UI_BACKEND_INTEGRATION_STATUS.md) - Added resolution summaries
- Updated [ISSUES_AND_SUGGESTIONS/README.md](ISSUES_AND_SUGGESTIONS/README.md) - Added resolved issues
- Updated [ISSUES_AND_SUGGESTIONS/file-upload-405-error-analysis.md](ISSUES_AND_SUGGESTIONS/file-upload-405-error-analysis.md) - Added resolution summary
- Updated [ISSUES_AND_SUGGESTIONS/file-upload-405-software-engineering-assessment.md](ISSUES_AND_SUGGESTIONS/file-upload-405-software-engineering-assessment.md) - Marked as resolved
- Updated [README.md](README.md) - Updated port numbers, credentials, and latest status

### Sentiment Service Fix

#### Fixed
- **NumPy 2.x Compatibility & Blocking Kafka Consumer** - HTTP endpoints not responding
  - Root Causes:
    1. NumPy 2.x breaking changes with RoBERTa sentiment analysis
    2. Synchronous Kafka consumer blocking async event loop
  - Solutions:
    1. Pinned NumPy to <2,>=1.26.4 in requirements.txt
    2. Converted Kafka consumer from synchronous generator to async generator
    3. Changed from `for event` to `async for event` in main.py
    4. Used `consumer.poll(timeout_ms=1000)` instead of iterator pattern
    5. Added `await asyncio.sleep(0)` to yield control to event loop
  - Files Updated:
    - `sentiment-service/requirements.txt` (line 6)
    - `sentiment-service/services/kafka_service.py` (complete rewrite to async)
    - `sentiment-service/main.py` (changed to async for loop)
  - Verification: All HTTP endpoints now responding, Kafka consumer still working
  - Status: ✅ RESOLVED
  - Documentation: [sentiment-service-numpy-compatibility-issue.md](ISSUES_AND_SUGGESTIONS/sentiment-service-numpy-compatibility-issue.md)

### Helper Scripts

#### Added
- `HELPER_SCRIPTS/` directory for deployment and management scripts
- `HELPER_SCRIPTS/rebuild-and-deploy.sh` - Comprehensive rebuild and deployment script
  - Options: --all, --backend, --frontend, --no-cache, --keep-data
  - Automatic infrastructure startup and health checks
  - Database initialization support
  - Supports rebuilding individual services or all services
- `HELPER_SCRIPTS/README.md` - Documentation for helper scripts
- Moved `quick-reset.sh` to HELPER_SCRIPTS directory

#### Changed
- Organized helper scripts into dedicated directory for better maintainability

---

## Test Results Summary (2026-01-02)

### Authentication
- ✅ Login with valid credentials → JWT token + refresh token + user info
- ✅ Protected endpoints require JWT → 401 without token
- ✅ Protected endpoints accept valid JWT → Request passes through
- ✅ Token refresh generates new tokens
- ✅ Current user endpoint retrieves user from JWT
- ✅ Invalid credentials rejected with 401

### File Upload
- ✅ OPTIONS preflight requests pass through JWT filter
- ✅ POST /api/calls/upload accessible with JWT auth
- ✅ File validation working (rejects non-audio files with 400)
- ✅ Valid audio files accepted (would return 201)
- ✅ CORS headers correct (no duplicates)
- ✅ No 405 errors since fixes applied

### UI Flow
- ✅ Login page functional at http://localhost:4142/login
- ✅ Upload page functional at http://localhost:4142/dashboard/calls
- ✅ JWT token stored in localStorage
- ✅ API requests include Authorization header
- ✅ End-to-end flow working correctly

---

## Service Status

All 9 backend services operational:
- ✅ API Gateway (port 8080)
- ✅ Call Ingestion Service (port 8081)
- ✅ Transcription Service (port 8082)
- ✅ Sentiment Service (port 8083) - **Fixed**
- ✅ VoC Service (port 8084)
- ✅ Audit Service (port 8085)
- ✅ Analytics Service (port 8086)
- ✅ Notification Service (port 8087)
- ✅ Monitor Service (port 8088)

Infrastructure:
- ✅ PostgreSQL (port 5432)
- ✅ MinIO (port 9000)
- ✅ Kafka (port 9092)
- ✅ Redis/Valkey (port 6379)

Frontend:
- ✅ Call Auditing UI (port 4142)

---

## Known Issues

See [ISSUES_AND_SUGGESTIONS/README.md](ISSUES_AND_SUGGESTIONS/README.md) for active issues.

### High Priority
- ❌ OpenTelemetry Collector configuration errors
- ❌ MinIO-dependent test failures in call-ingestion-service

### Recently Resolved (2026-01-02)
- ✅ File Upload 405 Method Not Allowed
- ✅ Authentication BCrypt Password Mismatch
- ✅ Sentiment Service NumPy Compatibility & Blocking Kafka Consumer

---

## Links

- [Installation Guide](INSTALLATION.md)
- [Quick Start Guide](QUICK_START.md)
- [UI Testing Report](UI_TESTING_REPORT.md)
- [UI Backend Integration Status](UI_BACKEND_INTEGRATION_STATUS.md)
- [Helper Scripts Documentation](HELPER_SCRIPTS/README.md)
- [Issues and Suggestions](ISSUES_AND_SUGGESTIONS/README.md)
