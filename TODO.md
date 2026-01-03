# Call Auditing Platform - TODO List

## Frontend / UI Cleanup

### NextAuth Removal (Optional Cleanup)
After fixing the authentication errors (2026-01-02), these NextAuth artifacts can be removed:

- [ ] **Remove NextAuth Dependency**
  - [ ] Remove `"next-auth": "^5.0.0-beta.25"` from `call-auditing-ui/package.json`
  - [ ] Run `npm install` to update package-lock.json
  - [ ] Verify app still works without the dependency

- [ ] **Remove NextAuth Environment Variables**
  - [ ] Remove `NEXTAUTH_URL=http://localhost:4142` from `call-auditing-ui/.env.local`
  - [ ] Remove `NEXTAUTH_SECRET=...` from `call-auditing-ui/.env.local`
  - [ ] Update `.env.example` if it exists

**Context**: The app uses custom Zustand authentication, not NextAuth. SessionProvider was removed from `providers.tsx` to fix ClientFetchError and Network Error on page load.

**Priority**: Low (optional cleanup - does not affect functionality)

---

## Testing

### Python Services - Unit Tests Setup
These services have comprehensive tests but require Python dependencies to run.

- [ ] **transcription-service**
  - [ ] Set up Python virtual environment (`python3 -m venv venv`)
  - [ ] Install dev dependencies (`pip install -r requirements-dev.txt`)
  - [ ] Run tests (`pytest tests/ -v`)
  - [ ] Verify all tests pass

- [ ] **sentiment-service**
  - [ ] Set up Python virtual environment (`python3 -m venv venv`)
  - [ ] Install dev dependencies (`pip install -r requirements-dev.txt`)
  - [ ] Run tests (`pytest tests/ -v`)
  - [ ] Verify all tests pass

### Spring Boot Services - Test Status ✅
- [x] **audit-service** - 84 tests, ALL PASSING
- [x] **analytics-service** - 87 tests, ALL PASSING
- [x] **notification-service** - 110 tests, ALL PASSING
- [ ] **call-ingestion-service** - Tests exist but not yet verified
- [x] **voc-service** - 97 tests, 95 PASSING, 2 SKIPPED (see below)
- [ ] **monitor-service** - Tests exist but not yet verified

### Skipped Tests - Require PostgreSQL-Specific Features
These tests are skipped because they use PostgreSQL-specific functions not available in H2 (test database):

- [ ] **voc-service: VocInsightRepositoryTest**
  - [ ] `findTopKeywords_WithMultipleInsights_ReturnsKeywordFrequencies`
    - Uses `jsonb_array_elements_text()` PostgreSQL function
    - Location: `voc-service/src/test/java/com/callaudit/voc/repository/VocInsightRepositoryTest.java:219`
  - [ ] `findTopTopics_WithMultipleInsights_ReturnsTopicFrequencies`
    - Uses `jsonb_array_elements_text()` PostgreSQL function
    - Location: `voc-service/src/test/java/com/callaudit/voc/repository/VocInsightRepositoryTest.java:247`
  - **Resolution Options:**
    1. Use Testcontainers with PostgreSQL for integration tests
    2. Create separate integration test profile with real PostgreSQL
    3. Mock the repository methods in unit tests instead

## Service Implementation

### Spring Boot Version Upgrades
Upgrade remaining services from Spring Boot 3.2.5 to 4.0.0:

- [ ] **audit-service** - Upgrade to Spring Boot 4.0.0
  - [ ] Update pom.xml parent version to 4.0.0
  - [ ] Update tests to Spring Boot 4.0.0 patterns (see SPRING_BOOT_4_TESTING_GUIDE.md)
  - [ ] Replace removed annotations (@MockBean → @Primary with Mockito)
  - [ ] Verify all 84 tests still pass

- [ ] **analytics-service** - Upgrade to Spring Boot 4.0.0
  - [ ] Update pom.xml parent version to 4.0.0
  - [ ] Update tests to Spring Boot 4.0.0 patterns (see SPRING_BOOT_4_TESTING_GUIDE.md)
  - [ ] Replace removed annotations (@MockBean → @Primary with Mockito)
  - [ ] Verify all 87 tests still pass

