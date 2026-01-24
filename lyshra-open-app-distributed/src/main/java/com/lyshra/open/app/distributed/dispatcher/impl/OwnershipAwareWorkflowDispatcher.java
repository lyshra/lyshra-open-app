package com.lyshra.open.app.distributed.dispatcher.impl;

import com.lyshra.open.app.distributed.coordination.AcquisitionOptions;
import com.lyshra.open.app.distributed.coordination.CoordinationResult;
import com.lyshra.open.app.distributed.coordination.IWorkflowOwnershipCoordinator;
import com.lyshra.open.app.distributed.coordination.OwnershipContext;
import com.lyshra.open.app.distributed.dispatcher.*;
import com.lyshra.open.app.distributed.sharding.IPartitionManager;
import com.lyshra.open.app.distributed.sharding.IShardingStrategy;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ownership-aware workflow dispatcher that ensures workflows are only executed
 * by nodes that successfully acquire ownership.
 *
 * This dispatcher integrates with the ownership coordinator to:
 * 1. Route workflows to the correct partition owner
 * 2. Acquire ownership before allowing execution
 * 3. Prevent duplicate execution across nodes
 * 4. Handle transient failures with configurable retry
 *
 * Dispatch Flow:
 * <pre>
 * dispatch(request) →
 *   1. Generate execution key from workflow identifier
 *   2. Determine target partition via sharding strategy
 *   3. Check if this node handles the partition
 *   4. If not → Return ROUTED_TO_OTHER result
 *   5. If yes → Attempt ownership acquisition with retry
 *   6. If acquired → Return DISPATCHED_LOCALLY with ownership context
 *   7. If owned by other → Return OWNED_BY_OTHER
 *   8. If failed → Return REJECTED with appropriate reason
 * </pre>
 *
 * Thread Safety: This class is thread-safe.
 */
@Slf4j
public class OwnershipAwareWorkflowDispatcher implements IWorkflowDispatcher {

    private final IWorkflowOwnershipCoordinator coordinator;
    private final IPartitionManager partitionManager;
    private final IShardingStrategy shardingStrategy;

    private final ConcurrentHashMap<String, OwnershipContext> locallyDispatchedWorkflows;
    private final CopyOnWriteArrayList<IDispatchEventListener> listeners;
    private final DispatcherMetrics metrics;

    private volatile DispatchRetryPolicy retryPolicy;
    private final AtomicBoolean active;
    private final AtomicBoolean shuttingDown;

    private final int maxLocalWorkflows;

    /**
     * Creates a new ownership-aware dispatcher.
     *
     * @param coordinator the ownership coordinator
     * @param partitionManager the partition manager
     * @param shardingStrategy the sharding strategy
     */
    public OwnershipAwareWorkflowDispatcher(IWorkflowOwnershipCoordinator coordinator,
                                             IPartitionManager partitionManager,
                                             IShardingStrategy shardingStrategy) {
        this(coordinator, partitionManager, shardingStrategy, 1000);
    }

    /**
     * Creates a new ownership-aware dispatcher with capacity limit.
     *
     * @param coordinator the ownership coordinator
     * @param partitionManager the partition manager
     * @param shardingStrategy the sharding strategy
     * @param maxLocalWorkflows maximum workflows this node can dispatch
     */
    public OwnershipAwareWorkflowDispatcher(IWorkflowOwnershipCoordinator coordinator,
                                             IPartitionManager partitionManager,
                                             IShardingStrategy shardingStrategy,
                                             int maxLocalWorkflows) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator must not be null");
        this.partitionManager = Objects.requireNonNull(partitionManager, "partitionManager must not be null");
        this.shardingStrategy = Objects.requireNonNull(shardingStrategy, "shardingStrategy must not be null");

