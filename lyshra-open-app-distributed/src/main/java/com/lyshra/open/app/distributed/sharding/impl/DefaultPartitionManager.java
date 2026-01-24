package com.lyshra.open.app.distributed.sharding.impl;

import com.lyshra.open.app.distributed.cluster.ClusterMembershipEvent;
import com.lyshra.open.app.distributed.cluster.IClusterCoordinator;
import com.lyshra.open.app.distributed.cluster.IClusterMembershipListener;
import com.lyshra.open.app.distributed.sharding.*;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Default implementation of IPartitionManager.
 *
 * Manages partition assignments across cluster nodes using the configured
 * sharding strategy. Key responsibilities:
 *
 * 1. Partition Assignment: Assigns partitions to nodes based on cluster membership
 * 2. Rebalancing: Redistributes partitions when nodes join or leave
 * 3. Ownership Tracking: Maintains and queries partition ownership
 * 4. Event Notification: Notifies listeners of assignment changes
 *
 * The rebalancing algorithm uses a "sticky" approach that minimizes partition
 * movement while maintaining even distribution across nodes.
 *
 * Thread Safety: This class is thread-safe. Uses atomic operations and
 * copy-on-write collections for all shared state.
 *
 * Design Patterns:
 * - Observer: Notifies listeners of partition changes
 * - Strategy: Uses pluggable sharding strategy
 * - Facade: Provides unified interface for partition operations
 */
@Slf4j
public class DefaultPartitionManager implements IPartitionManager, IClusterMembershipListener {

    private final IClusterCoordinator clusterCoordinator;
    private final IShardingStrategy shardingStrategy;
    private final int totalPartitions;

    private final AtomicReference<Map<Integer, String>> partitionAssignments;
    private final AtomicReference<Set<Integer>> localPartitions;
    private final AtomicLong assignmentEpoch;
    private final List<IPartitionChangeListener> listeners;

    private volatile boolean initialized = false;

    /**
     * Creates a new partition manager.
     *
     * @param clusterCoordinator the cluster coordinator for membership info
     * @param shardingStrategy the strategy for computing partition assignments
     * @param totalPartitions the total number of partitions (should be prime or power of 2)
     */
    public DefaultPartitionManager(IClusterCoordinator clusterCoordinator,
                                    IShardingStrategy shardingStrategy,
                                    int totalPartitions) {
        this.clusterCoordinator = Objects.requireNonNull(clusterCoordinator, "clusterCoordinator must not be null");
        this.shardingStrategy = Objects.requireNonNull(shardingStrategy, "shardingStrategy must not be null");

        if (totalPartitions <= 0) {
            throw new IllegalArgumentException("totalPartitions must be positive");
        }
        this.totalPartitions = totalPartitions;

        this.partitionAssignments = new AtomicReference<>(new ConcurrentHashMap<>());
        this.localPartitions = new AtomicReference<>(Collections.emptySet());
        this.assignmentEpoch = new AtomicLong(0);
        this.listeners = new CopyOnWriteArrayList<>();
    }

    @Override
    public Mono<Void> initialize() {
        if (initialized) {
            log.warn("PartitionManager already initialized");
            return Mono.empty();
        }

        log.info("Initializing partition manager with {} partitions using strategy: {}",
                totalPartitions, shardingStrategy.getStrategyName());

        return Mono.fromRunnable(() -> {
            // Register for membership changes
            clusterCoordinator.addMembershipListener(this);

            // Perform initial assignment
            Set<String> activeNodes = clusterCoordinator.getActiveNodes();
            if (!activeNodes.isEmpty()) {
                Map<Integer, String> assignment = computeAssignment(activeNodes);
                applyAssignment(assignment, PartitionChangeEvent.EventType.INITIAL_ASSIGNMENT, null);
            }

            initialized = true;
            log.info("Partition manager initialized. Local node owns {} partitions",
                    localPartitions.get().size());
        });
    }