- [ ] **api-gateway** - Upgrade to Spring Boot 4.0.0
  - [ ] Update pom.xml parent version to 4.0.0
  - [ ] Test Spring Cloud Gateway compatibility with Spring Boot 4.0.0
  - [ ] Update R2DBC configuration if needed
  - [ ] Update tests (if any exist)

- [ ] **notification-service** - Upgrade to Spring Boot 4.0.0
  - [ ] Update pom.xml parent version to 4.0.0
  - [ ] Update tests to Spring Boot 4.0.0 patterns (see SPRING_BOOT_4_TESTING_GUIDE.md)
  - [ ] Replace removed annotations (@MockBean → @Primary with Mockito)
  - [ ] Verify all 110 tests still pass

### Core Services (Not Yet Implemented)
These services have infrastructure but lack business logic:

- [ ] **call-ingestion-service**
  - [ ] Implement audio upload endpoint
  - [ ] Add MinIO integration for storage
  - [ ] Implement Kafka event publishing (CallReceived)
  - [ ] Add file validation (WAV, MP3, M4A)

- [ ] **transcription-service**
  - [ ] Implement Whisper v3 integration
  - [ ] Add Kafka consumer for CallReceived events
  - [ ] Implement transcription logic
  - [ ] Publish CallTranscribed events
  - [ ] Add speaker diarization

- [ ] **sentiment-service**
  - [ ] Implement RoBERTa sentiment analysis
  - [ ] Add VADER fallback logic
  - [ ] Add Kafka consumer for CallTranscribed events
  - [ ] Implement escalation detection
  - [ ] Publish SentimentAnalyzed events

- [ ] **voc-service**
  - [ ] Implement Voice of Customer insights extraction
  - [ ] Add theme detection
  - [ ] Add keyword extraction
  - [ ] Consume CallTranscribed and SentimentAnalyzed events
  - [ ] Publish VoCAAnalyzed events

- [ ] **audit-service**
  - [ ] Implement compliance rule evaluation logic
  - [ ] Add keyword checking
  - [ ] Add prohibited words detection
  - [ ] Add sentiment response validation
  - [ ] Publish CallAudited events

- [ ] **analytics-service**
  - [ ] Implement event consumption for all event types
  - [ ] Build materialized views
  - [ ] Add TimescaleDB hypertable integration
  - [ ] Implement dashboard metrics aggregation

- [ ] **notification-service**
  - [ ] Implement alert rule engine
  - [ ] Add email notification integration
  - [ ] Add Slack notification integration
  - [ ] Implement notification templates

## Infrastructure & DevOps

- [ ] **Restore Observability Stack**
  - Observability services are currently commented out in `docker-compose.yml` to reduce resource usage during development.
  - To restore, uncomment these services in `docker-compose.yml`:
    - [ ] `prometheus` (lines ~249-258)
    - [ ] `grafana` (lines ~260-271)
    - [ ] `jaeger` (lines ~274-285)
    - [ ] `otel-collector` (lines ~288-299)
  - Restore `depends_on: otel-collector` in these services (search for "# Restore when observability is re-enabled"):
    - [ ] `call-ingestion-service`
    - [ ] `voc-service`
    - [ ] `audit-service`
    - [ ] `analytics-service`
    - [ ] `notification-service`
    - [ ] `monitor-service`
    - [ ] `api-gateway`
  - After uncommenting, run: `docker compose up -d prometheus grafana jaeger otel-collector`

- [ ] **CI/CD Pipeline**
  - [ ] Set up GitHub Actions / Jenkins
  - [ ] Add automated testing on PR
  - [ ] Add Docker image building
  - [ ] Add deployment automation

- [ ] **Service Authentication**
  - [ ] Implement service-to-service authentication
  - [ ] Add JWT token validation
  - [ ] Secure Kafka topics

- [ ] **Monitoring & Observability**
  - [ ] Create Grafana dashboards
  - [ ] Add custom metrics
  - [ ] Set up alerting rules
  - [ ] Configure log aggregation

- [ ] **Future Enhancements**
  - [ ] **Event Store Manager Service** (Future - See database migration plan Section 9.1)
    - [ ] Design dedicated service for event persistence
    - [ ] Implement Kafka listener for all event topics
    - [ ] Create event persistence logic (batch writes, optimized indexes)
    - [ ] Build query/replay API for event sourcing
    - [ ] Remove direct event_store writes from application services
    - [ ] Consider migration to EventStoreDB or similar specialized database
    - [ ] Document in `docs/EVENT_STORE_MANAGER.md`

