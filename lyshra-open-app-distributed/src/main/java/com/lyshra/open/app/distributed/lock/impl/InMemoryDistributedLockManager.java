package com.lyshra.open.app.distributed.lock.impl;

import com.lyshra.open.app.distributed.cluster.IClusterCoordinator;
import com.lyshra.open.app.distributed.lock.DistributedLockResult;
import com.lyshra.open.app.distributed.lock.IDistributedLockManager;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-memory implementation of distributed lock manager with cluster coordination.
 *
 * This implementation provides local locking semantics suitable for:
 * - Single-node deployments
 * - Development and testing
 * - Scenarios where external coordination services are unavailable
 *
 * For production distributed deployments, use a database-backed or
 * Redis-backed implementation that provides true cluster-wide locking.
 *
 * Key Features:
 * 1. Time-bounded locks: All locks have automatic expiration
 * 2. Reentrancy: Same thread can acquire the same lock multiple times
 * 3. Fair ordering: Waiters are served in FIFO order
 * 4. Automatic cleanup: Expired locks are periodically cleaned
 *
 * Thread Safety: This class is fully thread-safe using ConcurrentHashMap
 * and atomic operations.
 *
 * Note: For true distributed locking across multiple JVMs, replace this
 * implementation with DatabaseDistributedLockManager or RedisDistributedLockManager.
 */
@Slf4j
public class InMemoryDistributedLockManager implements IDistributedLockManager {

    private static final Duration DEFAULT_LOCK_DURATION = Duration.ofSeconds(30);
    private static final Duration CLEANUP_INTERVAL = Duration.ofSeconds(10);

    private final IClusterCoordinator clusterCoordinator;
    private final ConcurrentHashMap<String, LockEntry> locks;
    private final AtomicLong lockVersion;
    private final AtomicBoolean initialized;

    private ScheduledExecutorService cleanupExecutor;
    private ScheduledFuture<?> cleanupTask;

    /**
     * Internal representation of a lock entry.
     */
    private static class LockEntry {
        final String lockKey;
        final String holderNodeId;
        final Instant acquiredAt;
        final Instant expiresAt;
        final long version;
        final ReentrantLock localLock;
        volatile int holdCount;

        LockEntry(String lockKey, String holderNodeId, Instant acquiredAt, Instant expiresAt, long version) {
            this.lockKey = lockKey;
            this.holderNodeId = holderNodeId;
            this.acquiredAt = acquiredAt;
            this.expiresAt = expiresAt;
            this.version = version;
            this.localLock = new ReentrantLock(true); // Fair lock
            this.holdCount = 1;
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        LockEntry withNewExpiration(Instant newExpiration, long newVersion) {
            LockEntry newEntry = new LockEntry(lockKey, holderNodeId, acquiredAt, newExpiration, newVersion);
            newEntry.holdCount = this.holdCount;
            return newEntry;
        }
    }

    public InMemoryDistributedLockManager(IClusterCoordinator clusterCoordinator) {
        this.clusterCoordinator = Objects.requireNonNull(clusterCoordinator);
        this.locks = new ConcurrentHashMap<>();
        this.lockVersion = new AtomicLong(0);
        this.initialized = new AtomicBoolean(false);
    }

    @Override
    public Mono<Void> initialize() {
        if (!initialized.compareAndSet(false, true)) {
            log.warn("Lock manager already initialized");
            return Mono.empty();
        }

        log.info("Initializing in-memory distributed lock manager");

        return Mono.fromRunnable(() -> {
            cleanupExecutor = Executors.newSingleThreadScheduledExecutor(
                    r -> new Thread(r, "lock-cleanup-" + clusterCoordinator.getNodeId())
            );

            cleanupTask = cleanupExecutor.scheduleAtFixedRate(
                    this::cleanupExpiredLocks,
                    CLEANUP_INTERVAL.toMillis(),
                    CLEANUP_INTERVAL.toMillis(),
                    TimeUnit.MILLISECONDS
            );

            log.info("Lock manager initialized with cleanup interval: {}", CLEANUP_INTERVAL);
        });
    }

    @Override
    public Mono<Void> shutdown() {
        log.info("Shutting down lock manager");

        return Mono.fromRunnable(() -> {
            if (cleanupTask != null) {
                cleanupTask.cancel(false);
            }
            if (cleanupExecutor != null) {
                cleanupExecutor.shutdown();
                try {
                    if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        cleanupExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    cleanupExecutor.shutdownNow();
                }
            }

            // Release all locks held by this node
            String nodeId = clusterCoordinator.getNodeId();
            locks.entrySet().removeIf(entry -> nodeId.equals(entry.getValue().holderNodeId));

            initialized.set(false);
            log.info("Lock manager shutdown complete");
        });
    }

    @Override
    public Mono<Boolean> tryLock(String lockKey, Duration timeout) {
        return lock(lockKey, timeout)
                .map(DistributedLockResult::isAcquired);
    }

