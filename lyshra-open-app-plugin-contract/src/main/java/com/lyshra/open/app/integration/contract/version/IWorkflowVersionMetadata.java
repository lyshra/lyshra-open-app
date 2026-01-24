package com.lyshra.open.app.integration.contract.version;

import java.time.Instant;
import java.util.Optional;

/**
 * Workflow version metadata model that uniquely identifies each workflow definition version.
 * Contains all essential metadata for tracking, selecting, and validating workflow versions.
 *
 * <p>This model provides:</p>
 * <ul>
 *   <li>Unique identification via workflowId + version combination</li>
 *   <li>Schema integrity verification through schemaHash</li>
 *   <li>Lifecycle management through isActive flag</li>
 *   <li>Audit trail through createdAt timestamp</li>
 * </ul>
 *
 * <p>The composite key of (workflowId, version) uniquely identifies a workflow version,
 * while schemaHash provides content-based verification to detect definition changes.</p>
 */
public interface IWorkflowVersionMetadata {

    /**
     * Returns the stable workflow identifier.
     * This identifier remains constant across all versions of the same logical workflow.
     * Used as the primary grouping key for workflow versions.
     *
     * <p>Format: lowercase alphanumeric with hyphens (e.g., "order-processing", "user-onboarding")</p>
     *
     * @return unique workflow identifier
     */
    String getWorkflowId();

    /**
     * Returns the semantic version for this workflow definition.
     * Combined with workflowId, forms the unique identifier for this specific version.
     *
     * <p>Follows SemVer 2.0.0: MAJOR.MINOR.PATCH[-PRERELEASE][+BUILD]</p>
     *
     * @return workflow version
     */
    IWorkflowVersion getVersion();

    /**
     * Returns the SHA-256 hash of the workflow schema/definition.
     * Used for integrity verification and change detection.
     *
     * <p>The hash is computed from the canonical representation of the workflow definition,
     * including steps, processors, branching logic, and configuration. This enables:</p>
     * <ul>
     *   <li>Detection of unauthorized modifications</li>
     *   <li>Verification of definition integrity during deployment</li>
     *   <li>Fast equality comparison between definitions</li>
     *   <li>Change tracking in version control systems</li>
     * </ul>
     *
     * @return SHA-256 hash string (64 hexadecimal characters)
     */
    String getSchemaHash();

    /**
     * Returns the timestamp when this workflow version was created/registered.
     * Used for audit trails, ordering, and lifecycle management.
     *
     * @return creation timestamp in UTC
     */
    Instant getCreatedAt();

    /**
     * Indicates whether this workflow version is active and can accept new executions.
     *
     * <p>Active vs Inactive:</p>
     * <ul>
     *   <li>Active (true): Version can be used for new workflow executions</li>
     *   <li>Inactive (false): Version is archived; existing executions continue but no new ones start</li>
     * </ul>
     *
     * <p>Use cases for deactivation:</p>
     * <ul>
     *   <li>Deprecating old versions after migration period</li>
     *   <li>Emergency disabling of problematic versions</li>
     *   <li>Controlled rollout with activation windows</li>
     * </ul>
     *
     * @return true if version is active and can accept new executions
     */
    boolean isActive();

    /**
     * Returns the timestamp when this version was last modified (e.g., activation status change).
     *
     * @return last modification timestamp
     */
    Optional<Instant> getUpdatedAt();

    /**
     * Returns the user or system that created this version.
     *
     * @return creator identifier
     */
    Optional<String> getCreatedBy();

    /**
     * Returns the reason if this version is inactive.
     *
     * @return deactivation reason if inactive
     */
    Optional<String> getDeactivationReason();

    /**
     * Returns the timestamp when this version was deactivated.
     *
     * @return deactivation timestamp if inactive
     */
    Optional<Instant> getDeactivatedAt();

    /**
     * Returns a unique composite key combining workflowId and version.
     * Format: "workflowId:version" (e.g., "order-processing:1.2.3")
     *
     * @return composite unique key
     */
    default String getCompositeKey() {
        return getWorkflowId() + ":" + getVersion().toVersionString();
    }

    /**
     * Checks if this version can be used for new executions.
     * A version must be both active and not deprecated.
     *
     * @return true if version is available for new executions
     */
    default boolean isAvailableForNewExecutions() {
        return isActive() && !getVersion().isDeprecated();
    }
}
