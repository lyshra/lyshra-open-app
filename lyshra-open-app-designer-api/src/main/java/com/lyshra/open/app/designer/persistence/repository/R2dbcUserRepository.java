package com.lyshra.open.app.designer.persistence.repository;

import com.lyshra.open.app.designer.persistence.entity.UserEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Spring Data R2DBC repository for UserEntity.
 */
@Repository
public interface R2dbcUserRepository extends R2dbcRepository<UserEntity, String> {

    /**
     * Find user by username.
     */
    Mono<UserEntity> findByUsername(String username);

    /**
     * Find user by email.
     */
    Mono<UserEntity> findByEmail(String email);

    /**
     * Check if username exists.
     */
    Mono<Boolean> existsByUsername(String username);

    /**
     * Check if email exists.
     */
    Mono<Boolean> existsByEmail(String email);

    /**
     * Find users by role (roles are stored as comma-separated string).
     */
    @Query("SELECT * FROM users WHERE roles LIKE CONCAT('%', :role, '%')")
    Flux<UserEntity> findByRolesContaining(String role);

    /**
     * Find all enabled users.
     */
    Flux<UserEntity> findByEnabledTrue();
}
