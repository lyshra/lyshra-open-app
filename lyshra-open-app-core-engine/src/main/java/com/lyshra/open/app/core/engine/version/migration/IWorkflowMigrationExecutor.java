package com.lyshra.open.app.core.engine.version.migration;

import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowExecutionBinding;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationContext;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationHandler;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationResult;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationValidationResult;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Optional;

/**
 * Executor for workflow migrations.
 * Orchestrates the migration process including validation, transformation, and rollback.
 *
 * <p>Design Pattern: Facade pattern for migration orchestration.</p>
 */
public interface IWorkflowMigrationExecutor {

    /**
     * Validates if an execution can be migrated to a target version.
     *
     * @param binding execution binding
     * @param targetWorkflow target workflow version
     * @return validation result
     */
    Mono<IMigrationValidationResult> validate(
            IWorkflowExecutionBinding binding,
            IVersionedWorkflow targetWorkflow);

    /**
     * Executes migration for an execution to a target version.
     *
     * @param context migration context
     * @return migration result
     */
    Mono<IMigrationResult> migrate(IMigrationContext context);

    /**
     * Executes a dry-run migration without applying changes.
     *
     * @param context migration context
     * @return dry-run result
     */
    Mono<IMigrationResult> dryRun(IMigrationContext context);

    /**
     * Rolls back a failed migration.
     *
     * @param context original migration context
     * @param failedResult failed migration result
     * @return rollback result
     */
    Mono<IMigrationResult> rollback(IMigrationContext context, IMigrationResult failedResult);

    /**
     * Registers a custom migration handler.
     *
     * @param handler migration handler
     */
    void registerHandler(IMigrationHandler handler);

    /**
     * Unregisters a migration handler.
     *
     * @param workflowId workflow ID
     * @param sourceVersionPattern source version pattern
     */
    void unregisterHandler(String workflowId, String sourceVersionPattern);

    /**
     * Gets all registered handlers for a workflow.
     *
     * @param workflowId workflow ID
     * @return registered handlers
     */
    Collection<IMigrationHandler> getHandlers(String workflowId);

    /**
     * Finds the best handler for a migration context.
     *
     * @param context migration context
     * @return best matching handler
     */
    Optional<IMigrationHandler> findHandler(IMigrationContext context);
}
