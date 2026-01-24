package com.lyshra.open.app.core.engine.lock;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Service interface for managing workflow execution locks.
 *
 * <p>This service provides distributed locking capabilities to prevent
 * concurrent execution of the same workflow instance, ensuring that:
 * <ul>
 *   <li>Only one resume operation can proceed at a time for a workflow</li>
 *   <li>Locks have automatic expiration to prevent deadlocks</li>
 *   <li>Lock ownership is tracked for proper release</li>
 * </ul>
 *
 * <h2>Lock Lifecycle</h2>
 * <pre>
 * tryAcquire() ─→ ACQUIRED
 *                    │
 *         ┌─────────┼──────────┐
 *         │         │          │
 *     extend()   release()  EXPIRED
 *         │         │          │
 *         └─────────┴──────────┘
 *                    │
 *                RELEASED
 * </pre>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * lockService.tryAcquire(workflowId, ownerId, Duration.ofMinutes(5))
 *     .flatMap(acquired -> {
 *         if (!acquired) {
 *             return Mono.error(new WorkflowLockedException("Workflow is locked"));
 *         }
 *         return performResumeOperation()
 *             .doFinally(signal -> lockService.release(workflowId, ownerId).subscribe());
 *     });
 * }</pre>
 *
 * @see WorkflowLock
 * @see WorkflowLockedException
 */
public interface ILyshraOpenAppWorkflowLockService {

    /**
     * Attempts to acquire a lock on a workflow instance.
     *
     * @param workflowInstanceId the workflow instance to lock
     * @param ownerId identifier of the lock requester (e.g., server ID, thread ID)
     * @param duration how long to hold the lock before automatic expiration
     * @return true if lock acquired, false if already locked by another owner
     */
    Mono<Boolean> tryAcquire(String workflowInstanceId, String ownerId, Duration duration);

    /**
     * Acquires a lock on a workflow instance, waiting if necessary.
     *
     * @param workflowInstanceId the workflow instance to lock
     * @param ownerId identifier of the lock requester
     * @param duration how long to hold the lock
     * @param waitTimeout maximum time to wait for lock acquisition
     * @return true if lock acquired, false if timeout exceeded
     */
    Mono<Boolean> acquireWithWait(String workflowInstanceId, String ownerId,
                                   Duration duration, Duration waitTimeout);

    /**
     * Releases a lock on a workflow instance.
     *
     * @param workflowInstanceId the workflow instance to unlock
     * @param ownerId identifier of the lock holder
     * @return true if lock released, false if not held by this owner
     */
    Mono<Boolean> release(String workflowInstanceId, String ownerId);

    /**
     * Extends a lock's expiration time.
     *
     * @param workflowInstanceId the workflow instance
     * @param ownerId identifier of the lock holder
     * @param extensionDuration how much time to add to the lock
     * @return true if extended, false if not held by this owner or expired
     */
    Mono<Boolean> extend(String workflowInstanceId, String ownerId, Duration extensionDuration);

    /**
     * Checks if a workflow instance is currently locked.
     *
     * @param workflowInstanceId the workflow instance
     * @return true if locked (by anyone)
     */
    Mono<Boolean> isLocked(String workflowInstanceId);

    /**
     * Checks if a workflow instance is locked by a specific owner.
     *
     * @param workflowInstanceId the workflow instance
     * @param ownerId the owner to check
     * @return true if locked by the specified owner
     */
    Mono<Boolean> isLockedBy(String workflowInstanceId, String ownerId);

    /**
     * Gets the current lock information for a workflow instance.
     *
     * @param workflowInstanceId the workflow instance
     * @return the lock info if locked, empty if not locked
     */
    Mono<Optional<WorkflowLock>> getLockInfo(String workflowInstanceId);

    /**
     * Forces release of a lock (admin operation).
     * Should be used with caution as it may cause inconsistent state.
     *
     * @param workflowInstanceId the workflow instance
     * @param reason the reason for force release
     * @return true if a lock was released
     */
    Mono<Boolean> forceRelease(String workflowInstanceId, String reason);

    /**
     * Gets count of currently held locks.
     *
     * @return number of active locks
     */
    Mono<Long> getActiveLockCount();

    /**
     * Cleans up expired locks.
     * This is called automatically but can be invoked manually.
     *
     * @return number of expired locks removed
     */
    Mono<Long> cleanupExpiredLocks();

    /**
     * Executes an action within a lock scope.
     * Automatically acquires and releases the lock.
     *
     * @param workflowInstanceId the workflow instance to lock
     * @param ownerId identifier of the lock requester
     * @param duration lock duration
     * @param action the action to execute while holding the lock
     * @param <T> the result type
     * @return the result of the action, or error if lock cannot be acquired
     */
    default <T> Mono<T> executeWithLock(String workflowInstanceId, String ownerId,
                                         Duration duration, Mono<T> action) {
        return tryAcquire(workflowInstanceId, ownerId, duration)
                .flatMap(acquired -> {
                    if (!acquired) {
                        return Mono.error(new WorkflowLockedException(
                                "Cannot acquire lock for workflow: " + workflowInstanceId));
                    }
                    return action
                            .doFinally(signal -> release(workflowInstanceId, ownerId).subscribe());
                });
    }
}
