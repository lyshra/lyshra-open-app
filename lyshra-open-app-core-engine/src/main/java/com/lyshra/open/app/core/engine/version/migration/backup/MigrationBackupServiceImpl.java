package com.lyshra.open.app.core.engine.version.migration.backup;

import com.lyshra.open.app.core.engine.version.IWorkflowExecutionBindingStore;
import com.lyshra.open.app.core.engine.version.impl.InMemoryWorkflowExecutionBindingStoreImpl;
import com.lyshra.open.app.integration.contract.version.IWorkflowExecutionBinding;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationContext;
import com.lyshra.open.app.integration.models.version.WorkflowExecutionBinding;
import com.lyshra.open.app.integration.models.version.migration.MigrationContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementation of IMigrationBackupService providing in-memory backup storage.
 * In production, this would be backed by a persistent store.
 */
@Slf4j
public class MigrationBackupServiceImpl implements IMigrationBackupService {

    private static volatile MigrationBackupServiceImpl instance;

    private final Map<String, MigrationBackup> backupStore = new ConcurrentHashMap<>();
    private final Map<String, List<String>> executionBackupIndex = new ConcurrentHashMap<>();
    private final IWorkflowExecutionBindingStore bindingStore;

    public MigrationBackupServiceImpl() {
        this.bindingStore = InMemoryWorkflowExecutionBindingStoreImpl.getInstance();
    }

    public MigrationBackupServiceImpl(IWorkflowExecutionBindingStore bindingStore) {
        this.bindingStore = bindingStore;
    }

    public static MigrationBackupServiceImpl getInstance() {
        if (instance == null) {
            synchronized (MigrationBackupServiceImpl.class) {
                if (instance == null) {
                    instance = new MigrationBackupServiceImpl();
                }
            }
        }
        return instance;
    }

    @Override
    public Mono<BackupResult> createBackup(IWorkflowExecutionBinding binding, IMigrationContext context) {
        return Mono.fromCallable(() -> {
            try {
                String backupId = UUID.randomUUID().toString();
                String executionId = binding.getExecutionId();
                String migrationId = (context instanceof MigrationContext mc) ?
                        mc.getMigrationId() : UUID.randomUUID().toString();

                // Capture execution data
                Map<String, Object> executionData = new HashMap<>();
                if (context.getExecutionContext() != null) {
                    Object data = context.getExecutionContext().getData();
                    if (data != null) {
                        executionData.put("data", deepCopy(data));
                    }
                }

                // Capture variables
                Map<String, Object> variables = new HashMap<>();
                if (context.getExecutionContext() != null) {
                    variables.putAll(deepCopyMap(context.getExecutionContext().getVariables()));
                }

                // Capture binding metadata
                Map<String, Object> bindingMetadata = new HashMap<>();
                bindingMetadata.put("state", binding.getState().name());
                bindingMetadata.put("migrationStrategy", binding.getMigrationStrategy().name());
                bindingMetadata.put("migrationCount", binding.getMigrationCount());
                binding.getMigratedFromVersion().ifPresent(version ->
                        bindingMetadata.put("migratedFromVersion", version.toVersionString())
                );

                MigrationBackup backup = new MigrationBackup(
                        backupId,
                        executionId,
                        migrationId,
                        binding.getWorkflowIdentifier().getWorkflowName(),
                        binding.getBoundVersion().toVersionString(),
                        binding.getCurrentStepName(),
                        executionData,
                        variables,
                        bindingMetadata,
                        binding.getState().name(),
                        Instant.now(),
                        context.getInitiatedBy(),
                        Optional.empty(),
                        Optional.empty(),
                        MigrationBackup.BackupType.PRE_MIGRATION
                );

                // Store backup
                backupStore.put(backupId, backup);

                // Update index
                executionBackupIndex.computeIfAbsent(executionId, k -> new ArrayList<>()).add(backupId);

                long sizeBytes = estimateBackupSize(backup);

                log.info("Created backup [{}] for execution [{}] migration [{}], size: {} bytes",
                        backupId, executionId, migrationId, sizeBytes);

                return BackupResult.success(backupId, executionId, migrationId, sizeBytes);

            } catch (Exception e) {
                log.error("Failed to create backup for execution [{}]: {}",
                        binding.getExecutionId(), e.getMessage(), e);
                String failedMigrationId = (context instanceof MigrationContext mc) ?
                        mc.getMigrationId() : "unknown";
                return BackupResult.failed(binding.getExecutionId(), failedMigrationId, e.getMessage());
            }
        });
    }

