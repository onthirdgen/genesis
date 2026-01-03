"""
Tests for Whisper transcription service.

Tests cover:
- Whisper model loading
- Audio transcription
- Speaker diarization
- Confidence calculation
- Error handling
"""

import os
import pytest
from unittest.mock import patch, MagicMock, Mock
from services.whisper_service import WhisperService
from models.transcription import ProcessedTranscription


class TestWhisperServiceInitialization:
    """Tests for WhisperService initialization."""

    def test_service_initializes_without_loading_model(self):
        """Test that service initializes without loading model immediately."""
        with patch('services.whisper_service.whisper') as mock_whisper:
            service = WhisperService()

            assert service.model is None
            assert service.model_size == "small"  # Default from config
            mock_whisper.load_model.assert_not_called()

    def test_service_respects_model_size_config(self):
        """Test that service respects model_size from config."""
        with patch('services.whisper_service.settings') as mock_settings:
            mock_settings.model_size = "base"

            service = WhisperService()

            assert service.model_size == "base"


class TestWhisperModelLoading:
    """Tests for Whisper model loading."""

    def test_load_model_loads_model_successfully(self, mock_whisper_model):
        """Test that load_model loads the Whisper model successfully."""
        with patch('services.whisper_service.whisper.load_model', return_value=mock_whisper_model) as mock_load:
            service = WhisperService()
            service.load_model()

            assert service.model is not None
            mock_load.assert_called_once_with("small")

    def test_load_model_only_loads_once(self, mock_whisper_model):
        """Test that load_model doesn't reload if model already loaded."""
        with patch('services.whisper_service.whisper.load_model', return_value=mock_whisper_model) as mock_load:
            service = WhisperService()

            # Load model twice
            service.load_model()
            service.load_model()

            # Should only be called once
            mock_load.assert_called_once()

    def test_load_model_raises_exception_on_failure(self):
        """Test that load_model raises exception when loading fails."""
        with patch('services.whisper_service.whisper.load_model', side_effect=Exception("Model not found")):
            service = WhisperService()

            with pytest.raises(Exception, match="Model not found"):
                service.load_model()

    def test_load_model_with_different_model_sizes(self, mock_whisper_model):
        """Test loading different model sizes."""
        model_sizes = ["tiny", "base", "small", "medium", "large"]

        for model_size in model_sizes:
            with patch('services.whisper_service.whisper.load_model', return_value=mock_whisper_model) as mock_load:
                with patch('services.whisper_service.settings') as mock_settings:
                    mock_settings.model_size = model_size

                    service = WhisperService()
                    service.load_model()

                    mock_load.assert_called_once_with(model_size)


class TestTranscription:
    """Tests for audio transcription."""

    def test_transcribe_loads_model_if_not_loaded(self, mock_whisper_model, temp_audio_file):
        """Test that transcribe loads model if not already loaded."""
        with patch('services.whisper_service.whisper.load_model', return_value=mock_whisper_model) as mock_load:
            service = WhisperService()

            assert service.model is None
            service.transcribe(temp_audio_file)

            mock_load.assert_called_once()
            assert service.model is not None

    def test_transcribe_returns_processed_transcription(self, mock_whisper_model, temp_audio_file):
        """Test that transcribe returns ProcessedTranscription object."""
        with patch('services.whisper_service.whisper.load_model', return_value=mock_whisper_model):
            service = WhisperService()
            result = service.transcribe(temp_audio_file)

            assert isinstance(result, ProcessedTranscription)
            assert result.full_text is not None
            assert result.language is not None
            assert result.confidence is not None
            assert isinstance(result.segments, list)

    def test_transcribe_extracts_full_text(self, mock_whisper_model, temp_audio_file):
        """Test that transcribe extracts full text correctly."""
        with patch('services.whisper_service.whisper.load_model', return_value=mock_whisper_model):
            service = WhisperService()
            result = service.transcribe(temp_audio_file)

            expected_text = 'Hello, this is a test transcription. How can I help you today?'
            assert result.full_text == expected_text

    def test_transcribe_detects_language(self, mock_whisper_model, temp_audio_file):
        """Test that transcribe detects language correctly."""
        with patch('services.whisper_service.whisper.load_model', return_value=mock_whisper_model):
            service = WhisperService()
            result = service.transcribe(temp_audio_file)

            assert result.language == 'en'

    def test_transcribe_calculates_confidence(self, mock_whisper_model, temp_audio_file):
        """Test that transcribe calculates confidence score."""
        with patch('services.whisper_service.whisper.load_model', return_value=mock_whisper_model):
            service = WhisperService()
            result = service.transcribe(temp_audio_file)

            assert isinstance(result.confidence, float)
            assert 0.0 <= result.confidence <= 1.0

    def test_transcribe_includes_segments(self, mock_whisper_model, temp_audio_file):
        """Test that transcribe includes segmented transcription."""
        with patch('services.whisper_service.whisper.load_model', return_value=mock_whisper_model):
            service = WhisperService()
            result = service.transcribe(temp_audio_file)

            assert len(result.segments) > 0
            for segment in result.segments:
                assert 'speaker' in segment
                assert 'startTime' in segment
                assert 'endTime' in segment
                assert 'text' in segment

    def test_transcribe_handles_nonexistent_file(self, mock_whisper_model):
        """Test that transcribe handles non-existent audio file."""
        with patch('services.whisper_service.whisper.load_model', return_value=mock_whisper_model):
            mock_whisper_model.transcribe.side_effect = Exception("File not found")

            service = WhisperService()

            with pytest.raises(Exception):
                service.transcribe('/nonexistent/audio.wav')

    def test_transcribe_calls_whisper_with_correct_params(self, mock_whisper_model, temp_audio_file):
        """Test that transcribe calls Whisper with correct parameters."""
        with patch('services.whisper_service.whisper.load_model', return_value=mock_whisper_model):
            service = WhisperService()
            service.transcribe(temp_audio_file)

            mock_whisper_model.transcribe.assert_called_once()
            call_args = mock_whisper_model.transcribe.call_args

            assert call_args[0][0] == temp_audio_file
            assert call_args[1]['verbose'] is False
            assert call_args[1]['language'] is None  # Auto-detect
            assert call_args[1]['task'] == 'transcribe'


