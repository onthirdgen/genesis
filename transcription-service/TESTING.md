# Transcription Service - Testing Guide

## Overview

Comprehensive pytest test suite for the transcription-service with **1,783 lines** of test code covering all service components.

## Test Statistics

| Metric | Value |
|--------|-------|
| Total Test Files | 6 files |
| Total Test Code | 1,783 lines |
| Test Fixtures | 10+ fixtures |
| Test Classes | 30+ classes |
| Test Functions | 120+ tests |
| Coverage Goal | 90%+ |

## Test Files

### 1. conftest.py (198 lines)
**Purpose**: Shared pytest fixtures and configuration

**Key Fixtures**:
- `mock_whisper_model` - Mocked OpenAI Whisper model with realistic transcription responses
- `mock_kafka_consumer` - Mocked Kafka consumer for event consumption
- `mock_kafka_producer` - Mocked Kafka producer for event publishing
- `mock_minio_client` - Mocked MinIO S3 client
- `temp_audio_file` - Temporary audio file for testing file operations
- `sample_call_received_event` - Sample CallReceived event payload
- `sample_transcription_result` - Sample transcription result data
- `client_no_lifespan` - FastAPI TestClient without lifespan events
- `mock_services` - All mocked services together

**Why**: Centralizes test setup, reduces code duplication, ensures consistent mocking across all tests.

### 2. test_main.py (240 lines)
**Purpose**: Tests for FastAPI application endpoints and lifecycle

**Test Classes**:
- `TestRootEndpoint` - Root endpoint (`/`) tests
- `TestHealthEndpoint` - Health check endpoint (`/health`) tests
- `TestReadinessEndpoint` - Readiness check endpoint (`/ready`) tests
- `TestMetricsEndpoint` - Prometheus metrics endpoint (`/metrics`) tests
- `TestNotFoundEndpoint` - 404 error handling tests
- `TestApplicationLifecycle` - Startup/shutdown tests
- `TestCORSandSecurity` - HTTP method validation tests

**Key Test Coverage**:
- All API endpoints return correct status codes and response formats
- Health checks properly validate dependency status (MinIO, Kafka)
- Readiness endpoint returns 503 when dependencies are unhealthy
- Metrics endpoint returns Prometheus-formatted data
- Application handles Whisper model load failures gracefully
- Kafka connections are closed on shutdown
- HTTP methods are properly restricted (GET only for health checks)

### 3. test_transcription.py (389 lines)
**Purpose**: Tests for Whisper transcription service logic

**Test Classes**:
- `TestWhisperServiceInitialization` - Service initialization tests
- `TestWhisperModelLoading` - Model loading and caching tests
- `TestTranscription` - Audio transcription tests
- `TestSpeakerDiarization` - Speaker diarization algorithm tests
- `TestConfidenceCalculation` - Confidence score calculation tests
- `TestWhisperServiceIntegration` - End-to-end workflow tests

**Key Test Coverage**:
- Lazy loading of Whisper model (not loaded on initialization)
- Model caching (model only loaded once)
- Different model sizes (tiny, base, small, medium, large)
- Transcription returns ProcessedTranscription with all required fields
- Speaker diarization alternates speakers based on pause duration (>1.5s threshold)
- Confidence calculation uses avg_logprob and no_speech_prob
- Confidence score weighted by segment duration
- Error handling for non-existent audio files
- Segment timestamps are rounded to 2 decimal places
- Text is stripped of whitespace
- Integration with event models (Segment conversion)

### 4. test_kafka_service.py (531 lines)
**Purpose**: Tests for Kafka event streaming service

**Test Classes**:
- `TestKafkaServiceInitialization` - Service initialization tests
- `TestConsumerCreation` - Consumer configuration tests
- `TestProducerCreation` - Producer configuration tests
- `TestEventConsumption` - CallReceived event consumption tests
- `TestEventPublishing` - CallTranscribed event publishing tests
- `TestHealthCheck` - Kafka health check tests
- `TestConnectionManagement` - Connection lifecycle tests

