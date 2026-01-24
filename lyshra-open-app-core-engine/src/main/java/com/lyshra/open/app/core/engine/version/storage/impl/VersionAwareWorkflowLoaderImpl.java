package com.lyshra.open.app.core.engine.version.storage.impl;

import com.lyshra.open.app.core.engine.version.storage.IVersionAwareWorkflowLoader;
import com.lyshra.open.app.core.engine.version.storage.IVersionedWorkflowStorage;
import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.models.version.WorkflowVersion;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Implementation of version-aware workflow loader with caching.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Intelligent version resolution from patterns</li>
 *   <li>In-memory caching for performance</li>
 *   <li>Thread-safe operations</li>
 *   <li>Lazy loading with cache population</li>
 * </ul>
 */
@Slf4j
public class VersionAwareWorkflowLoaderImpl implements IVersionAwareWorkflowLoader {

    private static final Pattern EXACT_VERSION_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(.*)$");
    private static final Pattern MAJOR_WILDCARD_PATTERN = Pattern.compile("^(\\d+)\\.x(\\.x)?$");
    private static final Pattern MINOR_WILDCARD_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.x$");
    private static final Pattern GTE_PATTERN = Pattern.compile("^>=\\s*(\\d+\\.\\d+\\.\\d+.*)$");
    private static final Pattern LT_PATTERN = Pattern.compile("^<\\s*(\\d+\\.\\d+\\.\\d+.*)$");
    private static final Pattern CARET_PATTERN = Pattern.compile("^\\^\\s*(\\d+)\\.(\\d+)\\.(\\d+)(.*)$");
    private static final Pattern TILDE_PATTERN = Pattern.compile("^~\\s*(\\d+)\\.(\\d+)\\.(\\d+)(.*)$");

    private final IVersionedWorkflowStorage storage;
    private final Map<String, NavigableMap<IWorkflowVersion, IVersionedWorkflow>> cache;
    private final Map<String, ReadWriteLock> locks;
    private final ReadWriteLock globalLock;

    public VersionAwareWorkflowLoaderImpl(IVersionedWorkflowStorage storage) {
        this.storage = storage;
        this.cache = new ConcurrentHashMap<>();
        this.locks = new ConcurrentHashMap<>();
        this.globalLock = new ReentrantReadWriteLock();
    }

    /**
     * Creates a loader with the given storage.
     *
     * @param storage workflow storage
     * @return loader instance
     */
    public static IVersionAwareWorkflowLoader create(IVersionedWorkflowStorage storage) {
        return new VersionAwareWorkflowLoaderImpl(storage);
    }

    private ReadWriteLock getLock(String workflowId) {
        return locks.computeIfAbsent(workflowId, k -> new ReentrantReadWriteLock());
    }

    private NavigableMap<IWorkflowVersion, IVersionedWorkflow> getOrLoadCache(String workflowId) {
        return cache.computeIfAbsent(workflowId, this::loadWorkflowCache);
    }

    private NavigableMap<IWorkflowVersion, IVersionedWorkflow> loadWorkflowCache(String workflowId) {
        NavigableMap<IWorkflowVersion, IVersionedWorkflow> versionMap =
                new TreeMap<>((a, b) -> b.compareTo(a)); // Descending order

        List<IVersionedWorkflow> workflows = storage.loadAllVersions(workflowId);
        for (IVersionedWorkflow workflow : workflows) {
            versionMap.put(workflow.getVersion(), workflow);
        }

        log.debug("Loaded {} versions into cache for workflow: {}", versionMap.size(), workflowId);
        return versionMap;
    }

