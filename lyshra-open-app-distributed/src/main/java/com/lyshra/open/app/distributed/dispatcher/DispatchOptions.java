package com.lyshra.open.app.distributed.dispatcher;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

/**
 * Options for customizing workflow dispatch behavior.
 *
 * This immutable class allows callers to configure various aspects of
 * the dispatch process, including timeouts, retry behavior, and
 * ownership settings.
 *
 * Thread Safety: This class is immutable and thread-safe.
 */
@Getter
@Builder(toBuilder = true)
@ToString
public final class DispatchOptions {

    /**
     * Default options instance.
     */
    public static final DispatchOptions DEFAULT = DispatchOptions.builder().build();

    // ========== Timeout Settings ==========

    /**
     * Maximum time to wait for ownership acquisition.
     * Default: 30 seconds
     */
    @Builder.Default
    private final Duration acquisitionTimeout = Duration.ofSeconds(30);

    /**
     * Duration for which the ownership lease should be held.
     * Default: 5 minutes
     */
    @Builder.Default
    private final Duration leaseDuration = Duration.ofMinutes(5);

    // ========== Retry Settings ==========

    /**
     * Whether to retry on transient failures.
     * Default: true
     */
    @Builder.Default
    private final boolean retryEnabled = true;

    /**
     * Maximum number of retry attempts.
     * Default: 3
     */
    @Builder.Default
    private final int maxRetries = 3;

    /**
     * Initial delay between retries.
     * Default: 100 milliseconds
     */
    @Builder.Default
    private final Duration retryDelay = Duration.ofMillis(100);

    /**
     * Multiplier for exponential backoff.
     * Default: 2.0
     */
    @Builder.Default
    private final double retryBackoffMultiplier = 2.0;

    /**
     * Maximum delay between retries.
     * Default: 5 seconds
     */
    @Builder.Default
    private final Duration maxRetryDelay = Duration.ofSeconds(5);

    // ========== Routing Settings ==========

    /**
     * Whether to skip partition validation.
     * Default: false
     */
    @Builder.Default
    private final boolean skipPartitionCheck = false;

    /**
     * Whether to allow local execution on wrong partition.
     * Use only for testing or special recovery scenarios.
     * Default: false
     */
    @Builder.Default
    private final boolean allowLocalOnWrongPartition = false;

    // ========== Ownership Settings ==========

    /**
     * Whether to force acquisition even if already owned.
     * Default: false
     */
    @Builder.Default
    private final boolean forceAcquisition = false;

    /**
     * Priority for this dispatch (higher = more preferred).
     * Default: 0
     */
    @Builder.Default
    private final int priority = 0;

    /**
     * Minimum acceptable fencing token.
     * Default: 0 (no minimum)
     */
    @Builder.Default
    private final long minimumFencingToken = 0;

    // ========== Capacity Settings ==========

    /**
     * Whether to check node capacity before dispatching.
     * Default: true
     */
    @Builder.Default
    private final boolean checkCapacity = true;

    /**
     * Whether to queue if capacity is exceeded.
     * Default: false
     */
    @Builder.Default
    private final boolean queueOnCapacityExceeded = false;

    // ========== Metadata ==========

    /**
     * Custom metadata to attach to the ownership record.
     */
    @Builder.Default
    private final Map<String, String> metadata = Collections.emptyMap();

    /**
     * Reason for this dispatch (for audit trail).
     */
    @Builder.Default
    private final String dispatchReason = "standard_dispatch";

    /**
     * Correlation ID for tracing.
     */
    private final String correlationId;

    // ========== Factory Methods ==========

    /**
     * Creates default options.
     */
    public static DispatchOptions defaults() {
        return DEFAULT;
    }

    /**
     * Creates options with a specific lease duration.
     */
    public static DispatchOptions withLeaseDuration(Duration duration) {
        return DispatchOptions.builder()
                .leaseDuration(duration)
                .build();
    }

    /**
     * Creates options for fast dispatch with short timeout.
     */
    public static DispatchOptions fast() {
        return DispatchOptions.builder()
                .acquisitionTimeout(Duration.ofSeconds(5))
                .maxRetries(1)
                .retryDelay(Duration.ofMillis(50))
                .build();
    }

    /**
     * Creates options for reliable dispatch with extended timeouts.
     */
    public static DispatchOptions reliable() {
        return DispatchOptions.builder()
                .acquisitionTimeout(Duration.ofMinutes(1))
                .maxRetries(5)
                .retryDelay(Duration.ofMillis(200))
                .retryBackoffMultiplier(1.5)
                .build();
    }

    /**
     * Creates options for recovery scenarios.
     */
    public static DispatchOptions forRecovery() {
        return DispatchOptions.builder()
                .skipPartitionCheck(true)
                .forceAcquisition(true)
                .dispatchReason("recovery")
                .maxRetries(5)
                .build();
    }

    /**
     * Creates options with no retries.
     */
    public static DispatchOptions noRetry() {
        return DispatchOptions.builder()
                .retryEnabled(false)
                .maxRetries(0)
                .build();
    }

    /**
     * Creates options for resuming a workflow.
     */
    public static DispatchOptions forResume() {
        return DispatchOptions.builder()
                .dispatchReason("resume")
                .forceAcquisition(true)
                .maxRetries(3)
                .build();
    }
}
