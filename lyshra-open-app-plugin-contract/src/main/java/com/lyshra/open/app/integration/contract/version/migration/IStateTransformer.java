package com.lyshra.open.app.integration.contract.version.migration;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Interface for custom state transformation during workflow migration.
 * Enables workflows to define specific logic for transforming execution
 * state from one version to another.
 *
 * <p>State transformers handle:</p>
 * <ul>
 *   <li>Variable transformations (renaming, type conversion, restructuring)</li>
 *   <li>Context data transformations (field mapping, data migration)</li>
 *   <li>Step state transformations (progress mapping)</li>
 *   <li>Custom business logic during migration</li>
 * </ul>
 *
 * <p>Design Pattern: Strategy pattern for pluggable transformation logic.</p>
 *
 * <p>Implementation Guidelines:</p>
 * <ul>
 *   <li>Transformers should be idempotent</li>
 *   <li>Transformers should not have side effects outside the context</li>
 *   <li>Failed transformations should leave state unchanged</li>
 *   <li>Always validate input before transformation</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * public class OrderDataTransformer implements IStateTransformer {
 *     @Override
 *     public Mono<TransformationResult> transform(IMigrationContext context) {
 *         ILyshraOpenAppContext execContext = context.getExecutionContext();
 *
 *         // Transform order data structure
 *         OrderV1 oldOrder = (OrderV1) execContext.getData();
 *         OrderV2 newOrder = OrderV2.fromV1(oldOrder);
 *         execContext.setData(newOrder);
 *
 *         // Rename variables
 *         if (execContext.hasVariable("orderTotal")) {
 *             Object total = execContext.getVariable("orderTotal");
 *             execContext.removeVariable("orderTotal");
 *             execContext.addVariable("order_total_amount", total);
 *         }
 *
 *         return Mono.just(TransformationResult.success());
 *     }
 * }
 * }</pre>
 */
public interface IStateTransformer {

    /**
     * Returns the unique name of this transformer.
     *
     * @return transformer name
     */
    String getName();

    /**
     * Returns the transformer description.
     *
     * @return description
     */
    String getDescription();

    /**
     * Returns the source version pattern this transformer applies to.
     *
     * @return source version pattern
     */
    String getSourceVersionPattern();

    /**
     * Returns the target version this transformer produces.
     *
     * @return target version
     */
    IWorkflowVersion getTargetVersion();

    /**
     * Returns the priority of this transformer (higher = executes first).
     *
     * @return priority value
     */
    int getPriority();

    /**
     * Checks if this transformer applies to the given migration context.
     *
     * @param context migration context
     * @return true if transformer applies
     */
    boolean appliesTo(IMigrationContext context);

    /**
     * Validates that transformation can be applied to the given context.
     *
     * @param context migration context
     * @return validation result
     */
    Mono<TransformationValidationResult> validate(IMigrationContext context);

    /**
     * Transforms the execution state from source to target version.
     *
     * @param context migration context containing execution state
     * @return transformation result
     */
    Mono<TransformationResult> transform(IMigrationContext context);

    /**
     * Reverses a transformation (for rollback support).
     *
     * @param context migration context with transformed state
     * @param originalSnapshot snapshot of state before transformation
     * @return reversal result
     */
    Mono<TransformationResult> reverse(IMigrationContext context, Map<String, Object> originalSnapshot);

    /**
     * Returns the variables this transformer reads.
     *
     * @return set of variable names
     */
    Set<String> getInputVariables();

    /**
     * Returns the variables this transformer produces/modifies.
     *
     * @return set of variable names
     */
    Set<String> getOutputVariables();

    /**
     * Returns whether this transformer modifies the context data object.
     *
     * @return true if data is modified
     */
    boolean modifiesData();

    /**
     * Returns whether this transformer supports reversal (rollback).
     *
     * @return true if reversible
     */
    boolean isReversible();

    /**
     * Returns the estimated risk level of this transformation.
     *
     * @return risk level 0.0-1.0
     */
    double getRiskLevel();

    /**
     * Result of transformation validation.
     */
    record TransformationValidationResult(
            boolean isValid,
            List<String> errors,
            List<String> warnings,
            Map<String, Object> diagnostics
    ) {
        public static TransformationValidationResult valid() {
            return new TransformationValidationResult(true, List.of(), List.of(), Map.of());
        }

        public static TransformationValidationResult validWithWarnings(List<String> warnings) {
            return new TransformationValidationResult(true, List.of(), warnings, Map.of());
        }

        public static TransformationValidationResult invalid(List<String> errors) {
            return new TransformationValidationResult(false, errors, List.of(), Map.of());
        }

        public static TransformationValidationResult invalidWithDiagnostics(List<String> errors, Map<String, Object> diagnostics) {
            return new TransformationValidationResult(false, errors, List.of(), diagnostics);
        }
    }

