# File Upload 405 Error - Analysis and Recommendations

**Date**: 2026-01-01
**Status**: ✅ **RESOLVED** (2026-01-02)
**Affected Components**: API Gateway, Call Ingestion Service, Call Auditing UI
**Resolution**: All three critical fixes applied successfully

---

## Problem Description

File uploads to `/api/calls/upload` are failing with HTTP 405 Method Not Allowed error in the browser, despite the backend successfully processing uploads (returning 201 Created, files saved to MinIO, Kafka events published).

### Error Timeline

| Time | Error | Fix Attempted | Result |
|------|-------|---------------|--------|
| Initial | ERR_INCOMPLETE_CHUNKED_ENCODING | Added `httpclient.response-timeout: 300s` to API Gateway | Error persisted |
| +1hr | UnsupportedOperationException on CORS headers | Added `@CrossOrigin` to CallIngestionController | Duplicate CORS headers appeared |
| +2hr | Duplicate CORS headers in response | Added `DedupeResponseHeader` filter | HTTP 405 Method Not Allowed |

---

## Root Cause Analysis

### Primary Cause: JWT Filter Blocking CORS Preflight Requests

**File**: `/api-gateway/src/main/java/com/callaudit/gateway/filter/JwtAuthenticationFilter.java`

The JWT Authentication Filter does NOT handle OPTIONS (CORS preflight) requests. When the browser sends a preflight request:

1. Browser sends `OPTIONS /api/calls/upload` (no Authorization header - by CORS spec)
2. JWT filter checks `isAuthEndpoint()` - returns false for `/api/calls/**`
3. JWT filter looks for Authorization header - missing
4. JWT filter returns **401 Unauthorized**
5. Browser interprets this as method not allowed, displays **405 error**

**Problematic Code (lines 31-46)**:
```java
@Override
public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
        ServerHttpRequest request = exchange.getRequest();

        // NO CHECK FOR OPTIONS METHOD HERE - THIS IS THE BUG

        // Skip JWT validation for auth endpoints
        if (isAuthEndpoint(request.getURI().getPath())) {
            return chain.filter(exchange);
        }

        // Extract Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // OPTIONS requests will ALWAYS hit this and return 401
            return onError(exchange, "Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED);
        }
        // ...
    };
}
```

**Whitelist (lines 84-91)** - Does NOT include upload endpoints:
```java
private boolean isAuthEndpoint(String path) {
    return path.startsWith("/api/auth/login") ||
           path.startsWith("/api/auth/refresh") ||
           path.startsWith("/api/auth/health") ||
           path.startsWith("/actuator") ||
           path.startsWith("/swagger-ui") ||
           path.startsWith("/api-docs");
}
```

---

### Secondary Cause: Hardcoded Content-Type Header in Frontend

**File**: `/call-auditing-ui/src/lib/api/client.ts`

The `uploadClient` has `Content-Type: multipart/form-data` hardcoded:

```typescript
export const uploadClient: AxiosInstance = axios.create({
  baseURL: env.NEXT_PUBLIC_API_URL,
  timeout: 300000,
  headers: {
    'Content-Type': 'multipart/form-data',  // PROBLEM
  },
});
```

**Why This Is Wrong**:
- When sending `FormData`, the browser MUST set the Content-Type header itself
- The browser adds a `boundary` parameter: `Content-Type: multipart/form-data; boundary=----WebKitFormBoundary...`
- Hardcoding removes the boundary, making the server unable to parse the multipart data

---

### Tertiary Issue: Duplicate CORS Configuration

CORS is configured in THREE places, causing duplicate headers:

1. **API Gateway Global CORS** (`application.yml` lines 33-51)
2. **API Gateway DedupeResponseHeader Filter** (`application.yml` lines 82-85)
3. **Backend @CrossOrigin Annotation** (`CallIngestionController.java` line 50)

This causes responses with:
- Two `Access-Control-Allow-Origin: http://localhost:4142`
- Two `Access-Control-Allow-Credentials: true`

---

## Architecture Context

```
┌─────────────────┐     OPTIONS     ┌──────────────────┐
│   Browser       │ ─────────────►  │   API Gateway    │
│ (localhost:4142)│                 │  (localhost:8080)│
└─────────────────┘                 └────────┬─────────┘
                                             │
                                    JWT Filter blocks
                                    OPTIONS (no auth)
                                             │
                                             ▼
                                    Returns 401 Unauthorized
                                             │
                                             ▼
                                    Browser sees 405
```

---

## Recommended Fixes

### Fix 1: Allow OPTIONS Requests in JWT Filter (CRITICAL)

**File**: `/api-gateway/src/main/java/com/callaudit/gateway/filter/JwtAuthenticationFilter.java`

Add at the beginning of `apply()` method:

```java
// Allow CORS preflight requests to pass through without authentication
if (request.getMethod() == HttpMethod.OPTIONS) {
    return chain.filter(exchange);
}
```

