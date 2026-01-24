package com.lyshra.open.app.core.engine.lock;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.Instant;

/**
 * Represents a lock on a workflow instance.
 *
 * <p>Contains all information about an active lock including ownership,
 * timing, and metadata for debugging and monitoring.</p>
 */
@Data
@Builder
public class WorkflowLock {

    /**
     * Lock status enumeration.
     */
    public enum LockStatus {
        /** Lock is currently active and valid */
        ACTIVE,
        /** Lock has expired but not yet cleaned up */
        EXPIRED,
        /** Lock was released normally */
        RELEASED,
        /** Lock was forcefully released by admin */
        FORCE_RELEASED
    }

    /**
     * The workflow instance this lock protects.
     */
    private final String workflowInstanceId;

    /**
     * The identifier of the lock owner.
     */
    private final String ownerId;

    /**
     * When the lock was acquired.
     */
    private final Instant acquiredAt;

    /**
     * When the lock expires.
     */
    private final Instant expiresAt;

    /**
     * The current status of the lock.
     */
    @Builder.Default
    private final LockStatus status = LockStatus.ACTIVE;

    /**
     * The operation that acquired the lock (for debugging).
     */
    private final String operation;

    /**
     * Number of times the lock has been extended.
     */
    @Builder.Default
    private final int extensionCount = 0;

    /**
     * Last time the lock was extended.
     */
    private final Instant lastExtendedAt;

    /**
     * Hostname/instance of the lock holder.
     */
    private final String holderHost;

    /**
     * Thread ID of the lock holder.
     */
    private final String holderThreadId;

    /**
     * Checks if the lock is currently valid (active and not expired).
     */
    public boolean isValid() {
        return status == LockStatus.ACTIVE && !isExpired();
    }

    /**
     * Checks if the lock has expired.
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Gets the remaining time until expiration.
     */
    public Duration getRemainingTime() {
        if (expiresAt == null) {
            return Duration.ZERO;
        }
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /**
     * Gets the duration the lock has been held.
     */
    public Duration getHeldDuration() {
        return Duration.between(acquiredAt, Instant.now());
    }

    /**
     * Creates an extended version of this lock.
     */
    public WorkflowLock extend(Duration extensionDuration) {
        return WorkflowLock.builder()
                .workflowInstanceId(this.workflowInstanceId)
                .ownerId(this.ownerId)
                .acquiredAt(this.acquiredAt)
                .expiresAt(Instant.now().plus(extensionDuration))
                .status(LockStatus.ACTIVE)
                .operation(this.operation)
                .extensionCount(this.extensionCount + 1)
                .lastExtendedAt(Instant.now())
                .holderHost(this.holderHost)
                .holderThreadId(this.holderThreadId)
                .build();
    }

    /**
     * Creates a released version of this lock.
     */
    public WorkflowLock release() {
        return WorkflowLock.builder()
                .workflowInstanceId(this.workflowInstanceId)
                .ownerId(this.ownerId)
                .acquiredAt(this.acquiredAt)
                .expiresAt(this.expiresAt)
                .status(LockStatus.RELEASED)
                .operation(this.operation)
                .extensionCount(this.extensionCount)
                .lastExtendedAt(this.lastExtendedAt)
                .holderHost(this.holderHost)
                .holderThreadId(this.holderThreadId)
                .build();
    }

    /**
     * Creates a new lock for a workflow instance.
     */
    public static WorkflowLock create(String workflowInstanceId, String ownerId,
                                       Duration duration, String operation) {
        return WorkflowLock.builder()
                .workflowInstanceId(workflowInstanceId)
                .ownerId(ownerId)
                .acquiredAt(Instant.now())
                .expiresAt(Instant.now().plus(duration))
                .status(LockStatus.ACTIVE)
                .operation(operation)
                .extensionCount(0)
                .holderHost(getHostName())
                .holderThreadId(Thread.currentThread().getName())
                .build();
    }

    private static String getHostName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
