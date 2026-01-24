package com.lyshra.open.app.distributed.recovery;

import com.lyshra.open.app.distributed.recovery.ILeaseExpiryDetector.ExpiredLease;
import com.lyshra.open.app.distributed.recovery.ILeaseExpiryDetector.ExpiryReason;
import com.lyshra.open.app.distributed.sharding.IPartitionManager;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Registry for tracking orphaned workflows that are eligible for recovery.
 *
 * This component maintains a list of workflows whose ownership has expired
 * and are available to be claimed by other nodes. It provides:
 *
 * 1. Registration of orphaned workflows when their leases expire
 * 2. Partition-aware querying for local node recovery
 * 3. Claim tracking to prevent duplicate recovery attempts
 * 4. Expiry of old entries to prevent memory leaks
 * 5. Priority ordering based on expiry age and importance
 *
 * Thread Safety: This class is thread-safe.
 */
@Slf4j
public class OrphanedWorkflowRegistry implements ILeaseExpiryDetector.ILeaseExpiryListener {

    private static final Duration DEFAULT_ENTRY_TTL = Duration.ofHours(1);
    private static final Duration DEFAULT_CLAIM_TIMEOUT = Duration.ofMinutes(5);

    private final IPartitionManager partitionManager;
    private final Duration entryTTL;
    private final Duration claimTimeout;

    private final ConcurrentHashMap<String, OrphanEntry> orphanedWorkflows;
    private final ConcurrentHashMap<String, ClaimRecord> activeClaimss;
    private final List<IOrphanedWorkflowListener> listeners;
    private final RegistryMetrics metrics;
    private final AtomicBoolean active;

    @Builder
    public OrphanedWorkflowRegistry(IPartitionManager partitionManager,
                                     Duration entryTTL,
                                     Duration claimTimeout) {
        this.partitionManager = Objects.requireNonNull(partitionManager);
        this.entryTTL = entryTTL != null ? entryTTL : DEFAULT_ENTRY_TTL;
        this.claimTimeout = claimTimeout != null ? claimTimeout : DEFAULT_CLAIM_TIMEOUT;

        this.orphanedWorkflows = new ConcurrentHashMap<>();
        this.activeClaimss = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.metrics = new RegistryMetrics();
        this.active = new AtomicBoolean(true);
    }

    // ========== Lifecycle ==========

    /**
     * Shuts down the registry.
     *
     * @return Mono that completes when shutdown
     */
    public Mono<Void> shutdown() {
        return Mono.fromRunnable(() -> {
            active.set(false);
            orphanedWorkflows.clear();
            activeClaimss.clear();
        });
    }

    /**
     * Checks if the registry is active.
     */
    public boolean isActive() {
        return active.get();
    }

    // ========== ILeaseExpiryListener Implementation ==========

    @Override
    public void onLeaseExpired(ExpiredLease expired) {
        registerOrphan(expired);
    }

    @Override
    public void onLeasesExpired(Set<ExpiredLease> expired) {
        for (ExpiredLease lease : expired) {
            registerOrphan(lease);
        }
    }

    @Override
    public void onNodeFailureDetected(String nodeId, Set<String> affectedWorkflows) {
        log.info("Node failure detected: {} with {} affected workflows", nodeId, affectedWorkflows.size());
        metrics.recordNodeFailure();
    }

    // ========== Registration ==========

    /**
     * Registers an orphaned workflow as claimable.
     *
     * @param expired the expired lease information
     */
    public void registerOrphan(ExpiredLease expired) {
        if (!active.get()) {
            return;
        }

        String workflowKey = expired.workflowExecutionKey();

        // Check if already registered or claimed
        if (orphanedWorkflows.containsKey(workflowKey)) {
            log.debug("Workflow {} already registered as orphan", workflowKey);
            return;
        }

        OrphanEntry entry = OrphanEntry.builder()
                .workflowExecutionKey(workflowKey)
                .previousOwnerId(expired.ownerId())
                .partitionId(expired.partitionId())
                .fencingToken(expired.fencingToken())
                .expiredAt(expired.expiredAt())
                .registeredAt(Instant.now())
                .expiryReason(expired.reason())
                .priority(calculatePriority(expired))
                .build();

        orphanedWorkflows.put(workflowKey, entry);
        metrics.recordOrphanRegistered();

        log.info("Registered orphaned workflow: {} from node {} (priority: {})",
                workflowKey, expired.ownerId(), entry.priority);

        notifyOrphanRegistered(entry);
    }

