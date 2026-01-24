package com.lyshra.open.app.distributed.sharding;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tracks the assignment of partitions to nodes in the cluster.
 *
 * This class maintains:
 * - Which node owns which partitions (primary assignment)
 * - Replica assignments for fault tolerance
 * - Assignment version for consistency
 * - Assignment history for debugging
 *
 * Thread Safety: This class is thread-safe using ConcurrentHashMap.
 *
 * Usage:
 * <pre>
 * PartitionAssignment assignment = PartitionAssignment.create(16);
 * assignment.assignPartition(0, "node-1");
 * assignment.assignPartition(1, "node-2");
 *
 * String owner = assignment.getOwner(0); // "node-1"
 * Set<Integer> partitions = assignment.getPartitionsForNode("node-1"); // [0]
 * </pre>
 */
@Slf4j
public class PartitionAssignment {

    private final int totalPartitions;
    private final ConcurrentHashMap<Integer, String> primaryAssignment;
    private final ConcurrentHashMap<Integer, List<String>> replicaAssignment;
    private final ConcurrentHashMap<String, Set<Integer>> nodeToPartitions;
    private volatile long version;
    private volatile Instant lastUpdated;

    /**
     * Creates a new partition assignment with the specified number of partitions.
     *
     * @param totalPartitions the total number of partitions
     */
    public PartitionAssignment(int totalPartitions) {
        if (totalPartitions <= 0) {
            throw new IllegalArgumentException("totalPartitions must be positive");
        }
        this.totalPartitions = totalPartitions;
        this.primaryAssignment = new ConcurrentHashMap<>();
        this.replicaAssignment = new ConcurrentHashMap<>();
        this.nodeToPartitions = new ConcurrentHashMap<>();
        this.version = 0;
        this.lastUpdated = Instant.now();
    }

    /**
     * Factory method to create a new partition assignment.
     *
     * @param totalPartitions the total number of partitions
     * @return a new PartitionAssignment
     */
    public static PartitionAssignment create(int totalPartitions) {
        return new PartitionAssignment(totalPartitions);
    }

    /**
     * Creates a partition assignment with initial assignments.
     *
     * @param totalPartitions the total number of partitions
     * @param initialAssignment the initial partition-to-node mapping
     * @return a new PartitionAssignment
     */
    public static PartitionAssignment create(int totalPartitions, Map<Integer, String> initialAssignment) {
        PartitionAssignment assignment = new PartitionAssignment(totalPartitions);
        initialAssignment.forEach(assignment::assignPartition);
        return assignment;
    }

    // ========== Assignment Operations ==========

    /**
     * Assigns a partition to a node (primary assignment).
     *
     * @param partitionId the partition ID
     * @param nodeId the node ID
     */
    public void assignPartition(int partitionId, String nodeId) {
        validatePartitionId(partitionId);
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        String previousOwner = primaryAssignment.put(partitionId, nodeId);

        // Update reverse mapping
        if (previousOwner != null && !previousOwner.equals(nodeId)) {
            nodeToPartitions.computeIfPresent(previousOwner, (k, v) -> {
                Set<Integer> updated = new HashSet<>(v);
                updated.remove(partitionId);
                return updated.isEmpty() ? null : updated;
            });
        }
        nodeToPartitions.computeIfAbsent(nodeId, k -> ConcurrentHashMap.newKeySet()).add(partitionId);

        incrementVersion();
        log.debug("Assigned partition {} to node {} (previous: {})", partitionId, nodeId, previousOwner);
    }

    /**
     * Assigns replicas for a partition.
     *
     * @param partitionId the partition ID
     * @param replicaNodes the replica node IDs (ordered by priority)
     */
    public void assignReplicas(int partitionId, List<String> replicaNodes) {
        validatePartitionId(partitionId);
        replicaAssignment.put(partitionId, Collections.unmodifiableList(new ArrayList<>(replicaNodes)));
        incrementVersion();
    }

    /**
     * Unassigns a partition (removes the owner).
     *
     * @param partitionId the partition ID
     * @return the previous owner, or null if not assigned
     */
    public String unassignPartition(int partitionId) {
        validatePartitionId(partitionId);

        String previousOwner = primaryAssignment.remove(partitionId);
        if (previousOwner != null) {
            nodeToPartitions.computeIfPresent(previousOwner, (k, v) -> {
                Set<Integer> updated = new HashSet<>(v);
                updated.remove(partitionId);
                return updated.isEmpty() ? null : updated;
            });
        }
        replicaAssignment.remove(partitionId);

        incrementVersion();
        return previousOwner;
    }

