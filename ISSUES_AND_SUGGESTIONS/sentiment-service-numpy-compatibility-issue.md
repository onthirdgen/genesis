# Sentiment Service - NumPy Compatibility and Uvicorn Startup Issues

## Status
✅ **RESOLVED** - Both NumPy and HTTP endpoint issues fixed

## Date Identified
2026-01-02

## Date Resolved
2026-01-02

## Resolution Summary
Fixed two separate issues:
1. **NumPy 2.x incompatibility** - Pinned to NumPy 1.26.4
2. **Blocking Kafka consumer** - Converted to async generator pattern

## Symptoms

1. **HTTP Endpoints Not Responding**
   - Service container is running
   - Health endpoint (`http://localhost:8083/health`) returns connection refused
   - Root endpoint (`http://localhost:8083/`) returns connection refused
   - Uvicorn shows "Waiting for application startup" but never completes

2. **Kafka Consumer Working**
   - Service successfully connects to Kafka
   - Consumer group joins successfully
   - Background processing loop is running
   - Can consume messages from `calls.transcribed` topic

3. **Service Logs Show**
   ```
   INFO:     Started server process [1]
   INFO:     Waiting for application startup.
   2026-01-01 17:11:02,022 - main - INFO - Sentiment Service is ready
   ```
   But NO "Application startup complete" message from uvicorn

## Root Cause

**NumPy 2.x Incompatibility with PyTorch 2.2.0**

### Technical Details

1. **Version Conflict**:
   - `requirements.txt` specifies `torch==2.2.0` (released February 2024)
   - NumPy version is **not pinned** in requirements.txt
   - Pip installs latest NumPy 2.4.0 (released December 2024)
   - PyTorch 2.2.0 was compiled against NumPy 1.x and is incompatible with NumPy 2.x

2. **Error Message**:
   ```
   A module that was compiled using NumPy 1.x cannot be run in
   NumPy 2.4.0 as it may crash. To support both 1.x and 2.x
   versions of NumPy, modules must be compiled with NumPy 2.0.
   Some module may need to rebuild instead e.g. with 'pybind11>=2.12'.

   If you are a user of the module, the easiest solution will be to
   downgrade to 'numpy<2' or try to upgrade the affected module.
   ```

3. **Import Chain**:
   ```
   main.py → services.sentiment_service → transformers → torch → torch.nn →
   torch.nn.modules.transformer → NumPy incompatibility crash
   ```

4. **Current Versions**:
   - numpy: `2.4.0` ❌
   - torch: `2.2.0` (requires NumPy 1.x)
   - transformers: `4.38.1`

## Impact

- ✅ Kafka event processing works (background task runs)
- ✅ ML model loading succeeds
- ❌ HTTP API completely non-functional
- ❌ Health checks fail
- ❌ Monitoring/observability broken
- ❌ Cannot perform manual sentiment analysis via API

## Solution

### Option 1: Pin NumPy to 1.x (Recommended - Quick Fix)

**File**: `sentiment-service/requirements.txt`

Add or update:
```txt
numpy<2,>=1.24.0
```

**Pros**:
- Minimal change
- Backward compatible
- Works with existing torch 2.2.0
- Quick to implement and test

**Cons**:
- Uses older NumPy (security/features)
- Will need upgrade path eventually

### Option 2: Upgrade PyTorch to 2.5+ (Long-term)

**File**: `sentiment-service/requirements.txt`

Update:
```txt
torch>=2.5.0
transformers>=4.46.0
numpy>=2.0.0
```

**Pros**:
- Modern dependencies
- NumPy 2.x support
- Better performance
- Future-proof

**Cons**:
- Requires testing for API changes
- Potential breaking changes in torch 2.5
- Larger Docker image rebuild
- May need code changes

### Option 3: Hybrid Approach

Pin NumPy 1.x now, plan upgrade:

**Immediate** (`requirements.txt`):
```txt
numpy<2,>=1.26.4  # Last stable NumPy 1.x
torch==2.2.0
transformers==4.38.1
```

**Future** (separate PR):
```txt
numpy>=2.1.0
torch>=2.5.1
transformers>=4.46.0
```

## Recommended Action Plan

### Phase 1: Immediate Fix (Today)

1. **Update `sentiment-service/requirements.txt`**:
   ```diff
   fastapi==0.110.0
   uvicorn[standard]==0.27.1
   kafka-python-ng==2.2.2
   transformers==4.38.1
   torch==2.2.0
   + numpy<2,>=1.26.4
   vaderSentiment==3.3.2
   pydantic==2.6.1
   pydantic-settings==2.1.0
   prometheus-client==0.20.0
   ```

2. **Rebuild Docker image**:
   ```bash
   docker compose build sentiment-service
   ```

3. **Test**:
   ```bash
   docker compose up -d sentiment-service
   curl http://localhost:8083/health
   ```

### Phase 2: Comprehensive Upgrade (Future Sprint)

1. Create upgrade branch
2. Update to PyTorch 2.5+ with NumPy 2.x support
3. Run full integration tests
4. Performance benchmarking
5. Deploy to staging
6. Production rollout

## Verification Steps (After Fix)

1. **Build and Start**:
   ```bash
   docker compose build sentiment-service
   docker compose up -d sentiment-service
   ```

2. **Check Logs** (should see):
   ```
   INFO:     Started server process [1]
   INFO:     Waiting for application startup.
   INFO:     Application startup complete.  ✅
   INFO:     Uvicorn running on http://0.0.0.0:8000  ✅
   ```

3. **Test HTTP Endpoints**:
   ```bash
   curl http://localhost:8083/health
   # Returns: {"status":"healthy","model_loaded":true,"kafka_connected":true} ✅

   curl http://localhost:8083/
   # Returns: {"service":"sentiment-service","status":"running"} ✅
   ```

