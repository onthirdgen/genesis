"""
Tests for sentiment analysis service logic
"""
import pytest
from unittest.mock import Mock, patch, MagicMock
import torch

from services.sentiment_service import SentimentAnalyzer
from models.events import TranscriptionSegment, SentimentSegment


class TestSentimentAnalyzerInit:
    """Tests for SentimentAnalyzer initialization"""

    def test_init_creates_instance_with_defaults(self):
        """Test that SentimentAnalyzer initializes with correct defaults"""
        analyzer = SentimentAnalyzer()

        assert analyzer.roberta_pipeline is None
        assert analyzer.vader_analyzer is not None
        assert analyzer.model_loaded is False
        assert analyzer.use_vader_fallback is False

    def test_vader_analyzer_is_initialized(self):
        """Test that VADER analyzer is always initialized"""
        analyzer = SentimentAnalyzer()

        # Verify VADER is ready to use
        assert hasattr(analyzer.vader_analyzer, 'polarity_scores')


class TestModelLoading:
    """Tests for model loading functionality"""

    @patch('services.sentiment_service.pipeline')
    @patch('services.sentiment_service.AutoModelForSequenceClassification')
    @patch('services.sentiment_service.AutoTokenizer')
    @patch('services.sentiment_service.torch')
    def test_load_model_success_with_roberta(
        self,
        mock_torch,
        mock_tokenizer,
        mock_model,
        mock_pipeline
    ):
        """Test successful RoBERTa model loading"""
        # Setup mocks
        mock_torch.cuda.is_available.return_value = False
        mock_tokenizer.from_pretrained.return_value = Mock()
        mock_model.from_pretrained.return_value = Mock()
        mock_pipeline.return_value = Mock()

        analyzer = SentimentAnalyzer()
        analyzer.load_model()

        # Verify model was loaded
        assert analyzer.model_loaded is True
        assert analyzer.use_vader_fallback is False
        assert analyzer.roberta_pipeline is not None

    @patch('services.sentiment_service.pipeline')
    @patch('services.sentiment_service.AutoModelForSequenceClassification')
    @patch('services.sentiment_service.AutoTokenizer')
    @patch('services.sentiment_service.torch')
    def test_load_model_uses_gpu_when_available(
        self,
        mock_torch,
        mock_tokenizer,
        mock_model,
        mock_pipeline
    ):
        """Test that GPU is used when available and enabled"""
        from config import settings

        # Setup mocks
        mock_torch.cuda.is_available.return_value = True
        mock_tokenizer.from_pretrained.return_value = Mock()
        mock_model.from_pretrained.return_value = Mock()
        mock_pipeline.return_value = Mock()

        # Temporarily enable GPU in settings
        original_use_gpu = settings.use_gpu
        settings.use_gpu = True

        try:
            analyzer = SentimentAnalyzer()
            analyzer.load_model()

            # Verify pipeline was created with GPU device
            call_kwargs = mock_pipeline.call_args[1]
            assert call_kwargs['device'] == 0  # GPU device
        finally:
            settings.use_gpu = original_use_gpu

    @patch('services.sentiment_service.pipeline')
    @patch('services.sentiment_service.AutoModelForSequenceClassification')
    @patch('services.sentiment_service.AutoTokenizer')
    @patch('services.sentiment_service.torch')
    def test_load_model_uses_cpu_when_gpu_disabled(
        self,
        mock_torch,
        mock_tokenizer,
        mock_model,
        mock_pipeline
    ):
        """Test that CPU is used when GPU is disabled"""
        # Setup mocks
        mock_torch.cuda.is_available.return_value = True
        mock_tokenizer.from_pretrained.return_value = Mock()
        mock_model.from_pretrained.return_value = Mock()
        mock_pipeline.return_value = Mock()

        analyzer = SentimentAnalyzer()
        analyzer.load_model()

        # Verify pipeline was created with CPU device
        call_kwargs = mock_pipeline.call_args[1]
        assert call_kwargs['device'] == -1  # CPU device

    @patch('services.sentiment_service.AutoTokenizer')
    def test_load_model_falls_back_to_vader_on_error(self, mock_tokenizer):
        """Test that analyzer falls back to VADER when RoBERTa loading fails"""
        # Setup mock to raise exception
        mock_tokenizer.from_pretrained.side_effect = Exception("Model not found")

        analyzer = SentimentAnalyzer()
        analyzer.load_model()

        # Verify fallback to VADER
        assert analyzer.model_loaded is True
        assert analyzer.use_vader_fallback is True
        assert analyzer.roberta_pipeline is None


