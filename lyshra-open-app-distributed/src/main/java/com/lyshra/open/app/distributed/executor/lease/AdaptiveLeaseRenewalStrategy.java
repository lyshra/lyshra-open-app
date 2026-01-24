package com.lyshra.open.app.distributed.executor.lease;

import com.lyshra.open.app.distributed.coordination.OwnershipContext;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Adaptive lease renewal strategy that adjusts renewal timing based on
 * lease duration, remaining time, and execution state.
 *
 * Key behaviors:
 * - Renews when remaining lease time falls below a configurable threshold
 * - Increases renewal urgency as expiration approaches
 * - Handles consecutive failures with exponential backoff
 * - Supports graceful degradation when renewals fail
 *
 * Thread Safety: This class is immutable and thread-safe.
 */
@Getter
@Builder(toBuilder = true)
public final class AdaptiveLeaseRenewalStrategy implements ILeaseRenewalStrategy {

    /**
     * Default strategy instance.
     */
    public static final AdaptiveLeaseRenewalStrategy DEFAULT = AdaptiveLeaseRenewalStrategy.builder().build();

    /**
     * Minimum remaining lease time before renewal is triggered.
     * Default: 2 minutes (renew when less than 2 minutes remain)
     */
    @Builder.Default
    private final Duration renewalThreshold = Duration.ofMinutes(2);

    /**
     * Target time to have remaining after renewal.
     * Default: 5 minutes
     */
    @Builder.Default
    private final Duration targetLeaseRemaining = Duration.ofMinutes(5);

    /**
     * Minimum time between renewal checks.
     * Default: 10 seconds
     */
    @Builder.Default
    private final Duration minCheckInterval = Duration.ofSeconds(10);

    /**
     * Maximum time between renewal checks.
     * Default: 60 seconds
     */
    @Builder.Default
    private final Duration maxCheckInterval = Duration.ofSeconds(60);

    /**
     * Maximum consecutive failures before aborting.
     * Default: 5
     */
    @Builder.Default
    private final int maxConsecutiveFailures = 5;

    /**
     * Base delay for retry after failure.
     * Default: 500 milliseconds
     */
    @Builder.Default
    private final Duration retryBaseDelay = Duration.ofMillis(500);

    /**
     * Maximum retry delay (for exponential backoff).
     * Default: 10 seconds
     */
    @Builder.Default
    private final Duration maxRetryDelay = Duration.ofSeconds(10);

    /**
     * Fraction of lease remaining at which to consider renewal urgent.
     * Default: 0.25 (25% remaining = urgent)
     */
    @Builder.Default
    private final double urgentThreshold = 0.25;

    /**
     * Whether to abort on lease expiration.
     * Default: true
     */
    @Builder.Default
    private final boolean abortOnExpiration = true;

    @Override
    public boolean shouldRenew(OwnershipContext context, LeaseRenewalState renewalState) {
        if (context == null || context.getExpiresAt() == null) {
            return false;
        }

        // Always try to renew if urgent
        if (renewalState.urgentRenewalNeeded()) {
            return true;
        }

        // Check if we're below the threshold
        Optional<Duration> remaining = context.getRemainingTime();
        if (remaining.isEmpty()) {
            // Lease expired - need to renew urgently
            return true;
        }

        return remaining.get().compareTo(renewalThreshold) <= 0;
    }

    @Override
    public Instant calculateNextRenewalTime(OwnershipContext context, LeaseRenewalState renewalState) {
        if (context == null || context.getExpiresAt() == null) {
            return Instant.now();
        }

        // Calculate when we should renew (expiration - threshold)
        return context.getExpiresAt().minus(renewalThreshold);
    }

    @Override
    public Duration calculateNextCheckDelay(OwnershipContext context, LeaseRenewalState renewalState) {
        if (context == null || context.getExpiresAt() == null) {
            return minCheckInterval;
        }

        // If urgent, check frequently
        if (renewalState.urgentRenewalNeeded()) {
            return minCheckInterval;
        }

        Optional<Duration> remaining = context.getRemainingTime();
        if (remaining.isEmpty()) {
            return minCheckInterval;
        }

        Duration remainingTime = remaining.get();

        // If lease is expiring soon, check more frequently
        if (remainingTime.compareTo(renewalThreshold) <= 0) {
            return minCheckInterval;
        }

        // Calculate adaptive check interval
        // Check more frequently as expiration approaches
        Duration timeUntilRenewal = remainingTime.minus(renewalThreshold);

        // Check at 1/4 of the time until renewal, bounded by min/max
        Duration checkInterval = timeUntilRenewal.dividedBy(4);

        if (checkInterval.compareTo(minCheckInterval) < 0) {
            return minCheckInterval;
        }
        if (checkInterval.compareTo(maxCheckInterval) > 0) {
            return maxCheckInterval;
        }

        return checkInterval;
    }

