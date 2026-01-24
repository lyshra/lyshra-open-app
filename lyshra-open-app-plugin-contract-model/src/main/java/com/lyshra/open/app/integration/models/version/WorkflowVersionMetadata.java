package com.lyshra.open.app.integration.models.version;

import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersionMetadata;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementation of workflow version metadata.
 * Immutable value object representing complete version tracking information.
 *
 * <p>This class provides:</p>
 * <ul>
 *   <li>Unique identification through workflowId + version composite key</li>
 *   <li>Schema integrity verification via SHA-256 hash</li>
 *   <li>Lifecycle management with isActive flag</li>
 *   <li>Complete audit trail with timestamps and actor information</li>
 * </ul>
 *
 * <p>Design Pattern: Value Object pattern with Builder for construction.</p>
 */
@Data
@Builder(toBuilder = true)
public class WorkflowVersionMetadata implements IWorkflowVersionMetadata, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Stable workflow identifier (e.g., "order-processing").
     */
    private final String workflowId;

    /**
     * Semantic version of this workflow definition.
     */
    private final IWorkflowVersion version;

    /**
     * SHA-256 hash of the workflow schema for integrity verification.
     */
    private final String schemaHash;

    /**
     * Timestamp when this version was created/registered.
     */
    @Builder.Default
    private final Instant createdAt = Instant.now();

    /**
     * Flag indicating if this version is active for new executions.
     */
    @Builder.Default
    private final boolean active = true;

    /**
     * Timestamp of last modification.
     */
    private final Instant updatedAt;

    /**
     * Identifier of the creator (user or system).
     */
    private final String createdBy;

    /**
     * Reason for deactivation if inactive.
     */
    private final String deactivationReason;

    /**
     * Timestamp when version was deactivated.
     */
    private final Instant deactivatedAt;

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public Optional<Instant> getUpdatedAt() {
        return Optional.ofNullable(updatedAt);
    }

    @Override
    public Optional<String> getCreatedBy() {
        return Optional.ofNullable(createdBy);
    }

    @Override
    public Optional<String> getDeactivationReason() {
        return Optional.ofNullable(deactivationReason);
    }

    @Override
    public Optional<Instant> getDeactivatedAt() {
        return Optional.ofNullable(deactivatedAt);
    }

    /**
     * Creates a new metadata instance with the version activated.
     *
     * @return new activated metadata instance
     */
    public WorkflowVersionMetadata activate() {
        return this.toBuilder()
                .active(true)
                .updatedAt(Instant.now())
                .deactivationReason(null)
                .deactivatedAt(null)
                .build();
    }

    /**
     * Creates a new metadata instance with the version deactivated.
     *
     * @param reason reason for deactivation
     * @return new deactivated metadata instance
     */
    public WorkflowVersionMetadata deactivate(String reason) {
        Instant now = Instant.now();
        return this.toBuilder()
                .active(false)
                .updatedAt(now)
                .deactivationReason(reason)
                .deactivatedAt(now)
                .build();
    }

    /**
     * Checks equality based on workflowId and version (composite key).
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowVersionMetadata that = (WorkflowVersionMetadata) o;
        return Objects.equals(workflowId, that.workflowId) &&
               Objects.equals(version, that.version);
    }

    /**
     * Hash code based on composite key (workflowId + version).
     */
    @Override
    public int hashCode() {
        return Objects.hash(workflowId, version);
    }

    @Override
    public String toString() {
        return String.format("WorkflowVersionMetadata{workflowId='%s', version=%s, active=%s, schemaHash='%s...'}",
                workflowId,
                version != null ? version.toVersionString() : "null",
                active,
                schemaHash != null && schemaHash.length() > 8 ? schemaHash.substring(0, 8) : schemaHash);
    }

    /**
     * Creates metadata for a new workflow version.
     *
     * @param workflowId workflow identifier
     * @param version semantic version
     * @param schemaHash SHA-256 hash of workflow schema
     * @param createdBy creator identifier
     * @return new metadata instance
     */
    public static WorkflowVersionMetadata create(
            String workflowId,
            IWorkflowVersion version,
            String schemaHash,
            String createdBy) {
        return WorkflowVersionMetadata.builder()
                .workflowId(workflowId)
                .version(version)
                .schemaHash(schemaHash)
                .createdBy(createdBy)
                .active(true)
                .build();
    }

    /**
     * Creates metadata from an existing versioned workflow.
     *
     * @param workflowId workflow identifier
     * @param version workflow version
     * @param schemaHash computed schema hash
     * @return new metadata instance
     */
    public static WorkflowVersionMetadata fromWorkflow(
            String workflowId,
            IWorkflowVersion version,
            String schemaHash) {
        return WorkflowVersionMetadata.builder()
                .workflowId(workflowId)
                .version(version)
                .schemaHash(schemaHash)
                .createdAt(version.getCreatedAt())
                .active(!version.isDeprecated())
                .build();
    }
}
