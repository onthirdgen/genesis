# Transcription Service Tests

Comprehensive pytest test suite for the transcription-service (Python/FastAPI).

## Overview

This test suite provides comprehensive coverage for the transcription service, including:

- **API Endpoint Tests** (`test_main.py`) - FastAPI endpoints, health checks, metrics
- **Transcription Logic Tests** (`test_transcription.py`) - Whisper model, speaker diarization, confidence calculation
- **Kafka Service Tests** (`test_kafka_service.py`) - Event consumption/production, error handling
- **MinIO Service Tests** (`test_minio_service.py`) - File download, URL parsing, health checks
- **Fixtures** (`conftest.py`) - Shared test fixtures and mocks

## Setup

### Install Test Dependencies

```bash
# From the transcription-service directory
pip install -r requirements-dev.txt
```

### Test Dependencies

- `pytest` - Testing framework
- `pytest-asyncio` - Async test support
- `pytest-cov` - Coverage reporting
- `pytest-mock` - Enhanced mocking
- `httpx` - Async HTTP client for FastAPI testing

## Running Tests

### Run All Tests

```bash
# From the transcription-service directory
pytest
```

### Run with Coverage

```bash
pytest --cov=. --cov-report=html --cov-report=term
```

This generates:
- Terminal coverage report
- HTML coverage report in `htmlcov/index.html`

### Run Specific Test Files

```bash
# Run only API endpoint tests
pytest tests/test_main.py

# Run only transcription tests
pytest tests/test_transcription.py

# Run only Kafka tests
pytest tests/test_kafka_service.py

# Run only MinIO tests
pytest tests/test_minio_service.py
```

### Run Specific Test Classes

```bash
# Run health endpoint tests only
pytest tests/test_main.py::TestHealthEndpoint

# Run Whisper model loading tests
pytest tests/test_transcription.py::TestWhisperModelLoading
```

### Run Specific Test Functions

```bash
# Run a single test
pytest tests/test_main.py::TestHealthEndpoint::test_health_endpoint_returns_healthy
```

### Run Tests by Marker

```bash
# Run only unit tests
pytest -m unit

# Run only integration tests
pytest -m integration

# Run only async tests
pytest -m asyncio

# Skip slow tests
pytest -m "not slow"
```

### Verbose Output

```bash
# Show detailed output
pytest -v

# Show extra verbose output (test names + output)
pytest -vv

# Show print statements
pytest -s
```

### Parallel Execution (if pytest-xdist installed)

```bash
# Run tests in parallel (faster)
pip install pytest-xdist
pytest -n auto
```

## Test Structure

### conftest.py

Shared fixtures including:

- `mock_whisper_model` - Mocked Whisper model with realistic responses
- `mock_kafka_consumer` - Mocked Kafka consumer
- `mock_kafka_producer` - Mocked Kafka producer
- `mock_minio_client` - Mocked MinIO client
- `temp_audio_file` - Temporary audio file for testing
- `sample_call_received_event` - Sample event data
- `client_no_lifespan` - FastAPI test client without background tasks
- `mock_services` - All mocked services together

### test_main.py

Tests for FastAPI application:

- Root endpoint (`/`)
- Health check endpoint (`/health`)
- Readiness check endpoint (`/ready`)
- Metrics endpoint (`/metrics`)
- 404 handling
- Application lifecycle (startup/shutdown)
- HTTP method validation

### test_transcription.py

Tests for Whisper transcription service:

- Service initialization
- Model loading (lazy loading, caching)
- Audio transcription (with mocked Whisper)
- Speaker diarization logic
- Confidence score calculation
- Error handling
- Integration workflows

### test_kafka_service.py

Tests for Kafka service:

- Consumer creation and configuration
- Producer creation and configuration
- Event consumption (CallReceived)
- Event publishing (CallTranscribed)
- Error handling (Kafka errors, parse errors)
- Health checks
- Connection lifecycle management

