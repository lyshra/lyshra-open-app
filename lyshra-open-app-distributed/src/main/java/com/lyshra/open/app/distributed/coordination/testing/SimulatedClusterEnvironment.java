package com.lyshra.open.app.distributed.coordination.testing;

import com.lyshra.open.app.distributed.coordination.CoordinationEvent;
import com.lyshra.open.app.distributed.coordination.CoordinationResult;
import com.lyshra.open.app.distributed.coordination.OrphanRecoveryResult;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Simulated cluster environment for testing distributed coordination scenarios.
 *
 * This class provides a complete in-memory cluster simulation that allows testing
 * of complex distributed scenarios including:
 * - Multi-node coordination
 * - Node failures and recovery
 * - Partition reassignment
 * - Split-brain scenarios
 * - Network partitions
 *
 * Usage:
 * <pre>
 * SimulatedClusterEnvironment cluster = SimulatedClusterEnvironment.builder()
 *     .totalPartitions(16)
 *     .defaultLeaseDuration(Duration.ofMinutes(5))
 *     .build();
 *
 * // Add nodes
 * cluster.addNode("node-1");
 * cluster.addNode("node-2");
 * cluster.addNode("node-3");
 *
 * // Start the cluster
 * cluster.start();
 *
 * // Test scenarios
 * cluster.getCoordinator("node-1").acquireOwnership("workflow-1").block();
 *
 * // Simulate node failure
 * cluster.simulateNodeFailure("node-1");
 *
 * // Verify failover
 * cluster.getCoordinator("node-2").claimOrphanedWorkflows("node-1").collectList().block();
 * </pre>
 *
 * Thread Safety: This class is thread-safe for concurrent testing.
 */
@Slf4j
public class SimulatedClusterEnvironment {

    @Getter private final int totalPartitions;
    @Getter private final Duration defaultLeaseDuration;

    private final ConcurrentHashMap<String, TestableOwnershipCoordinator> coordinators;
    private final ConcurrentHashMap<String, Set<Integer>> nodePartitions;
    private final List<CoordinationEvent> clusterEvents;
    private final List<Consumer<CoordinationEvent>> clusterEventListeners;

    // Shared state across all coordinators
    private final ConcurrentHashMap<String, SharedLeaseState> sharedLeases;

    // Simulated time (shared across all coordinators)
    private volatile Instant simulatedTime;

    // Network partition simulation
    private final Set<String> isolatedNodes;

    private volatile boolean started;

    /**
     * Shared lease state visible to all nodes.
     */
    @Getter
    public static class SharedLeaseState {
        private final String workflowKey;
        private volatile String ownerId;
        private volatile int partitionId;
        private volatile Instant acquiredAt;
        private volatile Instant expiresAt;
        private volatile long fencingToken;
        private volatile long ownershipEpoch;
        private volatile long version;
        private volatile boolean released;

        public SharedLeaseState(String workflowKey, String ownerId, int partitionId,
                               Instant acquiredAt, Duration leaseDuration, long fencingToken, long epoch) {
            this.workflowKey = workflowKey;
            this.ownerId = ownerId;
            this.partitionId = partitionId;
            this.acquiredAt = acquiredAt;
            this.expiresAt = acquiredAt.plus(leaseDuration);
            this.fencingToken = fencingToken;
            this.ownershipEpoch = epoch;
            this.version = 1;
            this.released = false;
        }

        public synchronized boolean isValid(Instant currentTime) {
            return !released && currentTime.isBefore(expiresAt);
        }

        public synchronized void renew(Instant now, Duration extension) {
            this.expiresAt = now.plus(extension);
            this.version++;
        }

        public synchronized void transfer(String newOwnerId, Instant now, Duration leaseDuration, long newToken) {
            this.ownerId = newOwnerId;
            this.acquiredAt = now;
            this.expiresAt = now.plus(leaseDuration);
            this.fencingToken = newToken;
            this.ownershipEpoch++;
            this.version++;
        }

