package com.callaudit.voc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Voice of Customer (VoC) Service Application
 *
 * This service extracts customer insights from call transcriptions including:
 * - Primary intent classification
 * - Topic and keyword extraction
 * - Customer satisfaction assessment
 * - Churn risk prediction
 * - Actionable items generation
 */
@SpringBootApplication
@EnableKafka
public class VocServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VocServiceApplication.class, args);
    }
}
