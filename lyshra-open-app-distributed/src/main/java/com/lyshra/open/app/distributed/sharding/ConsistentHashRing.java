package com.lyshra.open.app.distributed.sharding;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Consistent hash ring implementation with virtual nodes for even distribution.
 *
 * This implementation provides:
 * - Virtual nodes for better load distribution
 * - O(log N) lookup time using a TreeMap
 * - Minimal key movement when nodes are added/removed
 * - Support for weighted nodes (different capacities)
 *
 * The hash ring uses a 64-bit hash space with configurable virtual nodes
 * per physical node. More virtual nodes = better distribution but more memory.
 *
 * Thread Safety: This class is thread-safe using ConcurrentSkipListMap.
 *
 * Usage:
 * <pre>
 * ConsistentHashRing ring = new ConsistentHashRing(150); // 150 virtual nodes
 * ring.addNode("node-1");
 * ring.addNode("node-2");
 * ring.addNode("node-3");
 *
 * String node = ring.getNode("workflow-key");
 * List<String> nodes = ring.getNodes("workflow-key", 3); // Get 3 nodes for replication
 * </pre>
 */
@Slf4j
public class ConsistentHashRing {

    private static final int HASH_SEED = 0x1234abcd;
    private static final int DEFAULT_VIRTUAL_NODES = 150;

    private final int virtualNodesPerNode;
    private final ConcurrentSkipListMap<Long, String> ring;
    private final Map<String, Integer> nodeWeights;
    private final Set<String> physicalNodes;
    private final AtomicLong version;

    @Getter
    private volatile int totalVirtualNodes;

    /**
     * Creates a consistent hash ring with default virtual nodes (150 per physical node).
     */
    public ConsistentHashRing() {
        this(DEFAULT_VIRTUAL_NODES);
    }

    /**
     * Creates a consistent hash ring with specified virtual nodes per physical node.
     *
     * @param virtualNodesPerNode number of virtual nodes per physical node
     */
    public ConsistentHashRing(int virtualNodesPerNode) {
        if (virtualNodesPerNode <= 0) {
            throw new IllegalArgumentException("virtualNodesPerNode must be positive");
        }
        this.virtualNodesPerNode = virtualNodesPerNode;
        this.ring = new ConcurrentSkipListMap<>();
        this.nodeWeights = new HashMap<>();
        this.physicalNodes = new HashSet<>();
        this.version = new AtomicLong(0);
        this.totalVirtualNodes = 0;
    }

    // ========== Node Management ==========

    /**
     * Adds a node to the ring with default weight (1.0).
     *
     * @param nodeId the node ID
     */
    public void addNode(String nodeId) {
        addNode(nodeId, 1.0);
    }

    /**
     * Adds a node to the ring with specified weight.
     *
     * Weight determines the relative number of virtual nodes:
     * - weight 1.0 = virtualNodesPerNode virtual nodes
     * - weight 2.0 = 2x virtualNodesPerNode virtual nodes
     *
     * @param nodeId the node ID
     * @param weight the node weight (capacity factor)
     */
    public synchronized void addNode(String nodeId, double weight) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        if (weight <= 0) {
            throw new IllegalArgumentException("weight must be positive");
        }

        if (physicalNodes.contains(nodeId)) {
            log.debug("Node {} already exists in the ring", nodeId);
            return;
        }

        int numVirtualNodes = (int) Math.ceil(virtualNodesPerNode * weight);
        nodeWeights.put(nodeId, numVirtualNodes);

        for (int i = 0; i < numVirtualNodes; i++) {
            String virtualNodeKey = nodeId + "#" + i;
            long hash = computeHash(virtualNodeKey);
            ring.put(hash, nodeId);
        }

        physicalNodes.add(nodeId);
        totalVirtualNodes += numVirtualNodes;
        version.incrementAndGet();

