package com.lyshra.open.app.integration.models.workflow;

import com.lyshra.open.app.integration.enumerations.LyshraOpenAppWorkflowExecutionState;
import lombok.Builder;
import lombok.Data;
import lombok.With;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Represents the persisted state of a workflow instance.
 * This model captures all information needed to resume workflow execution
 * after a pause, restart, or crash.
 *
 * <p>Design Pattern: Memento Pattern
 * - Captures workflow state at a point in time
 * - Allows restoration of workflow to that state
 * - Immutable snapshots with builder for modifications
 *
 * <h2>State Components</h2>
 * <ul>
 *   <li><b>Identity:</b> Instance ID, workflow definition, version</li>
 *   <li><b>Execution State:</b> Current status, current step, execution history</li>
 *   <li><b>Context Data:</b> Variables, data payload, metadata</li>
 *   <li><b>Suspension Info:</b> Human task ID, suspension reason, timestamps</li>
 *   <li><b>Audit Trail:</b> State change history for debugging and compliance</li>
 * </ul>
 */
@Data
@Builder(toBuilder = true)
@With
public class LyshraOpenAppWorkflowInstanceState implements Serializable {

    private static final long serialVersionUID = 1L;

    // ========================================================================
    // IDENTITY FIELDS
    // ========================================================================

    /**
     * Unique identifier for this workflow instance.
     * Format: UUID or custom format based on configuration.
     */
    private final String instanceId;

    /**
     * The workflow definition identifier (organization/module/version/name).
     */
    private final String workflowDefinitionId;

    /**
     * The workflow name for quick reference.
     */
    private final String workflowName;

    /**
     * Version of the workflow definition when this instance was created.
     */
    private final String workflowVersion;

    // ========================================================================
    // EXECUTION STATE
    // ========================================================================

    /**
     * Current execution status of the workflow.
     */
    private final LyshraOpenAppWorkflowExecutionState status;

    /**
     * The current step where the workflow is paused or will resume from.
     */
    private final String currentStepId;

    /**
     * The step that was being executed when suspension occurred.
     */
    private final String suspendedAtStepId;

    /**
     * Ordered list of steps that have been executed.
     */
    @Builder.Default
    private final List<ExecutedStepInfo> executionHistory = new ArrayList<>();

    /**
     * The branch that led to the current step (for resumption routing).
     */
    private final String pendingBranch;

    // ========================================================================
    // CONTEXT DATA
    // ========================================================================

    /**
     * The workflow context data (main payload).
     * Serialized as JSON for storage.
     */
    @Builder.Default
    private final Map<String, Object> contextData = new HashMap<>();

    /**
     * Workflow variables (named values for expression evaluation).
     */
    @Builder.Default
    private final Map<String, Object> variables = new HashMap<>();

    /**
     * Custom metadata for extensibility.
     */
    @Builder.Default
    private final Map<String, Object> metadata = new HashMap<>();

    // ========================================================================
    // SUSPENSION INFORMATION
    // ========================================================================

    /**
     * Human task ID if workflow is waiting for human action.
     */
    private final String humanTaskId;

    /**
     * Reason for suspension (human task, error, admin pause, etc.).
     */
    private final SuspensionReason suspensionReason;

    /**
     * Human-readable description of why workflow is suspended.
     */
    private final String suspensionDescription;

    // ========================================================================
    // TIMESTAMPS
    // ========================================================================

    /**
     * When this workflow instance was created.
     */
    private final Instant createdAt;

    /**
     * When this workflow instance was last updated.
     */
    private final Instant updatedAt;

    /**
     * When the workflow started execution.
     */
    private final Instant startedAt;

    /**
     * When the workflow was suspended (if applicable).
     */
    private final Instant suspendedAt;

    /**
     * When the workflow completed (if applicable).
     */
    private final Instant completedAt;

    /**
     * Scheduled time for automatic resumption (e.g., after timeout).
     */
    private final Instant scheduledResumeAt;

    // ========================================================================
    // CORRELATION AND MULTI-TENANCY
    // ========================================================================

    /**
     * Business key for domain-specific correlation.
     */
    private final String businessKey;

    /**
     * Correlation ID for distributed tracing.
     */
    private final String correlationId;

    /**
     * Tenant identifier for multi-tenant deployments.
     */
    private final String tenantId;

    /**
     * User who initiated this workflow.
     */
    private final String initiatedBy;

    // ========================================================================
    // ERROR INFORMATION
    // ========================================================================

    /**
     * Error information if workflow failed.
     */
    private final ErrorInfo errorInfo;

    /**
     * Number of retry attempts for the current step.
     */
    @Builder.Default
    private final int retryCount = 0;

    /**
     * Maximum retry attempts allowed.
     */
    @Builder.Default
    private final int maxRetries = 3;

    // ========================================================================
    // STATE CHANGE HISTORY
    // ========================================================================

    /**
     * Audit trail of state changes.
     */
    @Builder.Default
    private final List<StateChangeEntry> stateHistory = new ArrayList<>();

    // ========================================================================
    // NESTED CLASSES
    // ========================================================================

