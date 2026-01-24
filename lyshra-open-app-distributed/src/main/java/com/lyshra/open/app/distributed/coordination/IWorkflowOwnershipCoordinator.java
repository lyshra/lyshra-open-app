package com.lyshra.open.app.distributed.coordination;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Set;

/**
 * Abstraction interface for distributed workflow ownership coordination.
 *
 * This interface encapsulates all distributed locking and ownership coordination
 * logic, allowing the core workflow engine to remain independent of the underlying
 * infrastructure (e.g., ZooKeeper, Redis, database, in-memory).
 *
 * Key Responsibilities:
 * 1. Ownership Acquisition - Claim exclusive ownership of workflow executions
 * 2. Lease Management - Renew ownership periodically to maintain control
 * 3. Graceful Release - Release ownership when workflow completes or node shuts down
 * 4. Orphan Recovery - Reclaim workflows from failed nodes
 * 5. Coordination Events - Notify listeners of ownership changes
 *
 * Implementation Requirements:
 * - All operations must be atomic and thread-safe
 * - Implementations must handle network partitions gracefully
 * - Fencing tokens must be used to prevent split-brain scenarios
 * - Operations should be idempotent where possible
 *
 * Usage Pattern:
 * <pre>
 * coordinator.acquireOwnership(workflowKey, options)
 *     .flatMap(result -> {
 *         if (result.isAcquired()) {
 *             return executeWorkflow(workflowKey)
 *                 .doFinally(signal -> coordinator.releaseOwnership(workflowKey));
 *         }
 *         return Mono.empty();
 *     });
 * </pre>
 *
 * @see CoordinationResult
 * @see OwnershipContext
 * @see ICoordinationEventListener
 */
public interface IWorkflowOwnershipCoordinator {

    // ========== Lifecycle Methods ==========

    /**
     * Initializes the coordinator and connects to the coordination backend.
     *
     * This method should:
     * - Establish connections to the backend (e.g., ZooKeeper, Redis)
     * - Register this node with the cluster
     * - Start background health check tasks
     * - Load any existing ownership state for this node
     *
     * @return Mono that completes when initialization is done
     */
    Mono<Void> initialize();

    /**
     * Shuts down the coordinator gracefully.
     *
     * This method should:
     * - Release all locally owned workflows
     * - Stop background tasks
     * - Close connections to the backend
     * - Notify the cluster of this node's departure
     *
     * @return Mono that completes when shutdown is done
     */
    Mono<Void> shutdown();

    /**
     * Checks if the coordinator is currently active and healthy.
     *
     * @return true if the coordinator is operational
     */
    boolean isActive();

    // ========== Ownership Acquisition ==========

    /**
     * Attempts to acquire exclusive ownership of a workflow execution.
     *
     * This is the primary method for claiming ownership. It performs:
     * 1. Partition validation - Ensures this node should handle the workflow
     * 2. Lock acquisition - Obtains a distributed lock for the workflow
     * 3. Ownership claim - Creates or updates the ownership record
     * 4. Fencing token generation - Assigns a token for split-brain prevention
     *
     * @param workflowExecutionKey the unique key identifying the workflow execution
     * @return Mono containing the coordination result
     */
    Mono<CoordinationResult> acquireOwnership(String workflowExecutionKey);

    /**
     * Attempts to acquire ownership with custom options.
     *
     * @param workflowExecutionKey the workflow execution key
     * @param options acquisition options (timeout, lease duration, etc.)
     * @return Mono containing the coordination result
     */
    Mono<CoordinationResult> acquireOwnership(String workflowExecutionKey, AcquisitionOptions options);

    /**
     * Attempts to acquire ownership of multiple workflows atomically.
     *
     * This is useful for batch processing scenarios where workflows
     * should be processed together or not at all.
     *
     * @param workflowExecutionKeys the set of workflow execution keys
     * @param options acquisition options
     * @return Mono containing results for each workflow
     */
    Mono<BatchCoordinationResult> acquireOwnershipBatch(Set<String> workflowExecutionKeys,
                                                         AcquisitionOptions options);

    // ========== Lease Renewal ==========

    /**
     * Renews the ownership lease for a workflow execution.
     *
     * This method should be called periodically (before lease expiration)
     * to maintain ownership. If renewal fails, ownership may be lost.
     *
     * @param workflowExecutionKey the workflow execution key
     * @return Mono containing true if renewal succeeded, false otherwise
     */
    Mono<Boolean> renewOwnership(String workflowExecutionKey);

    /**
     * Renews ownership with a specific extension duration.
     *
     * @param workflowExecutionKey the workflow execution key
     * @param extensionDuration the duration to extend the lease
     * @return Mono containing true if renewal succeeded
     */
    Mono<Boolean> renewOwnership(String workflowExecutionKey, Duration extensionDuration);

    /**
     * Renews all locally owned workflows.
     *
     * This is typically called by a background task to keep all
     * active leases alive.
     *
     * @return Mono containing the number of successfully renewed leases
     */
    Mono<Integer> renewAllOwnedWorkflows();

