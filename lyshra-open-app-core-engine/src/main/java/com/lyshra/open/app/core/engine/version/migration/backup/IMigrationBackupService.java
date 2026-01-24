package com.lyshra.open.app.core.engine.version.migration.backup;

import com.lyshra.open.app.integration.contract.version.IWorkflowExecutionBinding;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationContext;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for creating and managing migration backups.
 * Provides state snapshot capabilities for safe rollback operations.
 *
 * <p>Key responsibilities:</p>
 * <ul>
 *   <li>Create execution state backups before migration</li>
 *   <li>Store and retrieve backup snapshots</li>
 *   <li>Restore execution state from backups</li>
 *   <li>Manage backup lifecycle and retention</li>
 * </ul>
 */
public interface IMigrationBackupService {

    /**
     * Creates a backup of the current execution state before migration.
     *
     * @param binding the execution binding to backup
     * @param context the migration context
     * @return backup result with backup ID
     */
    Mono<BackupResult> createBackup(IWorkflowExecutionBinding binding, IMigrationContext context);

    /**
     * Retrieves a backup by its ID.
     *
     * @param backupId the backup identifier
     * @return the backup if found
     */
    Mono<Optional<MigrationBackup>> getBackup(String backupId);

    /**
     * Retrieves all backups for an execution.
     *
     * @param executionId the execution identifier
     * @return list of backups ordered by creation time (newest first)
     */
    Mono<List<MigrationBackup>> getBackupsForExecution(String executionId);

    /**
     * Retrieves the most recent backup for an execution.
     *
     * @param executionId the execution identifier
     * @return the most recent backup if any exists
     */
    Mono<Optional<MigrationBackup>> getLatestBackup(String executionId);

    /**
     * Restores execution state from a backup.
     *
     * @param backupId the backup identifier
     * @return restore result
     */
    Mono<RestoreResult> restoreFromBackup(String backupId);

    /**
     * Restores execution state from the most recent backup.
     *
     * @param executionId the execution identifier
     * @return restore result
     */
    Mono<RestoreResult> restoreFromLatestBackup(String executionId);

    /**
     * Deletes a backup.
     *
     * @param backupId the backup identifier
     * @return true if deleted, false if not found
     */
    Mono<Boolean> deleteBackup(String backupId);

    /**
     * Deletes all backups for an execution.
     *
     * @param executionId the execution identifier
     * @return number of backups deleted
     */
    Mono<Integer> deleteBackupsForExecution(String executionId);

    /**
     * Cleans up old backups based on retention policy.
     *
     * @param retentionDuration backups older than this are deleted
     * @return number of backups cleaned up
     */
    Mono<Integer> cleanupOldBackups(Duration retentionDuration);

    /**
     * Marks a backup as used (for tracking rollback operations).
     *
     * @param backupId the backup identifier
     * @param restoredBy who initiated the restore
     * @return updated backup
     */
    Mono<MigrationBackup> markBackupAsUsed(String backupId, String restoredBy);

    // ==================== Records ====================

    /**
     * Result of a backup creation operation.
     */
    record BackupResult(
            String backupId,
            String executionId,
            String migrationId,
            boolean success,
            Instant createdAt,
            long sizeBytes,
            Optional<String> errorMessage
    ) {
        public static BackupResult success(String backupId, String executionId,
                                           String migrationId, long sizeBytes) {
            return new BackupResult(backupId, executionId, migrationId, true,
                    Instant.now(), sizeBytes, Optional.empty());
        }

        public static BackupResult failed(String executionId, String migrationId, String error) {
            return new BackupResult(null, executionId, migrationId, false,
                    Instant.now(), 0, Optional.of(error));
        }
    }

    /**
     * Represents a migration backup snapshot.
     */
    record MigrationBackup(
            String backupId,
            String executionId,
            String migrationId,
            String workflowId,
            String sourceVersion,
            String currentStepName,
            Map<String, Object> executionData,
            Map<String, Object> variables,
            Map<String, Object> bindingMetadata,
            String executionState,
            Instant createdAt,
            String createdBy,
            Optional<Instant> restoredAt,
            Optional<String> restoredBy,
            BackupType type
    ) {
        public enum BackupType {
            PRE_MIGRATION,      // Created before migration starts
            CHECKPOINT,         // Created at migration checkpoint
            MANUAL              // Created on demand
        }

        public boolean isRestored() {
            return restoredAt.isPresent();
        }
    }

    /**
     * Result of a restore operation.
     */
    record RestoreResult(
            String backupId,
            String executionId,
            boolean success,
            String restoredVersion,
            String restoredStep,
            Instant restoredAt,
            Duration duration,
            List<String> restoredComponents,
            Optional<String> errorMessage
    ) {
        public static RestoreResult success(String backupId, String executionId,
                                            String version, String step,
                                            List<String> components, Duration duration) {
            return new RestoreResult(backupId, executionId, true, version, step,
                    Instant.now(), duration, components, Optional.empty());
        }

        public static RestoreResult failed(String backupId, String executionId, String error) {
            return new RestoreResult(backupId, executionId, false, null, null,
                    Instant.now(), Duration.ZERO, List.of(), Optional.of(error));
        }
    }
}
