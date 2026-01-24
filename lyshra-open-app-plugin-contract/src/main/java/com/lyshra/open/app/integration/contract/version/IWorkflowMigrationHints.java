package com.lyshra.open.app.integration.contract.version;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Provides hints for workflow migration between versions.
 * Contains step mappings, field transformations, and migration notes.
 */
public interface IWorkflowMigrationHints {

    /**
     * Returns step name mappings from old names to new names.
     * Used when steps are renamed between versions.
     *
     * @return map of old step name to new step name
     */
    Map<String, String> getStepNameMappings();

    /**
     * Returns mappings for steps that were split into multiple steps.
     * Key is old step name, value is set of new step names.
     *
     * @return step split mappings
     */
    Map<String, Set<String>> getStepSplitMappings();

    /**
     * Returns mappings for steps that were merged from multiple steps.
     * Key is new step name, value is set of old step names.
     *
     * @return step merge mappings
     */
    Map<String, Set<String>> getStepMergeMappings();

    /**
     * Returns the suggested entry point step when migrating an in-flight execution.
     * If not specified, migration handler determines the appropriate step.
     *
     * @param currentStepName the current step in the old version
     * @return suggested target step in new version
     */
    Optional<String> getSuggestedMigrationStep(String currentStepName);

    /**
     * Returns context data field mappings for transformation.
     * Key is old field path, value is new field path.
     *
     * @return context field mappings
     */
    Map<String, String> getContextFieldMappings();

    /**
     * Returns variable name mappings for transformation.
     * Key is old variable name, value is new variable name.
     *
     * @return variable mappings
     */
    Map<String, String> getVariableMappings();

    /**
     * Returns set of steps that are safe migration points.
     * In-flight executions should preferably be migrated at these steps.
     *
     * @return safe migration step names
     */
    Set<String> getSafeMigrationPoints();

    /**
     * Returns set of steps where migration should never occur.
     * These steps have critical state that would be corrupted by migration.
     *
     * @return blocked migration step names
     */
    Set<String> getBlockedMigrationPoints();

    /**
     * Returns human-readable notes about migration considerations.
     *
     * @return migration notes
     */
    Optional<String> getMigrationNotes();
}
