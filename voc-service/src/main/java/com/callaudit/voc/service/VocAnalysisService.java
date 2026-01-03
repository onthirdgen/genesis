package com.callaudit.voc.service;

import com.callaudit.voc.event.SentimentAnalyzedEvent;
import com.callaudit.voc.model.Intent;
import com.callaudit.voc.model.Satisfaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core VoC analysis service
 * Extracts insights from transcriptions and sentiment data
 */
@Service
@Slf4j
public class VocAnalysisService {

    @Value("${voc.stopwords}")
    private String stopwordsConfig;

    @Value("${voc.max-keywords:10}")
    private int maxKeywords;

    @Value("${voc.min-keyword-length:3}")
    private int minKeywordLength;

    @Value("${voc.churn-risk.high-threshold:0.7}")
    private double highChurnThreshold;

    @Value("${voc.churn-risk.medium-threshold:0.4}")
    private double mediumChurnThreshold;

    private Set<String> stopwords;

    private static final Map<String, Set<String>> INTENT_KEYWORDS = Map.of(
            "complaint", Set.of("problem", "issue", "complaint", "complain", "unhappy", "dissatisfied",
                               "disappointed", "frustrat", "terrible", "awful", "worst", "unacceptable",
                               "angry", "upset", "wrong", "mistake", "error", "broken", "fail"),
            "inquiry", Set.of("question", "ask", "wondering", "curious", "information", "how", "what",
                             "when", "where", "why", "explain", "tell", "know", "understand", "help"),
            "compliment", Set.of("great", "excellent", "wonderful", "amazing", "fantastic", "perfect",
                                "love", "thank", "appreciate", "satisfied", "happy", "pleased", "impressed"),
            "request", Set.of("need", "want", "would like", "require", "request", "please", "can you",
                             "could you", "help me", "assist", "support", "service", "order", "purchase")
    );

    /**
     * Analyze transcription and sentiment to extract VoC insights
     */
    public VocAnalysisResult analyzeTranscription(String transcription, SentimentAnalyzedEvent.SentimentPayload sentiment) {
        log.info("Analyzing transcription for VoC insights");

        if (stopwords == null) {
            initializeStopwords();
        }

        String normalizedText = normalizeText(transcription);

        List<String> keywords = extractKeywords(normalizedText);
        List<String> topics = extractTopics(normalizedText, keywords);
        Intent intent = classifyIntent(normalizedText, sentiment);
        Satisfaction satisfaction = mapSentimentToSatisfaction(sentiment);
        double churnRisk = calculateChurnRisk(sentiment, satisfaction, intent);
        List<String> actionableItems = generateActionableItems(topics, intent, sentiment, churnRisk);
        String summary = generateSummary(intent, satisfaction, churnRisk, topics);

        return VocAnalysisResult.builder()
                .keywords(keywords)
                .topics(topics)
                .primaryIntent(intent)
                .customerSatisfaction(satisfaction)
                .predictedChurnRisk(churnRisk)
                .actionableItems(actionableItems)
                .summary(summary)
                .build();
    }

