package com.lyshra.open.app.core.engine.lock.impl;

import com.lyshra.open.app.core.engine.lock.WorkflowLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link InMemoryWorkflowLockService}.
 * Tests lock acquisition, release, expiration, and concurrent access prevention.
 */
class InMemoryWorkflowLockServiceTest {

    private InMemoryWorkflowLockService lockService;

    @BeforeEach
    void setUp() {
        lockService = new InMemoryWorkflowLockService(
                Duration.ofMinutes(5),  // default lock duration
                Duration.ofSeconds(60), // cleanup interval (long to prevent auto-cleanup during tests)
                10,                      // max wait retries
                Duration.ofMillis(50)   // retry interval
        );
    }

    // ========================================================================
    // LOCK ACQUISITION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Lock Acquisition")
    class LockAcquisitionTests {

        @Test
        @DisplayName("should acquire lock on unlocked workflow")
        void shouldAcquireLockOnUnlockedWorkflow() {
            // Given
            String workflowId = "workflow-123";
            String ownerId = "owner-1";

            // When
            Boolean acquired = lockService.tryAcquire(workflowId, ownerId, Duration.ofMinutes(5))
                    .block();

            // Then
            assertTrue(acquired);
            assertTrue(lockService.isLocked(workflowId).block());
            assertTrue(lockService.isLockedBy(workflowId, ownerId).block());
        }

        @Test
        @DisplayName("should fail to acquire lock when already locked by another owner")
        void shouldFailToAcquireLockWhenAlreadyLocked() {
            // Given
            String workflowId = "workflow-123";
            String owner1 = "owner-1";
            String owner2 = "owner-2";

            // When - first owner acquires
            lockService.tryAcquire(workflowId, owner1, Duration.ofMinutes(5)).block();

            // Then - second owner cannot acquire
            Boolean acquired = lockService.tryAcquire(workflowId, owner2, Duration.ofMinutes(5))
                    .block();
            assertFalse(acquired);

            // Verify lock is still held by first owner
            assertTrue(lockService.isLockedBy(workflowId, owner1).block());
            assertFalse(lockService.isLockedBy(workflowId, owner2).block());
        }

        @Test
        @DisplayName("should allow same owner to reacquire lock (refresh)")
        void shouldAllowSameOwnerToReacquireLock() {
            // Given
            String workflowId = "workflow-123";
            String ownerId = "owner-1";

            // When - acquire twice
            Boolean firstAcquire = lockService.tryAcquire(workflowId, ownerId, Duration.ofMinutes(5)).block();
            Boolean secondAcquire = lockService.tryAcquire(workflowId, ownerId, Duration.ofMinutes(10)).block();

            // Then - both should succeed
            assertTrue(firstAcquire);
            assertTrue(secondAcquire);
            assertTrue(lockService.isLockedBy(workflowId, ownerId).block());
        }

        @Test
        @DisplayName("should acquire lock on expired lock held by another")
        void shouldAcquireLockOnExpiredLock() throws InterruptedException {
            // Given
            String workflowId = "workflow-123";
            String owner1 = "owner-1";
            String owner2 = "owner-2";

            // When - first owner acquires with very short duration
            lockService.tryAcquire(workflowId, owner1, Duration.ofMillis(50)).block();

            // Wait for lock to expire
            Thread.sleep(100);

            // Then - second owner can acquire
            Boolean acquired = lockService.tryAcquire(workflowId, owner2, Duration.ofMinutes(5)).block();
            assertTrue(acquired);
            assertTrue(lockService.isLockedBy(workflowId, owner2).block());
        }

        @Test
        @DisplayName("should reject null workflowInstanceId")
        void shouldRejectNullWorkflowInstanceId() {
            assertThrows(IllegalArgumentException.class, () ->
                    lockService.tryAcquire(null, "owner-1", Duration.ofMinutes(5)).block());
        }

        @Test
        @DisplayName("should reject null ownerId")
        void shouldRejectNullOwnerId() {
            assertThrows(IllegalArgumentException.class, () ->
                    lockService.tryAcquire("workflow-123", null, Duration.ofMinutes(5)).block());
        }
    }

    // ========================================================================
    // LOCK RELEASE TESTS
    // ========================================================================

    @Nested
    @DisplayName("Lock Release")
    class LockReleaseTests {

        @Test
        @DisplayName("should release lock held by owner")
        void shouldReleaseLockHeldByOwner() {
            // Given
            String workflowId = "workflow-123";
            String ownerId = "owner-1";
            lockService.tryAcquire(workflowId, ownerId, Duration.ofMinutes(5)).block();

            // When
            Boolean released = lockService.release(workflowId, ownerId).block();

            // Then
            assertTrue(released);
            assertFalse(lockService.isLocked(workflowId).block());
        }

