"""
Health check endpoints for monitoring and orchestration
"""
from fastapi import APIRouter, status
from pydantic import BaseModel
from typing import Optional
import time

router = APIRouter(tags=["health"])

# Track service start time
_start_time = time.time()


class HealthResponse(BaseModel):
    """Health check response model"""
    status: str
    service: str
    version: str
    uptime_seconds: float
    model_loaded: bool
    kafka_connected: Optional[bool] = None


class ReadinessResponse(BaseModel):
    """Readiness check response model"""
    ready: bool
    checks: dict


# Global state for health checks
_health_state = {
    "model_loaded": False,
    "kafka_consumer_ready": False,
    "kafka_producer_ready": False
}


def set_model_loaded(loaded: bool) -> None:
    """Update model loaded state"""
    _health_state["model_loaded"] = loaded


def set_kafka_ready(consumer_ready: bool, producer_ready: bool) -> None:
    """Update Kafka connection state"""
    _health_state["kafka_consumer_ready"] = consumer_ready
    _health_state["kafka_producer_ready"] = producer_ready


@router.get("/health", response_model=HealthResponse, status_code=status.HTTP_200_OK)
async def health_check():
    """
    Basic health check endpoint
    Returns 200 if service is running
    """
    uptime = time.time() - _start_time

    return HealthResponse(
        status="healthy",
        service="sentiment-service",
        version="1.0.0",
        uptime_seconds=round(uptime, 2),
        model_loaded=_health_state["model_loaded"],
        kafka_connected=_health_state["kafka_consumer_ready"] and _health_state["kafka_producer_ready"]
    )


@router.get("/health/ready", response_model=ReadinessResponse)
async def readiness_check():
    """
    Readiness check endpoint
    Returns ready=true only if all dependencies are initialized
    """
    checks = {
        "model_loaded": _health_state["model_loaded"],
        "kafka_consumer": _health_state["kafka_consumer_ready"],
        "kafka_producer": _health_state["kafka_producer_ready"]
    }

    all_ready = all(checks.values())

    return ReadinessResponse(
        ready=all_ready,
        checks=checks
    )


@router.get("/health/live", status_code=status.HTTP_200_OK)
async def liveness_check():
    """
    Liveness check endpoint
    Returns 200 if service process is running
    """
    return {"status": "alive"}
