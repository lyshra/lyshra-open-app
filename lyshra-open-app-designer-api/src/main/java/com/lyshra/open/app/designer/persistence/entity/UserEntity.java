package com.lyshra.open.app.designer.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Database entity for users.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("users")
public class UserEntity {

    @Id
    private String id;

    @Column("username")
    private String username;

    @Column("email")
    private String email;

    @Column("password_hash")
    private String passwordHash;

    @Column("first_name")
    private String firstName;

    @Column("last_name")
    private String lastName;

    /**
     * Roles stored as comma-separated string or JSON array.
     */
    @Column("roles")
    private String roles;

    @Column("enabled")
    private boolean enabled;

    @Column("account_locked")
    private boolean accountLocked;

    @Column("failed_login_attempts")
    private int failedLoginAttempts;

    @Column("last_login_at")
    private Instant lastLoginAt;

    @CreatedDate
    @Column("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private Instant updatedAt;

    /**
     * Optimistic locking version.
     */
    @Version
    @Column("version")
    private Long entityVersion;
}
