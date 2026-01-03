# Known Issues and Resolutions

This document tracks known issues encountered in the Call Auditing Platform and their resolutions.

## Resolved Issues

### 1. File Upload Errors - ERR_INCOMPLETE_CHUNKED_ENCODING (RESOLVED)

**Date Discovered**: 2026-01-01
**Date Resolved**: 2026-01-01
**Affected Component**: API Gateway, Call Auditing UI

**Symptoms**:
- Browser displays "No response from server. Please check your connection."
- Browser DevTools Console shows `ERR_INCOMPLETE_CHUNKED_ENCODING` error
- HTTP status is 201 Created (indicating backend success)
- Backend logs show successful file upload to MinIO
- Kafka events are published successfully
- File is persisted but frontend never receives response

**Root Cause**:
Spring Cloud Gateway's default HTTP client response timeout was insufficient for file upload operations. The backend successfully processed uploads (uploading to MinIO, saving to database, publishing Kafka events) but the API Gateway timed out while waiting for the response, causing an incomplete chunked transfer encoding error.

**Resolution**:
Added HTTP client timeout configuration to `api-gateway/src/main/resources/application.yml`:

```yaml
spring:
  cloud:
    gateway:
      httpclient:
        response-timeout: 300s  # 5 minutes for large file uploads
        connect-timeout: 30000  # 30 seconds
        pool:
          type: ELASTIC
          max-idle-time: 30s
```

**Files Modified**:
- `api-gateway/src/main/resources/application.yml` - Added HTTP client configuration
- `api-gateway/README.md` - Added troubleshooting documentation
- `TODO.md` - Documented fix in completed section

**Verification**:
After applying the fix and restarting the API Gateway:
```bash
docker compose restart api-gateway
```

File uploads now complete successfully with proper response delivery to the frontend.

**Lessons Learned**:
- Spring Cloud Gateway requires explicit timeout configuration for long-running operations
- Default timeouts (typically 30-60 seconds) are insufficient for file uploads
- Response timeout must accommodate: multipart parsing + storage upload + database writes + event publishing
- Always monitor both frontend errors AND backend logs to identify proxy/gateway timeout issues

---

### 2. Transcription Service OOM Crashes (RESOLVED)

**Date Discovered**: 2026-01-01
**Date Resolved**: 2026-01-01
**Affected Component**: Transcription Service

**Symptoms**:
- Transcription service exits with code 137 (SIGKILL)
- Events appear in `calls.received` Kafka topic but not in `calls.transcribed`
- Service restarts but crashes again during Whisper model loading
- Docker logs show service startup but no event consumption

**Root Cause**:
The Whisper "small" model requires ~2GB+ RAM to load. Docker container did not have sufficient memory allocated, causing the OS OOM killer to terminate the process during model initialization.

**Resolution**:
Changed Whisper model size from "small" to "tiny" in `transcription-service/config.py`:

```python
model_size: str = "tiny"  # Changed from "small"
```

The "tiny" model requires ~1GB RAM and provides acceptable transcription quality for development/testing.

**Files Modified**:
- `transcription-service/config.py` - Changed model_size from "small" to "tiny"

**Verification**:
After applying the fix and rebuilding:
```bash
docker compose up -d --build transcription-service
```

Service successfully:
- Loads Whisper "tiny" model
- Consumes events from `calls.received` topic
- Processes transcriptions
- Publishes to `calls.transcribed` topic
- Remains stable without OOM crashes

**For Production**:
Consider either:
1. Increasing Docker memory limits to support larger models
2. Using GPU acceleration for better performance
3. Deploying to nodes with sufficient RAM (8GB+ for "medium" model)

---

## Active Issues

(None currently)

---

## Monitoring and Prevention

### How to Detect Similar Issues

1. **API Gateway Timeout Issues**:
   - Monitor for ERR_INCOMPLETE_CHUNKED_ENCODING in browser console
   - Check if HTTP status is 2xx but response body is incomplete
   - Look for timeout-related errors in gateway logs
   - Solution: Increase `response-timeout` in gateway config

2. **Service OOM Crashes**:
   - Monitor for exit code 137 in Docker logs
   - Check Docker stats for memory usage patterns
   - Look for services that restart frequently
   - Solution: Increase container memory limits or optimize service

3. **Event Processing Delays**:
   - Monitor Kafka consumer lag
   - Check for missing events in downstream topics
   - Verify all consumers are running and healthy
   - Solution: Restart stopped services, check for processing errors

### Useful Commands

```bash
# Check service health
docker compose ps

# Check service logs for errors
docker compose logs --tail=100 <service-name> | grep -E "ERROR|Exception|timeout"

# Check for OOM crashes (exit code 137)
docker compose ps -a | grep "Exited (137)"

# Monitor Docker resource usage
docker stats

# Check Kafka topic messages
docker compose exec kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic calls.received \
  --from-beginning --max-messages 5

# Verify API Gateway routes
curl http://localhost:8080/actuator/gateway/routes
```

---

**Last Updated**: 2026-01-01
