package com.lyshra.open.app.core.engine.node;

import com.lyshra.open.app.core.engine.lock.WorkflowLock;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskResult;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowInstance;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

/**
 * Service interface for resuming suspended workflows.
 * Handles the continuation of workflow execution after human tasks are resolved.
 *
 * <p>Design Pattern: Command Pattern (Continuation)
 * - Encapsulates the resumption logic
 * - Supports different resumption scenarios (approval, rejection, completion, timeout)
 *
 * <p>Responsibilities:
 * - Load workflow state from persistence
 * - Reconstruct execution context
 * - Determine next step based on signal/result
 * - Continue workflow execution
 */
public interface ILyshraOpenAppWorkflowResumptionService {

    /**
     * Resumes a workflow from a human task result.
     *
     * @param instance the workflow instance to resume
     * @param taskResult the result of the human task
     * @return the final workflow context after execution completes
     */
    Mono<ILyshraOpenAppContext> resumeFromHumanTask(
            ILyshraOpenAppWorkflowInstance instance,
            ILyshraOpenAppHumanTaskResult taskResult);

    /**
     * Resumes a workflow with a specific branch and additional data.
     *
     * @param instance the workflow instance to resume
     * @param branch the branch to follow (APPROVED, REJECTED, DEFAULT, etc.)
     * @param additionalData data to merge into context
     * @return the final workflow context after execution completes
     */
    Mono<ILyshraOpenAppContext> resumeWithBranch(
            ILyshraOpenAppWorkflowInstance instance,
            String branch,
            Map<String, Object> additionalData);

    /**
     * Resumes a paused workflow from its current state.
     * Used when resuming administratively paused workflows.
     *
     * @param instance the workflow instance to resume
     * @return the final workflow context after execution completes
     */
    Mono<ILyshraOpenAppContext> resumePausedWorkflow(ILyshraOpenAppWorkflowInstance instance);

    /**
     * Suspends a running workflow at its current position.
     *
     * @param workflowInstanceId the workflow instance to suspend
     * @param reason the reason for suspension
     * @return the suspended workflow instance
     */
    Mono<ILyshraOpenAppWorkflowInstance> suspendWorkflow(String workflowInstanceId, String reason);

    /**
     * Suspends a workflow at a human task step.
     * Called by human task processors when they create a task.
     *
     * @param workflowInstanceId the workflow instance ID
     * @param currentStep the current step where execution is suspended
     * @param humanTaskId the ID of the human task being waited on
     * @param context the current workflow context to persist
     * @return the suspended workflow instance
     */
    Mono<ILyshraOpenAppWorkflowInstance> suspendAtHumanTask(
            String workflowInstanceId,
            String currentStep,
            String humanTaskId,
            ILyshraOpenAppContext context);

    /**
     * Checks if a workflow can be resumed.
     *
     * @param workflowInstanceId the workflow instance ID
     * @return true if the workflow can be resumed
     */
    Mono<Boolean> canResume(String workflowInstanceId);

    /**
     * Gets the current state of a workflow instance.
     *
     * @param workflowInstanceId the workflow instance ID
     * @return the workflow instance
     */
    Mono<ILyshraOpenAppWorkflowInstance> getWorkflowInstance(String workflowInstanceId);

    // ========================================================================
    // LOCKING METHODS
    // ========================================================================

    /**
     * Checks if a workflow instance is currently locked for resume.
     *
     * @param workflowInstanceId the workflow instance ID
     * @return true if locked (resume in progress by another actor)
     */
    default Mono<Boolean> isLocked(String workflowInstanceId) {
        return Mono.just(false);
    }

    /**
     * Gets the current lock information for a workflow instance.
     *
     * @param workflowInstanceId the workflow instance ID
     * @return the lock info if locked, empty if not locked
     */
    default Mono<Optional<WorkflowLock>> getLockInfo(String workflowInstanceId) {
        return Mono.just(Optional.empty());
    }

    /**
     * Forces release of a workflow lock (admin operation).
     * Should be used with caution as it may cause inconsistent state
     * if the original operation is still running.
     *
     * @param workflowInstanceId the workflow instance ID
     * @param reason the reason for force release
     * @return true if a lock was released
     */
    default Mono<Boolean> forceReleaseLock(String workflowInstanceId, String reason) {
        return Mono.just(false);
    }
}
