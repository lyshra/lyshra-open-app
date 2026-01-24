package com.lyshra.open.app.distributed.sharding;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of a sharding operation, containing the target partition and node information.
 *
 * This class encapsulates:
 * - The partition ID that the workflow maps to
 * - The primary node responsible for the partition
 * - Replica nodes for fault tolerance
 * - Metadata about the sharding decision
 *
 * Thread Safety: This class is immutable and thread-safe.
 *
 * Usage:
 * <pre>
 * ShardingResult result = shardingStrategy.shard(key);
 * int partition = result.getPartitionId();
 * String primaryNode = result.getPrimaryNodeId();
 * </pre>
 */
@Getter
@Builder(toBuilder = true)
@ToString
public final class ShardingResult {

    /**
     * The partition ID that the sharding key maps to.
     */
    private final int partitionId;

    /**
     * The primary node ID responsible for this partition.
     */
    private final String primaryNodeId;

    /**
     * Replica node IDs for fault tolerance (ordered by priority).
     */
    private final List<String> replicaNodeIds;

    /**
     * The hash value computed for the sharding key.
     */
    private final long hashValue;

    /**
     * The sharding strategy that produced this result.
     */
    private final String strategyName;

    /**
     * Whether this is a sticky assignment (should not be rebalanced).
     */
    private final boolean sticky;

    /**
     * The sharding key that was used.
     */
    private final ShardingKey shardingKey;

    /**
     * Additional metadata about the sharding decision.
     */
    private final java.util.Map<String, String> metadata;

    private ShardingResult(int partitionId, String primaryNodeId, List<String> replicaNodeIds,
                          long hashValue, String strategyName, boolean sticky,
                          ShardingKey shardingKey, java.util.Map<String, String> metadata) {
        this.partitionId = partitionId;
        this.primaryNodeId = primaryNodeId;
        this.replicaNodeIds = replicaNodeIds != null ?
                Collections.unmodifiableList(replicaNodeIds) : Collections.emptyList();
        this.hashValue = hashValue;
        this.strategyName = strategyName;
        this.sticky = sticky;
        this.shardingKey = shardingKey;
        this.metadata = metadata != null ?
                Collections.unmodifiableMap(metadata) : Collections.emptyMap();
    }

    // ========== Factory Methods ==========

    /**
     * Creates a simple sharding result with just partition and node.
     *
     * @param partitionId the partition ID
     * @param primaryNodeId the primary node ID
     * @return a new ShardingResult
     */
    public static ShardingResult of(int partitionId, String primaryNodeId) {
        return ShardingResult.builder()
                .partitionId(partitionId)
                .primaryNodeId(primaryNodeId)
                .build();
    }

    /**
     * Creates a sharding result with hash value.
     *
     * @param partitionId the partition ID
     * @param primaryNodeId the primary node ID
     * @param hashValue the computed hash value
     * @return a new ShardingResult
     */
    public static ShardingResult of(int partitionId, String primaryNodeId, long hashValue) {
        return ShardingResult.builder()
                .partitionId(partitionId)
                .primaryNodeId(primaryNodeId)
                .hashValue(hashValue)
                .build();
    }

    /**
     * Creates an unassigned result (no node available for partition).
     *
     * @param partitionId the partition ID
     * @param hashValue the computed hash value
     * @return a new ShardingResult with no primary node
     */
    public static ShardingResult unassigned(int partitionId, long hashValue) {
        return ShardingResult.builder()
                .partitionId(partitionId)
                .hashValue(hashValue)
                .build();
    }

    // ========== Query Methods ==========

    /**
     * Checks if the sharding result has a valid primary node assignment.
     *
     * @return true if a primary node is assigned
     */
    public boolean isAssigned() {
        return primaryNodeId != null && !primaryNodeId.isEmpty();
    }

    /**
     * Gets the primary node ID as an Optional.
     *
     * @return Optional containing the primary node ID
     */
    public Optional<String> getPrimaryNodeIdOptional() {
        return Optional.ofNullable(primaryNodeId);
    }

    /**
     * Gets the first replica node ID.
     *
     * @return Optional containing the first replica node ID
     */
    public Optional<String> getFirstReplicaNodeId() {
        return replicaNodeIds.isEmpty() ? Optional.empty() : Optional.of(replicaNodeIds.get(0));
    }

    /**
     * Gets all node IDs (primary + replicas) in priority order.
     *
     * @return list of all node IDs
     */
    public List<String> getAllNodeIds() {
        if (primaryNodeId == null) {
            return replicaNodeIds;
        }
        List<String> all = new java.util.ArrayList<>();
        all.add(primaryNodeId);
        all.addAll(replicaNodeIds);
        return Collections.unmodifiableList(all);
    }

    /**
     * Gets the total number of nodes (primary + replicas).
     *
     * @return the node count
     */
    public int getNodeCount() {
        return (primaryNodeId != null ? 1 : 0) + replicaNodeIds.size();
    }

    /**
     * Checks if a specific node is part of this sharding result.
     *
     * @param nodeId the node ID to check
     * @return true if the node is primary or a replica
     */
    public boolean includesNode(String nodeId) {
        if (nodeId == null) {
            return false;
        }
        return nodeId.equals(primaryNodeId) || replicaNodeIds.contains(nodeId);
    }

    /**
     * Gets metadata value by key.
     *
     * @param key the metadata key
     * @return Optional containing the metadata value
     */
    public Optional<String> getMetadata(String key) {
        return Optional.ofNullable(metadata.get(key));
    }

    /**
     * Creates a copy with a different primary node.
     *
     * @param newPrimaryNodeId the new primary node ID
     * @return a new ShardingResult with updated primary node
     */
    public ShardingResult withPrimaryNode(String newPrimaryNodeId) {
        return this.toBuilder().primaryNodeId(newPrimaryNodeId).build();
    }

    /**
     * Creates a copy with replica nodes added.
     *
     * @param replicas the replica node IDs
     * @return a new ShardingResult with replicas
     */
    public ShardingResult withReplicas(List<String> replicas) {
        return this.toBuilder().replicaNodeIds(replicas).build();
    }

    /**
     * Creates a copy marked as sticky.
     *
     * @return a new ShardingResult marked as sticky
     */
    public ShardingResult asSticky() {
        return this.toBuilder().sticky(true).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShardingResult that = (ShardingResult) o;
        return partitionId == that.partitionId &&
               Objects.equals(primaryNodeId, that.primaryNodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(partitionId, primaryNodeId);
    }
}
