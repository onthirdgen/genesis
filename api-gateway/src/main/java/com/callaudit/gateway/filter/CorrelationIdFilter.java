package com.callaudit.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global Filter that adds correlation ID to all requests.
 *
 * The correlation ID is used for:
 * - Distributed tracing across microservices
 * - Log aggregation and correlation
 * - Event sourcing (linking events to original request)
 *
 * If a request already has an X-Correlation-ID header, it is preserved.
 * Otherwise, a new UUID is generated.
 */
@Slf4j
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();

        // Get or generate correlation ID
        String existingCorrelationId = headers.getFirst(CORRELATION_ID_HEADER);
        final String correlationId;
        if (existingCorrelationId == null || existingCorrelationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
            log.debug("Generated new correlation ID: {}", correlationId);
        } else {
            correlationId = existingCorrelationId;
            log.debug("Using existing correlation ID: {}", correlationId);
        }

        // Generate unique request ID for this specific request
        final String requestId = UUID.randomUUID().toString();

        // Add correlation ID and request ID to response headers BEFORE processing the request
        exchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, correlationId);
        exchange.getResponse().getHeaders().add(REQUEST_ID_HEADER, requestId);

        // Add headers to the request going to downstream services
        ServerWebExchange mutatedExchange = exchange.mutate()
            .request(builder -> builder
                .header(CORRELATION_ID_HEADER, correlationId)
                .header(REQUEST_ID_HEADER, requestId))
            .build();

        log.info("Processing request - Path: {}, Method: {}, CorrelationID: {}, RequestID: {}",
            exchange.getRequest().getPath(),
            exchange.getRequest().getMethod(),
            correlationId,
            requestId);

        // Process the request
        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        // Execute this filter first (before routing)
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
