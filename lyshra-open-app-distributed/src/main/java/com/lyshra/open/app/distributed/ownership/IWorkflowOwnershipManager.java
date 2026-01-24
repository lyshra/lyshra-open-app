package com.lyshra.open.app.distributed.ownership;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Interface for managing workflow execution ownership.
 *
 * Workflow ownership ensures that exactly one node is responsible for executing
 * a workflow at any given time. This prevents duplicate execution, race conditions,
 * and split-brain scenarios.
 *
 * The ownership model uses time-based leases that must be periodically renewed.
 * If a node fails to renew its lease, the workflow becomes available for
 * reassignment to another node.
 *
 * Design Pattern: Lease Pattern - time-bounded exclusive access to resources.
 */
public interface IWorkflowOwnershipManager {

    /**
     * Initializes the ownership manager.
     *
     * @return Mono that completes when initialization is done
     */
    Mono<Void> initialize();

    /**
     * Shuts down the ownership manager, releasing all owned workflows.
     *
     * @return Mono that completes when shutdown is done
     */
    Mono<Void> shutdown();

    /**
     * Attempts to acquire ownership of a workflow execution.
     *
     * @param workflowExecutionKey the unique workflow execution key
     * @param leaseDuration the requested lease duration
     * @return Mono containing the ownership result
     */
    Mono<WorkflowOwnershipResult> acquireOwnership(String workflowExecutionKey, Duration leaseDuration);

    /**
     * Renews the lease for an owned workflow execution.
     *
     * @param workflowExecutionKey the workflow execution key
     * @param extensionDuration the additional lease duration
     * @return Mono containing true if renewal succeeded
     */
    Mono<Boolean> renewLease(String workflowExecutionKey, Duration extensionDuration);

    /**
     * Releases ownership of a workflow execution.
     *
     * @param workflowExecutionKey the workflow execution key
     * @return Mono that completes when ownership is released
     */
    Mono<Void> releaseOwnership(String workflowExecutionKey);

    /**
     * Checks if the local node owns a specific workflow execution.
     *
     * @param workflowExecutionKey the workflow execution key
     * @return true if owned by local node
     */
    boolean isOwnedLocally(String workflowExecutionKey);

    /**
     * Returns the current owner of a workflow execution.
     *
     * @param workflowExecutionKey the workflow execution key
     * @return Optional containing the owner node ID
     */
    Optional<String> getOwner(String workflowExecutionKey);

    /**
     * Returns all workflow executions owned by the local node.
     *
     * @return set of workflow execution keys
     */
    Set<String> getLocallyOwnedWorkflows();

    /**
     * Returns the number of workflows owned by the local node.
     *
     * @return ownership count
     */
    int getLocalOwnershipCount();

    /**
     * Forcibly revokes ownership from a failed node.
     * Should only be called by the cluster leader or during failover.
     *
     * @param nodeId the node whose ownership should be revoked
     * @return Mono containing the set of revoked workflow execution keys
     */
    Mono<Set<String>> revokeNodeOwnership(String nodeId);

    /**
     * Registers a listener for ownership change events.
     *
     * @param listener the listener to register
     */
    void addOwnershipChangeListener(IWorkflowOwnershipChangeListener listener);

    /**
     * Removes a previously registered ownership listener.
     *
     * @param listener the listener to remove
     */
    void removeOwnershipChangeListener(IWorkflowOwnershipChangeListener listener);
}
