"""
Configuration for Sentiment Service using Pydantic Settings
"""
from pydantic_settings import BaseSettings
from typing import Optional


class Settings(BaseSettings):
    """Application configuration"""

    # Kafka Configuration
    kafka_bootstrap_servers: str = "localhost:9092"
    kafka_consumer_group: str = "sentiment-service"
    kafka_input_topic: str = "calls.transcribed"
    kafka_output_topic: str = "calls.sentiment-analyzed"
    kafka_auto_offset_reset: str = "earliest"
    kafka_enable_auto_commit: bool = True

    # Model Configuration
    model_name: str = "cardiffnlp/twitter-roberta-base-sentiment-latest"
    use_gpu: bool = False
    model_cache_dir: Optional[str] = None

    # Sentiment Analysis Configuration
    escalation_threshold: float = 0.5  # Sentiment drop threshold for escalation detection
    neutral_range: tuple[float, float] = (-0.2, 0.2)  # Range for neutral sentiment

    # Service Configuration
    service_name: str = "sentiment-service"
    log_level: str = "INFO"

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"
        case_sensitive = False


# Global settings instance
settings = Settings()
