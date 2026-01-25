package com.lyshra.open.app.designer.config;

import com.lyshra.open.app.designer.domain.User;
import com.lyshra.open.app.designer.domain.UserRole;
import com.lyshra.open.app.designer.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Initializes default data for development and testing.
 */
@Component
public class DataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
        createDefaultAdminUser();
    }

    private void createDefaultAdminUser() {
        userRepository.findByUsername("admin")
                .flatMap(optUser -> {
                    if (optUser.isEmpty()) {
                        User admin = User.builder()
                                .id(UUID.randomUUID().toString())
                                .username("admin")
                                .email("admin@lyshra.com")
                                .passwordHash(passwordEncoder.encode("admin123"))
                                .firstName("System")
                                .lastName("Administrator")
                                .roles(Set.of(UserRole.ADMIN, UserRole.DEVELOPER, UserRole.OPERATOR))
                                .enabled(true)
                                .accountLocked(false)
                                .createdAt(Instant.now())
                                .failedLoginAttempts(0)
                                .build();
                        return userRepository.save(admin);
                    }
                    return optUser.map(user -> user).map(reactor.core.publisher.Mono::just)
                            .orElse(reactor.core.publisher.Mono.empty());
                })
                .subscribe();
    }
}
