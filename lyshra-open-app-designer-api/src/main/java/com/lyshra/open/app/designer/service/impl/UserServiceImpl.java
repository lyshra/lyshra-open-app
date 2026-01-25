package com.lyshra.open.app.designer.service.impl;

import com.lyshra.open.app.designer.domain.User;
import com.lyshra.open.app.designer.domain.UserRole;
import com.lyshra.open.app.designer.dto.UserCreateRequest;
import com.lyshra.open.app.designer.dto.UserUpdateRequest;
import com.lyshra.open.app.designer.exception.ConflictException;
import com.lyshra.open.app.designer.exception.ResourceNotFoundException;
import com.lyshra.open.app.designer.exception.UnauthorizedException;
import com.lyshra.open.app.designer.repository.UserRepository;
import com.lyshra.open.app.designer.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of UserService.
 * Handles user management operations.
 */
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Mono<User> createUser(UserCreateRequest request) {
        return userRepository.existsByUsername(request.getUsername())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new ConflictException("Username already exists"));
                    }
                    return userRepository.existsByEmail(request.getEmail());
                })
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new ConflictException("Email already exists"));
                    }
                    return createAndSaveUser(request);
                });
    }

    private Mono<User> createAndSaveUser(UserCreateRequest request) {
        Set<UserRole> roles = Optional.ofNullable(request.getRoles())
                .orElseGet(() -> Set.of(UserRole.VIEWER));

        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .roles(roles)
                .enabled(true)
                .accountLocked(false)
                .createdAt(Instant.now())
                .failedLoginAttempts(0)
                .build();

        return userRepository.save(user);
    }

    @Override
    public Mono<User> updateUser(String id, UserUpdateRequest request) {
        return userRepository.findById(id)
                .flatMap(optUser -> optUser
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new ResourceNotFoundException("User", id))))
                .flatMap(user -> {
                    if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
                        return userRepository.existsByEmail(request.getEmail())
                                .flatMap(exists -> {
                                    if (exists) {
                                        return Mono.error(new ConflictException("Email already exists"));
                                    }
                                    return updateAndSaveUser(user, request);
                                });
                    }
                    return updateAndSaveUser(user, request);
                });
    }

    private Mono<User> updateAndSaveUser(User user, UserUpdateRequest request) {
        Optional.ofNullable(request.getEmail()).ifPresent(user::setEmail);
        Optional.ofNullable(request.getFirstName()).ifPresent(user::setFirstName);
        Optional.ofNullable(request.getLastName()).ifPresent(user::setLastName);
        return userRepository.save(user);
    }

    @Override
    public Mono<User> getUser(String id) {
        return userRepository.findById(id)
                .flatMap(optUser -> optUser
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new ResourceNotFoundException("User", id))));
    }

    @Override
    public Mono<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .flatMap(optUser -> optUser
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new ResourceNotFoundException("User", username))));
    }

    @Override
    public Flux<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public Flux<User> getUsersByRole(UserRole role) {
        return userRepository.findByRole(role);
    }

    @Override
    public Mono<Void> deleteUser(String id) {
        return userRepository.findById(id)
                .flatMap(optUser -> optUser
                        .map(user -> userRepository.deleteById(id))
                        .orElseGet(() -> Mono.error(new ResourceNotFoundException("User", id))));
    }

    @Override
    public Mono<User> enableUser(String id) {
        return userRepository.findById(id)
                .flatMap(optUser -> optUser
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new ResourceNotFoundException("User", id))))
                .flatMap(user -> {
                    user.setEnabled(true);
                    user.setAccountLocked(false);
                    user.setFailedLoginAttempts(0);
                    return userRepository.save(user);
                });
    }

    @Override
    public Mono<User> disableUser(String id) {
        return userRepository.findById(id)
                .flatMap(optUser -> optUser
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new ResourceNotFoundException("User", id))))
                .flatMap(user -> {
                    user.setEnabled(false);
                    return userRepository.save(user);
                });
    }

    @Override
    public Mono<Void> changePassword(String id, String oldPassword, String newPassword) {
        return userRepository.findById(id)
                .flatMap(optUser -> optUser
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new ResourceNotFoundException("User", id))))
                .flatMap(user -> {
                    if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
                        return Mono.error(new UnauthorizedException("Invalid current password"));
                    }
                    user.setPasswordHash(passwordEncoder.encode(newPassword));
                    return userRepository.save(user).then();
                });
    }

    @Override
    public Mono<Void> resetPassword(String id, String newPassword) {
        return userRepository.findById(id)
                .flatMap(optUser -> optUser
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new ResourceNotFoundException("User", id))))
                .flatMap(user -> {
                    user.setPasswordHash(passwordEncoder.encode(newPassword));
                    user.setFailedLoginAttempts(0);
                    user.setAccountLocked(false);
                    return userRepository.save(user).then();
                });
    }

    @Override
    public Mono<User> addRole(String id, UserRole role) {
        return userRepository.findById(id)
                .flatMap(optUser -> optUser
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new ResourceNotFoundException("User", id))))
                .flatMap(user -> {
                    Set<UserRole> roles = new HashSet<>(
                            Optional.ofNullable(user.getRoles()).orElseGet(HashSet::new));
                    roles.add(role);
                    user.setRoles(roles);
                    return userRepository.save(user);
                });
    }

    @Override
    public Mono<User> removeRole(String id, UserRole role) {
        return userRepository.findById(id)
                .flatMap(optUser -> optUser
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new ResourceNotFoundException("User", id))))
                .flatMap(user -> {
                    Set<UserRole> roles = new HashSet<>(
                            Optional.ofNullable(user.getRoles()).orElseGet(HashSet::new));
                    roles.remove(role);
                    user.setRoles(roles);
                    return userRepository.save(user);
                });
    }
}
