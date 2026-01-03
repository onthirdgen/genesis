# File Upload 405 Error - Software Engineering Expert Assessment

**Date**: 2026-01-01
**Status**: ✅ **RESOLVED** (2026-01-02)
**Assessor**: Software Engineering Expert Agent
**Related Analysis**: [UI Expert Analysis](./file-upload-405-error-analysis.md)

---

## Executive Summary

The software engineering expert **validates** the UI expert's root cause analysis and provides additional technical depth, security considerations, and a comprehensive testing strategy.

---

## 1. Root Cause Analysis Validation

### Primary Issue: JWT Filter Blocking OPTIONS Requests ✅ CONFIRMED
**Severity**: CRITICAL

The JWT filter failing to handle CORS preflight OPTIONS requests is the primary blocker. This is a classic mistake in API Gateway authentication patterns.

**Why this causes 405 Method Not Allowed:**
1. Browser sends OPTIONS preflight → Gateway JWT filter intercepts
2. No "OPTIONS bypass" logic exists → Filter attempts to validate JWT
3. No Authorization header in OPTIONS (by CORS spec) → Returns 401 Unauthorized
4. In some configurations, when auth filters reject before routing occurs, it manifests as 405

### Secondary Issue: Hardcoded Content-Type ✅ CONFIRMED
**Severity**: MAJOR

Setting `Content-Type: multipart/form-data` without the boundary parameter causes the server to reject the request.

```typescript
// WRONG - Strips auto-generated boundary
headers: {
  'Content-Type': 'multipart/form-data'
}

// CORRECT - Let axios/browser handle it
// (no Content-Type header specified for FormData)
```

### Tertiary Issue: CORS Configuration Sprawl ✅ CONFIRMED
**Severity**: MODERATE

Having CORS configured in multiple layers is an anti-pattern that leads to:
- Header duplication
- Conflicting configurations
- Debugging nightmares
- Response commitment race conditions

---

## 2. Additional Issues Discovered

### Issue #4: The 405 Mystery - Route Predicate Problem 🔍

**405 Method Not Allowed** specifically means the route exists but doesn't accept the HTTP method. This suggests a possible route predicate mismatch.