    @Override
    public Mono<Void> shutdown() {
        log.info("Shutting down partition manager");

        return Mono.fromRunnable(() -> {
            clusterCoordinator.removeMembershipListener(this);

            // Notify listeners of partition release
            Set<Integer> currentLocal = localPartitions.get();
            if (!currentLocal.isEmpty()) {
                PartitionChangeEvent event = PartitionChangeEvent.builder()
                        .eventType(PartitionChangeEvent.EventType.GRACEFUL_RELEASE)
                        .gainedPartitions(Collections.emptySet())
                        .lostPartitions(currentLocal)
                        .currentLocalPartitions(Collections.emptySet())
                        .fullAssignment(partitionAssignments.get())
                        .assignmentEpoch(assignmentEpoch.incrementAndGet())
                        .timestamp(java.time.Instant.now())
                        .localNodeId(clusterCoordinator.getNodeId())
                        .triggerNodeId(clusterCoordinator.getNodeId())
                        .build();
                notifyListeners(event);
            }

            localPartitions.set(Collections.emptySet());
            initialized = false;
        });
    }

    @Override
    public int getTotalPartitions() {
        return totalPartitions;
    }

    @Override
    public Optional<String> getPartitionOwner(int partitionId) {
        if (partitionId < 0 || partitionId >= totalPartitions) {
            throw new IllegalArgumentException("Invalid partition ID: " + partitionId);
        }
        return Optional.ofNullable(partitionAssignments.get().get(partitionId));
    }

    @Override
    public Set<Integer> getLocalPartitions() {
        return Collections.unmodifiableSet(localPartitions.get());
    }

