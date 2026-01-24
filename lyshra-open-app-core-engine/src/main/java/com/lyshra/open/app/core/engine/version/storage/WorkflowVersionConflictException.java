package com.lyshra.open.app.core.engine.version.storage;

import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;

/**
 * Exception thrown when attempting to store a workflow version that already exists.
 * This prevents accidental overwriting of existing versions.
 */
public class WorkflowVersionConflictException extends RuntimeException {

    private final String workflowId;
    private final IWorkflowVersion version;
    private final String existingSchemaHash;
    private final String newSchemaHash;

    public WorkflowVersionConflictException(
            String workflowId,
            IWorkflowVersion version,
            String existingSchemaHash,
            String newSchemaHash) {
        super(String.format(
                "Workflow version conflict: %s:%s already exists. " +
                "Existing hash: %s, New hash: %s. " +
                "Increment the version number to store a new definition.",
                workflowId,
                version.toVersionString(),
                existingSchemaHash != null ? existingSchemaHash.substring(0, 8) + "..." : "unknown",
                newSchemaHash != null ? newSchemaHash.substring(0, 8) + "..." : "unknown"));
        this.workflowId = workflowId;
        this.version = version;
        this.existingSchemaHash = existingSchemaHash;
        this.newSchemaHash = newSchemaHash;
    }

    public WorkflowVersionConflictException(String workflowId, IWorkflowVersion version) {
        this(workflowId, version, null, null);
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public IWorkflowVersion getVersion() {
        return version;
    }

    public String getExistingSchemaHash() {
        return existingSchemaHash;
    }

    public String getNewSchemaHash() {
        return newSchemaHash;
    }

    /**
     * Checks if this is a content conflict (same version, different content).
     *
     * @return true if content differs
     */
    public boolean isContentConflict() {
        return existingSchemaHash != null &&
               newSchemaHash != null &&
               !existingSchemaHash.equals(newSchemaHash);
    }
}