        @Test
        @DisplayName("should not release lock held by different owner")
        void shouldNotReleaseLockHeldByDifferentOwner() {
            // Given
            String workflowId = "workflow-123";
            String owner1 = "owner-1";
            String owner2 = "owner-2";
            lockService.tryAcquire(workflowId, owner1, Duration.ofMinutes(5)).block();

            // When
            Boolean released = lockService.release(workflowId, owner2).block();

            // Then
            assertFalse(released);
            assertTrue(lockService.isLockedBy(workflowId, owner1).block());
        }

        @Test
        @DisplayName("should return false when releasing non-existent lock")
        void shouldReturnFalseWhenReleasingNonExistentLock() {
            // When
            Boolean released = lockService.release("non-existent", "owner-1").block();

            // Then
            assertFalse(released);
        }
    }

    // ========================================================================
    // LOCK EXTENSION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Lock Extension")
    class LockExtensionTests {

        @Test
        @DisplayName("should extend lock held by owner")
        void shouldExtendLockHeldByOwner() {
            // Given
            String workflowId = "workflow-123";
            String ownerId = "owner-1";
            lockService.tryAcquire(workflowId, ownerId, Duration.ofMinutes(1)).block();

            // When
            Boolean extended = lockService.extend(workflowId, ownerId, Duration.ofMinutes(10)).block();

            // Then
            assertTrue(extended);
            assertTrue(lockService.isLocked(workflowId).block());

            // Verify extension was applied
            Optional<WorkflowLock> lockInfo = lockService.getLockInfo(workflowId).block();
            assertTrue(lockInfo.isPresent());
            assertEquals(1, lockInfo.get().getExtensionCount());
        }

        @Test
        @DisplayName("should not extend lock held by different owner")
        void shouldNotExtendLockHeldByDifferentOwner() {
            // Given
            String workflowId = "workflow-123";
            String owner1 = "owner-1";
            String owner2 = "owner-2";
            lockService.tryAcquire(workflowId, owner1, Duration.ofMinutes(5)).block();

            // When
            Boolean extended = lockService.extend(workflowId, owner2, Duration.ofMinutes(10)).block();

            // Then
            assertFalse(extended);
        }

        @Test
        @DisplayName("should not extend expired lock")
        void shouldNotExtendExpiredLock() throws InterruptedException {
            // Given
            String workflowId = "workflow-123";
            String ownerId = "owner-1";
            lockService.tryAcquire(workflowId, ownerId, Duration.ofMillis(50)).block();

            // Wait for expiration
            Thread.sleep(100);

            // When
            Boolean extended = lockService.extend(workflowId, ownerId, Duration.ofMinutes(10)).block();

            // Then
            assertFalse(extended);
        }
    }

    // ========================================================================
    // FORCE RELEASE TESTS
    // ========================================================================

    @Nested
    @DisplayName("Force Release")
    class ForceReleaseTests {

        @Test
        @DisplayName("should force release any lock")
        void shouldForceReleaseAnyLock() {
            // Given
            String workflowId = "workflow-123";
            String ownerId = "owner-1";
            lockService.tryAcquire(workflowId, ownerId, Duration.ofMinutes(5)).block();

            // When
            Boolean released = lockService.forceRelease(workflowId, "Admin override").block();

            // Then
            assertTrue(released);
            assertFalse(lockService.isLocked(workflowId).block());
        }

        @Test
        @DisplayName("should return false when force releasing non-existent lock")
        void shouldReturnFalseWhenForceReleasingNonExistentLock() {
            // When
            Boolean released = lockService.forceRelease("non-existent", "Admin override").block();

            // Then
            assertFalse(released);
        }
    }

    // ========================================================================
    // LOCK INFO TESTS
    // ========================================================================

    @Nested
    @DisplayName("Lock Information")
    class LockInfoTests {

        @Test
        @DisplayName("should return lock info for active lock")
        void shouldReturnLockInfoForActiveLock() {
            // Given
            String workflowId = "workflow-123";
            String ownerId = "owner-1";
            lockService.tryAcquireSync(workflowId, ownerId, Duration.ofMinutes(5), "test-operation");

            // When
            Optional<WorkflowLock> lockInfo = lockService.getLockInfo(workflowId).block();

            // Then
            assertTrue(lockInfo.isPresent());
            assertEquals(workflowId, lockInfo.get().getWorkflowInstanceId());
            assertEquals(ownerId, lockInfo.get().getOwnerId());
            assertEquals("test-operation", lockInfo.get().getOperation());
            assertNotNull(lockInfo.get().getAcquiredAt());
            assertNotNull(lockInfo.get().getExpiresAt());
            assertTrue(lockInfo.get().isValid());
        }

