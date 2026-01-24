package com.lyshra.open.app.distributed.ownership;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable event representing a change in workflow ownership.
 *
 * Contains information about which workflow changed ownership, the previous
 * and new owners, and the reason for the change.
 */
@Getter
@Builder
@ToString
public final class WorkflowOwnershipChangeEvent {

    /**
     * Types of ownership changes.
     */
    public enum EventType {
        /**
         * Ownership was acquired by this node.
         */
        ACQUIRED,

        /**
         * Ownership was released by this node.
         */
        RELEASED,

        /**
         * Lease was successfully renewed.
         */
        RENEWED,

        /**
         * Lease expired without renewal.
         */
        EXPIRED,

        /**
         * Ownership was revoked (e.g., during failover).
         */
        REVOKED,

        /**
         * Ownership was transferred to another node.
         */
        TRANSFERRED
    }

    private final EventType eventType;
    private final String workflowExecutionKey;
    private final String previousOwnerId;
    private final String newOwnerId;
    private final String localNodeId;
    private final Instant timestamp;
    private final long ownershipVersion;
    private final String reason;

    private WorkflowOwnershipChangeEvent(EventType eventType,
                                          String workflowExecutionKey,
                                          String previousOwnerId,
                                          String newOwnerId,
                                          String localNodeId,
                                          Instant timestamp,
                                          long ownershipVersion,
                                          String reason) {
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.workflowExecutionKey = Objects.requireNonNull(workflowExecutionKey, "workflowExecutionKey must not be null");
        this.previousOwnerId = previousOwnerId;
        this.newOwnerId = newOwnerId;
        this.localNodeId = Objects.requireNonNull(localNodeId, "localNodeId must not be null");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.ownershipVersion = ownershipVersion;
        this.reason = reason;
    }

    /**
     * Creates an event for ownership acquisition.
     */
    public static WorkflowOwnershipChangeEvent acquired(String workflowExecutionKey,
                                                         String localNodeId,
                                                         long version) {
        return new WorkflowOwnershipChangeEvent(
                EventType.ACQUIRED,
                workflowExecutionKey,
                null,
                localNodeId,
                localNodeId,
                Instant.now(),
                version,
                "Ownership acquired"
        );
    }

    /**
     * Creates an event for ownership release.
     */
    public static WorkflowOwnershipChangeEvent released(String workflowExecutionKey,
                                                         String localNodeId,
                                                         String reason) {
        return new WorkflowOwnershipChangeEvent(
                EventType.RELEASED,
                workflowExecutionKey,
                localNodeId,
                null,
                localNodeId,
                Instant.now(),
                0,
                reason
        );
    }

    /**
     * Creates an event for lease expiration.
     */
    public static WorkflowOwnershipChangeEvent expired(String workflowExecutionKey,
                                                        String previousOwnerId,
                                                        String localNodeId) {
        return new WorkflowOwnershipChangeEvent(
                EventType.EXPIRED,
                workflowExecutionKey,
                previousOwnerId,
                null,
                localNodeId,
                Instant.now(),
                0,
                "Lease expired"
        );
    }

    /**
     * Creates an event for ownership revocation.
     */
    public static WorkflowOwnershipChangeEvent revoked(String workflowExecutionKey,
                                                        String previousOwnerId,
                                                        String localNodeId,
                                                        String reason) {
        return new WorkflowOwnershipChangeEvent(
                EventType.REVOKED,
                workflowExecutionKey,
                previousOwnerId,
                null,
                localNodeId,
                Instant.now(),
                0,
                reason
        );
    }

    /**
     * Checks if this event affects the local node.
     */
    public boolean affectsLocalNode() {
        return localNodeId.equals(previousOwnerId) || localNodeId.equals(newOwnerId);
    }

    /**
     * Checks if the local node gained ownership.
     */
    public boolean isLocalNodeGain() {
        return localNodeId.equals(newOwnerId) && !localNodeId.equals(previousOwnerId);
    }

    /**
     * Checks if the local node lost ownership.
     */
    public boolean isLocalNodeLoss() {
        return localNodeId.equals(previousOwnerId) && !localNodeId.equals(newOwnerId);
    }

    /**
     * Returns the previous owner as an Optional.
     */
    public Optional<String> getPreviousOwnerIdOptional() {
        return Optional.ofNullable(previousOwnerId);
    }

    /**
     * Returns the new owner as an Optional.
     */
    public Optional<String> getNewOwnerIdOptional() {
        return Optional.ofNullable(newOwnerId);
    }
}
