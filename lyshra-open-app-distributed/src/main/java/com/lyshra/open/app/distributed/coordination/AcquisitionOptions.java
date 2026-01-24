package com.lyshra.open.app.distributed.coordination;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

/**
 * Options for customizing workflow ownership acquisition behavior.
 *
 * This immutable class allows callers to configure various aspects of
 * the ownership acquisition process, such as timeouts, lease duration,
 * and retry behavior.
 *
 * Usage:
 * <pre>
 * AcquisitionOptions options = AcquisitionOptions.builder()
 *     .leaseDuration(Duration.ofMinutes(10))
 *     .acquireTimeout(Duration.ofSeconds(30))
 *     .retryOnFailure(true)
 *     .build();
 *
 * coordinator.acquireOwnership(workflowKey, options);
 * </pre>
 *
 * Thread Safety: This class is immutable and thread-safe.
 */
@Getter
@Builder(toBuilder = true)
@ToString
public final class AcquisitionOptions {

    /**
     * Default options instance.
     */
    public static final AcquisitionOptions DEFAULT = AcquisitionOptions.builder().build();

    /**
     * Duration for which the ownership lease should be held.
     * Default: 5 minutes
     */
    @Builder.Default
    private final Duration leaseDuration = Duration.ofMinutes(5);

    /**
     * Maximum time to wait for acquiring ownership (including lock acquisition).
     * Default: 30 seconds
     */
    @Builder.Default
    private final Duration acquireTimeout = Duration.ofSeconds(30);

    /**
     * Maximum time to wait for the distributed lock.
     * Default: 10 seconds
     */
    @Builder.Default
    private final Duration lockTimeout = Duration.ofSeconds(10);

    /**
     * Whether to retry acquisition on transient failures.
     * Default: true
     */
    @Builder.Default
    private final boolean retryOnFailure = true;

    /**
     * Maximum number of retry attempts on failure.
     * Default: 3
     */
    @Builder.Default
    private final int maxRetries = 3;

    /**
     * Delay between retry attempts.
     * Default: 100 milliseconds
     */
    @Builder.Default
    private final Duration retryDelay = Duration.ofMillis(100);

    /**
     * Whether to skip partition validation.
     * Use with caution - only for special cases like recovery.
     * Default: false
     */
    @Builder.Default
    private final boolean skipPartitionCheck = false;

    /**
     * Whether to force acquisition even if already owned by another node.
     * This requires elevated privileges and should be used sparingly.
     * Default: false
     */
    @Builder.Default
    private final boolean forceAcquisition = false;

    /**
     * Minimum fencing token required for the acquisition.
     * Used to ensure we don't get a stale lease.
     * Default: 0 (no minimum)
     */
    @Builder.Default
    private final long minimumFencingToken = 0;

    /**
     * Priority for this acquisition (higher = more preferred).
     * Used when multiple nodes compete for the same workflow.
     * Default: 0
     */
    @Builder.Default
    private final int priority = 0;

    /**
     * Custom metadata to attach to the ownership record.
     */
    @Builder.Default
    private final Map<String, String> metadata = Collections.emptyMap();

    /**
     * Reason for acquisition (for audit trail).
     */
    @Builder.Default
    private final String acquisitionReason = "standard_acquisition";

    /**
     * Whether to wait for pending workflow completion before acquiring.
     * Useful when taking over from a node that is gracefully shutting down.
     * Default: false
     */
    @Builder.Default
    private final boolean waitForPendingCompletion = false;

    /**
     * Maximum time to wait for pending completion.
     * Default: 1 minute
     */
    @Builder.Default
    private final Duration pendingCompletionTimeout = Duration.ofMinutes(1);

    // ========== Factory Methods ==========

    /**
     * Creates options with a specific lease duration.
     */
    public static AcquisitionOptions withLeaseDuration(Duration duration) {
        return AcquisitionOptions.builder()
                .leaseDuration(duration)
                .build();
    }

    /**
     * Creates options for fast acquisition with short timeout.
     */
    public static AcquisitionOptions fast() {
        return AcquisitionOptions.builder()
                .acquireTimeout(Duration.ofSeconds(5))
                .lockTimeout(Duration.ofSeconds(2))
                .maxRetries(1)
                .build();
    }

    /**
     * Creates options for reliable acquisition with extended timeouts.
     */
    public static AcquisitionOptions reliable() {
        return AcquisitionOptions.builder()
                .acquireTimeout(Duration.ofMinutes(1))
                .lockTimeout(Duration.ofSeconds(30))
                .maxRetries(5)
                .retryDelay(Duration.ofMillis(500))
                .build();
    }

    /**
     * Creates options for recovery scenarios.
     */
    public static AcquisitionOptions forRecovery() {
        return AcquisitionOptions.builder()
                .skipPartitionCheck(true)
                .forceAcquisition(true)
                .acquisitionReason("recovery")
                .build();
    }

    /**
     * Creates options with no retries.
     */
    public static AcquisitionOptions noRetry() {
        return AcquisitionOptions.builder()
                .retryOnFailure(false)
                .maxRetries(0)
                .build();
    }
}
