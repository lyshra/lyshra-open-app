package com.lyshra.open.app.integration.contract.version.migration;

import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import reactor.core.publisher.Mono;

/**
 * Handler interface for custom workflow migration logic.
 * Plugins can implement this to provide specific migration behaviors.
 *
 * <p>Design Pattern: Handler/Adapter pattern for pluggable migration implementations.</p>
 *
 * <p>Implementations should be:</p>
 * <ul>
 *   <li>Idempotent - multiple invocations produce the same result</li>
 *   <li>Reversible - support rollback where possible</li>
 *   <li>Atomic - complete fully or not at all</li>
 * </ul>
 */
public interface IMigrationHandler {

    /**
     * Returns the workflow ID this handler supports.
     *
     * @return workflow ID
     */
    String getWorkflowId();

    /**
     * Returns the source version range this handler supports.
     *
     * @return source version (can use wildcards like "1.x.x")
     */
    String getSourceVersionPattern();

    /**
     * Returns the target version this handler migrates to.
     *
     * @return target version
     */
    IWorkflowVersion getTargetVersion();

    /**
     * Returns the priority of this handler (higher = more preferred).
     * When multiple handlers match, the highest priority is used.
     *
     * @return priority value
     */
    int getPriority();

    /**
     * Validates whether migration can proceed.
     * Should perform pre-flight checks without modifying state.
     *
     * @param context migration context
     * @return validation result
     */
    Mono<IMigrationValidationResult> validate(IMigrationContext context);

    /**
     * Executes the migration logic.
     * Transforms context, maps steps, and migrates state.
     *
     * @param context migration context
     * @return migration result
     */
    Mono<IMigrationResult> migrate(IMigrationContext context);

    /**
     * Rolls back a failed migration.
     * Should restore the execution to pre-migration state.
     *
     * @param context migration context
     * @param failedResult the failed migration result
     * @return rollback result
     */
    Mono<IMigrationResult> rollback(IMigrationContext context, IMigrationResult failedResult);

    /**
     * Checks if this handler can handle the given migration.
     *
     * @param context migration context
     * @return true if handler applies
     */
    boolean canHandle(IMigrationContext context);
}
