package com.lyshra.open.app.distributed.coordination.testing;

import com.lyshra.open.app.distributed.coordination.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Self-contained in-memory implementation of IWorkflowOwnershipCoordinator for testing.
 *
 * This implementation provides a fully functional coordinator without any external
 * dependencies, making it ideal for:
 * - Unit testing coordination logic
 * - Integration testing without infrastructure
 * - Local development and debugging
 * - Validating ownership behavior before deploying
 *
 * Key Testing Features:
 * 1. Configurable behavior - Inject failures, delays, and custom responses
 * 2. Time simulation - Control time progression for lease expiration testing
 * 3. Event capture - Record and verify all coordination events
 * 4. State inspection - Examine internal state for assertions
 * 5. Multi-node simulation - Test distributed scenarios locally
 *
 * Usage:
 * <pre>
 * TestableOwnershipCoordinator coordinator = TestableOwnershipCoordinator.builder()
 *     .nodeId("test-node-1")
 *     .totalPartitions(16)
 *     .defaultLeaseDuration(Duration.ofMinutes(5))
 *     .build();
 *
 * coordinator.initialize().block();
 *
 * CoordinationResult result = coordinator.acquireOwnership("workflow-1").block();
 * assertTrue(result.isAcquired());
 *
 * // Simulate time passing
 * coordinator.advanceTime(Duration.ofMinutes(6));
 * assertTrue(coordinator.isOrphaned("workflow-1").block());
 * </pre>
 *
 * Thread Safety: This class is thread-safe for concurrent testing scenarios.
 */
@Slf4j
public class TestableOwnershipCoordinator implements IWorkflowOwnershipCoordinator {

    // Configuration
    @Getter private final String nodeId;
    private final int totalPartitions;
    private final Duration defaultLeaseDuration;
    private final Set<Integer> assignedPartitions;

    // State
    private final ConcurrentHashMap<String, LeaseRecord> leases;
    private final AtomicLong fencingTokenGenerator;
    private final AtomicBoolean active;
    private final CoordinationMetrics metrics;
    private Instant startedAt;

    // Simulated time (null = use real time)
    private volatile Instant simulatedTime;

    // Event capture
    private final List<CoordinationEvent> capturedEvents;
    private final List<ICoordinationEventListener> listeners;

    // Failure injection
    private volatile Predicate<String> acquisitionFailurePredicate;
    private volatile Predicate<String> renewalFailurePredicate;
    private volatile Duration artificialDelay;
    private volatile boolean simulateBackendFailure;

    // Lock simulation
    private final ConcurrentHashMap<String, String> locks;
    private final Duration lockTimeout;

    /**
     * Internal lease record for tracking ownership.
     */
    @Getter
    public static class LeaseRecord {
        private final String workflowKey;
        private final String ownerId;
        private final int partitionId;
        private final Instant acquiredAt;
        private volatile Instant renewedAt;
        private volatile Instant expiresAt;
        private final Duration leaseDuration;
        private final long fencingToken;
        private final long ownershipEpoch;
        private volatile long version;
        private volatile int renewalCount;
        private volatile boolean released;
        private volatile String releaseReason;

        public LeaseRecord(String workflowKey, String ownerId, int partitionId,
                          Duration leaseDuration, long fencingToken, long epoch, Instant now) {
            this.workflowKey = workflowKey;
            this.ownerId = ownerId;
            this.partitionId = partitionId;
            this.acquiredAt = now;
            this.renewedAt = now;
            this.leaseDuration = leaseDuration;
            this.expiresAt = now.plus(leaseDuration);
            this.fencingToken = fencingToken;
            this.ownershipEpoch = epoch;
            this.version = 1;
            this.renewalCount = 0;
            this.released = false;
        }

        public boolean isValid(Instant currentTime) {
            return !released && currentTime.isBefore(expiresAt);
        }

        public boolean isExpired(Instant currentTime) {
            return currentTime.isAfter(expiresAt) || released;
        }

