package com.lyshra.open.app.integration.contract.version.migration;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Result of a workflow migration operation.
 * Contains success/failure status, migrated state, and audit information.
 */
public interface IMigrationResult {

    /**
     * Migration outcome status.
     */
    enum Status {
        /**
         * Migration completed successfully.
         */
        SUCCESS,

        /**
         * Migration failed due to incompatibility.
         */
        FAILED_INCOMPATIBLE,

        /**
         * Migration failed due to validation error.
         */
        FAILED_VALIDATION,

        /**
         * Migration failed due to handler error.
         */
        FAILED_HANDLER_ERROR,

        /**
         * Migration was skipped (e.g., at blocked migration point).
         */
        SKIPPED,

        /**
         * Migration is pending manual approval.
         */
        PENDING_APPROVAL,

        /**
         * Migration was rolled back due to error.
         */
        ROLLED_BACK,

        /**
         * Dry-run completed without applying changes.
         */
        DRY_RUN_COMPLETE
    }

    /**
     * Returns the migration status.
     *
     * @return migration status
     */
    Status getStatus();

    /**
     * Returns the execution ID that was migrated.
     *
     * @return execution ID
     */
    String getExecutionId();

    /**
     * Returns the source version.
     *
     * @return source version
     */
    IWorkflowVersion getSourceVersion();

    /**
     * Returns the target version.
     *
     * @return target version
     */
    IWorkflowVersion getTargetVersion();

    /**
     * Returns the migrated execution context if successful.
     *
     * @return migrated context
     */
    Optional<ILyshraOpenAppContext> getMigratedContext();

    /**
     * Returns the target step name after migration.
     *
     * @return target step name
     */
    Optional<String> getTargetStepName();

    /**
     * Returns the error if migration failed.
     *
     * @return error throwable
     */
    Optional<Throwable> getError();

    /**
     * Returns detailed error message if migration failed.
     *
     * @return error message
     */
    Optional<String> getErrorMessage();

    /**
     * Returns the list of warnings generated during migration.
     *
     * @return warning messages
     */
    List<String> getWarnings();

    /**
     * Returns the migration duration.
     *
     * @return migration duration
     */
    Duration getDuration();

    /**
     * Returns the timestamp when migration completed.
     *
     * @return completion timestamp
     */
    Instant getCompletedAt();

    /**
     * Returns the pre-migration state snapshot.
     *
     * @return pre-migration state
     */
    Map<String, Object> getPreMigrationSnapshot();

    /**
     * Returns the post-migration state snapshot.
     *
     * @return post-migration state
     */
    Map<String, Object> getPostMigrationSnapshot();

    /**
     * Returns audit trail entries for the migration.
     *
     * @return audit entries
     */
    List<IMigrationAuditEntry> getAuditTrail();

    /**
     * Checks if migration was successful.
     *
     * @return true if successful
     */
    default boolean isSuccessful() {
        return getStatus() == Status.SUCCESS;
    }
}