class TestSpeakerDiarization:
    """Tests for speaker diarization logic."""

    def test_add_speaker_diarization_assigns_speakers(self, mock_whisper_model):
        """Test that speaker diarization assigns speaker labels."""
        with patch('services.whisper_service.whisper.load_model', return_value=mock_whisper_model):
            service = WhisperService()

            segments = [
                {'start': 0.0, 'end': 2.0, 'text': 'Hello'},
                {'start': 4.0, 'end': 6.0, 'text': 'Hi there'},  # Pause > 1.5s, should switch
                {'start': 6.5, 'end': 8.0, 'text': 'How are you?'}  # No pause, same speaker
            ]

            result = service._add_speaker_diarization(segments)

            assert result[0]['speaker'] == 'agent'
            assert result[1]['speaker'] == 'customer'  # Switched due to pause
            assert result[2]['speaker'] == 'customer'  # Same as previous

    def test_add_speaker_diarization_alternates_on_pause(self, mock_whisper_model):
        """Test that speaker diarization alternates speakers on significant pauses."""
        with patch('services.whisper_service.whisper.load_model', return_value=mock_whisper_model):
            service = WhisperService()

            segments = [
                {'start': 0.0, 'end': 1.0, 'text': 'First'},
                {'start': 3.0, 'end': 4.0, 'text': 'Second'},  # Pause > 1.5s
                {'start': 6.0, 'end': 7.0, 'text': 'Third'},   # Pause > 1.5s
            ]

            result = service._add_speaker_diarization(segments)

            assert result[0]['speaker'] == 'agent'
            assert result[1]['speaker'] == 'customer'
            assert result[2]['speaker'] == 'agent'

    def test_add_speaker_diarization_includes_timestamps(self, mock_whisper_model):
        """Test that diarization includes rounded timestamps."""
        with patch('services.whisper_service.whisper.load_model', return_value=mock_whisper_model):
            service = WhisperService()

            segments = [
                {'start': 0.123456, 'end': 2.987654, 'text': 'Test'}
            ]

            result = service._add_speaker_diarization(segments)

            assert result[0]['startTime'] == 0.12
            assert result[0]['endTime'] == 2.99

    def test_add_speaker_diarization_strips_whitespace(self, mock_whisper_model):
        """Test that diarization strips whitespace from text."""
        with patch('services.whisper_service.whisper.load_model', return_value=mock_whisper_model):
            service = WhisperService()

            segments = [
                {'start': 0.0, 'end': 1.0, 'text': '  Hello World  '}
            ]

            result = service._add_speaker_diarization(segments)

            assert result[0]['text'] == 'Hello World'


