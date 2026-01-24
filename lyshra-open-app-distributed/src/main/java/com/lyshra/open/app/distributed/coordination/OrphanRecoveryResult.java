package com.lyshra.open.app.distributed.coordination;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Result of an orphan workflow recovery operation.
 *
 * This class provides detailed information about the recovery process,
 * including which workflows were successfully claimed, which failed,
 * and any errors encountered.
 *
 * Thread Safety: This class is immutable and thread-safe.
 */
@Getter
@Builder(toBuilder = true)
@ToString
public final class OrphanRecoveryResult {

    /**
     * The ID of the failed node being recovered from.
     */
    private final String failedNodeId;

    /**
     * The node ID that performed the recovery.
     */
    private final String recoveringNodeId;

    /**
     * When the recovery operation started.
     */
    private final Instant startedAt;

    /**
     * When the recovery operation completed.
     */
    private final Instant completedAt;

    /**
     * Total duration of the recovery operation.
     */
    private final Duration duration;

    /**
     * Total number of orphaned workflows found.
     */
    private final int orphansFound;

    /**
     * Number of workflows successfully claimed.
     */
    private final int successfulClaims;

    /**
     * Number of workflows that failed to be claimed.
     */
    private final int failedClaims;

    /**
     * Number of workflows skipped (e.g., wrong partition).
     */
    private final int skippedWorkflows;

    /**
     * Workflow keys that were successfully claimed.
     */
    private final Set<String> claimedWorkflows;

    /**
     * Workflow keys that failed to be claimed, with reasons.
     */
    private final Map<String, String> failedWorkflows;

    /**
     * Workflow keys that were skipped, with reasons.
     */
    private final Map<String, String> skippedWorkflowReasons;

    /**
     * Whether the recovery was complete (no more orphans to claim).
     */
    private final boolean complete;

    /**
     * Whether the recovery reached its claim limit.
     */
    private final boolean limitReached;

    /**
     * Any error that occurred during recovery.
     */
    private final String errorMessage;

    /**
     * The exception that caused recovery failure (if any).
     */
    private final Throwable cause;

    /**
     * Private constructor - use builder.
     */
    private OrphanRecoveryResult(String failedNodeId,
                                  String recoveringNodeId,
                                  Instant startedAt,
                                  Instant completedAt,
                                  Duration duration,
                                  int orphansFound,
                                  int successfulClaims,
                                  int failedClaims,
                                  int skippedWorkflows,
                                  Set<String> claimedWorkflows,
                                  Map<String, String> failedWorkflows,
                                  Map<String, String> skippedWorkflowReasons,
                                  boolean complete,
                                  boolean limitReached,
                                  String errorMessage,
                                  Throwable cause) {
        this.failedNodeId = failedNodeId;
        this.recoveringNodeId = recoveringNodeId;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.duration = duration;
        this.orphansFound = orphansFound;
        this.successfulClaims = successfulClaims;
        this.failedClaims = failedClaims;
        this.skippedWorkflows = skippedWorkflows;
        this.claimedWorkflows = claimedWorkflows != null
                ? Collections.unmodifiableSet(new HashSet<>(claimedWorkflows))
                : Collections.emptySet();
        this.failedWorkflows = failedWorkflows != null
                ? Collections.unmodifiableMap(new HashMap<>(failedWorkflows))
                : Collections.emptyMap();
        this.skippedWorkflowReasons = skippedWorkflowReasons != null
                ? Collections.unmodifiableMap(new HashMap<>(skippedWorkflowReasons))
                : Collections.emptyMap();
        this.complete = complete;
        this.limitReached = limitReached;
        this.errorMessage = errorMessage;
        this.cause = cause;
    }

    // ========== Query Methods ==========

    /**
     * Checks if the recovery was successful (at least some workflows claimed).
     */
    public boolean isSuccessful() {
        return successfulClaims > 0 && errorMessage == null;
    }

    /**
     * Checks if any workflows were found but none could be claimed.
     */
    public boolean isPartialFailure() {
        return orphansFound > 0 && successfulClaims == 0 && failedClaims > 0;
    }

    /**
     * Checks if the recovery encountered an error.
     */
    public boolean hasError() {
        return errorMessage != null || cause != null;
    }

    /**
     * Checks if no orphans were found.
     */
    public boolean noOrphansFound() {
        return orphansFound == 0;
    }

