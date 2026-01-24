package com.lyshra.open.app.distributed.coordination;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Event representing a change in workflow ownership coordination.
 *
 * These events are emitted by the coordinator when ownership state changes,
 * allowing components to react to ownership transitions.
 *
 * Thread Safety: This class is immutable and thread-safe.
 */
@Getter
@Builder(toBuilder = true)
@ToString
public final class CoordinationEvent {

    /**
     * Types of coordination events.
     */
    public enum EventType {
        /**
         * Ownership was successfully acquired.
         */
        OWNERSHIP_ACQUIRED,

        /**
         * Ownership was renewed (lease extended).
         */
        OWNERSHIP_RENEWED,

        /**
         * Ownership was explicitly released.
         */
        OWNERSHIP_RELEASED,

        /**
         * Ownership was lost (lease expired or revoked).
         */
        OWNERSHIP_LOST,

        /**
         * Ownership was transferred from another node.
         */
        OWNERSHIP_TRANSFERRED,

        /**
         * Orphan workflow was recovered.
         */
        ORPHAN_RECOVERED,

        /**
         * Lease renewal failed.
         */
        RENEWAL_FAILED,

        /**
         * Fencing conflict detected.
         */
        FENCING_CONFLICT,

        /**
         * Coordinator became active.
         */
        COORDINATOR_ACTIVE,

        /**
         * Coordinator became inactive.
         */
        COORDINATOR_INACTIVE,

        /**
         * Backend connection lost.
         */
        BACKEND_DISCONNECTED,

        /**
         * Backend connection restored.
         */
        BACKEND_RECONNECTED
    }

    /**
     * The type of event.
     */
    private final EventType type;

    /**
     * The workflow execution key (null for coordinator-level events).
     */
    private final String workflowExecutionKey;

    /**
     * The node that now owns the workflow (if applicable).
     */
    private final String newOwnerId;

    /**
     * The node that previously owned the workflow (if applicable).
     */
    private final String previousOwnerId;

    /**
     * The fencing token associated with this event.
     */
    private final long fencingToken;

    /**
     * The ownership epoch.
     */
    private final long ownershipEpoch;

    /**
     * When the event occurred.
     */
    private final Instant timestamp;

    /**
     * Reason for the ownership change.
     */
    private final String reason;

    /**
     * Additional context information.
     */
    private final String contextInfo;

    /**
     * The partition ID (if applicable).
     */
    private final int partitionId;

