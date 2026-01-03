package com.callaudit.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Fallback Controller for Circuit Breaker.
 *
 * When a downstream service is unavailable or experiencing high error rates,
 * the circuit breaker redirects requests to this fallback endpoint.
 *
 * This prevents cascading failures and provides a graceful degradation
 * of service.
 */
@Slf4j
@RestController
public class FallbackController {

    /**
     * Generic fallback endpoint for all services.
     *
     * Returns a 503 Service Unavailable response with a friendly error message.
     */
    @RequestMapping(
        value = "/fallback",
        method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH}
    )
    public Mono<ResponseEntity<Map<String, Object>>> fallback() {
        log.warn("Fallback endpoint triggered - downstream service unavailable");

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", Instant.now().toString());
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("error", "Service Unavailable");
        response.put("message", "The requested service is temporarily unavailable. Please try again later.");
        response.put("details", "Circuit breaker is open due to high error rate or timeout");

        return Mono.just(ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(response));
    }

    /**
     * Health check endpoint for the gateway itself.
     *
     * Note: Actuator provides /actuator/health, but this is a simple
     * alternative that doesn't require actuator dependencies.
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, String>>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "api-gateway");
        response.put("timestamp", Instant.now().toString());

        return Mono.just(ResponseEntity.ok(response));
    }

    /**
     * Gateway information endpoint.
     *
     * Returns basic information about the gateway and available routes.
     */
    @GetMapping("/info")
    public Mono<ResponseEntity<Map<String, Object>>> info() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "Call Auditing Platform - API Gateway");
        response.put("version", "1.0.0");
        response.put("description", "Spring Cloud Gateway for routing requests to microservices");

        Map<String, String> routes = new HashMap<>();
        routes.put("/api/calls/**", "Call Ingestion Service");
        routes.put("/api/voc/**", "Voice of Customer Service");
        routes.put("/api/audit/**", "Audit Service");
        routes.put("/api/analytics/**", "Analytics Service");
        routes.put("/api/notifications/**", "Notification Service");

        response.put("routes", routes);
        response.put("features", new String[]{
            "Circuit Breaker",
            "Rate Limiting",
            "CORS Support",
            "Correlation ID Propagation",
            "Request/Response Logging"
        });

        return Mono.just(ResponseEntity.ok(response));
    }
}
