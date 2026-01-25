package com.lyshra.open.app.designer.domain;

/**
 * Strategies for handling errors in workflow execution.
 */
public enum ErrorStrategy {
    /**
     * End the workflow gracefully without propagating the error.
     */
    END_WORKFLOW,

    /**
     * Abort the workflow and propagate the error.
     */
    ABORT_WORKFLOW,

    /**
     * Use a fallback step to handle the error.
     */
    USE_FALLBACK_STEP
}
