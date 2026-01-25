package com.lyshra.open.app.designer.controller;

import com.lyshra.open.app.designer.domain.User;
import com.lyshra.open.app.designer.dto.UserCreateRequest;
import com.lyshra.open.app.designer.exception.UnauthorizedException;
import com.lyshra.open.app.designer.repository.UserRepository;
import com.lyshra.open.app.designer.security.JwtService;
import com.lyshra.open.app.designer.service.UserService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for authentication operations.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final UserService userService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(
            UserRepository userRepository,
            UserService userService,
            JwtService jwtService,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return userRepository.findByUsername(request.getUsername())
                .flatMap(optUser -> optUser
                        .map(user -> {
                            if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                                return Mono.<ResponseEntity<AuthResponse>>error(
                                        new UnauthorizedException("Invalid credentials"));
                            }
                            if (!user.isEnabled()) {
                                return Mono.<ResponseEntity<AuthResponse>>error(
                                        new UnauthorizedException("Account is disabled"));
                            }
                            if (user.isAccountLocked()) {
                                return Mono.<ResponseEntity<AuthResponse>>error(
                                        new UnauthorizedException("Account is locked"));
                            }

                            user.setLastLoginAt(Instant.now());
                            user.setFailedLoginAttempts(0);

                            return userRepository.save(user)
                                    .map(savedUser -> {
                                        String token = generateToken(savedUser);
                                        return ResponseEntity.ok(AuthResponse.builder()
                                                .token(token)
                                                .username(savedUser.getUsername())
                                                .email(savedUser.getEmail())
                                                .roles(savedUser.getRoles().stream()
                                                        .map(Enum::name)
                                                        .collect(Collectors.toSet()))
                                                .build());
                                    });
                        })
                        .orElse(Mono.error(new UnauthorizedException("Invalid credentials"))));
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<AuthResponse>> register(@Valid @RequestBody UserCreateRequest request) {
        return userService.createUser(request)
                .map(user -> {
                    String token = generateToken(user);
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .body(AuthResponse.builder()
                                    .token(token)
                                    .username(user.getUsername())
                                    .email(user.getEmail())
                                    .roles(user.getRoles().stream()
                                            .map(Enum::name)
                                            .collect(Collectors.toSet()))
                                    .build());
                });
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<AuthResponse>> refresh(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Mono.error(new UnauthorizedException("Invalid token"));
        }

        String token = authHeader.substring(7);
        return jwtService.extractUsername(token)
                .map(username -> userRepository.findByUsername(username)
                        .flatMap(optUser -> optUser
                                .map(user -> {
                                    String newToken = generateToken(user);
                                    return Mono.just(ResponseEntity.ok(AuthResponse.builder()
                                            .token(newToken)
                                            .username(user.getUsername())
                                            .email(user.getEmail())
                                            .roles(user.getRoles().stream()
                                                    .map(Enum::name)
                                                    .collect(Collectors.toSet()))
                                            .build()));
                                })
                                .orElse(Mono.error(new UnauthorizedException("User not found")))))
                .orElse(Mono.error(new UnauthorizedException("Invalid token")));
    }

    private String generateToken(User user) {
        return jwtService.generateToken(user.getUsername(), Map.of(
                "email", user.getEmail(),
                "roles", user.getRoles().stream().map(Enum::name).collect(Collectors.toList())
        ));
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthResponse {
        private String token;
        private String username;
        private String email;
        private java.util.Set<String> roles;
    }
}
