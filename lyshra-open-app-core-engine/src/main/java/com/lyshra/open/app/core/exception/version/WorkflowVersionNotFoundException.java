package com.lyshra.open.app.core.exception.version;

import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;

/**
 * Exception thrown when a workflow version is not found.
 */
public class WorkflowVersionNotFoundException extends WorkflowVersionException {

    public WorkflowVersionNotFoundException(String workflowId, IWorkflowVersion version) {
        super("Workflow version not found: " + workflowId + " v" + version.toVersionString(),
                workflowId, version);
    }

    public WorkflowVersionNotFoundException(String workflowId, String versionSpec) {
        super("Workflow version not found: " + workflowId + " spec=" + versionSpec, workflowId, null);
    }
}
