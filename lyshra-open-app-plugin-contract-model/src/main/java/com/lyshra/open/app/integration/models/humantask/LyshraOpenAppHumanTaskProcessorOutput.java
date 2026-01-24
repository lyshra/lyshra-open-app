package com.lyshra.open.app.integration.models.humantask;

import com.lyshra.open.app.integration.constant.LyshraOpenAppConstants;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorResult;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * Specialized processor output for human task processors.
 * Carries additional information needed to suspend workflow and create human tasks.
 *
 * <p>When a human task processor executes, it returns this output which:
 * <ul>
 *   <li>Signals the workflow engine to suspend execution</li>
 *   <li>Carries the created task ID for correlation</li>
 *   <li>Preserves workflow state for later resumption</li>
 * </ul>
 *
 * <p>Design Pattern: Extension of Value Object Pattern
 * - Immutable output carrying suspension state
 * - Self-documenting through specialized factory methods
 */
@Data
public class LyshraOpenAppHumanTaskProcessorOutput implements ILyshraOpenAppProcessorResult, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The branch for workflow routing. For suspension, this is WAITING.
     * After task completion, this becomes the actual outcome (APPROVED, REJECTED, etc.)
     */
    private final String branch;

    /**
     * Additional data associated with the output.
     */
    private final Object data;

    /**
     * The unique identifier of the created human task.
     */
    private final String taskId;

    /**
     * The workflow instance ID that is being suspended.
     */
    private final String workflowInstanceId;

    /**
     * The workflow step ID where suspension occurred.
     */
    private final String workflowStepId;

    /**
     * Indicates whether the workflow should be suspended.
     */
    private final boolean suspended;

    /**
     * Optional context data to preserve for resumption.
     */
    private final Map<String, Object> preservedContext;

    private LyshraOpenAppHumanTaskProcessorOutput(
            String branch,
            Object data,
            String taskId,
            String workflowInstanceId,
            String workflowStepId,
            boolean suspended,
            Map<String, Object> preservedContext) {
        this.branch = branch;
        this.data = data;
        this.taskId = taskId;
        this.workflowInstanceId = workflowInstanceId;
        this.workflowStepId = workflowStepId;
        this.suspended = suspended;
        this.preservedContext = preservedContext;
    }

    /**
     * Creates a suspension output indicating workflow should pause for human task.
     *
     * @param taskId the created human task ID
     * @param workflowInstanceId the workflow instance being suspended
     * @param workflowStepId the step where suspension occurred
     * @return output indicating suspension
     */
    public static LyshraOpenAppHumanTaskProcessorOutput ofSuspended(
            String taskId,
            String workflowInstanceId,
            String workflowStepId) {
        return new LyshraOpenAppHumanTaskProcessorOutput(
                LyshraOpenAppConstants.WAITING_BRANCH,
                null,
                taskId,
                workflowInstanceId,
                workflowStepId,
                true,
                null
        );
    }

    /**
     * Creates a suspension output with preserved context.
     *
     * @param taskId the created human task ID
     * @param workflowInstanceId the workflow instance being suspended
     * @param workflowStepId the step where suspension occurred
     * @param preservedContext context data to preserve for resumption
     * @return output indicating suspension with context
     */
    public static LyshraOpenAppHumanTaskProcessorOutput ofSuspendedWithContext(
            String taskId,
            String workflowInstanceId,
            String workflowStepId,
            Map<String, Object> preservedContext) {
        return new LyshraOpenAppHumanTaskProcessorOutput(
                LyshraOpenAppConstants.WAITING_BRANCH,
                null,
                taskId,
                workflowInstanceId,
                workflowStepId,
                true,
                preservedContext
        );
    }

    /**
     * Creates a resumed output after human task completion.
     *
     * @param branch the outcome branch (APPROVED, REJECTED, etc.)
     * @param data the task result data
     * @param taskId the completed task ID
     * @return output for workflow resumption
     */
    public static LyshraOpenAppHumanTaskProcessorOutput ofResumed(
            String branch,
            Object data,
            String taskId) {
        return new LyshraOpenAppHumanTaskProcessorOutput(
                branch,
                data,
                taskId,
                null,
                null,
                false,
                null
        );
    }

    /**
     * Creates an approved output.
     *
     * @param taskId the completed task ID
     * @param data optional result data
     * @return output for approved outcome
     */
    public static LyshraOpenAppHumanTaskProcessorOutput ofApproved(String taskId, Object data) {
        return ofResumed(LyshraOpenAppConstants.APPROVED_BRANCH, data, taskId);
    }

    /**
     * Creates a rejected output.
     *
     * @param taskId the completed task ID
     * @param data optional result data
     * @return output for rejected outcome
     */
    public static LyshraOpenAppHumanTaskProcessorOutput ofRejected(String taskId, Object data) {
        return ofResumed(LyshraOpenAppConstants.REJECTED_BRANCH, data, taskId);
    }

    /**
     * Creates a completed output with form data.
     *
     * @param taskId the completed task ID
     * @param data the submitted form data
     * @return output for completed outcome
     */
    public static LyshraOpenAppHumanTaskProcessorOutput ofCompleted(String taskId, Object data) {
        return ofResumed(LyshraOpenAppConstants.COMPLETED_BRANCH, data, taskId);
    }

    /**
     * Creates a timed out output.
     *
     * @param taskId the timed out task ID
     * @return output for timeout outcome
     */
    public static LyshraOpenAppHumanTaskProcessorOutput ofTimedOut(String taskId) {
        return ofResumed(LyshraOpenAppConstants.TIMED_OUT_BRANCH, null, taskId);
    }

    /**
     * Creates a cancelled output.
     *
     * @param taskId the cancelled task ID
     * @return output for cancelled outcome
     */
    public static LyshraOpenAppHumanTaskProcessorOutput ofCancelled(String taskId) {
        return ofResumed(LyshraOpenAppConstants.CANCELLED_BRANCH, null, taskId);
    }

    /**
     * Creates an output for a custom decision branch.
     *
     * @param decisionValue the selected decision value (becomes branch name)
     * @param taskId the completed task ID
     * @param data optional result data
     * @return output for custom decision outcome
     */
    public static LyshraOpenAppHumanTaskProcessorOutput ofDecision(
            String decisionValue,
            String taskId,
            Object data) {
        return ofResumed(decisionValue, data, taskId);
    }

    /**
     * Checks if this output represents a workflow suspension.
     *
     * @return true if workflow should be suspended
     */
    public boolean isWaiting() {
        return suspended || LyshraOpenAppConstants.WAITING_BRANCH.equals(branch);
    }
}
