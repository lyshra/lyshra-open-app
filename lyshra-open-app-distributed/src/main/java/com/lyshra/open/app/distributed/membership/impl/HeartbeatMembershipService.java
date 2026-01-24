package com.lyshra.open.app.distributed.membership.impl;

import com.lyshra.open.app.distributed.cluster.ClusterNodeInfo;
import com.lyshra.open.app.distributed.cluster.IClusterCoordinator;
import com.lyshra.open.app.distributed.membership.INodeHealthListener;
import com.lyshra.open.app.distributed.membership.INodeMembershipService;
import com.lyshra.open.app.distributed.membership.NodeHealthEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Heartbeat-based implementation of node membership service.
 *
 * This implementation uses periodic heartbeats for failure detection.
 * A node is considered failed if it misses a configurable number of
 * consecutive heartbeat intervals.
 *
 * Failure Detection Algorithm:
 * 1. Each node sends heartbeats at regular intervals
 * 2. Missing heartbeats are tracked per node
 * 3. After suspicion threshold, node is marked as UNKNOWN
 * 4. After failure threshold, node is marked as FAILED
 *
 * The two-phase detection (suspicion → failure) prevents false positives
 * from transient network issues.
 *
 * Thread Safety: This class is thread-safe using ConcurrentHashMap
 * and atomic operations.
 */
@Slf4j
public class HeartbeatMembershipService implements INodeMembershipService {

    private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(5);
    private static final Duration DEFAULT_FAILURE_TIMEOUT = Duration.ofSeconds(30);
    private static final int SUSPICION_THRESHOLD = 2;
    private static final int FAILURE_THRESHOLD = 4;

    private final IClusterCoordinator clusterCoordinator;
    private final Duration heartbeatInterval;
    private final Duration failureTimeout;

    private final ConcurrentHashMap<String, NodeRegistration> nodes;
    private final List<INodeHealthListener> healthListeners;
    private final AtomicLong heartbeatEpoch;
    private final AtomicBoolean initialized;

    private ClusterNodeInfo localNodeInfo;
    private ScheduledExecutorService heartbeatExecutor;
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> detectionTask;

    /**
     * Internal registration for tracking node state.
     */
    private static class NodeRegistration {
        final ClusterNodeInfo nodeInfo;
        final Instant lastHeartbeat;
        final int missedHeartbeats;
        final long heartbeatEpoch;

        NodeRegistration(ClusterNodeInfo nodeInfo, Instant lastHeartbeat, int missedHeartbeats, long epoch) {
            this.nodeInfo = nodeInfo;
            this.lastHeartbeat = lastHeartbeat;
            this.missedHeartbeats = missedHeartbeats;
            this.heartbeatEpoch = epoch;
        }

        NodeRegistration withHeartbeat(Instant time, long epoch) {
            ClusterNodeInfo updated = nodeInfo.withHeartbeat(time, epoch);
            return new NodeRegistration(updated.withState(ClusterNodeInfo.NodeState.ACTIVE), time, 0, epoch);
        }

        NodeRegistration withMissedHeartbeat(int missed) {
            ClusterNodeInfo.NodeState newState = missed >= FAILURE_THRESHOLD
                    ? ClusterNodeInfo.NodeState.FAILED
                    : missed >= SUSPICION_THRESHOLD
                            ? ClusterNodeInfo.NodeState.UNKNOWN
                            : nodeInfo.getState();
            return new NodeRegistration(nodeInfo.withState(newState), lastHeartbeat, missed, heartbeatEpoch);
        }

        Duration timeSinceHeartbeat() {
            return Duration.between(lastHeartbeat, Instant.now());
        }

        boolean isSuspected() {
            return missedHeartbeats >= SUSPICION_THRESHOLD;
        }

        boolean isFailed() {
            return missedHeartbeats >= FAILURE_THRESHOLD;
        }
    }

    public HeartbeatMembershipService(IClusterCoordinator clusterCoordinator) {
        this(clusterCoordinator, DEFAULT_HEARTBEAT_INTERVAL, DEFAULT_FAILURE_TIMEOUT);
    }

    public HeartbeatMembershipService(IClusterCoordinator clusterCoordinator,
                                       Duration heartbeatInterval,
                                       Duration failureTimeout) {
        this.clusterCoordinator = Objects.requireNonNull(clusterCoordinator);
        this.heartbeatInterval = heartbeatInterval != null ? heartbeatInterval : DEFAULT_HEARTBEAT_INTERVAL;
        this.failureTimeout = failureTimeout != null ? failureTimeout : DEFAULT_FAILURE_TIMEOUT;

        this.nodes = new ConcurrentHashMap<>();
        this.healthListeners = new CopyOnWriteArrayList<>();
        this.heartbeatEpoch = new AtomicLong(0);
        this.initialized = new AtomicBoolean(false);
    }

