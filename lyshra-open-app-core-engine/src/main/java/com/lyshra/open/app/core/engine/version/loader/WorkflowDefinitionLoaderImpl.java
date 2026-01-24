package com.lyshra.open.app.core.engine.version.loader;

import com.lyshra.open.app.core.engine.version.storage.IVersionAwareWorkflowLoader;
import com.lyshra.open.app.core.engine.version.storage.IVersionedWorkflowStorage;
import com.lyshra.open.app.core.engine.version.storage.impl.VersionAwareWorkflowLoaderImpl;
import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.models.version.WorkflowVersion;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * Implementation of workflow definition loader with fallback support.
 *
 * <p>This loader retrieves workflow definitions from storage based on workflowId
 * and version, with support for fallback rules when the requested version is
 * not available.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Exact version loading by workflowId and version</li>
 *   <li>Fallback strategies (latest active, latest stable, latest any, nearest compatible)</li>
 *   <li>Version resolution with detailed results</li>
 *   <li>Caching via underlying version-aware loader</li>
 *   <li>Thread-safe operations</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * IWorkflowDefinitionLoader loader = WorkflowDefinitionLoaderImpl.create(storage);
 *
 * // Simple load
 * Optional<IVersionedWorkflow> workflow = loader.load("order-process", "1.2.0");
 *
 * // Load with fallback
 * LoadResult result = loader.load(LoadRequest.builder()
 *     .workflowId("order-process")
 *     .version("1.2.0")
 *     .fallbackStrategy(FallbackStrategy.LATEST_ACTIVE)
 *     .build());
 *
 * if (result.isPresent()) {
 *     IVersionedWorkflow wf = result.workflow().get();
 *     if (result.usedFallback()) {
 *         log.info("Used fallback: {}", result.message());
 *     }
 * }
 * }</pre>
 */
@Slf4j
public class WorkflowDefinitionLoaderImpl implements IWorkflowDefinitionLoader {

    private final IVersionAwareWorkflowLoader versionAwareLoader;
    private final IVersionedWorkflowStorage storage;

    /**
     * Creates a loader with the given version-aware loader and storage.
     *
     * @param versionAwareLoader version-aware loader for cached access
     * @param storage underlying storage for direct access
     */
    public WorkflowDefinitionLoaderImpl(
            IVersionAwareWorkflowLoader versionAwareLoader,
            IVersionedWorkflowStorage storage) {
        this.versionAwareLoader = versionAwareLoader;
        this.storage = storage;
    }

    /**
     * Creates a loader with just storage (creates internal version-aware loader).
     *
     * @param storage workflow storage
     */
    public WorkflowDefinitionLoaderImpl(IVersionedWorkflowStorage storage) {
        this(VersionAwareWorkflowLoaderImpl.create(storage), storage);
    }

    /**
     * Factory method to create a loader from storage.
     *
     * @param storage workflow storage
     * @return loader instance
     */
    public static IWorkflowDefinitionLoader create(IVersionedWorkflowStorage storage) {
        return new WorkflowDefinitionLoaderImpl(storage);
    }

    /**
     * Factory method to create a loader with custom version-aware loader.
     *
     * @param versionAwareLoader version-aware loader
     * @param storage workflow storage
     * @return loader instance
     */
    public static IWorkflowDefinitionLoader create(
            IVersionAwareWorkflowLoader versionAwareLoader,
            IVersionedWorkflowStorage storage) {
        return new WorkflowDefinitionLoaderImpl(versionAwareLoader, storage);
    }

    @Override
    public Optional<IVersionedWorkflow> load(String workflowId, String version) {
        if (version == null || version.isBlank()) {
            return loadLatestActive(workflowId);
        }

        log.debug("Loading workflow {}:{}", workflowId, version);
        return versionAwareLoader.load(workflowId, version);
    }

    @Override
    public Optional<IVersionedWorkflow> load(String workflowId, IWorkflowVersion version) {
        log.debug("Loading workflow {}:{}", workflowId, version.toVersionString());
        return versionAwareLoader.load(workflowId, version);
    }