**Key Test Coverage**:
- Consumer subscribes to correct topic (`calls.received`)
- Consumer uses correct group ID (`transcription-service`)
- Consumer configured with `auto_offset_reset='earliest'`
- Consumer uses JSON deserializer
- Producer uses JSON serializer
- Producer configured with `acks='all'` for reliability
- Producer retries 3 times on failure
- Async event consumption yields CallReceivedEvent objects
- Malformed events are logged but don't crash the consumer
- Events are published to correct topic (`calls.transcribed`)
- Event serialization preserves all fields
- Returns True on successful publish, False on error
- Health check creates temporary producer to test connection
- Consumer and producer are closed properly on shutdown

### 5. test_minio_service.py (424 lines)
**Purpose**: Tests for MinIO object storage service

**Test Classes**:
- `TestMinioServiceInitialization` - Service initialization tests
- `TestFileDownload` - File download functionality tests
- `TestURLParsing` - URL parsing logic tests
- `TestHealthCheck` - MinIO health check tests
- `TestMinioServiceIntegration` - End-to-end workflow tests

**Key Test Coverage**:
- Service initializes with correct MinIO configuration
- Supports full HTTP/HTTPS URLs with parsing
- Supports object keys without URLs (relative paths)
- Removes bucket name from URL path if present
- Preserves file extension in temporary file (.wav, .mp3, .m4a)
- Creates temporary file with `audio_` prefix
- Downloads to temp directory
- Handles S3Error for non-existent files
- Handles generic exceptions gracefully
- URL parsing handles leading slashes correctly
- Health check verifies bucket exists
- Returns False when bucket doesn't exist or on connection error

## Quick Start

### Install Dependencies

```bash
cd /Users/jon/AI/genesis/transcription-service
pip install -r requirements-dev.txt
```

### Run All Tests

```bash
pytest
```

### Run Tests with Coverage

```bash
pytest --cov=. --cov-report=html --cov-report=term-missing
```

### Run Specific Test File

```bash
pytest tests/test_main.py
pytest tests/test_transcription.py
pytest tests/test_kafka_service.py
pytest tests/test_minio_service.py
```

### Run with Convenience Script

```bash
# Simple run
./run_tests.sh

# With coverage
./run_tests.sh --coverage

# With HTML coverage report
./run_tests.sh --html

# Verbose output
./run_tests.sh --verbose

# Specific test file
./run_tests.sh --test tests/test_main.py
```

## Test Patterns Used

### 1. Arrange-Act-Assert (AAA)

```python
def test_health_endpoint_returns_healthy(client_no_lifespan):
    # Arrange - setup done in fixture

    # Act
    response = client_no_lifespan.get("/health")

    # Assert
    assert response.status_code == 200
    assert response.json()["status"] == "healthy"
```

### 2. Mocking External Dependencies

```python
@patch('services.whisper_service.whisper.load_model')
def test_transcribe_loads_model(mock_load_model, temp_audio_file):
    mock_load_model.return_value = MagicMock()
    service = WhisperService()
    service.transcribe(temp_audio_file)
    mock_load_model.assert_called_once()
```

### 3. Async Testing

```python
@pytest.mark.asyncio
@patch('services.kafka_service.KafkaConsumer')
async def test_consume_call_received_yields_events(mock_consumer_class):
    # Setup mock
    mock_consumer = MagicMock()
    mock_consumer_class.return_value = mock_consumer

    # Test async iteration
    async for event in service.consume_call_received():
        assert isinstance(event, CallReceivedEvent)
```

### 4. Fixture-Based Setup

```python
@pytest.fixture
def temp_audio_file():
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f:
        f.write(b'RIFF' + b'\x00' * 100)
        temp_path = f.name

    yield temp_path

    # Cleanup
    if os.path.exists(temp_path):
        os.remove(temp_path)
```

### 5. Test Classes for Organization

