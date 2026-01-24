package com.lyshra.open.app.integration.contract.version;

import java.util.Set;

/**
 * Defines compatibility rules between workflow versions.
 * Used to determine safe migration paths and execution behavior.
 *
 * <p>Compatibility Levels:</p>
 * <ul>
 *   <li>FULL: Complete backward compatibility - no migration needed</li>
 *   <li>FORWARD: Forward compatible - older clients can work with newer data</li>
 *   <li>BACKWARD: Backward compatible - newer clients can work with older data</li>
 *   <li>NONE: Breaking change - explicit migration required</li>
 * </ul>
 */
public interface IVersionCompatibility {

    /**
     * Returns the compatibility level for this version.
     *
     * @return compatibility level
     */
    CompatibilityLevel getLevel();

    /**
     * Returns the minimum version this version is backward compatible with.
     * Executions on versions >= minimumCompatibleVersion can be safely migrated.
     *
     * @return minimum compatible version
     */
    IWorkflowVersion getMinimumCompatibleVersion();

    /**
     * Returns the set of versions that can be directly migrated to this version.
     * If empty, uses minimumCompatibleVersion for range-based compatibility.
     *
     * @return set of explicitly compatible versions
     */
    Set<IWorkflowVersion> getExplicitlyCompatibleVersions();

    /**
     * Returns the set of versions that are explicitly incompatible.
     * Migration from these versions requires special handling.
     *
     * @return set of incompatible versions
     */
    Set<IWorkflowVersion> getIncompatibleVersions();

    /**
     * Indicates if context data migration is required when upgrading.
     *
     * @return true if context migration needed
     */
    boolean requiresContextMigration();

    /**
     * Indicates if variable migration is required when upgrading.
     *
     * @return true if variable migration needed
     */
    boolean requiresVariableMigration();

    /**
     * Checks if the given version is compatible for migration.
     *
     * @param fromVersion source version
     * @return true if compatible
     */
    boolean isCompatibleWith(IWorkflowVersion fromVersion);

    /**
     * Compatibility level enumeration.
     */
    enum CompatibilityLevel {
        /**
         * Fully backward and forward compatible.
         * No migration logic required.
         */
        FULL,

        /**
         * Only backward compatible.
         * Older versions can migrate to this version.
         */
        BACKWARD,

        /**
         * Only forward compatible.
         * This version can work with data from newer versions.
         */
        FORWARD,

        /**
         * Breaking change.
         * Explicit migration logic required.
         */
        NONE
    }
}
