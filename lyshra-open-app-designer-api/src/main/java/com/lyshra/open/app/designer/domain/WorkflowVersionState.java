package com.lyshra.open.app.designer.domain;

/**
 * States for workflow versions.
 */
public enum WorkflowVersionState {
    /**
     * Version is in draft state and not yet published.
     */
    DRAFT,

    /**
     * Version is active and being used for execution.
     */
    ACTIVE,

    /**
     * Version was previously active but has been superseded by a newer version.
     */
    DEPRECATED,

    /**
     * Version is archived and cannot be used.
     */
    ARCHIVED
}
