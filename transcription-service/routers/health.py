import logging
from fastapi import APIRouter, Response, status
from services.minio_service import minio_service
from services.kafka_service import kafka_service

logger = logging.getLogger(__name__)

router = APIRouter()


@router.get("/health")
async def health_check():
    """
    Basic health check endpoint.

    Returns:
        Health status
    """
    return {"status": "healthy", "service": "transcription-service"}


@router.get("/ready")
async def readiness_check(response: Response):
    """
    Readiness check that verifies connectivity to dependencies.

    Returns:
        Readiness status with dependency checks
    """
    checks = {
        "minio": False,
        "kafka": False
    }

    # Check MinIO
    try:
        checks["minio"] = minio_service.health_check()
    except Exception as e:
        logger.error(f"MinIO health check failed: {e}")

    # Check Kafka
    try:
        checks["kafka"] = kafka_service.health_check()
    except Exception as e:
        logger.error(f"Kafka health check failed: {e}")

    # Determine overall readiness
    is_ready = all(checks.values())

    if not is_ready:
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE

    return {
        "status": "ready" if is_ready else "not_ready",
        "service": "transcription-service",
        "dependencies": checks
    }
