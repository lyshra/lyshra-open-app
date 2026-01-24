package com.lyshra.open.app.distributed.membership;

import com.lyshra.open.app.distributed.cluster.ClusterNodeInfo;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Interface for node membership and failure detection.
 *
 * Manages the cluster membership lifecycle including:
 * - Node registration and heartbeat
 * - Failure detection using heartbeat timeouts
 * - Health status monitoring
 * - Graceful shutdown coordination
 *
 * The failure detection uses a heartbeat-based approach where nodes
 * periodically send heartbeats. A node is considered failed if it
 * misses a configurable number of consecutive heartbeats.
 *
 * Design Pattern: Heartbeat Pattern for distributed failure detection.
 */
public interface INodeMembershipService {

    /**
     * Initializes the membership service and registers this node.
     *
     * @return Mono that completes when initialization is done
     */
    Mono<Void> initialize();

    /**
     * Shuts down the membership service and deregisters this node.
     *
     * @return Mono that completes when shutdown is done
     */
    Mono<Void> shutdown();

    /**
     * Returns information about the local node.
     *
     * @return the local node information
     */
    ClusterNodeInfo getLocalNodeInfo();

    /**
     * Returns information about a specific node.
     *
     * @param nodeId the node identifier
     * @return Optional containing the node info if found
     */
    Optional<ClusterNodeInfo> getNodeInfo(String nodeId);

    /**
     * Returns information about all registered nodes.
     *
     * @return list of all node information
     */
    List<ClusterNodeInfo> getAllNodes();

    /**
     * Returns information about all active (healthy) nodes.
     *
     * @return list of active node information
     */
    List<ClusterNodeInfo> getActiveNodes();

    /**
     * Checks if a specific node is currently active.
     *
     * @param nodeId the node identifier
     * @return true if the node is active
     */
    boolean isNodeActive(String nodeId);

    /**
     * Returns the number of active nodes in the cluster.
     *
     * @return active node count
     */
    int getActiveNodeCount();

    /**
     * Manually triggers a heartbeat for this node.
     *
     * @return Mono that completes when heartbeat is sent
     */
    Mono<Void> sendHeartbeat();

    /**
     * Marks this node as draining (preparing to leave the cluster).
     *
     * @return Mono that completes when status is updated
     */
    Mono<Void> startDraining();

    /**
     * Returns the heartbeat interval for this service.
     *
     * @return the heartbeat interval
     */
    Duration getHeartbeatInterval();

    /**
     * Returns the failure detection timeout.
     *
     * @return the timeout after which a node is considered failed
     */
    Duration getFailureDetectionTimeout();

    /**
     * Registers a listener for node health events.
     *
     * @param listener the health event listener
     */
    void addHealthListener(INodeHealthListener listener);

    /**
     * Removes a previously registered health listener.
     *
     * @param listener the listener to remove
     */
    void removeHealthListener(INodeHealthListener listener);
}
