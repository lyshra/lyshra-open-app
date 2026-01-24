package com.lyshra.open.app.distributed.recovery;

import com.lyshra.open.app.distributed.cluster.IClusterCoordinator;
import com.lyshra.open.app.distributed.coordination.CoordinationResult;
import com.lyshra.open.app.distributed.coordination.IWorkflowOwnershipCoordinator;
import com.lyshra.open.app.distributed.coordination.OrphanRecoveryResult;
import com.lyshra.open.app.distributed.coordination.RecoveryOptions;
import com.lyshra.open.app.distributed.recovery.ILeaseExpiryDetector.ExpiredLease;
import com.lyshra.open.app.distributed.recovery.ILeaseExpiryDetector.ILeaseExpiryListener;
import com.lyshra.open.app.distributed.recovery.OrphanedWorkflowRegistry.ClaimResult;
import com.lyshra.open.app.distributed.recovery.OrphanedWorkflowRegistry.OrphanEntry;
import com.lyshra.open.app.distributed.recovery.SafeTakeoverCoordinator.TakeoverOptions;
import com.lyshra.open.app.distributed.recovery.SafeTakeoverCoordinator.TakeoverResult;
import com.lyshra.open.app.distributed.sharding.IPartitionManager;
import com.lyshra.open.app.distributed.state.IWorkflowStateStore;
import com.lyshra.open.app.distributed.state.WorkflowExecutionState;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Coordinates the recovery of orphaned workflows whose ownership has expired.
 *
 * This is the main orchestrator for the recovery process, responsible for:
 *
 * 1. Listening for lease expiry events and node failures
 * 2. Coordinating with the orphan registry to track claimable workflows
 * 3. Claiming orphaned workflows for recovery
 * 4. Transferring ownership and triggering workflow resumption
 * 5. Handling recovery failures and retries
 *
 * Recovery Flow:
 * 1. Expiry detector identifies expired leases
 * 2. Orphan registry tracks them as claimable
 * 3. Recovery coordinator claims workflows for local partitions
 * 4. Ownership is transferred to this node
 * 5. Workflow state is updated and resumption is triggered
 * 6. Recovery is confirmed or retried on failure
 *
 * Thread Safety: This class is thread-safe.
 */
@Slf4j
public class WorkflowRecoveryCoordinator implements ILeaseExpiryListener {

    private static final Duration DEFAULT_RECOVERY_INTERVAL = Duration.ofSeconds(30);
    private static final int DEFAULT_MAX_CONCURRENT_RECOVERIES = 5;
    private static final int DEFAULT_MAX_RECOVERY_ATTEMPTS = 3;

    private final IClusterCoordinator clusterCoordinator;
    private final IWorkflowOwnershipCoordinator ownershipCoordinator;
    private final ILeaseExpiryDetector expiryDetector;
    private final OrphanedWorkflowRegistry orphanRegistry;
    private final IPartitionManager partitionManager;
    private final IWorkflowStateStore stateStore;
    private final SafeTakeoverCoordinator safeTakeoverCoordinator;

    private final Duration recoveryInterval;
    private final int maxConcurrentRecoveries;
    private final int maxRecoveryAttempts;

    private final List<IRecoveryListener> listeners;
    private final RecoveryMetrics metrics;
    private final AtomicBoolean running;

    private volatile Disposable recoveryTask;
    private volatile Function<String, Mono<Boolean>> resumeHandler;

