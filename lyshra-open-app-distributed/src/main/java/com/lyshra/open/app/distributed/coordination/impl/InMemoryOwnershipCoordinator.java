package com.lyshra.open.app.distributed.coordination.impl;

import com.lyshra.open.app.distributed.cluster.IClusterCoordinator;
import com.lyshra.open.app.distributed.coordination.*;
import com.lyshra.open.app.distributed.lock.IDistributedLockManager;
import com.lyshra.open.app.distributed.ownership.IWorkflowOwnershipRepository;
import com.lyshra.open.app.distributed.ownership.WorkflowOwnershipLease;
import com.lyshra.open.app.distributed.sharding.IPartitionManager;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory implementation of IWorkflowOwnershipCoordinator.
 *
 * This implementation is suitable for:
 * - Single-node deployments
 * - Development and testing
 * - Scenarios where distributed coordination is not required
 *
 * For production distributed deployments, use implementations backed by
 * distributed coordination systems (ZooKeeper, Redis, etcd).
 *
 * Key Features:
 * - Lease-based ownership with automatic expiration
 * - Background lease renewal
 * - Fencing tokens for split-brain prevention
 * - Event notifications for ownership changes
 * - Comprehensive metrics collection
 *
 * Thread Safety: This class is thread-safe.
 */
@Slf4j
public class InMemoryOwnershipCoordinator implements IWorkflowOwnershipCoordinator {

    private static final Duration DEFAULT_LEASE_DURATION = Duration.ofMinutes(5);
    private static final Duration RENEWAL_THRESHOLD = Duration.ofMinutes(2);
    private static final Duration RENEWAL_CHECK_INTERVAL = Duration.ofSeconds(30);
    private static final String COORDINATION_LOCK_PREFIX = "coord-lock:";

    private final IClusterCoordinator clusterCoordinator;
    private final IPartitionManager partitionManager;
    private final IDistributedLockManager lockManager;
    private final IWorkflowOwnershipRepository repository;

    private final ConcurrentHashMap<String, OwnershipContext> localOwnership;
    private final List<ICoordinationEventListener> listeners;
    private final CoordinationMetrics metrics;
    private final AtomicBoolean active;

    private ScheduledExecutorService scheduledExecutor;
    private ScheduledFuture<?> renewalTask;
    private Instant startedAt;

    public InMemoryOwnershipCoordinator(IClusterCoordinator clusterCoordinator,
                                         IPartitionManager partitionManager,
                                         IDistributedLockManager lockManager,
                                         IWorkflowOwnershipRepository repository) {
        this.clusterCoordinator = Objects.requireNonNull(clusterCoordinator);
        this.partitionManager = Objects.requireNonNull(partitionManager);
        this.lockManager = Objects.requireNonNull(lockManager);
        this.repository = Objects.requireNonNull(repository);

        this.localOwnership = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.metrics = new CoordinationMetrics();
        this.active = new AtomicBoolean(false);
    }

    // ========== Lifecycle Methods ==========

    @Override
    public Mono<Void> initialize() {
        if (!active.compareAndSet(false, true)) {
            log.warn("Coordinator already initialized");
            return Mono.empty();
        }

        log.info("Initializing in-memory ownership coordinator for node {}", getNodeId());
        startedAt = Instant.now();

        return repository.initialize()
                .then(loadExistingOwnership())
                .then(startBackgroundTasks())
                .doOnSuccess(v -> {
                    log.info("Ownership coordinator initialized: node={}, existingOwnership={}",
                            getNodeId(), localOwnership.size());
                    emitEvent(CoordinationEvent.coordinatorActive(getNodeId()));
                })
                .doOnError(e -> {
                    log.error("Failed to initialize ownership coordinator", e);
                    active.set(false);
                });
    }

