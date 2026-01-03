# UUID/String Type Mismatch in JPA Entities

## Problem

The `notification-service` and `voc-service` fail to start due to JPA schema validation errors. Entity fields are typed as `String` but the corresponding database columns are `UUID`.

## Error Messages

### notification-service
```
Schema-validation: wrong column type encountered in column [call_id]
in table [notification.notifications]; found [uuid (Types#OTHER)],
but expecting [character varying(255) (Types#VARCHAR)]
```

### voc-service
```
Schema-validation: wrong column type encountered in column [id]
in table [voc.voc_insights]; found [uuid (Types#OTHER)],
but expecting [character varying(255) (Types#VARCHAR)]
```

## Root Cause

When using `@GeneratedValue(strategy = GenerationType.UUID)`, Hibernate expects the field type to be `java.util.UUID`. Using `String` causes a type mismatch because:

1. PostgreSQL stores the column as native `UUID` type
2. Hibernate's `ddl-auto: validate` correctly expects `UUID` type fields
3. The entity declares `String`, causing the schema validation to fail

## Affected Files

| Service | File | Field | Line |
|---------|------|-------|------|
| notification-service | `model/Notification.java` | `callId` | 34 |
| voc-service | `model/VocInsight.java` | `id` | 27 |

### Related Files Requiring Updates

**notification-service:**
- `repository/NotificationRepository.java` - Query parameter types
- `service/NotificationService.java` - Method signatures
- `controller/NotificationController.java` - Path variable types
- `listener/NotificationEventListener.java` - UUID parsing from events

**voc-service:**
- `repository/VocInsightRepository.java` - Repository ID type generic

## Solution

Change field types from `String` to `UUID`:

```java
// Before
private String callId;

// After
private UUID callId;
```

For the `@Id` field with UUID generation:
```java
// Before
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private String id;

// After
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private UUID id;
```

## Prevention

A rule has been added to `CLAUDE.md` to prevent this issue:

> **UUID Field Type Policy**: Always use `java.util.UUID` type for UUID fields in JPA entities, never `String`. When using `@GeneratedValue(strategy = GenerationType.UUID)`, the field MUST be typed as `UUID`.

## Status

**Fixed** - Entity types updated to use `UUID` instead of `String`.
