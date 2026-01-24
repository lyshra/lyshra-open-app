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
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lease-based implementation of workflow ownership management.
 *
 * This implementation uses time-bounded leases to ensure exclusive ownership
 * of workflow executions. Key features:
 *
 * 1. Lease-based ownership: Each workflow has a lease that must be renewed
 * 2. Automatic expiration: Leases expire if not renewed, enabling failover
 * 3. Partition-aware: Only allows ownership of workflows on local partitions
 * 4. Heartbeat-based renewal: Background thread renews leases periodically
 *
 * Concurrency Strategy:
 * - Uses ConcurrentHashMap for thread-safe ownership tracking
 * - Distributed locks prevent race conditions during acquisition
 * - Atomic operations ensure consistency
 *
 * Failure Handling:
 * - Lease expiration triggers automatic ownership release
 * - Failed nodes' leases expire, allowing workflow reassignment
 * - Graceful shutdown releases all leases
 *
 * Thread Safety: This class is thread-safe.
 */
@Slf4j
public class LeaseBasedOwnershipManager implements IWorkflowOwnershipManager {

    private static final Duration DEFAULT_LEASE_DURATION = Duration.ofSeconds(30);
    private static final Duration RENEWAL_INTERVAL = Duration.ofSeconds(10);
    private static final String OWNERSHIP_LOCK_PREFIX = "ownership:";

    private final IClusterCoordinator clusterCoordinator;
    private final IPartitionManager partitionManager;
    private final IDistributedLockManager lockManager;

    // Local ownership tracking
    private final ConcurrentHashMap<String, WorkflowLease> localLeases;
    private final List<IWorkflowOwnershipChangeListener> listeners;
    private final AtomicLong ownershipVersion;
    private final AtomicBoolean initialized;

    // Background renewal
    private ScheduledExecutorService renewalExecutor;
    private ScheduledFuture<?> renewalTask;

    /**
     * Internal representation of a workflow lease.
     */
    private record WorkflowLease(
            String workflowExecutionKey,
            String ownerNodeId,
            Instant leaseStart,
            Instant leaseExpiration,
            long version
    ) {
        boolean isExpired() {
            return Instant.now().isAfter(leaseExpiration);
        }

        WorkflowLease renew(Duration extension, long newVersion) {
            return new WorkflowLease(
                    workflowExecutionKey,
                    ownerNodeId,
                    leaseStart,
                    Instant.now().plus(extension),
                    newVersion
            );
        }
    }

    public LeaseBasedOwnershipManager(IClusterCoordinator clusterCoordinator,
                                       IPartitionManager partitionManager,
                                       IDistributedLockManager lockManager) {
        this.clusterCoordinator = Objects.requireNonNull(clusterCoordinator);
        this.partitionManager = Objects.requireNonNull(partitionManager);
        this.lockManager = Objects.requireNonNull(lockManager);

        this.localLeases = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.ownershipVersion = new AtomicLong(0);
        this.initialized = new AtomicBoolean(false);
    }

    @Override
    public Mono<Void> initialize() {
        if (!initialized.compareAndSet(false, true)) {
            log.warn("Ownership manager already initialized");
            return Mono.empty();
        }

        log.info("Initializing lease-based ownership manager");

        return Mono.fromRunnable(() -> {
            // Start background lease renewal
            renewalExecutor = Executors.newSingleThreadScheduledExecutor(
                    r -> new Thread(r, "ownership-renewal-" + clusterCoordinator.getNodeId())
            );

            renewalTask = renewalExecutor.scheduleAtFixedRate(
                    this::renewAllLeases,
                    RENEWAL_INTERVAL.toMillis(),
                    RENEWAL_INTERVAL.toMillis(),
                    TimeUnit.MILLISECONDS
            );

            log.info("Ownership manager initialized with renewal interval: {}", RENEWAL_INTERVAL);
        });
    }

    @Override
    public Mono<Void> shutdown() {
        log.info("Shutting down ownership manager");

        return Mono.fromRunnable(() -> {
            // Stop renewal task
            if (renewalTask != null) {
                renewalTask.cancel(false);
            }
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
            Set<String> leasesToRelease = new HashSet<>(localLeases.keySet());
            for (String key : leasesToRelease) {
                releaseOwnershipInternal(key, "shutdown");
            }

            initialized.set(false);
            log.info("Ownership manager shutdown complete");
        });
    }

