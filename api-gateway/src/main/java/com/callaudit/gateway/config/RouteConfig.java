package com.callaudit.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Programmatic Route Configuration for Spring Cloud Gateway.
 *
 * This provides an alternative to YAML-based route definitions
 * and allows for more complex routing logic if needed.
 *
 * Currently, routes are defined in application.yml, but this
 * class demonstrates how to configure routes programmatically.
 */
@Slf4j
@Configuration
public class RouteConfig {

    /**
     * Uncomment this bean to use programmatic route configuration
     * instead of YAML-based configuration.
     */
    /*
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        log.info("Initializing programmatic route configuration");

        return builder.routes()
            // Call Ingestion Service
            .route("call-ingestion-service", r -> r
                .path("/api/calls/**")
                .filters(f -> f
                    .rewritePath("/api/calls/(?<segment>.*)", "/${segment}")
                    .circuitBreaker(config -> config
                        .setName("callIngestionCircuitBreaker")
                        .setFallbackUri("forward:/fallback"))
                    .requestRateLimiter(config -> config
                        .setRateLimiter(redisRateLimiter())))
                .uri("http://call-ingestion-service:8080"))

            // VoC Service
            .route("voc-service", r -> r
                .path("/api/voc/**")
                .filters(f -> f
                    .rewritePath("/api/voc/(?<segment>.*)", "/${segment}")
                    .circuitBreaker(config -> config
                        .setName("vocCircuitBreaker")
                        .setFallbackUri("forward:/fallback"))
                    .requestRateLimiter(config -> config
                        .setRateLimiter(redisRateLimiter())))
                .uri("http://voc-service:8080"))

            // Audit Service
            .route("audit-service", r -> r
                .path("/api/audit/**")
                .filters(f -> f
                    .rewritePath("/api/audit/(?<segment>.*)", "/${segment}")
                    .circuitBreaker(config -> config
                        .setName("auditCircuitBreaker")
                        .setFallbackUri("forward:/fallback"))
                    .requestRateLimiter(config -> config
                        .setRateLimiter(redisRateLimiter())))
                .uri("http://audit-service:8080"))

            // Analytics Service
            .route("analytics-service", r -> r
                .path("/api/analytics/**")
                .filters(f -> f
                    .rewritePath("/api/analytics/(?<segment>.*)", "/${segment}")
                    .circuitBreaker(config -> config
                        .setName("analyticsCircuitBreaker")
                        .setFallbackUri("forward:/fallback"))
                    .requestRateLimiter(config -> config
                        .setRateLimiter(redisRateLimiter())))
                .uri("http://analytics-service:8080"))

            // Notification Service
            .route("notification-service", r -> r
                .path("/api/notifications/**")
                .filters(f -> f
                    .rewritePath("/api/notifications/(?<segment>.*)", "/${segment}")
                    .circuitBreaker(config -> config
                        .setName("notificationCircuitBreaker")
                        .setFallbackUri("forward:/fallback"))
                    .requestRateLimiter(config -> config
                        .setRateLimiter(redisRateLimiter())))
                .uri("http://notification-service:8080"))

            .build();
    }
    */
}