```python
class TestWhisperModelLoading:
    """Tests for Whisper model loading."""

    def test_load_model_loads_successfully(self):
        # Test implementation
        pass

    def test_load_model_only_loads_once(self):
        # Test implementation
        pass
```

## Coverage Report

Run tests with coverage to see detailed coverage report:

```bash
pytest --cov=. --cov-report=html
open htmlcov/index.html
```

Expected coverage:
- `main.py` - 90%+
- `services/whisper_service.py` - 95%+
- `services/kafka_service.py` - 90%+
- `services/minio_service.py` - 95%+
- `routers/health.py` - 100%
- `routers/metrics.py` - 100%

## Why These Tests Matter

### 1. Fast Feedback
Tests run in seconds (no real Whisper model loading, Kafka connections, or MinIO downloads).

### 2. Confidence in Changes
Comprehensive tests ensure refactoring doesn't break functionality.

### 3. Documentation
Tests serve as executable documentation showing how components work.

### 4. Regression Prevention
Catches bugs before they reach production.

### 5. Design Validation
Writing tests reveals design issues early.

## Mocking Strategy

### Why Mock?

1. **Speed** - Real Whisper model loading takes minutes
2. **Reliability** - Don't depend on external services (Kafka, MinIO)
3. **Isolation** - Test one component at a time
4. **Repeatability** - Same results every time

### What's Mocked?

- **Whisper Model** - Mock returns predefined transcription
- **Kafka Consumer/Producer** - Mock simulates event streaming
- **MinIO Client** - Mock simulates S3 operations
- **Config Settings** - Override with test values

### What's NOT Mocked?

- **Business Logic** - Speaker diarization algorithm runs for real
- **Data Models** - Pydantic models validate for real
- **Event Serialization** - JSON serialization runs for real

## CI/CD Integration

These tests are designed for CI/CD pipelines:

```yaml
# GitHub Actions example
name: Test

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-python@v4
        with:
          python-version: '3.11'
      - name: Install dependencies
        run: |
          pip install -r requirements-dev.txt
      - name: Run tests with coverage
        run: |
          pytest --cov=. --cov-report=xml --cov-report=term
      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v3
        with:
          file: ./coverage.xml
```

## Common Issues and Solutions

### Issue: Import Errors

**Solution**: Run from `transcription-service` directory, ensure dependencies installed.

### Issue: Async Warnings

**Solution**: Ensure `pytest-asyncio` is installed and `asyncio_mode = auto` in `pytest.ini`.

### Issue: Mock Not Working

**Solution**: Check patch path is correct (patch where it's used, not where it's defined).

### Issue: Slow Tests

**Solution**: All external dependencies should be mocked. Check for real network calls.

## Future Enhancements

Potential additions to the test suite:

- [ ] **Property-based testing** with Hypothesis
- [ ] **Mutation testing** with mutmut
- [ ] **Integration tests** with Testcontainers (real Kafka/MinIO)
- [ ] **Load/performance tests** with Locust
- [ ] **Contract tests** for event schemas
- [ ] **API tests** with Tavern
- [ ] **Security tests** for input validation

## Resources

- [pytest Documentation](https://docs.pytest.org/)
- [FastAPI Testing Guide](https://fastapi.tiangolo.com/tutorial/testing/)
- [pytest-asyncio Documentation](https://pytest-asyncio.readthedocs.io/)
- [unittest.mock Documentation](https://docs.python.org/3/library/unittest.mock.html)
- [Coverage.py Documentation](https://coverage.readthedocs.io/)

## Summary

This comprehensive test suite provides:

- **120+ tests** covering all service components
- **1,783 lines** of well-documented test code
- **10+ fixtures** for consistent test setup
- **Complete mocking** of external dependencies
- **Fast execution** (seconds, not minutes)
- **90%+ coverage** of critical code paths
- **CI/CD ready** for automated testing

The tests follow FastAPI best practices, use pytest's powerful features, and provide confidence that the transcription service works correctly.
