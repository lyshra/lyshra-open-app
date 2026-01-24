package com.lyshra.open.app.distributed.dispatcher;

import com.lyshra.open.app.distributed.coordination.OwnershipContext;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of a workflow dispatch operation.
 *
 * This immutable class represents the outcome of attempting to dispatch
 * a workflow for execution. It provides all information needed to determine
 * the next action: execute locally, forward to another node, or handle rejection.
 *
 * Possible outcomes:
 * - DISPATCHED_LOCALLY: Ownership acquired, execute on this node
 * - ROUTED_TO_OTHER: Workflow belongs to different node, forward it
 * - ALREADY_OWNED_LOCALLY: Re-dispatch of workflow already owned here
 * - REJECTED: Cannot dispatch, apply retry policy or fail
 *
 * Usage:
 * <pre>
 * WorkflowDispatchResult result = dispatcher.dispatch(request).block();
 *
 * switch (result.getOutcome()) {
 *     case DISPATCHED_LOCALLY:
 *         // Execute workflow with result.getOwnershipContext()
 *         long fencingToken = result.getFencingToken();
 *         break;
 *     case ROUTED_TO_OTHER:
 *         // Forward to result.getTargetNodeId()
 *         break;
 *     case REJECTED:
 *         // Handle based on result.getRejectReason()
 *         if (result.isRetryable()) {
 *             // Schedule retry
 *         }
 *         break;
 * }
 * </pre>
 *
 * Thread Safety: This class is immutable and thread-safe.
 */
@Getter
@Builder(toBuilder = true)
@ToString
public final class WorkflowDispatchResult {

    /**
     * Possible outcomes of a dispatch operation.
     */
    public enum Outcome {
        /**
         * Workflow was successfully dispatched to this node.
         * Ownership has been acquired and execution can proceed.
         */
        DISPATCHED_LOCALLY,

        /**
         * Workflow was already owned by this node.
         * This is an idempotent success - execution can proceed.
         */
        ALREADY_OWNED_LOCALLY,

        /**
         * Workflow belongs to a different node.
         * Forward the request to the target node.
         */
        ROUTED_TO_OTHER,

        /**
         * Workflow is currently owned by another node.
         * Either wait for completion or fail.
         */
        OWNED_BY_OTHER,

        /**
         * Dispatch was rejected for various reasons.
         * Check rejectReason for details.
         */
        REJECTED
    }

    /**
     * Reasons why a dispatch might be rejected.
     */
    public enum RejectReason {
        /**
         * No reason (not rejected).
         */
        NONE,

        /**
         * Ownership acquisition timed out.
         */
        ACQUISITION_TIMEOUT,

        /**
         * Coordinator is not active.
         */
        COORDINATOR_INACTIVE,

        /**
         * Backend storage is unavailable.
         */
        BACKEND_UNAVAILABLE,

        /**
         * Node is overloaded.
         */
        NODE_OVERLOADED,

        /**
         * Workflow capacity limit reached.
         */
        CAPACITY_EXCEEDED,

        /**
         * Fencing token validation failed.
         */
        FENCING_CONFLICT,

        /**
         * Dispatcher is shutting down.
         */
        DISPATCHER_SHUTTING_DOWN,

        /**
         * Maximum retry attempts exceeded.
         */
        MAX_RETRIES_EXCEEDED,

        /**
         * Invalid dispatch request.
         */
        INVALID_REQUEST,

        /**
         * Unknown error occurred.
         */
        UNKNOWN_ERROR
    }

    // ========== Result Identification ==========

    /**
     * The workflow execution key this result is for.
     */
    private final String executionKey;

    /**
     * The outcome of the dispatch operation.
     */
    private final Outcome outcome;

    /**
     * The reason for rejection (if rejected).
     */
    @Builder.Default
    private final RejectReason rejectReason = RejectReason.NONE;

    // ========== Ownership Information ==========

    /**
     * The ownership context (if dispatched locally).
     */
    private final OwnershipContext ownershipContext;

    /**
     * The fencing token for this dispatch.
     */
    @Builder.Default
    private final long fencingToken = -1;

    /**
     * The ownership epoch.
     */
    @Builder.Default
    private final long ownershipEpoch = 0;

    // ========== Routing Information ==========

    /**
     * The node that should handle this workflow (if routed).
     */
    private final String targetNodeId;

    /**
     * The current owner (if owned by another).
     */
    private final String currentOwnerId;

    /**
     * The partition this workflow belongs to.
     */
    @Builder.Default
    private final int partitionId = -1;

    // ========== Timing Information ==========

    /**
     * When the dispatch was initiated.
     */
    private final Instant dispatchedAt;

    /**
     * When ownership expires (if dispatched locally).
     */
    private final Instant ownershipExpiresAt;

    /**
     * How long the acquisition took.
     */
    private final java.time.Duration acquisitionDuration;

    // ========== Error Information ==========

    /**
     * Error message if the dispatch failed.
     */
    private final String errorMessage;

    /**
     * Exception that caused the failure.
     */
    private final Throwable cause;

