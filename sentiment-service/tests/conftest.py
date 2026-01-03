"""
Pytest fixtures for sentiment-service tests
"""
import pytest
from unittest.mock import Mock, MagicMock, patch
from fastapi.testclient import TestClient
from uuid import uuid4
from datetime import datetime

from models.events import (
    CallTranscribedEvent,
    CallTranscribedEventPayload,
    TranscriptionSegment,
    SentimentSegment
)


@pytest.fixture
def test_client():
    """
    Create TestClient with mocked dependencies
    Prevents actual model loading and Kafka connections during tests
    """
    with patch('main.sentiment_analyzer') as mock_analyzer, \
         patch('main.kafka_service') as mock_kafka, \
         patch('main.process_transcriptions'):

        # Configure mock sentiment analyzer
        mock_analyzer.model_loaded = True
        mock_analyzer.use_vader_fallback = False

        # Import app after mocking to avoid startup issues
        from main import app

        client = TestClient(app)
        yield client


@pytest.fixture
def mock_sentiment_analyzer():
    """Mock SentimentAnalyzer instance"""
    analyzer = Mock()
    analyzer.model_loaded = True
    analyzer.use_vader_fallback = False
    analyzer.roberta_pipeline = Mock()
    analyzer.vader_analyzer = Mock()
    return analyzer


@pytest.fixture
def mock_kafka_service():
    """Mock KafkaService instance"""
    kafka = Mock()
    kafka.consumer = Mock()
    kafka.producer = Mock()
    return kafka


@pytest.fixture
def sample_transcription_segments():
    """Sample transcription segments for testing"""
    return [
        TranscriptionSegment(
            startTime=0.0,
            endTime=5.0,
            text="Hello, how can I help you today?",
            speaker="agent",
            confidence=0.95
        ),
        TranscriptionSegment(
            startTime=5.0,
            endTime=10.0,
            text="I'm having a problem with my order.",
            speaker="customer",
            confidence=0.92
        ),
        TranscriptionSegment(
            startTime=10.0,
            endTime=15.0,
            text="I understand. Let me check that for you.",
            speaker="agent",
            confidence=0.94
        ),
        TranscriptionSegment(
            startTime=15.0,
            endTime=20.0,
            text="This is taking forever! I'm very frustrated!",
            speaker="customer",
            confidence=0.89
        ),
        TranscriptionSegment(
            startTime=20.0,
            endTime=25.0,
            text="I apologize for the delay. I found your order.",
            speaker="agent",
            confidence=0.93
        )
    ]


@pytest.fixture
def sample_call_transcribed_event(sample_transcription_segments):
    """Sample CallTranscribed event for testing"""
    call_id = str(uuid4())
    correlation_id = uuid4()

    return CallTranscribedEvent(
        eventId=uuid4(),
        eventType="CallTranscribed",
        aggregateId=call_id,
        aggregateType="Call",
        timestamp=datetime.utcnow(),
        version=1,
        causationId=uuid4(),
        correlationId=correlation_id,
        metadata={
            "service": "transcription-service",
            "userId": "system"
        },
        payload=CallTranscribedEventPayload(
            callId=call_id,
            transcription="Full transcription text here...",
            segments=sample_transcription_segments,
            duration=25.0,
            language="en"
        )
    )


@pytest.fixture
def sample_sentiment_segments():
    """Sample sentiment segments for testing"""
    return [
        SentimentSegment(
            startTime=0.0,
            endTime=5.0,
            text="Hello, how can I help you today?",
            sentiment="neutral",
            score=0.1,
            confidence=0.85,
            emotions={"LABEL_0": 0.1, "LABEL_1": 0.8, "LABEL_2": 0.1},
            speaker="agent"
        ),
        SentimentSegment(
            startTime=5.0,
            endTime=10.0,
            text="I'm having a problem with my order.",
            sentiment="negative",
            score=-0.3,
            confidence=0.75,
            emotions={"LABEL_0": 0.7, "LABEL_1": 0.2, "LABEL_2": 0.1},
            speaker="customer"
        ),
        SentimentSegment(
            startTime=10.0,
            endTime=15.0,
            text="I understand. Let me check that for you.",
            sentiment="positive",
            score=0.2,
            confidence=0.80,
            emotions={"LABEL_0": 0.1, "LABEL_1": 0.3, "LABEL_2": 0.6},
            speaker="agent"
        ),
        SentimentSegment(
            startTime=15.0,
            endTime=20.0,
            text="This is taking forever! I'm very frustrated!",
            sentiment="negative",
            score=-0.8,
            confidence=0.95,
            emotions={"LABEL_0": 0.9, "LABEL_1": 0.05, "LABEL_2": 0.05},
            speaker="customer"
        ),
        SentimentSegment(
            startTime=20.0,
            endTime=25.0,
            text="I apologize for the delay. I found your order.",
            sentiment="neutral",
            score=0.0,
            confidence=0.70,
            emotions={"LABEL_0": 0.3, "LABEL_1": 0.5, "LABEL_2": 0.2},
            speaker="agent"
        )
    ]


@pytest.fixture
def mock_roberta_pipeline_response():
    """Mock response from RoBERTa pipeline"""
    return [[
        {"label": "LABEL_0", "score": 0.1},  # negative
        {"label": "LABEL_1", "score": 0.2},  # neutral
        {"label": "LABEL_2", "score": 0.7}   # positive
    ]]


@pytest.fixture
def mock_vader_scores():
    """Mock VADER sentiment scores"""
    return {
        "neg": 0.0,
        "neu": 0.5,
        "pos": 0.5,
        "compound": 0.5
    }
