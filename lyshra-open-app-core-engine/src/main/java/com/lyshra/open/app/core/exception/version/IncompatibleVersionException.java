package com.lyshra.open.app.core.exception.version;

import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;

/**
 * Exception thrown when attempting to migrate between incompatible versions.
 */
public class IncompatibleVersionException extends WorkflowVersionException {

    private final IWorkflowVersion sourceVersion;

    public IncompatibleVersionException(
            String workflowId,
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion) {
        super("Incompatible versions: " + workflowId +
              " cannot migrate from v" + sourceVersion.toVersionString() +
              " to v" + targetVersion.toVersionString(),
                workflowId, targetVersion);
        this.sourceVersion = sourceVersion;
    }

    public IWorkflowVersion getSourceVersion() {
        return sourceVersion;
    }
}
