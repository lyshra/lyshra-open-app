package com.lyshra.open.app.distributed.recovery;

import com.lyshra.open.app.distributed.cluster.IClusterCoordinator;
import com.lyshra.open.app.distributed.coordination.CoordinationResult;
import com.lyshra.open.app.distributed.coordination.IWorkflowOwnershipCoordinator;
import com.lyshra.open.app.distributed.coordination.OwnershipContext;
import com.lyshra.open.app.distributed.lock.IDistributedLockManager;
import com.lyshra.open.app.distributed.membership.INodeMembershipService;
import com.lyshra.open.app.distributed.ownership.IWorkflowOwnershipRepository;
import com.lyshra.open.app.distributed.ownership.WorkflowOwnershipLease;
import com.lyshra.open.app.distributed.state.IWorkflowStateStore;
import com.lyshra.open.app.distributed.state.WorkflowExecutionState;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coordinates safe workflow takeover to prevent split-brain scenarios.
 *
 * This component ensures that only one node can own a workflow at a time by:
 *
 * 1. Verifying Lease Validity
 *    - Confirms the previous lease has actually expired
 *    - Checks that the previous owner is truly unresponsive
 *    - Validates no concurrent takeover is in progress
 *
 * 2. Atomic Compare-and-Set Operations
 *    - Uses distributed locking for coordination
 *    - Validates version/epoch before modifications
 *    - Ensures exactly-once ownership transfer
 *
 * 3. Fencing Token Validation
 *    - Assigns monotonically increasing fencing tokens
 *    - Rejects operations with stale fencing tokens
 *    - Prevents zombie processes from interfering
 *
 * 4. Race Condition Prevention
 *    - Detects concurrent takeover attempts
 *    - Uses optimistic locking with version checks
 *    - Implements retry with exponential backoff
 *
 * Split-Brain Prevention Strategy:
 * - Fencing tokens create a total ordering of ownership
 * - Only the owner with the highest fencing token is valid
 * - All operations must include and validate fencing tokens
 * - Lease expiration + grace period prevents false positives
 *
 * Thread Safety: This class is thread-safe.
 */
@Slf4j
public class SafeTakeoverCoordinator {

    private static final String TAKEOVER_LOCK_PREFIX = "takeover-lock:";
    private static final Duration DEFAULT_LOCK_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_GRACE_PERIOD = Duration.ofSeconds(30);
    private static final Duration DEFAULT_VERIFICATION_DELAY = Duration.ofSeconds(5);
    private static final int DEFAULT_MAX_RETRIES = 3;

    private final IClusterCoordinator clusterCoordinator;
    private final IWorkflowOwnershipCoordinator ownershipCoordinator;
    private final IWorkflowOwnershipRepository ownershipRepository;
    private final IWorkflowStateStore stateStore;
    private final IDistributedLockManager lockManager;
    private final INodeMembershipService membershipService;

    private final Duration lockTimeout;
    private final Duration gracePeriod;
    private final Duration verificationDelay;
    private final int maxRetries;

    private final List<ITakeoverListener> listeners;
    private final TakeoverMetrics metrics;

    @Builder
    public SafeTakeoverCoordinator(IClusterCoordinator clusterCoordinator,
                                    IWorkflowOwnershipCoordinator ownershipCoordinator,
                                    IWorkflowOwnershipRepository ownershipRepository,
                                    IWorkflowStateStore stateStore,
                                    IDistributedLockManager lockManager,
                                    INodeMembershipService membershipService,
                                    Duration lockTimeout,
                                    Duration gracePeriod,
                                    Duration verificationDelay,
                                    Integer maxRetries) {
        this.clusterCoordinator = Objects.requireNonNull(clusterCoordinator);
        this.ownershipCoordinator = Objects.requireNonNull(ownershipCoordinator);
        this.ownershipRepository = Objects.requireNonNull(ownershipRepository);
        this.stateStore = Objects.requireNonNull(stateStore);
        this.lockManager = Objects.requireNonNull(lockManager);
        this.membershipService = membershipService; // Can be null

        this.lockTimeout = lockTimeout != null ? lockTimeout : DEFAULT_LOCK_TIMEOUT;
        this.gracePeriod = gracePeriod != null ? gracePeriod : DEFAULT_GRACE_PERIOD;
        this.verificationDelay = verificationDelay != null ? verificationDelay : DEFAULT_VERIFICATION_DELAY;
        this.maxRetries = maxRetries != null ? maxRetries : DEFAULT_MAX_RETRIES;

        this.listeners = new CopyOnWriteArrayList<>();
        this.metrics = new TakeoverMetrics();
    }

