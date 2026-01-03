package com.callaudit.gateway.service;

import com.callaudit.gateway.dto.AuthRequest;
import com.callaudit.gateway.dto.AuthResponse;
import com.callaudit.gateway.dto.UserResponse;
import com.callaudit.gateway.model.User;
import com.callaudit.gateway.repository.UserRepository;
import com.callaudit.gateway.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Authentication service for login and user management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * Authenticate user and generate JWT tokens
     */
    public Mono<AuthResponse> login(AuthRequest request) {
        return userRepository.findByEmail(request.getEmail())
                .doOnNext(user -> {
                    log.debug("User found: {}, passwordHash length: {}",
                        user.getEmail(),
                        user.getPasswordHash() != null ? user.getPasswordHash().length() : "null");
                    log.debug("Password check: request={}, matches={}",
                        request.getPassword(),
                        passwordEncoder.matches(request.getPassword(), user.getPasswordHash()));
                })
                .filter(user -> passwordEncoder.matches(request.getPassword(), user.getPasswordHash()))
                .map(user -> {
                    String accessToken = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole());
                    String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getEmail());

                    UserResponse userResponse = UserResponse.builder()
                            .id(user.getId())
                            .email(user.getEmail())
                            .fullName(user.getFullName())
                            .role(user.getRole())
                            .build();

                    return AuthResponse.builder()
                            .token(accessToken)
                            .refreshToken(refreshToken)
                            .type("Bearer")
                            .user(userResponse)
                            .build();
                })
                .doOnNext(response -> log.info("User authenticated: {}", request.getEmail()))
                .switchIfEmpty(Mono.error(new RuntimeException("Invalid email or password")));
    }

    /**
     * Refresh access token using refresh token
     */
    public Mono<AuthResponse> refreshToken(String refreshToken) {
        try {
            if (!jwtUtil.validateToken(refreshToken)) {
                return Mono.error(new RuntimeException("Invalid refresh token"));
            }

            String email = jwtUtil.extractEmail(refreshToken);

            return userRepository.findByEmail(email)
                    .map(user -> {
                        String newAccessToken = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole());
                        String newRefreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getEmail());

                        UserResponse userResponse = UserResponse.builder()
                                .id(user.getId())
                                .email(user.getEmail())
                                .fullName(user.getFullName())
                                .role(user.getRole())
                                .build();

                        return AuthResponse.builder()
                                .token(newAccessToken)
                                .refreshToken(newRefreshToken)
                                .type("Bearer")
                                .user(userResponse)
                                .build();
                    })
                    .doOnNext(response -> log.info("Token refreshed for user: {}", email));
        } catch (Exception e) {
            log.error("Token refresh failed: {}", e.getMessage());
            return Mono.error(new RuntimeException("Token refresh failed"));
        }
    }

    /**
     * Get current user info from JWT token
     */
    public Mono<UserResponse> getCurrentUser(String token) {
        try {
            if (!jwtUtil.validateToken(token)) {
                return Mono.error(new RuntimeException("Invalid token"));
            }

            String email = jwtUtil.extractEmail(token);

            return userRepository.findByEmail(email)
                    .map(user -> UserResponse.builder()
                            .id(user.getId())
                            .email(user.getEmail())
                            .fullName(user.getFullName())
                            .role(user.getRole())
                            .build());
        } catch (Exception e) {
            log.error("Get current user failed: {}", e.getMessage());
            return Mono.error(new RuntimeException("Failed to get user info"));
        }
    }
}
