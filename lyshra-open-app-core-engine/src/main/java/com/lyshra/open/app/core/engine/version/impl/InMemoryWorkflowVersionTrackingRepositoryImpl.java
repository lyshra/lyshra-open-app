package com.lyshra.open.app.core.engine.version.impl;

import com.lyshra.open.app.core.engine.version.IWorkflowVersionTrackingRepository;
import com.lyshra.open.app.core.engine.version.util.WorkflowSchemaHashUtil;
import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersionMetadata;
import com.lyshra.open.app.integration.models.version.VersionedWorkflowDefinition;
import com.lyshra.open.app.integration.models.version.WorkflowVersionMetadata;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * In-memory implementation of workflow version tracking repository.
 * Provides reliable tracking and retrieval of workflow versions.
 *
 * <p>Thread-safe implementation using read-write locks for concurrent access.</p>
 *
 * <p>Design Pattern: Repository pattern with Singleton lifecycle.</p>
 */
@Slf4j
public class InMemoryWorkflowVersionTrackingRepositoryImpl implements IWorkflowVersionTrackingRepository {

    // Primary storage: workflowId -> (version -> workflow)
    private final Map<String, NavigableMap<IWorkflowVersion, VersionEntry>> workflowVersions;

    // Secondary index: schemaHash -> workflowId:version
    private final Map<String, String> schemaHashIndex;

    // Locking for thread safety
    private final Map<String, ReadWriteLock> workflowLocks;
    private final ReadWriteLock globalLock;

    private InMemoryWorkflowVersionTrackingRepositoryImpl() {
        this.workflowVersions = new ConcurrentHashMap<>();
        this.schemaHashIndex = new ConcurrentHashMap<>();
        this.workflowLocks = new ConcurrentHashMap<>();
        this.globalLock = new ReentrantReadWriteLock();
    }

    private static final class SingletonHelper {
        private static final InMemoryWorkflowVersionTrackingRepositoryImpl INSTANCE =
                new InMemoryWorkflowVersionTrackingRepositoryImpl();
    }

    public static IWorkflowVersionTrackingRepository getInstance() {
        return SingletonHelper.INSTANCE;
    }

    /**
     * Internal entry holding workflow and its metadata.
     */
    private record VersionEntry(
            IVersionedWorkflow workflow,
            IWorkflowVersionMetadata metadata
    ) {}

    private ReadWriteLock getLock(String workflowId) {
        return workflowLocks.computeIfAbsent(workflowId, k -> new ReentrantReadWriteLock());
    }

    @Override
    public IVersionedWorkflow save(IVersionedWorkflow workflow) {
        String workflowId = workflow.getWorkflowId();
        IWorkflowVersion version = workflow.getVersion();

        ReadWriteLock lock = getLock(workflowId);
        lock.writeLock().lock();
        try {
            NavigableMap<IWorkflowVersion, VersionEntry> versions = workflowVersions
                    .computeIfAbsent(workflowId, k -> new TreeMap<>(Comparator.reverseOrder()));

            if (versions.containsKey(version)) {
                throw new IllegalStateException(
                        "Version already exists: " + workflowId + ":" + version.toVersionString());
            }

            // Compute schema hash if not provided
            String schemaHash = workflow.getSchemaHash();
            if (schemaHash == null || schemaHash.isEmpty()) {
                schemaHash = WorkflowSchemaHashUtil.computeSchemaHash(workflow);
            }

            // Create metadata
            WorkflowVersionMetadata metadata = WorkflowVersionMetadata.create(
                    workflowId,
                    version,
                    schemaHash,
                    "system"
            );

            // Create workflow with computed fields
            VersionedWorkflowDefinition enrichedWorkflow = enrichWorkflow(workflow, schemaHash, metadata);

            // Store
            versions.put(version, new VersionEntry(enrichedWorkflow, metadata));

            // Update schema hash index
            schemaHashIndex.put(schemaHash, workflowId + ":" + version.toVersionString());

            log.info("Saved workflow version: {}:{} with schemaHash: {}...",
                    workflowId, version.toVersionString(),
                    schemaHash.substring(0, 8));

            return enrichedWorkflow;

        } finally {
            lock.writeLock().unlock();
        }
    }

