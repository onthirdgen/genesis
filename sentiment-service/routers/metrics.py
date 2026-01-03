from fastapi import APIRouter, Response
from prometheus_client import Counter, Histogram, Gauge, generate_latest, CONTENT_TYPE_LATEST, REGISTRY

router = APIRouter()

# Define metrics
sentiment_analyses_total = Counter(
    'sentiment_analyses_total',
    'Total number of sentiment analyses performed',
    ['status', 'sentiment']
)

sentiment_analysis_duration_seconds = Histogram(
    'sentiment_analysis_duration_seconds',
    'Time spent analyzing sentiment',
    buckets=[0.01, 0.05, 0.1, 0.5, 1, 2, 5]
)

sentiment_scores = Histogram(
    'sentiment_scores',
    'Distribution of sentiment scores',
    buckets=[-1.0, -0.75, -0.5, -0.25, 0, 0.25, 0.5, 0.75, 1.0]
)

active_analyses = Gauge(
    'active_sentiment_analyses',
    'Number of sentiment analyses currently being processed'
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
