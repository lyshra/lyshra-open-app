package com.lyshra.open.app.integration.contract.version.migration;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Represents a single step in a migration plan.
 * Each step performs a specific transformation or validation.
 *
 * <p>Migration steps are executed sequentially in the order defined
 * by the migration plan. Each step can:</p>
 * <ul>
 *   <li>Transform variables (rename, convert, add, remove)</li>
 *   <li>Map steps from source to target version</li>
 *   <li>Apply custom transformations</li>
 *   <li>Validate state before/after transformation</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * for (IMigrationStep step : plan.getSteps()) {
 *     if (!step.canExecute(context)) {
 *         throw new MigrationException("Step cannot execute: " + step.getBlockedReason());
 *     }
 *
 *     StepResult result = step.execute(context).block();
 *     if (!result.isSuccess()) {
 *         // Handle failure or rollback
 *     }
 * }
 * }</pre>
 */
public interface IMigrationStep {

    /**
     * Returns the unique identifier for this step.
     *
     * @return step ID
     */
    String getStepId();

    /**
     * Returns the display name for this step.
     *
     * @return step name
     */
    String getName();

    /**
     * Returns the description of what this step does.
     *
     * @return step description
     */
    String getDescription();

    /**
     * Returns the type of this migration step.
     *
     * @return step type
     */
    StepType getType();

    /**
     * Returns the order/sequence number for this step.
     *
     * @return execution order
     */
    int getOrder();

    /**
     * Checks if this step can be executed given the current context.
     *
     * @param context migration context
     * @return true if step can execute
     */
    boolean canExecute(IMigrationContext context);

    /**
     * Returns the reason why this step cannot execute.
     *
     * @return blocked reason if step cannot execute
     */
    Optional<String> getBlockedReason();

    /**
     * Executes this migration step.
     *
     * @param context migration context
     * @return step result
     */
    Mono<StepResult> execute(IMigrationContext context);

    /**
     * Rolls back this migration step.
     *
     * @param context migration context
     * @param previousResult the result from the forward execution
     * @return rollback result
     */
    Mono<StepResult> rollback(IMigrationContext context, StepResult previousResult);

    /**
     * Returns whether this step supports rollback.
     *
     * @return true if rollback supported
     */
    boolean supportsRollback();

    /**
     * Returns whether this step is optional.
     * Optional steps can be skipped without failing the migration.
     *
     * @return true if optional
     */
    boolean isOptional();

    /**
     * Returns the estimated duration for this step.
     *
     * @return estimated duration
     */
    Duration getEstimatedDuration();

    /**
     * Returns dependencies on other steps.
     *
     * @return set of step IDs that must complete before this step
     */
    Set<String> getDependencies();

    /**
     * Returns variables affected by this step.
     *
     * @return set of affected variable names
     */
    Set<String> getAffectedVariables();

    /**
     * Returns steps affected by this step.
     *
     * @return set of affected step names
     */
    Set<String> getAffectedSteps();

    /**
     * Returns custom metadata for this step.
     *
     * @return metadata map
     */
    Map<String, Object> getMetadata();

    /**
     * Type of migration step.
     */
    enum StepType {
        /** Validates preconditions */
        VALIDATION,
        /** Creates backup/checkpoint */
        CHECKPOINT,
        /** Renames variables */
        VARIABLE_RENAME,
        /** Transforms variable values */
        VARIABLE_TRANSFORM,
        /** Adds new variables with defaults */
        VARIABLE_ADD,
        /** Removes deprecated variables */
        VARIABLE_REMOVE,
        /** Maps source step to target step */
        STEP_MAPPING,
        /** Applies custom transformation */
        CUSTOM_TRANSFORM,
        /** Runs post-migration verification */
        VERIFICATION,
        /** Commits the migration */
        COMMIT
    }

    /**
     * Result of executing a migration step.
     */
    record StepResult(
            String stepId,
            boolean isSuccess,
            StepStatus status,
            List<String> changes,
            Map<String, Object> beforeState,
            Map<String, Object> afterState,
            Optional<Throwable> error,
            Duration duration,
            List<String> warnings
    ) {
        public enum StepStatus {
            SUCCESS,
            SKIPPED,
            FAILED,
            ROLLED_BACK
        }

        public static StepResult success(String stepId, List<String> changes,
                                         Map<String, Object> beforeState,
                                         Map<String, Object> afterState,
                                         Duration duration) {
            return new StepResult(stepId, true, StepStatus.SUCCESS, changes,
                    beforeState, afterState, Optional.empty(), duration, List.of());
        }

        public static StepResult successWithWarnings(String stepId, List<String> changes,
                                                     Map<String, Object> beforeState,
                                                     Map<String, Object> afterState,
                                                     Duration duration,
                                                     List<String> warnings) {
            return new StepResult(stepId, true, StepStatus.SUCCESS, changes,
                    beforeState, afterState, Optional.empty(), duration, warnings);
        }

        public static StepResult skipped(String stepId, String reason) {
            return new StepResult(stepId, true, StepStatus.SKIPPED,
                    List.of("Skipped: " + reason), Map.of(), Map.of(),
                    Optional.empty(), Duration.ZERO, List.of());
        }

        public static StepResult failed(String stepId, Throwable error, Duration duration) {
            return new StepResult(stepId, false, StepStatus.FAILED, List.of(),
                    Map.of(), Map.of(), Optional.of(error), duration, List.of());
        }

        public static StepResult rolledBack(String stepId, Map<String, Object> restoredState) {
            return new StepResult(stepId, true, StepStatus.ROLLED_BACK,
                    List.of("Rolled back to previous state"), restoredState, restoredState,
                    Optional.empty(), Duration.ZERO, List.of());
        }
    }
}