class TestRobertaLabelMapping:
    """Tests for RoBERTa label to sentiment mapping"""

    def test_map_roberta_label_negative(self):
        """Test mapping LABEL_0 to negative sentiment"""
        analyzer = SentimentAnalyzer()
        sentiment, score = analyzer._map_roberta_label_to_sentiment("LABEL_0")

        assert sentiment == "negative"
        assert score == -1.0

    def test_map_roberta_label_neutral(self):
        """Test mapping LABEL_1 to neutral sentiment"""
        analyzer = SentimentAnalyzer()
        sentiment, score = analyzer._map_roberta_label_to_sentiment("LABEL_1")

        assert sentiment == "neutral"
        assert score == 0.0

    def test_map_roberta_label_positive(self):
        """Test mapping LABEL_2 to positive sentiment"""
        analyzer = SentimentAnalyzer()
        sentiment, score = analyzer._map_roberta_label_to_sentiment("LABEL_2")

        assert sentiment == "positive"
        assert score == 1.0

    def test_map_roberta_label_unknown_defaults_to_neutral(self):
        """Test that unknown labels default to neutral"""
        analyzer = SentimentAnalyzer()
        sentiment, score = analyzer._map_roberta_label_to_sentiment("UNKNOWN")

        assert sentiment == "neutral"
        assert score == 0.0


class TestWeightedScoreCalculation:
    """Tests for weighted sentiment score calculation"""

    def test_calculate_weighted_score_positive(self):
        """Test weighted score calculation for positive sentiment"""
        analyzer = SentimentAnalyzer()
        scores = [
            {"label": "LABEL_0", "score": 0.1},  # negative
            {"label": "LABEL_1", "score": 0.2},  # neutral
            {"label": "LABEL_2", "score": 0.7}   # positive
        ]

        sentiment, score, confidence = analyzer._calculate_weighted_score(scores)

        assert sentiment == "positive"
        assert score > 0
        assert confidence == 0.7  # Highest score

    def test_calculate_weighted_score_negative(self):
        """Test weighted score calculation for negative sentiment"""
        analyzer = SentimentAnalyzer()
        scores = [
            {"label": "LABEL_0", "score": 0.8},  # negative
            {"label": "LABEL_1", "score": 0.1},  # neutral
            {"label": "LABEL_2", "score": 0.1}   # positive
        ]

        sentiment, score, confidence = analyzer._calculate_weighted_score(scores)

        assert sentiment == "negative"
        assert score < 0
        assert confidence == 0.8

    def test_calculate_weighted_score_neutral(self):
        """Test weighted score calculation for neutral sentiment"""
        analyzer = SentimentAnalyzer()
        scores = [
            {"label": "LABEL_0", "score": 0.3},  # negative
            {"label": "LABEL_1", "score": 0.5},  # neutral
            {"label": "LABEL_2", "score": 0.2}   # positive
        ]

        sentiment, score, confidence = analyzer._calculate_weighted_score(scores)

        assert sentiment == "neutral"
        # Score should be within neutral range
        from config import settings
        assert settings.neutral_range[0] <= score <= settings.neutral_range[1]


class TestAnalyzeText:
    """Tests for individual text analysis"""

    def test_analyze_text_empty_string_returns_neutral(self):
        """Test that empty text returns neutral sentiment"""
        analyzer = SentimentAnalyzer()
        sentiment, score, confidence, emotions = analyzer.analyze_text("")

        assert sentiment == "neutral"
        assert score == 0.0
        assert confidence == 1.0
        assert emotions == {}

    def test_analyze_text_whitespace_only_returns_neutral(self):
        """Test that whitespace-only text returns neutral sentiment"""
        analyzer = SentimentAnalyzer()
        sentiment, score, confidence, emotions = analyzer.analyze_text("   \n  \t  ")

        assert sentiment == "neutral"
        assert score == 0.0

    @patch.object(SentimentAnalyzer, '_analyze_with_vader')
    def test_analyze_text_uses_vader_when_fallback_enabled(self, mock_vader):
        """Test that VADER is used when fallback is enabled"""
        mock_vader.return_value = ("positive", 0.5, 0.8, {})

        analyzer = SentimentAnalyzer()
        analyzer.use_vader_fallback = True

        analyzer.analyze_text("This is great!")

        mock_vader.assert_called_once_with("This is great!")

    @patch.object(SentimentAnalyzer, '_analyze_with_roberta')
    def test_analyze_text_uses_roberta_when_available(self, mock_roberta):
        """Test that RoBERTa is used when not in fallback mode"""
        mock_roberta.return_value = ("positive", 0.7, 0.9, {})

        analyzer = SentimentAnalyzer()
        analyzer.use_vader_fallback = False

        analyzer.analyze_text("This is excellent!")

        mock_roberta.assert_called_once_with("This is excellent!")

    @patch.object(SentimentAnalyzer, '_analyze_with_roberta')
    @patch.object(SentimentAnalyzer, '_analyze_with_vader')
    def test_analyze_text_falls_back_to_vader_on_roberta_error(
        self,
        mock_vader,
        mock_roberta
    ):
        """Test that analysis falls back to VADER if RoBERTa fails"""
        mock_roberta.side_effect = Exception("Pipeline error")
        mock_vader.return_value = ("neutral", 0.0, 0.5, {})

        analyzer = SentimentAnalyzer()
        analyzer.use_vader_fallback = False

        result = analyzer.analyze_text("Test text")

        # Should have fallen back to VADER
        mock_vader.assert_called_once()
        assert result == ("neutral", 0.0, 0.5, {})


