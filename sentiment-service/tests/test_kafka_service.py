"""
Tests for Kafka service integration
"""
import pytest
import json
from unittest.mock import Mock, MagicMock, patch
from kafka.errors import KafkaError
from uuid import uuid4

from services.kafka_service import KafkaService
from models.events import CallTranscribedEvent, SentimentAnalyzedEvent


class TestKafkaServiceInit:
    """Tests for KafkaService initialization"""

    def test_init_creates_instance_with_none_values(self):
        """Test that KafkaService initializes with None for consumer/producer"""
        service = KafkaService()

        assert service.consumer is None
        assert service.producer is None


class TestInitializeConsumer:
    """Tests for Kafka consumer initialization"""

    @patch('services.kafka_service.KafkaConsumer')
    def test_initialize_consumer_success(self, mock_kafka_consumer):
        """Test successful consumer initialization"""
        from config import settings

        mock_consumer_instance = Mock()
        mock_kafka_consumer.return_value = mock_consumer_instance

        service = KafkaService()
        service.initialize_consumer()

        # Verify consumer was created with correct parameters
        mock_kafka_consumer.assert_called_once()
        call_args = mock_kafka_consumer.call_args

        assert settings.kafka_input_topic in call_args[0]
        assert call_args[1]['bootstrap_servers'] == settings.kafka_bootstrap_servers
        assert call_args[1]['group_id'] == settings.kafka_consumer_group
        assert call_args[1]['auto_offset_reset'] == settings.kafka_auto_offset_reset
        assert service.consumer == mock_consumer_instance

    @patch('services.kafka_service.KafkaConsumer')
    def test_initialize_consumer_handles_kafka_error(self, mock_kafka_consumer):
        """Test that KafkaError is raised when consumer initialization fails"""
        mock_kafka_consumer.side_effect = KafkaError("Connection failed")

        service = KafkaService()

        with pytest.raises(KafkaError):
            service.initialize_consumer()

    @patch('services.kafka_service.KafkaConsumer')
    def test_initialize_consumer_sets_deserializer(self, mock_kafka_consumer):
        """Test that JSON deserializer is configured"""
        mock_consumer_instance = Mock()
        mock_kafka_consumer.return_value = mock_consumer_instance

        service = KafkaService()
        service.initialize_consumer()

        # Verify deserializer function is set
        call_kwargs = mock_kafka_consumer.call_args[1]
        deserializer = call_kwargs['value_deserializer']

        # Test deserializer
        test_json = json.dumps({"test": "data"}).encode('utf-8')
        result = deserializer(test_json)
        assert result == {"test": "data"}

    @patch('services.kafka_service.KafkaConsumer')
    def test_initialize_consumer_sets_timeout(self, mock_kafka_consumer):
        """Test that consumer timeout is configured"""
        mock_consumer_instance = Mock()
        mock_kafka_consumer.return_value = mock_consumer_instance

        service = KafkaService()
        service.initialize_consumer()

        call_kwargs = mock_kafka_consumer.call_args[1]
        assert 'consumer_timeout_ms' in call_kwargs
        assert call_kwargs['consumer_timeout_ms'] == 1000


class TestInitializeProducer:
    """Tests for Kafka producer initialization"""

    @patch('services.kafka_service.KafkaProducer')
    def test_initialize_producer_success(self, mock_kafka_producer):
        """Test successful producer initialization"""
        from config import settings

        mock_producer_instance = Mock()
        mock_kafka_producer.return_value = mock_producer_instance

        service = KafkaService()
        service.initialize_producer()

        # Verify producer was created
        mock_kafka_producer.assert_called_once()
        call_kwargs = mock_kafka_producer.call_args[1]

        assert call_kwargs['bootstrap_servers'] == settings.kafka_bootstrap_servers
        assert call_kwargs['acks'] == 'all'
        assert call_kwargs['retries'] == 3
        assert service.producer == mock_producer_instance

    @patch('services.kafka_service.KafkaProducer')
    def test_initialize_producer_handles_kafka_error(self, mock_kafka_producer):
        """Test that KafkaError is raised when producer initialization fails"""
        mock_kafka_producer.side_effect = KafkaError("Connection failed")

        service = KafkaService()

        with pytest.raises(KafkaError):
            service.initialize_producer()

    @patch('services.kafka_service.KafkaProducer')
    def test_initialize_producer_sets_serializer(self, mock_kafka_producer):
        """Test that JSON serializer is configured"""
        mock_producer_instance = Mock()
        mock_kafka_producer.return_value = mock_producer_instance

        service = KafkaService()
        service.initialize_producer()

        # Verify serializer function is set
        call_kwargs = mock_kafka_producer.call_args[1]
        serializer = call_kwargs['value_serializer']

        # Test serializer
        test_data = {"test": "data"}
        result = serializer(test_data)
        expected = json.dumps(test_data, default=str).encode('utf-8')
        assert result == expected


