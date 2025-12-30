from fastapi import APIRouter, Response
from prometheus_client import Counter, Histogram, Gauge, generate_latest, CONTENT_TYPE_LATEST, REGISTRY
import time

router = APIRouter()

# Define metrics
transcriptions_total = Counter(
    'transcriptions_total',
    'Total number of transcriptions processed',
    ['status']
)

transcription_duration_seconds = Histogram(
    'transcription_duration_seconds',
    'Time spent transcribing audio files',
    buckets=[1, 5, 10, 30, 60, 120, 300, 600]
)

audio_file_size_bytes = Histogram(
    'audio_file_size_bytes',
    'Size of audio files processed',
    buckets=[1024, 10240, 102400, 1024000, 10240000, 102400000]
)

active_transcriptions = Gauge(
    'active_transcriptions',
    'Number of transcriptions currently being processed'
)

kafka_messages_consumed = Counter(
    'kafka_messages_consumed_total',
    'Total number of Kafka messages consumed',
    ['topic']
)

kafka_messages_produced = Counter(
    'kafka_messages_produced_total',
    'Total number of Kafka messages produced',
    ['topic']
)


@router.get("/metrics")
async def metrics():
    """
    Prometheus metrics endpoint.
    Returns metrics in Prometheus text format.
    """
    return Response(
        content=generate_latest(REGISTRY),
        media_type=CONTENT_TYPE_LATEST
    )
