package com.lyshra.open.app.distributed.cluster;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable event representing a change in cluster leadership.
 *
 * Contains information about the old and new leader, the type of change,
 * and whether this node is now the leader.
 */
@Getter
@Builder
@ToString
public final class ClusterLeadershipEvent {

    /**
     * Types of leadership changes that can occur.
     */
    public enum EventType {
        /**
         * This node has become the leader.
         */
        BECAME_LEADER,

        /**
         * This node has lost leadership to another node.
         */
        LOST_LEADERSHIP,

        /**
         * The leader changed but this node was not involved.
         */
        LEADER_CHANGED,

        /**
         * There is currently no leader in the cluster.
         */
        NO_LEADER,

        /**
         * A leadership election is in progress.
         */
        ELECTION_IN_PROGRESS
    }

    private final EventType eventType;
    private final String previousLeaderId;
    private final String newLeaderId;
    private final String localNodeId;
    private final Instant timestamp;
    private final long leadershipEpoch;

    private ClusterLeadershipEvent(EventType eventType,
                                    String previousLeaderId,
                                    String newLeaderId,
                                    String localNodeId,
                                    Instant timestamp,
                                    long leadershipEpoch) {
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.previousLeaderId = previousLeaderId;
        this.newLeaderId = newLeaderId;
        this.localNodeId = Objects.requireNonNull(localNodeId, "localNodeId must not be null");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.leadershipEpoch = leadershipEpoch;
    }

    /**
     * Creates an event indicating this node has become the leader.
     */
    public static ClusterLeadershipEvent becameLeader(String previousLeaderId, String localNodeId, long epoch) {
        return new ClusterLeadershipEvent(
                EventType.BECAME_LEADER, previousLeaderId, localNodeId, localNodeId, Instant.now(), epoch);
    }

    /**
     * Creates an event indicating this node has lost leadership.
     */
    public static ClusterLeadershipEvent lostLeadership(String newLeaderId, String localNodeId, long epoch) {
        return new ClusterLeadershipEvent(
                EventType.LOST_LEADERSHIP, localNodeId, newLeaderId, localNodeId, Instant.now(), epoch);
    }

    /**
     * Creates an event indicating the leader changed (this node was not involved).
     */
    public static ClusterLeadershipEvent leaderChanged(String previousLeaderId, String newLeaderId,
                                                        String localNodeId, long epoch) {
        return new ClusterLeadershipEvent(
                EventType.LEADER_CHANGED, previousLeaderId, newLeaderId, localNodeId, Instant.now(), epoch);
    }

    /**
     * Creates an event indicating there is no leader.
     */
    public static ClusterLeadershipEvent noLeader(String previousLeaderId, String localNodeId, long epoch) {
        return new ClusterLeadershipEvent(
                EventType.NO_LEADER, previousLeaderId, null, localNodeId, Instant.now(), epoch);
    }

    /**
     * Checks if the local node is now the leader.
     */
    public boolean isLocalNodeLeader() {
        return localNodeId.equals(newLeaderId);
    }

    /**
     * Checks if the local node was previously the leader.
     */
    public boolean wasLocalNodeLeader() {
        return localNodeId.equals(previousLeaderId);
    }

    /**
     * Returns the new leader ID as an Optional.
     */
    public Optional<String> getNewLeaderIdOptional() {
        return Optional.ofNullable(newLeaderId);
    }

    /**
     * Returns the previous leader ID as an Optional.
     */
    public Optional<String> getPreviousLeaderIdOptional() {
        return Optional.ofNullable(previousLeaderId);
    }
}
