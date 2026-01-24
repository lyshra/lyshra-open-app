package com.lyshra.open.app.integration.contract.version.migration;

import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Defines a migration path between two workflow versions.
 * A migration path specifies how to transform state from source to target version.
 *
 * <p>Migration paths can be:</p>
 * <ul>
 *   <li>Direct - single hop from source to target</li>
 *   <li>Chained - multiple intermediate versions</li>
 *   <li>Conditional - based on execution state</li>
 * </ul>
 *
 * <p>Design Pattern: Builder pattern for fluent path construction.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * // Simple direct path
 * IMigrationPath path = MigrationPath.from("1.0.0").to("1.1.0")
 *     .withStepMapping("oldStep", "newStep")
 *     .withVariableMapping("oldVar", "newVar")
 *     .build();
 *
 * // Path requiring approval
 * IMigrationPath path = MigrationPath.from("1.x.x").to("2.0.0")
 *     .requiresApproval()
 *     .withTransformer(customTransformer)
 *     .withSafePoints("checkout", "confirmation")
 *     .build();
 * }</pre>
 */
public interface IMigrationPath {

    /**
     * Path type classification.
     */
    enum PathType {
        /**
         * Automatic migration without intervention.
         */
        AUTOMATIC,

        /**
         * Requires manual approval before migration.
         */
        REQUIRES_APPROVAL,

        /**
         * Migration is blocked - not allowed.
         */
        BLOCKED,

        /**
         * Deprecated path - will be removed.
         */
        DEPRECATED
    }

    /**
     * Returns the source version pattern.
     * Can be exact version or pattern like "1.x.x".
     *
     * @return source version pattern
     */
    String getSourceVersionPattern();

    /**
     * Returns the target version.
     *
     * @return target version
     */
    IWorkflowVersion getTargetVersion();

    /**
     * Returns the path type.
     *
     * @return path type
     */
    PathType getPathType();

    /**
     * Returns the priority of this path (higher = more preferred).
     *
     * @return priority value
     */
    int getPriority();

    /**
     * Checks if a source version matches this path's pattern.
     *
     * @param version version to check
     * @return true if version matches
     */
    boolean matchesSource(IWorkflowVersion version);

    /**
     * Returns step mappings from source to target version.
     *
     * @return map of old step name to new step name
     */
    Map<String, String> getStepMappings();

    /**
     * Returns variable mappings from source to target version.
     *
     * @return map of old variable name to new variable name
     */
    Map<String, String> getVariableMappings();

    /**
     * Returns context field mappings from source to target version.
     *
     * @return map of old field path to new field path
     */
    Map<String, String> getContextFieldMappings();

    /**
     * Returns steps that are safe migration points for this path.
     *
     * @return set of safe step names
     */
    Set<String> getSafePoints();

    /**
     * Returns steps where migration is blocked for this path.
     *
     * @return set of blocked step names
     */
    Set<String> getBlockedPoints();

    /**
     * Returns the state transformers to apply for this path.
     *
     * @return list of transformer names
     */
    List<String> getTransformerNames();

    /**
     * Returns the custom migration handler class for this path.
     *
     * @return handler class name if specified
     */
    Optional<String> getCustomHandlerClass();

    /**
     * Returns the estimated data loss risk for this migration path.
     * Value between 0.0 (no risk) and 1.0 (total data loss).
     *
     * @return risk level
     */
    double getDataLossRisk();

    /**
     * Returns whether this path requires context backup before migration.
     *
     * @return true if backup required
     */
    boolean requiresBackup();

    /**
     * Returns whether this path supports rollback.
     *
     * @return true if rollback supported
     */
    boolean supportsRollback();

    /**
     * Returns the timeout for this specific migration path.
     *
     * @return timeout if specified
     */
    Optional<Duration> getTimeout();

    /**
     * Returns preconditions that must be met for this path.
     *
     * @return list of precondition descriptions
     */
    List<String> getPreconditions();

    /**
     * Returns postconditions to verify after migration.
     *
     * @return list of postcondition descriptions
     */
    List<String> getPostconditions();

    /**
     * Returns the description of this migration path.
     *
     * @return path description
     */
    String getDescription();

    /**
     * Returns custom metadata for this path.
     *
     * @return metadata map
     */
    Map<String, Object> getMetadata();

    /**
     * Validates this path configuration.
     *
     * @return validation result
     */
    PathValidationResult validate();

    /**
     * Result of path validation.
     */
    record PathValidationResult(
            boolean isValid,
            List<String> errors,
            List<String> warnings
    ) {
        public static PathValidationResult valid() {
            return new PathValidationResult(true, List.of(), List.of());
        }

        public static PathValidationResult invalid(List<String> errors) {
            return new PathValidationResult(false, errors, List.of());
        }
    }
}
