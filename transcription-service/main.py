import asyncio
import logging
import os
from contextlib import asynccontextmanager
from fastapi import FastAPI
from routers import health, metrics
from services.kafka_service import kafka_service
from services.minio_service import minio_service
from services.whisper_service import whisper_service
from models.events import CallTranscribedEvent, CallTranscribedPayload, TranscriptionData, Segment

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Set Prometheus client logging to WARN level (reduces noise)
logging.getLogger('prometheus_client').setLevel(logging.WARN)

# Global flag for background task
processing_active = False


async def process_transcription_events():
    """
    Background task that consumes CallReceived events and processes transcriptions.

    The consumer uses poll() with configurable timeout, allowing graceful shutdown
    by checking the processing_active flag periodically.
    """
    global processing_active
    processing_active = True

    logger.info("Starting transcription event processor")

    try:
        async for event in kafka_service.consume_call_received():
            # Check if shutdown was requested
            if not processing_active:
                logger.info("Processing stopped, exiting event loop")
                break

            try:
                logger.info(f"Processing call: {event.aggregateId}")

                # Extract audio URL from event payload
                audio_url = event.payload.audioFileUrl
                call_id = event.payload.callId

                # Download audio file from MinIO
                logger.info(f"Downloading audio file from MinIO: {audio_url}")
                audio_path = minio_service.download_file(audio_url)

                try:
                    # Transcribe the audio
                    logger.info(f"Transcribing audio file: {audio_path}")
                    transcription = whisper_service.transcribe(audio_path)

                    # Convert segments to event model format
                    segments = [
                        Segment(
                            speaker=seg["speaker"],
                            startTime=seg["startTime"],
                            endTime=seg["endTime"],
                            text=seg["text"]
                        )
                        for seg in transcription.segments
                    ]

                    # Build CallTranscribed event
                    transcribed_event = CallTranscribedEvent(
                        aggregateId=call_id,
                        causationId=event.eventId,
                        correlationId=event.correlationId,
                        payload=CallTranscribedPayload(
                            callId=call_id,
                            transcription=TranscriptionData(
                                fullText=transcription.full_text,
                                segments=segments,
                                language=transcription.language,
                                confidence=transcription.confidence
                            )
                        )
                    )

                    # Publish to Kafka
                    logger.info(f"Publishing CallTranscribed event for call: {call_id}")
                    success = kafka_service.publish_call_transcribed(transcribed_event)

                    if success:
                        logger.info(f"Successfully processed and published transcription for call: {call_id}")
                    else:
                        logger.error(f"Failed to publish transcription event for call: {call_id}")

                finally:
                    # Clean up temporary audio file
                    if os.path.exists(audio_path):
                        os.remove(audio_path)
                        logger.info(f"Cleaned up temporary file: {audio_path}")

            except Exception as e:
                logger.error(f"Error processing call {event.aggregateId}: {e}", exc_info=True)
                # Continue processing other events
                continue

    except Exception as e:
        logger.error(f"Fatal error in transcription processor: {e}", exc_info=True)
    finally:
        processing_active = False
        logger.info("Transcription event processor stopped")


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Lifespan context manager for FastAPI application.
    Handles startup and shutdown events.
    """
    # Startup
    logger.info("Starting Transcription Service")

    # Load Whisper model on startup
    logger.info("Loading Whisper model...")
    try:
        whisper_service.load_model()
        logger.info("Whisper model loaded successfully")
    except Exception as e:
        logger.error(f"Failed to load Whisper model: {e}")
        # Don't fail startup, model will be loaded on first use

    # Start background task for processing events
    logger.info("Starting background event processor")
    task = asyncio.create_task(process_transcription_events())

    logger.info("Transcription Service started successfully")

    yield

    # Shutdown
    logger.info("Shutting down Transcription Service")

    # Stop background task
    global processing_active
    processing_active = False

    # Wait for task to complete (with timeout)
    try:
        await asyncio.wait_for(task, timeout=10.0)
    except asyncio.TimeoutError:
        logger.warning("Background task did not complete in time, cancelling")
        task.cancel()

    # Close Kafka connections
    kafka_service.close()

    logger.info("Transcription Service shutdown complete")


# Create FastAPI application
app = FastAPI(
    title="Transcription Service",
    description="Audio transcription service using OpenAI Whisper",
    version="1.0.0",
    lifespan=lifespan
)

# Include routers
app.include_router(health.router, tags=["health"])
app.include_router(metrics.router, tags=["metrics"])


@app.get("/")
async def root():
    """Root endpoint."""
    return {
        "service": "transcription-service",
        "status": "running",
        "version": "1.0.0"
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