    /**
     * Removes all partition assignments for a node.
     *
     * @param nodeId the node ID
     * @return the set of partitions that were unassigned
     */
    public Set<Integer> removeNode(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        Set<Integer> removed = new HashSet<>();
        Set<Integer> partitions = nodeToPartitions.remove(nodeId);

        if (partitions != null) {
            for (Integer partitionId : partitions) {
                if (nodeId.equals(primaryAssignment.get(partitionId))) {
                    primaryAssignment.remove(partitionId);
                    removed.add(partitionId);
                }
            }
        }

        // Also remove from replicas
        for (Map.Entry<Integer, List<String>> entry : replicaAssignment.entrySet()) {
            if (entry.getValue().contains(nodeId)) {
                List<String> updated = entry.getValue().stream()
                        .filter(n -> !n.equals(nodeId))
                        .collect(Collectors.toList());
                replicaAssignment.put(entry.getKey(), Collections.unmodifiableList(updated));
            }
        }

        incrementVersion();
        log.info("Removed node {} from partition assignment, unassigned {} partitions", nodeId, removed.size());
        return removed;
    }

    /**
     * Bulk assigns partitions to nodes.
     *
     * @param assignments map of partition ID to node ID
     */
    public void bulkAssign(Map<Integer, String> assignments) {
        assignments.forEach(this::assignPartition);
    }

    /**
     * Distributes partitions evenly across the given nodes.
     *
     * @param nodes the set of available nodes
     */
    public void distributeEvenly(Set<String> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            log.warn("Cannot distribute partitions: no nodes available");
            return;
        }

        List<String> nodeList = new ArrayList<>(nodes);
        for (int i = 0; i < totalPartitions; i++) {
            String node = nodeList.get(i % nodeList.size());
            assignPartition(i, node);
        }