    // ========== Ownership Release ==========

    /**
     * Releases ownership of a workflow execution.
     *
     * This should be called when:
     * - Workflow execution completes successfully
     * - Workflow execution fails permanently
     * - Node is shutting down gracefully
     *
     * @param workflowExecutionKey the workflow execution key
     * @return Mono that completes when ownership is released
     */
    Mono<Void> releaseOwnership(String workflowExecutionKey);

    /**
     * Releases ownership with a reason for auditing.
     *
     * @param workflowExecutionKey the workflow execution key
     * @param reason the reason for release (e.g., "completed", "failed", "shutdown")
     * @return Mono that completes when ownership is released
     */
    Mono<Void> releaseOwnership(String workflowExecutionKey, String reason);

    /**
     * Releases all workflows owned by this node.
     *
     * This is typically called during graceful shutdown.
     *
     * @param reason the reason for release
     * @return Mono containing the number of released workflows
     */
    Mono<Integer> releaseAllOwnedWorkflows(String reason);

    // ========== Orphan Recovery ==========

    /**
     * Claims orphaned workflows from a failed node.
     *
     * This method identifies workflows that were owned by a node that
     * has failed and attempts to claim them for this node. It should:
     * 1. Verify the original owner is actually dead
     * 2. Validate partition assignment for each workflow
     * 3. Atomically transfer ownership with a new fencing token
     *
     * @param failedNodeId the ID of the failed node
     * @return Flux of claimed workflow keys
     */
    Flux<String> claimOrphanedWorkflows(String failedNodeId);

    /**
     * Claims orphaned workflows with options.
     *
     * @param failedNodeId the ID of the failed node
     * @param options recovery options (max claims, timeout, etc.)
     * @return Mono containing the recovery result
     */
    Mono<OrphanRecoveryResult> claimOrphanedWorkflows(String failedNodeId, RecoveryOptions options);

    /**
     * Scans for and claims all orphaned workflows across the cluster.
     *
     * This performs a full scan of ownership records to find workflows
     * whose owners are no longer alive. Use with caution in large clusters.
     *
     * @return Mono containing the recovery result
     */
    Mono<OrphanRecoveryResult> claimAllOrphanedWorkflows();

    /**
     * Checks if a specific workflow is orphaned (owner dead, lease expired).
     *
     * @param workflowExecutionKey the workflow execution key
     * @return Mono containing true if the workflow is orphaned
     */
    Mono<Boolean> isOrphaned(String workflowExecutionKey);

    // ========== Ownership Query ==========

    /**
     * Gets the current ownership context for a workflow.
     *
     * @param workflowExecutionKey the workflow execution key
     * @return Mono containing the ownership context, or empty if not owned
     */
    Mono<OwnershipContext> getOwnershipContext(String workflowExecutionKey);

    /**
     * Checks if this node currently owns a workflow.
     *
     * @param workflowExecutionKey the workflow execution key
     * @return true if owned by this node
     */
    boolean isOwnedLocally(String workflowExecutionKey);

    /**
     * Gets all workflows currently owned by this node.
     *
     * @return set of workflow execution keys
     */
    Set<String> getLocallyOwnedWorkflows();

    /**
     * Gets the count of workflows owned by this node.
     *
     * @return the ownership count
     */
    int getLocalOwnershipCount();

    /**
     * Gets the owner of a specific workflow.
     *
     * @param workflowExecutionKey the workflow execution key
     * @return Mono containing the owner node ID, or empty if not owned
     */
    Mono<String> getOwner(String workflowExecutionKey);

    // ========== Fencing Token Operations ==========

    /**
     * Gets the current fencing token for a workflow.
     *
     * Fencing tokens are used to prevent split-brain scenarios.
     * Operations with a lower fencing token should be rejected.
     *
     * @param workflowExecutionKey the workflow execution key
     * @return Mono containing the fencing token, or -1 if not found
     */
    Mono<Long> getFencingToken(String workflowExecutionKey);

    /**
     * Validates that an operation's fencing token is current.
     *
     * @param workflowExecutionKey the workflow execution key
     * @param fencingToken the token to validate
     * @return Mono containing true if the token is valid (current or higher)
     */
    Mono<Boolean> validateFencingToken(String workflowExecutionKey, long fencingToken);

    // ========== Event Listeners ==========

    /**
     * Registers a listener for coordination events.
     *
     * @param listener the listener to register
     */
    void addCoordinationEventListener(ICoordinationEventListener listener);

    /**
     * Removes a coordination event listener.
     *
     * @param listener the listener to remove
     */
    void removeCoordinationEventListener(ICoordinationEventListener listener);

    // ========== Health & Metrics ==========

    /**
     * Gets the health status of the coordinator.
     *
     * @return Mono containing the health status
     */
    Mono<CoordinatorHealth> getHealth();

    /**
     * Gets coordination metrics for monitoring.
     *
     * @return the current metrics
     */
    CoordinationMetrics getMetrics();

    /**
     * Returns the node ID of this coordinator instance.
     *
     * @return the local node ID
     */
    String getNodeId();
}
