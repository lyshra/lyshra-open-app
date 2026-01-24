package com.lyshra.open.app.integration.contract.workflow;

import com.lyshra.open.app.integration.enumerations.LyshraOpenAppWorkflowExecutionState;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a running or suspended workflow instance.
 * This interface provides access to the complete state of a workflow execution,
 * enabling pause, resume, and recovery operations.
 *
 * <p>Design Pattern: Memento Pattern
 * - Captures and externalizes workflow state without violating encapsulation
 * - Enables checkpoint/restore functionality for long-running workflows
 * - Supports distributed execution with state transfer
 */
public interface ILyshraOpenAppWorkflowInstance {

    /**
     * Unique identifier for this workflow instance.
     */
    String getInstanceId();

    /**
     * The workflow definition identifier.
     */
    ILyshraOpenAppWorkflowIdentifier getWorkflowIdentifier();

    /**
     * Current execution state of the workflow.
     */
    LyshraOpenAppWorkflowExecutionState getExecutionState();

    /**
     * Current step being executed or suspended at.
     */
    String getCurrentStep();

    /**
     * The serialized context data (workflow variables and data).
     */
    Map<String, Object> getContextData();

    /**
     * The serialized context variables.
     */
    Map<String, Object> getContextVariables();

    /**
     * When the workflow instance was created.
     */
    Instant getCreatedAt();

    /**
     * When the workflow instance was last updated.
     */
    Instant getUpdatedAt();

    /**
     * When the workflow instance was started.
     */
    Instant getStartedAt();

    /**
     * When the workflow instance was completed (if completed).
     */
    Optional<Instant> getCompletedAt();

    /**
     * When the workflow instance was suspended (if waiting/paused).
     */
    Optional<Instant> getSuspendedAt();

    /**
     * The active human task ID (if in WAITING state).
     */
    Optional<String> getActiveHumanTaskId();

    /**
     * List of all human task IDs created by this workflow instance.
     */
    List<String> getHumanTaskIds();

    /**
     * Parent workflow instance ID (for nested workflow calls).
     */
    Optional<String> getParentInstanceId();

    /**
     * Child workflow instance IDs (for nested workflow calls).
     */
    List<String> getChildInstanceIds();

    /**
     * Error information if the workflow failed.
     */
    Optional<ILyshraOpenAppWorkflowError> getError();

    /**
     * Correlation ID for tracing across systems.
     */
    Optional<String> getCorrelationId();

    /**
     * Business key for domain-specific lookup.
     */
    Optional<String> getBusinessKey();

    /**
     * Tenant/organization identifier for multi-tenancy.
     */
    Optional<String> getTenantId();

    /**
     * User who initiated the workflow.
     */
    Optional<String> getInitiatedBy();

    /**
     * Custom metadata for extensibility.
     */
    Map<String, Object> getMetadata();

    /**
     * Execution history/checkpoints.
     */
    List<ILyshraOpenAppWorkflowCheckpoint> getCheckpoints();

    /**
     * Version for optimistic locking.
     */
    long getVersion();

    /**
     * Represents an error that occurred during workflow execution.
     */
    interface ILyshraOpenAppWorkflowError {

        String getErrorCode();

        String getErrorMessage();

        String getFailedStep();

        Instant getOccurredAt();

        Optional<String> getStackTrace();

        Map<String, Object> getErrorContext();
    }

    /**
     * Represents a checkpoint in workflow execution history.
     */
    interface ILyshraOpenAppWorkflowCheckpoint {

        String getCheckpointId();

        String getStepName();

        LyshraOpenAppWorkflowExecutionState getState();

        Instant getTimestamp();

        Map<String, Object> getContextSnapshot();

        Optional<String> getHumanTaskId();

        String getAction();
    }
}
