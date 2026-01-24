package com.lyshra.open.app.distributed.sharding.impl;

import com.lyshra.open.app.distributed.sharding.IShardingStrategy;
import com.lyshra.open.app.distributed.sharding.PartitionAssignment;
import com.lyshra.open.app.distributed.sharding.ShardingKey;
import com.lyshra.open.app.distributed.sharding.ShardingResult;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tenant-aware sharding strategy for multi-tenant workflow systems.
 *
 * This strategy provides tenant isolation by:
 * 1. Dedicated partitions: Large tenants can have dedicated partition ranges
 * 2. Tenant grouping: Small tenants share partitions based on tenant ID hash
 * 3. Sticky routing: All workflows for a tenant go to the same partition subset
 *
 * Benefits:
 * - Tenant isolation for performance and security
 * - Predictable routing for tenant-specific operations
 * - Support for tenant-level scaling (dedicated resources)
 * - Fair distribution among shared tenants
 *
 * Configuration:
 * - Dedicated tenants: Tenants with reserved partition ranges
 * - Shared pool: Remaining partitions for smaller tenants
 * - Partition affinity: Number of partitions per tenant (for load spreading)
 *
 * Thread Safety: This class is thread-safe using ConcurrentHashMap.
 *
 * Usage:
 * <pre>
 * TenantAwareShardingStrategy strategy = TenantAwareShardingStrategy.builder()
 *     .totalPartitions(64)
 *     .dedicatedTenant("enterprise-tenant", 0, 15)  // Partitions 0-15
 *     .dedicatedTenant("premium-tenant", 16, 23)    // Partitions 16-23
 *     .sharedPartitionRange(24, 63)                 // Remaining for others
 *     .build();
 *
 * ShardingKey key = ShardingKey.forTenant("small-tenant", "workflow", "exec-1");
 * ShardingResult result = strategy.shard(key, 64);
 * </pre>
 */
@Slf4j
public class TenantAwareShardingStrategy implements IShardingStrategy {

    private static final String STRATEGY_NAME = "tenant-aware";
    private static final String KEY_SEPARATOR = "::";
    private static final int HASH_SEED = 0x9747b28c;

    @Getter
    private final int defaultTotalPartitions;

    // Dedicated tenant mappings: tenantId -> partition range
    private final Map<String, PartitionRange> dedicatedTenants;

    // Shared partition pool for non-dedicated tenants
    private final int sharedPartitionStart;
    private final int sharedPartitionEnd;

    // Tenant partition affinity (number of partitions per tenant for load spreading)
    private final int partitionsPerTenant;

    // Fallback strategy for non-tenant keys
    private final ConsistentHashShardingStrategy fallbackStrategy;

