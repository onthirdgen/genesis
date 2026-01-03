# PostgreSQL Enum Type Fix for TranscriptionSegment

## Problem Summary

After uploading an audio file, the UI displayed two errors:
1. **503 (Service Unavailable)** during `uploadCall`
2. **404 (Not Found)** during `getTranscription`

The root cause was a type mismatch between the JPA entity and PostgreSQL database for the `speaker` column in the `analytics.segments` table.

## Original Error

```
ERROR: column "speaker" is of type speaker_type but expression is of type smallint
```

After the first fix attempt:
```
ERROR: column "speaker" is of type speaker_type but expression is of type character varying
```

## Technical Background

### PostgreSQL Custom Enum Types

PostgreSQL supports custom enum types, defined like:
```sql
CREATE TYPE speaker_type AS ENUM ('agent', 'customer', 'unknown');
```

The original schema used this custom type:
```sql
CREATE TABLE analytics.segments (
    ...
    speaker speaker_type NOT NULL,  -- Custom PostgreSQL enum
    ...
);
```

### JPA Enum Mapping Options

JPA provides two ways to map Java enums via `@Enumerated`:

1. **`EnumType.ORDINAL`** (default): Stores the enum's ordinal position (0, 1, 2...) as an integer
2. **`EnumType.STRING`**: Stores the enum constant name as a string

### The Mismatch

The original entity had no `@Enumerated` annotation:
```java
@Column(name = "speaker", nullable = false)
private SpeakerType speaker;  // Defaults to ORDINAL (integer)
```

This caused Hibernate to send an integer, but PostgreSQL expected the custom `speaker_type` enum.

---

## Fix Attempts

### Attempt 1: Add @Enumerated(EnumType.STRING)

**File:** `analytics-service/src/main/java/com/callaudit/analytics/domain/transcription/TranscriptionSegment.java`

**Change:**
```java
// Before
@Column(name = "speaker", nullable = false)
private SpeakerType speaker;

// After
@Enumerated(EnumType.STRING)
@Column(name = "speaker", nullable = false)
private SpeakerType speaker;
```

**What it does:** Tells Hibernate to store the enum as its string name (`"agent"`, `"customer"`, `"unknown"`) instead of its ordinal position (0, 1, 2).

**Result:** Error changed from `smallint` to `character varying` - progress, but still a type mismatch because PostgreSQL expected the custom `speaker_type` enum, not a plain string.

---

### Attempt 2: Hypersistence Utils (Abandoned)

**Goal:** Use Hypersistence Utils library to properly handle PostgreSQL custom enum types.

**Changes attempted:**

1. **Added dependency to `pom.xml`:**
```xml
<dependency>
    <groupId>io.hypersistence</groupId>
    <artifactId>hypersistence-utils-hibernate-63</artifactId>
    <version>3.7.3</version>
</dependency>
```

2. **Updated entity with PostgreSQL-specific type:**
```java
import io.hypersistence.utils.hibernate.type.basic.PostgreSQLEnumType;
import org.hibernate.annotations.Type;

@Enumerated(EnumType.STRING)
@Type(PostgreSQLEnumType.class)
@Column(name = "speaker", nullable = false, columnDefinition = "speaker_type")
private SpeakerType speaker;
```

**What Hypersistence Utils does:**
- Provides custom Hibernate type handlers for PostgreSQL-specific types
- `PostgreSQLEnumType` tells Hibernate to cast the string value to the PostgreSQL enum type
- Generates SQL like: `INSERT INTO segments (speaker, ...) VALUES ('agent'::speaker_type, ...)`

**Result:** Build failed with:
```
cannot find symbol: class PostgreSQLEnumType
location: package io.hypersistence.utils.hibernate.type.basic
```

**Why it failed:** The class location may have changed in newer versions, or there's a compatibility issue with Hibernate 6.4.x used by Spring Boot 3.2.x. Would require further investigation of the correct package path.

---

### Attempt 3: Change Database Schema (Final Solution)

Instead of fighting the PostgreSQL custom enum type, align the database schema with how other enum columns are handled in the project.

