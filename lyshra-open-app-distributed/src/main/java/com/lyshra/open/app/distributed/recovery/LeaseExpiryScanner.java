package com.lyshra.open.app.distributed.recovery;

import com.lyshra.open.app.distributed.cluster.IClusterCoordinator;
import com.lyshra.open.app.distributed.membership.INodeMembershipService;
import com.lyshra.open.app.distributed.ownership.IWorkflowOwnershipRepository;
import com.lyshra.open.app.distributed.ownership.WorkflowOwnershipLease;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Periodic scanner that detects expired ownership leases.
 *
 * This component runs a background task that:
 * 1. Periodically scans the ownership repository for expired leases
 * 2. Identifies nodes that appear to have failed
 * 3. Notifies listeners when workflows become orphaned
 * 4. Tracks metrics for monitoring and alerting
 *
 * The scanner coordinates with the membership manager to distinguish between:
 * - Lease expiry due to genuine node failure
 * - Lease expiry due to slow renewal (node still alive)
 * - Network partitions
 *
 * Thread Safety: This class is thread-safe.
 */
@Slf4j
public class LeaseExpiryScanner implements ILeaseExpiryDetector {

    private static final Duration DEFAULT_SCAN_INTERVAL = Duration.ofSeconds(30);
    private static final Duration DEFAULT_EXPIRY_GRACE_PERIOD = Duration.ofSeconds(10);
    private static final Duration NODE_FAILURE_DETECTION_THRESHOLD = Duration.ofMinutes(2);

    private final IWorkflowOwnershipRepository repository;
    private final IClusterCoordinator clusterCoordinator;
    private final INodeMembershipService membershipService;

    private final List<ILeaseExpiryListener> listeners;
    private final ScannerMetrics metrics;
    private final AtomicBoolean running;
    private final AtomicReference<Duration> scanInterval;
    private final Duration expiryGracePeriod;

    private volatile Disposable scanTask;

    @Builder
    public LeaseExpiryScanner(IWorkflowOwnershipRepository repository,
                               IClusterCoordinator clusterCoordinator,
                               INodeMembershipService membershipService,
                               Duration scanInterval,
                               Duration expiryGracePeriod) {
        this.repository = Objects.requireNonNull(repository);
        this.clusterCoordinator = Objects.requireNonNull(clusterCoordinator);
        this.membershipService = membershipService; // Can be null for simpler deployments

        this.listeners = new CopyOnWriteArrayList<>();
        this.metrics = new ScannerMetrics();
        this.running = new AtomicBoolean(false);
        this.scanInterval = new AtomicReference<>(
                scanInterval != null ? scanInterval : DEFAULT_SCAN_INTERVAL);
        this.expiryGracePeriod = expiryGracePeriod != null ?
                expiryGracePeriod : DEFAULT_EXPIRY_GRACE_PERIOD;
    }

    // ========== Lifecycle ==========

    @Override
    public Mono<Void> start() {
        return Mono.fromRunnable(() -> {
            if (!running.compareAndSet(false, true)) {
                log.warn("Lease expiry scanner already running");
                return;
            }

            log.info("Starting lease expiry scanner with interval {}", scanInterval.get());

            // Start periodic scan
            scanTask = Flux.interval(scanInterval.get())
                    .flatMap(tick -> performScan())
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(
                            result -> {
                                if (result.expiredCount > 0) {
                                    log.info("Expiry scan completed: {} expired leases detected",
                                            result.expiredCount);
                                }
                            },
                            error -> log.error("Error during expiry scan", error)
                    );
        });
    }

