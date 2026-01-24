package com.lyshra.open.app.core.engine.version.migration.handlers;

import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowMigrationHints;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Factory for creating default migration handlers for common workflow changes.
 *
 * <p>This factory simplifies the creation of migration handlers by providing
 * convenient methods for common scenarios:</p>
 * <ul>
 *   <li>{@link #forAddedSteps} - when new steps are added</li>
 *   <li>{@link #forRemovedSteps} - when steps are removed</li>
 *   <li>{@link #forRenamedVariables} - when variables are renamed</li>
 *   <li>{@link #fromWorkflowDiff} - auto-detect changes between versions</li>
 *   <li>{@link #fromMigrationHints} - create from workflow migration hints</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * // Simple handler for added steps
 * IMigrationHandler handler = DefaultMigrationHandlerFactory.forAddedSteps(
 *     "order-processing",
 *     "1.x.x",
 *     targetVersion,
 *     Set.of("validateInventory", "notifyWarehouse"));
 *
 * // Handler for renamed variables
 * IMigrationHandler handler = DefaultMigrationHandlerFactory.forRenamedVariables(
 *     "order-processing",
 *     "1.x.x",
 *     targetVersion,
 *     Map.of("orderTotal", "order_total_amount",
 *            "custId", "customer_id"));
 *
 * // Auto-create from workflow diff
 * IMigrationHandler handler = DefaultMigrationHandlerFactory.fromWorkflowDiff(
 *     sourceWorkflow, targetWorkflow);
 * }</pre>
 */
@Slf4j
public final class DefaultMigrationHandlerFactory {

    private DefaultMigrationHandlerFactory() {
        // Utility class
    }

    /**
     * Creates a handler for when new steps have been added to a workflow.
     *
     * @param workflowId workflow identifier
     * @param sourceVersionPattern source version pattern (e.g., "1.x.x")
     * @param targetVersion target version
     * @param addedSteps steps that were added
     * @return migration handler
     */
    public static IMigrationHandler forAddedSteps(
            String workflowId,
            String sourceVersionPattern,
            IWorkflowVersion targetVersion,
            Set<String> addedSteps) {

        return AddStepMigrationHandler.builder()
                .workflowId(workflowId)
                .sourceVersionPattern(sourceVersionPattern)
                .targetVersion(targetVersion)
                .addedSteps(addedSteps)
                .build();
    }

    /**
     * Creates a handler for when new steps have been added with default variable values.
     *
     * @param workflowId workflow identifier
     * @param sourceVersionPattern source version pattern
     * @param targetVersion target version
     * @param addedSteps steps that were added
     * @param defaultVariables default values for new required variables
     * @return migration handler
     */
    public static IMigrationHandler forAddedSteps(
            String workflowId,
            String sourceVersionPattern,
            IWorkflowVersion targetVersion,
            Set<String> addedSteps,
            Map<String, Object> defaultVariables) {

        return AddStepMigrationHandler.builder()
                .workflowId(workflowId)
                .sourceVersionPattern(sourceVersionPattern)
                .targetVersion(targetVersion)
                .addedSteps(addedSteps)
                .defaultVariables(defaultVariables)
                .build();
    }

    /**
     * Creates a handler for when steps have been removed from a workflow.
     *
     * @param workflowId workflow identifier
     * @param sourceVersionPattern source version pattern
     * @param targetVersion target version
     * @param stepReplacements map of removed step to replacement step
     * @return migration handler
     */
    public static IMigrationHandler forRemovedSteps(
            String workflowId,
            String sourceVersionPattern,
            IWorkflowVersion targetVersion,
            Map<String, String> stepReplacements) {

        RemoveStepMigrationHandler.Builder builder = RemoveStepMigrationHandler.builder()
                .workflowId(workflowId)
                .sourceVersionPattern(sourceVersionPattern)
                .targetVersion(targetVersion);

        for (Map.Entry<String, String> entry : stepReplacements.entrySet()) {
            builder.removedStep(entry.getKey());
            builder.stepReplacement(entry.getKey(), entry.getValue());
        }

        return builder.build();
    }

    /**
     * Creates a handler for when steps have been removed with a default replacement.
     *
     * @param workflowId workflow identifier
     * @param sourceVersionPattern source version pattern
     * @param targetVersion target version
     * @param removedSteps steps that were removed
     * @param defaultReplacementStep default step to use when at a removed step
     * @return migration handler
     */
    public static IMigrationHandler forRemovedSteps(
            String workflowId,
            String sourceVersionPattern,
            IWorkflowVersion targetVersion,
            Set<String> removedSteps,
            String defaultReplacementStep) {

        return RemoveStepMigrationHandler.builder()
                .workflowId(workflowId)
                .sourceVersionPattern(sourceVersionPattern)
                .targetVersion(targetVersion)
                .removedSteps(removedSteps)
                .defaultReplacementStep(defaultReplacementStep)
                .build();
    }

    /**
     * Creates a handler for simple variable renames.
     *
     * @param workflowId workflow identifier
     * @param sourceVersionPattern source version pattern
     * @param targetVersion target version
     * @param variableRenames map of old name to new name
     * @return migration handler
     */
    public static IMigrationHandler forRenamedVariables(
            String workflowId,
            String sourceVersionPattern,
            IWorkflowVersion targetVersion,
            Map<String, String> variableRenames) {

        return RenameVariableMigrationHandler.builder()
                .workflowId(workflowId)
                .sourceVersionPattern(sourceVersionPattern)
                .targetVersion(targetVersion)
                .renames(variableRenames)
                .build();
    }

    /**
     * Creates a handler for variable renames with transformations.
     *
     * @param workflowId workflow identifier
     * @param sourceVersionPattern source version pattern
     * @param targetVersion target version
     * @param variableRenames map of old name to new name
     * @param transformers map of variable name to transformation function
     * @return migration handler
     */
    public static IMigrationHandler forRenamedVariables(
            String workflowId,
            String sourceVersionPattern,
            IWorkflowVersion targetVersion,
            Map<String, String> variableRenames,
            Map<String, Function<Object, Object>> transformers) {

        RenameVariableMigrationHandler.Builder builder = RenameVariableMigrationHandler.builder()
                .workflowId(workflowId)
                .sourceVersionPattern(sourceVersionPattern)
                .targetVersion(targetVersion)
                .renames(variableRenames);

        for (Map.Entry<String, Function<Object, Object>> entry : transformers.entrySet()) {
            builder.transform(entry.getKey(), entry.getValue());
        }

        return builder.build();
    }

    /**
     * Creates a handler automatically from workflow migration hints.
     *
     * @param workflowId workflow identifier
     * @param sourceVersionPattern source version pattern
     * @param targetWorkflow target workflow with migration hints
     * @return migration handler
     */
    public static IMigrationHandler fromMigrationHints(
            String workflowId,
            String sourceVersionPattern,
            IVersionedWorkflow targetWorkflow) {

        Optional<IWorkflowMigrationHints> hintsOpt = targetWorkflow.getMigrationHints();
        if (hintsOpt.isEmpty()) {
            log.warn("No migration hints available for workflow [{}] version [{}]",
                    workflowId, targetWorkflow.getVersion().toVersionString());
            return createNoOpHandler(workflowId, sourceVersionPattern, targetWorkflow.getVersion());
        }

        IWorkflowMigrationHints hints = hintsOpt.get();
        CompositeDefaultMigrationHandler.Builder builder = CompositeDefaultMigrationHandler.builder()
                .workflowId(workflowId)
                .sourceVersionPattern(sourceVersionPattern)
                .targetVersion(targetWorkflow.getVersion());

        // Add variable mappings
        for (Map.Entry<String, String> entry : hints.getVariableMappings().entrySet()) {
            builder.renameVariable(entry.getKey(), entry.getValue());
        }

        // Add step mappings
        for (Map.Entry<String, String> entry : hints.getStepNameMappings().entrySet()) {
            builder.stepMapping(entry.getKey(), entry.getValue());
        }

        // Handle removed steps from workflow definition
        Set<String> removedSteps = targetWorkflow.getRemovedSteps();
        for (String removedStep : removedSteps) {
            Optional<String> replacement = hints.getSuggestedMigrationStep(removedStep);
            if (replacement.isPresent()) {
                builder.removedStep(removedStep, replacement.get());
            }
        }

        return builder.build();
    }

    /**
     * Creates a handler by comparing two workflow versions.
     * Automatically detects added/removed steps and suggests migrations.
     *
     * @param sourceWorkflow source workflow definition
     * @param targetWorkflow target workflow definition
     * @return migration handler
     */
    public static IMigrationHandler fromWorkflowDiff(
            IVersionedWorkflow sourceWorkflow,
            IVersionedWorkflow targetWorkflow) {

        String workflowId = sourceWorkflow.getWorkflowId();
        String sourceVersionPattern = sourceWorkflow.getVersion().toVersionString();

        // Detect added steps
        Set<String> sourceSteps = sourceWorkflow.getSteps().keySet();
        Set<String> targetSteps = targetWorkflow.getSteps().keySet();

        Set<String> addedSteps = new HashSet<>(targetSteps);
        addedSteps.removeAll(sourceSteps);

        Set<String> removedSteps = new HashSet<>(sourceSteps);
        removedSteps.removeAll(targetSteps);

        // Build the composite handler
        CompositeDefaultMigrationHandler.Builder builder = CompositeDefaultMigrationHandler.builder()
                .workflowId(workflowId)
                .sourceVersionPattern(sourceVersionPattern)
                .targetVersion(targetWorkflow.getVersion());

        // Add detected steps
        if (!addedSteps.isEmpty()) {
            builder.addedSteps(addedSteps);
            log.debug("Detected added steps: {}", addedSteps);
        }

        // Handle removed steps - use start step as default replacement
        if (!removedSteps.isEmpty()) {
            String defaultReplacement = targetWorkflow.getStartStep();
            builder.defaultReplacementStep(defaultReplacement);

            for (String removed : removedSteps) {
                builder.removedStep(removed, defaultReplacement);
            }
            log.debug("Detected removed steps: {} -> default to '{}'", removedSteps, defaultReplacement);
        }

        // Add migration hints if available
        targetWorkflow.getMigrationHints().ifPresent(hints -> {
            for (Map.Entry<String, String> entry : hints.getVariableMappings().entrySet()) {
                builder.renameVariable(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, String> entry : hints.getStepNameMappings().entrySet()) {
                builder.stepMapping(entry.getKey(), entry.getValue());
            }
        });

        return builder.build();
    }

    /**
     * Creates a list of handlers for a multi-version migration path.
     *
     * @param workflowVersions list of workflow versions from source to target
     * @return list of handlers for each version hop
     */
    public static List<IMigrationHandler> forMigrationPath(List<IVersionedWorkflow> workflowVersions) {
        List<IMigrationHandler> handlers = new ArrayList<>();

        for (int i = 0; i < workflowVersions.size() - 1; i++) {
            IVersionedWorkflow source = workflowVersions.get(i);
            IVersionedWorkflow target = workflowVersions.get(i + 1);

            IMigrationHandler handler = fromWorkflowDiff(source, target);
            handlers.add(handler);
        }

        return handlers;
    }

    /**
     * Creates a no-op handler that passes through without changes.
     */
    public static IMigrationHandler createNoOpHandler(
            String workflowId,
            String sourceVersionPattern,
            IWorkflowVersion targetVersion) {

        return CompositeDefaultMigrationHandler.builder()
                .workflowId(workflowId)
                .sourceVersionPattern(sourceVersionPattern)
                .targetVersion(targetVersion)
                .build();
    }

    /**
     * Creates a builder for a composite handler with fluent API.
     */
    public static CompositeBuilder composite(
            String workflowId,
            String sourceVersionPattern,
            IWorkflowVersion targetVersion) {

        return new CompositeBuilder(workflowId, sourceVersionPattern, targetVersion);
    }

    /**
     * Fluent builder for composite migration handlers.
     */
    public static class CompositeBuilder {
        private final CompositeDefaultMigrationHandler.Builder delegate;

        CompositeBuilder(String workflowId, String sourceVersionPattern, IWorkflowVersion targetVersion) {
            this.delegate = CompositeDefaultMigrationHandler.builder()
                    .workflowId(workflowId)
                    .sourceVersionPattern(sourceVersionPattern)
                    .targetVersion(targetVersion);
        }

        public CompositeBuilder priority(int priority) {
            delegate.priority(priority);
            return this;
        }

        public CompositeBuilder addStep(String stepName) {
            delegate.addedStep(stepName);
            return this;
        }

        public CompositeBuilder addSteps(Set<String> stepNames) {
            delegate.addedSteps(stepNames);
            return this;
        }

        public CompositeBuilder removeStep(String stepName, String replacement) {
            delegate.removedStep(stepName, replacement);
            return this;
        }

        public CompositeBuilder mapStep(String oldStep, String newStep) {
            delegate.stepMapping(oldStep, newStep);
            return this;
        }

        public CompositeBuilder renameVariable(String oldName, String newName) {
            delegate.renameVariable(oldName, newName);
            return this;
        }

        public CompositeBuilder transformVariable(String oldName, String newName, Function<Object, Object> transformer) {
            delegate.transformVariable(oldName, newName, transformer);
            return this;
        }

        public CompositeBuilder addVariable(String name, Object defaultValue) {
            delegate.addDefaultVariable(name, defaultValue);
            return this;
        }

        public CompositeBuilder removeVariable(String name) {
            delegate.removeVariable(name);
            return this;
        }

        public IMigrationHandler build() {
            return delegate.build();
        }
    }
}
