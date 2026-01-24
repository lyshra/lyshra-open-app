package com.lyshra.open.app.distributed.lock;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable result of a distributed lock acquisition attempt.
 *
 * Contains information about whether the lock was acquired, lease details,
 * and in case of failure, the current holder information.
 */
@Getter
@Builder
@ToString
public final class DistributedLockResult {

    /**
     * Possible outcomes of a lock acquisition attempt.
     */
    public enum Status {
        /**
         * Lock was successfully acquired.
         */
        ACQUIRED,

        /**
         * Lock could not be acquired; already held by another node.
         */
        ALREADY_HELD,

        /**
         * Lock acquisition timed out.
         */
        TIMEOUT,

        /**
         * Lock was successfully extended.
         */
        EXTENDED,

        /**
         * Lock acquisition failed due to an error.
         */
        ERROR
    }

    private final Status status;
    private final String lockKey;
    private final String holderNodeId;
    private final Instant acquiredAt;
    private final Instant expiresAt;
    private final long lockVersion;
    private final String errorMessage;

    private DistributedLockResult(Status status,
                                   String lockKey,
                                   String holderNodeId,
                                   Instant acquiredAt,
                                   Instant expiresAt,
                                   long lockVersion,
                                   String errorMessage) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.lockKey = Objects.requireNonNull(lockKey, "lockKey must not be null");
        this.holderNodeId = holderNodeId;
        this.acquiredAt = acquiredAt;
        this.expiresAt = expiresAt;
        this.lockVersion = lockVersion;
        this.errorMessage = errorMessage;
    }

    /**
     * Creates a successful acquisition result.
     */
    public static DistributedLockResult acquired(String lockKey, String nodeId,
                                                  Instant acquired, Instant expires,
                                                  long version) {
        return new DistributedLockResult(
                Status.ACQUIRED,
                lockKey,
                nodeId,
                acquired,
                expires,
                version,
                null
        );
    }

    /**
     * Creates a result indicating the lock is already held.
     */
    public static DistributedLockResult alreadyHeld(String lockKey, String currentHolder,
                                                     Instant expires) {
        return new DistributedLockResult(
                Status.ALREADY_HELD,
                lockKey,
                currentHolder,
                null,
                expires,
                0,
                "Lock already held by " + currentHolder
        );
    }

    /**
     * Creates a timeout result.
     */
    public static DistributedLockResult timeout(String lockKey) {
        return new DistributedLockResult(
                Status.TIMEOUT,
                lockKey,
                null,
                null,
                null,
                0,
                "Lock acquisition timed out"
        );
    }

    /**
     * Creates a successful extension result.
     */
    public static DistributedLockResult extended(String lockKey, String nodeId,
                                                  Instant newExpiration, long version) {
        return new DistributedLockResult(
                Status.EXTENDED,
                lockKey,
                nodeId,
                null,
                newExpiration,
                version,
                null
        );
    }

    /**
     * Creates an error result.
     */
    public static DistributedLockResult error(String lockKey, String errorMessage) {
        return new DistributedLockResult(
                Status.ERROR,
                lockKey,
                null,
                null,
                null,
                0,
                errorMessage
        );
    }

    /**
     * Checks if the lock was successfully acquired.
     */
    public boolean isAcquired() {
        return status == Status.ACQUIRED;
    }

    /**
     * Checks if the operation was successful (acquired or extended).
     */
    public boolean isSuccessful() {
        return status == Status.ACQUIRED || status == Status.EXTENDED;
    }

    /**
     * Checks if the lock is still valid.
     */
    public boolean isValid() {
        return expiresAt != null && Instant.now().isBefore(expiresAt);
    }

    /**
     * Returns the remaining lock time.
     */
    public Optional<java.time.Duration> getRemainingTime() {
        if (expiresAt == null) {
            return Optional.empty();
        }
        java.time.Duration remaining = java.time.Duration.between(Instant.now(), expiresAt);
        return remaining.isNegative() ? Optional.empty() : Optional.of(remaining);
    }
}
