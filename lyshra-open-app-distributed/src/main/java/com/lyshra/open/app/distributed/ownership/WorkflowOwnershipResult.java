package com.lyshra.open.app.distributed.ownership;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable result of a workflow ownership acquisition attempt.
 *
 * Contains information about whether ownership was acquired, the lease details,
 * and in case of failure, the current owner information.
 */
@Getter
@Builder
@ToString
public final class WorkflowOwnershipResult {

    /**
     * Possible outcomes of an ownership acquisition attempt.
     */
    public enum Status {
        /**
         * Ownership was successfully acquired.
         */
        ACQUIRED,

        /**
         * Ownership could not be acquired; already owned by another node.
         */
        ALREADY_OWNED,

        /**
         * Ownership could not be acquired; workflow is on a different partition.
         */
        WRONG_PARTITION,

        /**
         * Ownership could not be acquired due to a system error.
         */
        ERROR,

        /**
         * Ownership was renewed (for renewal requests).
         */
        RENEWED
    }

    private final Status status;
    private final String workflowExecutionKey;
    private final String ownerNodeId;
    private final Instant leaseStartTime;
    private final Instant leaseExpirationTime;
    private final long leaseVersion;
    private final String errorMessage;

    private WorkflowOwnershipResult(Status status,
                                     String workflowExecutionKey,
                                     String ownerNodeId,
                                     Instant leaseStartTime,
                                     Instant leaseExpirationTime,
                                     long leaseVersion,
                                     String errorMessage) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.workflowExecutionKey = Objects.requireNonNull(workflowExecutionKey, "workflowExecutionKey must not be null");
        this.ownerNodeId = ownerNodeId;
        this.leaseStartTime = leaseStartTime;
        this.leaseExpirationTime = leaseExpirationTime;
        this.leaseVersion = leaseVersion;
        this.errorMessage = errorMessage;
    }

    /**
     * Creates a successful acquisition result.
     */
    public static WorkflowOwnershipResult acquired(String workflowExecutionKey,
                                                    String nodeId,
                                                    Instant leaseStart,
                                                    Instant leaseExpiration,
                                                    long version) {
        return new WorkflowOwnershipResult(
                Status.ACQUIRED,
                workflowExecutionKey,
                nodeId,
                leaseStart,
                leaseExpiration,
                version,
                null
        );
    }

    /**
     * Creates a result indicating ownership is held by another node.
     */
    public static WorkflowOwnershipResult alreadyOwned(String workflowExecutionKey,
                                                        String currentOwnerNodeId,
                                                        Instant leaseExpiration) {
        return new WorkflowOwnershipResult(
                Status.ALREADY_OWNED,
                workflowExecutionKey,
                currentOwnerNodeId,
                null,
                leaseExpiration,
                0,
                "Workflow is already owned by node: " + currentOwnerNodeId
        );
    }

    /**
     * Creates a result indicating the workflow belongs to a different partition.
     */
    public static WorkflowOwnershipResult wrongPartition(String workflowExecutionKey,
                                                          int expectedPartition,
                                                          String expectedNode) {
        return new WorkflowOwnershipResult(
                Status.WRONG_PARTITION,
                workflowExecutionKey,
                expectedNode,
                null,
                null,
                0,
                "Workflow belongs to partition " + expectedPartition + " owned by " + expectedNode
        );
    }

    /**
     * Creates an error result.
     */
    public static WorkflowOwnershipResult error(String workflowExecutionKey, String errorMessage) {
        return new WorkflowOwnershipResult(
                Status.ERROR,
                workflowExecutionKey,
                null,
                null,
                null,
                0,
                errorMessage
        );
    }

    /**
     * Creates a successful renewal result.
     */
    public static WorkflowOwnershipResult renewed(String workflowExecutionKey,
                                                   String nodeId,
                                                   Instant newExpiration,
                                                   long newVersion) {
        return new WorkflowOwnershipResult(
                Status.RENEWED,
                workflowExecutionKey,
                nodeId,
                null,
                newExpiration,
                newVersion,
                null
        );
    }

    /**
     * Checks if ownership was successfully acquired.
     */
    public boolean isAcquired() {
        return status == Status.ACQUIRED;
    }

    /**
     * Checks if ownership was successfully renewed.
     */
    public boolean isRenewed() {
        return status == Status.RENEWED;
    }

    /**
     * Checks if the operation was successful (acquired or renewed).
     */
    public boolean isSuccessful() {
        return status == Status.ACQUIRED || status == Status.RENEWED;
    }

    /**
     * Checks if the lease is still valid.
     */
    public boolean isLeaseValid() {
        return leaseExpirationTime != null && Instant.now().isBefore(leaseExpirationTime);
    }

    /**
     * Returns the remaining lease time.
     */
    public Optional<java.time.Duration> getRemainingLeaseTime() {
        if (leaseExpirationTime == null) {
            return Optional.empty();
        }
        java.time.Duration remaining = java.time.Duration.between(Instant.now(), leaseExpirationTime);
        return remaining.isNegative() ? Optional.empty() : Optional.of(remaining);
    }
}
