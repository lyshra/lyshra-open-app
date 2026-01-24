package com.lyshra.open.app.distributed.sharding;

import com.lyshra.open.app.distributed.sharding.impl.ConsistentHashShardingStrategy;
import com.lyshra.open.app.distributed.sharding.impl.TenantAwareShardingStrategy;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for the sharding subsystem.
 *
 * This class centralizes all sharding-related configuration including:
 * - Number of partitions
 * - Sharding strategy selection
 * - Replication factor
 * - Rebalancing settings
 * - Tenant-specific configurations
 *
 * Thread Safety: This class is immutable and thread-safe.
 *
 * Usage:
 * <pre>
 * ShardingConfiguration config = ShardingConfiguration.builder()
 *     .totalPartitions(64)
 *     .strategy(ShardingStrategy.CONSISTENT_HASH)
 *     .replicationFactor(3)
 *     .build();
 *
 * IShardingStrategy strategy = config.createStrategy();
 * </pre>
 */
@Getter
@Builder(toBuilder = true)
@ToString
public final class ShardingConfiguration {

    /**
     * Available sharding strategies.
     */
    public enum ShardingStrategy {
        /**
         * Consistent hash using Jump Consistent Hash algorithm.
         * Best for: General purpose, minimal key movement on scaling.
         */
        CONSISTENT_HASH,

        /**
         * Tenant-aware sharding with dedicated and shared partitions.
         * Best for: Multi-tenant systems with varying tenant sizes.
         */
        TENANT_AWARE,

        /**
         * Round-robin sharding (simple modulo).
         * Best for: Testing, simple even distribution.
         */
        ROUND_ROBIN,

        /**
         * Custom sharding strategy (provide your own implementation).
         */
        CUSTOM
    }

    /**
     * Total number of partitions.
     * Recommended: Power of 2 (16, 32, 64, 128) for better distribution.
     */
    @Builder.Default
    private final int totalPartitions = 16;

    /**
     * The sharding strategy to use.
     */
    @Builder.Default
    private final ShardingStrategy strategy = ShardingStrategy.CONSISTENT_HASH;

    /**
     * Replication factor for fault tolerance.
     * 1 = no replication, 3 = recommended for production.
     */
    @Builder.Default
    private final int replicationFactor = 1;

    /**
     * Number of virtual nodes per physical node (for consistent hash ring).
     * Higher values provide better distribution but use more memory.
     */
    @Builder.Default
    private final int virtualNodesPerNode = 150;

    /**
     * Whether to enable automatic rebalancing.
     */
    @Builder.Default
    private final boolean autoRebalanceEnabled = true;

    /**
     * Delay before triggering rebalancing after node changes.
     * Prevents thrashing during rapid cluster changes.
     */
    @Builder.Default
    private final Duration rebalanceDelay = Duration.ofSeconds(30);

    /**
     * Maximum concurrent partition moves during rebalancing.
     */
    @Builder.Default
    private final int maxConcurrentMoves = 4;

    /**
     * Tenant-specific configurations (for TENANT_AWARE strategy).
     * Map of tenant ID to dedicated partition range.
     */
    @Builder.Default
    private final Map<String, PartitionRangeConfig> tenantConfigs = Collections.emptyMap();

    /**
     * Number of partitions per tenant in shared pool.
     */
    @Builder.Default
    private final int partitionsPerTenant = 4;

    /**
     * Start of shared partition range (for TENANT_AWARE strategy).
     */
    @Builder.Default
    private final int sharedPartitionStart = 0;

    /**
     * End of shared partition range (for TENANT_AWARE strategy).
     */
    @Builder.Default
    private final int sharedPartitionEnd = -1; // -1 means use remaining

    /**
     * Custom strategy class name (for CUSTOM strategy).
     */
    private final String customStrategyClassName;

    /**
     * Custom strategy instance (for CUSTOM strategy).
     */
    private final IShardingStrategy customStrategy;

    /**
     * Whether to enable sticky sessions (prefer same node for related workflows).
     */
    @Builder.Default
    private final boolean stickySessionsEnabled = false;

    /**
     * Hash seed for deterministic hashing across restarts.
     */
    @Builder.Default
    private final int hashSeed = 0x9747b28c;

    // ========== Factory Methods ==========

    /**
     * Creates a default configuration suitable for development.
     *
     * @return default configuration
     */
    public static ShardingConfiguration defaultConfig() {
        return ShardingConfiguration.builder().build();
    }

    /**
     * Creates a production-ready configuration.
     *
     * @param totalPartitions the total number of partitions
     * @return production configuration
     */
    public static ShardingConfiguration production(int totalPartitions) {
        return ShardingConfiguration.builder()
                .totalPartitions(totalPartitions)
                .strategy(ShardingStrategy.CONSISTENT_HASH)
                .replicationFactor(3)
                .autoRebalanceEnabled(true)
                .virtualNodesPerNode(150)
                .build();
    }

    /**
     * Creates a configuration for testing.
     *
     * @return test configuration
     */
    public static ShardingConfiguration forTesting() {
        return ShardingConfiguration.builder()
                .totalPartitions(4)
                .strategy(ShardingStrategy.CONSISTENT_HASH)
                .replicationFactor(1)
                .autoRebalanceEnabled(false)
                .build();
    }

