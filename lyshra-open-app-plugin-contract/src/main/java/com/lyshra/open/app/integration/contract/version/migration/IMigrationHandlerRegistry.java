package com.lyshra.open.app.integration.contract.version.migration;

import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Registry for managing workflow migration handlers and strategies.
 * Provides centralized lookup and management of migration configurations
 * per workflow.
 *
 * <p>The registry supports:</p>
 * <ul>
 *   <li>Per-workflow migration strategy registration</li>
 *   <li>Migration handler registration and lookup</li>
 *   <li>State transformer registration and lookup</li>
 *   <li>Migration path computation</li>
 * </ul>
 *
 * <p>Design Pattern: Registry pattern for centralized handler management.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * // Register workflow-specific strategy
 * registry.registerStrategy(orderProcessingStrategy);
 *
 * // Register custom handler
 * registry.registerHandler(orderMigrationHandler);
 *
 * // Register state transformer
 * registry.registerTransformer(orderDataTransformer);
 *
 * // Find best handler for migration
 * Optional<IMigrationHandler> handler = registry.findHandler(context);
 * }</pre>
 */
public interface IMigrationHandlerRegistry {

    /**
     * Registers a migration strategy for a workflow.
     *
     * @param strategy workflow migration strategy
     */
    void registerStrategy(IWorkflowMigrationStrategy strategy);

    /**
     * Unregisters a migration strategy for a workflow.
     *
     * @param workflowId workflow identifier
     */
    void unregisterStrategy(String workflowId);

    /**
     * Gets the migration strategy for a workflow.
     *
     * @param workflowId workflow identifier
     * @return strategy if registered
     */
    Optional<IWorkflowMigrationStrategy> getStrategy(String workflowId);

    /**
     * Gets all registered strategies.
     *
     * @return collection of all strategies
     */
    Collection<IWorkflowMigrationStrategy> getAllStrategies();

    /**
     * Registers a migration handler.
     *
     * @param handler migration handler
     */
    void registerHandler(IMigrationHandler handler);

    /**
     * Unregisters a migration handler.
     *
     * @param workflowId workflow identifier
     * @param sourceVersionPattern source version pattern
     * @param targetVersion target version
     */
    void unregisterHandler(String workflowId, String sourceVersionPattern, IWorkflowVersion targetVersion);

    /**
     * Gets all handlers for a workflow.
     *
     * @param workflowId workflow identifier
     * @return list of handlers sorted by priority
     */
    List<IMigrationHandler> getHandlers(String workflowId);

    /**
     * Finds the best handler for a migration context.
     *
     * @param context migration context
     * @return best matching handler if available
     */
    Optional<IMigrationHandler> findHandler(IMigrationContext context);

    /**
     * Finds all applicable handlers for a migration context.
     *
     * @param context migration context
     * @return list of applicable handlers sorted by priority
     */
    List<IMigrationHandler> findApplicableHandlers(IMigrationContext context);

    /**
     * Registers a state transformer.
     *
     * @param transformer state transformer
     */
    void registerTransformer(IStateTransformer transformer);

    /**
     * Unregisters a state transformer.
     *
     * @param name transformer name
     */
    void unregisterTransformer(String name);

    /**
     * Gets a state transformer by name.
     *
     * @param name transformer name
     * @return transformer if registered
     */
    Optional<IStateTransformer> getTransformer(String name);

    /**
     * Gets all transformers applicable to a migration context.
     *
     * @param context migration context
     * @return list of applicable transformers sorted by priority
     */
    List<IStateTransformer> getApplicableTransformers(IMigrationContext context);

    /**
     * Gets all registered transformers.
     *
     * @return collection of all transformers
     */
    Collection<IStateTransformer> getAllTransformers();

    /**
     * Registers a migration path for a workflow.
     *
     * @param workflowId workflow identifier
     * @param path migration path
     */
    void registerPath(String workflowId, IMigrationPath path);

