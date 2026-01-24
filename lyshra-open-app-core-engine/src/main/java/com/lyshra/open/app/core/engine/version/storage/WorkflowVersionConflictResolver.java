package com.lyshra.open.app.core.engine.version.storage;

import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.models.version.VersionedWorkflowDefinition;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Utility class for resolving version conflicts when storing workflows.
 *
 * <p>Provides different resolution strategies:</p>
 * <ul>
 *   <li>{@link ResolutionStrategy#FAIL} - Throw exception on conflict (default)</li>
 *   <li>{@link ResolutionStrategy#AUTO_INCREMENT} - Automatically increment version</li>
 *   <li>{@link ResolutionStrategy#SKIP_IF_IDENTICAL} - Skip storage if content is identical</li>
 *   <li>{@link ResolutionStrategy#CUSTOM} - Use custom resolution logic</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Auto-increment on conflict
 * IVersionedWorkflowStorage.StorageResult result = WorkflowVersionConflictResolver
 *     .withStorage(storage)
 *     .strategy(ResolutionStrategy.AUTO_INCREMENT)
 *     .store(workflow);
 *
 * // Skip if identical content
 * IVersionedWorkflowStorage.StorageResult result = WorkflowVersionConflictResolver
 *     .withStorage(storage)
 *     .strategy(ResolutionStrategy.SKIP_IF_IDENTICAL)
 *     .store(workflow);
 *
 * // Custom resolution
 * IVersionedWorkflowStorage.StorageResult result = WorkflowVersionConflictResolver
 *     .withStorage(storage)
 *     .customResolver((workflow, conflict) -> {
 *         // Custom logic to resolve conflict
 *         return Optional.of(workflowWithNewVersion);
 *     })
 *     .store(workflow);
 * }</pre>
 */
@Slf4j
public final class WorkflowVersionConflictResolver {

    /**
     * Resolution strategies for version conflicts.
     */
    public enum ResolutionStrategy {
        /**
         * Fail with exception when conflict detected.
         */
        FAIL,

        /**
         * Automatically increment the version number.
         */
        AUTO_INCREMENT,

        /**
         * Skip storage if the content is identical to existing.
         */
        SKIP_IF_IDENTICAL,

        /**
         * Use custom resolution function.
         */
        CUSTOM
    }

    private final IVersionedWorkflowStorage storage;
    private ResolutionStrategy strategy = ResolutionStrategy.FAIL;
    private BiFunction<IVersionedWorkflow, IVersionedWorkflowStorage.ConflictCheckResult, Optional<IVersionedWorkflow>>
            customResolver;
    private IVersionedWorkflowStorage.SerializationFormat format = IVersionedWorkflowStorage.SerializationFormat.YAML;
    private int maxRetries = 10;

    private WorkflowVersionConflictResolver(IVersionedWorkflowStorage storage) {
        this.storage = storage;
    }

    /**
     * Creates a conflict resolver for the given storage.
     *
     * @param storage workflow storage
     * @return resolver builder
     */
    public static WorkflowVersionConflictResolver withStorage(IVersionedWorkflowStorage storage) {
        return new WorkflowVersionConflictResolver(storage);
    }

    /**
     * Sets the resolution strategy.
     *
     * @param strategy resolution strategy
     * @return this resolver
     */
    public WorkflowVersionConflictResolver strategy(ResolutionStrategy strategy) {
        this.strategy = strategy;
        return this;
    }

    /**
     * Sets a custom resolver function.
     * Automatically sets strategy to CUSTOM.
     *
     * @param resolver function that takes workflow and conflict info, returns resolved workflow
     * @return this resolver
     */
    public WorkflowVersionConflictResolver customResolver(
            BiFunction<IVersionedWorkflow, IVersionedWorkflowStorage.ConflictCheckResult, Optional<IVersionedWorkflow>> resolver) {
        this.customResolver = resolver;
        this.strategy = ResolutionStrategy.CUSTOM;
        return this;
    }

    /**
     * Sets the serialization format.
     *
     * @param format YAML or JSON
     * @return this resolver
     */
    public WorkflowVersionConflictResolver format(IVersionedWorkflowStorage.SerializationFormat format) {
        this.format = format;
        return this;
    }

    /**
     * Sets the maximum number of retries for AUTO_INCREMENT strategy.
     *
     * @param maxRetries maximum retries
     * @return this resolver
     */
    public WorkflowVersionConflictResolver maxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    /**
     * Stores the workflow, resolving conflicts according to the configured strategy.
     *
     * @param workflow workflow to store
     * @return storage result
     * @throws WorkflowVersionConflictException if conflict cannot be resolved
     */
    public IVersionedWorkflowStorage.StorageResult store(IVersionedWorkflow workflow) {
        IVersionedWorkflowStorage.ConflictCheckResult conflict = storage.checkConflict(workflow);

        if (!conflict.hasConflict()) {
            return storage.store(workflow, format);
        }

        return handleConflict(workflow, conflict);
    }

    /**
     * Stores the workflow, resolving conflicts according to the configured strategy.
     * Returns the result wrapped in Optional, empty if skipped.
     *
     * @param workflow workflow to store
     * @return storage result or empty if skipped
     */
    public Optional<IVersionedWorkflowStorage.StorageResult> storeOptional(IVersionedWorkflow workflow) {
        IVersionedWorkflowStorage.ConflictCheckResult conflict = storage.checkConflict(workflow);

        if (!conflict.hasConflict()) {
            return Optional.of(storage.store(workflow, format));
        }

        if (strategy == ResolutionStrategy.SKIP_IF_IDENTICAL && conflict.isIdenticalContent()) {
            log.info("Skipping storage of {}:{} - identical content already exists",
                    workflow.getWorkflowId(), workflow.getVersion().toVersionString());
            return Optional.empty();
        }

        return Optional.of(handleConflict(workflow, conflict));
    }

    private IVersionedWorkflowStorage.StorageResult handleConflict(
            IVersionedWorkflow workflow,
            IVersionedWorkflowStorage.ConflictCheckResult conflict) {

        switch (strategy) {
            case FAIL:
                throw new WorkflowVersionConflictException(
                        conflict.workflowId(),
                        conflict.version(),
                        conflict.existingSchemaHash(),
                        conflict.newSchemaHash());

            case AUTO_INCREMENT:
                return handleAutoIncrement(workflow, conflict);

            case SKIP_IF_IDENTICAL:
                if (conflict.isIdenticalContent()) {
                    log.info("Skipping storage of {}:{} - identical content already exists",
                            workflow.getWorkflowId(), workflow.getVersion().toVersionString());
                    return IVersionedWorkflowStorage.StorageResult.success(
                            workflow.getWorkflowId(),
                            workflow.getVersion(),
                            storage.getStorageLocation(workflow.getWorkflowId(), workflow.getVersion()),
                            conflict.existingSchemaHash(),
                            format);
                }
                // Content differs - auto-increment
                return handleAutoIncrement(workflow, conflict);

            case CUSTOM:
                return handleCustom(workflow, conflict);

            default:
                throw new IllegalStateException("Unknown resolution strategy: " + strategy);
        }
    }

    private IVersionedWorkflowStorage.StorageResult handleAutoIncrement(
            IVersionedWorkflow workflow,
            IVersionedWorkflowStorage.ConflictCheckResult conflict) {

        IVersionedWorkflow currentWorkflow = workflow;
        IWorkflowVersion currentVersion = conflict.suggestedVersion();
        int attempts = 0;

        while (attempts < maxRetries) {
            // Create workflow with new version
            currentWorkflow = withVersion(currentWorkflow, currentVersion);

            IVersionedWorkflowStorage.ConflictCheckResult newConflict = storage.checkConflict(currentWorkflow);
            if (!newConflict.hasConflict()) {
                log.info("Auto-incremented version from {} to {} for workflow {}",
                        workflow.getVersion().toVersionString(),
                        currentVersion.toVersionString(),
                        workflow.getWorkflowId());
                return storage.store(currentWorkflow, format);
            }

            currentVersion = newConflict.suggestedVersion();
            attempts++;
        }

        throw new WorkflowVersionConflictException(
                workflow.getWorkflowId(),
                workflow.getVersion(),
                conflict.existingSchemaHash(),
                conflict.newSchemaHash());
    }

    private IVersionedWorkflowStorage.StorageResult handleCustom(
            IVersionedWorkflow workflow,
            IVersionedWorkflowStorage.ConflictCheckResult conflict) {

        if (customResolver == null) {
            throw new IllegalStateException("Custom strategy requires a custom resolver function");
        }

        Optional<IVersionedWorkflow> resolved = customResolver.apply(workflow, conflict);
        if (resolved.isEmpty()) {
            throw new WorkflowVersionConflictException(
                    conflict.workflowId(),
                    conflict.version(),
                    conflict.existingSchemaHash(),
                    conflict.newSchemaHash());
        }

        return storage.store(resolved.get(), format);
    }

    /**
     * Creates a new workflow instance with a different version.
     */
    private IVersionedWorkflow withVersion(IVersionedWorkflow workflow, IWorkflowVersion newVersion) {
        if (workflow instanceof VersionedWorkflowDefinition vwd) {
            return vwd.withVersion(newVersion);
        }

        // Fallback - create new definition with same properties
        return VersionedWorkflowDefinition.builder()
                .workflowId(workflow.getWorkflowId())
                .name(workflow.getName())
                .version(newVersion)
                .startStep(workflow.getStartStep())
                .contextRetention(workflow.getContextRetention())
                .steps(s -> {
                    // Copy steps from original workflow
                    workflow.getSteps().values().forEach(s::step);
                    return s;
                })
                .compatibility(workflow.getCompatibility())
                .schemaHash(null) // Will be recomputed
                .createdAt(workflow.getCreatedAt())
                .active(workflow.isActive())
                .schemaVersion(workflow.getSchemaVersion())
                .build();
    }

    /**
     * Checks if the given workflow would conflict with existing versions.
     *
     * @param workflow workflow to check
     * @return conflict check result
     */
    public IVersionedWorkflowStorage.ConflictCheckResult checkConflict(IVersionedWorkflow workflow) {
        return storage.checkConflict(workflow);
    }

    /**
     * Gets the suggested non-conflicting version for a workflow.
     *
     * @param workflowId workflow identifier
     * @param proposedVersion proposed version
     * @return suggested version that won't conflict
     */
    public IWorkflowVersion suggestVersion(String workflowId, IWorkflowVersion proposedVersion) {
        return storage.suggestNonConflictingVersion(workflowId, proposedVersion);
    }
}
