"""
Tests for MinIO service.

Tests cover:
- Service initialization
- File download from MinIO
- URL parsing
- Error handling
- Health checks
"""

import os
import tempfile
import pytest
from unittest.mock import patch, MagicMock, Mock
from minio.error import S3Error
from services.minio_service import MinioService


class TestMinioServiceInitialization:
    """Tests for MinioService initialization."""

    @patch('services.minio_service.Minio')
    def test_service_initializes_with_correct_config(self, mock_minio_class):
        """Test that service initializes with correct MinIO configuration."""
        mock_client = MagicMock()
        mock_minio_class.return_value = mock_client

        service = MinioService()

        assert service.client == mock_client
        assert service.bucket == "calls"

    @patch('services.minio_service.Minio')
    def test_service_creates_client_with_correct_endpoint(self, mock_minio_class):
        """Test that service creates client with correct endpoint."""
        with patch('services.minio_service.settings') as mock_settings:
            mock_settings.minio_endpoint = "minio:9000"
            mock_settings.minio_access_key = "minioadmin"
            mock_settings.minio_secret_key = "minioadmin"
            mock_settings.minio_bucket = "calls"

            service = MinioService()

            mock_minio_class.assert_called_once()
            call_args = mock_minio_class.call_args

            assert call_args[0][0] == "minio:9000"
            assert call_args[1]['access_key'] == "minioadmin"
            assert call_args[1]['secret_key'] == "minioadmin"
            assert call_args[1]['secure'] is False  # Development mode


class TestFileDownload:
    """Tests for downloading files from MinIO."""

    @patch('services.minio_service.Minio')
    def test_download_file_with_full_url(self, mock_minio_class):
        """Test downloading file with full HTTP URL."""
        mock_client = MagicMock()
        mock_minio_class.return_value = mock_client

        service = MinioService()

        # Mock the fget_object to create a temp file
        def mock_fget(bucket_name, object_name, file_path):
            with open(file_path, 'w') as f:
                f.write('test audio data')

        mock_client.fget_object.side_effect = mock_fget

        audio_url = "http://minio:9000/calls/2024/01/call-123.wav"
        result_path = service.download_file(audio_url)

        # Verify download was called with correct parameters
        mock_client.fget_object.assert_called_once()
        call_args = mock_client.fget_object.call_args

        assert call_args[1]['bucket_name'] == "calls"
        assert call_args[1]['object_name'] == "2024/01/call-123.wav"
        assert os.path.exists(result_path)

        # Cleanup
        os.remove(result_path)

    @patch('services.minio_service.Minio')
    def test_download_file_with_object_key_only(self, mock_minio_class):
        """Test downloading file with object key only (no URL)."""
        mock_client = MagicMock()
        mock_minio_class.return_value = mock_client

        service = MinioService()

        def mock_fget(bucket_name, object_name, file_path):
            with open(file_path, 'w') as f:
                f.write('test audio data')

        mock_client.fget_object.side_effect = mock_fget

        object_key = "2024/01/call-123.wav"
        result_path = service.download_file(object_key)

        mock_client.fget_object.assert_called_once()
        call_args = mock_client.fget_object.call_args

        assert call_args[1]['bucket_name'] == "calls"
        assert call_args[1]['object_name'] == "2024/01/call-123.wav"
        assert os.path.exists(result_path)

        # Cleanup
        os.remove(result_path)

    @patch('services.minio_service.Minio')
    def test_download_file_preserves_extension(self, mock_minio_class):
        """Test that download_file preserves file extension."""
        mock_client = MagicMock()
        mock_minio_class.return_value = mock_client

        service = MinioService()

        def mock_fget(bucket_name, object_name, file_path):
            with open(file_path, 'w') as f:
                f.write('test audio data')

        mock_client.fget_object.side_effect = mock_fget

        # Test different extensions
        extensions = ['.wav', '.mp3', '.m4a']

        for ext in extensions:
            object_key = f"2024/01/call-123{ext}"
            result_path = service.download_file(object_key)

            assert result_path.endswith(ext)
            assert os.path.exists(result_path)

            # Cleanup
            os.remove(result_path)

    @patch('services.minio_service.Minio')
    def test_download_file_creates_temp_file(self, mock_minio_class):
        """Test that download_file creates temporary file."""
        mock_client = MagicMock()
        mock_minio_class.return_value = mock_client

        service = MinioService()

        def mock_fget(bucket_name, object_name, file_path):
            with open(file_path, 'w') as f:
                f.write('test audio data')

        mock_client.fget_object.side_effect = mock_fget

        result_path = service.download_file("2024/01/call-123.wav")

        # Should be in temp directory
        assert tempfile.gettempdir() in result_path
        assert 'audio_' in result_path
        assert os.path.exists(result_path)

        # Cleanup
        os.remove(result_path)

    @patch('services.minio_service.Minio')
    def test_download_file_handles_url_with_bucket_in_path(self, mock_minio_class):
        """Test downloading file when URL includes bucket name in path."""
        mock_client = MagicMock()
        mock_minio_class.return_value = mock_client

        service = MinioService()

        def mock_fget(bucket_name, object_name, file_path):
            with open(file_path, 'w') as f:
                f.write('test audio data')

        mock_client.fget_object.side_effect = mock_fget

        # URL with bucket name in path
        audio_url = "http://minio:9000/calls/2024/01/call-123.wav"
        result_path = service.download_file(audio_url)

        call_args = mock_client.fget_object.call_args
        # Should remove the bucket name from the object key
        assert call_args[1]['object_name'] == "2024/01/call-123.wav"

        # Cleanup
        os.remove(result_path)

    @patch('services.minio_service.Minio')
    def test_download_file_raises_s3_error_on_not_found(self, mock_minio_class):
        """Test that download_file raises S3Error when file not found."""
        mock_client = MagicMock()
        mock_minio_class.return_value = mock_client

        mock_client.fget_object.side_effect = S3Error(
            code="NoSuchKey",
            message="The specified key does not exist",
            resource="/calls/nonexistent.wav",
            request_id="123",
            host_id="456",
            response="error"
        )

        service = MinioService()

        with pytest.raises(S3Error):
            service.download_file("nonexistent.wav")

    @patch('services.minio_service.Minio')
    def test_download_file_handles_generic_exception(self, mock_minio_class):
        """Test that download_file handles generic exceptions."""
        mock_client = MagicMock()
        mock_minio_class.return_value = mock_client

        mock_client.fget_object.side_effect = Exception("Unexpected error")

        service = MinioService()

        with pytest.raises(Exception, match="Unexpected error"):
            service.download_file("2024/01/call-123.wav")


