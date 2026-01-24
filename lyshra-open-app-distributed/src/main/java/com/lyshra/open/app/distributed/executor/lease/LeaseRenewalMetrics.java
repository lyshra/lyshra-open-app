package com.lyshra.open.app.distributed.executor.lease;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Metrics for monitoring lease renewal performance.
 *
 * Thread Safety: This class is thread-safe.
 */
@ToString
public final class LeaseRenewalMetrics {

    // ========== Counters ==========

    private final LongAdder renewalsStarted = new LongAdder();
    private final LongAdder renewalsStopped = new LongAdder();
    private final LongAdder renewalAttempts = new LongAdder();
    private final LongAdder renewalSuccesses = new LongAdder();
    private final LongAdder renewalFailures = new LongAdder();
    private final LongAdder ownershipLostEvents = new LongAdder();
    private final LongAdder abortedExecutions = new LongAdder();

    // ========== Timing ==========

    private final AtomicLong totalRenewalTimeNanos = new AtomicLong(0);
    private final AtomicLong minRenewalTimeNanos = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxRenewalTimeNanos = new AtomicLong(0);
    private final LongAdder renewalTimeCount = new LongAdder();

    // ========== State ==========

    private final AtomicLong currentActiveRenewals = new AtomicLong(0);
    private volatile Instant startTime;

    public LeaseRenewalMetrics() {
        this.startTime = Instant.now();
    }

    // ========== Recording Methods ==========

    public void recordRenewalStarted() {
        renewalsStarted.increment();
        currentActiveRenewals.incrementAndGet();
    }

    public void recordRenewalStopped() {
        renewalsStopped.increment();
        currentActiveRenewals.decrementAndGet();
    }

    public void recordRenewalAttempt() {
        renewalAttempts.increment();
    }

    public void recordRenewalSuccess(Duration renewalTime) {
        renewalSuccesses.increment();

        if (renewalTime != null) {
            long nanos = renewalTime.toNanos();
            totalRenewalTimeNanos.addAndGet(nanos);
            renewalTimeCount.increment();

            // Update min
            long currentMin;
            while (nanos < (currentMin = minRenewalTimeNanos.get())) {
                if (minRenewalTimeNanos.compareAndSet(currentMin, nanos)) {
                    break;
                }
            }

            // Update max
            long currentMax;
            while (nanos > (currentMax = maxRenewalTimeNanos.get())) {
                if (maxRenewalTimeNanos.compareAndSet(currentMax, nanos)) {
                    break;
                }
            }
        }
    }

    public void recordRenewalFailure() {
        renewalFailures.increment();
    }

    public void recordOwnershipLost() {
        ownershipLostEvents.increment();
    }

    public void recordAbortedExecution() {
        abortedExecutions.increment();
    }

    // ========== Query Methods ==========

    public long getRenewalsStarted() {
        return renewalsStarted.sum();
    }

    public long getRenewalsStopped() {
        return renewalsStopped.sum();
    }

    public long getRenewalAttempts() {
        return renewalAttempts.sum();
    }

    public long getRenewalSuccesses() {
        return renewalSuccesses.sum();
    }

    public long getRenewalFailures() {
        return renewalFailures.sum();
    }

    public long getOwnershipLostEvents() {
        return ownershipLostEvents.sum();
    }

    public long getAbortedExecutions() {
        return abortedExecutions.sum();
    }

    public long getCurrentActiveRenewals() {
        return currentActiveRenewals.get();
    }

    public double getSuccessRate() {
        long attempts = getRenewalAttempts();
        if (attempts == 0) {
            return 1.0;
        }
        return (double) getRenewalSuccesses() / attempts;
    }

    public Duration getAverageRenewalTime() {
        long count = renewalTimeCount.sum();
        if (count == 0) {
            return Duration.ZERO;
        }
        return Duration.ofNanos(totalRenewalTimeNanos.get() / count);
    }

    public Duration getMinRenewalTime() {
        long min = minRenewalTimeNanos.get();
        return min == Long.MAX_VALUE ? Duration.ZERO : Duration.ofNanos(min);
    }

    public Duration getMaxRenewalTime() {
        return Duration.ofNanos(maxRenewalTimeNanos.get());
    }

    public Duration getUptime() {
        return Duration.between(startTime, Instant.now());
    }

    /**
     * Creates an immutable snapshot of the current metrics.
     */
    public MetricsSnapshot snapshot() {
        return MetricsSnapshot.builder()
                .renewalsStarted(getRenewalsStarted())
                .renewalsStopped(getRenewalsStopped())
                .renewalAttempts(getRenewalAttempts())
                .renewalSuccesses(getRenewalSuccesses())
                .renewalFailures(getRenewalFailures())
                .ownershipLostEvents(getOwnershipLostEvents())
                .abortedExecutions(getAbortedExecutions())
                .currentActiveRenewals(getCurrentActiveRenewals())
                .successRate(getSuccessRate())
                .averageRenewalTime(getAverageRenewalTime())
                .minRenewalTime(getMinRenewalTime())
                .maxRenewalTime(getMaxRenewalTime())
                .uptime(getUptime())
                .snapshotTime(Instant.now())
                .build();
    }

    /**
     * Resets all metrics.
     */
    public void reset() {
        renewalsStarted.reset();
        renewalsStopped.reset();
        renewalAttempts.reset();
        renewalSuccesses.reset();
        renewalFailures.reset();
        ownershipLostEvents.reset();
        abortedExecutions.reset();
        totalRenewalTimeNanos.set(0);
        minRenewalTimeNanos.set(Long.MAX_VALUE);
        maxRenewalTimeNanos.set(0);
        renewalTimeCount.reset();
        currentActiveRenewals.set(0);
        startTime = Instant.now();
    }

    /**
     * Immutable snapshot of metrics.
     */
    @Getter
    @Builder
    @ToString
    public static final class MetricsSnapshot {
        private final long renewalsStarted;
        private final long renewalsStopped;
        private final long renewalAttempts;
        private final long renewalSuccesses;
        private final long renewalFailures;
        private final long ownershipLostEvents;
        private final long abortedExecutions;
        private final long currentActiveRenewals;
        private final double successRate;
        private final Duration averageRenewalTime;
        private final Duration minRenewalTime;
        private final Duration maxRenewalTime;
        private final Duration uptime;
        private final Instant snapshotTime;
    }
}
