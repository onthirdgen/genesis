package com.callaudit.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Rate Limiting Configuration for API Gateway.
 *
 * Uses Redis/Valkey as the backing store for distributed rate limiting.
 * The KeyResolver determines how to group requests for rate limiting
 * (by IP address, user ID, API key, etc.).
 */
@Slf4j
@Configuration
public class RateLimitConfig {

    /**
     * Rate limit by client IP address.
     *
     * In production, consider using:
     * - User ID (from JWT token)
     * - API key
     * - Combination of user + endpoint
     */
    @Bean
    public KeyResolver ipAddressKeyResolver() {
        return exchange -> {
            String ipAddress = exchange.getRequest()
                .getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";

            log.debug("Rate limiting key: {}", ipAddress);
            return Mono.just(ipAddress);
        };
    }

    /**
     * Alternative: Rate limit by path (endpoint-based limiting)
     */
    /*
    @Bean
    public KeyResolver pathKeyResolver() {
        return exchange -> {
            String path = exchange.getRequest().getPath().value();
            log.debug("Rate limiting by path: {}", path);
            return Mono.just(path);
        };
    }
    */

    /**
     * Alternative: Rate limit by user (requires authentication)
     */
    /*
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            return exchange.getPrincipal()
                .map(Principal::getName)
                .defaultIfEmpty("anonymous");
        };
    }
    */
}
