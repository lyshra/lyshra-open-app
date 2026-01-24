package com.lyshra.open.app.integration.enumerations;

/**
 * Defines the types of human tasks that can be used in workflows.
 * Each type represents a different kind of human interaction pattern.
 */
public enum LyshraOpenAppHumanTaskType {

    /**
     * Simple approval task - requires approve or reject action.
     */
    APPROVAL,

    /**
     * Multi-option decision task - requires selection from predefined options.
     */
    DECISION,

    /**
     * Manual data input task - requires user to provide data/information.
     */
    MANUAL_INPUT,

    /**
     * Review task - requires human review of data/documents.
     */
    REVIEW,

    /**
     * Confirmation task - requires human confirmation before proceeding.
     */
    CONFIRMATION,

    /**
     * Multi-level approval task - requires approval from multiple parties.
     */
    MULTI_LEVEL_APPROVAL,

    /**
     * Parallel approval task - requires approval from all parties concurrently.
     */
    PARALLEL_APPROVAL,

    /**
     * Custom task type for extensibility.
     */
    CUSTOM
}
