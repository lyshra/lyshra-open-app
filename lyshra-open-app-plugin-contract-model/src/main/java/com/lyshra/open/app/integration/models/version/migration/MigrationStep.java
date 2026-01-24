package com.lyshra.open.app.integration.models.version.migration;

import com.lyshra.open.app.integration.contract.version.migration.IMigrationContext;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationStep;
import lombok.Builder;
import lombok.Getter;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Implementation of IMigrationStep representing a single migration step.
 */
@Getter
@Builder
public class MigrationStep implements IMigrationStep {

    @Builder.Default
    private final String stepId = UUID.randomUUID().toString();

    private final String name;
    private final String description;
    private final StepType type;

    @Builder.Default
    private final int order = 0;

    @Builder.Default
    private final boolean optional = false;

    @Builder.Default
    private final Duration estimatedDuration = Duration.ofMillis(100);

    @Builder.Default
    private final Set<String> dependencies = new HashSet<>();

    @Builder.Default
    private final Set<String> affectedVariables = new HashSet<>();

    @Builder.Default
    private final Set<String> affectedSteps = new HashSet<>();

    @Builder.Default
    private final Map<String, Object> metadata = new HashMap<>();

    @Builder.Default
    private final boolean supportsRollback = true;

    private final String blockedReason;

    // Execution logic
    private final Function<IMigrationContext, Mono<StepResult>> executor;
    private final Function<RollbackInput, Mono<StepResult>> rollbackExecutor;

    @Override
    public boolean canExecute(IMigrationContext context) {
        return blockedReason == null;
    }

    @Override
    public Optional<String> getBlockedReason() {
        return Optional.ofNullable(blockedReason);
    }

    @Override
    public Mono<StepResult> execute(IMigrationContext context) {
        if (!canExecute(context)) {
            return Mono.just(StepResult.failed(stepId,
                    new IllegalStateException("Step blocked: " + blockedReason),
                    Duration.ZERO));
        }

        if (executor != null) {
            return executor.apply(context);
        }

        // Default no-op execution
        return Mono.just(StepResult.success(stepId, List.of("No-op step executed"),
                Map.of(), Map.of(), Duration.ZERO));
    }

    @Override
    public Mono<StepResult> rollback(IMigrationContext context, StepResult previousResult) {
        if (!supportsRollback) {
            return Mono.just(StepResult.failed(stepId,
                    new UnsupportedOperationException("Rollback not supported"),
                    Duration.ZERO));
        }

        if (rollbackExecutor != null) {
            return rollbackExecutor.apply(new RollbackInput(context, previousResult));
        }

        // Default rollback: restore before state
        return Mono.just(StepResult.rolledBack(stepId, previousResult.beforeState()));
    }

    @Override
    public boolean supportsRollback() {
        return supportsRollback;
    }

    @Override
    public boolean isOptional() {
        return optional;
    }

    /**
     * Input for rollback executor.
     */
    public record RollbackInput(IMigrationContext context, StepResult previousResult) {}

    /**
     * Creates a validation step.
     */
    public static MigrationStep validation(String name, String description,
                                           Function<IMigrationContext, Mono<StepResult>> validator) {
        return MigrationStep.builder()
                .name(name)
                .description(description)
                .type(StepType.VALIDATION)
                .order(0)
                .supportsRollback(false)
                .executor(validator)
                .build();
    }

    /**
     * Creates a variable rename step.
     */
    public static MigrationStep variableRename(String oldName, String newName, int order) {
        return MigrationStep.builder()
                .name("rename-" + oldName)
                .description("Rename variable '" + oldName + "' to '" + newName + "'")
                .type(StepType.VARIABLE_RENAME)
                .order(order)
                .affectedVariables(Set.of(oldName, newName))
                .executor(ctx -> {
                    Instant start = Instant.now();
                    var execContext = ctx.getExecutionContext();
                    Map<String, Object> beforeState = Map.of(oldName, execContext.getVariable(oldName));

                    if (execContext.hasVariable(oldName)) {
                        Object value = execContext.getVariable(oldName);
                        execContext.removeVariable(oldName);
                        execContext.addVariable(newName, value);
                    }

                    Map<String, Object> afterState = Map.of(newName, execContext.getVariable(newName));
                    Duration duration = Duration.between(start, Instant.now());

                    return Mono.just(StepResult.success(
                            "rename-" + oldName,
                            List.of("Renamed '" + oldName + "' to '" + newName + "'"),
                            beforeState, afterState, duration));
                })
                .rollbackExecutor(input -> {
                    var ctx = input.context();
                    var execContext = ctx.getExecutionContext();
                    Object value = input.previousResult().beforeState().get(oldName);

                    if (execContext.hasVariable(newName)) {
                        execContext.removeVariable(newName);
                    }
                    if (value != null) {
                        execContext.addVariable(oldName, value);
                    }

                    return Mono.just(StepResult.rolledBack("rename-" + oldName,
                            Map.of(oldName, value)));
                })
                .build();
    }

