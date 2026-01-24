package com.lyshra.open.app.distributed.membership;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.MemoryMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Service responsible for managing the local node's registration and lifecycle.
 *
 * This service handles:
 * - Generating/loading the node identity
 * - Registering the node with the cluster
 * - Sending periodic heartbeats
 * - Handling graceful shutdown with draining
 * - Monitoring resource usage
 * - Responding to status changes
 *
 * The service automatically:
 * - Generates a unique node ID on first start
 * - Persists node ID to allow same ID after restart (optional)
 * - Sends heartbeats at configured intervals
 * - Reports CPU and memory metrics
 * - Transitions through proper status states
 *
 * Thread Safety: This class is thread-safe.
 *
 * Usage:
 * <pre>
 * NodeRegistrationService service = NodeRegistrationService.builder()
 *     .registry(registry)
 *     .configuration(config)
 *     .build();
 *
 * // Start the service (registers and starts heartbeats)
 * service.start().block();
 *
 * // Get the node ID
 * String nodeId = service.getNodeId();
 *
 * // Graceful shutdown
 * service.shutdown().block();
 * </pre>
 */
@Slf4j
public class NodeRegistrationService {

    private final INodeRegistry registry;
    private final NodeConfiguration configuration;
    private final CopyOnWriteArrayList<Consumer<NodeInfo>> nodeInfoListeners;

    @Getter
    private volatile NodeIdentity identity;

    @Getter
    private volatile NodeInfo localNodeInfo;

    private final AtomicBoolean running;
    private final AtomicBoolean shuttingDown;
    private final AtomicReference<Disposable> heartbeatDisposable;
    private final AtomicInteger workflowCount;
    private final AtomicInteger partitionCount;

    private final OperatingSystemMXBean osMxBean;
    private final MemoryMXBean memoryMxBean;

