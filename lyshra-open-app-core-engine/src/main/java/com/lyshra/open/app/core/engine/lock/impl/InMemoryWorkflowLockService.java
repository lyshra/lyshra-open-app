package com.lyshra.open.app.core.engine.lock.impl;

import com.lyshra.open.app.core.engine.lock.ILyshraOpenAppWorkflowLockService;
import com.lyshra.open.app.core.engine.lock.WorkflowLock;
import com.lyshra.open.app.core.engine.lock.WorkflowLockedException;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation of the workflow lock service.
 *
 * <p>This implementation is suitable for single-instance deployments.
 * For distributed deployments, use a database-backed or Redis-backed
 * implementation.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Thread-safe lock acquisition and release</li>
 *   <li>Automatic lock expiration</li>
 *   <li>Lock extension support</li>
 *   <li>Periodic cleanup of expired locks</li>
 * </ul>
 */
@Slf4j
public class InMemoryWorkflowLockService implements ILyshraOpenAppWorkflowLockService {

    private final Map<String, WorkflowLock> locks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor;
    private final AtomicLong acquiredCount = new AtomicLong(0);
    private final AtomicLong releasedCount = new AtomicLong(0);
    private final AtomicLong expiredCount = new AtomicLong(0);

    // Configuration
    private final Duration defaultLockDuration;
    private final Duration cleanupInterval;
    private final int maxWaitRetries;
    private final Duration retryInterval;

    private InMemoryWorkflowLockService() {
        this(Duration.ofMinutes(5), Duration.ofSeconds(30), 10, Duration.ofMillis(100));
    }

