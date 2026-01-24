package com.lyshra.open.app.distributed.state;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Interface for durable workflow state persistence.
 *
 * Provides reliable storage for workflow execution state, enabling:
 * - Recovery after node failures
 * - Workflow resumption from checkpoints
 * - Distributed state synchronization
 * - Audit trail and history
 *
 * The state store uses optimistic locking (version/epoch) to handle
 * concurrent updates safely.
 *
 * Design Pattern: Repository Pattern - abstracts data access from business logic.
 */
public interface IWorkflowStateStore {

    /**
     * Initializes the state store.
     *
     * @return Mono that completes when initialization is done
     */
    Mono<Void> initialize();

    /**
     * Shuts down the state store.
     *
     * @return Mono that completes when shutdown is done
     */
    Mono<Void> shutdown();

    /**
     * Saves or updates a workflow execution state.
     *
     * Uses optimistic locking - the update will fail if the state
     * has been modified since it was last read.
     *
     * @param state the workflow state to save
     * @return Mono containing the saved state with updated version
     */
    Mono<WorkflowExecutionState> save(WorkflowExecutionState state);

    /**
     * Saves or updates a workflow execution state with fencing token validation.
     *
     * This method provides split-brain prevention by validating that the provided
     * fencing token is still valid (not superseded by a newer token from a takeover).
     * The update will fail if:
     * - The state has been modified since last read (optimistic locking)
     * - The provided fencing token is lower than the current token (stale owner)
     *
     * Use this method for all state updates during workflow execution to prevent
     * zombie processes from corrupting state after ownership transfer.
     *
     * @param state the workflow state to save
     * @param fencingToken the fencing token of the current owner
     * @return Mono containing the saved state if successful, empty if fencing token invalid
     */
    default Mono<WorkflowExecutionState> saveWithFencingToken(WorkflowExecutionState state, long fencingToken) {
        // Default implementation: validate fencing token matches state
        if (state.getFencingToken() != fencingToken) {
            return Mono.empty(); // Fencing token mismatch - stale owner
        }
        return save(state);
    }

    /**
     * Creates a checkpoint with fencing token validation.
     *
     * The checkpoint will only be saved if the fencing token is still valid.
     *
     * @param executionKey the execution key
     * @param checkpoint the checkpoint data
     * @param fencingToken the fencing token of the current owner
     * @return Mono that completes when checkpoint is saved, errors if token invalid
     */
    default Mono<Void> saveCheckpointWithFencingToken(String executionKey, WorkflowCheckpoint checkpoint, long fencingToken) {
        return findByExecutionKey(executionKey)
                .flatMap(optState -> {
                    if (optState.isEmpty()) {
                        return Mono.error(new IllegalStateException("Workflow state not found: " + executionKey));
                    }
                    WorkflowExecutionState state = optState.get();
                    if (state.getFencingToken() > fencingToken) {
                        return Mono.error(new IllegalStateException(
                                "Fencing token validation failed: current=" + state.getFencingToken() + ", provided=" + fencingToken));
                    }
                    return saveCheckpoint(executionKey, checkpoint);
                });
    }

    /**
     * Loads a workflow execution state by its execution key.
     *
     * @param executionKey the unique workflow execution key
     * @return Mono containing the state if found
     */
    Mono<Optional<WorkflowExecutionState>> findByExecutionKey(String executionKey);

    /**
     * Loads all workflow executions for a specific partition.
     *
     * @param partitionId the partition ID
     * @return Flux of workflow states
     */
    Flux<WorkflowExecutionState> findByPartition(int partitionId);

    /**
     * Loads all workflow executions owned by a specific node.
     *
     * @param nodeId the owner node ID
     * @return Flux of workflow states
     */
    Flux<WorkflowExecutionState> findByOwner(String nodeId);

    /**
     * Loads all workflow executions in a specific status.
     *
     * @param status the execution status
     * @return Flux of workflow states
     */
    Flux<WorkflowExecutionState> findByStatus(WorkflowExecutionState.ExecutionStatus status);

