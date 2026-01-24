package com.lyshra.open.app.distributed.membership;

import com.lyshra.open.app.distributed.cluster.ClusterNodeInfo;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable event representing a change in node health status.
 *
 * Contains information about which node's health changed and the
 * details of the health transition.
 */
@Getter
@Builder
@ToString
public final class NodeHealthEvent {

    /**
     * Types of health changes.
     */
    public enum EventType {
        /**
         * Node became healthy.
         */
        BECAME_HEALTHY,

        /**
         * Node became unhealthy (suspected failure).
         */
        BECAME_UNHEALTHY,

        /**
         * Node confirmed as failed.
         */
        CONFIRMED_FAILED,

        /**
         * Node recovered from unhealthy/failed state.
         */
        RECOVERED,

        /**
         * Node started graceful shutdown.
         */
        DRAINING_STARTED,

        /**
         * Node completed graceful shutdown.
         */
        SHUTDOWN_COMPLETE,

        /**
         * Heartbeat received from node.
         */
        HEARTBEAT_RECEIVED,

        /**
         * Heartbeat timeout for node.
         */
        HEARTBEAT_TIMEOUT
    }

    private final EventType eventType;
    private final String nodeId;
    private final ClusterNodeInfo.NodeState previousState;
    private final ClusterNodeInfo.NodeState currentState;
    private final Instant timestamp;
    private final Duration timeSinceLastHeartbeat;
    private final int missedHeartbeats;
    private final String sourceNodeId;

    private NodeHealthEvent(EventType eventType,
                            String nodeId,
                            ClusterNodeInfo.NodeState previousState,
                            ClusterNodeInfo.NodeState currentState,
                            Instant timestamp,
                            Duration timeSinceLastHeartbeat,
                            int missedHeartbeats,
                            String sourceNodeId) {
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId must not be null");
        this.previousState = previousState;
        this.currentState = currentState;
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.timeSinceLastHeartbeat = timeSinceLastHeartbeat;
        this.missedHeartbeats = missedHeartbeats;
        this.sourceNodeId = Objects.requireNonNull(sourceNodeId, "sourceNodeId must not be null");
    }

    /**
     * Creates an event for when a node becomes healthy.
     */
    public static NodeHealthEvent becameHealthy(String nodeId, ClusterNodeInfo.NodeState previousState,
                                                 String sourceNodeId) {
        return new NodeHealthEvent(
                EventType.BECAME_HEALTHY,
                nodeId,
                previousState,
                ClusterNodeInfo.NodeState.ACTIVE,
                Instant.now(),
                Duration.ZERO,
                0,
                sourceNodeId
        );
    }

    /**
     * Creates an event for when a node becomes unhealthy.
     */
    public static NodeHealthEvent becameUnhealthy(String nodeId, Duration timeSinceHeartbeat,
                                                   int missedBeats, String sourceNodeId) {
        return new NodeHealthEvent(
                EventType.BECAME_UNHEALTHY,
                nodeId,
                ClusterNodeInfo.NodeState.ACTIVE,
                ClusterNodeInfo.NodeState.UNKNOWN,
                Instant.now(),
                timeSinceHeartbeat,
                missedBeats,
                sourceNodeId
        );
    }

    /**
     * Creates an event for when a node is confirmed as failed.
     */
    public static NodeHealthEvent confirmedFailed(String nodeId, Duration timeSinceHeartbeat,
                                                   int missedBeats, String sourceNodeId) {
        return new NodeHealthEvent(
                EventType.CONFIRMED_FAILED,
                nodeId,
                ClusterNodeInfo.NodeState.UNKNOWN,
                ClusterNodeInfo.NodeState.FAILED,
                Instant.now(),
                timeSinceHeartbeat,
                missedBeats,
                sourceNodeId
        );
    }

    /**
     * Creates an event for heartbeat timeout.
     */
    public static NodeHealthEvent heartbeatTimeout(String nodeId, Duration timeSinceHeartbeat,
                                                    int missedBeats, String sourceNodeId) {
        return new NodeHealthEvent(
                EventType.HEARTBEAT_TIMEOUT,
                nodeId,
                null,
                null,
                Instant.now(),
                timeSinceHeartbeat,
                missedBeats,
                sourceNodeId
        );
    }

    /**
     * Creates an event for heartbeat received.
     */
    public static NodeHealthEvent heartbeatReceived(String nodeId, String sourceNodeId) {
        return new NodeHealthEvent(
                EventType.HEARTBEAT_RECEIVED,
                nodeId,
                null,
                ClusterNodeInfo.NodeState.ACTIVE,
                Instant.now(),
                Duration.ZERO,
                0,
                sourceNodeId
        );
    }

    /**
     * Checks if this is a failure-related event.
     */
    public boolean isFailureEvent() {
        return eventType == EventType.BECAME_UNHEALTHY ||
               eventType == EventType.CONFIRMED_FAILED ||
               eventType == EventType.HEARTBEAT_TIMEOUT;
    }

    /**
     * Checks if this is a recovery-related event.
     */
    public boolean isRecoveryEvent() {
        return eventType == EventType.BECAME_HEALTHY ||
               eventType == EventType.RECOVERED ||
               eventType == EventType.HEARTBEAT_RECEIVED;
    }

    /**
     * Returns the previous state as an Optional.
     */
    public Optional<ClusterNodeInfo.NodeState> getPreviousStateOptional() {
        return Optional.ofNullable(previousState);
    }

    /**
     * Returns the current state as an Optional.
     */
    public Optional<ClusterNodeInfo.NodeState> getCurrentStateOptional() {
        return Optional.ofNullable(currentState);
    }
}
