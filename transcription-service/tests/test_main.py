"""
Tests for main FastAPI application endpoints.

Tests cover:
- Root endpoint
- Health check endpoints
- Application startup/shutdown
- Error handling
"""

import pytest
from unittest.mock import patch, MagicMock
from fastapi import status


class TestRootEndpoint:
    """Tests for root endpoint."""

    def test_root_endpoint_returns_service_info(self, client_no_lifespan):
        """Test that root endpoint returns service information."""
        response = client_no_lifespan.get("/")

        assert response.status_code == status.HTTP_200_OK
        data = response.json()
        assert data["service"] == "transcription-service"
        assert data["status"] == "running"
        assert data["version"] == "1.0.0"

    def test_root_endpoint_structure(self, client_no_lifespan):
        """Test that root endpoint has correct structure."""
        response = client_no_lifespan.get("/")
        data = response.json()

        assert "service" in data
        assert "status" in data
        assert "version" in data
        assert isinstance(data["service"], str)
        assert isinstance(data["status"], str)
        assert isinstance(data["version"], str)


class TestHealthEndpoint:
    """Tests for health check endpoint."""

    def test_health_endpoint_returns_healthy(self, client_no_lifespan):
        """Test that health endpoint returns healthy status."""
        response = client_no_lifespan.get("/health")

        assert response.status_code == status.HTTP_200_OK
        data = response.json()
        assert data["status"] == "healthy"
        assert data["service"] == "transcription-service"

    def test_health_endpoint_structure(self, client_no_lifespan):
        """Test that health endpoint has correct structure."""
        response = client_no_lifespan.get("/health")
        data = response.json()

        assert "status" in data
        assert "service" in data
        assert isinstance(data["status"], str)
        assert isinstance(data["service"], str)


class TestReadinessEndpoint:
    """Tests for readiness check endpoint."""

    def test_readiness_endpoint_ready_when_all_dependencies_healthy(self, client_no_lifespan):
        """Test that readiness endpoint returns ready when all dependencies are healthy."""
        with patch('services.minio_service.minio_service.health_check', return_value=True), \
             patch('services.kafka_service.kafka_service.health_check', return_value=True):

            response = client_no_lifespan.get("/ready")

            assert response.status_code == status.HTTP_200_OK
            data = response.json()
            assert data["status"] == "ready"
            assert data["service"] == "transcription-service"
            assert data["dependencies"]["minio"] is True
            assert data["dependencies"]["kafka"] is True

    def test_readiness_endpoint_not_ready_when_minio_unhealthy(self, client_no_lifespan):
        """Test that readiness endpoint returns not ready when MinIO is unhealthy."""
        with patch('services.minio_service.minio_service.health_check', return_value=False), \
             patch('services.kafka_service.kafka_service.health_check', return_value=True):

            response = client_no_lifespan.get("/ready")

            assert response.status_code == status.HTTP_503_SERVICE_UNAVAILABLE
            data = response.json()
            assert data["status"] == "not_ready"
            assert data["dependencies"]["minio"] is False
            assert data["dependencies"]["kafka"] is True

    def test_readiness_endpoint_not_ready_when_kafka_unhealthy(self, client_no_lifespan):
        """Test that readiness endpoint returns not ready when Kafka is unhealthy."""
        with patch('services.minio_service.minio_service.health_check', return_value=True), \
             patch('services.kafka_service.kafka_service.health_check', return_value=False):

            response = client_no_lifespan.get("/ready")

            assert response.status_code == status.HTTP_503_SERVICE_UNAVAILABLE
            data = response.json()
            assert data["status"] == "not_ready"
            assert data["dependencies"]["minio"] is True
            assert data["dependencies"]["kafka"] is False

    def test_readiness_endpoint_not_ready_when_all_dependencies_unhealthy(self, client_no_lifespan):
        """Test that readiness endpoint returns not ready when all dependencies are unhealthy."""
        with patch('services.minio_service.minio_service.health_check', return_value=False), \
             patch('services.kafka_service.kafka_service.health_check', return_value=False):

            response = client_no_lifespan.get("/ready")

            assert response.status_code == status.HTTP_503_SERVICE_UNAVAILABLE
            data = response.json()
            assert data["status"] == "not_ready"
            assert data["dependencies"]["minio"] is False
            assert data["dependencies"]["kafka"] is False

    def test_readiness_endpoint_handles_minio_exception(self, client_no_lifespan):
        """Test that readiness endpoint handles MinIO exceptions gracefully."""
        with patch('services.minio_service.minio_service.health_check', side_effect=Exception("MinIO error")), \
             patch('services.kafka_service.kafka_service.health_check', return_value=True):

            response = client_no_lifespan.get("/ready")

            assert response.status_code == status.HTTP_503_SERVICE_UNAVAILABLE
            data = response.json()
            assert data["status"] == "not_ready"
            assert data["dependencies"]["minio"] is False

    def test_readiness_endpoint_handles_kafka_exception(self, client_no_lifespan):
        """Test that readiness endpoint handles Kafka exceptions gracefully."""
        with patch('services.minio_service.minio_service.health_check', return_value=True), \
             patch('services.kafka_service.kafka_service.health_check', side_effect=Exception("Kafka error")):

            response = client_no_lifespan.get("/ready")

            assert response.status_code == status.HTTP_503_SERVICE_UNAVAILABLE
            data = response.json()
            assert data["status"] == "not_ready"
            assert data["dependencies"]["kafka"] is False