    @Override
    public Mono<Void> initialize() {
        if (!initialized.compareAndSet(false, true)) {
            log.warn("Membership service already initialized");
            return Mono.empty();
        }

        log.info("Initializing heartbeat membership service");

        return Mono.fromRunnable(() -> {
            // Create local node info
            String nodeId = clusterCoordinator.getNodeId();
            String hostname = getLocalHostname();
            int port = 8080; // Default port - could be configurable

            localNodeInfo = ClusterNodeInfo.builder()
                    .nodeId(nodeId)
                    .hostname(hostname)
                    .port(port)
                    .state(ClusterNodeInfo.NodeState.ACTIVE)
                    .startTime(Instant.now())
                    .lastHeartbeat(Instant.now())
                    .heartbeatEpoch(0)
                    .assignedPartitionCount(0)
                    .build();

            // Register self
            nodes.put(nodeId, new NodeRegistration(localNodeInfo, Instant.now(), 0, 0));

            // Start heartbeat executor
            heartbeatExecutor = Executors.newScheduledThreadPool(2,
                    r -> new Thread(r, "membership-" + nodeId));

            heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(
                    this::doSendHeartbeat,
                    heartbeatInterval.toMillis(),
                    heartbeatInterval.toMillis(),
                    TimeUnit.MILLISECONDS
            );

            detectionTask = heartbeatExecutor.scheduleAtFixedRate(
                    this::checkNodeHealth,
                    heartbeatInterval.toMillis() * 2,
                    heartbeatInterval.toMillis(),
                    TimeUnit.MILLISECONDS
            );

            log.info("Membership service initialized. Node {} registered with heartbeat interval {}",
                    nodeId, heartbeatInterval);
        });
    }

