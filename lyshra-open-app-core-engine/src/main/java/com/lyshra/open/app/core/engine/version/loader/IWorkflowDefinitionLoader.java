package com.lyshra.open.app.core.engine.version.loader;

import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;

import java.util.List;
import java.util.Optional;

/**
 * Loader for retrieving workflow definitions from storage based on workflowId and version.
 *
 * <p>This loader provides a clean API for the engine to load any version of workflow
 * definition on demand, with support for fallback rules when the requested version
 * is not available.</p>
 *
 * <p>Fallback Strategies:</p>
 * <ul>
 *   <li>{@link FallbackStrategy#NONE} - No fallback, return empty if version not found</li>
 *   <li>{@link FallbackStrategy#LATEST_ACTIVE} - Fall back to latest active version</li>
 *   <li>{@link FallbackStrategy#LATEST_STABLE} - Fall back to latest stable (non-pre-release) version</li>
 *   <li>{@link FallbackStrategy#LATEST_ANY} - Fall back to latest version (including pre-release)</li>
 *   <li>{@link FallbackStrategy#NEAREST_COMPATIBLE} - Fall back to nearest compatible version</li>
 * </ul>
 *
 * <p>Usage Examples:</p>
 * <pre>{@code
 * // Load specific version, no fallback
 * Optional<IVersionedWorkflow> workflow = loader.load("order-processing", "1.2.0");
 *
 * // Load with fallback to latest active
 * LoadResult result = loader.load(LoadRequest.builder()
 *     .workflowId("order-processing")
 *     .version("1.2.0")
 *     .fallbackStrategy(FallbackStrategy.LATEST_ACTIVE)
 *     .build());
 *
 * // Load latest active version directly
 * Optional<IVersionedWorkflow> latest = loader.loadLatestActive("order-processing");
 * }</pre>
 */
public interface IWorkflowDefinitionLoader {

    /**
     * Loads a workflow definition by exact workflowId and version.
     * Returns empty if the version is not found.
     *
     * @param workflowId workflow identifier
     * @param version exact version string (e.g., "1.2.0")
     * @return workflow definition if found
     */
    Optional<IVersionedWorkflow> load(String workflowId, String version);

    /**
     * Loads a workflow definition by exact workflowId and version object.
     * Returns empty if the version is not found.
     *
     * @param workflowId workflow identifier
     * @param version exact version
     * @return workflow definition if found
     */
    Optional<IVersionedWorkflow> load(String workflowId, IWorkflowVersion version);

    /**
     * Loads a workflow definition with fallback support.
     * If the requested version is not found, applies the fallback strategy.
     *
     * @param request load request with workflowId, version, and fallback strategy
     * @return load result containing the workflow (if found) and resolution details
     */
    LoadResult load(LoadRequest request);

    /**
     * Loads the latest active version of a workflow.
     * An active version is one that accepts new executions.
     *
     * @param workflowId workflow identifier
     * @return latest active workflow definition if found
     */
    Optional<IVersionedWorkflow> loadLatestActive(String workflowId);

    /**
     * Loads the latest stable (non-pre-release) version of a workflow.
     *
     * @param workflowId workflow identifier
     * @return latest stable workflow definition if found
     */
    Optional<IVersionedWorkflow> loadLatestStable(String workflowId);

    /**
     * Loads the latest version of a workflow (including pre-release).
     *
     * @param workflowId workflow identifier
     * @return latest workflow definition if found
     */
    Optional<IVersionedWorkflow> loadLatest(String workflowId);

    /**
     * Loads all versions of a workflow.
     *
     * @param workflowId workflow identifier
     * @return all versions sorted by version descending (newest first)
     */
    List<IVersionedWorkflow> loadAllVersions(String workflowId);

    /**
     * Loads all active versions of a workflow.
     *
     * @param workflowId workflow identifier
     * @return all active versions sorted by version descending
     */
    List<IVersionedWorkflow> loadActiveVersions(String workflowId);

    /**
     * Lists all available version numbers for a workflow.
     *
     * @param workflowId workflow identifier
     * @return list of versions sorted descending (newest first)
     */
    List<IWorkflowVersion> listVersions(String workflowId);

    /**
     * Checks if a specific version exists.
     *
     * @param workflowId workflow identifier
     * @param version version to check
     * @return true if the version exists
     */
    boolean exists(String workflowId, IWorkflowVersion version);

    /**
     * Checks if any version of a workflow exists.
     *
     * @param workflowId workflow identifier
     * @return true if any version exists
     */
    boolean exists(String workflowId);

    /**
     * Fallback strategies when requested version is not found.
     */
    enum FallbackStrategy {
        /**
         * No fallback - return empty if version not found.
         */
        NONE,

        /**
         * Fall back to the latest active version.
         * Active versions accept new workflow executions.
         */
        LATEST_ACTIVE,

        /**
         * Fall back to the latest stable (non-pre-release) version.
         * Stable versions have no pre-release suffix.
         */
        LATEST_STABLE,

