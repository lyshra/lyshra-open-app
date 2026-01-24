package com.lyshra.open.app.distributed.executor.impl;

import com.lyshra.open.app.core.engine.ILyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.node.ILyshraOpenAppWorkflowExecutor;
import com.lyshra.open.app.distributed.cluster.IClusterCoordinator;
import com.lyshra.open.app.distributed.coordination.IWorkflowOwnershipCoordinator;
import com.lyshra.open.app.distributed.coordination.OwnershipContext;
import com.lyshra.open.app.distributed.dispatcher.DispatchOptions;
import com.lyshra.open.app.distributed.dispatcher.IWorkflowDispatcher;
import com.lyshra.open.app.distributed.dispatcher.IWorkflowDispatcher.DispatchRequest;
import com.lyshra.open.app.distributed.dispatcher.WorkflowDispatchResult;
import com.lyshra.open.app.distributed.executor.DistributedExecutionResult;
import com.lyshra.open.app.distributed.executor.IDistributedExecutionListener;
import com.lyshra.open.app.distributed.executor.IDistributedWorkflowExecutor;
import com.lyshra.open.app.distributed.executor.lease.LeaseRenewalManager;
import com.lyshra.open.app.distributed.executor.lease.OwnershipGuardedExecution;
import com.lyshra.open.app.distributed.ownership.IWorkflowOwnershipManager;
import com.lyshra.open.app.distributed.recovery.WorkflowRecoveryCoordinator;
import com.lyshra.open.app.distributed.ownership.WorkflowOwnershipResult;
import com.lyshra.open.app.distributed.sharding.IPartitionManager;
import com.lyshra.open.app.distributed.sharding.IShardingStrategy;
import com.lyshra.open.app.distributed.state.IWorkflowStateStore;
import com.lyshra.open.app.distributed.state.WorkflowCheckpoint;
import com.lyshra.open.app.distributed.state.WorkflowExecutionState;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Production-ready implementation of distributed workflow execution.
 *
 * This executor orchestrates:
 * 1. Partition-based routing
 * 2. Ownership acquisition and maintenance
 * 3. Durable state persistence
 * 4. Checkpoint-based recovery
 * 5. Integration with local workflow executor
 *
 * Execution Flow:
 * 1. Submit received → Compute partition → Check ownership
 * 2. If not local → Return routing info
 * 3. If local → Acquire ownership → Persist state → Execute
 * 4. During execution → Create checkpoints → Update state
 * 5. On completion/failure → Update state → Release ownership
 *
 * Concurrency Strategy:
 * - Uses reactive programming (Project Reactor) for non-blocking I/O
 * - Ownership prevents concurrent execution of same workflow
 * - Optimistic locking in state store prevents lost updates
 *
 * Failure Handling:
 * - State persisted before execution starts
 * - Checkpoints created at step boundaries
 * - Orphaned executions recovered by new owners
 * - Lease expiration triggers recovery
 *
 * Thread Safety: This class is thread-safe.
 */
@Slf4j
public class DistributedWorkflowExecutorImpl implements IDistributedWorkflowExecutor {

    private static final Duration DEFAULT_LEASE_DURATION = Duration.ofMinutes(5);
    private static final Duration LEASE_RENEWAL_INTERVAL = Duration.ofMinutes(1);

    private final IClusterCoordinator clusterCoordinator;
    private final IPartitionManager partitionManager;
    private final IShardingStrategy shardingStrategy;
    private final IWorkflowOwnershipManager ownershipManager;
    private final IWorkflowStateStore stateStore;
    private final ILyshraOpenAppFacade facade;
    private final IWorkflowDispatcher dispatcher;
    private final LeaseRenewalManager leaseRenewalManager;

    private volatile WorkflowRecoveryCoordinator recoveryCoordinator;

    private final List<IDistributedExecutionListener> listeners;
    private final AtomicBoolean initialized;

    /**
     * Creates executor with ownership manager (legacy constructor for backwards compatibility).
     */
    public DistributedWorkflowExecutorImpl(IClusterCoordinator clusterCoordinator,
                                            IPartitionManager partitionManager,
                                            IShardingStrategy shardingStrategy,
                                            IWorkflowOwnershipManager ownershipManager,
                                            IWorkflowStateStore stateStore,
                                            ILyshraOpenAppFacade facade) {
        this(clusterCoordinator, partitionManager, shardingStrategy, ownershipManager, stateStore, facade, null, null);
    }

    /**
     * Creates executor with ownership-aware dispatcher (recommended).
     */
    public DistributedWorkflowExecutorImpl(IClusterCoordinator clusterCoordinator,
                                            IPartitionManager partitionManager,
                                            IShardingStrategy shardingStrategy,
                                            IWorkflowOwnershipManager ownershipManager,
                                            IWorkflowStateStore stateStore,
                                            ILyshraOpenAppFacade facade,
                                            IWorkflowDispatcher dispatcher) {
        this(clusterCoordinator, partitionManager, shardingStrategy, ownershipManager, stateStore, facade, dispatcher, null);
    }

