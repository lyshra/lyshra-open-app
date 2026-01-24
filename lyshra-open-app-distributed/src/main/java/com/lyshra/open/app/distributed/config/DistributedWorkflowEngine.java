package com.lyshra.open.app.distributed.config;

import com.lyshra.open.app.core.engine.ILyshraOpenAppFacade;
import com.lyshra.open.app.distributed.cluster.ClusterMembershipEvent;
import com.lyshra.open.app.distributed.cluster.IClusterCoordinator;
import com.lyshra.open.app.distributed.cluster.IClusterMembershipListener;
import com.lyshra.open.app.distributed.executor.IDistributedWorkflowExecutor;
import com.lyshra.open.app.distributed.lock.IDistributedLockManager;
import com.lyshra.open.app.distributed.membership.INodeHealthListener;
import com.lyshra.open.app.distributed.membership.INodeMembershipService;
import com.lyshra.open.app.distributed.membership.NodeHealthEvent;
import com.lyshra.open.app.distributed.ownership.IWorkflowOwnershipManager;
import com.lyshra.open.app.distributed.sharding.IPartitionManager;
import com.lyshra.open.app.distributed.state.IWorkflowStateStore;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main entry point for the distributed workflow execution system.
 *
 * This class orchestrates all distributed components and provides:
 * - Coordinated lifecycle management
 * - Centralized access to all subsystems
 * - Automatic failure handling and recovery
 * - Integration with the existing Lyshra OpenApp engine
 *
 * Typical usage:
 * <pre>
 * DistributedConfig config = DistributedConfig.production("node-1", "host1", 8080, 256);
 * DistributedWorkflowEngine engine = DistributedWorkflowEngineFactory.create(config);
 *
 * // Initialize the engine
 * engine.initialize().block();
 *
 * // Submit workflows for distributed execution
 * engine.getDistributedExecutor()
 *     .submit(workflowIdentifier, context, executionId)
 *     .subscribe(result -> System.out.println("Result: " + result));
 *
 * // Graceful shutdown
 * engine.shutdown().block();
 * </pre>
 *
 * Design Patterns:
 * - Facade: Provides simplified access to complex subsystems
 * - Mediator: Coordinates interactions between components
 */
@Slf4j
public final class DistributedWorkflowEngine implements IClusterMembershipListener, INodeHealthListener {

    @Getter
    private final DistributedConfig config;

    @Getter
    private final IClusterCoordinator clusterCoordinator;

    @Getter
    private final IPartitionManager partitionManager;

    @Getter
    private final IDistributedLockManager lockManager;

    @Getter
    private final IWorkflowOwnershipManager ownershipManager;

    @Getter
    private final IWorkflowStateStore stateStore;

    @Getter
    private final INodeMembershipService membershipService;

    @Getter
    private final IDistributedWorkflowExecutor distributedExecutor;

    @Getter
    private final ILyshraOpenAppFacade localFacade;

    private final AtomicBoolean initialized;
    private final AtomicBoolean shuttingDown;

    DistributedWorkflowEngine(DistributedConfig config,
                               IClusterCoordinator clusterCoordinator,
                               IPartitionManager partitionManager,
                               IDistributedLockManager lockManager,
                               IWorkflowOwnershipManager ownershipManager,
                               IWorkflowStateStore stateStore,
                               INodeMembershipService membershipService,
                               IDistributedWorkflowExecutor distributedExecutor,
                               ILyshraOpenAppFacade localFacade) {
        this.config = Objects.requireNonNull(config);
        this.clusterCoordinator = Objects.requireNonNull(clusterCoordinator);
        this.partitionManager = Objects.requireNonNull(partitionManager);
        this.lockManager = Objects.requireNonNull(lockManager);
        this.ownershipManager = Objects.requireNonNull(ownershipManager);
        this.stateStore = Objects.requireNonNull(stateStore);
        this.membershipService = Objects.requireNonNull(membershipService);
        this.distributedExecutor = Objects.requireNonNull(distributedExecutor);
        this.localFacade = Objects.requireNonNull(localFacade);

        this.initialized = new AtomicBoolean(false);
        this.shuttingDown = new AtomicBoolean(false);
    }

    /**
     * Initializes the distributed workflow engine.
     *
     * This method:
     * 1. Initializes the cluster coordinator and joins the cluster
     * 2. Initializes the state store
     * 3. Initializes partition manager and claims partitions
     * 4. Initializes lock manager
     * 5. Initializes ownership manager
     * 6. Initializes membership service and starts heartbeats
     * 7. Initializes the distributed executor
     * 8. Registers for cluster and health events
     * 9. Triggers recovery of orphaned workflows
     *
     * @return Mono that completes when initialization is done
     */
    public Mono<Void> initialize() {
        if (!initialized.compareAndSet(false, true)) {
            log.warn("Distributed workflow engine already initialized");
            return Mono.empty();
        }

        log.info("Initializing distributed workflow engine for node: {}", config.getNodeId());

        return clusterCoordinator.initialize()
                .then(stateStore.initialize())
                .then(partitionManager.initialize())
                .then(lockManager.initialize())
                .then(ownershipManager.initialize())
                .then(membershipService.initialize())
                .then(distributedExecutor.initialize())
                .then(Mono.fromRunnable(this::registerListeners))
                .then(recoverOrphanedExecutions())
                .doOnSuccess(v -> log.info("Distributed workflow engine initialized successfully"))
                .doOnError(e -> {
                    log.error("Failed to initialize distributed workflow engine", e);
                    initialized.set(false);
                });
    }

