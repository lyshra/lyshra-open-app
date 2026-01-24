package com.lyshra.open.app.integration.contract.version;

import com.lyshra.open.app.integration.contract.version.migration.IMigrationStrategy;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Binds a workflow execution to a specific workflow version.
 * Ensures in-flight executions continue on the version they started with.
 *
 * <p>This is the cornerstone of version safety - once an execution starts,
 * it is bound to a specific version until explicitly migrated.</p>
 */
public interface IWorkflowExecutionBinding {

    /**
     * Execution lifecycle state.
     */
    enum ExecutionState {
        /**
         * Execution is newly created but not started.
         */
        CREATED,

        /**
         * Execution is currently running.
         */
        RUNNING,

        /**
         * Execution is paused (e.g., waiting for human input).
         */
        PAUSED,

        /**
         * Execution is waiting for migration approval.
         */
        PENDING_MIGRATION,

        /**
         * Execution is being migrated.
         */
        MIGRATING,

        /**
         * Execution completed successfully.
         */
        COMPLETED,

        /**
         * Execution failed.
         */
        FAILED,

        /**
         * Execution was cancelled.
         */
        CANCELLED,

        /**
         * Execution was terminated due to version deprecation.
         */
        TERMINATED
    }

    /**
     * Returns the unique execution ID.
     *
     * @return execution ID
     */
    String getExecutionId();

    /**
     * Returns the workflow identifier (includes plugin and workflow name).
     *
     * @return workflow identifier
     */
    ILyshraOpenAppWorkflowIdentifier getWorkflowIdentifier();

    /**
     * Returns the workflow version this execution is bound to.
     *
     * @return bound version
     */
    IWorkflowVersion getBoundVersion();

    /**
     * Returns the current execution state.
     *
     * @return execution state
     */
    ExecutionState getState();

    /**
     * Returns the current step name.
     *
     * @return current step name
     */
    String getCurrentStepName();

    /**
     * Returns the timestamp when execution started.
     *
     * @return start timestamp
     */
    Instant getStartedAt();

    /**
     * Returns the timestamp of last state change.
     *
     * @return last updated timestamp
     */
    Instant getLastUpdatedAt();

    /**
     * Returns the migration strategy for this execution.
     *
     * @return migration strategy
     */
    IMigrationStrategy getMigrationStrategy();

    /**
     * Returns the version this execution was migrated from, if any.
     *
     * @return original version before migration
     */
    Optional<IWorkflowVersion> getMigratedFromVersion();

    /**
     * Returns the count of migrations performed on this execution.
     *
     * @return migration count
     */
    int getMigrationCount();

    /**
     * Returns execution metadata.
     *
     * @return metadata map
     */
    Map<String, Object> getMetadata();

    /**
     * Returns correlation ID for distributed tracing.
     *
     * @return correlation ID
     */
    Optional<String> getCorrelationId();

    /**
     * Returns the parent execution ID if this is a nested workflow.
     *
     * @return parent execution ID
     */
    Optional<String> getParentExecutionId();

    /**
     * Checks if this execution can be migrated.
     *
     * @return true if migration allowed
     */
    boolean isMigrationAllowed();

    /**
     * Checks if execution is in a terminal state.
     *
     * @return true if terminal
     */
    default boolean isTerminal() {
        ExecutionState s = getState();
        return s == ExecutionState.COMPLETED ||
               s == ExecutionState.FAILED ||
               s == ExecutionState.CANCELLED ||
               s == ExecutionState.TERMINATED;
    }

    /**
     * Checks if execution is active (running or paused).
     *
     * @return true if active
     */
    default boolean isActive() {
        ExecutionState s = getState();
        return s == ExecutionState.RUNNING || s == ExecutionState.PAUSED;
    }
}
