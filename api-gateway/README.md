# API Gateway

Spring Cloud Gateway for the Call Auditing Platform - main entry point for all API requests.

## Overview

The API Gateway routes HTTP requests to appropriate microservices, providing:

- **Request Routing** - Routes to downstream services based on URL paths
- **Circuit Breaker** - Prevents cascading failures when services are down
- **Rate Limiting** - Redis/Valkey-backed distributed rate limiting
- **CORS Support** - Configurable cross-origin resource sharing
- **Correlation ID** - Automatic correlation ID propagation for distributed tracing
- **Request/Response Logging** - Comprehensive logging of all requests
- **Health Checks** - Actuator endpoints for monitoring

## Port

**8080** - Main API entry point

## Routes

| Path Pattern | Target Service | Description |
|--------------|----------------|-------------|
| `/api/calls/**` | call-ingestion-service:8080 | Audio upload and call management |
| `/api/voc/**` | voc-service:8080 | Voice of Customer insights |
| `/api/audit/**` | audit-service:8080 | Compliance auditing |
| `/api/analytics/**` | analytics-service:8080 | Metrics and analytics |
| `/api/notifications/**` | notification-service:8080 | Alerts and notifications |

## Running the Service

### With Docker Compose (Recommended)

```bash
# Build and start
docker compose up -d api-gateway

# View logs
docker compose logs -f api-gateway

# Rebuild after code changes
docker compose up -d --build api-gateway
```

### Locally (for development)

```bash
# Build
./mvnw clean package

# Run tests
./mvnw test

# Run application
./mvnw spring-boot:run

# Or run the JAR directly
java -jar target/api-gateway-1.0.0-SNAPSHOT.jar
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `REDIS_HOST` | valkey | Redis/Valkey host for rate limiting |
| `REDIS_PORT` | 6379 | Redis/Valkey port |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | http://otel-collector:4317 | OpenTelemetry endpoint |
| `OTEL_SERVICE_NAME` | api-gateway | Service name for tracing |

## Endpoints

### Gateway Endpoints

- `GET /info` - Gateway information and available routes
- `GET /health` - Simple health check
- `GET /fallback` - Circuit breaker fallback endpoint

### Actuator Endpoints

- `GET /actuator/health` - Detailed health status
- `GET /actuator/prometheus` - Prometheus metrics
- `GET /actuator/gateway/routes` - List all configured routes

## Features

### Circuit Breaker

Resilience4j-based circuit breaker prevents cascading failures:

- **Sliding Window**: 10 requests
- **Failure Threshold**: 50%
- **Wait Duration**: 10 seconds in open state
- **Half-Open Calls**: 3 permitted calls to test recovery

When a circuit opens, requests are redirected to `/fallback` endpoint.

### Rate Limiting

Redis-backed rate limiting per IP address:

- **Replenish Rate**: 10 requests/second
- **Burst Capacity**: 20 requests
- **Key Resolution**: By client IP address

Can be configured to use user ID, API key, or other identifiers.

### Correlation ID Propagation

Automatic correlation ID management:

1. If request has `X-Correlation-ID` header, it's preserved
2. Otherwise, a new UUID is generated
3. Correlation ID is added to all downstream requests
4. Correlation ID is included in response headers

This enables end-to-end tracing across all microservices.

### Request/Response Logging

All requests are logged with:

- HTTP method and path
- Client IP address
- Correlation ID and Request ID
- Response status code
- Processing duration

## Configuration

### HTTP Client Configuration (File Uploads)

**Critical for large file uploads**. The API Gateway uses Spring Cloud Gateway's HTTP client with configured timeouts and chunked encoding fixes to handle multipart file uploads properly.

Located in `src/main/resources/application.yml`:

```yaml
spring:
  cloud:
    gateway:
      httpclient:
        response-timeout: 300s  # 5 minutes for large file uploads
        connect-timeout: 30000  # 30 seconds
        wiretap: false  # Disable to prevent chunked encoding issues
        pool:
          type: ELASTIC
          max-idle-time: 30s

      httpserver:
        wiretap: false  # Disable server wiretap

      # Default filters for all routes
      default-filters:
        - name: DedupeResponseHeader
          args:
            name: Access-Control-Allow-Credentials Access-Control-Allow-Origin
            strategy: RETAIN_FIRST