## Documentation

- [ ] **API Documentation**
  - [ ] Complete OpenAPI/Swagger specs for all services
  - [ ] Add request/response examples
  - [ ] Document error codes

- [ ] **Developer Guide**
  - [ ] Add service development workflow
  - [ ] Document testing strategies
  - [ ] Add debugging guide

- [ ] **Deployment Guide**
  - [ ] Document production deployment
  - [ ] Add scaling guidelines
  - [ ] Document backup/restore procedures

## Database

- [ ] **Schema Implementation**
  - [ ] Verify schema.sql is complete
  - [ ] Add database migrations
  - [ ] Create indexes for performance
  - [ ] Set up TimescaleDB hypertables
  - [ ] **Phase 1: Schema Separation** (See `.private/database-schema-migration-plan.md`)
    - [ ] Organize tables into service-specific schemas
    - [ ] Remove cross-schema foreign key constraints
    - [ ] Implement application-level referential integrity
    - [ ] Update JPA entities with schema annotations
    - [ ] Configure per-service database users

- [ ] **Data Retention**
  - [ ] Implement data archival strategy
  - [ ] Add data cleanup jobs
  - [ ] Configure retention policies

- [ ] **Data Quality & Consistency**
  - [ ] **Orphaned Data Detection** (Future - See database migration plan Section 9.2)
    - [ ] Implement scheduled jobs to detect orphaned records
    - [ ] Add consistency check API endpoints
    - [ ] Set up Prometheus metrics for orphaned record counts
    - [ ] Create Grafana alerts for high orphan counts
    - [ ] Document cleanup procedures (manual review vs automated)
  - [ ] **Data Consistency Monitoring**
    - [ ] Log application-level validation failures
    - [ ] Track event processing failures
    - [ ] Monitor cross-schema reference integrity

## Security

- [ ] **API Authentication & Authorization**
  - [ ] **Call Upload Endpoint Security** (High Priority)
    - [ ] Re-enable JWT authentication for `/api/calls/upload` endpoint
    - [ ] Implement role-based access control (RBAC) for uploads
    - [ ] Add user attribution to uploaded calls (track who uploaded)
    - [ ] Add audit logging for all upload attempts
    - [ ] See also: "Calls Page - Security & Permissions" section below
    - **Context**: Authentication temporarily disabled (2026-01-02) to allow testing.
      See `api-gateway/src/main/resources/application.yml:86`

- [ ] **Input Validation**
  - [ ] Add comprehensive input validation to all endpoints
  - [ ] Implement request size limits
  - [ ] Add rate limiting

- [ ] **Security Scanning**
  - [ ] Add dependency vulnerability scanning
  - [ ] Implement SAST/DAST
  - [ ] Add container scanning

## Performance

- [ ] **Load Testing**
  - [ ] Create load test scenarios
  - [ ] Test Kafka throughput
  - [ ] Test database performance
  - [ ] Identify bottlenecks

- [ ] **Optimization**
  - [ ] Optimize database queries
  - [ ] Add caching where appropriate
  - [ ] Tune Kafka consumer settings
  - [ ] Optimize Docker images

## Completed ✅

- [x] Docker Compose orchestration setup
- [x] Infrastructure services configuration (Kafka, PostgreSQL, MinIO, OpenSearch, Valkey)
- [x] Monitoring stack setup (Prometheus, Grafana, Jaeger, OpenTelemetry)
- [x] Spring Boot service test fixes (audit, analytics, notification)
- [x] Database schema with TimescaleDB support
- [x] Service logging configuration
- [x] Project documentation (architecture docs, guides)
- [x] **API Gateway File Upload Fix** (2026-01-01)
  - Fixed ERR_INCOMPLETE_CHUNKED_ENCODING error for large file uploads
  - Added HTTP client response timeout configuration (300s)
  - Documented in api-gateway/README.md troubleshooting section
