package com.lyshra.open.app.distributed.cluster.impl;

import com.lyshra.open.app.distributed.cluster.*;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstract base implementation of IClusterCoordinator providing common functionality
 * for cluster coordination operations.
 *
 * This class implements the Template Method pattern, defining the skeleton of cluster
 * coordination while allowing subclasses to provide specific implementations for
 * backend-specific operations.
 *
 * Subclasses must implement:
 * - doInitialize(): Backend-specific initialization
 * - doShutdown(): Backend-specific cleanup
 * - doHeartbeat(): Backend-specific heartbeat sending
 * - doLeaderElection(): Backend-specific leader election
 * - doFetchActiveNodes(): Backend-specific node discovery
 *
 * Thread Safety: This class is thread-safe. All listener collections use
 * CopyOnWriteArrayList, and all state variables use atomic operations.
 */
@Slf4j
public abstract class AbstractClusterCoordinator implements IClusterCoordinator {

    protected final String nodeId;
    protected final ClusterNodeInfo localNodeInfo;

    private final List<IClusterMembershipListener> membershipListeners = new CopyOnWriteArrayList<>();
    private final List<IClusterLeadershipListener> leadershipListeners = new CopyOnWriteArrayList<>();

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicReference<String> currentLeaderId = new AtomicReference<>();
    private final AtomicLong leadershipEpoch = new AtomicLong(0);

    protected final AtomicReference<Set<String>> activeNodes = new AtomicReference<>(Collections.emptySet());

