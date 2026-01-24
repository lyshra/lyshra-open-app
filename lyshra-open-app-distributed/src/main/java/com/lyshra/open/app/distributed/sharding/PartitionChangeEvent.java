package com.lyshra.open.app.distributed.sharding;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable event representing a change in partition assignments.
 *
 * Contains information about which partitions were gained or lost by the local node,
 * and the complete new assignment state.
 */
@Getter
@Builder
@ToString
public final class PartitionChangeEvent {

    /**
     * Types of partition changes.
     */
    public enum EventType {
        /**
         * Initial assignment when node joins.
         */
        INITIAL_ASSIGNMENT,

        /**
         * Rebalancing due to topology change.
         */
        REBALANCE,

        /**
         * Partitions acquired from a failed node.
         */
        FAILOVER,

        /**
         * Manual partition reassignment.
         */
        MANUAL_REASSIGNMENT,

        /**
         * Partitions released during graceful shutdown.
         */
        GRACEFUL_RELEASE
    }

    private final EventType eventType;
    private final Set<Integer> gainedPartitions;
    private final Set<Integer> lostPartitions;
    private final Set<Integer> currentLocalPartitions;
    private final Map<Integer, String> fullAssignment;
    private final long assignmentEpoch;
    private final Instant timestamp;
    private final String localNodeId;
    private final String triggerNodeId;

    private PartitionChangeEvent(EventType eventType,
                                  Set<Integer> gainedPartitions,
                                  Set<Integer> lostPartitions,
                                  Set<Integer> currentLocalPartitions,
                                  Map<Integer, String> fullAssignment,
                                  long assignmentEpoch,
                                  Instant timestamp,
                                  String localNodeId,
                                  String triggerNodeId) {
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.gainedPartitions = gainedPartitions != null
                ? Collections.unmodifiableSet(gainedPartitions)
                : Collections.emptySet();
        this.lostPartitions = lostPartitions != null
                ? Collections.unmodifiableSet(lostPartitions)
                : Collections.emptySet();
        this.currentLocalPartitions = currentLocalPartitions != null
                ? Collections.unmodifiableSet(currentLocalPartitions)
                : Collections.emptySet();
        this.fullAssignment = fullAssignment != null
                ? Collections.unmodifiableMap(fullAssignment)
                : Collections.emptyMap();
        this.assignmentEpoch = assignmentEpoch;
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.localNodeId = Objects.requireNonNull(localNodeId, "localNodeId must not be null");
        this.triggerNodeId = triggerNodeId;
    }

    /**
     * Creates an initial assignment event.
     */
    public static PartitionChangeEvent initialAssignment(Set<Integer> partitions,
                                                          Map<Integer, String> fullAssignment,
                                                          long epoch,
                                                          String localNodeId) {
        return new PartitionChangeEvent(
                EventType.INITIAL_ASSIGNMENT,
                partitions,
                Collections.emptySet(),
                partitions,
                fullAssignment,
                epoch,
                Instant.now(),
                localNodeId,
                localNodeId
        );
    }

    /**
     * Creates a rebalance event.
     */
    public static PartitionChangeEvent rebalance(Set<Integer> gained,
                                                  Set<Integer> lost,
                                                  Set<Integer> current,
                                                  Map<Integer, String> fullAssignment,
                                                  long epoch,
                                                  String localNodeId,
                                                  String triggerNodeId) {
        return new PartitionChangeEvent(
                EventType.REBALANCE,
                gained,
                lost,
                current,
                fullAssignment,
                epoch,
                Instant.now(),
                localNodeId,
                triggerNodeId
        );
    }

    /**
     * Creates a failover event.
     */
    public static PartitionChangeEvent failover(Set<Integer> gained,
                                                 Set<Integer> current,
                                                 Map<Integer, String> fullAssignment,
                                                 long epoch,
                                                 String localNodeId,
                                                 String failedNodeId) {
        return new PartitionChangeEvent(
                EventType.FAILOVER,
                gained,
                Collections.emptySet(),
                current,
                fullAssignment,
                epoch,
                Instant.now(),
                localNodeId,
                failedNodeId
        );
    }

    /**
     * Checks if any partitions changed.
     */
    public boolean hasChanges() {
        return !gainedPartitions.isEmpty() || !lostPartitions.isEmpty();
    }

    /**
     * Checks if this node gained partitions.
     */
    public boolean hasGainedPartitions() {
        return !gainedPartitions.isEmpty();
    }

    /**
     * Checks if this node lost partitions.
     */
    public boolean hasLostPartitions() {
        return !lostPartitions.isEmpty();
    }

    /**
     * Returns the number of partitions currently owned by this node.
     */
    public int getLocalPartitionCount() {
        return currentLocalPartitions.size();
    }
}
