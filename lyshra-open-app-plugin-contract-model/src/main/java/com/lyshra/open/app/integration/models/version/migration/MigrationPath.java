package com.lyshra.open.app.integration.models.version.migration;

import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationPath;
import com.lyshra.open.app.integration.models.version.WorkflowVersion;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Implementation of migration path between workflow versions.
 * Defines how to transform state from source to target version.
 */
@Data
@Builder
public class MigrationPath implements IMigrationPath, Serializable {

    private static final long serialVersionUID = 1L;

    private final String sourceVersionPattern;
    private final IWorkflowVersion targetVersion;

    @Builder.Default
    private final PathType pathType = PathType.AUTOMATIC;

    @Builder.Default
    private final int priority = 0;

    @Builder.Default
    private final Map<String, String> stepMappings = new HashMap<>();

    @Builder.Default
    private final Map<String, String> variableMappings = new HashMap<>();

    @Builder.Default
    private final Map<String, String> contextFieldMappings = new HashMap<>();

    @Builder.Default
    private final Set<String> safePoints = new HashSet<>();

    @Builder.Default
    private final Set<String> blockedPoints = new HashSet<>();

    @Builder.Default
    private final List<String> transformerNames = new ArrayList<>();

    private final String customHandlerClass;

    @Builder.Default
    private final double dataLossRisk = 0.0;

    @Builder.Default
    private final boolean requiresBackup = false;

    @Builder.Default
    private final boolean supportsRollback = true;

    private final Duration timeout;

    @Builder.Default
    private final List<String> preconditions = new ArrayList<>();

    @Builder.Default
    private final List<String> postconditions = new ArrayList<>();

    @Builder.Default
    private final String description = "";

    @Builder.Default
    private final Map<String, Object> metadata = new HashMap<>();

    // Cached regex pattern
    private transient Pattern compiledPattern;

    @Override
    public boolean matchesSource(IWorkflowVersion version) {
        if (sourceVersionPattern == null) {
            return false;
        }

        // Direct match
        if (sourceVersionPattern.equals(version.toVersionString())) {
            return true;
        }

        // Pattern match (e.g., "1.x.x", "1.*.*", "1.0.*")
        if (compiledPattern == null) {
            String regex = sourceVersionPattern
                    .replace(".", "\\.")
                    .replace("x", "\\d+")
                    .replace("*", ".*");
            compiledPattern = Pattern.compile("^" + regex + "$");
        }

        return compiledPattern.matcher(version.toVersionString()).matches();
    }

    @Override
    public Map<String, String> getStepMappings() {
        return Collections.unmodifiableMap(stepMappings);
    }

    @Override
    public Map<String, String> getVariableMappings() {
        return Collections.unmodifiableMap(variableMappings);
    }

    @Override
    public Map<String, String> getContextFieldMappings() {
        return Collections.unmodifiableMap(contextFieldMappings);
    }

    @Override
    public Set<String> getSafePoints() {
        return Collections.unmodifiableSet(safePoints);
    }

    @Override
    public Set<String> getBlockedPoints() {
        return Collections.unmodifiableSet(blockedPoints);
    }

    @Override
    public List<String> getTransformerNames() {
        return Collections.unmodifiableList(transformerNames);
    }

    @Override
    public Optional<String> getCustomHandlerClass() {
        return Optional.ofNullable(customHandlerClass);
    }

    @Override
    public boolean requiresBackup() {
        return requiresBackup;
    }

    @Override
    public boolean supportsRollback() {
        return supportsRollback;
    }

    @Override
    public Optional<Duration> getTimeout() {
        return Optional.ofNullable(timeout);
    }

    @Override
    public List<String> getPreconditions() {
        return Collections.unmodifiableList(preconditions);
    }

    @Override
    public List<String> getPostconditions() {
        return Collections.unmodifiableList(postconditions);
    }

    @Override
    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    @Override
    public PathValidationResult validate() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (sourceVersionPattern == null || sourceVersionPattern.isBlank()) {
            errors.add("Source version pattern is required");
        }

        if (targetVersion == null) {
            errors.add("Target version is required");
        }

        if (dataLossRisk < 0.0 || dataLossRisk > 1.0) {
            errors.add("Data loss risk must be between 0.0 and 1.0");
        }

        // Check for conflicting safe/blocked points
        Set<String> overlap = new HashSet<>(safePoints);
        overlap.retainAll(blockedPoints);
        if (!overlap.isEmpty()) {
            errors.add("Steps cannot be both safe and blocked: " + overlap);
        }

        // Warn about high risk without backup
        if (dataLossRisk > 0.5 && !requiresBackup) {
            warnings.add("High data loss risk but backup not required");
        }

        // Warn about blocked path type
        if (pathType == PathType.BLOCKED && !stepMappings.isEmpty()) {
            warnings.add("Blocked path has step mappings that will never be used");
        }

        if (!errors.isEmpty()) {
            return PathValidationResult.invalid(errors);
        }

