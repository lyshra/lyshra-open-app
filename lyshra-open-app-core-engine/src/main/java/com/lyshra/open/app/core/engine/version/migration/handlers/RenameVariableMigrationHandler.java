package com.lyshra.open.app.core.engine.version.migration.handlers;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationContext;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Default migration handler for workflows where variables have been renamed.
 *
 * <p>This handler safely migrates executions when variables have been renamed
 * between versions. It handles:</p>
 * <ul>
 *   <li>Simple variable renaming (oldName -> newName)</li>
 *   <li>Variable removal (no longer needed)</li>
 *   <li>Variable transformation (type changes, value mapping)</li>
 *   <li>New required variables with defaults</li>
 * </ul>
 *
 * <p>Migration Strategy:</p>
 * <ul>
 *   <li>Rename variables according to mapping</li>
 *   <li>Apply transformers for type/value changes</li>
 *   <li>Add default values for new required variables</li>
 *   <li>Remove deprecated variables</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * IMigrationHandler handler = RenameVariableMigrationHandler.builder()
 *     .workflowId("order-processing")
 *     .sourceVersionPattern("1.x.x")
 *     .targetVersion(WorkflowVersion.parse("2.0.0"))
 *     .rename("orderTotal", "order_total_amount")
 *     .rename("custId", "customer_id")
 *     .transform("price", "price_cents", v -> ((Number) v).intValue() * 100)
 *     .addDefault("currency", "USD")
 *     .remove("legacyFlag")
 *     .build();
 * }</pre>
 */
@Slf4j
public class RenameVariableMigrationHandler extends AbstractMigrationHandler {

    private final Map<String, String> variableRenames;
    private final Map<String, Function<Object, Object>> variableTransformers;
    private final Map<String, Object> defaultVariables;
    private final Set<String> variablesToRemove;
    private final boolean failOnMissingVariable;

    private RenameVariableMigrationHandler(Builder builder) {
        super(builder.workflowId,
              builder.sourceVersionPattern,
              builder.targetVersion,
              builder.priority,
              "Handles migration when variables are renamed");
        this.variableRenames = builder.variableRenames;
        this.variableTransformers = builder.variableTransformers;
        this.defaultVariables = builder.defaultVariables;
        this.variablesToRemove = builder.variablesToRemove;
        this.failOnMissingVariable = builder.failOnMissingVariable;
    }

    @Override
    protected void doValidate(IMigrationContext context, List<String> errors, List<String> warnings) {
        ILyshraOpenAppContext execContext = context.getExecutionContext();

        // Check for variables to rename
        for (Map.Entry<String, String> rename : variableRenames.entrySet()) {
            String oldName = rename.getKey();
            String newName = rename.getValue();

            if (execContext.hasVariable(oldName)) {
                warnings.add("Variable '" + oldName + "' will be renamed to '" + newName + "'");
            } else if (failOnMissingVariable) {
                errors.add("Variable '" + oldName + "' not found but is required for rename");
            } else {
                warnings.add("Variable '" + oldName + "' not present, rename will be skipped");
            }

            // Check for conflicts
            if (execContext.hasVariable(newName)) {
                warnings.add("Target variable '" + newName + "' already exists, will be overwritten");
            }
        }

        // Check for variables with transformers
        for (String varName : variableTransformers.keySet()) {
            if (!execContext.hasVariable(varName) && failOnMissingVariable) {
                errors.add("Variable '" + varName + "' not found but is required for transformation");
            }
        }

        // Check for variables to remove
        for (String varName : variablesToRemove) {
            if (execContext.hasVariable(varName)) {
                warnings.add("Variable '" + varName + "' will be removed");
            }
        }

        // Info about new default variables
        for (String varName : defaultVariables.keySet()) {
            if (!execContext.hasVariable(varName)) {
                warnings.add("New variable '" + varName + "' will be added with default value");
            }
        }
    }

