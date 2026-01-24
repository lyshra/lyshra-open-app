package com.lyshra.open.app.distributed.coordination;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Result of a batch workflow ownership acquisition operation.
 *
 * This class aggregates results for multiple workflow acquisition attempts
 * into a single response, useful for batch processing scenarios.
 *
 * Thread Safety: This class is immutable and thread-safe.
 */
@Getter
@Builder(toBuilder = true)
@ToString
public final class BatchCoordinationResult {

    /**
     * Individual results for each workflow.
     */
    private final Map<String, CoordinationResult> results;

    /**
     * Workflow keys that were successfully acquired.
     */
    private final Set<String> acquiredWorkflows;

    /**
     * Workflow keys that failed to be acquired.
     */
    private final Set<String> failedWorkflows;

    /**
     * When the batch operation started.
     */
    private final Instant startedAt;

    /**
     * When the batch operation completed.
     */
    private final Instant completedAt;

    /**
     * Duration of the batch operation.
     */
    private final Duration duration;

    /**
     * Whether all workflows were successfully acquired.
     */
    private final boolean allAcquired;

    /**
     * Whether the batch was atomic (all-or-nothing).
     */
    private final boolean atomic;

    /**
     * Error message if the batch operation failed.
     */
    private final String errorMessage;

    /**
     * Private constructor - use builder.
     */
    private BatchCoordinationResult(Map<String, CoordinationResult> results,
                                     Set<String> acquiredWorkflows,
                                     Set<String> failedWorkflows,
                                     Instant startedAt,
                                     Instant completedAt,
                                     Duration duration,
                                     boolean allAcquired,
                                     boolean atomic,
                                     String errorMessage) {
        this.results = results != null
                ? Collections.unmodifiableMap(new HashMap<>(results))
                : Collections.emptyMap();
        this.acquiredWorkflows = acquiredWorkflows != null
                ? Collections.unmodifiableSet(new HashSet<>(acquiredWorkflows))
                : Collections.emptySet();
        this.failedWorkflows = failedWorkflows != null
                ? Collections.unmodifiableSet(new HashSet<>(failedWorkflows))
                : Collections.emptySet();
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.duration = duration;
        this.allAcquired = allAcquired;
        this.atomic = atomic;
        this.errorMessage = errorMessage;
    }

    // ========== Query Methods ==========

    /**
     * Gets the total number of workflows in the batch.
     */
    public int getTotalCount() {
        return results.size();
    }

    /**
     * Gets the number of successful acquisitions.
     */
    public int getSuccessCount() {
        return acquiredWorkflows.size();
    }

    /**
     * Gets the number of failed acquisitions.
     */
    public int getFailureCount() {
        return failedWorkflows.size();
    }

    /**
     * Gets the success rate (0.0 to 1.0).
     */
    public double getSuccessRate() {
        int total = getTotalCount();
        return total > 0 ? (double) getSuccessCount() / total : 0.0;
    }

    /**
     * Checks if any workflows were successfully acquired.
     */
    public boolean hasAnySuccess() {
        return !acquiredWorkflows.isEmpty();
    }

    /**
     * Checks if any workflows failed to be acquired.
     */
    public boolean hasAnyFailure() {
        return !failedWorkflows.isEmpty();
    }

    /**
     * Gets the result for a specific workflow.
     */
    public Optional<CoordinationResult> getResult(String workflowKey) {
        return Optional.ofNullable(results.get(workflowKey));
    }

    /**
     * Checks if a specific workflow was acquired.
     */
    public boolean isAcquired(String workflowKey) {
        return acquiredWorkflows.contains(workflowKey);
    }

    /**
     * Gets the error message as an Optional.
     */
    public Optional<String> getErrorMessageOptional() {
        return Optional.ofNullable(errorMessage);
    }

    // ========== Factory Methods ==========

    /**
     * Creates a batch result from individual results.
     */
    public static BatchCoordinationResult from(Map<String, CoordinationResult> results,
                                                Instant startedAt,
                                                boolean atomic) {
        Set<String> acquired = new HashSet<>();
        Set<String> failed = new HashSet<>();

        for (Map.Entry<String, CoordinationResult> entry : results.entrySet()) {
            if (entry.getValue().isAcquired()) {
                acquired.add(entry.getKey());
            } else {
                failed.add(entry.getKey());
            }
        }

        Instant now = Instant.now();
        return BatchCoordinationResult.builder()
                .results(results)
                .acquiredWorkflows(acquired)
                .failedWorkflows(failed)
                .startedAt(startedAt)
                .completedAt(now)
                .duration(Duration.between(startedAt, now))
                .allAcquired(failed.isEmpty())
                .atomic(atomic)
                .build();
    }

    /**
     * Creates an all-success batch result.
     */
    public static BatchCoordinationResult allAcquired(Set<String> workflowKeys,
                                                       Map<String, CoordinationResult> results,
                                                       Duration duration) {
        Instant now = Instant.now();
        return BatchCoordinationResult.builder()
                .results(results)
                .acquiredWorkflows(workflowKeys)
                .failedWorkflows(Collections.emptySet())
                .startedAt(now.minus(duration))
                .completedAt(now)
                .duration(duration)
                .allAcquired(true)
                .atomic(false)
                .build();
    }

    /**
     * Creates a batch failure result.
     */
    public static BatchCoordinationResult failure(String errorMessage, Throwable cause) {
        Instant now = Instant.now();
        return BatchCoordinationResult.builder()
                .results(Collections.emptyMap())
                .acquiredWorkflows(Collections.emptySet())
                .failedWorkflows(Collections.emptySet())
                .startedAt(now)
                .completedAt(now)
                .duration(Duration.ZERO)
                .allAcquired(false)
                .atomic(false)
                .errorMessage(errorMessage + (cause != null ? ": " + cause.getMessage() : ""))
                .build();
    }

    /**
     * Creates an empty batch result (no workflows requested).
     */
    public static BatchCoordinationResult empty() {
        Instant now = Instant.now();
        return BatchCoordinationResult.builder()
                .results(Collections.emptyMap())
                .acquiredWorkflows(Collections.emptySet())
                .failedWorkflows(Collections.emptySet())
                .startedAt(now)
                .completedAt(now)
                .duration(Duration.ZERO)
                .allAcquired(true)
                .atomic(false)
                .build();
    }
}