        return PathValidationResult.valid();
    }

    /**
     * Builder extension for fluent API.
     */
    public static class MigrationPathBuilder {

        public MigrationPathBuilder addStepMapping(String sourceStep, String targetStep) {
            if (this.stepMappings$value == null) {
                this.stepMappings$value = new HashMap<>();
                this.stepMappings$set = true;
            }
            this.stepMappings$value.put(sourceStep, targetStep);
            return this;
        }

        public MigrationPathBuilder addVariableMapping(String sourceVar, String targetVar) {
            if (this.variableMappings$value == null) {
                this.variableMappings$value = new HashMap<>();
                this.variableMappings$set = true;
            }
            this.variableMappings$value.put(sourceVar, targetVar);
            return this;
        }

        public MigrationPathBuilder addContextFieldMapping(String sourceField, String targetField) {
            if (this.contextFieldMappings$value == null) {
                this.contextFieldMappings$value = new HashMap<>();
                this.contextFieldMappings$set = true;
            }
            this.contextFieldMappings$value.put(sourceField, targetField);
            return this;
        }

        public MigrationPathBuilder addSafePoint(String stepName) {
            if (this.safePoints$value == null) {
                this.safePoints$value = new HashSet<>();
                this.safePoints$set = true;
            }
            this.safePoints$value.add(stepName);
            return this;
        }

        public MigrationPathBuilder addBlockedPoint(String stepName) {
            if (this.blockedPoints$value == null) {
                this.blockedPoints$value = new HashSet<>();
                this.blockedPoints$set = true;
            }
            this.blockedPoints$value.add(stepName);
            return this;
        }

        public MigrationPathBuilder addTransformer(String transformerName) {
            if (this.transformerNames$value == null) {
                this.transformerNames$value = new ArrayList<>();
                this.transformerNames$set = true;
            }
            this.transformerNames$value.add(transformerName);
            return this;
        }

        public MigrationPathBuilder addPrecondition(String precondition) {
            if (this.preconditions$value == null) {
                this.preconditions$value = new ArrayList<>();
                this.preconditions$set = true;
            }
            this.preconditions$value.add(precondition);
            return this;
        }

        public MigrationPathBuilder addPostcondition(String postcondition) {
            if (this.postconditions$value == null) {
                this.postconditions$value = new ArrayList<>();
                this.postconditions$set = true;
            }
            this.postconditions$value.add(postcondition);
            return this;
        }

        public MigrationPathBuilder addMetadata(String key, Object value) {
            if (this.metadata$value == null) {
                this.metadata$value = new HashMap<>();
                this.metadata$set = true;
            }
            this.metadata$value.put(key, value);
            return this;
        }
    }

    /**
     * Creates a path builder starting from a source version pattern.
     *
     * @param sourcePattern source version pattern
     * @return path builder
     */
    public static PathBuilderStage from(String sourcePattern) {
        return new PathBuilderStage(sourcePattern);
    }

    /**
     * Creates a path builder starting from a source version.
     *
     * @param sourceVersion source version
     * @return path builder
     */
    public static PathBuilderStage from(IWorkflowVersion sourceVersion) {
        return new PathBuilderStage(sourceVersion.toVersionString());
    }

    /**
     * Intermediate builder stage for fluent API.
     */
    public static class PathBuilderStage {
        private final String sourcePattern;

        PathBuilderStage(String sourcePattern) {
            this.sourcePattern = sourcePattern;
        }

        /**
         * Sets the target version.
         *
         * @param targetVersion target version string
         * @return migration path builder
         */
        public MigrationPathBuilder to(String targetVersion) {
            return MigrationPath.builder()
                    .sourceVersionPattern(sourcePattern)
                    .targetVersion(WorkflowVersion.parse(targetVersion));
        }

        /**
         * Sets the target version.
         *
         * @param targetVersion target version
         * @return migration path builder
         */
        public MigrationPathBuilder to(IWorkflowVersion targetVersion) {
            return MigrationPath.builder()
                    .sourceVersionPattern(sourcePattern)
                    .targetVersion(targetVersion);
        }
    }

    /**
     * Creates an automatic migration path.
     */
    public static MigrationPath automatic(String sourcePattern, IWorkflowVersion targetVersion) {
        return MigrationPath.builder()
                .sourceVersionPattern(sourcePattern)
                .targetVersion(targetVersion)
                .pathType(PathType.AUTOMATIC)
                .build();
    }

    /**
     * Creates a path requiring approval.
     */
    public static MigrationPath requiresApproval(String sourcePattern, IWorkflowVersion targetVersion) {
        return MigrationPath.builder()
                .sourceVersionPattern(sourcePattern)
                .targetVersion(targetVersion)
                .pathType(PathType.REQUIRES_APPROVAL)
                .build();
    }

    /**
     * Creates a blocked migration path.
     */
    public static MigrationPath blocked(String sourcePattern, IWorkflowVersion targetVersion, String reason) {
        return MigrationPath.builder()
                .sourceVersionPattern(sourcePattern)
                .targetVersion(targetVersion)
                .pathType(PathType.BLOCKED)
                .description(reason)
                .build();
    }
}
