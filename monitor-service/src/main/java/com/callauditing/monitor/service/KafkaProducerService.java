package com.callauditing.monitor.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${kafka.topics.test-publish}")
    private String testPublishTopic;

    public void publishMessage(String message) {
        log.info("Publishing message to {} topic: {}", testPublishTopic, message);

        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(testPublishTopic, message);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Message published successfully to topic {} with offset {}",
                        testPublishTopic,
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish message to topic {}: {}", testPublishTopic, ex.getMessage(), ex);
            }
        });
    }

    public void publishMessage(String key, String message) {
        log.info("Publishing message with key {} to {} topic: {}", key, testPublishTopic, message);

        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(testPublishTopic, key, message);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Message with key {} published successfully to topic {} with offset {}",
                        key,
                        testPublishTopic,
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish message with key {} to topic {}: {}",
                        key, testPublishTopic, ex.getMessage(), ex);
            }
        });
    }
}