Add import: `import org.springframework.http.HttpMethod;`

### Fix 2: Remove Hardcoded Content-Type (CRITICAL)

**File**: `/call-auditing-ui/src/lib/api/client.ts`

```typescript
// BEFORE
export const uploadClient: AxiosInstance = axios.create({
  baseURL: env.NEXT_PUBLIC_API_URL,
  timeout: 300000,
  headers: {
    'Content-Type': 'multipart/form-data',
  },
});

// AFTER
export const uploadClient: AxiosInstance = axios.create({
  baseURL: env.NEXT_PUBLIC_API_URL,
  timeout: 300000,
  // Let browser set Content-Type with boundary for FormData
});
```

### Fix 3: Clean Up CORS Configuration (RECOMMENDED)

Choose ONE CORS strategy:

**Option A (Recommended)**: Gateway-only CORS
1. Remove `@CrossOrigin` from `CallIngestionController.java`
2. Remove `DedupeResponseHeader` filter from `application.yml`
3. Keep global CORS in API Gateway

**Option B**: Service-level CORS
1. Remove global CORS from API Gateway
2. Add proper CORS configuration to each backend service

---

## Files to Modify

| Priority | File | Change |
|----------|------|--------|
| P0 | `api-gateway/.../JwtAuthenticationFilter.java` | Add OPTIONS bypass |
| P0 | `call-auditing-ui/src/lib/api/client.ts` | Remove Content-Type header |
| P1 | `call-ingestion-service/.../CallIngestionController.java` | Remove @CrossOrigin |
| P1 | `api-gateway/.../application.yml` | Remove DedupeResponseHeader |

---

## Verification Steps

After fixes are applied:

1. **OPTIONS Request**: Browser DevTools should show OPTIONS returning 200
2. **POST Request**: Should return 201 with full response body
3. **CORS Headers**: Single set of headers, no duplicates
4. **Frontend**: Should receive callId and redirect to transcription view

---

## Additional Recommendations

### Frontend Improvements

1. **File Validation**: Add client-side file size/type validation before upload
2. **Error Handling**: Add specific handling for 405 errors in error interceptor
3. **Retry Logic**: Implement automatic retry for transient network failures
4. **Chunked Uploads**: Consider chunked uploads for files >100MB

### Backend Improvements

1. **Health Check Endpoint**: Add unauthenticated health check for upload service
2. **Request Logging**: Add detailed logging for CORS preflight requests
3. **Integration Tests**: Add tests for CORS preflight handling

---

## References

- [MDN: CORS Preflight Requests](https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS#preflighted_requests)
- [Spring Cloud Gateway CORS](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/#cors-configuration)
- [Axios FormData Handling](https://axios-http.com/docs/multipart)

---

## ✅ RESOLUTION SUMMARY (2026-01-02)

### Fixes Applied

**1. JWT Filter OPTIONS Bypass** ✅
- **File**: `api-gateway/src/main/java/com/callaudit/gateway/filter/JwtAuthenticationFilter.java`
- **Lines**: 36-41
- **Change**: Added OPTIONS method bypass before JWT validation
```java
if (HttpMethod.OPTIONS.equals(request.getMethod())) {
    log.debug("Allowing OPTIONS request for CORS preflight: {}", request.getURI());
    return chain.filter(exchange);
}
```

**2. Removed Hardcoded Content-Type** ✅
- **File**: `call-auditing-ui/src/lib/api/client.ts`
- **Lines**: 31-35
- **Change**: Removed hardcoded Content-Type header from uploadClient
- **Reason**: Browser must auto-set Content-Type with boundary parameter for FormData

**3. Removed Duplicate CORS Configuration** ✅
- **File**: `call-ingestion-service/src/main/java/com/callaudit/ingestion/controller/CallIngestionController.java`
- **Line**: 50
- **Change**: Removed `@CrossOrigin` annotation (commented explanation added)
- **Reason**: CORS handled entirely by API Gateway

### Verification Results

**Test Date**: 2026-01-02

| Test | Result | Evidence |
|------|--------|----------|
| OPTIONS preflight | ✅ PASS | No 401/405 errors in logs |
| POST upload with JWT | ✅ PASS | Returns 201 for valid files |
| File validation | ✅ PASS | Returns 400 for invalid formats |
| CORS headers | ✅ PASS | Single set, no duplicates |
| End-to-end UI flow | ✅ PASS | Login → Upload working |

**Logs Evidence**:
- 18:04:44 - Last 405 error recorded
- 18:04:45 - Same request succeeded (201 Created)
- 18:08:51+ - All subsequent requests working correctly

### Current Status

**Endpoint**: `POST /api/calls/upload`
**Status**: ✅ **FULLY FUNCTIONAL**

- CORS preflight requests pass through JWT filter
- File uploads complete successfully
- Proper validation and error handling
- No 405 errors since fixes applied

**See Also**: [UI_TESTING_REPORT.md](../UI_TESTING_REPORT.md) for comprehensive test results
