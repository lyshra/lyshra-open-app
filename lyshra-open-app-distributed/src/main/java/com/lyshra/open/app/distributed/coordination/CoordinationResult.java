package com.lyshra.open.app.distributed.coordination;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of a workflow ownership coordination operation.
 *
 * This immutable class represents the outcome of attempting to acquire
 * ownership of a workflow execution. It provides detailed information
 * about success/failure and relevant context for decision-making.
 *
 * Usage:
 * <pre>
 * CoordinationResult result = coordinator.acquireOwnership(key).block();
 * if (result.isAcquired()) {
 *     // Proceed with workflow execution
 *     long fencingToken = result.getFencingToken();
 * } else if (result.isAlreadyOwned()) {
 *     // Route to the actual owner
 *     String owner = result.getCurrentOwner().get();
 * }
 * </pre>
 *
 * Thread Safety: This class is immutable and thread-safe.
 */
@Getter
@Builder(toBuilder = true)
@ToString
public final class CoordinationResult {

    /**
     * Possible outcomes of a coordination operation.
     */
    public enum Status {
        /**
         * Ownership was successfully acquired.
         */
        ACQUIRED,

        /**
         * Ownership was already held by this node (idempotent success).
         */
        ALREADY_OWNED_BY_SELF,

        /**
         * Ownership is held by another node.
         */
        ALREADY_OWNED_BY_OTHER,

        /**
         * Workflow belongs to a different partition (wrong node).
         */
        WRONG_PARTITION,

        /**
         * Acquisition timed out while waiting for lock.
         */
        TIMEOUT,

        /**
         * Coordinator is not active or shutting down.
         */
        COORDINATOR_INACTIVE,

        /**
         * Backend is unavailable (connection lost).
         */
        BACKEND_UNAVAILABLE,

        /**
         * Fencing token validation failed (stale claim).
         */
        FENCING_CONFLICT,

        /**
         * Generic error during acquisition.
         */
        ERROR
    }

    /**
     * The workflow execution key this result is for.
     */
    private final String workflowExecutionKey;

    /**
     * The outcome status of the operation.
     */
    private final Status status;

    /**
     * The fencing token assigned (if acquired).
     */
    private final long fencingToken;

    /**
     * The ownership epoch (increments on each ownership transfer).
     */
    private final long ownershipEpoch;

    /**
     * The lease version (increments on each update).
     */
    private final long leaseVersion;

    /**
     * The node that acquired ownership (this node if acquired).
     */
    private final String ownerId;

    /**
     * The current owner if already owned by another node.
     */
    private final String currentOwner;

    /**
     * The partition ID for this workflow.
     */
    private final int partitionId;

    /**
     * The correct node if this is a wrong partition result.
     */
    private final String correctNode;

    /**
     * When the lease was acquired.
     */
    private final Instant leaseAcquiredAt;

    /**
     * When the lease expires.
     */
    private final Instant leaseExpiresAt;

    /**
     * Error message if the operation failed.
     */
    private final String errorMessage;

    /**
     * Exception that caused the failure (if any).
     */
    private final Throwable cause;

    // ========== Convenience Query Methods ==========

    /**
     * Checks if ownership was successfully acquired.
     *
     * @return true if this node now owns the workflow
     */
    public boolean isAcquired() {
        return status == Status.ACQUIRED || status == Status.ALREADY_OWNED_BY_SELF;
    }

    /**
     * Checks if the workflow is already owned by another node.
     *
     * @return true if owned by another node
     */
    public boolean isAlreadyOwned() {
        return status == Status.ALREADY_OWNED_BY_OTHER;
    }

    /**
     * Checks if this node is the wrong partition for the workflow.
     *
     * @return true if wrong partition
     */
    public boolean isWrongPartition() {
        return status == Status.WRONG_PARTITION;
    }

    /**
     * Checks if the operation failed due to an error.
     *
     * @return true if an error occurred
     */
    public boolean isError() {
        return status == Status.ERROR ||
               status == Status.TIMEOUT ||
               status == Status.BACKEND_UNAVAILABLE ||
               status == Status.FENCING_CONFLICT;
    }

    /**
     * Checks if the operation can be retried.
     *
     * @return true if retry is possible
     */
    public boolean isRetryable() {
        return status == Status.TIMEOUT ||
               status == Status.BACKEND_UNAVAILABLE;
    }

    /**
     * Gets the current owner as an Optional.
     *
     * @return Optional containing the current owner
     */
    public Optional<String> getCurrentOwnerOptional() {
        return Optional.ofNullable(currentOwner);
    }

    /**
     * Gets the correct node for routing as an Optional.
     *
     * @return Optional containing the correct node
     */
    public Optional<String> getCorrectNodeOptional() {
        return Optional.ofNullable(correctNode);
    }

