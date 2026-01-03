package com.callaudit.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway Application - Main entry point for the Call Auditing Platform.
 *
 * Routes incoming HTTP requests to appropriate downstream microservices:
 * - Call Ingestion Service (audio upload)
 * - VoC Service (Voice of Customer insights)
 * - Audit Service (compliance auditing)
 * - Analytics Service (metrics and dashboards)
 * - Notification Service (alerts)
 *
 * Features:
 * - Request routing with path rewriting
 * - Circuit breaker pattern for fault tolerance
 * - Rate limiting (Redis/Valkey-backed)
 * - CORS configuration for browser clients
 * - Correlation ID propagation
 * - Request/response logging
 * - Health checks and metrics (Prometheus)
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
