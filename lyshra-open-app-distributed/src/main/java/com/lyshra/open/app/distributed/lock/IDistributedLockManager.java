package com.lyshra.open.app.distributed.lock;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

/**
 * Interface for distributed lock management.
 *
 * Provides cluster-wide mutual exclusion for workflow operations. This is essential
 * for preventing race conditions during:
 * - Workflow ownership acquisition
 * - State transitions
 * - Checkpoint operations
 *
 * The implementation uses time-bounded locks that automatically release if the
 * holder fails or times out, preventing deadlocks in distributed scenarios.
 *
 * Design Pattern: Distributed Mutex Pattern with automatic expiration.
 */
public interface IDistributedLockManager {

    /**
     * Initializes the lock manager.
     *
     * @return Mono that completes when initialization is done
     */
    Mono<Void> initialize();

    /**
     * Shuts down the lock manager, releasing all held locks.
     *
     * @return Mono that completes when shutdown is done
     */
    Mono<Void> shutdown();

    /**
     * Attempts to acquire a distributed lock.
     *
     * This method is non-blocking and returns immediately with the result.
     * If the lock cannot be acquired, returns false.
     *
     * @param lockKey the unique key identifying the lock
     * @param timeout the maximum time to wait for the lock
     * @return Mono containing true if lock was acquired, false otherwise
     */
    Mono<Boolean> tryLock(String lockKey, Duration timeout);

    /**
     * Acquires a distributed lock, waiting if necessary.
     *
     * This method blocks until the lock is acquired or the timeout expires.
     *
     * @param lockKey the unique key identifying the lock
     * @param timeout the maximum time to wait for the lock
     * @return Mono containing the lock result
     */
    Mono<DistributedLockResult> lock(String lockKey, Duration timeout);

    /**
     * Releases a distributed lock.
     *
     * @param lockKey the unique key identifying the lock
     * @return Mono that completes when the lock is released
     */
    Mono<Void> unlock(String lockKey);

    /**
     * Checks if a lock is currently held by any node.
     *
     * @param lockKey the unique key identifying the lock
     * @return true if the lock is held
     */
    boolean isLocked(String lockKey);

    /**
     * Checks if this node holds a specific lock.
     *
     * @param lockKey the unique key identifying the lock
     * @return true if this node holds the lock
     */
    boolean isHeldByCurrentNode(String lockKey);

    /**
     * Returns the node ID holding a specific lock.
     *
     * @param lockKey the unique key identifying the lock
     * @return Optional containing the holder node ID
     */
    Optional<String> getLockHolder(String lockKey);

    /**
     * Extends the lease of a held lock.
     *
     * @param lockKey the unique key identifying the lock
     * @param extension the additional lease duration
     * @return Mono containing true if extension succeeded
     */
    Mono<Boolean> extendLease(String lockKey, Duration extension);

    /**
     * Forcibly releases all locks held by a specific node.
     * Should only be called during failover scenarios.
     *
     * @param nodeId the node whose locks should be released
     * @return Mono containing the number of locks released
     */
    Mono<Integer> releaseNodeLocks(String nodeId);

    /**
     * Executes a task while holding a distributed lock.
     *
     * Acquires the lock, executes the task, and releases the lock.
     * If the lock cannot be acquired within the timeout, the task is not executed.
     *
     * @param lockKey the unique key identifying the lock
     * @param timeout the maximum time to wait for the lock
     * @param task the task to execute while holding the lock
     * @param <T> the return type of the task
     * @return Mono containing the task result, or error if lock not acquired
     */
    <T> Mono<T> withLock(String lockKey, Duration timeout, Mono<T> task);

    /**
     * Returns the number of locks currently held by this node.
     *
     * @return lock count
     */
    int getHeldLockCount();
}
