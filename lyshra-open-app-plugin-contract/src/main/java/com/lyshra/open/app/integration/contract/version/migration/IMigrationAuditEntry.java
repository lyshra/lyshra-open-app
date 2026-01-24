package com.lyshra.open.app.integration.contract.version.migration;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Audit trail entry for migration operations.
 * Provides detailed tracking for compliance and debugging.
 */
public interface IMigrationAuditEntry {

    /**
     * Entry type enumeration.
     */
    enum EntryType {
        MIGRATION_INITIATED,
        VERSION_VALIDATION_STARTED,
        VERSION_VALIDATION_COMPLETED,
        CONTEXT_SNAPSHOT_TAKEN,
        CONTEXT_TRANSFORMATION_STARTED,
        CONTEXT_TRANSFORMATION_COMPLETED,
        STEP_MAPPING_APPLIED,
        VARIABLE_MIGRATION_STARTED,
        VARIABLE_MIGRATION_COMPLETED,
        MIGRATION_HANDLER_INVOKED,
        MIGRATION_COMPLETED,
        MIGRATION_FAILED,
        ROLLBACK_INITIATED,
        ROLLBACK_COMPLETED,
        APPROVAL_REQUESTED,
        APPROVAL_GRANTED,
        APPROVAL_DENIED
    }

    /**
     * Returns the audit entry type.
     *
     * @return entry type
     */
    EntryType getEntryType();

    /**
     * Returns the timestamp of the entry.
     *
     * @return timestamp
     */
    Instant getTimestamp();

    /**
     * Returns the execution ID.
     *
     * @return execution ID
     */
    String getExecutionId();

    /**
     * Returns the migration ID.
     *
     * @return migration ID
     */
    String getMigrationId();

    /**
     * Returns human-readable message.
     *
     * @return message
     */
    String getMessage();

    /**
     * Returns additional details.
     *
     * @return details map
     */
    Map<String, Object> getDetails();

    /**
     * Returns associated error if any.
     *
     * @return error
     */
    Optional<Throwable> getError();

    /**
     * Returns the actor who performed/triggered this action.
     *
     * @return actor identifier
     */
    String getActor();
}