    @Override
    public LoadResult load(LoadRequest request) {
        String workflowId = request.workflowId();
        String requestedVersion = resolveVersionString(request);

        log.debug("Loading workflow {} with version={}, fallback={}",
                workflowId, requestedVersion, request.fallbackStrategy());

        // Try exact match first
        Optional<IVersionedWorkflow> exactMatch = tryExactMatch(workflowId, request);

        if (exactMatch.isPresent()) {
            IVersionedWorkflow workflow = exactMatch.get();

            // Check if inactive and not allowed
            if (!workflow.isActive() && !request.includeInactive()) {
                log.debug("Found inactive version {}:{}, applying fallback",
                        workflowId, workflow.getVersion().toVersionString());
                return applyFallback(workflowId, requestedVersion, request.fallbackStrategy());
            }

            return LoadResult.exactMatch(workflow, requestedVersion);
        }

        // Apply fallback strategy
        return applyFallback(workflowId, requestedVersion, request.fallbackStrategy());
    }

    @Override
    public Optional<IVersionedWorkflow> loadLatestActive(String workflowId) {
        log.debug("Loading latest active version for workflow {}", workflowId);
        return versionAwareLoader.loadLatestActive(workflowId);
    }

    @Override
    public Optional<IVersionedWorkflow> loadLatestStable(String workflowId) {
        log.debug("Loading latest stable version for workflow {}", workflowId);
        return versionAwareLoader.loadLatestStable(workflowId);
    }

    @Override
    public Optional<IVersionedWorkflow> loadLatest(String workflowId) {
        log.debug("Loading latest version for workflow {}", workflowId);
        return versionAwareLoader.loadLatest(workflowId);
    }

    @Override
    public List<IVersionedWorkflow> loadAllVersions(String workflowId) {
        return versionAwareLoader.loadAllVersions(workflowId);
    }

    @Override
    public List<IVersionedWorkflow> loadActiveVersions(String workflowId) {
        return versionAwareLoader.loadActiveVersions(workflowId);
    }

    @Override
    public List<IWorkflowVersion> listVersions(String workflowId) {
        return versionAwareLoader.listAvailableVersions(workflowId);
    }

    @Override
    public boolean exists(String workflowId, IWorkflowVersion version) {
        return versionAwareLoader.isVersionAvailable(workflowId, version);
    }

    @Override
    public boolean exists(String workflowId) {
        return storage.exists(workflowId);
    }

    /**
     * Resolves the version string from the request.
     */
    private String resolveVersionString(LoadRequest request) {
        if (request.versionObject() != null) {
            return request.versionObject().toVersionString();
        }
        return request.version();
    }

    /**
     * Tries to find an exact match for the requested version.
     */
    private Optional<IVersionedWorkflow> tryExactMatch(String workflowId, LoadRequest request) {
        if (request.versionObject() != null) {
            return versionAwareLoader.load(workflowId, request.versionObject());
        }

        if (request.version() != null && !request.version().isBlank()) {
            // Try parsing as exact version first
            try {
                IWorkflowVersion exactVersion = WorkflowVersion.parse(request.version());
                Optional<IVersionedWorkflow> result = versionAwareLoader.load(workflowId, exactVersion);
                if (result.isPresent()) {
                    return result;
                }
            } catch (IllegalArgumentException e) {
                // Not a valid exact version, might be a pattern
                log.debug("Version '{}' is not exact, trying pattern resolution", request.version());
            }

            // Try as version spec/pattern
            return versionAwareLoader.load(workflowId, request.version());
        }

        return Optional.empty();
    }