    // ========== Retry Information ==========

    /**
     * Number of attempts made for this dispatch.
     */
    @Builder.Default
    private final int attemptCount = 1;

    /**
     * Whether this result is retryable.
     */
    @Builder.Default
    private final boolean retryable = false;

    /**
     * Suggested delay before retry.
     */
    private final java.time.Duration suggestedRetryDelay;

    // ========== Query Methods ==========

    /**
     * Checks if the workflow was dispatched locally.
     */
    public boolean isDispatchedLocally() {
        return outcome == Outcome.DISPATCHED_LOCALLY || outcome == Outcome.ALREADY_OWNED_LOCALLY;
    }

    /**
     * Checks if the workflow should be routed to another node.
     */
    public boolean isRoutedToOther() {
        return outcome == Outcome.ROUTED_TO_OTHER;
    }

    /**
     * Checks if the workflow is owned by another node.
     */
    public boolean isOwnedByOther() {
        return outcome == Outcome.OWNED_BY_OTHER;
    }

    /**
     * Checks if the dispatch was rejected.
     */
    public boolean isRejected() {
        return outcome == Outcome.REJECTED;
    }

    /**
     * Checks if the dispatch can be retried.
     */
    public boolean canRetry() {
        return retryable && (
                rejectReason == RejectReason.ACQUISITION_TIMEOUT ||
                rejectReason == RejectReason.BACKEND_UNAVAILABLE ||
                rejectReason == RejectReason.COORDINATOR_INACTIVE
        );
    }

    /**
     * Gets the ownership context as an Optional.
     */
    public Optional<OwnershipContext> getOwnershipContextOptional() {
        return Optional.ofNullable(ownershipContext);
    }

    /**
     * Gets the target node ID as an Optional.
     */
    public Optional<String> getTargetNodeIdOptional() {
        return Optional.ofNullable(targetNodeId);
    }

    /**
     * Gets the current owner ID as an Optional.
     */
    public Optional<String> getCurrentOwnerIdOptional() {
        return Optional.ofNullable(currentOwnerId);
    }

    /**
     * Gets the error message as an Optional.
     */
    public Optional<String> getErrorMessageOptional() {
        return Optional.ofNullable(errorMessage);
    }

    /**
     * Gets the suggested retry delay as an Optional.
     */
    public Optional<java.time.Duration> getSuggestedRetryDelayOptional() {
        return Optional.ofNullable(suggestedRetryDelay);
    }

    // ========== Factory Methods ==========

    /**
     * Creates a result for successful local dispatch.
     */
    public static WorkflowDispatchResult dispatchedLocally(String executionKey,
                                                            OwnershipContext context,
                                                            java.time.Duration acquisitionDuration) {
        return WorkflowDispatchResult.builder()
                .executionKey(Objects.requireNonNull(executionKey))
                .outcome(Outcome.DISPATCHED_LOCALLY)
                .ownershipContext(Objects.requireNonNull(context))
                .fencingToken(context.getFencingToken())
                .ownershipEpoch(context.getOwnershipEpoch())
                .partitionId(context.getPartitionId())
                .dispatchedAt(Instant.now())
                .ownershipExpiresAt(context.getExpiresAt())
                .acquisitionDuration(acquisitionDuration)
                .build();
    }

    /**
     * Creates a result for already owned locally.
     */
    public static WorkflowDispatchResult alreadyOwnedLocally(String executionKey,
                                                              OwnershipContext context) {
        return WorkflowDispatchResult.builder()
                .executionKey(Objects.requireNonNull(executionKey))
                .outcome(Outcome.ALREADY_OWNED_LOCALLY)
                .ownershipContext(context)
                .fencingToken(context.getFencingToken())
                .ownershipEpoch(context.getOwnershipEpoch())
                .partitionId(context.getPartitionId())
                .dispatchedAt(Instant.now())
                .ownershipExpiresAt(context.getExpiresAt())
                .build();
    }

    /**
     * Creates a result for routing to another node.
     */
    public static WorkflowDispatchResult routedToOther(String executionKey,
                                                        String targetNodeId,
                                                        int partitionId) {
        return WorkflowDispatchResult.builder()
                .executionKey(Objects.requireNonNull(executionKey))
                .outcome(Outcome.ROUTED_TO_OTHER)
                .targetNodeId(Objects.requireNonNull(targetNodeId))
                .partitionId(partitionId)
                .dispatchedAt(Instant.now())
                .build();
    }

    /**
     * Creates a result for workflow owned by another node.
     */
    public static WorkflowDispatchResult ownedByOther(String executionKey,
                                                       String currentOwnerId,
                                                       Instant ownershipExpiresAt) {
        return WorkflowDispatchResult.builder()
                .executionKey(Objects.requireNonNull(executionKey))
                .outcome(Outcome.OWNED_BY_OTHER)
                .currentOwnerId(Objects.requireNonNull(currentOwnerId))
                .ownershipExpiresAt(ownershipExpiresAt)
                .dispatchedAt(Instant.now())
                .build();
    }

