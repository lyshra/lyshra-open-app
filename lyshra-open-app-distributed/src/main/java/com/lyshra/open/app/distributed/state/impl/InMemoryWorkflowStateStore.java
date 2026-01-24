package com.lyshra.open.app.distributed.state.impl;

import com.lyshra.open.app.distributed.state.IWorkflowStateStore;
import com.lyshra.open.app.distributed.state.WorkflowCheckpoint;
import com.lyshra.open.app.distributed.state.WorkflowExecutionState;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory implementation of IWorkflowStateStore.
 *
 * Suitable for:
 * - Single-node deployments
 * - Development and testing
 * - Scenarios where persistence is not required
 *
 * For production distributed deployments, use a database-backed
 * implementation (JDBC, MongoDB, etc.) for durability.
 *
 * Key Features:
 * 1. Optimistic locking via version checking
 * 2. Fast in-memory operations
 * 3. Support for all query patterns
 * 4. Checkpoint management
 *
 * Thread Safety: This class is thread-safe using ConcurrentHashMap.
 *
 * Note: Data is NOT persisted across restarts. For production use,
 * implement DatabaseWorkflowStateStore or MongoWorkflowStateStore.
 */
@Slf4j
public class InMemoryWorkflowStateStore implements IWorkflowStateStore {

    private final ConcurrentHashMap<String, WorkflowExecutionState> states;
    private final ConcurrentHashMap<String, List<WorkflowCheckpoint>> checkpoints;
    private final AtomicBoolean initialized;

    public InMemoryWorkflowStateStore() {
        this.states = new ConcurrentHashMap<>();
        this.checkpoints = new ConcurrentHashMap<>();
        this.initialized = new AtomicBoolean(false);
    }

    @Override
    public Mono<Void> initialize() {
        if (!initialized.compareAndSet(false, true)) {
            log.warn("State store already initialized");
            return Mono.empty();
        }

        log.info("Initializing in-memory workflow state store");
        return Mono.empty();
    }

    @Override
    public Mono<Void> shutdown() {
        log.info("Shutting down in-memory workflow state store");

        return Mono.fromRunnable(() -> {
            states.clear();
            checkpoints.clear();
            initialized.set(false);
        });
    }