class TestURLParsing:
    """Tests for URL parsing logic."""

    @patch('services.minio_service.Minio')
    def test_parse_url_with_http_scheme(self, mock_minio_class):
        """Test parsing URL with HTTP scheme."""
        mock_client = MagicMock()
        mock_minio_class.return_value = mock_client

        def mock_fget(bucket_name, object_name, file_path):
            with open(file_path, 'w') as f:
                f.write('test')

        mock_client.fget_object.side_effect = mock_fget

        service = MinioService()

        url = "http://minio:9000/calls/2024/01/call-123.wav"
        result_path = service.download_file(url)

        call_args = mock_client.fget_object.call_args
        assert call_args[1]['object_name'] == "2024/01/call-123.wav"

        os.remove(result_path)

    @patch('services.minio_service.Minio')
    def test_parse_url_with_https_scheme(self, mock_minio_class):
        """Test parsing URL with HTTPS scheme."""
        mock_client = MagicMock()
        mock_minio_class.return_value = mock_client

        def mock_fget(bucket_name, object_name, file_path):
            with open(file_path, 'w') as f:
                f.write('test')

        mock_client.fget_object.side_effect = mock_fget

        service = MinioService()

        url = "https://minio:9000/calls/2024/01/call-123.wav"
        result_path = service.download_file(url)

        call_args = mock_client.fget_object.call_args
        assert call_args[1]['object_name'] == "2024/01/call-123.wav"

        os.remove(result_path)

    @patch('services.minio_service.Minio')
    def test_parse_relative_path(self, mock_minio_class):
        """Test parsing relative path (no scheme)."""
        mock_client = MagicMock()
        mock_minio_class.return_value = mock_client

        def mock_fget(bucket_name, object_name, file_path):
            with open(file_path, 'w') as f:
                f.write('test')

        mock_client.fget_object.side_effect = mock_fget

        service = MinioService()

        path = "2024/01/call-123.wav"
        result_path = service.download_file(path)

        call_args = mock_client.fget_object.call_args
        assert call_args[1]['object_name'] == "2024/01/call-123.wav"

        os.remove(result_path)

    @patch('services.minio_service.Minio')
    def test_parse_url_removes_leading_slash(self, mock_minio_class):
        """Test that URL parsing removes leading slash from path."""
        mock_client = MagicMock()
        mock_minio_class.return_value = mock_client

        def mock_fget(bucket_name, object_name, file_path):
            with open(file_path, 'w') as f:
                f.write('test')

        mock_client.fget_object.side_effect = mock_fget

        service = MinioService()

        url = "http://minio:9000//calls/2024/01/call-123.wav"
        result_path = service.download_file(url)

        call_args = mock_client.fget_object.call_args
        # Leading slash and bucket name should be removed
        assert not call_args[1]['object_name'].startswith('/')

        os.remove(result_path)