    /**
     * Unregisters a migration path.
     *
     * @param workflowId workflow identifier
     * @param sourceVersionPattern source version pattern
     * @param targetVersion target version
     */
    void unregisterPath(String workflowId, String sourceVersionPattern, IWorkflowVersion targetVersion);

    /**
     * Gets all migration paths for a workflow.
     *
     * @param workflowId workflow identifier
     * @return list of migration paths
     */
    List<IMigrationPath> getPaths(String workflowId);

    /**
     * Finds a direct migration path from source to target version.
     *
     * @param workflowId workflow identifier
     * @param sourceVersion source version
     * @param targetVersion target version
     * @return direct path if available
     */
    Optional<IMigrationPath> findDirectPath(
            String workflowId,
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion);

    /**
     * Computes the migration chain from source to target version.
     * May involve multiple intermediate paths.
     *
     * @param workflowId workflow identifier
     * @param sourceVersion source version
     * @param targetVersion target version
     * @return list of paths forming the migration chain
     */
    List<IMigrationPath> computeMigrationChain(
            String workflowId,
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion);

    /**
     * Checks if a migration path exists from source to target.
     *
     * @param workflowId workflow identifier
     * @param sourceVersion source version
     * @param targetVersion target version
     * @return true if path exists
     */
    boolean hasPath(String workflowId, IWorkflowVersion sourceVersion, IWorkflowVersion targetVersion);

    /**
     * Gets all workflow IDs with registered migration configurations.
     *
     * @return set of workflow IDs
     */
    Set<String> getRegisteredWorkflowIds();

    /**
     * Validates the migration configuration for a workflow.
     *
     * @param workflowId workflow identifier
     * @return validation result
     */
    RegistryValidationResult validateWorkflowConfiguration(String workflowId);

    /**
     * Validates all registered configurations.
     *
     * @return validation result
     */
    RegistryValidationResult validateAll();

    /**
     * Clears all registrations for a workflow.
     *
     * @param workflowId workflow identifier
     */
    void clearWorkflow(String workflowId);

    /**
     * Clears all registrations.
     */
    void clearAll();

    /**
     * Gets registry statistics.
     *
     * @return statistics
     */
    RegistryStats getStats();

    /**
     * Result of registry validation.
     */
    record RegistryValidationResult(
            boolean isValid,
            List<ValidationIssue> issues
    ) {
        public static RegistryValidationResult valid() {
            return new RegistryValidationResult(true, List.of());
        }

        public static RegistryValidationResult invalid(List<ValidationIssue> issues) {
            return new RegistryValidationResult(false, issues);
        }

        public List<ValidationIssue> getErrors() {
            return issues.stream()
                    .filter(i -> i.severity() == ValidationIssue.Severity.ERROR)
                    .toList();
        }

        public List<ValidationIssue> getWarnings() {
            return issues.stream()
                    .filter(i -> i.severity() == ValidationIssue.Severity.WARNING)
                    .toList();
        }
    }

    /**
     * A validation issue found in the registry.
     */
    record ValidationIssue(
            Severity severity,
            String workflowId,
            String component,
            String message
    ) {
        public enum Severity {
            ERROR,
            WARNING,
            INFO
        }

        public static ValidationIssue error(String workflowId, String component, String message) {
            return new ValidationIssue(Severity.ERROR, workflowId, component, message);
        }

        public static ValidationIssue warning(String workflowId, String component, String message) {
            return new ValidationIssue(Severity.WARNING, workflowId, component, message);
        }

        public static ValidationIssue info(String workflowId, String component, String message) {
            return new ValidationIssue(Severity.INFO, workflowId, component, message);
        }
    }

    /**
     * Registry statistics.
     */
    record RegistryStats(
            int totalWorkflows,
            int totalStrategies,
            int totalHandlers,
            int totalTransformers,
            int totalPaths
    ) {}
}
