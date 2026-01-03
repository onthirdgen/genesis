package com.callaudit.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS Configuration for API Gateway.
 *
 * Allows browser-based clients to make cross-origin requests to the API.
 * In production, restrict allowed-origins to specific domains.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();

        // Allow all origins (development mode - restrict in production!)
        corsConfig.setAllowedOriginPatterns(List.of("*"));

        // Allow common HTTP methods
        corsConfig.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));

        // Allow all headers
        corsConfig.setAllowedHeaders(List.of("*"));

        // Allow credentials (cookies, authorization headers)
        corsConfig.setAllowCredentials(true);

        // Expose custom headers to browser
        corsConfig.setExposedHeaders(Arrays.asList(
            "X-Correlation-ID",
            "X-Request-ID"
        ));

        // Cache preflight response for 1 hour
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}
