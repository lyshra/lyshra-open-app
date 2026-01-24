package com.lyshra.open.app.distributed.cluster;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable representation of a cluster node's information.
 *
 * Contains metadata about a node including its identifier, host information,
 * health status, and any custom attributes.
 */
@Getter
@Builder
@ToString
public final class ClusterNodeInfo {

    /**
     * Possible states a cluster node can be in.
     */
    public enum NodeState {
        /**
         * Node is starting up and not yet ready.
         */
        STARTING,

        /**
         * Node is active and ready to accept work.
         */
        ACTIVE,

        /**
         * Node is draining work and preparing to leave.
         */
        DRAINING,

        /**
         * Node is shutting down.
         */
        SHUTTING_DOWN,

        /**
         * Node has been marked as failed.
         */
        FAILED,

        /**
         * Node state is unknown.
         */
        UNKNOWN
    }

    private final String nodeId;
    private final String hostname;
    private final int port;
    private final NodeState state;
    private final Instant startTime;
    private final Instant lastHeartbeat;
    private final long heartbeatEpoch;
    private final Map<String, String> attributes;
    private final int assignedPartitionCount;

    private ClusterNodeInfo(String nodeId,
                            String hostname,
                            int port,
                            NodeState state,
                            Instant startTime,
                            Instant lastHeartbeat,
                            long heartbeatEpoch,
                            Map<String, String> attributes,
                            int assignedPartitionCount) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId must not be null");
        this.hostname = Objects.requireNonNull(hostname, "hostname must not be null");
        this.port = port;
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.startTime = Objects.requireNonNull(startTime, "startTime must not be null");
        this.lastHeartbeat = Objects.requireNonNull(lastHeartbeat, "lastHeartbeat must not be null");
        this.heartbeatEpoch = heartbeatEpoch;
        this.attributes = attributes != null ? Collections.unmodifiableMap(attributes) : Collections.emptyMap();
        this.assignedPartitionCount = assignedPartitionCount;
    }

    /**
     * Checks if this node is available to accept new work.
     */
    public boolean isAvailable() {
        return state == NodeState.ACTIVE;
    }

    /**
     * Checks if this node is leaving or has left the cluster.
     */
    public boolean isLeaving() {
        return state == NodeState.DRAINING || state == NodeState.SHUTTING_DOWN;
    }

    /**
     * Returns the time since the last heartbeat.
     */
    public java.time.Duration timeSinceLastHeartbeat() {
        return java.time.Duration.between(lastHeartbeat, Instant.now());
    }

    /**
     * Returns the node's address in host:port format.
     */
    public String getAddress() {
        return hostname + ":" + port;
    }

    /**
     * Gets an optional attribute by key.
     */
    public Optional<String> getAttribute(String key) {
        return Optional.ofNullable(attributes.get(key));
    }

    /**
     * Creates a new ClusterNodeInfo with updated state.
     */
    public ClusterNodeInfo withState(NodeState newState) {
        return new ClusterNodeInfo(
                nodeId, hostname, port, newState, startTime, lastHeartbeat, heartbeatEpoch, attributes, assignedPartitionCount);
    }

    /**
     * Creates a new ClusterNodeInfo with updated heartbeat.
     */
    public ClusterNodeInfo withHeartbeat(Instant newHeartbeat, long newEpoch) {
        return new ClusterNodeInfo(
                nodeId, hostname, port, state, startTime, newHeartbeat, newEpoch, attributes, assignedPartitionCount);
    }

    /**
     * Creates a new ClusterNodeInfo with updated partition count.
     */
    public ClusterNodeInfo withPartitionCount(int count) {
        return new ClusterNodeInfo(
                nodeId, hostname, port, state, startTime, lastHeartbeat, heartbeatEpoch, attributes, count);
    }
}
