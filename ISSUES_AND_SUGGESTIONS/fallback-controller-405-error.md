# API Gateway Fallback Controller Returns 405 for POST Requests

## Issue Summary

When the circuit breaker triggers during file uploads or other POST/PUT/DELETE requests, the API Gateway returns a 405 "Method Not Allowed" error instead of a proper 503 "Service Unavailable" response.

## Status: RESOLVED

Fixed by updating `FallbackController` to support all HTTP methods.

## Root Cause

The `FallbackController.java` only supported GET requests:

```java
@GetMapping("/fallback")  // Only supports GET!
public Mono<ResponseEntity<Map<String, Object>>> fallback() { ... }
```

When the circuit breaker forwards non-GET requests to `/fallback`, Spring returns 405 because no handler supports those methods.

## Evidence

From `api-gateway` logs during a file upload:

```
2026-01-02 23:18:42 - POST /api/calls/upload, Content-Length:"2469721"
2026-01-02 23:18:43 - MethodNotAllowedException: 405 METHOD_NOT_ALLOWED "Request method 'POST' is not supported."
2026-01-02 23:18:44 - Route matched: call-ingestion-service (retry succeeded)
```

## Circuit Breaker Trigger Analysis

The circuit breaker triggered due to:

1. **Large file upload** (2.4MB) taking longer than expected
2. **Timeout during streaming** - Gateway was still receiving the multipart upload
3. **Circuit breaker policy** - After meeting minimum call threshold with high failure rate, circuit opened

Configuration in `application.yml`:
```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
```

## Solution Applied

Changed the fallback endpoint to support all HTTP methods:

```java
@RequestMapping(
    value = "/fallback",
    method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH}
)
public Mono<ResponseEntity<Map<String, Object>>> fallback() { ... }
```

## Files Modified

- `api-gateway/src/main/java/com/callaudit/gateway/controller/FallbackController.java`

## Verification

After the fix, when the circuit breaker triggers for any HTTP method, the fallback returns:

```json
{
    "timestamp": "2026-01-03T...",
    "status": 503,
    "error": "Service Unavailable",
    "message": "The requested service is temporarily unavailable. Please try again later.",
    "details": "Circuit breaker is open due to high error rate or timeout"
}
```

## Related Issues

- WebSocket connections to `/ws/calls/{callId}` fail because no WebSocket route is configured
- Transcription 404 errors are expected when transcription is not yet complete

## Recommendations

1. **Increase upload timeout** - Consider increasing the gateway's HTTP client timeout for large file uploads
2. **Add WebSocket route** - Configure a route for `/ws/**` if real-time updates are needed
3. **Circuit breaker tuning** - Consider adjusting thresholds for file upload routes specifically
