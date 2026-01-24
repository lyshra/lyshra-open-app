package com.lyshra.open.app.core.engine.lock;

import com.lyshra.open.app.core.engine.lock.impl.InMemoryWorkflowLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for workflow resumption locking mechanism.
 * Tests that concurrent resume operations are properly prevented.
 */
class WorkflowResumptionLockingTest {

    private InMemoryWorkflowLockService lockService;

    @BeforeEach
    void setUp() {
        lockService = new InMemoryWorkflowLockService(
                Duration.ofMinutes(5),
                Duration.ofSeconds(60),
                10,
                Duration.ofMillis(50)
        );
        lockService.reset();
    }

    // ========================================================================
    // CONCURRENT RESUME PREVENTION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Concurrent Resume Prevention")
    class ConcurrentResumePrevention {

        @Test
        @DisplayName("should prevent concurrent resume operations on same workflow")
        void shouldPreventConcurrentResumeOnSameWorkflow() throws InterruptedException, ExecutionException {
            // Given
            String workflowId = "workflow-123";
            int numConcurrentResumes = 5;
            ExecutorService executor = Executors.newFixedThreadPool(numConcurrentResumes);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numConcurrentResumes);
            AtomicInteger successfulResumes = new AtomicInteger(0);
            AtomicInteger blockedResumes = new AtomicInteger(0);