- [x] **UI Authentication Errors Fix** (2026-01-02)
  - Fixed ClientFetchError: "The string did not match the expected pattern" on page load
  - Fixed Network Error on initial page load
  - Root cause: NextAuth SessionProvider initialized without configuration
  - Solution: Removed SessionProvider from providers.tsx (app uses Zustand auth)
  - Documented in ISSUES_AND_SUGGESTIONS/expert-comparison-and-final-recommendation.md
- [x] **API Gateway Chunked Encoding Fix** (2026-01-02)
  - Fixed ERR_INCOMPLETE_CHUNKED_ENCODING error for file uploads in Chrome
  - Root cause: `CorrelationIdFilter` was adding response headers in a `.then()` callback after headers were committed (read-only), causing premature connection closure
  - Solutions applied:
    - **Primary fix**: Modified `CorrelationIdFilter.java` to add response headers BEFORE processing the request chain
    - Added DedupeResponseHeader filter to prevent duplicate CORS headers (additional safeguard)
    - Disabled httpclient.wiretap and httpserver.wiretap to prevent chunked encoding interference
    - JWT authentication temporarily disabled for /api/calls/upload endpoint (for testing purposes)
  - Updated api-gateway/README.md with comprehensive troubleshooting documentation
  - Updated api-gateway/CHANGELOG.md with detailed root cause analysis
  - See api-gateway/src/main/java/com/callaudit/gateway/filter/CorrelationIdFilter.java
  - See api-gateway/src/main/resources/application.yml:35-39, 185-188
- [x] **OTLP Collector Logs Pipeline** (2026-01-02)
  - Fixed 404 errors when services export logs to OpenTelemetry collector
  - Added logs pipeline to monitoring/otel-collector-config.yaml
  - Restarted otel-collector service
- [x] **Kafka Consumer Stabilization** (2026-01-02)
  - Fixed Python services (transcription-service, sentiment-service) not connecting to Kafka
  - Root cause: Services started before Kafka was ready, no retry logic
  - Solution: Restarted Python services after Kafka was fully initialized
  - All consumer groups now active with 0 lag

---

**Last Updated:** 2026-01-02

**Note:** This is a skeleton/prototype project. Core business logic implementation is the primary outstanding work.

## Calls Page - Security & Permissions (FUTURE)

### Session Management & Authentication
- [ ] **Session Expiration Handling**
  - [ ] Detect expired token errors (401 Unauthorized) in API responses
  - [ ] Show user-friendly alert/modal: "Your session has expired"
  - [ ] Redirect to login page after user clicks "Ok"
  - [ ] Clear localStorage auth tokens on session expiration
  - [ ] Implement automatic token refresh before expiration (optional)
  - [ ] Add session timeout warning (e.g., "Your session will expire in 5 minutes")

### File Upload Security
- [ ] **Role-Based Access Control**
  - [ ] Implement user roles (admin, manager, agent, viewer)
  - [ ] Restrict upload capability to specific roles
  - [ ] Add permission checks before showing upload UI
  - [ ] Add role-based API endpoint protection

- [ ] **File Upload Validation**
  - [ ] Implement virus/malware scanning integration
  - [ ] Validate audio file integrity (detect corrupted files)
  - [ ] Implement file checksum verification
  - [ ] Add rate limiting for uploads (per user/per hour)
  - [ ] Implement upload quota system

- [ ] **File Encryption & Storage**
  - [ ] Encrypt files at rest in MinIO (AES-256)
  - [ ] Manage encryption keys securely
  - [ ] Add audit logging for all upload attempts
  - [ ] Log file access/download events
  - [ ] Track metadata changes

### Transcription Access Control
- [ ] **View Permissions**
  - [ ] Implement view own uploads only (agent role)
  - [ ] Implement view team uploads (manager role)
  - [ ] Implement view all uploads (admin role)
  - [ ] Add permission checks in UI and API

- [ ] **Edit Permissions**
  - [ ] Implement edit own transcriptions only
  - [ ] Implement edit team transcriptions (manager)
  - [ ] Add transcription locking after compliance review
  - [ ] Track edit history with user attribution

- [ ] **Data Masking & PII Protection**
  - [ ] Auto-detect and mask PII (SSN, credit card numbers)
  - [ ] Redact sensitive segments (configurable patterns)
  - [ ] Implement role-based unmasking permissions
  - [ ] Add audit trail for unmasking actions

---

**Last Updated:** 2026-01-01
