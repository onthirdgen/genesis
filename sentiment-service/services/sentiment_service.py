"""
Sentiment analysis service using RoBERTa and VADER
"""
import logging
from typing import List, Dict, Tuple
from vaderSentiment.vaderSentiment import SentimentIntensityAnalyzer
from transformers import AutoTokenizer, AutoModelForSequenceClassification, pipeline
import torch

from config import settings
from models.events import TranscriptionSegment, SentimentSegment

logger = logging.getLogger(__name__)


class SentimentAnalyzer:
    """
    Sentiment analysis using RoBERTa model with VADER as fallback
    """

    def __init__(self):
        self.roberta_pipeline = None
        self.vader_analyzer = SentimentIntensityAnalyzer()
        self.model_loaded = False
        self.use_vader_fallback = False

    def load_model(self) -> None:
        """
        Load RoBERTa sentiment model from HuggingFace
        Falls back to VADER if model loading fails
        """
        try:
            logger.info(f"Loading sentiment model: {settings.model_name}")

            # Determine device
            device = 0 if settings.use_gpu and torch.cuda.is_available() else -1
            device_name = "GPU" if device == 0 else "CPU"
            logger.info(f"Using device: {device_name}")

            # Load tokenizer and model
            tokenizer = AutoTokenizer.from_pretrained(
                settings.model_name,
                cache_dir=settings.model_cache_dir
            )
            model = AutoModelForSequenceClassification.from_pretrained(
                settings.model_name,
                cache_dir=settings.model_cache_dir
            )

            # Create sentiment analysis pipeline
            self.roberta_pipeline = pipeline(
                "sentiment-analysis",
                model=model,
                tokenizer=tokenizer,
                device=device,
                top_k=None  # Return all scores
            )

            self.model_loaded = True
            logger.info("RoBERTa model loaded successfully")

        except Exception as e:
            logger.error(f"Failed to load RoBERTa model: {e}")
            logger.warning("Falling back to VADER sentiment analyzer")
            self.use_vader_fallback = True
            self.model_loaded = True

    def _map_roberta_label_to_sentiment(self, label: str) -> Tuple[str, float]:
        """
        Map RoBERTa label to sentiment and normalized score
        RoBERTa labels: LABEL_0 (negative), LABEL_1 (neutral), LABEL_2 (positive)
        """
        mapping = {
            "LABEL_0": ("negative", -1.0),
            "LABEL_1": ("neutral", 0.0),
            "LABEL_2": ("positive", 1.0)
        }
        return mapping.get(label, ("neutral", 0.0))

    def _calculate_weighted_score(self, scores: List[Dict]) -> Tuple[str, float, float]:
        """
        Calculate weighted sentiment score from RoBERTa output
        Returns: (sentiment_label, score, confidence)
        """
        # scores is a list of dicts: [{"label": "LABEL_0", "score": 0.1}, ...]
        sentiment_values = {
            "LABEL_0": -1.0,  # negative
            "LABEL_1": 0.0,   # neutral
            "LABEL_2": 1.0    # positive
        }

        weighted_score = sum(
            sentiment_values[item["label"]] * item["score"]
            for item in scores
        )

        # Get the label with highest confidence
        top_label = max(scores, key=lambda x: x["score"])
        sentiment, _ = self._map_roberta_label_to_sentiment(top_label["label"])
        confidence = top_label["score"]

        # Determine sentiment based on score and neutral range
        if settings.neutral_range[0] <= weighted_score <= settings.neutral_range[1]:
            sentiment = "neutral"
        elif weighted_score > 0:
            sentiment = "positive"
        else:
            sentiment = "negative"

        return sentiment, weighted_score, confidence

    def analyze_text(self, text: str) -> Tuple[str, float, float, Dict]:
        """
        Analyze sentiment of a single text
        Returns: (sentiment, score, confidence, emotions)
        """
        if not text or not text.strip():
            return "neutral", 0.0, 1.0, {}

        try:
            if self.use_vader_fallback:
                return self._analyze_with_vader(text)
            else:
                return self._analyze_with_roberta(text)
        except Exception as e:
            logger.error(f"Error analyzing text: {e}")
            # Fallback to VADER
            return self._analyze_with_vader(text)

    def _analyze_with_roberta(self, text: str) -> Tuple[str, float, float, Dict]:
        """Analyze text using RoBERTa model"""
        # Truncate text if too long (RoBERTa has 512 token limit)
        max_length = 500  # chars, approximate
        if len(text) > max_length:
            text = text[:max_length]

        results = self.roberta_pipeline(text)[0]
        sentiment, score, confidence = self._calculate_weighted_score(results)

        # Extract emotion-like scores
        emotions = {
            item["label"]: item["score"]
            for item in results
        }

        return sentiment, score, confidence, emotions

    def _analyze_with_vader(self, text: str) -> Tuple[str, float, float, Dict]:
        """Analyze text using VADER (fallback)"""
        scores = self.vader_analyzer.polarity_scores(text)
        compound = scores["compound"]

        # Map VADER compound score to sentiment
        if compound >= 0.05:
            sentiment = "positive"
            score = min(compound, 1.0)
        elif compound <= -0.05:
            sentiment = "negative"
            score = max(compound, -1.0)
        else:
            sentiment = "neutral"
            score = 0.0

        emotions = {
            "positive": scores["pos"],
            "neutral": scores["neu"],
            "negative": scores["neg"]
        }

        confidence = abs(compound)  # Use absolute compound as confidence

        return sentiment, score, confidence, emotions

    def analyze_segments(self, segments: List[TranscriptionSegment]) -> List[SentimentSegment]:
        """
        Analyze sentiment for each transcription segment
        """
        sentiment_segments = []

        for segment in segments:
            sentiment, score, confidence, emotions = self.analyze_text(segment.text)

            sentiment_segment = SentimentSegment(
                startTime=segment.startTime,
                endTime=segment.endTime,
                text=segment.text,
                sentiment=sentiment,
                score=score,
                confidence=confidence,
                emotions=emotions,
                speaker=segment.speaker
            )
            sentiment_segments.append(sentiment_segment)

        return sentiment_segments

    def calculate_overall_sentiment(self, segments: List[SentimentSegment]) -> Tuple[str, float]:
        """
        Calculate overall sentiment weighted by segment duration
        Returns: (overall_sentiment, overall_score)
        """
        if not segments:
            return "neutral", 0.0

        total_duration = 0.0
        weighted_sum = 0.0

        for segment in segments:
            duration = segment.endTime - segment.startTime
            if duration > 0:
                weighted_sum += segment.score * duration
                total_duration += duration

        if total_duration == 0:
            return "neutral", 0.0

        overall_score = weighted_sum / total_duration

        # Determine overall sentiment
        if settings.neutral_range[0] <= overall_score <= settings.neutral_range[1]:
            overall_sentiment = "neutral"
        elif overall_score > 0:
            overall_sentiment = "positive"
        else:
            overall_sentiment = "negative"

        return overall_sentiment, overall_score

    def detect_escalation(self, segments: List[SentimentSegment]) -> Tuple[bool, Dict]:
        """
        Detect if sentiment worsens significantly during the call
        Returns: (escalation_detected, escalation_details)
        """
        if len(segments) < 2:
            return False, {}

        # Calculate sentiment trend
        scores = [seg.score for seg in segments]

        # Check for significant drops
        max_drop = 0.0
        drop_start_idx = 0
        drop_end_idx = 0

        for i in range(len(scores)):
            for j in range(i + 1, len(scores)):
                drop = scores[i] - scores[j]
                if drop > max_drop:
                    max_drop = drop
                    drop_start_idx = i
                    drop_end_idx = j

        escalation_detected = max_drop >= settings.escalation_threshold

        escalation_details = {}
        if escalation_detected:
            escalation_details = {
                "maxDrop": round(max_drop, 3),
                "startTime": segments[drop_start_idx].startTime,
                "endTime": segments[drop_end_idx].endTime,
                "startScore": round(scores[drop_start_idx], 3),
                "endScore": round(scores[drop_end_idx], 3),
                "duration": round(segments[drop_end_idx].endTime - segments[drop_start_idx].startTime, 2)
            }

            logger.info(
                f"Escalation detected: score dropped from {escalation_details['startScore']} "
                f"to {escalation_details['endScore']} (drop: {escalation_details['maxDrop']})"
            )

        return escalation_detected, escalation_details
