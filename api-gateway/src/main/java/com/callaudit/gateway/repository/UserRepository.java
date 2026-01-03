package com.callaudit.gateway.repository;

import com.callaudit.gateway.model.User;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Repository for User entity
 */
@Repository
public interface UserRepository extends R2dbcRepository<User, UUID> {

    Mono<User> findByEmail(String email);

    Mono<Boolean> existsByEmail(String email);
}