        @Test
        @DisplayName("should return empty for expired lock")
        void shouldReturnEmptyForExpiredLock() throws InterruptedException {
            // Given
            String workflowId = "workflow-123";
            String ownerId = "owner-1";
            lockService.tryAcquire(workflowId, ownerId, Duration.ofMillis(50)).block();

            // Wait for expiration
            Thread.sleep(100);

            // When
            Optional<WorkflowLock> lockInfo = lockService.getLockInfo(workflowId).block();

            // Then
            assertTrue(lockInfo.isEmpty());
        }

        @Test
        @DisplayName("should return empty for non-existent lock")
        void shouldReturnEmptyForNonExistentLock() {
            // When
            Optional<WorkflowLock> lockInfo = lockService.getLockInfo("non-existent").block();

            // Then
            assertTrue(lockInfo.isEmpty());
        }
    }

    // ========================================================================
    // CONCURRENT ACCESS TESTS
    // ========================================================================

    @Nested
    @DisplayName("Concurrent Access Prevention")
    class ConcurrentAccessTests {

        @Test
        @DisplayName("should prevent concurrent lock acquisition")
        void shouldPreventConcurrentLockAcquisition() throws InterruptedException {
            // Given
            String workflowId = "workflow-123";
            int numThreads = 10;
            CountDownLatch latch = new CountDownLatch(numThreads);
            AtomicInteger successCount = new AtomicInteger(0);
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);