    /**
     * Registers an orphan from basic information.
     */
    public void registerOrphan(String workflowKey, String previousOwnerId,
                                int partitionId, long fencingToken, Instant expiredAt) {
        ExpiredLease expired = ExpiredLease.of(workflowKey, previousOwnerId, partitionId,
                fencingToken, null, expiredAt, ExpiryReason.UNKNOWN);
        registerOrphan(expired);
    }

    // ========== Query ==========

    /**
     * Gets all orphaned workflows.
     *
     * @return Flux of orphan entries
     */
    public Flux<OrphanEntry> getAllOrphans() {
        cleanupExpiredEntries();
        return Flux.fromIterable(orphanedWorkflows.values())
                .filter(entry -> !isClaimActive(entry.workflowExecutionKey));
    }

    /**
     * Gets orphaned workflows that this node should handle (by partition).
     *
     * @return Flux of orphan entries for local partitions
     */
    public Flux<OrphanEntry> getLocalOrphans() {
        cleanupExpiredEntries();
        return Flux.fromIterable(orphanedWorkflows.values())
                .filter(entry -> !isClaimActive(entry.workflowExecutionKey))
                .filter(entry -> partitionManager.shouldHandleWorkflow(entry.workflowExecutionKey));
    }

    /**
     * Gets orphaned workflows ordered by priority.
     *
     * @param limit maximum number to return
     * @return Flux of orphan entries sorted by priority
     */
    public Flux<OrphanEntry> getOrphansByPriority(int limit) {
        cleanupExpiredEntries();
        return Flux.fromIterable(orphanedWorkflows.values())
                .filter(entry -> !isClaimActive(entry.workflowExecutionKey))
                .sort(Comparator.comparingInt(OrphanEntry::getPriority).reversed())
                .take(limit);
    }

    /**
     * Gets orphaned workflows from a specific failed node.
     *
     * @param nodeId the failed node ID
     * @return Flux of orphan entries
     */
    public Flux<OrphanEntry> getOrphansByNode(String nodeId) {
        return Flux.fromIterable(orphanedWorkflows.values())
                .filter(entry -> entry.previousOwnerId.equals(nodeId))
                .filter(entry -> !isClaimActive(entry.workflowExecutionKey));
    }

    /**
     * Checks if a workflow is registered as orphaned.
     *
     * @param workflowKey the workflow execution key
     * @return true if orphaned
     */
    public boolean isOrphaned(String workflowKey) {
        return orphanedWorkflows.containsKey(workflowKey);
    }

    /**
     * Gets orphan information for a specific workflow.
     *
     * @param workflowKey the workflow execution key
     * @return Optional containing the orphan entry
     */
    public Optional<OrphanEntry> getOrphanEntry(String workflowKey) {
        return Optional.ofNullable(orphanedWorkflows.get(workflowKey));
    }

    /**
     * Gets the count of orphaned workflows.
     *
     * @return the orphan count
     */
    public int getOrphanCount() {
        return orphanedWorkflows.size();
    }

    /**
     * Gets the count of orphans for local partitions.
     *
     * @return local orphan count
     */
    public int getLocalOrphanCount() {
        return (int) orphanedWorkflows.values().stream()
                .filter(entry -> partitionManager.shouldHandleWorkflow(entry.workflowExecutionKey))
                .count();
    }

    // ========== Claiming ==========

    /**
     * Attempts to claim an orphaned workflow for recovery.
     *
     * @param workflowKey the workflow execution key
     * @param claimerId the claiming node ID
     * @return Mono containing the claim result
     */
    public Mono<ClaimResult> tryClaim(String workflowKey, String claimerId) {
        return Mono.fromCallable(() -> {
            OrphanEntry entry = orphanedWorkflows.get(workflowKey);
            if (entry == null) {
                return ClaimResult.notFound(workflowKey);
            }

            // Check if already claimed
            ClaimRecord existingClaim = activeClaimss.get(workflowKey);
            if (existingClaim != null && !existingClaim.isExpired()) {
                if (existingClaim.claimerId.equals(claimerId)) {
                    return ClaimResult.alreadyClaimed(workflowKey, claimerId, existingClaim.claimedAt);
                }
                return ClaimResult.claimedByOther(workflowKey, existingClaim.claimerId);
            }

            // Create claim record
            ClaimRecord claim = new ClaimRecord(workflowKey, claimerId, Instant.now(),
                    Instant.now().plus(claimTimeout));
            activeClaimss.put(workflowKey, claim);
            metrics.recordClaimAttempt();

            log.info("Workflow {} claimed by node {} for recovery", workflowKey, claimerId);

            return ClaimResult.claimed(workflowKey, claimerId, entry);
        });
    }

