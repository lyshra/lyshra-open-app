package com.lyshra.open.app.distributed.cluster;

import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.Set;

/**
 * Core interface for cluster coordination operations.
 *
 * This abstraction allows plugging in different coordination backends
 * such as Hazelcast, Redis, Zookeeper, or database-based coordination.
 *
 * Design Pattern: Strategy Pattern - allows swapping coordination implementations
 * without changing client code.
 */
public interface IClusterCoordinator {

    /**
     * Initializes the cluster coordinator and joins the cluster.
     *
     * @return Mono that completes when initialization is done
     */
    Mono<Void> initialize();

    /**
     * Gracefully shuts down the coordinator and leaves the cluster.
     *
     * @return Mono that completes when shutdown is done
     */
    Mono<Void> shutdown();

    /**
     * Returns the unique identifier for this node in the cluster.
     *
     * @return the node identifier
     */
    String getNodeId();

    /**
     * Returns the set of all active node IDs in the cluster.
     *
     * @return set of active node identifiers
     */
    Set<String> getActiveNodes();

    /**
     * Checks if this node is currently the cluster leader.
     *
     * @return true if this node is the leader
     */
    boolean isLeader();

    /**
     * Returns the current leader node ID.
     *
     * @return Optional containing the leader node ID, empty if no leader
     */
    Optional<String> getLeaderId();

    /**
     * Registers a listener for cluster membership changes.
     *
     * @param listener the membership change listener
     */
    void addMembershipListener(IClusterMembershipListener listener);

    /**
     * Removes a previously registered membership listener.
     *
     * @param listener the listener to remove
     */
    void removeMembershipListener(IClusterMembershipListener listener);

    /**
     * Registers a listener for leadership changes.
     *
     * @param listener the leadership change listener
     */
    void addLeadershipListener(IClusterLeadershipListener listener);

    /**
     * Removes a previously registered leadership listener.
     *
     * @param listener the listener to remove
     */
    void removeLeadershipListener(IClusterLeadershipListener listener);

    /**
     * Checks if the coordinator is currently connected to the cluster.
     *
     * @return true if connected
     */
    boolean isConnected();

    /**
     * Returns the total number of active nodes in the cluster.
     *
     * @return node count
     */
    int getClusterSize();
}