class TestHealthCheck:
    """Tests for MinIO health check."""

    @patch('services.minio_service.Minio')
    def test_health_check_returns_true_when_bucket_exists(self, mock_minio_class):
        """Test that health_check returns True when bucket exists."""
        mock_client = MagicMock()
        mock_client.bucket_exists.return_value = True
        mock_minio_class.return_value = mock_client

        service = MinioService()
        result = service.health_check()

        assert result is True
        mock_client.bucket_exists.assert_called_once_with("calls")

    @patch('services.minio_service.Minio')
    def test_health_check_returns_false_when_bucket_not_exists(self, mock_minio_class):
        """Test that health_check returns False when bucket doesn't exist."""
        mock_client = MagicMock()
        mock_client.bucket_exists.return_value = False
        mock_minio_class.return_value = mock_client

        service = MinioService()
        result = service.health_check()

        assert result is False

    @patch('services.minio_service.Minio')
    def test_health_check_returns_false_on_exception(self, mock_minio_class):
        """Test that health_check returns False on exception."""
        mock_client = MagicMock()
        mock_client.bucket_exists.side_effect = Exception("Connection failed")
        mock_minio_class.return_value = mock_client

        service = MinioService()
        result = service.health_check()

        assert result is False


class TestMinioServiceIntegration:
    """Integration tests for MinioService."""

    @patch('services.minio_service.Minio')
    def test_complete_download_workflow(self, mock_minio_class):
        """Test complete file download workflow."""
        mock_client = MagicMock()
        mock_minio_class.return_value = mock_client

        # Mock successful download
        def mock_fget(bucket_name, object_name, file_path):
            # Simulate downloading actual audio data
            with open(file_path, 'wb') as f:
                f.write(b'RIFF' + b'\x00' * 1000)  # Fake WAV header

        mock_client.fget_object.side_effect = mock_fget

        service = MinioService()

        # Download file
        audio_url = "http://minio:9000/calls/2024/12/call-123.wav"
        temp_path = service.download_file(audio_url)

        try:
            # Verify file was created
            assert os.path.exists(temp_path)
            assert temp_path.endswith('.wav')

            # Verify file has content
            assert os.path.getsize(temp_path) > 0

            # Verify correct object was requested
            call_args = mock_client.fget_object.call_args
            assert call_args[1]['bucket_name'] == "calls"
            assert call_args[1]['object_name'] == "2024/12/call-123.wav"

        finally:
            # Cleanup
            if os.path.exists(temp_path):
                os.remove(temp_path)

    @patch('services.minio_service.Minio')
    def test_download_different_audio_formats(self, mock_minio_class):
        """Test downloading different audio file formats."""
        mock_client = MagicMock()
        mock_minio_class.return_value = mock_client

        def mock_fget(bucket_name, object_name, file_path):
            with open(file_path, 'wb') as f:
                f.write(b'audio data')

        mock_client.fget_object.side_effect = mock_fget

        service = MinioService()

        formats = [
            ('call-123.wav', '.wav'),
            ('call-456.mp3', '.mp3'),
            ('call-789.m4a', '.m4a')
        ]

        for filename, ext in formats:
            temp_path = service.download_file(f"2024/12/{filename}")

            assert os.path.exists(temp_path)
            assert temp_path.endswith(ext)

            os.remove(temp_path)
