package com.lyshra.open.app.distributed.cluster;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable event representing a change in cluster membership.
 *
 * Contains information about which node joined or left, the type of change,
 * and the current cluster state after the change.
 */
@Getter
@Builder
@ToString
public final class ClusterMembershipEvent {

    /**
     * Types of membership changes that can occur in the cluster.
     */
    public enum EventType {
        /**
         * A new node has joined the cluster.
         */
        NODE_JOINED,

        /**
         * A node has gracefully left the cluster.
         */
        NODE_LEFT,

        /**
         * A node was detected as failed (ungraceful departure).
         */
        NODE_FAILED,

        /**
         * A previously failed node has rejoined.
         */
        NODE_REJOINED,

        /**
         * The local node has successfully joined the cluster.
         */
        SELF_JOINED,

        /**
         * The local node is being evicted from the cluster.
         */
        SELF_EVICTED
    }

    private final EventType eventType;
    private final String affectedNodeId;
    private final Set<String> currentActiveNodes;
    private final Instant timestamp;
    private final String sourceNodeId;

    private ClusterMembershipEvent(EventType eventType,
                                    String affectedNodeId,
                                    Set<String> currentActiveNodes,
                                    Instant timestamp,
                                    String sourceNodeId) {
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.affectedNodeId = Objects.requireNonNull(affectedNodeId, "affectedNodeId must not be null");
        this.currentActiveNodes = Collections.unmodifiableSet(
                Objects.requireNonNull(currentActiveNodes, "currentActiveNodes must not be null"));
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.sourceNodeId = Objects.requireNonNull(sourceNodeId, "sourceNodeId must not be null");
    }

    /**
     * Creates a new event for when a node joins the cluster.
     */
    public static ClusterMembershipEvent nodeJoined(String nodeId, Set<String> activeNodes, String sourceNodeId) {
        return new ClusterMembershipEvent(EventType.NODE_JOINED, nodeId, activeNodes, Instant.now(), sourceNodeId);
    }

    /**
     * Creates a new event for when a node gracefully leaves the cluster.
     */
    public static ClusterMembershipEvent nodeLeft(String nodeId, Set<String> activeNodes, String sourceNodeId) {
        return new ClusterMembershipEvent(EventType.NODE_LEFT, nodeId, activeNodes, Instant.now(), sourceNodeId);
    }

    /**
     * Creates a new event for when a node fails (ungraceful departure).
     */
    public static ClusterMembershipEvent nodeFailed(String nodeId, Set<String> activeNodes, String sourceNodeId) {
        return new ClusterMembershipEvent(EventType.NODE_FAILED, nodeId, activeNodes, Instant.now(), sourceNodeId);
    }

    /**
     * Creates a new event for when this node joins the cluster.
     */
    public static ClusterMembershipEvent selfJoined(String nodeId, Set<String> activeNodes) {
        return new ClusterMembershipEvent(EventType.SELF_JOINED, nodeId, activeNodes, Instant.now(), nodeId);
    }

    /**
     * Checks if this event represents a node departure (left or failed).
     */
    public boolean isNodeDeparture() {
        return eventType == EventType.NODE_LEFT || eventType == EventType.NODE_FAILED;
    }

    /**
     * Checks if this event represents a node arrival (joined or rejoined).
     */
    public boolean isNodeArrival() {
        return eventType == EventType.NODE_JOINED || eventType == EventType.NODE_REJOINED || eventType == EventType.SELF_JOINED;
    }

    /**
     * Returns the number of nodes in the cluster after this event.
     */
    public int getClusterSizeAfterEvent() {
        return currentActiveNodes.size();
    }
}