        public void renew(Duration extension, Instant now) {
            this.renewedAt = now;
            this.expiresAt = now.plus(extension);
            this.version++;
            this.renewalCount++;
        }

        public void release(String reason) {
            this.released = true;
            this.releaseReason = reason;
        }

        public OwnershipContext toContext() {
            return OwnershipContext.builder()
                    .workflowExecutionKey(workflowKey)
                    .leaseId("test-lease-" + workflowKey)
                    .ownerId(ownerId)
                    .partitionId(partitionId)
                    .acquiredAt(acquiredAt)
                    .renewedAt(renewedAt)
                    .expiresAt(expiresAt)
                    .leaseDuration(leaseDuration)
                    .fencingToken(fencingToken)
                    .ownershipEpoch(ownershipEpoch)
                    .version(version)
                    .renewalCount(renewalCount)
                    .build();
        }
    }

    private TestableOwnershipCoordinator(Builder builder) {
        this.nodeId = builder.nodeId;
        this.totalPartitions = builder.totalPartitions;
        this.defaultLeaseDuration = builder.defaultLeaseDuration;
        this.assignedPartitions = new HashSet<>(builder.assignedPartitions);
        this.lockTimeout = builder.lockTimeout;

        this.leases = new ConcurrentHashMap<>();
        this.fencingTokenGenerator = new AtomicLong(System.currentTimeMillis());
        this.active = new AtomicBoolean(false);
        this.metrics = new CoordinationMetrics();

        this.capturedEvents = new CopyOnWriteArrayList<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.locks = new ConcurrentHashMap<>();

        this.simulatedTime = null;
        this.artificialDelay = Duration.ZERO;
        this.simulateBackendFailure = false;
    }

    public static Builder builder() {
        return new Builder();
    }

    // ========== Lifecycle Methods ==========

    @Override
    public Mono<Void> initialize() {
        return Mono.fromRunnable(() -> {
            if (active.compareAndSet(false, true)) {
                startedAt = now();
                log.info("[TEST] Coordinator initialized: nodeId={}, partitions={}",
                        nodeId, assignedPartitions);
                emitEvent(CoordinationEvent.coordinatorActive(nodeId));
            }
        });
    }

