package com.lyshra.open.app.core.engine.version.storage;

import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;

import java.util.List;
import java.util.Optional;

/**
 * Version-aware loader for workflow definitions.
 * Provides intelligent version resolution and loading capabilities at runtime.
 *
 * <p>This loader supports:</p>
 * <ul>
 *   <li>Exact version loading</li>
 *   <li>Version range/pattern resolution (e.g., "1.x", ">=1.2.0")</li>
 *   <li>Latest stable version loading</li>
 *   <li>Active version filtering</li>
 *   <li>Caching for performance</li>
 * </ul>
 *
 * <p>Version Resolution Patterns:</p>
 * <pre>
 * "1.2.3"       - Exact version
 * "latest"      - Latest version (including pre-release)
 * "stable"      - Latest stable (non-pre-release) version
 * "active"      - Latest active version
 * "1.x"         - Latest 1.x.x version
 * "1.2.x"       - Latest 1.2.x version
 * ">=1.2.0"     - Minimum version 1.2.0
 * "&lt;2.0.0"      - Less than 2.0.0
 * "^1.2.0"      - Compatible with 1.2.0 (>=1.2.0 &lt;2.0.0)
 * "~1.2.0"      - Approximately 1.2.0 (>=1.2.0 &lt;1.3.0)
 * </pre>
 */
public interface IVersionAwareWorkflowLoader {

    /**
     * Loads a workflow by exact version.
     *
     * @param workflowId workflow identifier
     * @param version exact version to load
     * @return workflow if found
     */
    Optional<IVersionedWorkflow> load(String workflowId, IWorkflowVersion version);

    /**
     * Loads a workflow by version specification pattern.
     *
     * @param workflowId workflow identifier
     * @param versionSpec version specification (e.g., "latest", "1.x", ">=1.2.0")
     * @return resolved workflow if found
     */
    Optional<IVersionedWorkflow> load(String workflowId, String versionSpec);

    /**
     * Loads the latest version of a workflow (including pre-release).
     *
     * @param workflowId workflow identifier
     * @return latest version if exists
     */
    Optional<IVersionedWorkflow> loadLatest(String workflowId);

    /**
     * Loads the latest stable (non-pre-release) version.
     *
     * @param workflowId workflow identifier
     * @return latest stable version if exists
     */
    Optional<IVersionedWorkflow> loadLatestStable(String workflowId);

    /**
     * Loads the latest active version.
     *
     * @param workflowId workflow identifier
     * @return latest active version if exists
     */
    Optional<IVersionedWorkflow> loadLatestActive(String workflowId);

    /**
     * Loads all versions of a workflow.
     *
     * @param workflowId workflow identifier
     * @return all versions sorted by version descending
     */
    List<IVersionedWorkflow> loadAllVersions(String workflowId);

    /**
     * Loads all active versions of a workflow.
     *
     * @param workflowId workflow identifier
     * @return active versions sorted by version descending
     */
    List<IVersionedWorkflow> loadActiveVersions(String workflowId);

    /**
     * Resolves a version specification to a concrete version.
     *
     * @param workflowId workflow identifier
     * @param versionSpec version specification
     * @return resolved version if found
     */
    Optional<IWorkflowVersion> resolveVersion(String workflowId, String versionSpec);

    /**
     * Lists all available versions for a workflow.
     *
     * @param workflowId workflow identifier
     * @return list of versions sorted descending
     */
    List<IWorkflowVersion> listAvailableVersions(String workflowId);

    /**
     * Checks if a specific version is available.
     *
     * @param workflowId workflow identifier
     * @param version version to check
     * @return true if available
     */
    boolean isVersionAvailable(String workflowId, IWorkflowVersion version);

    /**
     * Clears any cached workflow definitions.
     */
    void clearCache();

    /**
     * Clears cached definitions for a specific workflow.
     *
     * @param workflowId workflow identifier
     */
    void clearCache(String workflowId);

    /**
     * Reloads all workflow definitions from storage.
     */
    void reload();

    /**
     * Reloads a specific workflow's definitions from storage.
     *
     * @param workflowId workflow identifier
     */
    void reload(String workflowId);
}