    @Override
    public Mono<WorkflowOwnershipResult> acquireOwnership(String workflowExecutionKey, Duration leaseDuration) {
        Objects.requireNonNull(workflowExecutionKey, "workflowExecutionKey must not be null");
        Duration actualDuration = leaseDuration != null ? leaseDuration : DEFAULT_LEASE_DURATION;

        return Mono.defer(() -> {
            // Check if this workflow belongs to a local partition
            if (!partitionManager.shouldHandleWorkflow(workflowExecutionKey)) {
                Optional<String> targetNode = partitionManager.getTargetNode(workflowExecutionKey);
                return Mono.just(WorkflowOwnershipResult.wrongPartition(
                        workflowExecutionKey,
                        -1,
                        targetNode.orElse("unknown")
                ));
            }

            // Check if we already own this workflow
            WorkflowLease existingLease = localLeases.get(workflowExecutionKey);
            if (existingLease != null && !existingLease.isExpired()) {
                log.debug("Workflow {} already owned locally", workflowExecutionKey);
                return Mono.just(WorkflowOwnershipResult.acquired(
                        workflowExecutionKey,
                        clusterCoordinator.getNodeId(),
                        existingLease.leaseStart(),
                        existingLease.leaseExpiration(),
                        existingLease.version()
                ));
            }

            // Acquire distributed lock for ownership
            String lockKey = OWNERSHIP_LOCK_PREFIX + workflowExecutionKey;
            return lockManager.tryLock(lockKey, Duration.ofSeconds(5))
                    .flatMap(acquired -> {
                        if (!acquired) {
                            return Mono.just(WorkflowOwnershipResult.error(
                                    workflowExecutionKey,
                                    "Failed to acquire ownership lock"
                            ));
                        }

                        try {
                            return doAcquireOwnership(workflowExecutionKey, actualDuration)
                                    .doFinally(signal -> lockManager.unlock(lockKey).subscribe());
                        } catch (Exception e) {
                            lockManager.unlock(lockKey).subscribe();
                            throw e;
                        }
                    });
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<WorkflowOwnershipResult> doAcquireOwnership(String workflowExecutionKey, Duration leaseDuration) {
        String nodeId = clusterCoordinator.getNodeId();
        Instant now = Instant.now();
        Instant expiration = now.plus(leaseDuration);
        long version = ownershipVersion.incrementAndGet();

        WorkflowLease lease = new WorkflowLease(
                workflowExecutionKey,
                nodeId,
                now,
                expiration,
                version
        );

        localLeases.put(workflowExecutionKey, lease);

        log.info("Acquired ownership of workflow {} with lease expiring at {}",
                workflowExecutionKey, expiration);

        // Notify listeners
        notifyListeners(WorkflowOwnershipChangeEvent.acquired(workflowExecutionKey, nodeId, version));

        return Mono.just(WorkflowOwnershipResult.acquired(
                workflowExecutionKey,
                nodeId,
                now,
                expiration,
                version
        ));
    }

    @Override
    public Mono<Boolean> renewLease(String workflowExecutionKey, Duration extensionDuration) {
        Objects.requireNonNull(workflowExecutionKey, "workflowExecutionKey must not be null");
        Duration extension = extensionDuration != null ? extensionDuration : DEFAULT_LEASE_DURATION;

        return Mono.fromCallable(() -> {
            WorkflowLease currentLease = localLeases.get(workflowExecutionKey);

            if (currentLease == null) {
                log.warn("Cannot renew lease for workflow {}: not owned locally", workflowExecutionKey);
                return false;
            }

            if (currentLease.isExpired()) {
                log.warn("Cannot renew expired lease for workflow {}", workflowExecutionKey);
                localLeases.remove(workflowExecutionKey);
                notifyListeners(WorkflowOwnershipChangeEvent.expired(
                        workflowExecutionKey,
                        clusterCoordinator.getNodeId(),
                        clusterCoordinator.getNodeId()
                ));
                return false;
            }

            // Renew the lease
            long newVersion = ownershipVersion.incrementAndGet();
            WorkflowLease renewed = currentLease.renew(extension, newVersion);
            localLeases.put(workflowExecutionKey, renewed);

            log.debug("Renewed lease for workflow {} until {}", workflowExecutionKey, renewed.leaseExpiration());
            return true;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> releaseOwnership(String workflowExecutionKey) {
        Objects.requireNonNull(workflowExecutionKey, "workflowExecutionKey must not be null");

        return Mono.fromRunnable(() -> releaseOwnershipInternal(workflowExecutionKey, "explicit release"))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void releaseOwnershipInternal(String workflowExecutionKey, String reason) {
        WorkflowLease removed = localLeases.remove(workflowExecutionKey);
        if (removed != null) {
            log.info("Released ownership of workflow {}: {}", workflowExecutionKey, reason);
            notifyListeners(WorkflowOwnershipChangeEvent.released(
                    workflowExecutionKey,
                    clusterCoordinator.getNodeId(),
                    reason
            ));
        }
    }

    @Override
    public boolean isOwnedLocally(String workflowExecutionKey) {
        WorkflowLease lease = localLeases.get(workflowExecutionKey);
        return lease != null && !lease.isExpired();
    }

    @Override
    public Optional<String> getOwner(String workflowExecutionKey) {
        WorkflowLease lease = localLeases.get(workflowExecutionKey);
        if (lease != null && !lease.isExpired()) {
            return Optional.of(lease.ownerNodeId());
        }
        return Optional.empty();
    }

    @Override
    public Set<String> getLocallyOwnedWorkflows() {
        Set<String> owned = new HashSet<>();
        for (Map.Entry<String, WorkflowLease> entry : localLeases.entrySet()) {
            if (!entry.getValue().isExpired()) {
                owned.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(owned);
    }

    @Override
    public int getLocalOwnershipCount() {
        int count = 0;
        for (WorkflowLease lease : localLeases.values()) {
            if (!lease.isExpired()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public Mono<Set<String>> revokeNodeOwnership(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        return Mono.fromCallable(() -> {
            // This implementation only tracks local leases
            // In a distributed setting, this would need to coordinate with other nodes
            if (nodeId.equals(clusterCoordinator.getNodeId())) {
                Set<String> revoked = new HashSet<>(localLeases.keySet());
                for (String key : revoked) {
                    releaseOwnershipInternal(key, "ownership revoked");
                }
                return revoked;
            }
            return Collections.<String>emptySet();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public void addOwnershipChangeListener(IWorkflowOwnershipChangeListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        listeners.add(listener);
    }

    @Override
    public void removeOwnershipChangeListener(IWorkflowOwnershipChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Background task to renew all local leases.
     */
    private void renewAllLeases() {
        if (!initialized.get()) {
            return;
        }

        List<String> expiredLeases = new ArrayList<>();

        for (Map.Entry<String, WorkflowLease> entry : localLeases.entrySet()) {
            String key = entry.getKey();
            WorkflowLease lease = entry.getValue();

            if (lease.isExpired()) {
                expiredLeases.add(key);
                continue;
            }

            // Renew leases that are more than halfway to expiration
            Duration remaining = Duration.between(Instant.now(), lease.leaseExpiration());
            if (remaining.compareTo(DEFAULT_LEASE_DURATION.dividedBy(2)) < 0) {
                try {
                    renewLease(key, DEFAULT_LEASE_DURATION).block(Duration.ofSeconds(5));
                } catch (Exception e) {
                    log.error("Failed to renew lease for workflow {}", key, e);
                }
            }
        }

        // Clean up expired leases
        for (String key : expiredLeases) {
            WorkflowLease removed = localLeases.remove(key);
            if (removed != null) {
                log.warn("Lease expired for workflow {}", key);
                notifyListeners(WorkflowOwnershipChangeEvent.expired(
                        key,
                        clusterCoordinator.getNodeId(),
                        clusterCoordinator.getNodeId()
                ));
            }
        }
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
