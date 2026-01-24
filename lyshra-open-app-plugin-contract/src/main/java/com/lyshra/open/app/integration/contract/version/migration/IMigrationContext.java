package com.lyshra.open.app.integration.contract.version.migration;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Context object carrying all information needed for workflow migration.
 * Provides access to source/target versions, execution state, and transformation data.
 */
public interface IMigrationContext {

    /**
     * Returns the execution ID being migrated.
     *
     * @return execution ID
     */
    String getExecutionId();

    /**
     * Returns the workflow ID (stable across versions).
     *
     * @return workflow ID
     */
    String getWorkflowId();

    /**
     * Returns the source version being migrated from.
     *
     * @return source version
     */
    IWorkflowVersion getSourceVersion();

    /**
     * Returns the target version being migrated to.
     *
     * @return target version
     */
    IWorkflowVersion getTargetVersion();

    /**
     * Returns the current execution context.
     *
     * @return execution context
     */
    ILyshraOpenAppContext getExecutionContext();

    /**
     * Returns the current step name in the source workflow.
     *
     * @return current step name
     */
    String getCurrentStepName();

    /**
     * Returns the execution state before migration.
     *
     * @return pre-migration state snapshot
     */
    Map<String, Object> getPreMigrationState();

    /**
     * Returns the migration strategy being applied.
     *
     * @return migration strategy
     */
    IMigrationStrategy getStrategy();

    /**
     * Returns the timestamp when migration was initiated.
     *
     * @return migration initiation time
     */
    Instant getMigrationInitiatedAt();

    /**
     * Returns the user/system that initiated the migration.
     *
     * @return migration initiator identifier
     */
    String getInitiatedBy();

    /**
     * Returns the reason for migration.
     *
     * @return migration reason
     */
    Optional<String> getMigrationReason();

    /**
     * Returns correlation ID for tracking related migrations.
     *
     * @return correlation ID
     */
    Optional<String> getCorrelationId();

    /**
     * Indicates if this is a dry-run migration (no actual changes).
     *
     * @return true if dry-run
     */
    boolean isDryRun();
}
