package com.lyshra.open.app.core.engine.version.storage;

import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;

import java.util.List;
import java.util.Optional;

/**
 * Storage interface for persisting multiple versions of workflow definitions.
 *
 * <p>This interface supports:</p>
 * <ul>
 *   <li>Storing multiple versions per workflowId without overwriting</li>
 *   <li>Persisting YAML/JSON definitions with metadata</li>
 *   <li>Loading specific versions at runtime</li>
 *   <li>Version conflict detection</li>
 * </ul>
 *
 * <p>Storage Structure:</p>
 * <pre>
 * workflows/
 *   └── {workflowId}/
 *       ├── metadata.json           (workflow-level metadata)
 *       └── versions/
 *           ├── 1.0.0/
 *           │   ├── definition.yaml (or .json)
 *           │   └── version-metadata.json
 *           ├── 1.1.0/
 *           │   ├── definition.yaml
 *           │   └── version-metadata.json
 *           └── 2.0.0/
 *               ├── definition.yaml
 *               └── version-metadata.json
 * </pre>
 *
 * <p>Design Pattern: Repository pattern for versioned workflow persistence.</p>
 */
public interface IVersionedWorkflowStorage {

    /**
     * Supported serialization formats.
     */
    enum SerializationFormat {
        YAML,
        JSON
    }

    /**
     * Stores a new workflow version.
     * Fails if the version already exists (no overwriting).
     *
     * @param workflow versioned workflow to store
     * @param format serialization format
     * @return storage result with location info
     * @throws WorkflowVersionConflictException if version already exists
     */
    StorageResult store(IVersionedWorkflow workflow, SerializationFormat format);

    /**
     * Stores a new workflow version using default format (YAML).
     *
     * @param workflow versioned workflow to store
     * @return storage result
     */
    default StorageResult store(IVersionedWorkflow workflow) {
        return store(workflow, SerializationFormat.YAML);
    }

    /**
     * Loads a specific workflow version.
     *
     * @param workflowId workflow identifier
     * @param version version to load
     * @return loaded workflow if exists
     */
    Optional<IVersionedWorkflow> load(String workflowId, IWorkflowVersion version);

    /**
     * Loads the latest version of a workflow.
     *
     * @param workflowId workflow identifier
     * @return latest version if exists
     */
    Optional<IVersionedWorkflow> loadLatest(String workflowId);

    /**
     * Loads the latest active version of a workflow.
     *
     * @param workflowId workflow identifier
     * @return latest active version if exists
     */
    Optional<IVersionedWorkflow> loadLatestActive(String workflowId);

    /**
     * Loads all versions of a workflow.
     *
     * @param workflowId workflow identifier
     * @return all versions sorted by version descending
     */
    List<IVersionedWorkflow> loadAllVersions(String workflowId);

    /**
     * Lists all stored workflow IDs.
     *
     * @return list of workflow IDs
     */
    List<String> listWorkflowIds();

    /**
     * Lists all version numbers for a workflow.
     *
     * @param workflowId workflow identifier
     * @return list of versions sorted descending
     */
    List<IWorkflowVersion> listVersions(String workflowId);

    /**
     * Checks if a specific version exists.
     *
     * @param workflowId workflow identifier
     * @param version version to check
     * @return true if exists
     */
    boolean exists(String workflowId, IWorkflowVersion version);

    /**
     * Checks if any version of a workflow exists.
     *
     * @param workflowId workflow identifier
     * @return true if any version exists
     */
    boolean exists(String workflowId);

    /**
     * Deletes a specific version (soft delete - marks as deleted).
     * Does not physically remove files to maintain audit trail.
     *
     * @param workflowId workflow identifier
     * @param version version to delete
     * @param reason deletion reason
     * @return true if deleted
     */
    boolean delete(String workflowId, IWorkflowVersion version, String reason);

    /**
     * Gets the raw definition content (YAML/JSON) for a version.
     *
     * @param workflowId workflow identifier
     * @param version version
     * @return raw content if exists
     */
    Optional<String> getRawDefinition(String workflowId, IWorkflowVersion version);

    /**
     * Gets the storage location for a workflow version.
     *
     * @param workflowId workflow identifier
     * @param version version
     * @return storage path/location
     */
    String getStorageLocation(String workflowId, IWorkflowVersion version);

    /**
     * Checks if storing the given workflow would cause a version conflict.
     * This allows pre-checking before attempting to store.
     *
     * @param workflow workflow to check
     * @return conflict check result
     */
    ConflictCheckResult checkConflict(IVersionedWorkflow workflow);

    /**
     * Checks if storing a workflow with the given ID and version would conflict.
     *
     * @param workflowId workflow identifier
     * @param version version to check
     * @return true if version already exists (would conflict)
     */
    default boolean wouldConflict(String workflowId, IWorkflowVersion version) {
        return exists(workflowId, version);
    }

    /**
     * Suggests the next available version number for a workflow.
     * Returns the incremented patch version if the current exists.
     *
     * @param workflowId workflow identifier
     * @param proposedVersion the version being proposed
     * @return suggested version that won't conflict
     */
    IWorkflowVersion suggestNonConflictingVersion(String workflowId, IWorkflowVersion proposedVersion);

    /**
     * Result of a conflict check operation.
     */
    record ConflictCheckResult(
            boolean hasConflict,
            String workflowId,
            IWorkflowVersion version,
            String existingSchemaHash,
            String newSchemaHash,
            boolean isContentConflict,
            IWorkflowVersion suggestedVersion
    ) {
        /**
         * Creates a result indicating no conflict.
         */
        public static ConflictCheckResult noConflict(String workflowId, IWorkflowVersion version) {
            return new ConflictCheckResult(false, workflowId, version, null, null, false, version);
        }

        /**
         * Creates a result indicating a conflict exists.
         */
        public static ConflictCheckResult conflict(
                String workflowId,
                IWorkflowVersion version,
                String existingHash,
                String newHash,
                IWorkflowVersion suggestedVersion) {
            boolean contentConflict = existingHash != null && newHash != null && !existingHash.equals(newHash);
            return new ConflictCheckResult(true, workflowId, version, existingHash, newHash,
                    contentConflict, suggestedVersion);
        }

        /**
         * Returns true if the conflict is due to different content (not just same version).
         */
        public boolean isDifferentContent() {
            return isContentConflict;
        }

        /**
         * Returns true if the existing and new content are identical.
         */
        public boolean isIdenticalContent() {
            return hasConflict && !isContentConflict;
        }
    }

    /**
     * Result of a storage operation.
     */
    record StorageResult(
            boolean success,
            String workflowId,
            IWorkflowVersion version,
            String location,
            String schemaHash,
            SerializationFormat format,
            String message
    ) {
        public static StorageResult success(
                String workflowId,
                IWorkflowVersion version,
                String location,
                String schemaHash,
                SerializationFormat format) {
            return new StorageResult(true, workflowId, version, location, schemaHash, format,
                    "Stored successfully");
        }

        public static StorageResult failure(
                String workflowId,
                IWorkflowVersion version,
                String message) {
            return new StorageResult(false, workflowId, version, null, null, null, message);
        }
    }
}
