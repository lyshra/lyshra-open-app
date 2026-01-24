package com.lyshra.open.app.distributed.membership;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.With;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Comprehensive information about a registered node in the cluster.
 *
 * NodeInfo combines:
 * - Node identity (from NodeIdentity)
 * - Registration state and timing
 * - Health and heartbeat information
 * - Capabilities and capacity
 * - Assignment information (partitions, workflows)
 *
 * Thread Safety: This class is immutable and thread-safe.
 *
 * Usage:
 * <pre>
 * NodeInfo info = NodeInfo.builder()
 *     .identity(NodeIdentity.generate())
 *     .status(NodeStatus.ACTIVE)
 *     .build();
 *
 * if (info.isHealthy() && info.canAcceptWork()) {
 *     // Route work to this node
 * }
 * </pre>
 */
@Getter
@Builder(toBuilder = true)
@ToString
public final class NodeInfo {

    /**
     * The node's identity information.
     */
    private final NodeIdentity identity;

    /**
     * The current status of the node.
     */
    @With
    private final NodeStatus status;

    /**
     * When this node registered with the cluster.
     */
    private final Instant registeredAt;

    /**
     * When this node's registration was last updated.
     */
    @With
    private final Instant lastUpdatedAt;

    /**
     * When the last heartbeat was received from this node.
     */
    @With
    private final Instant lastHeartbeatAt;

    /**
     * Number of consecutive successful heartbeats.
     */
    @With
    private final int consecutiveHeartbeats;

    /**
     * Number of consecutive missed heartbeats.
     */
    @With
    private final int missedHeartbeats;

    /**
     * The heartbeat epoch (incremented each heartbeat).
     */
    @With
    private final long heartbeatEpoch;

    /**
     * Reason for current status (e.g., why draining).
     */
    @With
    private final String statusReason;

    /**
     * When the current status was set.
     */
    @With
    private final Instant statusChangedAt;

    // ========== Capacity Information ==========

    /**
     * Maximum number of workflows this node can handle.
     */
    private final int maxWorkflows;

    /**
     * Current number of workflows assigned to this node.
     */
    @With
    private final int currentWorkflows;

    /**
     * Maximum number of partitions this node can own.
     */
    private final int maxPartitions;

    /**
     * Current number of partitions assigned to this node.
     */
    @With
    private final int currentPartitions;

    /**
     * Set of partition IDs assigned to this node.
     */
    @With
    private final Set<Integer> assignedPartitions;

    /**
     * Current CPU load (0.0 to 1.0).
     */
    @With
    private final double cpuLoad;

    /**
     * Current memory usage (0.0 to 1.0).
     */
    @With
    private final double memoryUsage;

    // ========== Capabilities ==========

    /**
     * Set of capabilities this node supports.
     */
    private final Set<String> capabilities;

    /**
     * Node priority for leadership election (higher = more preferred).
     */
    private final int leaderPriority;

    /**
     * Whether this node can be elected as leader.
     */
    private final boolean leaderEligible;

    /**
     * Weight factor for load balancing (higher = more capacity).
     */
    private final double loadWeight;

    // ========== Metadata ==========

    /**
     * Version information.
     */
    @With
    private final long version;

    /**
     * Custom metadata.
     */
    private final Map<String, String> metadata;

    private NodeInfo(NodeIdentity identity, NodeStatus status, Instant registeredAt,
                    Instant lastUpdatedAt, Instant lastHeartbeatAt, int consecutiveHeartbeats,
                    int missedHeartbeats, long heartbeatEpoch, String statusReason,
                    Instant statusChangedAt, int maxWorkflows, int currentWorkflows,
                    int maxPartitions, int currentPartitions, Set<Integer> assignedPartitions,
                    double cpuLoad, double memoryUsage, Set<String> capabilities,
                    int leaderPriority, boolean leaderEligible, double loadWeight,
                    long version, Map<String, String> metadata) {
        this.identity = Objects.requireNonNull(identity, "identity must not be null");
        this.status = status != null ? status : NodeStatus.STARTING;
        this.registeredAt = registeredAt != null ? registeredAt : Instant.now();
        this.lastUpdatedAt = lastUpdatedAt != null ? lastUpdatedAt : Instant.now();
        this.lastHeartbeatAt = lastHeartbeatAt;
        this.consecutiveHeartbeats = consecutiveHeartbeats;
        this.missedHeartbeats = missedHeartbeats;
        this.heartbeatEpoch = heartbeatEpoch;
        this.statusReason = statusReason;
        this.statusChangedAt = statusChangedAt != null ? statusChangedAt : Instant.now();
        this.maxWorkflows = maxWorkflows > 0 ? maxWorkflows : 1000;
        this.currentWorkflows = currentWorkflows;
        this.maxPartitions = maxPartitions > 0 ? maxPartitions : 64;
        this.currentPartitions = currentPartitions;
        this.assignedPartitions = assignedPartitions != null ?
                Collections.unmodifiableSet(new HashSet<>(assignedPartitions)) :
                Collections.emptySet();
        this.cpuLoad = cpuLoad;
        this.memoryUsage = memoryUsage;
        this.capabilities = capabilities != null ?
                Collections.unmodifiableSet(new HashSet<>(capabilities)) :
                Collections.emptySet();
        this.leaderPriority = leaderPriority;
        this.leaderEligible = leaderEligible;
        this.loadWeight = loadWeight > 0 ? loadWeight : 1.0;
        this.version = version;
        this.metadata = metadata != null ?
                Collections.unmodifiableMap(new HashMap<>(metadata)) :
                Collections.emptyMap();
    }

