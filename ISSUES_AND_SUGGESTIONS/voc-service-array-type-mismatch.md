# VOC Service Array Type Mismatch

## Problem

The `voc-service` fails to start due to a Hibernate 7 schema validation error with PostgreSQL native array columns.

## Error Message

```
Schema-validation: wrong column type encountered in column [keywords] in table [voc_insights];
found [_text (Types#ARRAY)], but expecting [text[] (Types#UNKNOWN(1550089733))]
```

## Root Cause

Hibernate 7 has stricter type validation for PostgreSQL array types. The issue occurs because:

1. The entity uses `@Column(columnDefinition = "text[]")` for array columns
2. PostgreSQL stores these as `_text` (its internal array type name)
3. Hibernate 7 sees `text[]` as an "UNKNOWN" type and fails to match it with the database's `_text` type
4. The custom `StringArrayConverter` works at runtime but doesn't help with schema validation

## Affected Fields

| Entity | Column | Current Definition |
|--------|--------|-------------------|
| VocInsight | topics | `text[]` with `StringArrayConverter` |
| VocInsight | keywords | `text[]` with `StringArrayConverter` |

## Solution

Convert array columns from native PostgreSQL arrays (`text[]`) to JSONB storage, which Hibernate 7 handles correctly. This is consistent with how `actionableItems` is already stored in the same entity.

### Before
```java
@Convert(converter = StringArrayConverter.class)
@Column(columnDefinition = "text[]")
private List<String> keywords;
```

### After
```java
@Convert(converter = StringListConverter.class)
@Column(columnDefinition = "jsonb")
private List<String> keywords;
```

## Database Migration Required

The schema change requires updating the existing columns:

```sql
-- Convert text[] columns to jsonb
ALTER TABLE voc.voc_insights
  ALTER COLUMN topics TYPE jsonb USING to_jsonb(topics),
  ALTER COLUMN keywords TYPE jsonb USING to_jsonb(keywords);
```

## Alternative Solutions Considered

1. **Use Hibernate's @JdbcTypeCode**: Hibernate 7 has built-in array support via `@JdbcTypeCode(SqlTypes.ARRAY)`, but this requires additional dialect configuration and may have compatibility issues.

2. **Custom Hibernate Type**: Create a custom Hibernate type for PostgreSQL arrays, but this adds complexity.

3. **Switch to JSONB (chosen)**: Simplest solution, consistent with existing patterns in the codebase, and JSONB is well-supported across Hibernate versions.

## Prevention

For array/list fields in JPA entities, prefer JSONB storage with `StringListConverter` over native PostgreSQL arrays when using Hibernate 7+.

## Additional Issue: DECIMAL/BigDecimal Type Mismatch

After fixing the array type issue, another schema validation error occurred:

```
Schema-validation: wrong column type encountered in column [predicted_churn_risk] in table [voc_insights];
found [numeric (Types#NUMERIC)], but expecting [decimal(5,4) (Types#FLOAT)]
```

### Root Cause

The `predictedChurnRisk` field was defined as:
- Java type: `Double`
- Column definition: `@Column(columnDefinition = "DECIMAL(5,4)")`

Hibernate 7 maps `Double` to `float(53)` (SQL FLOAT), not to NUMERIC/DECIMAL.

### Solution

Changed the Java type from `Double` to `BigDecimal` with proper precision/scale:

```java
// Before
@Column(nullable = false, columnDefinition = "DECIMAL(5,4)")
private Double predictedChurnRisk;

// After
@Column(nullable = false, precision = 5, scale = 4)
private BigDecimal predictedChurnRisk;
```

### Related Changes

- Updated `VocEventListener` to convert `Double` from analysis result to `BigDecimal`
- Updated `VocInsightRepository.findByPredictedChurnRiskGreaterThanEqual` to use `BigDecimal` parameter
- Updated `InsightService` to convert `double` threshold to `BigDecimal`
- Updated all test files to use `BigDecimal.valueOf()` for churn risk values

## Status

**Fixed** - All type issues resolved:
1. `topics` and `keywords` columns now use JSONB storage with `StringListConverter`
2. `predictedChurnRisk` now uses `BigDecimal` with `precision = 5, scale = 4`
