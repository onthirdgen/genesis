# Sentiment Service - Test Suite

Comprehensive pytest test suite for the sentiment-service (Python/FastAPI).

## Test Structure

```
tests/
├── __init__.py              # Test package initialization
├── conftest.py              # Shared pytest fixtures
├── test_main.py             # FastAPI application and endpoint tests
├── test_sentiment.py        # Sentiment analysis logic tests
└── test_kafka_service.py    # Kafka integration tests
```

## Test Coverage

### test_main.py - API Endpoints
- **Root Endpoint**: Service information
- **Health Checks**: `/health`, `/health/live`, `/health/ready`
- **Metrics**: Prometheus metrics endpoint
- **Event Processing**: CallTranscribed event processing logic
- **Lifecycle**: Application startup/shutdown

### test_sentiment.py - Sentiment Analysis
- **Initialization**: SentimentAnalyzer setup
- **Model Loading**: RoBERTa and VADER model loading
- **Text Analysis**: Individual text sentiment analysis
- **Segment Analysis**: Multi-segment analysis
- **Overall Sentiment**: Weighted sentiment calculation
- **Escalation Detection**: Sentiment drop detection
- **Fallback Logic**: RoBERTa to VADER fallback

### test_kafka_service.py - Kafka Integration
- **Consumer**: Kafka consumer initialization and event consumption
- **Producer**: Kafka producer initialization and event publishing
- **Error Handling**: Connection failures, timeouts, invalid messages
- **Serialization**: Event serialization/deserialization

## Running Tests

### Install Test Dependencies

```bash
# From sentiment-service directory
pip install -r requirements-test.txt
```

### Run All Tests

```bash
pytest
```

### Run Specific Test File

```bash
pytest tests/test_main.py
pytest tests/test_sentiment.py
pytest tests/test_kafka_service.py
```

### Run Specific Test Class

```bash
pytest tests/test_sentiment.py::TestSentimentAnalyzerInit
pytest tests/test_main.py::TestHealthEndpoints
```

### Run Specific Test

```bash
pytest tests/test_sentiment.py::TestAnalyzeText::test_analyze_text_empty_string_returns_neutral
```

### Run with Coverage

```bash
# Install pytest-cov if not already installed
pip install pytest-cov

# Run with coverage report
pytest --cov=. --cov-report=term-missing --cov-report=html

# View HTML coverage report
open htmlcov/index.html
```

### Run with Verbose Output

```bash
pytest -v
```

### Run with Test Output

```bash
pytest -s
```

### Run Only Fast Unit Tests

```bash
pytest -m unit
```

## Test Fixtures

Fixtures are defined in `conftest.py` and are automatically available to all tests.

### Available Fixtures

- **test_client**: FastAPI TestClient with mocked dependencies
- **mock_sentiment_analyzer**: Mock SentimentAnalyzer instance
- **mock_kafka_service**: Mock KafkaService instance
- **sample_transcription_segments**: Sample transcription data
- **sample_call_transcribed_event**: Sample CallTranscribed Kafka event
- **sample_sentiment_segments**: Sample sentiment analysis results
- **mock_roberta_pipeline_response**: Mock RoBERTa model output
- **mock_vader_scores**: Mock VADER sentiment scores

### Using Fixtures

```python
def test_example(sample_transcription_segments):
    """Test using fixture"""
    assert len(sample_transcription_segments) == 5
```

## Mocking Strategy

### External Dependencies

All external dependencies are mocked to ensure fast, isolated unit tests:

- **RoBERTa Model**: Mocked using `unittest.mock.patch`
- **VADER**: Uses real implementation (lightweight)
- **Kafka**: Mocked KafkaConsumer and KafkaProducer
- **Transformers Pipeline**: Mocked to avoid model downloads

### Example Mock Usage

```python
from unittest.mock import Mock, patch

@patch('services.kafka_service.KafkaProducer')
def test_example(mock_producer):
    mock_producer.return_value = Mock()
    # Test code here
```

## Test Categories

Tests are organized into categories using pytest markers:

- **Unit Tests**: Fast tests with no external dependencies
- **Integration Tests**: Tests requiring external services (Kafka, etc.)
- **Slow Tests**: Long-running tests (model loading, etc.)

### Running by Category

```bash
# Run only unit tests
pytest -m unit

# Run only integration tests
pytest -m integration

# Skip slow tests
pytest -m "not slow"
```

## Common Test Patterns

### Testing FastAPI Endpoints

```python
def test_endpoint(test_client):
    response = test_client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "healthy"
```

### Testing Event Processing

```python
@patch('main.kafka_service')
@patch('main.sentiment_analyzer')
def test_process_event(mock_analyzer, mock_kafka, sample_event):
    mock_analyzer.analyze_segments.return_value = []
    process_single_event(sample_event)
    mock_kafka.publish_sentiment.assert_called_once()
```

### Testing Sentiment Analysis

```python
def test_sentiment_analysis():
    analyzer = SentimentAnalyzer()
    analyzer.use_vader_fallback = True

    sentiment, score, confidence, emotions = analyzer.analyze_text("Great!")

    assert sentiment == "positive"
    assert score > 0
```

## Continuous Integration

These tests are designed to run in CI/CD pipelines:

```yaml
# Example GitHub Actions workflow
- name: Install dependencies
  run: pip install -r requirements-test.txt

- name: Run tests
  run: pytest --cov=. --cov-report=xml

- name: Upload coverage
  uses: codecov/codecov-action@v3
```

## Troubleshooting

### Import Errors

If you get import errors, ensure you're running pytest from the sentiment-service directory:

```bash
cd sentiment-service
pytest
```

### Model Loading Errors

Tests mock model loading by default. If you see model-related errors, ensure mocks are properly configured in conftest.py.

### Kafka Connection Errors

Tests should NOT connect to real Kafka. If you see connection errors, verify that KafkaConsumer/KafkaProducer are mocked.

### Async Test Warnings

If you see async-related warnings, ensure pytest-asyncio is installed:

```bash
pip install pytest-asyncio
```

## Adding New Tests

### 1. Choose the Correct File

- API/endpoint tests → `test_main.py`
- Sentiment logic tests → `test_sentiment.py`
- Kafka integration tests → `test_kafka_service.py`

### 2. Use Existing Fixtures

Check `conftest.py` for available fixtures before creating new ones.

### 3. Follow Naming Conventions

- Test files: `test_*.py`
- Test classes: `Test*`
- Test functions: `test_*`

### 4. Add Docstrings

```python
def test_example():
    """Test that example functionality works correctly"""
    pass
```

### 5. Use Descriptive Names

```python
# Good
def test_analyze_text_returns_neutral_for_empty_string():
    pass

# Bad
def test_1():
    pass
```

## Best Practices

1. **Isolation**: Each test should be independent
2. **Mocking**: Mock external dependencies (Kafka, models)
3. **Fixtures**: Use fixtures for common setup
4. **Assertions**: Use clear, specific assertions
5. **Documentation**: Add docstrings to test functions
6. **Coverage**: Aim for >80% code coverage
7. **Speed**: Keep tests fast (mock heavy operations)

## Resources

- [Pytest Documentation](https://docs.pytest.org/)
- [FastAPI Testing](https://fastapi.tiangolo.com/tutorial/testing/)
- [unittest.mock](https://docs.python.org/3/library/unittest.mock.html)
- [pytest-asyncio](https://pytest-asyncio.readthedocs.io/)
