"""
Pytest fixtures and configuration for transcription-service tests.

This module provides shared fixtures for testing the transcription service,
including mocked external dependencies (Whisper, Kafka, MinIO).
"""

import os
import sys
import tempfile
from unittest.mock import Mock, patch, MagicMock
import pytest
from fastapi.testclient import TestClient

# Add parent directory to path to import app modules
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))


@pytest.fixture(scope="session")
def mock_whisper_model():
    """Mock Whisper model for testing."""
    mock_model = MagicMock()

    # Mock transcribe method with realistic response
    mock_model.transcribe.return_value = {
        'text': 'Hello, this is a test transcription. How can I help you today?',
        'language': 'en',
        'segments': [
            {
                'id': 0,
                'seek': 0,
                'start': 0.0,
                'end': 2.5,
                'text': 'Hello, this is a test transcription.',
                'tokens': [1, 2, 3],
                'temperature': 0.0,
                'avg_logprob': -0.3,
                'compression_ratio': 1.5,
                'no_speech_prob': 0.1
            },
            {
                'id': 1,
                'seek': 250,
                'start': 2.5,
                'end': 5.0,
                'text': 'How can I help you today?',
                'tokens': [4, 5, 6],
                'temperature': 0.0,
                'avg_logprob': -0.2,
                'compression_ratio': 1.6,
                'no_speech_prob': 0.05
            }
        ]
    }

    return mock_model


@pytest.fixture(scope="session")
def mock_kafka_consumer():
    """Mock Kafka consumer for testing."""
    mock_consumer = MagicMock()
    mock_consumer.poll.return_value = {}
    return mock_consumer


@pytest.fixture(scope="session")
def mock_kafka_producer():
    """Mock Kafka producer for testing."""
    mock_producer = MagicMock()

    # Mock the send method to return a future-like object
    mock_future = MagicMock()
    mock_future.get.return_value = MagicMock(
        topic='calls.transcribed',
        partition=0,
        offset=123
    )
    mock_producer.send.return_value = mock_future

    return mock_producer


@pytest.fixture(scope="session")
def mock_minio_client():
    """Mock MinIO client for testing."""
    mock_client = MagicMock()
    mock_client.bucket_exists.return_value = True
    return mock_client


@pytest.fixture
def temp_audio_file():
    """Create a temporary audio file for testing."""
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f:
        # Write some dummy data
        f.write(b'RIFF' + b'\x00' * 100)
        temp_path = f.name

    yield temp_path

    # Cleanup
    if os.path.exists(temp_path):
        os.remove(temp_path)


@pytest.fixture
def sample_call_received_event():
    """Sample CallReceived event for testing."""
    return {
        'eventId': 'event-123',
        'eventType': 'CallReceived',
        'aggregateId': 'call-456',
        'aggregateType': 'Call',
        'timestamp': '2024-01-01T12:00:00',
        'version': 1,
        'causationId': None,
        'correlationId': 'corr-789',
        'metadata': {
            'userId': 'system',
            'service': 'call-ingestion-service'
        },
        'payload': {
            'callId': 'call-456',
            'callerId': 'caller-123',
            'agentId': 'agent-456',
            'channel': 'phone',
            'startTime': '2024-01-01T12:00:00',
            'audioFileUrl': 'http://minio:9000/calls/2024/01/call-456.wav',
            'audioFormat': 'wav',
            'audioFileSize': 1024000
        }
    }


@pytest.fixture
def sample_transcription_result():
    """Sample transcription result for testing."""
    return {
        'full_text': 'Hello, this is a test transcription. How can I help you today?',
        'segments': [
            {
                'speaker': 'agent',
                'startTime': 0.0,
                'endTime': 2.5,
                'text': 'Hello, this is a test transcription.'
            },
            {
                'speaker': 'agent',
                'startTime': 2.5,
                'endTime': 5.0,
                'text': 'How can I help you today?'
            }
        ],
        'language': 'en',
        'confidence': 0.85
    }


@pytest.fixture
def client_no_lifespan():
    """
    Test client without lifespan events.

    This prevents the background Kafka consumer from starting during tests.
    """
    # Mock all external dependencies before importing main
    with patch('services.whisper_service.whisper.load_model') as mock_whisper, \
         patch('services.kafka_service.KafkaConsumer') as mock_consumer, \
         patch('services.kafka_service.KafkaProducer') as mock_producer, \
         patch('services.minio_service.Minio') as mock_minio:

        # Import after patching to prevent actual connections
        from main import app

        # Create client without triggering lifespan
        with TestClient(app, raise_server_exceptions=False) as test_client:
            yield test_client


@pytest.fixture
def mock_services():
    """
    Mock all service dependencies.

    Returns a dict of mocked services that can be used in tests.
    """
    with patch('services.whisper_service.whisper.load_model') as mock_whisper, \
         patch('services.kafka_service.KafkaConsumer') as mock_consumer, \
         patch('services.kafka_service.KafkaProducer') as mock_producer, \
         patch('services.minio_service.Minio') as mock_minio:

        yield {
            'whisper': mock_whisper,
            'kafka_consumer': mock_consumer,
            'kafka_producer': mock_producer,
            'minio': mock_minio
        }
