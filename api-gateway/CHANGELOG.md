# Changelog - API Gateway

All notable changes to the API Gateway service will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

## [1.0.0] - 2026-01-02

### Fixed
- **ERR_INCOMPLETE_CHUNKED_ENCODING Error** - Fixed browser error when uploading files
  - **Root Cause**: `CorrelationIdFilter` was adding response headers in a `.then()` callback after headers were committed (read-only), causing premature connection closure
  - **Solution**: Modified `CorrelationIdFilter.java` to add response headers BEFORE processing the request chain
  - Added `DedupeResponseHeader` filter to prevent duplicate CORS headers (additional safeguard)
  - Disabled `httpclient.wiretap` and `httpserver.wiretap` to prevent chunked transfer encoding interference
  - Updated `src/main/java/com/callaudit/gateway/filter/CorrelationIdFilter.java`
  - Updated configuration in `src/main/resources/application.yml`
  - See troubleshooting section in README.md for details

### Changed
- **JWT Authentication** - Temporarily disabled for `/api/calls/upload` endpoint to allow testing
  - Comment added in `application.yml` noting this should be re-enabled for production
  - TODO item created in project TODO.md for future implementation

### Added
- **Enhanced Documentation** - Comprehensive troubleshooting guide for file upload errors in README.md
  - Detailed root cause analysis
  - Step-by-step solution instructions
  - Verification steps and curl test examples
  - Technical details about Spring Cloud Gateway chunked encoding behavior

## [1.0.0-beta] - 2026-01-01

### Added
- Initial API Gateway implementation
- Spring Cloud Gateway routing to all microservices
- Circuit breaker with Resilience4j
- Redis-backed rate limiting
- CORS configuration for frontend integration
- Correlation ID propagation
- Request/response logging
- OpenTelemetry instrumentation
- JWT authentication filter
- Health check endpoints
- Prometheus metrics export

### Fixed
- **File Upload Timeout** - Added HTTP client response timeout configuration (300s)
  - Configured `spring.cloud.gateway.httpclient.response-timeout: 300s`
  - Configured connection timeout to 30 seconds
  - Set connection pool type to ELASTIC
  - See README.md troubleshooting section for details

### Configuration
- `application.yml` - Main application configuration
- `RouteConfig.java` - Programmatic route configuration (commented out, using YAML)
- `SecurityConfig.java` - Spring Security configuration
- `CorsConfig.java` - CORS policy configuration
- `RateLimitConfig.java` - Rate limiting configuration

### Routes
- `/api/calls/**` → call-ingestion-service:8080
- `/api/voc/**` → voc-service:8080
- `/api/audit/**` → audit-service:8080
- `/api/analytics/**` → analytics-service:8080
- `/api/notifications/**` → notification-service:8080

### Filters
- `JwtAuthenticationFilter` - JWT token validation
- `LoggingFilter` - Request/response logging
- `CorrelationIdFilter` - Correlation ID management
- Circuit breaker with fallback
- Rate limiting per IP
- Retry logic for failed requests

---

## Version History

- **1.0.0** (2026-01-02) - Chunked encoding fix, authentication disabled for testing
- **1.0.0-beta** (2026-01-01) - Initial release with timeout fix
