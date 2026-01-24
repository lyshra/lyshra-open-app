package com.lyshra.open.app.integration.contract.version;

import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Metadata for a workflow instance that tracks which version it is executing.
 *
 * <p>Each running workflow instance carries this metadata to ensure execution
 * uses the correct definition even after workflow updates. This is essential for:</p>
 * <ul>
 *   <li>Version safety - in-flight executions continue on their original version</li>
 *   <li>Auditability - tracking which version was used for each execution</li>
 *   <li>Migration support - knowing the source version for migration operations</li>
 *   <li>Debugging - understanding execution behavior based on version</li>
 * </ul>
 *
 * <p>Lifecycle:</p>
 * <pre>
 * 1. Instance created with initial version metadata
 * 2. Execution runs using the bound workflow version
 * 3. If migrated, originalVersion preserved, currentVersion updated
 * 4. Instance completes with full version history
 * </pre>
 */
public interface IWorkflowInstanceMetadata {

    /**
     * Returns the unique instance ID for this workflow execution.
     *
     * @return instance ID
     */
    String getInstanceId();

    /**
     * Returns the workflow identifier (organization, module, workflow name).
     *
     * @return workflow identifier
     */
    ILyshraOpenAppWorkflowIdentifier getWorkflowIdentifier();

    /**
     * Returns the workflow ID (simple string identifier).
     *
     * @return workflow ID
     */
    String getWorkflowId();

    /**
     * Returns the version this instance started with.
     * This never changes, even after migration.
     *
     * @return original version at start
     */
    IWorkflowVersion getOriginalVersion();

    /**
     * Returns the current version this instance is executing.
     * Same as original unless migrated.
     *
     * @return current executing version
     */
    IWorkflowVersion getCurrentVersion();

    /**
     * Returns the schema hash of the workflow definition at start.
     * Used for integrity verification.
     *
     * @return schema hash
     */
    String getSchemaHash();

    /**
     * Returns the timestamp when this instance was created.
     *
     * @return creation timestamp
     */
    Instant getCreatedAt();

    /**
     * Returns the timestamp when execution started.
     *
     * @return start timestamp, empty if not yet started
     */
    Optional<Instant> getStartedAt();

    /**
     * Returns the timestamp when execution completed.
     *
     * @return completion timestamp, empty if still running
     */
    Optional<Instant> getCompletedAt();

    /**
     * Returns the correlation ID for distributed tracing.
     *
     * @return correlation ID, empty if not set
     */
    Optional<String> getCorrelationId();

    /**
     * Returns the parent instance ID if this is a nested workflow.
     *
     * @return parent instance ID, empty if root workflow
     */
    Optional<String> getParentInstanceId();

    /**
     * Returns the number of times this instance has been migrated.
     *
     * @return migration count
     */
    int getMigrationCount();

    /**
     * Returns true if this instance has been migrated from its original version.
     *
     * @return true if migrated
     */
    default boolean isMigrated() {
        return getMigrationCount() > 0;
    }

    /**
     * Returns the version this instance was last migrated from.
     *
     * @return previous version before last migration, empty if never migrated
     */
    Optional<IWorkflowVersion> getMigratedFromVersion();

    /**
     * Returns additional metadata associated with this instance.
     *
     * @return metadata map
     */
    Map<String, Object> getMetadata();

    /**
     * Returns the current step name in execution.
     *
     * @return current step name, empty if not tracking
     */
    Optional<String> getCurrentStepName();

    /**
     * Returns the execution state of this instance.
     *
     * @return execution state
     */
    ExecutionState getExecutionState();

    /**
     * Checks if this instance is still active (running or paused).
     *
     * @return true if active
     */
    default boolean isActive() {
        ExecutionState state = getExecutionState();
        return state == ExecutionState.RUNNING || state == ExecutionState.PAUSED;
    }

    /**
     * Checks if this instance is in a terminal state.
     *
     * @return true if terminal
     */
    default boolean isTerminal() {
        ExecutionState state = getExecutionState();
        return state == ExecutionState.COMPLETED ||
               state == ExecutionState.FAILED ||
               state == ExecutionState.CANCELLED ||
               state == ExecutionState.TERMINATED;
    }

    /**
     * Returns a composite key for this instance (workflowId:instanceId).
     *
     * @return composite key
     */
    default String getCompositeKey() {
        return getWorkflowId() + ":" + getInstanceId();
    }

    /**
     * Returns the version string of the original version.
     *
     * @return original version string
     */
    default String getOriginalVersionString() {
        return getOriginalVersion().toVersionString();
    }

    /**
     * Returns the version string of the current version.
     *
     * @return current version string
     */
    default String getCurrentVersionString() {
        return getCurrentVersion().toVersionString();
    }

    /**
     * Execution state for workflow instances.
     */
    enum ExecutionState {
        /**
         * Instance is created but not started.
         */
        CREATED,

        /**
         * Instance is queued for execution.
         */
        QUEUED,

        /**
         * Instance is currently running.
         */
        RUNNING,

        /**
         * Instance is paused (e.g., waiting for human input).
         */
        PAUSED,

        /**
         * Instance is pending migration approval.
         */
        PENDING_MIGRATION,

        /**
         * Instance is being migrated to a new version.
         */
        MIGRATING,

        /**
         * Instance completed successfully.
         */
        COMPLETED,

        /**
         * Instance failed with an error.
         */
        FAILED,

        /**
         * Instance was cancelled by user/system.
         */
        CANCELLED,

        /**
         * Instance was terminated due to version deprecation.
         */
        TERMINATED
    }
}