class TestConsumeTranscribed:
    """Tests for consuming CallTranscribed events"""

    def test_consume_transcribed_raises_if_not_initialized(self):
        """Test that error is raised if consumer not initialized"""
        service = KafkaService()

        with pytest.raises(RuntimeError, match="Consumer not initialized"):
            list(service.consume_transcribed())

    @patch('services.kafka_service.KafkaConsumer')
    def test_consume_transcribed_yields_parsed_events(
        self,
        mock_kafka_consumer,
        sample_call_transcribed_event
    ):
        """Test that events are parsed and yielded correctly"""
        # Setup mock messages
        mock_message = Mock()
        mock_message.value = sample_call_transcribed_event.model_dump(mode='json')

        mock_consumer_instance = Mock()
        mock_consumer_instance.__iter__ = Mock(return_value=iter([mock_message]))
        mock_kafka_consumer.return_value = mock_consumer_instance

        service = KafkaService()
        service.initialize_consumer()

        # Consume events
        events = list(service.consume_transcribed())

        assert len(events) == 1
        assert isinstance(events[0], CallTranscribedEvent)
        assert events[0].aggregateId == sample_call_transcribed_event.aggregateId

    @patch('services.kafka_service.KafkaConsumer')
    def test_consume_transcribed_handles_invalid_messages(self, mock_kafka_consumer):
        """Test that invalid messages are skipped"""
        # Create mock messages: one invalid, one valid
        invalid_message = Mock()
        invalid_message.value = {"invalid": "data"}  # Missing required fields

        valid_message = Mock()
        valid_message.value = {
            "eventId": str(uuid4()),
            "eventType": "CallTranscribed",
            "aggregateId": "call-123",
            "aggregateType": "Call",
            "correlationId": str(uuid4()),
            "payload": {
                "callId": "call-123",
                "transcription": "Test",
                "segments": [],
                "duration": 10.0
            }
        }

        mock_consumer_instance = Mock()
        mock_consumer_instance.__iter__ = Mock(
            return_value=iter([invalid_message, valid_message])
        )
        mock_kafka_consumer.return_value = mock_consumer_instance

        service = KafkaService()
        service.initialize_consumer()

        # Consume events - should only get valid one
        events = list(service.consume_transcribed())

        assert len(events) == 1
        assert events[0].aggregateId == "call-123"

    @patch('services.kafka_service.KafkaConsumer')
    def test_consume_transcribed_empty_topic_returns_empty(self, mock_kafka_consumer):
        """Test consuming from empty topic"""
        mock_consumer_instance = Mock()
        mock_consumer_instance.__iter__ = Mock(return_value=iter([]))
        mock_kafka_consumer.return_value = mock_consumer_instance

        service = KafkaService()
        service.initialize_consumer()

        events = list(service.consume_transcribed())

        assert events == []