```

**Why this is needed**:
1. **Timeouts**: Without proper timeouts, large file uploads (>1MB) will timeout during:
   - Multipart form data parsing
   - Uploading to MinIO object storage
   - Writing to database
   - Publishing Kafka events

2. **Wiretap Disabled**: Spring Cloud Gateway's wiretap feature (used for debugging) interferes with chunked transfer encoding, causing incomplete responses

3. **DedupeResponseHeader**: Prevents duplicate CORS headers that can break chunked responses when proxying through the gateway

**Troubleshooting File Upload Errors**:
- If uploads fail with "Network Error" or "ERR_INCOMPLETE_CHUNKED_ENCODING", verify all three configurations above
- For very large files (>100MB), increase `response-timeout` to 600s (10 minutes)
- Monitor gateway logs during uploads to identify issues
- Test with curl first to isolate browser-specific problems

### CORS Configuration

Located in `src/main/java/com/callaudit/gateway/config/CorsConfig.java`

**Development settings** (allows all origins):
```yaml
allowed-origins: "*"
allowed-methods: [GET, POST, PUT, DELETE, PATCH, OPTIONS]
allowed-headers: "*"
```

**Production**: Restrict `allowed-origins` to specific domains.

### Route Configuration

Routes can be configured in two ways:

1. **YAML** (current): `src/main/resources/application.yml`
2. **Programmatic**: `src/main/java/com/callaudit/gateway/config/RouteConfig.java`

See `RouteConfig.java` for programmatic examples (currently commented out).

## Monitoring

### Prometheus Metrics

Gateway exposes metrics at `/actuator/prometheus`:

- Request counts by route
- Response times (histogram)
- Circuit breaker states
- Rate limiter metrics

Prometheus scrapes these metrics every 15 seconds.

### Distributed Tracing

OpenTelemetry Java Agent auto-instruments the gateway:

- Spans created for each request
- Spans include correlation ID
- Traces visible in Jaeger UI (http://localhost:16686)

### Health Checks

Multiple health check endpoints:

- `/actuator/health` - Actuator health (includes circuit breaker status)
- `/health` - Simple health check
- `/actuator/health/readiness` - Kubernetes readiness probe
- `/actuator/health/liveness` - Kubernetes liveness probe

## Development

### Adding a New Route

**Option 1: YAML (easier)**

Edit `src/main/resources/application.yml`:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: new-service
          uri: http://new-service:8080
          predicates:
            - Path=/api/new/**
          filters:
            - RewritePath=/api/new/(?<segment>.*), /${segment}
            - name: CircuitBreaker
              args:
                name: newServiceCircuitBreaker
                fallbackUri: forward:/fallback
```

**Option 2: Programmatic**

Add route in `RouteConfig.java`:

```java
.route("new-service", r -> r
    .path("/api/new/**")
    .filters(f -> f
        .rewritePath("/api/new/(?<segment>.*)", "/${segment}")
        .circuitBreaker(config -> config
            .setName("newServiceCircuitBreaker")
            .setFallbackUri("forward:/fallback")))
    .uri("http://new-service:8080"))
```

### Custom Filters

Create global filters in `src/main/java/com/callaudit/gateway/filter/`:

```java
@Component
public class CustomFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Your logic here
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
```

## Testing

### Manual Testing

```bash
# Test gateway info endpoint
curl http://localhost:8080/info

# Test health check
curl http://localhost:8080/actuator/health

# Test routing (requires downstream service running)
curl http://localhost:8080/api/calls/health

# Test with correlation ID
curl -H "X-Correlation-ID: test-123" http://localhost:8080/api/calls/health

# Test rate limiting (make 25+ rapid requests)
for i in {1..25}; do curl http://localhost:8080/info; done
```

### Unit Tests

```bash
./mvnw test
```

### Integration Tests

```bash
# Requires Docker for Testcontainers
./mvnw verify
```

## Troubleshooting

### Gateway won't start

1. Check Redis/Valkey is running:
   ```bash
   docker compose ps valkey
   ```

2. Check logs:
   ```bash
   docker compose logs -f api-gateway
   ```

### Routes not working

1. View configured routes:
   ```bash
   curl http://localhost:8080/actuator/gateway/routes
   ```

2. Check downstream service is running:
   ```bash
   docker compose ps call-ingestion-service
   ```

### Circuit breaker always open

1. Check circuit breaker status:
   ```bash
   curl http://localhost:8080/actuator/health
   ```

2. Reduce failure threshold in `application.yml` for testing

### Rate limiting not working

1. Verify Redis connection:
   ```bash
   docker compose exec valkey redis-cli ping
   ```

2. Check rate limiter configuration in `application.yml`

### File uploads failing with "Network Error" or ERR_INCOMPLETE_CHUNKED_ENCODING

**Symptoms**:
- Browser shows "No response from server" or "Network Error"
- Browser DevTools shows `ERR_INCOMPLETE_CHUNKED_ENCODING` with HTTP 201 status
- Backend logs show successful file upload and Kafka event publishing
- File is saved to MinIO but frontend receives no response