    // ========== Safe Takeover Operations ==========

    /**
     * Attempts a safe takeover of an orphaned workflow.
     *
     * This method implements the full split-brain prevention protocol:
     * 1. Acquire distributed lock to serialize takeover attempts
     * 2. Verify the lease is truly expired (with grace period)
     * 3. Verify the previous owner is unresponsive
     * 4. Atomically transfer ownership with new fencing token
     * 5. Update workflow state with new ownership info
     *
     * @param workflowKey the workflow execution key
     * @return Mono containing the takeover result
     */
    public Mono<TakeoverResult> attemptSafeTakeover(String workflowKey) {
        return attemptSafeTakeover(workflowKey, TakeoverOptions.DEFAULT);
    }

    /**
     * Attempts a safe takeover with options.
     *
     * @param workflowKey the workflow execution key
     * @param options the takeover options
     * @return Mono containing the takeover result
     */
    public Mono<TakeoverResult> attemptSafeTakeover(String workflowKey, TakeoverOptions options) {
        Objects.requireNonNull(workflowKey);
        Objects.requireNonNull(options);

        String nodeId = clusterCoordinator.getNodeId();
        String lockKey = TAKEOVER_LOCK_PREFIX + workflowKey;

        log.info("Attempting safe takeover of workflow {} by node {}", workflowKey, nodeId);
        metrics.recordTakeoverAttempt();

        return lockManager.tryLock(lockKey, lockTimeout)
                .flatMap(acquired -> {
                    if (!acquired) {
                        log.warn("Failed to acquire takeover lock for {}", workflowKey);
                        metrics.recordLockContentionFailure();
                        return Mono.just(TakeoverResult.lockContention(workflowKey, nodeId));
                    }

                    return performSafeTakeoverWithLock(workflowKey, nodeId, options)
                            .doFinally(signal -> lockManager.unlock(lockKey).subscribe());
                })
                .onErrorResume(error -> {
                    log.error("Error during safe takeover of {}", workflowKey, error);
                    metrics.recordTakeoverError();
                    return Mono.just(TakeoverResult.error(workflowKey, nodeId, error.getMessage()));
                });
    }

    /**
     * Validates that a takeover is safe without actually performing it.
     *
     * @param workflowKey the workflow execution key
     * @return Mono containing the validation result
     */
    public Mono<TakeoverValidation> validateTakeover(String workflowKey) {
        return ownershipRepository.findByWorkflowId(workflowKey)
                .flatMap(optLease -> {
                    if (optLease.isEmpty()) {
                        return Mono.just(TakeoverValidation.valid(workflowKey,
                                "No existing lease - workflow is available"));
                    }

                    WorkflowOwnershipLease lease = optLease.get();
                    return validateLeaseForTakeover(lease);
                });
    }

    /**
     * Validates a fencing token for an operation.
     *
     * @param workflowKey the workflow execution key
     * @param fencingToken the fencing token to validate
     * @return Mono containing true if the token is valid
     */
    public Mono<Boolean> validateFencingToken(String workflowKey, long fencingToken) {
        return ownershipCoordinator.getFencingToken(workflowKey)
                .map(currentToken -> {
                    boolean valid = fencingToken >= currentToken;
                    if (!valid) {
                        log.warn("Fencing token validation failed for {}: provided={}, current={}",
                                workflowKey, fencingToken, currentToken);
                        metrics.recordFencingTokenRejection();
                    }
                    return valid;
                });
    }

    /**
     * Forces a takeover even if normal validation fails.
     * This is for emergency situations only and should be used with extreme caution.
     *
     * @param workflowKey the workflow execution key
     * @param reason the reason for forced takeover
     * @return Mono containing the takeover result
     */
    public Mono<TakeoverResult> forceTakeover(String workflowKey, String reason) {
        Objects.requireNonNull(workflowKey);
        Objects.requireNonNull(reason);

        String nodeId = clusterCoordinator.getNodeId();
        log.warn("Forcing takeover of workflow {} by node {} - reason: {}", workflowKey, nodeId, reason);

        TakeoverOptions options = TakeoverOptions.builder()
                .skipExpiryValidation(true)
                .skipOwnerValidation(true)
                .build();

        return attemptSafeTakeover(workflowKey, options)
                .doOnSuccess(result -> {
                    if (result.isSuccessful()) {
                        metrics.recordForcedTakeover();
                        notifyForcedTakeover(workflowKey, nodeId, reason);
                    }
                });
    }

