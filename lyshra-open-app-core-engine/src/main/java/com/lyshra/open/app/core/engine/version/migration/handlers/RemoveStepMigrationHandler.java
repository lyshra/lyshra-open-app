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

/**
 * Default migration handler for workflows where steps have been removed.
 *
 * <p>This handler safely migrates executions when the target version has removed steps
 * that existed in the source version. It handles:</p>
 * <ul>
 *   <li>Execution currently at a removed step</li>
 *   <li>Execution that has already passed removed steps</li>
 *   <li>Removed steps in parallel branches</li>
 * </ul>
 *
 * <p>Migration Strategy:</p>
 * <ul>
 *   <li>If at removed step, jump to designated replacement step</li>
 *   <li>If past removed step, continue normally</li>
 *   <li>Clean up any step-specific variables if configured</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * IMigrationHandler handler = RemoveStepMigrationHandler.builder()
 *     .workflowId("order-processing")
 *     .sourceVersionPattern("1.0.x")
 *     .targetVersion(WorkflowVersion.parse("2.0.0"))
 *     .removedSteps(Set.of("legacyValidation", "oldNotification"))
 *     .stepReplacement("legacyValidation", "newValidation")
 *     .stepReplacement("oldNotification", "sendNotification")
 *     .build();
 * }</pre>
 */
@Slf4j
public class RemoveStepMigrationHandler extends AbstractMigrationHandler {

    private final Set<String> removedSteps;
    private final Map<String, String> stepReplacements;
    private final Set<String> variablesToCleanup;
    private final String defaultReplacementStep;

    private RemoveStepMigrationHandler(Builder builder) {
        super(builder.workflowId,
              builder.sourceVersionPattern,
              builder.targetVersion,
              builder.priority,
              "Handles migration when steps are removed from workflow");
        this.removedSteps = builder.removedSteps;
        this.stepReplacements = builder.stepReplacements;
        this.variablesToCleanup = builder.variablesToCleanup;
        this.defaultReplacementStep = builder.defaultReplacementStep;
    }

    @Override
    protected void doValidate(IMigrationContext context, List<String> errors, List<String> warnings) {
        String currentStep = context.getCurrentStepName();

        // Check if currently at a removed step
        if (removedSteps.contains(currentStep)) {
            if (stepReplacements.containsKey(currentStep)) {
                String replacement = stepReplacements.get(currentStep);
                warnings.add("Current step '" + currentStep + "' was removed, " +
                        "will migrate to replacement step '" + replacement + "'");
            } else if (defaultReplacementStep != null) {
                warnings.add("Current step '" + currentStep + "' was removed, " +
                        "will migrate to default replacement step '" + defaultReplacementStep + "'");
            } else {
                errors.add("Current step '" + currentStep + "' was removed and " +
                        "no replacement step is configured");
            }
        }

        // Info about all removed steps
        if (!removedSteps.isEmpty()) {
            warnings.add("Steps removed in target version: " + removedSteps);
        }

        // Warn about variables to be cleaned up
        if (!variablesToCleanup.isEmpty()) {
            ILyshraOpenAppContext execContext = context.getExecutionContext();
            for (String varName : variablesToCleanup) {
                if (execContext.hasVariable(varName)) {
                    warnings.add("Variable '" + varName + "' will be removed (associated with removed step)");
                }
            }
        }
    }

    @Override
    protected void doMigrate(
            IMigrationContext context,
            ILyshraOpenAppContext executionContext,
            List<String> warnings) {

        // Clean up variables associated with removed steps
        if (!variablesToCleanup.isEmpty()) {
            for (String varName : variablesToCleanup) {
                if (executionContext.hasVariable(varName)) {
                    executionContext.removeVariable(varName);
                    log.debug("Removed variable '{}' associated with removed step", varName);
                }
            }
        }

        String currentStep = context.getCurrentStepName();
        if (removedSteps.contains(currentStep)) {
            String targetStep = determineTargetStep(context);
            log.info("RemoveStepMigrationHandler: Execution [{}] at removed step '{}', " +
                    "migrating to '{}'",
                    context.getExecutionId(), currentStep, targetStep);
        } else {
            log.info("RemoveStepMigrationHandler: Execution [{}] not at removed step, " +
                    "migration complete. {} steps removed: {}",
                    context.getExecutionId(), removedSteps.size(), removedSteps);
        }
    }

