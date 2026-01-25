package com.lyshra.open.app.designer.domain;

/**
 * Status of a step execution within a workflow.
 */
public enum StepExecutionStatus {
    /**
     * Step is pending execution.
     */
    PENDING,

    /**
     * Step is currently executing.
     */
    RUNNING,

    /**
     * Step completed successfully.
     */
    COMPLETED,

    /**
     * Step failed with an error.
     */
    FAILED,

    /**
     * Step was skipped due to conditional branching.
     */
    SKIPPED,

    /**
     * Step was aborted.
     */
    ABORTED,

    /**
     * Step is being retried.
     */
    RETRYING
}
