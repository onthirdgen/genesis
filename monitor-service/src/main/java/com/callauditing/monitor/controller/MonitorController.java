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
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
@Slf4j
public class MonitorController {

    private final KafkaMessageConsumerService kafkaMessageConsumerService;

    @Value("${kafka.consumer.poll-timeout:30000}")
    private long defaultTimeout;

    /**
     * Get the last 5 messages from calls.received topic (sorted from latest to oldest)
     *
     * @return ResponseEntity with list of KafkaMessage objects
     */
    @GetMapping("/calls/received")
    public ResponseEntity<?> getCallsReceived() {
        try {
            log.info("Fetching last 5 messages from calls.received topic");

            List<KafkaMessage> messages = kafkaMessageConsumerService.getLatestMessages(
                    "calls.received", 5, defaultTimeout);

            Map<String, Object> response = new HashMap<>();
            response.put("topic", "calls.received");
            response.put("count", messages.size());
            response.put("messages", messages);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching messages from calls.received", e);
            return buildErrorResponse("calls.received", e);
        }
    }

    /**
     * Get the last 5 messages from calls.transcribed topic (sorted from latest to oldest)
     *
     * @return ResponseEntity with list of KafkaMessage objects
     */
    @GetMapping("/calls/transcribed")
    public ResponseEntity<?> getCallsTranscribed() {
        try {
            log.info("Fetching last 5 messages from calls.transcribed topic");

            List<KafkaMessage> messages = kafkaMessageConsumerService.getLatestMessages(
                    "calls.transcribed", 5, defaultTimeout);

            Map<String, Object> response = new HashMap<>();
            response.put("topic", "calls.transcribed");
            response.put("count", messages.size());
            response.put("messages", messages);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching messages from calls.transcribed", e);
            return buildErrorResponse("calls.transcribed", e);
        }
    }

    /**
     * Build error response
     */
    private ResponseEntity<?> buildErrorResponse(String topic, Exception e) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", "Failed to fetch messages from topic");
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
        response.put("description", "Kafka message monitoring API");

        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("calls-received", "GET /api/monitor/calls/received - Get last 5 messages from calls.received");
        endpoints.put("calls-transcribed", "GET /api/monitor/calls/transcribed - Get last 5 messages from calls.transcribed");
        response.put("endpoints", endpoints);

        return ResponseEntity.ok(response);
    }
}