    @Builder
    public WorkflowRecoveryCoordinator(IClusterCoordinator clusterCoordinator,
                                        IWorkflowOwnershipCoordinator ownershipCoordinator,
                                        ILeaseExpiryDetector expiryDetector,
                                        OrphanedWorkflowRegistry orphanRegistry,
                                        IPartitionManager partitionManager,
                                        IWorkflowStateStore stateStore,
                                        SafeTakeoverCoordinator safeTakeoverCoordinator,
                                        Duration recoveryInterval,
                                        Integer maxConcurrentRecoveries,
                                        Integer maxRecoveryAttempts) {
        this.clusterCoordinator = Objects.requireNonNull(clusterCoordinator);
        this.ownershipCoordinator = Objects.requireNonNull(ownershipCoordinator);
        this.expiryDetector = Objects.requireNonNull(expiryDetector);
        this.orphanRegistry = Objects.requireNonNull(orphanRegistry);
        this.partitionManager = Objects.requireNonNull(partitionManager);
        this.stateStore = Objects.requireNonNull(stateStore);
        this.safeTakeoverCoordinator = safeTakeoverCoordinator; // Can be null for basic recovery without split-brain prevention

        this.recoveryInterval = recoveryInterval != null ? recoveryInterval : DEFAULT_RECOVERY_INTERVAL;
        this.maxConcurrentRecoveries = maxConcurrentRecoveries != null ?
                maxConcurrentRecoveries : DEFAULT_MAX_CONCURRENT_RECOVERIES;
        this.maxRecoveryAttempts = maxRecoveryAttempts != null ?
                maxRecoveryAttempts : DEFAULT_MAX_RECOVERY_ATTEMPTS;

        this.listeners = new CopyOnWriteArrayList<>();
        this.metrics = new RecoveryMetrics();
        this.running = new AtomicBoolean(false);
    }

    // ========== Lifecycle ==========

    /**
     * Starts the recovery coordinator.
     *
     * @return Mono that completes when started
     */
    public Mono<Void> start() {
        return Mono.fromRunnable(() -> {
            if (!running.compareAndSet(false, true)) {
                log.warn("Recovery coordinator already running");
                return;
            }

            String nodeId = clusterCoordinator.getNodeId();
            log.info("Starting workflow recovery coordinator on node {}", nodeId);

            // Register as expiry listener
            expiryDetector.addExpiryListener(this);
            expiryDetector.addExpiryListener(orphanRegistry);

            // Start periodic recovery task
            recoveryTask = Flux.interval(recoveryInterval)
                    .flatMap(tick -> performRecoveryCycle())
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(
                            result -> {
                                if (result.recovered > 0) {
                                    log.info("Recovery cycle completed: {} workflows recovered",
                                            result.recovered);
                                }
                            },
                            error -> log.error("Error during recovery cycle", error)
                    );
        });
    }

    /**
     * Stops the recovery coordinator.
     *
     * @return Mono that completes when stopped
     */
    public Mono<Void> stop() {
        return Mono.fromRunnable(() -> {
            if (!running.compareAndSet(true, false)) {
                return;
            }

            log.info("Stopping workflow recovery coordinator");

            expiryDetector.removeExpiryListener(this);
            expiryDetector.removeExpiryListener(orphanRegistry);

            if (recoveryTask != null) {
                recoveryTask.dispose();
                recoveryTask = null;
            }
        });
    }

    /**
     * Checks if the coordinator is running.
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Sets the resume handler for recovered workflows.
     *
     * @param handler function that resumes a workflow by its execution key
     */
    public void setResumeHandler(Function<String, Mono<Boolean>> handler) {
        this.resumeHandler = handler;
    }

    /**
     * Checks if safe takeover (split-brain prevention) is enabled.
     *
     * @return true if SafeTakeoverCoordinator is configured
     */
    public boolean isSafeTakeoverEnabled() {
        return safeTakeoverCoordinator != null;
    }

    /**
     * Gets the SafeTakeoverCoordinator if configured.
     *
     * @return Optional containing the SafeTakeoverCoordinator
     */
    public Optional<SafeTakeoverCoordinator> getSafeTakeoverCoordinator() {
        return Optional.ofNullable(safeTakeoverCoordinator);
    }

    // ========== ILeaseExpiryListener Implementation ==========

    @Override
    public void onLeaseExpired(ExpiredLease expired) {
        // The orphan registry handles registration
        log.debug("Lease expired for workflow {}", expired.workflowExecutionKey());
    }

