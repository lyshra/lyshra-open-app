package com.lyshra.open.app.core.exception.version;

import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationResult;

/**
 * Exception thrown when workflow migration fails.
 */
public class MigrationException extends WorkflowVersionException {

    private final String executionId;
    private final IWorkflowVersion sourceVersion;
    private final IWorkflowVersion targetVersion;
    private final IMigrationResult.Status status;

    public MigrationException(
            String message,
            String executionId,
            String workflowId,
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion,
            IMigrationResult.Status status) {
        super(message, workflowId, targetVersion);
        this.executionId = executionId;
        this.sourceVersion = sourceVersion;
        this.targetVersion = targetVersion;
        this.status = status;
    }

    public MigrationException(
            String message,
            String executionId,
            String workflowId,
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion,
            IMigrationResult.Status status,
            Throwable cause) {
        super(message, workflowId, targetVersion, cause);
        this.executionId = executionId;
        this.sourceVersion = sourceVersion;
        this.targetVersion = targetVersion;
        this.status = status;
    }

    public String getExecutionId() {
        return executionId;
    }

    public IWorkflowVersion getSourceVersion() {
        return sourceVersion;
    }

    public IWorkflowVersion getTargetVersion() {
        return targetVersion;
    }

    public IMigrationResult.Status getStatus() {
        return status;
    }
}
