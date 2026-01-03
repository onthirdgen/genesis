package com.callaudit.gateway.controller;

import com.callaudit.gateway.dto.AuthRequest;
import com.callaudit.gateway.dto.AuthResponse;
import com.callaudit.gateway.dto.UserResponse;
import com.callaudit.gateway.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Authentication REST controller
 * Handles login, token refresh, and current user endpoints
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication API endpoints")
public class AuthController {

    private final AuthService authService;

    /**
     * Login endpoint - returns JWT access and refresh tokens
     */
    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate with email and password, returns JWT tokens")
    public Mono<ResponseEntity<AuthResponse>> login(@RequestBody AuthRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        return authService.login(request)
                .map(response -> ResponseEntity.ok(response))
                .onErrorResume(e -> {
                    log.error("Login failed for email {}: {}", request.getEmail(), e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(null));
                });
    }

    /**
     * Refresh token endpoint - returns new access and refresh tokens
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Get new access token using refresh token")
    public Mono<ResponseEntity<AuthResponse>> refresh(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");

        if (refreshToken == null || refreshToken.isEmpty()) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null));
        }

        return authService.refreshToken(refreshToken)
                .map(response -> ResponseEntity.ok(response))
                .onErrorResume(e -> {
                    log.error("Token refresh failed: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null));
                });
    }

    /**
     * Get current user info from JWT token
     */
    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Get current user information from JWT token")
    public Mono<ResponseEntity<UserResponse>> getCurrentUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null));
        }

        String token = authHeader.substring(7);

        return authService.getCurrentUser(token)
                .map(user -> ResponseEntity.ok(user))
                .onErrorResume(e -> {
                    log.error("Get current user failed: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null));
                });
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(summary = "Auth health check", description = "Check if authentication service is running")
    public Mono<ResponseEntity<Map<String, String>>> health() {
        return Mono.just(ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "auth-service"
        )));
    }
}