        public synchronized void release() {
            this.released = true;
        }
    }

    private SimulatedClusterEnvironment(Builder builder) {
        this.totalPartitions = builder.totalPartitions;
        this.defaultLeaseDuration = builder.defaultLeaseDuration;

        this.coordinators = new ConcurrentHashMap<>();
        this.nodePartitions = new ConcurrentHashMap<>();
        this.clusterEvents = new CopyOnWriteArrayList<>();
        this.clusterEventListeners = new CopyOnWriteArrayList<>();
        this.sharedLeases = new ConcurrentHashMap<>();
        this.isolatedNodes = ConcurrentHashMap.newKeySet();
        this.started = false;
    }

    public static Builder builder() {
        return new Builder();
    }

    // ========== Cluster Lifecycle ==========

    /**
     * Starts all coordinators in the cluster.
     */
    public void start() {
        if (started) {
            return;
        }

        log.info("[CLUSTER] Starting simulated cluster with {} nodes and {} partitions",
                coordinators.size(), totalPartitions);

        // Ensure partitions are assigned
        if (nodePartitions.isEmpty() && !coordinators.isEmpty()) {
            distributePartitionsEvenly();
        }

        // Initialize all coordinators
        for (TestableOwnershipCoordinator coordinator : coordinators.values()) {
            coordinator.initialize().block();
        }

        started = true;
        log.info("[CLUSTER] Cluster started successfully");
    }

    /**
     * Stops all coordinators in the cluster.
     */
    public void stop() {
        log.info("[CLUSTER] Stopping simulated cluster");

        for (TestableOwnershipCoordinator coordinator : coordinators.values()) {
            coordinator.shutdown().block();
        }

        started = false;
    }

    /**
     * Resets the entire cluster state.
     */
    public void reset() {
        stop();
        sharedLeases.clear();
        clusterEvents.clear();
        isolatedNodes.clear();
        simulatedTime = null;

        for (TestableOwnershipCoordinator coordinator : coordinators.values()) {
            coordinator.reset();
        }

        log.info("[CLUSTER] Cluster reset complete");
    }

    // ========== Node Management ==========

    /**
     * Adds a new node to the cluster.
     */
    public TestableOwnershipCoordinator addNode(String nodeId) {
        return addNode(nodeId, Collections.emptySet());
    }

    /**
     * Adds a new node with specific partition assignments.
     */
    public TestableOwnershipCoordinator addNode(String nodeId, Set<Integer> partitions) {
        TestableOwnershipCoordinator coordinator = TestableOwnershipCoordinator.builder()
                .nodeId(nodeId)
                .totalPartitions(totalPartitions)
                .defaultLeaseDuration(defaultLeaseDuration)
                .assignedPartitions(partitions.isEmpty() ? allPartitions() : partitions)
                .build();

        // Sync simulated time
        if (simulatedTime != null) {
            coordinator.setSimulatedTime(simulatedTime);
        }

        // Register cluster event listener
        coordinator.addCoordinationEventListener(event -> {
            clusterEvents.add(event);
            notifyClusterEventListeners(event);
        });

        coordinators.put(nodeId, coordinator);
        nodePartitions.put(nodeId, new HashSet<>(partitions));

        if (started) {
            coordinator.initialize().block();
        }

        log.info("[CLUSTER] Added node: {} with partitions: {}", nodeId, partitions);
        return coordinator;
    }

    /**
     * Removes a node from the cluster (graceful shutdown).
     */
    public void removeNode(String nodeId) {
        TestableOwnershipCoordinator coordinator = coordinators.remove(nodeId);
        if (coordinator != null) {
            coordinator.shutdown().block();
            nodePartitions.remove(nodeId);
            log.info("[CLUSTER] Removed node: {}", nodeId);
        }
    }