    protected AbstractClusterCoordinator(String nodeId, String hostname, int port) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId must not be null");
        this.localNodeInfo = ClusterNodeInfo.builder()
                .nodeId(nodeId)
                .hostname(hostname)
                .port(port)
                .state(ClusterNodeInfo.NodeState.STARTING)
                .startTime(Instant.now())
                .lastHeartbeat(Instant.now())
                .heartbeatEpoch(0)
                .assignedPartitionCount(0)
                .build();
    }

    @Override
    public final Mono<Void> initialize() {
        if (!initialized.compareAndSet(false, true)) {
            log.warn("Cluster coordinator already initialized for node: {}", nodeId);
            return Mono.empty();
        }

        log.info("Initializing cluster coordinator for node: {}", nodeId);

        return doInitialize()
                .doOnSuccess(v -> {
                    connected.set(true);
                    log.info("Cluster coordinator initialized successfully for node: {}", nodeId);
                    notifyMembershipListeners(ClusterMembershipEvent.selfJoined(nodeId, activeNodes.get()));
                })
                .doOnError(e -> {
                    initialized.set(false);
                    log.error("Failed to initialize cluster coordinator for node: {}", nodeId, e);
                });
    }

    @Override
    public final Mono<Void> shutdown() {
        if (!connected.compareAndSet(true, false)) {
            log.warn("Cluster coordinator already disconnected for node: {}", nodeId);
            return Mono.empty();
        }

        log.info("Shutting down cluster coordinator for node: {}", nodeId);

        return doShutdown()
                .doOnSuccess(v -> {
                    initialized.set(false);
                    log.info("Cluster coordinator shutdown complete for node: {}", nodeId);
                })
                .doOnError(e -> log.error("Error during cluster coordinator shutdown for node: {}", nodeId, e));
    }

    @Override
    public final String getNodeId() {
        return nodeId;
    }

    @Override
    public final Set<String> getActiveNodes() {
        return Collections.unmodifiableSet(activeNodes.get());
    }

    @Override
    public final boolean isLeader() {
        return nodeId.equals(currentLeaderId.get());
    }

    @Override
    public final Optional<String> getLeaderId() {
        return Optional.ofNullable(currentLeaderId.get());
    }

    @Override
    public final void addMembershipListener(IClusterMembershipListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        membershipListeners.add(listener);
    }

    @Override
    public final void removeMembershipListener(IClusterMembershipListener listener) {
        membershipListeners.remove(listener);
    }

    @Override
    public final void addLeadershipListener(IClusterLeadershipListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        leadershipListeners.add(listener);
    }

    @Override
    public final void removeLeadershipListener(IClusterLeadershipListener listener) {
        leadershipListeners.remove(listener);
    }

    @Override
    public final boolean isConnected() {
        return connected.get();
    }

    @Override
    public final int getClusterSize() {
        return activeNodes.get().size();
    }

    /**
     * Updates the set of active nodes and notifies listeners of changes.
     * Thread-safe: uses atomic compare-and-swap.
     */
    protected void updateActiveNodes(Set<String> newActiveNodes) {
        Set<String> oldNodes = activeNodes.getAndSet(new HashSet<>(newActiveNodes));

        // Find nodes that joined
        Set<String> joined = new HashSet<>(newActiveNodes);
        joined.removeAll(oldNodes);

        // Find nodes that left
        Set<String> left = new HashSet<>(oldNodes);
        left.removeAll(newActiveNodes);

        // Notify listeners
        for (String joinedNode : joined) {
            if (!joinedNode.equals(nodeId)) {
                notifyMembershipListeners(ClusterMembershipEvent.nodeJoined(joinedNode, newActiveNodes, nodeId));
            }
        }

        for (String leftNode : left) {
            notifyMembershipListeners(ClusterMembershipEvent.nodeFailed(leftNode, newActiveNodes, nodeId));
        }
    }

    /**
     * Updates the leader and notifies listeners of changes.
     * Thread-safe: uses atomic compare-and-swap.
     */
    protected void updateLeader(String newLeaderId) {
        String previousLeader = currentLeaderId.getAndSet(newLeaderId);
        long epoch = leadershipEpoch.incrementAndGet();

        if (Objects.equals(previousLeader, newLeaderId)) {
            return; // No change
        }

        ClusterLeadershipEvent event;
        if (nodeId.equals(newLeaderId)) {
            event = ClusterLeadershipEvent.becameLeader(previousLeader, nodeId, epoch);
        } else if (nodeId.equals(previousLeader)) {
            event = ClusterLeadershipEvent.lostLeadership(newLeaderId, nodeId, epoch);
        } else if (newLeaderId == null) {
            event = ClusterLeadershipEvent.noLeader(previousLeader, nodeId, epoch);
        } else {
            event = ClusterLeadershipEvent.leaderChanged(previousLeader, newLeaderId, nodeId, epoch);
        }

        notifyLeadershipListeners(event);
    }

    /**
     * Returns the current leadership epoch.
     */
    protected long getLeadershipEpoch() {
        return leadershipEpoch.get();
    }

    private void notifyMembershipListeners(ClusterMembershipEvent event) {
        for (IClusterMembershipListener listener : membershipListeners) {
            try {
                listener.onMembershipChange(event);
            } catch (Exception e) {
                log.error("Error notifying membership listener of event: {}", event, e);
            }
        }
    }

    private void notifyLeadershipListeners(ClusterLeadershipEvent event) {
        for (IClusterLeadershipListener listener : leadershipListeners) {
            try {
                listener.onLeadershipChange(event);
            } catch (Exception e) {
                log.error("Error notifying leadership listener of event: {}", event, e);
            }
        }
    }

    // Abstract methods to be implemented by subclasses

    /**
     * Performs backend-specific initialization.
     */
    protected abstract Mono<Void> doInitialize();

    /**
     * Performs backend-specific shutdown.
     */
    protected abstract Mono<Void> doShutdown();

    /**
     * Sends a heartbeat to maintain cluster membership.
     */
    protected abstract Mono<Void> doHeartbeat();

    /**
     * Triggers or participates in leader election.
     */
    protected abstract Mono<Optional<String>> doLeaderElection();

    /**
     * Fetches the current set of active nodes from the backend.
     */
    protected abstract Mono<Set<String>> doFetchActiveNodes();
}
