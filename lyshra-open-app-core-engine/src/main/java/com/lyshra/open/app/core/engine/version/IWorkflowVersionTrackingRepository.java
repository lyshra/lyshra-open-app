package com.lyshra.open.app.core.engine.version;

import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersionMetadata;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for tracking and retrieving workflow versions reliably.
 *
 * <p>This repository provides comprehensive querying capabilities for workflow versions
 * using the versioning model fields:</p>
 * <ul>
 *   <li>workflowId - stable identifier for querying all versions of a workflow</li>
 *   <li>version - semantic version for specific version retrieval</li>
 *   <li>schemaHash - for integrity verification and duplicate detection</li>
 *   <li>createdAt - for temporal queries and ordering</li>
 *   <li>isActive - for filtering available versions</li>
 * </ul>
 *
 * <p>Design Pattern: Repository pattern for version persistence and retrieval.</p>
 */
public interface IWorkflowVersionTrackingRepository {

    // ===== CRUD Operations =====

    /**
     * Saves a new versioned workflow with its metadata.
     * Computes schemaHash if not provided.
     *
     * @param workflow versioned workflow to save
     * @return saved workflow with computed metadata
     * @throws IllegalStateException if version already exists
     */
    IVersionedWorkflow save(IVersionedWorkflow workflow);

    /**
     * Updates an existing workflow version's metadata (e.g., isActive flag).
     * Does not allow changing the workflow definition itself.
     *
     * @param workflowId workflow identifier
     * @param version version to update
     * @param metadata updated metadata
     * @return updated workflow
     */
    Optional<IVersionedWorkflow> updateMetadata(String workflowId, IWorkflowVersion version, IWorkflowVersionMetadata metadata);

    /**
     * Deletes a workflow version (soft delete - marks as inactive).
     *
     * @param workflowId workflow identifier
     * @param version version to delete
     * @param reason deletion reason
     * @return true if deleted
     */
    boolean delete(String workflowId, IWorkflowVersion version, String reason);

    // ===== Query by workflowId =====

    /**
     * Retrieves all versions of a workflow ordered by version descending.
     *
     * @param workflowId workflow identifier
     * @return list of all versions
     */
    List<IVersionedWorkflow> findAllByWorkflowId(String workflowId);

    /**
     * Retrieves only active versions of a workflow.
     *
     * @param workflowId workflow identifier
     * @return list of active versions
     */
    List<IVersionedWorkflow> findActiveByWorkflowId(String workflowId);

    /**
     * Counts total versions of a workflow.
     *
     * @param workflowId workflow identifier
     * @return version count
     */
    long countByWorkflowId(String workflowId);

    // ===== Query by version =====

    /**
     * Retrieves a specific workflow version.
     *
     * @param workflowId workflow identifier
     * @param version semantic version
     * @return workflow if found
     */
    Optional<IVersionedWorkflow> findByWorkflowIdAndVersion(String workflowId, IWorkflowVersion version);

    /**
     * Retrieves the latest version of a workflow (including pre-release).
     *
     * @param workflowId workflow identifier
     * @return latest version if exists
     */
    Optional<IVersionedWorkflow> findLatestByWorkflowId(String workflowId);

    /**
     * Retrieves the latest stable (non-pre-release) version.
     *
     * @param workflowId workflow identifier
     * @return latest stable version if exists
     */
    Optional<IVersionedWorkflow> findLatestStableByWorkflowId(String workflowId);

    /**
     * Retrieves the latest active version.
     *
     * @param workflowId workflow identifier
     * @return latest active version if exists
     */
    Optional<IVersionedWorkflow> findLatestActiveByWorkflowId(String workflowId);

    // ===== Query by schemaHash =====

    /**
     * Finds a workflow version by its schema hash.
     * Useful for detecting duplicate definitions.
     *
     * @param schemaHash SHA-256 hash
     * @return workflow if found
     */
    Optional<IVersionedWorkflow> findBySchemaHash(String schemaHash);