class TestPublishSentiment:
    """Tests for publishing SentimentAnalyzed events"""

    def test_publish_sentiment_raises_if_not_initialized(self):
        """Test that error is raised if producer not initialized"""
        from models.events import SentimentAnalyzedEvent, SentimentAnalyzedEventPayload

        service = KafkaService()

        event = SentimentAnalyzedEvent(
            aggregateId="call-123",
            correlationId=uuid4(),
            payload=SentimentAnalyzedEventPayload(
                callId="call-123",
                overallSentiment="positive",
                sentimentScore=0.5,
                segments=[],
                escalationDetected=False
            )
        )

        with pytest.raises(RuntimeError, match="Producer not initialized"):
            service.publish_sentiment(event)

    @patch('services.kafka_service.KafkaProducer')
    def test_publish_sentiment_success(self, mock_kafka_producer):
        """Test successful event publishing"""
        from config import settings
        from models.events import SentimentAnalyzedEvent, SentimentAnalyzedEventPayload

        # Setup mock producer
        mock_future = Mock()
        mock_record_metadata = Mock()
        mock_record_metadata.topic = settings.kafka_output_topic
        mock_record_metadata.partition = 0
        mock_record_metadata.offset = 123
        mock_future.get.return_value = mock_record_metadata

        mock_producer_instance = Mock()
        mock_producer_instance.send.return_value = mock_future
        mock_kafka_producer.return_value = mock_producer_instance

        service = KafkaService()
        service.initialize_producer()

        # Create event
        event = SentimentAnalyzedEvent(
            aggregateId="call-123",
            correlationId=uuid4(),
            payload=SentimentAnalyzedEventPayload(
                callId="call-123",
                overallSentiment="positive",
                sentimentScore=0.7,
                segments=[],
                escalationDetected=False
            )
        )

        # Publish
        result = service.publish_sentiment(event)

        assert result is True
        mock_producer_instance.send.assert_called_once()
        call_args = mock_producer_instance.send.call_args
        assert call_args[0][0] == settings.kafka_output_topic

    @patch('services.kafka_service.KafkaProducer')
    def test_publish_sentiment_handles_kafka_error(self, mock_kafka_producer):
        """Test handling of Kafka errors during publishing"""
        from models.events import SentimentAnalyzedEvent, SentimentAnalyzedEventPayload

        mock_producer_instance = Mock()
        mock_producer_instance.send.side_effect = KafkaError("Send failed")
        mock_kafka_producer.return_value = mock_producer_instance

        service = KafkaService()
        service.initialize_producer()

        event = SentimentAnalyzedEvent(
            aggregateId="call-123",
            correlationId=uuid4(),
            payload=SentimentAnalyzedEventPayload(
                callId="call-123",
                overallSentiment="negative",
                sentimentScore=-0.5,
                segments=[],
                escalationDetected=True
            )
        )

        result = service.publish_sentiment(event)

        assert result is False

    @patch('services.kafka_service.KafkaProducer')
    def test_publish_sentiment_handles_timeout(self, mock_kafka_producer):
        """Test handling of timeout during publish acknowledgment"""
        from models.events import SentimentAnalyzedEvent, SentimentAnalyzedEventPayload

        mock_future = Mock()
        mock_future.get.side_effect = TimeoutError("Timeout waiting for ack")

        mock_producer_instance = Mock()
        mock_producer_instance.send.return_value = mock_future
        mock_kafka_producer.return_value = mock_producer_instance

        service = KafkaService()
        service.initialize_producer()

        event = SentimentAnalyzedEvent(
            aggregateId="call-123",
            correlationId=uuid4(),
            payload=SentimentAnalyzedEventPayload(
                callId="call-123",
                overallSentiment="neutral",
                sentimentScore=0.0,
                segments=[],
                escalationDetected=False
            )
        )

        result = service.publish_sentiment(event)

        assert result is False

    @patch('services.kafka_service.KafkaProducer')
    def test_publish_sentiment_serializes_pydantic_model(self, mock_kafka_producer):
        """Test that Pydantic model is correctly serialized"""
        from models.events import SentimentAnalyzedEvent, SentimentAnalyzedEventPayload

        mock_future = Mock()
        mock_record_metadata = Mock()
        mock_record_metadata.topic = "test-topic"
        mock_record_metadata.partition = 0
        mock_record_metadata.offset = 1
        mock_future.get.return_value = mock_record_metadata

        mock_producer_instance = Mock()
        mock_producer_instance.send.return_value = mock_future
        mock_kafka_producer.return_value = mock_producer_instance

        service = KafkaService()
        service.initialize_producer()

        event = SentimentAnalyzedEvent(
            aggregateId="call-456",
            correlationId=uuid4(),
            payload=SentimentAnalyzedEventPayload(
                callId="call-456",
                overallSentiment="positive",
                sentimentScore=0.8,
                segments=[],
                escalationDetected=False,
                processingTimeMs=150.5
            )
        )

        service.publish_sentiment(event)

        # Verify the sent value is a dict
        call_args = mock_producer_instance.send.call_args
        sent_value = call_args[1]['value']
        assert isinstance(sent_value, dict)
        assert sent_value['aggregateId'] == "call-456"
        assert sent_value['payload']['sentimentScore'] == 0.8


class TestClose:
    """Tests for closing Kafka connections"""

    @patch('services.kafka_service.KafkaConsumer')
    @patch('services.kafka_service.KafkaProducer')
    def test_close_closes_consumer_and_producer(
        self,
        mock_kafka_producer,
        mock_kafka_consumer
    ):
        """Test that close() closes both consumer and producer"""
        mock_consumer_instance = Mock()
        mock_producer_instance = Mock()
        mock_kafka_consumer.return_value = mock_consumer_instance
        mock_kafka_producer.return_value = mock_producer_instance

        service = KafkaService()
        service.initialize_consumer()
        service.initialize_producer()

        service.close()

        mock_consumer_instance.close.assert_called_once()
        mock_producer_instance.flush.assert_called_once()
        mock_producer_instance.close.assert_called_once()

    def test_close_handles_none_consumer(self):
        """Test that close() handles None consumer gracefully"""
        service = KafkaService()
        service.consumer = None

        # Should not raise exception
        service.close()

    def test_close_handles_none_producer(self):
        """Test that close() handles None producer gracefully"""
        service = KafkaService()
        service.producer = None

        # Should not raise exception
        service.close()

    @patch('services.kafka_service.KafkaProducer')
    def test_close_flushes_producer_before_closing(self, mock_kafka_producer):
        """Test that producer is flushed before closing"""
        mock_producer_instance = Mock()
        mock_kafka_producer.return_value = mock_producer_instance

        service = KafkaService()
        service.initialize_producer()
        service.close()

        # Verify flush is called before close
        assert mock_producer_instance.flush.call_count == 1
        assert mock_producer_instance.close.call_count == 1
        # Flush should be called before close
        assert mock_producer_instance.method_calls[0][0] == 'flush'
        assert mock_producer_instance.method_calls[1][0] == 'close'