    /**
     * Result of a state transformation.
     */
    record TransformationResult(
            boolean isSuccess,
            ILyshraOpenAppContext transformedContext,
            Map<String, Object> preTransformSnapshot,
            Map<String, Object> postTransformSnapshot,
            List<TransformationAction> actions,
            Optional<String> errorMessage,
            Optional<Throwable> error
    ) {
        /**
         * Creates a successful transformation result.
         */
        public static TransformationResult success(
                ILyshraOpenAppContext context,
                Map<String, Object> preSnapshot,
                Map<String, Object> postSnapshot,
                List<TransformationAction> actions) {
            return new TransformationResult(
                    true, context, preSnapshot, postSnapshot, actions,
                    Optional.empty(), Optional.empty());
        }

        /**
         * Creates a successful transformation result with minimal info.
         */
        public static TransformationResult success() {
            return new TransformationResult(
                    true, null, Map.of(), Map.of(), List.of(),
                    Optional.empty(), Optional.empty());
        }

        /**
         * Creates a failed transformation result.
         */
        public static TransformationResult failure(String errorMessage, Throwable error) {
            return new TransformationResult(
                    false, null, Map.of(), Map.of(), List.of(),
                    Optional.of(errorMessage), Optional.ofNullable(error));
        }

        /**
         * Creates a failed transformation result with message only.
         */
        public static TransformationResult failure(String errorMessage) {
            return new TransformationResult(
                    false, null, Map.of(), Map.of(), List.of(),
                    Optional.of(errorMessage), Optional.empty());
        }
    }

    /**
     * Describes an action taken during transformation.
     */
    record TransformationAction(
            ActionType type,
            String target,
            String description,
            Optional<Object> oldValue,
            Optional<Object> newValue
    ) {
        public enum ActionType {
            VARIABLE_RENAMED,
            VARIABLE_ADDED,
            VARIABLE_REMOVED,
            VARIABLE_TRANSFORMED,
            DATA_FIELD_RENAMED,
            DATA_FIELD_ADDED,
            DATA_FIELD_REMOVED,
            DATA_FIELD_TRANSFORMED,
            DATA_RESTRUCTURED,
            STEP_MAPPED,
            CUSTOM
        }

        public static TransformationAction variableRenamed(String oldName, String newName, Object value) {
            return new TransformationAction(
                    ActionType.VARIABLE_RENAMED,
                    oldName + " -> " + newName,
                    "Renamed variable from " + oldName + " to " + newName,
                    Optional.ofNullable(value),
                    Optional.ofNullable(value));
        }

        public static TransformationAction variableAdded(String name, Object value) {
            return new TransformationAction(
                    ActionType.VARIABLE_ADDED,
                    name,
                    "Added variable " + name,
                    Optional.empty(),
                    Optional.ofNullable(value));
        }

        public static TransformationAction variableRemoved(String name, Object oldValue) {
            return new TransformationAction(
                    ActionType.VARIABLE_REMOVED,
                    name,
                    "Removed variable " + name,
                    Optional.ofNullable(oldValue),
                    Optional.empty());
        }

        public static TransformationAction variableTransformed(String name, Object oldValue, Object newValue) {
            return new TransformationAction(
                    ActionType.VARIABLE_TRANSFORMED,
                    name,
                    "Transformed variable " + name,
                    Optional.ofNullable(oldValue),
                    Optional.ofNullable(newValue));
        }

        public static TransformationAction dataRestructured(String description) {
            return new TransformationAction(
                    ActionType.DATA_RESTRUCTURED,
                    "data",
                    description,
                    Optional.empty(),
                    Optional.empty());
        }

        public static TransformationAction stepMapped(String oldStep, String newStep) {
            return new TransformationAction(
                    ActionType.STEP_MAPPED,
                    oldStep + " -> " + newStep,
                    "Mapped step from " + oldStep + " to " + newStep,
                    Optional.of(oldStep),
                    Optional.of(newStep));
        }

        public static TransformationAction custom(String target, String description) {
            return new TransformationAction(
                    ActionType.CUSTOM,
                    target,
                    description,
                    Optional.empty(),
                    Optional.empty());
        }
    }
}
