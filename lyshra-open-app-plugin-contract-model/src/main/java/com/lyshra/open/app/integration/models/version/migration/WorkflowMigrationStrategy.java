package com.lyshra.open.app.integration.models.version.migration;

import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationPath;
import com.lyshra.open.app.integration.contract.version.migration.IStateTransformer;
import com.lyshra.open.app.integration.contract.version.migration.IWorkflowMigrationStrategy;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

/**
 * Implementation of per-workflow migration strategy.
 * Provides comprehensive configuration for workflow migration behavior.
 */
@Data
@Builder
public class WorkflowMigrationStrategy implements IWorkflowMigrationStrategy, Serializable {

    private static final long serialVersionUID = 1L;

    private final String workflowId;

    @Builder.Default
    private final MigrationPolicy migrationPolicy = MigrationPolicy.FROZEN;

    @Builder.Default
    private final RollbackPolicy rollbackPolicy = RollbackPolicy.AUTOMATIC;

    @Builder.Default
    private final boolean migrationEnabled = true;

    @Builder.Default
    private final List<IMigrationPath> migrationPaths = new ArrayList<>();

    @Builder.Default
    private final Map<String, IStateTransformer> stateTransformers = new HashMap<>();

    @Builder.Default
    private final Set<IWorkflowVersion> stableVersions = new HashSet<>();

    @Builder.Default
    private final Set<IWorkflowVersion> skipVersions = new HashSet<>();

    @Builder.Default
    private final int maxMigrationHops = 5;

    @Builder.Default
    private final Duration migrationTimeout = Duration.ofMinutes(5);

    @Builder.Default
    private final int maxRetries = 3;

    @Builder.Default
    private final Duration retryDelay = Duration.ofSeconds(30);

    @Builder.Default
    private final boolean preValidationRequired = true;

    @Builder.Default
    private final boolean checkpointingEnabled = true;

    @Builder.Default
    private final int batchSize = 100;

    @Builder.Default
    private final boolean dryRunDefault = false;

    @Builder.Default
    private final Map<String, Object> metadata = new HashMap<>();

    @Override
    public List<IMigrationPath> getMigrationPaths() {
        return Collections.unmodifiableList(migrationPaths);
    }

    @Override
    public Optional<IMigrationPath> findMigrationPath(IWorkflowVersion sourceVersion, IWorkflowVersion targetVersion) {
        return migrationPaths.stream()
                .filter(p -> p.matchesSource(sourceVersion))
                .filter(p -> p.getTargetVersion().equals(targetVersion))
                .max((a, b) -> Integer.compare(a.getPriority(), b.getPriority()));
    }

    @Override
    public List<IMigrationPath> getPathsFrom(IWorkflowVersion sourceVersion) {
        return migrationPaths.stream()
                .filter(p -> p.matchesSource(sourceVersion))
                .toList();
    }

    @Override
    public List<IMigrationPath> computeMigrationChain(IWorkflowVersion sourceVersion, IWorkflowVersion targetVersion) {
        if (sourceVersion.equals(targetVersion)) {
            return List.of();
        }

        // BFS to find shortest path
        Map<IWorkflowVersion, IMigrationPath> predecessorPath = new HashMap<>();
        Map<IWorkflowVersion, IWorkflowVersion> predecessor = new HashMap<>();
        Queue<IWorkflowVersion> queue = new LinkedList<>();
        Set<IWorkflowVersion> visited = new HashSet<>();

        queue.add(sourceVersion);
        visited.add(sourceVersion);

        while (!queue.isEmpty()) {
            IWorkflowVersion current = queue.poll();

            if (current.equals(targetVersion)) {
                // Reconstruct path
                List<IMigrationPath> chain = new ArrayList<>();
                IWorkflowVersion step = targetVersion;
                while (predecessor.containsKey(step)) {
                    chain.add(0, predecessorPath.get(step));
                    step = predecessor.get(step);
                }
                return chain;
            }

            if (chain(current, visited).size() >= maxMigrationHops) {
                continue; // Too many hops
            }

            for (IMigrationPath path : getPathsFrom(current)) {
                IWorkflowVersion next = path.getTargetVersion();
                if (!visited.contains(next) && !skipVersions.contains(next)) {
                    visited.add(next);
                    predecessor.put(next, current);
                    predecessorPath.put(next, path);
                    queue.add(next);
                }
            }
        }

        return List.of(); // No path found
    }

    private Set<IWorkflowVersion> chain(IWorkflowVersion current, Set<IWorkflowVersion> visited) {
        return visited;
    }