    /**
     * Creates a multi-tenant configuration.
     *
     * @param totalPartitions the total number of partitions
     * @param tenantConfigs tenant-specific configurations
     * @return multi-tenant configuration
     */
    public static ShardingConfiguration multiTenant(int totalPartitions,
                                                     Map<String, PartitionRangeConfig> tenantConfigs) {
        // Calculate shared partition range (partitions not assigned to tenants)
        int maxDedicatedPartition = 0;
        for (PartitionRangeConfig config : tenantConfigs.values()) {
            maxDedicatedPartition = Math.max(maxDedicatedPartition, config.endPartition());
        }

        return ShardingConfiguration.builder()
                .totalPartitions(totalPartitions)
                .strategy(ShardingStrategy.TENANT_AWARE)
                .tenantConfigs(tenantConfigs)
                .sharedPartitionStart(maxDedicatedPartition + 1)
                .sharedPartitionEnd(totalPartitions - 1)
                .replicationFactor(3)
                .build();
    }

    // ========== Strategy Creation ==========

    /**
     * Creates a sharding strategy based on this configuration.
     *
     * @return the configured sharding strategy
     */
    public IShardingStrategy createStrategy() {
        return switch (strategy) {
            case CONSISTENT_HASH -> new ConsistentHashShardingStrategy(totalPartitions);
            case TENANT_AWARE -> createTenantAwareStrategy();
            case ROUND_ROBIN -> createRoundRobinStrategy();
            case CUSTOM -> getCustomStrategy();
        };
    }

    private IShardingStrategy createTenantAwareStrategy() {
        TenantAwareShardingStrategy.Builder builder = TenantAwareShardingStrategy.builder()
                .totalPartitions(totalPartitions)
                .partitionsPerTenant(partitionsPerTenant);

        // Add dedicated tenants
        for (Map.Entry<String, PartitionRangeConfig> entry : tenantConfigs.entrySet()) {
            PartitionRangeConfig config = entry.getValue();
            builder.dedicatedTenant(entry.getKey(), config.startPartition(), config.endPartition());
        }

        // Set shared partition range
        int sharedEnd = sharedPartitionEnd >= 0 ? sharedPartitionEnd : totalPartitions - 1;
        builder.sharedPartitionRange(sharedPartitionStart, sharedEnd);

        return builder.build();
    }

    private IShardingStrategy createRoundRobinStrategy() {
        return new RoundRobinShardingStrategy(totalPartitions);
    }

    private IShardingStrategy getCustomStrategy() {
        if (customStrategy != null) {
            return customStrategy;
        }
        if (customStrategyClassName != null) {
            try {
                Class<?> clazz = Class.forName(customStrategyClassName);
                return (IShardingStrategy) clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create custom strategy: " + customStrategyClassName, e);
            }
        }
        throw new IllegalStateException("CUSTOM strategy requires customStrategy or customStrategyClassName");
    }

    /**
     * Creates a consistent hash ring based on this configuration.
     *
     * @return a new ConsistentHashRing
     */
    public ConsistentHashRing createHashRing() {
        return new ConsistentHashRing(virtualNodesPerNode);
    }

    /**
     * Creates a partition assignment based on this configuration.
     *
     * @return a new PartitionAssignment
     */
    public PartitionAssignment createPartitionAssignment() {
        return PartitionAssignment.create(totalPartitions);
    }

    // ========== Validation ==========

    /**
     * Validates this configuration.
     *
     * @throws IllegalStateException if configuration is invalid
     */
    public void validate() {
        if (totalPartitions <= 0) {
            throw new IllegalStateException("totalPartitions must be positive");
        }
        if (replicationFactor <= 0) {
            throw new IllegalStateException("replicationFactor must be positive");
        }
        if (virtualNodesPerNode <= 0) {
            throw new IllegalStateException("virtualNodesPerNode must be positive");
        }
        if (rebalanceDelay == null || rebalanceDelay.isNegative()) {
            throw new IllegalStateException("rebalanceDelay must be non-negative");
        }
        if (maxConcurrentMoves <= 0) {
            throw new IllegalStateException("maxConcurrentMoves must be positive");
        }

        // Validate tenant configs
        for (Map.Entry<String, PartitionRangeConfig> entry : tenantConfigs.entrySet()) {
            PartitionRangeConfig config = entry.getValue();
            if (config.startPartition() < 0 || config.endPartition() >= totalPartitions) {
                throw new IllegalStateException(
                        "Invalid partition range for tenant " + entry.getKey() + ": " + config);
            }
        }
    }

    // ========== Inner Classes ==========

    /**
     * Configuration for a tenant's dedicated partition range.
     */
    public record PartitionRangeConfig(int startPartition, int endPartition) {
        public PartitionRangeConfig {
            if (startPartition < 0 || endPartition < startPartition) {
                throw new IllegalArgumentException(
                        "Invalid partition range: " + startPartition + "-" + endPartition);
            }
        }

        public int size() {
            return endPartition - startPartition + 1;
        }
    }

    /**
     * Simple round-robin sharding strategy for testing.
     */
    private static class RoundRobinShardingStrategy implements IShardingStrategy {
        private final int totalPartitions;

        public RoundRobinShardingStrategy(int totalPartitions) {
            this.totalPartitions = totalPartitions;
        }

        @Override
        public int computePartition(String workflowExecutionKey, int totalPartitions) {
            return Math.abs(workflowExecutionKey.hashCode()) % totalPartitions;
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
            return String.join("::", organization, module, version, workflowName, executionId);
        }

        @Override
        public java.util.Set<Integer> getAffectedPartitions(String nodeId,
                                                             Map<Integer, String> currentAssignment,
                                                             int totalPartitions) {
            java.util.Set<Integer> affected = new java.util.HashSet<>();
            for (Map.Entry<Integer, String> entry : currentAssignment.entrySet()) {
                if (nodeId.equals(entry.getValue())) {
                    affected.add(entry.getKey());
                }
            }
            return affected;
        }

        @Override
        public String getStrategyName() {
            return "round-robin";
        }

        @Override
        public long computeHash(String key) {
            return key.hashCode();
        }

        @Override
        public int getDefaultTotalPartitions() {
            return totalPartitions;
        }
    }
}