    /**
     * Extract keywords using word frequency (TF approach)
     */
    public List<String> extractKeywords(String normalizedText) {
        Map<String, Integer> wordFrequency = new HashMap<>();

        String[] words = normalizedText.split("\\s+");
        for (String word : words) {
            word = word.toLowerCase().replaceAll("[^a-z]", "");

            if (word.length() >= minKeywordLength && !stopwords.contains(word)) {
                wordFrequency.put(word, wordFrequency.getOrDefault(word, 0) + 1);
            }
        }

        return wordFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(maxKeywords)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Extract topics from keywords (group related keywords)
     */
    public List<String> extractTopics(String text, List<String> keywords) {
        List<String> topics = new ArrayList<>();

        // Define topic categories based on keyword presence
        Map<String, Set<String>> topicCategories = Map.of(
                "Billing", Set.of("bill", "charge", "payment", "invoice", "price", "cost", "fee"),
                "Technical Support", Set.of("technical", "not working", "broken", "error", "bug", "crash", "issue"),
                "Account Management", Set.of("account", "login", "password", "username", "profile", "settings"),
                "Product Quality", Set.of("quality", "product", "defective", "warranty", "return", "refund"),
                "Customer Service", Set.of("service", "representative", "agent", "support", "help", "assist"),
                "Delivery", Set.of("delivery", "shipping", "tracking", "arrived", "package", "order"),
                "Cancellation", Set.of("cancel", "terminate", "discontinue", "stop", "end")
        );

        for (Map.Entry<String, Set<String>> entry : topicCategories.entrySet()) {
            String topic = entry.getKey();
            Set<String> topicKeywords = entry.getValue();

            boolean matchFound = keywords.stream()
                    .anyMatch(keyword -> topicKeywords.stream()
                            .anyMatch(topicKw -> keyword.contains(topicKw) || topicKw.contains(keyword)));

            if (matchFound) {
                topics.add(topic);
            }
        }

        return topics.isEmpty() ? List.of("General Inquiry") : topics;
    }

    /**
     * Classify primary intent based on keywords and sentiment
     */
    public Intent classifyIntent(String text, SentimentAnalyzedEvent.SentimentPayload sentiment) {
        String lowerText = text.toLowerCase();

        Map<Intent, Integer> intentScores = new HashMap<>();
        intentScores.put(Intent.complaint, 0);
        intentScores.put(Intent.inquiry, 0);
        intentScores.put(Intent.compliment, 0);
        intentScores.put(Intent.request, 0);

        // Score based on keyword presence
        for (Map.Entry<String, Set<String>> entry : INTENT_KEYWORDS.entrySet()) {
            String intentKey = entry.getKey();
            Set<String> keywords = entry.getValue();

            Intent intent = Intent.valueOf(intentKey);
            int score = 0;

            for (String keyword : keywords) {
                if (lowerText.contains(keyword)) {
                    score++;
                }
            }

            intentScores.put(intent, score);
        }

        // Adjust scores based on sentiment
        if (sentiment.getOverallSentiment().equals("NEGATIVE")) {
            intentScores.put(Intent.complaint, intentScores.get(Intent.complaint) + 2);
        } else if (sentiment.getOverallSentiment().equals("POSITIVE")) {
            intentScores.put(Intent.compliment, intentScores.get(Intent.compliment) + 2);
        }

        // Find highest scoring intent
        Intent primaryIntent = intentScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(Intent.other);

        // If all scores are zero, return OTHER
        if (intentScores.get(primaryIntent) == 0) {
            return Intent.other;
        }

        return primaryIntent;
    }

    /**
     * Calculate churn risk based on sentiment, satisfaction, and intent
     */
    public double calculateChurnRisk(SentimentAnalyzedEvent.SentimentPayload sentiment,
                                    Satisfaction satisfaction, Intent intent) {
        double riskScore = 0.0;

        // Base risk from sentiment (inverted sentiment score)
        double sentimentScore = sentiment.getSentimentScore();
        riskScore += (1 - sentimentScore) * 0.5;

        // Add risk from satisfaction level
        if (satisfaction == Satisfaction.low) {
            riskScore += 0.3;
        } else if (satisfaction == Satisfaction.medium) {
            riskScore += 0.1;
        }

        // Add risk from intent
        if (intent == Intent.complaint) {
            riskScore += 0.2;
        }

        // Cap at 1.0
        return Math.min(riskScore, 1.0);
    }

    /**
     * Generate actionable items based on analysis
     */
    public List<String> generateActionableItems(List<String> topics, Intent intent,
                                                SentimentAnalyzedEvent.SentimentPayload sentiment,
                                                double churnRisk) {
        List<String> items = new ArrayList<>();

        // High churn risk actions
        if (churnRisk >= highChurnThreshold) {
            items.add("URGENT: Contact customer within 24 hours to address concerns");
            items.add("Escalate to retention team");
            items.add("Offer compensation or service recovery");
        } else if (churnRisk >= mediumChurnThreshold) {
            items.add("Follow up within 3 business days");
            items.add("Monitor account for additional issues");
        }

        // Intent-based actions
        switch (intent) {
            case complaint:
                items.add("Assign to complaints resolution team");
                items.add("Document issue in customer profile");
                items.add("Send apology and resolution timeline");
                break;
            case inquiry:
                items.add("Provide requested information");
                items.add("Send educational resources");
                break;
            case request:
                items.add("Process customer request");
                items.add("Confirm completion with customer");
                break;
            case compliment:
                items.add("Share positive feedback with team");
                items.add("Consider for testimonial or case study");
                break;
        }

        // Topic-based actions
        if (topics.contains("Billing")) {
            items.add("Review billing accuracy");
        }
        if (topics.contains("Technical Support")) {
            items.add("Create technical support ticket");
        }
        if (topics.contains("Cancellation")) {
            items.add("Initiate save process");
        }

        return items.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Map sentiment to satisfaction level
     */
    private Satisfaction mapSentimentToSatisfaction(SentimentAnalyzedEvent.SentimentPayload sentiment) {
        String overallSentiment = sentiment.getOverallSentiment();

        if (overallSentiment.equals("POSITIVE")) {
            return Satisfaction.high;
        } else if (overallSentiment.equals("NEGATIVE")) {
            return Satisfaction.low;
        } else {
            return Satisfaction.medium;
        }
    }

    /**
     * Generate a summary of the analysis
     */
    private String generateSummary(Intent intent, Satisfaction satisfaction,
                                   double churnRisk, List<String> topics) {
        StringBuilder summary = new StringBuilder();

        summary.append("Customer contact classified as ").append(intent.name().toLowerCase());
        summary.append(" with ").append(satisfaction.name().toLowerCase()).append(" satisfaction level. ");

        if (churnRisk >= highChurnThreshold) {
            summary.append("HIGH churn risk detected. ");
        } else if (churnRisk >= mediumChurnThreshold) {
            summary.append("MEDIUM churn risk detected. ");
        } else {
            summary.append("LOW churn risk. ");
        }

        if (!topics.isEmpty()) {
            summary.append("Primary topics: ").append(String.join(", ", topics)).append(".");
        }

        return summary.toString();
    }

    /**
     * Normalize text for analysis
     */
    private String normalizeText(String text) {
        return text.toLowerCase()
                .replaceAll("[^a-z\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Initialize stopwords set
     */
    private void initializeStopwords() {
        stopwords = new HashSet<>();
        if (stopwordsConfig != null && !stopwordsConfig.isEmpty()) {
            stopwords.addAll(Arrays.asList(stopwordsConfig.split(",")));
        }
    }

    /**
     * Result object for VoC analysis
     */
    @lombok.Data
    @lombok.Builder
    public static class VocAnalysisResult {
        private List<String> keywords;
        private List<String> topics;
        private Intent primaryIntent;
        private Satisfaction customerSatisfaction;
        private Double predictedChurnRisk;
        private List<String> actionableItems;
        private String summary;
    }
}
