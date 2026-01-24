package com.lyshra.open.app.distributed.coordination;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Metrics for workflow ownership coordination operations.
 *
 * This class tracks operational statistics for monitoring and
 * performance analysis. It is designed for high-concurrency access.
 *
 * Thread Safety: This class uses atomic operations for thread-safe updates.
 */
@Getter
@ToString
public final class CoordinationMetrics {

    // ========== Counters ==========

    /**
     * Total number of ownership acquisition attempts.
     */
    private final LongAdder acquisitionAttempts = new LongAdder();

    /**
     * Number of successful ownership acquisitions.
     */
    private final LongAdder acquisitionSuccesses = new LongAdder();

    /**
     * Number of failed ownership acquisitions.
     */
    private final LongAdder acquisitionFailures = new LongAdder();

    /**
     * Total number of lease renewal attempts.
     */
    private final LongAdder renewalAttempts = new LongAdder();

    /**
     * Number of successful lease renewals.
     */
    private final LongAdder renewalSuccesses = new LongAdder();

    /**
     * Number of failed lease renewals.
     */
    private final LongAdder renewalFailures = new LongAdder();

    /**
     * Total number of ownership releases.
     */
    private final LongAdder releasesTotal = new LongAdder();

    /**
     * Number of graceful releases.
     */
    private final LongAdder gracefulReleases = new LongAdder();

    /**
     * Number of forced releases (expiry/revocation).
     */
    private final LongAdder forcedReleases = new LongAdder();

    /**
     * Total number of orphan recovery operations.
     */
    private final LongAdder orphanRecoveryAttempts = new LongAdder();

    /**
     * Number of workflows recovered from orphans.
     */
    private final LongAdder orphansRecovered = new LongAdder();

    /**
     * Number of fencing conflicts detected.
     */
    private final LongAdder fencingConflicts = new LongAdder();

    /**
     * Number of backend connection failures.
     */
    private final LongAdder backendConnectionFailures = new LongAdder();

    /**
     * Number of lock acquisition timeouts.
     */
    private final LongAdder lockTimeouts = new LongAdder();

    // ========== Gauges ==========

    /**
     * Current number of locally owned workflows.
     */
    private final AtomicLong currentOwnershipCount = new AtomicLong(0);

    /**
     * Current number of pending renewals.
     */
    private final AtomicLong pendingRenewals = new AtomicLong(0);

    /**
     * Maximum concurrent ownership count observed.
     */
    private final AtomicLong maxOwnershipCount = new AtomicLong(0);

    // ========== Timing ==========

    /**
     * Total time spent on acquisitions (nanoseconds).
     */
    private final LongAdder totalAcquisitionTimeNanos = new LongAdder();

    /**
     * Total time spent on renewals (nanoseconds).
     */
    private final LongAdder totalRenewalTimeNanos = new LongAdder();

    /**
     * Total time spent on recovery operations (nanoseconds).
     */
    private final LongAdder totalRecoveryTimeNanos = new LongAdder();

    /**
     * When metrics collection started.
     */
    private final Instant startedAt;

    /**
     * When metrics were last reset.
     */
    private volatile Instant lastResetAt;

    /**
     * Creates a new metrics instance.
     */
    public CoordinationMetrics() {
        this.startedAt = Instant.now();
        this.lastResetAt = this.startedAt;
    }

    // ========== Recording Methods ==========

    /**
     * Records an acquisition attempt.
     */
    public void recordAcquisitionAttempt() {
        acquisitionAttempts.increment();
    }

    /**
     * Records a successful acquisition.
     */
    public void recordAcquisitionSuccess(Duration duration) {
        acquisitionSuccesses.increment();
        totalAcquisitionTimeNanos.add(duration.toNanos());
        long current = currentOwnershipCount.incrementAndGet();
        maxOwnershipCount.updateAndGet(max -> Math.max(max, current));
    }

    /**
     * Records a failed acquisition.
     */
    public void recordAcquisitionFailure() {
        acquisitionFailures.increment();
    }

    /**
     * Records a renewal attempt.
     */
    public void recordRenewalAttempt() {
        renewalAttempts.increment();
    }

    /**
     * Records a successful renewal.
     */
    public void recordRenewalSuccess(Duration duration) {
        renewalSuccesses.increment();
        totalRenewalTimeNanos.add(duration.toNanos());
    }

    /**
     * Records a failed renewal.
     */
    public void recordRenewalFailure() {
        renewalFailures.increment();
    }

    /**
     * Records a graceful release.
     */
    public void recordGracefulRelease() {
        releasesTotal.increment();
        gracefulReleases.increment();
        currentOwnershipCount.decrementAndGet();
    }

    /**
     * Records a forced release.
     */
    public void recordForcedRelease() {
        releasesTotal.increment();
        forcedReleases.increment();
        currentOwnershipCount.decrementAndGet();
    }

    /**
     * Records an orphan recovery operation.
     */
    public void recordOrphanRecoveryAttempt() {
        orphanRecoveryAttempts.increment();
    }

    /**
     * Records recovered orphans.
     */
    public void recordOrphansRecovered(int count, Duration duration) {
        orphansRecovered.add(count);
        totalRecoveryTimeNanos.add(duration.toNanos());
        long current = currentOwnershipCount.addAndGet(count);
        maxOwnershipCount.updateAndGet(max -> Math.max(max, current));
    }

    /**
     * Records a fencing conflict.
     */
    public void recordFencingConflict() {
        fencingConflicts.increment();
    }

