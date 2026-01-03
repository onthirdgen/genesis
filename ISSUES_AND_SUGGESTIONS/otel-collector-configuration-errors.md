# Issue: OpenTelemetry Collector Configuration Errors Causing Service Failures

**Date Identified**: January 1, 2026
**Severity**: HIGH (Blocks observability stack)
**Affected Component**: `otel-collector` (Infrastructure)
**Impact**: All microservices unable to export telemetry data (traces, metrics, logs)

---

## Problem Summary

The OpenTelemetry Collector (otel-collector) container is **failing to start** due to configuration errors in `/monitoring/otel-collector-config.yaml`. This causes all microservices that depend on it to fail when attempting to export telemetry data.

### Observable Symptoms

1. **otel-collector container not running**
   ```bash
   $ docker compose ps otel-collector
   # Returns empty - container crashed on startup
   ```

2. **All microservices logging connection errors**
   ```
   [ERROR] io.opentelemetry.exporter.internal.http.HttpExporter - Failed to export spans/logs/metrics
   java.net.UnknownHostException: otel-collector
   ```

3. **Repeated error patterns across services:**
   - call-ingestion-service: Every ~10-15 seconds
   - api-gateway: Every ~10-15 seconds
   - voc-service: Every ~10-15 seconds
   - audit-service: Every ~10-15 seconds
   - analytics-service: Every ~10-15 seconds
   - notification-service: Every ~10-15 seconds

---

## Root Cause Analysis

### Container Startup Failure

The otel-collector container **crashes immediately on startup** with the following errors:

```
Error: failed to get config: cannot unmarshal the configuration: decoding failed due to the following error(s):

'exporters' the logging exporter has been deprecated, use the debug exporter instead
'service.telemetry.metrics' decoding failed due to the following error(s):

'' has invalid keys: address
```

### Configuration File Issues

**File**: `/monitoring/otel-collector-config.yaml`

#### Error 1: Deprecated Exporter (Line 31-32)

```yaml
exporters:
  logging:           # ❌ DEPRECATED
    loglevel: info
```

**Problem**: The `logging` exporter has been deprecated in favor of the `debug` exporter in recent OpenTelemetry Collector versions.