    /**
     * Information about an executed step.
     */
    @Data
    @Builder
    public static class ExecutedStepInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String stepId;
        private final String processorId;
        private final Instant startedAt;
        private final Instant completedAt;
        private final String resultBranch;
        private final Map<String, Object> outputData;
        private final boolean success;
        private final String errorMessage;
    }

    /**
     * Reasons for workflow suspension.
     */
    public enum SuspensionReason {
        /** Waiting for human task completion */
        HUMAN_TASK,
        /** Administratively paused */
        ADMIN_PAUSE,
        /** Waiting for external event/signal */
        EXTERNAL_SIGNAL,
        /** Scheduled delay */
        SCHEDULED_DELAY,
        /** Error requiring intervention */
        ERROR_INTERVENTION,
        /** Rate limiting */
        RATE_LIMITED,
        /** Waiting for resource availability */
        RESOURCE_WAIT
    }

    /**
     * Error information for failed workflows.
     */
    @Data
    @Builder
    public static class ErrorInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String errorCode;
        private final String errorMessage;
        private final String errorType;
        private final String stackTrace;
        private final String failedStepId;
        private final Instant occurredAt;
        private final Map<String, Object> errorContext;
    }

    /**
     * Entry in the state change history.
     */
    @Data
    @Builder
    public static class StateChangeEntry implements Serializable {
        private static final long serialVersionUID = 1L;

        private final Instant timestamp;
        private final LyshraOpenAppWorkflowExecutionState previousState;
        private final LyshraOpenAppWorkflowExecutionState newState;
        private final String changedBy;
        private final String reason;
        private final String stepId;
        private final Map<String, Object> additionalInfo;
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Checks if the workflow can be resumed.
     */
    public boolean canResume() {
        return status == LyshraOpenAppWorkflowExecutionState.WAITING
                || status == LyshraOpenAppWorkflowExecutionState.PAUSED;
    }

    /**
     * Checks if the workflow is in a terminal state.
     */
    public boolean isTerminal() {
        return status == LyshraOpenAppWorkflowExecutionState.COMPLETED
                || status == LyshraOpenAppWorkflowExecutionState.FAILED
                || status == LyshraOpenAppWorkflowExecutionState.CANCELLED
                || status == LyshraOpenAppWorkflowExecutionState.TIMED_OUT;
    }

    /**
     * Checks if the workflow is waiting for human task.
     */
    public boolean isWaitingForHumanTask() {
        return status == LyshraOpenAppWorkflowExecutionState.WAITING
                && suspensionReason == SuspensionReason.HUMAN_TASK
                && humanTaskId != null;
    }

    /**
     * Gets the duration since suspension.
     */
    public Optional<java.time.Duration> getSuspensionDuration() {
        if (suspendedAt == null) {
            return Optional.empty();
        }
        return Optional.of(java.time.Duration.between(suspendedAt, Instant.now()));
    }

    /**
     * Creates a new state with updated status and timestamp.
     */
    public LyshraOpenAppWorkflowInstanceState withStatusChange(
            LyshraOpenAppWorkflowExecutionState newStatus,
            String changedBy,
            String reason) {

        List<StateChangeEntry> newHistory = new ArrayList<>(this.stateHistory);
        newHistory.add(StateChangeEntry.builder()
                .timestamp(Instant.now())
                .previousState(this.status)
                .newState(newStatus)
                .changedBy(changedBy)
                .reason(reason)
                .stepId(this.currentStepId)
                .build());

        return this.toBuilder()
                .status(newStatus)
                .updatedAt(Instant.now())
                .stateHistory(newHistory)
                .build();
    }

    /**
     * Creates a new state for human task suspension.
     */
    public LyshraOpenAppWorkflowInstanceState suspendForHumanTask(
            String taskId,
            String stepId,
            String description) {

        return this.toBuilder()
                .status(LyshraOpenAppWorkflowExecutionState.WAITING)
                .humanTaskId(taskId)
                .suspendedAtStepId(stepId)
                .currentStepId(stepId)
                .suspensionReason(SuspensionReason.HUMAN_TASK)
                .suspensionDescription(description)
                .suspendedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Creates a new state for workflow resumption.
     */
    public LyshraOpenAppWorkflowInstanceState resume(String nextStepId, String branch) {
        return this.toBuilder()
                .status(LyshraOpenAppWorkflowExecutionState.RUNNING)
                .currentStepId(nextStepId)
                .pendingBranch(branch)
                .humanTaskId(null)
                .suspensionReason(null)
                .suspensionDescription(null)
                .suspendedAt(null)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Creates a new state for workflow completion.
     */
    public LyshraOpenAppWorkflowInstanceState complete() {
        return this.toBuilder()
                .status(LyshraOpenAppWorkflowExecutionState.COMPLETED)
                .completedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Creates a new state for workflow failure.
     */
    public LyshraOpenAppWorkflowInstanceState fail(ErrorInfo error) {
        return this.toBuilder()
                .status(LyshraOpenAppWorkflowExecutionState.FAILED)
                .errorInfo(error)
                .completedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