    @Override
    public void onLeasesExpired(Set<ExpiredLease> expired) {
        log.info("Batch of {} leases expired", expired.size());
        metrics.recordLeasesExpired(expired.size());
    }

    @Override
    public void onNodeFailureDetected(String nodeId, Set<String> affectedWorkflows) {
        log.warn("Node {} failed, {} workflows affected", nodeId, affectedWorkflows.size());
        metrics.recordNodeFailure(affectedWorkflows.size());

        // Trigger immediate recovery for affected workflows
        if (!affectedWorkflows.isEmpty()) {
            triggerImmediateRecovery(nodeId, affectedWorkflows).subscribe();
        }
    }

    // ========== Recovery Operations ==========

    /**
     * Triggers immediate recovery for workflows from a failed node.
     *
     * @param failedNodeId the failed node ID
     * @param workflowKeys the affected workflow keys
     * @return Mono containing the recovery result
     */
    public Mono<RecoveryCycleResult> triggerImmediateRecovery(String failedNodeId, Set<String> workflowKeys) {
        if (!running.get()) {
            return Mono.just(new RecoveryCycleResult(0, 0, 0));
        }

        log.info("Triggering immediate recovery for {} workflows from failed node {}",
                workflowKeys.size(), failedNodeId);

        return Flux.fromIterable(workflowKeys)
                .filter(key -> partitionManager.shouldHandleWorkflow(key))
                .flatMap(this::recoverSingleWorkflow, maxConcurrentRecoveries)
                .collectList()
                .map(results -> aggregateResults(results));
    }

    /**
     * Recovers a single orphaned workflow.
     *
     * @param workflowKey the workflow execution key
     * @return Mono containing the recovery result
     */
    public Mono<SingleRecoveryResult> recoverWorkflow(String workflowKey) {
        return recoverSingleWorkflow(workflowKey);
    }

    /**
     * Performs recovery for all orphaned workflows assigned to this node.
     *
     * @return Mono containing the recovery result
     */
    public Mono<OrphanRecoveryResult> recoverAllLocal() {
        String nodeId = clusterCoordinator.getNodeId();

        return orphanRegistry.getLocalOrphans()
                .take(maxConcurrentRecoveries * 2) // Process in batches
                .flatMap(entry -> recoverSingleWorkflow(entry.getWorkflowExecutionKey()),
                        maxConcurrentRecoveries)
                .filter(result -> result.status == SingleRecoveryResult.RecoveryStatus.RECOVERED)
                .map(SingleRecoveryResult::getWorkflowKey)
                .collect(java.util.stream.Collectors.toSet())
                .map(recovered -> OrphanRecoveryResult.success(
                        null, nodeId, recovered, recovered.size(), Duration.ZERO));
    }

    // ========== Listeners ==========

    /**
     * Adds a recovery event listener.
     */
    public void addListener(IRecoveryListener listener) {
        Objects.requireNonNull(listener);
        listeners.add(listener);
    }

    /**
     * Removes a recovery event listener.
     */
    public void removeListener(IRecoveryListener listener) {
        listeners.remove(listener);
    }

    // ========== Metrics ==========

    /**
     * Gets recovery metrics.
     */
    public RecoveryMetrics getMetrics() {
        return metrics;
    }

    // ========== Private Methods ==========

    private Mono<RecoveryCycleResult> performRecoveryCycle() {
        if (!running.get()) {
            return Mono.just(new RecoveryCycleResult(0, 0, 0));
        }

        Instant cycleStart = Instant.now();
        metrics.recordCycleStarted();

        return orphanRegistry.getOrphansByPriority(maxConcurrentRecoveries)
                .filter(entry -> partitionManager.shouldHandleWorkflow(entry.getWorkflowExecutionKey()))
                .flatMap(entry -> recoverSingleWorkflow(entry.getWorkflowExecutionKey()),
                        maxConcurrentRecoveries)
                .collectList()
                .map(results -> {
                    RecoveryCycleResult cycleResult = aggregateResults(results);
                    Duration duration = Duration.between(cycleStart, Instant.now());
                    metrics.recordCycleCompleted(cycleResult.recovered, duration);
                    return cycleResult;
                })
                .onErrorResume(error -> {
                    log.error("Recovery cycle failed", error);
                    metrics.recordCycleError();
                    return Mono.just(new RecoveryCycleResult(0, 0, 1));
                });
    }