**Observation:** Other enum columns in the schema use `VARCHAR`:
```sql
-- From schema.sql (lines 96-107)
-- Note: channel and status use VARCHAR to match JPA @Enumerated(EnumType.STRING)
channel VARCHAR(255) NOT NULL, -- Values: 'INBOUND', 'OUTBOUND', 'INTERNAL'
status VARCHAR(255) NOT NULL,  -- Values: 'PENDING', 'TRANSCRIBING', etc.
```

**Changes:**

1. **Updated `schema.sql` (line 362-363):**
```sql
-- Before
speaker speaker_type NOT NULL,

-- After
speaker VARCHAR(50) NOT NULL, -- Values: 'agent', 'customer', 'unknown'
```

2. **Applied change to running database:**
```sql
DROP TABLE IF EXISTS analytics.segments CASCADE;
CREATE TABLE IF NOT EXISTS analytics.segments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transcription_id UUID NOT NULL REFERENCES analytics.transcriptions(id) ON DELETE CASCADE,
    speaker VARCHAR(50) NOT NULL,
    start_time DECIMAL(10,3) NOT NULL,
    end_time DECIMAL(10,3) NOT NULL,
    text TEXT NOT NULL,
    confidence DECIMAL(5,4),
    word_count INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_segments_transcription_id ON analytics.segments(transcription_id);
CREATE INDEX IF NOT EXISTS idx_segments_speaker ON analytics.segments(speaker);
```

3. **Reverted entity to simple form:**
```java
@Enumerated(EnumType.STRING)
@Column(name = "speaker", nullable = false)
private SpeakerType speaker;
```

4. **Removed Hypersistence Utils dependency from `pom.xml`**

**Result:** Success. JPA sends string values, PostgreSQL stores them as VARCHAR.

---

## Final State of Files

### TranscriptionSegment.java (lines 33-35)
```java
@Enumerated(EnumType.STRING)
@Column(name = "speaker", nullable = false)
private SpeakerType speaker;
```

### schema.sql (lines 358-370)
```sql
-- Segments table - speaker-separated segments within transcriptions
-- Note: speaker uses VARCHAR to match JPA @Enumerated(EnumType.STRING)
CREATE TABLE IF NOT EXISTS analytics.segments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transcription_id UUID NOT NULL REFERENCES analytics.transcriptions(id) ON DELETE CASCADE,
    speaker VARCHAR(50) NOT NULL, -- Values: 'agent', 'customer', 'unknown'
    start_time DECIMAL(10,3) NOT NULL,
    end_time DECIMAL(10,3) NOT NULL,
    text TEXT NOT NULL,
    confidence DECIMAL(5,4),
    word_count INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
```

### pom.xml
No Hypersistence Utils dependency (reverted).

---

## When to Use Hypersistence Utils

Hypersistence Utils is valuable when you need to:
1. Use PostgreSQL-specific types (arrays, JSONB, enums, ranges, etc.)
2. Keep database constraints enforced at the DB level
3. Leverage PostgreSQL's type safety for enums

If you want to use Hypersistence Utils in the future, here's the correct approach for Hibernate 6.x:

```xml
<dependency>
    <groupId>io.hypersistence</groupId>
    <artifactId>hypersistence-utils-hibernate-63</artifactId>
    <version>3.7.3</version>
</dependency>
```

The exact annotation may vary - consult the [Hypersistence Utils documentation](https://github.com/vladmihalcea/hypersistence-utils) for the correct class path for your Hibernate version.

---

## Trade-offs of VARCHAR vs Custom Enum

| Aspect | Custom PostgreSQL Enum | VARCHAR |
|--------|----------------------|---------|
| Type safety | Database enforces valid values | Application must enforce |
| Storage | More compact (4 bytes) | Variable (up to 50 chars) |
| Flexibility | Requires `ALTER TYPE` to add values | Easy to add new values |
| JPA compatibility | Requires extra library | Works out of the box |
| Consistency | Can differ from Java enum | Mirrors Java enum exactly |

The project chose VARCHAR for consistency across all enum columns and simpler JPA integration.