    /**
     * Gets a coordinator by node ID.
     */
    public TestableOwnershipCoordinator getCoordinator(String nodeId) {
        return coordinators.get(nodeId);
    }

    /**
     * Gets all coordinators.
     */
    public Map<String, TestableOwnershipCoordinator> getAllCoordinators() {
        return Collections.unmodifiableMap(new HashMap<>(coordinators));
    }

    /**
     * Gets all active node IDs.
     */
    public Set<String> getActiveNodes() {
        return coordinators.entrySet().stream()
                .filter(e -> e.getValue().isActive())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    // ========== Partition Management ==========

    /**
     * Distributes partitions evenly across nodes.
     */
    public void distributePartitionsEvenly() {
        List<String> nodeIds = new ArrayList<>(coordinators.keySet());
        if (nodeIds.isEmpty()) {
            return;
        }

        // Clear existing assignments
        nodePartitions.clear();
        for (String nodeId : nodeIds) {
            nodePartitions.put(nodeId, new HashSet<>());
        }

        // Distribute partitions round-robin
        for (int p = 0; p < totalPartitions; p++) {
            String nodeId = nodeIds.get(p % nodeIds.size());
            nodePartitions.get(nodeId).add(p);
        }

        // Update coordinators
        for (Map.Entry<String, Set<Integer>> entry : nodePartitions.entrySet()) {
            TestableOwnershipCoordinator coordinator = coordinators.get(entry.getKey());
            if (coordinator != null) {
                // Recreate coordinator with new partitions
                coordinators.put(entry.getKey(), TestableOwnershipCoordinator.builder()
                        .nodeId(entry.getKey())
                        .totalPartitions(totalPartitions)
                        .defaultLeaseDuration(defaultLeaseDuration)
                        .assignedPartitions(entry.getValue())
                        .build());
            }
        }

        log.info("[CLUSTER] Distributed partitions: {}", nodePartitions);
    }

    /**
     * Assigns specific partitions to a node.
     */
    public void assignPartitions(String nodeId, Set<Integer> partitions) {
        nodePartitions.put(nodeId, new HashSet<>(partitions));

        // Recreate coordinator with new assignments
        TestableOwnershipCoordinator existing = coordinators.get(nodeId);
        if (existing != null) {
            boolean wasActive = existing.isActive();
            existing.shutdown().block();

            TestableOwnershipCoordinator newCoordinator = TestableOwnershipCoordinator.builder()
                    .nodeId(nodeId)
                    .totalPartitions(totalPartitions)
                    .defaultLeaseDuration(defaultLeaseDuration)
                    .assignedPartitions(partitions)
                    .build();

            if (simulatedTime != null) {
                newCoordinator.setSimulatedTime(simulatedTime);
            }

            coordinators.put(nodeId, newCoordinator);

            if (wasActive) {
                newCoordinator.initialize().block();
            }
        }

        log.info("[CLUSTER] Assigned partitions {} to node {}", partitions, nodeId);
    }

    /**
     * Gets the partitions assigned to a node.
     */
    public Set<Integer> getNodePartitions(String nodeId) {
        return Collections.unmodifiableSet(nodePartitions.getOrDefault(nodeId, Collections.emptySet()));
    }

    // ========== Failure Simulation ==========

    /**
     * Simulates a node failure (abrupt, no graceful shutdown).
     */
    public void simulateNodeFailure(String nodeId) {
        TestableOwnershipCoordinator coordinator = coordinators.get(nodeId);
        if (coordinator != null) {
            coordinator.simulateNodeFailure();
            log.info("[CLUSTER] Simulated failure of node: {}", nodeId);
        }
    }

    /**
     * Simulates a network partition isolating a node.
     */
    public void isolateNode(String nodeId) {
        isolatedNodes.add(nodeId);
        TestableOwnershipCoordinator coordinator = coordinators.get(nodeId);
        if (coordinator != null) {
            coordinator.simulateBackendFailure(true);
        }
        log.info("[CLUSTER] Isolated node: {}", nodeId);
    }

    /**
     * Heals a network partition for a node.
     */
    public void healNodePartition(String nodeId) {
        isolatedNodes.remove(nodeId);
        TestableOwnershipCoordinator coordinator = coordinators.get(nodeId);
        if (coordinator != null) {
            coordinator.simulateBackendFailure(false);
        }
        log.info("[CLUSTER] Healed partition for node: {}", nodeId);
    }

    /**
     * Checks if a node is isolated.
     */
    public boolean isNodeIsolated(String nodeId) {
        return isolatedNodes.contains(nodeId);
    }

    /**
     * Restarts a failed node.
     */
    public void restartNode(String nodeId) {
        TestableOwnershipCoordinator coordinator = coordinators.get(nodeId);
        if (coordinator != null) {
            coordinator.reset();
            coordinator.initialize().block();
            log.info("[CLUSTER] Restarted node: {}", nodeId);
        }
    }

    // ========== Time Simulation ==========

    /**
     * Sets simulated time for all coordinators.
     */
    public void setSimulatedTime(Instant time) {
        this.simulatedTime = time;
        for (TestableOwnershipCoordinator coordinator : coordinators.values()) {
            coordinator.setSimulatedTime(time);
        }
        log.debug("[CLUSTER] Simulated time set to: {}", time);
    }

    /**
     * Advances simulated time for all coordinators.
     */
    public void advanceTime(Duration duration) {
        if (simulatedTime == null) {
            simulatedTime = Instant.now();
        }
        simulatedTime = simulatedTime.plus(duration);
        for (TestableOwnershipCoordinator coordinator : coordinators.values()) {
            coordinator.setSimulatedTime(simulatedTime);
        }
        log.debug("[CLUSTER] Advanced time by {} to {}", duration, simulatedTime);
    }

    /**
     * Resets to real time for all coordinators.
     */
    public void useRealTime() {
        this.simulatedTime = null;
        for (TestableOwnershipCoordinator coordinator : coordinators.values()) {
            coordinator.useRealTime();
        }
    }

    /**
     * Gets the current simulated time.
     */
    public Instant now() {
        return simulatedTime != null ? simulatedTime : Instant.now();
    }

    // ========== Cluster Event Handling ==========

    /**
     * Registers a cluster-wide event listener.
     */
    public void addClusterEventListener(Consumer<CoordinationEvent> listener) {
        clusterEventListeners.add(listener);
    }

    /**
     * Gets all cluster events.
     */
    public List<CoordinationEvent> getClusterEvents() {
        return Collections.unmodifiableList(new ArrayList<>(clusterEvents));
    }

    /**
     * Gets cluster events of a specific type.
     */
    public List<CoordinationEvent> getClusterEvents(CoordinationEvent.EventType type) {
        return clusterEvents.stream()
                .filter(e -> e.getType() == type)
                .toList();
    }

    /**
     * Clears all cluster events.
     */
    public void clearClusterEvents() {
        clusterEvents.clear();
    }

    private void notifyClusterEventListeners(CoordinationEvent event) {
        for (Consumer<CoordinationEvent> listener : clusterEventListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.error("[CLUSTER] Error notifying cluster event listener", e);
            }
        }
    }