            // When - simulate multiple concurrent resume attempts
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < numConcurrentResumes; i++) {
                final int resumerId = i;
                futures.add(executor.submit(() -> {
                    try {
                        startLatch.await(); // Wait for all threads to be ready

                        String lockOwnerId = "resumer-" + resumerId;
                        Boolean acquired = lockService.tryAcquire(workflowId, lockOwnerId, Duration.ofMinutes(5)).block();

                        if (acquired) {
                            successfulResumes.incrementAndGet();
                            // Simulate resume operation taking some time
                            Thread.sleep(100);
                            lockService.release(workflowId, lockOwnerId).block();
                        } else {
                            blockedResumes.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                }));
            }

            // Start all threads simultaneously
            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Then - only one resume should succeed at a time
            assertEquals(1, successfulResumes.get(),
                    "Only one concurrent resume should succeed");
            assertEquals(numConcurrentResumes - 1, blockedResumes.get(),
                    "Other resumes should be blocked");
        }

        @Test
        @DisplayName("should allow sequential resume operations after lock release")
        void shouldAllowSequentialResumeAfterLockRelease() {
            // Given
            String workflowId = "workflow-123";
            String resumer1 = "resumer-1";
            String resumer2 = "resumer-2";

            // When - first resume acquires and releases
            Boolean acquired1 = lockService.tryAcquire(workflowId, resumer1, Duration.ofMinutes(5)).block();
            assertTrue(acquired1);
            lockService.release(workflowId, resumer1).block();

            // Then - second resume should succeed
            Boolean acquired2 = lockService.tryAcquire(workflowId, resumer2, Duration.ofMinutes(5)).block();
            assertTrue(acquired2);
        }

        @Test
        @DisplayName("should handle multiple workflows concurrently")
        void shouldHandleMultipleWorkflowsConcurrently() throws InterruptedException {
            // Given
            int numWorkflows = 10;
            ExecutorService executor = Executors.newFixedThreadPool(numWorkflows);
            CountDownLatch latch = new CountDownLatch(numWorkflows);
            AtomicInteger successfulLocks = new AtomicInteger(0);

            // When - different workflows can be resumed concurrently
            for (int i = 0; i < numWorkflows; i++) {
                final String workflowId = "workflow-" + i;
                executor.submit(() -> {
                    try {
                        Boolean acquired = lockService.tryAcquire(workflowId, "owner", Duration.ofMinutes(5)).block();
                        if (acquired) {
                            successfulLocks.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Then - all should succeed since they're different workflows
            assertEquals(numWorkflows, successfulLocks.get(),
                    "All workflows should be lockable concurrently");
        }
    }

    // ========================================================================
    // LOCK EXCEPTION TESTS
    // ========================================================================

    @Nested
    @DisplayName("WorkflowLockedException Handling")
    class WorkflowLockedExceptionHandling {

        @Test
        @DisplayName("should create exception with lock info")
        void shouldCreateExceptionWithLockInfo() {
            // Given
            String workflowId = "workflow-123";
            String ownerId = "owner-1";
            lockService.tryAcquireSync(workflowId, ownerId, Duration.ofMinutes(5), "test-operation");

            // When
            Optional<WorkflowLock> lockInfo = lockService.getLockInfo(workflowId).block();
            WorkflowLockedException exception = WorkflowLockedException.withLockInfo(lockInfo.get());

            // Then
            assertNotNull(exception);
            assertEquals(workflowId, exception.getWorkflowInstanceId());
            assertEquals(ownerId, exception.getCurrentOwner());
            assertNotNull(exception.getLockInfo());
            assertTrue(exception.getMessage().contains(workflowId));
            assertTrue(exception.getMessage().contains(ownerId));
        }

        @Test
        @DisplayName("should create timeout exception")
        void shouldCreateTimeoutException() {
            // Given
            String workflowId = "workflow-123";

            // When
            WorkflowLockedException exception = WorkflowLockedException.timeout(workflowId);

            // Then
            assertNotNull(exception);
            assertEquals(workflowId, exception.getWorkflowInstanceId());
            assertTrue(exception.getMessage().contains("Timeout"));
            assertTrue(exception.getMessage().contains(workflowId));
        }

        @Test
        @DisplayName("should create locked by exception")
        void shouldCreateLockedByException() {
            // Given
            String workflowId = "workflow-123";
            String currentOwner = "owner-1";

            // When
            WorkflowLockedException exception = WorkflowLockedException.lockedBy(workflowId, currentOwner);

            // Then
            assertNotNull(exception);
            assertEquals(workflowId, exception.getWorkflowInstanceId());
            assertEquals(currentOwner, exception.getCurrentOwner());
        }
    }

    // ========================================================================
    // LOCK LIFECYCLE TESTS
    // ========================================================================

    @Nested
    @DisplayName("Lock Lifecycle During Resume")
    class LockLifecycleDuringResume {

        @Test
        @DisplayName("should lock during resume and unlock after completion")
        void shouldLockDuringResumeAndUnlockAfterCompletion() {
            // Given
            String workflowId = "workflow-123";
            String ownerId = "resumer-1";

            // When - simulate resume operation
            Boolean acquired = lockService.tryAcquire(workflowId, ownerId, Duration.ofMinutes(5)).block();
            assertTrue(acquired, "Lock should be acquired");
            assertTrue(lockService.isLocked(workflowId).block(), "Workflow should be locked during resume");

            // Simulate resume operation...
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Release lock after resume
            Boolean released = lockService.release(workflowId, ownerId).block();

            // Then
            assertTrue(released, "Lock should be released");
            assertFalse(lockService.isLocked(workflowId).block(), "Workflow should be unlocked after resume");
        }

        @Test
        @DisplayName("should release lock even on error")
        void shouldReleaseLockEvenOnError() {
            // Given
            String workflowId = "workflow-123";
            String ownerId = "resumer-1";

            // When - simulate resume with error
            Boolean acquired = lockService.tryAcquire(workflowId, ownerId, Duration.ofMinutes(5)).block();
            assertTrue(acquired);

            try {
                // Simulate error during resume
                throw new RuntimeException("Simulated error");
            } catch (Exception e) {
                // Error occurred - should still release lock
            } finally {
                lockService.release(workflowId, ownerId).block();
            }

            // Then - lock should be released
            assertFalse(lockService.isLocked(workflowId).block());
        }

        @Test
        @DisplayName("should allow force release of stuck lock")
        void shouldAllowForceReleaseOfStuckLock() {
            // Given
            String workflowId = "workflow-123";
            String ownerId = "stuck-resumer";
            lockService.tryAcquire(workflowId, ownerId, Duration.ofMinutes(5)).block();

            // Verify lock is held
            assertTrue(lockService.isLocked(workflowId).block());

            // When - admin force releases
            Boolean forceReleased = lockService.forceRelease(workflowId, "Admin intervention").block();

            // Then
            assertTrue(forceReleased);
            assertFalse(lockService.isLocked(workflowId).block());

            // And new resumer can proceed
            Boolean newAcquired = lockService.tryAcquire(workflowId, "new-resumer", Duration.ofMinutes(5)).block();
            assertTrue(newAcquired);
        }
    }

    // ========================================================================
    // LOCK EXPIRATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Lock Expiration")
    class LockExpiration {

        @Test
        @DisplayName("should allow resume after lock expires")
        void shouldAllowResumeAfterLockExpires() throws InterruptedException {
            // Given - a lock that expires quickly
            String workflowId = "workflow-123";
            String resumer1 = "resumer-1";
            String resumer2 = "resumer-2";

            lockService.tryAcquire(workflowId, resumer1, Duration.ofMillis(50)).block();

            // When - wait for expiration
            Thread.sleep(100);

            // Then - second resumer should be able to acquire
            Boolean acquired = lockService.tryAcquire(workflowId, resumer2, Duration.ofMinutes(5)).block();
            assertTrue(acquired);
            assertTrue(lockService.isLockedBy(workflowId, resumer2).block());
        }

        @Test
        @DisplayName("should extend lock for long-running resume")
        void shouldExtendLockForLongRunningResume() throws InterruptedException {
            // Given
            String workflowId = "workflow-123";
            String ownerId = "long-resumer";
            lockService.tryAcquire(workflowId, ownerId, Duration.ofMillis(100)).block();

            // When - extend before expiration
            Thread.sleep(50);
            Boolean extended = lockService.extend(workflowId, ownerId, Duration.ofMinutes(5)).block();

            // Then
            assertTrue(extended);

            // Wait past original expiration
            Thread.sleep(100);

            // Lock should still be valid
            assertTrue(lockService.isLockedBy(workflowId, ownerId).block());

            Optional<WorkflowLock> lockInfo = lockService.getLockInfo(workflowId).block();
            assertTrue(lockInfo.isPresent());
            assertEquals(1, lockInfo.get().getExtensionCount());
        }
    }

    // ========================================================================
    // LOCK INFORMATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Lock Information")
    class LockInformation {

        @Test
        @DisplayName("should provide detailed lock information")
        void shouldProvideDetailedLockInformation() {
            // Given
            String workflowId = "workflow-123";
            String ownerId = "resumer-1";

            // When
            lockService.tryAcquireSync(workflowId, ownerId, Duration.ofMinutes(5), "resume-human-task");

            // Then
            Optional<WorkflowLock> lockInfo = lockService.getLockInfo(workflowId).block();
            assertTrue(lockInfo.isPresent());

            WorkflowLock lock = lockInfo.get();
            assertEquals(workflowId, lock.getWorkflowInstanceId());
            assertEquals(ownerId, lock.getOwnerId());
            assertEquals("resume-human-task", lock.getOperation());
            assertNotNull(lock.getAcquiredAt());
            assertNotNull(lock.getExpiresAt());
            assertTrue(lock.getAcquiredAt().isBefore(lock.getExpiresAt()));
            assertTrue(lock.isValid());
            assertFalse(lock.isExpired());
            assertTrue(lock.getRemainingTime().toMinutes() > 0);
        }

        @Test
        @DisplayName("should track lock statistics across operations")
        void shouldTrackLockStatisticsAcrossOperations() {
            // Given - reset statistics
            lockService.reset();

            // When - perform various lock operations
            lockService.tryAcquire("wf-1", "owner-1", Duration.ofMinutes(5)).block();
            lockService.tryAcquire("wf-2", "owner-2", Duration.ofMinutes(5)).block();
            lockService.tryAcquire("wf-3", "owner-3", Duration.ofMinutes(5)).block();
            lockService.release("wf-1", "owner-1").block();
            lockService.release("wf-2", "owner-2").block();

            // Then
            InMemoryWorkflowLockService.LockStatistics stats = lockService.getStatistics();
            assertEquals(3, stats.acquiredCount());
            assertEquals(2, stats.releasedCount());
            assertEquals(1, stats.activeLocks());
        }
    }

    // ========================================================================
    // EDGE CASE TESTS
    // ========================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle null workflow ID gracefully")
        void shouldHandleNullWorkflowIdGracefully() {
            assertThrows(IllegalArgumentException.class, () ->
                    lockService.tryAcquire(null, "owner", Duration.ofMinutes(5)).block());
        }

        @Test
        @DisplayName("should handle null owner ID gracefully")
        void shouldHandleNullOwnerIdGracefully() {
            assertThrows(IllegalArgumentException.class, () ->
                    lockService.tryAcquire("workflow-123", null, Duration.ofMinutes(5)).block());
        }

        @Test
        @DisplayName("should handle release of non-existent lock")
        void shouldHandleReleaseOfNonExistentLock() {
            Boolean released = lockService.release("non-existent", "owner").block();
            assertFalse(released);
        }

        @Test
        @DisplayName("should handle double release")
        void shouldHandleDoubleRelease() {
            // Given
            String workflowId = "workflow-123";
            String ownerId = "owner-1";
            lockService.tryAcquire(workflowId, ownerId, Duration.ofMinutes(5)).block();

            // When
            Boolean firstRelease = lockService.release(workflowId, ownerId).block();
            Boolean secondRelease = lockService.release(workflowId, ownerId).block();

            // Then
            assertTrue(firstRelease);
            assertFalse(secondRelease);
        }

        @Test
        @DisplayName("should handle same owner reacquiring lock")
        void shouldHandleSameOwnerReacquiringLock() {
            // Given
            String workflowId = "workflow-123";
            String ownerId = "owner-1";

            // When - same owner acquires multiple times (refresh)
            Boolean first = lockService.tryAcquire(workflowId, ownerId, Duration.ofMinutes(5)).block();
            Boolean second = lockService.tryAcquire(workflowId, ownerId, Duration.ofMinutes(10)).block();

            // Then - both should succeed (refresh behavior)
            assertTrue(first);
            assertTrue(second);
            assertTrue(lockService.isLockedBy(workflowId, ownerId).block());
        }
    }

}
