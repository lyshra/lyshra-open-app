package com.lyshra.open.app.integration.models.version;

import com.lyshra.open.app.integration.contract.version.IVersionCompatibility;
import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowMigrationHints;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersionMetadata;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStep;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppWorkflowContextRetention;
import com.lyshra.open.app.integration.models.workflows.step.LyshraOpenAppWorkflowStep;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Versioned workflow definition that extends the base workflow with version metadata.
 * Supports the full lifecycle of versioned workflow management.
 *
 * <p>This implementation provides complete version tracking including:</p>
 * <ul>
 *   <li>workflowId - stable identifier across versions</li>
 *   <li>version - semantic version (MAJOR.MINOR.PATCH)</li>
 *   <li>schemaHash - SHA-256 hash of workflow definition for integrity</li>
 *   <li>createdAt - timestamp of version creation</li>
 *   <li>isActive - flag indicating if version accepts new executions</li>
 * </ul>
 *
 * <p>Design Pattern: Builder pattern with type-safe fluent API.</p>
 */
@Data
public class VersionedWorkflowDefinition implements IVersionedWorkflow, Serializable {

    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_SCHEMA_VERSION = "1.0.0";

    // Core identification fields
    private final String workflowId;
    private final String name;
    private final String startStep;
    private final LyshraOpenAppWorkflowContextRetention contextRetention;
    private final Map<String, ILyshraOpenAppWorkflowStep> steps;

    // Version tracking fields (as per requirements)
    private final IWorkflowVersion version;
    private final String schemaHash;
    private final Instant createdAt;
    private final boolean active;

    // Additional version metadata
    private final IVersionCompatibility compatibility;
    private final String schemaVersion;
    private final Map<String, Object> metadata;
    private final Set<String> addedSteps;
    private final Set<String> removedSteps;
    private final Set<String> modifiedSteps;
    private final IWorkflowMigrationHints migrationHints;
    private final IWorkflowVersionMetadata versionMetadata;