    public InMemoryWorkflowLockService(Duration defaultLockDuration, Duration cleanupInterval,
                                        int maxWaitRetries, Duration retryInterval) {
        this.defaultLockDuration = defaultLockDuration;
        this.cleanupInterval = cleanupInterval;
        this.maxWaitRetries = maxWaitRetries;
        this.retryInterval = retryInterval;

        // Start cleanup task
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "workflow-lock-cleanup");
            t.setDaemon(true);
            return t;
        });

        this.cleanupExecutor.scheduleAtFixedRate(
                this::cleanupExpiredLocksSync,
                cleanupInterval.toMillis(),
                cleanupInterval.toMillis(),
                TimeUnit.MILLISECONDS);

        log.info("InMemoryWorkflowLockService initialized with cleanup interval: {}", cleanupInterval);
    }

    private static final class SingletonHelper {
        private static final InMemoryWorkflowLockService INSTANCE = new InMemoryWorkflowLockService();
    }

    public static InMemoryWorkflowLockService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    @Override
    public Mono<Boolean> tryAcquire(String workflowInstanceId, String ownerId, Duration duration) {
        return Mono.fromCallable(() -> tryAcquireSync(workflowInstanceId, ownerId, duration, "resume"));
    }

    /**
     * Synchronous lock acquisition for internal use.
     */
    public boolean tryAcquireSync(String workflowInstanceId, String ownerId,
                                   Duration duration, String operation) {
        if (workflowInstanceId == null || ownerId == null) {
            throw new IllegalArgumentException("workflowInstanceId and ownerId cannot be null");
        }

        Duration lockDuration = duration != null ? duration : defaultLockDuration;

        return locks.compute(workflowInstanceId, (key, existingLock) -> {
            // No existing lock
            if (existingLock == null) {
                log.debug("Acquiring new lock: workflowId={}, owner={}, duration={}",
                        workflowInstanceId, ownerId, lockDuration);
                acquiredCount.incrementAndGet();
                return WorkflowLock.create(workflowInstanceId, ownerId, lockDuration, operation);
            }

            // Existing lock by same owner - refresh it
            if (existingLock.getOwnerId().equals(ownerId)) {
                log.debug("Refreshing existing lock: workflowId={}, owner={}",
                        workflowInstanceId, ownerId);
                return existingLock.extend(lockDuration);
            }

            // Existing lock by different owner - check if expired
            if (existingLock.isExpired()) {
                log.debug("Taking over expired lock: workflowId={}, previousOwner={}, newOwner={}",
                        workflowInstanceId, existingLock.getOwnerId(), ownerId);
                expiredCount.incrementAndGet();
                acquiredCount.incrementAndGet();
                return WorkflowLock.create(workflowInstanceId, ownerId, lockDuration, operation);
            }

            // Lock held by another owner and not expired - cannot acquire
            log.debug("Lock acquisition failed - held by another: workflowId={}, holder={}",
                    workflowInstanceId, existingLock.getOwnerId());
            return existingLock;
        }).getOwnerId().equals(ownerId);
    }

    @Override
    public Mono<Boolean> acquireWithWait(String workflowInstanceId, String ownerId,
                                          Duration duration, Duration waitTimeout) {
        return Mono.defer(() -> {
            long startTime = System.currentTimeMillis();
            long timeoutMs = waitTimeout.toMillis();
            int retries = 0;

            while (System.currentTimeMillis() - startTime < timeoutMs && retries < maxWaitRetries) {
                if (tryAcquireSync(workflowInstanceId, ownerId, duration, "resume-wait")) {
                    return Mono.just(true);
                }

                retries++;
                try {
                    Thread.sleep(retryInterval.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Mono.just(false);
                }
            }

            return Mono.just(false);
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> release(String workflowInstanceId, String ownerId) {
        return Mono.fromCallable(() -> releaseSync(workflowInstanceId, ownerId));
    }

    /**
     * Synchronous lock release for internal use.
     */
    public boolean releaseSync(String workflowInstanceId, String ownerId) {
        if (workflowInstanceId == null || ownerId == null) {
            return false;
        }

        boolean[] released = {false};

        locks.computeIfPresent(workflowInstanceId, (key, existingLock) -> {
            if (existingLock.getOwnerId().equals(ownerId)) {
                log.debug("Releasing lock: workflowId={}, owner={}", workflowInstanceId, ownerId);
                released[0] = true;
                releasedCount.incrementAndGet();
                return null; // Remove the lock
            }
            // Lock held by different owner - cannot release
            log.warn("Cannot release lock - not owner: workflowId={}, holder={}, requester={}",
                    workflowInstanceId, existingLock.getOwnerId(), ownerId);
            return existingLock;
        });

        return released[0];
    }

    @Override
    public Mono<Boolean> extend(String workflowInstanceId, String ownerId, Duration extensionDuration) {
        return Mono.fromCallable(() -> {
            if (workflowInstanceId == null || ownerId == null) {
                return false;
            }

            boolean[] extended = {false};

            locks.computeIfPresent(workflowInstanceId, (key, existingLock) -> {
                if (existingLock.getOwnerId().equals(ownerId) && !existingLock.isExpired()) {
                    log.debug("Extending lock: workflowId={}, owner={}, extension={}",
                            workflowInstanceId, ownerId, extensionDuration);
                    extended[0] = true;
                    return existingLock.extend(extensionDuration);
                }
                return existingLock;
            });

            return extended[0];
        });
    }

    @Override
    public Mono<Boolean> isLocked(String workflowInstanceId) {
        return Mono.fromCallable(() -> {
            WorkflowLock lock = locks.get(workflowInstanceId);
            return lock != null && lock.isValid();
        });
    }

    @Override
    public Mono<Boolean> isLockedBy(String workflowInstanceId, String ownerId) {
        return Mono.fromCallable(() -> {
            WorkflowLock lock = locks.get(workflowInstanceId);
            return lock != null && lock.isValid() && lock.getOwnerId().equals(ownerId);
        });
    }

    @Override
    public Mono<Optional<WorkflowLock>> getLockInfo(String workflowInstanceId) {
        return Mono.fromCallable(() -> {
            WorkflowLock lock = locks.get(workflowInstanceId);
            if (lock != null && !lock.isExpired()) {
                return Optional.of(lock);
            }
            return Optional.empty();
        });
    }

    @Override
    public Mono<Boolean> forceRelease(String workflowInstanceId, String reason) {
        return Mono.fromCallable(() -> {
            WorkflowLock removed = locks.remove(workflowInstanceId);
            if (removed != null) {
                log.warn("Force released lock: workflowId={}, owner={}, reason={}",
                        workflowInstanceId, removed.getOwnerId(), reason);
                releasedCount.incrementAndGet();
                return true;
            }
            return false;
        });
    }

    @Override
    public Mono<Long> getActiveLockCount() {
        return Mono.fromCallable(() -> locks.values().stream()
                .filter(lock -> !lock.isExpired())
                .count());
    }

    @Override
    public Mono<Long> cleanupExpiredLocks() {
        return Mono.fromCallable(this::cleanupExpiredLocksSync);
    }

    private long cleanupExpiredLocksSync() {
        long count = 0;
        for (Map.Entry<String, WorkflowLock> entry : locks.entrySet()) {
            if (entry.getValue().isExpired()) {
                locks.remove(entry.getKey(), entry.getValue());
                count++;
                expiredCount.incrementAndGet();
                log.debug("Cleaned up expired lock: workflowId={}, owner={}",
                        entry.getKey(), entry.getValue().getOwnerId());
            }
        }
        if (count > 0) {
            log.info("Cleaned up {} expired locks", count);
        }
        return count;
    }

    /**
     * Gets statistics about lock operations.
     */
    public LockStatistics getStatistics() {
        return new LockStatistics(
                locks.size(),
                locks.values().stream().filter(lock -> !lock.isExpired()).count(),
                acquiredCount.get(),
                releasedCount.get(),
                expiredCount.get()
        );
    }

    /**
     * Shuts down the cleanup executor.
     * Should be called on application shutdown.
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("InMemoryWorkflowLockService shut down");
    }

    /**
     * Clears all locks (for testing).
     */
    public void reset() {
        locks.clear();
        acquiredCount.set(0);
        releasedCount.set(0);
        expiredCount.set(0);
    }

    /**
     * Statistics record for lock operations.
     */
    public record LockStatistics(
            long totalLocks,
            long activeLocks,
            long acquiredCount,
            long releasedCount,
            long expiredCount
    ) {}
}