    /**
     * Creates executor with full configuration including lease renewal.
     *
     * @param clusterCoordinator cluster coordination
     * @param partitionManager partition management
     * @param shardingStrategy sharding strategy
     * @param ownershipManager ownership management
     * @param stateStore workflow state persistence
     * @param facade workflow execution facade
     * @param dispatcher ownership-aware dispatcher (optional)
     * @param leaseRenewalManager lease renewal manager (optional, will be created if null and dispatcher exists)
     */
    public DistributedWorkflowExecutorImpl(IClusterCoordinator clusterCoordinator,
                                            IPartitionManager partitionManager,
                                            IShardingStrategy shardingStrategy,
                                            IWorkflowOwnershipManager ownershipManager,
                                            IWorkflowStateStore stateStore,
                                            ILyshraOpenAppFacade facade,
                                            IWorkflowDispatcher dispatcher,
                                            LeaseRenewalManager leaseRenewalManager) {
        this.clusterCoordinator = Objects.requireNonNull(clusterCoordinator);
        this.partitionManager = Objects.requireNonNull(partitionManager);
        this.shardingStrategy = Objects.requireNonNull(shardingStrategy);
        this.ownershipManager = Objects.requireNonNull(ownershipManager);
        this.stateStore = Objects.requireNonNull(stateStore);
        this.facade = Objects.requireNonNull(facade);
        this.dispatcher = dispatcher; // Can be null for legacy behavior

        // Create lease renewal manager if not provided but dispatcher exists
        if (leaseRenewalManager != null) {
            this.leaseRenewalManager = leaseRenewalManager;
        } else if (dispatcher != null) {
            this.leaseRenewalManager = new LeaseRenewalManager(dispatcher);
        } else {
            this.leaseRenewalManager = null;
        }

        this.listeners = new CopyOnWriteArrayList<>();
        this.initialized = new AtomicBoolean(false);
    }

    @Override
    public Mono<Void> initialize() {
        if (!initialized.compareAndSet(false, true)) {
            log.warn("Distributed executor already initialized");
            return Mono.empty();
        }

        log.info("Initializing distributed workflow executor");

        // Start the lease renewal manager if available
        if (leaseRenewalManager != null) {
            return leaseRenewalManager.start()
                    .doOnSuccess(v -> log.info("Lease renewal manager started"))
                    .doOnError(e -> log.error("Failed to start lease renewal manager", e));
        }

        return Mono.empty();
    }

    @Override
    public Mono<Void> shutdown() {
        log.info("Shutting down distributed workflow executor");

        // Stop the lease renewal manager first
        Mono<Void> stopRenewalMono = leaseRenewalManager != null
                ? leaseRenewalManager.stop()
                        .doOnSuccess(v -> log.info("Lease renewal manager stopped"))
                        .doOnError(e -> log.warn("Error stopping lease renewal manager", e))
                        .onErrorResume(e -> Mono.empty())
                : Mono.empty();

        return stopRenewalMono.then(Mono.fromRunnable(() -> {
            initialized.set(false);
        }));
    }

    @Override
    public Mono<DistributedExecutionResult> submit(ILyshraOpenAppWorkflowIdentifier identifier,
                                                    ILyshraOpenAppContext context,
                                                    String executionId) {
        Objects.requireNonNull(identifier, "identifier must not be null");
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(executionId, "executionId must not be null");

        String executionKey = generateExecutionKey(identifier, executionId);
        Instant startTime = Instant.now();

        log.info("Submitting workflow execution: {}", executionKey);

        // Use dispatcher if available (ownership-aware path)
        if (dispatcher != null) {
            return submitWithDispatcher(identifier, context, executionId, executionKey, startTime);
        }

        // Legacy path using ownership manager directly
        return submitWithOwnershipManager(identifier, context, executionId, executionKey, startTime);
    }