    /**
     * Gets the success rate of claims (0.0 to 1.0).
     */
    public double getSuccessRate() {
        int attempted = successfulClaims + failedClaims;
        return attempted > 0 ? (double) successfulClaims / attempted : 0.0;
    }

    /**
     * Gets the error message as an Optional.
     */
    public Optional<String> getErrorMessageOptional() {
        return Optional.ofNullable(errorMessage);
    }

    /**
     * Gets the cause as an Optional.
     */
    public Optional<Throwable> getCauseOptional() {
        return Optional.ofNullable(cause);
    }

    // ========== Factory Methods ==========

    /**
     * Creates a successful recovery result.
     */
    public static OrphanRecoveryResult success(String failedNodeId,
                                                String recoveringNodeId,
                                                Set<String> claimedWorkflows,
                                                int orphansFound,
                                                Duration duration) {
        Instant now = Instant.now();
        return OrphanRecoveryResult.builder()
                .failedNodeId(failedNodeId)
                .recoveringNodeId(recoveringNodeId)
                .startedAt(now.minus(duration))
                .completedAt(now)
                .duration(duration)
                .orphansFound(orphansFound)
                .successfulClaims(claimedWorkflows.size())
                .failedClaims(0)
                .skippedWorkflows(orphansFound - claimedWorkflows.size())
                .claimedWorkflows(claimedWorkflows)
                .complete(true)
                .limitReached(false)
                .build();
    }

    /**
     * Creates a partial success result (some claims failed).
     */
    public static OrphanRecoveryResult partial(String failedNodeId,
                                                String recoveringNodeId,
                                                Set<String> claimedWorkflows,
                                                Map<String, String> failedWorkflows,
                                                int orphansFound,
                                                Duration duration) {
        Instant now = Instant.now();
        return OrphanRecoveryResult.builder()
                .failedNodeId(failedNodeId)
                .recoveringNodeId(recoveringNodeId)
                .startedAt(now.minus(duration))
                .completedAt(now)
                .duration(duration)
                .orphansFound(orphansFound)
                .successfulClaims(claimedWorkflows.size())
                .failedClaims(failedWorkflows.size())
                .skippedWorkflows(orphansFound - claimedWorkflows.size() - failedWorkflows.size())
                .claimedWorkflows(claimedWorkflows)
                .failedWorkflows(failedWorkflows)
                .complete(false)
                .limitReached(false)
                .build();
    }

    /**
     * Creates a result indicating no orphans were found.
     */
    public static OrphanRecoveryResult noOrphans(String failedNodeId, String recoveringNodeId) {
        Instant now = Instant.now();
        return OrphanRecoveryResult.builder()
                .failedNodeId(failedNodeId)
                .recoveringNodeId(recoveringNodeId)
                .startedAt(now)
                .completedAt(now)
                .duration(Duration.ZERO)
                .orphansFound(0)
                .successfulClaims(0)
                .failedClaims(0)
                .skippedWorkflows(0)
                .complete(true)
                .limitReached(false)
                .build();
    }

    /**
     * Creates a result indicating the claim limit was reached.
     */
    public static OrphanRecoveryResult limitReached(String failedNodeId,
                                                     String recoveringNodeId,
                                                     Set<String> claimedWorkflows,
                                                     int orphansFound,
                                                     Duration duration) {
        Instant now = Instant.now();
        return OrphanRecoveryResult.builder()
                .failedNodeId(failedNodeId)
                .recoveringNodeId(recoveringNodeId)
                .startedAt(now.minus(duration))
                .completedAt(now)
                .duration(duration)
                .orphansFound(orphansFound)
                .successfulClaims(claimedWorkflows.size())
                .failedClaims(0)
                .skippedWorkflows(orphansFound - claimedWorkflows.size())
                .claimedWorkflows(claimedWorkflows)
                .complete(false)
                .limitReached(true)
                .build();
    }

    /**
     * Creates a failure result.
     */
    public static OrphanRecoveryResult failure(String failedNodeId,
                                                String recoveringNodeId,
                                                String errorMessage,
                                                Throwable cause) {
        Instant now = Instant.now();
        return OrphanRecoveryResult.builder()
                .failedNodeId(failedNodeId)
                .recoveringNodeId(recoveringNodeId)
                .startedAt(now)
                .completedAt(now)
                .duration(Duration.ZERO)
                .orphansFound(0)
                .successfulClaims(0)
                .failedClaims(0)
                .skippedWorkflows(0)
                .complete(false)
                .limitReached(false)
                .errorMessage(errorMessage)
                .cause(cause)
                .build();
    }
}