        this.locallyDispatchedWorkflows = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.metrics = new DispatcherMetrics();
        this.retryPolicy = DispatchRetryPolicy.DEFAULT;
        this.active = new AtomicBoolean(false);
        this.shuttingDown = new AtomicBoolean(false);
        this.maxLocalWorkflows = maxLocalWorkflows;
    }

    // ========== Lifecycle ==========

    @Override
    public Mono<Void> initialize() {
        return Mono.defer(() -> {
            if (!active.compareAndSet(false, true)) {
                log.warn("Dispatcher already initialized");
                return Mono.empty();
            }

            log.info("Initializing OwnershipAwareWorkflowDispatcher");
            return Mono.empty();
        });
    }

    @Override
    public Mono<Void> shutdown() {
        return Mono.defer(() -> {
            if (!shuttingDown.compareAndSet(false, true)) {
                return Mono.empty();
            }

            log.info("Shutting down OwnershipAwareWorkflowDispatcher with {} active dispatches",
                    locallyDispatchedWorkflows.size());

            // Release all owned workflows
            return releaseOwnershipBatch(locallyDispatchedWorkflows.keySet(), "dispatcher_shutdown")
                    .doOnSuccess(count -> {
                        active.set(false);
                        log.info("Dispatcher shutdown complete, released {} workflows", count);
                    })
                    .then();
        });
    }

    @Override
    public boolean isActive() {
        return active.get() && !shuttingDown.get();
    }

    // ========== Dispatch Operations ==========

    @Override
    public Mono<WorkflowDispatchResult> dispatch(DispatchRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.workflowIdentifier(), "workflowIdentifier must not be null");
        Objects.requireNonNull(request.executionId(), "executionId must not be null");

        String executionKey = generateExecutionKey(request.workflowIdentifier(), request.executionId());
        DispatchOptions options = request.options() != null ? request.options() : DispatchOptions.defaults();

        Instant startTime = Instant.now();
        metrics.recordDispatchAttempt();

        log.debug("Dispatching workflow: {}", executionKey);

        return Mono.defer(() -> {
            // Check if dispatcher is active
            if (!isActive()) {
                return Mono.just(WorkflowDispatchResult.dispatcherShuttingDown(executionKey));
            }

            // Check capacity
            if (options.isCheckCapacity() && locallyDispatchedWorkflows.size() >= maxLocalWorkflows) {
                WorkflowDispatchResult result = WorkflowDispatchResult.capacityExceeded(
                        executionKey, locallyDispatchedWorkflows.size(), maxLocalWorkflows);
                metrics.recordRejected(WorkflowDispatchResult.RejectReason.CAPACITY_EXCEEDED);
                return Mono.just(result);
            }

            // Check if already dispatched locally
            OwnershipContext existingContext = locallyDispatchedWorkflows.get(executionKey);
            if (existingContext != null && existingContext.isValid()) {
                WorkflowDispatchResult result = WorkflowDispatchResult.alreadyOwnedLocally(
                        executionKey, existingContext);
                return Mono.just(result);
            }

            // Step 1: Check partition assignment
            if (!options.isSkipPartitionCheck() && !partitionManager.shouldHandleWorkflow(executionKey)) {
                Optional<String> targetNode = partitionManager.getTargetNode(executionKey);
                int partitionId = shardingStrategy.computePartition(executionKey, partitionManager.getTotalPartitions());

                if (targetNode.isPresent()) {
                    log.debug("Workflow {} routed to node {}", executionKey, targetNode.get());
                    WorkflowDispatchResult result = WorkflowDispatchResult.routedToOther(
                            executionKey, targetNode.get(), partitionId);
                    metrics.recordRoutedToOther();
                    notifyRoutedToOther(executionKey, targetNode.get());
                    return Mono.just(result);
                }
            }

            // Step 2: Attempt ownership acquisition with retry
            return attemptOwnershipAcquisition(executionKey, options, startTime, 1);
        });
    }

    @Override
    public Mono<WorkflowDispatchResult> dispatchForResume(String executionKey, DispatchOptions options) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");

        DispatchOptions resumeOptions = options != null ? options : DispatchOptions.forResume();
        Instant startTime = Instant.now();

        log.debug("Dispatching workflow for resume: {}", executionKey);
        metrics.recordDispatchAttempt();

        return Mono.defer(() -> {
            if (!isActive()) {
                return Mono.just(WorkflowDispatchResult.dispatcherShuttingDown(executionKey));
            }

            // For resume, we may need to force acquisition
            return attemptOwnershipAcquisition(executionKey, resumeOptions, startTime, 1);
        });
    }

    @Override
    public Flux<WorkflowDispatchResult> dispatchBatch(Set<DispatchRequest> requests) {
        Objects.requireNonNull(requests, "requests must not be null");

        return Flux.fromIterable(requests)
                .flatMap(this::dispatch, 4); // Limit concurrency
    }

    // ========== Ownership Acquisition ==========

    private Mono<WorkflowDispatchResult> attemptOwnershipAcquisition(String executionKey,
                                                                       DispatchOptions options,
                                                                       Instant startTime,
                                                                       int attemptNumber) {
        return acquireOwnershipWithCoordinator(executionKey, options)
                .flatMap(result -> handleCoordinationResult(executionKey, result, options, startTime, attemptNumber))
                .onErrorResume(error -> handleAcquisitionError(executionKey, error, options, startTime, attemptNumber));
    }

    private Mono<CoordinationResult> acquireOwnershipWithCoordinator(String executionKey,
                                                                      DispatchOptions options) {
        AcquisitionOptions acquisitionOptions = AcquisitionOptions.builder()
                .leaseDuration(options.getLeaseDuration())
                .acquireTimeout(options.getAcquisitionTimeout())
                .retryOnFailure(false) // We handle retry at dispatcher level
                .skipPartitionCheck(options.isSkipPartitionCheck())
                .forceAcquisition(options.isForceAcquisition())
                .minimumFencingToken(options.getMinimumFencingToken())
                .priority(options.getPriority())
                .metadata(options.getMetadata())
                .acquisitionReason(options.getDispatchReason())
                .build();

        return coordinator.acquireOwnership(executionKey, acquisitionOptions);
    }

    private Mono<WorkflowDispatchResult> handleCoordinationResult(String executionKey,
                                                                    CoordinationResult coordResult,
                                                                    DispatchOptions options,
                                                                    Instant startTime,
                                                                    int attemptNumber) {
        Duration acquisitionDuration = Duration.between(startTime, Instant.now());

        if (coordResult.isAcquired()) {
            // Successfully acquired ownership
            OwnershipContext context = createOwnershipContext(executionKey, coordResult);
            locallyDispatchedWorkflows.put(executionKey, context);

            WorkflowDispatchResult result = WorkflowDispatchResult.dispatchedLocally(
                    executionKey, context, acquisitionDuration);

            metrics.recordDispatchedLocally(acquisitionDuration);
            notifyDispatchedLocally(executionKey, result);

            log.info("Workflow {} dispatched locally with fencing token {}",
                    executionKey, context.getFencingToken());

            return Mono.just(result);
        }

        if (coordResult.isAlreadyOwned()) {
            // Owned by another node
            WorkflowDispatchResult result = WorkflowDispatchResult.ownedByOther(
                    executionKey,
                    coordResult.getCurrentOwner(),
                    coordResult.getLeaseExpiresAt());

            metrics.recordOwnedByOther();
            log.debug("Workflow {} owned by other node: {}", executionKey, coordResult.getCurrentOwner());

            return Mono.just(result);
        }

        if (coordResult.isWrongPartition()) {
            // Route to correct node
            WorkflowDispatchResult result = WorkflowDispatchResult.routedToOther(
                    executionKey,
                    coordResult.getCorrectNode(),
                    coordResult.getPartitionId());

            metrics.recordRoutedToOther();
            notifyRoutedToOther(executionKey, coordResult.getCorrectNode());

            return Mono.just(result);
        }

        // Handle various failure cases with potential retry
        return handleCoordinationFailure(executionKey, coordResult, options, startTime, attemptNumber);
    }

    private Mono<WorkflowDispatchResult> handleCoordinationFailure(String executionKey,
                                                                     CoordinationResult coordResult,
                                                                     DispatchOptions options,
                                                                     Instant startTime,
                                                                     int attemptNumber) {
        WorkflowDispatchResult.RejectReason rejectReason = mapToRejectReason(coordResult.getStatus());
        Duration acquisitionDuration = Duration.between(startTime, Instant.now());

        // Check if we should retry
        if (options.isRetryEnabled() &&
            retryPolicy.shouldRetry(rejectReason) &&
            retryPolicy.shouldRetry(attemptNumber)) {

            Duration retryDelay = retryPolicy.calculateDelay(attemptNumber);

            log.debug("Retrying dispatch for {} after {} (attempt {})",
                    executionKey, retryDelay, attemptNumber);

            metrics.recordRetry(false);
            notifyDispatchRetry(executionKey, attemptNumber, coordResult.getStatus().name());

            return Mono.delay(retryDelay)
                    .then(attemptOwnershipAcquisition(executionKey, options, startTime, attemptNumber + 1));
        }

        // No retry - return rejection
        WorkflowDispatchResult result;
        if (rejectReason == WorkflowDispatchResult.RejectReason.ACQUISITION_TIMEOUT) {
            result = WorkflowDispatchResult.timeout(executionKey, acquisitionDuration, attemptNumber);
        } else if (attemptNumber > 1) {
            result = WorkflowDispatchResult.maxRetriesExceeded(
                    executionKey, attemptNumber,
                    coordResult.getErrorMessageOptional().orElse("Unknown error"));
        } else {
            result = WorkflowDispatchResult.builder()
                    .executionKey(executionKey)
                    .outcome(WorkflowDispatchResult.Outcome.REJECTED)
                    .rejectReason(rejectReason)
                    .dispatchedAt(Instant.now())
                    .acquisitionDuration(acquisitionDuration)
                    .attemptCount(attemptNumber)
                    .errorMessage(coordResult.getErrorMessageOptional().orElse("Coordination failed"))
                    .build();
        }

        metrics.recordRejected(rejectReason);
        notifyDispatchRejected(executionKey, rejectReason);

        return Mono.just(result);
    }

    private Mono<WorkflowDispatchResult> handleAcquisitionError(String executionKey,
                                                                  Throwable error,
                                                                  DispatchOptions options,
                                                                  Instant startTime,
                                                                  int attemptNumber) {
        log.warn("Error during ownership acquisition for {}: {}", executionKey, error.getMessage());

        WorkflowDispatchResult.RejectReason rejectReason = WorkflowDispatchResult.RejectReason.BACKEND_UNAVAILABLE;

        // Check if we should retry
        if (options.isRetryEnabled() &&
            retryPolicy.shouldRetry(rejectReason) &&
            retryPolicy.shouldRetry(attemptNumber)) {

            Duration retryDelay = retryPolicy.calculateDelay(attemptNumber);
            metrics.recordRetry(false);

            return Mono.delay(retryDelay)
                    .then(attemptOwnershipAcquisition(executionKey, options, startTime, attemptNumber + 1));
        }

        WorkflowDispatchResult result = WorkflowDispatchResult.backendUnavailable(executionKey, error);
        metrics.recordRejected(rejectReason);
        notifyDispatchRejected(executionKey, rejectReason);

        return Mono.just(result);
    }

    private WorkflowDispatchResult.RejectReason mapToRejectReason(CoordinationResult.Status status) {
        return switch (status) {
            case TIMEOUT -> WorkflowDispatchResult.RejectReason.ACQUISITION_TIMEOUT;
            case COORDINATOR_INACTIVE -> WorkflowDispatchResult.RejectReason.COORDINATOR_INACTIVE;
            case BACKEND_UNAVAILABLE -> WorkflowDispatchResult.RejectReason.BACKEND_UNAVAILABLE;
            case FENCING_CONFLICT -> WorkflowDispatchResult.RejectReason.FENCING_CONFLICT;
            case ERROR -> WorkflowDispatchResult.RejectReason.UNKNOWN_ERROR;
            default -> WorkflowDispatchResult.RejectReason.UNKNOWN_ERROR;
        };
    }

    private OwnershipContext createOwnershipContext(String executionKey, CoordinationResult coordResult) {
        return OwnershipContext.fromAcquisition(
                executionKey,
                java.util.UUID.randomUUID().toString(),
                coordResult.getOwnerId(),
                coordResult.getPartitionId(),
                Duration.between(Instant.now(), coordResult.getLeaseExpiresAt()),
                coordResult.getFencingToken(),
                coordResult.getOwnershipEpoch()
        );
    }

    // ========== Ownership Verification ==========

    @Override
    public Mono<Boolean> verifyOwnership(String executionKey, long fencingToken) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");

        OwnershipContext context = locallyDispatchedWorkflows.get(executionKey);
        if (context == null) {
            return Mono.just(false);
        }

        // Check local context first
        if (!context.isValid() || context.getFencingToken() != fencingToken) {
            return Mono.just(false);
        }

        // Validate with coordinator
        return coordinator.validateFencingToken(executionKey, fencingToken);
    }

    @Override
    public Mono<Boolean> renewOwnership(String executionKey) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");

        OwnershipContext context = locallyDispatchedWorkflows.get(executionKey);
        if (context == null) {
            return Mono.just(false);
        }

        return coordinator.renewOwnership(executionKey)
                .doOnNext(success -> {
                    if (success) {
                        metrics.recordOwnershipRenewal();
                        log.debug("Renewed ownership for {}", executionKey);
                    }
                })
                .doOnError(e -> log.warn("Failed to renew ownership for {}: {}", executionKey, e.getMessage()))
                .onErrorReturn(false);
    }

    // ========== Ownership Release ==========

    @Override
    public Mono<Void> releaseOwnership(String executionKey, String reason) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");

        return Mono.defer(() -> {
            OwnershipContext removed = locallyDispatchedWorkflows.remove(executionKey);
            if (removed == null) {
                return Mono.empty();
            }

            return coordinator.releaseOwnership(executionKey, reason)
                    .doOnSuccess(v -> {
                        metrics.recordOwnershipRelease();
                        notifyOwnershipReleased(executionKey, reason);
                        log.debug("Released ownership for {}: {}", executionKey, reason);
                    })
                    .doOnError(e -> log.warn("Error releasing ownership for {}: {}", executionKey, e.getMessage()))
                    .onErrorResume(e -> Mono.empty());
        });
    }

    @Override
    public Mono<Integer> releaseOwnershipBatch(Set<String> executionKeys, String reason) {
        if (executionKeys == null || executionKeys.isEmpty()) {
            return Mono.just(0);
        }

        return Flux.fromIterable(executionKeys)
                .flatMap(key -> releaseOwnership(key, reason).thenReturn(1))
                .reduce(0, Integer::sum);
    }

    // ========== Query Operations ==========

    @Override
    public Set<String> getDispatchedWorkflows() {
        return Set.copyOf(locallyDispatchedWorkflows.keySet());
    }

    @Override
    public int getDispatchedCount() {
        return locallyDispatchedWorkflows.size();
    }

    @Override
    public boolean isDispatchedLocally(String executionKey) {
        OwnershipContext context = locallyDispatchedWorkflows.get(executionKey);
        return context != null && context.isValid();
    }

    @Override
    public Mono<String> getTargetNode(String executionKey) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");

        if (partitionManager.shouldHandleWorkflow(executionKey)) {
            return Mono.just(coordinator.getNodeId());
        }

        return Mono.justOrEmpty(partitionManager.getTargetNode(executionKey));
    }

    // ========== Configuration ==========

    @Override
    public DispatchRetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    @Override
    public void setRetryPolicy(DispatchRetryPolicy policy) {
        this.retryPolicy = policy != null ? policy : DispatchRetryPolicy.DEFAULT;
    }

    @Override
    public DispatcherMetrics getMetrics() {
        return metrics;
    }

    // ========== Event Listeners ==========

    @Override
    public void addDispatchListener(IDispatchEventListener listener) {
        Objects.requireNonNull(listener);
        listeners.add(listener);
    }

    @Override
    public void removeDispatchListener(IDispatchEventListener listener) {
        listeners.remove(listener);
    }

    // ========== Helper Methods ==========

    private String generateExecutionKey(ILyshraOpenAppWorkflowIdentifier identifier, String executionId) {
        return shardingStrategy.generateExecutionKey(
                identifier.getOrganization(),
                identifier.getModule(),
                identifier.getVersion(),
                identifier.getWorkflowName(),
                executionId
        );
    }

    // ========== Notification Methods ==========

    private void notifyDispatchedLocally(String executionKey, WorkflowDispatchResult result) {
        for (IDispatchEventListener listener : listeners) {
            try {
                listener.onDispatchedLocally(executionKey, result);
            } catch (Exception e) {
                log.error("Error notifying listener of local dispatch", e);
            }
        }
    }

    private void notifyRoutedToOther(String executionKey, String targetNodeId) {
        for (IDispatchEventListener listener : listeners) {
            try {
                listener.onRoutedToOther(executionKey, targetNodeId);
            } catch (Exception e) {
                log.error("Error notifying listener of routing", e);
            }
        }
    }

    private void notifyDispatchRejected(String executionKey, WorkflowDispatchResult.RejectReason reason) {
        for (IDispatchEventListener listener : listeners) {
            try {
                listener.onDispatchRejected(executionKey, reason);
            } catch (Exception e) {
                log.error("Error notifying listener of rejection", e);
            }
        }
    }

    private void notifyOwnershipReleased(String executionKey, String reason) {
        for (IDispatchEventListener listener : listeners) {
            try {
                listener.onOwnershipReleased(executionKey, reason);
            } catch (Exception e) {
                log.error("Error notifying listener of ownership release", e);
            }
        }
    }

    private void notifyDispatchRetry(String executionKey, int attemptNumber, String reason) {
        for (IDispatchEventListener listener : listeners) {
            try {
                listener.onDispatchRetry(executionKey, attemptNumber, reason);
            } catch (Exception e) {
                log.error("Error notifying listener of retry", e);
            }
        }
    }
}
