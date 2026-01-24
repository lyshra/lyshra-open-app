package com.lyshra.open.app.integration.contract.version.migration;

import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Per-workflow migration strategy configuration.
 * Allows workflows to customize their migration behavior, paths, and constraints.
 *
 * <p>This interface provides comprehensive control over how workflow instances
 * are migrated between versions, including:</p>
 * <ul>
 *   <li>Migration policy (automatic, manual, scheduled)</li>
 *   <li>Migration paths between versions</li>
 *   <li>State transformation rules</li>
 *   <li>Rollback behavior</li>
 *   <li>Batch processing configuration</li>
 * </ul>
 *
 * <p>Design Pattern: Strategy pattern for pluggable migration behaviors per workflow.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * // Define strategy for order-processing workflow
 * IWorkflowMigrationStrategy strategy = WorkflowMigrationStrategy.builder()
 *     .workflowId("order-processing")
 *     .migrationPolicy(MigrationPolicy.OPPORTUNISTIC)
 *     .addMigrationPath(MigrationPath.from("1.0.0").to("1.1.0").automatic())
 *     .addMigrationPath(MigrationPath.from("1.1.0").to("2.0.0").requiresApproval())
 *     .addStateTransformer(orderDataTransformer)
 *     .rollbackEnabled(true)
 *     .build();
 * }</pre>
 */
public interface IWorkflowMigrationStrategy {

    /**
     * Migration policy determining when migrations are executed.
     */
    enum MigrationPolicy {
        /**
         * Never migrate automatically - executions stay on original version.
         */
        FROZEN,

        /**
         * Migrate at safe points during execution.
         */
        OPPORTUNISTIC,

        /**
         * Migrate immediately at next step boundary.
         */
        IMMEDIATE,

        /**
         * Require manual approval before migration.
         */
        MANUAL_APPROVAL,

        /**
         * Migrate on a scheduled basis (batch processing).
         */
        SCHEDULED,

        /**
         * Run shadow execution on both versions for validation.
         */
        SHADOW
    }

    /**
     * Rollback policy when migration fails.
     */
    enum RollbackPolicy {
        /**
         * Automatically rollback to previous state on failure.
         */
        AUTOMATIC,

        /**
         * Pause execution and wait for manual intervention.
         */
        MANUAL,

        /**
         * Fail the execution without rollback.
         */
        FAIL_FAST,

        /**
         * Retry migration with exponential backoff.
         */
        RETRY
    }

    /**
     * Returns the workflow ID this strategy applies to.
     *
     * @return workflow identifier
     */
    String getWorkflowId();

    /**
     * Returns the migration policy for this workflow.
     *
     * @return migration policy
     */
    MigrationPolicy getMigrationPolicy();

    /**
     * Returns the rollback policy for failed migrations.
     *
     * @return rollback policy
     */
    RollbackPolicy getRollbackPolicy();

    /**
     * Returns whether migration is enabled for this workflow.
     *
     * @return true if migration is enabled
     */
    boolean isMigrationEnabled();

    /**
     * Returns the defined migration paths for this workflow.
     *
     * @return list of migration paths
     */
    List<IMigrationPath> getMigrationPaths();

    /**
     * Finds the best migration path from source to target version.
     *
     * @param sourceVersion current version
     * @param targetVersion desired version
     * @return best migration path if one exists
     */
    Optional<IMigrationPath> findMigrationPath(IWorkflowVersion sourceVersion, IWorkflowVersion targetVersion);

    /**
     * Returns all direct paths from a source version.
     *
     * @param sourceVersion source version
     * @return list of paths from this version
     */
    List<IMigrationPath> getPathsFrom(IWorkflowVersion sourceVersion);

    /**
     * Computes the complete migration chain from source to target.
     * May involve multiple intermediate versions.
     *
     * @param sourceVersion starting version
     * @param targetVersion ending version
     * @return ordered list of migration paths forming the chain
     */
    List<IMigrationPath> computeMigrationChain(IWorkflowVersion sourceVersion, IWorkflowVersion targetVersion);

    /**
     * Returns the registered state transformers for this workflow.
     *
     * @return map of transformer name to transformer
     */
    Map<String, IStateTransformer> getStateTransformers();

    /**
     * Gets a state transformer by name.
     *
     * @param name transformer name
     * @return transformer if found
     */
    Optional<IStateTransformer> getStateTransformer(String name);

    /**
     * Returns versions that are marked as "stable" for migration.
     * Migrations typically target stable versions.
     *
     * @return set of stable versions
     */
    Set<IWorkflowVersion> getStableVersions();

    /**
     * Returns versions that should be skipped during migration chains.
     *
     * @return set of skip versions
     */
    Set<IWorkflowVersion> getSkipVersions();

    /**
     * Returns the maximum number of version hops allowed in a migration chain.
     *
     * @return max hop count
     */
    int getMaxMigrationHops();

    /**
     * Returns the timeout for individual migration operations.
     *
     * @return migration timeout
     */
    Duration getMigrationTimeout();

    /**
     * Returns the maximum retry count for failed migrations.
     *
     * @return max retries
     */
    int getMaxRetries();

    /**
     * Returns the delay between retry attempts.
     *
     * @return retry delay
     */
    Duration getRetryDelay();

    /**
     * Returns whether to validate migration before execution.
     *
     * @return true if pre-validation is required
     */
    boolean isPreValidationRequired();

    /**
     * Returns whether to create checkpoints during migration.
     *
     * @return true if checkpointing is enabled
     */
    boolean isCheckpointingEnabled();

    /**
     * Returns the batch size for scheduled migrations.
     *
     * @return batch size
     */
    int getBatchSize();

    /**
     * Returns whether dry-run mode is enabled by default.
     *
     * @return true if dry-run is default
     */
    boolean isDryRunDefault();

    /**
     * Returns custom metadata for this strategy.
     *
     * @return metadata map
     */
    Map<String, Object> getMetadata();

    /**
     * Validates this strategy configuration.
     *
     * @return validation result
     */
    StrategyValidationResult validate();

    /**
     * Result of strategy validation.
     */
    record StrategyValidationResult(
            boolean isValid,
            List<String> errors,
            List<String> warnings
    ) {
        public static StrategyValidationResult valid() {
            return new StrategyValidationResult(true, List.of(), List.of());
        }

        public static StrategyValidationResult invalid(List<String> errors) {
            return new StrategyValidationResult(false, errors, List.of());
        }

        public static StrategyValidationResult withWarnings(List<String> warnings) {
            return new StrategyValidationResult(true, List.of(), warnings);
        }
    }
}