    @Override
    public Mono<Void> shutdown() {
        log.info("Shutting down membership service");

        return Mono.fromRunnable(() -> {
            // Stop scheduled tasks
            if (heartbeatTask != null) {
                heartbeatTask.cancel(false);
            }
            if (detectionTask != null) {
                detectionTask.cancel(false);
            }
            if (heartbeatExecutor != null) {
                heartbeatExecutor.shutdown();
                try {
                    if (!heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        heartbeatExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    heartbeatExecutor.shutdownNow();
                }
            }

            // Deregister self
            nodes.remove(clusterCoordinator.getNodeId());

            initialized.set(false);
            log.info("Membership service shutdown complete");
        });
    }

    @Override
    public ClusterNodeInfo getLocalNodeInfo() {
        return localNodeInfo;
    }

    @Override
    public Optional<ClusterNodeInfo> getNodeInfo(String nodeId) {
        NodeRegistration reg = nodes.get(nodeId);
        return reg != null ? Optional.of(reg.nodeInfo) : Optional.empty();
    }

    @Override
    public List<ClusterNodeInfo> getAllNodes() {
        return nodes.values().stream()
                .map(reg -> reg.nodeInfo)
                .toList();
    }

    @Override
    public List<ClusterNodeInfo> getActiveNodes() {
        return nodes.values().stream()
                .filter(reg -> reg.nodeInfo.getState() == ClusterNodeInfo.NodeState.ACTIVE)
                .map(reg -> reg.nodeInfo)
                .toList();
    }

    @Override
    public boolean isNodeActive(String nodeId) {
        NodeRegistration reg = nodes.get(nodeId);
        return reg != null && reg.nodeInfo.getState() == ClusterNodeInfo.NodeState.ACTIVE;
    }

    @Override
    public int getActiveNodeCount() {
        return (int) nodes.values().stream()
                .filter(reg -> reg.nodeInfo.getState() == ClusterNodeInfo.NodeState.ACTIVE)
                .count();
    }

    @Override
    public Mono<Void> sendHeartbeat() {
        return Mono.fromRunnable(this::doSendHeartbeat)
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<Void> startDraining() {
        return Mono.fromRunnable(() -> {
            String nodeId = clusterCoordinator.getNodeId();
            NodeRegistration current = nodes.get(nodeId);
            if (current != null) {
                ClusterNodeInfo draining = current.nodeInfo.withState(ClusterNodeInfo.NodeState.DRAINING);
                nodes.put(nodeId, new NodeRegistration(draining, current.lastHeartbeat,
                        current.missedHeartbeats, current.heartbeatEpoch));
                localNodeInfo = draining;
                log.info("Node {} started draining", nodeId);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    @Override
    public Duration getFailureDetectionTimeout() {
        return failureTimeout;
    }

    @Override
    public void addHealthListener(INodeHealthListener listener) {
        Objects.requireNonNull(listener);
        healthListeners.add(listener);
    }

    @Override
    public void removeHealthListener(INodeHealthListener listener) {
        healthListeners.remove(listener);
    }

    /**
     * Registers or updates a remote node's heartbeat.
     * Called when receiving heartbeat from another node.
     */
    public void receiveHeartbeat(String nodeId, ClusterNodeInfo nodeInfo) {
        long epoch = heartbeatEpoch.incrementAndGet();
        Instant now = Instant.now();

        NodeRegistration existing = nodes.get(nodeId);
        ClusterNodeInfo.NodeState previousState = existing != null ? existing.nodeInfo.getState() : null;

        NodeRegistration updated = new NodeRegistration(nodeInfo.withHeartbeat(now, epoch), now, 0, epoch);
        nodes.put(nodeId, updated);

        // Notify listeners if node recovered
        if (previousState != null && previousState != ClusterNodeInfo.NodeState.ACTIVE) {
            notifyListeners(NodeHealthEvent.becameHealthy(nodeId, previousState, clusterCoordinator.getNodeId()));
        }

        log.debug("Received heartbeat from node {}, epoch {}", nodeId, epoch);
    }

    private void doSendHeartbeat() {
        if (!initialized.get()) {
            return;
        }

        try {
            String nodeId = clusterCoordinator.getNodeId();
            long epoch = heartbeatEpoch.incrementAndGet();
            Instant now = Instant.now();

            // Update local registration
            localNodeInfo = localNodeInfo.withHeartbeat(now, epoch);
            nodes.put(nodeId, new NodeRegistration(localNodeInfo, now, 0, epoch));

            log.trace("Sent heartbeat, epoch {}", epoch);
        } catch (Exception e) {
            log.error("Error sending heartbeat", e);
        }
    }

    private void checkNodeHealth() {
        if (!initialized.get()) {
            return;
        }

        String localNodeId = clusterCoordinator.getNodeId();
        Instant now = Instant.now();

        for (Map.Entry<String, NodeRegistration> entry : nodes.entrySet()) {
            String nodeId = entry.getKey();
            NodeRegistration reg = entry.getValue();

            // Skip self
            if (nodeId.equals(localNodeId)) {
                continue;
            }

            Duration timeSinceHeartbeat = Duration.between(reg.lastHeartbeat, now);

            if (timeSinceHeartbeat.compareTo(heartbeatInterval) > 0) {
                int missedBeats = (int) (timeSinceHeartbeat.toMillis() / heartbeatInterval.toMillis());
                NodeRegistration updated = reg.withMissedHeartbeat(missedBeats);
                nodes.put(nodeId, updated);

                // Notify based on threshold
                if (missedBeats >= FAILURE_THRESHOLD && !reg.isFailed()) {
                    log.warn("Node {} confirmed as failed after {} missed heartbeats", nodeId, missedBeats);
                    notifyListeners(NodeHealthEvent.confirmedFailed(nodeId, timeSinceHeartbeat, missedBeats, localNodeId));
                } else if (missedBeats >= SUSPICION_THRESHOLD && !reg.isSuspected()) {
                    log.warn("Node {} suspected as unhealthy after {} missed heartbeats", nodeId, missedBeats);
                    notifyListeners(NodeHealthEvent.becameUnhealthy(nodeId, timeSinceHeartbeat, missedBeats, localNodeId));
                } else if (missedBeats > 0) {
                    log.debug("Node {} missed {} heartbeats", nodeId, missedBeats);
                    notifyListeners(NodeHealthEvent.heartbeatTimeout(nodeId, timeSinceHeartbeat, missedBeats, localNodeId));
                }
            }
        }
    }

    private void notifyListeners(NodeHealthEvent event) {
        for (INodeHealthListener listener : healthListeners) {
            try {
                listener.onHealthChange(event);
            } catch (Exception e) {
                log.error("Error notifying health listener", e);
            }
        }
    }

    private String getLocalHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            log.warn("Could not determine local hostname, using localhost", e);
            return "localhost";
        }
    }
}
