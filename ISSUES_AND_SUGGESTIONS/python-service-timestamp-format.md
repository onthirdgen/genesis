# Python Service Timestamp Format Mismatch

## Problem

The `voc-service` and `audit-service` were failing to deserialize Kafka events published by Python services due to missing timezone information in timestamps.

## Error Messages

**voc-service:**
```
com.fasterxml.jackson.databind.exc.InvalidFormatException: Cannot deserialize value of type `java.time.Instant` from String "2026-01-03T06:05:37.424154": Failed to deserialize java.time.Instant: (java.time.format.DateTimeParseException) Text '2026-01-03T06:05:37.424154' could not be parsed at index 26
```

**audit-service:**
```
com.fasterxml.jackson.databind.exc.InvalidFormatException: Cannot deserialize value of type `java.time.OffsetDateTime` from String "2026-01-03T06:05:24.871302": Failed to deserialize java.time.OffsetDateTime: (java.time.format.DateTimeParseException) Text '2026-01-03T06:05:24.871302' could not be parsed at index 26
```

## Root Cause

Python services were using `datetime.utcnow().isoformat()` which produces timestamps without timezone suffix:
- **Published:** `2026-01-03T06:05:37.424154`
- **Expected:** `2026-01-03T06:05:37.424154Z` (with `Z` suffix for UTC)

Java's `Instant` and `OffsetDateTime` require ISO-8601 timestamps with timezone information.

## Affected Services

| Service | File | Issue |
|---------|------|-------|
| transcription-service | `models/events.py` | `datetime.utcnow().isoformat()` missing `Z` suffix |
| sentiment-service | `models/events.py` | `datetime.utcnow` produces naive datetime |

## Solution

### transcription-service

Added helper function and updated timestamp fields:

```python
from datetime import datetime, timezone

def utc_timestamp() -> str:
    """Generate ISO-8601 timestamp with UTC timezone suffix."""
    return datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z')

class CallTranscribedEvent(BaseModel):
    # Before
    timestamp: str = Field(default_factory=lambda: datetime.utcnow().isoformat())

    # After
    timestamp: str = Field(default_factory=utc_timestamp)
```

### sentiment-service

Added helper function for timezone-aware datetime:

```python
from datetime import datetime, timezone

def utc_now() -> datetime:
    """Generate timezone-aware UTC datetime."""
    return datetime.now(timezone.utc)

class SentimentAnalyzedEvent(BaseModel):
    # Before
    timestamp: datetime = Field(default_factory=datetime.utcnow)

    # After
    timestamp: datetime = Field(default_factory=utc_now)
```

## Files Modified

- `transcription-service/models/events.py`
  - Added `utc_timestamp()` helper function
  - Updated `CallReceivedEvent.timestamp`
  - Updated `CallTranscribedEvent.timestamp`

- `sentiment-service/models/events.py`
  - Added `utc_now()` helper function
  - Updated `CallTranscribedEvent.timestamp`
  - Updated `SentimentAnalyzedEvent.timestamp`

## Timestamp Format Reference

| Format | Example | Valid for Java |
|--------|---------|----------------|
| No timezone | `2026-01-03T06:05:37.424154` | No |
| UTC with Z | `2026-01-03T06:05:37.424154Z` | Yes |
| UTC with offset | `2026-01-03T06:05:37.424154+00:00` | Yes |

## Prevention

When publishing events from Python to be consumed by Java/Spring Boot services:

1. **Always use timezone-aware datetimes:** `datetime.now(timezone.utc)` instead of `datetime.utcnow()`
2. **For string timestamps:** Ensure the `Z` suffix is included for UTC times
3. **Pydantic serialization:** Timezone-aware datetimes serialize correctly with offset

## Status

**Fixed** - Both Python services now publish timestamps with proper UTC timezone information.
