# Issues and Suggestions

This directory contains documentation of known issues, bugs, and suggested improvements for the Call Auditing Platform.

## Purpose

- **Track Issues**: Document problems discovered during development and testing
- **Suggest Solutions**: Provide detailed analysis and recommended fixes
- **Knowledge Base**: Preserve context and decisions for future reference
- **Onboarding**: Help new team members understand known issues and workarounds

## Active Issues

### High Priority

| Issue | Service | Status | Document |
|-------|---------|--------|----------|
| OpenTelemetry Collector Config Errors | otel-collector | ❌ FAILING | [otel-collector-configuration-errors.md](otel-collector-configuration-errors.md) |
| MinIO-Dependent Test Failures | call-ingestion-service | ❌ FAILING | [test-failures-minio-dependency.md](test-failures-minio-dependency.md) |

### Recently Resolved

| Issue | Service | Resolved Date | Document |
|-------|---------|---------------|----------|
| File Upload 405 Method Not Allowed | api-gateway, call-ingestion, UI | 2026-01-02 | [file-upload-405-error-analysis.md](file-upload-405-error-analysis.md) |
| Authentication BCrypt Password Mismatch | api-gateway | 2026-01-02 | [UI_BACKEND_INTEGRATION_STATUS.md](../UI_BACKEND_INTEGRATION_STATUS.md) |
| NumPy Compatibility & Blocking Kafka Consumer | sentiment-service | 2026-01-02 | [sentiment-service-numpy-compatibility-issue.md](sentiment-service-numpy-compatibility-issue.md) |

---

## Issue Categories

### Authentication & Security Issues
- [UI_BACKEND_INTEGRATION_STATUS.md](../UI_BACKEND_INTEGRATION_STATUS.md) - ✅ RESOLVED: BCrypt password hash mismatch preventing login
- [file-upload-405-error-analysis.md](file-upload-405-error-analysis.md) - ✅ RESOLVED: JWT filter blocking CORS preflight requests

### Dependency & Compatibility Issues
- [sentiment-service-numpy-compatibility-issue.md](sentiment-service-numpy-compatibility-issue.md) - ✅ RESOLVED: NumPy 2.x incompatibility and blocking Kafka consumer fixed

### Testing Issues
- [test-failures-minio-dependency.md](test-failures-minio-dependency.md) - Tests failing due to MinIO connectivity requirements

### Performance Issues
- *None documented yet*

### Infrastructure Issues
- [otel-collector-configuration-errors.md](otel-collector-configuration-errors.md) - OpenTelemetry Collector failing to start, blocking all observability

---

## How to Use This Directory

### When You Discover an Issue

1. Create a new markdown file: `<issue-name>.md`
2. Use the template below
3. Update this README with a link to your issue
4. Assign priority (High/Medium/Low)

### Issue Document Template

```markdown
# Issue: [Brief Description]

**Date Identified**: YYYY-MM-DD
**Severity**: HIGH | MEDIUM | LOW
**Affected Component**: [Service/Module Name]
**Impact**: [Description of impact]

---

## Problem Summary
[Brief description of the problem]

## Root Cause Analysis
[Technical explanation of why this happens]

## Impact
[Who/what is affected]

## Solutions
[Proposed solutions with pros/cons]

## Recommendation
[Which solution to implement and why]

## Action Items
[Concrete steps to resolve]

## Status
[Current status and next steps]
```

---

## Contributing

When adding new issues:
- Be specific and include error messages/logs
- Provide context (when, where, how often)
- Suggest solutions, not just problems
- Update this README index

---

## Issue Status Definitions

- ❌ **FAILING** - Active issue causing failures
- ⚠️ **DEGRADED** or **PARTIAL** - Issue causing reduced functionality or partially resolved
- 🔍 **INVESTIGATING** - Root cause being analyzed
- 🛠️ **IN PROGRESS** - Fix is being implemented
- ✅ **RESOLVED** - Issue has been fixed
- 📝 **DOCUMENTED** - Known issue with workaround

---

**Last Updated**: January 2, 2026 (sentiment-service issue resolved)
