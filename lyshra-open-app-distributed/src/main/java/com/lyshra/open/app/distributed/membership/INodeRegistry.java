package com.lyshra.open.app.distributed.membership;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Interface for node registration and discovery in the cluster.
 *
 * The NodeRegistry provides:
 * - Node registration and deregistration
 * - Node discovery and lookup
 * - Heartbeat management
 * - Node status tracking
 * - Failure detection
 *
 * Implementations may use various backends:
 * - In-memory (for testing and single-node)
 * - ZooKeeper
 * - etcd
 * - Consul
 * - Database-backed
 *
 * Thread Safety: Implementations must be thread-safe.
 *
 * Usage:
 * <pre>
 * INodeRegistry registry = new InMemoryNodeRegistry();
 * registry.initialize().block();
 *
 * // Register this node
 * NodeIdentity identity = NodeIdentity.generate();
 * RegistrationResult result = registry.register(identity).block();
 *
 * // Discover other nodes
 * List<NodeInfo> activeNodes = registry.getActiveNodes().collectList().block();
 * </pre>
 */
public interface INodeRegistry {

    // ========== Lifecycle ==========

    /**
     * Initializes the registry.
     *
     * @return Mono that completes when initialization is done
     */
    Mono<Void> initialize();

    /**
     * Shuts down the registry.
     *
     * @return Mono that completes when shutdown is done
     */
    Mono<Void> shutdown();

    /**
     * Checks if the registry is initialized and running.
     *
     * @return true if running
     */
    boolean isRunning();

    // ========== Registration ==========

    /**
     * Registers a new node with the cluster.
     *
     * @param identity the node identity
     * @return Mono containing the registration result
     */
    Mono<RegistrationResult> register(NodeIdentity identity);

    /**
     * Registers a new node with additional configuration.
     *
     * @param identity the node identity
     * @param options registration options
     * @return Mono containing the registration result
     */
    Mono<RegistrationResult> register(NodeIdentity identity, RegistrationOptions options);

    /**
     * Deregisters a node from the cluster.
     *
     * @param nodeId the node ID to deregister
     * @return Mono that completes when deregistration is done
     */
    Mono<Void> deregister(String nodeId);

    /**
     * Deregisters a node with a reason.
     *
     * @param nodeId the node ID
     * @param reason the reason for deregistration
     * @return Mono that completes when deregistration is done
     */
    Mono<Void> deregister(String nodeId, String reason);

    /**
     * Checks if a node is registered.
     *
     * @param nodeId the node ID
     * @return Mono containing true if registered
     */
    Mono<Boolean> isRegistered(String nodeId);

    // ========== Heartbeat ==========

    /**
     * Sends a heartbeat for a node.
     *
     * @param nodeId the node ID
     * @return Mono containing true if heartbeat was successful
     */
    Mono<Boolean> heartbeat(String nodeId);

    /**
     * Sends a heartbeat with additional metrics.
     *
     * @param nodeId the node ID
     * @param metrics the metrics to include
     * @return Mono containing true if heartbeat was successful
     */
    Mono<Boolean> heartbeat(String nodeId, HeartbeatMetrics metrics);

    /**
     * Gets the configured heartbeat interval.
     *
     * @return the heartbeat interval
     */
    Duration getHeartbeatInterval();

    /**
     * Gets the configured heartbeat timeout.
     *
     * @return the timeout after which a node is considered failed
     */
    Duration getHeartbeatTimeout();

    // ========== Node Lookup ==========

    /**
     * Gets information about a specific node.
     *
     * @param nodeId the node ID
     * @return Mono containing the node info if found
     */
    Mono<Optional<NodeInfo>> getNode(String nodeId);

    /**
     * Gets information about all registered nodes.
     *
     * @return Flux of all node info
     */
    Flux<NodeInfo> getAllNodes();

    /**
     * Gets information about all active (healthy) nodes.
     *
     * @return Flux of active node info
     */
    Flux<NodeInfo> getActiveNodes();

    /**
     * Gets information about nodes with a specific status.
     *
     * @param status the status to filter by
     * @return Flux of matching node info
     */
    Flux<NodeInfo> getNodesByStatus(NodeStatus status);

