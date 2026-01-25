package com.lyshra.open.app.designer.domain;

/**
 * Lifecycle states for workflow definitions.
 */
public enum WorkflowLifecycleState {
    /**
     * Workflow is in draft state and not yet published.
     */
    DRAFT,

    /**
     * Workflow is active and can be executed.
     */
    ACTIVE,

    /**
     * Workflow is deprecated but still executable for existing processes.
     */
    DEPRECATED,

    /**
     * Workflow is archived and cannot be executed.
     */
    ARCHIVED
}
