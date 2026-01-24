package com.lyshra.open.app.integration.models.version;

import com.lyshra.open.app.integration.contract.version.IWorkflowInstanceMetadata;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
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
 * Implementation of workflow instance metadata that tracks version information.
 *
 * <p>Each workflow instance carries this metadata to ensure it knows which
 * version it started with and is currently executing.</p>
 *
 * <p>Thread-safety: This class is immutable after construction. Use the
 * builder or copy methods to create modified instances.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * WorkflowInstanceMetadata metadata = WorkflowInstanceMetadata.builder()
 *     .workflowId("order-processing")
 *     .originalVersion(WorkflowVersion.of(1, 2, 0))
 *     .schemaHash("abc123...")
 *     .correlationId("req-12345")
 *     .build();
 *
 * // Start execution
 * metadata = metadata.start();
 *
 * // Update step
 * metadata = metadata.withCurrentStep("validate-order");
 *
 * // Complete
 * metadata = metadata.complete();
 * }</pre>
 */
@Data
@Builder(toBuilder = true)
public class WorkflowInstanceMetadata implements IWorkflowInstanceMetadata, Serializable {

    private static final long serialVersionUID = 1L;

    @Builder.Default
    private final String instanceId = UUID.randomUUID().toString();

    private final ILyshraOpenAppWorkflowIdentifier workflowIdentifier;
    private final String workflowId;
    private final IWorkflowVersion originalVersion;

    @Builder.Default
    private final IWorkflowVersion currentVersion = null; // Defaults to originalVersion

    private final String schemaHash;

    @Builder.Default
    private final Instant createdAt = Instant.now();

    private final Instant startedAt;
    private final Instant completedAt;
    private final String correlationId;
    private final String parentInstanceId;

    @Builder.Default
    private final int migrationCount = 0;

    private final IWorkflowVersion migratedFromVersion;

    @Builder.Default
    private final Map<String, Object> metadata = new HashMap<>();

    private final String currentStepName;

    @Builder.Default
    private final ExecutionState executionState = ExecutionState.CREATED;

    @Override
    public IWorkflowVersion getCurrentVersion() {
        return currentVersion != null ? currentVersion : originalVersion;
    }

    @Override
    public Optional<Instant> getStartedAt() {
        return Optional.ofNullable(startedAt);
    }

    @Override
    public Optional<Instant> getCompletedAt() {
        return Optional.ofNullable(completedAt);
    }

    @Override
    public Optional<String> getCorrelationId() {
        return Optional.ofNullable(correlationId);
    }

    @Override
    public Optional<String> getParentInstanceId() {
        return Optional.ofNullable(parentInstanceId);
    }

    @Override
    public Optional<IWorkflowVersion> getMigratedFromVersion() {
        return Optional.ofNullable(migratedFromVersion);
    }

    @Override
    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    @Override
    public Optional<String> getCurrentStepName() {
        return Optional.ofNullable(currentStepName);
    }

    /**
     * Creates a new instance with started state.
     *
     * @return new metadata with RUNNING state and startedAt timestamp
     */
    public WorkflowInstanceMetadata start() {
        return toBuilder()
                .executionState(ExecutionState.RUNNING)
                .startedAt(Instant.now())
                .build();
    }

    /**
     * Creates a new instance with completed state.
     *
     * @return new metadata with COMPLETED state and completedAt timestamp
     */
    public WorkflowInstanceMetadata complete() {
        return toBuilder()
                .executionState(ExecutionState.COMPLETED)
                .completedAt(Instant.now())
                .build();
    }

    /**
     * Creates a new instance with failed state.
     *
     * @return new metadata with FAILED state and completedAt timestamp
     */
    public WorkflowInstanceMetadata fail() {
        return toBuilder()
                .executionState(ExecutionState.FAILED)
                .completedAt(Instant.now())
                .build();
    }

    /**
     * Creates a new instance with cancelled state.
     *
     * @return new metadata with CANCELLED state and completedAt timestamp
     */
    public WorkflowInstanceMetadata cancel() {
        return toBuilder()
                .executionState(ExecutionState.CANCELLED)
                .completedAt(Instant.now())
                .build();
    }