**Reference**: [OpenTelemetry Collector v0.88.0 Release Notes](https://github.com/open-telemetry/opentelemetry-collector/releases/tag/v0.88.0)

---

#### Error 2: Invalid Telemetry Configuration (Line 46-51)

```yaml
service:
  telemetry:
    logs:
      level: info
    metrics:
      level: detailed
      address: 0.0.0.0:8888    # ❌ INVALID KEY
```

**Problem**: The `address` key is not valid in the `service.telemetry.metrics` section in recent collector versions.

**Reference**: Starting with OpenTelemetry Collector v0.86.0, the telemetry configuration schema changed. The `address` field should be at the root `telemetry` level, not nested under `metrics`.

---

### Why This Breaks All Microservices

1. **otel-collector fails to start** → Container exits immediately
2. **Docker network hostname unresolvable** → `otel-collector` hostname doesn't exist
3. **Microservices attempt connection** → OpenTelemetry Java agent tries to export data
4. **Connection fails repeatedly** → `UnknownHostException: otel-collector`
5. **Log pollution** → Errors logged every 10-15 seconds across all services

---

## Impact Assessment

### Critical Impact: No Observability

| Component | Status | Impact |
|-----------|--------|--------|
| Distributed Tracing | ❌ BROKEN | Cannot trace requests across microservices |
| Metrics Collection | ❌ BROKEN | No service metrics in Prometheus |
| Log Aggregation | ❌ BROKEN | No centralized logging via OTLP |
| Jaeger UI | ⚠️ EMPTY | Running but receiving no trace data |
| Grafana Dashboards | ⚠️ EMPTY | No metrics to display |

### Service-Level Impact

| Service | Functional? | Telemetry Errors | Log Pollution |
|---------|-------------|------------------|---------------|
| call-ingestion-service | ✅ YES | ❌ Every 10-15s | 🔴 HIGH |
| api-gateway | ✅ YES | ❌ Every 10-15s | 🔴 HIGH |
| voc-service | ✅ YES | ❌ Every 10-15s | 🔴 HIGH |
| audit-service | ✅ YES | ❌ Every 10-15s | 🔴 HIGH |
| analytics-service | ✅ YES | ❌ Every 10-15s | 🔴 HIGH |
| notification-service | ✅ YES | ❌ Every 10-15s | 🔴 HIGH |

**Good News**: All services are **functionally operational** - this is purely an observability issue.

**Bad News**:
- Cannot debug distributed request flows
- Cannot monitor service health metrics
- Cannot detect performance issues
- Logs polluted with telemetry errors

---

## Solutions

### Solution 1: Fix Configuration File (Recommended)

**Approach**: Update `otel-collector-config.yaml` to use correct syntax.

#### Changes Required

**Change 1: Replace deprecated `logging` exporter with `debug`**

```yaml
exporters:
  # Export traces to Jaeger
  otlp/jaeger:
    endpoint: jaeger:4317
    tls:
      insecure: true

  # Export metrics to Prometheus
  prometheus:
    endpoint: "0.0.0.0:8888"
    namespace: call_auditing

  # Debug exporter for troubleshooting (UPDATED)
  debug:
    verbosity: detailed
```

**Change 2: Fix telemetry configuration**

```yaml
service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [otlp/jaeger, debug]  # Changed logging → debug

    metrics:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [prometheus, debug]  # Changed logging → debug

  telemetry:
    logs:
      level: info
    metrics:
      level: detailed
      # REMOVED: address field (invalid)
```

**Alternative: If you need to configure telemetry endpoint**

```yaml
service:
  telemetry:
    logs:
      level: info
    metrics:
      level: detailed
      address: 0.0.0.0:8888  # Move to root telemetry level if needed
```

---

### Solution 2: Disable OpenTelemetry in Services (Temporary Workaround)

**Approach**: Remove OTEL environment variables from docker-compose.yml to stop connection attempts.

**Not Recommended**: This eliminates all observability - only use as last resort.

---

## Recommended Fix Plan

### Step 1: Fix Configuration File

Edit `/monitoring/otel-collector-config.yaml`:

1. Replace `logging` with `debug` exporter (lines 31-32)
2. Update pipeline exporters to use `debug` instead of `logging` (lines 39, 44)
3. Remove `address: 0.0.0.0:8888` from `service.telemetry.metrics` (line 51)

### Step 2: Restart otel-collector

```bash
docker compose up -d otel-collector
```

### Step 3: Verify Startup

```bash
# Check container is running
docker compose ps otel-collector

# Check logs for successful startup
docker compose logs otel-collector --tail 20
```

Expected output:
```
otel-collector-1  | 2026-01-01T08:00:00.000Z info service@v0.x.x/service.go:XXX Everything is ready. Begin running and processing data.
```

### Step 4: Verify Services Can Connect

```bash
# Should see successful OTLP exports instead of UnknownHostException
docker compose logs call-ingestion-service --tail 20 | grep -i otel
```

### Step 5: Verify Telemetry Flow

1. **Check Jaeger UI**: http://localhost:16686
   - Should see traces appearing from microservices

2. **Check Prometheus Metrics**: http://localhost:9090
   - Query: `{job="otel-collector"}` - should return metrics

3. **Check Grafana**: http://localhost:3000
   - Dashboards should populate with service metrics

---

## Testing the Fix

### Before Fix

```bash
$ docker compose ps otel-collector
# Empty output - container not running

$ docker compose logs call-ingestion-service | grep -i "otel"
[ERROR] Failed to export spans. java.net.UnknownHostException: otel-collector
```

### After Fix

```bash
$ docker compose ps otel-collector
NAME                   STATUS          PORTS
genesis-otel-collector-1   Up 2 minutes   0.0.0.0:4317-4318->4317-4318/tcp

$ docker compose logs call-ingestion-service | grep -i "otel"
# No errors - telemetry exporting successfully
```

---

## Additional Configuration Issues Found

### Issue 1: Hibernate Dialect Warning (call-ingestion-service)

**Warning**: `PostgreSQLDialect does not need to be specified explicitly using 'hibernate.dialect'`

**Location**: `call-ingestion-service/src/main/resources/application.yml:29`

**Fix**: Remove the following lines:
```yaml
properties:
  hibernate:
    dialect: org.hibernate.dialect.PostgreSQLDialect  # Remove this
```

**Priority**: LOW (just a warning, auto-detection works fine)

---

### Issue 2: JPA Open-in-View Warning (call-ingestion-service)

**Warning**: `spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering.`

**Location**: `call-ingestion-service/src/main/resources/application.yml`

**Fix**: Add explicit configuration:
```yaml
spring:
  jpa:
    open-in-view: false  # Add this line
```

**Why**:
- Open-in-view is an anti-pattern in REST APIs
- Can cause performance issues
- Holds database connections open during HTTP response rendering

**Priority**: MEDIUM (performance and best practices)

---

## Action Items

### Immediate (High Priority)

- [ ] Fix `otel-collector-config.yaml` configuration errors
- [ ] Restart otel-collector container
- [ ] Verify all services can export telemetry
- [ ] Confirm Jaeger receives traces
- [ ] Confirm Prometheus receives metrics

### Short-term (Medium Priority)

- [ ] Fix JPA open-in-view configuration in call-ingestion-service
- [ ] Remove deprecated Hibernate dialect specification
- [ ] Test distributed tracing across all microservices
- [ ] Create Grafana dashboards for service metrics

### Long-term (Low Priority)

- [ ] Document OpenTelemetry setup in README
- [ ] Add health checks for otel-collector in docker-compose.yml
- [ ] Consider custom OTLP SDK instrumentation for Python services
- [ ] Set up alerting for telemetry pipeline failures

---

## References

### OpenTelemetry Documentation

- [Collector Configuration Schema](https://opentelemetry.io/docs/collector/configuration/)
- [Debug Exporter](https://github.com/open-telemetry/opentelemetry-collector/tree/main/exporter/debugexporter)
- [Migration from Logging to Debug Exporter](https://github.com/open-telemetry/opentelemetry-collector/blob/main/CHANGELOG.md#v0880)

### Related Files

- Configuration: `/monitoring/otel-collector-config.yaml`
- Docker Compose: `/docker-compose.yml` (lines 284-296)
- Service Configs: `*/src/main/resources/application.yml`

### Related Issues

- MinIO Test Failures: [test-failures-minio-dependency.md](test-failures-minio-dependency.md)

---

## Status

**Current**: ❌ **FAILING** (otel-collector not running, all services logging errors)
**Target**: ✅ **otel-collector running, telemetry flowing to Jaeger and Prometheus**
**Estimated Fix Time**: 5-10 minutes (configuration changes only)
**Priority**: **HIGH** - Blocking observability and polluting logs

---

**Last Updated**: January 1, 2026
