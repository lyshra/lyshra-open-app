package com.lyshra.open.app.distributed.sharding.impl;

import com.lyshra.open.app.distributed.sharding.IShardingStrategy;
import com.lyshra.open.app.distributed.sharding.PartitionAssignment;
import com.lyshra.open.app.distributed.sharding.ShardingKey;
import com.lyshra.open.app.distributed.sharding.ShardingResult;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Consistent hashing based sharding strategy.
 *
 * Uses MurmurHash3-style hashing for uniform distribution and consistent
 * mapping of workflow execution keys to partitions. This strategy ensures:
 *
 * 1. Determinism: Same key always maps to same partition
 * 2. Uniform distribution: Keys are evenly spread across partitions
 * 3. Minimal disruption: When partition count changes, only 1/n keys move
 *
 * The strategy uses Jump Consistent Hash algorithm which provides:
 * - O(1) time complexity for partition lookup
 * - Perfect consistency: when bucket count changes from n to n+1, only 1/(n+1) keys move
 * - No memory overhead for virtual nodes
 *
 * Thread Safety: This class is thread-safe. All operations are stateless.
 *
 * Usage:
 * <pre>
 * IShardingStrategy strategy = new ConsistentHashShardingStrategy();
 * int partition = strategy.computePartition("workflow-123", 16);
 *
 * // Or with ShardingKey
 * ShardingKey key = ShardingKey.forWorkflow("order-process", "exec-123");
 * ShardingResult result = strategy.shard(key, 16);
 * </pre>
 */
@Slf4j
public class ConsistentHashShardingStrategy implements IShardingStrategy {

    private static final String STRATEGY_NAME = "consistent-hash";
    private static final String KEY_SEPARATOR = "::";
    private static final int HASH_SEED = 0x9747b28c;

    @Getter
    private final int defaultTotalPartitions;

    /**
     * Creates a ConsistentHashShardingStrategy with default 16 partitions.
     */
    public ConsistentHashShardingStrategy() {
        this(16);
    }

    /**
     * Creates a ConsistentHashShardingStrategy with specified default partitions.
     *
     * @param defaultTotalPartitions the default number of partitions
     */
    public ConsistentHashShardingStrategy(int defaultTotalPartitions) {
        if (defaultTotalPartitions <= 0) {
            throw new IllegalArgumentException("defaultTotalPartitions must be positive");
        }
        this.defaultTotalPartitions = defaultTotalPartitions;
    }

    @Override
    public int computePartition(String workflowExecutionKey, int totalPartitions) {
        Objects.requireNonNull(workflowExecutionKey, "workflowExecutionKey must not be null");
        if (totalPartitions <= 0) {
            throw new IllegalArgumentException("totalPartitions must be positive");
        }

        // Use consistent hashing with jump hash for uniform distribution
        long hash = murmurHash64(workflowExecutionKey.getBytes(StandardCharsets.UTF_8), HASH_SEED);
        return jumpConsistentHash(hash, totalPartitions);
    }

    @Override
    public int computePartition(String organization, String module, String version,
                                 String workflowName, String executionId, int totalPartitions) {
        String key = generateExecutionKey(organization, module, version, workflowName, executionId);
        return computePartition(key, totalPartitions);
    }

    @Override
    public String generateExecutionKey(String organization, String module, String version,
                                         String workflowName, String executionId) {
        Objects.requireNonNull(organization, "organization must not be null");
        Objects.requireNonNull(module, "module must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(workflowName, "workflowName must not be null");
        Objects.requireNonNull(executionId, "executionId must not be null");

        return String.join(KEY_SEPARATOR,
                organization,
                module,
                version,
                workflowName,
                executionId);
    }

    @Override
    public Set<Integer> getAffectedPartitions(String nodeId,
                                               Map<Integer, String> currentAssignment,
                                               int totalPartitions) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(currentAssignment, "currentAssignment must not be null");

        Set<Integer> affected = new HashSet<>();
        for (Map.Entry<Integer, String> entry : currentAssignment.entrySet()) {
            if (nodeId.equals(entry.getValue())) {
                affected.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(affected);
    }

    @Override
    public String getStrategyName() {
        return STRATEGY_NAME;
    }

    @Override
    public long computeHash(String key) {
        Objects.requireNonNull(key, "key must not be null");
        return murmurHash64(key.getBytes(StandardCharsets.UTF_8), HASH_SEED);
    }

    @Override
    public ShardingResult shard(ShardingKey key, int totalPartitions) {
        Objects.requireNonNull(key, "key must not be null");
        if (totalPartitions <= 0) {
            throw new IllegalArgumentException("totalPartitions must be positive");
        }

        String primaryKey = key.getPrimaryKey();
        long hash = computeHash(primaryKey);
        int partition = jumpConsistentHash(hash, totalPartitions);

        return ShardingResult.builder()
                .partitionId(partition)
                .hashValue(hash)
                .strategyName(STRATEGY_NAME)
                .shardingKey(key)
                .build();
    }

    @Override
    public ShardingResult shardWithAssignment(ShardingKey key, int totalPartitions,
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
     * Jump Consistent Hash algorithm.
     *
     * This algorithm provides O(1) hashing with perfect consistency:
     * when the bucket count changes from n to n+1, only 1/(n+1) keys move.
     *
     * Reference: "A Fast, Minimal Memory, Consistent Hash Algorithm"
     * by John Lamping and Eric Veach (Google)
     *
     * @param key the 64-bit hash of the key
     * @param numBuckets the number of buckets (partitions)
     * @return the bucket (partition) number
     */
    private int jumpConsistentHash(long key, int numBuckets) {
        long b = -1;
        long j = 0;

        while (j < numBuckets) {
            b = j;
            key = key * 2862933555777941757L + 1;
            j = (long) ((b + 1) * (double) (1L << 31) / (double) ((key >>> 33) + 1));
        }

        return (int) b;
    }

    /**
     * MurmurHash3 64-bit implementation.
     *
     * Provides excellent distribution and performance for string keys.
     *
     * @param data the data to hash
     * @param seed the hash seed
     * @return 64-bit hash value
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
                case 7:
                    h ^= (long) (data[offset + 6] & 0xff) << 48;
                case 6:
                    h ^= (long) (data[offset + 5] & 0xff) << 40;
                case 5:
                    h ^= (long) (data[offset + 4] & 0xff) << 32;
                case 4:
                    h ^= (long) (data[offset + 3] & 0xff) << 24;
                case 3:
                    h ^= (long) (data[offset + 2] & 0xff) << 16;
                case 2:
                    h ^= (long) (data[offset + 1] & 0xff) << 8;
                case 1:
                    h ^= (long) (data[offset] & 0xff);
                    h *= m;
            }
        }

        h ^= h >>> r;
        h *= m;
        h ^= h >>> r;

        return h;
    }
}
