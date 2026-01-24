package com.lyshra.open.app.core.exception.version;

import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;

/**
 * Exception thrown when attempting to register a version that already exists.
 */
public class WorkflowVersionAlreadyExistsException extends WorkflowVersionException {

    public WorkflowVersionAlreadyExistsException(String workflowId, IWorkflowVersion version) {
        super("Workflow version already exists: " + workflowId + " v" + version.toVersionString(),
                workflowId, version);
    }
}
