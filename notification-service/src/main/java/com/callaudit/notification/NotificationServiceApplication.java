package com.callaudit.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Notification Service Application
 *
 * Sends alerts for compliance violations, high churn risk, and negative sentiment.
 * Consumes events from Kafka topics: calls.sentiment-analyzed, calls.voc-analyzed, calls.audited
 */
@SpringBootApplication
@EnableKafka
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