    /**
     * Gets information about nodes with specific capabilities.
     *
     * @param capabilities the required capabilities
     * @return Flux of matching node info
     */
    Flux<NodeInfo> getNodesWithCapabilities(Set<String> capabilities);

    /**
     * Gets all node IDs.
     *
     * @return Mono containing set of node IDs
     */
    Mono<Set<String>> getAllNodeIds();

    /**
     * Gets all active node IDs.
     *
     * @return Mono containing set of active node IDs
     */
    Mono<Set<String>> getActiveNodeIds();

    /**
     * Gets the count of registered nodes.
     *
     * @return Mono containing the count
     */
    Mono<Integer> getNodeCount();

    /**
     * Gets the count of active nodes.
     *
     * @return Mono containing the count
     */
    Mono<Integer> getActiveNodeCount();

    // ========== Status Updates ==========

    /**
     * Updates a node's status.
     *
     * @param nodeId the node ID
     * @param newStatus the new status
     * @param reason the reason for the status change
     * @return Mono containing true if update was successful
     */
    Mono<Boolean> updateStatus(String nodeId, NodeStatus newStatus, String reason);

    /**
     * Updates node information.
     *
     * @param nodeId the node ID
     * @param updater function to update the node info
     * @return Mono containing the updated node info
     */
    Mono<NodeInfo> updateNode(String nodeId, java.util.function.Function<NodeInfo, NodeInfo> updater);

    /**
     * Marks a node as draining.
     *
     * @param nodeId the node ID
     * @param reason the reason for draining
     * @return Mono that completes when the status is updated
     */
    Mono<Void> startDraining(String nodeId, String reason);

    /**
     * Marks a node as failed.
     *
     * @param nodeId the node ID
     * @param reason the reason for failure
     * @return Mono that completes when the status is updated
     */
    Mono<Void> markFailed(String nodeId, String reason);

    // ========== Partition Assignment ==========

    /**
     * Assigns a partition to a node.
     *
     * @param nodeId the node ID
     * @param partitionId the partition ID
     * @return Mono containing true if assignment was successful
     */
    Mono<Boolean> assignPartition(String nodeId, int partitionId);

    /**
     * Removes a partition from a node.
     *
     * @param nodeId the node ID
     * @param partitionId the partition ID
     * @return Mono containing true if removal was successful
     */
    Mono<Boolean> removePartition(String nodeId, int partitionId);

    /**
     * Gets the node that owns a partition.
     *
     * @param partitionId the partition ID
     * @return Mono containing the owner node ID
     */
    Mono<Optional<String>> getPartitionOwner(int partitionId);

    /**
     * Gets all partitions owned by a node.
     *
     * @param nodeId the node ID
     * @return Mono containing set of partition IDs
     */
    Mono<Set<Integer>> getNodePartitions(String nodeId);

    // ========== Event Listeners ==========

    /**
     * Adds a listener for node registration events.
     *
     * @param listener the event listener
     */
    void addNodeListener(NodeEventListener listener);

    /**
     * Removes a node event listener.
     *
     * @param listener the listener to remove
     */
    void removeNodeListener(NodeEventListener listener);

    /**
     * Subscribes to node events as a Flux.
     *
     * @return Flux of node events
     */
    Flux<NodeEvent> nodeEvents();

    // ========== Result and Options Classes ==========

    /**
     * Result of a node registration attempt.
     */
    record RegistrationResult(
            boolean success,
            String nodeId,
            NodeInfo nodeInfo,
            String errorMessage,
            long registrationEpoch
    ) {
        public static RegistrationResult success(NodeInfo nodeInfo, long epoch) {
            return new RegistrationResult(true, nodeInfo.getNodeId(), nodeInfo, null, epoch);
        }

        public static RegistrationResult failure(String nodeId, String error) {
            return new RegistrationResult(false, nodeId, null, error, -1);
        }

        public boolean isSuccess() {
            return success;
        }
    }

