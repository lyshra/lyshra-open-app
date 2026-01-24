package com.lyshra.open.app.core.engine.audit;

import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskAuditEntry;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskAuditEntry.AuditAction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service interface for human task audit logging.
 * Provides comprehensive audit trail capabilities for compliance and debugging.
 *
 * <h2>Purpose</h2>
 * <p>This service captures all human task lifecycle events including:</p>
 * <ul>
 *   <li>Task creation</li>
 *   <li>Task assignment (automatic and manual)</li>
 *   <li>Task claim and unclaim</li>
 *   <li>Task completion (approve, reject, complete)</li>
 *   <li>Task cancellation</li>
 *   <li>Task escalation and timeout</li>
 *   <li>Comments and data updates</li>
 * </ul>
 *
 * <h2>Compliance Support</h2>
 * <p>Audit entries capture:</p>
 * <ul>
 *   <li>Who - Actor ID and type (user, system, timer)</li>
 *   <li>What - Action performed with detailed description</li>
 *   <li>When - Precise timestamp</li>
 *   <li>Context - Previous/new status, additional data</li>
 *   <li>Traceability - Correlation ID, client info, session ID</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Log a task creation event
 * auditService.logAuditEntry(
 *     LyshraOpenAppHumanTaskAuditEntryModel.taskCreated(taskId, workflowId, actorId, data)
 * ).subscribe();
 *
 * // Query audit history for a task
 * auditService.getAuditTrailForTask(taskId)
 *     .doOnNext(entry -> log.info("Event: {} at {}", entry.getAction(), entry.getTimestamp()))
 *     .subscribe();
 *
 * // Export audit data for compliance
 * List<ILyshraOpenAppHumanTaskAuditEntry> entries =
 *     auditService.queryAuditEntries(AuditQuery.builder()
 *         .fromTimestamp(startOfDay)
 *         .toTimestamp(endOfDay)
 *         .actions(List.of(AuditAction.APPROVED, AuditAction.REJECTED))
 *         .build())
 *     .collectList().block();
 * }</pre>
 *
 * @see ILyshraOpenAppHumanTaskAuditEntry
 * @see LyshraOpenAppHumanTaskAuditEntryModel
 */
public interface ILyshraOpenAppHumanTaskAuditService {

    // ========================================================================
    // AUDIT ENTRY LOGGING
    // ========================================================================

    /**
     * Logs an audit entry.
     *
     * @param entry the audit entry to log
     * @return the logged entry (may include generated fields)
     */
    Mono<ILyshraOpenAppHumanTaskAuditEntry> logAuditEntry(ILyshraOpenAppHumanTaskAuditEntry entry);

    /**
     * Logs multiple audit entries in batch.
     *
     * @param entries the entries to log
     * @return flux of logged entries
     */
    Flux<ILyshraOpenAppHumanTaskAuditEntry> logAuditEntries(List<ILyshraOpenAppHumanTaskAuditEntry> entries);

    // ========================================================================
    // AUDIT TRAIL RETRIEVAL
    // ========================================================================

    /**
     * Gets the complete audit trail for a specific task.
     * Entries are returned in chronological order (oldest first).
     *
     * @param taskId the task identifier
     * @return flux of audit entries for the task
     */
    Flux<ILyshraOpenAppHumanTaskAuditEntry> getAuditTrailForTask(String taskId);

    /**
     * Gets the audit trail for a specific task in reverse chronological order.
     *
     * @param taskId the task identifier
     * @return flux of audit entries (newest first)
     */
    default Flux<ILyshraOpenAppHumanTaskAuditEntry> getAuditTrailForTaskDesc(String taskId) {
        return getAuditTrailForTask(taskId)
                .collectList()
                .flatMapMany(list -> {
                    java.util.Collections.reverse(list);
                    return Flux.fromIterable(list);
                });
    }

    /**
     * Gets the audit trail for a workflow instance.
     * Includes entries for all tasks in the workflow.
     *
     * @param workflowInstanceId the workflow instance identifier
     * @return flux of audit entries for the workflow
     */
    Flux<ILyshraOpenAppHumanTaskAuditEntry> getAuditTrailForWorkflow(String workflowInstanceId);

    /**
     * Gets the latest audit entry for a task.
     *
     * @param taskId the task identifier
     * @return the most recent audit entry
     */
    Mono<ILyshraOpenAppHumanTaskAuditEntry> getLatestAuditEntry(String taskId);

    /**
     * Gets a specific audit entry by ID.
     *
     * @param entryId the audit entry identifier
     * @return the audit entry if found
     */
    Mono<ILyshraOpenAppHumanTaskAuditEntry> getAuditEntry(String entryId);

    // ========================================================================
    // AUDIT QUERY OPERATIONS
    // ========================================================================

    /**
     * Queries audit entries by action type.
     *
     * @param action the audit action type
     * @return flux of matching audit entries
     */
    Flux<ILyshraOpenAppHumanTaskAuditEntry> getAuditEntriesByAction(AuditAction action);

