package com.lyshra.open.app.integration.contract.version.migration;

import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Migration planner that calculates safe migration paths between workflow versions.
 * Validates migrations before execution by checking step/variable compatibility.
 *
 * <p>The planner performs comprehensive analysis including:</p>
 * <ul>
 *   <li>Step existence validation (source and target)</li>
 *   <li>Variable compatibility checking</li>
 *   <li>Safe point identification</li>
 *   <li>Migration path optimization</li>
 *   <li>Risk assessment</li>
 * </ul>
 *
 * <p>Design Pattern: Strategy pattern for pluggable planning algorithms.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * IMigrationPlanner planner = new MigrationPlannerImpl(registry, workflowLoader);
 *
 * // Plan a migration
 * MigrationPlanRequest request = MigrationPlanRequest.builder()
 *     .workflowId("order-processing")
 *     .sourceVersion(WorkflowVersion.parse("1.0.0"))
 *     .targetVersion(WorkflowVersion.parse("2.0.0"))
 *     .currentStepName("processPayment")
 *     .currentVariables(Map.of("orderId", "12345"))
 *     .build();
 *
 * Mono<IMigrationPlan> plan = planner.planMigration(request);
 * plan.subscribe(p -> {
 *     if (p.isValid()) {
 *         System.out.println("Migration is safe: " + p.getSteps());
 *     } else {
 *         System.out.println("Migration blocked: " + p.getValidationResult().getErrors());
 *     }
 * });
 * }</pre>
 */
public interface IMigrationPlanner {

    /**
     * Plans a migration based on the given request.
     * Performs full validation and generates migration steps.
     *
     * @param request migration plan request
     * @return migration plan
     */
    Mono<IMigrationPlan> planMigration(MigrationPlanRequest request);

    /**
     * Validates whether a migration is possible without generating a full plan.
     * Faster than full planning when only validation is needed.
     *
     * @param request migration plan request
     * @return validation result
     */
    Mono<MigrationValidation> validateMigration(MigrationPlanRequest request);

    /**
     * Finds all possible migration paths from source to target version.
     *
     * @param workflowId workflow identifier
     * @param sourceVersion source version
     * @param targetVersion target version
     * @return list of possible migration paths
     */
    List<MigrationPathInfo> findMigrationPaths(
            String workflowId,
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion);

    /**
     * Finds the optimal migration path considering risk and complexity.
     *
     * @param workflowId workflow identifier
     * @param sourceVersion source version
     * @param targetVersion target version
     * @return optimal migration path if available
     */
    Optional<MigrationPathInfo> findOptimalPath(
            String workflowId,
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion);

    /**
     * Analyzes the differences between two workflow versions.
     *
     * @param sourceWorkflow source workflow definition
     * @param targetWorkflow target workflow definition
     * @return version diff analysis
     */
    VersionDiff analyzeVersionDiff(
            IVersionedWorkflow sourceWorkflow,
            IVersionedWorkflow targetWorkflow);

    /**
     * Checks if a specific step is a safe migration point.
     *
     * @param workflowId workflow identifier
     * @param sourceVersion source version
     * @param targetVersion target version
     * @param stepName step name to check
     * @return true if step is a safe migration point
     */
    boolean isSafeMigrationPoint(
            String workflowId,
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion,
            String stepName);

    /**
     * Gets all safe migration points for a version transition.
     *
     * @param workflowId workflow identifier
     * @param sourceVersion source version
     * @param targetVersion target version
     * @return set of safe step names
     */
    Set<String> getSafeMigrationPoints(
            String workflowId,
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion);

    /**
     * Estimates the risk level of a migration.
     *
     * @param request migration request
     * @return risk assessment
     */
    Mono<RiskAssessment> assessRisk(MigrationPlanRequest request);