    /**
     * Private constructor - use builder or factory methods.
     */
    private CoordinationEvent(EventType type,
                               String workflowExecutionKey,
                               String newOwnerId,
                               String previousOwnerId,
                               long fencingToken,
                               long ownershipEpoch,
                               Instant timestamp,
                               String reason,
                               String contextInfo,
                               int partitionId) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.workflowExecutionKey = workflowExecutionKey;
        this.newOwnerId = newOwnerId;
        this.previousOwnerId = previousOwnerId;
        this.fencingToken = fencingToken;
        this.ownershipEpoch = ownershipEpoch;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.reason = reason;
        this.contextInfo = contextInfo;
        this.partitionId = partitionId;
    }

    // ========== Query Methods ==========

    /**
     * Checks if this is a workflow-level event.
     */
    public boolean isWorkflowEvent() {
        return workflowExecutionKey != null;
    }

    /**
     * Checks if this is a coordinator-level event.
     */
    public boolean isCoordinatorEvent() {
        return type == EventType.COORDINATOR_ACTIVE ||
               type == EventType.COORDINATOR_INACTIVE ||
               type == EventType.BACKEND_DISCONNECTED ||
               type == EventType.BACKEND_RECONNECTED;
    }

    /**
     * Checks if this event indicates ownership gain.
     */
    public boolean isOwnershipGained() {
        return type == EventType.OWNERSHIP_ACQUIRED ||
               type == EventType.OWNERSHIP_TRANSFERRED ||
               type == EventType.ORPHAN_RECOVERED;
    }

    /**
     * Checks if this event indicates ownership loss.
     */
    public boolean isOwnershipLost() {
        return type == EventType.OWNERSHIP_RELEASED ||
               type == EventType.OWNERSHIP_LOST ||
               type == EventType.FENCING_CONFLICT;
    }

    /**
     * Gets the workflow key as an Optional.
     */
    public Optional<String> getWorkflowKeyOptional() {
        return Optional.ofNullable(workflowExecutionKey);
    }

    /**
     * Gets the new owner as an Optional.
     */
    public Optional<String> getNewOwnerIdOptional() {
        return Optional.ofNullable(newOwnerId);
    }

    /**
     * Gets the previous owner as an Optional.
     */
    public Optional<String> getPreviousOwnerIdOptional() {
        return Optional.ofNullable(previousOwnerId);
    }

    /**
     * Gets the reason as an Optional.
     */
    public Optional<String> getReasonOptional() {
        return Optional.ofNullable(reason);
    }

    // ========== Factory Methods ==========

    /**
     * Creates an ownership acquired event.
     */
    public static CoordinationEvent ownershipAcquired(String workflowKey,
                                                       String ownerId,
                                                       int partitionId,
                                                       long fencingToken,
                                                       long epoch) {
        return CoordinationEvent.builder()
                .type(EventType.OWNERSHIP_ACQUIRED)
                .workflowExecutionKey(workflowKey)
                .newOwnerId(ownerId)
                .partitionId(partitionId)
                .fencingToken(fencingToken)
                .ownershipEpoch(epoch)
                .timestamp(Instant.now())
                .reason("Ownership acquired")
                .build();
    }

    /**
     * Creates an ownership renewed event.
     */
    public static CoordinationEvent ownershipRenewed(String workflowKey,
                                                      String ownerId,
                                                      long fencingToken) {
        return CoordinationEvent.builder()
                .type(EventType.OWNERSHIP_RENEWED)
                .workflowExecutionKey(workflowKey)
                .newOwnerId(ownerId)
                .fencingToken(fencingToken)
                .timestamp(Instant.now())
                .reason("Lease renewed")
                .build();
    }

    /**
     * Creates an ownership released event.
     */
    public static CoordinationEvent ownershipReleased(String workflowKey,
                                                       String ownerId,
                                                       String reason) {
        return CoordinationEvent.builder()
                .type(EventType.OWNERSHIP_RELEASED)
                .workflowExecutionKey(workflowKey)
                .previousOwnerId(ownerId)
                .timestamp(Instant.now())
                .reason(reason)
                .build();
    }

    /**
     * Creates an ownership lost event.
     */
    public static CoordinationEvent ownershipLost(String workflowKey,
                                                   String previousOwnerId,
                                                   String reason) {
        return CoordinationEvent.builder()
                .type(EventType.OWNERSHIP_LOST)
                .workflowExecutionKey(workflowKey)
                .previousOwnerId(previousOwnerId)
                .timestamp(Instant.now())
                .reason(reason)
                .build();
    }

    /**
     * Creates an ownership transferred event.
     */
    public static CoordinationEvent ownershipTransferred(String workflowKey,
                                                          String newOwnerId,
                                                          String previousOwnerId,
                                                          long fencingToken,
                                                          long epoch,
                                                          String reason) {
        return CoordinationEvent.builder()
                .type(EventType.OWNERSHIP_TRANSFERRED)
                .workflowExecutionKey(workflowKey)
                .newOwnerId(newOwnerId)
                .previousOwnerId(previousOwnerId)
                .fencingToken(fencingToken)
                .ownershipEpoch(epoch)
                .timestamp(Instant.now())
                .reason(reason)
                .build();
    }

    /**
     * Creates an orphan recovered event.
     */
    public static CoordinationEvent orphanRecovered(String workflowKey,
                                                     String newOwnerId,
                                                     String failedNodeId,
                                                     long fencingToken,
                                                     long epoch) {
        return CoordinationEvent.builder()
                .type(EventType.ORPHAN_RECOVERED)
                .workflowExecutionKey(workflowKey)
                .newOwnerId(newOwnerId)
                .previousOwnerId(failedNodeId)
                .fencingToken(fencingToken)
                .ownershipEpoch(epoch)
                .timestamp(Instant.now())
                .reason("Recovered from failed node: " + failedNodeId)
                .build();
    }

    /**
     * Creates a renewal failed event.
     */
    public static CoordinationEvent renewalFailed(String workflowKey,
                                                   String ownerId,
                                                   String reason) {
        return CoordinationEvent.builder()
                .type(EventType.RENEWAL_FAILED)
                .workflowExecutionKey(workflowKey)
                .previousOwnerId(ownerId)
                .timestamp(Instant.now())
                .reason(reason)
                .build();
    }

    /**
     * Creates a fencing conflict event.
     */
    public static CoordinationEvent fencingConflict(String workflowKey,
                                                     String nodeId,
                                                     long expectedToken,
                                                     long actualToken) {
        return CoordinationEvent.builder()
                .type(EventType.FENCING_CONFLICT)
                .workflowExecutionKey(workflowKey)
                .previousOwnerId(nodeId)
                .fencingToken(actualToken)
                .timestamp(Instant.now())
                .reason("Fencing conflict: expected " + expectedToken + ", actual " + actualToken)
                .build();
    }

    /**
     * Creates a coordinator active event.
     */
    public static CoordinationEvent coordinatorActive(String nodeId) {
        return CoordinationEvent.builder()
                .type(EventType.COORDINATOR_ACTIVE)
                .newOwnerId(nodeId)
                .timestamp(Instant.now())
                .reason("Coordinator initialized")
                .build();
    }

    /**
     * Creates a coordinator inactive event.
     */
    public static CoordinationEvent coordinatorInactive(String nodeId, String reason) {
        return CoordinationEvent.builder()
                .type(EventType.COORDINATOR_INACTIVE)
                .previousOwnerId(nodeId)
                .timestamp(Instant.now())
                .reason(reason)
                .build();
    }

    /**
     * Creates a backend disconnected event.
     */
    public static CoordinationEvent backendDisconnected(String nodeId, String reason) {
        return CoordinationEvent.builder()
                .type(EventType.BACKEND_DISCONNECTED)
                .newOwnerId(nodeId)
                .timestamp(Instant.now())
                .reason(reason)
                .build();
    }

    /**
     * Creates a backend reconnected event.
     */
    public static CoordinationEvent backendReconnected(String nodeId) {
        return CoordinationEvent.builder()
                .type(EventType.BACKEND_RECONNECTED)
                .newOwnerId(nodeId)
                .timestamp(Instant.now())
                .reason("Backend connection restored")
                .build();
    }
}
