# Transcription 404 Investigation

**Date**: 2026-01-02
**Symptom**: After uploading audio file, `GET /api/transcriptions/{callId}` returns 404
**Status**: Root cause identified - critical bug in analytics-service

---

## Summary

After uploading an audio file:
1. `GET /api/calls/{callId}/audio` - returned **200 OK**
2. `GET /api/transcriptions/{callId}` - returned **404 Not Found**

Investigation revealed two findings:
1. **UI behavior is expected** - the immediate transcription fetch is by design
2. **Critical bug in analytics-service** - transcriptions are NOT being saved to database

---

## Finding 1: UI Behavior (Expected)

### Why the UI makes the transcription call immediately

The UI uses React Query's automatic data fetching pattern:

**File**: `call-auditing-ui/src/lib/hooks/use-calls.ts` (lines 150-161)
```typescript
export function useTranscription(callId: string | null) {
  return useQuery({
    queryKey: ['calls', callId, 'transcription'],
    queryFn: async () => {
      if (!callId) throw new Error('Call ID is required');
      return getTranscription(callId);
    },
    enabled: !!callId,  // Runs immediately when callId exists
    staleTime: 10 * 60 * 1000,
  });
}
```

### Flow after upload

1. Upload succeeds → `onSuccess` callback sets `callId` in state
2. Router navigates to `/dashboard/calls?callId=...`
3. Page re-renders with the new `callId`
4. `useTranscription(callId)` hook activates (because `enabled: !!callId`)
5. React Query fetches `GET /api/transcriptions/{callId}`
6. 404 response - transcription doesn't exist yet

### This is intentional design

The UI implements an **optimistic fetch with graceful fallback**:

```typescript
// Fallback chain in page.tsx
segments: transcriptionData?.segments || wsSegments || []
```

- First tries REST API data (works for revisiting already-processed calls)
- Falls back to WebSocket segments (for real-time updates on new uploads)
- Falls back to empty array (shows "processing" message)

**Conclusion**: The 404 on initial upload is expected. The UI handles it gracefully.

---

## Finding 2: Critical Bug in Analytics Service

### The real problem

Transcriptions are **NOT being saved to the database** due to a PostgreSQL enum type mismatch.

### Database verification

```sql
SELECT * FROM analytics.transcriptions
WHERE call_id = '48c5b630-4d1f-433e-a563-50fd3d147bfa';
-- Result: (0 rows)
```

### Error in analytics-service logs

```
ERROR: column "speaker" is of type speaker_type but expression is of type character varying
Hint: You will need to rewrite or cast the expression.
```

### Root cause

**File**: `analytics-service/src/main/java/com/callaudit/analytics/domain/transcription/TranscriptionSegment.java` (lines 33-35)

```java
@Enumerated(EnumType.STRING)  // ← Tells Hibernate to use VARCHAR
@Column(name = "speaker", nullable = false, columnDefinition = "speaker_type")
private SpeakerType speaker;
```

**Problem**:
- `@Enumerated(EnumType.STRING)` tells Hibernate to treat the enum as VARCHAR
- `columnDefinition = "speaker_type"` is just a DDL hint, doesn't affect runtime
- Hibernate sends VARCHAR to PostgreSQL
- PostgreSQL column expects `speaker_type` enum
- Type mismatch → insert fails

### Impact

This is a **system-wide critical bug**:
- Every audio upload appears to succeed (200 OK)
- Transcription-service completes successfully
- Analytics-service **silently fails** to save transcription
- After 10 retries, the Kafka message is abandoned
- Users always see 404 when querying transcriptions
- **No transcriptions are being saved to the database**

### Kafka retry behavior observed

```
Retrying processing of call 48c5b630... (attempt 1/10)
Retrying processing of call 48c5b630... (attempt 2/10)
...
Retrying processing of call 48c5b630... (attempt 10/10)
Failed to process transcription after 10 attempts
```

---

## Solutions

### Option 1: Remove @Enumerated annotation (Quick fix)

Remove the annotation and let Hibernate use native enum handling:

```java
// @Enumerated(EnumType.STRING)  ← Remove this line
@Column(name = "speaker", nullable = false, columnDefinition = "speaker_type")
private SpeakerType speaker;
```

**Risk**: May not work correctly with all Hibernate/PostgreSQL driver versions.

### Option 2: Use Hibernate Types library (Recommended)

Add dependency to `analytics-service/pom.xml`:

```xml
<dependency>
    <groupId>com.vladmihalcea</groupId>
    <artifactId>hibernate-types-60</artifactId>
    <version>2.21.1</version>
</dependency>
```

Update entity:

```java
@Type(PostgreSQLEnumType.class)
@Column(name = "speaker", nullable = false, columnDefinition = "speaker_type")
private SpeakerType speaker;
```

### Option 3: Change database column to VARCHAR

Modify `schema.sql` to use VARCHAR instead of PostgreSQL enum:

```sql
-- Change from:
speaker speaker_type NOT NULL,

-- To:
speaker VARCHAR(50) NOT NULL,
```

**Trade-off**: Loses database-level type safety but simpler Hibernate mapping.

### Option 4: Custom AttributeConverter

Create a JPA AttributeConverter to handle the enum-to-PostgreSQL-enum conversion explicitly.

---

## Recommended Action

1. **Immediate**: Apply Option 1 or Option 3 to unblock transcription saving
2. **Follow-up**: Consider Option 2 for proper PostgreSQL enum support across all services
3. **Testing**: After fix, re-upload audio file and verify transcription appears

---

## Related Files

- `analytics-service/src/main/java/com/callaudit/analytics/domain/transcription/TranscriptionSegment.java`
- `analytics-service/src/main/java/com/callaudit/analytics/domain/transcription/TranscriptionEventHandler.java`
- `call-auditing-ui/src/lib/hooks/use-calls.ts`
- `call-auditing-ui/src/app/dashboard/calls/page.tsx`
- `schema.sql` (analytics.segments table definition)
