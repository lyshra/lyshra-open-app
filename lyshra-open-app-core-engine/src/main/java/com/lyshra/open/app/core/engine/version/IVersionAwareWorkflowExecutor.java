package com.lyshra.open.app.core.engine.version;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowExecutionBinding;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationStrategy;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Version-aware workflow executor that manages execution bindings
 * and supports migration during workflow execution.
 *
 * <p>Design Pattern: Decorator pattern extending base executor with versioning.</p>
 */
public interface IVersionAwareWorkflowExecutor {

    /**
     * Starts a new workflow execution on the default (latest stable) version.
     *
     * @param identifier workflow identifier
     * @param context execution context
     * @return execution result with context
     */
    Mono<VersionedExecutionResult> execute(
            ILyshraOpenAppWorkflowIdentifier identifier,
            ILyshraOpenAppContext context);

    /**
     * Starts a new workflow execution on a specific version.
     *
     * @param workflow versioned workflow to execute
     * @param context execution context
     * @param migrationStrategy migration strategy for this execution
     * @return execution result with context
     */
    Mono<VersionedExecutionResult> executeVersion(
            IVersionedWorkflow workflow,
            ILyshraOpenAppContext context,
            IMigrationStrategy migrationStrategy);

    /**
     * Resumes an existing execution from its binding.
     *
     * @param executionId execution ID to resume
     * @param context execution context
     * @return execution result with context
     */
    Mono<VersionedExecutionResult> resume(
            String executionId,
            ILyshraOpenAppContext context);

    /**
     * Gets the current execution binding for an execution.
     *
     * @param executionId execution ID
     * @return execution binding if exists
     */
    Optional<IWorkflowExecutionBinding> getBinding(String executionId);

    /**
     * Checks if a newer version is available for the execution.
     *
     * @param executionId execution ID
     * @return true if upgrade available
     */
    boolean hasNewerVersionAvailable(String executionId);

    /**
     * Gets the recommended upgrade version for an execution.
     *
     * @param executionId execution ID
     * @return recommended version if available
     */
    Optional<IVersionedWorkflow> getRecommendedUpgrade(String executionId);

    /**
     * Result of versioned workflow execution.
     */
    record VersionedExecutionResult(
            String executionId,
            ILyshraOpenAppContext context,
            IWorkflowExecutionBinding binding,
            boolean migrationOccurred,
            Optional<String> migrationDetails
    ) {
        public static VersionedExecutionResult of(
                String executionId,
                ILyshraOpenAppContext context,
                IWorkflowExecutionBinding binding) {
            return new VersionedExecutionResult(executionId, context, binding, false, Optional.empty());
        }

        public static VersionedExecutionResult withMigration(
                String executionId,
                ILyshraOpenAppContext context,
                IWorkflowExecutionBinding binding,
                String migrationDetails) {
            return new VersionedExecutionResult(executionId, context, binding, true, Optional.of(migrationDetails));
        }
    }
}
