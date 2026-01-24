package com.lyshra.open.app.distributed.sharding;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strategy interface for determining workflow-to-partition mapping.
 *
 * A sharding strategy defines how workflow execution keys are mapped to partitions.
 * Different strategies can be plugged in based on requirements:
 * - Consistent hashing for minimal rebalancing during topology changes
 * - Range-based sharding for ordered workflows
 * - Tenant-aware sharding for multi-tenant isolation
 * - Custom strategies for specific business requirements
 *
 * Key Properties:
 * - Deterministic: Same key always maps to same partition
 * - Even distribution: Keys are evenly spread across partitions
 * - Minimal reshuffling: Adding/removing nodes moves minimal data
 *
 * Design Pattern: Strategy Pattern - allows swapping sharding algorithms without
 * changing the partition management code.
 *
 * Thread Safety: Implementations should be thread-safe.
 */
public interface IShardingStrategy {

    // ========== Core Sharding Methods ==========

    /**
     * Computes the partition ID for a given workflow execution key.
     *
     * This method must be:
     * - Deterministic: Same input always produces same output
     * - Fast: Called for every workflow operation
     * - Consistent: Results remain stable across JVM restarts
     *
     * @param workflowExecutionKey the unique key identifying a workflow execution
     * @param totalPartitions the total number of partitions in the cluster
     * @return the partition ID (0 to totalPartitions-1)
     */
    int computePartition(String workflowExecutionKey, int totalPartitions);

    /**
     * Computes the partition ID for a workflow based on its identifier components.
     *
     * @param organization the plugin organization
     * @param module the plugin module
     * @param version the plugin version
     * @param workflowName the workflow name
     * @param executionId the unique execution identifier
     * @param totalPartitions the total number of partitions
     * @return the partition ID
     */
    int computePartition(String organization, String module, String version,
                         String workflowName, String executionId, int totalPartitions);

    /**
     * Generates the execution key from workflow identifier components.
     *
     * @param organization the plugin organization
     * @param module the plugin module
     * @param version the plugin version
     * @param workflowName the workflow name
     * @param executionId the unique execution identifier
     * @return the composite execution key
     */
    String generateExecutionKey(String organization, String module, String version,
                                 String workflowName, String executionId);

    /**
     * Returns the set of partitions that would be affected if a node leaves.
     * Used for planning rebalancing operations.
     *
     * @param nodeId the node that is leaving
     * @param currentAssignment the current partition-to-node assignment
     * @param totalPartitions the total number of partitions
     * @return set of partition IDs that need reassignment
     */
    Set<Integer> getAffectedPartitions(String nodeId,
                                        Map<Integer, String> currentAssignment,
                                        int totalPartitions);

    /**
     * Returns the name of this sharding strategy for configuration purposes.
     *
     * @return the strategy name
     */
    String getStrategyName();

    // ========== Enhanced Sharding with ShardingKey ==========

    /**
     * Computes the shard (partition) for a given sharding key.
     *
     * This is the primary method for determining where a workflow should be placed.
     * The result includes the partition ID and, if available, the node assignment.
     *
     * @param key the sharding key
     * @param totalPartitions the total number of partitions
     * @return the sharding result with partition and node information
     */
    default ShardingResult shard(ShardingKey key, int totalPartitions) {
        int partition = computePartition(key.getPrimaryKey(), totalPartitions);
        long hash = computeHash(key.getPrimaryKey());
        return ShardingResult.builder()
                .partitionId(partition)
                .hashValue(hash)
                .strategyName(getStrategyName())
                .shardingKey(key)
                .build();
    }

    /**
     * Computes the shard using the strategy's configured partition count.
     *
     * @param key the sharding key
     * @return the sharding result
     */
    default ShardingResult shard(ShardingKey key) {
        return shard(key, getDefaultTotalPartitions());
    }

    /**
     * Computes the hash value for a key without mapping to partition.
     *
     * Useful for debugging or custom partition calculations.
     *
     * @param key the string key
     * @return the hash value
     */
    long computeHash(String key);

    /**
     * Gets the default total partitions for this strategy.
     *
     * @return the default partition count
     */
    default int getDefaultTotalPartitions() {
        return 16; // Default value, implementations can override
    }

    /**
     * Batch computes partitions for multiple keys.
     *
     * More efficient than calling shard() multiple times for bulk operations.
     *
     * @param keys the sharding keys
     * @param totalPartitions the total number of partitions
     * @return map of sharding key to result
     */
    default Map<ShardingKey, ShardingResult> shardBatch(List<ShardingKey> keys, int totalPartitions) {
        Map<ShardingKey, ShardingResult> results = new java.util.HashMap<>();
        for (ShardingKey key : keys) {
            results.put(key, shard(key, totalPartitions));
        }
        return results;
    }

    // ========== Node Assignment Integration ==========

    /**
     * Computes the shard with node assignment information.
     *
     * @param key the sharding key
     * @param totalPartitions the total number of partitions
     * @param assignment the current partition assignment
     * @return the sharding result with node information
     */
    default ShardingResult shardWithAssignment(ShardingKey key, int totalPartitions,
                                                PartitionAssignment assignment) {
        ShardingResult baseResult = shard(key, totalPartitions);
        if (assignment != null) {
            String primaryNode = assignment.getOwner(baseResult.getPartitionId());
            List<String> replicas = assignment.getReplicas(baseResult.getPartitionId());
            return baseResult.toBuilder()
                    .primaryNodeId(primaryNode)
                    .replicaNodeIds(replicas)
                    .build();
        }
        return baseResult;
    }