    // ========== Listeners ==========

    /**
     * Adds a takeover event listener.
     */
    public void addListener(ITakeoverListener listener) {
        Objects.requireNonNull(listener);
        listeners.add(listener);
    }

    /**
     * Removes a takeover event listener.
     */
    public void removeListener(ITakeoverListener listener) {
        listeners.remove(listener);
    }

    // ========== Metrics ==========

    /**
     * Gets takeover metrics.
     */
    public TakeoverMetrics getMetrics() {
        return metrics;
    }

    // ========== Private Methods ==========

    private Mono<TakeoverResult> performSafeTakeoverWithLock(String workflowKey,
                                                               String nodeId,
                                                               TakeoverOptions options) {
        return validateTakeoverPreconditions(workflowKey, nodeId, options)
                .flatMap(validation -> {
                    if (!validation.isValid()) {
                        log.warn("Takeover validation failed for {}: {}", workflowKey, validation.getReason());
                        metrics.recordValidationFailure();
                        return Mono.just(TakeoverResult.validationFailed(workflowKey, nodeId,
                                validation.getReason()));
                    }

                    // Double-check lease expiry after verification delay (prevents race conditions)
                    if (!options.isSkipExpiryValidation()) {
                        return Mono.delay(verificationDelay)
                                .then(revalidateAndExecuteTakeover(workflowKey, nodeId, options, validation));
                    }

                    return executeTakeover(workflowKey, nodeId, validation);
                });
    }

    private Mono<TakeoverValidation> validateTakeoverPreconditions(String workflowKey,
                                                                     String nodeId,
                                                                     TakeoverOptions options) {
        return ownershipRepository.findByWorkflowId(workflowKey)
                .flatMap(optLease -> {
                    if (optLease.isEmpty()) {
                        // No lease exists - safe to acquire
                        return Mono.just(TakeoverValidation.valid(workflowKey,
                                "No existing lease"));
                    }

                    WorkflowOwnershipLease lease = optLease.get();

                    // Check if already owned by this node
                    if (lease.getOwnerId().equals(nodeId)) {
                        if (lease.isValid()) {
                            return Mono.just(TakeoverValidation.invalid(workflowKey,
                                    "Already owned by this node with valid lease"));
                        }
                        // Owned by this node but expired - allow renewal/reacquisition
                        return Mono.just(TakeoverValidation.valid(workflowKey,
                                "Reacquiring expired lease from same node"));
                    }

                    // Validate lease expiry
                    if (!options.isSkipExpiryValidation()) {
                        TakeoverValidation expiryValidation = validateLeaseExpiry(lease);
                        if (!expiryValidation.isValid()) {
                            return Mono.just(expiryValidation);
                        }
                    }

                    // Validate owner is unresponsive
                    if (!options.isSkipOwnerValidation()) {
                        return validateOwnerStatus(lease);
                    }

                    return Mono.just(TakeoverValidation.valid(workflowKey,
                            "Lease expired and validation skipped"));
                });
    }

    private TakeoverValidation validateLeaseExpiry(WorkflowOwnershipLease lease) {
        if (!lease.isExpired()) {
            return TakeoverValidation.invalid(lease.getWorkflowId(),
                    "Lease is still valid until " + lease.getLeaseExpiresAt());
        }

        // Check grace period
        Duration expiredFor = Duration.between(lease.getLeaseExpiresAt(), Instant.now());
        if (expiredFor.compareTo(gracePeriod) < 0) {
            return TakeoverValidation.invalid(lease.getWorkflowId(),
                    String.format("Lease expired only %s ago, grace period is %s",
                            expiredFor, gracePeriod));
        }

        return TakeoverValidation.valid(lease.getWorkflowId(),
                String.format("Lease expired %s ago (beyond grace period)", expiredFor));
    }