        log.debug("Added node {} to ring with {} virtual nodes (weight: {})",
                nodeId, numVirtualNodes, weight);
    }

    /**
     * Removes a node from the ring.
     *
     * @param nodeId the node ID
     * @return true if the node was removed
     */
    public synchronized boolean removeNode(String nodeId) {
        if (!physicalNodes.contains(nodeId)) {
            return false;
        }

        Integer numVirtualNodes = nodeWeights.remove(nodeId);
        if (numVirtualNodes == null) {
            numVirtualNodes = virtualNodesPerNode;
        }

        for (int i = 0; i < numVirtualNodes; i++) {
            String virtualNodeKey = nodeId + "#" + i;
            long hash = computeHash(virtualNodeKey);
            ring.remove(hash);
        }

        physicalNodes.remove(nodeId);
        totalVirtualNodes -= numVirtualNodes;
        version.incrementAndGet();

        log.debug("Removed node {} from ring ({} virtual nodes removed)", nodeId, numVirtualNodes);
        return true;
    }

    /**
     * Clears all nodes from the ring.
     */
    public synchronized void clear() {
        ring.clear();
        nodeWeights.clear();
        physicalNodes.clear();
        totalVirtualNodes = 0;
        version.incrementAndGet();
    }

    // ========== Node Lookup ==========

    /**
     * Gets the node responsible for a key.
     *
     * @param key the key to look up
     * @return the node ID, or null if the ring is empty
     */
    public String getNode(String key) {
        if (ring.isEmpty()) {
            return null;
        }

        long hash = computeHash(key);
        Map.Entry<Long, String> entry = ring.ceilingEntry(hash);

        // Wrap around to the beginning if necessary
        if (entry == null) {
            entry = ring.firstEntry();
        }

        return entry.getValue();
    }

    /**
     * Gets multiple nodes for a key (for replication).
     *
     * Returns unique physical nodes in order of their position on the ring.
     *
     * @param key the key to look up
     * @param count the number of nodes to return
     * @return list of node IDs (may be fewer than count if not enough nodes)
     */
    public List<String> getNodes(String key, int count) {
        if (ring.isEmpty() || count <= 0) {
            return Collections.emptyList();
        }

        List<String> nodes = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        long hash = computeHash(key);

        // Start from the ceiling entry
        NavigableMap<Long, String> tailMap = ring.tailMap(hash, true);
        for (String nodeId : tailMap.values()) {
            if (!seen.contains(nodeId)) {
                nodes.add(nodeId);
                seen.add(nodeId);
                if (nodes.size() >= count) {
                    return nodes;
                }
            }
        }

        // Wrap around to the beginning
        for (String nodeId : ring.values()) {
            if (!seen.contains(nodeId)) {
                nodes.add(nodeId);
                seen.add(nodeId);
                if (nodes.size() >= count) {
                    return nodes;
                }
            }
        }

        return nodes;
    }

    /**
     * Gets the node and hash position for a key.
     *
     * @param key the key to look up
     * @return Optional containing the lookup result
     */
    public Optional<LookupResult> lookup(String key) {
        if (ring.isEmpty()) {
            return Optional.empty();
        }

        long hash = computeHash(key);
        Map.Entry<Long, String> entry = ring.ceilingEntry(hash);

        if (entry == null) {
            entry = ring.firstEntry();
        }

        return Optional.of(new LookupResult(key, hash, entry.getValue(), entry.getKey()));
    }

    // ========== Ring Information ==========

    /**
     * Gets all physical nodes in the ring.
     *
     * @return set of node IDs
     */
    public Set<String> getPhysicalNodes() {
        return Collections.unmodifiableSet(new HashSet<>(physicalNodes));
    }

    /**
     * Gets the number of physical nodes.
     *
     * @return the node count
     */
    public int getNodeCount() {
        return physicalNodes.size();
    }

    /**
     * Checks if a node exists in the ring.
     *
     * @param nodeId the node ID
     * @return true if the node exists
     */
    public boolean containsNode(String nodeId) {
        return physicalNodes.contains(nodeId);
    }

    /**
     * Gets the current ring version (incremented on changes).
     *
     * @return the version number
     */
    public long getVersion() {
        return version.get();
    }

    /**
     * Gets the weight of a node.
     *
     * @param nodeId the node ID
     * @return the weight, or 0 if the node doesn't exist
     */
    public double getNodeWeight(String nodeId) {
        Integer virtualNodes = nodeWeights.get(nodeId);
        if (virtualNodes == null) {
            return 0;
        }
        return (double) virtualNodes / virtualNodesPerNode;
    }

    // ========== Distribution Analysis ==========

    /**
     * Analyzes the distribution of keys across nodes.
     *
     * @param keys the keys to analyze
     * @return distribution analysis
     */
    public DistributionAnalysis analyzeDistribution(List<String> keys) {
        Map<String, Integer> nodeCounts = new HashMap<>();
        for (String node : physicalNodes) {
            nodeCounts.put(node, 0);
        }

        for (String key : keys) {
            String node = getNode(key);
            if (node != null) {
                nodeCounts.merge(node, 1, Integer::sum);
            }
        }

        int min = nodeCounts.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        int max = nodeCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        double avg = keys.isEmpty() ? 0 : (double) keys.size() / Math.max(1, physicalNodes.size());

        double variance = nodeCounts.values().stream()
                .mapToDouble(c -> Math.pow(c - avg, 2))
                .average().orElse(0);
        double stdDev = Math.sqrt(variance);

        return new DistributionAnalysis(
                physicalNodes.size(),
                keys.size(),
                min,
                max,
                avg,
                stdDev,
                nodeCounts
        );
    }

    /**
     * Gets the hash ring positions (for debugging/visualization).
     *
     * @return sorted map of hash position to node ID
     */
    public NavigableMap<Long, String> getRingPositions() {
        return Collections.unmodifiableNavigableMap(new TreeMap<>(ring));
    }

    /**
     * Calculates the keys that would move if a node is removed.
     *
     * @param nodeId the node to potentially remove
     * @param sampleKeys a sample of keys to check
     * @return set of keys that would move
     */
    public Set<String> calculateKeyMovement(String nodeId, Set<String> sampleKeys) {
        if (!containsNode(nodeId)) {
            return Collections.emptySet();
        }

        Set<String> movedKeys = new HashSet<>();

        for (String key : sampleKeys) {
            String currentNode = getNode(key);
            if (nodeId.equals(currentNode)) {
                movedKeys.add(key);
            }
        }

        return movedKeys;
    }

    // ========== Hash Computation ==========

    /**
     * Computes the hash for a key.
     *
     * @param key the key
     * @return the 64-bit hash
     */
    public long computeHash(String key) {
        return murmurHash64(key.getBytes(StandardCharsets.UTF_8), HASH_SEED);
    }

    /**
     * MurmurHash3 64-bit implementation.
     */
    private long murmurHash64(byte[] data, int seed) {
        final long m = 0xc6a4a7935bd1e995L;
        final int r = 47;

        int length = data.length;
        long h = (seed & 0xffffffffL) ^ (length * m);

        int length8 = length / 8;

        for (int i = 0; i < length8; i++) {
            final int i8 = i * 8;
            long k = ((long) data[i8] & 0xff)
                    | (((long) data[i8 + 1] & 0xff) << 8)
                    | (((long) data[i8 + 2] & 0xff) << 16)
                    | (((long) data[i8 + 3] & 0xff) << 24)
                    | (((long) data[i8 + 4] & 0xff) << 32)
                    | (((long) data[i8 + 5] & 0xff) << 40)
                    | (((long) data[i8 + 6] & 0xff) << 48)
                    | (((long) data[i8 + 7] & 0xff) << 56);

            k *= m;
            k ^= k >>> r;
            k *= m;

            h ^= k;
            h *= m;
        }

        int remaining = length % 8;
        if (remaining > 0) {
            int offset = length8 * 8;
            switch (remaining) {
                case 7: h ^= (long) (data[offset + 6] & 0xff) << 48;
                case 6: h ^= (long) (data[offset + 5] & 0xff) << 40;
                case 5: h ^= (long) (data[offset + 4] & 0xff) << 32;
                case 4: h ^= (long) (data[offset + 3] & 0xff) << 24;
                case 3: h ^= (long) (data[offset + 2] & 0xff) << 16;
                case 2: h ^= (long) (data[offset + 1] & 0xff) << 8;
                case 1: h ^= (long) (data[offset] & 0xff);
                    h *= m;
            }
        }

        h ^= h >>> r;
        h *= m;
        h ^= h >>> r;

        return h;
    }

    // ========== Inner Classes ==========

    /**
     * Result of a key lookup.
     */
    public record LookupResult(String key, long keyHash, String nodeId, long nodeHash) {
        /**
         * Gets the "distance" on the ring from key to node (clockwise).
         */
        public long getRingDistance() {
            if (nodeHash >= keyHash) {
                return nodeHash - keyHash;
            }
            // Wrapped around
            return (Long.MAX_VALUE - keyHash) + nodeHash + 1;
        }
    }

    /**
     * Analysis of key distribution across nodes.
     */
    public record DistributionAnalysis(
            int totalNodes,
            int totalKeys,
            int minKeysPerNode,
            int maxKeysPerNode,
            double averageKeysPerNode,
            double standardDeviation,
            Map<String, Integer> keysPerNode
    ) {
        /**
         * Checks if the distribution is balanced.
         */
        public boolean isBalanced() {
            if (averageKeysPerNode == 0) return true;
            return standardDeviation / averageKeysPerNode < 0.15;
        }

        /**
         * Gets the imbalance ratio.
         */
        public double getImbalanceRatio() {
            if (minKeysPerNode == 0) return maxKeysPerNode > 0 ? Double.POSITIVE_INFINITY : 1.0;
            return (double) maxKeysPerNode / minKeysPerNode;
        }
    }

    @Override
    public String toString() {
        return "ConsistentHashRing{" +
                "physicalNodes=" + physicalNodes.size() +
                ", virtualNodes=" + totalVirtualNodes +
                ", virtualNodesPerNode=" + virtualNodesPerNode +
                ", version=" + version.get() +
                '}';
    }
}
