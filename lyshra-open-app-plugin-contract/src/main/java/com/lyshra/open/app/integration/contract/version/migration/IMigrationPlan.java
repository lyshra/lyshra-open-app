package com.lyshra.open.app.integration.contract.version.migration;

import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Represents a validated migration plan with all steps and validations.
 * A migration plan is the result of migration planning and contains
 * everything needed to execute the migration safely.
 *
 * <p>A migration plan includes:</p>
 * <ul>
 *   <li>Validation status and results</li>
 *   <li>Ordered list of migration steps</li>
 *   <li>Step and variable mappings</li>
 *   <li>Risk assessment</li>
 *   <li>Estimated duration and complexity</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * IMigrationPlan plan = planner.planMigration(request).block();
 *
 * if (plan.isValid()) {
 *     // Execute migration
 *     for (IMigrationStep step : plan.getSteps()) {
 *         step.execute(context);
 *     }
 * } else {
 *     // Handle validation errors
 *     plan.getValidationResult().getErrors().forEach(System.out::println);
 * }
 * }</pre>
 */
public interface IMigrationPlan {

    /**
     * Returns the unique identifier for this plan.
     *
     * @return plan ID
     */
    String getPlanId();

    /**
     * Returns the workflow ID this plan applies to.
     *
     * @return workflow identifier
     */
    String getWorkflowId();

    /**
     * Returns the source version being migrated from.
     *
     * @return source version
     */
    IWorkflowVersion getSourceVersion();

    /**
     * Returns the target version being migrated to.
     *
     * @return target version
     */
    IWorkflowVersion getTargetVersion();

    /**
     * Returns whether the migration plan is valid and can be executed.
     *
     * @return true if plan is valid
     */
    boolean isValid();

    /**
     * Returns whether the migration can proceed automatically without approval.
     *
     * @return true if migration is safe for automatic execution
     */
    boolean isSafe();

    /**
     * Returns whether this is a dry-run plan (no actual changes).
     *
     * @return true if dry-run
     */
    boolean isDryRun();

    /**
     * Returns the validation result with details.
     *
     * @return validation result
     */
    IMigrationPlanner.MigrationValidation getValidationResult();

    /**
     * Returns the ordered list of migration steps to execute.
     *
     * @return list of migration steps
     */
    List<IMigrationStep> getSteps();

    /**
     * Returns the migration paths that form this plan.
     *
     * @return list of migration paths
     */
    List<IMigrationPath> getMigrationPaths();

    /**
     * Returns the number of version hops in this migration.
     *
     * @return hop count
     */
    int getHopCount();

    /**
     * Returns the step mappings from source to target version.
     *
     * @return map of old step name to new step name
     */
    Map<String, String> getStepMappings();

    /**
     * Returns the variable mappings from source to target version.
     *
     * @return map of old variable name to new variable name
     */
    Map<String, String> getVariableMappings();

    /**
     * Returns the recommended target step name after migration.
     *
     * @return target step name
     */
    String getTargetStepName();

    /**
     * Returns the original step name before migration.
     *
     * @return source step name
     */
    Optional<String> getSourceStepName();

    /**
     * Returns steps that were added in the target version.
     *
     * @return set of added step names
     */
    Set<String> getAddedSteps();

    /**
     * Returns steps that were removed from the source version.
     *
     * @return set of removed step names
     */
    Set<String> getRemovedSteps();

    /**
     * Returns variables that will be added during migration.
     *
     * @return set of added variable names
     */
    Set<String> getAddedVariables();

    /**
     * Returns variables that will be removed during migration.
     *
     * @return set of removed variable names
     */
    Set<String> getRemovedVariables();

    /**
     * Returns variables that will be renamed during migration.
     *
     * @return map of old name to new name
     */
    Map<String, String> getRenamedVariables();

    /**
     * Returns the risk assessment for this migration.
     *
     * @return risk assessment
     */
    IMigrationPlanner.RiskAssessment getRiskAssessment();

    /**
     * Returns the migration complexity classification.
     *
     * @return complexity level
     */
    IMigrationPlanner.MigrationComplexity getComplexity();

    /**
     * Returns the estimated duration for this migration.
     *
     * @return estimated duration
     */
    Duration getEstimatedDuration();

    /**
     * Returns when this plan was created.
     *
     * @return creation timestamp
     */
    Instant getCreatedAt();

