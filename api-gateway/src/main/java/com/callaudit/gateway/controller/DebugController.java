package com.callaudit.gateway.controller;

import com.callaudit.gateway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Debug controller for troubleshooting authentication
 * WARNING: Remove this controller in production!
 */
@Slf4j
@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
public class DebugController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Debug endpoint to test password matching
     * Tests if a user exists and if the password matches
     */
    @PostMapping("/test-auth")
    public Mono<Map<String, Object>> testAuth(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        log.info("DEBUG: Testing auth for email: {}", email);

        return userRepository.findByEmail(email)
                .map(user -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("userFound", true);
                    response.put("email", user.getEmail());
                    response.put("fullName", user.getFullName());
                    response.put("role", user.getRole());
                    response.put("passwordHash", user.getPasswordHash());

                    // Test password match
                    boolean passwordMatches = passwordEncoder.matches(password, user.getPasswordHash());
                    response.put("passwordMatches", passwordMatches);

                    // Generate a new hash for comparison
                    String newHash = passwordEncoder.encode(password);
                    response.put("newHashGenerated", newHash);

                    log.info("DEBUG: User found: {}, Password matches: {}", user.getEmail(), passwordMatches);

                    return response;
                })
                .switchIfEmpty(Mono.fromCallable(() -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("userFound", false);
                    response.put("message", "No user found with email: " + email);
                    log.warn("DEBUG: No user found with email: {}", email);
                    return response;
                }));
    }

    /**
     * List all users (email only, for debugging)
     */
    @GetMapping("/users")
    public Mono<Map<String, Object>> listUsers() {
        return userRepository.findAll()
                .map(user -> Map.of(
                        "email", user.getEmail(),
                        "fullName", user.getFullName(),
                        "role", user.getRole()
                ))
                .collectList()
                .map(users -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("totalUsers", users.size());
                    response.put("users", users);
                    return response;
                });
    }

    /**
     * Test raw password encoding
     */
    @PostMapping("/test-encode")
    public Mono<Map<String, Object>> testEncode(@RequestBody Map<String, String> request) {
        String password = request.get("password");
        String hash = passwordEncoder.encode(password);

        Map<String, Object> response = new HashMap<>();
        response.put("password", password);
        response.put("hash", hash);
        response.put("matches", passwordEncoder.matches(password, hash));

        return Mono.just(response);
    }
}
