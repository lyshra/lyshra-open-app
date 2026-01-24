package com.lyshra.open.app.distributed.ownership.impl;

import com.lyshra.open.app.distributed.ownership.IWorkflowOwnershipRepository;
import com.lyshra.open.app.distributed.ownership.WorkflowOwnershipLease;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation of IWorkflowOwnershipRepository.
 *
 * This implementation is suitable for:
 * - Single-node deployments
 * - Development and testing
 * - Scenarios where ownership persistence is not required
 *
 * For production distributed deployments, use a database-backed implementation
 * that provides durability across restarts and visibility across nodes.
 *
 * Thread Safety: This class is thread-safe using ConcurrentHashMap and atomic operations.
 *
 * Note: Data is NOT persisted across JVM restarts.
 */
@Slf4j
public class InMemoryOwnershipRepository implements IWorkflowOwnershipRepository {

    // Primary storage: workflowId -> lease
    private final ConcurrentHashMap<String, WorkflowOwnershipLease> leasesByWorkflowId;

    // Secondary index: leaseId -> workflowId (for lease ID lookups)
    private final ConcurrentHashMap<String, String> leaseIdIndex;

    // Fencing token generator
    private final AtomicLong fencingTokenGenerator;

    private volatile boolean initialized = false;

    public InMemoryOwnershipRepository() {
        this.leasesByWorkflowId = new ConcurrentHashMap<>();
        this.leaseIdIndex = new ConcurrentHashMap<>();
        this.fencingTokenGenerator = new AtomicLong(System.currentTimeMillis());
    }

    @Override
    public Mono<Void> initialize() {
        return Mono.fromRunnable(() -> {
            initialized = true;
            log.info("In-memory ownership repository initialized");
        });
    }

    @Override
    public Mono<Void> shutdown() {
        return Mono.fromRunnable(() -> {
            leasesByWorkflowId.clear();
            leaseIdIndex.clear();
            initialized = false;
            log.info("In-memory ownership repository shutdown");
        });
    }

