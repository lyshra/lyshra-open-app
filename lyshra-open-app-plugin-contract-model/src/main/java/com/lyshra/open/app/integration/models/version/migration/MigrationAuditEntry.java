package com.lyshra.open.app.integration.models.version.migration;

import com.lyshra.open.app.integration.contract.version.migration.IMigrationAuditEntry;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of migration audit entry for compliance tracking.
 */
@Data
@Builder
public class MigrationAuditEntry implements IMigrationAuditEntry, Serializable {

    private static final long serialVersionUID = 1L;

    private final EntryType entryType;
    @Builder.Default
    private final Instant timestamp = Instant.now();
    private final String executionId;
    private final String migrationId;
    private final String message;
    @Builder.Default
    private final Map<String, Object> details = new HashMap<>();
    private final Throwable error;
    @Builder.Default
    private final String actor = "system";

    @Override
    public Map<String, Object> getDetails() {
        return Collections.unmodifiableMap(details);
    }

    @Override
    public Optional<Throwable> getError() {
        return Optional.ofNullable(error);
    }

    /**
     * Creates an entry for migration initiation.
     */
    public static MigrationAuditEntry initiated(String executionId, String migrationId, String actor, Map<String, Object> details) {
        return MigrationAuditEntry.builder()
                .entryType(EntryType.MIGRATION_INITIATED)
                .executionId(executionId)
                .migrationId(migrationId)
                .message("Migration initiated")
                .actor(actor)
                .details(new HashMap<>(details))
                .build();
    }

    /**
     * Creates an entry for validation start.
     */
    public static MigrationAuditEntry validationStarted(String executionId, String migrationId) {
        return MigrationAuditEntry.builder()
                .entryType(EntryType.VERSION_VALIDATION_STARTED)
                .executionId(executionId)
                .migrationId(migrationId)
                .message("Version validation started")
                .build();
    }

    /**
     * Creates an entry for validation completion.
     */
    public static MigrationAuditEntry validationCompleted(String executionId, String migrationId, boolean passed, Map<String, Object> details) {
        return MigrationAuditEntry.builder()
                .entryType(EntryType.VERSION_VALIDATION_COMPLETED)
                .executionId(executionId)
                .migrationId(migrationId)
                .message(passed ? "Validation passed" : "Validation failed")
                .details(new HashMap<>(details))
                .build();
    }

    /**
     * Creates an entry for context snapshot.
     */
    public static MigrationAuditEntry contextSnapshotTaken(String executionId, String migrationId, Map<String, Object> snapshotSummary) {
        return MigrationAuditEntry.builder()
                .entryType(EntryType.CONTEXT_SNAPSHOT_TAKEN)
                .executionId(executionId)
                .migrationId(migrationId)
                .message("Context snapshot captured")
                .details(new HashMap<>(snapshotSummary))
                .build();
    }

    /**
     * Creates an entry for context transformation.
     */
    public static MigrationAuditEntry contextTransformationStarted(String executionId, String migrationId) {
        return MigrationAuditEntry.builder()
                .entryType(EntryType.CONTEXT_TRANSFORMATION_STARTED)
                .executionId(executionId)
                .migrationId(migrationId)
                .message("Context transformation started")
                .build();
    }

    /**
     * Creates an entry for context transformation completion.
     */
    public static MigrationAuditEntry contextTransformationCompleted(String executionId, String migrationId, Map<String, Object> transformDetails) {
        return MigrationAuditEntry.builder()
                .entryType(EntryType.CONTEXT_TRANSFORMATION_COMPLETED)
                .executionId(executionId)
                .migrationId(migrationId)
                .message("Context transformation completed")
                .details(new HashMap<>(transformDetails))
                .build();
    }

    /**
     * Creates an entry for step mapping.
     */
    public static MigrationAuditEntry stepMappingApplied(String executionId, String migrationId, String fromStep, String toStep) {
        Map<String, Object> details = new HashMap<>();
        details.put("fromStep", fromStep);
        details.put("toStep", toStep);
        return MigrationAuditEntry.builder()
                .entryType(EntryType.STEP_MAPPING_APPLIED)
                .executionId(executionId)
                .migrationId(migrationId)
                .message("Step mapping applied: " + fromStep + " -> " + toStep)
                .details(details)
                .build();
    }

    /**
     * Creates an entry for migration completion.
     */
    public static MigrationAuditEntry completed(String executionId, String migrationId, Map<String, Object> resultSummary) {
        return MigrationAuditEntry.builder()
                .entryType(EntryType.MIGRATION_COMPLETED)
                .executionId(executionId)
                .migrationId(migrationId)
                .message("Migration completed successfully")
                .details(new HashMap<>(resultSummary))
                .build();
    }

    /**
     * Creates an entry for migration failure.
     */
    public static MigrationAuditEntry failed(String executionId, String migrationId, Throwable error, Map<String, Object> details) {
        return MigrationAuditEntry.builder()
                .entryType(EntryType.MIGRATION_FAILED)
                .executionId(executionId)
                .migrationId(migrationId)
                .message("Migration failed: " + (error != null ? error.getMessage() : "Unknown error"))
                .error(error)
                .details(new HashMap<>(details))
                .build();
    }

    /**
     * Creates an entry for rollback initiation.
     */
    public static MigrationAuditEntry rollbackInitiated(String executionId, String migrationId, String reason) {
        Map<String, Object> details = new HashMap<>();
        details.put("reason", reason);
        return MigrationAuditEntry.builder()
                .entryType(EntryType.ROLLBACK_INITIATED)
                .executionId(executionId)
                .migrationId(migrationId)
                .message("Rollback initiated: " + reason)
                .details(details)
                .build();
    }

    /**
     * Creates an entry for rollback completion.
     */
    public static MigrationAuditEntry rollbackCompleted(String executionId, String migrationId, boolean successful) {
        return MigrationAuditEntry.builder()
                .entryType(EntryType.ROLLBACK_COMPLETED)
                .executionId(executionId)
                .migrationId(migrationId)
                .message(successful ? "Rollback completed successfully" : "Rollback failed")
                .build();
    }
}
