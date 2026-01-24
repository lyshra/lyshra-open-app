package com.lyshra.open.app.core.engine.version.migration.handlers;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationContext;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationHandler;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationResult;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationValidationResult;
import com.lyshra.open.app.integration.models.version.migration.MigrationResult;
import com.lyshra.open.app.integration.models.version.migration.MigrationValidationResult;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Composite migration handler that combines multiple migration operations.
 *
 * <p>This handler allows combining add step, remove step, and rename variable
 * operations into a single handler for complex migrations. Operations are
 * applied in a specific order:</p>
 * <ol>
 *   <li>Variable transformations (type conversions, value mapping)</li>
 *   <li>Variable renames</li>
 *   <li>Variable removals</li>
 *   <li>Default variable additions</li>
 *   <li>Step mapping resolution</li>
 * </ol>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * IMigrationHandler handler = CompositeDefaultMigrationHandler.builder()
 *     .workflowId("order-processing")
 *     .sourceVersionPattern("1.x.x")
 *     .targetVersion(WorkflowVersion.parse("2.0.0"))
 *     // Step changes
 *     .addedSteps(Set.of("validateInventory", "notifyWarehouse"))
 *     .removedStep("legacyValidation", "newValidation")
 *     // Variable changes
 *     .renameVariable("orderTotal", "order_total_amount")
 *     .transformVariable("price", "price_cents", v -> ((Number) v).intValue() * 100)
 *     .removeVariable("legacyFlag")
 *     .addDefaultVariable("currency", "USD")
 *     .build();
 * }</pre>
 */
@Slf4j
public class CompositeDefaultMigrationHandler implements IMigrationHandler {

    private final String workflowId;
    private final String sourceVersionPattern;
    private final IWorkflowVersion targetVersion;
    private final int priority;

    // Step operations
    private final Set<String> addedSteps;
    private final Set<String> removedSteps;
    private final Map<String, String> stepMappings;
    private final String defaultReplacementStep;

    // Variable operations
    private final Map<String, String> variableRenames;
    private final Map<String, Function<Object, Object>> variableTransformers;
    private final Map<String, Object> defaultVariables;
    private final Set<String> variablesToRemove;

    private CompositeDefaultMigrationHandler(Builder builder) {
        this.workflowId = builder.workflowId;
        this.sourceVersionPattern = builder.sourceVersionPattern;
        this.targetVersion = builder.targetVersion;
        this.priority = builder.priority;
        this.addedSteps = builder.addedSteps;
        this.removedSteps = builder.removedSteps;
        this.stepMappings = builder.stepMappings;
        this.defaultReplacementStep = builder.defaultReplacementStep;
        this.variableRenames = builder.variableRenames;
        this.variableTransformers = builder.variableTransformers;
        this.defaultVariables = builder.defaultVariables;
        this.variablesToRemove = builder.variablesToRemove;
    }

    @Override
    public String getWorkflowId() {
        return workflowId;
    }

    @Override
    public String getSourceVersionPattern() {
        return sourceVersionPattern;
    }

