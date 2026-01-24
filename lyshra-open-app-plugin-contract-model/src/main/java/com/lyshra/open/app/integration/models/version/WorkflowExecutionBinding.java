package com.lyshra.open.app.integration.models.version;

import com.lyshra.open.app.integration.contract.version.IWorkflowExecutionBinding;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationStrategy;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier;
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
 * Implementation of workflow execution binding.
 * Binds an execution to a specific workflow version and tracks lifecycle.
 */
@Data
@Builder
public class WorkflowExecutionBinding implements IWorkflowExecutionBinding, Serializable {

    private static final long serialVersionUID = 1L;

    @Builder.Default
    private final String executionId = UUID.randomUUID().toString();
    private final ILyshraOpenAppWorkflowIdentifier workflowIdentifier;
    private final IWorkflowVersion boundVersion;
    @Builder.Default
    private ExecutionState state = ExecutionState.CREATED;
    private String currentStepName;
    @Builder.Default
    private final Instant startedAt = Instant.now();
    @Builder.Default
    private Instant lastUpdatedAt = Instant.now();
    @Builder.Default
    private IMigrationStrategy migrationStrategy = IMigrationStrategy.FROZEN;
    private IWorkflowVersion migratedFromVersion;
    @Builder.Default
    private int migrationCount = 0;
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    private String correlationId;
    private String parentExecutionId;
    @Builder.Default
    private boolean migrationAllowed = true;

    @Override
    public Optional<IWorkflowVersion> getMigratedFromVersion() {
        return Optional.ofNullable(migratedFromVersion);
    }

    @Override
    public Optional<String> getCorrelationId() {
        return Optional.ofNullable(correlationId);
    }

    @Override
    public Optional<String> getParentExecutionId() {
        return Optional.ofNullable(parentExecutionId);
    }

    @Override
    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    /**
     * Creates a new binding for starting a workflow execution.
     *
     * @param workflowIdentifier workflow identifier
     * @param version workflow version
     * @param startStep starting step name
     * @return new execution binding
     */
    public static WorkflowExecutionBinding create(
            ILyshraOpenAppWorkflowIdentifier workflowIdentifier,
            IWorkflowVersion version,
            String startStep) {
        return WorkflowExecutionBinding.builder()
                .workflowIdentifier(workflowIdentifier)
                .boundVersion(version)
                .currentStepName(startStep)
                .state(ExecutionState.CREATED)
                .build();
    }

    /**
     * Creates a new binding with updated state.
     *
     * @param newState new execution state
     * @return updated binding
     */
    public WorkflowExecutionBinding withState(ExecutionState newState) {
        return WorkflowExecutionBinding.builder()
                .executionId(this.executionId)
                .workflowIdentifier(this.workflowIdentifier)
                .boundVersion(this.boundVersion)
                .state(newState)
                .currentStepName(this.currentStepName)
                .startedAt(this.startedAt)
                .lastUpdatedAt(Instant.now())
                .migrationStrategy(this.migrationStrategy)
                .migratedFromVersion(this.migratedFromVersion)
                .migrationCount(this.migrationCount)
                .metadata(new HashMap<>(this.metadata))
                .correlationId(this.correlationId)
                .parentExecutionId(this.parentExecutionId)
                .migrationAllowed(this.migrationAllowed)
                .build();
    }

    /**
     * Creates a new binding with updated step.
     *
     * @param stepName new step name
     * @return updated binding
     */
    public WorkflowExecutionBinding withCurrentStep(String stepName) {
        return WorkflowExecutionBinding.builder()
                .executionId(this.executionId)
                .workflowIdentifier(this.workflowIdentifier)
                .boundVersion(this.boundVersion)
                .state(this.state)
                .currentStepName(stepName)
                .startedAt(this.startedAt)
                .lastUpdatedAt(Instant.now())
                .migrationStrategy(this.migrationStrategy)
                .migratedFromVersion(this.migratedFromVersion)
                .migrationCount(this.migrationCount)
                .metadata(new HashMap<>(this.metadata))
                .correlationId(this.correlationId)
                .parentExecutionId(this.parentExecutionId)
                .migrationAllowed(this.migrationAllowed)
                .build();
    }

    /**
     * Creates a new binding after migration.
     *
     * @param newVersion new bound version
     * @param newStepName new step name
     * @return migrated binding
     */
    public WorkflowExecutionBinding migrateTo(IWorkflowVersion newVersion, String newStepName) {
        return WorkflowExecutionBinding.builder()
                .executionId(this.executionId)
                .workflowIdentifier(this.workflowIdentifier)
                .boundVersion(newVersion)
                .state(ExecutionState.RUNNING)
                .currentStepName(newStepName)
                .startedAt(this.startedAt)
                .lastUpdatedAt(Instant.now())
                .migrationStrategy(this.migrationStrategy)
                .migratedFromVersion(this.boundVersion)
                .migrationCount(this.migrationCount + 1)
                .metadata(new HashMap<>(this.metadata))
                .correlationId(this.correlationId)
                .parentExecutionId(this.parentExecutionId)
                .migrationAllowed(this.migrationAllowed)
                .build();
    }
}