    /**
     * Loads all workflow executions that need recovery.
     *
     * This includes executions that:
     * - Were running when a node failed
     * - Have stale leases
     * - Are stuck in intermediate states
     *
     * @param staleThreshold the threshold for considering a state stale
     * @return Flux of workflow states needing recovery
     */
    Flux<WorkflowExecutionState> findStaleExecutions(java.time.Duration staleThreshold);

    /**
     * Deletes a workflow execution state.
     *
     * @param executionKey the execution key to delete
     * @return Mono that completes when deletion is done
     */
    Mono<Void> delete(String executionKey);

    /**
     * Deletes all workflow execution states older than the specified time.
     *
     * @param before the cutoff time
     * @return Mono containing the number of deleted states
     */
    Mono<Integer> deleteOlderThan(Instant before);

    /**
     * Updates the owner of a workflow execution.
     *
     * @param executionKey the execution key
     * @param newOwnerNodeId the new owner node ID
     * @param expectedVersion the expected current version for optimistic locking
     * @return Mono containing true if update succeeded
     */
    Mono<Boolean> updateOwner(String executionKey, String newOwnerNodeId, long expectedVersion);

    /**
     * Updates the status of a workflow execution.
     *
     * @param executionKey the execution key
     * @param newStatus the new status
     * @param expectedVersion the expected current version for optimistic locking
     * @return Mono containing true if update succeeded
     */
    Mono<Boolean> updateStatus(String executionKey, WorkflowExecutionState.ExecutionStatus newStatus,
                               long expectedVersion);

    /**
     * Creates a checkpoint for a workflow execution.
     *
     * @param executionKey the execution key
     * @param checkpoint the checkpoint data
     * @return Mono that completes when checkpoint is saved
     */
    Mono<Void> saveCheckpoint(String executionKey, WorkflowCheckpoint checkpoint);

    /**
     * Loads the latest checkpoint for a workflow execution.
     *
     * @param executionKey the execution key
     * @return Mono containing the latest checkpoint if found
     */
    Mono<Optional<WorkflowCheckpoint>> loadLatestCheckpoint(String executionKey);

    /**
     * Loads all checkpoints for a workflow execution.
     *
     * @param executionKey the execution key
     * @return Flux of checkpoints ordered by creation time
     */
    Flux<WorkflowCheckpoint> loadAllCheckpoints(String executionKey);

    /**
     * Counts workflow executions by status.
     *
     * @param status the status to count
     * @return Mono containing the count
     */
    Mono<Long> countByStatus(WorkflowExecutionState.ExecutionStatus status);

    /**
     * Counts workflow executions by partition.
     *
     * @param partitionId the partition ID
     * @return Mono containing the count
     */
    Mono<Long> countByPartition(int partitionId);

    /**
     * Checks if a workflow execution exists.
     *
     * @param executionKey the execution key
     * @return Mono containing true if exists
     */
    Mono<Boolean> exists(String executionKey);

    /**
     * Bulk updates owner for all executions owned by a failed node.
     *
     * @param failedNodeId the failed node's ID
     * @param newOwnerNodeId the new owner (null for unassigned)
     * @return Mono containing the number of updated executions
     */
    Mono<Integer> reassignOwnership(String failedNodeId, String newOwnerNodeId);

    // ========== Ownership-Specific Query Methods ==========

    /**
     * Finds all workflow executions with a specific ownership status.
     *
     * @param status the ownership status to filter by
     * @return Flux of workflow states with the specified ownership status
     */
    Flux<WorkflowExecutionState> findByOwnershipStatus(WorkflowExecutionState.OwnershipStatus status);

    /**
     * Finds all orphaned workflow executions.
     *
     * Orphaned executions include those with:
     * - OwnershipStatus.ORPHANED
     * - OwnershipStatus.LEASE_EXPIRED
     * - OWNED status but with expired lease
     *
     * @return Flux of orphaned workflow states
     */
    Flux<WorkflowExecutionState> findOrphanedExecutions();

