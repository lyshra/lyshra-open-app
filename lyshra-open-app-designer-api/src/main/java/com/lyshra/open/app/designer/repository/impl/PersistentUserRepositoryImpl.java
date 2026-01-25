package com.lyshra.open.app.designer.repository.impl;

import com.lyshra.open.app.designer.domain.User;
import com.lyshra.open.app.designer.domain.UserRole;
import com.lyshra.open.app.designer.persistence.converter.UserConverter;
import com.lyshra.open.app.designer.persistence.repository.R2dbcUserRepository;
import com.lyshra.open.app.designer.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistent implementation of UserRepository using R2DBC.
 * This is the primary implementation used when database is configured.
 */
@Slf4j
@Repository
@Primary
@RequiredArgsConstructor
public class PersistentUserRepositoryImpl implements UserRepository {

    private final R2dbcUserRepository r2dbcRepository;
    private final UserConverter converter;

    @Override
    public Mono<User> save(User user) {
        if (user.getId() == null || user.getId().isBlank()) {
            user.setId(UUID.randomUUID().toString());
        }
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(Instant.now());
        }

        return r2dbcRepository.save(converter.toEntity(user))
                .map(converter::toDomain)
                .doOnSuccess(saved -> log.debug("Saved user: {}", saved.getUsername()))
                .doOnError(error -> log.error("Failed to save user: {}", user.getUsername(), error));
    }

    @Override
    public Mono<Optional<User>> findById(String id) {
        return r2dbcRepository.findById(id)
                .map(converter::toDomainOptional)
                .defaultIfEmpty(Optional.empty());
    }

    @Override
    public Mono<Optional<User>> findByUsername(String username) {
        return r2dbcRepository.findByUsername(username)
                .map(converter::toDomainOptional)
                .defaultIfEmpty(Optional.empty());
    }

    @Override
    public Mono<Optional<User>> findByEmail(String email) {
        return r2dbcRepository.findByEmail(email)
                .map(converter::toDomainOptional)
                .defaultIfEmpty(Optional.empty());
    }

    @Override
    public Flux<User> findAll() {
        return r2dbcRepository.findAll()
                .map(converter::toDomain);
    }

    @Override
    public Flux<User> findByRole(UserRole role) {
        return r2dbcRepository.findByRolesContaining(role.name())
                .map(converter::toDomain);
    }

    @Override
    public Flux<User> findEnabled() {
        return r2dbcRepository.findByEnabledTrue()
                .map(converter::toDomain);
    }

    @Override
    public Mono<Void> deleteById(String id) {
        return r2dbcRepository.deleteById(id)
                .doOnSuccess(v -> log.debug("Deleted user: {}", id))
                .doOnError(error -> log.error("Failed to delete user: {}", id, error));
    }

    @Override
    public Mono<Boolean> existsByUsername(String username) {
        return r2dbcRepository.existsByUsername(username);
    }

    @Override
    public Mono<Boolean> existsByEmail(String email) {
        return r2dbcRepository.existsByEmail(email);
    }
}