    // ========== Cluster State Inspection ==========

    /**
     * Gets the owner of a workflow across the cluster.
     */
    public Optional<String> getWorkflowOwner(String workflowKey) {
        for (TestableOwnershipCoordinator coordinator : coordinators.values()) {
            if (coordinator.isOwnedLocally(workflowKey)) {
                return Optional.of(coordinator.getNodeId());
            }
        }
        return Optional.empty();
    }

    /**
     * Gets all workflows owned by a specific node.
     */
    public Set<String> getWorkflowsOwnedBy(String nodeId) {
        TestableOwnershipCoordinator coordinator = coordinators.get(nodeId);
        if (coordinator != null) {
            return coordinator.getLocallyOwnedWorkflows();
        }
        return Collections.emptySet();
    }

    /**
     * Gets total ownership count across the cluster.
     */
    public int getTotalOwnershipCount() {
        return coordinators.values().stream()
                .mapToInt(TestableOwnershipCoordinator::getLocalOwnershipCount)
                .sum();
    }

    /**
     * Verifies that each workflow has exactly one owner.
     */
    public boolean verifySingleOwnership() {
        Map<String, List<String>> workflowOwners = new HashMap<>();

        for (TestableOwnershipCoordinator coordinator : coordinators.values()) {
            for (String workflowKey : coordinator.getLocallyOwnedWorkflows()) {
                workflowOwners.computeIfAbsent(workflowKey, k -> new ArrayList<>())
                        .add(coordinator.getNodeId());
            }
        }

        for (Map.Entry<String, List<String>> entry : workflowOwners.entrySet()) {
            if (entry.getValue().size() > 1) {
                log.error("[CLUSTER] Workflow {} has multiple owners: {}", entry.getKey(), entry.getValue());
                return false;
            }
        }

        return true;
    }

