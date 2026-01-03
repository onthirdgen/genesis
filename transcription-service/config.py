from pydantic_settings import BaseSettings
from pydantic import ConfigDict


class Settings(BaseSettings):
    kafka_bootstrap_servers: str = "kafka:9092"
    minio_endpoint: str = "minio:9000"
    minio_access_key: str = "minioadmin"
    minio_secret_key: str = "minioadmin"
    minio_bucket: str = "calls"

    # Kafka Consumer Configuration
    kafka_consumer_timeout_ms: int = 1000  # Timeout for consumer poll operations
    kafka_consumer_retry_delay_ms: int = 50  # Delay before retrying after errors

    # Whisper Model Configuration
    # Available models: tiny, base, small, medium, large
    # Change this value to use a different model - this is the primary config
    # Can be overridden by MODEL_SIZE environment variable if needed
    model_size: str = "small"

    model_config = ConfigDict(
        env_prefix="",
        protected_namespaces=()  # Disable protected namespace warnings
    )


settings = Settings()
