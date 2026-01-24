package com.lyshra.open.app.distributed.ownership.impl;

import com.lyshra.open.app.distributed.cluster.IClusterCoordinator;
import com.lyshra.open.app.distributed.lock.IDistributedLockManager;
import com.lyshra.open.app.distributed.ownership.*;
import com.lyshra.open.app.distributed.sharding.IPartitionManager;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Persistable implementation of workflow ownership management.
 *
 * This implementation uses the {@link WorkflowOwnershipLease} model and
 * {@link IWorkflowOwnershipRepository} for durable ownership tracking.
 *
 * Key Features:
 * 1. Durable ownership - Leases survive restarts via repository persistence
 * 2. Fencing tokens - Prevent split-brain with monotonically increasing tokens
 * 3. Automatic renewal - Background task renews leases before expiration
 * 4. Partition-aware - Integrates with partition manager for routing
 * 5. Failure recovery - Supports ownership transfer on node failures
 *
 * Concurrency Strategy:
 * - Uses distributed locks for ownership acquisition
 * - Repository uses optimistic locking for concurrent updates
 * - Local cache for fast ownership lookups
 *
 * Thread Safety: This class is thread-safe.
 */
@Slf4j
public class PersistableOwnershipManager implements IWorkflowOwnershipManager {

    private static final Duration DEFAULT_LEASE_DURATION = Duration.ofMinutes(5);
    private static final Duration RENEWAL_THRESHOLD = Duration.ofMinutes(2);
    private static final Duration RENEWAL_CHECK_INTERVAL = Duration.ofSeconds(30);
    private static final String OWNERSHIP_LOCK_PREFIX = "ownership-lock:";

    private final IClusterCoordinator clusterCoordinator;
    private final IPartitionManager partitionManager;
    private final IDistributedLockManager lockManager;
    private final IWorkflowOwnershipRepository repository;

    // Local cache for fast lookups
    private final ConcurrentHashMap<String, WorkflowOwnershipLease> localLeaseCache;
    private final List<IWorkflowOwnershipChangeListener> listeners;
    private final AtomicBoolean initialized;

    // Background renewal
    private ScheduledExecutorService renewalExecutor;
    private ScheduledFuture<?> renewalTask;
    private ScheduledFuture<?> expirationTask;

    public PersistableOwnershipManager(IClusterCoordinator clusterCoordinator,
                                        IPartitionManager partitionManager,
                                        IDistributedLockManager lockManager,
                                        IWorkflowOwnershipRepository repository) {
        this.clusterCoordinator = Objects.requireNonNull(clusterCoordinator);
        this.partitionManager = Objects.requireNonNull(partitionManager);
        this.lockManager = Objects.requireNonNull(lockManager);
        this.repository = Objects.requireNonNull(repository);

        this.localLeaseCache = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.initialized = new AtomicBoolean(false);
    }

    @Override
    public Mono<Void> initialize() {
        if (!initialized.compareAndSet(false, true)) {
            log.warn("Persistable ownership manager already initialized");
            return Mono.empty();
        }

        log.info("Initializing persistable ownership manager");

        return repository.initialize()
                .then(loadExistingLeases())
                .then(startBackgroundTasks())
                .doOnSuccess(v -> log.info("Persistable ownership manager initialized"))
                .doOnError(e -> {
                    log.error("Failed to initialize persistable ownership manager", e);
                    initialized.set(false);
                });
    }