    @Override
    public Mono<Optional<MigrationBackup>> getBackup(String backupId) {
        return Mono.fromCallable(() -> Optional.ofNullable(backupStore.get(backupId)));
    }

    @Override
    public Mono<List<MigrationBackup>> getBackupsForExecution(String executionId) {
        return Mono.fromCallable(() -> {
            List<String> backupIds = executionBackupIndex.getOrDefault(executionId, List.of());
            return backupIds.stream()
                    .map(backupStore::get)
                    .filter(b -> b != null)
                    .sorted(Comparator.comparing(MigrationBackup::createdAt).reversed())
                    .collect(Collectors.toList());
        });
    }

    @Override
    public Mono<Optional<MigrationBackup>> getLatestBackup(String executionId) {
        return getBackupsForExecution(executionId)
                .map(backups -> backups.isEmpty() ? Optional.empty() : Optional.of(backups.get(0)));
    }

    @Override
    public Mono<RestoreResult> restoreFromBackup(String backupId) {
        return Mono.fromCallable(() -> {
            Instant startTime = Instant.now();

            MigrationBackup backup = backupStore.get(backupId);
            if (backup == null) {
                return RestoreResult.failed(backupId, null, "Backup not found: " + backupId);
            }

            String executionId = backup.executionId();

            try {
                // Find current binding
                Optional<IWorkflowExecutionBinding> bindingOpt = bindingStore.findById(executionId);
                if (bindingOpt.isEmpty()) {
                    return RestoreResult.failed(backupId, executionId,
                            "Execution binding not found: " + executionId);
                }

                IWorkflowExecutionBinding currentBinding = bindingOpt.get();
                List<String> restoredComponents = new ArrayList<>();

                // Restore binding state
                IWorkflowExecutionBinding.ExecutionState restoredState =
                        IWorkflowExecutionBinding.ExecutionState.valueOf(backup.executionState());

                WorkflowExecutionBinding restoredBinding = WorkflowExecutionBinding.builder()
                        .executionId(executionId)
                        .workflowIdentifier(currentBinding.getWorkflowIdentifier())
                        .boundVersion(com.lyshra.open.app.integration.models.version.WorkflowVersion.parse(backup.sourceVersion()))
                        .state(restoredState)
                        .currentStepName(backup.currentStepName())
                        .startedAt(currentBinding.getStartedAt())
                        .migrationStrategy(currentBinding.getMigrationStrategy())
                        .migrationCount(currentBinding.getMigrationCount())
                        .build();

                bindingStore.update(restoredBinding);
                restoredComponents.add("binding");
                restoredComponents.add("version:" + backup.sourceVersion());
                restoredComponents.add("step:" + backup.currentStepName());
                restoredComponents.add("state:" + backup.executionState());

                // Note: In a full implementation, we would also restore the execution context
                // This requires access to the actual execution runtime

                Duration duration = Duration.between(startTime, Instant.now());

                // Mark backup as used
                MigrationBackup updatedBackup = new MigrationBackup(
                        backup.backupId(),
                        backup.executionId(),
                        backup.migrationId(),
                        backup.workflowId(),
                        backup.sourceVersion(),
                        backup.currentStepName(),
                        backup.executionData(),
                        backup.variables(),
                        backup.bindingMetadata(),
                        backup.executionState(),
                        backup.createdAt(),
                        backup.createdBy(),
                        Optional.of(Instant.now()),
                        Optional.of("system"),
                        backup.type()
                );
                backupStore.put(backupId, updatedBackup);

                log.info("Restored execution [{}] from backup [{}] to version [{}] step [{}]",
                        executionId, backupId, backup.sourceVersion(), backup.currentStepName());

                return RestoreResult.success(backupId, executionId, backup.sourceVersion(),
                        backup.currentStepName(), restoredComponents, duration);

            } catch (Exception e) {
                log.error("Failed to restore from backup [{}]: {}", backupId, e.getMessage(), e);
                return RestoreResult.failed(backupId, executionId, e.getMessage());
            }
        });
    }

    @Override
    public Mono<RestoreResult> restoreFromLatestBackup(String executionId) {
        return getLatestBackup(executionId)
                .flatMap(optBackup -> {
                    if (optBackup.isEmpty()) {
                        return Mono.just(RestoreResult.failed(null, executionId,
                                "No backup found for execution: " + executionId));
                    }
                    return restoreFromBackup(optBackup.get().backupId());
                });
    }

