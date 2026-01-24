package com.lyshra.open.app.distributed.dispatcher;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Metrics for monitoring dispatcher performance and health.
 *
 * This class tracks various dispatch-related metrics including:
 * - Dispatch counts (success, failure, routed, rejected)
 * - Timing metrics (acquisition latency)
 * - Retry statistics
 * - Current state
 *
 * Thread Safety: This class is thread-safe.
 */
@Getter
@ToString
public final class DispatcherMetrics {

    // ========== Counters ==========

    private final LongAdder totalDispatchAttempts = new LongAdder();
    private final LongAdder dispatchedLocally = new LongAdder();
    private final LongAdder routedToOther = new LongAdder();
    private final LongAdder ownedByOther = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private final LongAdder totalRetries = new LongAdder();
    private final LongAdder successfulRetries = new LongAdder();
    private final LongAdder failedRetries = new LongAdder();
    private final LongAdder ownershipRenewals = new LongAdder();
    private final LongAdder ownershipReleases = new LongAdder();
    private final LongAdder fencingConflicts = new LongAdder();
    private final LongAdder timeouts = new LongAdder();

    // ========== Rejection Reason Counters ==========

    private final Map<WorkflowDispatchResult.RejectReason, LongAdder> rejectionsByReason =
            new ConcurrentHashMap<>();

    // ========== Timing Metrics ==========

    private final AtomicLong totalAcquisitionTimeNanos = new AtomicLong(0);
    private final AtomicLong minAcquisitionTimeNanos = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxAcquisitionTimeNanos = new AtomicLong(0);
    private final LongAdder acquisitionTimeCount = new LongAdder();

    // ========== State ==========

    private final AtomicLong currentlyDispatchedCount = new AtomicLong(0);
    private volatile Instant lastDispatchTime;
    private volatile Instant startTime;

    /**
     * Creates a new metrics instance.
     */
    public DispatcherMetrics() {
        this.startTime = Instant.now();
    }

    // ========== Recording Methods ==========

    /**
     * Records a dispatch attempt.
     */
    public void recordDispatchAttempt() {
        totalDispatchAttempts.increment();
        lastDispatchTime = Instant.now();
    }

    /**
     * Records a successful local dispatch.
     *
     * @param acquisitionTime time taken to acquire ownership
     */
    public void recordDispatchedLocally(Duration acquisitionTime) {
        dispatchedLocally.increment();
        currentlyDispatchedCount.incrementAndGet();
        recordAcquisitionTime(acquisitionTime);
    }

    /**
     * Records a dispatch routed to another node.
     */
    public void recordRoutedToOther() {
        routedToOther.increment();
    }

    /**
     * Records a dispatch blocked by another owner.
     */
    public void recordOwnedByOther() {
        ownedByOther.increment();
    }

    /**
     * Records a rejected dispatch.
     *
     * @param reason the rejection reason
     */
    public void recordRejected(WorkflowDispatchResult.RejectReason reason) {
        rejected.increment();
        rejectionsByReason.computeIfAbsent(reason, k -> new LongAdder()).increment();

        if (reason == WorkflowDispatchResult.RejectReason.FENCING_CONFLICT) {
            fencingConflicts.increment();
        } else if (reason == WorkflowDispatchResult.RejectReason.ACQUISITION_TIMEOUT) {
            timeouts.increment();
        }
    }

    /**
     * Records a retry attempt.
     *
     * @param successful whether the retry succeeded
     */
    public void recordRetry(boolean successful) {
        totalRetries.increment();
        if (successful) {
            successfulRetries.increment();
        } else {
            failedRetries.increment();
        }
    }

    /**
     * Records an ownership renewal.
     */
    public void recordOwnershipRenewal() {
        ownershipRenewals.increment();
    }

    /**
     * Records an ownership release.
     */
    public void recordOwnershipRelease() {
        ownershipReleases.increment();
        currentlyDispatchedCount.decrementAndGet();
    }

    /**
     * Records acquisition time.
     *
     * @param duration the acquisition duration
     */
    public void recordAcquisitionTime(Duration duration) {
        if (duration == null) {
            return;
        }

        long nanos = duration.toNanos();
        totalAcquisitionTimeNanos.addAndGet(nanos);
        acquisitionTimeCount.increment();

        // Update min
        long currentMin;
        while (nanos < (currentMin = minAcquisitionTimeNanos.get())) {
            if (minAcquisitionTimeNanos.compareAndSet(currentMin, nanos)) {
                break;
            }
        }

        // Update max
        long currentMax;
        while (nanos > (currentMax = maxAcquisitionTimeNanos.get())) {
            if (maxAcquisitionTimeNanos.compareAndSet(currentMax, nanos)) {
                break;
            }
        }
    }

    // ========== Query Methods ==========

    /**
     * Gets the total number of dispatch attempts.
     */
    public long getTotalDispatchAttempts() {
        return totalDispatchAttempts.sum();
    }

    /**
     * Gets the number of successful local dispatches.
     */
    public long getDispatchedLocallyCount() {
        return dispatchedLocally.sum();
    }

    /**
     * Gets the number of dispatches routed to other nodes.
     */
    public long getRoutedToOtherCount() {
        return routedToOther.sum();
    }

    /**
     * Gets the number of dispatches blocked by other owners.
     */
    public long getOwnedByOtherCount() {
        return ownedByOther.sum();
    }

    /**
     * Gets the number of rejected dispatches.
     */
    public long getRejectedCount() {
        return rejected.sum();
    }

    /**
     * Gets the total number of retry attempts.
     */
    public long getTotalRetries() {
        return totalRetries.sum();
    }

