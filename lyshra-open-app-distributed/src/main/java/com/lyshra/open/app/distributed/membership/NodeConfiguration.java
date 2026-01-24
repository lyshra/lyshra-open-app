package com.lyshra.open.app.distributed.membership;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Configuration for node registration and behavior.
 *
 * This class centralizes all node-related configuration including:
 * - Identity configuration (prefix, port)
 * - Capacity settings (max workflows, partitions)
 * - Heartbeat and timeout settings
 * - Capability declarations
 * - Leadership settings
 *
 * Thread Safety: This class is immutable and thread-safe.
 *
 * Usage:
 * <pre>
 * NodeConfiguration config = NodeConfiguration.builder()
 *     .nodeIdPrefix("workflow-engine")
 *     .port(8080)
 *     .maxWorkflows(1000)
 *     .heartbeatInterval(Duration.ofSeconds(5))
 *     .capability("workflow-execution")
 *     .capability("step-processing")
 *     .build();
 * </pre>
 */
@Getter
@Builder(toBuilder = true)
@ToString
public final class NodeConfiguration {

    // ========== Identity Configuration ==========

    /**
     * Prefix for auto-generated node IDs.
     */
    @Builder.Default
    private final String nodeIdPrefix = "node";

    /**
     * The port this node listens on (0 if not applicable).
     */
    @Builder.Default
    private final int port = 0;

    /**
     * The datacenter this node is in (for topology-aware routing).
     */
    private final String datacenter;

    /**
     * The availability zone this node is in.
     */
    private final String availabilityZone;

    /**
     * The rack this node is in (for rack-aware placement).
     */
    private final String rack;

    // ========== Capacity Configuration ==========

    /**
     * Maximum number of workflows this node can handle concurrently.
     */
    @Builder.Default
    private final int maxWorkflows = 1000;

    /**
     * Maximum number of partitions this node can own.
     */
    @Builder.Default
    private final int maxPartitions = 64;

    /**
     * Weight factor for load balancing (higher = more capacity).
     */
    @Builder.Default
    private final double loadWeight = 1.0;

    // ========== Heartbeat Configuration ==========

    /**
     * Interval between heartbeats sent to the registry.
     */
    @Builder.Default
    private final Duration heartbeatInterval = Duration.ofSeconds(5);

    /**
     * Timeout after which a node is considered failed if no heartbeat received.
     */
    @Builder.Default
    private final Duration heartbeatTimeout = Duration.ofSeconds(30);

    /**
     * Number of consecutive missed heartbeats before a node is marked failed.
     */
    @Builder.Default
    private final int maxMissedHeartbeats = 3;

    // ========== Shutdown Configuration ==========

    /**
     * Maximum time to wait for graceful shutdown (draining).
     */
    @Builder.Default
    private final Duration shutdownTimeout = Duration.ofMinutes(5);

    /**
     * Whether to deregister immediately on shutdown or wait for draining.
     */
    @Builder.Default
    private final boolean gracefulShutdown = true;

    // ========== Leadership Configuration ==========

    /**
     * Whether this node can be elected as leader.
     */
    @Builder.Default
    private final boolean leaderEligible = true;

    /**
     * Priority for leadership election (higher = more preferred).
     */
    @Builder.Default
    private final int leaderPriority = 0;

    // ========== Capability Configuration ==========

    /**
     * Set of capabilities this node supports.
     */
    @Builder.Default
    private final Set<String> capabilities = Collections.emptySet();

    // ========== Metadata ==========

    /**
     * Custom metadata for this node.
     */
    @Builder.Default
    private final Map<String, String> metadata = Collections.emptyMap();

    // ========== Factory Methods ==========

    /**
     * Creates a default configuration.
     *
     * @return default configuration
     */
    public static NodeConfiguration defaults() {
        return NodeConfiguration.builder().build();
    }

    /**
     * Creates a configuration for testing.
     *
     * @return test configuration with short timeouts
     */
    public static NodeConfiguration forTesting() {
        return NodeConfiguration.builder()
                .nodeIdPrefix("test-node")
                .heartbeatInterval(Duration.ofMillis(100))
                .heartbeatTimeout(Duration.ofMillis(500))
                .maxMissedHeartbeats(2)
                .shutdownTimeout(Duration.ofSeconds(5))
                .maxWorkflows(100)
                .maxPartitions(16)
                .build();
    }

    /**
     * Creates a production-ready configuration.
     *
     * @param nodeIdPrefix the node ID prefix
     * @param port the port number
     * @return production configuration
     */
    public static NodeConfiguration production(String nodeIdPrefix, int port) {
        return NodeConfiguration.builder()
                .nodeIdPrefix(nodeIdPrefix)
                .port(port)
                .heartbeatInterval(Duration.ofSeconds(5))
                .heartbeatTimeout(Duration.ofSeconds(30))
                .maxMissedHeartbeats(3)
                .shutdownTimeout(Duration.ofMinutes(5))
                .maxWorkflows(1000)
                .maxPartitions(64)
                .gracefulShutdown(true)
                .build();
    }

    /**
     * Creates a worker node configuration (not leader eligible).
     *
     * @param nodeIdPrefix the node ID prefix
     * @return worker configuration
     */
    public static NodeConfiguration worker(String nodeIdPrefix) {
        return NodeConfiguration.builder()
                .nodeIdPrefix(nodeIdPrefix)
                .leaderEligible(false)
                .capability("workflow-execution")
                .build();
    }

