package com.lyshra.open.app.integration.enumerations;

/**
 * Represents the status of a human task within a workflow.
 * Used to track the lifecycle of human interaction steps.
 */
public enum LyshraOpenAppHumanTaskStatus {

    /**
     * Task has been created but not yet assigned or claimed.
     */
    CREATED,

    /**
     * Task is waiting for human action.
     */
    PENDING,

    /**
     * Task has been assigned to a specific user or group.
     */
    ASSIGNED,

    /**
     * Task has been claimed by a user and is being worked on.
     */
    IN_PROGRESS,

    /**
     * Task has been approved.
     */
    APPROVED,

    /**
     * Task has been rejected.
     */
    REJECTED,

    /**
     * Task has been completed with custom data/action.
     */
    COMPLETED,

    /**
     * Task has timed out without human action.
     */
    TIMED_OUT,

    /**
     * Task has been escalated due to timeout or other conditions.
     */
    ESCALATED,

    /**
     * Task has been cancelled.
     */
    CANCELLED,

    /**
     * Task has expired and is no longer actionable.
     */
    EXPIRED
}
