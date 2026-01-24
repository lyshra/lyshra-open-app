package com.lyshra.open.app.integration.models.version.migration;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationContext;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationStrategy;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of migration context carrying all migration state.
 */
@Data
@Builder
public class MigrationContext implements IMigrationContext, Serializable {

    private static final long serialVersionUID = 1L;

    @Builder.Default
    private final String migrationId = UUID.randomUUID().toString();
    private final String executionId;
    private final String workflowId;
    private final IWorkflowVersion sourceVersion;
    private final IWorkflowVersion targetVersion;
    private final ILyshraOpenAppContext executionContext;
    private final String currentStepName;
    @Builder.Default
    private final Map<String, Object> preMigrationState = new HashMap<>();
    @Builder.Default
    private final IMigrationStrategy strategy = IMigrationStrategy.FROZEN;
    @Builder.Default
    private final Instant migrationInitiatedAt = Instant.now();
    @Builder.Default
    private final String initiatedBy = "system";
    private final String migrationReason;
    private final String correlationId;
    @Builder.Default
    private final boolean dryRun = false;

    @Override
    public Map<String, Object> getPreMigrationState() {
        return Collections.unmodifiableMap(preMigrationState);
    }

    @Override
    public Optional<String> getMigrationReason() {
        return Optional.ofNullable(migrationReason);
    }

    @Override
    public Optional<String> getCorrelationId() {
        return Optional.ofNullable(correlationId);
    }

    /**
     * Creates a migration context for the given execution.
     *
     * @param executionId execution ID
     * @param workflowId workflow ID
     * @param sourceVersion source version
     * @param targetVersion target version
     * @param executionContext current execution context
     * @param currentStepName current step
     * @param strategy migration strategy
     * @return migration context
     */
    public static MigrationContext forExecution(
            String executionId,
            String workflowId,
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion,
            ILyshraOpenAppContext executionContext,
            String currentStepName,
            IMigrationStrategy strategy) {

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("data", executionContext.getData());
        snapshot.put("variables", new HashMap<>(executionContext.getVariables()));
        snapshot.put("currentStep", currentStepName);

        return MigrationContext.builder()
                .executionId(executionId)
                .workflowId(workflowId)
                .sourceVersion(sourceVersion)
                .targetVersion(targetVersion)
                .executionContext(executionContext)
                .currentStepName(currentStepName)
                .preMigrationState(snapshot)
                .strategy(strategy)
                .build();
    }

    /**
     * Creates a dry-run migration context.
     *
     * @param base base context
     * @return dry-run context
     */
    public static MigrationContext dryRun(MigrationContext base) {
        return MigrationContext.builder()
                .executionId(base.executionId)
                .workflowId(base.workflowId)
                .sourceVersion(base.sourceVersion)
                .targetVersion(base.targetVersion)
                .executionContext(base.executionContext)
                .currentStepName(base.currentStepName)
                .preMigrationState(new HashMap<>(base.preMigrationState))
                .strategy(base.strategy)
                .migrationInitiatedAt(base.migrationInitiatedAt)
                .initiatedBy(base.initiatedBy)
                .migrationReason(base.migrationReason)
                .correlationId(base.correlationId)
                .dryRun(true)
                .build();
    }
}
