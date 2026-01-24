package com.lyshra.open.app.core.engine.version.migration.impl;

import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationContext;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationHandler;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationHandlerRegistry;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationPath;
import com.lyshra.open.app.integration.contract.version.migration.IStateTransformer;
import com.lyshra.open.app.integration.contract.version.migration.IWorkflowMigrationStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Implementation of migration handler registry.
 * Manages workflow migration strategies, handlers, transformers, and paths.
 *
 * <p>Thread-safe implementation using read-write locks for concurrent access.</p>
 */
@Slf4j
public class MigrationHandlerRegistryImpl implements IMigrationHandlerRegistry {

    private final Map<String, IWorkflowMigrationStrategy> strategies;
    private final Map<String, List<IMigrationHandler>> handlers;
    private final Map<String, IStateTransformer> transformers;
    private final Map<String, List<IMigrationPath>> paths;
    private final ReadWriteLock lock;

    private MigrationHandlerRegistryImpl() {
        this.strategies = new ConcurrentHashMap<>();
        this.handlers = new ConcurrentHashMap<>();
        this.transformers = new ConcurrentHashMap<>();
        this.paths = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
    }

    private static final class SingletonHelper {
        private static final MigrationHandlerRegistryImpl INSTANCE = new MigrationHandlerRegistryImpl();
    }

    /**
     * Gets the singleton instance.
     *
     * @return registry instance
     */
    public static IMigrationHandlerRegistry getInstance() {
        return SingletonHelper.INSTANCE;
    }

    /**
     * Creates a new registry instance (for testing).
     *
     * @return new registry instance
     */
    public static IMigrationHandlerRegistry create() {
        return new MigrationHandlerRegistryImpl();
    }

    // === Strategy Management ===