    /**
     * Finds all workflow versions with the same schema hash.
     * Useful for identifying duplicate definitions across workflows.
     *
     * @param schemaHash SHA-256 hash
     * @return list of workflows with matching hash
     */
    List<IVersionedWorkflow> findAllBySchemaHash(String schemaHash);

    /**
     * Verifies that a workflow's schema hash matches the stored value.
     *
     * @param workflowId workflow identifier
     * @param version version to verify
     * @param expectedHash expected schema hash
     * @return true if hash matches
     */
    boolean verifySchemaHash(String workflowId, IWorkflowVersion version, String expectedHash);

    // ===== Query by createdAt =====

    /**
     * Finds workflow versions created after the given timestamp.
     *
     * @param workflowId workflow identifier
     * @param after timestamp threshold
     * @return versions created after timestamp
     */
    List<IVersionedWorkflow> findByWorkflowIdCreatedAfter(String workflowId, Instant after);

    /**
     * Finds workflow versions created before the given timestamp.
     *
     * @param workflowId workflow identifier
     * @param before timestamp threshold
     * @return versions created before timestamp
     */
    List<IVersionedWorkflow> findByWorkflowIdCreatedBefore(String workflowId, Instant before);

    /**
     * Finds workflow versions created within a time range.
     *
     * @param workflowId workflow identifier
     * @param from start timestamp (inclusive)
     * @param to end timestamp (exclusive)
     * @return versions created within range
     */
    List<IVersionedWorkflow> findByWorkflowIdCreatedBetween(String workflowId, Instant from, Instant to);

    // ===== Query by isActive =====

    /**
     * Finds all active workflow versions across all workflows.
     *
     * @return all active versions
     */
    List<IVersionedWorkflow> findAllActive();

    /**
     * Finds all inactive workflow versions for a workflow.
     *
     * @param workflowId workflow identifier
     * @return inactive versions
     */
    List<IVersionedWorkflow> findInactiveByWorkflowId(String workflowId);

    // ===== Activation/Deactivation =====

    /**
     * Activates a workflow version.
     *
     * @param workflowId workflow identifier
     * @param version version to activate
     * @return activated workflow
     */
    Optional<IVersionedWorkflow> activate(String workflowId, IWorkflowVersion version);

    /**
     * Deactivates a workflow version.
     *
     * @param workflowId workflow identifier
     * @param version version to deactivate
     * @param reason deactivation reason
     * @return deactivated workflow
     */
    Optional<IVersionedWorkflow> deactivate(String workflowId, IWorkflowVersion version, String reason);

    /**
     * Checks if a specific version is active.
     *
     * @param workflowId workflow identifier
     * @param version version to check
     * @return true if active
     */
    boolean isActive(String workflowId, IWorkflowVersion version);

    // ===== Existence Checks =====

    /**
     * Checks if a workflow has any versions.
     *
     * @param workflowId workflow identifier
     * @return true if versions exist
     */
    boolean existsByWorkflowId(String workflowId);

    /**
     * Checks if a specific version exists.
     *
     * @param workflowId workflow identifier
     * @param version version to check
     * @return true if exists
     */
    boolean existsByWorkflowIdAndVersion(String workflowId, IWorkflowVersion version);

    /**
     * Checks if a schema hash already exists (for duplicate detection).
     *
     * @param schemaHash hash to check
     * @return true if exists
     */
    boolean existsBySchemaHash(String schemaHash);

    // ===== Metadata Operations =====

    /**
     * Retrieves version metadata for a specific version.
     *
     * @param workflowId workflow identifier
     * @param version version
     * @return metadata if found
     */
    Optional<IWorkflowVersionMetadata> getMetadata(String workflowId, IWorkflowVersion version);

    /**
     * Retrieves all metadata for a workflow's versions.
     *
     * @param workflowId workflow identifier
     * @return list of metadata ordered by version descending
     */
    List<IWorkflowVersionMetadata> getAllMetadataByWorkflowId(String workflowId);
}
