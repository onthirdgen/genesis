package com.callaudit.voc.service;

import com.callaudit.voc.event.SentimentAnalyzedEvent;
import com.callaudit.voc.model.Intent;
import com.callaudit.voc.model.Satisfaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VocAnalysisService
 */
@ExtendWith(MockitoExtension.class)
class VocAnalysisServiceTest {

    private VocAnalysisService vocAnalysisService;

    @BeforeEach
    void setUp() {
        vocAnalysisService = new VocAnalysisService();

        // Set configuration values using reflection
        ReflectionTestUtils.setField(vocAnalysisService, "stopwordsConfig",
            "the,a,an,and,or,but,is,are,was,were,be,been,being,have,has,had,do,does,did,will,would,should,could,may,might,must,can,to,of,in,for,on,with,at,by,from,up,about,into,through,during");
        ReflectionTestUtils.setField(vocAnalysisService, "maxKeywords", 10);
        ReflectionTestUtils.setField(vocAnalysisService, "minKeywordLength", 3);
        ReflectionTestUtils.setField(vocAnalysisService, "highChurnThreshold", 0.7);
        ReflectionTestUtils.setField(vocAnalysisService, "mediumChurnThreshold", 0.4);

        // Manually invoke the private initializeStopwords() method
        ReflectionTestUtils.invokeMethod(vocAnalysisService, "initializeStopwords");
    }

    @Test
    void analyzeTranscription_ComplaintScenario_ReturnsComplaintIntent() {
        // Arrange
        String transcription = "I'm really frustrated with the terrible service. " +
                              "This is the worst experience I've had. The problem needs to be fixed immediately.";
        SentimentAnalyzedEvent.SentimentPayload sentiment = createSentiment("NEGATIVE", -0.8);

        // Act
        VocAnalysisService.VocAnalysisResult result = vocAnalysisService.analyzeTranscription(transcription, sentiment);

        // Assert
        assertEquals(Intent.complaint, result.getPrimaryIntent());
        assertEquals(Satisfaction.low, result.getCustomerSatisfaction());
        assertThat(result.getPredictedChurnRisk()).isGreaterThan(0.5);
        assertThat(result.getKeywords()).isNotEmpty();
        assertThat(result.getSummary()).contains("complaint");
    }

    @Test
    void analyzeTranscription_InquiryScenario_ReturnsInquiryIntent() {
        // Arrange
        String transcription = "I have a question about my account. " +
                              "Can you explain how the billing works? I would like to understand the charges.";
        SentimentAnalyzedEvent.SentimentPayload sentiment = createSentiment("NEUTRAL", 0.1);

        // Act
        VocAnalysisService.VocAnalysisResult result = vocAnalysisService.analyzeTranscription(transcription, sentiment);

        // Assert
        assertEquals(Intent.inquiry, result.getPrimaryIntent());
        assertEquals(Satisfaction.medium, result.getCustomerSatisfaction());
        assertThat(result.getTopics()).contains("Account Management");
    }

    @Test
    void analyzeTranscription_ComplimentScenario_ReturnsComplimentIntent() {
        // Arrange
        String transcription = "This is excellent service! I'm very happy and satisfied. " +
                              "Thank you so much for the wonderful support. Everything was perfect.";
        SentimentAnalyzedEvent.SentimentPayload sentiment = createSentiment("POSITIVE", 0.9);

        // Act
        VocAnalysisService.VocAnalysisResult result = vocAnalysisService.analyzeTranscription(transcription, sentiment);

        // Assert
        assertEquals(Intent.compliment, result.getPrimaryIntent());
        assertEquals(Satisfaction.high, result.getCustomerSatisfaction());
        assertThat(result.getPredictedChurnRisk()).isLessThan(0.3);
    }

