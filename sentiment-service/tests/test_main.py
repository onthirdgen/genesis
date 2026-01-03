"""
Tests for main FastAPI application endpoints
"""
import pytest
from unittest.mock import Mock, patch, MagicMock
from fastapi import status
from uuid import uuid4

from models.events import SentimentAnalyzedEvent


class TestRootEndpoint:
    """Tests for root endpoint"""

    def test_root_endpoint_returns_service_info(self, test_client):
        """Test that root endpoint returns service information"""
        response = test_client.get("/")

        assert response.status_code == status.HTTP_200_OK
        data = response.json()
        assert data["service"] == "sentiment-service"
        assert data["version"] == "1.0.0"
        assert data["status"] == "running"
        assert "description" in data


class TestHealthEndpoints:
    """Tests for health check endpoints"""

    def test_health_endpoint_returns_healthy_status(self, test_client):
        """Test basic health endpoint"""
        response = test_client.get("/health")

        assert response.status_code == status.HTTP_200_OK
        data = response.json()
        assert data["status"] == "healthy"
        assert data["service"] == "sentiment-service"
        assert data["version"] == "1.0.0"
        assert "uptime_seconds" in data
        assert isinstance(data["uptime_seconds"], (int, float))
        assert data["uptime_seconds"] >= 0

    def test_health_endpoint_includes_model_status(self, test_client):
        """Test that health endpoint includes model loaded status"""
        response = test_client.get("/health")

        assert response.status_code == status.HTTP_200_OK
        data = response.json()
        assert "model_loaded" in data
        assert isinstance(data["model_loaded"], bool)

    def test_health_endpoint_includes_kafka_status(self, test_client):
        """Test that health endpoint includes Kafka connection status"""
        response = test_client.get("/health")

        assert response.status_code == status.HTTP_200_OK
        data = response.json()
        assert "kafka_connected" in data
        assert data["kafka_connected"] is not None

    def test_liveness_endpoint_returns_alive(self, test_client):
        """Test liveness probe endpoint"""
        response = test_client.get("/health/live")

        assert response.status_code == status.HTTP_200_OK
        data = response.json()
        assert data["status"] == "alive"

    def test_readiness_endpoint_structure(self, test_client):
        """Test readiness probe endpoint returns correct structure"""
        response = test_client.get("/health/ready")

        assert response.status_code == status.HTTP_200_OK
        data = response.json()
        assert "ready" in data
        assert "checks" in data
        assert isinstance(data["ready"], bool)
        assert isinstance(data["checks"], dict)

    def test_readiness_endpoint_checks_all_dependencies(self, test_client):
        """Test that readiness endpoint checks all required dependencies"""
        response = test_client.get("/health/ready")

        assert response.status_code == status.HTTP_200_OK
        data = response.json()
        checks = data["checks"]
        assert "model_loaded" in checks
        assert "kafka_consumer" in checks
        assert "kafka_producer" in checks


class TestMetricsEndpoint:
    """Tests for Prometheus metrics endpoint"""

    def test_metrics_endpoint_returns_prometheus_format(self, test_client):
        """Test that metrics endpoint returns Prometheus text format"""
        response = test_client.get("/metrics")

        assert response.status_code == status.HTTP_200_OK
        assert "text/plain" in response.headers["content-type"]

    def test_metrics_endpoint_includes_standard_metrics(self, test_client):
        """Test that metrics endpoint includes expected metric types"""
        response = test_client.get("/metrics")

        assert response.status_code == status.HTTP_200_OK
        content = response.text

        # Check for presence of metric names (not values, as those may be 0)
        assert "sentiment_analyses_total" in content or "# HELP" in content
        assert "python_info" in content or "process_" in content


