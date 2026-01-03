"""
Sentiment Service - FastAPI application for sentiment analysis of call transcriptions

This service:
1. Consumes CallTranscribed events from Kafka
2. Analyzes sentiment using RoBERTa (with VADER fallback)
3. Detects escalation patterns
4. Publishes SentimentAnalyzed events
"""
import asyncio
import logging
import time
from contextlib import asynccontextmanager
from fastapi import FastAPI
from uuid import uuid4

from config import settings
from services.sentiment_service import SentimentAnalyzer
from services.kafka_service import KafkaService
from routers.health import router as health_router, set_model_loaded, set_kafka_ready
from routers.metrics import router as metrics_router
from models.events import SentimentAnalyzedEvent, SentimentAnalyzedEventPayload

# Configure logging
logging.basicConfig(
    level=getattr(logging, settings.log_level),
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Global instances
sentiment_analyzer = SentimentAnalyzer()
kafka_service = KafkaService()
processing_task = None


async def process_transcriptions():
    """
    Background task to process transcription events
    Runs continuously and processes events from Kafka
    """
    logger.info("Starting transcription processing loop")

    try:
        # Process events from Kafka consumer (async generator)
        async for event in kafka_service.consume_transcribed():
            process_single_event(event)

    except Exception as e:
        logger.error(f"Error in processing loop: {e}", exc_info=True)


def process_single_event(event):
    """
    Process a single CallTranscribed event
    """
    start_time = time.time()
    call_id = event.aggregateId

    try:
        logger.info(f"Processing sentiment analysis for call {call_id}")

        # Extract transcription segments
        segments = event.payload.segments

        if not segments:
            logger.warning(f"No segments found for call {call_id}")
            return

        # Analyze sentiment for each segment
        logger.info(f"Analyzing {len(segments)} segments")
        sentiment_segments = sentiment_analyzer.analyze_segments(segments)

        # Calculate overall sentiment (weighted by duration)
        overall_sentiment, overall_score = sentiment_analyzer.calculate_overall_sentiment(
            sentiment_segments
        )

        # Detect escalation
        escalation_detected, escalation_details = sentiment_analyzer.detect_escalation(
            sentiment_segments
        )

        # Calculate processing time
        processing_time_ms = (time.time() - start_time) * 1000

        # Create SentimentAnalyzed event
        sentiment_event = SentimentAnalyzedEvent(
            eventId=uuid4(),
            aggregateId=call_id,
            causationId=event.eventId,
            correlationId=event.correlationId,
            metadata={
                "service": settings.service_name,
                "modelName": settings.model_name,
                "usedFallback": sentiment_analyzer.use_vader_fallback
            },
            payload=SentimentAnalyzedEventPayload(
                callId=call_id,
                overallSentiment=overall_sentiment,
                sentimentScore=round(overall_score, 3),
                segments=sentiment_segments,
                escalationDetected=escalation_detected,
                escalationDetails=escalation_details if escalation_detected else None,
                processingTimeMs=round(processing_time_ms, 2)
            )
        )

        # Publish to Kafka
        success = kafka_service.publish_sentiment(sentiment_event)

        if success:
            logger.info(
                f"Successfully processed call {call_id}: "
                f"sentiment={overall_sentiment}, score={overall_score:.3f}, "
                f"escalation={escalation_detected}, time={processing_time_ms:.2f}ms"
            )
        else:
            logger.error(f"Failed to publish sentiment event for call {call_id}")

    except Exception as e:
        logger.error(f"Error processing call {call_id}: {e}", exc_info=True)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Lifespan context manager for startup and shutdown events
    """
    # Startup
    logger.info("Starting Sentiment Service")

    # Load ML model
    try:
        logger.info("Loading sentiment analysis model...")
        sentiment_analyzer.load_model()
        set_model_loaded(sentiment_analyzer.model_loaded)
        logger.info("Model loaded successfully")
    except Exception as e:
        logger.error(f"Failed to load model: {e}")
        set_model_loaded(False)

    # Initialize Kafka
    try:
        logger.info("Initializing Kafka connections...")
        kafka_service.initialize_consumer()
        kafka_service.initialize_producer()
        set_kafka_ready(True, True)
        logger.info("Kafka initialized successfully")
    except Exception as e:
        logger.error(f"Failed to initialize Kafka: {e}")
        set_kafka_ready(False, False)

    # Start background processing task
    global processing_task
    processing_task = asyncio.create_task(process_transcriptions())
    logger.info("Background processing task started")

    logger.info("Sentiment Service is ready")

    yield

    # Shutdown
    logger.info("Shutting down Sentiment Service")

    # Cancel background task
    if processing_task:
        processing_task.cancel()
        try:
            await processing_task
        except asyncio.CancelledError:
            logger.info("Background task cancelled")

    # Close Kafka connections
    kafka_service.close()

    logger.info("Sentiment Service shutdown complete")


# Create FastAPI application
app = FastAPI(
    title="Sentiment Service",
    description="Analyzes sentiment of call transcriptions using RoBERTa and VADER",
    version="1.0.0",
    lifespan=lifespan
)

# Include routers
app.include_router(health_router)
app.include_router(metrics_router)


@app.get("/")
async def root():
    """Root endpoint"""
    return {
        "service": "sentiment-service",
        "version": "1.0.0",
        "description": "Call sentiment analysis service",
        "status": "running"
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
