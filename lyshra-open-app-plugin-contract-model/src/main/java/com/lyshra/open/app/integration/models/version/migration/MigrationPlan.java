package com.lyshra.open.app.integration.models.version.migration;

import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationPath;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationPlan;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationPlanner;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationStep;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of IMigrationPlan representing a validated migration plan.
 */
@Getter
@Builder
public class MigrationPlan implements IMigrationPlan {

    @Builder.Default
    private final String planId = UUID.randomUUID().toString();

    private final String workflowId;
    private final IWorkflowVersion sourceVersion;
    private final IWorkflowVersion targetVersion;

    @Builder.Default
    private final boolean valid = true;

    @Builder.Default
    private final boolean safe = true;

    @Builder.Default
    private final boolean dryRun = false;

    private final IMigrationPlanner.MigrationValidation validationResult;

    @Builder.Default
    private final List<IMigrationStep> steps = new ArrayList<>();

    @Builder.Default
    private final List<IMigrationPath> migrationPaths = new ArrayList<>();

    @Builder.Default
    private final int hopCount = 1;

    @Builder.Default
    private final Map<String, String> stepMappings = new HashMap<>();

    @Builder.Default
    private final Map<String, String> variableMappings = new HashMap<>();

    private final String targetStepName;
    private final String sourceStepName;

    @Builder.Default
    private final Set<String> addedSteps = new HashSet<>();

    @Builder.Default
    private final Set<String> removedSteps = new HashSet<>();

    @Builder.Default
    private final Set<String> addedVariables = new HashSet<>();

    @Builder.Default
    private final Set<String> removedVariables = new HashSet<>();

    @Builder.Default
    private final Map<String, String> renamedVariables = new HashMap<>();

    private final IMigrationPlanner.RiskAssessment riskAssessment;
    private final IMigrationPlanner.MigrationComplexity complexity;

    @Builder.Default
    private final Duration estimatedDuration = Duration.ofSeconds(1);

    @Builder.Default
    private final Instant createdAt = Instant.now();

    private final Instant expiresAt;

    @Builder.Default
    private final boolean requiresApproval = false;

    @Builder.Default
    private final boolean requiresBackup = false;

    @Builder.Default
    private final boolean supportsRollback = true;

    @Builder.Default
    private final List<String> handlerIds = new ArrayList<>();

    @Builder.Default
    private final List<String> transformerNames = new ArrayList<>();

    @Builder.Default
    private final List<Precondition> preconditions = new ArrayList<>();

    @Builder.Default
    private final List<Postcondition> postconditions = new ArrayList<>();

    @Builder.Default
    private final Map<String, Object> metadata = new HashMap<>();

    @Override
    public boolean isValid() {
        return valid;
    }

    @Override
    public boolean isSafe() {
        return safe;
    }

    @Override
    public boolean isDryRun() {
        return dryRun;
    }

    @Override
    public Optional<String> getSourceStepName() {
        return Optional.ofNullable(sourceStepName);
    }

    @Override
    public Optional<Instant> getExpiresAt() {
        return Optional.ofNullable(expiresAt);
    }

    @Override
    public boolean requiresApproval() {
        return requiresApproval;
    }

    @Override
    public boolean requiresBackup() {
        return requiresBackup;
    }

    @Override
    public boolean supportsRollback() {
        return supportsRollback;
    }

    @Override
    public PlanSummary getSummary() {
        return new PlanSummary(
                workflowId,
                sourceVersion.toVersionString(),
                targetVersion.toVersionString(),
                steps.size(),
                hopCount,
                valid,
                safe,
                complexity != null ? complexity.name() : "SIMPLE",
                riskAssessment != null ? riskAssessment.overallRisk().name() : "LOW",
                addedSteps.size(),
                removedSteps.size(),
                renamedVariables.size() + addedVariables.size() + removedVariables.size(),
                requiresApproval,
                formatDuration(estimatedDuration)
        );
    }

    private String formatDuration(Duration duration) {
        if (duration.toSeconds() < 60) {
            return duration.toSeconds() + "s";
        } else if (duration.toMinutes() < 60) {
            return duration.toMinutes() + "m";
        } else {
            return duration.toHours() + "h";
        }
    }

    /**
     * Creates an invalid plan with validation errors.
     */
    public static MigrationPlan invalid(
            String workflowId,
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion,
            IMigrationPlanner.MigrationValidation validationResult) {
        return MigrationPlan.builder()
                .workflowId(workflowId)
                .sourceVersion(sourceVersion)
                .targetVersion(targetVersion)
                .valid(false)
                .safe(false)
                .validationResult(validationResult)
                .complexity(IMigrationPlanner.MigrationComplexity.BLOCKED)
                .riskAssessment(IMigrationPlanner.RiskAssessment.critical(List.of()))
                .build();
    }

    /**
     * Creates a simple valid plan.
     */
    public static MigrationPlan simple(
            String workflowId,
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion,
            String targetStepName,
            List<IMigrationStep> steps) {
        return MigrationPlan.builder()
                .workflowId(workflowId)
                .sourceVersion(sourceVersion)
                .targetVersion(targetVersion)
                .valid(true)
                .safe(true)
                .targetStepName(targetStepName)
                .steps(steps)
                .validationResult(IMigrationPlanner.MigrationValidation.valid())
                .complexity(IMigrationPlanner.MigrationComplexity.SIMPLE)
                .riskAssessment(IMigrationPlanner.RiskAssessment.low())
                .build();
    }
}