    @Test
    void extractKeywords_ValidText_ReturnsTopKeywords() {
        // Arrange
        String text = "billing problem billing issue charge charge charge payment payment";

        // Act
        List<String> keywords = vocAnalysisService.extractKeywords(text);

        // Assert
        assertThat(keywords).isNotEmpty();
        assertThat(keywords).contains("charge", "billing", "payment");
        assertThat(keywords.size()).isLessThanOrEqualTo(10);
    }

    @Test
    void extractKeywords_FilterStopwords_ExcludesCommonWords() {
        // Arrange
        String text = "the and or but is are this that billing problem";

        // Act
        List<String> keywords = vocAnalysisService.extractKeywords(text);

        // Assert
        assertThat(keywords).doesNotContain("the", "and", "or", "but", "is", "are");
        assertThat(keywords).contains("billing", "problem");
    }

    @Test
    void extractKeywords_ShortWords_FiltersOutByLength() {
        // Arrange
        String text = "I am ok go to my billing problem account";

        // Act
        List<String> keywords = vocAnalysisService.extractKeywords(text);

        // Assert
        assertThat(keywords).doesNotContain("am", "ok", "go");
        assertThat(keywords).contains("billing", "problem", "account");
    }

    @Test
    void extractTopics_BillingKeywords_IdentifiesBillingTopic() {
        // Arrange
        String text = "billing charge payment invoice";
        List<String> keywords = List.of("billing", "charge", "payment");

        // Act
        List<String> topics = vocAnalysisService.extractTopics(text, keywords);

        // Assert
        assertThat(topics).contains("Billing");
    }

    @Test
    void extractTopics_TechnicalKeywords_IdentifiesTechnicalSupportTopic() {
        // Arrange
        String text = "technical issue broken error not working";
        List<String> keywords = List.of("technical", "broken", "error");

        // Act
        List<String> topics = vocAnalysisService.extractTopics(text, keywords);

        // Assert
        assertThat(topics).contains("Technical Support");
    }

    @Test
    void extractTopics_NoMatchingKeywords_ReturnsGeneralInquiry() {
        // Arrange
        String text = "hello goodbye thanks";
        List<String> keywords = List.of("hello", "goodbye");

        // Act
        List<String> topics = vocAnalysisService.extractTopics(text, keywords);

        // Assert
        assertThat(topics).contains("General Inquiry");
    }

    @Test
    void extractTopics_MultipleCategories_ReturnsAllMatchingTopics() {
        // Arrange
        String text = "billing charge technical broken account password";
        List<String> keywords = List.of("billing", "technical", "account");

        // Act
        List<String> topics = vocAnalysisService.extractTopics(text, keywords);

        // Assert
        assertThat(topics).contains("Billing", "Technical Support", "Account Management");
    }

    @Test
    void classifyIntent_ComplaintKeywords_ReturnsComplaint() {
        // Arrange
        String text = "I have a complaint about the terrible problem with your service";
        SentimentAnalyzedEvent.SentimentPayload sentiment = createSentiment("NEGATIVE", -0.7);

        // Act
        Intent intent = vocAnalysisService.classifyIntent(text, sentiment);

        // Assert
        assertEquals(Intent.complaint, intent);
    }

    @Test
    void classifyIntent_InquiryKeywords_ReturnsInquiry() {
        // Arrange
        String text = "I have a question and would like information about how this works";
        SentimentAnalyzedEvent.SentimentPayload sentiment = createSentiment("NEUTRAL", 0.0);

        // Act
        Intent intent = vocAnalysisService.classifyIntent(text, sentiment);

        // Assert
        assertEquals(Intent.inquiry, intent);
    }

    @Test
    void classifyIntent_ComplimentKeywords_ReturnsCompliment() {
        // Arrange
        String text = "This is excellent and wonderful service I love it thank you";
        SentimentAnalyzedEvent.SentimentPayload sentiment = createSentiment("POSITIVE", 0.8);

        // Act
        Intent intent = vocAnalysisService.classifyIntent(text, sentiment);

        // Assert
        assertEquals(Intent.compliment, intent);
    }