    /**
     * Finds all workflow executions available for takeover.
     *
     * This includes executions that are:
     * - UNOWNED
     * - ORPHANED
     * - RELEASED
     * - LEASE_EXPIRED
     * - REVOKED
     * - OWNED with expired lease
     *
     * @return Flux of workflow states available for takeover
     */
    Flux<WorkflowExecutionState> findAvailableForTakeover();

    /**
     * Finds all workflow executions with expired leases.
     *
     * @param cutoffTime executions with leaseExpiresAt before this time are considered expired
     * @return Flux of workflow states with expired leases
     */
    Flux<WorkflowExecutionState> findWithExpiredLeases(Instant cutoffTime);

    /**
     * Finds all workflow executions with recovery in progress.
     *
     * @return Flux of workflow states in PENDING_TAKEOVER or ACQUIRING status
     */
    Flux<WorkflowExecutionState> findRecoveryInProgress();

    /**
     * Finds workflow executions that have exceeded max recovery attempts.
     *
     * @return Flux of workflow states that have exhausted recovery attempts
     */
    Flux<WorkflowExecutionState> findExhaustedRecoveryAttempts();

    /**
     * Finds workflow executions by owner with a specific ownership status.
     *
     * @param nodeId the owner node ID
     * @param status the ownership status
     * @return Flux of workflow states matching both criteria
     */
    Flux<WorkflowExecutionState> findByOwnerAndOwnershipStatus(String nodeId,
                                                                 WorkflowExecutionState.OwnershipStatus status);

    // ========== Atomic Ownership Update Methods ==========

    /**
     * Atomically updates the ownership status of a workflow execution.
     *
     * @param executionKey the execution key
     * @param newStatus the new ownership status
     * @param reason the reason for the status change
     * @param expectedVersion the expected current version for optimistic locking
     * @return Mono containing true if update succeeded
     */
    Mono<Boolean> updateOwnershipStatus(String executionKey,
                                         WorkflowExecutionState.OwnershipStatus newStatus,
                                         String reason,
                                         long expectedVersion);

    /**
     * Atomically acquires ownership of a workflow execution.
     *
     * This method sets all ownership-related fields atomically:
     * - ownerNodeId
     * - leaseId
     * - leaseAcquiredAt
     * - leaseExpiresAt
     * - fencingToken
     * - ownershipStatus = OWNED
     *
     * @param executionKey the execution key
     * @param nodeId the acquiring node ID
     * @param leaseId the lease ID
     * @param leaseDuration the duration of the lease
     * @param fencingToken the fencing token
     * @param expectedVersion the expected current version for optimistic locking
     * @return Mono containing the updated state if successful, empty if failed
     */
    Mono<WorkflowExecutionState> acquireOwnership(String executionKey,
                                                    String nodeId,
                                                    String leaseId,
                                                    Duration leaseDuration,
                                                    long fencingToken,
                                                    long expectedVersion);

    /**
     * Atomically renews the lease for a workflow execution.
     *
     * @param executionKey the execution key
     * @param extensionDuration the duration to extend the lease by
     * @param expectedVersion the expected current version for optimistic locking
     * @return Mono containing true if renewal succeeded
     */
    Mono<Boolean> renewLease(String executionKey, Duration extensionDuration, long expectedVersion);

    /**
     * Atomically releases ownership of a workflow execution.
     *
     * Sets:
     * - ownerNodeId = null (or previous)
     * - ownershipStatus = RELEASED
     * - Records the release reason
     *
     * @param executionKey the execution key
     * @param reason the reason for release
     * @param expectedVersion the expected current version for optimistic locking
     * @return Mono containing true if release succeeded
     */
    Mono<Boolean> releaseOwnership(String executionKey, String reason, long expectedVersion);

