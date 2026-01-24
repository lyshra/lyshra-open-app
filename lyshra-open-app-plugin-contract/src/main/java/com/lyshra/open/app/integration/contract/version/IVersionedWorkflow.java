package com.lyshra.open.app.integration.contract.version;

import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflow;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Extends workflow definition with version metadata and compatibility information.
 * Enables multiple versions of the same workflow to coexist safely.
 *
 * <p>This interface provides complete version tracking including:</p>
 * <ul>
 *   <li>workflowId - stable identifier across versions</li>
 *   <li>version - semantic version (MAJOR.MINOR.PATCH)</li>
 *   <li>schemaHash - SHA-256 hash of workflow definition for integrity</li>
 *   <li>createdAt - timestamp of version creation</li>
 *   <li>isActive - flag indicating if version accepts new executions</li>
 * </ul>
 *
 * <p>Design Pattern: Decorator pattern extending base workflow with versioning capabilities.</p>
 */
public interface IVersionedWorkflow extends ILyshraOpenAppWorkflow {

    /**
     * Returns the version metadata for this workflow.
     *
     * @return workflow version
     */
    IWorkflowVersion getVersion();

    /**
     * Returns the SHA-256 hash of this workflow's schema/definition.
     * Used for integrity verification and change detection.
     *
     * <p>The hash is computed from the canonical representation including
     * steps, processors, branching logic, and configurations.</p>
     *
     * @return 64-character hexadecimal SHA-256 hash
     */
    String getSchemaHash();

    /**
     * Returns the timestamp when this workflow version was created.
     *
     * @return creation timestamp in UTC
     */
    Instant getCreatedAt();

    /**
     * Indicates whether this workflow version is active and can accept new executions.
     *
     * <p>Inactive versions:</p>
     * <ul>
     *   <li>Cannot be used for new workflow executions</li>
     *   <li>Existing in-flight executions continue normally</li>
     *   <li>Can be reactivated if needed</li>
     * </ul>
     *
     * @return true if version is active
     */
    boolean isActive();

    /**
     * Returns the full version metadata for this workflow.
     * Provides comprehensive tracking information.
     *
     * @return version metadata if available
     */
    Optional<IWorkflowVersionMetadata> getVersionMetadata();

    /**
     * Returns the compatibility information for this workflow version.
     *
     * @return compatibility metadata
     */
    IVersionCompatibility getCompatibility();

    /**
     * Returns the unique workflow identifier (stable across versions).
     * This is different from name - it's a canonical identifier that
     * remains constant across all versions of the same logical workflow.
     *
     * @return stable workflow identifier
     */
    String getWorkflowId();

    /**
     * Returns schema version for the workflow definition format.
     * Allows future evolution of the workflow definition structure.
     *
     * @return schema version string
     */
    String getSchemaVersion();

    /**
     * Returns metadata annotations for this workflow version.
     * Can include author, changelog, tags, etc.
     *
     * @return metadata map
     */
    Map<String, Object> getMetadata();

    /**
     * Returns the set of steps that were added in this version.
     * Useful for migration analysis.
     *
     * @return set of added step names
     */
    Set<String> getAddedSteps();

    /**
     * Returns the set of steps that were removed in this version.
     * Useful for migration analysis.
     *
     * @return set of removed step names
     */
    Set<String> getRemovedSteps();

    /**
     * Returns the set of steps that were modified in this version.
     * Useful for migration analysis.
     *
     * @return set of modified step names
     */
    Set<String> getModifiedSteps();

    /**
     * Returns migration hints for upgrading from previous versions.
     *
     * @return migration hints if available
     */
    Optional<IWorkflowMigrationHints> getMigrationHints();

    /**
     * Indicates if this version supports in-flight migration from the given version.
     *
     * @param fromVersion source version
     * @return true if migration is supported
     */
    boolean supportsMigrationFrom(IWorkflowVersion fromVersion);
}