    /**
     * Request object for migration planning.
     */
    record MigrationPlanRequest(
            String workflowId,
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion,
            String currentStepName,
            Map<String, Object> currentVariables,
            Object currentData,
            boolean dryRun,
            boolean allowUnsafeMigration,
            Set<String> requiredVariables,
            Map<String, Object> metadata
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String workflowId;
            private IWorkflowVersion sourceVersion;
            private IWorkflowVersion targetVersion;
            private String currentStepName;
            private Map<String, Object> currentVariables = Map.of();
            private Object currentData;
            private boolean dryRun = false;
            private boolean allowUnsafeMigration = false;
            private Set<String> requiredVariables = Set.of();
            private Map<String, Object> metadata = Map.of();

            public Builder workflowId(String workflowId) {
                this.workflowId = workflowId;
                return this;
            }

            public Builder sourceVersion(IWorkflowVersion sourceVersion) {
                this.sourceVersion = sourceVersion;
                return this;
            }

            public Builder targetVersion(IWorkflowVersion targetVersion) {
                this.targetVersion = targetVersion;
                return this;
            }

            public Builder currentStepName(String currentStepName) {
                this.currentStepName = currentStepName;
                return this;
            }

            public Builder currentVariables(Map<String, Object> currentVariables) {
                this.currentVariables = currentVariables;
                return this;
            }

            public Builder currentData(Object currentData) {
                this.currentData = currentData;
                return this;
            }

            public Builder dryRun(boolean dryRun) {
                this.dryRun = dryRun;
                return this;
            }

            public Builder allowUnsafeMigration(boolean allowUnsafeMigration) {
                this.allowUnsafeMigration = allowUnsafeMigration;
                return this;
            }

            public Builder requiredVariables(Set<String> requiredVariables) {
                this.requiredVariables = requiredVariables;
                return this;
            }

            public Builder metadata(Map<String, Object> metadata) {
                this.metadata = metadata;
                return this;
            }

            public MigrationPlanRequest build() {
                if (workflowId == null || workflowId.isBlank()) {
                    throw new IllegalArgumentException("workflowId is required");
                }
                if (sourceVersion == null) {
                    throw new IllegalArgumentException("sourceVersion is required");
                }
                if (targetVersion == null) {
                    throw new IllegalArgumentException("targetVersion is required");
                }
                return new MigrationPlanRequest(
                        workflowId, sourceVersion, targetVersion, currentStepName,
                        currentVariables, currentData, dryRun, allowUnsafeMigration,
                        requiredVariables, metadata);
            }
        }
    }

    /**
     * Validation result for migration.
     */
    record MigrationValidation(
            boolean isValid,
            boolean isSafe,
            List<ValidationError> errors,
            List<ValidationWarning> warnings,
            Set<String> missingSteps,
            Set<String> missingVariables,
            Set<String> incompatibleVariables,
            MigrationComplexity complexity
    ) {
        public static MigrationValidation valid() {
            return new MigrationValidation(true, true, List.of(), List.of(),
                    Set.of(), Set.of(), Set.of(), MigrationComplexity.SIMPLE);
        }

        public static MigrationValidation validWithWarnings(List<ValidationWarning> warnings) {
            return new MigrationValidation(true, true, List.of(), warnings,
                    Set.of(), Set.of(), Set.of(), MigrationComplexity.SIMPLE);
        }

        public static MigrationValidation invalid(List<ValidationError> errors) {
            return new MigrationValidation(false, false, errors, List.of(),
                    Set.of(), Set.of(), Set.of(), MigrationComplexity.BLOCKED);
        }

        public static MigrationValidation unsafe(List<ValidationWarning> warnings) {
            return new MigrationValidation(true, false, List.of(), warnings,
                    Set.of(), Set.of(), Set.of(), MigrationComplexity.COMPLEX);
        }
    }

    /**
     * Validation error details.
     */
    record ValidationError(
            ErrorType type,
            String component,
            String message,
            Optional<String> suggestion
    ) {
        public enum ErrorType {
            MISSING_STEP,
            MISSING_VARIABLE,
            INCOMPATIBLE_TYPE,
            BLOCKED_PATH,
            NO_HANDLER,
            CONFIGURATION_ERROR,
            VERSION_MISMATCH
        }

        public static ValidationError missingStep(String stepName) {
            return new ValidationError(ErrorType.MISSING_STEP, stepName,
                    "Step '" + stepName + "' does not exist in target version",
                    Optional.of("Define step mapping or use a different target step"));
        }

        public static ValidationError missingVariable(String variableName) {
            return new ValidationError(ErrorType.MISSING_VARIABLE, variableName,
                    "Required variable '" + variableName + "' is missing",
                    Optional.of("Add default value or variable mapping"));
        }

        public static ValidationError incompatibleType(String variableName, String expected, String actual) {
            return new ValidationError(ErrorType.INCOMPATIBLE_TYPE, variableName,
                    "Variable '" + variableName + "' type mismatch: expected " + expected + ", got " + actual,
                    Optional.of("Add type transformer for this variable"));
        }

        public static ValidationError blockedPath(String reason) {
            return new ValidationError(ErrorType.BLOCKED_PATH, "migration-path",
                    "Migration path is blocked: " + reason,
                    Optional.empty());
        }

        public static ValidationError noHandler() {
            return new ValidationError(ErrorType.NO_HANDLER, "handler",
                    "No migration handler found for this version transition",
                    Optional.of("Register a custom handler or use default migration handlers"));
        }
    }

    /**
     * Validation warning details.
     */
    record ValidationWarning(
            WarningType type,
            String component,
            String message
    ) {
        public enum WarningType {
            DEPRECATED_VERSION,
            DATA_LOSS_RISK,
            PERFORMANCE_IMPACT,
            UNTESTED_PATH,
            VARIABLE_RENAME,
            STEP_CHANGE
        }

        public static ValidationWarning deprecatedVersion(String version) {
            return new ValidationWarning(WarningType.DEPRECATED_VERSION, version,
                    "Version " + version + " is deprecated");
        }

        public static ValidationWarning dataLossRisk(String component, double riskLevel) {
            return new ValidationWarning(WarningType.DATA_LOSS_RISK, component,
                    String.format("Potential data loss risk (%.1f%%) for: %s", riskLevel * 100, component));
        }

        public static ValidationWarning variableRename(String oldName, String newName) {
            return new ValidationWarning(WarningType.VARIABLE_RENAME, oldName,
                    "Variable '" + oldName + "' will be renamed to '" + newName + "'");
        }

        public static ValidationWarning stepChange(String stepName, String changeType) {
            return new ValidationWarning(WarningType.STEP_CHANGE, stepName,
                    "Step '" + stepName + "' " + changeType);
        }
    }

    /**
     * Migration complexity classification.
     */
    enum MigrationComplexity {
        /** Simple migration with minimal changes */
        SIMPLE,
        /** Moderate migration with some transformations */
        MODERATE,
        /** Complex migration requiring careful handling */
        COMPLEX,
        /** Migration is blocked or not possible */
        BLOCKED
    }

    /**
     * Information about a possible migration path.
     */
    record MigrationPathInfo(
            List<IMigrationPath> paths,
            int hopCount,
            double totalRisk,
            MigrationComplexity complexity,
            Set<String> affectedSteps,
            Set<String> affectedVariables,
            boolean requiresApproval
    ) {
        public boolean isDirect() {
            return hopCount == 1;
        }

        public boolean isMultiHop() {
            return hopCount > 1;
        }
    }

    /**
     * Analysis of differences between workflow versions.
     */
    record VersionDiff(
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion,
            Set<String> addedSteps,
            Set<String> removedSteps,
            Set<String> modifiedSteps,
            Set<String> unchangedSteps,
            Map<String, String> stepMappings,
            Set<String> addedVariables,
            Set<String> removedVariables,
            Map<String, String> variableMappings,
            Map<String, TypeChange> variableTypeChanges,
            boolean isBackwardCompatible,
            boolean requiresMigrationHandler,
            List<String> breakingChanges
    ) {
        public boolean hasStepChanges() {
            return !addedSteps.isEmpty() || !removedSteps.isEmpty() || !modifiedSteps.isEmpty();
        }

        public boolean hasVariableChanges() {
            return !addedVariables.isEmpty() || !removedVariables.isEmpty() ||
                   !variableMappings.isEmpty() || !variableTypeChanges.isEmpty();
        }

        public boolean hasBreakingChanges() {
            return !breakingChanges.isEmpty();
        }
    }

    /**
     * Represents a variable type change between versions.
     */
    record TypeChange(
            String variableName,
            String sourceType,
            String targetType,
            boolean isCompatible,
            Optional<String> transformerName
    ) {}

    /**
     * Risk assessment for a migration.
     */
    record RiskAssessment(
            RiskLevel overallRisk,
            double riskScore,
            List<RiskFactor> riskFactors,
            List<String> mitigations,
            boolean recommendsApproval,
            boolean recommendsBackup
    ) {
        public enum RiskLevel {
            LOW,
            MEDIUM,
            HIGH,
            CRITICAL
        }

        public static RiskAssessment low() {
            return new RiskAssessment(RiskLevel.LOW, 0.1, List.of(), List.of(), false, false);
        }

        public static RiskAssessment medium(List<RiskFactor> factors) {
            return new RiskAssessment(RiskLevel.MEDIUM, 0.4, factors,
                    List.of("Review migration plan before execution"), false, true);
        }

        public static RiskAssessment high(List<RiskFactor> factors) {
            return new RiskAssessment(RiskLevel.HIGH, 0.7, factors,
                    List.of("Require approval before migration", "Create full backup"),
                    true, true);
        }

        public static RiskAssessment critical(List<RiskFactor> factors) {
            return new RiskAssessment(RiskLevel.CRITICAL, 0.95, factors,
                    List.of("Migration not recommended", "Consider alternative approach"),
                    true, true);
        }
    }

    /**
     * Individual risk factor.
     */
    record RiskFactor(
            String name,
            String description,
            double impact,
            String category
    ) {
        public static RiskFactor dataLoss(String component, double impact) {
            return new RiskFactor("data-loss", "Potential data loss in: " + component,
                    impact, "data-integrity");
        }

        public static RiskFactor stateCorruption(String component, double impact) {
            return new RiskFactor("state-corruption", "Risk of state corruption in: " + component,
                    impact, "data-integrity");
        }

        public static RiskFactor multiHopMigration(int hops) {
            return new RiskFactor("multi-hop", "Migration requires " + hops + " version hops",
                    0.1 * hops, "complexity");
        }

        public static RiskFactor unsafePoint(String stepName) {
            return new RiskFactor("unsafe-point", "Current step '" + stepName + "' is not a safe migration point",
                    0.3, "timing");
        }
    }
}
