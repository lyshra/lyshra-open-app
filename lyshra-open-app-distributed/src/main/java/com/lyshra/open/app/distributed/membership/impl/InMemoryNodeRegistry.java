package com.lyshra.open.app.distributed.membership.impl;

import com.lyshra.open.app.distributed.membership.*;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * In-memory implementation of INodeRegistry for testing and single-node deployments.
 *
 * Features:
 * - Thread-safe node registration and lookup
 * - Heartbeat tracking with configurable timeouts
 * - Event emission for node state changes
 * - Partition assignment tracking
 *
 * Limitations:
 * - Not distributed (only works for single JVM)
 * - State is lost on restart
 *
 * For production distributed deployments, use ZooKeeperNodeRegistry
 * or similar distributed implementations.
 *
 * Thread Safety: This class is thread-safe using ConcurrentHashMap.
 *
 * Usage:
 * <pre>
 * InMemoryNodeRegistry registry = new InMemoryNodeRegistry();
 * registry.initialize().block();
 *
 * NodeIdentity identity = NodeIdentity.generate();
 * registry.register(identity).block();
 * </pre>
 */
@Slf4j
public class InMemoryNodeRegistry implements INodeRegistry {

    private final ConcurrentHashMap<String, NodeInfo> nodes;
    private final ConcurrentHashMap<Integer, String> partitionOwners;
    private final List<NodeEventListener> listeners;
    private final Sinks.Many<NodeEvent> eventSink;
    private final AtomicLong registrationEpoch;
    private final AtomicBoolean running;

    private Duration heartbeatInterval;
    private Duration heartbeatTimeout;
    private int maxMissedHeartbeats;

    public InMemoryNodeRegistry() {
        this(Duration.ofSeconds(5), Duration.ofSeconds(30), 3);
    }

    public InMemoryNodeRegistry(Duration heartbeatInterval, Duration heartbeatTimeout, int maxMissedHeartbeats) {
        this.nodes = new ConcurrentHashMap<>();
        this.partitionOwners = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.eventSink = Sinks.many().multicast().onBackpressureBuffer();
        this.registrationEpoch = new AtomicLong(0);
        this.running = new AtomicBoolean(false);
        this.heartbeatInterval = heartbeatInterval;
        this.heartbeatTimeout = heartbeatTimeout;
        this.maxMissedHeartbeats = maxMissedHeartbeats;
    }

    // ========== Lifecycle ==========

    @Override
    public Mono<Void> initialize() {
        return Mono.fromRunnable(() -> {
            if (running.compareAndSet(false, true)) {
                log.info("InMemoryNodeRegistry initialized");
            } else {
                log.warn("InMemoryNodeRegistry already initialized");
            }
        });
    }