    private Mono<SingleRecoveryResult> recoverSingleWorkflow(String workflowKey) {
        Instant startTime = Instant.now();
        String nodeId = clusterCoordinator.getNodeId();

        return orphanRegistry.tryClaim(workflowKey, nodeId)
                .flatMap(claimResult -> {
                    if (!claimResult.isClaimed()) {
                        return Mono.just(SingleRecoveryResult.skipped(workflowKey,
                                "Could not claim: " + claimResult.getStatus()));
                    }

                    // Use SafeTakeoverCoordinator if available for split-brain prevention
                    if (safeTakeoverCoordinator != null) {
                        return performSafeTakeover(workflowKey, claimResult, startTime);
                    }

                    // Fall back to basic ownership transfer
                    return performBasicRecovery(workflowKey, claimResult, startTime);
                })
                .onErrorResume(error -> {
                    log.error("Error recovering workflow {}", workflowKey, error);
                    orphanRegistry.releaseClaim(workflowKey, nodeId, error.getMessage()).subscribe();
                    metrics.recordRecoveryError();
                    return Mono.just(SingleRecoveryResult.failed(workflowKey, error.getMessage()));
                });
    }

    /**
     * Performs safe takeover with split-brain prevention using SafeTakeoverCoordinator.
     */
    private Mono<SingleRecoveryResult> performSafeTakeover(String workflowKey,
                                                            ClaimResult claimResult,
                                                            Instant startTime) {
        String nodeId = clusterCoordinator.getNodeId();

        return safeTakeoverCoordinator.attemptSafeTakeover(workflowKey, TakeoverOptions.DEFAULT)
                .flatMap(takeoverResult -> {
                    if (!takeoverResult.isSuccessful()) {
                        orphanRegistry.releaseClaim(workflowKey, nodeId,
                                "Safe takeover failed: " + takeoverResult.getStatus()).subscribe();

                        // Determine if we should retry or give up
                        if (takeoverResult.getStatus() == TakeoverResult.Status.LOCK_CONTENTION ||
                            takeoverResult.getStatus() == TakeoverResult.Status.CONCURRENT_TAKEOVER) {
                            return Mono.just(SingleRecoveryResult.skipped(workflowKey,
                                    "Concurrent takeover: " + takeoverResult.getReason()));
                        }

                        return Mono.just(SingleRecoveryResult.failed(workflowKey,
                                "Safe takeover failed: " + takeoverResult.getReason()));
                    }

                    // Takeover successful - state is already updated by SafeTakeoverCoordinator
                    // Now trigger workflow resumption
                    return triggerWorkflowResumption(workflowKey)
                            .map(resumed -> {
                                orphanRegistry.confirmRecovery(workflowKey, nodeId).subscribe();
                                Duration duration = Duration.between(startTime, Instant.now());
                                metrics.recordWorkflowRecovered(duration);
                                notifyRecoveryCompleted(workflowKey, nodeId);

                                log.info("Safe takeover completed for workflow {} with fencing token {}",
                                        workflowKey, takeoverResult.getNewFencingToken());

                                return SingleRecoveryResult.recovered(workflowKey, nodeId, duration);
                            });
                });
    }

