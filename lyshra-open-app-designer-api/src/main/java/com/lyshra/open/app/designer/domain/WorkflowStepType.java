package com.lyshra.open.app.designer.domain;

/**
 * Types of workflow steps.
 */
public enum WorkflowStepType {
    /**
     * Step executes a processor.
     */
    PROCESSOR,

    /**
     * Step calls another workflow.
     */
    WORKFLOW
}
