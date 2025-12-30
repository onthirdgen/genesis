import os
import tempfile
import logging
from urllib.parse import urlparse
from minio import Minio
from minio.error import S3Error
from config import settings

logger = logging.getLogger(__name__)


class MinioService:
    """Service for interacting with MinIO object storage."""

    def __init__(self):
        """Initialize MinIO client."""
        self.client = Minio(
            settings.minio_endpoint,
            access_key=settings.minio_access_key,
            secret_key=settings.minio_secret_key,
            secure=False  # Using HTTP in development
        )
        self.bucket = settings.minio_bucket
        logger.info(f"MinIO client initialized for bucket: {self.bucket}")

    def download_file(self, audio_url: str) -> str:
        """
        Download audio file from MinIO to a temporary location.

        Args:
            audio_url: URL or object key for the audio file in MinIO

        Returns:
            Path to the downloaded temporary file

        Raises:
            S3Error: If download fails
        """
        try:
            # Parse the URL to extract object key
            # URL format: http://minio:9000/calls/2024/12/call-id.wav
            # or just: 2024/12/call-id.wav
            if audio_url.startswith("http"):
                parsed = urlparse(audio_url)
                # Remove leading slash and bucket name if present
                object_key = parsed.path.lstrip('/')
                if object_key.startswith(f"{self.bucket}/"):
                    object_key = object_key[len(f"{self.bucket}/"):]
            else:
                object_key = audio_url

            logger.info(f"Downloading audio file: {object_key} from bucket: {self.bucket}")

            # Get file extension from object key
            _, ext = os.path.splitext(object_key)

            # Create temporary file with same extension
            temp_file = tempfile.NamedTemporaryFile(
                delete=False,
                suffix=ext,
                prefix="audio_"
            )
            temp_path = temp_file.name
            temp_file.close()

            # Download file from MinIO
            self.client.fget_object(
                bucket_name=self.bucket,
                object_name=object_key,
                file_path=temp_path
            )

            logger.info(f"Audio file downloaded to: {temp_path}")
            return temp_path

        except S3Error as e:
            logger.error(f"Error downloading file from MinIO: {e}")
            raise
        except Exception as e:
            logger.error(f"Unexpected error downloading file: {e}")
            raise

    def health_check(self) -> bool:
        """
        Check if MinIO is accessible.

        Returns:
            True if MinIO is accessible, False otherwise
        """
        try:
            # Check if bucket exists
            exists = self.client.bucket_exists(self.bucket)
            if not exists:
                logger.warning(f"Bucket {self.bucket} does not exist")
                return False
            return True
        except Exception as e:
            logger.error(f"MinIO health check failed: {e}")
            return False


# Singleton instance
minio_service = MinioService()