    @Override
    public Mono<Void> shutdown() {
        return Mono.fromRunnable(() -> {
            if (running.compareAndSet(true, false)) {
                // Deregister all nodes
                nodes.keySet().forEach(nodeId -> {
                    try {
                        deregister(nodeId, "Registry shutdown").block();
                    } catch (Exception e) {
                        log.warn("Error deregistering node {} during shutdown", nodeId, e);
                    }
                });
                nodes.clear();
                partitionOwners.clear();
                eventSink.tryEmitComplete();
                log.info("InMemoryNodeRegistry shut down");
            }
        });
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    // ========== Registration ==========

    @Override
    public Mono<RegistrationResult> register(NodeIdentity identity) {
        return register(identity, RegistrationOptions.defaults());
    }

    @Override
    public Mono<RegistrationResult> register(NodeIdentity identity, RegistrationOptions options) {
        Objects.requireNonNull(identity, "identity must not be null");

        return Mono.fromCallable(() -> {
            if (!running.get()) {
                return RegistrationResult.failure(identity.getNodeId(), "Registry is not running");
            }

            String nodeId = identity.getNodeId();

            // Check for existing registration
            if (nodes.containsKey(nodeId)) {
                log.warn("Node {} is already registered", nodeId);
                return RegistrationResult.failure(nodeId, "Node is already registered");
            }

            // Create NodeInfo with options
            Instant now = Instant.now();
            NodeInfo nodeInfo = NodeInfo.builder()
                    .identity(identity)
                    .status(NodeStatus.STARTING)
                    .registeredAt(now)
                    .lastUpdatedAt(now)
                    .lastHeartbeatAt(now)
                    .statusChangedAt(now)
                    .consecutiveHeartbeats(0)
                    .missedHeartbeats(0)
                    .heartbeatEpoch(0)
                    .maxWorkflows(options.maxWorkflows())
                    .maxPartitions(options.maxPartitions())
                    .capabilities(options.capabilities())
                    .leaderEligible(options.leaderEligible())
                    .leaderPriority(options.leaderPriority())
                    .loadWeight(options.loadWeight())
                    .metadata(options.metadata())
                    .version(1)
                    .build();

            nodes.put(nodeId, nodeInfo);
            long epoch = registrationEpoch.incrementAndGet();

            // Emit event
            emitEvent(NodeEvent.registered(nodeInfo));

            log.info("Registered node {} (epoch: {})", nodeId, epoch);
            return RegistrationResult.success(nodeInfo, epoch);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> deregister(String nodeId) {
        return deregister(nodeId, "Node deregistered");
    }

    @Override
    public Mono<Void> deregister(String nodeId, String reason) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        return Mono.fromRunnable(() -> {
            NodeInfo removed = nodes.remove(nodeId);
            if (removed != null) {
                // Remove partition assignments
                removed.getAssignedPartitions().forEach(p -> partitionOwners.remove(p));

                // Emit event
                emitEvent(NodeEvent.deregistered(nodeId, reason));

                log.info("Deregistered node {} (reason: {})", nodeId, reason);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Boolean> isRegistered(String nodeId) {
        return Mono.just(nodes.containsKey(nodeId));
    }

    // ========== Heartbeat ==========

    @Override
    public Mono<Boolean> heartbeat(String nodeId) {
        return heartbeat(nodeId, HeartbeatMetrics.empty());
    }

    @Override
    public Mono<Boolean> heartbeat(String nodeId, HeartbeatMetrics metrics) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        return Mono.fromCallable(() -> {
            NodeInfo existing = nodes.get(nodeId);
            if (existing == null) {
                log.warn("Heartbeat from unknown node: {}", nodeId);
                return false;
            }

            // Update node info with heartbeat and metrics
            NodeInfo updated = existing
                    .recordHeartbeat()
                    .withResourceMetrics(metrics.cpuLoad(), metrics.memoryUsage())
                    .withCurrentWorkflows(metrics.currentWorkflows())
                    .withCurrentPartitions(metrics.currentPartitions());

            // If node was starting, transition to active
            if (existing.getStatus() == NodeStatus.STARTING ||
                existing.getStatus() == NodeStatus.JOINING) {
                updated = updated.markActive();
                emitEvent(NodeEvent.statusChanged(updated, existing.getStatus(), "Node became active"));
            }

            // If node was unreachable, transition to active
            if (existing.getStatus() == NodeStatus.UNREACHABLE) {
                updated = updated.markActive();
                emitEvent(NodeEvent.statusChanged(updated, NodeStatus.UNREACHABLE, "Node recovered"));
            }

            nodes.put(nodeId, updated);

            log.debug("Heartbeat received from node {} (epoch: {})", nodeId, updated.getHeartbeatEpoch());
            return true;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    @Override
    public Duration getHeartbeatTimeout() {
        return heartbeatTimeout;
    }

    // ========== Node Lookup ==========

    @Override
    public Mono<Optional<NodeInfo>> getNode(String nodeId) {
        return Mono.just(Optional.ofNullable(nodes.get(nodeId)));
    }

    @Override
    public Flux<NodeInfo> getAllNodes() {
        return Flux.fromIterable(new ArrayList<>(nodes.values()));
    }

    @Override
    public Flux<NodeInfo> getActiveNodes() {
        return Flux.fromIterable(nodes.values())
                .filter(node -> node.getStatus() == NodeStatus.ACTIVE);
    }

    @Override
    public Flux<NodeInfo> getNodesByStatus(NodeStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        return Flux.fromIterable(nodes.values())
                .filter(node -> node.getStatus() == status);
    }

    @Override
    public Flux<NodeInfo> getNodesWithCapabilities(Set<String> capabilities) {
        Objects.requireNonNull(capabilities, "capabilities must not be null");
        return Flux.fromIterable(nodes.values())
                .filter(node -> node.hasAllCapabilities(capabilities));
    }

    @Override
    public Mono<Set<String>> getAllNodeIds() {
        return Mono.just(new HashSet<>(nodes.keySet()));
    }

    @Override
    public Mono<Set<String>> getActiveNodeIds() {
        return Mono.fromCallable(() -> {
            Set<String> active = new HashSet<>();
            for (NodeInfo node : nodes.values()) {
                if (node.getStatus() == NodeStatus.ACTIVE) {
                    active.add(node.getNodeId());
                }
            }
            return active;
        });
    }

    @Override
    public Mono<Integer> getNodeCount() {
        return Mono.just(nodes.size());
    }

    @Override
    public Mono<Integer> getActiveNodeCount() {
        return Mono.fromCallable(() -> (int) nodes.values().stream()
                .filter(node -> node.getStatus() == NodeStatus.ACTIVE)
                .count());
    }

    // ========== Status Updates ==========

    @Override
    public Mono<Boolean> updateStatus(String nodeId, NodeStatus newStatus, String reason) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(newStatus, "newStatus must not be null");

        return Mono.fromCallable(() -> {
            NodeInfo existing = nodes.get(nodeId);
            if (existing == null) {
                return false;
            }

            if (!existing.getStatus().canTransitionTo(newStatus)) {
                log.warn("Invalid status transition for node {}: {} -> {}",
                        nodeId, existing.getStatus(), newStatus);
                return false;
            }

            NodeStatus previousStatus = existing.getStatus();
            NodeInfo updated = existing.toBuilder()
                    .status(newStatus)
                    .statusReason(reason)
                    .statusChangedAt(Instant.now())
                    .lastUpdatedAt(Instant.now())
                    .version(existing.getVersion() + 1)
                    .build();

            nodes.put(nodeId, updated);
            emitEvent(NodeEvent.statusChanged(updated, previousStatus, reason));

            log.info("Node {} status changed: {} -> {} (reason: {})",
                    nodeId, previousStatus, newStatus, reason);
            return true;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<NodeInfo> updateNode(String nodeId, Function<NodeInfo, NodeInfo> updater) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(updater, "updater must not be null");

        return Mono.fromCallable(() -> {
            NodeInfo existing = nodes.get(nodeId);
            if (existing == null) {
                return null;
            }

            NodeInfo updated = updater.apply(existing);
            nodes.put(nodeId, updated);

            return updated;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> startDraining(String nodeId, String reason) {
        return updateStatus(nodeId, NodeStatus.DRAINING, reason).then();
    }

    @Override
    public Mono<Void> markFailed(String nodeId, String reason) {
        return updateStatus(nodeId, NodeStatus.FAILED, reason).then();
    }

    // ========== Partition Assignment ==========

    @Override
    public Mono<Boolean> assignPartition(String nodeId, int partitionId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        return Mono.fromCallable(() -> {
            NodeInfo existing = nodes.get(nodeId);
            if (existing == null) {
                return false;
            }

            // Check if partition is already assigned
            String currentOwner = partitionOwners.get(partitionId);
            if (currentOwner != null && !currentOwner.equals(nodeId)) {
                log.warn("Partition {} is already assigned to node {}", partitionId, currentOwner);
                return false;
            }

            // Update partition owner
            partitionOwners.put(partitionId, nodeId);

            // Update node info
            NodeInfo updated = existing.addPartition(partitionId);
            nodes.put(nodeId, updated);

            log.debug("Assigned partition {} to node {}", partitionId, nodeId);
            return true;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> removePartition(String nodeId, int partitionId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        return Mono.fromCallable(() -> {
            NodeInfo existing = nodes.get(nodeId);
            if (existing == null) {
                return false;
            }

            // Verify ownership
            String currentOwner = partitionOwners.get(partitionId);
            if (!nodeId.equals(currentOwner)) {
                return false;
            }

            // Remove partition
            partitionOwners.remove(partitionId);
            NodeInfo updated = existing.removePartition(partitionId);
            nodes.put(nodeId, updated);

            log.debug("Removed partition {} from node {}", partitionId, nodeId);
            return true;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Optional<String>> getPartitionOwner(int partitionId) {
        return Mono.just(Optional.ofNullable(partitionOwners.get(partitionId)));
    }

    @Override
    public Mono<Set<Integer>> getNodePartitions(String nodeId) {
        return Mono.fromCallable(() -> {
            NodeInfo info = nodes.get(nodeId);
            if (info == null) {
                return Collections.<Integer>emptySet();
            }
            return info.getAssignedPartitions();
        });
    }

    // ========== Event Listeners ==========

    @Override
    public void addNodeListener(NodeEventListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        listeners.add(listener);
    }

    @Override
    public void removeNodeListener(NodeEventListener listener) {
        listeners.remove(listener);
    }

    @Override
    public Flux<NodeEvent> nodeEvents() {
        return eventSink.asFlux();
    }

    private void emitEvent(NodeEvent event) {
        // Emit to sink
        eventSink.tryEmitNext(event);

        // Notify listeners
        for (NodeEventListener listener : listeners) {
            try {
                listener.onNodeEvent(event);
            } catch (Exception e) {
                log.error("Error notifying listener of event: {}", event, e);
            }
        }
    }

    // ========== Failure Detection ==========

    /**
     * Checks for failed nodes based on heartbeat timeouts.
     *
     * This method should be called periodically by an external scheduler.
     *
     * @return Flux of node IDs that were marked as failed
     */
    public Flux<String> checkForFailedNodes() {
        return Flux.fromIterable(new ArrayList<>(nodes.values()))
                .filter(node -> !node.isFailed() && !node.isLeaving())
                .filter(node -> node.getTimeSinceLastHeartbeat().compareTo(heartbeatTimeout) > 0)
                .flatMap(node -> {
                    int newMissedCount = node.getMissedHeartbeats() + 1;

                    if (newMissedCount >= maxMissedHeartbeats) {
                        // Mark as failed
                        return markFailed(node.getNodeId(),
                                "Missed " + newMissedCount + " heartbeats")
                                .thenReturn(node.getNodeId());
                    } else {
                        // Mark as unreachable
                        NodeInfo updated = node.recordMissedHeartbeat();
                        if (node.getStatus() != NodeStatus.UNREACHABLE) {
                            updated = updated.toBuilder()
                                    .status(NodeStatus.UNREACHABLE)
                                    .statusChangedAt(Instant.now())
                                    .statusReason("Missed heartbeat")
                                    .build();
                            emitEvent(NodeEvent.statusChanged(updated, node.getStatus(), "Heartbeat missed"));
                        }
                        nodes.put(node.getNodeId(), updated);
                        return Mono.empty();
                    }
                });
    }

    /**
     * Gets a summary of the registry state.
     *
     * @return summary string
     */
    public String getSummary() {
        long active = nodes.values().stream()
                .filter(n -> n.getStatus() == NodeStatus.ACTIVE)
                .count();
        long failed = nodes.values().stream()
                .filter(NodeInfo::isFailed)
                .count();
        long draining = nodes.values().stream()
                .filter(n -> n.getStatus() == NodeStatus.DRAINING)
                .count();

        return String.format("InMemoryNodeRegistry{total=%d, active=%d, failed=%d, draining=%d, partitions=%d}",
                nodes.size(), active, failed, draining, partitionOwners.size());
    }

    /**
     * Clears all nodes (for testing).
     */
    public void clear() {
        nodes.clear();
        partitionOwners.clear();
        log.info("Registry cleared");
    }
}