class TestProcessSingleEvent:
    """Tests for event processing logic"""

    @patch('main.kafka_service')
    @patch('main.sentiment_analyzer')
    def test_process_single_event_success(
        self,
        mock_analyzer,
        mock_kafka,
        sample_call_transcribed_event,
        sample_sentiment_segments
    ):
        """Test successful processing of a CallTranscribed event"""
        from main import process_single_event

        # Setup mocks
        mock_analyzer.analyze_segments.return_value = sample_sentiment_segments
        mock_analyzer.calculate_overall_sentiment.return_value = ("negative", -0.2)
        mock_analyzer.detect_escalation.return_value = (True, {
            "maxDrop": 0.8,
            "startTime": 5.0,
            "endTime": 20.0
        })
        mock_analyzer.use_vader_fallback = False
        mock_kafka.publish_sentiment.return_value = True

        # Process event
        process_single_event(sample_call_transcribed_event)

        # Verify analyzer was called
        mock_analyzer.analyze_segments.assert_called_once()
        segments_arg = mock_analyzer.analyze_segments.call_args[0][0]
        assert len(segments_arg) == len(sample_call_transcribed_event.payload.segments)

        # Verify overall sentiment calculation
        mock_analyzer.calculate_overall_sentiment.assert_called_once()

        # Verify escalation detection
        mock_analyzer.detect_escalation.assert_called_once()

        # Verify event was published
        mock_kafka.publish_sentiment.assert_called_once()
        published_event = mock_kafka.publish_sentiment.call_args[0][0]
        assert isinstance(published_event, SentimentAnalyzedEvent)
        assert published_event.aggregateId == sample_call_transcribed_event.aggregateId
        assert published_event.payload.overallSentiment == "negative"
        assert published_event.payload.escalationDetected is True

    @patch('main.kafka_service')
    @patch('main.sentiment_analyzer')
    def test_process_single_event_with_empty_segments(
        self,
        mock_analyzer,
        mock_kafka,
        sample_call_transcribed_event
    ):
        """Test processing event with no segments"""
        from main import process_single_event

        # Create event with empty segments
        sample_call_transcribed_event.payload.segments = []

        # Process event - should return early without processing
        process_single_event(sample_call_transcribed_event)

        # Verify analyzer was NOT called
        mock_analyzer.analyze_segments.assert_not_called()
        mock_kafka.publish_sentiment.assert_not_called()

    @patch('main.kafka_service')
    @patch('main.sentiment_analyzer')
    def test_process_single_event_handles_analyzer_error(
        self,
        mock_analyzer,
        mock_kafka,
        sample_call_transcribed_event
    ):
        """Test that processing handles analyzer errors gracefully"""
        from main import process_single_event

        # Setup mock to raise exception
        mock_analyzer.analyze_segments.side_effect = Exception("Model error")

        # Process event - should not raise exception
        process_single_event(sample_call_transcribed_event)

        # Verify kafka publish was NOT called
        mock_kafka.publish_sentiment.assert_not_called()

    @patch('main.kafka_service')
    @patch('main.sentiment_analyzer')
    def test_process_single_event_handles_kafka_publish_failure(
        self,
        mock_analyzer,
        mock_kafka,
        sample_call_transcribed_event,
        sample_sentiment_segments
    ):
        """Test handling of Kafka publish failures"""
        from main import process_single_event

        # Setup mocks
        mock_analyzer.analyze_segments.return_value = sample_sentiment_segments
        mock_analyzer.calculate_overall_sentiment.return_value = ("positive", 0.5)
        mock_analyzer.detect_escalation.return_value = (False, {})
        mock_kafka.publish_sentiment.return_value = False  # Simulate failure

        # Process event - should not raise exception
        process_single_event(sample_call_transcribed_event)

        # Verify publish was attempted
        mock_kafka.publish_sentiment.assert_called_once()

    @patch('main.kafka_service')
    @patch('main.sentiment_analyzer')
    def test_process_single_event_metadata_includes_model_info(
        self,
        mock_analyzer,
        mock_kafka,
        sample_call_transcribed_event,
        sample_sentiment_segments
    ):
        """Test that published event includes model metadata"""
        from main import process_single_event
        from config import settings

        # Setup mocks
        mock_analyzer.analyze_segments.return_value = sample_sentiment_segments
        mock_analyzer.calculate_overall_sentiment.return_value = ("neutral", 0.0)
        mock_analyzer.detect_escalation.return_value = (False, {})
        mock_analyzer.use_vader_fallback = True
        mock_kafka.publish_sentiment.return_value = True

        # Process event
        process_single_event(sample_call_transcribed_event)

        # Verify metadata
        published_event = mock_kafka.publish_sentiment.call_args[0][0]
        assert published_event.metadata["service"] == settings.service_name
        assert published_event.metadata["modelName"] == settings.model_name
        assert published_event.metadata["usedFallback"] is True

    @patch('main.kafka_service')
    @patch('main.sentiment_analyzer')
    def test_process_single_event_preserves_correlation_id(
        self,
        mock_analyzer,
        mock_kafka,
        sample_call_transcribed_event,
        sample_sentiment_segments
    ):
        """Test that correlation ID is preserved for tracing"""
        from main import process_single_event

        # Setup mocks
        mock_analyzer.analyze_segments.return_value = sample_sentiment_segments
        mock_analyzer.calculate_overall_sentiment.return_value = ("positive", 0.3)
        mock_analyzer.detect_escalation.return_value = (False, {})
        mock_kafka.publish_sentiment.return_value = True

        # Process event
        process_single_event(sample_call_transcribed_event)

        # Verify correlation ID is preserved
        published_event = mock_kafka.publish_sentiment.call_args[0][0]
        assert published_event.correlationId == sample_call_transcribed_event.correlationId

    @patch('main.kafka_service')
    @patch('main.sentiment_analyzer')
    def test_process_single_event_sets_causation_id(
        self,
        mock_analyzer,
        mock_kafka,
        sample_call_transcribed_event,
        sample_sentiment_segments
    ):
        """Test that causation ID is set to source event ID"""
        from main import process_single_event

        # Setup mocks
        mock_analyzer.analyze_segments.return_value = sample_sentiment_segments
        mock_analyzer.calculate_overall_sentiment.return_value = ("neutral", 0.1)
        mock_analyzer.detect_escalation.return_value = (False, {})
        mock_kafka.publish_sentiment.return_value = True

        # Process event
        process_single_event(sample_call_transcribed_event)

        # Verify causation ID
        published_event = mock_kafka.publish_sentiment.call_args[0][0]
        assert published_event.causationId == sample_call_transcribed_event.eventId


class TestLifecycleManagement:
    """Tests for application lifecycle"""

    @patch('main.kafka_service')
    @patch('main.sentiment_analyzer')
    @patch('main.process_transcriptions')
    def test_app_startup_loads_model(self, mock_process, mock_analyzer, mock_kafka):
        """Test that model is loaded on startup"""
        from main import app
        from fastapi.testclient import TestClient

        with TestClient(app):
            # Startup should have been triggered
            pass

        # Note: Due to lifespan context manager, we can't directly test
        # this without more complex setup. This is a placeholder for
        # integration testing.

    @patch('main.kafka_service')
    @patch('main.sentiment_analyzer')
    @patch('main.process_transcriptions')
    def test_app_startup_initializes_kafka(self, mock_process, mock_analyzer, mock_kafka):
        """Test that Kafka is initialized on startup"""
        from main import app
        from fastapi.testclient import TestClient

        with TestClient(app):
            pass

        # Placeholder for integration test
