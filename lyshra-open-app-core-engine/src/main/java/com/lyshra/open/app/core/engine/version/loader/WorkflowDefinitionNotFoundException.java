package com.lyshra.open.app.core.engine.version.loader;

import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;

/**
 * Exception thrown when a requested workflow definition version cannot be found.
 */
public class WorkflowDefinitionNotFoundException extends RuntimeException {

    private final String workflowId;
    private final String requestedVersion;

    public WorkflowDefinitionNotFoundException(String workflowId, String requestedVersion, String message) {
        super(message);
        this.workflowId = workflowId;
        this.requestedVersion = requestedVersion;
    }

    public WorkflowDefinitionNotFoundException(String workflowId, IWorkflowVersion version) {
        this(workflowId, version.toVersionString(),
                String.format("Workflow definition not found: %s:%s", workflowId, version.toVersionString()));
    }

    public WorkflowDefinitionNotFoundException(String workflowId, String requestedVersion) {
        this(workflowId, requestedVersion,
                String.format("Workflow definition not found: %s:%s", workflowId, requestedVersion));
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public String getRequestedVersion() {
        return requestedVersion;
    }
}
