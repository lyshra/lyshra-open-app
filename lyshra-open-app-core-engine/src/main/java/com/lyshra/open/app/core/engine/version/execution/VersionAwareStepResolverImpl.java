package com.lyshra.open.app.core.engine.version.execution;

import com.lyshra.open.app.core.engine.version.loader.IWorkflowDefinitionLoader;
import com.lyshra.open.app.core.engine.version.loader.WorkflowDefinitionLoaderImpl;
import com.lyshra.open.app.core.engine.version.storage.IVersionedWorkflowStorage;
import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStep;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepNext;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of version-aware step resolver.
 *
 * <p>This resolver loads workflow definitions from storage and resolves step
 * configurations based on the specific workflow version. It ensures that
 * running workflows use the exact step definitions they started with.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Version-specific step resolution</li>
 *   <li>Caching for performance</li>
 *   <li>Thread-safe operations</li>
 *   <li>Support for branch resolution</li>
 * </ul>
 */
@Slf4j
public class VersionAwareStepResolverImpl implements IVersionAwareStepResolver {

    private final IWorkflowDefinitionLoader workflowLoader;
    private final Map<String, IVersionedWorkflow> workflowCache;

    /**
     * Creates a resolver with the given workflow loader.
     *
     * @param workflowLoader workflow definition loader
     */
    public VersionAwareStepResolverImpl(IWorkflowDefinitionLoader workflowLoader) {
        this.workflowLoader = workflowLoader;
        this.workflowCache = new ConcurrentHashMap<>();
    }

    /**
     * Creates a resolver with a storage backend.
     *
     * @param storage workflow storage
     * @return resolver instance
     */
    public static IVersionAwareStepResolver create(IVersionedWorkflowStorage storage) {
        return new VersionAwareStepResolverImpl(WorkflowDefinitionLoaderImpl.create(storage));
    }

    /**
     * Creates a resolver with the given loader.
     *
     * @param loader workflow definition loader
     * @return resolver instance
     */
    public static IVersionAwareStepResolver create(IWorkflowDefinitionLoader loader) {
        return new VersionAwareStepResolverImpl(loader);
    }

    @Override
    public Optional<ILyshraOpenAppWorkflowStep> resolveStep(
            String workflowId,
            IWorkflowVersion version,
            String stepName) {

        if (stepName == null || stepName.isBlank()) {
            return Optional.empty();
        }

        return loadWorkflow(workflowId, version)
                .map(workflow -> workflow.getSteps().get(stepName));
    }

    @Override
    public Map<String, ILyshraOpenAppWorkflowStep> resolveAllSteps(
            String workflowId,
            IWorkflowVersion version) {

        return loadWorkflow(workflowId, version)
                .map(IVersionedWorkflow::getSteps)
                .orElse(Collections.emptyMap());
    }

    @Override
    public boolean stepExists(String workflowId, IWorkflowVersion version, String stepName) {
        return resolveStep(workflowId, version, stepName).isPresent();
    }

    @Override
    public Optional<String> resolveStartStep(String workflowId, IWorkflowVersion version) {
        return loadWorkflow(workflowId, version)
                .map(IVersionedWorkflow::getStartStep);
    }

    @Override
    public Optional<String> resolveNextStep(
            String workflowId,
            IWorkflowVersion version,
            String currentStep,
            String branchCondition) {

        return resolveStep(workflowId, version, currentStep)
                .flatMap(step -> resolveNextFromStep(step, branchCondition));
    }

    /**
     * Resolves the next step from step configuration.
     */
    private Optional<String> resolveNextFromStep(ILyshraOpenAppWorkflowStep step, String branchCondition) {
        ILyshraOpenAppWorkflowStepNext next = step.getNext();
        if (next == null) {
            return Optional.empty();
        }

        Map<String, String> branches = next.getBranches();
        if (branches == null || branches.isEmpty()) {
            return Optional.empty();
        }

        // If branch condition is provided, look for matching branch
        if (branchCondition != null && !branchCondition.isBlank()) {
            String nextStep = branches.get(branchCondition);
            if (nextStep != null) {
                return Optional.of(nextStep);
            }
        }

        // Fall back to default branch
        String defaultNext = branches.get("default");
        if (defaultNext != null) {
            return Optional.of(defaultNext);
        }

        // Use first branch if no default
        return branches.values().stream().findFirst();
    }

    @Override
    public Optional<IVersionedWorkflow> loadWorkflow(String workflowId, IWorkflowVersion version) {
        String cacheKey = workflowId + ":" + version.toVersionString();

        // Check cache first
        IVersionedWorkflow cached = workflowCache.get(cacheKey);
        if (cached != null) {
            return Optional.of(cached);
        }

        // Load from storage
        Optional<IVersionedWorkflow> workflow = workflowLoader.load(workflowId, version);

        // Cache if found
        workflow.ifPresent(w -> {
            workflowCache.put(cacheKey, w);
            log.debug("Cached workflow definition: {}:{}", workflowId, version.toVersionString());
        });

        return workflow;
    }

    @Override
    public Optional<StepMetadata> getStepMetadata(
            String workflowId,
            IWorkflowVersion version,
            String stepName) {

        return resolveStep(workflowId, version, stepName)
                .map(StepMetadata::fromStep);
    }

    /**
     * Clears the workflow cache.
     */
    public void clearCache() {
        workflowCache.clear();
        log.info("Cleared step resolver cache");
    }

    /**
     * Clears the cache for a specific workflow.
     *
     * @param workflowId workflow ID
     */
    public void clearCache(String workflowId) {
        workflowCache.entrySet().removeIf(entry -> entry.getKey().startsWith(workflowId + ":"));
        log.debug("Cleared cache for workflow: {}", workflowId);
    }

    /**
     * Gets the number of cached workflows.
     *
     * @return cache size
     */
    public int getCacheSize() {
        return workflowCache.size();
    }
}
