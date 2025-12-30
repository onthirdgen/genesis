package com.callauditing.monitor.controller;

import com.callauditing.monitor.model.PublishRequest;
import com.callauditing.monitor.service.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/publish")
@RequiredArgsConstructor
@Slf4j
public class PublishController {

    private final KafkaProducerService kafkaProducerService;

    @PostMapping
    public ResponseEntity<Map<String, String>> publishMessage(@RequestBody PublishRequest request) {
        log.info("Received publish request: {}", request);

        if (request.getMessage() == null || request.getMessage().isBlank()) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Message cannot be empty");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        if (request.getKey() != null && !request.getKey().isBlank()) {
            kafkaProducerService.publishMessage(request.getKey(), request.getMessage());
        } else {
            kafkaProducerService.publishMessage(request.getMessage());
        }

        Map<String, String> response = new HashMap<>();
        response.put("status", "Message published successfully");
        response.put("topic", "test.publish");
        response.put("message", request.getMessage());
        if (request.getKey() != null) {
            response.put("key", request.getKey());
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "monitor-service");
        return ResponseEntity.ok(response);
    }
}