    // ========== Factory Methods ==========

    /**
     * Creates a new NodeInfo for a freshly registered node.
     *
     * @param identity the node identity
     * @return a new NodeInfo
     */
    public static NodeInfo newNode(NodeIdentity identity) {
        Instant now = Instant.now();
        return NodeInfo.builder()
                .identity(identity)
                .status(NodeStatus.STARTING)
                .registeredAt(now)
                .lastUpdatedAt(now)
                .lastHeartbeatAt(now)
                .statusChangedAt(now)
                .consecutiveHeartbeats(0)
                .missedHeartbeats(0)
                .heartbeatEpoch(0)
                .version(1)
                .leaderEligible(true)
                .loadWeight(1.0)
                .build();
    }

    /**
     * Creates a NodeInfo for testing.
     *
     * @param nodeId the test node ID
     * @return a new NodeInfo for testing
     */
    public static NodeInfo forTesting(String nodeId) {
        return newNode(NodeIdentity.forTesting(nodeId));
    }

    // ========== Convenience Accessors ==========

    /**
     * Gets the node ID.
     *
     * @return the node ID
     */
    public String getNodeId() {
        return identity.getNodeId();
    }

    /**
     * Gets the hostname.
     *
     * @return the hostname
     */
    public String getHostname() {
        return identity.getHostname();
    }

    /**
     * Gets the IP address.
     *
     * @return the IP address
     */
    public String getIpAddress() {
        return identity.getIpAddress();
    }

    /**
     * Gets the port.
     *
     * @return the port
     */
    public int getPort() {
        return identity.getPort();
    }

    /**
     * Gets the full address.
     *
     * @return the address in host:port format
     */
    public String getAddress() {
        return identity.getAddress();
    }

    // ========== Health Queries ==========

    /**
     * Checks if the node is healthy and active.
     *
     * @return true if healthy
     */
    public boolean isHealthy() {
        return status == NodeStatus.ACTIVE &&
               missedHeartbeats == 0;
    }

    /**
     * Checks if the node is available to accept new work.
     *
     * @return true if can accept work
     */
    public boolean canAcceptWork() {
        return status == NodeStatus.ACTIVE &&
               currentWorkflows < maxWorkflows &&
               cpuLoad < 0.9 &&
               memoryUsage < 0.9;
    }

    /**
     * Checks if the node is leaving the cluster.
     *
     * @return true if draining or shutting down
     */
    public boolean isLeaving() {
        return status == NodeStatus.DRAINING ||
               status == NodeStatus.SHUTTING_DOWN ||
               status == NodeStatus.LEFT;
    }

    /**
     * Checks if the node has failed.
     *
     * @return true if failed or unreachable
     */
    public boolean isFailed() {
        return status == NodeStatus.FAILED ||
               status == NodeStatus.UNREACHABLE;
    }

    /**
     * Gets the time since the last heartbeat.
     *
     * @return the duration since last heartbeat
     */
    public Duration getTimeSinceLastHeartbeat() {
        if (lastHeartbeatAt == null) {
            return Duration.ofDays(365); // Never received heartbeat
        }
        return Duration.between(lastHeartbeatAt, Instant.now());
    }

    /**
     * Gets the uptime of this node.
     *
     * @return the uptime duration
     */
    public Duration getUptime() {
        return Duration.between(registeredAt, Instant.now());
    }

    /**
     * Gets the remaining workflow capacity.
     *
     * @return number of additional workflows this node can handle
     */
    public int getRemainingWorkflowCapacity() {
        return Math.max(0, maxWorkflows - currentWorkflows);
    }

    /**
     * Gets the remaining partition capacity.
     *
     * @return number of additional partitions this node can handle
     */
    public int getRemainingPartitionCapacity() {
        return Math.max(0, maxPartitions - currentPartitions);
    }

    /**
     * Gets the current load factor (0.0 to 1.0).
     *
     * @return the load factor
     */
    public double getLoadFactor() {
        double workflowLoad = maxWorkflows > 0 ? (double) currentWorkflows / maxWorkflows : 0;
        double resourceLoad = (cpuLoad + memoryUsage) / 2;
        return Math.max(workflowLoad, resourceLoad);
    }

