package com.lyshra.open.app.integration.contract.signal;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Represents the result of processing a signal.
 * Provides information about whether the signal was accepted,
 * how it affected the workflow/task, and any errors that occurred.
 */
public interface ILyshraOpenAppSignalResult {

    /**
     * The signal that was processed.
     */
    ILyshraOpenAppSignal getSignal();

    /**
     * Whether the signal was successfully processed.
     */
    boolean isSuccess();

    /**
     * The outcome of processing the signal.
     */
    SignalOutcome getOutcome();

    /**
     * Error message if processing failed.
     */
    Optional<String> getErrorMessage();

    /**
     * Error code if processing failed.
     */
    Optional<String> getErrorCode();

    /**
     * The workflow instance state after processing.
     */
    Optional<String> getWorkflowState();

    /**
     * The task status after processing (if applicable).
     */
    Optional<String> getTaskStatus();

    /**
     * The next workflow step that will be executed (if workflow resumed).
     */
    Optional<String> getNextStep();

    /**
     * When the signal was processed.
     */
    Instant getProcessedAt();

    /**
     * Additional result data.
     */
    Map<String, Object> getResultData();

    /**
     * Processing duration in milliseconds.
     */
    long getProcessingDurationMillis();

    /**
     * Defines possible outcomes of signal processing.
     */
    enum SignalOutcome {
        /**
         * Signal was accepted and workflow/task was updated.
         */
        ACCEPTED,

        /**
         * Signal was accepted and workflow resumed execution.
         */
        WORKFLOW_RESUMED,

        /**
         * Signal was rejected due to invalid state.
         */
        REJECTED_INVALID_STATE,

        /**
         * Signal was rejected because target was not found.
         */
        REJECTED_NOT_FOUND,

        /**
         * Signal was rejected due to authorization failure.
         */
        REJECTED_UNAUTHORIZED,

        /**
         * Signal was rejected due to validation failure.
         */
        REJECTED_VALIDATION_FAILED,

        /**
         * Signal was rejected because it has expired.
         */
        REJECTED_EXPIRED,

        /**
         * Signal was rejected because task is already resolved.
         */
        REJECTED_ALREADY_RESOLVED,

        /**
         * Signal processing failed due to internal error.
         */
        FAILED_INTERNAL_ERROR,

        /**
         * Signal was queued for async processing.
         */
        QUEUED
    }
}
