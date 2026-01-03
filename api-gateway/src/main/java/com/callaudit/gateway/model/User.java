package com.callaudit.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User entity for authentication
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users", schema = "gateway")
public class User {

    @Id
    private UUID id;

    @Column("email")
    private String email;

    @Column("password_hash")
    private String passwordHash;

    @Column("full_name")
    private String fullName;

    @Column("role")
    private String role;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
