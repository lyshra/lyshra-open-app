package com.lyshra.open.app.core.engine.version.storage.impl;

import com.lyshra.open.app.core.engine.version.storage.IVersionedWorkflowStorage;
import com.lyshra.open.app.core.engine.version.storage.WorkflowDefinitionSerializer;
import com.lyshra.open.app.core.engine.version.storage.WorkflowVersionConflictException;
import com.lyshra.open.app.core.engine.version.util.WorkflowSchemaHashUtil;
import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersionMetadata;
import com.lyshra.open.app.integration.models.version.WorkflowVersion;
import com.lyshra.open.app.integration.models.version.WorkflowVersionMetadata;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * File-based implementation of versioned workflow storage.
 *
 * <p>Directory Structure:</p>
 * <pre>
 * {baseDir}/
 *   └── workflows/
 *       └── {workflowId}/
 *           ├── workflow-metadata.json    (workflow-level info)
 *           └── versions/
 *               ├── 1.0.0/
 *               │   ├── definition.yaml   (or .json)
 *               │   └── version-metadata.json
 *               ├── 1.1.0/
 *               │   ├── definition.yaml
 *               │   └── version-metadata.json
 *               └── 2.0.0/
 *                   ├── definition.yaml
 *                   └── version-metadata.json
 * </pre>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Multiple versions per workflow without overwriting</li>
 *   <li>YAML and JSON format support</li>
 *   <li>Version conflict detection via schema hash</li>
 *   <li>Thread-safe operations with per-workflow locking</li>
 *   <li>Soft delete preserving audit trail</li>
 * </ul>
 */
@Slf4j
public class FileBasedVersionedWorkflowStorageImpl implements IVersionedWorkflowStorage {

    private static final String WORKFLOWS_DIR = "workflows";
    private static final String VERSIONS_DIR = "versions";
    private static final String DEFINITION_FILE_YAML = "definition.yaml";
    private static final String DEFINITION_FILE_JSON = "definition.json";
    private static final String VERSION_METADATA_FILE = "version-metadata.json";
    private static final String WORKFLOW_METADATA_FILE = "workflow-metadata.json";

    private final Path baseDir;
    private final ConcurrentHashMap<String, ReadWriteLock> workflowLocks;

    public FileBasedVersionedWorkflowStorageImpl(Path baseDir) {
        this.baseDir = baseDir;
        this.workflowLocks = new ConcurrentHashMap<>();
        initializeStorage();
    }

    /**
     * Creates a storage instance with the given base directory.
     *
     * @param baseDir base directory for workflow storage
     * @return storage instance
     */
    public static IVersionedWorkflowStorage create(Path baseDir) {
        return new FileBasedVersionedWorkflowStorageImpl(baseDir);
    }