        log.info("Distributed {} partitions evenly across {} nodes", totalPartitions, nodes.size());
    }

    // ========== Query Operations ==========

    /**
     * Gets the owner of a partition.
     *
     * @param partitionId the partition ID
     * @return the owner node ID, or null if not assigned
     */
    public String getOwner(int partitionId) {
        validatePartitionId(partitionId);
        return primaryAssignment.get(partitionId);
    }

    /**
     * Gets the owner of a partition as an Optional.
     *
     * @param partitionId the partition ID
     * @return Optional containing the owner node ID
     */
    public Optional<String> getOwnerOptional(int partitionId) {
        return Optional.ofNullable(getOwner(partitionId));
    }

    /**
     * Gets the replica nodes for a partition.
     *
     * @param partitionId the partition ID
     * @return list of replica node IDs
     */
    public List<String> getReplicas(int partitionId) {
        validatePartitionId(partitionId);
        return replicaAssignment.getOrDefault(partitionId, Collections.emptyList());
    }

    /**
     * Gets all nodes for a partition (primary + replicas).
     *
     * @param partitionId the partition ID
     * @return list of all node IDs for the partition
     */
    public List<String> getAllNodesForPartition(int partitionId) {
        List<String> nodes = new ArrayList<>();
        String primary = getOwner(partitionId);
        if (primary != null) {
            nodes.add(primary);
        }
        nodes.addAll(getReplicas(partitionId));
        return Collections.unmodifiableList(nodes);
    }

    /**
     * Gets all partitions assigned to a node (as primary).
     *
     * @param nodeId the node ID
     * @return set of partition IDs
     */
    public Set<Integer> getPartitionsForNode(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        return Collections.unmodifiableSet(
                nodeToPartitions.getOrDefault(nodeId, Collections.emptySet()));
    }

    /**
     * Gets all active nodes.
     *
     * @return set of node IDs with at least one partition
     */
    public Set<String> getActiveNodes() {
        return Collections.unmodifiableSet(new HashSet<>(nodeToPartitions.keySet()));
    }

    /**
     * Gets unassigned partitions.
     *
     * @return set of partition IDs without an owner
     */
    public Set<Integer> getUnassignedPartitions() {
        Set<Integer> unassigned = new HashSet<>();
        for (int i = 0; i < totalPartitions; i++) {
            if (!primaryAssignment.containsKey(i)) {
                unassigned.add(i);
            }
        }
        return Collections.unmodifiableSet(unassigned);
    }

    /**
     * Checks if a partition is assigned.
     *
     * @param partitionId the partition ID
     * @return true if assigned
     */
    public boolean isAssigned(int partitionId) {
        validatePartitionId(partitionId);
        return primaryAssignment.containsKey(partitionId);
    }

    /**
     * Checks if a node owns a specific partition.
     *
     * @param nodeId the node ID
     * @param partitionId the partition ID
     * @return true if the node owns the partition
     */
    public boolean isOwnedBy(String nodeId, int partitionId) {
        return nodeId != null && nodeId.equals(getOwner(partitionId));
    }

    /**
     * Gets the total number of partitions.
     *
     * @return the total partition count
     */
    public int getTotalPartitions() {
        return totalPartitions;
    }

    /**
     * Gets the current assignment version.
     *
     * @return the version number
     */
    public long getVersion() {
        return version;
    }

    /**
     * Gets the last update timestamp.
     *
     * @return the last update time
     */
    public Instant getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Gets the primary assignment as a map.
     *
     * @return unmodifiable map of partition ID to node ID
     */
    public Map<Integer, String> getPrimaryAssignmentMap() {
        return Collections.unmodifiableMap(new HashMap<>(primaryAssignment));
    }

    // ========== Statistics ==========

    /**
     * Gets distribution statistics.
     *
     * @return the distribution statistics
     */
    public DistributionStats getDistributionStats() {
        Map<String, Integer> partitionsPerNode = new HashMap<>();
        for (String nodeId : nodeToPartitions.keySet()) {
            partitionsPerNode.put(nodeId, getPartitionsForNode(nodeId).size());
        }

        int assigned = primaryAssignment.size();
        int unassigned = totalPartitions - assigned;

        double avg = partitionsPerNode.isEmpty() ? 0 :
                partitionsPerNode.values().stream().mapToInt(Integer::intValue).average().orElse(0);

        int min = partitionsPerNode.isEmpty() ? 0 :
                partitionsPerNode.values().stream().mapToInt(Integer::intValue).min().orElse(0);

        int max = partitionsPerNode.isEmpty() ? 0 :
                partitionsPerNode.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        double stdDev = 0;
        if (!partitionsPerNode.isEmpty()) {
            double sumSquaredDiff = 0;
            for (int count : partitionsPerNode.values()) {
                sumSquaredDiff += Math.pow(count - avg, 2);
            }
            stdDev = Math.sqrt(sumSquaredDiff / partitionsPerNode.size());
        }

        return DistributionStats.builder()
                .totalPartitions(totalPartitions)
                .assignedPartitions(assigned)
                .unassignedPartitions(unassigned)
                .activeNodes(partitionsPerNode.size())
                .minPartitionsPerNode(min)
                .maxPartitionsPerNode(max)
                .averagePartitionsPerNode(avg)
                .standardDeviation(stdDev)
                .partitionsPerNode(partitionsPerNode)
                .balanced(max - min <= 1 && unassigned == 0)
                .build();
    }

    /**
     * Creates a snapshot of the current assignment.
     *
     * @return a snapshot of the assignment
     */
    public AssignmentSnapshot snapshot() {
        return AssignmentSnapshot.builder()
                .totalPartitions(totalPartitions)
                .primaryAssignment(new HashMap<>(primaryAssignment))
                .replicaAssignment(new HashMap<>(replicaAssignment))
                .version(version)
                .timestamp(lastUpdated)
                .build();
    }

    // ========== Helper Methods ==========

    private void validatePartitionId(int partitionId) {
        if (partitionId < 0 || partitionId >= totalPartitions) {
            throw new IllegalArgumentException(
                    "partitionId must be between 0 and " + (totalPartitions - 1) + ", got: " + partitionId);
        }
    }

    private void incrementVersion() {
        version++;
        lastUpdated = Instant.now();
    }

    @Override
    public String toString() {
        return "PartitionAssignment{" +
                "totalPartitions=" + totalPartitions +
                ", assigned=" + primaryAssignment.size() +
                ", nodes=" + nodeToPartitions.size() +
                ", version=" + version +
                '}';
    }

    // ========== Inner Classes ==========

    /**
     * Distribution statistics for partition assignment.
     */
    @Getter
    @Builder
    @ToString
    public static class DistributionStats {
        private final int totalPartitions;
        private final int assignedPartitions;
        private final int unassignedPartitions;
        private final int activeNodes;
        private final int minPartitionsPerNode;
        private final int maxPartitionsPerNode;
        private final double averagePartitionsPerNode;
        private final double standardDeviation;
        private final Map<String, Integer> partitionsPerNode;
        private final boolean balanced;
    }

    /**
     * Snapshot of assignment state at a point in time.
     */
    @Getter
    @Builder
    @ToString
    public static class AssignmentSnapshot {
        private final int totalPartitions;
        private final Map<Integer, String> primaryAssignment;
        private final Map<Integer, List<String>> replicaAssignment;
        private final long version;
        private final Instant timestamp;

        /**
         * Restores a PartitionAssignment from this snapshot.
         *
         * @return a new PartitionAssignment
         */
        public PartitionAssignment restore() {
            PartitionAssignment assignment = new PartitionAssignment(totalPartitions);
            primaryAssignment.forEach(assignment::assignPartition);
            replicaAssignment.forEach(assignment::assignReplicas);
            return assignment;
        }
    }
}
