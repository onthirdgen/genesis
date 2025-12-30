from datetime import datetime
from typing import Dict, List, Optional, Any
from pydantic import BaseModel, Field
from uuid import UUID, uuid4


class Segment(BaseModel):
    """Represents a segment of transcribed audio."""
    speaker: str = Field(description="Speaker identifier (agent or customer)")
    startTime: float = Field(description="Start time in seconds")
    endTime: float = Field(description="End time in seconds")
    text: str = Field(description="Transcribed text for this segment")


class EventMetadata(BaseModel):
    """Metadata for event tracking."""
    userId: str = "system"
    service: str = "transcription-service"


class CallReceivedPayload(BaseModel):
    """Payload for CallReceived event."""
    callId: str
    callerId: str
    agentId: str
    channel: str
    startTime: str
    audioFileUrl: str
    audioFormat: str
    audioFileSize: int


class CallReceivedEvent(BaseModel):
    """Event published when a call is received and stored."""
    eventId: str = Field(default_factory=lambda: str(uuid4()))
    eventType: str = "CallReceived"
    aggregateId: str = Field(description="Call ID")
    aggregateType: str = "Call"
    timestamp: str = Field(default_factory=lambda: datetime.utcnow().isoformat())
    version: int = 1
    causationId: Optional[str] = None
    correlationId: str = Field(default_factory=lambda: str(uuid4()))
    metadata: EventMetadata = Field(default_factory=EventMetadata)
    payload: CallReceivedPayload


class TranscriptionData(BaseModel):
    """Transcription result data."""
    fullText: str = Field(description="Complete transcription text")
    segments: List[Segment] = Field(description="Segmented transcription with timestamps")
    language: str = Field(description="Detected language code")
    confidence: float = Field(description="Overall transcription confidence score")


class CallTranscribedPayload(BaseModel):
    """Payload for CallTranscribed event."""
    callId: str
    transcription: TranscriptionData


class CallTranscribedEvent(BaseModel):
    """Event published when a call has been transcribed."""
    eventId: str = Field(default_factory=lambda: str(uuid4()))
    eventType: str = "CallTranscribed"
    aggregateId: str = Field(description="Call ID")
    aggregateType: str = "Call"
    timestamp: str = Field(default_factory=lambda: datetime.utcnow().isoformat())
    version: int = 1
    causationId: str = Field(description="Event ID that caused this event")
    correlationId: str = Field(description="Correlation ID from original event")
    metadata: EventMetadata = Field(default_factory=EventMetadata)
    payload: CallTranscribedPayload