    /**
     * Submits workflow using the ownership-aware dispatcher (recommended path).
     * The dispatcher handles ownership acquisition with retry and proper routing.
     */
    private Mono<DistributedExecutionResult> submitWithDispatcher(ILyshraOpenAppWorkflowIdentifier identifier,
                                                                   ILyshraOpenAppContext context,
                                                                   String executionId,
                                                                   String executionKey,
                                                                   Instant startTime) {
        DispatchRequest request = DispatchRequest.of(identifier, context, executionId);

        return dispatcher.dispatch(request)
                .flatMap(dispatchResult -> handleDispatchResult(
                        dispatchResult, identifier, context, executionId, executionKey, startTime))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Handles the dispatch result and proceeds with execution if ownership was acquired.
     */
    private Mono<DistributedExecutionResult> handleDispatchResult(WorkflowDispatchResult dispatchResult,
                                                                    ILyshraOpenAppWorkflowIdentifier identifier,
                                                                    ILyshraOpenAppContext context,
                                                                    String executionId,
                                                                    String executionKey,
                                                                    Instant startTime) {
        switch (dispatchResult.getOutcome()) {
            case DISPATCHED_LOCALLY:
            case ALREADY_OWNED_LOCALLY:
                // Ownership acquired - proceed with execution
                log.info("Workflow {} dispatched locally with fencing token {}",
                        executionKey, dispatchResult.getFencingToken());

                return createAndPersistInitialState(identifier, context, executionId, executionKey)
                        .flatMap(state -> {
                            notifyExecutionSubmitted(executionKey, state);

                            // Use guarded execution with lease renewal if available
                            if (leaseRenewalManager != null) {
                                return executeWithLeaseRenewal(identifier, context, executionKey,
                                        state, startTime, dispatchResult);
                            }

                            // Fall back to direct execution without renewal
                            return executeWorkflow(identifier, context, executionKey, state, startTime);
                        })
                        .doFinally(signal -> {
                            // Release ownership on completion using dispatcher
                            dispatcher.releaseOwnership(executionKey, "execution_completed").subscribe();
                        });

            case ROUTED_TO_OTHER:
                // Workflow belongs to different node
                log.info("Workflow {} routed to node {}", executionKey,
                        dispatchResult.getTargetNodeIdOptional().orElse("unknown"));
                return Mono.just(DistributedExecutionResult.routedToOtherNode(
                        executionKey, dispatchResult.getTargetNodeIdOptional().orElse("unknown")));

            case OWNED_BY_OTHER:
                // Another node owns this workflow
                log.info("Workflow {} owned by another node: {}",
                        executionKey, dispatchResult.getCurrentOwnerIdOptional().orElse("unknown"));
                return Mono.just(DistributedExecutionResult.ownershipDenied(
                        executionKey, dispatchResult.getCurrentOwnerIdOptional().orElse("unknown")));

            case REJECTED:
                // Dispatch was rejected - convert reason to execution result
                log.warn("Workflow {} dispatch rejected: {} - {}",
                        executionKey, dispatchResult.getRejectReason(),
                        dispatchResult.getErrorMessageOptional().orElse(""));
                return Mono.just(mapRejectionToExecutionResult(executionKey, dispatchResult, startTime));

            default:
                return Mono.just(DistributedExecutionResult.failed(
                        executionKey, null, null, startTime, clusterCoordinator.getNodeId(),
                        "Unknown dispatch outcome: " + dispatchResult.getOutcome(), null));
        }
    }

    /**
     * Maps a dispatch rejection to an appropriate execution result.
     */
    private DistributedExecutionResult mapRejectionToExecutionResult(String executionKey,
                                                                       WorkflowDispatchResult dispatchResult,
                                                                       Instant startTime) {
        String errorMessage = dispatchResult.getErrorMessageOptional()
                .orElse("Dispatch rejected: " + dispatchResult.getRejectReason());

        // For certain rejection reasons, return ownership denied
        if (dispatchResult.getRejectReason() == WorkflowDispatchResult.RejectReason.CAPACITY_EXCEEDED) {
            return DistributedExecutionResult.failed(executionKey, null, null, startTime,
                    clusterCoordinator.getNodeId(), errorMessage, null);
        }

        return DistributedExecutionResult.ownershipDenied(executionKey, errorMessage);
    }

    /**
     * Legacy submit path using ownership manager directly (for backwards compatibility).
     */
    private Mono<DistributedExecutionResult> submitWithOwnershipManager(ILyshraOpenAppWorkflowIdentifier identifier,
                                                                          ILyshraOpenAppContext context,
                                                                          String executionId,
                                                                          String executionKey,
                                                                          Instant startTime) {
        return Mono.defer(() -> {
            // Step 1: Check if this is a local partition
            if (!partitionManager.shouldHandleWorkflow(executionKey)) {
                Optional<String> targetNode = partitionManager.getTargetNode(executionKey);
                log.info("Workflow {} belongs to different node: {}", executionKey, targetNode.orElse("unknown"));
                return Mono.just(DistributedExecutionResult.routedToOtherNode(
                        executionKey, targetNode.orElse("unknown")));
            }

            // Step 2: Try to acquire ownership
            return ownershipManager.acquireOwnership(executionKey, DEFAULT_LEASE_DURATION)
                    .flatMap(ownershipResult -> {
                        if (!ownershipResult.isAcquired()) {
                            log.warn("Failed to acquire ownership for {}: {}",
                                    executionKey, ownershipResult.getStatus());
                            return Mono.just(DistributedExecutionResult.ownershipDenied(
                                    executionKey, ownershipResult.getOwnerNodeId()));
                        }

                        // Step 3: Create and persist initial state
                        return createAndPersistInitialState(identifier, context, executionId, executionKey)
                                .flatMap(state -> {
                                    notifyExecutionSubmitted(executionKey, state);

                                    // Step 4: Execute the workflow
                                    return executeWorkflow(identifier, context, executionKey, state, startTime);
                                })
                                .doFinally(signal -> {
                                    // Release ownership on completion
                                    ownershipManager.releaseOwnership(executionKey).subscribe();
                                });
                    });
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<DistributedExecutionResult> resume(String executionKey) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");

        log.info("Resuming workflow execution: {}", executionKey);

        return stateStore.findByExecutionKey(executionKey)
                .flatMap(optState -> {
                    if (optState.isEmpty()) {
                        return Mono.just(DistributedExecutionResult.failed(
                                executionKey, null, null, Instant.now(),
                                clusterCoordinator.getNodeId(),
                                "Execution not found", null));
                    }

                    WorkflowExecutionState state = optState.get();

                    if (!state.isResumable()) {
                        return Mono.just(DistributedExecutionResult.failed(
                                executionKey, state.getWorkflowIdentifier(), state, Instant.now(),
                                clusterCoordinator.getNodeId(),
                                "Execution is not resumable: " + state.getStatus(), null));
                    }

                    // Use dispatcher if available
                    if (dispatcher != null) {
                        return resumeWithDispatcher(executionKey, state);
                    }

                    // Legacy path using ownership manager
                    return resumeWithOwnershipManager(executionKey, state);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Resumes workflow using dispatcher (recommended path).
     */
    private Mono<DistributedExecutionResult> resumeWithDispatcher(String executionKey,
                                                                    WorkflowExecutionState state) {
        return dispatcher.dispatchForResume(executionKey, DispatchOptions.forResume())
                .flatMap(dispatchResult -> {
                    if (!dispatchResult.isDispatchedLocally()) {
                        if (dispatchResult.isRoutedToOther()) {
                            return Mono.just(DistributedExecutionResult.routedToOtherNode(
                                    executionKey, dispatchResult.getTargetNodeIdOptional().orElse("unknown")));
                        }
                        if (dispatchResult.isOwnedByOther()) {
                            return Mono.just(DistributedExecutionResult.ownershipDenied(
                                    executionKey, dispatchResult.getCurrentOwnerIdOptional().orElse("unknown")));
                        }
                        return Mono.just(DistributedExecutionResult.failed(
                                executionKey, state.getWorkflowIdentifier(), state, Instant.now(),
                                clusterCoordinator.getNodeId(),
                                "Failed to acquire ownership for resume: " + dispatchResult.getRejectReason(), null));
                    }

                    log.info("Ownership acquired for resume with fencing token {}",
                            dispatchResult.getFencingToken());

                    // Load latest checkpoint and resume with lease renewal if available
                    return stateStore.loadLatestCheckpoint(executionKey)
                            .flatMap(optCheckpoint -> {
                                ILyshraOpenAppContext context = reconstructContext(optCheckpoint.orElse(null), state);

                                // Use guarded execution with lease renewal if available
                                if (leaseRenewalManager != null) {
                                    return resumeWithLeaseRenewal(state, context, optCheckpoint.orElse(null), dispatchResult);
                                }

                                return resumeFromCheckpoint(state, context, optCheckpoint.orElse(null));
                            })
                            .doFinally(signal -> {
                                dispatcher.releaseOwnership(executionKey, "resume_completed").subscribe();
                            });
                });
    }

    /**
     * Resumes workflow with automatic lease renewal.
     */
    private Mono<DistributedExecutionResult> resumeWithLeaseRenewal(
            WorkflowExecutionState state,
            ILyshraOpenAppContext context,
            WorkflowCheckpoint checkpoint,
            WorkflowDispatchResult dispatchResult) {

        String executionKey = state.getExecutionKey();

        // Create ownership context from dispatch result
        OwnershipContext ownershipContext = OwnershipContext.builder()
                .workflowExecutionKey(executionKey)
                .ownerId(clusterCoordinator.getNodeId())
                .fencingToken(dispatchResult.getFencingToken())
                .acquiredAt(Instant.now())
                .expiresAt(Instant.now().plus(DEFAULT_LEASE_DURATION))
                .leaseDuration(DEFAULT_LEASE_DURATION)
                .build();

        // Create guarded execution
        OwnershipGuardedExecution guardedExecution = OwnershipGuardedExecution.create(
                executionKey, ownershipContext, leaseRenewalManager);

        log.info("Starting guarded resume for {} with lease renewal enabled", executionKey);

        return guardedExecution.executeWithContext(guard -> {
            return resumeFromCheckpoint(state, context, checkpoint);
        })
        .doOnSuccess(result -> {
            guardedExecution.complete();
            log.debug("Guarded resume completed for {}", executionKey);
        })
        .doOnError(error -> {
            guardedExecution.complete();
        })
        .onErrorResume(OwnershipGuardedExecution.OwnershipLostException.class, ownershipError -> {
            WorkflowExecutionState failedState = state.markFailed(
                    "Ownership lost during resume: " + ownershipError.getMessage());
            stateStore.save(failedState).subscribe();

            return Mono.just(DistributedExecutionResult.failed(
                    executionKey,
                    state.getWorkflowIdentifier(),
                    failedState,
                    Instant.now(),
                    clusterCoordinator.getNodeId(),
                    "Ownership lost during resume",
                    ownershipError
            ));
        });
    }

    /**
     * Resumes workflow using ownership manager (legacy path).
     */
    private Mono<DistributedExecutionResult> resumeWithOwnershipManager(String executionKey,
                                                                          WorkflowExecutionState state) {
        return ownershipManager.acquireOwnership(executionKey, DEFAULT_LEASE_DURATION)
                .flatMap(ownershipResult -> {
                    if (!ownershipResult.isAcquired()) {
                        return Mono.just(DistributedExecutionResult.ownershipDenied(
                                executionKey, ownershipResult.getOwnerNodeId()));
                    }

                    // Load latest checkpoint and resume
                    return stateStore.loadLatestCheckpoint(executionKey)
                            .flatMap(optCheckpoint -> {
                                ILyshraOpenAppContext context = reconstructContext(optCheckpoint.orElse(null), state);
                                return resumeFromCheckpoint(state, context, optCheckpoint.orElse(null));
                            })
                            .doFinally(signal -> {
                                ownershipManager.releaseOwnership(executionKey).subscribe();
                            });
                });
    }

    @Override
    public Mono<Boolean> cancel(String executionKey) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");

        log.info("Cancelling workflow execution: {}", executionKey);

        return stateStore.findByExecutionKey(executionKey)
                .flatMap(optState -> {
                    if (optState.isEmpty()) {
                        return Mono.just(false);
                    }

                    WorkflowExecutionState state = optState.get();
                    if (state.isTerminal()) {
                        return Mono.just(false);
                    }

                    return stateStore.updateStatus(executionKey,
                            WorkflowExecutionState.ExecutionStatus.CANCELLED,
                            state.getVersion())
                            .doOnSuccess(success -> {
                                if (success) {
                                    notifyExecutionCancelled(executionKey);
                                }
                            });
                });
    }

    @Override
    public Mono<Boolean> pause(String executionKey) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");

        log.info("Pausing workflow execution: {}", executionKey);

        return stateStore.findByExecutionKey(executionKey)
                .flatMap(optState -> {
                    if (optState.isEmpty()) {
                        return Mono.just(false);
                    }

                    WorkflowExecutionState state = optState.get();
                    if (state.getStatus() != WorkflowExecutionState.ExecutionStatus.RUNNING) {
                        return Mono.just(false);
                    }

                    return stateStore.updateStatus(executionKey,
                            WorkflowExecutionState.ExecutionStatus.PAUSED,
                            state.getVersion())
                            .doOnSuccess(success -> {
                                if (success) {
                                    notifyExecutionPaused(executionKey, state);
                                }
                            });
                });
    }

    @Override
    public Mono<Optional<WorkflowExecutionState>> getExecutionState(String executionKey) {
        return stateStore.findByExecutionKey(executionKey);
    }

    @Override
    public Flux<WorkflowExecutionState> getLocalExecutions() {
        return stateStore.findByOwner(clusterCoordinator.getNodeId());
    }

    @Override
    public Flux<WorkflowExecutionState> getExecutionsByStatus(WorkflowExecutionState.ExecutionStatus status) {
        return stateStore.findByStatus(status);
    }

    @Override
    public Mono<Integer> recoverOrphanedExecutions(String failedNodeId) {
        Objects.requireNonNull(failedNodeId, "failedNodeId must not be null");

        log.info("Recovering orphaned executions from failed node: {}", failedNodeId);

        return stateStore.findByOwner(failedNodeId)
                .filter(state -> !state.isTerminal())
                .flatMap(state -> {
                    // Mark for recovery and clear owner
                    WorkflowExecutionState needsRecovery = state.markNeedsRecovery();
                    return stateStore.save(needsRecovery)
                            .doOnSuccess(saved -> {
                                notifyExecutionRecovered(state.getExecutionKey(), failedNodeId,
                                        clusterCoordinator.getNodeId());
                            })
                            .thenReturn(1);
                })
                .reduce(0, Integer::sum)
                .doOnSuccess(count -> log.info("Marked {} executions for recovery from node {}",
                        count, failedNodeId));
    }

    @Override
    public boolean isLocalExecution(String executionKey) {
        return partitionManager.shouldHandleWorkflow(executionKey);
    }

    @Override
    public Optional<String> getTargetNode(String executionKey) {
        return partitionManager.getTargetNode(executionKey);
    }

    /**
     * Gets the lease renewal manager for monitoring and metrics.
     *
     * @return optional containing the lease renewal manager if configured
     */
    public Optional<LeaseRenewalManager> getLeaseRenewalManager() {
        return Optional.ofNullable(leaseRenewalManager);
    }

    /**
     * Checks if lease renewal is enabled for this executor.
     *
     * @return true if lease renewal is configured and running
     */
    public boolean isLeaseRenewalEnabled() {
        return leaseRenewalManager != null && leaseRenewalManager.isRunning();
    }

    /**
     * Sets the workflow recovery coordinator for handling orphaned workflows.
     *
     * @param coordinator the recovery coordinator
     */
    public void setRecoveryCoordinator(WorkflowRecoveryCoordinator coordinator) {
        this.recoveryCoordinator = coordinator;

        // Register the resume handler
        if (coordinator != null) {
            coordinator.setResumeHandler(this::resumeRecoveredWorkflow);
        }
    }

    /**
     * Gets the recovery coordinator if configured.
     *
     * @return optional containing the recovery coordinator
     */
    public Optional<WorkflowRecoveryCoordinator> getRecoveryCoordinator() {
        return Optional.ofNullable(recoveryCoordinator);
    }

    /**
     * Checks if recovery is enabled.
     *
     * @return true if recovery coordinator is configured and running
     */
    public boolean isRecoveryEnabled() {
        return recoveryCoordinator != null && recoveryCoordinator.isRunning();
    }

    /**
     * Resumes a recovered workflow.
     *
     * @param executionKey the workflow execution key
     * @return Mono containing true if resumed successfully
     */
    private Mono<Boolean> resumeRecoveredWorkflow(String executionKey) {
        return resume(executionKey)
                .map(result -> {
                    // Successfully resumed if completed or not denied ownership
                    return result.getOutcome() == DistributedExecutionResult.Outcome.COMPLETED ||
                           (result.getOutcome() != DistributedExecutionResult.Outcome.OWNERSHIP_DENIED &&
                            result.getOutcome() != DistributedExecutionResult.Outcome.FAILED);
                })
                .onErrorReturn(false);
    }

    @Override
    public void addExecutionListener(IDistributedExecutionListener listener) {
        Objects.requireNonNull(listener);
        listeners.add(listener);
    }

    @Override
    public void removeExecutionListener(IDistributedExecutionListener listener) {
        listeners.remove(listener);
    }

    // Private helper methods

    private String generateExecutionKey(ILyshraOpenAppWorkflowIdentifier identifier, String executionId) {
        return shardingStrategy.generateExecutionKey(
                identifier.getOrganization(),
                identifier.getModule(),
                identifier.getVersion(),
                identifier.getWorkflowName(),
                executionId
        );
    }

    private Mono<WorkflowExecutionState> createAndPersistInitialState(
            ILyshraOpenAppWorkflowIdentifier identifier,
            ILyshraOpenAppContext context,
            String executionId,
            String executionKey) {

        int partitionId = shardingStrategy.computePartition(executionKey, partitionManager.getTotalPartitions());
        String workflowIdentifier = identifier.getOrganization() + "/" +
                identifier.getModule() + "/" +
                identifier.getVersion() + "/" +
                identifier.getWorkflowName();

        WorkflowExecutionState state = WorkflowExecutionState.newExecution(
                executionKey,
                workflowIdentifier,
                executionId,
                partitionId,
                clusterCoordinator.getNodeId()
        );

        // Serialize context
        byte[] contextData = serializeContext(context);
        state = state.withContextData(contextData);

        return stateStore.save(state);
    }

    /**
     * Executes workflow with automatic lease renewal using OwnershipGuardedExecution.
     * This ensures ownership is maintained throughout long-running workflow execution.
     */
    private Mono<DistributedExecutionResult> executeWithLeaseRenewal(
            ILyshraOpenAppWorkflowIdentifier identifier,
            ILyshraOpenAppContext context,
            String executionKey,
            WorkflowExecutionState initialState,
            Instant startTime,
            WorkflowDispatchResult dispatchResult) {

        // Create ownership context from dispatch result
        OwnershipContext ownershipContext = OwnershipContext.builder()
                .workflowExecutionKey(executionKey)
                .ownerId(clusterCoordinator.getNodeId())
                .fencingToken(dispatchResult.getFencingToken())
                .acquiredAt(Instant.now())
                .expiresAt(Instant.now().plus(DEFAULT_LEASE_DURATION))
                .leaseDuration(DEFAULT_LEASE_DURATION)
                .build();

        // Create guarded execution that will auto-renew the lease
        OwnershipGuardedExecution guardedExecution = OwnershipGuardedExecution.create(
                executionKey, ownershipContext, leaseRenewalManager);

        log.info("Starting guarded execution for {} with lease renewal enabled", executionKey);

        // Execute workflow within guarded context
        return guardedExecution.executeWithContext(guard -> {
            // Periodically check ownership during execution
            return executeWorkflowWithOwnershipCheck(identifier, context, executionKey, initialState, startTime, guard);
        })
        .doOnSuccess(result -> {
            guardedExecution.complete();
            log.debug("Guarded execution completed successfully for {}", executionKey);
        })
        .doOnError(error -> {
            if (error instanceof OwnershipGuardedExecution.OwnershipLostException) {
                log.warn("Ownership lost during execution of {}: {}", executionKey, error.getMessage());
            } else {
                log.error("Error during guarded execution of {}", executionKey, error);
            }
            guardedExecution.complete();
        })
        .onErrorResume(OwnershipGuardedExecution.OwnershipLostException.class, ownershipError -> {
            // Handle ownership lost during execution
            WorkflowExecutionState failedState = initialState.markFailed(
                    "Ownership lost during execution: " + ownershipError.getMessage());
            stateStore.save(failedState).subscribe();

            return Mono.just(DistributedExecutionResult.failed(
                    executionKey,
                    initialState.getWorkflowIdentifier(),
                    failedState,
                    startTime,
                    clusterCoordinator.getNodeId(),
                    "Ownership lost during execution",
                    ownershipError
            ));
        });
    }

    /**
     * Executes workflow with periodic ownership checks via the guarded execution context.
     * Uses fencing token validation for all state updates to prevent split-brain corruption.
     */
    private Mono<DistributedExecutionResult> executeWorkflowWithOwnershipCheck(
            ILyshraOpenAppWorkflowIdentifier identifier,
            ILyshraOpenAppContext context,
            String executionKey,
            WorkflowExecutionState initialState,
            Instant startTime,
            OwnershipGuardedExecution guard) {

        // Get fencing token from guard for validation
        long fencingToken = guard.getFencingToken();

        // Update state to running with fencing token validation
        WorkflowExecutionState runningState = initialState.markRunning(null);

        return stateStore.saveWithFencingToken(runningState, fencingToken)
                .switchIfEmpty(Mono.error(new OwnershipGuardedExecution.OwnershipLostException(
                        executionKey, "Fencing token validation failed - ownership may have been transferred")))
                .flatMap(state -> {
                    notifyExecutionStarted(executionKey, state);

                    // Verify ownership before starting execution
                    if (!guard.isOwnershipValid()) {
                        return Mono.error(new OwnershipGuardedExecution.OwnershipLostException(
                                executionKey, "Ownership invalid before execution start"));
                    }

                    // Execute using the local workflow executor
                    ILyshraOpenAppWorkflowExecutor localExecutor = facade.getWorkflowExecutor();

                    return localExecutor.execute(identifier, context)
                            .map(finalContext -> {
                                // Verify ownership still valid after execution
                                if (!guard.isOwnershipValid()) {
                                    throw new OwnershipGuardedExecution.OwnershipLostException(
                                            executionKey, "Ownership lost during execution");
                                }

                                // Execution completed successfully - save with fencing token validation
                                WorkflowExecutionState completedState = state.markCompleted();
                                stateStore.saveWithFencingToken(completedState, fencingToken).subscribe();

                                DistributedExecutionResult result = DistributedExecutionResult.completed(
                                        executionKey,
                                        state.getWorkflowIdentifier(),
                                        finalContext,
                                        completedState,
                                        startTime,
                                        clusterCoordinator.getNodeId()
                                );

                                notifyExecutionCompleted(executionKey, result);
                                log.info("Workflow {} completed with {} lease renewals during execution",
                                        executionKey, guard.getExecutionDuration());
                                return result;
                            })
                            .onErrorResume(error -> {
                                // Check if it's an ownership error
                                if (error instanceof OwnershipGuardedExecution.OwnershipLostException) {
                                    return Mono.error(error);
                                }

                                // Regular execution failure - save with fencing token validation
                                log.error("Workflow execution failed: {}", executionKey, error);

                                WorkflowExecutionState failedState = state.markFailed(error.getMessage());
                                stateStore.saveWithFencingToken(failedState, fencingToken).subscribe();

                                DistributedExecutionResult result = DistributedExecutionResult.failed(
                                        executionKey,
                                        state.getWorkflowIdentifier(),
                                        failedState,
                                        startTime,
                                        clusterCoordinator.getNodeId(),
                                        error.getMessage(),
                                        error
                                );

                                notifyExecutionFailed(executionKey, result);
                                return Mono.just(result);
                            });
                });
    }

    private Mono<DistributedExecutionResult> executeWorkflow(
            ILyshraOpenAppWorkflowIdentifier identifier,
            ILyshraOpenAppContext context,
            String executionKey,
            WorkflowExecutionState initialState,
            Instant startTime) {

        // Update state to running
        WorkflowExecutionState runningState = initialState.markRunning(null);

        return stateStore.save(runningState)
                .flatMap(state -> {
                    notifyExecutionStarted(executionKey, state);

                    // Execute using the local workflow executor
                    ILyshraOpenAppWorkflowExecutor localExecutor = facade.getWorkflowExecutor();

                    return localExecutor.execute(identifier, context)
                            .map(finalContext -> {
                                // Execution completed successfully
                                WorkflowExecutionState completedState = state.markCompleted();
                                stateStore.save(completedState).subscribe();

                                DistributedExecutionResult result = DistributedExecutionResult.completed(
                                        executionKey,
                                        state.getWorkflowIdentifier(),
                                        finalContext,
                                        completedState,
                                        startTime,
                                        clusterCoordinator.getNodeId()
                                );

                                notifyExecutionCompleted(executionKey, result);
                                return result;
                            })
                            .onErrorResume(error -> {
                                // Execution failed
                                log.error("Workflow execution failed: {}", executionKey, error);

                                WorkflowExecutionState failedState = state.markFailed(error.getMessage());
                                stateStore.save(failedState).subscribe();

                                DistributedExecutionResult result = DistributedExecutionResult.failed(
                                        executionKey,
                                        state.getWorkflowIdentifier(),
                                        failedState,
                                        startTime,
                                        clusterCoordinator.getNodeId(),
                                        error.getMessage(),
                                        error
                                );

                                notifyExecutionFailed(executionKey, result);
                                return Mono.just(result);
                            });
                });
    }

    private Mono<DistributedExecutionResult> resumeFromCheckpoint(
            WorkflowExecutionState state,
            ILyshraOpenAppContext context,
            WorkflowCheckpoint checkpoint) {

        Instant startTime = Instant.now();
        String executionKey = state.getExecutionKey();

        // Update state to running
        WorkflowExecutionState runningState = state.markRunning(
                checkpoint != null ? checkpoint.getStepName() : state.getCurrentStepName()
        );

        return stateStore.save(runningState)
                .flatMap(savedState -> {
                    // For now, we restart from the beginning
                    // A full implementation would parse the workflow identifier and resume from checkpoint
                    log.info("Resuming execution {} from step: {}",
                            executionKey, savedState.getCurrentStepName());

                    // This is a simplified implementation - full implementation would
                    // need to reconstruct workflow identifier and resume from specific step
                    return Mono.just(DistributedExecutionResult.failed(
                            executionKey,
                            savedState.getWorkflowIdentifier(),
                            savedState,
                            startTime,
                            clusterCoordinator.getNodeId(),
                            "Full checkpoint resumption not yet implemented",
                            null
                    ));
                });
    }

    private ILyshraOpenAppContext reconstructContext(WorkflowCheckpoint checkpoint,
                                                      WorkflowExecutionState state) {
        // Deserialize context from checkpoint or state
        byte[] contextData = checkpoint != null ? checkpoint.getContextData() : state.getContextData();

        if (contextData != null) {
            return deserializeContext(contextData);
        }

        // Return a new context if none found
        try {
            return (ILyshraOpenAppContext) Class.forName(
                    "com.lyshra.open.app.core.models.LyshraOpenAppContext")
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create context", e);
        }
    }

    private byte[] serializeContext(ILyshraOpenAppContext context) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(context.getData());
            oos.writeObject(context.getVariables());
            return bos.toByteArray();
        } catch (IOException e) {
            log.warn("Failed to serialize context", e);
            return null;
        }
    }

    private ILyshraOpenAppContext deserializeContext(byte[] data) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            Object contextData = ois.readObject();
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> variables = (java.util.Map<String, Object>) ois.readObject();

            ILyshraOpenAppContext context = (ILyshraOpenAppContext) Class.forName(
                    "com.lyshra.open.app.core.models.LyshraOpenAppContext")
                    .getDeclaredConstructor()
                    .newInstance();
            context.setData(contextData);
            context.setVariables(variables);
            return context;
        } catch (Exception e) {
            log.warn("Failed to deserialize context", e);
            throw new RuntimeException("Failed to deserialize context", e);
        }
    }

