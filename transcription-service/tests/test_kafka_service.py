"""
Tests for Kafka service.

Tests cover:
- Consumer creation and configuration
- Producer creation and configuration
- Event consumption
- Event publishing
- Error handling
- Health checks
"""

import json
import pytest
from unittest.mock import patch, MagicMock, Mock, AsyncMock
from kafka.errors import KafkaError
from services.kafka_service import KafkaService
from models.events import CallReceivedEvent, CallTranscribedEvent, CallTranscribedPayload, TranscriptionData, Segment


class TestKafkaServiceInitialization:
    """Tests for KafkaService initialization."""

    def test_service_initializes_without_creating_connections(self):
        """Test that service initializes without creating connections immediately."""
        service = KafkaService()

        assert service.consumer is None
        assert service.producer is None

    def test_service_has_correct_topic_names(self):
        """Test that service has correct topic names configured."""
        service = KafkaService()

        assert service.TOPIC_CALLS_RECEIVED == "calls.received"
        assert service.TOPIC_CALLS_TRANSCRIBED == "calls.transcribed"
        assert service.CONSUMER_GROUP == "transcription-service"


class TestConsumerCreation:
    """Tests for Kafka consumer creation."""

    @patch('services.kafka_service.KafkaConsumer')
    def test_create_consumer_creates_consumer_successfully(self, mock_consumer_class):
        """Test that create_consumer creates a Kafka consumer successfully."""
        mock_consumer = MagicMock()
        mock_consumer_class.return_value = mock_consumer

        service = KafkaService()
        consumer = service.create_consumer()

        assert consumer == mock_consumer
        mock_consumer_class.assert_called_once()

    @patch('services.kafka_service.KafkaConsumer')
    def test_create_consumer_configures_correct_topic(self, mock_consumer_class):
        """Test that create_consumer subscribes to correct topic."""
        service = KafkaService()
        service.create_consumer()

        # First positional argument should be the topic
        call_args = mock_consumer_class.call_args
        assert call_args[0][0] == "calls.received"

    @patch('services.kafka_service.KafkaConsumer')
    def test_create_consumer_configures_consumer_group(self, mock_consumer_class):
        """Test that create_consumer sets correct consumer group."""
        service = KafkaService()
        service.create_consumer()

        call_kwargs = mock_consumer_class.call_args[1]
        assert call_kwargs['group_id'] == "transcription-service"

    @patch('services.kafka_service.KafkaConsumer')
    def test_create_consumer_configures_auto_offset_reset(self, mock_consumer_class):
        """Test that create_consumer configures auto offset reset to earliest."""
        service = KafkaService()
        service.create_consumer()

        call_kwargs = mock_consumer_class.call_args[1]
        assert call_kwargs['auto_offset_reset'] == 'earliest'

    @patch('services.kafka_service.KafkaConsumer')
    def test_create_consumer_enables_auto_commit(self, mock_consumer_class):
        """Test that create_consumer enables auto commit."""
        service = KafkaService()
        service.create_consumer()

        call_kwargs = mock_consumer_class.call_args[1]
        assert call_kwargs['enable_auto_commit'] is True

    @patch('services.kafka_service.KafkaConsumer')
    def test_create_consumer_configures_value_deserializer(self, mock_consumer_class):
        """Test that create_consumer configures JSON deserializer."""
        service = KafkaService()
        service.create_consumer()

        call_kwargs = mock_consumer_class.call_args[1]
        deserializer = call_kwargs['value_deserializer']

        # Test the deserializer function
        test_json = json.dumps({"test": "data"})
        result = deserializer(test_json.encode('utf-8'))
        assert result == {"test": "data"}

    @patch('services.kafka_service.KafkaConsumer')
    def test_create_consumer_handles_connection_error(self, mock_consumer_class):
        """Test that create_consumer handles connection errors."""
        mock_consumer_class.side_effect = Exception("Connection failed")

        service = KafkaService()

        with pytest.raises(Exception, match="Connection failed"):
            service.create_consumer()


