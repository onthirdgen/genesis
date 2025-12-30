import asyncio
import json
import logging
from typing import AsyncGenerator
from kafka import KafkaConsumer, KafkaProducer
from kafka.errors import KafkaError
from models.events import CallReceivedEvent, CallTranscribedEvent
from config import settings

logger = logging.getLogger(__name__)


class KafkaService:
    """Service for interacting with Kafka for event streaming."""

    TOPIC_CALLS_RECEIVED = "calls.received"
    TOPIC_CALLS_TRANSCRIBED = "calls.transcribed"
    CONSUMER_GROUP = "transcription-service"

    def __init__(self):
        """Initialize Kafka service (connections created on demand)."""
        self.consumer = None
        self.producer = None
        logger.info("KafkaService initialized")

    def create_consumer(self) -> KafkaConsumer:
        """
        Create and configure Kafka consumer.

        Returns:
            Configured KafkaConsumer
        """
        try:
            logger.info(f"Creating Kafka consumer for topic: {self.TOPIC_CALLS_RECEIVED}")
            consumer = KafkaConsumer(
                self.TOPIC_CALLS_RECEIVED,
                bootstrap_servers=settings.kafka_bootstrap_servers,
                group_id=self.CONSUMER_GROUP,
                auto_offset_reset='earliest',
                enable_auto_commit=True,
                value_deserializer=lambda m: json.loads(m.decode('utf-8'))
            )
            logger.info(f"Kafka consumer created successfully")
            return consumer
        except Exception as e:
            logger.error(f"Failed to create Kafka consumer: {e}")
            raise

    def create_producer(self) -> KafkaProducer:
        """
        Create and configure Kafka producer.

        Returns:
            Configured KafkaProducer
        """
        try:
            logger.info("Creating Kafka producer")
            producer = KafkaProducer(
                bootstrap_servers=settings.kafka_bootstrap_servers,
                value_serializer=lambda v: json.dumps(v).encode('utf-8'),
                acks='all',  # Wait for all replicas to acknowledge
                retries=3
            )
            logger.info("Kafka producer created successfully")
            return producer
        except Exception as e:
            logger.error(f"Failed to create Kafka producer: {e}")
            raise

    async def consume_call_received(self) -> AsyncGenerator[CallReceivedEvent, None]:
        """
        Asynchronously consume CallReceived events from Kafka.

        Uses poll() with configurable timeout to allow graceful shutdown and
        proper async integration. The consumer will periodically check for new
        messages and yield control back to the async event loop.

        Yields:
            CallReceivedEvent instances
        """
        if self.consumer is None:
            self.consumer = self.create_consumer()

        logger.info(
            f"Starting to consume CallReceived events "
            f"(poll_timeout={settings.kafka_consumer_timeout_ms}ms, "
            f"retry_delay={settings.kafka_consumer_retry_delay_ms}ms)"
        )

        try:
            # Infinite loop with poll() instead of iterator
            while True:
                try:
                    # Poll for messages with configurable timeout
                    messages = self.consumer.poll(timeout_ms=settings.kafka_consumer_timeout_ms)

                    # Process all messages from all partitions
                    for topic_partition, records in messages.items():
                        for message in records:
                            try:
                                # Parse message into CallReceivedEvent
                                event_data = message.value
                                event = CallReceivedEvent(**event_data)

                                logger.info(
                                    f"Received event - ID: {event.eventId}, "
                                    f"CallID: {event.aggregateId}, "
                                    f"Correlation: {event.correlationId}"
                                )

                                yield event

                            except Exception as e:
                                logger.error(f"Error parsing CallReceived event: {e}")
                                logger.error(f"Raw message: {message.value}")
                                # Continue processing other messages
                                continue

                    # No messages received in this poll cycle, yield control to event loop
                    if not messages:
                        await asyncio.sleep(0)  # Yield to event loop

                except KeyboardInterrupt:
                    logger.info("Consumer interrupted by keyboard")
                    break
                except Exception as e:
                    logger.error(f"Error consuming messages: {e}", exc_info=True)
                    # Brief delay before retrying (configurable)
                    await asyncio.sleep(settings.kafka_consumer_retry_delay_ms / 1000.0)
                    # Continue the loop to retry

        finally:
            if self.consumer:
                logger.info("Closing Kafka consumer")
                self.consumer.close()
                self.consumer = None

    def publish_call_transcribed(self, event: CallTranscribedEvent) -> bool:
        """
        Publish CallTranscribed event to Kafka.

        Args:
            event: CallTranscribedEvent to publish

        Returns:
            True if published successfully, False otherwise
        """
        if self.producer is None:
            self.producer = self.create_producer()

        try:
            # Convert event to dict for serialization
            event_dict = event.model_dump(mode='json')

            logger.info(
                f"Publishing CallTranscribed event - "
                f"CallID: {event.aggregateId}, "
                f"EventID: {event.eventId}"
            )

            # Send message to Kafka
            future = self.producer.send(
                self.TOPIC_CALLS_TRANSCRIBED,
                value=event_dict
            )

            # Block until message is sent (with timeout)
            record_metadata = future.get(timeout=10)

            logger.info(
                f"Event published successfully - "
                f"Topic: {record_metadata.topic}, "
                f"Partition: {record_metadata.partition}, "
                f"Offset: {record_metadata.offset}"
            )

            return True

        except KafkaError as e:
            logger.error(f"Kafka error publishing event: {e}")
            return False
        except Exception as e:
            logger.error(f"Error publishing CallTranscribed event: {e}")
            return False

    def health_check(self) -> bool:
        """
        Check if Kafka is accessible.

        Returns:
            True if Kafka is accessible, False otherwise
        """
        try:
            # Try to create a temporary producer to test connection
            test_producer = KafkaProducer(
                bootstrap_servers=settings.kafka_bootstrap_servers,
                request_timeout_ms=5000
            )
            test_producer.close()
            return True
        except Exception as e:
            logger.error(f"Kafka health check failed: {e}")
            return False

    def close(self):
        """Close Kafka connections."""
        if self.consumer:
            logger.info("Closing Kafka consumer")
            self.consumer.close()
            self.consumer = None

        if self.producer:
            logger.info("Closing Kafka producer")
            self.producer.flush()
            self.producer.close()
            self.producer = None


# Singleton instance
kafka_service = KafkaService()