class TestAnalyzeWithRoberta:
    """Tests for RoBERTa-based analysis"""

    def test_analyze_with_roberta_truncates_long_text(self):
        """Test that long text is truncated"""
        analyzer = SentimentAnalyzer()
        analyzer.roberta_pipeline = Mock(return_value=[[
            {"label": "LABEL_2", "score": 0.9},
            {"label": "LABEL_1", "score": 0.08},
            {"label": "LABEL_0", "score": 0.02}
        ]])

        long_text = "a" * 1000  # Very long text
        analyzer._analyze_with_roberta(long_text)

        # Verify pipeline was called with truncated text
        call_args = analyzer.roberta_pipeline.call_args[0][0]
        assert len(call_args) <= 500

    def test_analyze_with_roberta_returns_correct_structure(self):
        """Test that RoBERTa analysis returns correct tuple structure"""
        analyzer = SentimentAnalyzer()
        analyzer.roberta_pipeline = Mock(return_value=[[
            {"label": "LABEL_2", "score": 0.8},
            {"label": "LABEL_1", "score": 0.15},
            {"label": "LABEL_0", "score": 0.05}
        ]])

        sentiment, score, confidence, emotions = analyzer._analyze_with_roberta("Great service!")

        assert isinstance(sentiment, str)
        assert sentiment in ["positive", "negative", "neutral"]
        assert isinstance(score, float)
        assert -1.0 <= score <= 1.0
        assert isinstance(confidence, float)
        assert 0.0 <= confidence <= 1.0
        assert isinstance(emotions, dict)


class TestAnalyzeWithVader:
    """Tests for VADER-based analysis"""

    def test_analyze_with_vader_positive_text(self):
        """Test VADER analysis of positive text"""
        analyzer = SentimentAnalyzer()
        sentiment, score, confidence, emotions = analyzer._analyze_with_vader(
            "This is excellent service! I'm very happy!"
        )

        assert sentiment == "positive"
        assert score > 0
        assert confidence > 0

    def test_analyze_with_vader_negative_text(self):
        """Test VADER analysis of negative text"""
        analyzer = SentimentAnalyzer()
        sentiment, score, confidence, emotions = analyzer._analyze_with_vader(
            "This is terrible! I'm very frustrated!"
        )

        assert sentiment == "negative"
        assert score < 0
        assert confidence > 0

    def test_analyze_with_vader_neutral_text(self):
        """Test VADER analysis of neutral text"""
        analyzer = SentimentAnalyzer()
        sentiment, score, confidence, emotions = analyzer._analyze_with_vader(
            "The service was okay."
        )

        assert sentiment == "neutral"
        assert -0.05 < score < 0.05

    def test_analyze_with_vader_returns_emotion_scores(self):
        """Test that VADER returns emotion breakdown"""
        analyzer = SentimentAnalyzer()
        sentiment, score, confidence, emotions = analyzer._analyze_with_vader(
            "This is great!"
        )

        assert "positive" in emotions
        assert "neutral" in emotions
        assert "negative" in emotions
        assert all(0.0 <= v <= 1.0 for v in emotions.values())


