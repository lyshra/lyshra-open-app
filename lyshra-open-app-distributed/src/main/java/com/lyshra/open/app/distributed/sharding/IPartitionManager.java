package com.lyshra.open.app.distributed.sharding;

import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

/**
 * Interface for managing partition assignments across cluster nodes.
 *
 * The partition manager is responsible for:
 * - Assigning partitions to nodes
 * - Tracking which node owns which partitions
 * - Rebalancing partitions when topology changes
 * - Providing partition ownership queries
 *
 * Design Pattern: Facade Pattern - provides a unified interface for complex
 * partition management operations.
 */
public interface IPartitionManager {

    /**
     * Initializes the partition manager.
     *
     * @return Mono that completes when initialization is done
     */
    Mono<Void> initialize();

    /**
     * Shuts down the partition manager.
     *
     * @return Mono that completes when shutdown is done
     */
    Mono<Void> shutdown();

    /**
     * Returns the total number of partitions in the system.
     *
     * @return total partition count
     */
    int getTotalPartitions();

    /**
     * Returns the node ID that owns the specified partition.
     *
     * @param partitionId the partition to query
     * @return the owning node ID, or empty if unassigned
     */
    java.util.Optional<String> getPartitionOwner(int partitionId);

    /**
     * Returns the set of partitions owned by the local node.
     *
     * @return set of partition IDs
     */
    Set<Integer> getLocalPartitions();

    /**
     * Returns the set of partitions owned by a specific node.
     *
     * @param nodeId the node to query
     * @return set of partition IDs
     */
    Set<Integer> getNodePartitions(String nodeId);

    /**
     * Returns the complete partition-to-node assignment map.
     *
     * @return map of partition ID to node ID
     */
    Map<Integer, String> getPartitionAssignments();

    /**
     * Checks if the local node owns the specified partition.
     *
     * @param partitionId the partition to check
     * @return true if local node owns the partition
     */
    boolean isLocalPartition(int partitionId);

    /**
     * Checks if the local node should handle a workflow execution.
     *
     * @param workflowExecutionKey the workflow execution key
     * @return true if this node should handle the workflow
     */
    boolean shouldHandleWorkflow(String workflowExecutionKey);

    /**
     * Returns the node that should handle a specific workflow execution.
     *
     * @param workflowExecutionKey the workflow execution key
     * @return the target node ID, or empty if no node is available
     */
    java.util.Optional<String> getTargetNode(String workflowExecutionKey);

    /**
     * Triggers a partition rebalance operation.
     * Only the cluster leader should call this.
     *
     * @return Mono that completes when rebalancing is done
     */
    Mono<Void> rebalance();

    /**
     * Registers a listener for partition assignment changes.
     *
     * @param listener the listener to register
     */
    void addPartitionChangeListener(IPartitionChangeListener listener);

    /**
     * Removes a previously registered partition listener.
     *
     * @param listener the listener to remove
     */
    void removePartitionChangeListener(IPartitionChangeListener listener);

    /**
     * Returns the current partition assignment version/epoch.
     * Used for consistency checks.
     *
     * @return the assignment epoch
     */
    long getAssignmentEpoch();

    /**
     * Acquires ownership of specified partitions during rebalancing.
     *
     * @param partitions the partitions to acquire
     * @return Mono that completes when partitions are acquired
     */
    Mono<Void> acquirePartitions(Set<Integer> partitions);

    /**
     * Releases ownership of specified partitions during rebalancing.
     *
     * @param partitions the partitions to release
     * @return Mono that completes when partitions are released
     */
    Mono<Void> releasePartitions(Set<Integer> partitions);
}
