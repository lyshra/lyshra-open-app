package com.lyshra.open.app.integration.models.version;

import com.lyshra.open.app.integration.contract.version.IWorkflowMigrationHints;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Implementation of migration hints for workflow version upgrades.
 * Provides mappings and guidance for the migration process.
 */
@Data
@Builder
public class WorkflowMigrationHints implements IWorkflowMigrationHints, Serializable {

    private static final long serialVersionUID = 1L;

    @Builder.Default
    private final Map<String, String> stepNameMappings = new HashMap<>();
    @Builder.Default
    private final Map<String, Set<String>> stepSplitMappings = new HashMap<>();
    @Builder.Default
    private final Map<String, Set<String>> stepMergeMappings = new HashMap<>();
    @Builder.Default
    private final Map<String, String> suggestedMigrationSteps = new HashMap<>();
    @Builder.Default
    private final Map<String, String> contextFieldMappings = new HashMap<>();
    @Builder.Default
    private final Map<String, String> variableMappings = new HashMap<>();
    @Builder.Default
    private final Set<String> safeMigrationPoints = new HashSet<>();
    @Builder.Default
    private final Set<String> blockedMigrationPoints = new HashSet<>();
    private final String migrationNotes;

    @Override
    public Map<String, String> getStepNameMappings() {
        return Collections.unmodifiableMap(stepNameMappings);
    }

    @Override
    public Map<String, Set<String>> getStepSplitMappings() {
        return Collections.unmodifiableMap(stepSplitMappings);
    }

    @Override
    public Map<String, Set<String>> getStepMergeMappings() {
        return Collections.unmodifiableMap(stepMergeMappings);
    }

    @Override
    public Optional<String> getSuggestedMigrationStep(String currentStepName) {
        // Check direct mapping first
        String mapped = stepNameMappings.get(currentStepName);
        if (mapped != null) {
            return Optional.of(mapped);
        }
        // Check suggested migration steps
        return Optional.ofNullable(suggestedMigrationSteps.get(currentStepName));
    }

    @Override
    public Map<String, String> getContextFieldMappings() {
        return Collections.unmodifiableMap(contextFieldMappings);
    }

    @Override
    public Map<String, String> getVariableMappings() {
        return Collections.unmodifiableMap(variableMappings);
    }

    @Override
    public Set<String> getSafeMigrationPoints() {
        return Collections.unmodifiableSet(safeMigrationPoints);
    }

    @Override
    public Set<String> getBlockedMigrationPoints() {
        return Collections.unmodifiableSet(blockedMigrationPoints);
    }

    @Override
    public Optional<String> getMigrationNotes() {
        return Optional.ofNullable(migrationNotes);
    }

    /**
     * Creates empty migration hints.
     *
     * @return empty hints
     */
    public static WorkflowMigrationHints empty() {
        return WorkflowMigrationHints.builder().build();
    }

    /**
     * Builder extension for fluent API.
     */
    public static class WorkflowMigrationHintsBuilder {

        public WorkflowMigrationHintsBuilder mapStep(String oldName, String newName) {
            if (this.stepNameMappings$value == null) {
                this.stepNameMappings$value = new HashMap<>();
                this.stepNameMappings$set = true;
            }
            this.stepNameMappings$value.put(oldName, newName);
            return this;
        }

        public WorkflowMigrationHintsBuilder splitStep(String oldName, Set<String> newNames) {
            if (this.stepSplitMappings$value == null) {
                this.stepSplitMappings$value = new HashMap<>();
                this.stepSplitMappings$set = true;
            }
            this.stepSplitMappings$value.put(oldName, newNames);
            return this;
        }

        public WorkflowMigrationHintsBuilder mergeSteps(String newName, Set<String> oldNames) {
            if (this.stepMergeMappings$value == null) {
                this.stepMergeMappings$value = new HashMap<>();
                this.stepMergeMappings$set = true;
            }
            this.stepMergeMappings$value.put(newName, oldNames);
            return this;
        }

        public WorkflowMigrationHintsBuilder mapContextField(String oldPath, String newPath) {
            if (this.contextFieldMappings$value == null) {
                this.contextFieldMappings$value = new HashMap<>();
                this.contextFieldMappings$set = true;
            }
            this.contextFieldMappings$value.put(oldPath, newPath);
            return this;
        }

        public WorkflowMigrationHintsBuilder mapVariable(String oldName, String newName) {
            if (this.variableMappings$value == null) {
                this.variableMappings$value = new HashMap<>();
                this.variableMappings$set = true;
            }
            this.variableMappings$value.put(oldName, newName);
            return this;
        }

        public WorkflowMigrationHintsBuilder addSafeMigrationPoint(String stepName) {
            if (this.safeMigrationPoints$value == null) {
                this.safeMigrationPoints$value = new HashSet<>();
                this.safeMigrationPoints$set = true;
            }
            this.safeMigrationPoints$value.add(stepName);
            return this;
        }

        public WorkflowMigrationHintsBuilder addBlockedMigrationPoint(String stepName) {
            if (this.blockedMigrationPoints$value == null) {
                this.blockedMigrationPoints$value = new HashSet<>();
                this.blockedMigrationPoints$set = true;
            }
            this.blockedMigrationPoints$value.add(stepName);
            return this;
        }

        public WorkflowMigrationHintsBuilder suggestMigrationStep(String fromStep, String toStep) {
            if (this.suggestedMigrationSteps$value == null) {
                this.suggestedMigrationSteps$value = new HashMap<>();
                this.suggestedMigrationSteps$set = true;
            }
            this.suggestedMigrationSteps$value.put(fromStep, toStep);
            return this;
        }
    }
}