    /**
     * Performs basic recovery without split-brain prevention (legacy mode).
     */
    private Mono<SingleRecoveryResult> performBasicRecovery(String workflowKey,
                                                             ClaimResult claimResult,
                                                             Instant startTime) {
        String nodeId = clusterCoordinator.getNodeId();

        return performOwnershipTransfer(workflowKey, claimResult)
                .flatMap(transferResult -> {
                    if (!transferResult) {
                        orphanRegistry.releaseClaim(workflowKey, nodeId,
                                "Ownership transfer failed").subscribe();
                        return Mono.just(SingleRecoveryResult.failed(workflowKey,
                                "Ownership transfer failed"));
                    }

                    return updateWorkflowStateForRecovery(workflowKey)
                            .flatMap(stateUpdated -> {
                                if (!stateUpdated) {
                                    return Mono.just(SingleRecoveryResult.failed(workflowKey,
                                            "State update failed"));
                                }

                                return triggerWorkflowResumption(workflowKey)
                                        .map(resumed -> {
                                            orphanRegistry.confirmRecovery(workflowKey, nodeId).subscribe();
                                            Duration duration = Duration.between(startTime, Instant.now());
                                            metrics.recordWorkflowRecovered(duration);
                                            notifyRecoveryCompleted(workflowKey, nodeId);

                                            return SingleRecoveryResult.recovered(workflowKey, nodeId, duration);
                                        });
                            });
                });
    }

    private Mono<Boolean> performOwnershipTransfer(String workflowKey, ClaimResult claimResult) {
        OrphanEntry entry = claimResult.getEntry();
        if (entry == null) {
            // Entry not available, try direct acquisition
            return ownershipCoordinator.acquireOwnership(workflowKey)
                    .map(CoordinationResult::isAcquired);
        }

        String nodeId = clusterCoordinator.getNodeId();

        // Use the coordinator's orphan claim method which handles fencing tokens
        return ownershipCoordinator.claimOrphanedWorkflows(entry.getPreviousOwnerId(),
                        RecoveryOptions.builder()
                                .maxClaimsPerOperation(1)
                                .respectPartitionAssignment(false) // Already verified
                                .build())
                .map(result -> result.getClaimedWorkflows().contains(workflowKey))
                .onErrorResume(e -> {
                    // Fall back to direct acquisition
                    log.debug("Orphan claim failed, trying direct acquisition for {}", workflowKey);
                    return ownershipCoordinator.acquireOwnership(workflowKey)
                            .map(CoordinationResult::isAcquired);
                });
    }

    private Mono<Boolean> updateWorkflowStateForRecovery(String workflowKey) {
        String nodeId = clusterCoordinator.getNodeId();

        return stateStore.findByExecutionKey(workflowKey)
                .flatMap(optState -> {
                    if (optState.isEmpty()) {
                        log.warn("No state found for workflow {} during recovery", workflowKey);
                        return Mono.just(false);
                    }

                    WorkflowExecutionState state = optState.get();

                    // Mark workflow as pending takeover and then complete the takeover
                    WorkflowExecutionState recoveryState = state
                            .markPendingTakeover(nodeId);

                    return stateStore.save(recoveryState)
                            .map(saved -> true)
                            .onErrorReturn(false);
                });
    }

    private Mono<Boolean> triggerWorkflowResumption(String workflowKey) {
        if (resumeHandler != null) {
            return resumeHandler.apply(workflowKey)
                    .onErrorReturn(false);
        }

        // No resume handler - just mark as ready for manual resume
        log.info("Workflow {} recovered but no resume handler configured", workflowKey);
        return Mono.just(true);
    }

    private RecoveryCycleResult aggregateResults(List<SingleRecoveryResult> results) {
        int recovered = 0;
        int failed = 0;
        int skipped = 0;

        for (SingleRecoveryResult result : results) {
            switch (result.status) {
                case RECOVERED -> recovered++;
                case FAILED -> failed++;
                case SKIPPED -> skipped++;
            }
        }

        return new RecoveryCycleResult(recovered, failed, skipped);
    }