class TestProducerCreation:
    """Tests for Kafka producer creation."""

    @patch('services.kafka_service.KafkaProducer')
    def test_create_producer_creates_producer_successfully(self, mock_producer_class):
        """Test that create_producer creates a Kafka producer successfully."""
        mock_producer = MagicMock()
        mock_producer_class.return_value = mock_producer

        service = KafkaService()
        producer = service.create_producer()

        assert producer == mock_producer
        mock_producer_class.assert_called_once()

    @patch('services.kafka_service.KafkaProducer')
    def test_create_producer_configures_value_serializer(self, mock_producer_class):
        """Test that create_producer configures JSON serializer."""
        service = KafkaService()
        service.create_producer()

        call_kwargs = mock_producer_class.call_args[1]
        serializer = call_kwargs['value_serializer']

        # Test the serializer function
        test_data = {"test": "data"}
        result = serializer(test_data)
        assert result == json.dumps(test_data).encode('utf-8')

    @patch('services.kafka_service.KafkaProducer')
    def test_create_producer_configures_acks_all(self, mock_producer_class):
        """Test that create_producer configures acks to 'all'."""
        service = KafkaService()
        service.create_producer()

        call_kwargs = mock_producer_class.call_args[1]
        assert call_kwargs['acks'] == 'all'

    @patch('services.kafka_service.KafkaProducer')
    def test_create_producer_configures_retries(self, mock_producer_class):
        """Test that create_producer configures retries."""
        service = KafkaService()
        service.create_producer()

        call_kwargs = mock_producer_class.call_args[1]
        assert call_kwargs['retries'] == 3

    @patch('services.kafka_service.KafkaProducer')
    def test_create_producer_handles_connection_error(self, mock_producer_class):
        """Test that create_producer handles connection errors."""
        mock_producer_class.side_effect = Exception("Connection failed")

        service = KafkaService()

        with pytest.raises(Exception, match="Connection failed"):
            service.create_producer()


class TestEventConsumption:
    """Tests for consuming CallReceived events."""

    @pytest.mark.asyncio
    @patch('services.kafka_service.KafkaConsumer')
    async def test_consume_call_received_creates_consumer_if_needed(self, mock_consumer_class):
        """Test that consume_call_received creates consumer if not exists."""
        mock_consumer = MagicMock()
        mock_consumer.poll.return_value = {}
        mock_consumer_class.return_value = mock_consumer

        service = KafkaService()
        assert service.consumer is None

        # Start consuming (just one iteration)
        consumer_gen = service.consume_call_received()
        try:
            await consumer_gen.__anext__()
        except StopAsyncIteration:
            pass

        # Consumer should be created
        mock_consumer_class.assert_called_once()

    @pytest.mark.asyncio
    @patch('services.kafka_service.KafkaConsumer')
    async def test_consume_call_received_yields_events(self, mock_consumer_class, sample_call_received_event):
        """Test that consume_call_received yields CallReceivedEvent objects."""
        # Mock consumer with one message
        mock_consumer = MagicMock()
        mock_message = MagicMock()
        mock_message.value = sample_call_received_event

        # Return message once, then empty
        mock_consumer.poll.side_effect = [
            {MagicMock(): [mock_message]},
            KeyboardInterrupt()  # Stop the loop
        ]
        mock_consumer_class.return_value = mock_consumer

        service = KafkaService()

        events = []
        try:
            async for event in service.consume_call_received():
                events.append(event)
        except KeyboardInterrupt:
            pass

        assert len(events) == 1
        assert isinstance(events[0], CallReceivedEvent)
        assert events[0].eventId == 'event-123'
        assert events[0].aggregateId == 'call-456'

    @pytest.mark.asyncio
    @patch('services.kafka_service.KafkaConsumer')
    async def test_consume_call_received_handles_parse_errors(self, mock_consumer_class):
        """Test that consume_call_received handles malformed events gracefully."""
        mock_consumer = MagicMock()
        mock_message = MagicMock()
        mock_message.value = {"invalid": "event"}  # Missing required fields

        mock_consumer.poll.side_effect = [
            {MagicMock(): [mock_message]},
            KeyboardInterrupt()
        ]
        mock_consumer_class.return_value = mock_consumer

        service = KafkaService()

        events = []
        try:
            async for event in service.consume_call_received():
                events.append(event)
        except KeyboardInterrupt:
            pass

        # Should not yield any events (error logged but continues)
        assert len(events) == 0

    @pytest.mark.asyncio
    @patch('services.kafka_service.KafkaConsumer')
    async def test_consume_call_received_closes_consumer_on_exit(self, mock_consumer_class):
        """Test that consume_call_received closes consumer on exit."""
        mock_consumer = MagicMock()
        mock_consumer.poll.side_effect = KeyboardInterrupt()
        mock_consumer_class.return_value = mock_consumer

        service = KafkaService()

        try:
            async for event in service.consume_call_received():
                pass
        except KeyboardInterrupt:
            pass

        mock_consumer.close.assert_called_once()