    /**
     * Confirms that a claim was successfully recovered.
     *
     * @param workflowKey the workflow execution key
     * @param claimerId the claimer node ID
     * @return Mono containing true if confirmed
     */
    public Mono<Boolean> confirmRecovery(String workflowKey, String claimerId) {
        return Mono.fromCallable(() -> {
            ClaimRecord claim = activeClaimss.get(workflowKey);
            if (claim == null || !claim.claimerId.equals(claimerId)) {
                return false;
            }

            // Remove from orphan registry and claims
            orphanedWorkflows.remove(workflowKey);
            activeClaimss.remove(workflowKey);
            metrics.recordRecoveryConfirmed();

            log.info("Recovery confirmed for workflow {} by node {}", workflowKey, claimerId);
            notifyRecoveryConfirmed(workflowKey, claimerId);

            return true;
        });
    }

    /**
     * Releases a claim without recovery (e.g., recovery failed).
     *
     * @param workflowKey the workflow execution key
     * @param claimerId the claimer node ID
     * @param reason the reason for release
     * @return Mono containing true if released
     */
    public Mono<Boolean> releaseClaim(String workflowKey, String claimerId, String reason) {
        return Mono.fromCallable(() -> {
            ClaimRecord claim = activeClaimss.get(workflowKey);
            if (claim == null || !claim.claimerId.equals(claimerId)) {
                return false;
            }

            activeClaimss.remove(workflowKey);
            metrics.recordClaimReleased();

            log.info("Claim released for workflow {} by node {}: {}",
                    workflowKey, claimerId, reason);

            return true;
        });
    }

    // ========== Removal ==========

    /**
     * Removes a workflow from the orphan registry.
     *
     * @param workflowKey the workflow execution key
     * @return true if removed
     */
    public boolean removeOrphan(String workflowKey) {
        OrphanEntry removed = orphanedWorkflows.remove(workflowKey);
        activeClaimss.remove(workflowKey);
        if (removed != null) {
            metrics.recordOrphanRemoved();
            return true;
        }
        return false;
    }

    /**
     * Removes all orphans from a specific node.
     *
     * @param nodeId the node ID
     * @return number removed
     */
    public int removeOrphansByNode(String nodeId) {
        List<String> toRemove = orphanedWorkflows.values().stream()
                .filter(entry -> entry.previousOwnerId.equals(nodeId))
                .map(OrphanEntry::getWorkflowExecutionKey)
                .collect(Collectors.toList());

        for (String key : toRemove) {
            removeOrphan(key);
        }

        return toRemove.size();
    }

    // ========== Listeners ==========

    /**
     * Adds a listener for orphan events.
     *
     * @param listener the listener
     */
    public void addListener(IOrphanedWorkflowListener listener) {
        Objects.requireNonNull(listener);
        listeners.add(listener);
    }

    /**
     * Removes an orphan event listener.
     *
     * @param listener the listener
     */
    public void removeListener(IOrphanedWorkflowListener listener) {
        listeners.remove(listener);
    }

    // ========== Metrics ==========

    /**
     * Gets the registry metrics.
     */
    public RegistryMetrics getMetrics() {
        return metrics;
    }

    // ========== Private Methods ==========

    private boolean isClaimActive(String workflowKey) {
        ClaimRecord claim = activeClaimss.get(workflowKey);
        return claim != null && !claim.isExpired();
    }

    private int calculatePriority(ExpiredLease expired) {
        // Higher priority for:
        // 1. Longer expiry time (more urgent)
        // 2. Node failure vs timeout
        // 3. Lower partition ID (arbitrary but consistent)

        int priority = 0;

        // Expiry age factor (max 50 points)
        Duration expiredFor = expired.expiredFor();
        if (expiredFor != null) {
            long minutes = expiredFor.toMinutes();
            priority += Math.min(50, minutes * 5);
        }

        // Expiry reason factor
        if (expired.reason() == ExpiryReason.NODE_FAILURE) {
            priority += 30;
        } else if (expired.reason() == ExpiryReason.NODE_UNRESPONSIVE) {
            priority += 20;
        }

        return priority;
    }