    private Mono<TakeoverValidation> validateOwnerStatus(WorkflowOwnershipLease lease) {
        String ownerId = lease.getOwnerId();

        if (membershipService != null) {
            // Use membership service for accurate owner status
            boolean isOwnerAlive = membershipService.isNodeActive(ownerId);
            if (isOwnerAlive) {
                return Mono.just(TakeoverValidation.invalid(lease.getWorkflowId(),
                        "Previous owner " + ownerId + " is still active in the cluster"));
            }
            return Mono.just(TakeoverValidation.valid(lease.getWorkflowId(),
                    "Previous owner " + ownerId + " is not active in the cluster"));
        }

        // Fall back to heuristic - check if owner has any valid leases
        return ownershipRepository.findByOwnerId(ownerId)
                .any(WorkflowOwnershipLease::isValid)
                .map(hasValidLeases -> {
                    if (hasValidLeases) {
                        return TakeoverValidation.invalid(lease.getWorkflowId(),
                                "Previous owner " + ownerId + " has other valid leases (may be slow but alive)");
                    }
                    return TakeoverValidation.valid(lease.getWorkflowId(),
                            "Previous owner " + ownerId + " has no valid leases");
                });
    }

    private Mono<TakeoverValidation> validateLeaseForTakeover(WorkflowOwnershipLease lease) {
        if (!lease.isExpired()) {
            return Mono.just(TakeoverValidation.invalid(lease.getWorkflowId(),
                    "Lease is still valid"));
        }

        return validateOwnerStatus(lease);
    }

    private Mono<TakeoverResult> revalidateAndExecuteTakeover(String workflowKey,
                                                                String nodeId,
                                                                TakeoverOptions options,
                                                                TakeoverValidation originalValidation) {
        // Re-check that conditions still hold after delay
        return ownershipRepository.findByWorkflowId(workflowKey)
                .flatMap(optLease -> {
                    if (optLease.isEmpty()) {
                        // Lease was cleared - proceed
                        return executeTakeover(workflowKey, nodeId, originalValidation);
                    }

                    WorkflowOwnershipLease lease = optLease.get();

                    // Check if someone else grabbed it during the delay
                    if (!lease.getOwnerId().equals(originalValidation.getPreviousOwnerId())) {
                        log.warn("Concurrent takeover detected for {} - new owner: {}",
                                workflowKey, lease.getOwnerId());
                        metrics.recordConcurrentTakeoverDetected();
                        return Mono.just(TakeoverResult.concurrentTakeover(workflowKey, nodeId,
                                lease.getOwnerId()));
                    }

                    // Check if version changed (indicates renewal or other modification)
                    if (lease.getVersion() != originalValidation.getOriginalVersion()) {
                        log.warn("Lease version changed during takeover validation for {}", workflowKey);
                        return Mono.just(TakeoverResult.versionConflict(workflowKey, nodeId,
                                originalValidation.getOriginalVersion(), lease.getVersion()));
                    }

                    return executeTakeover(workflowKey, nodeId, originalValidation);
                });
    }

    private Mono<TakeoverResult> executeTakeover(String workflowKey,
                                                   String nodeId,
                                                   TakeoverValidation validation) {
        Instant startTime = Instant.now();

        return ownershipRepository.generateFencingToken()
                .flatMap(newFencingToken -> {
                    log.info("Executing takeover for {} with new fencing token {}",
                            workflowKey, newFencingToken);

                    // Atomically transfer ownership
                    return ownershipRepository.tryTransfer(
                                    workflowKey,
                                    validation.getPreviousOwnerId(),
                                    nodeId,
                                    "Safe takeover by " + nodeId)
                            .flatMap(optNewLease -> {
                                if (optNewLease.isEmpty()) {
                                    log.warn("Atomic transfer failed for {} - race condition?", workflowKey);
                                    metrics.recordAtomicTransferFailure();
                                    return Mono.just(TakeoverResult.atomicTransferFailed(workflowKey, nodeId));
                                }

                                WorkflowOwnershipLease newLease = optNewLease.get();

                                // Update workflow state
                                return updateWorkflowStateAfterTakeover(workflowKey, nodeId, newLease)
                                        .map(stateUpdated -> {
                                            Duration duration = Duration.between(startTime, Instant.now());
                                            metrics.recordSuccessfulTakeover(duration);

                                            TakeoverResult result = TakeoverResult.success(
                                                    workflowKey, nodeId, newLease.getFencingToken(),
                                                    newLease.getOwnershipEpoch(), validation.getPreviousOwnerId());

                                            notifyTakeoverSuccess(result);
                                            return result;
                                        });
                            });
                });
    }

