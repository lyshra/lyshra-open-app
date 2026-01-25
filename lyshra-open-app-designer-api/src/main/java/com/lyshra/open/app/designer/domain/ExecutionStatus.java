package com.lyshra.open.app.designer.domain;

/**
 * Status of a workflow execution.
 */
public enum ExecutionStatus {
    /**
     * Execution is pending and has not started yet.
     */
    PENDING,

    /**
     * Execution is currently running.
     */
    RUNNING,

    /**
     * Execution is waiting for an external event or condition.
     */
    WAITING,

    /**
     * Execution is retrying after a failure.
     */
    RETRYING,

    /**
     * Execution completed successfully.
     */
    COMPLETED,

    /**
     * Execution failed with an error.
     */
    FAILED,

    /**
     * Execution was aborted.
     */
    ABORTED,

    /**
     * Execution was cancelled by user.
     */
    CANCELLED
}