    private NodeRegistrationService(Builder builder) {
        this.registry = Objects.requireNonNull(builder.registry, "registry must not be null");
        this.configuration = builder.configuration != null ?
                builder.configuration : NodeConfiguration.defaults();
        this.nodeInfoListeners = new CopyOnWriteArrayList<>();
        this.running = new AtomicBoolean(false);
        this.shuttingDown = new AtomicBoolean(false);
        this.heartbeatDisposable = new AtomicReference<>();
        this.workflowCount = new AtomicInteger(0);
        this.partitionCount = new AtomicInteger(0);

        // Get MXBeans for metrics
        this.osMxBean = ManagementFactory.getOperatingSystemMXBean();
        this.memoryMxBean = ManagementFactory.getMemoryMXBean();

        // Generate or use provided identity
        if (builder.identity != null) {
            this.identity = builder.identity;
        } else if (builder.nodeIdPrefix != null) {
            this.identity = NodeIdentity.generate(builder.nodeIdPrefix);
        } else {
            this.identity = NodeIdentity.generate(configuration.getNodeIdPrefix());
        }

        // Apply port if configured
        if (configuration.getPort() > 0) {
            this.identity = identity.withPort(configuration.getPort());
        }

        // Apply location if configured
        if (configuration.getDatacenter() != null || configuration.getAvailabilityZone() != null) {
            this.identity = identity.withLocation(
                    configuration.getDatacenter(),
                    configuration.getAvailabilityZone()
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    // ========== Lifecycle ==========

    /**
     * Starts the registration service.
     *
     * This will:
     * 1. Register the node with the cluster
     * 2. Start the heartbeat scheduler
     * 3. Transition to ACTIVE status
     *
     * @return Mono that completes when the service is started
     */
    public Mono<Void> start() {
        return Mono.defer(() -> {
            if (!running.compareAndSet(false, true)) {
                return Mono.error(new IllegalStateException("Service is already running"));
            }

            log.info("Starting NodeRegistrationService for node: {}", identity.getNodeId());

            return registry.register(identity, buildRegistrationOptions())
                    .flatMap(result -> {
                        if (!result.isSuccess()) {
                            running.set(false);
                            return Mono.error(new RuntimeException(
                                    "Failed to register node: " + result.errorMessage()));
                        }

                        localNodeInfo = result.nodeInfo();

                        // Start heartbeat scheduler
                        startHeartbeatScheduler();

                        // Transition to active after a short delay
                        return Mono.delay(Duration.ofMillis(100))
                                .then(registry.updateStatus(
                                        identity.getNodeId(),
                                        NodeStatus.ACTIVE,
                                        "Node started successfully"))
                                .then(refreshLocalNodeInfo());
                    })
                    .doOnSuccess(v -> log.info("NodeRegistrationService started for node: {}",
                            identity.getNodeId()))
                    .doOnError(e -> {
                        running.set(false);
                        log.error("Failed to start NodeRegistrationService", e);
                    });
        });
    }

    /**
     * Initiates graceful shutdown.
     *
     * This will:
     * 1. Mark the node as draining
     * 2. Wait for work to complete (up to timeout)
     * 3. Deregister from the cluster
     *
     * @return Mono that completes when shutdown is done
     */
    public Mono<Void> shutdown() {
        return shutdown(configuration.getShutdownTimeout());
    }

    /**
     * Initiates graceful shutdown with custom timeout.
     *
     * @param timeout maximum time to wait for draining
     * @return Mono that completes when shutdown is done
     */
    public Mono<Void> shutdown(Duration timeout) {
        return Mono.defer(() -> {
            if (!running.get()) {
                return Mono.empty();
            }

            if (!shuttingDown.compareAndSet(false, true)) {
                return Mono.empty(); // Already shutting down
            }

            log.info("Initiating graceful shutdown for node: {}", identity.getNodeId());

            // Stop heartbeat scheduler
            stopHeartbeatScheduler();

            return registry.startDraining(identity.getNodeId(), "Graceful shutdown")
                    .then(waitForDraining(timeout))
                    .then(registry.deregister(identity.getNodeId(), "Node shutdown"))
                    .doOnSuccess(v -> {
                        running.set(false);
                        log.info("Node {} shutdown complete", identity.getNodeId());
                    })
                    .doOnError(e -> log.error("Error during shutdown", e))
                    .onErrorResume(e -> {
                        running.set(false);
                        return Mono.empty();
                    });
        });
    }

    /**
     * Checks if the service is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Checks if the service is shutting down.
     *
     * @return true if shutting down
     */
    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    /**
     * Gets the node ID.
     *
     * @return the node ID
     */
    public String getNodeId() {
        return identity.getNodeId();
    }

    // ========== Metrics Reporting ==========

    /**
     * Increments the workflow count.
     */
    public void incrementWorkflowCount() {
        workflowCount.incrementAndGet();
    }

    /**
     * Decrements the workflow count.
     */
    public void decrementWorkflowCount() {
        workflowCount.decrementAndGet();
    }

    /**
     * Sets the workflow count.
     *
     * @param count the new count
     */
    public void setWorkflowCount(int count) {
        workflowCount.set(count);
    }

    /**
     * Gets the current workflow count.
     *
     * @return the workflow count
     */
    public int getWorkflowCount() {
        return workflowCount.get();
    }

    /**
     * Increments the partition count.
     */
    public void incrementPartitionCount() {
        partitionCount.incrementAndGet();
    }

    /**
     * Decrements the partition count.
     */
    public void decrementPartitionCount() {
        partitionCount.decrementAndGet();
    }

    /**
     * Sets the partition count.
     *
     * @param count the new count
     */
    public void setPartitionCount(int count) {
        partitionCount.set(count);
    }

    /**
     * Gets the current partition count.
     *
     * @return the partition count
     */
    public int getPartitionCount() {
        return partitionCount.get();
    }

    // ========== Listeners ==========

    /**
     * Adds a listener for local node info changes.
     *
     * @param listener the listener
     */
    public void addNodeInfoListener(Consumer<NodeInfo> listener) {
        nodeInfoListeners.add(listener);
    }

    /**
     * Removes a node info listener.
     *
     * @param listener the listener to remove
     */
    public void removeNodeInfoListener(Consumer<NodeInfo> listener) {
        nodeInfoListeners.remove(listener);
    }

    // ========== Internal Methods ==========

    private INodeRegistry.RegistrationOptions buildRegistrationOptions() {
        return INodeRegistry.RegistrationOptions.builder()
                .capabilities(configuration.getCapabilities())
                .maxWorkflows(configuration.getMaxWorkflows())
                .maxPartitions(configuration.getMaxPartitions())
                .leaderEligible(configuration.isLeaderEligible())
                .leaderPriority(configuration.getLeaderPriority())
                .loadWeight(configuration.getLoadWeight())
                .metadata(configuration.getMetadata())
                .build();
    }

    private void startHeartbeatScheduler() {
        Duration interval = configuration.getHeartbeatInterval();

        Disposable disposable = Flux.interval(interval, interval)
                .flatMap(tick -> sendHeartbeat())
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        success -> { /* Heartbeat sent */ },
                        error -> log.error("Error in heartbeat scheduler", error)
                );

        heartbeatDisposable.set(disposable);
        log.debug("Heartbeat scheduler started with interval: {}", interval);
    }

    private void stopHeartbeatScheduler() {
        Disposable disposable = heartbeatDisposable.getAndSet(null);
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
            log.debug("Heartbeat scheduler stopped");
        }
    }

    private Mono<Boolean> sendHeartbeat() {
        if (!running.get() || shuttingDown.get()) {
            return Mono.just(false);
        }

        INodeRegistry.HeartbeatMetrics metrics = collectMetrics();

        return registry.heartbeat(identity.getNodeId(), metrics)
                .doOnNext(success -> {
                    if (success) {
                        refreshLocalNodeInfo().subscribe();
                    }
                })
                .doOnError(e -> log.warn("Failed to send heartbeat", e))
                .onErrorReturn(false);
    }

    private INodeRegistry.HeartbeatMetrics collectMetrics() {
        double cpuLoad = getCpuLoad();
        double memoryUsage = getMemoryUsage();

        return new INodeRegistry.HeartbeatMetrics(
                cpuLoad,
                memoryUsage,
                workflowCount.get(),
                partitionCount.get(),
                0, // processedCount - could be tracked
                0  // errorCount - could be tracked
        );
    }

    private double getCpuLoad() {
        try {
            double load = osMxBean.getSystemLoadAverage();
            if (load < 0) {
                // System load average not available
                return 0.5; // Default to 50%
            }
            // Normalize by number of processors
            int processors = Runtime.getRuntime().availableProcessors();
            return Math.min(1.0, load / processors);
        } catch (Exception e) {
            return 0.5;
        }
    }

    private double getMemoryUsage() {
        try {
            long used = memoryMxBean.getHeapMemoryUsage().getUsed();
            long max = memoryMxBean.getHeapMemoryUsage().getMax();
            if (max <= 0) {
                return 0.5;
            }
            return (double) used / max;
        } catch (Exception e) {
            return 0.5;
        }
    }

    private Mono<Void> refreshLocalNodeInfo() {
        return registry.getNode(identity.getNodeId())
                .doOnNext(opt -> {
                    opt.ifPresent(info -> {
                        localNodeInfo = info;
                        notifyListeners(info);
                    });
                })
                .then();
    }

    private void notifyListeners(NodeInfo info) {
        for (Consumer<NodeInfo> listener : nodeInfoListeners) {
            try {
                listener.accept(info);
            } catch (Exception e) {
                log.error("Error notifying node info listener", e);
            }
        }
    }

    private Mono<Void> waitForDraining(Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);

        return Mono.defer(() -> {
            if (workflowCount.get() <= 0 || Instant.now().isAfter(deadline)) {
                return Mono.empty();
            }
            return Mono.delay(Duration.ofMillis(500))
                    .then(waitForDraining(Duration.between(Instant.now(), deadline)));
        });
    }

    // ========== Builder ==========

    public static class Builder {
        private INodeRegistry registry;
        private NodeConfiguration configuration;
        private NodeIdentity identity;
        private String nodeIdPrefix;

        public Builder registry(INodeRegistry registry) {
            this.registry = registry;
            return this;
        }

        public Builder configuration(NodeConfiguration configuration) {
            this.configuration = configuration;
            return this;
        }

        public Builder identity(NodeIdentity identity) {
            this.identity = identity;
            return this;
        }

        public Builder nodeIdPrefix(String nodeIdPrefix) {
            this.nodeIdPrefix = nodeIdPrefix;
            return this;
        }

        public NodeRegistrationService build() {
            return new NodeRegistrationService(this);
        }
    }
}