4. **Verify Kafka Processing**:
   ```bash
   docker compose logs sentiment-service | grep "Starting to consume"
   # Should see periodic polling messages ✅
   ```

## Root Cause Analysis - HTTP Endpoint Issue (RESOLVED)

### The Problem

The background Kafka consumer task was **blocking the async event loop**, preventing Uvicorn from completing its startup sequence.

**sentiment-service/services/kafka_service.py (BROKEN):**
```python
def consume_transcribed(self) -> Generator[CallTranscribedEvent, None, None]:
    """Synchronous generator - BLOCKS event loop"""
    for message in self.consumer:  # ← Blocking synchronous iteration
        yield event
```

**sentiment-service/main.py:**
```python
async def process_transcriptions():
    while True:
        try:
            for event in kafka_service.consume_transcribed():  # ← Blocking sync call!
                process_single_event(event)
```

When the background task called `kafka_service.consume_transcribed()`, it entered a **synchronous blocking loop** that never yielded control back to the event loop. This prevented Uvicorn from completing its startup, even though the lifespan context manager had yielded.

### The Solution

Converted the synchronous Kafka consumer to an **async generator** pattern, matching the working transcription-service implementation:

**sentiment-service/services/kafka_service.py (FIXED):**
```python
async def consume_transcribed(self) -> AsyncGenerator[CallTranscribedEvent, None]:
    """Async generator - yields control to event loop"""
    try:
        while True:
            # Poll with timeout instead of blocking iteration
            messages = self.consumer.poll(timeout_ms=1000)

            for topic_partition, records in messages.items():
                for message in records:
                    event = CallTranscribedEvent(**message.value)
                    yield event

            # Yield control back to event loop when no messages
            if not messages:
                await asyncio.sleep(0)  # ← Key: yields to event loop
```

**sentiment-service/main.py (FIXED):**
```python
async def process_transcriptions():
    try:
        # Use async for instead of sync for
        async for event in kafka_service.consume_transcribed():
            process_single_event(event)
```

### Why This Works

1. **poll() with timeout**: Instead of blocking indefinitely, polls for messages with 1-second timeout
2. **await asyncio.sleep(0)**: Explicitly yields control to the event loop when no messages
3. **Async generator**: Allows the event loop to interleave other tasks (like Uvicorn startup)

### Result

**After Fix:**
```
INFO:     Started server process [1]
INFO:     Waiting for application startup.
2026-01-01 17:35:12 - main - INFO - Sentiment Service is ready
INFO:     Application startup complete. ✅
INFO:     Uvicorn running on http://0.0.0.0:8000 ✅
curl http://localhost:8083/health → {"status":"healthy"} ✅
```

## Files Modified

### NumPy Fix
- `sentiment-service/requirements.txt:6` - Added `numpy<2,>=1.26.4`

### HTTP Endpoint Fix
- `sentiment-service/services/kafka_service.py:4,7` - Added `asyncio` import, changed `Generator` to `AsyncGenerator`
- `sentiment-service/services/kafka_service.py:60-115` - Converted `consume_transcribed()` to async generator with poll()
- `sentiment-service/main.py:46` - Changed `for event` to `async for event`

## Prevention

**Future Dependency Management**:

1. **Pin All Major Dependencies**:
   ```txt
   # Core ML
   numpy<2,>=1.26.4
   torch==2.2.0
   transformers==4.38.1

   # Data Processing
   pandas==2.x.x
   scikit-learn==1.x.x
   ```

2. **Use Dependabot** or Renovate for automated updates

3. **Add Integration Tests** that would catch this:
   ```python
   def test_http_server_starts():
       response = requests.get("http://localhost:8083/health", timeout=30)
       assert response.status_code == 200
   ```

4. **Docker Health Check** in `docker-compose.yml`:
   ```yaml
   healthcheck:
     test: ["CMD", "curl", "-f", "http://localhost:8000/health"]
     interval: 30s
     timeout: 10s
     retries: 3
     start_period: 60s
   ```

## References

- [NumPy 2.0 Migration Guide](https://numpy.org/devdocs/numpy_2_0_migration_guide.html)
- [PyTorch NumPy Compatibility](https://github.com/pytorch/pytorch/issues/115354)
- [NumPy Version Compatibility](https://github.com/numpy/numpy/releases/tag/v2.0.0)
- PyTorch 2.2.0 requires NumPy < 2.0
- PyTorch 2.5.0+ supports NumPy 2.x

## Lessons Learned

1. **Async/Sync Mismatch**: Mixing synchronous blocking operations in async functions can prevent proper event loop execution
2. **Event Loop Starvation**: Synchronous blocking calls in background tasks can starve the event loop, preventing framework initialization
3. **Pattern Consistency**: When one service works (transcription-service) and another doesn't, compare implementation patterns carefully
4. **Kafka Consumer Patterns**: Use `poll()` with timeout instead of iterator pattern in async contexts
5. **Always Yield**: In async generators, always include `await asyncio.sleep(0)` when no work to yield control

## Prevention

To prevent similar issues in future Python async services:

1. **Code Review Checklist**:
   - All Kafka consumers in async contexts must be async generators
   - Use `poll()` with timeout, not `for message in consumer`
   - Include `await asyncio.sleep(0)` in idle loops

2. **Testing**:
   - Add startup health check tests that verify "Application startup complete" message
   - Test HTTP endpoints as part of service startup verification

3. **Linting**:
   - Consider tools that detect blocking calls in async functions
   - Add type hints to catch `Generator` vs `AsyncGenerator` mismatches