    /**
     * Applies the fallback strategy to find an alternative version.
     */
    private LoadResult applyFallback(
            String workflowId,
            String requestedVersion,
            FallbackStrategy strategy) {

        if (strategy == FallbackStrategy.NONE) {
            return LoadResult.notFound(workflowId, requestedVersion,
                    String.format("Version %s not found for workflow %s (no fallback)",
                            requestedVersion, workflowId));
        }

        Optional<IVersionedWorkflow> fallbackResult;
        ResolutionType resolutionType;

        switch (strategy) {
            case LATEST_ACTIVE:
                fallbackResult = loadLatestActive(workflowId);
                resolutionType = ResolutionType.FALLBACK_LATEST_ACTIVE;
                break;

            case LATEST_STABLE:
                fallbackResult = loadLatestStable(workflowId);
                resolutionType = ResolutionType.FALLBACK_LATEST_STABLE;
                break;

            case LATEST_ANY:
                fallbackResult = loadLatest(workflowId);
                resolutionType = ResolutionType.FALLBACK_LATEST_ANY;
                break;

            case NEAREST_COMPATIBLE:
                fallbackResult = findNearestCompatible(workflowId, requestedVersion);
                resolutionType = ResolutionType.FALLBACK_NEAREST_COMPATIBLE;
                break;

            default:
                return LoadResult.notFound(workflowId, requestedVersion,
                        "Unknown fallback strategy: " + strategy);
        }

        if (fallbackResult.isPresent()) {
            log.info("Applied {} fallback for {}:{} -> {}",
                    strategy, workflowId, requestedVersion,
                    fallbackResult.get().getVersion().toVersionString());
            return LoadResult.fallback(fallbackResult.get(), requestedVersion, resolutionType);
        }

        return LoadResult.notFound(workflowId, requestedVersion,
                String.format("No fallback version found for workflow %s (strategy: %s)",
                        workflowId, strategy));
    }

    /**
     * Finds the nearest compatible version to the requested version.
     * Uses semantic versioning rules to find the best match.
     */
    private Optional<IVersionedWorkflow> findNearestCompatible(String workflowId, String requestedVersion) {
        if (requestedVersion == null || requestedVersion.isBlank()) {
            return loadLatestActive(workflowId);
        }

        try {
            IWorkflowVersion requestedVersionObj = WorkflowVersion.parse(requestedVersion);
            List<IVersionedWorkflow> allVersions = loadActiveVersions(workflowId);

            if (allVersions.isEmpty()) {
                return Optional.empty();
            }

            // Strategy: Find the highest version that is compatible
            // For semantic versioning, compatible means same major version

            int requestedMajor = requestedVersionObj.getMajor();
            int requestedMinor = requestedVersionObj.getMinor();

            // First try: Same major.minor, higher patch
            Optional<IVersionedWorkflow> sameMajorMinor = allVersions.stream()
                    .filter(w -> w.getVersion().getMajor() == requestedMajor)
                    .filter(w -> w.getVersion().getMinor() == requestedMinor)
                    .filter(w -> w.getVersion().compareTo(requestedVersionObj) >= 0)
                    .findFirst();

            if (sameMajorMinor.isPresent()) {
                return sameMajorMinor;
            }

            // Second try: Same major, any minor >= requested
            Optional<IVersionedWorkflow> sameMajor = allVersions.stream()
                    .filter(w -> w.getVersion().getMajor() == requestedMajor)
                    .filter(w -> w.getVersion().getMinor() >= requestedMinor)
                    .findFirst();

            if (sameMajor.isPresent()) {
                return sameMajor;
            }

            // Third try: Same major, any version
            Optional<IVersionedWorkflow> anyMajor = allVersions.stream()
                    .filter(w -> w.getVersion().getMajor() == requestedMajor)
                    .findFirst();

            if (anyMajor.isPresent()) {
                return anyMajor;
            }

            // Last resort: Use compatibility information if available
            for (IVersionedWorkflow workflow : allVersions) {
                if (workflow.supportsMigrationFrom(requestedVersionObj)) {
                    return Optional.of(workflow);
                }
            }

            // No compatible version found, return latest active as last resort
            return allVersions.isEmpty() ? Optional.empty() : Optional.of(allVersions.get(0));

        } catch (IllegalArgumentException e) {
            log.debug("Cannot parse version '{}' for nearest compatible search", requestedVersion);
            return loadLatestActive(workflowId);
        }
    }

    /**
     * Clears the cache for all workflows.
     */
    public void clearCache() {
        versionAwareLoader.clearCache();
    }

    /**
     * Clears the cache for a specific workflow.
     *
     * @param workflowId workflow identifier
     */
    public void clearCache(String workflowId) {
        versionAwareLoader.clearCache(workflowId);
    }

    /**
     * Reloads all workflow definitions from storage.
     */
    public void reload() {
        versionAwareLoader.reload();
    }

    /**
     * Reloads a specific workflow's definitions from storage.
     *
     * @param workflowId workflow identifier
     */
    public void reload(String workflowId) {
        versionAwareLoader.reload(workflowId);
    }
}