**Root Causes**:
1. **Primary**: `CorrelationIdFilter` trying to modify response headers after they're committed (read-only)
   - Filter was adding correlation ID headers in a `.then()` callback
   - Response headers are committed before the callback executes
   - Attempting to modify read-only headers causes premature connection closure
   - Chunked encoding stream terminates before sending final chunk
2. API Gateway HTTP client response timeout too short for file upload operations
3. Spring Cloud Gateway chunked transfer encoding not properly terminated
4. Duplicate CORS headers interfering with response

**Solution**:

1. **Fix CorrelationIdFilter** in `src/main/java/com/callaudit/gateway/filter/CorrelationIdFilter.java`:

   Change the filter to add response headers BEFORE processing the request chain, not in a `.then()` callback:

   ```java
   @Override
   public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
       // ... correlation ID generation code ...

       // Add headers to response BEFORE processing the request
       exchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, correlationId);
       exchange.getResponse().getHeaders().add(REQUEST_ID_HEADER, requestId);

       // Process the request
       return chain.filter(mutatedExchange);
   }
   ```

   **Do NOT add headers in a `.then()` callback** - headers are read-only after the response starts.

2. **Configure HTTP Client Timeouts** in `application.yml`:
   ```yaml
   spring:
     cloud:
       gateway:
         httpclient:
           response-timeout: 300s  # 5 minutes for large uploads
           connect-timeout: 30000  # 30 seconds
           wiretap: false  # Disable to prevent chunked encoding issues

         httpserver:
           wiretap: false  # Disable server wiretap
   ```

3. **Add DedupeResponseHeader Filter** in `application.yml`:
   ```yaml
   spring:
     cloud:
       gateway:
         default-filters:
           - name: DedupeResponseHeader
             args:
               name: Access-Control-Allow-Credentials Access-Control-Allow-Origin
               strategy: RETAIN_FIRST
   ```

4. **Rebuild and Restart** API Gateway:
   ```bash
   docker compose build api-gateway
   docker compose up -d api-gateway
   ```

5. For very large files (>100MB), increase `response-timeout` to 600s or more

**How to verify**:
- Check gateway logs: `docker compose logs api-gateway | grep -i error`
- Monitor upload completion: `docker compose logs call-ingestion-service | grep "Successfully uploaded"`
- Test with curl first to isolate browser-specific issues:
  ```bash
  curl -X POST http://localhost:8080/api/calls/upload \
    -F "file=@test.wav" \
    -F "callerId=555-0123" \
    -F "agentId=agent-001"
  ```
- Test with small file first (<1MB) to rule out timeout issues

**Technical Details**:
- **Primary Issue**: In Spring Cloud Gateway's reactive model, response headers must be set before the response body starts streaming
- Global filters that try to modify headers in `.then()` or `.doFinally()` callbacks will fail because headers are committed (read-only) by that point
- The `CorrelationIdFilter` fix ensures headers are added to `exchange.getResponse().getHeaders()` BEFORE calling `chain.filter()`
- The `DedupeResponseHeader` filter prevents duplicate CORS headers that can cause incomplete chunked responses
- Disabling `wiretap` prevents Spring Cloud Gateway's debug logging from interfering with chunked transfer encoding
- These issues commonly occur when proxying responses through Spring Cloud Gateway in reactive mode

**Fixed**:
- 2026-01-01 - Added default 300s response timeout configuration
- 2026-01-02 - Added DedupeResponseHeader filter and disabled wiretap
- 2026-01-02 - Fixed CorrelationIdFilter to add headers before response (primary fix for ERR_INCOMPLETE_CHUNKED_ENCODING)

## Architecture

```
Client
  ↓
API Gateway (Port 8080)
  ├→ CorrelationIdFilter (adds correlation ID)
  ├→ LoggingFilter (logs request/response)
  ├→ Route Matching
  ├→ Circuit Breaker
  ├→ Rate Limiter (Redis/Valkey)
  ↓
Downstream Microservice
```

## Dependencies

- **Spring Cloud Gateway** - Reactive API Gateway
- **Spring Boot Actuator** - Monitoring endpoints
- **Resilience4j** - Circuit breaker
- **Spring Data Redis Reactive** - Rate limiting backend
- **Micrometer Prometheus** - Metrics export
- **OpenTelemetry Java Agent** - Distributed tracing (runtime)

## References

- [Spring Cloud Gateway Docs](https://spring.io/projects/spring-cloud-gateway)
- [Resilience4j Circuit Breaker](https://resilience4j.readme.io/docs/circuitbreaker)
- [OpenTelemetry Java](https://opentelemetry.io/docs/instrumentation/java/)
- [Project Architecture](../call_auditing_architecture.md)