    @Override
    public Mono<Void> shutdown() {
        if (!active.compareAndSet(true, false)) {
            return Mono.empty();
        }

        log.info("Shutting down ownership coordinator for node {}", getNodeId());

        return Mono.fromRunnable(() -> {
                    // Stop background tasks
                    if (renewalTask != null) {
                        renewalTask.cancel(false);
                    }
                    if (scheduledExecutor != null) {
                        scheduledExecutor.shutdown();
                        try {
                            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                                scheduledExecutor.shutdownNow();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            scheduledExecutor.shutdownNow();
                        }
                    }
                })
                .then(releaseAllOwnedWorkflows("coordinator_shutdown").then())
                .then(repository.shutdown())
                .doOnSuccess(v -> {
                    localOwnership.clear();
                    emitEvent(CoordinationEvent.coordinatorInactive(getNodeId(), "Shutdown"));
                    log.info("Ownership coordinator shutdown complete");
                });
    }

    @Override
    public boolean isActive() {
        return active.get();
    }

    // ========== Ownership Acquisition ==========

    @Override
    public Mono<CoordinationResult> acquireOwnership(String workflowExecutionKey) {
        return acquireOwnership(workflowExecutionKey, AcquisitionOptions.DEFAULT);
    }

    @Override
    public Mono<CoordinationResult> acquireOwnership(String workflowExecutionKey, AcquisitionOptions options) {
        Objects.requireNonNull(workflowExecutionKey, "workflowExecutionKey must not be null");
        Objects.requireNonNull(options, "options must not be null");

        if (!active.get()) {
            return Mono.just(CoordinationResult.coordinatorInactive(workflowExecutionKey));
        }

        metrics.recordAcquisitionAttempt();
        Instant startTime = Instant.now();

        return Mono.defer(() -> {
            String nodeId = getNodeId();

            // Check if already owned locally
            OwnershipContext existing = localOwnership.get(workflowExecutionKey);
            if (existing != null && existing.isOwnedBy(nodeId)) {
                return Mono.just(CoordinationResult.alreadyOwnedBySelf(
                        workflowExecutionKey, nodeId, existing.getFencingToken(), existing.getExpiresAt()));
            }

            // Validate partition assignment
            if (!options.isSkipPartitionCheck() && !partitionManager.shouldHandleWorkflow(workflowExecutionKey)) {
                Optional<String> correctNode = partitionManager.getTargetNode(workflowExecutionKey);
                int partitionId = computePartitionId(workflowExecutionKey);
                return Mono.just(CoordinationResult.wrongPartition(
                        workflowExecutionKey, partitionId, correctNode.orElse("unknown")));
            }

            // Acquire lock and ownership
            String lockKey = COORDINATION_LOCK_PREFIX + workflowExecutionKey;
            return lockManager.tryLock(lockKey, options.getLockTimeout())
                    .flatMap(acquired -> {
                        if (!acquired) {
                            metrics.recordLockTimeout();
                            return Mono.just(CoordinationResult.timeout(workflowExecutionKey,
                                    "Failed to acquire coordination lock"));
                        }

                        return doAcquireOwnership(workflowExecutionKey, nodeId, options, startTime)
                                .doFinally(signal -> lockManager.unlock(lockKey).subscribe());
                    });
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<CoordinationResult> doAcquireOwnership(String workflowKey,
                                                         String nodeId,
                                                         AcquisitionOptions options,
                                                         Instant startTime) {
        int partitionId = computePartitionId(workflowKey);
        Duration leaseDuration = options.getLeaseDuration();

        return repository.generateFencingToken()
                .flatMap(fencingToken -> repository.tryAcquire(
                        workflowKey, nodeId, partitionId, leaseDuration, fencingToken))
                .map(optLease -> {
                    if (optLease.isEmpty()) {
                        // Check who owns it
                        metrics.recordAcquisitionFailure();
                        return repository.findByWorkflowId(workflowKey)
                                .map(opt -> opt
                                        .filter(WorkflowOwnershipLease::isValid)
                                        .map(lease -> CoordinationResult.alreadyOwnedByOther(
                                                workflowKey, lease.getOwnerId(), lease.getLeaseExpiresAt()))
                                        .orElseGet(() -> CoordinationResult.error(workflowKey,
                                                "Failed to acquire ownership", null)))
                                .block();
                    }

                    WorkflowOwnershipLease lease = optLease.get();
                    Duration elapsed = Duration.between(startTime, Instant.now());
                    metrics.recordAcquisitionSuccess(elapsed);

                    // Create and cache ownership context
                    OwnershipContext context = OwnershipContext.fromAcquisition(
                            workflowKey, lease.getLeaseId(), nodeId, partitionId,
                            leaseDuration, lease.getFencingToken(), lease.getOwnershipEpoch());
                    localOwnership.put(workflowKey, context);

                    log.info("Acquired ownership: workflow={}, node={}, fencingToken={}, expires={}",
                            workflowKey, nodeId, lease.getFencingToken(), lease.getLeaseExpiresAt());

                    emitEvent(CoordinationEvent.ownershipAcquired(workflowKey, nodeId, partitionId,
                            lease.getFencingToken(), lease.getOwnershipEpoch()));

                    return CoordinationResult.acquired(workflowKey, nodeId, partitionId,
                            lease.getFencingToken(), lease.getOwnershipEpoch(), lease.getVersion(),
                            lease.getLeaseAcquiredAt(), lease.getLeaseExpiresAt());
                });
    }

    @Override
    public Mono<BatchCoordinationResult> acquireOwnershipBatch(Set<String> workflowExecutionKeys,
                                                                AcquisitionOptions options) {
        if (workflowExecutionKeys == null || workflowExecutionKeys.isEmpty()) {
            return Mono.just(BatchCoordinationResult.empty());
        }

        Instant startTime = Instant.now();

        return Flux.fromIterable(workflowExecutionKeys)
                .flatMap(key -> acquireOwnership(key, options)
                        .map(result -> Map.entry(key, result)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .map(results -> BatchCoordinationResult.from(results, startTime, false));
    }

    // ========== Lease Renewal ==========

    @Override
    public Mono<Boolean> renewOwnership(String workflowExecutionKey) {
        return renewOwnership(workflowExecutionKey, DEFAULT_LEASE_DURATION);
    }

    @Override
    public Mono<Boolean> renewOwnership(String workflowExecutionKey, Duration extensionDuration) {
        Objects.requireNonNull(workflowExecutionKey);

        if (!active.get()) {
            return Mono.just(false);
        }

        metrics.recordRenewalAttempt();
        Instant startTime = Instant.now();

        return Mono.defer(() -> {
            String nodeId = getNodeId();
            OwnershipContext context = localOwnership.get(workflowExecutionKey);

            if (context == null || !context.isOwnedBy(nodeId)) {
                log.warn("Cannot renew: workflow {} not owned locally", workflowExecutionKey);
                return Mono.just(false);
            }

            return repository.tryRenew(workflowExecutionKey, nodeId, context.getVersion(), extensionDuration)
                    .map(optLease -> {
                        if (optLease.isEmpty()) {
                            metrics.recordRenewalFailure();
                            localOwnership.remove(workflowExecutionKey);
                            emitEvent(CoordinationEvent.renewalFailed(workflowExecutionKey, nodeId,
                                    "Failed to renew lease"));
                            return false;
                        }

                        WorkflowOwnershipLease renewed = optLease.get();
                        Duration elapsed = Duration.between(startTime, Instant.now());
                        metrics.recordRenewalSuccess(elapsed);

                        // Update local cache
                        OwnershipContext updatedContext = context.toBuilder()
                                .renewedAt(Instant.now())
                                .expiresAt(renewed.getLeaseExpiresAt())
                                .version(renewed.getVersion())
                                .build();
                        localOwnership.put(workflowExecutionKey, updatedContext);

                        log.debug("Renewed ownership: workflow={}, newExpires={}",
                                workflowExecutionKey, renewed.getLeaseExpiresAt());

                        emitEvent(CoordinationEvent.ownershipRenewed(workflowExecutionKey, nodeId,
                                renewed.getFencingToken()));
                        return true;
                    });
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Integer> renewAllOwnedWorkflows() {
        if (!active.get()) {
            return Mono.just(0);
        }

        String nodeId = getNodeId();
        Instant threshold = Instant.now().plus(RENEWAL_THRESHOLD);

        return Flux.fromIterable(new ArrayList<>(localOwnership.entrySet()))
                .filter(entry -> entry.getValue().isOwnedBy(nodeId))
                .filter(entry -> entry.getValue().getExpiresAt() != null &&
                                entry.getValue().getExpiresAt().isBefore(threshold))
                .flatMap(entry -> renewOwnership(entry.getKey())
                        .map(success -> success ? 1 : 0))
                .reduce(0, Integer::sum);
    }

    // ========== Ownership Release ==========

    @Override
    public Mono<Void> releaseOwnership(String workflowExecutionKey) {
        return releaseOwnership(workflowExecutionKey, "explicit_release");
    }

    @Override
    public Mono<Void> releaseOwnership(String workflowExecutionKey, String reason) {
        Objects.requireNonNull(workflowExecutionKey);

        return Mono.defer(() -> {
            String nodeId = getNodeId();

            return repository.tryRelease(workflowExecutionKey, nodeId, reason)
                    .doOnSuccess(released -> {
                        if (released) {
                            OwnershipContext removed = localOwnership.remove(workflowExecutionKey);
                            metrics.recordGracefulRelease();
                            log.info("Released ownership: workflow={}, reason={}", workflowExecutionKey, reason);

                            emitEvent(CoordinationEvent.ownershipReleased(workflowExecutionKey, nodeId, reason));
                        }
                    })
                    .then();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Integer> releaseAllOwnedWorkflows(String reason) {
        String nodeId = getNodeId();

        return Flux.fromIterable(new ArrayList<>(localOwnership.keySet()))
                .flatMap(key -> releaseOwnership(key, reason).thenReturn(1))
                .reduce(0, Integer::sum);
    }

    // ========== Orphan Recovery ==========

    @Override
    public Flux<String> claimOrphanedWorkflows(String failedNodeId) {
        return claimOrphanedWorkflows(failedNodeId, RecoveryOptions.DEFAULT)
                .flatMapMany(result -> Flux.fromIterable(result.getClaimedWorkflows()));
    }

    @Override
    public Mono<OrphanRecoveryResult> claimOrphanedWorkflows(String failedNodeId, RecoveryOptions options) {
        Objects.requireNonNull(failedNodeId);
        Objects.requireNonNull(options);

        if (!active.get()) {
            return Mono.just(OrphanRecoveryResult.failure(failedNodeId, getNodeId(),
                    "Coordinator is not active", null));
        }

        metrics.recordOrphanRecoveryAttempt();
        Instant startTime = Instant.now();
        String nodeId = getNodeId();

        return repository.findByOwnerId(failedNodeId)
                .filter(lease -> lease.isValid() || lease.isExpired())
                .filter(lease -> !options.isRespectPartitionAssignment() ||
                                partitionManager.shouldHandleWorkflow(lease.getWorkflowId()))
                .take(options.getMaxClaimsPerOperation())
                .flatMap(lease -> tryClaimOrphan(lease, nodeId, options))
                .collectList()
                .map(claimed -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    Set<String> claimedSet = new HashSet<>(claimed);
                    metrics.recordOrphansRecovered(claimedSet.size(), duration);

                    if (claimedSet.isEmpty()) {
                        return OrphanRecoveryResult.noOrphans(failedNodeId, nodeId);
                    }

                    log.info("Recovered {} orphan workflows from failed node {}",
                            claimedSet.size(), failedNodeId);

                    return OrphanRecoveryResult.success(failedNodeId, nodeId, claimedSet,
                            claimedSet.size(), duration);
                });
    }

    private Mono<String> tryClaimOrphan(WorkflowOwnershipLease orphanLease,
                                         String newOwnerId,
                                         RecoveryOptions options) {
        String workflowId = orphanLease.getWorkflowId();

        return repository.tryTransfer(workflowId, orphanLease.getOwnerId(), newOwnerId,
                        "Recovery from failed node")
                .flatMap(optLease -> {
                    if (optLease.isEmpty()) {
                        return Mono.empty();
                    }

                    WorkflowOwnershipLease newLease = optLease.get();

                    // Update local cache
                    OwnershipContext context = OwnershipContext.fromAcquisition(
                            workflowId, newLease.getLeaseId(), newOwnerId, newLease.getPartitionId(),
                            newLease.getLeaseDuration(), newLease.getFencingToken(), newLease.getOwnershipEpoch());
                    localOwnership.put(workflowId, context);

                    emitEvent(CoordinationEvent.orphanRecovered(workflowId, newOwnerId,
                            orphanLease.getOwnerId(), newLease.getFencingToken(), newLease.getOwnershipEpoch()));

                    return Mono.just(workflowId);
                });
    }

    @Override
    public Mono<OrphanRecoveryResult> claimAllOrphanedWorkflows() {
        if (!active.get()) {
            return Mono.just(OrphanRecoveryResult.failure(null, getNodeId(),
                    "Coordinator is not active", null));
        }

        Instant startTime = Instant.now();
        String nodeId = getNodeId();

        return repository.findAllExpired()
                .filter(lease -> partitionManager.shouldHandleWorkflow(lease.getWorkflowId()))
                .flatMap(lease -> tryClaimOrphan(lease, nodeId, RecoveryOptions.DEFAULT))
                .collectList()
                .map(claimed -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    Set<String> claimedSet = new HashSet<>(claimed);
                    metrics.recordOrphansRecovered(claimedSet.size(), duration);

                    return OrphanRecoveryResult.success(null, nodeId, claimedSet,
                            claimedSet.size(), duration);
                });
    }

    @Override
    public Mono<Boolean> isOrphaned(String workflowExecutionKey) {
        return repository.findByWorkflowId(workflowExecutionKey)
                .map(opt -> opt.map(lease -> lease.isExpired() || !lease.isValid()).orElse(true));
    }

    // ========== Ownership Query ==========

    @Override
    public Mono<OwnershipContext> getOwnershipContext(String workflowExecutionKey) {
        OwnershipContext local = localOwnership.get(workflowExecutionKey);
        if (local != null && local.isValid()) {
            return Mono.just(local);
        }

        return repository.findByWorkflowId(workflowExecutionKey)
                .map(opt -> opt.filter(WorkflowOwnershipLease::isValid)
                        .map(lease -> OwnershipContext.builder()
                                .workflowExecutionKey(workflowExecutionKey)
                                .leaseId(lease.getLeaseId())
                                .ownerId(lease.getOwnerId())
                                .partitionId(lease.getPartitionId())
                                .acquiredAt(lease.getLeaseAcquiredAt())
                                .renewedAt(lease.getLeaseRenewedAt())
                                .expiresAt(lease.getLeaseExpiresAt())
                                .leaseDuration(lease.getLeaseDuration())
                                .fencingToken(lease.getFencingToken())
                                .ownershipEpoch(lease.getOwnershipEpoch())
                                .version(lease.getVersion())
                                .previousOwnerId(lease.getPreviousOwnerId())
                                .transferredAt(lease.getOwnershipTransferredAt())
                                .transferReason(lease.getTransferReason())
                                .renewalCount(lease.getRenewalCount())
                                .clusterId(lease.getClusterId())
                                .ownerRegion(lease.getOwnerRegion())
                                .build())
                        .orElseGet(() -> OwnershipContext.empty(workflowExecutionKey)));
    }

    @Override
    public boolean isOwnedLocally(String workflowExecutionKey) {
        OwnershipContext context = localOwnership.get(workflowExecutionKey);
        return context != null && context.isOwnedBy(getNodeId());
    }

    @Override
    public Set<String> getLocallyOwnedWorkflows() {
        String nodeId = getNodeId();
        Set<String> owned = new HashSet<>();
        for (Map.Entry<String, OwnershipContext> entry : localOwnership.entrySet()) {
            if (entry.getValue().isOwnedBy(nodeId)) {
                owned.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(owned);
    }

    @Override
    public int getLocalOwnershipCount() {
        String nodeId = getNodeId();
        return (int) localOwnership.values().stream()
                .filter(ctx -> ctx.isOwnedBy(nodeId))
                .count();
    }

    @Override
    public Mono<String> getOwner(String workflowExecutionKey) {
        return getOwnershipContext(workflowExecutionKey)
                .map(ctx -> ctx.getOwnerIdOptional().orElse(null));
    }

    // ========== Fencing Token Operations ==========

    @Override
    public Mono<Long> getFencingToken(String workflowExecutionKey) {
        OwnershipContext local = localOwnership.get(workflowExecutionKey);
        if (local != null) {
            return Mono.just(local.getFencingToken());
        }

        return repository.findByWorkflowId(workflowExecutionKey)
                .map(opt -> opt.map(WorkflowOwnershipLease::getFencingToken).orElse(-1L));
    }

    @Override
    public Mono<Boolean> validateFencingToken(String workflowExecutionKey, long fencingToken) {
        return getFencingToken(workflowExecutionKey)
                .map(current -> current <= fencingToken);
    }

    // ========== Event Listeners ==========

    @Override
    public void addCoordinationEventListener(ICoordinationEventListener listener) {
        Objects.requireNonNull(listener);
        listeners.add(listener);
    }

    @Override
    public void removeCoordinationEventListener(ICoordinationEventListener listener) {
        listeners.remove(listener);
    }

    private void emitEvent(CoordinationEvent event) {
        for (ICoordinationEventListener listener : listeners) {
            try {
                listener.onCoordinationEvent(event);
            } catch (Exception e) {
                log.error("Error notifying coordination event listener", e);
            }
        }
    }

    // ========== Health & Metrics ==========

    @Override
    public Mono<CoordinatorHealth> getHealth() {
        if (!active.get()) {
            return Mono.just(CoordinatorHealth.down("Coordinator is not active"));
        }

        int ownershipCount = getLocalOwnershipCount();
        Instant checkStart = Instant.now();

        return repository.countActive()
                .map(count -> {
                    Duration latency = Duration.between(checkStart, Instant.now());
                    return CoordinatorHealth.healthy(getNodeId(), startedAt, ownershipCount, latency);
                })
                .onErrorResume(e -> Mono.just(
                        CoordinatorHealth.unhealthy("Backend error", startedAt, e.getMessage())));
    }

    @Override
    public CoordinationMetrics getMetrics() {
        metrics.setCurrentOwnershipCount(getLocalOwnershipCount());
        return metrics;
    }

    @Override
    public String getNodeId() {
        return clusterCoordinator.getNodeId();
    }

    // ========== Private Helper Methods ==========

    private Mono<Void> loadExistingOwnership() {
        String nodeId = getNodeId();

        return repository.findByOwnerId(nodeId)
                .filter(WorkflowOwnershipLease::isValid)
                .doOnNext(lease -> {
                    OwnershipContext context = OwnershipContext.builder()
                            .workflowExecutionKey(lease.getWorkflowId())
                            .leaseId(lease.getLeaseId())
                            .ownerId(lease.getOwnerId())
                            .partitionId(lease.getPartitionId())
                            .acquiredAt(lease.getLeaseAcquiredAt())
                            .renewedAt(lease.getLeaseRenewedAt())
                            .expiresAt(lease.getLeaseExpiresAt())
                            .leaseDuration(lease.getLeaseDuration())
                            .fencingToken(lease.getFencingToken())
                            .ownershipEpoch(lease.getOwnershipEpoch())
                            .version(lease.getVersion())
                            .renewalCount(lease.getRenewalCount())
                            .build();
                    localOwnership.put(lease.getWorkflowId(), context);
                    log.debug("Loaded existing ownership: workflow={}", lease.getWorkflowId());
                })
                .then();
    }

    private Mono<Void> startBackgroundTasks() {
        return Mono.fromRunnable(() -> {
            scheduledExecutor = Executors.newScheduledThreadPool(1,
                    r -> new Thread(r, "coord-renewal-" + getNodeId()));

            renewalTask = scheduledExecutor.scheduleAtFixedRate(
                    this::performBackgroundRenewal,
                    RENEWAL_CHECK_INTERVAL.toMillis(),
                    RENEWAL_CHECK_INTERVAL.toMillis(),
                    TimeUnit.MILLISECONDS
            );

            log.info("Started background renewal task");
        });
    }

    private void performBackgroundRenewal() {
        if (!active.get()) return;

        try {
            Integer renewed = renewAllOwnedWorkflows().block(Duration.ofSeconds(30));
            if (renewed != null && renewed > 0) {
                log.debug("Background renewal completed: {} leases renewed", renewed);
            }
        } catch (Exception e) {
            log.error("Error during background renewal", e);
        }
    }

    private int computePartitionId(String workflowKey) {
        return Math.abs(workflowKey.hashCode()) % partitionManager.getTotalPartitions();
    }
}