            // When - multiple threads try to acquire the same lock
            for (int i = 0; i < numThreads; i++) {
                final String ownerId = "owner-" + i;
                executor.submit(() -> {
                    try {
                        Boolean acquired = lockService.tryAcquire(workflowId, ownerId, Duration.ofMinutes(5)).block();
                        if (acquired) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // Then - only one should succeed
            assertEquals(1, successCount.get());
        }

        @Test
        @DisplayName("should allow sequential lock acquisition after release")
        void shouldAllowSequentialLockAcquisitionAfterRelease() {
            // Given
            String workflowId = "workflow-123";
            String owner1 = "owner-1";
            String owner2 = "owner-2";

            // When - first owner acquires and releases
            lockService.tryAcquire(workflowId, owner1, Duration.ofMinutes(5)).block();
            lockService.release(workflowId, owner1).block();

            // Then - second owner can acquire
            Boolean acquired = lockService.tryAcquire(workflowId, owner2, Duration.ofMinutes(5)).block();
            assertTrue(acquired);
            assertTrue(lockService.isLockedBy(workflowId, owner2).block());
        }

        @Test
        @DisplayName("should handle multiple workflows independently")
        void shouldHandleMultipleWorkflowsIndependently() {
            // Given
            String workflow1 = "workflow-1";
            String workflow2 = "workflow-2";
            String ownerId = "owner-1";

            // When
            lockService.tryAcquire(workflow1, ownerId, Duration.ofMinutes(5)).block();
            lockService.tryAcquire(workflow2, ownerId, Duration.ofMinutes(5)).block();

            // Then
            assertTrue(lockService.isLockedBy(workflow1, ownerId).block());
            assertTrue(lockService.isLockedBy(workflow2, ownerId).block());

            // Release one
            lockService.release(workflow1, ownerId).block();
            assertFalse(lockService.isLocked(workflow1).block());
            assertTrue(lockService.isLockedBy(workflow2, ownerId).block());
        }
    }

    // ========================================================================
    // ACQUIRE WITH WAIT TESTS
    // ========================================================================

    @Nested
    @DisplayName("Acquire With Wait")
    class AcquireWithWaitTests {

        @Test
        @DisplayName("should acquire immediately when not locked")
        void shouldAcquireImmediatelyWhenNotLocked() {
            // Given
            String workflowId = "workflow-123";
            String ownerId = "owner-1";

            // When
            Boolean acquired = lockService.acquireWithWait(
                    workflowId, ownerId, Duration.ofMinutes(5), Duration.ofSeconds(5)
            ).block();

            // Then
            assertTrue(acquired);
        }

        @Test
        @DisplayName("should wait and acquire after lock is released")
        void shouldWaitAndAcquireAfterLockIsReleased() throws InterruptedException, ExecutionException, TimeoutException {
            // Given
            String workflowId = "workflow-123";
            String owner1 = "owner-1";
            String owner2 = "owner-2";
            lockService.tryAcquire(workflowId, owner1, Duration.ofMinutes(5)).block();

            // When - start waiting for lock in background
            CompletableFuture<Boolean> acquireFuture = CompletableFuture.supplyAsync(() ->
                    lockService.acquireWithWait(workflowId, owner2, Duration.ofMinutes(5), Duration.ofSeconds(5)).block()
            );

            // Release after short delay
            Thread.sleep(100);
            lockService.release(workflowId, owner1).block();

            // Then
            Boolean acquired = acquireFuture.get(5, TimeUnit.SECONDS);
            assertTrue(acquired);
            assertTrue(lockService.isLockedBy(workflowId, owner2).block());
        }

        @Test
        @DisplayName("should timeout if lock not released")
        void shouldTimeoutIfLockNotReleased() {
            // Given
            String workflowId = "workflow-123";
            String owner1 = "owner-1";
            String owner2 = "owner-2";
            lockService.tryAcquire(workflowId, owner1, Duration.ofMinutes(5)).block();

            // When - try to acquire with short timeout
            Boolean acquired = lockService.acquireWithWait(
                    workflowId, owner2, Duration.ofMinutes(5), Duration.ofMillis(200)
            ).block();

            // Then
            assertFalse(acquired);
            // Original lock should still be held
            assertTrue(lockService.isLockedBy(workflowId, owner1).block());
        }
    }

    // ========================================================================
    // STATISTICS TESTS
    // ========================================================================

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("should track lock statistics")
        void shouldTrackLockStatistics() {
            // Given
            lockService.reset();

            // When
            lockService.tryAcquire("wf-1", "owner-1", Duration.ofMinutes(5)).block();
            lockService.tryAcquire("wf-2", "owner-2", Duration.ofMinutes(5)).block();
            lockService.release("wf-1", "owner-1").block();

            // Then
            InMemoryWorkflowLockService.LockStatistics stats = lockService.getStatistics();
            assertEquals(1, stats.totalLocks());
            assertEquals(1, stats.activeLocks());
            assertEquals(2, stats.acquiredCount());
            assertEquals(1, stats.releasedCount());
        }

        @Test
        @DisplayName("should count active locks")
        void shouldCountActiveLocks() {
            // Given
            lockService.reset();

            // When
            lockService.tryAcquire("wf-1", "owner-1", Duration.ofMinutes(5)).block();
            lockService.tryAcquire("wf-2", "owner-2", Duration.ofMinutes(5)).block();
            lockService.tryAcquire("wf-3", "owner-3", Duration.ofMinutes(5)).block();

            // Then
            Long count = lockService.getActiveLockCount().block();
            assertEquals(3, count);
        }
    }

    // ========================================================================
    // CLEANUP TESTS
    // ========================================================================

    @Nested
    @DisplayName("Expired Lock Cleanup")
    class CleanupTests {

        @Test
        @DisplayName("should cleanup expired locks")
        void shouldCleanupExpiredLocks() throws InterruptedException {
            // Given
            lockService.reset();
            lockService.tryAcquire("wf-1", "owner-1", Duration.ofMillis(50)).block();
            lockService.tryAcquire("wf-2", "owner-2", Duration.ofMillis(50)).block();
            lockService.tryAcquire("wf-3", "owner-3", Duration.ofMinutes(5)).block();

            // Wait for expiration
            Thread.sleep(100);

            // When
            Long cleanedUp = lockService.cleanupExpiredLocks().block();

            // Then
            assertEquals(2, cleanedUp);
            assertFalse(lockService.isLocked("wf-1").block());
            assertFalse(lockService.isLocked("wf-2").block());
            assertTrue(lockService.isLocked("wf-3").block());
        }
    }

    // ========================================================================
    // EXECUTE WITH LOCK TESTS
    // ========================================================================

    @Nested
    @DisplayName("Execute With Lock")
    class ExecuteWithLockTests {

        @Test
        @DisplayName("should execute action with lock and release after")
        void shouldExecuteActionWithLockAndReleaseAfter() {
            // Given
            String workflowId = "workflow-123";
            String ownerId = "owner-1";
            AtomicBoolean executed = new AtomicBoolean(false);

            // When
            String result = lockService.executeWithLock(
                    workflowId, ownerId, Duration.ofMinutes(5),
                    Mono.fromCallable(() -> {
                        executed.set(true);
                        // Verify lock is held during execution
                        assertTrue(lockService.isLockedBy(workflowId, ownerId).block());
                        return "success";
                    })
            ).block();

            // Then
            assertTrue(executed.get());
            assertEquals("success", result);
            // Lock should be released after execution
            assertFalse(lockService.isLocked(workflowId).block());
        }

        @Test
        @DisplayName("should release lock even on error")
        void shouldReleaseLockEvenOnError() {
            // Given
            String workflowId = "workflow-123";
            String ownerId = "owner-1";

            // When
            StepVerifier.create(
                    lockService.executeWithLock(
                            workflowId, ownerId, Duration.ofMinutes(5),
                            Mono.error(new RuntimeException("Test error"))
                    )
            )
            .expectError(RuntimeException.class)
            .verify();

            // Then - lock should be released
            assertFalse(lockService.isLocked(workflowId).block());
        }
    }
}
