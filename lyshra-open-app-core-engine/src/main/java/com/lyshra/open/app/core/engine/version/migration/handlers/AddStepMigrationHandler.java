package com.lyshra.open.app.core.engine.version.migration.handlers;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationContext;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default migration handler for workflows where new steps have been added.
 *
 * <p>This handler safely migrates executions when the target version has additional steps
 * that don't exist in the source version. It handles:</p>
 * <ul>
 *   <li>New steps inserted before the current step</li>
 *   <li>New steps inserted after the current step</li>
 *   <li>New steps inserted in parallel branches</li>
 * </ul>
 *
 * <p>Migration Strategy:</p>
 * <ul>
 *   <li>If current step exists in target, continue from same step</li>
 *   <li>If current step was renamed, map to new name</li>
 *   <li>New steps are naturally encountered during execution flow</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * IMigrationHandler handler = AddStepMigrationHandler.builder()
 *     .workflowId("order-processing")
 *     .sourceVersionPattern("1.0.x")
 *     .targetVersion(WorkflowVersion.parse("1.1.0"))
 *     .addedSteps(Set.of("validateInventory", "notifyWarehouse"))
 *     .build();
 * }</pre>
 */
@Slf4j
public class AddStepMigrationHandler extends AbstractMigrationHandler {

    private final Set<String> addedSteps;
    private final Map<String, String> stepMappings;
    private final Map<String, Object> defaultVariables;

    private AddStepMigrationHandler(Builder builder) {
        super(builder.workflowId,
              builder.sourceVersionPattern,
              builder.targetVersion,
              builder.priority,
              "Handles migration when new steps are added to workflow");
        this.addedSteps = builder.addedSteps;
        this.stepMappings = builder.stepMappings;
        this.defaultVariables = builder.defaultVariables;
    }

    @Override
    protected void doValidate(IMigrationContext context, List<String> errors, List<String> warnings) {
        String currentStep = context.getCurrentStepName();

        // Check if current step is one of the newly added steps (shouldn't happen, but check)
        if (addedSteps.contains(currentStep)) {
            errors.add("Current step '" + currentStep + "' is a newly added step - " +
                    "execution should not be at this step in the source version");
            return;
        }

        // Info about added steps
        if (!addedSteps.isEmpty()) {
            warnings.add("New steps added in target version: " + addedSteps);
        }

        // Check if step mapping is needed
        if (stepMappings.containsKey(currentStep)) {
            String mappedStep = stepMappings.get(currentStep);
            warnings.add("Step '" + currentStep + "' will be mapped to '" + mappedStep + "'");
        }
    }

    @Override
    protected void doMigrate(
            IMigrationContext context,
            ILyshraOpenAppContext executionContext,
            List<String> warnings) {

        // Add any default variables required by new steps
        if (!defaultVariables.isEmpty()) {
            for (Map.Entry<String, Object> entry : defaultVariables.entrySet()) {
                String varName = entry.getKey();
                Object defaultValue = entry.getValue();

                if (!executionContext.hasVariable(varName)) {
                    executionContext.addVariable(varName, defaultValue);
                    log.debug("Added default variable '{}' with value '{}' for new steps",
                            varName, defaultValue);
                    warnings.add("Added default variable '" + varName + "' for new steps");
                }
            }
        }

        log.info("AddStepMigrationHandler: Migrated execution [{}] - {} new steps available: {}",
                context.getExecutionId(), addedSteps.size(), addedSteps);
    }

    @Override
    protected String determineTargetStep(IMigrationContext context) {
        String currentStep = context.getCurrentStepName();

        // Apply step mapping if exists
        if (stepMappings.containsKey(currentStep)) {
            return stepMappings.get(currentStep);
        }

        // Otherwise keep the same step
        return currentStep;
    }

    /**
     * Creates a new builder for AddStepMigrationHandler.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for AddStepMigrationHandler.
     */
    public static class Builder {
        private String workflowId;
        private String sourceVersionPattern;
        private IWorkflowVersion targetVersion;
        private int priority = 0;
        private Set<String> addedSteps = Set.of();
        private Map<String, String> stepMappings = new HashMap<>();
        private Map<String, Object> defaultVariables = new HashMap<>();

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
         * Sets the steps that were added in the target version.
         */
        public Builder addedSteps(Set<String> steps) {
            this.addedSteps = steps;
            return this;
        }

        /**
         * Adds a step mapping for renamed steps.
         */
        public Builder stepMapping(String oldStep, String newStep) {
            this.stepMappings.put(oldStep, newStep);
            return this;
        }

        /**
         * Sets all step mappings.
         */
        public Builder stepMappings(Map<String, String> mappings) {
            this.stepMappings = new HashMap<>(mappings);
            return this;
        }

        /**
         * Adds a default variable value required by new steps.
         */
        public Builder defaultVariable(String name, Object value) {
            this.defaultVariables.put(name, value);
            return this;
        }

        /**
         * Sets all default variables.
         */
        public Builder defaultVariables(Map<String, Object> variables) {
            this.defaultVariables = new HashMap<>(variables);
            return this;
        }

        public AddStepMigrationHandler build() {
            if (workflowId == null || workflowId.isBlank()) {
                throw new IllegalArgumentException("workflowId is required");
            }
            if (sourceVersionPattern == null || sourceVersionPattern.isBlank()) {
                throw new IllegalArgumentException("sourceVersionPattern is required");
            }
            if (targetVersion == null) {
                throw new IllegalArgumentException("targetVersion is required");
            }
            return new AddStepMigrationHandler(this);
        }
    }
}
