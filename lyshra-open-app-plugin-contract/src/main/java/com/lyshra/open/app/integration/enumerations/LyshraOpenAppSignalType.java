package com.lyshra.open.app.integration.enumerations;

/**
 * Defines the types of external signals that can be sent to a waiting workflow.
 * Signals are used to resume workflow execution or change its state.
 */
public enum LyshraOpenAppSignalType {

    /**
     * Signal to approve a human task.
     */
    APPROVE,

    /**
     * Signal to reject a human task.
     */
    REJECT,

    /**
     * Signal to complete a human task with custom data.
     */
    COMPLETE,

    /**
     * Signal indicating the task has timed out.
     */
    TIMEOUT,

    /**
     * Signal to escalate the task.
     */
    ESCALATE,

    /**
     * Signal to cancel the task.
     */
    CANCEL,

    /**
     * Signal to pause workflow execution.
     */
    PAUSE,

    /**
     * Signal to resume workflow execution.
     */
    RESUME,

    /**
     * Signal to retry the current step.
     */
    RETRY,

    /**
     * Signal to skip the current step and proceed to next.
     */
    SKIP,

    /**
     * Custom signal for extensibility.
     */
    CUSTOM
}