    /**
     * Creates a new instance with paused state.
     *
     * @return new metadata with PAUSED state
     */
    public WorkflowInstanceMetadata pause() {
        return toBuilder()
                .executionState(ExecutionState.PAUSED)
                .build();
    }

    /**
     * Creates a new instance with resumed (running) state.
     *
     * @return new metadata with RUNNING state
     */
    public WorkflowInstanceMetadata resume() {
        return toBuilder()
                .executionState(ExecutionState.RUNNING)
                .build();
    }

    /**
     * Creates a new instance with updated current step.
     *
     * @param stepName name of the current step
     * @return new metadata with updated current step
     */
    public WorkflowInstanceMetadata withCurrentStep(String stepName) {
        return toBuilder()
                .currentStepName(stepName)
                .build();
    }

    /**
     * Creates a new instance after migration to a new version.
     *
     * @param newVersion the version migrated to
     * @return new metadata with updated version info
     */
    public WorkflowInstanceMetadata migratedTo(IWorkflowVersion newVersion) {
        return toBuilder()
                .migratedFromVersion(getCurrentVersion())
                .currentVersion(newVersion)
                .migrationCount(migrationCount + 1)
                .executionState(ExecutionState.RUNNING)
                .build();
    }

    /**
     * Creates a new instance with added metadata.
     *
     * @param key metadata key
     * @param value metadata value
     * @return new metadata with added entry
     */
    public WorkflowInstanceMetadata withMetadata(String key, Object value) {
        Map<String, Object> newMetadata = new HashMap<>(metadata);
        newMetadata.put(key, value);
        return toBuilder()
                .metadata(newMetadata)
                .build();
    }

    /**
     * Creates a new instance for a nested workflow.
     *
     * @param nestedWorkflowId nested workflow ID
     * @param nestedVersion nested workflow version
     * @param nestedSchemaHash nested workflow schema hash
     * @return new metadata for nested workflow
     */
    public WorkflowInstanceMetadata createNestedInstance(
            String nestedWorkflowId,
            IWorkflowVersion nestedVersion,
            String nestedSchemaHash) {
        return WorkflowInstanceMetadata.builder()
                .workflowId(nestedWorkflowId)
                .originalVersion(nestedVersion)
                .schemaHash(nestedSchemaHash)
                .parentInstanceId(this.instanceId)
                .correlationId(this.correlationId)
                .build();
    }

    /**
     * Creates instance metadata for a new workflow execution.
     *
     * @param workflowId workflow ID
     * @param version workflow version
     * @param schemaHash workflow schema hash
     * @return new instance metadata
     */
    public static WorkflowInstanceMetadata forNewExecution(
            String workflowId,
            IWorkflowVersion version,
            String schemaHash) {
        return WorkflowInstanceMetadata.builder()
                .workflowId(workflowId)
                .originalVersion(version)
                .schemaHash(schemaHash)
                .build();
    }

    /**
     * Creates instance metadata with a correlation ID.
     *
     * @param workflowId workflow ID
     * @param version workflow version
     * @param schemaHash workflow schema hash
     * @param correlationId correlation ID for tracing
     * @return new instance metadata
     */
    public static WorkflowInstanceMetadata forNewExecution(
            String workflowId,
            IWorkflowVersion version,
            String schemaHash,
            String correlationId) {
        return WorkflowInstanceMetadata.builder()
                .workflowId(workflowId)
                .originalVersion(version)
                .schemaHash(schemaHash)
                .correlationId(correlationId)
                .build();
    }

    /**
     * Builder customization.
     */
    public static class WorkflowInstanceMetadataBuilder {

        /**
         * Adds a metadata entry.
         */
        public WorkflowInstanceMetadataBuilder addMetadata(String key, Object value) {
            if (this.metadata$value == null) {
                this.metadata$value = new HashMap<>();
                this.metadata$set = true;
            }
            this.metadata$value.put(key, value);
            return this;
        }
    }
}
