package com.lyshra.open.app.designer.persistence.converter;

import com.lyshra.open.app.designer.domain.User;
import com.lyshra.open.app.designer.domain.UserRole;
import com.lyshra.open.app.designer.persistence.entity.UserEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Converter between User domain object and UserEntity.
 */
@Slf4j
@Component
public class UserConverter {

    /**
     * Convert domain object to entity.
     */
    public UserEntity toEntity(User domain) {
        if (domain == null) {
            return null;
        }

        String rolesString = null;
        if (domain.getRoles() != null && !domain.getRoles().isEmpty()) {
            rolesString = domain.getRoles().stream()
                    .map(Enum::name)
                    .collect(Collectors.joining(","));
        }

        return UserEntity.builder()
                .id(domain.getId())
                .username(domain.getUsername())
                .email(domain.getEmail())
                .passwordHash(domain.getPasswordHash())
                .firstName(domain.getFirstName())
                .lastName(domain.getLastName())
                .roles(rolesString)
                .enabled(domain.isEnabled())
                .accountLocked(domain.isAccountLocked())
                .failedLoginAttempts(domain.getFailedLoginAttempts())
                .lastLoginAt(domain.getLastLoginAt())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    /**
     * Convert entity to domain object.
     */
    public User toDomain(UserEntity entity) {
        if (entity == null) {
            return null;
        }

        Set<UserRole> roles = new HashSet<>();
        if (entity.getRoles() != null && !entity.getRoles().isBlank()) {
            roles = Arrays.stream(entity.getRoles().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> {
                        try {
                            return UserRole.valueOf(s);
                        } catch (IllegalArgumentException e) {
                            log.warn("Unknown role: {}", s);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        return User.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .email(entity.getEmail())
                .passwordHash(entity.getPasswordHash())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .roles(roles)
                .enabled(entity.isEnabled())
                .accountLocked(entity.isAccountLocked())
                .failedLoginAttempts(entity.getFailedLoginAttempts())
                .lastLoginAt(entity.getLastLoginAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    /**
     * Convert Optional entity to Optional domain object.
     */
    public Optional<User> toDomainOptional(UserEntity entity) {
        return Optional.ofNullable(toDomain(entity));
    }
}
