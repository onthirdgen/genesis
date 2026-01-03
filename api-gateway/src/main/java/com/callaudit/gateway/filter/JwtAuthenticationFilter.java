package com.callaudit.gateway.filter;

import com.callaudit.gateway.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * JWT Authentication Filter for Spring Cloud Gateway
 * Validates JWT tokens for protected routes
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    @Autowired
    private JwtUtil jwtUtil;

    public JwtAuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // MUST BE FIRST - Allow CORS preflight requests without authentication
            // OPTIONS requests don't include Authorization headers by CORS spec
            if (HttpMethod.OPTIONS.equals(request.getMethod())) {
                log.debug("Allowing OPTIONS request for CORS preflight: {}", request.getURI());
                return chain.filter(exchange);
            }

            // Skip JWT validation for auth endpoints
            if (isAuthEndpoint(request.getURI().getPath())) {
                return chain.filter(exchange);
            }

            // Extract Authorization header
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("Missing or invalid Authorization header for path: {}", request.getURI().getPath());
                return onError(exchange, "Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7);

            try {
                // Validate token
                if (!jwtUtil.validateToken(token)) {
                    log.warn("Invalid JWT token for path: {}", request.getURI().getPath());
                    return onError(exchange, "Invalid or expired token", HttpStatus.UNAUTHORIZED);
                }

                // Extract user info and add to request headers for downstream services
                String email = jwtUtil.extractEmail(token);
                String userId = jwtUtil.extractUserId(token).toString();
                String role = jwtUtil.extractRole(token);

                // Add user context to headers for downstream services
                ServerHttpRequest mutatedRequest = request.mutate()
                        .header("X-User-Email", email)
                        .header("X-User-Id", userId)
                        .header("X-User-Role", role)
                        .build();

                ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

                log.debug("JWT validated for user: {} ({})", email, role);
                return chain.filter(mutatedExchange);

            } catch (Exception e) {
                log.error("JWT validation error: {}", e.getMessage());
                return onError(exchange, "Authentication failed", HttpStatus.UNAUTHORIZED);
            }
        };
    }

    /**
     * Check if path is an auth endpoint (should skip JWT validation)
     */
    private boolean isAuthEndpoint(String path) {
        return path.startsWith("/api/auth/login") ||
               path.startsWith("/api/auth/refresh") ||
               path.startsWith("/api/auth/health") ||
               path.startsWith("/actuator") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/api-docs");
    }

    /**
     * Return error response
     */
    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");

        String errorJson = String.format("{\"error\":\"%s\",\"message\":\"%s\"}",
                status.getReasonPhrase(), message);

        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(errorJson.getBytes()))
        );
    }

    /**
     * Configuration class for the filter
     */
    public static class Config {
        // Empty configuration class for future use
    }
}