    @Override
    public Optional<IVersionedWorkflow> load(String workflowId, IWorkflowVersion version) {
        ReadWriteLock lock = getLock(workflowId);
        lock.readLock().lock();
        try {
            NavigableMap<IWorkflowVersion, IVersionedWorkflow> versionMap = getOrLoadCache(workflowId);
            IVersionedWorkflow workflow = versionMap.get(version);

            // Cache miss - try loading directly from storage
            if (workflow == null) {
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    Optional<IVersionedWorkflow> loaded = storage.load(workflowId, version);
                    loaded.ifPresent(w -> versionMap.put(version, w));
                    return loaded;
                } finally {
                    lock.readLock().lock();
                    lock.writeLock().unlock();
                }
            }

            return Optional.of(workflow);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<IVersionedWorkflow> load(String workflowId, String versionSpec) {
        if (versionSpec == null || versionSpec.isBlank()) {
            return loadLatestActive(workflowId);
        }

        String spec = versionSpec.trim().toLowerCase();

        // Handle special keywords
        switch (spec) {
            case "latest":
                return loadLatest(workflowId);
            case "stable":
                return loadLatestStable(workflowId);
            case "active":
                return loadLatestActive(workflowId);
            default:
                // Continue with pattern matching
                break;
        }

        // Resolve version and load
        Optional<IWorkflowVersion> resolved = resolveVersion(workflowId, versionSpec);
        if (resolved.isPresent()) {
            return load(workflowId, resolved.get());
        }

        log.warn("Could not resolve version spec '{}' for workflow '{}'", versionSpec, workflowId);
        return Optional.empty();
    }

    @Override
    public Optional<IVersionedWorkflow> loadLatest(String workflowId) {
        ReadWriteLock lock = getLock(workflowId);
        lock.readLock().lock();
        try {
            NavigableMap<IWorkflowVersion, IVersionedWorkflow> versionMap = getOrLoadCache(workflowId);
            if (versionMap.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(versionMap.firstEntry().getValue());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<IVersionedWorkflow> loadLatestStable(String workflowId) {
        ReadWriteLock lock = getLock(workflowId);
        lock.readLock().lock();
        try {
            NavigableMap<IWorkflowVersion, IVersionedWorkflow> versionMap = getOrLoadCache(workflowId);
            return versionMap.values().stream()
                    .filter(w -> w.getVersion().isStable())
                    .findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<IVersionedWorkflow> loadLatestActive(String workflowId) {
        ReadWriteLock lock = getLock(workflowId);
        lock.readLock().lock();
        try {
            NavigableMap<IWorkflowVersion, IVersionedWorkflow> versionMap = getOrLoadCache(workflowId);
            return versionMap.values().stream()
                    .filter(IVersionedWorkflow::isActive)
                    .filter(w -> w.getVersion().isStable())
                    .findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<IVersionedWorkflow> loadAllVersions(String workflowId) {
        ReadWriteLock lock = getLock(workflowId);
        lock.readLock().lock();
        try {
            NavigableMap<IWorkflowVersion, IVersionedWorkflow> versionMap = getOrLoadCache(workflowId);
            return List.copyOf(versionMap.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<IVersionedWorkflow> loadActiveVersions(String workflowId) {
        return loadAllVersions(workflowId).stream()
                .filter(IVersionedWorkflow::isActive)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<IWorkflowVersion> resolveVersion(String workflowId, String versionSpec) {
        if (versionSpec == null || versionSpec.isBlank()) {
            return loadLatestActive(workflowId).map(IVersionedWorkflow::getVersion);
        }

        String spec = versionSpec.trim();
        List<IWorkflowVersion> available = listAvailableVersions(workflowId);

        if (available.isEmpty()) {
            return Optional.empty();
        }

        // Exact version match
        Matcher exactMatcher = EXACT_VERSION_PATTERN.matcher(spec);
        if (exactMatcher.matches()) {
            try {
                IWorkflowVersion exact = WorkflowVersion.parse(spec);
                return available.contains(exact) ? Optional.of(exact) : Optional.empty();
            } catch (IllegalArgumentException e) {
                log.debug("Invalid version format: {}", spec);
            }
        }

        // Major wildcard: 1.x or 1.x.x
        Matcher majorWildcard = MAJOR_WILDCARD_PATTERN.matcher(spec);
        if (majorWildcard.matches()) {
            int major = Integer.parseInt(majorWildcard.group(1));
            return available.stream()
                    .filter(v -> v.getMajor() == major)
                    .filter(v -> v.isStable())
                    .findFirst();
        }

        // Minor wildcard: 1.2.x
        Matcher minorWildcard = MINOR_WILDCARD_PATTERN.matcher(spec);
        if (minorWildcard.matches()) {
            int major = Integer.parseInt(minorWildcard.group(1));
            int minor = Integer.parseInt(minorWildcard.group(2));
            return available.stream()
                    .filter(v -> v.getMajor() == major && v.getMinor() == minor)
                    .filter(v -> v.isStable())
                    .findFirst();
        }

        // Greater than or equal: >=1.2.0
        Matcher gteMatcher = GTE_PATTERN.matcher(spec);
        if (gteMatcher.matches()) {
            try {
                IWorkflowVersion minVersion = WorkflowVersion.parse(gteMatcher.group(1));
                return available.stream()
                        .filter(v -> v.compareTo(minVersion) >= 0)
                        .filter(v -> v.isStable())
                        .findFirst();
            } catch (IllegalArgumentException e) {
                log.debug("Invalid version in >=: {}", spec);
            }
        }

        // Less than: <2.0.0
        Matcher ltMatcher = LT_PATTERN.matcher(spec);
        if (ltMatcher.matches()) {
            try {
                IWorkflowVersion maxVersion = WorkflowVersion.parse(ltMatcher.group(1));
                return available.stream()
                        .filter(v -> v.compareTo(maxVersion) < 0)
                        .filter(v -> v.isStable())
                        .findFirst();
            } catch (IllegalArgumentException e) {
                log.debug("Invalid version in <: {}", spec);
            }
        }

        // Caret range: ^1.2.3 means >=1.2.3 <2.0.0
        Matcher caretMatcher = CARET_PATTERN.matcher(spec);
        if (caretMatcher.matches()) {
            int major = Integer.parseInt(caretMatcher.group(1));
            int minor = Integer.parseInt(caretMatcher.group(2));
            int patch = Integer.parseInt(caretMatcher.group(3));
            IWorkflowVersion minVersion = WorkflowVersion.of(major, minor, patch);
            return available.stream()
                    .filter(v -> v.compareTo(minVersion) >= 0)
                    .filter(v -> v.getMajor() == major)
                    .filter(v -> v.isStable())
                    .findFirst();
        }

        // Tilde range: ~1.2.3 means >=1.2.3 <1.3.0
        Matcher tildeMatcher = TILDE_PATTERN.matcher(spec);
        if (tildeMatcher.matches()) {
            int major = Integer.parseInt(tildeMatcher.group(1));
            int minor = Integer.parseInt(tildeMatcher.group(2));
            int patch = Integer.parseInt(tildeMatcher.group(3));
            IWorkflowVersion minVersion = WorkflowVersion.of(major, minor, patch);
            return available.stream()
                    .filter(v -> v.compareTo(minVersion) >= 0)
                    .filter(v -> v.getMajor() == major && v.getMinor() == minor)
                    .filter(v -> v.isStable())
                    .findFirst();
        }

        log.warn("Unrecognized version spec pattern: {}", spec);
        return Optional.empty();
    }

    @Override
    public List<IWorkflowVersion> listAvailableVersions(String workflowId) {
        ReadWriteLock lock = getLock(workflowId);
        lock.readLock().lock();
        try {
            NavigableMap<IWorkflowVersion, IVersionedWorkflow> versionMap = getOrLoadCache(workflowId);
            return List.copyOf(versionMap.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean isVersionAvailable(String workflowId, IWorkflowVersion version) {
        ReadWriteLock lock = getLock(workflowId);
        lock.readLock().lock();
        try {
            NavigableMap<IWorkflowVersion, IVersionedWorkflow> versionMap = getOrLoadCache(workflowId);
            return versionMap.containsKey(version);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void clearCache() {
        globalLock.writeLock().lock();
        try {
            cache.clear();
            log.info("Cleared all workflow caches");
        } finally {
            globalLock.writeLock().unlock();
        }
    }

    @Override
    public void clearCache(String workflowId) {
        ReadWriteLock lock = getLock(workflowId);
        lock.writeLock().lock();
        try {
            cache.remove(workflowId);
            log.debug("Cleared cache for workflow: {}", workflowId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void reload() {
        globalLock.writeLock().lock();
        try {
            cache.clear();
            // Cache will be repopulated on next access
            log.info("Scheduled reload for all workflows (lazy)");
        } finally {
            globalLock.writeLock().unlock();
        }
    }

    @Override
    public void reload(String workflowId) {
        ReadWriteLock lock = getLock(workflowId);
        lock.writeLock().lock();
        try {
            cache.remove(workflowId);
            // Force immediate reload
            getOrLoadCache(workflowId);
            log.debug("Reloaded cache for workflow: {}", workflowId);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
