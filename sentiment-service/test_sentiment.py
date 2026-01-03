"""
Simple test script for sentiment analyzer
Run this to test the sentiment analysis logic without Kafka
"""
from services.sentiment_service import SentimentAnalyzer
from models.events import TranscriptionSegment


def test_sentiment_analyzer():
    """Test the sentiment analyzer with sample data"""
    print("Initializing Sentiment Analyzer...")
    analyzer = SentimentAnalyzer()
    analyzer.load_model()

    if analyzer.use_vader_fallback:
        print("Using VADER sentiment analyzer (fallback mode)")
    else:
        print("Using RoBERTa sentiment analyzer")

    # Test individual text analysis
    print("\n--- Testing Individual Text Analysis ---")
    test_texts = [
        "This is excellent service! I'm very happy!",
        "I'm extremely frustrated and angry with this.",
        "The service was okay, nothing special.",
        "I hate waiting on hold for so long!",
        "Thank you for your help, you've been great!"
    ]

    for text in test_texts:
        sentiment, score, confidence, emotions = analyzer.analyze_text(text)
        print(f"\nText: {text}")
        print(f"  Sentiment: {sentiment}")
        print(f"  Score: {score:.3f}")
        print(f"  Confidence: {confidence:.3f}")

    # Test segment analysis
    print("\n--- Testing Segment Analysis ---")
    segments = [
        TranscriptionSegment(
            startTime=0.0,
            endTime=5.0,
            text="Hello, how can I help you today?",
            speaker="agent"
        ),
        TranscriptionSegment(
            startTime=5.0,
            endTime=10.0,
            text="I'm having a problem with my order.",
            speaker="customer"
        ),
        TranscriptionSegment(
            startTime=10.0,
            endTime=15.0,
            text="I understand. Let me check that for you.",
            speaker="agent"
        ),
        TranscriptionSegment(
            startTime=15.0,
            endTime=20.0,
            text="This is taking forever! I'm very frustrated!",
            speaker="customer"
        ),
        TranscriptionSegment(
            startTime=20.0,
            endTime=25.0,
            text="I apologize for the delay. I found your order.",
            speaker="agent"
        )
    ]

    sentiment_segments = analyzer.analyze_segments(segments)

    print("\nSegment Analysis Results:")
    for seg in sentiment_segments:
        print(f"\n[{seg.startTime:.1f}s - {seg.endTime:.1f}s] {seg.speaker or 'unknown'}")
        print(f"  Text: {seg.text}")
        print(f"  Sentiment: {seg.sentiment} (score: {seg.score:.3f}, confidence: {seg.confidence:.3f})")

    # Test overall sentiment calculation
    print("\n--- Testing Overall Sentiment ---")
    overall_sentiment, overall_score = analyzer.calculate_overall_sentiment(sentiment_segments)
    print(f"Overall Sentiment: {overall_sentiment}")
    print(f"Overall Score: {overall_score:.3f}")

    # Test escalation detection
    print("\n--- Testing Escalation Detection ---")
    escalation_detected, escalation_details = analyzer.detect_escalation(sentiment_segments)
    print(f"Escalation Detected: {escalation_detected}")
    if escalation_detected:
        print(f"Escalation Details: {escalation_details}")

    print("\n--- Test Complete ---")


if __name__ == "__main__":
    test_sentiment_analyzer()
