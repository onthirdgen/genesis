"""
Kafka service for consuming and publishing events
"""
import asyncio
import json
import logging
from typing import AsyncGenerator, Optional
from kafka import KafkaConsumer, KafkaProducer
from kafka.errors import KafkaError

from config import settings
from models.events import CallTranscribedEvent, SentimentAnalyzedEvent

logger = logging.getLogger(__name__)


class KafkaService:
    """
    Kafka integration for consuming CallTranscribed events and publishing SentimentAnalyzed events
    """

    def __init__(self):
        self.consumer: Optional[KafkaConsumer] = None
        self.producer: Optional[KafkaProducer] = None

    def initialize_consumer(self) -> None:
        """Initialize Kafka consumer for transcribed calls"""
        try:
            self.consumer = KafkaConsumer(
                settings.kafka_input_topic,
                bootstrap_servers=settings.kafka_bootstrap_servers,
                group_id=settings.kafka_consumer_group,
                auto_offset_reset=settings.kafka_auto_offset_reset,
                enable_auto_commit=settings.kafka_enable_auto_commit,
                value_deserializer=lambda m: json.loads(m.decode('utf-8')),
                consumer_timeout_ms=1000  # Return control every second
            )
            logger.info(
                f"Kafka consumer initialized: topic={settings.kafka_input_topic}, "
                f"group={settings.kafka_consumer_group}"
            )
        except KafkaError as e:
            logger.error(f"Failed to initialize Kafka consumer: {e}")
            raise

    def initialize_producer(self) -> None:
        """Initialize Kafka producer for sentiment events"""
        try:
            self.producer = KafkaProducer(
                bootstrap_servers=settings.kafka_bootstrap_servers,
                value_serializer=lambda v: json.dumps(v, default=str).encode('utf-8'),
                acks='all',  # Wait for all replicas
                retries=3
            )
            logger.info("Kafka producer initialized")
        except KafkaError as e:
            logger.error(f"Failed to initialize Kafka producer: {e}")
            raise

    async def consume_transcribed(self) -> AsyncGenerator[CallTranscribedEvent, None]:
        """
        Asynchronously consume CallTranscribed events from Kafka

        Uses poll() with timeout to allow graceful shutdown and proper async integration.
        The consumer will periodically check for new messages and yield control back
        to the async event loop.

        Yields: CallTranscribedEvent instances
        """
        if not self.consumer:
            raise RuntimeError("Consumer not initialized. Call initialize_consumer() first.")

        logger.info(f"Starting to consume from topic: {settings.kafka_input_topic}")

        try:
            while True:
                try:
                    # Poll for messages with timeout (1000ms from consumer_timeout_ms config)
                    messages = self.consumer.poll(timeout_ms=1000)

                    # Process all messages from all partitions
                    for topic_partition, records in messages.items():
                        for message in records:
                            try:
                                event_data = message.value
                                logger.info(
                                    f"Received event: {event_data.get('eventType')} "
                                    f"for call {event_data.get('aggregateId')}"
                                )

                                # Parse into Pydantic model
                                event = CallTranscribedEvent(**event_data)
                                yield event

                            except Exception as e:
                                logger.error(f"Error processing message: {e}")
                                logger.debug(f"Message value: {message.value}")
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
                    # Brief delay before retrying
                    await asyncio.sleep(1.0)
                    # Continue the loop to retry

        finally:
            logger.info("Stopping Kafka consumer")

    def publish_sentiment(self, event: SentimentAnalyzedEvent) -> bool:
        """
        Publish SentimentAnalyzed event to Kafka
        Returns: True if successful, False otherwise
        """
        if not self.producer:
            raise RuntimeError("Producer not initialized. Call initialize_producer() first.")

        try:
            # Convert Pydantic model to dict
            event_dict = event.model_dump(mode='json')

            # Send to Kafka
            future = self.producer.send(settings.kafka_output_topic, value=event_dict)

            # Wait for acknowledgment (with timeout)
            record_metadata = future.get(timeout=10)

            logger.info(
                f"Published SentimentAnalyzed event for call {event.aggregateId} "
                f"to topic {record_metadata.topic} partition {record_metadata.partition} "
                f"offset {record_metadata.offset}"
            )

            return True

        except KafkaError as e:
            logger.error(f"Failed to publish event: {e}")
            return False
        except Exception as e:
            logger.error(f"Unexpected error publishing event: {e}")
            return False

    def close(self) -> None:
        """Close Kafka consumer and producer connections"""
        if self.consumer:
            self.consumer.close()
            logger.info("Kafka consumer closed")

        if self.producer:
            self.producer.flush()
            self.producer.close()
            logger.info("Kafka producer closed")