    /**
     * Gets the error message as an Optional.
     *
     * @return Optional containing the error message
     */
    public Optional<String> getErrorMessageOptional() {
        return Optional.ofNullable(errorMessage);
    }

    /**
     * Gets the cause exception as an Optional.
     *
     * @return Optional containing the cause
     */
    public Optional<Throwable> getCauseOptional() {
        return Optional.ofNullable(cause);
    }

    // ========== Factory Methods ==========

    /**
     * Creates a successful acquisition result.
     */
    public static CoordinationResult acquired(String workflowKey,
                                               String ownerId,
                                               int partitionId,
                                               long fencingToken,
                                               long ownershipEpoch,
                                               long leaseVersion,
                                               Instant leaseAcquiredAt,
                                               Instant leaseExpiresAt) {
        return CoordinationResult.builder()
                .workflowExecutionKey(Objects.requireNonNull(workflowKey))
                .status(Status.ACQUIRED)
                .ownerId(Objects.requireNonNull(ownerId))
                .partitionId(partitionId)
                .fencingToken(fencingToken)
                .ownershipEpoch(ownershipEpoch)
                .leaseVersion(leaseVersion)
                .leaseAcquiredAt(leaseAcquiredAt)
                .leaseExpiresAt(leaseExpiresAt)
                .build();
    }

    /**
     * Creates a result indicating ownership was already held by this node.
     */
    public static CoordinationResult alreadyOwnedBySelf(String workflowKey,
                                                         String ownerId,
                                                         long fencingToken,
                                                         Instant leaseExpiresAt) {
        return CoordinationResult.builder()
                .workflowExecutionKey(Objects.requireNonNull(workflowKey))
                .status(Status.ALREADY_OWNED_BY_SELF)
                .ownerId(Objects.requireNonNull(ownerId))
                .currentOwner(ownerId)
                .fencingToken(fencingToken)
                .leaseExpiresAt(leaseExpiresAt)
                .build();
    }

    /**
     * Creates a result indicating ownership is held by another node.
     */
    public static CoordinationResult alreadyOwnedByOther(String workflowKey,
                                                          String currentOwner,
                                                          Instant leaseExpiresAt) {
        return CoordinationResult.builder()
                .workflowExecutionKey(Objects.requireNonNull(workflowKey))
                .status(Status.ALREADY_OWNED_BY_OTHER)
                .currentOwner(Objects.requireNonNull(currentOwner))
                .leaseExpiresAt(leaseExpiresAt)
                .build();
    }

    /**
     * Creates a wrong partition result.
     */
    public static CoordinationResult wrongPartition(String workflowKey,
                                                     int partitionId,
                                                     String correctNode) {
        return CoordinationResult.builder()
                .workflowExecutionKey(Objects.requireNonNull(workflowKey))
                .status(Status.WRONG_PARTITION)
                .partitionId(partitionId)
                .correctNode(correctNode)
                .build();
    }

    /**
     * Creates a timeout result.
     */
    public static CoordinationResult timeout(String workflowKey, String message) {
        return CoordinationResult.builder()
                .workflowExecutionKey(Objects.requireNonNull(workflowKey))
                .status(Status.TIMEOUT)
                .errorMessage(message)
                .build();
    }

    /**
     * Creates a coordinator inactive result.
     */
    public static CoordinationResult coordinatorInactive(String workflowKey) {
        return CoordinationResult.builder()
                .workflowExecutionKey(Objects.requireNonNull(workflowKey))
                .status(Status.COORDINATOR_INACTIVE)
                .errorMessage("Coordinator is not active")
                .build();
    }

    /**
     * Creates a backend unavailable result.
     */
    public static CoordinationResult backendUnavailable(String workflowKey, Throwable cause) {
        return CoordinationResult.builder()
                .workflowExecutionKey(Objects.requireNonNull(workflowKey))
                .status(Status.BACKEND_UNAVAILABLE)
                .errorMessage("Backend unavailable: " + (cause != null ? cause.getMessage() : "unknown"))
                .cause(cause)
                .build();
    }

    /**
     * Creates a fencing conflict result.
     */
    public static CoordinationResult fencingConflict(String workflowKey, long expectedToken, long actualToken) {
        return CoordinationResult.builder()
                .workflowExecutionKey(Objects.requireNonNull(workflowKey))
                .status(Status.FENCING_CONFLICT)
                .fencingToken(actualToken)
                .errorMessage(String.format("Fencing token conflict: expected %d, actual %d",
                        expectedToken, actualToken))
                .build();
    }

    /**
     * Creates a generic error result.
     */
    public static CoordinationResult error(String workflowKey, String message, Throwable cause) {
        return CoordinationResult.builder()
                .workflowExecutionKey(Objects.requireNonNull(workflowKey))
                .status(Status.ERROR)
                .errorMessage(message)
                .cause(cause)
                .build();
    }
}