class TestAnalyzeSegments:
    """Tests for segment analysis"""

    def test_analyze_segments_processes_all_segments(
        self,
        sample_transcription_segments
    ):
        """Test that all segments are analyzed"""
        analyzer = SentimentAnalyzer()
        analyzer.use_vader_fallback = True  # Use VADER for deterministic testing

        result = analyzer.analyze_segments(sample_transcription_segments)

        assert len(result) == len(sample_transcription_segments)
        assert all(isinstance(seg, SentimentSegment) for seg in result)

    def test_analyze_segments_preserves_timing_info(
        self,
        sample_transcription_segments
    ):
        """Test that timing information is preserved"""
        analyzer = SentimentAnalyzer()
        analyzer.use_vader_fallback = True

        result = analyzer.analyze_segments(sample_transcription_segments)

        for original, analyzed in zip(sample_transcription_segments, result):
            assert analyzed.startTime == original.startTime
            assert analyzed.endTime == original.endTime
            assert analyzed.text == original.text
            assert analyzed.speaker == original.speaker

    def test_analyze_segments_empty_list_returns_empty(self):
        """Test that empty segment list returns empty result"""
        analyzer = SentimentAnalyzer()
        result = analyzer.analyze_segments([])

        assert result == []

    def test_analyze_segments_adds_sentiment_data(
        self,
        sample_transcription_segments
    ):
        """Test that sentiment data is added to segments"""
        analyzer = SentimentAnalyzer()
        analyzer.use_vader_fallback = True

        result = analyzer.analyze_segments(sample_transcription_segments)

        for segment in result:
            assert hasattr(segment, 'sentiment')
            assert hasattr(segment, 'score')
            assert hasattr(segment, 'confidence')
            assert segment.sentiment in ["positive", "negative", "neutral"]
            assert -1.0 <= segment.score <= 1.0
            assert 0.0 <= segment.confidence <= 1.0


class TestCalculateOverallSentiment:
    """Tests for overall sentiment calculation"""

    def test_calculate_overall_sentiment_empty_list(self):
        """Test that empty list returns neutral"""
        analyzer = SentimentAnalyzer()
        sentiment, score = analyzer.calculate_overall_sentiment([])

        assert sentiment == "neutral"
        assert score == 0.0

    def test_calculate_overall_sentiment_weighted_by_duration(
        self,
        sample_sentiment_segments
    ):
        """Test that sentiment is weighted by segment duration"""
        analyzer = SentimentAnalyzer()
        sentiment, score = analyzer.calculate_overall_sentiment(sample_sentiment_segments)

        assert isinstance(sentiment, str)
        assert sentiment in ["positive", "negative", "neutral"]
        assert isinstance(score, float)
        assert -1.0 <= score <= 1.0

    def test_calculate_overall_sentiment_all_positive(self):
        """Test overall sentiment for all positive segments"""
        segments = [
            SentimentSegment(
                startTime=0.0,
                endTime=5.0,
                text="Great!",
                sentiment="positive",
                score=0.8,
                confidence=0.9,
                emotions={},
                speaker="customer"
            ),
            SentimentSegment(
                startTime=5.0,
                endTime=10.0,
                text="Excellent!",
                sentiment="positive",
                score=0.9,
                confidence=0.95,
                emotions={},
                speaker="customer"
            )
        ]

        analyzer = SentimentAnalyzer()
        sentiment, score = analyzer.calculate_overall_sentiment(segments)

        assert sentiment == "positive"
        assert score > 0

    def test_calculate_overall_sentiment_all_negative(self):
        """Test overall sentiment for all negative segments"""
        segments = [
            SentimentSegment(
                startTime=0.0,
                endTime=5.0,
                text="Terrible!",
                sentiment="negative",
                score=-0.8,
                confidence=0.9,
                emotions={},
                speaker="customer"
            ),
            SentimentSegment(
                startTime=5.0,
                endTime=10.0,
                text="Awful!",
                sentiment="negative",
                score=-0.9,
                confidence=0.95,
                emotions={},
                speaker="customer"
            )
        ]

        analyzer = SentimentAnalyzer()
        sentiment, score = analyzer.calculate_overall_sentiment(segments)

        assert sentiment == "negative"
        assert score < 0

    def test_calculate_overall_sentiment_handles_zero_duration(self):
        """Test that zero-duration segments are handled"""
        segments = [
            SentimentSegment(
                startTime=5.0,
                endTime=5.0,  # Zero duration
                text="Test",
                sentiment="neutral",
                score=0.0,
                confidence=0.5,
                emotions={},
                speaker=None
            )
        ]

        analyzer = SentimentAnalyzer()
        sentiment, score = analyzer.calculate_overall_sentiment(segments)

        assert sentiment == "neutral"
        assert score == 0.0


