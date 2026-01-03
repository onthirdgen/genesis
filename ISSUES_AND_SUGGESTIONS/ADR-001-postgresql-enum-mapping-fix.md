# ADR-001: PostgreSQL Enum Mapping Fix for TranscriptionSegment

**Date**: 2026-01-02
**Status**: Accepted
**Deciders**: Development Team
**Context**: Transcription 404 bug investigation (see `transcription-404-investigation.md`)

---

## Context

The analytics-service fails to persist transcription segments to the database due to a PostgreSQL enum type mismatch error:

```
ERROR: column "speaker" is of type speaker_type but expression is of type character varying
Hint: You will need to rewrite or cast the expression.
```

**Root cause**: In `TranscriptionSegment.java`, the combination of annotations:
```java
@Enumerated(EnumType.STRING)  // Forces VARCHAR mapping
@Column(name = "speaker", nullable = false, columnDefinition = "speaker_type")
private SpeakerType speaker;
```

The `@Enumerated(EnumType.STRING)` tells Hibernate to send VARCHAR to PostgreSQL, but the database column expects the native `speaker_type` enum. The `columnDefinition` is only a DDL hint and does not affect runtime behavior.

**Impact**: All transcription saves fail silently after 10 Kafka retries. No transcriptions are stored.

---

## Decision

**We will implement Option 1: Remove the `@Enumerated` annotation and let Hibernate 6.x use native enum handling.**

### Change

```java
// BEFORE:
@Enumerated(EnumType.STRING)
@Column(name = "speaker", nullable = false, columnDefinition = "speaker_type")
private SpeakerType speaker;

// AFTER:
@Column(name = "speaker", nullable = false)
private SpeakerType speaker;
```

---

## Options Considered

### Option 1: Remove @Enumerated annotation (SELECTED)
- **Pros**: Zero dependencies, leverages Hibernate 6.x native PostgreSQL enum support, maintains database-level type safety
- **Cons**: Relies on Hibernate auto-detection (well-supported in Hibernate 6.x)
- **Risk**: Low - Hibernate 6 bundled with Spring Boot 3.2.5 has mature PostgreSQL enum support

### Option 2: Use Hibernate Types library
- **Pros**: Explicit control via `@Type(PostgreSQLEnumType.class)`, well-documented
- **Cons**: Adds external dependency (`hypersistence-utils-hibernate-60`)
- **Risk**: Low - Vlad Mihalcea's library is widely used and maintained

### Option 3: Change database column to VARCHAR
- **Pros**: Simplest fix, framework-agnostic, portable across databases
- **Cons**: Loses database-level type safety, larger storage footprint, raw SQL can insert invalid values
- **Risk**: Medium - Data integrity issues in compliance-sensitive call auditing domain

### Option 4: Custom JPA AttributeConverter
- **Pros**: Full control over conversion logic
- **Cons**: Additional code to maintain, over-engineered for this use case
- **Risk**: Low - Standard JPA approach

---

## Rationale

### Why Option 1?

1. **Hibernate 6.x Native Support**: Modern Hibernate 6 (bundled with Spring Boot 3.2.5) automatically detects and correctly maps Java enums to PostgreSQL native enums when `@Enumerated` is absent.

2. **Database-Level Integrity**: Our event-sourced architecture requires database-level validation:
   - Events may replay and bypass JPA validation
   - Other services could write directly to the analytics schema
   - Call auditing is a compliance domain requiring strict data integrity

3. **Zero Dependencies**: No external libraries needed - relies on mature Hibernate core functionality.

4. **Enum Value Alignment**: Java `SpeakerType` values (`agent`, `customer`, `unknown`) already exactly match PostgreSQL enum values - no mapping issues.

5. **Performance**: Native PostgreSQL enums use 4-byte integer storage vs VARCHAR's variable length, providing better storage efficiency and index performance for analytics workloads.

### Why Not Option 3 (VARCHAR)?

While VARCHAR is simpler and more portable:
- Loses database-level constraint enforcement
- Raw SQL inserts can corrupt data with invalid values
- Larger storage and index overhead (~46MB per 1M segments)
- Wrong trade-off for a compliance-sensitive auditing system

---

## Consequences

### Positive
- Transcription segments will persist correctly to the database
- Database-level type safety preserved
- No new dependencies introduced
- No schema migration required

### Negative
- H2 test database does not support PostgreSQL enums - integration tests should use Testcontainers with real PostgreSQL
- Other entities with similar patterns should be audited and fixed

### Neutral
- Hibernate logging should be monitored to verify correct type casting (`?::speaker_type`)

---

