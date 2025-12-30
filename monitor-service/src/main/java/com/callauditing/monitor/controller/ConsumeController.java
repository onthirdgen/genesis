package com.callauditing.monitor.controller;

import com.callauditing.monitor.model.KafkaMessage;
import com.callauditing.monitor.service.KafkaMessageConsumerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/consume")
@RequiredArgsConstructor
@Slf4j
public class ConsumeController {

    private final KafkaMessageConsumerService kafkaMessageConsumerService;

    @Value("${kafka.consumer.default-limit:1}")
    private int defaultLimit;

    @Value("${kafka.consumer.poll-timeout:30000}")
    private long defaultTimeout;

    /**
     * Consume messages from a Kafka topic starting from the beginning.
     *
     * @param topic   The topic name to consume from
     * @param limit   Optional maximum number of messages (defaults to 1)
     * @param timeout Optional timeout in milliseconds (defaults to 30000ms)
     * @return ResponseEntity with list of KafkaMessage objects
     */
    @GetMapping("/{topic}/from-beginning")
    public ResponseEntity<?> consumeFromBeginning(
            @PathVariable String topic,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long timeout) {

        try {
            // Use defaults if parameters not provided
            int messageLimit = (limit != null && limit > 0) ? limit : defaultLimit;
            long pollTimeout = (timeout != null && timeout > 0) ? timeout : defaultTimeout;

            log.info("Consuming messages from beginning of topic: {}, limit: {}, timeout: {}ms",
                    topic, messageLimit, pollTimeout);

            List<KafkaMessage> messages = kafkaMessageConsumerService.consumeFromBeginning(
                    topic, messageLimit, pollTimeout);

            Map<String, Object> response = new HashMap<>();
            response.put("topic", topic);
            response.put("startPosition", "beginning");
            response.put("limit", messageLimit);
            response.put("timeout", pollTimeout);
            response.put("messagesRetrieved", messages.size());
            response.put("messages", messages);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error consuming messages from beginning of topic: {}", topic, e);
            return buildErrorResponse(topic, e);
        }
    }

    /**
     * Consume messages from a Kafka topic starting from a specific offset.
     *
     * @param topic   The topic name to consume from
     * @param offset  The starting offset (required)
     * @param limit   Optional maximum number of messages (defaults to 1)
     * @param timeout Optional timeout in milliseconds (defaults to 30000ms)
     * @return ResponseEntity with list of KafkaMessage objects
     */
    @GetMapping("/{topic}/from-offset/{offset}")
    public ResponseEntity<?> consumeFromOffset(
            @PathVariable String topic,
            @PathVariable long offset,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long timeout) {

        try {
            // Use defaults if parameters not provided
            int messageLimit = (limit != null && limit > 0) ? limit : defaultLimit;
            long pollTimeout = (timeout != null && timeout > 0) ? timeout : defaultTimeout;

            log.info("Consuming messages from topic: {}, offset: {}, limit: {}, timeout: {}ms",
                    topic, offset, messageLimit, pollTimeout);

            List<KafkaMessage> messages = kafkaMessageConsumerService.consumeFromOffset(
                    topic, offset, messageLimit, pollTimeout);

            Map<String, Object> response = new HashMap<>();
            response.put("topic", topic);
            response.put("startPosition", "offset-" + offset);
            response.put("offset", offset);
            response.put("limit", messageLimit);
            response.put("timeout", pollTimeout);
            response.put("messagesRetrieved", messages.size());
            response.put("messages", messages);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error consuming messages from topic: {} at offset: {}", topic, offset, e);
            return buildErrorResponse(topic, e);
        }
    }

    /**
     * Build error response
     */
    private ResponseEntity<?> buildErrorResponse(String topic, Exception e) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", "Failed to consume messages from topic");
        errorResponse.put("topic", topic);
        errorResponse.put("message", e.getMessage());
        return ResponseEntity.internalServerError().body(errorResponse);
    }

    /**
     * Get API usage information
     *
     * @return API documentation
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getApiInfo() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "monitor-service");
        response.put("description", "Kafka message consumption API");

        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("from-beginning", "GET /api/consume/{topic}/from-beginning?limit=10&timeout=30000");
        endpoints.put("from-offset", "GET /api/consume/{topic}/from-offset/{offset}?limit=10&timeout=30000");
        response.put("endpoints", endpoints);

        Map<String, String> examples = new HashMap<>();
        examples.put("Read first 5 messages", "/api/consume/calls.received/from-beginning?limit=5");
        examples.put("Read from offset 100", "/api/consume/calls.received/from-offset/100?limit=10");
        response.put("examples", examples);

        Map<String, Object> defaults = new HashMap<>();
        defaults.put("limit", defaultLimit);
        defaults.put("timeout", defaultTimeout + "ms");
        response.put("defaults", defaults);

        return ResponseEntity.ok(response);
    }
}
