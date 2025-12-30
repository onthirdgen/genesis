from typing import List
from pydantic import BaseModel, Field


class TranscriptionSegment(BaseModel):
    """Represents a single segment from Whisper transcription."""
    id: int
    seek: int
    start: float
    end: float
    text: str
    tokens: List[int]
    temperature: float
    avg_logprob: float
    compression_ratio: float
    no_speech_prob: float


class WhisperResult(BaseModel):
    """Complete result from Whisper transcription."""
    text: str = Field(description="Full transcription text")
    segments: List[TranscriptionSegment] = Field(description="Detailed segments")
    language: str = Field(description="Detected language code")


class ProcessedTranscription(BaseModel):
    """Processed transcription with speaker diarization."""
    full_text: str
    segments: List[dict]
    language: str
    confidence: float
