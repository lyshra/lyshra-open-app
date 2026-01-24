package com.lyshra.open.app.distributed.dispatcher;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.util.Set;

/**
 * Policy for retrying failed dispatch operations.
 *
 * This class defines the behavior for handling transient failures during
 * workflow dispatch. It supports:
 * - Configurable retry limits
 * - Exponential backoff with jitter
 * - Selective retry based on rejection reason
 *
 * Thread Safety: This class is immutable and thread-safe.
 */
@Getter
@Builder(toBuilder = true)
@ToString
public final class DispatchRetryPolicy {

    /**
     * Default retry policy instance.
     */
    public static final DispatchRetryPolicy DEFAULT = DispatchRetryPolicy.builder().build();

    /**
     * No retry policy - fails immediately on any error.
     */
    public static final DispatchRetryPolicy NO_RETRY = DispatchRetryPolicy.builder()
            .maxRetries(0)
            .enabled(false)
            .build();

    // ========== Basic Settings ==========

    /**
     * Whether retry is enabled.
     * Default: true
     */
    @Builder.Default
    private final boolean enabled = true;

    /**
     * Maximum number of retry attempts.
     * Default: 3
     */
    @Builder.Default
    private final int maxRetries = 3;

    // ========== Backoff Settings ==========

    /**
     * Initial delay before the first retry.
     * Default: 100 milliseconds
     */
    @Builder.Default
    private final Duration initialDelay = Duration.ofMillis(100);

    /**
     * Multiplier for exponential backoff.
     * Default: 2.0
     */
    @Builder.Default
    private final double backoffMultiplier = 2.0;

    /**
     * Maximum delay between retries.
     * Default: 10 seconds
     */
    @Builder.Default
    private final Duration maxDelay = Duration.ofSeconds(10);

    /**
     * Whether to add random jitter to delays.
     * Default: true
     */
    @Builder.Default
    private final boolean jitterEnabled = true;

    /**
     * Maximum jitter as a percentage of the delay (0.0 to 1.0).
     * Default: 0.1 (10%)
     */
    @Builder.Default
    private final double jitterFactor = 0.1;

    // ========== Selective Retry Settings ==========

    /**
     * Reasons that should be retried.
     * Default: ACQUISITION_TIMEOUT, BACKEND_UNAVAILABLE, COORDINATOR_INACTIVE
     */
    @Builder.Default
    private final Set<WorkflowDispatchResult.RejectReason> retryableReasons = Set.of(
            WorkflowDispatchResult.RejectReason.ACQUISITION_TIMEOUT,
            WorkflowDispatchResult.RejectReason.BACKEND_UNAVAILABLE,
            WorkflowDispatchResult.RejectReason.COORDINATOR_INACTIVE
    );

    /**
     * Reasons that should never be retried.
     */
    @Builder.Default
    private final Set<WorkflowDispatchResult.RejectReason> nonRetryableReasons = Set.of(
            WorkflowDispatchResult.RejectReason.FENCING_CONFLICT,
            WorkflowDispatchResult.RejectReason.CAPACITY_EXCEEDED,
            WorkflowDispatchResult.RejectReason.DISPATCHER_SHUTTING_DOWN,
            WorkflowDispatchResult.RejectReason.INVALID_REQUEST
    );

    // ========== Query Methods ==========

    /**
     * Checks if a specific rejection reason should be retried.
     *
     * @param reason the rejection reason
     * @return true if should retry
     */
    public boolean shouldRetry(WorkflowDispatchResult.RejectReason reason) {
        if (!enabled || reason == null) {
            return false;
        }
        if (nonRetryableReasons.contains(reason)) {
            return false;
        }
        return retryableReasons.contains(reason);
    }

    /**
     * Checks if another retry should be attempted.
     *
     * @param attemptNumber the current attempt number (1-based)
     * @return true if should retry
     */
    public boolean shouldRetry(int attemptNumber) {
        return enabled && attemptNumber <= maxRetries;
    }

    /**
     * Calculates the delay before the next retry.
     *
     * @param attemptNumber the current attempt number (1-based)
     * @return the delay duration
     */
    public Duration calculateDelay(int attemptNumber) {
        if (attemptNumber <= 0) {
            return initialDelay;
        }

        // Calculate exponential backoff
        double delayMs = initialDelay.toMillis() * Math.pow(backoffMultiplier, attemptNumber - 1);

        // Cap at max delay
        delayMs = Math.min(delayMs, maxDelay.toMillis());

        // Add jitter if enabled
        if (jitterEnabled && jitterFactor > 0) {
            double jitter = delayMs * jitterFactor * (Math.random() * 2 - 1);
            delayMs = Math.max(0, delayMs + jitter);
        }

        return Duration.ofMillis((long) delayMs);
    }

    /**
     * Gets the total maximum time that could be spent retrying.
     *
     * @return the maximum retry duration
     */
    public Duration getMaxRetryDuration() {
        if (!enabled || maxRetries == 0) {
            return Duration.ZERO;
        }

        long totalMs = 0;
        for (int i = 1; i <= maxRetries; i++) {
            double delayMs = initialDelay.toMillis() * Math.pow(backoffMultiplier, i - 1);
            totalMs += Math.min((long) delayMs, maxDelay.toMillis());
        }

        return Duration.ofMillis(totalMs);
    }

    // ========== Factory Methods ==========

    /**
     * Creates a policy with a specific max retries.
     */
    public static DispatchRetryPolicy withMaxRetries(int maxRetries) {
        return DispatchRetryPolicy.builder()
                .maxRetries(maxRetries)
                .build();
    }

    /**
     * Creates an aggressive retry policy with more attempts.
     */
    public static DispatchRetryPolicy aggressive() {
        return DispatchRetryPolicy.builder()
                .maxRetries(5)
                .initialDelay(Duration.ofMillis(50))
                .backoffMultiplier(1.5)
                .maxDelay(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Creates a conservative retry policy with fewer attempts.
     */
    public static DispatchRetryPolicy conservative() {
        return DispatchRetryPolicy.builder()
                .maxRetries(2)
                .initialDelay(Duration.ofMillis(500))
                .backoffMultiplier(3.0)
                .maxDelay(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Creates a policy optimized for production with balanced settings.
     */
    public static DispatchRetryPolicy production() {
        return DispatchRetryPolicy.builder()
                .maxRetries(3)
                .initialDelay(Duration.ofMillis(200))
                .backoffMultiplier(2.0)
                .maxDelay(Duration.ofSeconds(10))
                .jitterEnabled(true)
                .jitterFactor(0.2)
                .build();
    }

    /**
     * Creates a policy for testing with fast retries.
     */
    public static DispatchRetryPolicy forTesting() {
        return DispatchRetryPolicy.builder()
                .maxRetries(3)
                .initialDelay(Duration.ofMillis(10))
                .backoffMultiplier(1.5)
                .maxDelay(Duration.ofMillis(100))
                .jitterEnabled(false)
                .build();
    }
}