    /**
     * Gets the number of successful retries.
     */
    public long getSuccessfulRetries() {
        return successfulRetries.sum();
    }

    /**
     * Gets the number of failed retries.
     */
    public long getFailedRetries() {
        return failedRetries.sum();
    }

    /**
     * Gets the number of ownership renewals.
     */
    public long getOwnershipRenewals() {
        return ownershipRenewals.sum();
    }

    /**
     * Gets the number of ownership releases.
     */
    public long getOwnershipReleases() {
        return ownershipReleases.sum();
    }

    /**
     * Gets the number of fencing conflicts.
     */
    public long getFencingConflicts() {
        return fencingConflicts.sum();
    }

    /**
     * Gets the number of timeouts.
     */
    public long getTimeouts() {
        return timeouts.sum();
    }

    /**
     * Gets the count of rejections for a specific reason.
     */
    public long getRejectionCount(WorkflowDispatchResult.RejectReason reason) {
        LongAdder adder = rejectionsByReason.get(reason);
        return adder != null ? adder.sum() : 0;
    }

    /**
     * Gets the number of currently dispatched workflows.
     */
    public long getCurrentlyDispatchedCount() {
        return currentlyDispatchedCount.get();
    }

    /**
     * Gets the success rate (dispatched locally / total attempts).
     */
    public double getSuccessRate() {
        long total = getTotalDispatchAttempts();
        if (total == 0) {
            return 0.0;
        }
        return (double) getDispatchedLocallyCount() / total;
    }

    /**
     * Gets the retry success rate.
     */
    public double getRetrySuccessRate() {
        long total = getTotalRetries();
        if (total == 0) {
            return 0.0;
        }
        return (double) getSuccessfulRetries() / total;
    }

    /**
     * Gets the average acquisition time.
     */
    public Duration getAverageAcquisitionTime() {
        long count = acquisitionTimeCount.sum();
        if (count == 0) {
            return Duration.ZERO;
        }
        return Duration.ofNanos(totalAcquisitionTimeNanos.get() / count);
    }

    /**
     * Gets the minimum acquisition time.
     */
    public Duration getMinAcquisitionTime() {
        long min = minAcquisitionTimeNanos.get();
        return min == Long.MAX_VALUE ? Duration.ZERO : Duration.ofNanos(min);
    }

    /**
     * Gets the maximum acquisition time.
     */
    public Duration getMaxAcquisitionTime() {
        return Duration.ofNanos(maxAcquisitionTimeNanos.get());
    }

    /**
     * Gets the last dispatch time.
     */
    public Instant getLastDispatchTime() {
        return lastDispatchTime;
    }

    /**
     * Gets the uptime since metrics were started.
     */
    public Duration getUptime() {
        return Duration.between(startTime, Instant.now());
    }

    /**
     * Creates a snapshot of current metrics.
     */
    public MetricsSnapshot snapshot() {
        return MetricsSnapshot.builder()
                .totalDispatchAttempts(getTotalDispatchAttempts())
                .dispatchedLocally(getDispatchedLocallyCount())
                .routedToOther(getRoutedToOtherCount())
                .ownedByOther(getOwnedByOtherCount())
                .rejected(getRejectedCount())
                .totalRetries(getTotalRetries())
                .successfulRetries(getSuccessfulRetries())
                .failedRetries(getFailedRetries())
                .ownershipRenewals(getOwnershipRenewals())
                .ownershipReleases(getOwnershipReleases())
                .fencingConflicts(getFencingConflicts())
                .timeouts(getTimeouts())
                .currentlyDispatched(getCurrentlyDispatchedCount())
                .successRate(getSuccessRate())
                .retrySuccessRate(getRetrySuccessRate())
                .averageAcquisitionTime(getAverageAcquisitionTime())
                .minAcquisitionTime(getMinAcquisitionTime())
                .maxAcquisitionTime(getMaxAcquisitionTime())
                .lastDispatchTime(lastDispatchTime)
                .uptime(getUptime())
                .snapshotTime(Instant.now())
                .build();
    }

    /**
     * Resets all metrics to zero.
     */
    public void reset() {
        totalDispatchAttempts.reset();
        dispatchedLocally.reset();
        routedToOther.reset();
        ownedByOther.reset();
        rejected.reset();
        totalRetries.reset();
        successfulRetries.reset();
        failedRetries.reset();
        ownershipRenewals.reset();
        ownershipReleases.reset();
        fencingConflicts.reset();
        timeouts.reset();
        rejectionsByReason.clear();
        totalAcquisitionTimeNanos.set(0);
        minAcquisitionTimeNanos.set(Long.MAX_VALUE);
        maxAcquisitionTimeNanos.set(0);
        acquisitionTimeCount.reset();
        currentlyDispatchedCount.set(0);
        lastDispatchTime = null;
        startTime = Instant.now();
    }

    /**
     * Immutable snapshot of metrics at a point in time.
     */
    @Getter
    @Builder
    @ToString
    public static final class MetricsSnapshot {
        private final long totalDispatchAttempts;
        private final long dispatchedLocally;
        private final long routedToOther;
        private final long ownedByOther;
        private final long rejected;
        private final long totalRetries;
        private final long successfulRetries;
        private final long failedRetries;
        private final long ownershipRenewals;
        private final long ownershipReleases;
        private final long fencingConflicts;
        private final long timeouts;
        private final long currentlyDispatched;
        private final double successRate;
        private final double retrySuccessRate;
        private final Duration averageAcquisitionTime;
        private final Duration minAcquisitionTime;
        private final Duration maxAcquisitionTime;
        private final Instant lastDispatchTime;
        private final Duration uptime;
        private final Instant snapshotTime;
    }
}