    /**
     * Options for node registration.
     */
    record RegistrationOptions(
            Set<String> capabilities,
            int maxWorkflows,
            int maxPartitions,
            boolean leaderEligible,
            int leaderPriority,
            double loadWeight,
            java.util.Map<String, String> metadata
    ) {
        public static RegistrationOptions defaults() {
            return new RegistrationOptions(
                    Set.of(),
                    1000,
                    64,
                    true,
                    0,
                    1.0,
                    java.util.Map.of()
            );
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Set<String> capabilities = Set.of();
            private int maxWorkflows = 1000;
            private int maxPartitions = 64;
            private boolean leaderEligible = true;
            private int leaderPriority = 0;
            private double loadWeight = 1.0;
            private java.util.Map<String, String> metadata = java.util.Map.of();

            public Builder capabilities(Set<String> capabilities) {
                this.capabilities = capabilities;
                return this;
            }

            public Builder maxWorkflows(int maxWorkflows) {
                this.maxWorkflows = maxWorkflows;
                return this;
            }

            public Builder maxPartitions(int maxPartitions) {
                this.maxPartitions = maxPartitions;
                return this;
            }

            public Builder leaderEligible(boolean leaderEligible) {
                this.leaderEligible = leaderEligible;
                return this;
            }

            public Builder leaderPriority(int leaderPriority) {
                this.leaderPriority = leaderPriority;
                return this;
            }

            public Builder loadWeight(double loadWeight) {
                this.loadWeight = loadWeight;
                return this;
            }

            public Builder metadata(java.util.Map<String, String> metadata) {
                this.metadata = metadata;
                return this;
            }

            public RegistrationOptions build() {
                return new RegistrationOptions(
                        capabilities, maxWorkflows, maxPartitions,
                        leaderEligible, leaderPriority, loadWeight, metadata
                );
            }
        }
    }

    /**
     * Metrics included in heartbeat.
     */
    record HeartbeatMetrics(
            double cpuLoad,
            double memoryUsage,
            int currentWorkflows,
            int currentPartitions,
            long processedCount,
            long errorCount
    ) {
        public static HeartbeatMetrics empty() {
            return new HeartbeatMetrics(0, 0, 0, 0, 0, 0);
        }
    }

    /**
     * Events emitted by the registry.
     */
    record NodeEvent(
            EventType type,
            String nodeId,
            NodeInfo nodeInfo,
            NodeStatus previousStatus,
            NodeStatus currentStatus,
            String reason,
            java.time.Instant timestamp
    ) {
        public enum EventType {
            REGISTERED,
            DEREGISTERED,
            STATUS_CHANGED,
            HEARTBEAT_RECEIVED,
            HEARTBEAT_MISSED,
            BECAME_ACTIVE,
            BECAME_UNREACHABLE,
            CONFIRMED_FAILED,
            DRAINING_STARTED,
            PARTITION_ASSIGNED,
            PARTITION_REMOVED
        }

        public static NodeEvent registered(NodeInfo info) {
            return new NodeEvent(
                    EventType.REGISTERED,
                    info.getNodeId(),
                    info,
                    null,
                    info.getStatus(),
                    "Node registered",
                    java.time.Instant.now()
            );
        }

        public static NodeEvent deregistered(String nodeId, String reason) {
            return new NodeEvent(
                    EventType.DEREGISTERED,
                    nodeId,
                    null,
                    null,
                    NodeStatus.LEFT,
                    reason,
                    java.time.Instant.now()
            );
        }

        public static NodeEvent statusChanged(NodeInfo info, NodeStatus previousStatus, String reason) {
            return new NodeEvent(
                    EventType.STATUS_CHANGED,
                    info.getNodeId(),
                    info,
                    previousStatus,
                    info.getStatus(),
                    reason,
                    java.time.Instant.now()
            );
        }

        public boolean isFailureEvent() {
            return type == EventType.HEARTBEAT_MISSED ||
                   type == EventType.BECAME_UNREACHABLE ||
                   type == EventType.CONFIRMED_FAILED;
        }
    }

    /**
     * Listener for node events.
     */
    interface NodeEventListener {
        void onNodeEvent(NodeEvent event);
    }
}