        /**
         * Fall back to the latest version (including pre-release).
         */
        LATEST_ANY,

        /**
         * Fall back to the nearest compatible version.
         * Uses version compatibility rules to find a suitable alternative.
         */
        NEAREST_COMPATIBLE
    }

    /**
     * Request for loading a workflow definition with options.
     */
    record LoadRequest(
            String workflowId,
            String version,
            IWorkflowVersion versionObject,
            FallbackStrategy fallbackStrategy,
            boolean includeInactive
    ) {
        /**
         * Creates a builder for load requests.
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Creates a simple request for exact version match.
         */
        public static LoadRequest exact(String workflowId, String version) {
            return new LoadRequest(workflowId, version, null, FallbackStrategy.NONE, false);
        }

        /**
         * Creates a request with fallback to latest active.
         */
        public static LoadRequest withFallback(String workflowId, String version) {
            return new LoadRequest(workflowId, version, null, FallbackStrategy.LATEST_ACTIVE, false);
        }

        /**
         * Builder for load requests.
         */
        public static class Builder {
            private String workflowId;
            private String version;
            private IWorkflowVersion versionObject;
            private FallbackStrategy fallbackStrategy = FallbackStrategy.NONE;
            private boolean includeInactive = false;

            public Builder workflowId(String workflowId) {
                this.workflowId = workflowId;
                return this;
            }

            public Builder version(String version) {
                this.version = version;
                return this;
            }

            public Builder version(IWorkflowVersion version) {
                this.versionObject = version;
                return this;
            }

            public Builder fallbackStrategy(FallbackStrategy fallbackStrategy) {
                this.fallbackStrategy = fallbackStrategy;
                return this;
            }

            public Builder includeInactive(boolean includeInactive) {
                this.includeInactive = includeInactive;
                return this;
            }

            public LoadRequest build() {
                if (workflowId == null || workflowId.isBlank()) {
                    throw new IllegalArgumentException("workflowId is required");
                }
                return new LoadRequest(workflowId, version, versionObject, fallbackStrategy, includeInactive);
            }
        }
    }

    /**
     * Result of a load operation with resolution details.
     */
    record LoadResult(
            boolean found,
            Optional<IVersionedWorkflow> workflow,
            String requestedWorkflowId,
            String requestedVersion,
            IWorkflowVersion resolvedVersion,
            ResolutionType resolutionType,
            String message
    ) {
        /**
         * Creates a successful result for exact match.
         */
        public static LoadResult exactMatch(IVersionedWorkflow workflow, String requestedVersion) {
            return new LoadResult(
                    true,
                    Optional.of(workflow),
                    workflow.getWorkflowId(),
                    requestedVersion,
                    workflow.getVersion(),
                    ResolutionType.EXACT_MATCH,
                    "Loaded exact version " + workflow.getVersion().toVersionString()
            );
        }

        /**
         * Creates a successful result for fallback resolution.
         */
        public static LoadResult fallback(
                IVersionedWorkflow workflow,
                String requestedVersion,
                ResolutionType resolutionType) {
            return new LoadResult(
                    true,
                    Optional.of(workflow),
                    workflow.getWorkflowId(),
                    requestedVersion,
                    workflow.getVersion(),
                    resolutionType,
                    String.format("Requested version %s not found, resolved to %s via %s",
                            requestedVersion, workflow.getVersion().toVersionString(), resolutionType)
            );
        }

        /**
         * Creates a not-found result.
         */
        public static LoadResult notFound(String workflowId, String requestedVersion, String reason) {
            return new LoadResult(
                    false,
                    Optional.empty(),
                    workflowId,
                    requestedVersion,
                    null,
                    ResolutionType.NOT_FOUND,
                    reason
            );
        }

        /**
         * Returns true if a workflow was found.
         */
        public boolean isPresent() {
            return found && workflow.isPresent();
        }

        /**
         * Returns true if fallback was used.
         */
        public boolean usedFallback() {
            return found && resolutionType != ResolutionType.EXACT_MATCH;
        }

        /**
         * Gets the workflow or throws if not found.
         */
        public IVersionedWorkflow getOrThrow() {
            return workflow.orElseThrow(() ->
                    new WorkflowDefinitionNotFoundException(requestedWorkflowId, requestedVersion, message));
        }
    }

    /**
     * How the version was resolved.
     */
    enum ResolutionType {
        /**
         * Exact version match found.
         */
        EXACT_MATCH,

        /**
         * Fell back to latest active version.
         */
        FALLBACK_LATEST_ACTIVE,

        /**
         * Fell back to latest stable version.
         */
        FALLBACK_LATEST_STABLE,

        /**
         * Fell back to latest (any) version.
         */
        FALLBACK_LATEST_ANY,

        /**
         * Fell back to nearest compatible version.
         */
        FALLBACK_NEAREST_COMPATIBLE,

        /**
         * No version found.
         */
        NOT_FOUND
    }
}