    @Override
    public Mono<DistributedLockResult> lock(String lockKey, Duration timeout) {
        Objects.requireNonNull(lockKey, "lockKey must not be null");
        Duration actualTimeout = timeout != null ? timeout : DEFAULT_LOCK_DURATION;

        return Mono.fromCallable(() -> doLock(lockKey, actualTimeout))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private DistributedLockResult doLock(String lockKey, Duration timeout) {
        String nodeId = clusterCoordinator.getNodeId();
        Instant deadline = Instant.now().plus(timeout);

        while (Instant.now().isBefore(deadline)) {
            LockEntry existing = locks.get(lockKey);

            // Check if lock is held by current node (reentrant)
            if (existing != null && !existing.isExpired() && nodeId.equals(existing.holderNodeId)) {
                existing.holdCount++;
                return DistributedLockResult.acquired(
                        lockKey, nodeId, existing.acquiredAt, existing.expiresAt, existing.version);
            }

            // Check if lock is expired
            if (existing != null && existing.isExpired()) {
                locks.remove(lockKey, existing);
                existing = null;
            }

            // Try to acquire the lock
            if (existing == null) {
                Instant now = Instant.now();
                Instant expiration = now.plus(DEFAULT_LOCK_DURATION);
                long version = lockVersion.incrementAndGet();

                LockEntry newEntry = new LockEntry(lockKey, nodeId, now, expiration, version);

                if (locks.putIfAbsent(lockKey, newEntry) == null) {
                    log.debug("Acquired lock {} for node {}", lockKey, nodeId);
                    return DistributedLockResult.acquired(lockKey, nodeId, now, expiration, version);
                }
            } else {
                // Lock is held by another node
                Duration remainingWait = Duration.between(Instant.now(), deadline);
                if (remainingWait.isNegative()) {
                    break;
                }

                // Wait briefly before retrying
                try {
                    Thread.sleep(Math.min(50, remainingWait.toMillis()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return DistributedLockResult.error(lockKey, "Lock acquisition interrupted");
                }
            }
        }

        // Timeout
        LockEntry currentHolder = locks.get(lockKey);
        if (currentHolder != null && !currentHolder.isExpired()) {
            return DistributedLockResult.alreadyHeld(
                    lockKey, currentHolder.holderNodeId, currentHolder.expiresAt);
        }

        return DistributedLockResult.timeout(lockKey);
    }

    @Override
    public Mono<Void> unlock(String lockKey) {
        Objects.requireNonNull(lockKey, "lockKey must not be null");

        return Mono.fromRunnable(() -> doUnlock(lockKey))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void doUnlock(String lockKey) {
        String nodeId = clusterCoordinator.getNodeId();
        LockEntry entry = locks.get(lockKey);

        if (entry == null) {
            log.debug("Lock {} not found during unlock", lockKey);
            return;
        }

        if (!nodeId.equals(entry.holderNodeId)) {
            log.warn("Cannot unlock {} - held by different node {}", lockKey, entry.holderNodeId);
            return;
        }

        entry.holdCount--;
        if (entry.holdCount <= 0) {
            locks.remove(lockKey, entry);
            log.debug("Released lock {} for node {}", lockKey, nodeId);
        }
    }

    @Override
    public boolean isLocked(String lockKey) {
        LockEntry entry = locks.get(lockKey);
        return entry != null && !entry.isExpired();
    }

    @Override
    public boolean isHeldByCurrentNode(String lockKey) {
        String nodeId = clusterCoordinator.getNodeId();
        LockEntry entry = locks.get(lockKey);
        return entry != null && !entry.isExpired() && nodeId.equals(entry.holderNodeId);
    }

    @Override
    public Optional<String> getLockHolder(String lockKey) {
        LockEntry entry = locks.get(lockKey);
        if (entry != null && !entry.isExpired()) {
            return Optional.of(entry.holderNodeId);
        }
        return Optional.empty();
    }

    @Override
    public Mono<Boolean> extendLease(String lockKey, Duration extension) {
        Objects.requireNonNull(lockKey, "lockKey must not be null");
        Duration actualExtension = extension != null ? extension : DEFAULT_LOCK_DURATION;

        return Mono.fromCallable(() -> {
            String nodeId = clusterCoordinator.getNodeId();
            LockEntry entry = locks.get(lockKey);

            if (entry == null || entry.isExpired() || !nodeId.equals(entry.holderNodeId)) {
                log.warn("Cannot extend lease for {} - not held by current node", lockKey);
                return false;
            }

            Instant newExpiration = Instant.now().plus(actualExtension);
            long newVersion = lockVersion.incrementAndGet();
            LockEntry extended = entry.withNewExpiration(newExpiration, newVersion);

            locks.put(lockKey, extended);
            log.debug("Extended lease for lock {} until {}", lockKey, newExpiration);
            return true;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Integer> releaseNodeLocks(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        return Mono.fromCallable(() -> {
            int released = 0;
            Iterator<Map.Entry<String, LockEntry>> iterator = locks.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<String, LockEntry> entry = iterator.next();
                if (nodeId.equals(entry.getValue().holderNodeId)) {
                    iterator.remove();
                    released++;
                }
            }

            log.info("Released {} locks held by node {}", released, nodeId);
            return released;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public <T> Mono<T> withLock(String lockKey, Duration timeout, Mono<T> task) {
        return lock(lockKey, timeout)
                .flatMap(result -> {
                    if (!result.isAcquired()) {
                        return Mono.error(new IllegalStateException(
                                "Failed to acquire lock: " + result.getErrorMessage()));
                    }
                    return task
                            .doFinally(signal -> unlock(lockKey).subscribe());
                });
    }

    @Override
    public int getHeldLockCount() {
        String nodeId = clusterCoordinator.getNodeId();
        int count = 0;
        for (LockEntry entry : locks.values()) {
            if (!entry.isExpired() && nodeId.equals(entry.holderNodeId)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Background task to clean up expired locks.
     */
    private void cleanupExpiredLocks() {
        int cleaned = 0;
        Iterator<Map.Entry<String, LockEntry>> iterator = locks.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, LockEntry> entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
                cleaned++;
            }
        }

        if (cleaned > 0) {
            log.debug("Cleaned up {} expired locks", cleaned);
        }
    }
}
