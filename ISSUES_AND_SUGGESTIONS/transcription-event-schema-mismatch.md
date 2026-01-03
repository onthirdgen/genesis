# Transcription Event Schema Mismatch

**Date**: 2026-01-02
**Status**: RESOLVED
**Affected Components**: analytics-service, transcription-service
**Resolution**: Updated analytics-service to match transcription-service event schema

---

## Problem Description

After uploading audio files, transcriptions were not appearing in the UI. The upload succeeded (201), transcription-service processed the audio and published `CallTranscribed` events, but analytics-service failed to consume them.

### Error Message

```
com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException:
Unrecognized field "transcription" (class com.callaudit.analytics.event.CallTranscribedEvent$Payload),
not marked as ignorable (7 known properties: "segments", "transcribedAt", "language", "callId",
"confidence", "durationSeconds", "transcriptionText"])
```

---

## Root Cause Analysis

### Schema Mismatch Between Services

**Transcription-service produces** (nested structure):
```json
{
  "eventType": "CallTranscribed",
  "payload": {
    "callId": "uuid",
    "transcription": {
      "fullText": "...",
      "segments": [
        {
          "speaker": "agent",
          "startTime": 0.0,
          "endTime": 6.8,
          "text": "..."
        }
      ],
      "language": "en",
      "confidence": 0.951
    }
  }
}
```

**Analytics-service expected** (flat structure):
```json
{
  "eventType": "CallTranscribed",
  "payload": {
    "callId": "uuid",
    "transcriptionText": "...",
    "segments": [...],
    "language": "en",
    "confidence": 0.951,
    "durationSeconds": 22,
    "transcribedAt": "..."
  }
}
```

### Key Differences

| Aspect | Transcription-service | Analytics-service (old) |
|--------|----------------------|------------------------|
| Transcription data | Nested under `transcription` | Flat at payload root |
| Full text field | `transcription.fullText` | `transcriptionText` |
| Segments location | `transcription.segments` | `segments` |
| Segment confidence | Not provided | Expected per-segment |

---

## Resolution

### Changes Made

**1. Updated `CallTranscribedEvent.java`**

File: `analytics-service/src/main/java/com/callaudit/analytics/event/CallTranscribedEvent.java`

```java
// OLD: Flat structure
public static class Payload {
    private String callId;
    private String transcriptionText;  // flat
    private List<Segment> segments;
    private String language;
    private Double confidence;
    private Integer durationSeconds;
    private String transcribedAt;
}

// NEW: Nested structure matching transcription-service
public static class Payload {
    private String callId;
    private Transcription transcription;  // nested object
}

public static class Transcription {
    private String fullText;
    private List<Segment> segments;
    private String language;
    private Double confidence;
}

public static class Segment {
    private String speaker;
    private String text;
    private Double startTime;
    private Double endTime;
}
```

**2. Updated `TranscriptionEventHandler.java`**

File: `analytics-service/src/main/java/com/callaudit/analytics/domain/transcription/TranscriptionEventHandler.java`

Changed field access from flat to nested:
```java
// OLD
.fullText(payload.getTranscriptionText())
.language(payload.getLanguage())
for (Payload.Segment segment : payload.getSegments())

// NEW
CallTranscribedEvent.Transcription transcriptionData = payload.getTranscription();
.fullText(transcriptionData.getFullText())
.language(transcriptionData.getLanguage())
for (CallTranscribedEvent.Segment segment : transcriptionData.getSegments())
```

**3. Updated Test**

File: `analytics-service/src/test/java/com/callaudit/analytics/listener/AnalyticsEventListenerTest.java`

Updated `createCallTranscribedEvent()` to use new nested schema.

**4. Created Missing Database Tables**

The `transcriptions` and `segments` tables were missing from the database. Created with:
```sql
CREATE TYPE speaker_type AS ENUM ('agent', 'customer', 'unknown');

CREATE TABLE transcriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    call_id UUID NOT NULL UNIQUE,
    full_text TEXT NOT NULL,
    language VARCHAR(10) NOT NULL,
    confidence DECIMAL(5,4),
    word_count INTEGER,
    processing_time_ms INTEGER,
    model_version VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE segments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transcription_id UUID NOT NULL REFERENCES transcriptions(id) ON DELETE CASCADE,
    speaker speaker_type NOT NULL,
    start_time DECIMAL(10,3) NOT NULL,
    end_time DECIMAL(10,3) NOT NULL,
    text TEXT NOT NULL,
    confidence DECIMAL(5,4),
    word_count INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

## Verification

- Maven build passes: `./mvnw compile` succeeds
- Docker image builds successfully
- Database tables created
- Analytics-service starts without errors

---

## Note on Existing Messages

Previously failed messages have already been consumed (offset committed) but not processed. They will not be automatically reprocessed. Options to recover:

1. **Reset consumer group offset** (requires service restart):
   ```bash
   kafka-consumer-groups --bootstrap-server localhost:9092 \
     --group analytics-service-transcription \
     --topic calls.transcribed \
     --reset-offsets --to-earliest --execute
   ```

2. **Re-upload affected files** - New uploads will trigger fresh events

3. **Replay events** using monitor-service to publish existing events to a reprocessing topic

---

## Recommendations

### Short-term
- Add contract tests between transcription-service and analytics-service
- Document event schemas in a shared schema registry or OpenAPI spec

### Long-term
- Consider using Avro/Protobuf with schema registry for event serialization
- Implement dead-letter queue for failed event processing
- Add event versioning to handle schema evolution

---

## Related Issues

- [api-response-wrapper-mismatch.md](./api-response-wrapper-mismatch.md) - Similar UI/backend contract issue