## Related Files

- `analytics-service/src/main/java/com/callaudit/analytics/domain/transcription/TranscriptionSegment.java`
- `schema.sql` (analytics.segments table, line ~362)
- `transcription-404-investigation.md`

---

## Follow-up Actions

- [x] Audit other enum columns across services for same issue
- [ ] Consider adding Testcontainers for PostgreSQL-specific integration tests
- [ ] Monitor Kafka consumer logs after deployment to verify transcription saves succeed

---

## Appendix: Other Affected Entities (Case Mismatch)

During the audit, we identified **5 additional entities** with the same `@Enumerated(EnumType.STRING)` pattern that map to native PostgreSQL enum columns. However, these have a **case mismatch** that prevents the simple removal of `@Enumerated`:

| Service | Entity | Column | Java Enum | PostgreSQL Enum |
|---------|--------|--------|-----------|-----------------|
| audit-service | `AuditResult` | `compliance_status` | `PASSED`, `FAILED`, `REVIEW_REQUIRED` | `'passed'`, `'failed'`, `'review_required'` |
| audit-service | `ComplianceViolation` | `severity` | `CRITICAL`, `HIGH`, `MEDIUM`, `LOW` | `'critical'`, `'high'`, `'medium'`, `'low'` |
| audit-service | `ComplianceRule` | `severity` | `CRITICAL`, `HIGH`, `MEDIUM`, `LOW` | `'critical'`, `'high'`, `'medium'`, `'low'` |
| voc-service | `VocInsight` | `primary_intent` | `INQUIRY`, `COMPLAINT`, etc. | `'inquiry'`, `'complaint'`, etc. |
| voc-service | `VocInsight` | `customer_satisfaction` | `LOW`, `MEDIUM`, `HIGH` | `'low'`, `'medium'`, `'high'` |

### Why TranscriptionSegment Works

The `SpeakerType` enum in `TranscriptionSegment` uses **lowercase values** that exactly match PostgreSQL:

```java
public enum SpeakerType {
    agent,      // matches 'agent'
    customer,   // matches 'customer'
    unknown     // matches 'unknown'
}
```

### Options for Case Mismatch Entities

**Option A: Change Java enum values to lowercase** (Recommended for consistency)
```java
// Change from:
public enum ComplianceStatus { PASSED, FAILED, REVIEW_REQUIRED }

// Change to:
public enum ComplianceStatus { passed, failed, review_required }
```
- Breaks Java naming convention but matches PostgreSQL
- Consistent with SpeakerType pattern already in codebase

**Option B: Custom AttributeConverter for case conversion**
```java
@Converter(autoApply = true)
public class ComplianceStatusConverter implements AttributeConverter<ComplianceStatus, String> {
    @Override
    public String convertToDatabaseColumn(ComplianceStatus status) {
        return status.name().toLowerCase();
    }

    @Override
    public ComplianceStatus convertToEntityAttribute(String value) {
        return ComplianceStatus.valueOf(value.toUpperCase());
    }
}
```
- Preserves Java convention
- Adds code complexity

**Option C: Update PostgreSQL enums to uppercase**
```sql
ALTER TYPE compliance_status RENAME VALUE 'passed' TO 'PASSED';
-- Requires migration for each enum value
```
- Requires schema migration
- May affect existing data and queries

### Recommendation

Use **Option A** (lowercase Java enums) for consistency with the existing `SpeakerType` pattern. While it breaks Java naming convention, it:
1. Maintains consistency within the codebase
2. Requires no additional code or dependencies
3. Works immediately with Hibernate 6 native enum support

### Files Updated (Option A Applied - 2026-01-02)

**Enum definitions changed to lowercase:**
1. `audit-service/src/main/java/com/callaudit/audit/model/ComplianceStatus.java` ✓
2. `audit-service/src/main/java/com/callaudit/audit/model/ViolationSeverity.java` ✓
3. `voc-service/src/main/java/com/callaudit/voc/model/Intent.java` ✓
4. `voc-service/src/main/java/com/callaudit/voc/model/Satisfaction.java` ✓

**@Enumerated annotations removed:**
5. `audit-service/src/main/java/com/callaudit/audit/model/AuditResult.java` ✓
6. `audit-service/src/main/java/com/callaudit/audit/model/ComplianceViolation.java` ✓
7. `audit-service/src/main/java/com/callaudit/audit/model/ComplianceRule.java` ✓
8. `voc-service/src/main/java/com/callaudit/voc/model/VocInsight.java` ✓

**All code references updated to lowercase:**
- audit-service main code + tests
- voc-service main code + tests