    private VersionedWorkflowDefinition(
            String workflowId,
            String name,
            String startStep,
            LyshraOpenAppWorkflowContextRetention contextRetention,
            Map<String, ILyshraOpenAppWorkflowStep> steps,
            IWorkflowVersion version,
            String schemaHash,
            Instant createdAt,
            boolean active,
            IVersionCompatibility compatibility,
            String schemaVersion,
            Map<String, Object> metadata,
            Set<String> addedSteps,
            Set<String> removedSteps,
            Set<String> modifiedSteps,
            IWorkflowMigrationHints migrationHints,
            IWorkflowVersionMetadata versionMetadata) {
        this.workflowId = workflowId;
        this.name = name;
        this.startStep = startStep;
        this.contextRetention = contextRetention;
        this.steps = Collections.unmodifiableMap(steps);
        this.version = version;
        this.schemaHash = schemaHash;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.active = active;
        this.compatibility = compatibility;
        this.schemaVersion = schemaVersion;
        this.metadata = Collections.unmodifiableMap(metadata);
        this.addedSteps = Collections.unmodifiableSet(addedSteps);
        this.removedSteps = Collections.unmodifiableSet(removedSteps);
        this.modifiedSteps = Collections.unmodifiableSet(modifiedSteps);
        this.migrationHints = migrationHints;
        this.versionMetadata = versionMetadata;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public Optional<IWorkflowVersionMetadata> getVersionMetadata() {
        return Optional.ofNullable(versionMetadata);
    }

    @Override
    public Optional<IWorkflowMigrationHints> getMigrationHints() {
        return Optional.ofNullable(migrationHints);
    }

    @Override
    public boolean supportsMigrationFrom(IWorkflowVersion fromVersion) {
        if (compatibility == null) {
            return false;
        }
        return compatibility.isCompatibleWith(fromVersion);
    }

    /**
     * Creates a copy of this workflow definition with a different version.
     * Useful for version conflict resolution.
     *
     * @param newVersion the new version to use
     * @return new workflow definition with updated version
     */
    public VersionedWorkflowDefinition withVersion(IWorkflowVersion newVersion) {
        return new VersionedWorkflowDefinition(
                this.workflowId,
                this.name,
                this.startStep,
                this.contextRetention,
                new LinkedHashMap<>(this.steps),
                newVersion,
                null, // Schema hash will need to be recomputed
                Instant.now(),
                this.active,
                this.compatibility,
                this.schemaVersion,
                new HashMap<>(this.metadata),
                new HashSet<>(this.addedSteps),
                new HashSet<>(this.removedSteps),
                new HashSet<>(this.modifiedSteps),
                this.migrationHints,
                this.versionMetadata
        );
    }

    public static WorkflowIdStep builder() {
        return new Builder();
    }

    // Type-safe builder interfaces
    public interface WorkflowIdStep { NameStep workflowId(String workflowId); }
    public interface NameStep { VersionStep name(String name); }
    public interface VersionStep { StartStepStep version(IWorkflowVersion version); }
    public interface StartStepStep { ContextRetentionStep startStep(String startStep); }
    public interface ContextRetentionStep { StepsStep contextRetention(LyshraOpenAppWorkflowContextRetention contextRetention); }
    public interface StepsStep { CompatibilityStep steps(Function<StepsBuilder, StepsBuilder> fn); }
    public interface CompatibilityStep { OptionalStep compatibility(IVersionCompatibility compatibility); }
    public interface OptionalStep extends BuildStep {
        OptionalStep schemaVersion(String schemaVersion);
        OptionalStep schemaHash(String schemaHash);
        OptionalStep createdAt(Instant createdAt);
        OptionalStep active(boolean active);
        OptionalStep metadata(Map<String, Object> metadata);
        OptionalStep addMetadata(String key, Object value);
        OptionalStep addedSteps(Set<String> addedSteps);
        OptionalStep removedSteps(Set<String> removedSteps);
        OptionalStep modifiedSteps(Set<String> modifiedSteps);
        OptionalStep migrationHints(IWorkflowMigrationHints hints);
        OptionalStep versionMetadata(IWorkflowVersionMetadata versionMetadata);
    }
    public interface BuildStep { VersionedWorkflowDefinition build(); }

    /**
     * Builder for workflow steps.
     */
    public static class StepsBuilder {
        private final Map<String, ILyshraOpenAppWorkflowStep> map = new LinkedHashMap<>();

        public StepsBuilder step(Function<LyshraOpenAppWorkflowStep.InitialStepBuilder, LyshraOpenAppWorkflowStep.BuildStep> fn) {
            LyshraOpenAppWorkflowStep step = fn.apply(LyshraOpenAppWorkflowStep.builder()).build();
            map.put(step.getName(), step);
            return this;
        }

        public StepsBuilder step(ILyshraOpenAppWorkflowStep step) {
            map.put(step.getName(), step);
            return this;
        }

        Map<String, ILyshraOpenAppWorkflowStep> build() {
            return map;
        }
    }

    private static class Builder implements WorkflowIdStep, NameStep, VersionStep, StartStepStep,
            ContextRetentionStep, StepsStep, CompatibilityStep, OptionalStep {

        private String workflowId;
        private String name;
        private IWorkflowVersion version;
        private String startStep;
        private LyshraOpenAppWorkflowContextRetention contextRetention;
        private Map<String, ILyshraOpenAppWorkflowStep> steps;
        private String schemaHash;
        private Instant createdAt;
        private boolean active = true;
        private IVersionCompatibility compatibility;
        private String schemaVersion = DEFAULT_SCHEMA_VERSION;
        private Map<String, Object> metadata = new HashMap<>();
        private Set<String> addedSteps = new HashSet<>();
        private Set<String> removedSteps = new HashSet<>();
        private Set<String> modifiedSteps = new HashSet<>();
        private IWorkflowMigrationHints migrationHints;
        private IWorkflowVersionMetadata versionMetadata;

        @Override
        public NameStep workflowId(String workflowId) {
            this.workflowId = workflowId;
            return this;
        }

        @Override
        public VersionStep name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public StartStepStep version(IWorkflowVersion version) {
            this.version = version;
            return this;
        }

        @Override
        public ContextRetentionStep startStep(String startStep) {
            this.startStep = startStep;
            return this;
        }

        @Override
        public StepsStep contextRetention(LyshraOpenAppWorkflowContextRetention contextRetention) {
            this.contextRetention = contextRetention;
            return this;
        }

        @Override
        public CompatibilityStep steps(Function<StepsBuilder, StepsBuilder> fn) {
            StepsBuilder sb = fn.apply(new StepsBuilder());
            this.steps = sb.build();
            return this;
        }

        @Override
        public OptionalStep compatibility(IVersionCompatibility compatibility) {
            this.compatibility = compatibility;
            return this;
        }

        @Override
        public OptionalStep schemaVersion(String schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        @Override
        public OptionalStep schemaHash(String schemaHash) {
            this.schemaHash = schemaHash;
            return this;
        }

        @Override
        public OptionalStep createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        @Override
        public OptionalStep active(boolean active) {
            this.active = active;
            return this;
        }

        @Override
        public OptionalStep metadata(Map<String, Object> metadata) {
            this.metadata = new HashMap<>(metadata);
            return this;
        }

        @Override
        public OptionalStep addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        @Override
        public OptionalStep addedSteps(Set<String> addedSteps) {
            this.addedSteps = new HashSet<>(addedSteps);
            return this;
        }

        @Override
        public OptionalStep removedSteps(Set<String> removedSteps) {
            this.removedSteps = new HashSet<>(removedSteps);
            return this;
        }

        @Override
        public OptionalStep modifiedSteps(Set<String> modifiedSteps) {
            this.modifiedSteps = new HashSet<>(modifiedSteps);
            return this;
        }

        @Override
        public OptionalStep migrationHints(IWorkflowMigrationHints hints) {
            this.migrationHints = hints;
            return this;
        }

        @Override
        public OptionalStep versionMetadata(IWorkflowVersionMetadata versionMetadata) {
            this.versionMetadata = versionMetadata;
            return this;
        }

        @Override
        public VersionedWorkflowDefinition build() {
            return new VersionedWorkflowDefinition(
                    workflowId,
                    name,
                    startStep,
                    contextRetention,
                    steps,
                    version,
                    schemaHash,
                    createdAt,
                    active,
                    compatibility,
                    schemaVersion,
                    metadata,
                    addedSteps,
                    removedSteps,
                    modifiedSteps,
                    migrationHints,
                    versionMetadata
            );
        }
    }
}
