package com.lyshra.open.app.distributed.sharding;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Value object representing a sharding key for workflow distribution.
 *
 * A sharding key can be composed of multiple attributes:
 * - workflowId: The unique workflow identifier
 * - executionId: The specific execution ID
 * - tenantId: The tenant/organization identifier
 * - Custom attributes for advanced sharding scenarios
 *
 * The sharding strategy will use these attributes to determine
 * which partition (and ultimately which node) should handle the workflow.
 *
 * Thread Safety: This class is immutable and thread-safe.
 *
 * Usage:
 * <pre>
 * ShardingKey key = ShardingKey.builder()
 *     .workflowId("order-processing")
 *     .executionId("exec-12345")
 *     .tenantId("tenant-abc")
 *     .build();
 *
 * // Or use factory methods
 * ShardingKey key = ShardingKey.forWorkflow("order-processing", "exec-12345");
 * ShardingKey key = ShardingKey.forTenant("tenant-abc", "order-processing");
 * </pre>
 */
@Getter
@Builder(toBuilder = true)
@ToString
public final class ShardingKey {

    /**
     * The workflow identifier (e.g., workflow definition ID).
     */
    private final String workflowId;

    /**
     * The specific execution ID.
     */
    private final String executionId;

    /**
     * The tenant/organization identifier for multi-tenant scenarios.
     */
    private final String tenantId;

    /**
     * The execution key (typically workflowId:executionId).
     */
    private final String executionKey;

    /**
     * Priority hint for routing (higher priority may get dedicated resources).
     */
    private final int priority;

    /**
     * Custom attributes for advanced sharding scenarios.
     */
    private final Map<String, String> attributes;

    private ShardingKey(String workflowId, String executionId, String tenantId,
                        String executionKey, int priority, Map<String, String> attributes) {
        this.workflowId = workflowId;
        this.executionId = executionId;
        this.tenantId = tenantId;
        this.executionKey = executionKey;
        this.priority = priority;
        this.attributes = attributes != null ? Collections.unmodifiableMap(attributes) : Collections.emptyMap();
    }

    // ========== Factory Methods ==========

    /**
     * Creates a sharding key for a workflow execution.
     *
     * @param workflowId the workflow identifier
     * @param executionId the execution ID
     * @return a new ShardingKey
     */
    public static ShardingKey forWorkflow(String workflowId, String executionId) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Objects.requireNonNull(executionId, "executionId must not be null");

        return ShardingKey.builder()
                .workflowId(workflowId)
                .executionId(executionId)
                .executionKey(workflowId + ":" + executionId)
                .priority(0)
                .build();
    }

    /**
     * Creates a sharding key for a tenant-specific workflow.
     *
     * @param tenantId the tenant identifier
     * @param workflowId the workflow identifier
     * @param executionId the execution ID
     * @return a new ShardingKey
     */
    public static ShardingKey forTenant(String tenantId, String workflowId, String executionId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Objects.requireNonNull(executionId, "executionId must not be null");

        return ShardingKey.builder()
                .tenantId(tenantId)
                .workflowId(workflowId)
                .executionId(executionId)
                .executionKey(tenantId + ":" + workflowId + ":" + executionId)
                .priority(0)
                .build();
    }

    /**
     * Creates a sharding key from an execution key.
     *
     * @param executionKey the execution key
     * @return a new ShardingKey
     */
    public static ShardingKey fromExecutionKey(String executionKey) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");

        return ShardingKey.builder()
                .executionKey(executionKey)
                .priority(0)
                .build();
    }

    /**
     * Creates a sharding key for tenant-only routing.
     *
     * @param tenantId the tenant identifier
     * @return a new ShardingKey
     */
    public static ShardingKey forTenantOnly(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        return ShardingKey.builder()
                .tenantId(tenantId)
                .priority(0)
                .build();
    }

    // ========== Query Methods ==========

    /**
     * Gets the primary key used for hashing.
     * Priority: executionKey > tenantId:workflowId:executionId > workflowId:executionId > tenantId
     *
     * @return the primary key string for hashing
     */
    public String getPrimaryKey() {
        if (executionKey != null && !executionKey.isEmpty()) {
            return executionKey;
        }
        if (tenantId != null && workflowId != null && executionId != null) {
            return tenantId + ":" + workflowId + ":" + executionId;
        }
        if (workflowId != null && executionId != null) {
            return workflowId + ":" + executionId;
        }
        if (tenantId != null) {
            return tenantId;
        }
        if (workflowId != null) {
            return workflowId;
        }
        throw new IllegalStateException("ShardingKey has no valid key components");
    }

    /**
     * Gets the tenant key for tenant-based routing.
     *
     * @return Optional containing the tenant ID
     */
    public Optional<String> getTenantKey() {
        return Optional.ofNullable(tenantId);
    }

    /**
     * Gets a custom attribute value.
     *
     * @param key the attribute key
     * @return Optional containing the attribute value
     */
    public Optional<String> getAttribute(String key) {
        return Optional.ofNullable(attributes.get(key));
    }

    /**
     * Checks if this key has tenant information.
     *
     * @return true if tenant ID is present
     */
    public boolean hasTenant() {
        return tenantId != null && !tenantId.isEmpty();
    }

    /**
     * Checks if this key has workflow information.
     *
     * @return true if workflow ID is present
     */
    public boolean hasWorkflow() {
        return workflowId != null && !workflowId.isEmpty();
    }

    /**
     * Creates a copy with updated priority.
     *
     * @param newPriority the new priority
     * @return a new ShardingKey with updated priority
     */
    public ShardingKey withPriority(int newPriority) {
        return this.toBuilder().priority(newPriority).build();
    }

    /**
     * Creates a copy with an additional attribute.
     *
     * @param key the attribute key
     * @param value the attribute value
     * @return a new ShardingKey with the attribute added
     */
    public ShardingKey withAttribute(String key, String value) {
        Map<String, String> newAttributes = new java.util.HashMap<>(this.attributes);
        newAttributes.put(key, value);
        return this.toBuilder().attributes(newAttributes).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShardingKey that = (ShardingKey) o;
        return Objects.equals(getPrimaryKey(), that.getPrimaryKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPrimaryKey());
    }
}