    /**
     * Creates a rejected result due to timeout.
     */
    public static WorkflowDispatchResult timeout(String executionKey,
                                                  java.time.Duration acquisitionDuration,
                                                  int attemptCount) {
        return WorkflowDispatchResult.builder()
                .executionKey(Objects.requireNonNull(executionKey))
                .outcome(Outcome.REJECTED)
                .rejectReason(RejectReason.ACQUISITION_TIMEOUT)
                .dispatchedAt(Instant.now())
                .acquisitionDuration(acquisitionDuration)
                .attemptCount(attemptCount)
                .retryable(true)
                .suggestedRetryDelay(java.time.Duration.ofMillis(100))
                .errorMessage("Ownership acquisition timed out after " + acquisitionDuration)
                .build();
    }

    /**
     * Creates a rejected result due to coordinator being inactive.
     */
    public static WorkflowDispatchResult coordinatorInactive(String executionKey) {
        return WorkflowDispatchResult.builder()
                .executionKey(Objects.requireNonNull(executionKey))
                .outcome(Outcome.REJECTED)
                .rejectReason(RejectReason.COORDINATOR_INACTIVE)
                .dispatchedAt(Instant.now())
                .retryable(true)
                .suggestedRetryDelay(java.time.Duration.ofSeconds(1))
                .errorMessage("Ownership coordinator is not active")
                .build();
    }

    /**
     * Creates a rejected result due to backend unavailability.
     */
    public static WorkflowDispatchResult backendUnavailable(String executionKey, Throwable cause) {
        return WorkflowDispatchResult.builder()
                .executionKey(Objects.requireNonNull(executionKey))
                .outcome(Outcome.REJECTED)
                .rejectReason(RejectReason.BACKEND_UNAVAILABLE)
                .dispatchedAt(Instant.now())
                .retryable(true)
                .suggestedRetryDelay(java.time.Duration.ofMillis(500))
                .errorMessage("Backend unavailable: " + (cause != null ? cause.getMessage() : "unknown"))
                .cause(cause)
                .build();
    }

    /**
     * Creates a rejected result due to capacity exceeded.
     */
    public static WorkflowDispatchResult capacityExceeded(String executionKey,
                                                           int currentCount,
                                                           int maxCount) {
        return WorkflowDispatchResult.builder()
                .executionKey(Objects.requireNonNull(executionKey))
                .outcome(Outcome.REJECTED)
                .rejectReason(RejectReason.CAPACITY_EXCEEDED)
                .dispatchedAt(Instant.now())
                .retryable(false)
                .errorMessage(String.format("Node capacity exceeded: %d/%d workflows", currentCount, maxCount))
                .build();
    }

    /**
     * Creates a rejected result due to fencing conflict.
     */
    public static WorkflowDispatchResult fencingConflict(String executionKey,
                                                          long expectedToken,
                                                          long actualToken) {
        return WorkflowDispatchResult.builder()
                .executionKey(Objects.requireNonNull(executionKey))
                .outcome(Outcome.REJECTED)
                .rejectReason(RejectReason.FENCING_CONFLICT)
                .fencingToken(actualToken)
                .dispatchedAt(Instant.now())
                .retryable(false)
                .errorMessage(String.format("Fencing token conflict: expected %d, got %d", expectedToken, actualToken))
                .build();
    }

    /**
     * Creates a rejected result due to max retries exceeded.
     */
    public static WorkflowDispatchResult maxRetriesExceeded(String executionKey,
                                                             int attemptCount,
                                                             String lastError) {
        return WorkflowDispatchResult.builder()
                .executionKey(Objects.requireNonNull(executionKey))
                .outcome(Outcome.REJECTED)
                .rejectReason(RejectReason.MAX_RETRIES_EXCEEDED)
                .dispatchedAt(Instant.now())
                .attemptCount(attemptCount)
                .retryable(false)
                .errorMessage("Max retries exceeded after " + attemptCount + " attempts: " + lastError)
                .build();
    }

    /**
     * Creates a rejected result due to dispatcher shutting down.
     */
    public static WorkflowDispatchResult dispatcherShuttingDown(String executionKey) {
        return WorkflowDispatchResult.builder()
                .executionKey(Objects.requireNonNull(executionKey))
                .outcome(Outcome.REJECTED)
                .rejectReason(RejectReason.DISPATCHER_SHUTTING_DOWN)
                .dispatchedAt(Instant.now())
                .retryable(false)
                .errorMessage("Dispatcher is shutting down")
                .build();
    }

    /**
     * Creates a rejected result due to an error.
     */
    public static WorkflowDispatchResult error(String executionKey,
                                                String message,
                                                Throwable cause) {
        return WorkflowDispatchResult.builder()
                .executionKey(Objects.requireNonNull(executionKey))
                .outcome(Outcome.REJECTED)
                .rejectReason(RejectReason.UNKNOWN_ERROR)
                .dispatchedAt(Instant.now())
                .retryable(false)
                .errorMessage(message)
                .cause(cause)
                .build();
    }
}