    /**
     * Records a backend connection failure.
     */
    public void recordBackendConnectionFailure() {
        backendConnectionFailures.increment();
    }

    /**
     * Records a lock timeout.
     */
    public void recordLockTimeout() {
        lockTimeouts.increment();
    }

    /**
     * Sets the pending renewal count.
     */
    public void setPendingRenewals(long count) {
        pendingRenewals.set(count);
    }

    /**
     * Sets the current ownership count.
     */
    public void setCurrentOwnershipCount(long count) {
        currentOwnershipCount.set(count);
        maxOwnershipCount.updateAndGet(max -> Math.max(max, count));
    }

    // ========== Query Methods ==========

    /**
     * Gets the acquisition success rate.
     */
    public double getAcquisitionSuccessRate() {
        long attempts = acquisitionAttempts.sum();
        return attempts > 0 ? (double) acquisitionSuccesses.sum() / attempts : 0.0;
    }

    /**
     * Gets the renewal success rate.
     */
    public double getRenewalSuccessRate() {
        long attempts = renewalAttempts.sum();
        return attempts > 0 ? (double) renewalSuccesses.sum() / attempts : 0.0;
    }

    /**
     * Gets the average acquisition time.
     */
    public Duration getAverageAcquisitionTime() {
        long successes = acquisitionSuccesses.sum();
        if (successes == 0) {
            return Duration.ZERO;
        }
        return Duration.ofNanos(totalAcquisitionTimeNanos.sum() / successes);
    }

    /**
     * Gets the average renewal time.
     */
    public Duration getAverageRenewalTime() {
        long successes = renewalSuccesses.sum();
        if (successes == 0) {
            return Duration.ZERO;
        }
        return Duration.ofNanos(totalRenewalTimeNanos.sum() / successes);
    }

    /**
     * Gets the metrics collection duration.
     */
    public Duration getCollectionDuration() {
        return Duration.between(lastResetAt, Instant.now());
    }

    /**
     * Gets the total uptime since metrics started.
     */
    public Duration getUptime() {
        return Duration.between(startedAt, Instant.now());
    }

    /**
     * Creates a snapshot of current metrics.
     */
    public MetricsSnapshot snapshot() {
        return MetricsSnapshot.builder()
                .timestamp(Instant.now())
                .acquisitionAttempts(acquisitionAttempts.sum())
                .acquisitionSuccesses(acquisitionSuccesses.sum())
                .acquisitionFailures(acquisitionFailures.sum())
                .renewalAttempts(renewalAttempts.sum())
                .renewalSuccesses(renewalSuccesses.sum())
                .renewalFailures(renewalFailures.sum())
                .releasesTotal(releasesTotal.sum())
                .gracefulReleases(gracefulReleases.sum())
                .forcedReleases(forcedReleases.sum())
                .orphanRecoveryAttempts(orphanRecoveryAttempts.sum())
                .orphansRecovered(orphansRecovered.sum())
                .fencingConflicts(fencingConflicts.sum())
                .backendConnectionFailures(backendConnectionFailures.sum())
                .lockTimeouts(lockTimeouts.sum())
                .currentOwnershipCount(currentOwnershipCount.get())
                .pendingRenewals(pendingRenewals.get())
                .maxOwnershipCount(maxOwnershipCount.get())
                .acquisitionSuccessRate(getAcquisitionSuccessRate())
                .renewalSuccessRate(getRenewalSuccessRate())
                .averageAcquisitionTimeNanos(getAverageAcquisitionTime().toNanos())
                .averageRenewalTimeNanos(getAverageRenewalTime().toNanos())
                .collectionDuration(getCollectionDuration())
                .build();
    }

    /**
     * Resets all counters (useful for periodic reporting).
     */
    public void reset() {
        acquisitionAttempts.reset();
        acquisitionSuccesses.reset();
        acquisitionFailures.reset();
        renewalAttempts.reset();
        renewalSuccesses.reset();
        renewalFailures.reset();
        releasesTotal.reset();
        gracefulReleases.reset();
        forcedReleases.reset();
        orphanRecoveryAttempts.reset();
        orphansRecovered.reset();
        fencingConflicts.reset();
        backendConnectionFailures.reset();
        lockTimeouts.reset();
        totalAcquisitionTimeNanos.reset();
        totalRenewalTimeNanos.reset();
        totalRecoveryTimeNanos.reset();
        pendingRenewals.set(0);
        lastResetAt = Instant.now();
    }

    /**
     * Immutable snapshot of metrics at a point in time.
     */
    @Getter
    @Builder
    @ToString
    public static class MetricsSnapshot {
        private final Instant timestamp;
        private final long acquisitionAttempts;
        private final long acquisitionSuccesses;
        private final long acquisitionFailures;
        private final long renewalAttempts;
        private final long renewalSuccesses;
        private final long renewalFailures;
        private final long releasesTotal;
        private final long gracefulReleases;
        private final long forcedReleases;
        private final long orphanRecoveryAttempts;
        private final long orphansRecovered;
        private final long fencingConflicts;
        private final long backendConnectionFailures;
        private final long lockTimeouts;
        private final long currentOwnershipCount;
        private final long pendingRenewals;
        private final long maxOwnershipCount;
        private final double acquisitionSuccessRate;
        private final double renewalSuccessRate;
        private final long averageAcquisitionTimeNanos;
        private final long averageRenewalTimeNanos;
        private final Duration collectionDuration;
    }
}
