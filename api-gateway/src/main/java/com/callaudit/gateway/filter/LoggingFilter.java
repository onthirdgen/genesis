package com.callaudit.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global Filter for logging all incoming requests and outgoing responses.
 *
 * Logs:
 * - Request method, path, headers
 * - Response status code
 * - Request processing time
 */
@Slf4j
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();

        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();
        String remoteAddress = exchange.getRequest().getRemoteAddress() != null
            ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
            : "unknown";

        log.info("Incoming request - Method: {}, Path: {}, RemoteAddress: {}",
            method, path, remoteAddress);

        return chain.filter(exchange)
            .then(Mono.fromRunnable(() -> {
                long duration = System.currentTimeMillis() - startTime;
                int statusCode = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value()
                    : 0;

                log.info("Outgoing response - Method: {}, Path: {}, Status: {}, Duration: {}ms",
                    method, path, statusCode, duration);
            }));
    }

    @Override
    public int getOrder() {
        // Execute after correlation ID filter
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