    /**
     * Creates a variable add step with default value.
     */
    public static MigrationStep variableAdd(String name, Object defaultValue, int order) {
        return MigrationStep.builder()
                .name("add-" + name)
                .description("Add variable '" + name + "' with default value")
                .type(StepType.VARIABLE_ADD)
                .order(order)
                .affectedVariables(Set.of(name))
                .executor(ctx -> {
                    Instant start = Instant.now();
                    var execContext = ctx.getExecutionContext();

                    if (!execContext.hasVariable(name)) {
                        execContext.addVariable(name, defaultValue);
                    }

                    Duration duration = Duration.between(start, Instant.now());
                    return Mono.just(StepResult.success(
                            "add-" + name,
                            List.of("Added variable '" + name + "'"),
                            Map.of(), Map.of(name, defaultValue), duration));
                })
                .rollbackExecutor(input -> {
                    var ctx = input.context();
                    var execContext = ctx.getExecutionContext();

                    if (execContext.hasVariable(name)) {
                        execContext.removeVariable(name);
                    }

                    return Mono.just(StepResult.rolledBack("add-" + name, Map.of()));
                })
                .build();
    }

    /**
     * Creates a variable remove step.
     */
    public static MigrationStep variableRemove(String name, int order) {
        return MigrationStep.builder()
                .name("remove-" + name)
                .description("Remove variable '" + name + "'")
                .type(StepType.VARIABLE_REMOVE)
                .order(order)
                .affectedVariables(Set.of(name))
                .executor(ctx -> {
                    Instant start = Instant.now();
                    var execContext = ctx.getExecutionContext();
                    Map<String, Object> beforeState = Map.of();

                    if (execContext.hasVariable(name)) {
                        beforeState = Map.of(name, execContext.getVariable(name));
                        execContext.removeVariable(name);
                    }

                    Duration duration = Duration.between(start, Instant.now());
                    return Mono.just(StepResult.success(
                            "remove-" + name,
                            List.of("Removed variable '" + name + "'"),
                            beforeState, Map.of(), duration));
                })
                .rollbackExecutor(input -> {
                    var ctx = input.context();
                    var execContext = ctx.getExecutionContext();
                    Object value = input.previousResult().beforeState().get(name);

                    if (value != null) {
                        execContext.addVariable(name, value);
                    }

                    return Mono.just(StepResult.rolledBack("remove-" + name,
                            Map.of(name, value)));
                })
                .build();
    }

    /**
     * Creates a step mapping step.
     */
    public static MigrationStep stepMapping(String sourceStep, String targetStep, int order) {
        return MigrationStep.builder()
                .name("map-step-" + sourceStep)
                .description("Map step '" + sourceStep + "' to '" + targetStep + "'")
                .type(StepType.STEP_MAPPING)
                .order(order)
                .affectedSteps(Set.of(sourceStep, targetStep))
                .executor(ctx -> {
                    // Step mapping is typically handled by the migration result
                    // This step just records the mapping
                    return Mono.just(StepResult.success(
                            "map-step-" + sourceStep,
                            List.of("Mapped step '" + sourceStep + "' to '" + targetStep + "'"),
                            Map.of("sourceStep", sourceStep),
                            Map.of("targetStep", targetStep),
                            Duration.ZERO));
                })
                .build();
    }

    /**
     * Creates a checkpoint step.
     */
    public static MigrationStep checkpoint(int order) {
        return MigrationStep.builder()
                .name("checkpoint")
                .description("Create migration checkpoint")
                .type(StepType.CHECKPOINT)
                .order(order)
                .supportsRollback(false)
                .executor(ctx -> {
                    var execContext = ctx.getExecutionContext();
                    Map<String, Object> state = new HashMap<>();
                    state.put("data", execContext.getData());
                    state.put("variables", new HashMap<>(execContext.getVariables()));
                    state.put("timestamp", Instant.now());

                    return Mono.just(StepResult.success(
                            "checkpoint",
                            List.of("Created checkpoint"),
                            Map.of(), state, Duration.ZERO));
                })
                .build();
    }

    /**
     * Creates a commit step.
     */
    public static MigrationStep commit(int order) {
        return MigrationStep.builder()
                .name("commit")
                .description("Commit migration")
                .type(StepType.COMMIT)
                .order(order)
                .supportsRollback(false)
                .executor(ctx -> Mono.just(StepResult.success(
                        "commit",
                        List.of("Migration committed"),
                        Map.of(), Map.of(), Duration.ZERO)))
                .build();
    }
}