    @Override
    public Mono<Boolean> deleteBackup(String backupId) {
        return Mono.fromCallable(() -> {
            MigrationBackup backup = backupStore.remove(backupId);
            if (backup != null) {
                List<String> backupIds = executionBackupIndex.get(backup.executionId());
                if (backupIds != null) {
                    backupIds.remove(backupId);
                }
                log.debug("Deleted backup [{}] for execution [{}]", backupId, backup.executionId());
                return true;
            }
            return false;
        });
    }

    @Override
    public Mono<Integer> deleteBackupsForExecution(String executionId) {
        return Mono.fromCallable(() -> {
            List<String> backupIds = executionBackupIndex.remove(executionId);
            if (backupIds == null || backupIds.isEmpty()) {
                return 0;
            }

            int deleted = 0;
            for (String backupId : backupIds) {
                if (backupStore.remove(backupId) != null) {
                    deleted++;
                }
            }

            log.info("Deleted {} backups for execution [{}]", deleted, executionId);
            return deleted;
        });
    }

    @Override
    public Mono<Integer> cleanupOldBackups(Duration retentionDuration) {
        return Mono.fromCallable(() -> {
            Instant cutoff = Instant.now().minus(retentionDuration);
            List<String> toDelete = backupStore.values().stream()
                    .filter(b -> b.createdAt().isBefore(cutoff))
                    .filter(b -> !b.isRestored()) // Don't delete backups that were used
                    .map(MigrationBackup::backupId)
                    .toList();

            int deleted = 0;
            for (String backupId : toDelete) {
                if (deleteBackup(backupId).block()) {
                    deleted++;
                }
            }

            if (deleted > 0) {
                log.info("Cleaned up {} old backups older than {}", deleted, retentionDuration);
            }
            return deleted;
        });
    }

    @Override
    public Mono<MigrationBackup> markBackupAsUsed(String backupId, String restoredBy) {
        return Mono.fromCallable(() -> {
            MigrationBackup backup = backupStore.get(backupId);
            if (backup == null) {
                throw new IllegalArgumentException("Backup not found: " + backupId);
            }

            MigrationBackup updatedBackup = new MigrationBackup(
                    backup.backupId(),
                    backup.executionId(),
                    backup.migrationId(),
                    backup.workflowId(),
                    backup.sourceVersion(),
                    backup.currentStepName(),
                    backup.executionData(),
                    backup.variables(),
                    backup.bindingMetadata(),
                    backup.executionState(),
                    backup.createdAt(),
                    backup.createdBy(),
                    Optional.of(Instant.now()),
                    Optional.of(restoredBy),
                    backup.type()
            );

            backupStore.put(backupId, updatedBackup);
            return updatedBackup;
        });
    }

    // ==================== Helper Methods ====================

    private long estimateBackupSize(MigrationBackup backup) {
        // Rough estimate of serialized size
        long size = 0;
        size += backup.backupId().length() * 2L;
        size += backup.executionId().length() * 2L;
        size += backup.migrationId().length() * 2L;
        size += backup.workflowId().length() * 2L;
        size += backup.sourceVersion().length() * 2L;
        size += backup.currentStepName().length() * 2L;
        size += estimateMapSize(backup.executionData());
        size += estimateMapSize(backup.variables());
        size += estimateMapSize(backup.bindingMetadata());
        return size;
    }

    private long estimateMapSize(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return 0;
        }
        // Rough estimate: 100 bytes per entry on average
        return map.size() * 100L;
    }

    @SuppressWarnings("unchecked")
    private Object deepCopy(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Map) {
            return deepCopyMap((Map<String, Object>) obj);
        }
        if (obj instanceof List) {
            return deepCopyList((List<Object>) obj);
        }
        // For immutable types, return as-is
        return obj;
    }

    private Map<String, Object> deepCopyMap(Map<String, Object> original) {
        if (original == null) {
            return new HashMap<>();
        }
        Map<String, Object> copy = new HashMap<>();
        for (Map.Entry<String, Object> entry : original.entrySet()) {
            copy.put(entry.getKey(), deepCopy(entry.getValue()));
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private List<Object> deepCopyList(List<Object> original) {
        if (original == null) {
            return new ArrayList<>();
        }
        List<Object> copy = new ArrayList<>();
        for (Object item : original) {
            copy.add(deepCopy(item));
        }
        return copy;
    }
}
