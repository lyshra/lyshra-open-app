package com.lyshra.open.app.integration.enumerations;

/**
 * Represents the execution state of a workflow instance.
 * Supports standard execution states as well as human-in-the-loop states
 * for workflows that require manual intervention.
 */
public enum LyshraOpenAppWorkflowExecutionState {

    /**
     * Workflow is currently executing.
     */
    RUNNING,

    /**
     * Workflow is waiting for human interaction (approval, input, etc.).
     * This state indicates the workflow has been paused at a human task step.
     */
    WAITING,

    /**
     * Workflow has been explicitly paused by an external signal.
     * Different from WAITING in that it's an administrative action rather than
     * a workflow step that requires human input.
     */
    PAUSED,

    /**
     * Workflow has completed successfully.
     */
    COMPLETED,

    /**
     * Workflow has failed due to an error.
     */
    FAILED,

    /**
     * Workflow was cancelled/aborted.
     */
    CANCELLED,

    /**
     * Workflow waiting step has timed out waiting for human interaction.
     */
    TIMED_OUT
}
