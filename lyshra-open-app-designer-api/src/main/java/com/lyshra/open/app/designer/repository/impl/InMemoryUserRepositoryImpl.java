package com.lyshra.open.app.designer.repository.impl;

import com.lyshra.open.app.designer.domain.User;
import com.lyshra.open.app.designer.domain.UserRole;
import com.lyshra.open.app.designer.repository.UserRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of UserRepository.
 * This implementation is suitable for development and testing.
 * For production, replace with a database-backed implementation.
 */
@Repository
public class InMemoryUserRepositoryImpl implements UserRepository {

    private final Map<String, User> store = new ConcurrentHashMap<>();

    @Override
    public Mono<User> save(User user) {
        return Mono.fromCallable(() -> {
            store.put(user.getId(), user);
            return user;
        });
    }

    @Override
    public Mono<Optional<User>> findById(String id) {
        return Mono.fromCallable(() -> Optional.ofNullable(store.get(id)));
    }

    @Override
    public Mono<Optional<User>> findByUsername(String username) {
        return Flux.fromIterable(store.values())
                .filter(user -> username.equals(user.getUsername()))
                .next()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    @Override
    public Mono<Optional<User>> findByEmail(String email) {
        return Flux.fromIterable(store.values())
                .filter(user -> email.equals(user.getEmail()))
                .next()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    @Override
    public Flux<User> findAll() {
        return Flux.fromIterable(store.values());
    }

    @Override
    public Flux<User> findByRole(UserRole role) {
        return Flux.fromIterable(store.values())
                .filter(user -> user.getRoles() != null && user.getRoles().contains(role));
    }

    @Override
    public Flux<User> findEnabled() {
        return Flux.fromIterable(store.values())
                .filter(User::isEnabled);
    }

    @Override
    public Mono<Void> deleteById(String id) {
        return Mono.fromRunnable(() -> store.remove(id));
    }

    @Override
    public Mono<Boolean> existsByUsername(String username) {
        return Flux.fromIterable(store.values())
                .any(user -> username.equals(user.getUsername()));
    }

    @Override
    public Mono<Boolean> existsByEmail(String email) {
        return Flux.fromIterable(store.values())
                .any(user -> email.equals(user.getEmail()));
    }
}