    @Override
    public Mono<Void> shutdown() {
        log.info("Shutting down persistable ownership manager");

        return Mono.fromRunnable(() -> {
            // Stop background tasks
            if (renewalTask != null) renewalTask.cancel(false);
            if (expirationTask != null) expirationTask.cancel(false);
            if (renewalExecutor != null) {
                renewalExecutor.shutdown();
                try {
                    if (!renewalExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        renewalExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    renewalExecutor.shutdownNow();
                }
            }

            // Release all local leases
            String nodeId = clusterCoordinator.getNodeId();
            for (String workflowId : new ArrayList<>(localLeaseCache.keySet())) {
                releaseOwnershipInternal(workflowId, "Node shutdown").block(Duration.ofSeconds(5));
            }

            localLeaseCache.clear();
            initialized.set(false);
        }).then(repository.shutdown())
          .doOnSuccess(v -> log.info("Persistable ownership manager shutdown complete"));
    }

    @Override
    public Mono<WorkflowOwnershipResult> acquireOwnership(String workflowExecutionKey, Duration leaseDuration) {
        Objects.requireNonNull(workflowExecutionKey, "workflowExecutionKey must not be null");
        Duration actualDuration = leaseDuration != null ? leaseDuration : DEFAULT_LEASE_DURATION;

        return Mono.defer(() -> {
            String nodeId = clusterCoordinator.getNodeId();

            // Step 1: Check partition ownership
            if (!partitionManager.shouldHandleWorkflow(workflowExecutionKey)) {
                Optional<String> targetNode = partitionManager.getTargetNode(workflowExecutionKey);
                return Mono.just(WorkflowOwnershipResult.wrongPartition(
                        workflowExecutionKey, -1, targetNode.orElse("unknown")));
            }

            // Step 2: Check local cache for existing ownership
            WorkflowOwnershipLease cachedLease = localLeaseCache.get(workflowExecutionKey);
            if (cachedLease != null && cachedLease.isOwnedBy(nodeId)) {
                return Mono.just(createResultFromLease(cachedLease));
            }

            // Step 3: Acquire distributed lock and attempt ownership
            String lockKey = OWNERSHIP_LOCK_PREFIX + workflowExecutionKey;
            return lockManager.tryLock(lockKey, Duration.ofSeconds(10))
                    .flatMap(acquired -> {
                        if (!acquired) {
                            return Mono.just(WorkflowOwnershipResult.error(
                                    workflowExecutionKey, "Failed to acquire ownership lock"));
                        }

                        return doAcquireOwnership(workflowExecutionKey, nodeId, actualDuration)
                                .doFinally(signal -> lockManager.unlock(lockKey).subscribe());
                    });
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<WorkflowOwnershipResult> doAcquireOwnership(String workflowId,
                                                              String nodeId,
                                                              Duration leaseDuration) {
        int partitionId = computePartitionId(workflowId);

        return repository.generateFencingToken()
                .flatMap(fencingToken -> repository.tryAcquire(
                        workflowId, nodeId, partitionId, leaseDuration, fencingToken))
                .map(optLease -> {
                    if (optLease.isEmpty()) {
                        // Check who owns it
                        WorkflowOwnershipLease existing = localLeaseCache.get(workflowId);
                        if (existing != null && existing.isValid()) {
                            return WorkflowOwnershipResult.alreadyOwned(
                                    workflowId, existing.getOwnerId(), existing.getLeaseExpiresAt());
                        }
                        return WorkflowOwnershipResult.error(workflowId, "Failed to acquire ownership");
                    }

                    WorkflowOwnershipLease lease = optLease.get();
                    localLeaseCache.put(workflowId, lease);

                    log.info("Acquired ownership of workflow {}: owner={}, expires={}",
                            workflowId, nodeId, lease.getLeaseExpiresAt());

                    notifyOwnershipAcquired(workflowId, lease);
                    return createResultFromLease(lease);
                });
    }

    @Override
    public Mono<Boolean> renewLease(String workflowExecutionKey, Duration extensionDuration) {
        Objects.requireNonNull(workflowExecutionKey, "workflowExecutionKey must not be null");
        Duration extension = extensionDuration != null ? extensionDuration : DEFAULT_LEASE_DURATION;

        return Mono.defer(() -> {
            String nodeId = clusterCoordinator.getNodeId();
            WorkflowOwnershipLease cached = localLeaseCache.get(workflowExecutionKey);

            if (cached == null || !cached.isOwnedBy(nodeId)) {
                log.warn("Cannot renew lease for {}: not owned locally", workflowExecutionKey);
                return Mono.just(false);
            }

            return repository.tryRenew(workflowExecutionKey, nodeId, cached.getVersion(), extension)
                    .map(optLease -> {
                        if (optLease.isEmpty()) {
                            log.warn("Failed to renew lease for {}", workflowExecutionKey);
                            localLeaseCache.remove(workflowExecutionKey);
                            return false;
                        }

                        WorkflowOwnershipLease renewed = optLease.get();
                        localLeaseCache.put(workflowExecutionKey, renewed);

                        log.debug("Renewed lease for {}: newExpiration={}",
                                workflowExecutionKey, renewed.getLeaseExpiresAt());
                        return true;
                    });
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> releaseOwnership(String workflowExecutionKey) {
        return releaseOwnershipInternal(workflowExecutionKey, "Explicit release");
    }

    private Mono<Void> releaseOwnershipInternal(String workflowExecutionKey, String reason) {
        Objects.requireNonNull(workflowExecutionKey, "workflowExecutionKey must not be null");

        return Mono.defer(() -> {
            String nodeId = clusterCoordinator.getNodeId();

            return repository.tryRelease(workflowExecutionKey, nodeId, reason)
                    .doOnSuccess(released -> {
                        if (released) {
                            WorkflowOwnershipLease removed = localLeaseCache.remove(workflowExecutionKey);
                            log.info("Released ownership of {}: reason={}", workflowExecutionKey, reason);
                            if (removed != null) {
                                notifyOwnershipReleased(workflowExecutionKey, reason);
                            }
                        }
                    })
                    .then();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public boolean isOwnedLocally(String workflowExecutionKey) {
        String nodeId = clusterCoordinator.getNodeId();
        WorkflowOwnershipLease lease = localLeaseCache.get(workflowExecutionKey);
        return lease != null && lease.isOwnedBy(nodeId);
    }

    @Override
    public Optional<String> getOwner(String workflowExecutionKey) {
        WorkflowOwnershipLease lease = localLeaseCache.get(workflowExecutionKey);
        if (lease != null && lease.isValid()) {
            return Optional.ofNullable(lease.getOwnerId());
        }

        // Fall back to repository lookup
        return repository.findByWorkflowId(workflowExecutionKey)
                .map(opt -> opt.filter(WorkflowOwnershipLease::isValid)
                        .map(WorkflowOwnershipLease::getOwnerId)
                        .orElse(null))
                .blockOptional()
                .flatMap(owner -> Optional.ofNullable(owner));
    }

    @Override
    public Set<String> getLocallyOwnedWorkflows() {
        String nodeId = clusterCoordinator.getNodeId();
        Set<String> owned = new HashSet<>();
        for (Map.Entry<String, WorkflowOwnershipLease> entry : localLeaseCache.entrySet()) {
            if (entry.getValue().isOwnedBy(nodeId)) {
                owned.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(owned);
    }

    @Override
    public int getLocalOwnershipCount() {
        String nodeId = clusterCoordinator.getNodeId();
        return (int) localLeaseCache.values().stream()
                .filter(lease -> lease.isOwnedBy(nodeId))
                .count();
    }

    @Override
    public Mono<Set<String>> revokeNodeOwnership(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        return repository.revokeAllByOwner(nodeId, "Node failure")
                .flatMap(count -> {
                    // Remove from local cache if it was this node
                    if (nodeId.equals(clusterCoordinator.getNodeId())) {
                        Set<String> revoked = new HashSet<>();
                        Iterator<Map.Entry<String, WorkflowOwnershipLease>> iterator =
                                localLeaseCache.entrySet().iterator();
                        while (iterator.hasNext()) {
                            Map.Entry<String, WorkflowOwnershipLease> entry = iterator.next();
                            if (nodeId.equals(entry.getValue().getOwnerId())) {
                                revoked.add(entry.getKey());
                                iterator.remove();
                            }
                        }
                        return Mono.just(revoked);
                    }
                    return Mono.just(Collections.<String>emptySet());
                });
    }

    @Override
    public void addOwnershipChangeListener(IWorkflowOwnershipChangeListener listener) {
        Objects.requireNonNull(listener);
        listeners.add(listener);
    }

    @Override
    public void removeOwnershipChangeListener(IWorkflowOwnershipChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Returns the ownership lease for a workflow.
     *
     * @param workflowId the workflow ID
     * @return Optional containing the lease
     */
    public Optional<WorkflowOwnershipLease> getLease(String workflowId) {
        return Optional.ofNullable(localLeaseCache.get(workflowId));
    }

    /**
     * Returns the ownership repository.
     */
    public IWorkflowOwnershipRepository getRepository() {
        return repository;
    }

    // ========== Private Helper Methods ==========

    private Mono<Void> loadExistingLeases() {
        String nodeId = clusterCoordinator.getNodeId();

        return repository.findByOwnerId(nodeId)
                .filter(WorkflowOwnershipLease::isValid)
                .doOnNext(lease -> {
                    localLeaseCache.put(lease.getWorkflowId(), lease);
                    log.debug("Loaded existing lease: workflow={}, expires={}",
                            lease.getWorkflowId(), lease.getLeaseExpiresAt());
                })
                .then()
                .doOnSuccess(v -> log.info("Loaded {} existing leases for node {}",
                        localLeaseCache.size(), nodeId));
    }

    private Mono<Void> startBackgroundTasks() {
        return Mono.fromRunnable(() -> {
            String nodeId = clusterCoordinator.getNodeId();
            renewalExecutor = Executors.newScheduledThreadPool(2,
                    r -> new Thread(r, "ownership-renewal-" + nodeId));

            // Lease renewal task
            renewalTask = renewalExecutor.scheduleAtFixedRate(
                    this::renewExpiringLeases,
                    RENEWAL_CHECK_INTERVAL.toMillis(),
                    RENEWAL_CHECK_INTERVAL.toMillis(),
                    TimeUnit.MILLISECONDS
            );

            // Expiration cleanup task
            expirationTask = renewalExecutor.scheduleAtFixedRate(
                    this::cleanupExpiredLeases,
                    RENEWAL_CHECK_INTERVAL.toMillis() * 2,
                    RENEWAL_CHECK_INTERVAL.toMillis() * 2,
                    TimeUnit.MILLISECONDS
            );

            log.info("Started background ownership renewal tasks");
        });
    }

    private void renewExpiringLeases() {
        if (!initialized.get()) return;

        String nodeId = clusterCoordinator.getNodeId();
        Instant threshold = Instant.now().plus(RENEWAL_THRESHOLD);

        for (Map.Entry<String, WorkflowOwnershipLease> entry : localLeaseCache.entrySet()) {
            WorkflowOwnershipLease lease = entry.getValue();

            if (lease.isOwnedBy(nodeId) &&
                lease.getLeaseExpiresAt() != null &&
                lease.getLeaseExpiresAt().isBefore(threshold)) {

                try {
                    renewLease(entry.getKey(), DEFAULT_LEASE_DURATION)
                            .block(Duration.ofSeconds(5));
                } catch (Exception e) {
                    log.error("Failed to renew lease for {}", entry.getKey(), e);
                }
            }
        }
    }

    private void cleanupExpiredLeases() {
        if (!initialized.get()) return;

        // Clean local cache
        Iterator<Map.Entry<String, WorkflowOwnershipLease>> iterator =
                localLeaseCache.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, WorkflowOwnershipLease> entry = iterator.next();
            WorkflowOwnershipLease lease = entry.getValue();

            if (lease.isExpired()) {
                iterator.remove();
                log.warn("Lease expired for workflow {}", entry.getKey());
                notifyOwnershipExpired(entry.getKey());
            }
        }

        // Expire stale leases in repository
        repository.expireAllStale().subscribe();
    }

    private int computePartitionId(String workflowId) {
        // Use the partition manager's sharding logic
        return Math.abs(workflowId.hashCode()) % partitionManager.getTotalPartitions();
    }

    private WorkflowOwnershipResult createResultFromLease(WorkflowOwnershipLease lease) {
        return WorkflowOwnershipResult.acquired(
                lease.getWorkflowId(),
                lease.getOwnerId(),
                lease.getLeaseAcquiredAt(),
                lease.getLeaseExpiresAt(),
                lease.getVersion()
        );
    }

    // ========== Notification Methods ==========

    private void notifyOwnershipAcquired(String workflowId, WorkflowOwnershipLease lease) {
        WorkflowOwnershipChangeEvent event = WorkflowOwnershipChangeEvent.acquired(
                workflowId, clusterCoordinator.getNodeId(), lease.getVersion());
        notifyListeners(event);
    }

    private void notifyOwnershipReleased(String workflowId, String reason) {
        WorkflowOwnershipChangeEvent event = WorkflowOwnershipChangeEvent.released(
                workflowId, clusterCoordinator.getNodeId(), reason);
        notifyListeners(event);
    }

    private void notifyOwnershipExpired(String workflowId) {
        WorkflowOwnershipChangeEvent event = WorkflowOwnershipChangeEvent.expired(
                workflowId, clusterCoordinator.getNodeId(), clusterCoordinator.getNodeId());
        notifyListeners(event);
    }

    private void notifyListeners(WorkflowOwnershipChangeEvent event) {
        for (IWorkflowOwnershipChangeListener listener : listeners) {
            try {
                listener.onOwnershipChange(event);
            } catch (Exception e) {
                log.error("Error notifying ownership change listener", e);
            }
        }
    }
}
