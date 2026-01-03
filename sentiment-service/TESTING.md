# Testing Guide - Sentiment Service

Quick reference for running and working with tests.

## Quick Start

```bash
# Install test dependencies
pip install -r requirements-test.txt

# Run all tests
pytest

# Run with the test runner script
./run_tests.sh
```

## Test Commands

### Basic Testing

```bash
# Run all tests
pytest

# Run with verbose output
pytest -v

# Run specific test file
pytest tests/test_main.py
pytest tests/test_sentiment.py
pytest tests/test_kafka_service.py

# Run specific test class
pytest tests/test_sentiment.py::TestSentimentAnalyzerInit

# Run specific test function
pytest tests/test_main.py::TestHealthEndpoints::test_health_endpoint_returns_healthy_status

# Run tests matching pattern
pytest -k "health"
pytest -k "sentiment and not escalation"
```

### Coverage

```bash
# Run with coverage report
pytest --cov=. --cov-report=term-missing

# Generate HTML coverage report
pytest --cov=. --cov-report=html
open htmlcov/index.html

# Run coverage with test runner
./run_tests.sh coverage
```

### Test Output

```bash
# Show print statements
pytest -s

# Show local variables on failure
pytest -l

# Stop on first failure
pytest -x

# Run last failed tests only
pytest --lf

# Run failed tests first, then others
pytest --ff
```

### Parallel Execution

```bash
# Install pytest-xdist
pip install pytest-xdist

# Run tests in parallel (4 workers)
pytest -n 4
```

## Test Structure

```
sentiment-service/
├── tests/
│   ├── __init__.py           # Package init
│   ├── conftest.py           # Shared fixtures
│   ├── test_main.py          # API endpoint tests (14 test classes)
│   ├── test_sentiment.py     # Sentiment logic tests (11 test classes)
│   └── test_kafka_service.py # Kafka integration tests (5 test classes)
├── pytest.ini                # Pytest configuration
├── requirements-test.txt     # Test dependencies
└── run_tests.sh             # Test runner script
```

## Test Statistics

- **Total Test Classes**: 30+
- **Total Test Functions**: 80+
- **Test Files**: 3
- **Fixtures**: 9

## Using the Test Runner

```bash
# Run all tests
./run_tests.sh

# Run with coverage
./run_tests.sh coverage

# Run unit tests only
./run_tests.sh unit

# Run integration tests only
./run_tests.sh integration

# Run fast tests (skip slow tests)
./run_tests.sh fast

# Run specific file
./run_tests.sh tests/test_main.py

# Pass any pytest arguments
./run_tests.sh -v -k "health"
```

## Key Test Areas

### 1. API Endpoints (test_main.py)

Tests for FastAPI application:
- Root endpoint (`/`)
- Health checks (`/health`, `/health/ready`, `/health/live`)
- Metrics endpoint (`/metrics`)
- Event processing logic
- Application lifecycle

Example:
```bash
pytest tests/test_main.py::TestHealthEndpoints -v
```

### 2. Sentiment Analysis (test_sentiment.py)

Tests for sentiment analysis logic:
- Model loading (RoBERTa and VADER)
- Text analysis (positive, negative, neutral)
- Segment analysis
- Overall sentiment calculation
- Escalation detection
- Error handling and fallbacks

Example:
```bash
pytest tests/test_sentiment.py::TestAnalyzeText -v
```

### 3. Kafka Integration (test_kafka_service.py)

Tests for Kafka message handling:
- Consumer initialization and event consumption
- Producer initialization and event publishing
- Error handling (connection failures, timeouts)
- Message serialization/deserialization

Example:
```bash
pytest tests/test_kafka_service.py::TestPublishSentiment -v
```

## Fixtures

Common fixtures available in all tests (from `conftest.py`):

```python
# FastAPI test client
def test_example(test_client):
    response = test_client.get("/health")
    assert response.status_code == 200

# Mock sentiment analyzer
def test_example(mock_sentiment_analyzer):
    mock_sentiment_analyzer.analyze_text.return_value = ("positive", 0.8, 0.9, {})

# Sample data
def test_example(sample_transcription_segments):
    assert len(sample_transcription_segments) == 5

# Complete Kafka event
def test_example(sample_call_transcribed_event):
    assert sample_call_transcribed_event.eventType == "CallTranscribed"
```

## Debugging Tests

### See detailed output

```bash
pytest -vv -s
```

### Drop into debugger on failure

```bash
pytest --pdb
```

### Set breakpoint in code

```python
def test_example():
    import pdb; pdb.set_trace()
    # test code
```

### Show warnings

```bash
pytest -v --disable-warnings=False
```

## Writing New Tests

### 1. Choose the right file

- API tests → `test_main.py`
- Business logic → `test_sentiment.py`
- Kafka/external → `test_kafka_service.py`

### 2. Use fixtures

```python
def test_my_feature(mock_sentiment_analyzer, sample_transcription_segments):
    # Use fixtures in your test
    result = analyzer.analyze_segments(sample_transcription_segments)
    assert len(result) > 0
```

### 3. Follow naming conventions

```python
class TestMyFeature:
    """Tests for my feature"""

    def test_feature_does_something(self):
        """Test that feature does something specific"""
        pass
```

### 4. Mock external dependencies

```python
from unittest.mock import Mock, patch

@patch('services.kafka_service.KafkaProducer')
def test_my_kafka_feature(mock_producer):
    mock_producer.return_value = Mock()
    # test code
```

## CI/CD Integration

### GitHub Actions

```yaml
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
      - run: pip install -r requirements-test.txt
      - run: pytest --cov=. --cov-report=xml
      - uses: codecov/codecov-action@v3
```

### Docker

```bash
# Run tests in Docker
docker run --rm -v $(pwd):/app -w /app python:3.11 bash -c \
  "pip install -r requirements-test.txt && pytest"
```

## Common Issues

### Import errors

**Problem**: `ModuleNotFoundError: No module named 'services'`

**Solution**: Run pytest from the sentiment-service directory:
```bash
cd sentiment-service
pytest
```

### Model loading errors

**Problem**: Tests trying to download RoBERTa model

**Solution**: Ensure proper mocking in conftest.py. The test_client fixture should mock model loading.

### Kafka connection errors

**Problem**: Tests trying to connect to Kafka

**Solution**: Verify KafkaConsumer and KafkaProducer are mocked:
```python
@patch('services.kafka_service.KafkaConsumer')
def test_example(mock_consumer):
    # test code
```

### Async warnings

**Problem**: Async-related warnings

**Solution**: Install pytest-asyncio:
```bash
pip install pytest-asyncio
```

## Best Practices

1. **Keep tests fast** - Mock external dependencies
2. **Test one thing** - Each test should verify one behavior
3. **Use descriptive names** - Test names should describe what they test
4. **Arrange-Act-Assert** - Follow AAA pattern
5. **Don't test implementation** - Test behavior, not internals
6. **Use fixtures** - Share setup code via fixtures
7. **Clean up** - Ensure tests don't leave side effects

## Resources

- [Full Test Documentation](tests/README.md)
- [Pytest Documentation](https://docs.pytest.org/)
- [FastAPI Testing](https://fastapi.tiangolo.com/tutorial/testing/)
- [Testing Best Practices](https://docs.pytest.org/en/stable/goodpractices.html)