    // ========== Test Scenario Helpers ==========

    /**
     * Acquires ownership on the appropriate node based on partition.
     */
    public CoordinationResult acquireOnCorrectNode(String workflowKey) {
        int partition = Math.abs(workflowKey.hashCode()) % totalPartitions;

        for (Map.Entry<String, Set<Integer>> entry : nodePartitions.entrySet()) {
            if (entry.getValue().contains(partition)) {
                TestableOwnershipCoordinator coordinator = coordinators.get(entry.getKey());
                if (coordinator != null && coordinator.isActive()) {
                    return coordinator.acquireOwnership(workflowKey).block();
                }
            }
        }

        // Fallback to first active coordinator
        for (TestableOwnershipCoordinator coordinator : coordinators.values()) {
            if (coordinator.isActive()) {
                return coordinator.acquireOwnership(workflowKey).block();
            }
        }

        return null;
    }

    /**
     * Triggers orphan recovery on all active nodes for a failed node.
     */
    public Map<String, OrphanRecoveryResult> triggerOrphanRecovery(String failedNodeId) {
        Map<String, OrphanRecoveryResult> results = new HashMap<>();

        for (TestableOwnershipCoordinator coordinator : coordinators.values()) {
            if (coordinator.isActive() && !coordinator.getNodeId().equals(failedNodeId)) {
                OrphanRecoveryResult result = coordinator.claimOrphanedWorkflows(failedNodeId,
                        com.lyshra.open.app.distributed.coordination.RecoveryOptions.DEFAULT).block();
                results.put(coordinator.getNodeId(), result);
            }
        }

        return results;
    }

    /**
     * Waits for a specific event type to occur.
     */
    public boolean waitForEvent(CoordinationEvent.EventType type, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        int initialCount = getClusterEvents(type).size();

        while (Instant.now().isBefore(deadline)) {
            if (getClusterEvents(type).size() > initialCount) {
                return true;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }

    // ========== Helper Methods ==========

    private Set<Integer> allPartitions() {
        Set<Integer> all = new HashSet<>();
        for (int i = 0; i < totalPartitions; i++) {
            all.add(i);
        }
        return all;
    }

    // ========== Builder ==========

    public static class Builder {
        private int totalPartitions = 16;
        private Duration defaultLeaseDuration = Duration.ofMinutes(5);

        public Builder totalPartitions(int partitions) {
            if (partitions <= 0) {
                throw new IllegalArgumentException("partitions must be positive");
            }
            this.totalPartitions = partitions;
            return this;
        }

        public Builder defaultLeaseDuration(Duration duration) {
            this.defaultLeaseDuration = Objects.requireNonNull(duration);
            return this;
        }

        public SimulatedClusterEnvironment build() {
            return new SimulatedClusterEnvironment(this);
        }
    }
}