    // ========== Capability Queries ==========

    /**
     * Checks if this node has a specific capability.
     *
     * @param capability the capability to check
     * @return true if the node has the capability
     */
    public boolean hasCapability(String capability) {
        return capabilities.contains(capability);
    }

    /**
     * Checks if this node has all specified capabilities.
     *
     * @param requiredCapabilities the capabilities to check
     * @return true if the node has all capabilities
     */
    public boolean hasAllCapabilities(Set<String> requiredCapabilities) {
        return capabilities.containsAll(requiredCapabilities);
    }

    /**
     * Checks if this node owns a specific partition.
     *
     * @param partitionId the partition ID
     * @return true if the node owns the partition
     */
    public boolean ownsPartition(int partitionId) {
        return assignedPartitions.contains(partitionId);
    }

    // ========== Status Transitions ==========

    /**
     * Creates a copy marked as active.
     *
     * @return a new NodeInfo with ACTIVE status
     */
    public NodeInfo markActive() {
        return this.toBuilder()
                .status(NodeStatus.ACTIVE)
                .statusReason("Node is active")
                .statusChangedAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .version(version + 1)
                .build();
    }

    /**
     * Creates a copy marked as draining.
     *
     * @param reason the reason for draining
     * @return a new NodeInfo with DRAINING status
     */
    public NodeInfo markDraining(String reason) {
        return this.toBuilder()
                .status(NodeStatus.DRAINING)
                .statusReason(reason)
                .statusChangedAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .version(version + 1)
                .build();
    }

    /**
     * Creates a copy marked as failed.
     *
     * @param reason the reason for failure
     * @return a new NodeInfo with FAILED status
     */
    public NodeInfo markFailed(String reason) {
        return this.toBuilder()
                .status(NodeStatus.FAILED)
                .statusReason(reason)
                .statusChangedAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .version(version + 1)
                .build();
    }

    /**
     * Creates a copy with heartbeat recorded.
     *
     * @return a new NodeInfo with heartbeat updated
     */
    public NodeInfo recordHeartbeat() {
        return this.toBuilder()
                .lastHeartbeatAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .consecutiveHeartbeats(consecutiveHeartbeats + 1)
                .missedHeartbeats(0)
                .heartbeatEpoch(heartbeatEpoch + 1)
                .version(version + 1)
                .build();
    }

    /**
     * Creates a copy with missed heartbeat recorded.
     *
     * @return a new NodeInfo with missed heartbeat
     */
    public NodeInfo recordMissedHeartbeat() {
        return this.toBuilder()
                .missedHeartbeats(missedHeartbeats + 1)
                .consecutiveHeartbeats(0)
                .lastUpdatedAt(Instant.now())
                .version(version + 1)
                .build();
    }

    /**
     * Creates a copy with updated resource metrics.
     *
     * @param cpuLoad the current CPU load
     * @param memoryUsage the current memory usage
     * @return a new NodeInfo with updated metrics
     */
    public NodeInfo withResourceMetrics(double cpuLoad, double memoryUsage) {
        return this.toBuilder()
                .cpuLoad(cpuLoad)
                .memoryUsage(memoryUsage)
                .lastUpdatedAt(Instant.now())
                .version(version + 1)
                .build();
    }

    /**
     * Creates a copy with partition added.
     *
     * @param partitionId the partition to add
     * @return a new NodeInfo with partition added
     */
    public NodeInfo addPartition(int partitionId) {
        Set<Integer> newPartitions = new HashSet<>(assignedPartitions);
        newPartitions.add(partitionId);
        return this.toBuilder()
                .assignedPartitions(newPartitions)
                .currentPartitions(newPartitions.size())
                .lastUpdatedAt(Instant.now())
                .version(version + 1)
                .build();
    }

    /**
     * Creates a copy with partition removed.
     *
     * @param partitionId the partition to remove
     * @return a new NodeInfo with partition removed
     */
    public NodeInfo removePartition(int partitionId) {
        Set<Integer> newPartitions = new HashSet<>(assignedPartitions);
        newPartitions.remove(partitionId);
        return this.toBuilder()
                .assignedPartitions(newPartitions)
                .currentPartitions(newPartitions.size())
                .lastUpdatedAt(Instant.now())
                .version(version + 1)
                .build();
    }

    /**
     * Gets metadata value.
     *
     * @param key the metadata key
     * @return Optional containing the value
     */
    public Optional<String> getMetadata(String key) {
        return Optional.ofNullable(metadata.get(key));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeInfo nodeInfo = (NodeInfo) o;
        return Objects.equals(identity, nodeInfo.identity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identity);
    }
}