    @Override
    public Mono<Void> shutdown() {
        return Mono.fromRunnable(() -> {
            if (active.compareAndSet(true, false)) {
                // Release all owned leases
                leases.values().stream()
                        .filter(lease -> lease.getOwnerId().equals(nodeId))
                        .forEach(lease -> lease.release("shutdown"));

                log.info("[TEST] Coordinator shutdown: nodeId={}", nodeId);
                emitEvent(CoordinationEvent.coordinatorInactive(nodeId, "shutdown"));
            }
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
        Objects.requireNonNull(workflowExecutionKey);

        return Mono.defer(() -> {
            // Apply artificial delay if configured
            if (!artificialDelay.isZero()) {
                try {
                    Thread.sleep(artificialDelay.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Check if coordinator is active
            if (!active.get()) {
                return Mono.just(CoordinationResult.coordinatorInactive(workflowExecutionKey));
            }

            // Check for simulated backend failure
            if (simulateBackendFailure) {
                return Mono.just(CoordinationResult.backendUnavailable(workflowExecutionKey,
                        new RuntimeException("Simulated backend failure")));
            }

            // Check for injected failure
            if (acquisitionFailurePredicate != null && acquisitionFailurePredicate.test(workflowExecutionKey)) {
                metrics.recordAcquisitionFailure();
                return Mono.just(CoordinationResult.error(workflowExecutionKey,
                        "Injected acquisition failure", null));
            }

            metrics.recordAcquisitionAttempt();
            Instant currentTime = now();
            int partitionId = computePartitionId(workflowExecutionKey);

            // Check partition assignment
            if (!options.isSkipPartitionCheck() && !assignedPartitions.contains(partitionId)) {
                return Mono.just(CoordinationResult.wrongPartition(workflowExecutionKey, partitionId,
                        "partition-" + partitionId + "-owner"));
            }

            // Try to acquire lock
            if (!tryAcquireLock(workflowExecutionKey)) {
                metrics.recordLockTimeout();
                return Mono.just(CoordinationResult.timeout(workflowExecutionKey,
                        "Failed to acquire lock"));
            }

            try {
                return Mono.just(doAcquire(workflowExecutionKey, partitionId, options, currentTime));
            } finally {
                releaseLock(workflowExecutionKey);
            }
        });
    }

    private CoordinationResult doAcquire(String workflowKey, int partitionId,
                                          AcquisitionOptions options, Instant currentTime) {
        LeaseRecord existing = leases.get(workflowKey);

        // Check if already owned
        if (existing != null && existing.isValid(currentTime)) {
            if (existing.getOwnerId().equals(nodeId)) {
                return CoordinationResult.alreadyOwnedBySelf(workflowKey, nodeId,
                        existing.getFencingToken(), existing.getExpiresAt());
            } else {
                metrics.recordAcquisitionFailure();
                return CoordinationResult.alreadyOwnedByOther(workflowKey,
                        existing.getOwnerId(), existing.getExpiresAt());
            }
        }

        // Create new lease
        Duration leaseDuration = options.getLeaseDuration() != null
                ? options.getLeaseDuration() : defaultLeaseDuration;
        long fencingToken = fencingTokenGenerator.incrementAndGet();
        long epoch = existing != null ? existing.getOwnershipEpoch() + 1 : 1;

        LeaseRecord newLease = new LeaseRecord(workflowKey, nodeId, partitionId,
                leaseDuration, fencingToken, epoch, currentTime);
        leases.put(workflowKey, newLease);

        metrics.recordAcquisitionSuccess(Duration.ZERO);

        log.debug("[TEST] Acquired ownership: workflow={}, node={}, fencingToken={}, expires={}",
                workflowKey, nodeId, fencingToken, newLease.getExpiresAt());

        CoordinationEvent event = CoordinationEvent.ownershipAcquired(workflowKey, nodeId,
                partitionId, fencingToken, epoch);
        emitEvent(event);

        return CoordinationResult.acquired(workflowKey, nodeId, partitionId, fencingToken, epoch,
                newLease.getVersion(), newLease.getAcquiredAt(), newLease.getExpiresAt());
    }

    @Override
    public Mono<BatchCoordinationResult> acquireOwnershipBatch(Set<String> workflowExecutionKeys,
                                                                AcquisitionOptions options) {
        if (workflowExecutionKeys == null || workflowExecutionKeys.isEmpty()) {
            return Mono.just(BatchCoordinationResult.empty());
        }

        Instant startTime = now();
        Map<String, CoordinationResult> results = new HashMap<>();

        for (String key : workflowExecutionKeys) {
            CoordinationResult result = acquireOwnership(key, options).block();
            results.put(key, result);
        }

        return Mono.just(BatchCoordinationResult.from(results, startTime, false));
    }

    // ========== Lease Renewal ==========

    @Override
    public Mono<Boolean> renewOwnership(String workflowExecutionKey) {
        return renewOwnership(workflowExecutionKey, defaultLeaseDuration);
    }

    @Override
    public Mono<Boolean> renewOwnership(String workflowExecutionKey, Duration extensionDuration) {
        Objects.requireNonNull(workflowExecutionKey);

        return Mono.defer(() -> {
            if (!active.get()) {
                return Mono.just(false);
            }

            // Check for injected failure
            if (renewalFailurePredicate != null && renewalFailurePredicate.test(workflowExecutionKey)) {
                metrics.recordRenewalFailure();
                emitEvent(CoordinationEvent.renewalFailed(workflowExecutionKey, nodeId,
                        "Injected renewal failure"));
                return Mono.just(false);
            }

            metrics.recordRenewalAttempt();
            Instant currentTime = now();
            LeaseRecord lease = leases.get(workflowExecutionKey);

            if (lease == null || !lease.getOwnerId().equals(nodeId)) {
                log.warn("[TEST] Cannot renew: workflow {} not owned by {}", workflowExecutionKey, nodeId);
                metrics.recordRenewalFailure();
                return Mono.just(false);
            }

            if (!lease.isValid(currentTime)) {
                log.warn("[TEST] Cannot renew: lease for {} has expired", workflowExecutionKey);
                metrics.recordRenewalFailure();
                emitEvent(CoordinationEvent.ownershipLost(workflowExecutionKey, nodeId, "Lease expired"));
                return Mono.just(false);
            }

            lease.renew(extensionDuration, currentTime);
            metrics.recordRenewalSuccess(Duration.ZERO);

            log.debug("[TEST] Renewed ownership: workflow={}, newExpires={}",
                    workflowExecutionKey, lease.getExpiresAt());

            emitEvent(CoordinationEvent.ownershipRenewed(workflowExecutionKey, nodeId,
                    lease.getFencingToken()));
            return Mono.just(true);
        });
    }

    @Override
    public Mono<Integer> renewAllOwnedWorkflows() {
        if (!active.get()) {
            return Mono.just(0);
        }

        Instant currentTime = now();
        int renewed = 0;

        for (LeaseRecord lease : leases.values()) {
            if (lease.getOwnerId().equals(nodeId) && lease.isValid(currentTime)) {
                Boolean success = renewOwnership(lease.getWorkflowKey()).block();
                if (Boolean.TRUE.equals(success)) {
                    renewed++;
                }
            }
        }

        return Mono.just(renewed);
    }

    // ========== Ownership Release ==========

    @Override
    public Mono<Void> releaseOwnership(String workflowExecutionKey) {
        return releaseOwnership(workflowExecutionKey, "explicit_release");
    }

    @Override
    public Mono<Void> releaseOwnership(String workflowExecutionKey, String reason) {
        Objects.requireNonNull(workflowExecutionKey);

        return Mono.fromRunnable(() -> {
            LeaseRecord lease = leases.get(workflowExecutionKey);
            if (lease != null && lease.getOwnerId().equals(nodeId)) {
                lease.release(reason);
                metrics.recordGracefulRelease();

                log.debug("[TEST] Released ownership: workflow={}, reason={}", workflowExecutionKey, reason);
                emitEvent(CoordinationEvent.ownershipReleased(workflowExecutionKey, nodeId, reason));
            }
        });
    }

    @Override
    public Mono<Integer> releaseAllOwnedWorkflows(String reason) {
        return Mono.fromCallable(() -> {
            int released = 0;
            for (LeaseRecord lease : leases.values()) {
                if (lease.getOwnerId().equals(nodeId) && !lease.isReleased()) {
                    lease.release(reason);
                    released++;
                    emitEvent(CoordinationEvent.ownershipReleased(lease.getWorkflowKey(), nodeId, reason));
                }
            }
            return released;
        });
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

        return Mono.fromCallable(() -> {
            if (!active.get()) {
                return OrphanRecoveryResult.failure(failedNodeId, nodeId,
                        "Coordinator not active", null);
            }

            metrics.recordOrphanRecoveryAttempt();
            Instant currentTime = now();
            Instant startTime = currentTime;
            Set<String> claimed = new HashSet<>();
            int orphansFound = 0;

            for (LeaseRecord lease : leases.values()) {
                if (failedNodeId.equals(lease.getOwnerId())) {
                    orphansFound++;

                    // Check partition assignment
                    if (options.isRespectPartitionAssignment() &&
                        !assignedPartitions.contains(lease.getPartitionId())) {
                        continue;
                    }

                    // Claim limit
                    if (claimed.size() >= options.getMaxClaimsPerOperation()) {
                        return OrphanRecoveryResult.limitReached(failedNodeId, nodeId, claimed,
                                orphansFound, Duration.between(startTime, currentTime));
                    }

                    // Transfer ownership
                    long newFencingToken = fencingTokenGenerator.incrementAndGet();
                    long newEpoch = lease.getOwnershipEpoch() + 1;

                    LeaseRecord newLease = new LeaseRecord(lease.getWorkflowKey(), nodeId,
                            lease.getPartitionId(), defaultLeaseDuration, newFencingToken, newEpoch, currentTime);
                    leases.put(lease.getWorkflowKey(), newLease);
                    claimed.add(lease.getWorkflowKey());

                    emitEvent(CoordinationEvent.orphanRecovered(lease.getWorkflowKey(), nodeId,
                            failedNodeId, newFencingToken, newEpoch));
                }
            }

            Duration duration = Duration.between(startTime, now());
            metrics.recordOrphansRecovered(claimed.size(), duration);

            if (claimed.isEmpty()) {
                return OrphanRecoveryResult.noOrphans(failedNodeId, nodeId);
            }

            log.info("[TEST] Recovered {} orphans from node {}", claimed.size(), failedNodeId);
            return OrphanRecoveryResult.success(failedNodeId, nodeId, claimed, orphansFound, duration);
        });
    }

    @Override
    public Mono<OrphanRecoveryResult> claimAllOrphanedWorkflows() {
        return Mono.fromCallable(() -> {
            if (!active.get()) {
                return OrphanRecoveryResult.failure(null, nodeId, "Coordinator not active", null);
            }

            Instant currentTime = now();
            Instant startTime = currentTime;
            Set<String> claimed = new HashSet<>();
            int orphansFound = 0;

            for (LeaseRecord lease : leases.values()) {
                if (lease.isExpired(currentTime) && assignedPartitions.contains(lease.getPartitionId())) {
                    orphansFound++;

                    long newFencingToken = fencingTokenGenerator.incrementAndGet();
                    long newEpoch = lease.getOwnershipEpoch() + 1;

                    LeaseRecord newLease = new LeaseRecord(lease.getWorkflowKey(), nodeId,
                            lease.getPartitionId(), defaultLeaseDuration, newFencingToken, newEpoch, currentTime);
                    leases.put(lease.getWorkflowKey(), newLease);
                    claimed.add(lease.getWorkflowKey());

                    emitEvent(CoordinationEvent.orphanRecovered(lease.getWorkflowKey(), nodeId,
                            lease.getOwnerId(), newFencingToken, newEpoch));
                }
            }

            Duration duration = Duration.between(startTime, now());
            return OrphanRecoveryResult.success(null, nodeId, claimed, orphansFound, duration);
        });
    }

    @Override
    public Mono<Boolean> isOrphaned(String workflowExecutionKey) {
        return Mono.fromCallable(() -> {
            LeaseRecord lease = leases.get(workflowExecutionKey);
            if (lease == null) {
                return true; // No lease = orphaned
            }
            return lease.isExpired(now());
        });
    }

    // ========== Ownership Query ==========

    @Override
    public Mono<OwnershipContext> getOwnershipContext(String workflowExecutionKey) {
        return Mono.fromCallable(() -> {
            LeaseRecord lease = leases.get(workflowExecutionKey);
            if (lease == null || !lease.isValid(now())) {
                return OwnershipContext.empty(workflowExecutionKey);
            }
            return lease.toContext();
        });
    }

    @Override
    public boolean isOwnedLocally(String workflowExecutionKey) {
        LeaseRecord lease = leases.get(workflowExecutionKey);
        return lease != null && lease.getOwnerId().equals(nodeId) && lease.isValid(now());
    }

    @Override
    public Set<String> getLocallyOwnedWorkflows() {
        Instant currentTime = now();
        Set<String> owned = new HashSet<>();
        for (LeaseRecord lease : leases.values()) {
            if (lease.getOwnerId().equals(nodeId) && lease.isValid(currentTime)) {
                owned.add(lease.getWorkflowKey());
            }
        }
        return Collections.unmodifiableSet(owned);
    }

    @Override
    public int getLocalOwnershipCount() {
        return getLocallyOwnedWorkflows().size();
    }

    @Override
    public Mono<String> getOwner(String workflowExecutionKey) {
        return Mono.fromCallable(() -> {
            LeaseRecord lease = leases.get(workflowExecutionKey);
            if (lease != null && lease.isValid(now())) {
                return lease.getOwnerId();
            }
            return null;
        });
    }

    // ========== Fencing Token Operations ==========

    @Override
    public Mono<Long> getFencingToken(String workflowExecutionKey) {
        return Mono.fromCallable(() -> {
            LeaseRecord lease = leases.get(workflowExecutionKey);
            return lease != null ? lease.getFencingToken() : -1L;
        });
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
        capturedEvents.add(event);
        for (ICoordinationEventListener listener : listeners) {
            try {
                listener.onCoordinationEvent(event);
            } catch (Exception e) {
                log.error("[TEST] Error notifying listener", e);
            }
        }
    }

    // ========== Health & Metrics ==========

    @Override
    public Mono<CoordinatorHealth> getHealth() {
        if (!active.get()) {
            return Mono.just(CoordinatorHealth.down("Coordinator not active"));
        }
        if (simulateBackendFailure) {
            return Mono.just(CoordinatorHealth.unhealthy("Simulated backend failure", startedAt, "Backend unavailable"));
        }
        return Mono.just(CoordinatorHealth.healthy(nodeId, startedAt, getLocalOwnershipCount(), Duration.ofMillis(1)));
    }

    @Override
    public CoordinationMetrics getMetrics() {
        return metrics;
    }

    @Override
    public String getNodeId() {
        return nodeId;
    }

    // ========== Testing Utilities ==========

    /**
     * Gets the current time (real or simulated).
     */
    public Instant now() {
        return simulatedTime != null ? simulatedTime : Instant.now();
    }

    /**
     * Sets simulated time for testing lease expiration.
     */
    public void setSimulatedTime(Instant time) {
        this.simulatedTime = time;
        log.debug("[TEST] Simulated time set to: {}", time);
    }

    /**
     * Advances simulated time by the specified duration.
     */
    public void advanceTime(Duration duration) {
        if (simulatedTime == null) {
            simulatedTime = Instant.now();
        }
        simulatedTime = simulatedTime.plus(duration);
        log.debug("[TEST] Simulated time advanced by {} to {}", duration, simulatedTime);
    }

    /**
     * Resets to real time.
     */
    public void useRealTime() {
        this.simulatedTime = null;
    }

    /**
     * Injects acquisition failures for specific workflows.
     */
    public void injectAcquisitionFailure(Predicate<String> predicate) {
        this.acquisitionFailurePredicate = predicate;
    }

    /**
     * Injects renewal failures for specific workflows.
     */
    public void injectRenewalFailure(Predicate<String> predicate) {
        this.renewalFailurePredicate = predicate;
    }

    /**
     * Clears all injected failures.
     */
    public void clearInjectedFailures() {
        this.acquisitionFailurePredicate = null;
        this.renewalFailurePredicate = null;
    }

    /**
     * Sets artificial delay for operations.
     */
    public void setArtificialDelay(Duration delay) {
        this.artificialDelay = delay != null ? delay : Duration.ZERO;
    }

    /**
     * Simulates backend failure.
     */
    public void simulateBackendFailure(boolean failure) {
        this.simulateBackendFailure = failure;
    }

    /**
     * Gets all captured coordination events.
     */
    public List<CoordinationEvent> getCapturedEvents() {
        return Collections.unmodifiableList(new ArrayList<>(capturedEvents));
    }

    /**
     * Gets captured events of a specific type.
     */
    public List<CoordinationEvent> getCapturedEvents(CoordinationEvent.EventType type) {
        return capturedEvents.stream()
                .filter(e -> e.getType() == type)
                .toList();
    }

    /**
     * Clears captured events.
     */
    public void clearCapturedEvents() {
        capturedEvents.clear();
    }

    /**
     * Gets the internal lease record for inspection.
     */
    public Optional<LeaseRecord> getLeaseRecord(String workflowKey) {
        return Optional.ofNullable(leases.get(workflowKey));
    }

    /**
     * Gets all lease records.
     */
    public Map<String, LeaseRecord> getAllLeaseRecords() {
        return Collections.unmodifiableMap(new HashMap<>(leases));
    }

    /**
     * Forces a lease to expire (for testing).
     */
    public void forceExpireLease(String workflowKey) {
        LeaseRecord lease = leases.get(workflowKey);
        if (lease != null) {
            lease.release("force_expired_for_testing");
            log.debug("[TEST] Force expired lease for: {}", workflowKey);
        }
    }

    /**
     * Simulates node failure by releasing all leases and deactivating.
     */
    public void simulateNodeFailure() {
        for (LeaseRecord lease : leases.values()) {
            if (lease.getOwnerId().equals(nodeId)) {
                // Don't mark as released - simulate abrupt failure
                lease.release("node_failure");
            }
        }
        active.set(false);
        emitEvent(CoordinationEvent.coordinatorInactive(nodeId, "Simulated node failure"));
        log.info("[TEST] Simulated node failure for: {}", nodeId);
    }

    /**
     * Resets the coordinator state for a fresh test.
     */
    public void reset() {
        leases.clear();
        capturedEvents.clear();
        locks.clear();
        metrics.reset();
        clearInjectedFailures();
        setArtificialDelay(Duration.ZERO);
        simulateBackendFailure(false);
        useRealTime();
        active.set(false);
        log.info("[TEST] Coordinator reset: {}", nodeId);
    }

    // ========== Private Helpers ==========

    private int computePartitionId(String workflowKey) {
        return Math.abs(workflowKey.hashCode()) % totalPartitions;
    }

    private boolean tryAcquireLock(String key) {
        String lockKey = "lock:" + key;
        return locks.putIfAbsent(lockKey, nodeId) == null || nodeId.equals(locks.get(lockKey));
    }

    private void releaseLock(String key) {
        String lockKey = "lock:" + key;
        locks.remove(lockKey, nodeId);
    }

    // ========== Builder ==========

    public static class Builder {
        private String nodeId = "test-node-" + UUID.randomUUID().toString().substring(0, 8);
        private int totalPartitions = 16;
        private Duration defaultLeaseDuration = Duration.ofMinutes(5);
        private Set<Integer> assignedPartitions = new HashSet<>();
        private Duration lockTimeout = Duration.ofSeconds(10);

        public Builder nodeId(String nodeId) {
            this.nodeId = Objects.requireNonNull(nodeId);
            return this;
        }

        public Builder totalPartitions(int totalPartitions) {
            if (totalPartitions <= 0) {
                throw new IllegalArgumentException("totalPartitions must be positive");
            }
            this.totalPartitions = totalPartitions;
            return this;
        }

        public Builder defaultLeaseDuration(Duration duration) {
            this.defaultLeaseDuration = Objects.requireNonNull(duration);
            return this;
        }

        public Builder assignedPartitions(Set<Integer> partitions) {
            this.assignedPartitions = new HashSet<>(partitions);
            return this;
        }

        public Builder assignAllPartitions() {
            this.assignedPartitions = new HashSet<>();
            for (int i = 0; i < totalPartitions; i++) {
                assignedPartitions.add(i);
            }
            return this;
        }

        public Builder assignPartitionRange(int start, int end) {
            for (int i = start; i < end; i++) {
                assignedPartitions.add(i);
            }
            return this;
        }

        public Builder lockTimeout(Duration timeout) {
            this.lockTimeout = Objects.requireNonNull(timeout);
            return this;
        }

        public TestableOwnershipCoordinator build() {
            // Assign all partitions by default if none specified
            if (assignedPartitions.isEmpty()) {
                assignAllPartitions();
            }
            return new TestableOwnershipCoordinator(this);
        }
    }
}