    @Override
    public IWorkflowVersion getTargetVersion() {
        return targetVersion;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean canHandle(IMigrationContext context) {
        return workflowId.equals(context.getWorkflowId()) &&
               matchesSourceVersion(context.getSourceVersion()) &&
               targetVersion.equals(context.getTargetVersion());
    }

    @Override
    public Mono<IMigrationValidationResult> validate(IMigrationContext context) {
        return Mono.fromCallable(() -> {
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            ILyshraOpenAppContext execContext = context.getExecutionContext();
            String currentStep = context.getCurrentStepName();

            // Validate step operations
            if (addedSteps.contains(currentStep)) {
                errors.add("Current step '" + currentStep + "' is a newly added step");
            }

            if (removedSteps.contains(currentStep)) {
                if (stepMappings.containsKey(currentStep)) {
                    warnings.add("Current step '" + currentStep + "' was removed, " +
                            "will migrate to '" + stepMappings.get(currentStep) + "'");
                } else if (defaultReplacementStep != null) {
                    warnings.add("Current step '" + currentStep + "' was removed, " +
                            "will migrate to default step '" + defaultReplacementStep + "'");
                } else {
                    errors.add("Current step '" + currentStep + "' was removed with no replacement");
                }
            }

            // Info about step changes
            if (!addedSteps.isEmpty()) {
                warnings.add("New steps in target: " + addedSteps);
            }
            if (!removedSteps.isEmpty()) {
                warnings.add("Removed steps: " + removedSteps);
            }

            // Validate variable operations
            for (Map.Entry<String, String> rename : variableRenames.entrySet()) {
                if (execContext.hasVariable(rename.getKey())) {
                    warnings.add("Variable '" + rename.getKey() + "' -> '" + rename.getValue() + "'");
                }
            }

            for (String varName : variablesToRemove) {
                if (execContext.hasVariable(varName)) {
                    warnings.add("Variable '" + varName + "' will be removed");
                }
            }

            for (String varName : defaultVariables.keySet()) {
                if (!execContext.hasVariable(varName)) {
                    warnings.add("Variable '" + varName + "' will be added with default value");
                }
            }

            // Determine target step
            String targetStep = determineTargetStep(context);

            if (!errors.isEmpty()) {
                return MigrationValidationResult.invalid(errors);
            }

            if (!warnings.isEmpty()) {
                return MigrationValidationResult.validWithWarnings(targetStep, true, warnings);
            }

            return MigrationValidationResult.valid(targetStep, true);
        });
    }

    @Override
    public Mono<IMigrationResult> migrate(IMigrationContext context) {
        Instant startTime = Instant.now();

        return validate(context)
                .<IMigrationResult>flatMap(validation -> {
                    if (!validation.isValid()) {
                        IMigrationResult failedValidation = MigrationResult.failed(
                                IMigrationResult.Status.FAILED_VALIDATION,
                                context.getExecutionId(),
                                context.getSourceVersion(),
                                context.getTargetVersion(),
                                new IllegalStateException("Validation failed: " + validation.getErrors()),
                                Duration.between(startTime, Instant.now()),
                                context.getPreMigrationState(),
                                List.of());
                        return Mono.just(failedValidation);
                    }

                    return Mono.fromCallable(() -> {
                        ILyshraOpenAppContext execContext = context.getExecutionContext();

                        // Apply all operations
                        applyVariableTransformations(execContext);
                        applyVariableRenames(execContext);
                        removeVariables(execContext);
                        addDefaultVariables(execContext);

                        String targetStep = validation.getRecommendedTargetStep()
                                .orElse(determineTargetStep(context));

                        Map<String, Object> postMigrationSnapshot = new HashMap<>();
                        postMigrationSnapshot.put("data", execContext.getData());
                        postMigrationSnapshot.put("variables", new HashMap<>(execContext.getVariables()));
                        postMigrationSnapshot.put("currentStep", targetStep);

                        log.info("CompositeDefaultMigrationHandler completed: {} -> {} at step {}",
                                context.getSourceVersion().toVersionString(),
                                context.getTargetVersion().toVersionString(),
                                targetStep);

                        return (IMigrationResult) MigrationResult.success(
                                context.getExecutionId(),
                                context.getSourceVersion(),
                                context.getTargetVersion(),
                                execContext,
                                targetStep,
                                Duration.between(startTime, Instant.now()),
                                context.getPreMigrationState(),
                                postMigrationSnapshot,
                                List.of());
                    });
                })
                .onErrorResume(error -> {
                    log.error("CompositeDefaultMigrationHandler failed: {}", error.getMessage(), error);
                    IMigrationResult failedResult = MigrationResult.failed(
                            IMigrationResult.Status.FAILED_HANDLER_ERROR,
                            context.getExecutionId(),
                            context.getSourceVersion(),
                            context.getTargetVersion(),
                            error,
                            Duration.between(startTime, Instant.now()),
                            context.getPreMigrationState(),
                            List.of());
                    return Mono.just(failedResult);
                });
    }

    @Override
    public Mono<IMigrationResult> rollback(IMigrationContext context, IMigrationResult failedResult) {
        return Mono.fromCallable(() -> {
            ILyshraOpenAppContext execContext = context.getExecutionContext();

            // Restore from pre-migration snapshot
            Map<String, Object> preState = context.getPreMigrationState();
            Object originalData = preState.get("data");
            if (originalData != null) {
                execContext.setData(originalData);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> originalVariables = (Map<String, Object>) preState.get("variables");
            if (originalVariables != null) {
                execContext.setVariables(new HashMap<>(originalVariables));
            }

            log.info("CompositeDefaultMigrationHandler rollback completed for [{}]",
                    context.getExecutionId());

            return MigrationResult.builder()
                    .status(IMigrationResult.Status.ROLLED_BACK)
                    .executionId(context.getExecutionId())
                    .sourceVersion(context.getSourceVersion())
                    .targetVersion(context.getTargetVersion())
                    .migratedContext(execContext)
                    .targetStepName(context.getCurrentStepName())
                    .preMigrationSnapshot(context.getPreMigrationState())
                    .duration(Duration.ZERO)
                    .build();
        });
    }

    private boolean matchesSourceVersion(IWorkflowVersion version) {
        if (sourceVersionPattern == null) return false;
        if (sourceVersionPattern.equals(version.toVersionString())) return true;
        if (sourceVersionPattern.equals("*") || sourceVersionPattern.equals("*.*.*")) return true;

        String regex = sourceVersionPattern
                .replace(".", "\\.")
                .replace("x", "\\d+")
                .replace("*", ".*");
        return version.toVersionString().matches("^" + regex + "$");
    }

    private String determineTargetStep(IMigrationContext context) {
        String currentStep = context.getCurrentStepName();

        if (removedSteps.contains(currentStep)) {
            if (stepMappings.containsKey(currentStep)) {
                return stepMappings.get(currentStep);
            }
            if (defaultReplacementStep != null) {
                return defaultReplacementStep;
            }
        }

        if (stepMappings.containsKey(currentStep)) {
            return stepMappings.get(currentStep);
        }

        return currentStep;
    }

    private void applyVariableTransformations(ILyshraOpenAppContext context) {
        for (Map.Entry<String, Function<Object, Object>> entry : variableTransformers.entrySet()) {
            String varName = entry.getKey();
            if (context.hasVariable(varName)) {
                Object oldValue = context.getVariable(varName);
                Object newValue = entry.getValue().apply(oldValue);
                context.removeVariable(varName);

                String targetName = variableRenames.getOrDefault(varName, varName);
                context.addVariable(targetName, newValue);
                log.debug("Transformed variable '{}' -> '{}'", varName, targetName);
            }
        }
    }

    private void applyVariableRenames(ILyshraOpenAppContext context) {
        for (Map.Entry<String, String> rename : variableRenames.entrySet()) {
            String oldName = rename.getKey();
            String newName = rename.getValue();

            // Skip if already handled by transformer
            if (variableTransformers.containsKey(oldName)) continue;

            if (context.hasVariable(oldName)) {
                Object value = context.getVariable(oldName);
                context.removeVariable(oldName);
                context.addVariable(newName, value);
                log.debug("Renamed variable '{}' -> '{}'", oldName, newName);
            }
        }
    }

    private void removeVariables(ILyshraOpenAppContext context) {
        for (String varName : variablesToRemove) {
            if (context.hasVariable(varName)) {
                context.removeVariable(varName);
                log.debug("Removed variable '{}'", varName);
            }
        }
    }

    private void addDefaultVariables(ILyshraOpenAppContext context) {
        for (Map.Entry<String, Object> entry : defaultVariables.entrySet()) {
            if (!context.hasVariable(entry.getKey())) {
                context.addVariable(entry.getKey(), entry.getValue());
                log.debug("Added default variable '{}'", entry.getKey());
            }
        }
    }

    /**
     * Creates a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for CompositeDefaultMigrationHandler.
     */
    public static class Builder {
        private String workflowId;
        private String sourceVersionPattern;
        private IWorkflowVersion targetVersion;
        private int priority = 0;

        private Set<String> addedSteps = new HashSet<>();
        private Set<String> removedSteps = new HashSet<>();
        private Map<String, String> stepMappings = new HashMap<>();
        private String defaultReplacementStep;

        private Map<String, String> variableRenames = new HashMap<>();
        private Map<String, Function<Object, Object>> variableTransformers = new HashMap<>();
        private Map<String, Object> defaultVariables = new HashMap<>();
        private Set<String> variablesToRemove = new HashSet<>();

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

        // Step operations

        public Builder addedSteps(Set<String> steps) {
            this.addedSteps = new HashSet<>(steps);
            return this;
        }

        public Builder addedStep(String step) {
            this.addedSteps.add(step);
            return this;
        }

        public Builder removedStep(String step, String replacement) {
            this.removedSteps.add(step);
            this.stepMappings.put(step, replacement);
            return this;
        }

        public Builder removedSteps(Set<String> steps) {
            this.removedSteps = new HashSet<>(steps);
            return this;
        }

        public Builder stepMapping(String oldStep, String newStep) {
            this.stepMappings.put(oldStep, newStep);
            return this;
        }

        public Builder defaultReplacementStep(String step) {
            this.defaultReplacementStep = step;
            return this;
        }

        // Variable operations

        public Builder renameVariable(String oldName, String newName) {
            this.variableRenames.put(oldName, newName);
            return this;
        }

        public Builder transformVariable(String oldName, String newName, Function<Object, Object> transformer) {
            this.variableRenames.put(oldName, newName);
            this.variableTransformers.put(oldName, transformer);
            return this;
        }

        public Builder transformVariable(String name, Function<Object, Object> transformer) {
            this.variableTransformers.put(name, transformer);
            return this;
        }

        public Builder addDefaultVariable(String name, Object value) {
            this.defaultVariables.put(name, value);
            return this;
        }

        public Builder removeVariable(String name) {
            this.variablesToRemove.add(name);
            return this;
        }

        public CompositeDefaultMigrationHandler build() {
            if (workflowId == null || workflowId.isBlank()) {
                throw new IllegalArgumentException("workflowId is required");
            }
            if (sourceVersionPattern == null || sourceVersionPattern.isBlank()) {
                throw new IllegalArgumentException("sourceVersionPattern is required");
            }
            if (targetVersion == null) {
                throw new IllegalArgumentException("targetVersion is required");
            }
            return new CompositeDefaultMigrationHandler(this);
        }
    }
}
