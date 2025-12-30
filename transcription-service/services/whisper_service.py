import logging
import whisper
import numpy as np
from typing import List, Dict
from models.transcription import ProcessedTranscription
from models.events import Segment
from config import settings

logger = logging.getLogger(__name__)


class WhisperService:
    """Service for transcribing audio using OpenAI Whisper."""

    def __init__(self):
        """Initialize Whisper service (model loaded on first use)."""
        self.model = None
        self.model_size = settings.model_size
        logger.info(f"WhisperService initialized (model will be loaded on demand: {self.model_size})")

    def load_model(self):
        """Load the Whisper model if not already loaded."""
        if self.model is None:
            logger.info(f"Loading Whisper model: {self.model_size}")
            try:
                self.model = whisper.load_model(self.model_size)
                logger.info(f"Whisper model loaded successfully: {self.model_size}")
            except Exception as e:
                logger.error(f"Failed to load Whisper model: {e}")
                raise

    def transcribe(self, audio_path: str) -> ProcessedTranscription:
        """
        Transcribe audio file using Whisper.

        Args:
            audio_path: Path to the audio file

        Returns:
            ProcessedTranscription with speaker diarization
        """
        # Ensure model is loaded
        self.load_model()

        logger.info(f"Starting transcription for: {audio_path}")

        try:
            # Transcribe with Whisper
            result = self.model.transcribe(
                audio_path,
                verbose=False,
                language=None,  # Auto-detect language
                task="transcribe"
            )

            logger.info(f"Transcription complete. Language: {result['language']}")

            # Process segments with basic speaker diarization
            processed_segments = self._add_speaker_diarization(result['segments'])

            # Calculate average confidence from segments
            confidence = self._calculate_confidence(result['segments'])

            return ProcessedTranscription(
                full_text=result['text'].strip(),
                segments=processed_segments,
                language=result['language'],
                confidence=confidence
            )

        except Exception as e:
            logger.error(f"Error during transcription: {e}")
            raise

    def _add_speaker_diarization(self, segments: List[Dict]) -> List[Dict]:
        """
        Add basic speaker diarization based on pauses.
        Alternates between 'agent' and 'customer' based on silence gaps.

        Args:
            segments: Raw Whisper segments

        Returns:
            Segments with speaker labels
        """
        PAUSE_THRESHOLD = 1.5  # seconds of silence to indicate speaker change

        processed = []
        current_speaker = "agent"  # Assume agent speaks first
        last_end_time = 0.0

        for segment in segments:
            start_time = segment['start']
            end_time = segment['end']
            text = segment['text'].strip()

            # Check for pause indicating speaker change
            pause_duration = start_time - last_end_time
            if pause_duration > PAUSE_THRESHOLD and len(processed) > 0:
                # Toggle speaker
                current_speaker = "customer" if current_speaker == "agent" else "agent"

            processed.append({
                "speaker": current_speaker,
                "startTime": round(start_time, 2),
                "endTime": round(end_time, 2),
                "text": text
            })

            last_end_time = end_time

        logger.info(f"Processed {len(processed)} segments with speaker diarization")
        return processed

    def _calculate_confidence(self, segments: List[Dict]) -> float:
        """
        Calculate average confidence score from segments.
        Uses avg_logprob and no_speech_prob as confidence indicators.

        Args:
            segments: Whisper segments

        Returns:
            Confidence score between 0 and 1
        """
        if not segments:
            return 0.0

        # Whisper doesn't directly provide confidence, but we can estimate it
        # from avg_logprob and no_speech_prob
        confidences = []

        for segment in segments:
            # avg_logprob is typically between -1 and 0 (higher is better)
            # no_speech_prob is between 0 and 1 (lower is better)
            avg_logprob = segment.get('avg_logprob', -1.0)
            no_speech_prob = segment.get('no_speech_prob', 0.5)

            # Convert avg_logprob to 0-1 scale (assume -2 to 0 range)
            logprob_confidence = max(0, min(1, (avg_logprob + 2) / 2))

            # Invert no_speech_prob
            speech_confidence = 1 - no_speech_prob

            # Average the two metrics
            segment_confidence = (logprob_confidence + speech_confidence) / 2
            confidences.append(segment_confidence)

        # Return weighted average (longer segments weighted more)
        total_duration = sum(s['end'] - s['start'] for s in segments)
        if total_duration == 0:
            return np.mean(confidences)

        weighted_sum = sum(
            conf * (seg['end'] - seg['start'])
            for conf, seg in zip(confidences, segments)
        )

        confidence = weighted_sum / total_duration
        return round(confidence, 3)


# Singleton instance
whisper_service = WhisperService()