    @Override
    public Duration getExtensionDuration(OwnershipContext context, LeaseRenewalState renewalState) {
        if (context == null || context.getLeaseDuration() == null) {
            return targetLeaseRemaining;
        }

        // Request the original lease duration (or target, whichever is larger)
        Duration originalDuration = context.getLeaseDuration();
        return originalDuration.compareTo(targetLeaseRemaining) > 0 ?
                originalDuration : targetLeaseRemaining;
    }

    @Override
    public RenewalFailureAction onRenewalFailure(OwnershipContext context,
                                                  LeaseRenewalState renewalState,
                                                  String failureReason) {
        int failures = renewalState.consecutiveFailures() + 1;

        // Too many failures - abort
        if (failures >= maxConsecutiveFailures) {
            return RenewalFailureAction.ABORT_EXECUTION;
        }

        // Check remaining time
        Optional<Duration> remaining = context != null ? context.getRemainingTime() : Optional.empty();

        if (remaining.isEmpty() || remaining.get().isNegative()) {
            // Already expired
            if (abortOnExpiration) {
                return RenewalFailureAction.ABORT_EXECUTION;
            } else {
                return RenewalFailureAction.PAUSE_AND_REACQUIRE;
            }
        }

        // If very little time left, retry immediately
        if (remaining.get().compareTo(Duration.ofSeconds(30)) < 0) {
            return RenewalFailureAction.RETRY_IMMEDIATELY;
        }

        // Otherwise, retry with backoff
        return RenewalFailureAction.RETRY_WITH_BACKOFF;
    }

    /**
     * Calculates the retry delay based on number of failures (exponential backoff).
     *
     * @param failures number of consecutive failures
     * @return delay before next retry
     */
    public Duration calculateRetryDelay(int failures) {
        if (failures <= 0) {
            return retryBaseDelay;
        }

        // Exponential backoff: base * 2^(failures-1)
        long delayMs = (long) (retryBaseDelay.toMillis() * Math.pow(2, failures - 1));
        delayMs = Math.min(delayMs, maxRetryDelay.toMillis());

        return Duration.ofMillis(delayMs);
    }

    /**
     * Checks if the lease is in urgent state (close to expiration).
     *
     * @param context the ownership context
     * @return true if urgent renewal is needed
     */
    public boolean isUrgent(OwnershipContext context) {
        if (context == null || context.getExpiresAt() == null || context.getLeaseDuration() == null) {
            return true;
        }

        Optional<Duration> remaining = context.getRemainingTime();
        if (remaining.isEmpty()) {
            return true;
        }

        Duration total = context.getLeaseDuration();
        double remainingFraction = (double) remaining.get().toMillis() / total.toMillis();

        return remainingFraction <= urgentThreshold;
    }

    // ========== Factory Methods ==========

    /**
     * Creates a strategy for short-lived workflows (more aggressive renewal).
     */
    public static AdaptiveLeaseRenewalStrategy forShortWorkflows() {
        return AdaptiveLeaseRenewalStrategy.builder()
                .renewalThreshold(Duration.ofSeconds(30))
                .targetLeaseRemaining(Duration.ofMinutes(1))
                .minCheckInterval(Duration.ofSeconds(5))
                .maxCheckInterval(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Creates a strategy for long-running workflows (more relaxed renewal).
     */
    public static AdaptiveLeaseRenewalStrategy forLongWorkflows() {
        return AdaptiveLeaseRenewalStrategy.builder()
                .renewalThreshold(Duration.ofMinutes(5))
                .targetLeaseRemaining(Duration.ofMinutes(15))
                .minCheckInterval(Duration.ofSeconds(30))
                .maxCheckInterval(Duration.ofMinutes(2))
                .maxConsecutiveFailures(10)
                .build();
    }

    /**
     * Creates a strategy for testing (fast renewal).
     */
    public static AdaptiveLeaseRenewalStrategy forTesting() {
        return AdaptiveLeaseRenewalStrategy.builder()
                .renewalThreshold(Duration.ofSeconds(5))
                .targetLeaseRemaining(Duration.ofSeconds(15))
                .minCheckInterval(Duration.ofSeconds(1))
                .maxCheckInterval(Duration.ofSeconds(3))
                .retryBaseDelay(Duration.ofMillis(50))
                .maxRetryDelay(Duration.ofMillis(500))
                .build();
    }
}