class TestEventPublishing:
    """Tests for publishing CallTranscribed events."""

    @patch('services.kafka_service.KafkaProducer')
    def test_publish_call_transcribed_creates_producer_if_needed(self, mock_producer_class):
        """Test that publish_call_transcribed creates producer if not exists."""
        mock_producer = MagicMock()
        mock_future = MagicMock()
        mock_future.get.return_value = MagicMock(topic='calls.transcribed', partition=0, offset=123)
        mock_producer.send.return_value = mock_future
        mock_producer_class.return_value = mock_producer

        service = KafkaService()
        assert service.producer is None

        # Create a sample event
        event = CallTranscribedEvent(
            aggregateId='call-123',
            causationId='event-456',
            correlationId='corr-789',
            payload=CallTranscribedPayload(
                callId='call-123',
                transcription=TranscriptionData(
                    fullText='Test transcription',
                    segments=[],
                    language='en',
                    confidence=0.9
                )
            )
        )

        service.publish_call_transcribed(event)

        mock_producer_class.assert_called_once()

    @patch('services.kafka_service.KafkaProducer')
    def test_publish_call_transcribed_sends_to_correct_topic(self, mock_producer_class):
        """Test that publish_call_transcribed sends to correct topic."""
        mock_producer = MagicMock()
        mock_future = MagicMock()
        mock_future.get.return_value = MagicMock(topic='calls.transcribed', partition=0, offset=123)
        mock_producer.send.return_value = mock_future
        mock_producer_class.return_value = mock_producer

        service = KafkaService()

        event = CallTranscribedEvent(
            aggregateId='call-123',
            causationId='event-456',
            correlationId='corr-789',
            payload=CallTranscribedPayload(
                callId='call-123',
                transcription=TranscriptionData(
                    fullText='Test',
                    segments=[],
                    language='en',
                    confidence=0.9
                )
            )
        )

        service.publish_call_transcribed(event)

        mock_producer.send.assert_called_once()
        call_args = mock_producer.send.call_args
        assert call_args[0][0] == "calls.transcribed"

    @patch('services.kafka_service.KafkaProducer')
    def test_publish_call_transcribed_serializes_event_correctly(self, mock_producer_class):
        """Test that publish_call_transcribed serializes event to dict."""
        mock_producer = MagicMock()
        mock_future = MagicMock()
        mock_future.get.return_value = MagicMock(topic='calls.transcribed', partition=0, offset=123)
        mock_producer.send.return_value = mock_future
        mock_producer_class.return_value = mock_producer

        service = KafkaService()

        event = CallTranscribedEvent(
            aggregateId='call-123',
            causationId='event-456',
            correlationId='corr-789',
            payload=CallTranscribedPayload(
                callId='call-123',
                transcription=TranscriptionData(
                    fullText='Test',
                    segments=[],
                    language='en',
                    confidence=0.9
                )
            )
        )

        service.publish_call_transcribed(event)

        call_args = mock_producer.send.call_args
        event_dict = call_args[1]['value']

        assert isinstance(event_dict, dict)
        assert event_dict['eventType'] == 'CallTranscribed'
        assert event_dict['aggregateId'] == 'call-123'

    @patch('services.kafka_service.KafkaProducer')
    def test_publish_call_transcribed_returns_true_on_success(self, mock_producer_class):
        """Test that publish_call_transcribed returns True on success."""
        mock_producer = MagicMock()
        mock_future = MagicMock()
        mock_future.get.return_value = MagicMock(topic='calls.transcribed', partition=0, offset=123)
        mock_producer.send.return_value = mock_future
        mock_producer_class.return_value = mock_producer

        service = KafkaService()

        event = CallTranscribedEvent(
            aggregateId='call-123',
            causationId='event-456',
            correlationId='corr-789',
            payload=CallTranscribedPayload(
                callId='call-123',
                transcription=TranscriptionData(
                    fullText='Test',
                    segments=[],
                    language='en',
                    confidence=0.9
                )
            )
        )

        result = service.publish_call_transcribed(event)

        assert result is True

    @patch('services.kafka_service.KafkaProducer')
    def test_publish_call_transcribed_returns_false_on_kafka_error(self, mock_producer_class):
        """Test that publish_call_transcribed returns False on Kafka error."""
        mock_producer = MagicMock()
        mock_producer.send.side_effect = KafkaError("Send failed")
        mock_producer_class.return_value = mock_producer

        service = KafkaService()

        event = CallTranscribedEvent(
            aggregateId='call-123',
            causationId='event-456',
            correlationId='corr-789',
            payload=CallTranscribedPayload(
                callId='call-123',
                transcription=TranscriptionData(
                    fullText='Test',
                    segments=[],
                    language='en',
                    confidence=0.9
                )
            )
        )

        result = service.publish_call_transcribed(event)

        assert result is False

    @patch('services.kafka_service.KafkaProducer')
    def test_publish_call_transcribed_returns_false_on_generic_error(self, mock_producer_class):
        """Test that publish_call_transcribed returns False on generic error."""
        mock_producer = MagicMock()
        mock_producer.send.side_effect = Exception("Unknown error")
        mock_producer_class.return_value = mock_producer

        service = KafkaService()

        event = CallTranscribedEvent(
            aggregateId='call-123',
            causationId='event-456',
            correlationId='corr-789',
            payload=CallTranscribedPayload(
                callId='call-123',
                transcription=TranscriptionData(
                    fullText='Test',
                    segments=[],
                    language='en',
                    confidence=0.9
                )
            )
        )

        result = service.publish_call_transcribed(event)

        assert result is False