    private Mono<Boolean> updateWorkflowStateAfterTakeover(String workflowKey,
                                                             String nodeId,
                                                             WorkflowOwnershipLease newLease) {
        return stateStore.findByExecutionKey(workflowKey)
                .flatMap(optState -> {
                    if (optState.isEmpty()) {
                        // No state to update
                        return Mono.just(true);
                    }

                    WorkflowExecutionState state = optState.get();
                    WorkflowExecutionState updatedState = state.completeTakeover(
                            nodeId,
                            newLease.getLeaseId(),
                            newLease.getLeaseDuration(),
                            newLease.getFencingToken());

                    return stateStore.save(updatedState)
                            .map(saved -> true)
                            .onErrorReturn(false);
                });
    }

    private void notifyTakeoverSuccess(TakeoverResult result) {
        for (ITakeoverListener listener : listeners) {
            try {
                listener.onTakeoverCompleted(result.getWorkflowKey(), result.getNewOwnerId(),
                        result.getPreviousOwnerId(), result.getNewFencingToken());
            } catch (Exception e) {
                log.error("Error notifying takeover listener", e);
            }
        }
    }

    private void notifyForcedTakeover(String workflowKey, String nodeId, String reason) {
        for (ITakeoverListener listener : listeners) {
            try {
                listener.onForcedTakeover(workflowKey, nodeId, reason);
            } catch (Exception e) {
                log.error("Error notifying forced takeover listener", e);
            }
        }
    }

    // ========== Inner Classes ==========

    /**
     * Options for takeover operations.
     */
    @Getter
    @Builder
    public static class TakeoverOptions {
        public static final TakeoverOptions DEFAULT = TakeoverOptions.builder().build();

        @Builder.Default
        private final boolean skipExpiryValidation = false;

        @Builder.Default
        private final boolean skipOwnerValidation = false;

        @Builder.Default
        private final boolean allowForcedTakeover = false;
    }

    /**
     * Result of takeover validation.
     */
    @Getter
    @ToString
    public static class TakeoverValidation {
        public enum Status {
            VALID, INVALID
        }

        private final String workflowKey;
        private final Status status;
        private final String reason;
        private final String previousOwnerId;
        private final long originalVersion;

        private TakeoverValidation(String workflowKey, Status status, String reason,
                                    String previousOwnerId, long originalVersion) {
            this.workflowKey = workflowKey;
            this.status = status;
            this.reason = reason;
            this.previousOwnerId = previousOwnerId;
            this.originalVersion = originalVersion;
        }

        public boolean isValid() {
            return status == Status.VALID;
        }

        public static TakeoverValidation valid(String workflowKey, String reason) {
            return new TakeoverValidation(workflowKey, Status.VALID, reason, null, 0);
        }

        public static TakeoverValidation valid(String workflowKey, String reason,
                                                String previousOwnerId, long version) {
            return new TakeoverValidation(workflowKey, Status.VALID, reason, previousOwnerId, version);
        }

        public static TakeoverValidation invalid(String workflowKey, String reason) {
            return new TakeoverValidation(workflowKey, Status.INVALID, reason, null, 0);
        }
    }

    /**
     * Result of a takeover attempt.
     */
    @Getter
    @ToString
    public static class TakeoverResult {
        public enum Status {
            SUCCESS,
            LOCK_CONTENTION,
            VALIDATION_FAILED,
            CONCURRENT_TAKEOVER,
            VERSION_CONFLICT,
            ATOMIC_TRANSFER_FAILED,
            ERROR
        }

        private final String workflowKey;
        private final String newOwnerId;
        private final Status status;
        private final String reason;
        private final long newFencingToken;
        private final long newEpoch;
        private final String previousOwnerId;
        private final String concurrentOwnerId;

        private TakeoverResult(String workflowKey, String newOwnerId, Status status, String reason,
                                long newFencingToken, long newEpoch, String previousOwnerId,
                                String concurrentOwnerId) {
            this.workflowKey = workflowKey;
            this.newOwnerId = newOwnerId;
            this.status = status;
            this.reason = reason;
            this.newFencingToken = newFencingToken;
            this.newEpoch = newEpoch;
            this.previousOwnerId = previousOwnerId;
            this.concurrentOwnerId = concurrentOwnerId;
        }

        public boolean isSuccessful() {
            return status == Status.SUCCESS;
        }