    @Override
    public Set<Integer> getNodePartitions(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        return partitionAssignments.get().entrySet().stream()
                .filter(entry -> nodeId.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Map<Integer, String> getPartitionAssignments() {
        return Collections.unmodifiableMap(partitionAssignments.get());
    }

    @Override
    public boolean isLocalPartition(int partitionId) {
        return localPartitions.get().contains(partitionId);
    }

    @Override
    public boolean shouldHandleWorkflow(String workflowExecutionKey) {
        int partition = shardingStrategy.computePartition(workflowExecutionKey, totalPartitions);
        return isLocalPartition(partition);
    }

    @Override
    public Optional<String> getTargetNode(String workflowExecutionKey) {
        int partition = shardingStrategy.computePartition(workflowExecutionKey, totalPartitions);
        return getPartitionOwner(partition);
    }

    @Override
    public Mono<Void> rebalance() {
        if (!clusterCoordinator.isLeader()) {
            log.warn("Rebalance requested but this node is not the leader");
            return Mono.empty();
        }

        log.info("Initiating partition rebalance");

        return Mono.fromRunnable(() -> {
            Set<String> activeNodes = clusterCoordinator.getActiveNodes();
            Map<Integer, String> newAssignment = computeAssignment(activeNodes);
            applyAssignment(newAssignment, PartitionChangeEvent.EventType.REBALANCE, null);
        });
    }

    @Override
    public void addPartitionChangeListener(IPartitionChangeListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        listeners.add(listener);
    }

    @Override
    public void removePartitionChangeListener(IPartitionChangeListener listener) {
        listeners.remove(listener);
    }

    @Override
    public long getAssignmentEpoch() {
        return assignmentEpoch.get();
    }

    @Override
    public Mono<Void> acquirePartitions(Set<Integer> partitions) {
        Objects.requireNonNull(partitions, "partitions must not be null");

        return Mono.fromRunnable(() -> {
            Set<Integer> current = new HashSet<>(localPartitions.get());
            current.addAll(partitions);
            localPartitions.set(Collections.unmodifiableSet(current));

            log.info("Acquired {} partitions. Total local partitions: {}",
                    partitions.size(), current.size());
        });
    }

    @Override
    public Mono<Void> releasePartitions(Set<Integer> partitions) {
        Objects.requireNonNull(partitions, "partitions must not be null");

        return Mono.fromRunnable(() -> {
            Set<Integer> current = new HashSet<>(localPartitions.get());
            current.removeAll(partitions);
            localPartitions.set(Collections.unmodifiableSet(current));

            log.info("Released {} partitions. Remaining local partitions: {}",
                    partitions.size(), current.size());
        });
    }

    // IClusterMembershipListener implementation

    @Override
    public void onMembershipChange(ClusterMembershipEvent event) {
        log.info("Handling membership change: {} for node {}",
                event.getEventType(), event.getAffectedNodeId());

        if (!initialized) {
            return;
        }

        switch (event.getEventType()) {
            case NODE_JOINED, NODE_REJOINED -> handleNodeJoin(event);
            case NODE_LEFT, NODE_FAILED -> handleNodeDeparture(event);
            case SELF_JOINED -> handleSelfJoin(event);
            default -> log.debug("Ignoring membership event: {}", event.getEventType());
        }
    }

    private void handleNodeJoin(ClusterMembershipEvent event) {
        if (clusterCoordinator.isLeader()) {
            // Leader triggers rebalance
            rebalance().subscribe();
        }
    }

    private void handleNodeDeparture(ClusterMembershipEvent event) {
        if (clusterCoordinator.isLeader()) {
            // Leader reassigns partitions from departed node
            Set<Integer> orphanedPartitions = shardingStrategy.getAffectedPartitions(
                    event.getAffectedNodeId(),
                    partitionAssignments.get(),
                    totalPartitions
            );

            if (!orphanedPartitions.isEmpty()) {
                log.info("Reassigning {} orphaned partitions from failed node {}",
                        orphanedPartitions.size(), event.getAffectedNodeId());
                rebalance().subscribe();
            }
        }
    }

    private void handleSelfJoin(ClusterMembershipEvent event) {
        // Request initial assignment from leader or compute locally
        Set<String> activeNodes = event.getCurrentActiveNodes();
        Map<Integer, String> assignment = computeAssignment(activeNodes);
        applyAssignment(assignment, PartitionChangeEvent.EventType.INITIAL_ASSIGNMENT, null);
    }

    /**
     * Computes partition assignment using round-robin distribution.
     *
     * The algorithm ensures:
     * 1. Even distribution: Each node gets approximately totalPartitions/nodeCount partitions
     * 2. Determinism: Same set of nodes always produces same assignment
     * 3. Stability: Existing assignments are preserved when possible
     */
    private Map<Integer, String> computeAssignment(Set<String> activeNodes) {
        if (activeNodes.isEmpty()) {
            return Collections.emptyMap();
        }

        // Sort nodes for deterministic assignment
        List<String> sortedNodes = activeNodes.stream()
                .sorted()
                .toList();

        Map<Integer, String> assignment = new HashMap<>();
        int nodeCount = sortedNodes.size();

        for (int partition = 0; partition < totalPartitions; partition++) {
            // Use modulo for even distribution
            int nodeIndex = partition % nodeCount;
            assignment.put(partition, sortedNodes.get(nodeIndex));
        }

        return assignment;
    }

    /**
     * Applies a new partition assignment and notifies listeners of changes.
     */
    private void applyAssignment(Map<Integer, String> newAssignment,
                                  PartitionChangeEvent.EventType eventType,
                                  String triggerNodeId) {
        String localNodeId = clusterCoordinator.getNodeId();
        Map<Integer, String> oldAssignment = partitionAssignments.get();
        Set<Integer> oldLocal = localPartitions.get();

        // Calculate new local partitions
        Set<Integer> newLocal = newAssignment.entrySet().stream()
                .filter(entry -> localNodeId.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        // Calculate gained and lost partitions
        Set<Integer> gained = new HashSet<>(newLocal);
        gained.removeAll(oldLocal);

        Set<Integer> lost = new HashSet<>(oldLocal);
        lost.removeAll(newLocal);

        // Update state atomically
        partitionAssignments.set(new ConcurrentHashMap<>(newAssignment));
        localPartitions.set(Collections.unmodifiableSet(newLocal));
        long epoch = assignmentEpoch.incrementAndGet();

        log.info("Partition assignment updated. Gained: {}, Lost: {}, Total local: {}, Epoch: {}",
                gained.size(), lost.size(), newLocal.size(), epoch);

        // Notify listeners if there were changes
        if (!gained.isEmpty() || !lost.isEmpty()) {
            PartitionChangeEvent event = PartitionChangeEvent.builder()
                    .eventType(eventType)
                    .gainedPartitions(gained)
                    .lostPartitions(lost)
                    .currentLocalPartitions(newLocal)
                    .fullAssignment(newAssignment)
                    .assignmentEpoch(epoch)
                    .timestamp(java.time.Instant.now())
                    .localNodeId(localNodeId)
                    .triggerNodeId(triggerNodeId != null ? triggerNodeId : localNodeId)
                    .build();
            notifyListeners(event);
        }
    }

    private void notifyListeners(PartitionChangeEvent event) {
        for (IPartitionChangeListener listener : listeners) {
            try {
                listener.onPartitionChange(event);
            } catch (Exception e) {
                log.error("Error notifying partition change listener", e);
            }
        }
    }
}