    private void initializeStorage() {
        try {
            Path workflowsDir = baseDir.resolve(WORKFLOWS_DIR);
            if (!Files.exists(workflowsDir)) {
                Files.createDirectories(workflowsDir);
                log.info("Created workflows directory: {}", workflowsDir);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize workflow storage", e);
        }
    }

    private ReadWriteLock getLock(String workflowId) {
        return workflowLocks.computeIfAbsent(workflowId, k -> new ReentrantReadWriteLock());
    }

    private Path getWorkflowDir(String workflowId) {
        return baseDir.resolve(WORKFLOWS_DIR).resolve(workflowId);
    }

    private Path getVersionsDir(String workflowId) {
        return getWorkflowDir(workflowId).resolve(VERSIONS_DIR);
    }

    private Path getVersionDir(String workflowId, IWorkflowVersion version) {
        return getVersionsDir(workflowId).resolve(version.toVersionString());
    }

    private Path getDefinitionFile(String workflowId, IWorkflowVersion version, SerializationFormat format) {
        String filename = format == SerializationFormat.JSON ? DEFINITION_FILE_JSON : DEFINITION_FILE_YAML;
        return getVersionDir(workflowId, version).resolve(filename);
    }

    private Path getVersionMetadataFile(String workflowId, IWorkflowVersion version) {
        return getVersionDir(workflowId, version).resolve(VERSION_METADATA_FILE);
    }

    @Override
    public StorageResult store(IVersionedWorkflow workflow, SerializationFormat format) {
        String workflowId = workflow.getWorkflowId();
        IWorkflowVersion version = workflow.getVersion();

        ReadWriteLock lock = getLock(workflowId);
        lock.writeLock().lock();
        try {
            Path versionDir = getVersionDir(workflowId, version);

            // Check for conflict (version already exists)
            if (Files.exists(versionDir)) {
                Optional<String> existingHash = loadSchemaHash(workflowId, version);
                String newHash = workflow.getSchemaHash() != null ?
                        workflow.getSchemaHash() :
                        WorkflowSchemaHashUtil.computeSchemaHash(workflow);

                throw new WorkflowVersionConflictException(
                        workflowId,
                        version,
                        existingHash.orElse(null),
                        newHash);
            }

            // Create directories
            Files.createDirectories(versionDir);

            // Compute schema hash if not provided
            String schemaHash = workflow.getSchemaHash();
            if (schemaHash == null || schemaHash.isEmpty()) {
                schemaHash = WorkflowSchemaHashUtil.computeSchemaHash(workflow);
            }

            // Serialize and write definition
            String definitionContent = format == SerializationFormat.JSON ?
                    WorkflowDefinitionSerializer.toJson(workflow) :
                    WorkflowDefinitionSerializer.toYaml(workflow);

            Path definitionFile = getDefinitionFile(workflowId, version, format);
            Files.writeString(definitionFile, definitionContent, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);

            // Create and write version metadata
            WorkflowVersionMetadata metadata = WorkflowVersionMetadata.builder()
                    .workflowId(workflowId)
                    .version(version)
                    .schemaHash(schemaHash)
                    .createdAt(Instant.now())
                    .active(true)
                    .createdBy("system")
                    .build();

            Path metadataFile = getVersionMetadataFile(workflowId, version);
            String metadataContent = WorkflowDefinitionSerializer.metadataToJson(metadata);
            Files.writeString(metadataFile, metadataContent, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);

            log.info("Stored workflow version: {}:{} at {} (format: {})",
                    workflowId, version.toVersionString(), versionDir, format);

            return StorageResult.success(workflowId, version, versionDir.toString(), schemaHash, format);

        } catch (WorkflowVersionConflictException e) {
            throw e;
        } catch (IOException e) {
            log.error("Failed to store workflow {}:{}: {}",
                    workflowId, version.toVersionString(), e.getMessage(), e);
            return StorageResult.failure(workflowId, version, "IO error: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<IVersionedWorkflow> load(String workflowId, IWorkflowVersion version) {
        ReadWriteLock lock = getLock(workflowId);
        lock.readLock().lock();
        try {
            Path versionDir = getVersionDir(workflowId, version);
            if (!Files.exists(versionDir)) {
                return Optional.empty();
            }

            // Try YAML first, then JSON
            Optional<String> content = loadDefinitionContent(workflowId, version);
            if (content.isEmpty()) {
                return Optional.empty();
            }

            // Determine format and deserialize
            Path yamlFile = getDefinitionFile(workflowId, version, SerializationFormat.YAML);
            IVersionedWorkflow workflow = Files.exists(yamlFile) ?
                    WorkflowDefinitionSerializer.fromYaml(content.get()) :
                    WorkflowDefinitionSerializer.fromJson(content.get());

            return Optional.of(workflow);

        } catch (Exception e) {
            log.error("Failed to load workflow {}:{}: {}",
                    workflowId, version.toVersionString(), e.getMessage(), e);
            return Optional.empty();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<IVersionedWorkflow> loadLatest(String workflowId) {
        List<IWorkflowVersion> versions = listVersions(workflowId);
        if (versions.isEmpty()) {
            return Optional.empty();
        }
        return load(workflowId, versions.get(0));
    }

    @Override
    public Optional<IVersionedWorkflow> loadLatestActive(String workflowId) {
        List<IWorkflowVersion> versions = listVersions(workflowId);
        for (IWorkflowVersion version : versions) {
            Optional<IWorkflowVersionMetadata> metadata = loadMetadata(workflowId, version);
            if (metadata.isPresent() && metadata.get().isActive()) {
                return load(workflowId, version);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<IVersionedWorkflow> loadAllVersions(String workflowId) {
        List<IVersionedWorkflow> workflows = new ArrayList<>();
        for (IWorkflowVersion version : listVersions(workflowId)) {
            load(workflowId, version).ifPresent(workflows::add);
        }
        return workflows;
    }

    @Override
    public List<String> listWorkflowIds() {
        Path workflowsDir = baseDir.resolve(WORKFLOWS_DIR);
        if (!Files.exists(workflowsDir)) {
            return List.of();
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(workflowsDir)) {
            List<String> ids = new ArrayList<>();
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    ids.add(path.getFileName().toString());
                }
            }
            return ids;
        } catch (IOException e) {
            log.error("Failed to list workflow IDs: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<IWorkflowVersion> listVersions(String workflowId) {
        Path versionsDir = getVersionsDir(workflowId);
        if (!Files.exists(versionsDir)) {
            return List.of();
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(versionsDir)) {
            List<IWorkflowVersion> versions = new ArrayList<>();
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    try {
                        IWorkflowVersion version = WorkflowVersion.parse(path.getFileName().toString());
                        versions.add(version);
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid version directory: {}", path.getFileName());
                    }
                }
            }
            // Sort descending (newest first)
            versions.sort(Comparator.reverseOrder());
            return versions;
        } catch (IOException e) {
            log.error("Failed to list versions for {}: {}", workflowId, e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public boolean exists(String workflowId, IWorkflowVersion version) {
        return Files.exists(getVersionDir(workflowId, version));
    }

    @Override
    public boolean exists(String workflowId) {
        return Files.exists(getWorkflowDir(workflowId)) && !listVersions(workflowId).isEmpty();
    }

    @Override
    public boolean delete(String workflowId, IWorkflowVersion version, String reason) {
        ReadWriteLock lock = getLock(workflowId);
        lock.writeLock().lock();
        try {
            Optional<IWorkflowVersionMetadata> metadataOpt = loadMetadata(workflowId, version);
            if (metadataOpt.isEmpty()) {
                return false;
            }

            // Soft delete - update metadata to mark as inactive
            WorkflowVersionMetadata metadata = (WorkflowVersionMetadata) metadataOpt.get();
            WorkflowVersionMetadata deletedMetadata = metadata.deactivate(reason);

            Path metadataFile = getVersionMetadataFile(workflowId, version);
            String metadataContent = WorkflowDefinitionSerializer.metadataToJson(deletedMetadata);
            Files.writeString(metadataFile, metadataContent, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);

            log.info("Soft-deleted workflow version {}:{} - reason: {}",
                    workflowId, version.toVersionString(), reason);
            return true;

        } catch (IOException e) {
            log.error("Failed to delete workflow {}:{}: {}",
                    workflowId, version.toVersionString(), e.getMessage(), e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<String> getRawDefinition(String workflowId, IWorkflowVersion version) {
        ReadWriteLock lock = getLock(workflowId);
        lock.readLock().lock();
        try {
            return loadDefinitionContent(workflowId, version);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public String getStorageLocation(String workflowId, IWorkflowVersion version) {
        return getVersionDir(workflowId, version).toString();
    }

    private Optional<String> loadDefinitionContent(String workflowId, IWorkflowVersion version) {
        // Try YAML first
        Path yamlFile = getDefinitionFile(workflowId, version, SerializationFormat.YAML);
        if (Files.exists(yamlFile)) {
            try {
                return Optional.of(Files.readString(yamlFile, StandardCharsets.UTF_8));
            } catch (IOException e) {
                log.error("Failed to read YAML definition: {}", e.getMessage());
            }
        }

        // Try JSON
        Path jsonFile = getDefinitionFile(workflowId, version, SerializationFormat.JSON);
        if (Files.exists(jsonFile)) {
            try {
                return Optional.of(Files.readString(jsonFile, StandardCharsets.UTF_8));
            } catch (IOException e) {
                log.error("Failed to read JSON definition: {}", e.getMessage());
            }
        }

        return Optional.empty();
    }

    private Optional<IWorkflowVersionMetadata> loadMetadata(String workflowId, IWorkflowVersion version) {
        Path metadataFile = getVersionMetadataFile(workflowId, version);
        if (!Files.exists(metadataFile)) {
            return Optional.empty();
        }

        try {
            String content = Files.readString(metadataFile, StandardCharsets.UTF_8);
            return Optional.of(WorkflowDefinitionSerializer.metadataFromJson(content));
        } catch (IOException e) {
            log.error("Failed to read version metadata: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> loadSchemaHash(String workflowId, IWorkflowVersion version) {
        return loadMetadata(workflowId, version).map(IWorkflowVersionMetadata::getSchemaHash);
    }

    @Override
    public ConflictCheckResult checkConflict(IVersionedWorkflow workflow) {
        String workflowId = workflow.getWorkflowId();
        IWorkflowVersion version = workflow.getVersion();

        ReadWriteLock lock = getLock(workflowId);
        lock.readLock().lock();
        try {
            if (!exists(workflowId, version)) {
                return ConflictCheckResult.noConflict(workflowId, version);
            }

            // Version exists - check if content differs
            Optional<String> existingHash = loadSchemaHash(workflowId, version);
            String newHash = workflow.getSchemaHash() != null ?
                    workflow.getSchemaHash() :
                    WorkflowSchemaHashUtil.computeSchemaHash(workflow);

            IWorkflowVersion suggestedVersion = suggestNonConflictingVersion(workflowId, version);

            return ConflictCheckResult.conflict(
                    workflowId,
                    version,
                    existingHash.orElse(null),
                    newHash,
                    suggestedVersion);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public IWorkflowVersion suggestNonConflictingVersion(String workflowId, IWorkflowVersion proposedVersion) {
        ReadWriteLock lock = getLock(workflowId);
        lock.readLock().lock();
        try {
            if (!exists(workflowId, proposedVersion)) {
                return proposedVersion;
            }

            // Version exists - find next available patch version
            int major = proposedVersion.getMajor();
            int minor = proposedVersion.getMinor();
            int patch = proposedVersion.getPatch();

            // Find the highest existing patch for this major.minor
            List<IWorkflowVersion> existingVersions = listVersions(workflowId);
            int maxPatch = patch;

            for (IWorkflowVersion v : existingVersions) {
                if (v.getMajor() == major && v.getMinor() == minor && v.getPatch() > maxPatch) {
                    maxPatch = v.getPatch();
                }
            }

            // Return next patch version
            return WorkflowVersion.of(major, minor, maxPatch + 1);
        } finally {
            lock.readLock().unlock();
        }
    }
}