    private TenantAwareShardingStrategy(Builder builder) {
        this.defaultTotalPartitions = builder.totalPartitions;
        this.dedicatedTenants = new ConcurrentHashMap<>(builder.dedicatedTenants);
        this.sharedPartitionStart = builder.sharedPartitionStart;
        this.sharedPartitionEnd = builder.sharedPartitionEnd;
        this.partitionsPerTenant = builder.partitionsPerTenant;
        this.fallbackStrategy = new ConsistentHashShardingStrategy(defaultTotalPartitions);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a simple tenant-aware strategy with default settings.
     *
     * @param totalPartitions the total number of partitions
     * @return a new TenantAwareShardingStrategy
     */
    public static TenantAwareShardingStrategy simple(int totalPartitions) {
        return builder()
                .totalPartitions(totalPartitions)
                .sharedPartitionRange(0, totalPartitions - 1)
                .build();
    }

    @Override
    public int computePartition(String workflowExecutionKey, int totalPartitions) {
        Objects.requireNonNull(workflowExecutionKey, "workflowExecutionKey must not be null");
        if (totalPartitions <= 0) {
            throw new IllegalArgumentException("totalPartitions must be positive");
        }

        // Try to extract tenant from key (format: tenantId::workflowId::executionId)
        String tenantId = extractTenantId(workflowExecutionKey);
        if (tenantId != null) {
            return computePartitionForTenant(tenantId, workflowExecutionKey, totalPartitions);
        }

        // Fall back to consistent hashing
        return fallbackStrategy.computePartition(workflowExecutionKey, totalPartitions);
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

        return String.join(KEY_SEPARATOR, organization, module, version, workflowName, executionId);
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
        return fallbackStrategy.computeHash(key);
    }

    @Override
    public ShardingResult shard(ShardingKey key, int totalPartitions) {
        Objects.requireNonNull(key, "key must not be null");
        if (totalPartitions <= 0) {
            throw new IllegalArgumentException("totalPartitions must be positive");
        }

        String tenantId = key.getTenantKey().orElse(null);
        long hash = computeHash(key.getPrimaryKey());

        int partition;
        Map<String, String> metadata = new HashMap<>();

        if (tenantId != null) {
            partition = computePartitionForTenant(tenantId, key.getPrimaryKey(), totalPartitions);
            metadata.put("tenantId", tenantId);

            // Check if dedicated or shared
            if (dedicatedTenants.containsKey(tenantId)) {
                metadata.put("partitionType", "dedicated");
                PartitionRange range = dedicatedTenants.get(tenantId);
                metadata.put("partitionRange", range.start + "-" + range.end);
            } else {
                metadata.put("partitionType", "shared");
            }
        } else {
            // No tenant - use fallback strategy
            partition = fallbackStrategy.computePartition(key.getPrimaryKey(), totalPartitions);
            metadata.put("partitionType", "default");
        }

        return ShardingResult.builder()
                .partitionId(partition)
                .hashValue(hash)
                .strategyName(STRATEGY_NAME)
                .shardingKey(key)
                .metadata(metadata)
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

    // ========== Tenant-Specific Methods ==========

    /**
     * Computes the partition for a specific tenant.
     */
    private int computePartitionForTenant(String tenantId, String key, int totalPartitions) {
        // Check for dedicated tenant partition range
        PartitionRange dedicated = dedicatedTenants.get(tenantId);
        if (dedicated != null) {
            // Hash within the dedicated range
            long hash = computeHash(key);
            int rangeSize = dedicated.end - dedicated.start + 1;
            int offset = (int) (Math.abs(hash) % rangeSize);
            return dedicated.start + offset;
        }

        // Use shared partition pool
        int sharedRangeSize = sharedPartitionEnd - sharedPartitionStart + 1;
        if (sharedRangeSize <= 0) {
            // No shared pool - fall back to all partitions
            return fallbackStrategy.computePartition(key, totalPartitions);
        }

        // Two-level hashing:
        // 1. Hash tenant to get a "home" partition subset
        // 2. Hash key within that subset for load spreading
        long tenantHash = computeHash(tenantId);
        int tenantHomeOffset = (int) (Math.abs(tenantHash) % (sharedRangeSize / Math.max(1, partitionsPerTenant)));
        int tenantHomeStart = sharedPartitionStart + (tenantHomeOffset * partitionsPerTenant);

        // Ensure we don't exceed the shared range
        int actualPartitionsForTenant = Math.min(partitionsPerTenant, sharedPartitionEnd - tenantHomeStart + 1);
        if (actualPartitionsForTenant <= 0) {
            actualPartitionsForTenant = 1;
            tenantHomeStart = sharedPartitionStart;
        }

        // Hash key within tenant's partition subset
        long keyHash = computeHash(key);
        int partitionOffset = (int) (Math.abs(keyHash) % actualPartitionsForTenant);

        return tenantHomeStart + partitionOffset;
    }

    /**
     * Extracts tenant ID from a composite key (if present).
     */
    private String extractTenantId(String key) {
        if (key == null || !key.contains(KEY_SEPARATOR)) {
            return null;
        }
        // Assume first component is tenant ID
        int sepIndex = key.indexOf(KEY_SEPARATOR);
        return key.substring(0, sepIndex);
    }

    /**
     * Registers a dedicated tenant with a reserved partition range.
     *
     * @param tenantId the tenant ID
     * @param startPartition the start of the partition range (inclusive)
     * @param endPartition the end of the partition range (inclusive)
     */
    public void registerDedicatedTenant(String tenantId, int startPartition, int endPartition) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (startPartition < 0 || endPartition >= defaultTotalPartitions || startPartition > endPartition) {
            throw new IllegalArgumentException("Invalid partition range: " + startPartition + "-" + endPartition);
        }

        dedicatedTenants.put(tenantId, new PartitionRange(startPartition, endPartition));
        log.info("Registered dedicated tenant {} with partition range {}-{}", tenantId, startPartition, endPartition);
    }

    /**
     * Removes a dedicated tenant registration.
     *
     * @param tenantId the tenant ID
     */
    public void unregisterDedicatedTenant(String tenantId) {
        dedicatedTenants.remove(tenantId);
        log.info("Unregistered dedicated tenant {}", tenantId);
    }

    /**
     * Checks if a tenant has dedicated partitions.
     *
     * @param tenantId the tenant ID
     * @return true if the tenant has dedicated partitions
     */
    public boolean isDedicatedTenant(String tenantId) {
        return dedicatedTenants.containsKey(tenantId);
    }

    /**
     * Gets the partition range for a dedicated tenant.
     *
     * @param tenantId the tenant ID
     * @return Optional containing the partition range
     */
    public Optional<PartitionRange> getDedicatedPartitionRange(String tenantId) {
        return Optional.ofNullable(dedicatedTenants.get(tenantId));
    }

    /**
     * Gets all dedicated tenants.
     *
     * @return unmodifiable map of tenant ID to partition range
     */
    public Map<String, PartitionRange> getDedicatedTenants() {
        return Collections.unmodifiableMap(new HashMap<>(dedicatedTenants));
    }

    /**
     * Gets all partitions that could be used by a tenant.
     *
     * @param tenantId the tenant ID
     * @return set of partition IDs
     */
    public Set<Integer> getPartitionsForTenant(String tenantId) {
        PartitionRange dedicated = dedicatedTenants.get(tenantId);
        if (dedicated != null) {
            Set<Integer> partitions = new HashSet<>();
            for (int i = dedicated.start; i <= dedicated.end; i++) {
                partitions.add(i);
            }
            return partitions;
        }

        // For shared tenants, return all shared partitions
        Set<Integer> sharedPartitions = new HashSet<>();
        for (int i = sharedPartitionStart; i <= sharedPartitionEnd; i++) {
            sharedPartitions.add(i);
        }
        return sharedPartitions;
    }

    // ========== Inner Classes ==========

    /**
     * Represents a range of partitions.
     */
    public record PartitionRange(int start, int end) {
        public PartitionRange {
            if (start < 0 || end < start) {
                throw new IllegalArgumentException("Invalid partition range: " + start + "-" + end);
            }
        }

        public int size() {
            return end - start + 1;
        }

        public boolean contains(int partition) {
            return partition >= start && partition <= end;
        }
    }

    // ========== Builder ==========

    public static class Builder {
        private int totalPartitions = 64;
        private final Map<String, PartitionRange> dedicatedTenants = new HashMap<>();
        private int sharedPartitionStart = 0;
        private int sharedPartitionEnd = 63;
        private int partitionsPerTenant = 4;

        public Builder totalPartitions(int totalPartitions) {
            if (totalPartitions <= 0) {
                throw new IllegalArgumentException("totalPartitions must be positive");
            }
            this.totalPartitions = totalPartitions;
            this.sharedPartitionEnd = totalPartitions - 1;
            return this;
        }

        public Builder dedicatedTenant(String tenantId, int startPartition, int endPartition) {
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            dedicatedTenants.put(tenantId, new PartitionRange(startPartition, endPartition));
            return this;
        }

        public Builder sharedPartitionRange(int start, int end) {
            this.sharedPartitionStart = start;
            this.sharedPartitionEnd = end;
            return this;
        }

        public Builder partitionsPerTenant(int partitionsPerTenant) {
            if (partitionsPerTenant <= 0) {
                throw new IllegalArgumentException("partitionsPerTenant must be positive");
            }
            this.partitionsPerTenant = partitionsPerTenant;
            return this;
        }

        public TenantAwareShardingStrategy build() {
            return new TenantAwareShardingStrategy(this);
        }
    }
}