    @Override
    public void registerStrategy(IWorkflowMigrationStrategy strategy) {
        lock.writeLock().lock();
        try {
            strategies.put(strategy.getWorkflowId(), strategy);
            log.info("Registered migration strategy for workflow [{}] with policy [{}]",
                    strategy.getWorkflowId(), strategy.getMigrationPolicy());

            // Auto-register paths from strategy
            for (IMigrationPath path : strategy.getMigrationPaths()) {
                registerPathInternal(strategy.getWorkflowId(), path);
            }

            // Auto-register transformers from strategy
            for (IStateTransformer transformer : strategy.getStateTransformers().values()) {
                registerTransformerInternal(transformer);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void unregisterStrategy(String workflowId) {
        lock.writeLock().lock();
        try {
            strategies.remove(workflowId);
            log.info("Unregistered migration strategy for workflow [{}]", workflowId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<IWorkflowMigrationStrategy> getStrategy(String workflowId) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(strategies.get(workflowId));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Collection<IWorkflowMigrationStrategy> getAllStrategies() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableCollection(new ArrayList<>(strategies.values()));
        } finally {
            lock.readLock().unlock();
        }
    }

    // === Handler Management ===

    @Override
    public void registerHandler(IMigrationHandler handler) {
        lock.writeLock().lock();
        try {
            String workflowId = handler.getWorkflowId();
            handlers.computeIfAbsent(workflowId, k -> new ArrayList<>()).add(handler);
            // Sort by priority descending
            handlers.get(workflowId).sort(Comparator.comparingInt(IMigrationHandler::getPriority).reversed());
            log.info("Registered migration handler for workflow [{}] source [{}] target [{}] priority [{}]",
                    workflowId, handler.getSourceVersionPattern(),
                    handler.getTargetVersion().toVersionString(), handler.getPriority());
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void unregisterHandler(String workflowId, String sourceVersionPattern, IWorkflowVersion targetVersion) {
        lock.writeLock().lock();
        try {
            List<IMigrationHandler> workflowHandlers = handlers.get(workflowId);
            if (workflowHandlers != null) {
                workflowHandlers.removeIf(h ->
                        h.getSourceVersionPattern().equals(sourceVersionPattern) &&
                        h.getTargetVersion().equals(targetVersion));
                log.info("Unregistered migration handler for workflow [{}] source [{}] target [{}]",
                        workflowId, sourceVersionPattern, targetVersion.toVersionString());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<IMigrationHandler> getHandlers(String workflowId) {
        lock.readLock().lock();
        try {
            List<IMigrationHandler> workflowHandlers = handlers.get(workflowId);
            return workflowHandlers != null ?
                    Collections.unmodifiableList(new ArrayList<>(workflowHandlers)) :
                    Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<IMigrationHandler> findHandler(IMigrationContext context) {
        lock.readLock().lock();
        try {
            List<IMigrationHandler> workflowHandlers = handlers.get(context.getWorkflowId());
            if (workflowHandlers == null || workflowHandlers.isEmpty()) {
                return Optional.empty();
            }

            return workflowHandlers.stream()
                    .filter(h -> h.canHandle(context))
                    .findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<IMigrationHandler> findApplicableHandlers(IMigrationContext context) {
        lock.readLock().lock();
        try {
            List<IMigrationHandler> workflowHandlers = handlers.get(context.getWorkflowId());
            if (workflowHandlers == null || workflowHandlers.isEmpty()) {
                return Collections.emptyList();
            }

            return workflowHandlers.stream()
                    .filter(h -> h.canHandle(context))
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    // === Transformer Management ===

    @Override
    public void registerTransformer(IStateTransformer transformer) {
        lock.writeLock().lock();
        try {
            registerTransformerInternal(transformer);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void registerTransformerInternal(IStateTransformer transformer) {
        transformers.put(transformer.getName(), transformer);
        log.info("Registered state transformer [{}] source [{}] target [{}]",
                transformer.getName(), transformer.getSourceVersionPattern(),
                transformer.getTargetVersion().toVersionString());
    }

    @Override
    public void unregisterTransformer(String name) {
        lock.writeLock().lock();
        try {
            transformers.remove(name);
            log.info("Unregistered state transformer [{}]", name);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<IStateTransformer> getTransformer(String name) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(transformers.get(name));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<IStateTransformer> getApplicableTransformers(IMigrationContext context) {
        lock.readLock().lock();
        try {
            return transformers.values().stream()
                    .filter(t -> t.appliesTo(context))
                    .sorted(Comparator.comparingInt(IStateTransformer::getPriority).reversed())
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Collection<IStateTransformer> getAllTransformers() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableCollection(new ArrayList<>(transformers.values()));
        } finally {
            lock.readLock().unlock();
        }
    }

    // === Path Management ===

    @Override
    public void registerPath(String workflowId, IMigrationPath path) {
        lock.writeLock().lock();
        try {
            registerPathInternal(workflowId, path);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void registerPathInternal(String workflowId, IMigrationPath path) {
        paths.computeIfAbsent(workflowId, k -> new ArrayList<>()).add(path);
        // Sort by priority descending
        paths.get(workflowId).sort(Comparator.comparingInt(IMigrationPath::getPriority).reversed());
        log.debug("Registered migration path for workflow [{}]: {} -> {}",
                workflowId, path.getSourceVersionPattern(), path.getTargetVersion().toVersionString());
    }

    @Override
    public void unregisterPath(String workflowId, String sourceVersionPattern, IWorkflowVersion targetVersion) {
        lock.writeLock().lock();
        try {
            List<IMigrationPath> workflowPaths = paths.get(workflowId);
            if (workflowPaths != null) {
                workflowPaths.removeIf(p ->
                        p.getSourceVersionPattern().equals(sourceVersionPattern) &&
                        p.getTargetVersion().equals(targetVersion));
                log.info("Unregistered migration path for workflow [{}]: {} -> {}",
                        workflowId, sourceVersionPattern, targetVersion.toVersionString());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<IMigrationPath> getPaths(String workflowId) {
        lock.readLock().lock();
        try {
            List<IMigrationPath> workflowPaths = paths.get(workflowId);
            return workflowPaths != null ?
                    Collections.unmodifiableList(new ArrayList<>(workflowPaths)) :
                    Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<IMigrationPath> findDirectPath(
            String workflowId,
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion) {

        lock.readLock().lock();
        try {
            List<IMigrationPath> workflowPaths = paths.get(workflowId);
            if (workflowPaths == null || workflowPaths.isEmpty()) {
                return Optional.empty();
            }

            return workflowPaths.stream()
                    .filter(p -> p.matchesSource(sourceVersion))
                    .filter(p -> p.getTargetVersion().equals(targetVersion))
                    .findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<IMigrationPath> computeMigrationChain(
            String workflowId,
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion) {

        lock.readLock().lock();
        try {
            if (sourceVersion.equals(targetVersion)) {
                return Collections.emptyList();
            }

            List<IMigrationPath> workflowPaths = paths.get(workflowId);
            if (workflowPaths == null || workflowPaths.isEmpty()) {
                return Collections.emptyList();
            }

            // Get max hops from strategy or use default
            int maxHops = getStrategy(workflowId)
                    .map(IWorkflowMigrationStrategy::getMaxMigrationHops)
                    .orElse(5);

            Set<IWorkflowVersion> skipVersions = getStrategy(workflowId)
                    .map(IWorkflowMigrationStrategy::getSkipVersions)
                    .orElse(Collections.emptySet());

            // BFS to find shortest path
            Map<IWorkflowVersion, IMigrationPath> predecessorPath = new HashMap<>();
            Map<IWorkflowVersion, IWorkflowVersion> predecessor = new HashMap<>();
            Queue<IWorkflowVersion> queue = new LinkedList<>();
            Set<IWorkflowVersion> visited = new HashSet<>();
            Map<IWorkflowVersion, Integer> depth = new HashMap<>();

            queue.add(sourceVersion);
            visited.add(sourceVersion);
            depth.put(sourceVersion, 0);

            while (!queue.isEmpty()) {
                IWorkflowVersion current = queue.poll();
                int currentDepth = depth.get(current);

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

                if (currentDepth >= maxHops) {
                    continue; // Too many hops
                }

                // Find all paths from current version
                for (IMigrationPath path : workflowPaths) {
                    if (!path.matchesSource(current)) {
                        continue;
                    }

                    IWorkflowVersion next = path.getTargetVersion();
                    if (!visited.contains(next) && !skipVersions.contains(next)) {
                        visited.add(next);
                        predecessor.put(next, current);
                        predecessorPath.put(next, path);
                        depth.put(next, currentDepth + 1);
                        queue.add(next);
                    }
                }
            }

            return Collections.emptyList(); // No path found
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean hasPath(String workflowId, IWorkflowVersion sourceVersion, IWorkflowVersion targetVersion) {
        return !computeMigrationChain(workflowId, sourceVersion, targetVersion).isEmpty();
    }

    // === Registry Management ===

    @Override
    public Set<String> getRegisteredWorkflowIds() {
        lock.readLock().lock();
        try {
            Set<String> ids = new HashSet<>();
            ids.addAll(strategies.keySet());
            ids.addAll(handlers.keySet());
            ids.addAll(paths.keySet());
            return Collections.unmodifiableSet(ids);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public RegistryValidationResult validateWorkflowConfiguration(String workflowId) {
        lock.readLock().lock();
        try {
            List<ValidationIssue> issues = new ArrayList<>();

            // Validate strategy
            IWorkflowMigrationStrategy strategy = strategies.get(workflowId);
            if (strategy != null) {
                IWorkflowMigrationStrategy.StrategyValidationResult result = strategy.validate();
                if (!result.isValid()) {
                    for (String error : result.errors()) {
                        issues.add(ValidationIssue.error(workflowId, "strategy", error));
                    }
                }
                for (String warning : result.warnings()) {
                    issues.add(ValidationIssue.warning(workflowId, "strategy", warning));
                }
            }

            // Validate paths
            List<IMigrationPath> workflowPaths = paths.get(workflowId);
            if (workflowPaths != null) {
                for (IMigrationPath path : workflowPaths) {
                    IMigrationPath.PathValidationResult result = path.validate();
                    if (!result.isValid()) {
                        for (String error : result.errors()) {
                            issues.add(ValidationIssue.error(workflowId,
                                    "path:" + path.getSourceVersionPattern() + "->" + path.getTargetVersion().toVersionString(),
                                    error));
                        }
                    }
                }
            }

            // Check for orphaned handlers (no matching paths)
            List<IMigrationHandler> workflowHandlers = handlers.get(workflowId);
            if (workflowHandlers != null && workflowPaths != null) {
                for (IMigrationHandler handler : workflowHandlers) {
                    boolean hasMatchingPath = workflowPaths.stream()
                            .anyMatch(p -> p.getSourceVersionPattern().equals(handler.getSourceVersionPattern()) &&
                                           p.getTargetVersion().equals(handler.getTargetVersion()));
                    if (!hasMatchingPath) {
                        issues.add(ValidationIssue.warning(workflowId,
                                "handler:" + handler.getSourceVersionPattern(),
                                "Handler has no matching migration path"));
                    }
                }
            }

            boolean hasErrors = issues.stream().anyMatch(i -> i.severity() == ValidationIssue.Severity.ERROR);
            return hasErrors ? RegistryValidationResult.invalid(issues) : new RegistryValidationResult(true, issues);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public RegistryValidationResult validateAll() {
        lock.readLock().lock();
        try {
            List<ValidationIssue> allIssues = new ArrayList<>();
            for (String workflowId : getRegisteredWorkflowIds()) {
                RegistryValidationResult result = validateWorkflowConfiguration(workflowId);
                allIssues.addAll(result.issues());
            }

            boolean hasErrors = allIssues.stream().anyMatch(i -> i.severity() == ValidationIssue.Severity.ERROR);
            return hasErrors ? RegistryValidationResult.invalid(allIssues) : new RegistryValidationResult(true, allIssues);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void clearWorkflow(String workflowId) {
        lock.writeLock().lock();
        try {
            strategies.remove(workflowId);
            handlers.remove(workflowId);
            paths.remove(workflowId);
            log.info("Cleared all migration configuration for workflow [{}]", workflowId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void clearAll() {
        lock.writeLock().lock();
        try {
            strategies.clear();
            handlers.clear();
            transformers.clear();
            paths.clear();
            log.info("Cleared all migration registry configuration");
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public RegistryStats getStats() {
        lock.readLock().lock();
        try {
            int totalHandlers = handlers.values().stream().mapToInt(List::size).sum();
            int totalPaths = paths.values().stream().mapToInt(List::size).sum();
            return new RegistryStats(
                    getRegisteredWorkflowIds().size(),
                    strategies.size(),
                    totalHandlers,
                    transformers.size(),
                    totalPaths
            );
        } finally {
            lock.readLock().unlock();
        }
    }
}