    private VersionedWorkflowDefinition enrichWorkflow(
            IVersionedWorkflow workflow,
            String schemaHash,
            IWorkflowVersionMetadata metadata) {

        if (workflow instanceof VersionedWorkflowDefinition vwd) {
            // Use builder to add computed fields
            return VersionedWorkflowDefinition.builder()
                    .workflowId(vwd.getWorkflowId())
                    .name(vwd.getName())
                    .version(vwd.getVersion())
                    .startStep(vwd.getStartStep())
                    .contextRetention(vwd.getContextRetention())
                    .steps(steps -> {
                        vwd.getSteps().values().forEach(steps::step);
                        return steps;
                    })
                    .compatibility(vwd.getCompatibility())
                    .schemaHash(schemaHash)
                    .createdAt(Instant.now())
                    .active(true)
                    .schemaVersion(vwd.getSchemaVersion())
                    .metadata(vwd.getMetadata())
                    .addedSteps(vwd.getAddedSteps())
                    .removedSteps(vwd.getRemovedSteps())
                    .modifiedSteps(vwd.getModifiedSteps())
                    .migrationHints(vwd.getMigrationHints().orElse(null))
                    .versionMetadata(metadata)
                    .build();
        }

        throw new IllegalArgumentException("Workflow must be VersionedWorkflowDefinition");
    }