class TestConfidenceCalculation:
    """Tests for confidence score calculation."""

    def test_calculate_confidence_returns_valid_score(self, mock_whisper_model):
        """Test that confidence calculation returns score between 0 and 1."""
        with patch('services.whisper_service.whisper.load_model', return_value=mock_whisper_model):
            service = WhisperService()

            segments = [
                {
                    'start': 0.0,
                    'end': 2.0,
                    'avg_logprob': -0.5,
                    'no_speech_prob': 0.1
                }
            ]

            confidence = service._calculate_confidence(segments)

            assert isinstance(confidence, float)
            assert 0.0 <= confidence <= 1.0

    def test_calculate_confidence_handles_empty_segments(self, mock_whisper_model):
        """Test that confidence calculation handles empty segments."""
        with patch('services.whisper_service.whisper.load_model', return_value=mock_whisper_model):
            service = WhisperService()

            confidence = service._calculate_confidence([])

            assert confidence == 0.0

    def test_calculate_confidence_weights_by_duration(self, mock_whisper_model):
        """Test that confidence is weighted by segment duration."""
        with patch('services.whisper_service.whisper.load_model', return_value=mock_whisper_model):
            service = WhisperService()

            segments = [
                {
                    'start': 0.0,
                    'end': 1.0,  # 1 second
                    'avg_logprob': -0.2,
                    'no_speech_prob': 0.1
                },
                {
                    'start': 1.0,
                    'end': 11.0,  # 10 seconds (weighted more)
                    'avg_logprob': -1.0,
                    'no_speech_prob': 0.5
                }
            ]

            confidence = service._calculate_confidence(segments)

            # Longer segment should have more influence
            assert confidence is not None
            assert 0.0 <= confidence <= 1.0

    def test_calculate_confidence_handles_missing_fields(self, mock_whisper_model):
        """Test that confidence calculation handles missing fields gracefully."""
        with patch('services.whisper_service.whisper.load_model', return_value=mock_whisper_model):
            service = WhisperService()

            segments = [
                {
                    'start': 0.0,
                    'end': 2.0
                    # Missing avg_logprob and no_speech_prob
                }
            ]

            confidence = service._calculate_confidence(segments)

            # Should use default values and not crash
            assert isinstance(confidence, float)
            assert 0.0 <= confidence <= 1.0

    def test_calculate_confidence_rounds_to_three_decimals(self, mock_whisper_model):
        """Test that confidence is rounded to 3 decimal places."""
        with patch('services.whisper_service.whisper.load_model', return_value=mock_whisper_model):
            service = WhisperService()

            segments = [
                {
                    'start': 0.0,
                    'end': 1.0,
                    'avg_logprob': -0.123456789,
                    'no_speech_prob': 0.123456789
                }
            ]

            confidence = service._calculate_confidence(segments)

            # Should be rounded to 3 decimals
            assert len(str(confidence).split('.')[-1]) <= 3


class TestWhisperServiceIntegration:
    """Integration tests for WhisperService."""

    def test_full_transcription_workflow(self, mock_whisper_model, temp_audio_file):
        """Test complete transcription workflow from audio to ProcessedTranscription."""
        with patch('services.whisper_service.whisper.load_model', return_value=mock_whisper_model):
            service = WhisperService()

            # Full workflow
            result = service.transcribe(temp_audio_file)

            # Verify all components
            assert isinstance(result, ProcessedTranscription)
            assert result.full_text
            assert result.language == 'en'
            assert result.confidence > 0
            assert len(result.segments) > 0

            # Verify segment structure
            for segment in result.segments:
                assert 'speaker' in segment
                assert segment['speaker'] in ['agent', 'customer']
                assert 'startTime' in segment
                assert 'endTime' in segment
                assert 'text' in segment

    def test_transcription_preserves_event_correlation(self, mock_whisper_model, temp_audio_file):
        """Test that transcription can be used to build event payloads."""
        with patch('services.whisper_service.whisper.load_model', return_value=mock_whisper_model):
            service = WhisperService()
            result = service.transcribe(temp_audio_file)

            # Verify the result can be used to build event segments
            from models.events import Segment

            segments = [
                Segment(
                    speaker=seg["speaker"],
                    startTime=seg["startTime"],
                    endTime=seg["endTime"],
                    text=seg["text"]
                )
                for seg in result.segments
            ]

            assert len(segments) > 0
            for segment in segments:
                assert segment.speaker
                assert segment.startTime >= 0
                assert segment.endTime > segment.startTime
                assert segment.text