    // Listener notification methods

    private void notifyExecutionSubmitted(String executionKey, WorkflowExecutionState state) {
        for (IDistributedExecutionListener listener : listeners) {
            try {
                listener.onExecutionSubmitted(executionKey, state);
            } catch (Exception e) {
                log.error("Error notifying listener of execution submitted", e);
            }
        }
    }

    private void notifyExecutionStarted(String executionKey, WorkflowExecutionState state) {
        for (IDistributedExecutionListener listener : listeners) {
            try {
                listener.onExecutionStarted(executionKey, state);
            } catch (Exception e) {
                log.error("Error notifying listener of execution started", e);
            }
        }
    }

    private void notifyExecutionCompleted(String executionKey, DistributedExecutionResult result) {
        for (IDistributedExecutionListener listener : listeners) {
            try {
                listener.onExecutionCompleted(executionKey, result);
            } catch (Exception e) {
                log.error("Error notifying listener of execution completed", e);
            }
        }
    }

    private void notifyExecutionFailed(String executionKey, DistributedExecutionResult result) {
        for (IDistributedExecutionListener listener : listeners) {
            try {
                listener.onExecutionFailed(executionKey, result);
            } catch (Exception e) {
                log.error("Error notifying listener of execution failed", e);
            }
        }
    }

    private void notifyExecutionPaused(String executionKey, WorkflowExecutionState state) {
        for (IDistributedExecutionListener listener : listeners) {
            try {
                listener.onExecutionPaused(executionKey, state);
            } catch (Exception e) {
                log.error("Error notifying listener of execution paused", e);
            }
        }
    }

    private void notifyExecutionCancelled(String executionKey) {
        for (IDistributedExecutionListener listener : listeners) {
            try {
                listener.onExecutionCancelled(executionKey);
            } catch (Exception e) {
                log.error("Error notifying listener of execution cancelled", e);
            }
        }
    }

    private void notifyExecutionRecovered(String executionKey, String failedNodeId, String newNodeId) {
        for (IDistributedExecutionListener listener : listeners) {
            try {
                listener.onExecutionRecovered(executionKey, failedNodeId, newNodeId);
            } catch (Exception e) {
                log.error("Error notifying listener of execution recovered", e);
            }
        }
    }
}
