package com.lyshra.open.app.designer.domain;

/**
 * Context retention policy for workflow execution.
 */
public enum WorkflowContextRetention {
    /**
     * Retain full execution history and context.
     */
    FULL,

    /**
     * Retain only the latest context state.
     */
    LATEST
}