    /**
     * Checks if a node is responsible for a partition.
     *
     * @param nodeId the node ID
     * @param partitionId the partition ID
     * @param assignment the partition assignment
     * @return true if the node owns the partition
     */
    default boolean isNodeResponsibleForPartition(String nodeId, int partitionId,
                                                   PartitionAssignment assignment) {
        return assignment != null && assignment.isOwnedBy(nodeId, partitionId);
    }

    /**
     * Gets the primary node for a partition.
     *
     * @param partitionId the partition ID
     * @param assignment the partition assignment
     * @return the primary node ID, or null if not assigned
     */
    default String getNodeForPartition(int partitionId, PartitionAssignment assignment) {
        return assignment != null ? assignment.getOwner(partitionId) : null;
    }

    // ========== Rebalancing Support ==========

    /**
     * Calculates optimal partition reassignments when nodes change.
     *
     * @param currentAssignment the current assignment
     * @param currentNodes the current set of active nodes
     * @param targetReplicationFactor the desired replication factor
     * @return map of partition ID to new node assignments (primary + replicas)
     */
    default Map<Integer, List<String>> calculateRebalance(PartitionAssignment currentAssignment,
                                                           Set<String> currentNodes,
                                                           int targetReplicationFactor) {
        Map<Integer, List<String>> newAssignments = new java.util.HashMap<>();

        if (currentNodes.isEmpty()) {
            return newAssignments;
        }

        List<String> nodeList = new java.util.ArrayList<>(currentNodes);
        int totalPartitions = currentAssignment.getTotalPartitions();

        // Simple round-robin assignment for primary
        for (int p = 0; p < totalPartitions; p++) {
            List<String> nodes = new java.util.ArrayList<>();
            int primaryIdx = p % nodeList.size();
            nodes.add(nodeList.get(primaryIdx));

            // Add replicas
            for (int r = 1; r < targetReplicationFactor && r < nodeList.size(); r++) {
                int replicaIdx = (primaryIdx + r) % nodeList.size();
                nodes.add(nodeList.get(replicaIdx));
            }

            newAssignments.put(p, nodes);
        }

        return newAssignments;
    }

    /**
     * Calculates the minimum number of partitions that need to move during rebalance.
     *
     * @param currentAssignment the current assignment
     * @param newAssignment the proposed new assignment
     * @return the number of partitions that need to move
     */
    default int calculateMovementCost(PartitionAssignment currentAssignment,
                                       Map<Integer, List<String>> newAssignment) {
        int moves = 0;
        for (Map.Entry<Integer, List<String>> entry : newAssignment.entrySet()) {
            String currentOwner = currentAssignment.getOwner(entry.getKey());
            String newOwner = entry.getValue().isEmpty() ? null : entry.getValue().get(0);
            if (currentOwner == null || !currentOwner.equals(newOwner)) {
                moves++;
            }
        }
        return moves;
    }

    // ========== Distribution Statistics ==========

    /**
     * Analyzes the distribution of a set of keys across partitions.
     *
     * @param keys the keys to analyze
     * @param totalPartitions the total number of partitions
     * @return distribution analysis
     */
    default DistributionAnalysis analyzeDistribution(List<String> keys, int totalPartitions) {
        Map<Integer, Integer> partitionCounts = new java.util.HashMap<>();
        for (int i = 0; i < totalPartitions; i++) {
            partitionCounts.put(i, 0);
        }

        for (String key : keys) {
            int partition = computePartition(key, totalPartitions);
            partitionCounts.merge(partition, 1, Integer::sum);
        }

        int min = partitionCounts.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        int max = partitionCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        double avg = keys.size() / (double) totalPartitions;

        double variance = partitionCounts.values().stream()
                .mapToDouble(c -> Math.pow(c - avg, 2))
                .average().orElse(0);
        double stdDev = Math.sqrt(variance);

        return new DistributionAnalysis(totalPartitions, keys.size(), min, max, avg, stdDev, partitionCounts);
    }

    /**
     * Analysis of key distribution across partitions.
     */
    record DistributionAnalysis(
            int totalPartitions,
            int totalKeys,
            int minKeysPerPartition,
            int maxKeysPerPartition,
            double averageKeysPerPartition,
            double standardDeviation,
            Map<Integer, Integer> keysPerPartition
    ) {
        /**
         * Checks if the distribution is balanced (coefficient of variation < 0.1).
         */
        public boolean isBalanced() {
            if (averageKeysPerPartition == 0) return true;
            return standardDeviation / averageKeysPerPartition < 0.1;
        }

        /**
         * Gets the imbalance ratio (max/min).
         */
        public double getImbalanceRatio() {
            if (minKeysPerPartition == 0) return maxKeysPerPartition > 0 ? Double.POSITIVE_INFINITY : 1.0;
            return (double) maxKeysPerPartition / minKeysPerPartition;
        }
    }
}