    @Override
    public Map<String, IStateTransformer> getStateTransformers() {
        return Collections.unmodifiableMap(stateTransformers);
    }

    @Override
    public Optional<IStateTransformer> getStateTransformer(String name) {
        return Optional.ofNullable(stateTransformers.get(name));
    }

    @Override
    public Set<IWorkflowVersion> getStableVersions() {
        return Collections.unmodifiableSet(stableVersions);
    }

    @Override
    public Set<IWorkflowVersion> getSkipVersions() {
        return Collections.unmodifiableSet(skipVersions);
    }

    @Override
    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    @Override
    public StrategyValidationResult validate() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (workflowId == null || workflowId.isBlank()) {
            errors.add("Workflow ID is required");
        }

        if (maxMigrationHops < 1) {
            errors.add("Max migration hops must be at least 1");
        }

        if (migrationTimeout.isNegative() || migrationTimeout.isZero()) {
            errors.add("Migration timeout must be positive");
        }

        if (maxRetries < 0) {
            errors.add("Max retries cannot be negative");
        }

        if (batchSize < 1) {
            errors.add("Batch size must be at least 1");
        }

        // Check for circular paths
        for (IMigrationPath path : migrationPaths) {
            if (path.validate() != null && !path.validate().isValid()) {
                errors.add("Invalid path from " + path.getSourceVersionPattern() +
                        " to " + path.getTargetVersion().toVersionString() + ": " +
                        path.validate().errors());
            }
        }

        // Warn about missing stable versions
        if (stableVersions.isEmpty() && !migrationPaths.isEmpty()) {
            warnings.add("No stable versions defined - consider marking production versions as stable");
        }

        if (!errors.isEmpty()) {
            return StrategyValidationResult.invalid(errors);
        }

        if (!warnings.isEmpty()) {
            return StrategyValidationResult.withWarnings(warnings);
        }

        return StrategyValidationResult.valid();
    }

    /**
     * Builder extension for fluent API.
     */
    public static class WorkflowMigrationStrategyBuilder {

        public WorkflowMigrationStrategyBuilder addMigrationPath(IMigrationPath path) {
            if (this.migrationPaths$value == null) {
                this.migrationPaths$value = new ArrayList<>();
                this.migrationPaths$set = true;
            }
            this.migrationPaths$value.add(path);
            return this;
        }

        public WorkflowMigrationStrategyBuilder addStateTransformer(IStateTransformer transformer) {
            if (this.stateTransformers$value == null) {
                this.stateTransformers$value = new HashMap<>();
                this.stateTransformers$set = true;
            }
            this.stateTransformers$value.put(transformer.getName(), transformer);
            return this;
        }

        public WorkflowMigrationStrategyBuilder addStableVersion(IWorkflowVersion version) {
            if (this.stableVersions$value == null) {
                this.stableVersions$value = new HashSet<>();
                this.stableVersions$set = true;
            }
            this.stableVersions$value.add(version);
            return this;
        }

        public WorkflowMigrationStrategyBuilder addSkipVersion(IWorkflowVersion version) {
            if (this.skipVersions$value == null) {
                this.skipVersions$value = new HashSet<>();
                this.skipVersions$set = true;
            }
            this.skipVersions$value.add(version);
            return this;
        }

        public WorkflowMigrationStrategyBuilder addMetadata(String key, Object value) {
            if (this.metadata$value == null) {
                this.metadata$value = new HashMap<>();
                this.metadata$set = true;
            }
            this.metadata$value.put(key, value);
            return this;
        }
    }

    /**
     * Creates a frozen strategy (no migrations allowed).
     */
    public static WorkflowMigrationStrategy frozen(String workflowId) {
        return WorkflowMigrationStrategy.builder()
                .workflowId(workflowId)
                .migrationPolicy(MigrationPolicy.FROZEN)
                .migrationEnabled(false)
                .build();
    }

    /**
     * Creates an opportunistic strategy.
     */
    public static WorkflowMigrationStrategy opportunistic(String workflowId) {
        return WorkflowMigrationStrategy.builder()
                .workflowId(workflowId)
                .migrationPolicy(MigrationPolicy.OPPORTUNISTIC)
                .build();
    }

    /**
     * Creates a manual approval strategy.
     */
    public static WorkflowMigrationStrategy manualApproval(String workflowId) {
        return WorkflowMigrationStrategy.builder()
                .workflowId(workflowId)
                .migrationPolicy(MigrationPolicy.MANUAL_APPROVAL)
                .preValidationRequired(true)
                .build();
    }
}
