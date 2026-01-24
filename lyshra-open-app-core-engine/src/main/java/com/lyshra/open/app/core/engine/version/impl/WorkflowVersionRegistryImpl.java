package com.lyshra.open.app.core.engine.version.impl;

import com.lyshra.open.app.core.engine.version.IWorkflowVersionRegistryService;
import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.models.version.WorkflowVersion;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
 * Thread-safe implementation of the workflow version registry.
 * Uses concurrent data structures with fine-grained locking for scalability.
 *
 * <p>Design Pattern: Registry pattern with Singleton lifecycle.</p>
 */
@Slf4j
public class WorkflowVersionRegistryImpl implements IWorkflowVersionRegistryService {

    private final Map<String, NavigableMap<IWorkflowVersion, IVersionedWorkflow>> workflowVersions;
    private final Map<String, IWorkflowVersion> defaultVersions;
    private final Map<String, ReadWriteLock> workflowLocks;
    private final ReadWriteLock globalLock;

    private WorkflowVersionRegistryImpl() {
        this.workflowVersions = new ConcurrentHashMap<>();
        this.defaultVersions = new ConcurrentHashMap<>();
        this.workflowLocks = new ConcurrentHashMap<>();
        this.globalLock = new ReentrantReadWriteLock();
    }

    private static final class SingletonHelper {
        private static final WorkflowVersionRegistryImpl INSTANCE = new WorkflowVersionRegistryImpl();
    }

    public static IWorkflowVersionRegistryService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    private ReadWriteLock getLockForWorkflow(String workflowId) {
        return workflowLocks.computeIfAbsent(workflowId, k -> new ReentrantReadWriteLock());
    }

