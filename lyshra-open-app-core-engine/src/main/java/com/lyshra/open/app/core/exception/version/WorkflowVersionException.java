package com.lyshra.open.app.core.exception.version;

import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;

/**
 * Base exception for workflow versioning errors.
 */
public class WorkflowVersionException extends RuntimeException {

    private final String workflowId;
    private final IWorkflowVersion version;

    public WorkflowVersionException(String message, String workflowId, IWorkflowVersion version) {
        super(message);
        this.workflowId = workflowId;
        this.version = version;
    }

    public WorkflowVersionException(String message, String workflowId, IWorkflowVersion version, Throwable cause) {
        super(message, cause);
        this.workflowId = workflowId;
        this.version = version;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public IWorkflowVersion getVersion() {
        return version;
    }
}