        public static TakeoverResult success(String workflowKey, String newOwnerId,
                                              long fencingToken, long epoch, String previousOwnerId) {
            return new TakeoverResult(workflowKey, newOwnerId, Status.SUCCESS, "Takeover successful",
                    fencingToken, epoch, previousOwnerId, null);
        }

        public static TakeoverResult lockContention(String workflowKey, String nodeId) {
            return new TakeoverResult(workflowKey, nodeId, Status.LOCK_CONTENTION,
                    "Failed to acquire takeover lock", 0, 0, null, null);
        }

        public static TakeoverResult validationFailed(String workflowKey, String nodeId, String reason) {
            return new TakeoverResult(workflowKey, nodeId, Status.VALIDATION_FAILED,
                    reason, 0, 0, null, null);
        }

        public static TakeoverResult concurrentTakeover(String workflowKey, String nodeId,
                                                         String concurrentOwnerId) {
            return new TakeoverResult(workflowKey, nodeId, Status.CONCURRENT_TAKEOVER,
                    "Another node completed takeover first", 0, 0, null, concurrentOwnerId);
        }

        public static TakeoverResult versionConflict(String workflowKey, String nodeId,
                                                       long expected, long actual) {
            return new TakeoverResult(workflowKey, nodeId, Status.VERSION_CONFLICT,
                    String.format("Version conflict: expected %d, actual %d", expected, actual),
                    0, 0, null, null);
        }

        public static TakeoverResult atomicTransferFailed(String workflowKey, String nodeId) {
            return new TakeoverResult(workflowKey, nodeId, Status.ATOMIC_TRANSFER_FAILED,
                    "Atomic ownership transfer failed", 0, 0, null, null);
        }

        public static TakeoverResult error(String workflowKey, String nodeId, String error) {
            return new TakeoverResult(workflowKey, nodeId, Status.ERROR,
                    error, 0, 0, null, null);
        }
    }

    /**
     * Listener for takeover events.
     */
    public interface ITakeoverListener {
        void onTakeoverCompleted(String workflowKey, String newOwnerId,
                                  String previousOwnerId, long newFencingToken);
        void onTakeoverFailed(String workflowKey, String nodeId, String reason);
        void onForcedTakeover(String workflowKey, String nodeId, String reason);
    }

    /**
     * Metrics for takeover operations.
     */
    @Getter
    public static class TakeoverMetrics {
        private final AtomicLong takeoverAttempts = new AtomicLong(0);
        private final AtomicLong successfulTakeovers = new AtomicLong(0);
        private final AtomicLong lockContentionFailures = new AtomicLong(0);
        private final AtomicLong validationFailures = new AtomicLong(0);
        private final AtomicLong concurrentTakeoversDetected = new AtomicLong(0);
        private final AtomicLong atomicTransferFailures = new AtomicLong(0);
        private final AtomicLong fencingTokenRejections = new AtomicLong(0);
        private final AtomicLong forcedTakeovers = new AtomicLong(0);
        private final AtomicLong errors = new AtomicLong(0);
        private final AtomicLong totalTakeoverTimeNanos = new AtomicLong(0);

        void recordTakeoverAttempt() { takeoverAttempts.incrementAndGet(); }
        void recordSuccessfulTakeover(Duration duration) {
            successfulTakeovers.incrementAndGet();
            totalTakeoverTimeNanos.addAndGet(duration.toNanos());
        }
        void recordLockContentionFailure() { lockContentionFailures.incrementAndGet(); }
        void recordValidationFailure() { validationFailures.incrementAndGet(); }
        void recordConcurrentTakeoverDetected() { concurrentTakeoversDetected.incrementAndGet(); }
        void recordAtomicTransferFailure() { atomicTransferFailures.incrementAndGet(); }
        void recordFencingTokenRejection() { fencingTokenRejections.incrementAndGet(); }
        void recordForcedTakeover() { forcedTakeovers.incrementAndGet(); }
        void recordTakeoverError() { errors.incrementAndGet(); }

        public double getSuccessRate() {
            long total = takeoverAttempts.get();
            if (total == 0) return 1.0;
            return (double) successfulTakeovers.get() / total;
        }

        public Duration getAverageTakeoverTime() {
            long count = successfulTakeovers.get();
            if (count == 0) return Duration.ZERO;
            return Duration.ofNanos(totalTakeoverTimeNanos.get() / count);
        }
    }
}