### test_minio_service.py

Tests for MinIO service:

- Service initialization
- File download from MinIO
- URL parsing (HTTP/HTTPS URLs, object keys)
- File extension preservation
- Temporary file handling
- Error handling (S3 errors, generic errors)
- Health checks

## Coverage Goals

Target coverage: **90%+**

Current coverage by module:
- `main.py` - API endpoints and lifespan
- `services/whisper_service.py` - Transcription logic
- `services/kafka_service.py` - Event streaming
- `services/minio_service.py` - Object storage
- `routers/health.py` - Health checks
- `routers/metrics.py` - Prometheus metrics

## Mocking Strategy

All external dependencies are mocked to ensure:

1. **Fast execution** - No real Whisper model loading, Kafka connections, or MinIO downloads
2. **Reliability** - Tests don't depend on external services
3. **Isolation** - Each test is independent
4. **Repeatability** - Consistent results across runs

### Mocked Components

- **Whisper Model** - Returns predefined transcription results
- **Kafka Consumer/Producer** - Returns/accepts events without real Kafka
- **MinIO Client** - Simulates file downloads without real S3
- **Config Settings** - Uses test-specific values

## Best Practices

### Writing New Tests

1. **Use descriptive test names** - `test_health_endpoint_returns_healthy_status`
2. **Follow AAA pattern** - Arrange, Act, Assert
3. **One assertion per test** (when possible)
4. **Use fixtures for setup** - Defined in `conftest.py`
5. **Mock external dependencies** - Never call real services
6. **Add docstrings** - Explain what the test validates

Example:

```python
def test_health_endpoint_returns_healthy(client_no_lifespan):
    \"\"\"Test that health endpoint returns healthy status.\"\"\"
    # Arrange - setup done in fixture

    # Act
    response = client_no_lifespan.get("/health")

    # Assert
    assert response.status_code == 200
    assert response.json()["status"] == "healthy"
```

### Async Tests

For async functions, use `@pytest.mark.asyncio`:

```python
@pytest.mark.asyncio
async def test_async_function():
    result = await some_async_function()
    assert result is not None
```

### Parametrized Tests

For testing multiple inputs:

```python
@pytest.mark.parametrize("model_size,expected", [
    ("tiny", "tiny"),
    ("base", "base"),
    ("small", "small"),
])
def test_model_sizes(model_size, expected):
    # Test logic
    pass
```

## Continuous Integration

These tests are designed to run in CI/CD pipelines:

```yaml
# Example GitHub Actions
- name: Run tests
  run: |
    pip install -r requirements-dev.txt
    pytest --cov=. --cov-report=xml
```

## Troubleshooting

### Import Errors

If you get import errors, ensure:

1. You're in the `transcription-service` directory
2. Dependencies are installed: `pip install -r requirements-dev.txt`
3. Python path is set correctly (conftest.py handles this)

### Async Warnings

If you see async warnings, ensure `pytest-asyncio` is installed and `asyncio_mode = auto` is set in `pytest.ini`.

### Mock Issues

If mocks aren't working:

1. Check that patches target the correct module path
2. Ensure patches are applied before importing the module
3. Use `conftest.py` fixtures for consistent mocking

## Future Enhancements

Potential test improvements:

- [ ] Add performance/load tests
- [ ] Add contract tests for event schemas
- [ ] Add tests for audio file format validation
- [ ] Add tests for Prometheus metrics increments
- [ ] Add integration tests with Testcontainers (real Kafka/MinIO)
- [ ] Add mutation testing (mutmut)
- [ ] Add property-based testing (hypothesis)

## Resources

- [pytest documentation](https://docs.pytest.org/)
- [FastAPI testing guide](https://fastapi.tiangolo.com/tutorial/testing/)
- [pytest-asyncio documentation](https://pytest-asyncio.readthedocs.io/)
- [unittest.mock documentation](https://docs.python.org/3/library/unittest.mock.html)