    /**
     * Atomically transfers ownership of a workflow execution to a new node.
     *
     * @param executionKey the execution key
     * @param newOwnerId the new owner node ID
     * @param newLeaseId the new lease ID
     * @param leaseDuration the duration of the new lease
     * @param newFencingToken the new fencing token
     * @param reason the reason for transfer
     * @param expectedVersion the expected current version for optimistic locking
     * @return Mono containing the updated state if successful, empty if failed
     */
    Mono<WorkflowExecutionState> transferOwnership(String executionKey,
                                                     String newOwnerId,
                                                     String newLeaseId,
                                                     Duration leaseDuration,
                                                     long newFencingToken,
                                                     String reason,
                                                     long expectedVersion);

    /**
     * Records a failed recovery attempt for a workflow execution.
     *
     * Increments recovery attempts counter and records the error.
     *
     * @param executionKey the execution key
     * @param error the error that occurred
     * @param expectedVersion the expected current version for optimistic locking
     * @return Mono containing true if update succeeded
     */
    Mono<Boolean> recordRecoveryAttempt(String executionKey, String error, long expectedVersion);

    /**
     * Marks a workflow execution as orphaned.
     *
     * Sets:
     * - ownershipStatus = ORPHANED
     * - status = NEEDS_RECOVERY
     *
     * @param executionKey the execution key
     * @param reason the reason for orphaning
     * @param expectedVersion the expected current version for optimistic locking
     * @return Mono containing true if update succeeded
     */
    Mono<Boolean> markOrphaned(String executionKey, String reason, long expectedVersion);

    /**
     * Marks a workflow execution as pending takeover.
     *
     * @param executionKey the execution key
     * @param targetNodeId the node attempting takeover
     * @param expectedVersion the expected current version for optimistic locking
     * @return Mono containing true if update succeeded
     */
    Mono<Boolean> markPendingTakeover(String executionKey, String targetNodeId, long expectedVersion);

    /**
     * Revokes ownership of a workflow execution.
     *
     * Used for forced revocation during rebalancing.
     *
     * @param executionKey the execution key
     * @param reason the reason for revocation
     * @param expectedVersion the expected current version for optimistic locking
     * @return Mono containing true if revocation succeeded
     */
    Mono<Boolean> revokeOwnership(String executionKey, String reason, long expectedVersion);

    // ========== Ownership Count Methods ==========

    /**
     * Counts workflow executions by ownership status.
     *
     * @param status the ownership status
     * @return Mono containing the count
     */
    Mono<Long> countByOwnershipStatus(WorkflowExecutionState.OwnershipStatus status);

    /**
     * Counts orphaned workflow executions.
     *
     * @return Mono containing the count of orphaned executions
     */
    Mono<Long> countOrphaned();

    /**
     * Counts workflow executions owned by a specific node.
     *
     * Only counts executions with valid (non-expired) leases.
     *
     * @param nodeId the owner node ID
     * @return Mono containing the count
     */
    Mono<Long> countActivelyOwnedBy(String nodeId);

    /**
     * Counts workflow executions available for takeover.
     *
     * @return Mono containing the count
     */
    Mono<Long> countAvailableForTakeover();

    // ========== Batch Ownership Operations ==========

    /**
     * Marks multiple workflow executions as orphaned.
     *
     * Used when a node is detected as failed.
     *
     * @param failedNodeId the failed node's ID
     * @param reason the reason for orphaning
     * @return Mono containing the number of executions marked as orphaned
     */
    Mono<Integer> markAllOrphaned(String failedNodeId, String reason);

    /**
     * Expires all leases for a specific node.
     *
     * Used during graceful shutdown or forced node removal.
     *
     * @param nodeId the node ID
     * @return Mono containing the number of leases expired
     */
    Mono<Integer> expireAllLeases(String nodeId);

    /**
     * Cleans up completed or failed executions with ownership data older than specified time.
     *
     * @param before the cutoff time
     * @return Mono containing the number of cleaned up executions
     */
    Mono<Integer> cleanupCompletedWithOwnership(Instant before);
}
