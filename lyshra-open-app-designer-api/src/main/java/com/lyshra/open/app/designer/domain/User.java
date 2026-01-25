package com.lyshra.open.app.designer.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

/**
 * Represents a user in the designer system.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private String id;
    private String username;
    private String email;
    private String passwordHash;
    private String firstName;
    private String lastName;
    private Set<UserRole> roles;
    private boolean enabled;
    private boolean accountLocked;
    private Instant createdAt;
    private Instant lastLoginAt;
    private int failedLoginAttempts;

    /**
     * Gets the full name of the user.
     *
     * @return the full name
     */
    public String getFullName() {
        if (firstName == null && lastName == null) {
            return username;
        }
        return String.format("%s %s",
                firstName != null ? firstName : "",
                lastName != null ? lastName : "").trim();
    }

    /**
     * Checks if the user has a specific role.
     *
     * @param role the role to check
     * @return true if the user has the role
     */
    public boolean hasRole(UserRole role) {
        return roles != null && roles.contains(role);
    }

    /**
     * Checks if the user is an admin.
     *
     * @return true if the user is an admin
     */
    public boolean isAdmin() {
        return hasRole(UserRole.ADMIN);
    }
}