    /**
     * Gracefully shuts down the distributed workflow engine.
     *
     * This method:
     * 1. Stops accepting new workflow executions
     * 2. Drains in-flight executions (with timeout)
     * 3. Releases all owned partitions and workflows
     * 4. Unregisters from cluster events
     * 5. Shuts down all subsystems in reverse order
     *
     * @return Mono that completes when shutdown is done
     */
    public Mono<Void> shutdown() {
        if (!shuttingDown.compareAndSet(false, true)) {
            log.warn("Distributed workflow engine already shutting down");
            return Mono.empty();
        }

        log.info("Shutting down distributed workflow engine for node: {}", config.getNodeId());

        return Mono.fromRunnable(this::unregisterListeners)
                .then(membershipService.startDraining())
                .then(distributedExecutor.shutdown())
                .then(ownershipManager.shutdown())
                .then(lockManager.shutdown())
                .then(partitionManager.shutdown())
                .then(stateStore.shutdown())
                .then(membershipService.shutdown())
                .then(clusterCoordinator.shutdown())
                .doOnSuccess(v -> {
                    initialized.set(false);
                    log.info("Distributed workflow engine shutdown complete");
                })
                .doOnError(e -> log.error("Error during distributed workflow engine shutdown", e));
    }

    /**
     * Checks if the engine is initialized and running.
     */
    public boolean isRunning() {
        return initialized.get() && !shuttingDown.get();
    }

    /**
     * Checks if this node is the cluster leader.
     */
    public boolean isLeader() {
        return clusterCoordinator.isLeader();
    }

    /**
     * Returns the unique identifier of this node.
     */
    public String getNodeId() {
        return config.getNodeId();
    }

    /**
     * Returns the number of partitions owned by this node.
     */
    public int getLocalPartitionCount() {
        return partitionManager.getLocalPartitions().size();
    }

    /**
     * Returns the number of workflows owned by this node.
     */
    public int getLocalWorkflowCount() {
        return ownershipManager.getLocalOwnershipCount();
    }

    // IClusterMembershipListener implementation

    @Override
    public void onMembershipChange(ClusterMembershipEvent event) {
        log.info("Cluster membership changed: {} for node {}", event.getEventType(), event.getAffectedNodeId());

        if (event.isNodeDeparture()) {
            // Handle node failure - trigger recovery if we are the leader
            if (clusterCoordinator.isLeader() && config.isAutoRecoveryEnabled()) {
                log.info("Node {} departed, triggering recovery", event.getAffectedNodeId());
                distributedExecutor.recoverOrphanedExecutions(event.getAffectedNodeId())
                        .subscribe(
                                count -> log.info("Recovered {} orphaned executions from node {}",
                                        count, event.getAffectedNodeId()),
                                error -> log.error("Error recovering executions from node {}",
                                        event.getAffectedNodeId(), error)
                        );
            }
        }
    }

    // INodeHealthListener implementation

    @Override
    public void onHealthChange(NodeHealthEvent event) {
        log.debug("Node health changed: {} for node {}", event.getEventType(), event.getNodeId());

        if (event.getEventType() == NodeHealthEvent.EventType.CONFIRMED_FAILED) {
            if (clusterCoordinator.isLeader() && config.isAutoRecoveryEnabled()) {
                log.warn("Node {} confirmed failed, revoking ownership", event.getNodeId());
                ownershipManager.revokeNodeOwnership(event.getNodeId())
                        .subscribe(
                                revoked -> log.info("Revoked ownership of {} workflows from failed node {}",
                                        revoked.size(), event.getNodeId()),
                                error -> log.error("Error revoking ownership from node {}",
                                        event.getNodeId(), error)
                        );
            }
        }
    }

    // Private helper methods

    private void registerListeners() {
        clusterCoordinator.addMembershipListener(this);
        membershipService.addHealthListener(this);
        log.debug("Registered cluster and health listeners");
    }

    private void unregisterListeners() {
        clusterCoordinator.removeMembershipListener(this);
        membershipService.removeHealthListener(this);
        log.debug("Unregistered cluster and health listeners");
    }

    private Mono<Void> recoverOrphanedExecutions() {
        if (!config.isAutoRecoveryEnabled()) {
            return Mono.empty();
        }

        return stateStore.findStaleExecutions(config.getStaleExecutionThreshold())
                .filter(state -> state.needsRecovery() || state.isActive())
                .flatMap(state -> {
                    if (partitionManager.shouldHandleWorkflow(state.getExecutionKey())) {
                        log.info("Found stale execution to recover: {}", state.getExecutionKey());
                        return distributedExecutor.resume(state.getExecutionKey())
                                .doOnError(e -> log.warn("Failed to recover execution {}: {}",
                                        state.getExecutionKey(), e.getMessage()))
                                .onErrorResume(e -> Mono.empty())
                                .then();
                    }
                    return Mono.empty();
                })
                .then();
    }
}
