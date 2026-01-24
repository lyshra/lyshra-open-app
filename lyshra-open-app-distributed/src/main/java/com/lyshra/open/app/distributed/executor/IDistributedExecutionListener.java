package com.lyshra.open.app.distributed.executor;

import com.lyshra.open.app.distributed.state.WorkflowExecutionState;

/**
 * Listener interface for distributed workflow execution lifecycle events.
 *
 * Implementations can track:
 * - Workflow start/completion
 * - Step progression
 * - Failures and retries
 * - Recovery events
 *
 * Design Pattern: Observer Pattern - decouples execution events from handlers.
 */
public interface IDistributedExecutionListener {

    /**
     * Called when a workflow execution is submitted.
     *
     * @param executionKey the execution key
     * @param state the initial state
     */
    default void onExecutionSubmitted(String executionKey, WorkflowExecutionState state) {}

    /**
     * Called when a workflow execution starts running.
     *
     * @param executionKey the execution key
     * @param state the current state
     */
    default void onExecutionStarted(String executionKey, WorkflowExecutionState state) {}

    /**
     * Called when a workflow step completes.
     *
     * @param executionKey the execution key
     * @param stepName the completed step name
     * @param nextStep the next step to execute
     */
    default void onStepCompleted(String executionKey, String stepName, String nextStep) {}

    /**
     * Called when a workflow execution completes successfully.
     *
     * @param executionKey the execution key
     * @param result the execution result
     */
    default void onExecutionCompleted(String executionKey, DistributedExecutionResult result) {}

    /**
     * Called when a workflow execution fails.
     *
     * @param executionKey the execution key
     * @param result the execution result
     */
    default void onExecutionFailed(String executionKey, DistributedExecutionResult result) {}

    /**
     * Called when a workflow execution is paused.
     *
     * @param executionKey the execution key
     * @param state the current state
     */
    default void onExecutionPaused(String executionKey, WorkflowExecutionState state) {}

    /**
     * Called when a workflow execution is cancelled.
     *
     * @param executionKey the execution key
     */
    default void onExecutionCancelled(String executionKey) {}

    /**
     * Called when a workflow execution is recovered from a failed node.
     *
     * @param executionKey the execution key
     * @param failedNodeId the failed node's ID
     * @param newNodeId the new owner node's ID
     */
    default void onExecutionRecovered(String executionKey, String failedNodeId, String newNodeId) {}

    /**
     * Called when a step execution fails and will be retried.
     *
     * @param executionKey the execution key
     * @param stepName the failed step
     * @param retryCount the current retry count
     * @param error the error that caused the failure
     */
    default void onStepRetry(String executionKey, String stepName, int retryCount, Throwable error) {}
}