**Check Gateway route configuration:**
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: call-ingestion-service
          uri: lb://call-ingestion-service
          predicates:
            - Path=/api/calls/**
            - Method=POST,GET,PUT,DELETE  # ❌ Missing OPTIONS?
```

If route predicates specify methods explicitly and omit OPTIONS, you'll get 405.

**Fix Options:**
1. Remove `Method` predicate entirely (allow all methods)
2. Add OPTIONS to the predicate list: `Method=GET,POST,PUT,DELETE,OPTIONS`

### Issue #5: Response Commitment Race Condition

The original error "ServerHttpResponse already committed (201 CREATED)" indicates the backend returned 201, the response body was sent, **then** the Gateway tried to add CORS headers.

**Root Cause**: Gateway filters run in a specific order. If JWT filter or CORS filter runs AFTER the response is committed, you get this error.

**Solution**: Ensure proper filter ordering - CORS must be a **pre-filter** (runs before routing).

### Issue #6: Timeout Configuration Mismatch

Gateway timeout: `response-timeout: 300s` (5 minutes)
Frontend timeout: `timeout: 300000` (5 minutes)

**Problem**: Browser may have its own timeout (typically 2 minutes). Also, relying on timeouts instead of proper streaming/progress handling.

### Issue #7: Lack of Retry Logic

Network errors during uploads should trigger retry logic with exponential backoff.

---

## 3. Proposed Fixes Review

### Fix 1: OPTIONS Bypass in JWT Filter ⚠️ INCOMPLETE AS PROPOSED

**Proposed fix (correct but incomplete):**
```java
if (request.getMethod() == HttpMethod.OPTIONS) {
    return chain.filter(exchange);
}
```

**Issues with this approach:**
1. **Placement matters**: Must be at the very TOP of the filter, before ANY other logic
2. **No CORS header guarantee**: Passing down the chain, but if no downstream component adds CORS headers, browser will reject
3. **Security boundary unclear**: Bypassing auth needs explicit documentation

**Recommended Implementation:**
```java
@Override
public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
        ServerHttpRequest request = exchange.getRequest();

        // ALWAYS allow OPTIONS requests for CORS preflight - MUST BE FIRST
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            log.debug("Allowing OPTIONS request for CORS preflight: {}", request.getURI());
            return chain.filter(exchange); // Let CORS config handle response
        }

        // Whitelist auth endpoints that don't require tokens
        if (isAuthEndpoint(request.getURI().getPath())) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return onError(exchange, "Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED);
        }

        // ... rest of JWT validation
    };
}
```

### Fix 2: Remove Hardcoded Content-Type ✅ CORRECT

**100% correct.** Remove it entirely:

```typescript
export const uploadClient: AxiosInstance = axios.create({
  baseURL: env.NEXT_PUBLIC_API_URL,
  timeout: 300000,
  // NO headers property for multipart uploads
});
```

### Fix 3: CORS Consolidation ✅ CORRECT (with refinements)

**Recommended CORS Strategy**: Gateway-Only, Global Configuration

**Remove:**
1. `@CrossOrigin` from CallIngestionController
2. `DedupeResponseHeader` filter (band-aid, not a solution)

**Keep (and refine) in application.yml:**
```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins:
              - "http://localhost:4142"
              - "${CORS_ALLOWED_ORIGINS:}"  # Production env var
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
              - OPTIONS  # CRITICAL
            allowedHeaders:
              - "*"
            exposedHeaders:
              - Content-Disposition
              - X-Total-Count
            allowCredentials: true
            maxAge: 3600  # Cache preflight for 1 hour
```

---

## 4. Security Considerations for OPTIONS Bypass

### ✅ Safe to Bypass Authentication for OPTIONS

**Why it's safe:**
- OPTIONS is a **safe method** (RFC 7231) - cannot modify server state
- Part of CORS specification - required for browser security
- Only returns allowed methods/headers, not actual data
- Blocking it breaks web application functionality

### Recommended Security Best Practices

1. **Rate Limiting**: Apply rate limits to OPTIONS to prevent DoS
```yaml
- name: RequestRateLimiter
  args:
    redis-rate-limiter.replenishRate: 100
    redis-rate-limiter.burstCapacity: 200
```

2. **Logging**: Log OPTIONS requests for monitoring
```java
if (HttpMethod.OPTIONS.equals(request.getMethod())) {
    log.debug("CORS preflight request: {} from origin: {}",
              request.getURI(),
              request.getHeaders().getOrigin());
    return chain.filter(exchange);
}
```

3. **Origin Validation**: Let CORS config handle origin validation (don't allow `*` in production with credentials)

4. **No Sensitive Info**: Don't expose internal details in `exposedHeaders`

### ⚠️ What NOT to Do

```java
// WRONG - Don't add upload paths to auth whitelist
private boolean isAuthEndpoint(String path) {
    return path.startsWith("/api/calls/upload");  // ❌ NO!
}
```
This would bypass auth for actual POST requests, not just OPTIONS.

---

## 5. Edge Cases and Potential Issues

### Edge Case #1: Browser Caching of Failed Preflight
If browsers cached a failed preflight response, they won't retry.
- Users may need to clear browser cache or hard refresh
- **Prevention**: Set appropriate `maxAge` in CORS config (3600 = 1 hour)

### Edge Case #2: Multiple File Upload
Ensure backend handles array of files:
```java
@PostMapping("/upload")
public ResponseEntity<?> uploadCalls(
    @RequestParam("file") MultipartFile[] files) {  // Array
    // ...
}
```

### Edge Case #3: CORS with Credentials
`allowCredentials: true` means:
- `allowedOrigins` **cannot** be `"*"` - must be explicit
- Cookies/auth headers are included in requests
- More strict browser security checks

### Edge Case #4: Proxy/Load Balancer Headers
If proxy in front of Gateway:
```yaml
spring:
  cloud:
    gateway:
      forwarded:
        enabled: true  # Trust X-Forwarded-* headers
```

### Edge Case #5: Large File Memory Issues
Multipart uploads load into memory by default:
```yaml
spring:
  codec:
    max-in-memory-size: 10MB  # Spill to disk after this
```

---

## 6. Recommended Testing Strategy

### Layer 1: Unit Tests

**JWT Filter Test:**
```java
@Test
void shouldAllowOptionsRequestsWithoutAuth() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.options("/api/calls/upload").build()
    );

    GatewayFilter filter = jwtAuthenticationFilter.apply(new Config());
    filter.filter(exchange, chain).block();

    verify(chain).filter(exchange);  // Should pass through
    assertThat(exchange.getResponse().getStatusCode()).isNull();
}

@Test
void shouldRejectPostRequestsWithoutAuth() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.post("/api/calls/upload").build()
    );

    GatewayFilter filter = jwtAuthenticationFilter.apply(new Config());

    StepVerifier.create(filter.filter(exchange, chain))
        .expectComplete()
        .verify();

    assertThat(exchange.getResponse().getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
}
```

### Layer 2: Integration Tests

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class FileUploadCorsTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldAllowCorsPreflightForUploadEndpoint() {
        webTestClient.options()
            .uri("/api/calls/upload")
            .header("Origin", "http://localhost:4142")
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "Authorization,Content-Type")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:4142")
            .expectHeader().valueEquals("Access-Control-Allow-Methods", "POST")
            .expectHeader().exists("Access-Control-Allow-Headers");
    }

    @Test
    void shouldAcceptMultipartUploadWithAuth() {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", new ClassPathResource("test-call.mp3"));

        webTestClient.post()
            .uri("/api/calls/upload")
            .header("Authorization", "Bearer " + validToken)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
            .exchange()
            .expectStatus().isCreated();
    }
}
```

### Layer 3: End-to-End Tests (Playwright/Cypress)

```typescript
test('should upload call recording successfully', async ({ page }) => {
  await page.goto('http://localhost:4142/login');
  await page.fill('[name="username"]', 'testuser');
  await page.fill('[name="password"]', 'testpass');
  await page.click('button[type="submit"]');

  await page.goto('http://localhost:4142/calls/upload');

  const fileInput = page.locator('input[type="file"]');
  await fileInput.setInputFiles('test-fixtures/sample-call.mp3');

  const uploadPromise = page.waitForResponse(
    response => response.url().includes('/api/calls/upload') && response.status() === 201
  );

  await page.click('button:has-text("Upload")');

  const response = await uploadPromise;
  expect(response.status()).toBe(201);

  await expect(page.locator('.success-message')).toBeVisible();
});
```

### Layer 4: Manual Testing Checklist

- [ ] OPTIONS request returns 200 with correct CORS headers
- [ ] OPTIONS request does NOT require Authorization header
- [ ] POST request with valid JWT and file returns 201
- [ ] POST request without JWT returns 401
- [ ] POST request with invalid JWT returns 401
- [ ] POST request with valid JWT but no file returns 400
- [ ] Large file upload (50MB+) completes successfully
- [ ] Upload with invalid content-type fails gracefully
- [ ] Concurrent uploads from same user work correctly
- [ ] Network interruption during upload shows proper error
- [ ] Browser DevTools shows single CORS headers (not duplicated)
- [ ] Kafka event published for successful upload
- [ ] File stored in MinIO with correct metadata

### Layer 5: Performance Testing (k6)

```javascript
import http from 'k6/http';
import { check } from 'k6';

export let options = {
  stages: [
    { duration: '2m', target: 10 },
    { duration: '5m', target: 10 },
    { duration: '2m', target: 0 },
  ],
};

export default function () {
  const file = open('./test-file.mp3', 'b');
  const data = { file: http.file(file, 'test-file.mp3') };

  let res = http.post('http://localhost:8080/api/calls/upload', data, {
    headers: { 'Authorization': 'Bearer ' + __ENV.TEST_TOKEN },
  });

  check(res, {
    'status is 201': (r) => r.status === 201,
    'upload time < 5s': (r) => r.timings.duration < 5000,
  });
}
```

---

## 7. Priority Matrix

| Priority | Fix | File | Impact |
|----------|-----|------|--------|
| **P0** | OPTIONS bypass in JWT filter | `JwtAuthenticationFilter.java` | Unblocks all uploads |
| **P0** | Remove hardcoded Content-Type | `client.ts` | Fixes multipart parsing |
| **P0** | Verify/fix route predicates | `application.yml` | May be causing 405 |
| **P1** | Remove @CrossOrigin | `CallIngestionController.java` | Eliminates duplicates |
| **P1** | Remove DedupeResponseHeader | `application.yml` | Cleanup |
| **P2** | Add upload progress tracking | `client.ts` | UX improvement |
| **P2** | Add retry logic | `calls.ts` | Resilience |
| **P3** | Add integration tests | New test files | Quality assurance |

---

## 8. Monitoring & Observability Recommendations

### Add Upload Metrics

```java
@Component
public class UploadMetricsFilter implements GlobalFilter {

    private final MeterRegistry meterRegistry;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (path.startsWith("/api/calls/upload")) {
            Timer.Sample sample = Timer.start(meterRegistry);

            return chain.filter(exchange).doFinally(signalType -> {
                sample.stop(Timer.builder("gateway.upload.duration")
                    .tag("path", path)
                    .tag("method", exchange.getRequest().getMethod().name())
                    .tag("status", String.valueOf(exchange.getResponse().getStatusCode()))
                    .register(meterRegistry));
            });
        }

        return chain.filter(exchange);
    }
}
```

### Grafana Dashboard Queries

```promql
# Upload success rate
rate(gateway_upload_duration_count{status="201"}[5m])
/
rate(gateway_upload_duration_count[5m])

# Upload latency p95
histogram_quantile(0.95, gateway_upload_duration_seconds_bucket)

# CORS preflight rate
rate(gateway_upload_duration_count{method="OPTIONS"}[5m])
```

---

## 9. Long-term Architecture Recommendations

### Medium-term Enhancements
- Add retry logic for failed uploads
- Implement chunked upload for large files (>100MB)
- Add rate limiting to prevent abuse
- Consider direct-to-MinIO presigned URLs for very large files

### Long-term Architecture
- Evaluate moving to direct S3/MinIO uploads (bypass Gateway)
- Implement resumable uploads (tus protocol)
- Add file validation (virus scanning, format checking)
- Implement upload quotas per user

---

## 10. Conclusion

**The UI expert's analysis was excellent.** All three root causes identified are correct:

1. ✅ JWT filter blocking OPTIONS requests (PRIMARY)
2. ✅ Hardcoded Content-Type removing boundary (SECONDARY)
3. ✅ CORS configuration sprawl (TERTIARY)

**Additional issues discovered:**
4. ⚠️ Possible Gateway route predicate excluding OPTIONS
5. ⚠️ Response commitment race condition (filter ordering)
6. ⚠️ Lack of upload progress feedback
7. ⚠️ Missing retry logic for transient failures

**The proposed fixes are sound** but need refinements:
- Ensuring OPTIONS check is **first** in JWT filter
- Verifying Gateway route predicates
- Complete CORS consolidation (not just deduplication)

**Security-wise**, bypassing auth for OPTIONS is standard practice and safe when done correctly.

---

## References

- [RFC 7231 - Safe Methods](https://tools.ietf.org/html/rfc7231#section-4.2.1)
- [MDN: CORS Preflight Requests](https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS#preflighted_requests)
- [Spring Cloud Gateway CORS](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/#cors-configuration)
- [Spring Cloud Gateway Filter Ordering](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/#gateway-combined-global-filter-and-gatewayfilter-ordering)