    /**
     * Returns when this plan expires (if applicable).
     *
     * @return expiration timestamp
     */
    Optional<Instant> getExpiresAt();

    /**
     * Returns whether this plan has expired.
     *
     * @return true if expired
     */
    default boolean isExpired() {
        return getExpiresAt().map(exp -> Instant.now().isAfter(exp)).orElse(false);
    }

    /**
     * Returns whether approval is required before execution.
     *
     * @return true if approval required
     */
    boolean requiresApproval();

    /**
     * Returns whether backup should be created before execution.
     *
     * @return true if backup recommended
     */
    boolean requiresBackup();

    /**
     * Returns whether rollback is supported for this migration.
     *
     * @return true if rollback supported
     */
    boolean supportsRollback();

    /**
     * Returns the handlers that will be used for this migration.
     *
     * @return list of handler identifiers
     */
    List<String> getHandlerIds();

    /**
     * Returns the transformers that will be applied.
     *
     * @return list of transformer names
     */
    List<String> getTransformerNames();

    /**
     * Returns preconditions that must be met before migration.
     *
     * @return list of preconditions
     */
    List<Precondition> getPreconditions();

    /**
     * Returns postconditions to verify after migration.
     *
     * @return list of postconditions
     */
    List<Postcondition> getPostconditions();

    /**
     * Returns custom metadata for this plan.
     *
     * @return metadata map
     */
    Map<String, Object> getMetadata();

    /**
     * Returns a summary of this migration plan.
     *
     * @return plan summary
     */
    PlanSummary getSummary();

    /**
     * A precondition that must be met before migration.
     */
    record Precondition(
            String name,
            String description,
            PreconditionType type,
            boolean isMet,
            Optional<String> failureReason
    ) {
        public enum PreconditionType {
            STEP_EXISTS,
            VARIABLE_EXISTS,
            VARIABLE_TYPE,
            CUSTOM_VALIDATION,
            HANDLER_AVAILABLE,
            VERSION_ACTIVE
        }

        public static Precondition stepExists(String stepName, boolean isMet) {
            return new Precondition(
                    "step-exists-" + stepName,
                    "Step '" + stepName + "' must exist in target version",
                    PreconditionType.STEP_EXISTS,
                    isMet,
                    isMet ? Optional.empty() : Optional.of("Step not found in target version"));
        }

        public static Precondition variableExists(String variableName, boolean isMet) {
            return new Precondition(
                    "variable-exists-" + variableName,
                    "Variable '" + variableName + "' must be available",
                    PreconditionType.VARIABLE_EXISTS,
                    isMet,
                    isMet ? Optional.empty() : Optional.of("Variable not found"));
        }

        public static Precondition handlerAvailable(boolean isMet) {
            return new Precondition(
                    "handler-available",
                    "Migration handler must be registered",
                    PreconditionType.HANDLER_AVAILABLE,
                    isMet,
                    isMet ? Optional.empty() : Optional.of("No handler found for this migration"));
        }
    }

    /**
     * A postcondition to verify after migration.
     */
    record Postcondition(
            String name,
            String description,
            PostconditionType type,
            String validationExpression
    ) {
        public enum PostconditionType {
            STEP_REACHED,
            VARIABLE_SET,
            DATA_INTEGRITY,
            CUSTOM_VALIDATION
        }

        public static Postcondition stepReached(String stepName) {
            return new Postcondition(
                    "step-reached-" + stepName,
                    "Execution should be at step '" + stepName + "' after migration",
                    PostconditionType.STEP_REACHED,
                    "currentStep == '" + stepName + "'");
        }

        public static Postcondition variableSet(String variableName) {
            return new Postcondition(
                    "variable-set-" + variableName,
                    "Variable '" + variableName + "' should be set after migration",
                    PostconditionType.VARIABLE_SET,
                    "hasVariable('" + variableName + "')");
        }
    }

    /**
     * Summary of the migration plan.
     */
    record PlanSummary(
            String workflowId,
            String sourceVersion,
            String targetVersion,
            int totalSteps,
            int hopCount,
            boolean isValid,
            boolean isSafe,
            String complexity,
            String riskLevel,
            int addedStepsCount,
            int removedStepsCount,
            int variableChangesCount,
            boolean requiresApproval,
            String estimatedDuration
    ) {}
}