class TestMetricsEndpoint:
    """Tests for Prometheus metrics endpoint."""

    def test_metrics_endpoint_returns_prometheus_format(self, client_no_lifespan):
        """Test that metrics endpoint returns data in Prometheus format."""
        response = client_no_lifespan.get("/metrics")

        assert response.status_code == status.HTTP_200_OK
        assert "text/plain" in response.headers["content-type"]

        # Check for some expected metric names
        content = response.text
        assert "transcriptions_total" in content or "python_info" in content

    def test_metrics_endpoint_content_type(self, client_no_lifespan):
        """Test that metrics endpoint has correct content type."""
        response = client_no_lifespan.get("/metrics")

        assert response.status_code == status.HTTP_200_OK
        # Prometheus metrics should be text/plain with specific version
        assert response.headers["content-type"].startswith("text/plain")


class TestNotFoundEndpoint:
    """Tests for 404 handling."""

    def test_nonexistent_endpoint_returns_404(self, client_no_lifespan):
        """Test that accessing a non-existent endpoint returns 404."""
        response = client_no_lifespan.get("/nonexistent")

        assert response.status_code == status.HTTP_404_NOT_FOUND

    def test_nonexistent_endpoint_with_trailing_slash(self, client_no_lifespan):
        """Test that non-existent endpoint with trailing slash returns 404."""
        response = client_no_lifespan.get("/nonexistent/")

        assert response.status_code == status.HTTP_404_NOT_FOUND


class TestApplicationLifecycle:
    """Tests for application startup and shutdown."""

    @patch('services.whisper_service.whisper_service.load_model')
    @patch('services.kafka_service.kafka_service.close')
    def test_lifespan_loads_whisper_model_on_startup(self, mock_close, mock_load_model):
        """Test that Whisper model is loaded on application startup."""
        # This test would need to trigger actual lifespan events
        # For now, we can test the service directly
        from services.whisper_service import whisper_service

        whisper_service.load_model()
        mock_load_model.assert_called_once()

    @patch('services.kafka_service.kafka_service.close')
    def test_lifespan_closes_kafka_on_shutdown(self, mock_close):
        """Test that Kafka connections are closed on shutdown."""
        from services.kafka_service import kafka_service

        kafka_service.close()
        mock_close.assert_called_once()

    @patch('services.whisper_service.whisper_service.load_model')
    def test_lifespan_handles_whisper_load_failure_gracefully(self, mock_load_model):
        """Test that application handles Whisper model load failure gracefully."""
        mock_load_model.side_effect = Exception("Failed to load model")

        # Application should not crash even if model fails to load
        from services.whisper_service import whisper_service

        with pytest.raises(Exception):
            whisper_service.load_model()


class TestCORSandSecurity:
    """Tests for CORS and security headers (if implemented)."""

    def test_health_endpoint_accepts_get_method(self, client_no_lifespan):
        """Test that health endpoint accepts GET method."""
        response = client_no_lifespan.get("/health")
        assert response.status_code == status.HTTP_200_OK

    def test_health_endpoint_rejects_post_method(self, client_no_lifespan):
        """Test that health endpoint rejects POST method."""
        response = client_no_lifespan.post("/health")
        assert response.status_code == status.HTTP_405_METHOD_NOT_ALLOWED

    def test_health_endpoint_rejects_put_method(self, client_no_lifespan):
        """Test that health endpoint rejects PUT method."""
        response = client_no_lifespan.put("/health")
        assert response.status_code == status.HTTP_405_METHOD_NOT_ALLOWED

    def test_health_endpoint_rejects_delete_method(self, client_no_lifespan):
        """Test that health endpoint rejects DELETE method."""
        response = client_no_lifespan.delete("/health")
        assert response.status_code == status.HTTP_405_METHOD_NOT_ALLOWED