    @Override
    public Mono<Void> stop() {
        return Mono.fromRunnable(() -> {
            if (!running.compareAndSet(true, false)) {
                return;
            }

            log.info("Stopping lease expiry scanner");

            if (scanTask != null) {
                scanTask.dispose();
                scanTask = null;
            }
        });
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    // ========== Detection ==========

    @Override
    public Flux<ExpiredLease> scanForExpiredLeases() {
        Instant now = Instant.now();
        Instant expiryThreshold = now.minus(expiryGracePeriod);

        return repository.findAllExpired()
                .filter(lease -> lease.getLeaseExpiresAt().isBefore(expiryThreshold))
                .map(this::toExpiredLease);
    }

    @Override
    public Flux<ExpiredLease> scanForExpiringLeases(Duration window) {
        return repository.findExpiringWithin(window)
                .map(this::toExpiredLease);
    }

    @Override
    public Mono<Boolean> isLeaseExpired(String workflowExecutionKey) {
        return repository.findByWorkflowId(workflowExecutionKey)
                .map(opt -> opt.map(WorkflowOwnershipLease::isExpired).orElse(true));
    }

    @Override
    public Mono<Instant> getLeaseExpiration(String workflowExecutionKey) {
        return repository.findByWorkflowId(workflowExecutionKey)
                .mapNotNull(opt -> opt.map(WorkflowOwnershipLease::getLeaseExpiresAt).orElse(null));
    }

    // ========== Node Detection ==========

    @Override
    public Mono<Set<String>> detectFailedNodes() {
        return repository.findAllExpired()
                .map(WorkflowOwnershipLease::getOwnerId)
                .distinct()
                .filterWhen(nodeId -> isNodeFailed(nodeId))
                .collect(Collectors.toSet());
    }

    @Override
    public Mono<Boolean> isNodeFailed(String nodeId) {
        // If we have membership service, use it for accurate detection
        if (membershipService != null) {
            return Mono.just(!membershipService.isNodeActive(nodeId));
        }

        // Fall back to heuristic: all leases expired for > threshold
        return repository.findByOwnerId(nodeId)
                .all(lease -> {
                    if (!lease.isExpired()) {
                        return false;
                    }
                    Duration expiredFor = Duration.between(
                            lease.getLeaseExpiresAt(), Instant.now());
                    return expiredFor.compareTo(NODE_FAILURE_DETECTION_THRESHOLD) > 0;
                });
    }

    @Override
    public Flux<String> getWorkflowsOwnedByNode(String nodeId) {
        return repository.findByOwnerId(nodeId)
                .map(WorkflowOwnershipLease::getWorkflowId);
    }

    // ========== Listeners ==========

    @Override
    public void addExpiryListener(ILeaseExpiryListener listener) {
        Objects.requireNonNull(listener);
        listeners.add(listener);
    }

    @Override
    public void removeExpiryListener(ILeaseExpiryListener listener) {
        listeners.remove(listener);
    }

    // ========== Configuration ==========

    @Override
    public Duration getScanInterval() {
        return scanInterval.get();
    }

    @Override
    public void setScanInterval(Duration interval) {
        Objects.requireNonNull(interval);
        scanInterval.set(interval);

        // Restart scan task with new interval if running
        if (running.get()) {
            stop().then(start()).subscribe();
        }
    }

    // ========== Metrics ==========

    @Override
    public ExpiryDetectionMetrics getMetrics() {
        return metrics;
    }

    // ========== Private Methods ==========

    private Mono<ScanResult> performScan() {
        if (!running.get()) {
            return Mono.just(new ScanResult(0, 0));
        }

        Instant scanStart = Instant.now();
        metrics.recordScanStarted();

        return scanForExpiredLeases()
                .collectList()
                .flatMap(expiredList -> {
                    Set<ExpiredLease> expiredSet = new HashSet<>(expiredList);
                    int expiredCount = expiredSet.size();

                    if (expiredCount > 0) {
                        // Notify listeners
                        notifyLeasesExpired(expiredSet);

                        // Detect failed nodes
                        return detectFailedNodes()
                                .map(failedNodes -> {
                                    for (String nodeId : failedNodes) {
                                        Set<String> affectedWorkflows = expiredSet.stream()
                                                .filter(e -> e.ownerId().equals(nodeId))
                                                .map(ExpiredLease::workflowExecutionKey)
                                                .collect(Collectors.toSet());

                                        notifyNodeFailure(nodeId, affectedWorkflows);
                                        metrics.recordFailedNodeDetected();
                                    }

                                    Duration scanDuration = Duration.between(scanStart, Instant.now());
                                    metrics.recordScanCompleted(expiredCount, scanDuration);

                                    return new ScanResult(expiredCount, failedNodes.size());
                                });
                    }

                    Duration scanDuration = Duration.between(scanStart, Instant.now());
                    metrics.recordScanCompleted(0, scanDuration);
                    return Mono.just(new ScanResult(0, 0));
                })
                .onErrorResume(error -> {
                    log.error("Error during expiry scan", error);
                    metrics.recordScanError();
                    return Mono.just(new ScanResult(0, 0));
                });
    }

    private ExpiredLease toExpiredLease(WorkflowOwnershipLease lease) {
        ExpiryReason reason = determineExpiryReason(lease);
        return ExpiredLease.of(
                lease.getWorkflowId(),
                lease.getOwnerId(),
                lease.getPartitionId(),
                lease.getFencingToken(),
                lease.getLeaseAcquiredAt(),
                lease.getLeaseExpiresAt(),
                reason
        );
    }

    private ExpiryReason determineExpiryReason(WorkflowOwnershipLease lease) {
        // Use membership service if available
        if (membershipService != null) {
            boolean isAlive = membershipService.isNodeActive(lease.getOwnerId());
            if (!isAlive) {
                return ExpiryReason.NODE_FAILURE;
            }
        }

        // Check how long ago the lease expired
        Duration expiredFor = Duration.between(lease.getLeaseExpiresAt(), Instant.now());
        if (expiredFor.compareTo(NODE_FAILURE_DETECTION_THRESHOLD) > 0) {
            return ExpiryReason.NODE_FAILURE;
        }

        // Default to timeout
        return ExpiryReason.TIMEOUT;
    }

    private void notifyLeasesExpired(Set<ExpiredLease> expired) {
        for (ILeaseExpiryListener listener : listeners) {
            try {
                listener.onLeasesExpired(expired);
                for (ExpiredLease lease : expired) {
                    listener.onLeaseExpired(lease);
                }
            } catch (Exception e) {
                log.error("Error notifying expiry listener", e);
            }
        }
    }

    private void notifyNodeFailure(String nodeId, Set<String> affectedWorkflows) {
        for (ILeaseExpiryListener listener : listeners) {
            try {
                listener.onNodeFailureDetected(nodeId, affectedWorkflows);
            } catch (Exception e) {
                log.error("Error notifying listener of node failure", e);
            }
        }
    }

    // ========== Inner Classes ==========

    private record ScanResult(int expiredCount, int failedNodeCount) {}

    /**
     * Metrics implementation for the scanner.
     */
    @Getter
    public static class ScannerMetrics implements ExpiryDetectionMetrics {
        private final AtomicLong totalScans = new AtomicLong(0);
        private final AtomicLong expiredLeasesDetected = new AtomicLong(0);
        private final AtomicLong failedNodesDetected = new AtomicLong(0);
        private final AtomicLong totalScanTimeNanos = new AtomicLong(0);
        private final AtomicLong scanErrors = new AtomicLong(0);
        private volatile Instant lastScanTime;

        void recordScanStarted() {
            totalScans.incrementAndGet();
            lastScanTime = Instant.now();
        }

        void recordScanCompleted(int expiredCount, Duration duration) {
            expiredLeasesDetected.addAndGet(expiredCount);
            totalScanTimeNanos.addAndGet(duration.toNanos());
        }

        void recordFailedNodeDetected() {
            failedNodesDetected.incrementAndGet();
        }

        void recordScanError() {
            scanErrors.incrementAndGet();
        }

        @Override
        public long getTotalScans() {
            return totalScans.get();
        }

        @Override
        public long getExpiredLeasesDetected() {
            return expiredLeasesDetected.get();
        }

        @Override
        public long getFailedNodesDetected() {
            return failedNodesDetected.get();
        }

        @Override
        public Duration getAverageExpiryDetectionTime() {
            long scans = totalScans.get();
            if (scans == 0) return Duration.ZERO;
            return Duration.ofNanos(totalScanTimeNanos.get() / scans);
        }

        @Override
        public Instant getLastScanTime() {
            return lastScanTime;
        }

        public long getScanErrors() {
            return scanErrors.get();
        }
    }
}