    @Override
    public Mono<WorkflowExecutionState> save(WorkflowExecutionState state) {
        Objects.requireNonNull(state, "state must not be null");

        return Mono.fromCallable(() -> {
            String key = state.getExecutionKey();

            // Optimistic locking check
            WorkflowExecutionState existing = states.get(key);
            if (existing != null && existing.getVersion() != state.getVersion()) {
                throw new OptimisticLockException(
                        "Version mismatch for " + key + ": expected " + state.getVersion() +
                        " but found " + existing.getVersion());
            }

            // Increment version and save
            WorkflowExecutionState toSave = state.withVersion(state.getVersion() + 1)
                    .withLastUpdatedAt(Instant.now());
            states.put(key, toSave);

            log.debug("Saved workflow state: {} (version {})", key, toSave.getVersion());
            return toSave;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Optional<WorkflowExecutionState>> findByExecutionKey(String executionKey) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");

        return Mono.fromCallable(() -> Optional.ofNullable(states.get(executionKey)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<WorkflowExecutionState> findByPartition(int partitionId) {
        return Flux.defer(() -> Flux.fromIterable(states.values())
                .filter(state -> state.getPartitionId() == partitionId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<WorkflowExecutionState> findByOwner(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        return Flux.defer(() -> Flux.fromIterable(states.values())
                .filter(state -> nodeId.equals(state.getOwnerNodeId())))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<WorkflowExecutionState> findByStatus(WorkflowExecutionState.ExecutionStatus status) {
        Objects.requireNonNull(status, "status must not be null");

        return Flux.defer(() -> Flux.fromIterable(states.values())
                .filter(state -> state.getStatus() == status))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<WorkflowExecutionState> findStaleExecutions(Duration staleThreshold) {
        Objects.requireNonNull(staleThreshold, "staleThreshold must not be null");

        Instant threshold = Instant.now().minus(staleThreshold);

        return Flux.defer(() -> Flux.fromIterable(states.values())
                .filter(state -> !state.isTerminal() &&
                                state.getLastUpdatedAt().isBefore(threshold)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> delete(String executionKey) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");

        return Mono.fromRunnable(() -> {
            states.remove(executionKey);
            checkpoints.remove(executionKey);
            log.debug("Deleted workflow state: {}", executionKey);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Integer> deleteOlderThan(Instant before) {
        Objects.requireNonNull(before, "before must not be null");

        return Mono.fromCallable(() -> {
            int deleted = 0;
            Iterator<Map.Entry<String, WorkflowExecutionState>> iterator = states.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<String, WorkflowExecutionState> entry = iterator.next();
                if (entry.getValue().isTerminal() &&
                    entry.getValue().getCompletedAt() != null &&
                    entry.getValue().getCompletedAt().isBefore(before)) {
                    iterator.remove();
                    checkpoints.remove(entry.getKey());
                    deleted++;
                }
            }

            log.info("Deleted {} workflow states older than {}", deleted, before);
            return deleted;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> updateOwner(String executionKey, String newOwnerNodeId, long expectedVersion) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");

        return Mono.fromCallable(() -> {
            WorkflowExecutionState existing = states.get(executionKey);
            if (existing == null) {
                return false;
            }

            if (existing.getVersion() != expectedVersion) {
                log.warn("Version mismatch updating owner for {}: expected {}, found {}",
                        executionKey, expectedVersion, existing.getVersion());
                return false;
            }

            WorkflowExecutionState updated = existing.withOwnerNodeId(newOwnerNodeId)
                    .withVersion(existing.getVersion() + 1)
                    .withLastUpdatedAt(Instant.now());
            states.put(executionKey, updated);

            log.debug("Updated owner for {} to {}", executionKey, newOwnerNodeId);
            return true;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> updateStatus(String executionKey, WorkflowExecutionState.ExecutionStatus newStatus,
                                       long expectedVersion) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");
        Objects.requireNonNull(newStatus, "newStatus must not be null");

        return Mono.fromCallable(() -> {
            WorkflowExecutionState existing = states.get(executionKey);
            if (existing == null) {
                return false;
            }

            if (existing.getVersion() != expectedVersion) {
                log.warn("Version mismatch updating status for {}: expected {}, found {}",
                        executionKey, expectedVersion, existing.getVersion());
                return false;
            }

            WorkflowExecutionState updated = existing.withStatus(newStatus)
                    .withVersion(existing.getVersion() + 1)
                    .withLastUpdatedAt(Instant.now());

            if (newStatus == WorkflowExecutionState.ExecutionStatus.COMPLETED ||
                newStatus == WorkflowExecutionState.ExecutionStatus.FAILED ||
                newStatus == WorkflowExecutionState.ExecutionStatus.CANCELLED) {
                updated = updated.withCompletedAt(Instant.now());
            }

            states.put(executionKey, updated);

            log.debug("Updated status for {} to {}", executionKey, newStatus);
            return true;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> saveCheckpoint(String executionKey, WorkflowCheckpoint checkpoint) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");
        Objects.requireNonNull(checkpoint, "checkpoint must not be null");

        return Mono.fromRunnable(() -> {
            checkpoints.compute(executionKey, (key, existing) -> {
                List<WorkflowCheckpoint> list = existing != null ? new ArrayList<>(existing) : new ArrayList<>();
                list.add(checkpoint);
                return list;
            });
            log.debug("Saved checkpoint {} for {}", checkpoint.getCheckpointId(), executionKey);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Optional<WorkflowCheckpoint>> loadLatestCheckpoint(String executionKey) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");

        return Mono.fromCallable(() -> {
            List<WorkflowCheckpoint> list = checkpoints.get(executionKey);
            if (list == null || list.isEmpty()) {
                return Optional.<WorkflowCheckpoint>empty();
            }
            return Optional.of(list.get(list.size() - 1));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<WorkflowCheckpoint> loadAllCheckpoints(String executionKey) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");

        return Flux.defer(() -> {
            List<WorkflowCheckpoint> list = checkpoints.get(executionKey);
            if (list == null) {
                return Flux.empty();
            }
            return Flux.fromIterable(list);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Long> countByStatus(WorkflowExecutionState.ExecutionStatus status) {
        Objects.requireNonNull(status, "status must not be null");

        return Mono.fromCallable(() -> states.values().stream()
                .filter(state -> state.getStatus() == status)
                .count())
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Long> countByPartition(int partitionId) {
        return Mono.fromCallable(() -> states.values().stream()
                .filter(state -> state.getPartitionId() == partitionId)
                .count())
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> exists(String executionKey) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");
        return Mono.just(states.containsKey(executionKey));
    }

    @Override
    public Mono<Integer> reassignOwnership(String failedNodeId, String newOwnerNodeId) {
        Objects.requireNonNull(failedNodeId, "failedNodeId must not be null");

        return Mono.fromCallable(() -> {
            int reassigned = 0;
            Instant now = Instant.now();

            for (Map.Entry<String, WorkflowExecutionState> entry : states.entrySet()) {
                WorkflowExecutionState state = entry.getValue();
                if (failedNodeId.equals(state.getOwnerNodeId()) && !state.isTerminal()) {
                    WorkflowExecutionState updated;
                    if (newOwnerNodeId != null) {
                        // Transfer to new owner with ownership status updated
                        updated = state
                                .withPreviousOwnerId(failedNodeId)
                                .withOwnerNodeId(newOwnerNodeId)
                                .withOwnershipStatus(WorkflowExecutionState.OwnershipStatus.OWNED)
                                .withOwnershipStatusChangedAt(now)
                                .withOwnershipStatusReason("Reassigned from failed node " + failedNodeId)
                                .withOwnershipTransferredAt(now)
                                .withTransferReason("Node failure recovery")
                                .withStatus(WorkflowExecutionState.ExecutionStatus.NEEDS_RECOVERY)
                                .withOwnershipEpoch(state.getOwnershipEpoch() + 1)
                                .withVersion(state.getVersion() + 1)
                                .withLastUpdatedAt(now);
                    } else {
                        // Mark as orphaned if no new owner specified
                        updated = state
                                .withPreviousOwnerId(failedNodeId)
                                .withOwnerNodeId(null)
                                .withOwnershipStatus(WorkflowExecutionState.OwnershipStatus.ORPHANED)
                                .withOwnershipStatusChangedAt(now)
                                .withOwnershipStatusReason("Owner node " + failedNodeId + " failed")
                                .withOwnershipTransferredAt(now)
                                .withTransferReason("Node failure")
                                .withStatus(WorkflowExecutionState.ExecutionStatus.NEEDS_RECOVERY)
                                .withVersion(state.getVersion() + 1)
                                .withLastUpdatedAt(now);
                    }
                    states.put(entry.getKey(), updated);
                    reassigned++;
                }
            }

            log.info("Reassigned {} workflow executions from {} to {}",
                    reassigned, failedNodeId, newOwnerNodeId);
            return reassigned;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ========== Ownership-Specific Query Methods ==========

    @Override
    public Flux<WorkflowExecutionState> findByOwnershipStatus(WorkflowExecutionState.OwnershipStatus status) {
        Objects.requireNonNull(status, "status must not be null");

        return Flux.defer(() -> Flux.fromIterable(states.values())
                .filter(state -> state.getOwnershipStatus() == status))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<WorkflowExecutionState> findOrphanedExecutions() {
        return Flux.defer(() -> Flux.fromIterable(states.values())
                .filter(state -> state.isOrphaned() && !state.isTerminal()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<WorkflowExecutionState> findAvailableForTakeover() {
        return Flux.defer(() -> Flux.fromIterable(states.values())
                .filter(state -> state.isAvailableForTakeover() && !state.isTerminal()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<WorkflowExecutionState> findWithExpiredLeases(Instant cutoffTime) {
        Objects.requireNonNull(cutoffTime, "cutoffTime must not be null");

        return Flux.defer(() -> Flux.fromIterable(states.values())
                .filter(state -> !state.isTerminal() &&
                                state.getLeaseExpiresAt() != null &&
                                state.getLeaseExpiresAt().isBefore(cutoffTime)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<WorkflowExecutionState> findRecoveryInProgress() {
        return Flux.defer(() -> Flux.fromIterable(states.values())
                .filter(state -> state.isRecoveryInProgress() && !state.isTerminal()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<WorkflowExecutionState> findExhaustedRecoveryAttempts() {
        return Flux.defer(() -> Flux.fromIterable(states.values())
                .filter(state -> !state.isTerminal() && !state.canAttemptRecovery()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<WorkflowExecutionState> findByOwnerAndOwnershipStatus(String nodeId,
                                                                        WorkflowExecutionState.OwnershipStatus status) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(status, "status must not be null");

        return Flux.defer(() -> Flux.fromIterable(states.values())
                .filter(state -> nodeId.equals(state.getOwnerNodeId()) &&
                                state.getOwnershipStatus() == status))
                .subscribeOn(Schedulers.boundedElastic());
    }

    // ========== Atomic Ownership Update Methods ==========

    @Override
    public Mono<Boolean> updateOwnershipStatus(String executionKey,
                                                WorkflowExecutionState.OwnershipStatus newStatus,
                                                String reason,
                                                long expectedVersion) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");
        Objects.requireNonNull(newStatus, "newStatus must not be null");

        return Mono.fromCallable(() -> {
            WorkflowExecutionState existing = states.get(executionKey);
            if (existing == null) {
                return false;
            }

            if (existing.getVersion() != expectedVersion) {
                log.warn("Version mismatch updating ownership status for {}: expected {}, found {}",
                        executionKey, expectedVersion, existing.getVersion());
                return false;
            }

            WorkflowExecutionState updated = existing.withOwnershipStatus(newStatus, reason);
            states.put(executionKey, updated);

            log.debug("Updated ownership status for {} to {} (reason: {})", executionKey, newStatus, reason);
            return true;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<WorkflowExecutionState> acquireOwnership(String executionKey,
                                                           String nodeId,
                                                           String leaseId,
                                                           Duration leaseDuration,
                                                           long fencingToken,
                                                           long expectedVersion) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(leaseId, "leaseId must not be null");
        Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");

        return Mono.fromCallable(() -> {
            WorkflowExecutionState existing = states.get(executionKey);
            if (existing == null) {
                return null;
            }

            if (existing.getVersion() != expectedVersion) {
                log.warn("Version mismatch acquiring ownership for {}: expected {}, found {}",
                        executionKey, expectedVersion, existing.getVersion());
                return null;
            }

            // Check if already owned by another node with valid lease
            if (existing.isCurrentlyOwned() && !nodeId.equals(existing.getOwnerNodeId())) {
                log.warn("Cannot acquire ownership for {}: already owned by {}",
                        executionKey, existing.getOwnerNodeId());
                return null;
            }

            Instant now = Instant.now();
            WorkflowExecutionState updated = existing.acquireOwnership(
                    leaseId, nodeId, now, now.plus(leaseDuration), fencingToken);
            states.put(executionKey, updated);

            log.debug("Acquired ownership for {} by node {} (fencing token: {})",
                    executionKey, nodeId, fencingToken);
            return updated;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> renewLease(String executionKey, Duration extensionDuration, long expectedVersion) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");
        Objects.requireNonNull(extensionDuration, "extensionDuration must not be null");

        return Mono.fromCallable(() -> {
            WorkflowExecutionState existing = states.get(executionKey);
            if (existing == null) {
                return false;
            }

            if (existing.getVersion() != expectedVersion) {
                log.warn("Version mismatch renewing lease for {}: expected {}, found {}",
                        executionKey, expectedVersion, existing.getVersion());
                return false;
            }

            Instant now = Instant.now();
            WorkflowExecutionState updated = existing.renewLease(now.plus(extensionDuration));
            states.put(executionKey, updated);

            log.debug("Renewed lease for {} until {}", executionKey, updated.getLeaseExpiresAt());
            return true;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> releaseOwnership(String executionKey, String reason, long expectedVersion) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");

        return Mono.fromCallable(() -> {
            WorkflowExecutionState existing = states.get(executionKey);
            if (existing == null) {
                return false;
            }

            if (existing.getVersion() != expectedVersion) {
                log.warn("Version mismatch releasing ownership for {}: expected {}, found {}",
                        executionKey, expectedVersion, existing.getVersion());
                return false;
            }

            WorkflowExecutionState updated = existing.releaseOwnership(reason != null ? reason : "Explicit release");
            states.put(executionKey, updated);

            log.debug("Released ownership for {} (reason: {})", executionKey, reason);
            return true;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<WorkflowExecutionState> transferOwnership(String executionKey,
                                                            String newOwnerId,
                                                            String newLeaseId,
                                                            Duration leaseDuration,
                                                            long newFencingToken,
                                                            String reason,
                                                            long expectedVersion) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");
        Objects.requireNonNull(newOwnerId, "newOwnerId must not be null");
        Objects.requireNonNull(newLeaseId, "newLeaseId must not be null");
        Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");

        return Mono.fromCallable(() -> {
            WorkflowExecutionState existing = states.get(executionKey);
            if (existing == null) {
                return null;
            }

            if (existing.getVersion() != expectedVersion) {
                log.warn("Version mismatch transferring ownership for {}: expected {}, found {}",
                        executionKey, expectedVersion, existing.getVersion());
                return null;
            }

            Instant now = Instant.now();
            WorkflowExecutionState updated = existing.transferOwnership(
                    newOwnerId, newLeaseId, now.plus(leaseDuration), newFencingToken,
                    reason != null ? reason : "Ownership transfer");
            states.put(executionKey, updated);

            log.debug("Transferred ownership for {} from {} to {} (reason: {})",
                    executionKey, existing.getOwnerNodeId(), newOwnerId, reason);
            return updated;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> recordRecoveryAttempt(String executionKey, String error, long expectedVersion) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");

        return Mono.fromCallable(() -> {
            WorkflowExecutionState existing = states.get(executionKey);
            if (existing == null) {
                return false;
            }

            if (existing.getVersion() != expectedVersion) {
                log.warn("Version mismatch recording recovery attempt for {}: expected {}, found {}",
                        executionKey, expectedVersion, existing.getVersion());
                return false;
            }

            WorkflowExecutionState updated = existing.recordRecoveryFailure(error);
            states.put(executionKey, updated);

            log.debug("Recorded recovery attempt {} for {} (error: {})",
                    updated.getRecoveryAttempts(), executionKey, error);
            return true;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> markOrphaned(String executionKey, String reason, long expectedVersion) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");

        return Mono.fromCallable(() -> {
            WorkflowExecutionState existing = states.get(executionKey);
            if (existing == null) {
                return false;
            }

            if (existing.getVersion() != expectedVersion) {
                log.warn("Version mismatch marking orphaned for {}: expected {}, found {}",
                        executionKey, expectedVersion, existing.getVersion());
                return false;
            }

            WorkflowExecutionState updated = existing.markOrphaned(reason != null ? reason : "Marked as orphaned");
            states.put(executionKey, updated);

            log.debug("Marked {} as orphaned (reason: {})", executionKey, reason);
            return true;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> markPendingTakeover(String executionKey, String targetNodeId, long expectedVersion) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");
        Objects.requireNonNull(targetNodeId, "targetNodeId must not be null");

        return Mono.fromCallable(() -> {
            WorkflowExecutionState existing = states.get(executionKey);
            if (existing == null) {
                return false;
            }

            if (existing.getVersion() != expectedVersion) {
                log.warn("Version mismatch marking pending takeover for {}: expected {}, found {}",
                        executionKey, expectedVersion, existing.getVersion());
                return false;
            }

            WorkflowExecutionState updated = existing.markPendingTakeover(targetNodeId);
            states.put(executionKey, updated);

            log.debug("Marked {} as pending takeover by {}", executionKey, targetNodeId);
            return true;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> revokeOwnership(String executionKey, String reason, long expectedVersion) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");

        return Mono.fromCallable(() -> {
            WorkflowExecutionState existing = states.get(executionKey);
            if (existing == null) {
                return false;
            }

            if (existing.getVersion() != expectedVersion) {
                log.warn("Version mismatch revoking ownership for {}: expected {}, found {}",
                        executionKey, expectedVersion, existing.getVersion());
                return false;
            }

            WorkflowExecutionState updated = existing.revokeOwnership(reason != null ? reason : "Ownership revoked");
            states.put(executionKey, updated);

            log.debug("Revoked ownership for {} (reason: {})", executionKey, reason);
            return true;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ========== Ownership Count Methods ==========

    @Override
    public Mono<Long> countByOwnershipStatus(WorkflowExecutionState.OwnershipStatus status) {
        Objects.requireNonNull(status, "status must not be null");

        return Mono.fromCallable(() -> states.values().stream()
                .filter(state -> state.getOwnershipStatus() == status)
                .count())
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Long> countOrphaned() {
        return Mono.fromCallable(() -> states.values().stream()
                .filter(state -> state.isOrphaned() && !state.isTerminal())
                .count())
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Long> countActivelyOwnedBy(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        return Mono.fromCallable(() -> states.values().stream()
                .filter(state -> nodeId.equals(state.getOwnerNodeId()) &&
                                state.isCurrentlyOwned() &&
                                !state.isTerminal())
                .count())
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Long> countAvailableForTakeover() {
        return Mono.fromCallable(() -> states.values().stream()
                .filter(state -> state.isAvailableForTakeover() && !state.isTerminal())
                .count())
                .subscribeOn(Schedulers.boundedElastic());
    }

    // ========== Batch Ownership Operations ==========

    @Override
    public Mono<Integer> markAllOrphaned(String failedNodeId, String reason) {
        Objects.requireNonNull(failedNodeId, "failedNodeId must not be null");

        return Mono.fromCallable(() -> {
            int marked = 0;
            Instant now = Instant.now();
            String orphanReason = reason != null ? reason : "Owner node " + failedNodeId + " failed";

            for (Map.Entry<String, WorkflowExecutionState> entry : states.entrySet()) {
                WorkflowExecutionState state = entry.getValue();
                if (failedNodeId.equals(state.getOwnerNodeId()) && !state.isTerminal()) {
                    WorkflowExecutionState updated = state
                            .withOwnershipStatus(WorkflowExecutionState.OwnershipStatus.ORPHANED)
                            .withOwnershipStatusChangedAt(now)
                            .withOwnershipStatusReason(orphanReason)
                            .withStatus(WorkflowExecutionState.ExecutionStatus.NEEDS_RECOVERY)
                            .withVersion(state.getVersion() + 1)
                            .withLastUpdatedAt(now);
                    states.put(entry.getKey(), updated);
                    marked++;
                }
            }

            log.info("Marked {} workflow executions as orphaned for failed node {}",
                    marked, failedNodeId);
            return marked;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Integer> expireAllLeases(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        return Mono.fromCallable(() -> {
            int expired = 0;
            Instant now = Instant.now();

            for (Map.Entry<String, WorkflowExecutionState> entry : states.entrySet()) {
                WorkflowExecutionState state = entry.getValue();
                if (nodeId.equals(state.getOwnerNodeId()) && !state.isTerminal()) {
                    WorkflowExecutionState updated = state
                            .withLeaseExpiresAt(now) // Expire immediately
                            .withOwnershipStatus(WorkflowExecutionState.OwnershipStatus.LEASE_EXPIRED)
                            .withOwnershipStatusChangedAt(now)
                            .withOwnershipStatusReason("Lease expired for node " + nodeId)
                            .withVersion(state.getVersion() + 1)
                            .withLastUpdatedAt(now);
                    states.put(entry.getKey(), updated);
                    expired++;
                }
            }

            log.info("Expired {} leases for node {}", expired, nodeId);
            return expired;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Integer> cleanupCompletedWithOwnership(Instant before) {
        Objects.requireNonNull(before, "before must not be null");

        return Mono.fromCallable(() -> {
            int cleaned = 0;
            Iterator<Map.Entry<String, WorkflowExecutionState>> iterator = states.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<String, WorkflowExecutionState> entry = iterator.next();
                WorkflowExecutionState state = entry.getValue();

                // Clean up terminal states with ownership data older than cutoff
                if (state.isTerminal() &&
                    state.getCompletedAt() != null &&
                    state.getCompletedAt().isBefore(before) &&
                    state.getOwnerNodeId() != null) {
                    iterator.remove();
                    checkpoints.remove(entry.getKey());
                    cleaned++;
                }
            }

            log.info("Cleaned up {} completed workflow executions with ownership data older than {}",
                    cleaned, before);
            return cleaned;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Exception thrown when optimistic locking fails.
     */
    public static class OptimisticLockException extends RuntimeException {
        public OptimisticLockException(String message) {
            super(message);
        }
    }
}