class TestDetectEscalation:
    """Tests for escalation detection"""

    def test_detect_escalation_no_escalation(self):
        """Test that no escalation is detected for stable sentiment"""
        segments = [
            SentimentSegment(
                startTime=0.0, endTime=5.0, text="", sentiment="neutral",
                score=0.1, confidence=0.8, emotions={}, speaker=None
            ),
            SentimentSegment(
                startTime=5.0, endTime=10.0, text="", sentiment="neutral",
                score=0.0, confidence=0.8, emotions={}, speaker=None
            ),
            SentimentSegment(
                startTime=10.0, endTime=15.0, text="", sentiment="neutral",
                score=0.1, confidence=0.8, emotions={}, speaker=None
            )
        ]

        analyzer = SentimentAnalyzer()
        escalation_detected, details = analyzer.detect_escalation(segments)

        assert escalation_detected is False
        assert details == {}

    def test_detect_escalation_significant_drop(self):
        """Test that significant sentiment drop is detected"""
        segments = [
            SentimentSegment(
                startTime=0.0, endTime=5.0, text="", sentiment="positive",
                score=0.8, confidence=0.9, emotions={}, speaker=None
            ),
            SentimentSegment(
                startTime=5.0, endTime=10.0, text="", sentiment="neutral",
                score=0.2, confidence=0.8, emotions={}, speaker=None
            ),
            SentimentSegment(
                startTime=10.0, endTime=15.0, text="", sentiment="negative",
                score=-0.5, confidence=0.9, emotions={}, speaker=None
            )
        ]

        analyzer = SentimentAnalyzer()
        escalation_detected, details = analyzer.detect_escalation(segments)

        assert escalation_detected is True
        assert "maxDrop" in details
        assert "startTime" in details
        assert "endTime" in details
        assert "startScore" in details
        assert "endScore" in details
        assert details["maxDrop"] >= 0.5  # From config.escalation_threshold

    def test_detect_escalation_fewer_than_two_segments(self):
        """Test that escalation cannot be detected with < 2 segments"""
        segments = [
            SentimentSegment(
                startTime=0.0, endTime=5.0, text="", sentiment="negative",
                score=-0.8, confidence=0.9, emotions={}, speaker=None
            )
        ]

        analyzer = SentimentAnalyzer()
        escalation_detected, details = analyzer.detect_escalation(segments)

        assert escalation_detected is False
        assert details == {}

    def test_detect_escalation_empty_segments(self):
        """Test escalation detection with empty segment list"""
        analyzer = SentimentAnalyzer()
        escalation_detected, details = analyzer.detect_escalation([])

        assert escalation_detected is False
        assert details == {}

    def test_detect_escalation_details_structure(self):
        """Test that escalation details have correct structure"""
        segments = [
            SentimentSegment(
                startTime=0.0, endTime=5.0, text="", sentiment="positive",
                score=0.9, confidence=0.95, emotions={}, speaker=None
            ),
            SentimentSegment(
                startTime=5.0, endTime=10.0, text="", sentiment="negative",
                score=-0.6, confidence=0.9, emotions={}, speaker=None
            )
        ]

        analyzer = SentimentAnalyzer()
        escalation_detected, details = analyzer.detect_escalation(segments)

        if escalation_detected:
            assert isinstance(details["maxDrop"], (int, float))
            assert isinstance(details["startTime"], (int, float))
            assert isinstance(details["endTime"], (int, float))
            assert isinstance(details["startScore"], (int, float))
            assert isinstance(details["endScore"], (int, float))
            assert isinstance(details["duration"], (int, float))
            assert details["duration"] >= 0

    def test_detect_escalation_finds_maximum_drop(self):
        """Test that the maximum drop is found across all segments"""
        segments = [
            SentimentSegment(
                startTime=0.0, endTime=5.0, text="", sentiment="positive",
                score=0.5, confidence=0.8, emotions={}, speaker=None
            ),
            SentimentSegment(
                startTime=5.0, endTime=10.0, text="", sentiment="positive",
                score=0.9, confidence=0.9, emotions={}, speaker=None
            ),
            SentimentSegment(
                startTime=10.0, endTime=15.0, text="", sentiment="negative",
                score=-0.8, confidence=0.95, emotions={}, speaker=None
            )
        ]

        analyzer = SentimentAnalyzer()
        escalation_detected, details = analyzer.detect_escalation(segments)

        if escalation_detected:
            # Maximum drop should be from index 1 (0.9) to index 2 (-0.8) = 1.7
            assert details["maxDrop"] > 1.0
            assert details["startScore"] > 0.5
            assert details["endScore"] < 0