    @Override
    protected String determineTargetStep(IMigrationContext context) {
        String currentStep = context.getCurrentStepName();

        // If at a removed step, find replacement
        if (removedSteps.contains(currentStep)) {
            if (stepReplacements.containsKey(currentStep)) {
                return stepReplacements.get(currentStep);
            }
            if (defaultReplacementStep != null) {
                return defaultReplacementStep;
            }
            // This shouldn't happen if validation passed
            throw new IllegalStateException("No replacement step for removed step: " + currentStep);
        }

        // Not at a removed step, keep current
        return currentStep;
    }

    @Override
    protected boolean isAtSafePoint(IMigrationContext context) {
        // If at a removed step, it's not really "safe" but we handle it
        // If not at a removed step, it's safe
        return !removedSteps.contains(context.getCurrentStepName());
    }

    /**
     * Creates a new builder for RemoveStepMigrationHandler.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for RemoveStepMigrationHandler.
     */
    public static class Builder {
        private String workflowId;
        private String sourceVersionPattern;
        private IWorkflowVersion targetVersion;
        private int priority = 0;
        private Set<String> removedSteps = new HashSet<>();
        private Map<String, String> stepReplacements = new HashMap<>();
        private Set<String> variablesToCleanup = new HashSet<>();
        private String defaultReplacementStep;

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
         * Adds a step that was removed in the target version.
         */
        public Builder removedStep(String stepName) {
            this.removedSteps.add(stepName);
            return this;
        }

        /**
         * Sets all steps that were removed in the target version.
         */
        public Builder removedSteps(Set<String> steps) {
            this.removedSteps = new HashSet<>(steps);
            return this;
        }

        /**
         * Specifies a replacement step for a removed step.
         */
        public Builder stepReplacement(String removedStep, String replacementStep) {
            this.stepReplacements.put(removedStep, replacementStep);
            return this;
        }

        /**
         * Sets all step replacements.
         */
        public Builder stepReplacements(Map<String, String> replacements) {
            this.stepReplacements = new HashMap<>(replacements);
            return this;
        }

        /**
         * Adds a variable to clean up (associated with a removed step).
         */
        public Builder cleanupVariable(String variableName) {
            this.variablesToCleanup.add(variableName);
            return this;
        }

        /**
         * Sets all variables to clean up.
         */
        public Builder cleanupVariables(Set<String> variables) {
            this.variablesToCleanup = new HashSet<>(variables);
            return this;
        }

        /**
         * Sets the default replacement step for any removed step without specific mapping.
         */
        public Builder defaultReplacementStep(String stepName) {
            this.defaultReplacementStep = stepName;
            return this;
        }

        public RemoveStepMigrationHandler build() {
            if (workflowId == null || workflowId.isBlank()) {
                throw new IllegalArgumentException("workflowId is required");
            }
            if (sourceVersionPattern == null || sourceVersionPattern.isBlank()) {
                throw new IllegalArgumentException("sourceVersionPattern is required");
            }
            if (targetVersion == null) {
                throw new IllegalArgumentException("targetVersion is required");
            }
            if (removedSteps.isEmpty()) {
                throw new IllegalArgumentException("At least one removed step must be specified");
            }
            // Validate all removed steps have a replacement
            for (String removed : removedSteps) {
                if (!stepReplacements.containsKey(removed) && defaultReplacementStep == null) {
                    throw new IllegalArgumentException(
                            "Removed step '" + removed + "' has no replacement configured. " +
                            "Either add a stepReplacement or set a defaultReplacementStep");
                }
            }
            return new RemoveStepMigrationHandler(this);
        }
    }
}