    private void cleanupExpiredEntries() {
        Instant cutoff = Instant.now().minus(entryTTL);

        orphanedWorkflows.entrySet().removeIf(entry -> {
            if (entry.getValue().registeredAt.isBefore(cutoff)) {
                log.debug("Removing expired orphan entry: {}", entry.getKey());
                metrics.recordOrphanExpired();
                return true;
            }
            return false;
        });

        // Cleanup expired claims
        activeClaimss.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private void notifyOrphanRegistered(OrphanEntry entry) {
        for (IOrphanedWorkflowListener listener : listeners) {
            try {
                listener.onOrphanRegistered(entry);
            } catch (Exception e) {
                log.error("Error notifying orphan listener", e);
            }
        }
    }

    private void notifyRecoveryConfirmed(String workflowKey, String newOwnerId) {
        for (IOrphanedWorkflowListener listener : listeners) {
            try {
                listener.onRecoveryConfirmed(workflowKey, newOwnerId);
            } catch (Exception e) {
                log.error("Error notifying recovery listener", e);
            }
        }
    }

    // ========== Inner Classes ==========

    /**
     * Represents an orphaned workflow entry.
     */
    @Getter
    @Builder
    @ToString
    public static class OrphanEntry {
        private final String workflowExecutionKey;
        private final String previousOwnerId;
        private final int partitionId;
        private final long fencingToken;
        private final Instant expiredAt;
        private final Instant registeredAt;
        private final ExpiryReason expiryReason;
        private final int priority;

        public Duration getTimeSinceExpiry() {
            return Duration.between(expiredAt, Instant.now());
        }

        public Duration getTimeSinceRegistration() {
            return Duration.between(registeredAt, Instant.now());
        }
    }

    /**
     * Result of a claim attempt.
     */
    @Getter
    @ToString
    public static class ClaimResult {
        public enum Status {
            CLAIMED, ALREADY_CLAIMED, CLAIMED_BY_OTHER, NOT_FOUND
        }

        private final String workflowExecutionKey;
        private final Status status;
        private final String claimerId;
        private final OrphanEntry entry;
        private final Instant claimedAt;

        private ClaimResult(String workflowKey, Status status, String claimerId,
                            OrphanEntry entry, Instant claimedAt) {
            this.workflowExecutionKey = workflowKey;
            this.status = status;
            this.claimerId = claimerId;
            this.entry = entry;
            this.claimedAt = claimedAt;
        }

        public boolean isClaimed() {
            return status == Status.CLAIMED || status == Status.ALREADY_CLAIMED;
        }

        public static ClaimResult claimed(String workflowKey, String claimerId, OrphanEntry entry) {
            return new ClaimResult(workflowKey, Status.CLAIMED, claimerId, entry, Instant.now());
        }

        public static ClaimResult alreadyClaimed(String workflowKey, String claimerId, Instant claimedAt) {
            return new ClaimResult(workflowKey, Status.ALREADY_CLAIMED, claimerId, null, claimedAt);
        }

        public static ClaimResult claimedByOther(String workflowKey, String otherClaimerId) {
            return new ClaimResult(workflowKey, Status.CLAIMED_BY_OTHER, otherClaimerId, null, null);
        }

        public static ClaimResult notFound(String workflowKey) {
            return new ClaimResult(workflowKey, Status.NOT_FOUND, null, null, null);
        }
    }

    /**
     * Record of an active claim.
     */
    private record ClaimRecord(
            String workflowExecutionKey,
            String claimerId,
            Instant claimedAt,
            Instant expiresAt
    ) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /**
     * Listener for orphaned workflow events.
     */
    public interface IOrphanedWorkflowListener {
        void onOrphanRegistered(OrphanEntry entry);
        void onRecoveryConfirmed(String workflowKey, String newOwnerId);
    }

    /**
     * Metrics for the orphan registry.
     */
    @Getter
    public static class RegistryMetrics {
        private final AtomicLong orphansRegistered = new AtomicLong(0);
        private final AtomicLong orphansRemoved = new AtomicLong(0);
        private final AtomicLong orphansExpired = new AtomicLong(0);
        private final AtomicLong claimAttempts = new AtomicLong(0);
        private final AtomicLong claimsReleased = new AtomicLong(0);
        private final AtomicLong recoveriesConfirmed = new AtomicLong(0);
        private final AtomicLong nodeFailures = new AtomicLong(0);

        void recordOrphanRegistered() { orphansRegistered.incrementAndGet(); }
        void recordOrphanRemoved() { orphansRemoved.incrementAndGet(); }
        void recordOrphanExpired() { orphansExpired.incrementAndGet(); }
        void recordClaimAttempt() { claimAttempts.incrementAndGet(); }
        void recordClaimReleased() { claimsReleased.incrementAndGet(); }
        void recordRecoveryConfirmed() { recoveriesConfirmed.incrementAndGet(); }
        void recordNodeFailure() { nodeFailures.incrementAndGet(); }

        public long getOrphansRegistered() { return orphansRegistered.get(); }
        public long getOrphansRemoved() { return orphansRemoved.get(); }
        public long getOrphansExpired() { return orphansExpired.get(); }
        public long getClaimAttempts() { return claimAttempts.get(); }
        public long getClaimsReleased() { return claimsReleased.get(); }
        public long getRecoveriesConfirmed() { return recoveriesConfirmed.get(); }
        public long getNodeFailures() { return nodeFailures.get(); }
    }
}