    @Override
    public void register(IVersionedWorkflow workflow) {
        String workflowId = workflow.getWorkflowId();
        IWorkflowVersion version = workflow.getVersion();

        ReadWriteLock lock = getLockForWorkflow(workflowId);
        lock.writeLock().lock();
        try {
            NavigableMap<IWorkflowVersion, IVersionedWorkflow> versions = workflowVersions
                    .computeIfAbsent(workflowId, k -> new TreeMap<>(Comparator.reverseOrder()));

            if (versions.containsKey(version)) {
                throw new IllegalStateException(
                        "Version " + version.toVersionString() + " already registered for workflow " + workflowId);
            }

            versions.put(version, workflow);
            log.info("Registered workflow [{}] version [{}]", workflowId, version.toVersionString());

            // Set as default if first stable version or higher than current default
            if (version.isStable()) {
                IWorkflowVersion currentDefault = defaultVersions.get(workflowId);
                if (currentDefault == null || version.compareTo(currentDefault) > 0) {
                    defaultVersions.put(workflowId, version);
                    log.info("Set default version for workflow [{}] to [{}]", workflowId, version.toVersionString());
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public RegistrationResult registerWithValidation(IVersionedWorkflow workflow) {
        try {
            // Validate workflow structure
            if (workflow.getWorkflowId() == null || workflow.getWorkflowId().isBlank()) {
                return RegistrationResult.failure("Workflow ID cannot be null or blank");
            }
            if (workflow.getVersion() == null) {
                return RegistrationResult.failure("Workflow version cannot be null");
            }
            if (workflow.getSteps() == null || workflow.getSteps().isEmpty()) {
                return RegistrationResult.failure("Workflow must have at least one step");
            }
            if (workflow.getStartStep() == null || !workflow.getSteps().containsKey(workflow.getStartStep())) {
                return RegistrationResult.failure("Start step must be a valid step in the workflow");
            }

            // Validate compatibility if not first version
            Optional<IVersionedWorkflow> latest = getLatestVersion(workflow.getWorkflowId());
            if (latest.isPresent()) {
                IVersionedWorkflow latestWorkflow = latest.get();
                if (workflow.getVersion().compareTo(latestWorkflow.getVersion()) <= 0) {
                    return RegistrationResult.failure(
                            "New version must be greater than latest version " + latestWorkflow.getVersion().toVersionString());
                }
            }

            register(workflow);
            return RegistrationResult.success(workflow);

        } catch (Exception e) {
            log.error("Failed to register workflow [{}] version [{}]: {}",
                    workflow.getWorkflowId(), workflow.getVersion().toVersionString(), e.getMessage());
            return RegistrationResult.failure(e.getMessage());
        }
    }

    @Override
    public boolean unregister(String workflowId, IWorkflowVersion version) {
        ReadWriteLock lock = getLockForWorkflow(workflowId);
        lock.writeLock().lock();
        try {
            NavigableMap<IWorkflowVersion, IVersionedWorkflow> versions = workflowVersions.get(workflowId);
            if (versions == null) {
                return false;
            }

            IVersionedWorkflow removed = versions.remove(version);
            if (removed != null) {
                log.info("Unregistered workflow [{}] version [{}]", workflowId, version.toVersionString());

                // Update default if this was the default version
                IWorkflowVersion currentDefault = defaultVersions.get(workflowId);
                if (currentDefault != null && currentDefault.equals(version)) {
                    Optional<IWorkflowVersion> newDefault = versions.keySet().stream()
                            .filter(IWorkflowVersion::isStable)
                            .findFirst();
                    if (newDefault.isPresent()) {
                        defaultVersions.put(workflowId, newDefault.get());
                    } else {
                        defaultVersions.remove(workflowId);
                    }
                }

                if (versions.isEmpty()) {
                    workflowVersions.remove(workflowId);
                }
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<IVersionedWorkflow> getVersion(String workflowId, IWorkflowVersion version) {
        ReadWriteLock lock = getLockForWorkflow(workflowId);
        lock.readLock().lock();
        try {
            NavigableMap<IWorkflowVersion, IVersionedWorkflow> versions = workflowVersions.get(workflowId);
            if (versions == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(versions.get(version));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<IVersionedWorkflow> getLatestStableVersion(String workflowId) {
        ReadWriteLock lock = getLockForWorkflow(workflowId);
        lock.readLock().lock();
        try {
            NavigableMap<IWorkflowVersion, IVersionedWorkflow> versions = workflowVersions.get(workflowId);
            if (versions == null) {
                return Optional.empty();
            }
            return versions.values().stream()
                    .filter(w -> w.getVersion().isStable() && !w.getVersion().isDeprecated())
                    .findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<IVersionedWorkflow> getLatestVersion(String workflowId) {
        ReadWriteLock lock = getLockForWorkflow(workflowId);
        lock.readLock().lock();
        try {
            NavigableMap<IWorkflowVersion, IVersionedWorkflow> versions = workflowVersions.get(workflowId);
            if (versions == null || versions.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(versions.firstEntry().getValue());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<IVersionedWorkflow> getAllVersions(String workflowId) {
        ReadWriteLock lock = getLockForWorkflow(workflowId);
        lock.readLock().lock();
        try {
            NavigableMap<IWorkflowVersion, IVersionedWorkflow> versions = workflowVersions.get(workflowId);
            if (versions == null) {
                return Collections.emptyList();
            }
            return new ArrayList<>(versions.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Collection<String> getAllWorkflowIds() {
        globalLock.readLock().lock();
        try {
            return new ArrayList<>(workflowVersions.keySet());
        } finally {
            globalLock.readLock().unlock();
        }
    }

    @Override
    public Collection<IVersionedWorkflow> getAllWorkflows() {
        globalLock.readLock().lock();
        try {
            return workflowVersions.values().stream()
                    .flatMap(map -> map.values().stream())
                    .collect(Collectors.toList());
        } finally {
            globalLock.readLock().unlock();
        }
    }

    @Override
    public boolean hasVersion(String workflowId, IWorkflowVersion version) {
        return getVersion(workflowId, version).isPresent();
    }

    @Override
    public int getVersionCount(String workflowId) {
        ReadWriteLock lock = getLockForWorkflow(workflowId);
        lock.readLock().lock();
        try {
            NavigableMap<IWorkflowVersion, IVersionedWorkflow> versions = workflowVersions.get(workflowId);
            return versions == null ? 0 : versions.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean deprecateVersion(String workflowId, IWorkflowVersion version, String reason) {
        ReadWriteLock lock = getLockForWorkflow(workflowId);
        lock.writeLock().lock();
        try {
            NavigableMap<IWorkflowVersion, IVersionedWorkflow> versions = workflowVersions.get(workflowId);
            if (versions == null) {
                return false;
            }
            IVersionedWorkflow workflow = versions.get(version);
            if (workflow == null) {
                return false;
            }

            // Create deprecated version
            if (version instanceof WorkflowVersion wv) {
                WorkflowVersion deprecated = wv.deprecate(reason);
                // We cannot replace the version in the workflow definition directly
                // as IVersionedWorkflow is immutable, but we log the deprecation
                log.warn("Deprecated workflow [{}] version [{}]: {}", workflowId, version.toVersionString(), reason);

                // Update default if this was the default version
                IWorkflowVersion currentDefault = defaultVersions.get(workflowId);
                if (currentDefault != null && currentDefault.equals(version)) {
                    Optional<IWorkflowVersion> newDefault = versions.keySet().stream()
                            .filter(v -> v.isStable() && !v.isDeprecated() && !v.equals(version))
                            .findFirst();
                    newDefault.ifPresent(v -> defaultVersions.put(workflowId, v));
                }
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<IVersionedWorkflow> findCompatibleVersions(String workflowId, IWorkflowVersion fromVersion) {
        ReadWriteLock lock = getLockForWorkflow(workflowId);
        lock.readLock().lock();
        try {
            NavigableMap<IWorkflowVersion, IVersionedWorkflow> versions = workflowVersions.get(workflowId);
            if (versions == null) {
                return Collections.emptyList();
            }
            return versions.values().stream()
                    .filter(w -> w.getVersion().compareTo(fromVersion) > 0)
                    .filter(w -> w.supportsMigrationFrom(fromVersion))
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<IVersionedWorkflow> getRecommendedMigrationTarget(String workflowId, IWorkflowVersion fromVersion) {
        List<IVersionedWorkflow> compatible = findCompatibleVersions(workflowId, fromVersion);
        return compatible.stream()
                .filter(w -> w.getVersion().isStable() && !w.getVersion().isDeprecated())
                .findFirst();
    }

    @Override
    public Optional<IVersionedWorkflow> resolveVersion(String workflowId, String versionSpec) {
        if (versionSpec == null || versionSpec.isBlank()) {
            return getDefaultVersion(workflowId);
        }

        String spec = versionSpec.trim().toLowerCase();

        // Handle special keywords
        if ("latest".equals(spec)) {
            return getLatestVersion(workflowId);
        }
        if ("latest-stable".equals(spec)) {
            return getLatestStableVersion(workflowId);
        }
        if ("default".equals(spec)) {
            return getDefaultVersion(workflowId);
        }

        // Handle range patterns like "1.x" or "1.2.x"
        Pattern rangePattern = Pattern.compile("^(\\d+)\\.(x|\\d+)(?:\\.(x|\\d+))?$");
        Matcher matcher = rangePattern.matcher(spec);
        if (matcher.matches()) {
            int major = Integer.parseInt(matcher.group(1));
            String minorStr = matcher.group(2);
            String patchStr = matcher.group(3);

            return getAllVersions(workflowId).stream()
                    .filter(w -> w.getVersion().getMajor() == major)
                    .filter(w -> "x".equals(minorStr) || w.getVersion().getMinor() == Integer.parseInt(minorStr))
                    .filter(w -> patchStr == null || "x".equals(patchStr) || w.getVersion().getPatch() == Integer.parseInt(patchStr))
                    .filter(w -> !w.getVersion().isDeprecated())
                    .findFirst();
        }

        // Try exact version match
        try {
            WorkflowVersion exactVersion = WorkflowVersion.parse(spec);
            return getVersion(workflowId, exactVersion);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid version specification: {}", versionSpec);
            return Optional.empty();
        }
    }

    @Override
    public Optional<IVersionedWorkflow> getActiveVersion(String workflowId) {
        return getLatestStableVersion(workflowId)
                .filter(w -> !w.getVersion().isDeprecated());
    }

    @Override
    public boolean setDefaultVersion(String workflowId, IWorkflowVersion version) {
        ReadWriteLock lock = getLockForWorkflow(workflowId);
        lock.writeLock().lock();
        try {
            if (!hasVersion(workflowId, version)) {
                return false;
            }
            defaultVersions.put(workflowId, version);
            log.info("Set default version for workflow [{}] to [{}]", workflowId, version.toVersionString());
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<IVersionedWorkflow> getDefaultVersion(String workflowId) {
        IWorkflowVersion defaultVersion = defaultVersions.get(workflowId);
        if (defaultVersion != null) {
            return getVersion(workflowId, defaultVersion);
        }
        return getLatestStableVersion(workflowId);
    }

    @Override
    public boolean hasActiveExecutions(String workflowId, IWorkflowVersion version) {
        // This would be implemented by checking the execution binding store
        // For now, return false as the execution store integration is separate
        return false;
    }
}
