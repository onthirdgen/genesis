# Changelog

All notable changes to the Call Ingestion Service will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] - 2025-12-31

### Added
- Initial implementation of Call Ingestion Service
- REST API endpoint for audio file uploads (`POST /api/calls/upload`)
- PostgreSQL integration with TimescaleDB support for call metadata storage
- MinIO integration for S3-compatible audio file storage
- Kafka event publishing for `CallReceived` events
- Spring Boot Actuator endpoints for health checks and Prometheus metrics
- Contract tests for integration testing with real infrastructure (PostgreSQL, MinIO, Kafka)
- Unit and integration tests with 54 test cases
- OpenTelemetry instrumentation for distributed tracing
- Docker support with multi-stage build

### Changed
- **[CRITICAL]** Upgraded Testcontainers from 1.19.3 to 2.0.3 to fix Docker 29.x compatibility
  - **Issue**: Testcontainers 1.19.3 incompatible with Docker Engine 29+ (API version mismatch)
  - **Root Cause**: Testcontainers 1.19.3 uses Docker API v1.32, Docker 29+ requires v1.44+
  - **Solution**: Upgraded to Testcontainers 2.0.3 which supports Docker API v1.44+
  - **Impact**: Contract tests now pass successfully on Docker Engine 29.1.3
  - **See**: `/ISSUES_AND_SUGGESTIONS/contract-tests-failure-root-cause-analysis.md`

### Fixed
- PostgreSQL connection configuration for TimescaleDB compatibility
  - Added `hibernate.temp.use_jdbc_metadata_defaults: false` to prevent SQLSTATE(0A000) errors
  - Configured HikariCP connection pooling for reliability
- MinIO dependency resolution by adding explicit OkHttp3 dependency
- Database schema compatibility: using VARCHAR for enum columns instead of PostgreSQL custom enum types

### Dependencies
- Spring Boot: 4.0.0
- Java: 21
- PostgreSQL Driver: 42.7.4 (managed by Spring Boot)
- MinIO SDK: 8.6.0
- Testcontainers: 2.0.3
- OkHttp3: 4.12.0
- lz4-java: 1.10.1 (CVE-2025-66566 fix)

### Testing
- Unit Tests: 54 tests (all passing)
- Contract Tests: 3 tests (all passing)
  - Full integration testing with PostgreSQL (TimescaleDB), MinIO, and Kafka via Testcontainers
  - Run with: `./mvnw verify -Pintegration`

### Configuration
- Default Port: 8081
- Database: PostgreSQL 16 with TimescaleDB extension
- Object Storage: MinIO (S3-compatible)
- Message Broker: Kafka 3.7+ (KRaft mode)
- Observability: OpenTelemetry, Prometheus, Grafana, Jaeger

### Known Issues
- None

### Security
- Fixed CVE-2025-66566 in lz4-java by switching to maintained fork (at.yawk.lz4:lz4-java)
- Using Spring Boot 4.0.0 with latest security patches

---

## Version History

### Versioning Strategy
- **MAJOR**: Breaking API changes
- **MINOR**: New features, backward-compatible
- **PATCH**: Bug fixes, backward-compatible

### Release Notes Location
- Detailed architecture: `/call_auditing_architecture.md`
- Testing guide: `/SPRING_BOOT_4_TESTING_GUIDE.md`
- Known issues: `/ISSUES_AND_SUGGESTIONS/`

---

**Maintained by**: Call Auditing Platform Team
**Last Updated**: 2025-12-31