    @Override
    protected void doMigrate(
            IMigrationContext context,
            ILyshraOpenAppContext executionContext,
            List<String> warnings) {

        int renamed = 0;
        int transformed = 0;
        int removed = 0;
        int added = 0;

        // Step 1: Apply transformations (before renames, in case transform targets different name)
        for (Map.Entry<String, Function<Object, Object>> entry : variableTransformers.entrySet()) {
            String varName = entry.getKey();
            Function<Object, Object> transformer = entry.getValue();

            if (executionContext.hasVariable(varName)) {
                Object oldValue = executionContext.getVariable(varName);
                try {
                    Object newValue = transformer.apply(oldValue);
                    executionContext.removeVariable(varName);

                    // Check if there's also a rename for this variable
                    String targetName = variableRenames.getOrDefault(varName, varName);
                    executionContext.addVariable(targetName, newValue);

                    log.debug("Transformed variable '{}' -> '{}': {} -> {}",
                            varName, targetName, oldValue, newValue);
                    transformed++;

                    // If we also renamed, don't process in rename loop
                    if (!targetName.equals(varName)) {
                        renamed++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to transform variable '{}': {}", varName, e.getMessage());
                    warnings.add("Failed to transform variable '" + varName + "': " + e.getMessage());
                }
            }
        }

        // Step 2: Apply renames (skip those already handled by transformers)
        for (Map.Entry<String, String> rename : variableRenames.entrySet()) {
            String oldName = rename.getKey();
            String newName = rename.getValue();

            // Skip if already handled by transformer
            if (variableTransformers.containsKey(oldName)) {
                continue;
            }

            if (executionContext.hasVariable(oldName)) {
                Object value = executionContext.getVariable(oldName);
                executionContext.removeVariable(oldName);
                executionContext.addVariable(newName, value);
                log.debug("Renamed variable '{}' -> '{}'", oldName, newName);
                renamed++;
            }
        }

        // Step 3: Remove deprecated variables
        for (String varName : variablesToRemove) {
            if (executionContext.hasVariable(varName)) {
                executionContext.removeVariable(varName);
                log.debug("Removed deprecated variable '{}'", varName);
                removed++;
            }
        }

        // Step 4: Add default values for new variables
        for (Map.Entry<String, Object> entry : defaultVariables.entrySet()) {
            String varName = entry.getKey();
            Object defaultValue = entry.getValue();

            if (!executionContext.hasVariable(varName)) {
                executionContext.addVariable(varName, defaultValue);
                log.debug("Added new variable '{}' with default value", varName);
                added++;
            }
        }

        log.info("RenameVariableMigrationHandler: Execution [{}] - " +
                "renamed: {}, transformed: {}, removed: {}, added: {}",
                context.getExecutionId(), renamed, transformed, removed, added);
    }

    @Override
    protected void doRollback(IMigrationContext context, ILyshraOpenAppContext executionContext) {
        // The base class already restores the original variables from snapshot
        // No additional rollback needed for variable changes
        log.debug("Variable changes rolled back from pre-migration snapshot");
    }

    /**
     * Creates a new builder for RenameVariableMigrationHandler.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for RenameVariableMigrationHandler.
     */
    public static class Builder {
        private String workflowId;
        private String sourceVersionPattern;
        private IWorkflowVersion targetVersion;
        private int priority = 0;
        private Map<String, String> variableRenames = new HashMap<>();
        private Map<String, Function<Object, Object>> variableTransformers = new HashMap<>();
        private Map<String, Object> defaultVariables = new HashMap<>();
        private Set<String> variablesToRemove = new HashSet<>();
        private boolean failOnMissingVariable = false;

        public Builder workflowId(String workflowId) {
            this.workflowId = workflowId;
            return this;
        }

        public Builder sourceVersionPattern(String pattern) {
            this.sourceVersionPattern = pattern;
            return this;
        }

        public Builder targetVersion(IWorkflowVersion version) {
            this.targetVersion = version;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        /**
         * Adds a simple variable rename.
         */
        public Builder rename(String oldName, String newName) {
            this.variableRenames.put(oldName, newName);
            return this;
        }

        /**
         * Sets all variable renames.
         */
        public Builder renames(Map<String, String> renames) {
            this.variableRenames = new HashMap<>(renames);
            return this;
        }

        /**
         * Adds a variable transformation with rename.
         * The transformer converts the old value to a new value.
         */
        public Builder transform(String oldName, String newName, Function<Object, Object> transformer) {
            this.variableRenames.put(oldName, newName);
            this.variableTransformers.put(oldName, transformer);
            return this;
        }

        /**
         * Adds a variable transformation without rename.
         */
        public Builder transform(String name, Function<Object, Object> transformer) {
            this.variableTransformers.put(name, transformer);
            return this;
        }

        /**
         * Adds a default value for a new required variable.
         */
        public Builder addDefault(String name, Object value) {
            this.defaultVariables.put(name, value);
            return this;
        }

        /**
         * Sets all default variables.
         */
        public Builder defaults(Map<String, Object> defaults) {
            this.defaultVariables = new HashMap<>(defaults);
            return this;
        }

        /**
         * Marks a variable for removal.
         */
        public Builder remove(String name) {
            this.variablesToRemove.add(name);
            return this;
        }

        /**
         * Sets all variables to remove.
         */
        public Builder removeAll(Set<String> names) {
            this.variablesToRemove = new HashSet<>(names);
            return this;
        }

        /**
         * If true, validation fails when a variable to rename is missing.
         * Default is false (missing variables are skipped with warning).
         */
        public Builder failOnMissingVariable(boolean fail) {
            this.failOnMissingVariable = fail;
            return this;
        }

        public RenameVariableMigrationHandler build() {
            if (workflowId == null || workflowId.isBlank()) {
                throw new IllegalArgumentException("workflowId is required");
            }
            if (sourceVersionPattern == null || sourceVersionPattern.isBlank()) {
                throw new IllegalArgumentException("sourceVersionPattern is required");
            }
            if (targetVersion == null) {
                throw new IllegalArgumentException("targetVersion is required");
            }
            if (variableRenames.isEmpty() && variableTransformers.isEmpty() &&
                defaultVariables.isEmpty() && variablesToRemove.isEmpty()) {
                throw new IllegalArgumentException(
                        "At least one operation (rename, transform, add default, or remove) must be specified");
            }
            return new RenameVariableMigrationHandler(this);
        }
    }
}