    // ========== Builder Enhancements ==========

    public static class NodeConfigurationBuilder {
        // Explicitly declare fields for custom builder methods
        private Set<String> capabilities$value;
        private boolean capabilities$set;
        private Map<String, String> metadata$value;
        private boolean metadata$set;
        private String datacenter;
        private String availabilityZone;
        private String rack;

        /**
         * Adds a capability to the set.
         *
         * @param capability the capability to add
         * @return this builder
         */
        public NodeConfigurationBuilder capability(String capability) {
            if (!this.capabilities$set || this.capabilities$value == null || this.capabilities$value.isEmpty()) {
                this.capabilities$value = new HashSet<>();
                this.capabilities$set = true;
            }
            // Ensure it's mutable
            if (!(this.capabilities$value instanceof HashSet)) {
                this.capabilities$value = new HashSet<>(this.capabilities$value);
            }
            this.capabilities$value.add(capability);
            return this;
        }

        /**
         * Adds a metadata entry.
         *
         * @param key the metadata key
         * @param value the metadata value
         * @return this builder
         */
        public NodeConfigurationBuilder metadataEntry(String key, String value) {
            if (!this.metadata$set || this.metadata$value == null || this.metadata$value.isEmpty()) {
                this.metadata$value = new HashMap<>();
                this.metadata$set = true;
            }
            // Ensure it's mutable
            if (!(this.metadata$value instanceof HashMap)) {
                this.metadata$value = new HashMap<>(this.metadata$value);
            }
            this.metadata$value.put(key, value);
            return this;
        }

        /**
         * Sets location information.
         *
         * @param datacenter the datacenter
         * @param availabilityZone the availability zone
         * @return this builder
         */
        public NodeConfigurationBuilder location(String datacenter, String availabilityZone) {
            this.datacenter = datacenter;
            this.availabilityZone = availabilityZone;
            return this;
        }

        /**
         * Sets location information with rack.
         *
         * @param datacenter the datacenter
         * @param availabilityZone the availability zone
         * @param rack the rack
         * @return this builder
         */
        public NodeConfigurationBuilder location(String datacenter, String availabilityZone, String rack) {
            this.datacenter = datacenter;
            this.availabilityZone = availabilityZone;
            this.rack = rack;
            return this;
        }
    }

    // ========== Validation ==========

    /**
     * Validates this configuration.
     *
     * @throws IllegalStateException if configuration is invalid
     */
    public void validate() {
        if (nodeIdPrefix == null || nodeIdPrefix.isEmpty()) {
            throw new IllegalStateException("nodeIdPrefix must not be empty");
        }
        if (maxWorkflows <= 0) {
            throw new IllegalStateException("maxWorkflows must be positive");
        }
        if (maxPartitions <= 0) {
            throw new IllegalStateException("maxPartitions must be positive");
        }
        if (loadWeight <= 0) {
            throw new IllegalStateException("loadWeight must be positive");
        }
        if (heartbeatInterval == null || heartbeatInterval.isNegative() || heartbeatInterval.isZero()) {
            throw new IllegalStateException("heartbeatInterval must be positive");
        }
        if (heartbeatTimeout == null || heartbeatTimeout.isNegative() || heartbeatTimeout.isZero()) {
            throw new IllegalStateException("heartbeatTimeout must be positive");
        }
        if (heartbeatTimeout.compareTo(heartbeatInterval) <= 0) {
            throw new IllegalStateException("heartbeatTimeout must be greater than heartbeatInterval");
        }
        if (maxMissedHeartbeats <= 0) {
            throw new IllegalStateException("maxMissedHeartbeats must be positive");
        }
        if (shutdownTimeout == null || shutdownTimeout.isNegative()) {
            throw new IllegalStateException("shutdownTimeout must not be negative");
        }
    }

    // ========== Computed Properties ==========

    /**
     * Gets the effective heartbeat deadline (when a node should be considered suspect).
     *
     * @return the suspect deadline duration
     */
    public Duration getSuspectDeadline() {
        return heartbeatInterval.multipliedBy(maxMissedHeartbeats - 1);
    }

    /**
     * Gets the effective failure deadline (when a node should be marked failed).
     *
     * @return the failure deadline duration
     */
    public Duration getFailureDeadline() {
        return heartbeatInterval.multipliedBy(maxMissedHeartbeats);
    }

    /**
     * Checks if this configuration has location information.
     *
     * @return true if location is configured
     */
    public boolean hasLocation() {
        return datacenter != null || availabilityZone != null || rack != null;
    }

    /**
     * Gets the location string (datacenter/zone/rack).
     *
     * @return the location string
     */
    public String getLocationString() {
        StringBuilder sb = new StringBuilder();
        if (datacenter != null) {
            sb.append(datacenter);
        }
        if (availabilityZone != null) {
            if (sb.length() > 0) sb.append("/");
            sb.append(availabilityZone);
        }
        if (rack != null) {
            if (sb.length() > 0) sb.append("/");
            sb.append(rack);
        }
        return sb.length() > 0 ? sb.toString() : "unknown";
    }
}