    @Test
    void classifyIntent_RequestKeywords_ReturnsRequest() {
        // Arrange
        String text = "I need assistance please help me I would like to request support";
        SentimentAnalyzedEvent.SentimentPayload sentiment = createSentiment("NEUTRAL", 0.1);

        // Act
        Intent intent = vocAnalysisService.classifyIntent(text, sentiment);

        // Assert
        assertEquals(Intent.request, intent);
    }

    @Test
    void classifyIntent_NoKeywords_ReturnsOther() {
        // Arrange
        String text = "hello goodbye have nice day";
        SentimentAnalyzedEvent.SentimentPayload sentiment = createSentiment("NEUTRAL", 0.0);

        // Act
        Intent intent = vocAnalysisService.classifyIntent(text, sentiment);

        // Assert
        assertEquals(Intent.other, intent);
    }

    @Test
    void classifyIntent_NegativeSentiment_BoostsComplaint() {
        // Arrange
        String text = "just a simple issue"; // Minimal complaint keywords
        SentimentAnalyzedEvent.SentimentPayload sentiment = createSentiment("NEGATIVE", -0.9);

        // Act
        Intent intent = vocAnalysisService.classifyIntent(text, sentiment);

        // Assert
        assertEquals(Intent.complaint, intent);
    }

    @Test
    void classifyIntent_PositiveSentiment_BoostsCompliment() {
        // Arrange
        String text = "everything is good"; // Minimal compliment keywords
        SentimentAnalyzedEvent.SentimentPayload sentiment = createSentiment("POSITIVE", 0.9);

        // Act
        Intent intent = vocAnalysisService.classifyIntent(text, sentiment);

        // Assert
        assertThat(intent).isIn(Intent.compliment, Intent.other);
    }

    @Test
    void calculateChurnRisk_HighRiskScenario_ReturnsHighScore() {
        // Arrange
        SentimentAnalyzedEvent.SentimentPayload sentiment = createSentiment("NEGATIVE", -0.9);
        Satisfaction satisfaction = Satisfaction.low;
        Intent intent = Intent.complaint;

        // Act
        double churnRisk = vocAnalysisService.calculateChurnRisk(sentiment, satisfaction, intent);

        // Assert
        assertThat(churnRisk).isGreaterThanOrEqualTo(0.7);
        assertThat(churnRisk).isLessThanOrEqualTo(1.0);
    }

    @Test
    void calculateChurnRisk_LowRiskScenario_ReturnsLowScore() {
        // Arrange
        SentimentAnalyzedEvent.SentimentPayload sentiment = createSentiment("POSITIVE", 0.9);
        Satisfaction satisfaction = Satisfaction.high;
        Intent intent = Intent.compliment;

        // Act
        double churnRisk = vocAnalysisService.calculateChurnRisk(sentiment, satisfaction, intent);

        // Assert
        assertThat(churnRisk).isLessThan(0.3);
    }

    @Test
    void calculateChurnRisk_MediumRiskScenario_ReturnsMediumScore() {
        // Arrange
        SentimentAnalyzedEvent.SentimentPayload sentiment = createSentiment("NEUTRAL", 0.0);
        Satisfaction satisfaction = Satisfaction.medium;
        Intent intent = Intent.inquiry;

        // Act
        double churnRisk = vocAnalysisService.calculateChurnRisk(sentiment, satisfaction, intent);

        // Assert
        assertThat(churnRisk).isBetween(0.3, 0.7);
    }

    @Test
    void calculateChurnRisk_NeverExceedsOne_CapsAt1() {
        // Arrange
        SentimentAnalyzedEvent.SentimentPayload sentiment = createSentiment("NEGATIVE", -1.0);
        Satisfaction satisfaction = Satisfaction.low;
        Intent intent = Intent.complaint;

        // Act
        double churnRisk = vocAnalysisService.calculateChurnRisk(sentiment, satisfaction, intent);

        // Assert
        assertThat(churnRisk).isLessThanOrEqualTo(1.0);
    }

