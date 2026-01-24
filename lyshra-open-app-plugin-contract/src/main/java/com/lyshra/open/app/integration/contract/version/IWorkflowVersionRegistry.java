package com.lyshra.open.app.integration.contract.version;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Registry for managing multiple versions of workflow definitions.
 * Provides version resolution, lookup, and lifecycle management.
 *
 * <p>Design Pattern: Registry pattern for centralized version management.</p>
 */
public interface IWorkflowVersionRegistry {

    /**
     * Registers a versioned workflow.
     *
     * @param workflow versioned workflow to register
     * @throws IllegalStateException if version already registered
     */
    void register(IVersionedWorkflow workflow);

    /**
     * Unregisters a workflow version.
     * Should fail if any in-flight executions are bound to this version.
     *
     * @param workflowId workflow ID
     * @param version version to unregister
     * @return true if unregistered
     */
    boolean unregister(String workflowId, IWorkflowVersion version);

    /**
     * Gets a specific version of a workflow.
     *
     * @param workflowId workflow ID
     * @param version specific version
     * @return workflow if found
     */
    Optional<IVersionedWorkflow> getVersion(String workflowId, IWorkflowVersion version);

    /**
     * Gets the latest stable version of a workflow.
     *
     * @param workflowId workflow ID
     * @return latest stable version
     */
    Optional<IVersionedWorkflow> getLatestStableVersion(String workflowId);

    /**
     * Gets the latest version of a workflow (including pre-release).
     *
     * @param workflowId workflow ID
     * @return latest version
     */
    Optional<IVersionedWorkflow> getLatestVersion(String workflowId);

    /**
     * Gets all versions of a workflow.
     *
     * @param workflowId workflow ID
     * @return all versions, sorted by version descending
     */
    List<IVersionedWorkflow> getAllVersions(String workflowId);

    /**
     * Gets all registered workflow IDs.
     *
     * @return collection of workflow IDs
     */
    Collection<String> getAllWorkflowIds();

    /**
     * Gets all registered versioned workflows.
     *
     * @return all workflows
     */
    Collection<IVersionedWorkflow> getAllWorkflows();

    /**
     * Checks if a specific version exists.
     *
     * @param workflowId workflow ID
     * @param version version to check
     * @return true if exists
     */
    boolean hasVersion(String workflowId, IWorkflowVersion version);

    /**
     * Gets the count of versions for a workflow.
     *
     * @param workflowId workflow ID
     * @return version count
     */
    int getVersionCount(String workflowId);

    /**
     * Marks a version as deprecated.
     *
     * @param workflowId workflow ID
     * @param version version to deprecate
     * @param reason deprecation reason
     * @return true if deprecated
     */
    boolean deprecateVersion(String workflowId, IWorkflowVersion version, String reason);

    /**
     * Finds versions compatible with the given version for migration.
     *
     * @param workflowId workflow ID
     * @param fromVersion source version
     * @return compatible target versions, sorted by version descending
     */
    List<IVersionedWorkflow> findCompatibleVersions(String workflowId, IWorkflowVersion fromVersion);

    /**
     * Gets the recommended migration target for a given version.
     *
     * @param workflowId workflow ID
     * @param fromVersion source version
     * @return recommended target version
     */
    Optional<IVersionedWorkflow> getRecommendedMigrationTarget(String workflowId, IWorkflowVersion fromVersion);

    /**
     * Resolves a version string to a concrete version.
     * Supports patterns like "latest", "1.x", ">=1.2.0".
     *
     * @param workflowId workflow ID
     * @param versionSpec version specification
     * @return resolved version
     */
    Optional<IVersionedWorkflow> resolveVersion(String workflowId, String versionSpec);
}