    @Override
    public Optional<IVersionedWorkflow> updateMetadata(
            String workflowId,
            IWorkflowVersion version,
            IWorkflowVersionMetadata metadata) {

        ReadWriteLock lock = getLock(workflowId);
        lock.writeLock().lock();
        try {
            NavigableMap<IWorkflowVersion, VersionEntry> versions = workflowVersions.get(workflowId);
            if (versions == null) {
                return Optional.empty();
            }

            VersionEntry entry = versions.get(version);
            if (entry == null) {
                return Optional.empty();
            }

            VersionEntry updatedEntry = new VersionEntry(entry.workflow(), metadata);
            versions.put(version, updatedEntry);

            log.info("Updated metadata for workflow version: {}:{}", workflowId, version.toVersionString());
            return Optional.of(entry.workflow());

        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean delete(String workflowId, IWorkflowVersion version, String reason) {
        return deactivate(workflowId, version, reason).isPresent();
    }

    @Override
    public List<IVersionedWorkflow> findAllByWorkflowId(String workflowId) {
        ReadWriteLock lock = getLock(workflowId);
        lock.readLock().lock();
        try {
            NavigableMap<IWorkflowVersion, VersionEntry> versions = workflowVersions.get(workflowId);
            if (versions == null) {
                return List.of();
            }
            return versions.values().stream()
                    .map(VersionEntry::workflow)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<IVersionedWorkflow> findActiveByWorkflowId(String workflowId) {
        return findAllByWorkflowId(workflowId).stream()
                .filter(IVersionedWorkflow::isActive)
                .collect(Collectors.toList());
    }

    @Override
    public long countByWorkflowId(String workflowId) {
        ReadWriteLock lock = getLock(workflowId);
        lock.readLock().lock();
        try {
            NavigableMap<IWorkflowVersion, VersionEntry> versions = workflowVersions.get(workflowId);
            return versions == null ? 0 : versions.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<IVersionedWorkflow> findByWorkflowIdAndVersion(String workflowId, IWorkflowVersion version) {
        ReadWriteLock lock = getLock(workflowId);
        lock.readLock().lock();
        try {
            NavigableMap<IWorkflowVersion, VersionEntry> versions = workflowVersions.get(workflowId);
            if (versions == null) {
                return Optional.empty();
            }
            VersionEntry entry = versions.get(version);
            return entry == null ? Optional.empty() : Optional.of(entry.workflow());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<IVersionedWorkflow> findLatestByWorkflowId(String workflowId) {
        ReadWriteLock lock = getLock(workflowId);
        lock.readLock().lock();
        try {
            NavigableMap<IWorkflowVersion, VersionEntry> versions = workflowVersions.get(workflowId);
            if (versions == null || versions.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(versions.firstEntry().getValue().workflow());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<IVersionedWorkflow> findLatestStableByWorkflowId(String workflowId) {
        return findAllByWorkflowId(workflowId).stream()
                .filter(w -> w.getVersion().isStable())
                .findFirst();
    }

    @Override
    public Optional<IVersionedWorkflow> findLatestActiveByWorkflowId(String workflowId) {
        return findAllByWorkflowId(workflowId).stream()
                .filter(IVersionedWorkflow::isActive)
                .filter(w -> w.getVersion().isStable())
                .findFirst();
    }

    @Override
    public Optional<IVersionedWorkflow> findBySchemaHash(String schemaHash) {
        String compositeKey = schemaHashIndex.get(schemaHash);
        if (compositeKey == null) {
            return Optional.empty();
        }

        String[] parts = compositeKey.split(":", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }

        return findAllByWorkflowId(parts[0]).stream()
                .filter(w -> schemaHash.equals(w.getSchemaHash()))
                .findFirst();
    }

    @Override
    public List<IVersionedWorkflow> findAllBySchemaHash(String schemaHash) {
        globalLock.readLock().lock();
        try {
            return workflowVersions.values().stream()
                    .flatMap(versions -> versions.values().stream())
                    .map(VersionEntry::workflow)
                    .filter(w -> schemaHash.equals(w.getSchemaHash()))
                    .collect(Collectors.toList());
        } finally {
            globalLock.readLock().unlock();
        }
    }

    @Override
    public boolean verifySchemaHash(String workflowId, IWorkflowVersion version, String expectedHash) {
        return findByWorkflowIdAndVersion(workflowId, version)
                .map(w -> expectedHash.equalsIgnoreCase(w.getSchemaHash()))
                .orElse(false);
    }

    @Override
    public List<IVersionedWorkflow> findByWorkflowIdCreatedAfter(String workflowId, Instant after) {
        return findAllByWorkflowId(workflowId).stream()
                .filter(w -> w.getCreatedAt().isAfter(after))
                .collect(Collectors.toList());
    }

    @Override
    public List<IVersionedWorkflow> findByWorkflowIdCreatedBefore(String workflowId, Instant before) {
        return findAllByWorkflowId(workflowId).stream()
                .filter(w -> w.getCreatedAt().isBefore(before))
                .collect(Collectors.toList());
    }

    @Override
    public List<IVersionedWorkflow> findByWorkflowIdCreatedBetween(String workflowId, Instant from, Instant to) {
        return findAllByWorkflowId(workflowId).stream()
                .filter(w -> !w.getCreatedAt().isBefore(from) && w.getCreatedAt().isBefore(to))
                .collect(Collectors.toList());
    }

    @Override
    public List<IVersionedWorkflow> findAllActive() {
        globalLock.readLock().lock();
        try {
            return workflowVersions.values().stream()
                    .flatMap(versions -> versions.values().stream())
                    .map(VersionEntry::workflow)
                    .filter(IVersionedWorkflow::isActive)
                    .collect(Collectors.toList());
        } finally {
            globalLock.readLock().unlock();
        }
    }

    @Override
    public List<IVersionedWorkflow> findInactiveByWorkflowId(String workflowId) {
        return findAllByWorkflowId(workflowId).stream()
                .filter(w -> !w.isActive())
                .collect(Collectors.toList());
    }

    @Override
    public Optional<IVersionedWorkflow> activate(String workflowId, IWorkflowVersion version) {
        ReadWriteLock lock = getLock(workflowId);
        lock.writeLock().lock();
        try {
            NavigableMap<IWorkflowVersion, VersionEntry> versions = workflowVersions.get(workflowId);
            if (versions == null) {
                return Optional.empty();
            }

            VersionEntry entry = versions.get(version);
            if (entry == null) {
                return Optional.empty();
            }

            WorkflowVersionMetadata activatedMetadata =
                    ((WorkflowVersionMetadata) entry.metadata()).activate();

            VersionedWorkflowDefinition activatedWorkflow = enrichWorkflowWithActiveStatus(
                    entry.workflow(), true, activatedMetadata);

            versions.put(version, new VersionEntry(activatedWorkflow, activatedMetadata));

            log.info("Activated workflow version: {}:{}", workflowId, version.toVersionString());
            return Optional.of(activatedWorkflow);

        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<IVersionedWorkflow> deactivate(String workflowId, IWorkflowVersion version, String reason) {
        ReadWriteLock lock = getLock(workflowId);
        lock.writeLock().lock();
        try {
            NavigableMap<IWorkflowVersion, VersionEntry> versions = workflowVersions.get(workflowId);
            if (versions == null) {
                return Optional.empty();
            }

            VersionEntry entry = versions.get(version);
            if (entry == null) {
                return Optional.empty();
            }

            WorkflowVersionMetadata deactivatedMetadata =
                    ((WorkflowVersionMetadata) entry.metadata()).deactivate(reason);

            VersionedWorkflowDefinition deactivatedWorkflow = enrichWorkflowWithActiveStatus(
                    entry.workflow(), false, deactivatedMetadata);

            versions.put(version, new VersionEntry(deactivatedWorkflow, deactivatedMetadata));

            log.info("Deactivated workflow version: {}:{} reason: {}",
                    workflowId, version.toVersionString(), reason);
            return Optional.of(deactivatedWorkflow);

        } finally {
            lock.writeLock().unlock();
        }
    }

    private VersionedWorkflowDefinition enrichWorkflowWithActiveStatus(
            IVersionedWorkflow workflow,
            boolean active,
            IWorkflowVersionMetadata metadata) {

        VersionedWorkflowDefinition vwd = (VersionedWorkflowDefinition) workflow;

        return VersionedWorkflowDefinition.builder()
                .workflowId(vwd.getWorkflowId())
                .name(vwd.getName())
                .version(vwd.getVersion())
                .startStep(vwd.getStartStep())
                .contextRetention(vwd.getContextRetention())
                .steps(steps -> {
                    vwd.getSteps().values().forEach(steps::step);
                    return steps;
                })
                .compatibility(vwd.getCompatibility())
                .schemaHash(vwd.getSchemaHash())
                .createdAt(vwd.getCreatedAt())
                .active(active)
                .schemaVersion(vwd.getSchemaVersion())
                .metadata(vwd.getMetadata())
                .addedSteps(vwd.getAddedSteps())
                .removedSteps(vwd.getRemovedSteps())
                .modifiedSteps(vwd.getModifiedSteps())
                .migrationHints(vwd.getMigrationHints().orElse(null))
                .versionMetadata(metadata)
                .build();
    }

    @Override
    public boolean isActive(String workflowId, IWorkflowVersion version) {
        return findByWorkflowIdAndVersion(workflowId, version)
                .map(IVersionedWorkflow::isActive)
                .orElse(false);
    }

    @Override
    public boolean existsByWorkflowId(String workflowId) {
        ReadWriteLock lock = getLock(workflowId);
        lock.readLock().lock();
        try {
            NavigableMap<IWorkflowVersion, VersionEntry> versions = workflowVersions.get(workflowId);
            return versions != null && !versions.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean existsByWorkflowIdAndVersion(String workflowId, IWorkflowVersion version) {
        return findByWorkflowIdAndVersion(workflowId, version).isPresent();
    }

    @Override
    public boolean existsBySchemaHash(String schemaHash) {
        return schemaHashIndex.containsKey(schemaHash);
    }

    @Override
    public Optional<IWorkflowVersionMetadata> getMetadata(String workflowId, IWorkflowVersion version) {
        ReadWriteLock lock = getLock(workflowId);
        lock.readLock().lock();
        try {
            NavigableMap<IWorkflowVersion, VersionEntry> versions = workflowVersions.get(workflowId);
            if (versions == null) {
                return Optional.empty();
            }
            VersionEntry entry = versions.get(version);
            return entry == null ? Optional.empty() : Optional.of(entry.metadata());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<IWorkflowVersionMetadata> getAllMetadataByWorkflowId(String workflowId) {
        ReadWriteLock lock = getLock(workflowId);
        lock.readLock().lock();
        try {
            NavigableMap<IWorkflowVersion, VersionEntry> versions = workflowVersions.get(workflowId);
            if (versions == null) {
                return List.of();
            }
            return versions.values().stream()
                    .map(VersionEntry::metadata)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
}
