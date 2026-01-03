# API Response Wrapper Mismatch - UI Cannot Read 'callId'

**Date**: 2026-01-02
**Status**: RESOLVED
**Affected Components**: Call Auditing UI
**Resolution**: Updated UI API client to handle direct backend responses

---

## Problem Description

After a successful file upload (HTTP 201), the UI displayed error:

```
Cannot read properties of undefined (reading 'callId')
```

The upload succeeded server-side but the UI couldn't process the response.

---

## Root Cause Analysis

### Mismatch Between Expected and Actual Response Format

**UI Expected** (`ApiResponse<T>` wrapper):
```json
{
  "data": {
    "callId": "...",
    "status": "...",
    "message": "..."
  },
  "timestamp": "..."
}
```

**Backend Actually Returns** (direct DTO):
```json
{
  "callId": "...",
  "status": "...",
  "audioFileUrl": "...",
  "uploadedAt": "...",
  "message": "..."
}
```

### Problematic Code

**File**: `call-auditing-ui/src/lib/api/calls.ts`

```typescript
const response = await uploadClient.post<ApiResponse<CallUploadResponse>>(
  '/api/calls/upload',
  formData,
  { ... }
);

return response.data.data;  // <-- ERROR: response.data has no 'data' property
```

When the UI did `response.data.data`:
- `response.data` = the backend response `{ callId, status, ... }`
- `response.data.data` = `undefined`
- `undefined.callId` = throws TypeError

### Backend Response Structure

**File**: `call-ingestion-service/src/main/java/com/callaudit/ingestion/controller/CallIngestionController.java`

The backend returns `CallUploadResponse` directly without any wrapper:

```java
CallUploadResponse response = CallUploadResponse.builder()
    .callId(call.getId())
    .status(call.getStatus().toString())
    .audioFileUrl(call.getAudioFileUrl())
    .uploadedAt(call.getCreatedAt())
    .message("Call audio uploaded successfully and is being processed")
    .build();

return ResponseEntity.status(HttpStatus.CREATED).body(response);
```

---

## Resolution

### Changes Made

**File**: `call-auditing-ui/src/lib/api/calls.ts`

Updated all API functions to handle direct responses:

| Function | Before | After |
|----------|--------|-------|
| `uploadCall` | `response.data.data` | `response.data` |
| `getCallStatus` | `response.data.data` | `response.data` |
| `getTranscription` | `response.data.data` | `response.data` |
| `updateCallMetadata` | `response.data.data` | `response.data` |
| `getCall` | `response.data.data` | `response.data` |

Also removed unused `ApiResponse` import.

### Example Fix

```typescript
// BEFORE (incorrect)
const response = await uploadClient.post<ApiResponse<CallUploadResponse>>(
  '/api/calls/upload',
  formData,
  { ... }
);
return response.data.data;

// AFTER (correct)
const response = await uploadClient.post<CallUploadResponse>(
  '/api/calls/upload',
  formData,
  { ... }
);
return response.data;
```

---

## Verification

- TypeScript type check passes: `npm run type-check`
- File upload now returns callId correctly to the UI

---

## Architectural Note

The UI has an `ApiResponse<T>` type defined in `src/types/index.ts`:

```typescript
export interface ApiResponse<T> {
  data: T;
  message?: string;
  timestamp: string;
}
```

This wrapper pattern is NOT currently used by the backend services. If standardizing API responses is desired in the future, the backend would need to wrap all responses in this format. For now, the UI has been updated to match the actual backend behavior.

---

## Related Issues

- [file-upload-405-error-analysis.md](./file-upload-405-error-analysis.md) - Previous upload issues (resolved)