    @Test
    void generateActionableItems_HighChurnRisk_IncludesUrgentActions() {
        // Arrange
        List<String> topics = List.of("Billing");
        Intent intent = Intent.complaint;
        SentimentAnalyzedEvent.SentimentPayload sentiment = createSentiment("NEGATIVE", -0.8);
        double churnRisk = 0.8;

        // Act
        List<String> items = vocAnalysisService.generateActionableItems(topics, intent, sentiment, churnRisk);

        // Assert
        assertThat(items).anyMatch(item -> item.contains("URGENT"));
        assertThat(items).anyMatch(item -> item.contains("retention"));
    }

    @Test
    void generateActionableItems_MediumChurnRisk_IncludesFollowUp() {
        // Arrange
        List<String> topics = List.of();
        Intent intent = Intent.inquiry;
        SentimentAnalyzedEvent.SentimentPayload sentiment = createSentiment("NEUTRAL", 0.0);
        double churnRisk = 0.5;

        // Act
        List<String> items = vocAnalysisService.generateActionableItems(topics, intent, sentiment, churnRisk);

        // Assert
        assertThat(items).anyMatch(item -> item.contains("Follow up"));
    }

    @Test
    void generateActionableItems_ComplaintIntent_IncludesComplaintActions() {
        // Arrange
        Intent intent = Intent.complaint;

        // Act
        List<String> items = vocAnalysisService.generateActionableItems(List.of(), intent,
            createSentiment("NEGATIVE", -0.5), 0.3);

        // Assert
        assertThat(items).anyMatch(item -> item.contains("complaints resolution"));
        assertThat(items).anyMatch(item -> item.contains("apology"));
    }

    @Test
    void generateActionableItems_InquiryIntent_IncludesInformationActions() {
        // Arrange
        Intent intent = Intent.inquiry;

        // Act
        List<String> items = vocAnalysisService.generateActionableItems(List.of(), intent,
            createSentiment("NEUTRAL", 0.0), 0.2);

        // Assert
        assertThat(items).anyMatch(item -> item.contains("information"));
        assertThat(items).anyMatch(item -> item.contains("educational"));
    }

    @Test
    void generateActionableItems_BillingTopic_IncludesBillingReview() {
        // Arrange
        List<String> topics = List.of("Billing");

        // Act
        List<String> items = vocAnalysisService.generateActionableItems(topics, Intent.other,
            createSentiment("NEUTRAL", 0.0), 0.2);

        // Assert
        assertThat(items).anyMatch(item -> item.contains("billing"));
    }

    @Test
    void generateActionableItems_CancellationTopic_IncludesSaveProcess() {
        // Arrange
        List<String> topics = List.of("Cancellation");

        // Act
        List<String> items = vocAnalysisService.generateActionableItems(topics, Intent.other,
            createSentiment("NEGATIVE", -0.5), 0.5);

        // Assert
        assertThat(items).anyMatch(item -> item.contains("save process"));
    }

    @Test
    void generateActionableItems_RemovesDuplicates() {
        // Arrange - scenario that might generate duplicate items
        List<String> topics = List.of("Billing", "Technical Support");
        Intent intent = Intent.complaint;
        double churnRisk = 0.8;

        // Act
        List<String> items = vocAnalysisService.generateActionableItems(topics, intent,
            createSentiment("NEGATIVE", -0.8), churnRisk);

        // Assert
        long uniqueCount = items.stream().distinct().count();
        assertEquals(uniqueCount, items.size());
    }

    // Helper methods

    private SentimentAnalyzedEvent.SentimentPayload createSentiment(String overall, double score) {
        SentimentAnalyzedEvent.SentimentPayload sentiment = new SentimentAnalyzedEvent.SentimentPayload();
        sentiment.setOverallSentiment(overall);
        sentiment.setSentimentScore(score);
        return sentiment;
    }
}