    private void notifyRecoveryCompleted(String workflowKey, String newOwnerId) {
        for (IRecoveryListener listener : listeners) {
            try {
                listener.onWorkflowRecovered(workflowKey, newOwnerId);
            } catch (Exception e) {
                log.error("Error notifying recovery listener", e);
            }
        }
    }

    // ========== Inner Classes ==========

    /**
     * Result of a recovery cycle.
     */
    public record RecoveryCycleResult(int recovered, int failed, int skipped) {
        public int total() {
            return recovered + failed + skipped;
        }
    }

    /**
     * Result of a single workflow recovery.
     */
    @Getter
    @ToString
    public static class SingleRecoveryResult {
        public enum RecoveryStatus {
            RECOVERED, FAILED, SKIPPED
        }

        private final String workflowKey;
        private final RecoveryStatus status;
        private final String newOwnerId;
        private final String reason;
        private final Duration recoveryTime;

        private SingleRecoveryResult(String workflowKey, RecoveryStatus status,
                                      String newOwnerId, String reason, Duration recoveryTime) {
            this.workflowKey = workflowKey;
            this.status = status;
            this.newOwnerId = newOwnerId;
            this.reason = reason;
            this.recoveryTime = recoveryTime;
        }

        public static SingleRecoveryResult recovered(String workflowKey, String newOwnerId, Duration time) {
            return new SingleRecoveryResult(workflowKey, RecoveryStatus.RECOVERED, newOwnerId, null, time);
        }

        public static SingleRecoveryResult failed(String workflowKey, String reason) {
            return new SingleRecoveryResult(workflowKey, RecoveryStatus.FAILED, null, reason, null);
        }

        public static SingleRecoveryResult skipped(String workflowKey, String reason) {
            return new SingleRecoveryResult(workflowKey, RecoveryStatus.SKIPPED, null, reason, null);
        }
    }

    /**
     * Listener for recovery events.
     */
    public interface IRecoveryListener {
        void onWorkflowRecovered(String workflowKey, String newOwnerId);
        void onRecoveryFailed(String workflowKey, String reason);
    }

    /**
     * Recovery metrics.
     */
    @Getter
    public static class RecoveryMetrics {
        private final AtomicLong cyclesStarted = new AtomicLong(0);
        private final AtomicLong cyclesCompleted = new AtomicLong(0);
        private final AtomicLong cycleErrors = new AtomicLong(0);
        private final AtomicLong workflowsRecovered = new AtomicLong(0);
        private final AtomicLong recoveryErrors = new AtomicLong(0);
        private final AtomicLong leasesExpired = new AtomicLong(0);
        private final AtomicLong nodeFailures = new AtomicLong(0);
        private final AtomicLong affectedWorkflows = new AtomicLong(0);
        private final AtomicLong totalRecoveryTimeNanos = new AtomicLong(0);

        void recordCycleStarted() { cyclesStarted.incrementAndGet(); }
        void recordCycleCompleted(int recovered, Duration duration) {
            cyclesCompleted.incrementAndGet();
        }
        void recordCycleError() { cycleErrors.incrementAndGet(); }
        void recordWorkflowRecovered(Duration duration) {
            workflowsRecovered.incrementAndGet();
            totalRecoveryTimeNanos.addAndGet(duration.toNanos());
        }
        void recordRecoveryError() { recoveryErrors.incrementAndGet(); }
        void recordLeasesExpired(int count) { leasesExpired.addAndGet(count); }
        void recordNodeFailure(int workflowCount) {
            nodeFailures.incrementAndGet();
            affectedWorkflows.addAndGet(workflowCount);
        }

        public Duration getAverageRecoveryTime() {
            long count = workflowsRecovered.get();
            if (count == 0) return Duration.ZERO;
            return Duration.ofNanos(totalRecoveryTimeNanos.get() / count);
        }

        public double getRecoverySuccessRate() {
            long total = workflowsRecovered.get() + recoveryErrors.get();
            if (total == 0) return 1.0;
            return (double) workflowsRecovered.get() / total;
        }
    }
}