    /**
     * Queries audit entries by actor.
     *
     * @param actorId the actor identifier
     * @return flux of audit entries by the actor
     */
    Flux<ILyshraOpenAppHumanTaskAuditEntry> getAuditEntriesByActor(String actorId);

    /**
     * Queries audit entries within a time range.
     *
     * @param from start of the range (inclusive)
     * @param to end of the range (exclusive)
     * @return flux of audit entries in the time range
     */
    Flux<ILyshraOpenAppHumanTaskAuditEntry> getAuditEntriesBetween(Instant from, Instant to);

    /**
     * Executes a complex audit query.
     *
     * @param query the audit query criteria
     * @return flux of matching audit entries
     */
    Flux<ILyshraOpenAppHumanTaskAuditEntry> queryAuditEntries(AuditQuery query);

    // ========================================================================
    // AUDIT STATISTICS
    // ========================================================================

    /**
     * Counts audit entries for a specific task.
     *
     * @param taskId the task identifier
     * @return count of audit entries
     */
    Mono<Long> countAuditEntriesForTask(String taskId);

    /**
     * Counts audit entries by action type.
     *
     * @param action the audit action type
     * @return count of audit entries
     */
    Mono<Long> countAuditEntriesByAction(AuditAction action);

    /**
     * Counts audit entries within a time range.
     *
     * @param from start of the range
     * @param to end of the range
     * @return count of audit entries
     */
    Mono<Long> countAuditEntriesBetween(Instant from, Instant to);

    /**
     * Gets audit statistics for reporting.
     *
     * @param from start of the reporting period
     * @param to end of the reporting period
     * @return audit statistics
     */
    Mono<AuditStatistics> getAuditStatistics(Instant from, Instant to);

    // ========================================================================
    // AUDIT EXPORT
    // ========================================================================

    /**
     * Exports audit entries to a structured format.
     *
     * @param query the query criteria
     * @param format the export format
     * @return export result containing the data
     */
    Mono<AuditExportResult> exportAuditEntries(AuditQuery query, AuditExportFormat format);

    // ========================================================================
    // AUDIT MAINTENANCE
    // ========================================================================

    /**
     * Deletes audit entries older than a specified time.
     * Use with caution - typically for data retention compliance.
     *
     * @param before delete entries before this time
     * @return count of deleted entries
     */
    Mono<Long> deleteAuditEntriesBefore(Instant before);

    /**
     * Archives audit entries to long-term storage.
     *
     * @param before archive entries before this time
     * @return count of archived entries
     */
    default Mono<Long> archiveAuditEntriesBefore(Instant before) {
        return Mono.just(0L);
    }

    // ========================================================================
    // SUPPORTING TYPES
    // ========================================================================

    /**
     * Query criteria for audit entries.
     */
    record AuditQuery(
            String taskId,
            String workflowInstanceId,
            String actorId,
            List<AuditAction> actions,
            Instant fromTimestamp,
            Instant toTimestamp,
            String tenantId,
            String correlationId,
            Integer limit,
            Integer offset,
            SortOrder sortOrder
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String taskId;
            private String workflowInstanceId;
            private String actorId;
            private List<AuditAction> actions;
            private Instant fromTimestamp;
            private Instant toTimestamp;
            private String tenantId;
            private String correlationId;
            private Integer limit;
            private Integer offset;
            private SortOrder sortOrder = SortOrder.ASCENDING;

            public Builder taskId(String taskId) { this.taskId = taskId; return this; }
            public Builder workflowInstanceId(String id) { this.workflowInstanceId = id; return this; }
            public Builder actorId(String actorId) { this.actorId = actorId; return this; }
            public Builder actions(List<AuditAction> actions) { this.actions = actions; return this; }
            public Builder fromTimestamp(Instant from) { this.fromTimestamp = from; return this; }
            public Builder toTimestamp(Instant to) { this.toTimestamp = to; return this; }
            public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
            public Builder correlationId(String id) { this.correlationId = id; return this; }
            public Builder limit(Integer limit) { this.limit = limit; return this; }
            public Builder offset(Integer offset) { this.offset = offset; return this; }
            public Builder sortOrder(SortOrder order) { this.sortOrder = order; return this; }

            public AuditQuery build() {
                return new AuditQuery(taskId, workflowInstanceId, actorId, actions,
                        fromTimestamp, toTimestamp, tenantId, correlationId, limit, offset, sortOrder);
            }
        }
    }

    /**
     * Sort order for query results.
     */
    enum SortOrder {
        ASCENDING,
        DESCENDING
    }

    /**
     * Export format options.
     */
    enum AuditExportFormat {
        JSON,
        CSV,
        XML
    }

    /**
     * Result of an audit export operation.
     */
    record AuditExportResult(
            String format,
            String data,
            long entryCount,
            Instant exportedAt,
            Map<String, Object> metadata
    ) {}

    /**
     * Audit statistics for reporting.
     */
    record AuditStatistics(
            long totalEntries,
            Map<AuditAction, Long> entriesByAction,
            Map<String, Long> entriesByActor,
            Map<String, Long> entriesByTask,
            Instant periodStart,
            Instant periodEnd,
            long uniqueTasks,
            long uniqueActors
    ) {}
}