class TestHealthCheck:
    """Tests for Kafka health check."""

    @patch('services.kafka_service.KafkaProducer')
    def test_health_check_returns_true_when_kafka_accessible(self, mock_producer_class):
        """Test that health_check returns True when Kafka is accessible."""
        mock_producer = MagicMock()
        mock_producer_class.return_value = mock_producer

        service = KafkaService()
        result = service.health_check()

        assert result is True
        mock_producer.close.assert_called_once()

    @patch('services.kafka_service.KafkaProducer')
    def test_health_check_returns_false_when_kafka_inaccessible(self, mock_producer_class):
        """Test that health_check returns False when Kafka is inaccessible."""
        mock_producer_class.side_effect = Exception("Connection failed")

        service = KafkaService()
        result = service.health_check()

        assert result is False


class TestConnectionManagement:
    """Tests for connection lifecycle management."""

    @patch('services.kafka_service.KafkaConsumer')
    @patch('services.kafka_service.KafkaProducer')
    def test_close_closes_consumer_if_exists(self, mock_producer_class, mock_consumer_class):
        """Test that close closes consumer connection if it exists."""
        mock_consumer = MagicMock()
        mock_consumer_class.return_value = mock_consumer

        service = KafkaService()
        service.consumer = mock_consumer

        service.close()

        mock_consumer.close.assert_called_once()
        assert service.consumer is None

    @patch('services.kafka_service.KafkaConsumer')
    @patch('services.kafka_service.KafkaProducer')
    def test_close_closes_producer_if_exists(self, mock_producer_class, mock_consumer_class):
        """Test that close closes producer connection if it exists."""
        mock_producer = MagicMock()
        mock_producer_class.return_value = mock_producer

        service = KafkaService()
        service.producer = mock_producer

        service.close()

        mock_producer.flush.assert_called_once()
        mock_producer.close.assert_called_once()
        assert service.producer is None

    def test_close_handles_no_connections_gracefully(self):
        """Test that close handles case where no connections exist."""
        service = KafkaService()

        # Should not raise any errors
        service.close()

        assert service.consumer is None
        assert service.producer is None
