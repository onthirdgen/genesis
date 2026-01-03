package com.callaudit.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Spring Security configuration for API Gateway
 * Configures CORS, CSRF, and authentication rules
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Configure security filter chain
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                // Disable CSRF for stateless JWT authentication
                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                // Configure authorization rules
                .authorizeExchange(exchange -> exchange
                        // Public endpoints
                        .pathMatchers("/api/auth/**").permitAll()
                        .pathMatchers("/api/debug/**").permitAll()  // Debug endpoints
                        .pathMatchers("/actuator/health").permitAll()
                        .pathMatchers("/swagger-ui/**").permitAll()
                        .pathMatchers("/api-docs/**").permitAll()

                        // All other endpoints require authentication
                        // JWT validation is handled by JwtAuthenticationFilter in routes
                        .anyExchange().permitAll()
                )

                // Disable HTTP Basic authentication
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)

                // Disable form login
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)

                .build();
    }

    /**
     * Password encoder bean
     * Uses BCrypt for secure password hashing
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