    @Override
    public Mono<WorkflowOwnershipLease> save(WorkflowOwnershipLease lease) {
        Objects.requireNonNull(lease, "lease must not be null");

        return Mono.fromCallable(() -> {
            String workflowId = lease.getWorkflowId();

            // Optimistic locking check
            WorkflowOwnershipLease existing = leasesByWorkflowId.get(workflowId);
            if (existing != null && existing.getVersion() >= lease.getVersion()) {
                throw new OptimisticLockingException(workflowId, lease.getVersion(), existing.getVersion());
            }

            // Save the lease
            leasesByWorkflowId.put(workflowId, lease);
            leaseIdIndex.put(lease.getLeaseId(), workflowId);

            // Clean up old lease ID index if lease ID changed
            if (existing != null && !existing.getLeaseId().equals(lease.getLeaseId())) {
                leaseIdIndex.remove(existing.getLeaseId());
            }

            log.debug("Saved lease for workflow {}: state={}, owner={}, version={}",
                    workflowId, lease.getState(), lease.getOwnerId(), lease.getVersion());
            return lease;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Optional<WorkflowOwnershipLease>> findByWorkflowId(String workflowId) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        return Mono.just(Optional.ofNullable(leasesByWorkflowId.get(workflowId)));
    }

    @Override
    public Mono<Optional<WorkflowOwnershipLease>> findByLeaseId(String leaseId) {
        Objects.requireNonNull(leaseId, "leaseId must not be null");
        return Mono.fromCallable(() -> {
            String workflowId = leaseIdIndex.get(leaseId);
            if (workflowId == null) {
                return Optional.<WorkflowOwnershipLease>empty();
            }
            return Optional.ofNullable(leasesByWorkflowId.get(workflowId));
        });
    }

    @Override
    public Mono<Boolean> deleteByWorkflowId(String workflowId) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        return Mono.fromCallable(() -> {
            WorkflowOwnershipLease removed = leasesByWorkflowId.remove(workflowId);
            if (removed != null) {
                leaseIdIndex.remove(removed.getLeaseId());
                log.debug("Deleted lease for workflow {}", workflowId);
                return true;
            }
            return false;
        });
    }

    @Override
    public Mono<Boolean> deleteByLeaseId(String leaseId) {
        Objects.requireNonNull(leaseId, "leaseId must not be null");
        return Mono.fromCallable(() -> {
            String workflowId = leaseIdIndex.remove(leaseId);
            if (workflowId != null) {
                leasesByWorkflowId.remove(workflowId);
                return true;
            }
            return false;
        });
    }

    @Override
    public Mono<Boolean> existsByWorkflowId(String workflowId) {
        return Mono.just(leasesByWorkflowId.containsKey(workflowId));
    }

    @Override
    public Flux<WorkflowOwnershipLease> findByOwnerId(String ownerId) {
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        return Flux.fromIterable(leasesByWorkflowId.values())
                .filter(lease -> ownerId.equals(lease.getOwnerId()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<WorkflowOwnershipLease> findByPartitionId(int partitionId) {
        return Flux.fromIterable(leasesByWorkflowId.values())
                .filter(lease -> lease.getPartitionId() == partitionId)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<WorkflowOwnershipLease> findByState(WorkflowOwnershipLease.LeaseState state) {
        Objects.requireNonNull(state, "state must not be null");
        return Flux.fromIterable(leasesByWorkflowId.values())
                .filter(lease -> lease.getState() == state)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<WorkflowOwnershipLease> findAllActive() {
        return Flux.fromIterable(leasesByWorkflowId.values())
                .filter(WorkflowOwnershipLease::isValid)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<WorkflowOwnershipLease> findAllExpired() {
        return Flux.fromIterable(leasesByWorkflowId.values())
                .filter(WorkflowOwnershipLease::isExpired)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<WorkflowOwnershipLease> findExpiringWithin(Duration withinDuration) {
        Instant threshold = Instant.now().plus(withinDuration);
        return Flux.fromIterable(leasesByWorkflowId.values())
                .filter(lease -> lease.getState() == WorkflowOwnershipLease.LeaseState.ACTIVE)
                .filter(lease -> lease.getLeaseExpiresAt() != null &&
                                lease.getLeaseExpiresAt().isBefore(threshold))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<WorkflowOwnershipLease> findByClusterId(String clusterId) {
        Objects.requireNonNull(clusterId, "clusterId must not be null");
        return Flux.fromIterable(leasesByWorkflowId.values())
                .filter(lease -> clusterId.equals(lease.getClusterId()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<WorkflowOwnershipLease> findByOwnerRegion(String region) {
        Objects.requireNonNull(region, "region must not be null");
        return Flux.fromIterable(leasesByWorkflowId.values())
                .filter(lease -> region.equals(lease.getOwnerRegion()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Optional<WorkflowOwnershipLease>> tryAcquire(String workflowId,
                                                              String ownerId,
                                                              int partitionId,
                                                              Duration leaseDuration,
                                                              long fencingToken) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");

        return Mono.fromCallable(() -> {
            synchronized (leasesByWorkflowId) {
                WorkflowOwnershipLease existing = leasesByWorkflowId.get(workflowId);

                // Check if lease is available
                if (existing != null && existing.isValid()) {
                    log.debug("Cannot acquire lease for {}: already owned by {}",
                            workflowId, existing.getOwnerId());
                    return Optional.<WorkflowOwnershipLease>empty();
                }

                // Create new lease
                WorkflowOwnershipLease newLease;
                if (existing != null) {
                    // Re-acquire after previous owner
                    newLease = WorkflowOwnershipLease.reacquire(
                            workflowId, ownerId, partitionId, leaseDuration, existing, "Previous lease expired/released");
                } else {
                    // Fresh acquisition
                    newLease = WorkflowOwnershipLease.acquire(
                            workflowId, ownerId, partitionId, leaseDuration, fencingToken);
                }

                leasesByWorkflowId.put(workflowId, newLease);
                leaseIdIndex.put(newLease.getLeaseId(), workflowId);

                log.debug("Acquired lease for workflow {}: owner={}, expires={}",
                        workflowId, ownerId, newLease.getLeaseExpiresAt());
                return Optional.of(newLease);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Optional<WorkflowOwnershipLease>> tryRenew(String workflowId,
                                                            String ownerId,
                                                            long expectedVersion,
                                                            Duration extensionDuration) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(extensionDuration, "extensionDuration must not be null");

        return Mono.fromCallable(() -> {
            synchronized (leasesByWorkflowId) {
                WorkflowOwnershipLease existing = leasesByWorkflowId.get(workflowId);

                if (existing == null) {
                    log.debug("Cannot renew lease for {}: not found", workflowId);
                    return Optional.<WorkflowOwnershipLease>empty();
                }

                if (!ownerId.equals(existing.getOwnerId())) {
                    log.debug("Cannot renew lease for {}: owned by different node {}",
                            workflowId, existing.getOwnerId());
                    return Optional.<WorkflowOwnershipLease>empty();
                }

                if (existing.getVersion() != expectedVersion) {
                    log.debug("Cannot renew lease for {}: version mismatch (expected {}, actual {})",
                            workflowId, expectedVersion, existing.getVersion());
                    return Optional.<WorkflowOwnershipLease>empty();
                }

                if (!existing.isRenewable()) {
                    log.debug("Cannot renew lease for {}: not renewable (state={})",
                            workflowId, existing.getState());
                    return Optional.<WorkflowOwnershipLease>empty();
                }

                WorkflowOwnershipLease renewed = existing.renew(extensionDuration);
                leasesByWorkflowId.put(workflowId, renewed);

                log.debug("Renewed lease for workflow {}: newExpiration={}",
                        workflowId, renewed.getLeaseExpiresAt());
                return Optional.of(renewed);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> tryRelease(String workflowId, String ownerId, String reason) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");

        return Mono.fromCallable(() -> {
            synchronized (leasesByWorkflowId) {
                WorkflowOwnershipLease existing = leasesByWorkflowId.get(workflowId);

                if (existing == null || !ownerId.equals(existing.getOwnerId())) {
                    return false;
                }

                WorkflowOwnershipLease released = existing.release(reason);
                leasesByWorkflowId.put(workflowId, released);

                log.debug("Released lease for workflow {}: reason={}", workflowId, reason);
                return true;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Optional<WorkflowOwnershipLease>> tryTransfer(String workflowId,
                                                               String fromOwnerId,
                                                               String toOwnerId,
                                                               String reason) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Objects.requireNonNull(fromOwnerId, "fromOwnerId must not be null");
        Objects.requireNonNull(toOwnerId, "toOwnerId must not be null");

        return Mono.fromCallable(() -> {
            synchronized (leasesByWorkflowId) {
                WorkflowOwnershipLease existing = leasesByWorkflowId.get(workflowId);

                if (existing == null || !fromOwnerId.equals(existing.getOwnerId())) {
                    return Optional.<WorkflowOwnershipLease>empty();
                }

                WorkflowOwnershipLease transferred = WorkflowOwnershipLease.reacquire(
                        workflowId, toOwnerId, existing.getPartitionId(),
                        existing.getLeaseDuration(), existing, reason);

                leasesByWorkflowId.put(workflowId, transferred);
                leaseIdIndex.put(transferred.getLeaseId(), workflowId);
                leaseIdIndex.remove(existing.getLeaseId());

                log.debug("Transferred lease for workflow {} from {} to {}: reason={}",
                        workflowId, fromOwnerId, toOwnerId, reason);
                return Optional.of(transferred);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Integer> releaseAllByOwner(String ownerId, String reason) {
        return findByOwnerId(ownerId)
                .flatMap(lease -> tryRelease(lease.getWorkflowId(), ownerId, reason)
                        .map(released -> released ? 1 : 0))
                .reduce(0, Integer::sum);
    }

    @Override
    public Mono<Integer> revokeAllByOwner(String failedOwnerId, String reason) {
        return Mono.fromCallable(() -> {
            int revoked = 0;
            synchronized (leasesByWorkflowId) {
                for (Map.Entry<String, WorkflowOwnershipLease> entry : leasesByWorkflowId.entrySet()) {
                    WorkflowOwnershipLease lease = entry.getValue();
                    if (failedOwnerId.equals(lease.getOwnerId()) && lease.isValid()) {
                        WorkflowOwnershipLease revokedLease = lease.revoke(reason);
                        leasesByWorkflowId.put(entry.getKey(), revokedLease);
                        revoked++;
                    }
                }
            }
            log.info("Revoked {} leases from owner {}: reason={}", revoked, failedOwnerId, reason);
            return revoked;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Integer> expireAllStale() {
        return Mono.fromCallable(() -> {
            int expired = 0;
            synchronized (leasesByWorkflowId) {
                for (Map.Entry<String, WorkflowOwnershipLease> entry : leasesByWorkflowId.entrySet()) {
                    WorkflowOwnershipLease lease = entry.getValue();
                    if (lease.getState() == WorkflowOwnershipLease.LeaseState.ACTIVE && lease.isExpired()) {
                        WorkflowOwnershipLease expiredLease = lease.expire();
                        leasesByWorkflowId.put(entry.getKey(), expiredLease);
                        expired++;
                    }
                }
            }
            if (expired > 0) {
                log.info("Expired {} stale leases", expired);
            }
            return expired;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Integer> deleteAllBefore(Instant before) {
        return Mono.fromCallable(() -> {
            int deleted = 0;
            Iterator<Map.Entry<String, WorkflowOwnershipLease>> iterator =
                    leasesByWorkflowId.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<String, WorkflowOwnershipLease> entry = iterator.next();
                WorkflowOwnershipLease lease = entry.getValue();
                if (lease.getLeaseAcquiredAt() != null && lease.getLeaseAcquiredAt().isBefore(before)) {
                    leaseIdIndex.remove(lease.getLeaseId());
                    iterator.remove();
                    deleted++;
                }
            }
            log.info("Deleted {} leases acquired before {}", deleted, before);
            return deleted;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Integer> deleteAllTerminal() {
        return Mono.fromCallable(() -> {
            int deleted = 0;
            Iterator<Map.Entry<String, WorkflowOwnershipLease>> iterator =
                    leasesByWorkflowId.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<String, WorkflowOwnershipLease> entry = iterator.next();
                WorkflowOwnershipLease lease = entry.getValue();
                if (lease.getState() == WorkflowOwnershipLease.LeaseState.RELEASED ||
                    lease.getState() == WorkflowOwnershipLease.LeaseState.EXPIRED ||
                    lease.getState() == WorkflowOwnershipLease.LeaseState.REVOKED) {
                    leaseIdIndex.remove(lease.getLeaseId());
                    iterator.remove();
                    deleted++;
                }
            }
            log.info("Deleted {} terminal leases", deleted);
            return deleted;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Long> countByOwnerId(String ownerId) {
        return Mono.fromCallable(() -> leasesByWorkflowId.values().stream()
                .filter(lease -> ownerId.equals(lease.getOwnerId()))
                .count());
    }

    @Override
    public Mono<Long> countByPartitionId(int partitionId) {
        return Mono.fromCallable(() -> leasesByWorkflowId.values().stream()
                .filter(lease -> lease.getPartitionId() == partitionId)
                .count());
    }

    @Override
    public Mono<Long> countByState(WorkflowOwnershipLease.LeaseState state) {
        return Mono.fromCallable(() -> leasesByWorkflowId.values().stream()
                .filter(lease -> lease.getState() == state)
                .count());
    }

    @Override
    public Mono<Long> countActive() {
        return Mono.fromCallable(() -> leasesByWorkflowId.values().stream()
                .filter(WorkflowOwnershipLease::isValid)
                .count());
    }

    @Override
    public Mono<Long> getMaxFencingToken() {
        return Mono.fromCallable(() -> leasesByWorkflowId.values().stream()
                .mapToLong(WorkflowOwnershipLease::getFencingToken)
                .max()
                .orElse(0L));
    }

    @Override
    public Mono<Long> generateFencingToken() {
        return Mono.just(fencingTokenGenerator.incrementAndGet());
    }
}
