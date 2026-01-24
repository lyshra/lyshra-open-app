package com.lyshra.open.app.core.engine.version.migration.api;

import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Migration API service for controlled workflow migration operations.
 * Provides endpoints for admins to safely migrate running workflow instances.
 *
 * <p>Supports:</p>
 * <ul>
 *   <li>Single execution migration</li>
 *   <li>Bulk migration by workflow/version</li>
 *   <li>Targeted migration by execution IDs</li>
 *   <li>Dry-run validation</li>
 *   <li>Rollback operations</li>
 *   <li>Migration progress tracking</li>
 * </ul>
 */
public interface IMigrationApiService {

    // ==================== Single Migration Operations ====================

    /**
     * Migrates a single execution to the target version.
     *
     * @param request single migration request
     * @return migration response
     */
    Mono<MigrationResponse> migrateExecution(SingleMigrationRequest request);

    /**
     * Validates a single execution migration without applying changes.
     *
     * @param request single migration request
     * @return validation response
     */
    Mono<ValidationResponse> validateExecution(SingleMigrationRequest request);

    /**
     * Performs a dry-run migration to preview changes.
     *
     * @param request single migration request
     * @return dry-run response
     */
    Mono<DryRunResponse> dryRunExecution(SingleMigrationRequest request);

    // ==================== Bulk Migration Operations ====================

    /**
     * Initiates bulk migration for multiple executions.
     *
     * @param request bulk migration request
     * @return bulk migration response with job ID
     */
    Mono<BulkMigrationResponse> initiateBulkMigration(BulkMigrationRequest request);

    /**
     * Validates bulk migration without applying changes.
     *
     * @param request bulk migration request
     * @return bulk validation response
     */
    Mono<BulkValidationResponse> validateBulkMigration(BulkMigrationRequest request);

    /**
     * Gets the status of a bulk migration job.
     *
     * @param jobId bulk migration job ID
     * @return job status
     */
    Mono<BulkMigrationStatus> getBulkMigrationStatus(String jobId);

    /**
     * Cancels a running bulk migration job.
     *
     * @param jobId bulk migration job ID
     * @return cancellation result
     */
    Mono<CancellationResponse> cancelBulkMigration(String jobId);

    /**
     * Streams migration progress updates for a bulk job.
     *
     * @param jobId bulk migration job ID
     * @return stream of progress updates
     */
    Flux<MigrationProgressEvent> streamMigrationProgress(String jobId);

    // ==================== Targeted Migration Operations ====================

    /**
     * Migrates a specific list of executions.
     *
     * @param request targeted migration request
     * @return targeted migration response
     */
    Mono<TargetedMigrationResponse> migrateTargetedExecutions(TargetedMigrationRequest request);

    // ==================== Rollback Operations ====================

    /**
     * Rolls back a single migration.
     *
     * @param request rollback request
     * @return rollback response
     */
    Mono<RollbackResponse> rollbackMigration(RollbackRequest request);

    /**
     * Rolls back all migrations in a bulk job.
     *
     * @param jobId bulk migration job ID
     * @return bulk rollback response
     */
    Mono<BulkRollbackResponse> rollbackBulkMigration(String jobId);

    // ==================== Query Operations ====================

    /**
     * Gets migration candidates for a workflow.
     *
     * @param workflowId workflow identifier
     * @param targetVersion target version
     * @param filters optional filters
     * @return list of migration candidates
     */
    Mono<MigrationCandidatesResponse> getMigrationCandidates(
            String workflowId,
            IWorkflowVersion targetVersion,
            MigrationFilters filters);

    /**
     * Gets migration history for an execution.
     *
     * @param executionId execution identifier
     * @return migration history
     */
    Mono<MigrationHistoryResponse> getMigrationHistory(String executionId);

    /**
     * Gets all active bulk migration jobs.
     *
     * @return list of active jobs
     */
    Mono<List<BulkMigrationStatus>> getActiveBulkMigrations();

    // ==================== Request/Response Records ====================

    /**
     * Request for single execution migration.
     */
    record SingleMigrationRequest(
            String executionId,
            String targetVersion,
            String initiatedBy,
            boolean createBackup,
            boolean allowUnsafe,
            Optional<String> reason,
            Map<String, Object> metadata
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String executionId;
            private String targetVersion;
            private String initiatedBy = "system";
            private boolean createBackup = true;
            private boolean allowUnsafe = false;
            private String reason;
            private Map<String, Object> metadata = Map.of();

            public Builder executionId(String executionId) {
                this.executionId = executionId;
                return this;
            }

            public Builder targetVersion(String targetVersion) {
                this.targetVersion = targetVersion;
                return this;
            }

            public Builder initiatedBy(String initiatedBy) {
                this.initiatedBy = initiatedBy;
                return this;
            }

            public Builder createBackup(boolean createBackup) {
                this.createBackup = createBackup;
                return this;
            }

            public Builder allowUnsafe(boolean allowUnsafe) {
                this.allowUnsafe = allowUnsafe;
                return this;
            }

            public Builder reason(String reason) {
                this.reason = reason;
                return this;
            }

            public Builder metadata(Map<String, Object> metadata) {
                this.metadata = metadata;
                return this;
            }

            public SingleMigrationRequest build() {
                return new SingleMigrationRequest(executionId, targetVersion, initiatedBy,
                        createBackup, allowUnsafe, Optional.ofNullable(reason), metadata);
            }
        }
    }

    /**
     * Request for bulk migration.
     */
    record BulkMigrationRequest(
            String workflowId,
            String sourceVersionPattern,
            String targetVersion,
            String initiatedBy,
            MigrationFilters filters,
            BulkMigrationOptions options
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String workflowId;
            private String sourceVersionPattern = "*";
            private String targetVersion;
            private String initiatedBy = "system";
            private MigrationFilters filters = MigrationFilters.defaults();
            private BulkMigrationOptions options = BulkMigrationOptions.defaults();

            public Builder workflowId(String workflowId) {
                this.workflowId = workflowId;
                return this;
            }

            public Builder sourceVersionPattern(String sourceVersionPattern) {
                this.sourceVersionPattern = sourceVersionPattern;
                return this;
            }

            public Builder targetVersion(String targetVersion) {
                this.targetVersion = targetVersion;
                return this;
            }

            public Builder initiatedBy(String initiatedBy) {
                this.initiatedBy = initiatedBy;
                return this;
            }

            public Builder filters(MigrationFilters filters) {
                this.filters = filters;
                return this;
            }

            public Builder options(BulkMigrationOptions options) {
                this.options = options;
                return this;
            }

            public BulkMigrationRequest build() {
                return new BulkMigrationRequest(workflowId, sourceVersionPattern, targetVersion,
                        initiatedBy, filters, options);
            }
        }
    }

    /**
     * Request for targeted migration of specific executions.
     */
    record TargetedMigrationRequest(
            Set<String> executionIds,
            String targetVersion,
            String initiatedBy,
            BulkMigrationOptions options
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Set<String> executionIds = Set.of();
            private String targetVersion;
            private String initiatedBy = "system";
            private BulkMigrationOptions options = BulkMigrationOptions.defaults();

            public Builder executionIds(Set<String> executionIds) {
                this.executionIds = executionIds;
                return this;
            }

            public Builder targetVersion(String targetVersion) {
                this.targetVersion = targetVersion;
                return this;
            }

            public Builder initiatedBy(String initiatedBy) {
                this.initiatedBy = initiatedBy;
                return this;
            }

            public Builder options(BulkMigrationOptions options) {
                this.options = options;
                return this;
            }

            public TargetedMigrationRequest build() {
                return new TargetedMigrationRequest(executionIds, targetVersion, initiatedBy, options);
            }
        }
    }

    /**
     * Filters for selecting migration candidates.
     */
    record MigrationFilters(
            Set<String> executionStates,
            Set<String> currentSteps,
            Optional<Instant> startedAfter,
            Optional<Instant> startedBefore,
            boolean onlySafePoints,
            boolean excludePendingMigrations,
            Optional<Integer> maxResults
    ) {
        public static MigrationFilters defaults() {
            return new MigrationFilters(
                    Set.of("RUNNING", "PAUSED"),
                    Set.of(),
                    Optional.empty(),
                    Optional.empty(),
                    false,
                    true,
                    Optional.empty());
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Set<String> executionStates = Set.of("RUNNING", "PAUSED");
            private Set<String> currentSteps = Set.of();
            private Instant startedAfter;
            private Instant startedBefore;
            private boolean onlySafePoints = false;
            private boolean excludePendingMigrations = true;
            private Integer maxResults;

            public Builder executionStates(Set<String> states) {
                this.executionStates = states;
                return this;
            }

            public Builder currentSteps(Set<String> steps) {
                this.currentSteps = steps;
                return this;
            }

            public Builder startedAfter(Instant startedAfter) {
                this.startedAfter = startedAfter;
                return this;
            }

            public Builder startedBefore(Instant startedBefore) {
                this.startedBefore = startedBefore;
                return this;
            }

            public Builder onlySafePoints(boolean onlySafePoints) {
                this.onlySafePoints = onlySafePoints;
                return this;
            }

            public Builder excludePendingMigrations(boolean exclude) {
                this.excludePendingMigrations = exclude;
                return this;
            }

            public Builder maxResults(Integer maxResults) {
                this.maxResults = maxResults;
                return this;
            }

            public MigrationFilters build() {
                return new MigrationFilters(executionStates, currentSteps,
                        Optional.ofNullable(startedAfter), Optional.ofNullable(startedBefore),
                        onlySafePoints, excludePendingMigrations, Optional.ofNullable(maxResults));
            }
        }
    }

    /**
     * Options for bulk migration.
     */
    record BulkMigrationOptions(
            int batchSize,
            int maxConcurrency,
            boolean stopOnError,
            boolean createBackups,
            boolean allowUnsafe,
            boolean dryRunFirst,
            Optional<String> reason
    ) {
        public static BulkMigrationOptions defaults() {
            return new BulkMigrationOptions(50, 5, false, true, false, true, Optional.empty());
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private int batchSize = 50;
            private int maxConcurrency = 5;
            private boolean stopOnError = false;
            private boolean createBackups = true;
            private boolean allowUnsafe = false;
            private boolean dryRunFirst = true;
            private String reason;

            public Builder batchSize(int batchSize) {
                this.batchSize = batchSize;
                return this;
            }

            public Builder maxConcurrency(int maxConcurrency) {
                this.maxConcurrency = maxConcurrency;
                return this;
            }

            public Builder stopOnError(boolean stopOnError) {
                this.stopOnError = stopOnError;
                return this;
            }

            public Builder createBackups(boolean createBackups) {
                this.createBackups = createBackups;
                return this;
            }

            public Builder allowUnsafe(boolean allowUnsafe) {
                this.allowUnsafe = allowUnsafe;
                return this;
            }

            public Builder dryRunFirst(boolean dryRunFirst) {
                this.dryRunFirst = dryRunFirst;
                return this;
            }

            public Builder reason(String reason) {
                this.reason = reason;
                return this;
            }

            public BulkMigrationOptions build() {
                return new BulkMigrationOptions(batchSize, maxConcurrency, stopOnError,
                        createBackups, allowUnsafe, dryRunFirst, Optional.ofNullable(reason));
            }
        }
    }

    /**
     * Request for rollback.
     */
    record RollbackRequest(
            String executionId,
            String migrationId,
            String initiatedBy
    ) {}

    // ==================== Response Records ====================

    /**
     * Response for single migration.
     */
    record MigrationResponse(
            String executionId,
            String migrationId,
            MigrationStatus status,
            String sourceVersion,
            String targetVersion,
            String targetStep,
            Instant completedAt,
            List<String> warnings,
            Optional<String> errorMessage,
            Map<String, Object> metadata
    ) {
        public static MigrationResponse success(String executionId, String migrationId,
                                                String sourceVersion, String targetVersion,
                                                String targetStep, List<String> warnings) {
            return new MigrationResponse(executionId, migrationId, MigrationStatus.SUCCESS,
                    sourceVersion, targetVersion, targetStep, Instant.now(), warnings,
                    Optional.empty(), Map.of());
        }

        public static MigrationResponse failed(String executionId, String migrationId,
                                               String sourceVersion, String targetVersion,
                                               String errorMessage) {
            return new MigrationResponse(executionId, migrationId, MigrationStatus.FAILED,
                    sourceVersion, targetVersion, null, Instant.now(), List.of(),
                    Optional.of(errorMessage), Map.of());
        }
    }

    /**
     * Migration status enum.
     */
    enum MigrationStatus {
        PENDING,
        VALIDATING,
        IN_PROGRESS,
        SUCCESS,
        FAILED,
        ROLLED_BACK,
        CANCELLED,
        SKIPPED
    }

    /**
     * Response for validation.
     */
    record ValidationResponse(
            String executionId,
            boolean isValid,
            boolean isSafe,
            String recommendedTargetStep,
            List<String> errors,
            List<String> warnings,
            double riskLevel
    ) {}

    /**
     * Response for dry-run.
     */
    record DryRunResponse(
            String executionId,
            boolean wouldSucceed,
            String sourceVersion,
            String targetVersion,
            String currentStep,
            String projectedTargetStep,
            List<String> projectedChanges,
            List<String> warnings,
            Map<String, Object> projectedState
    ) {}

    /**
     * Response for bulk migration initiation.
     */
    record BulkMigrationResponse(
            String jobId,
            String workflowId,
            String targetVersion,
            int totalCandidates,
            Instant startedAt,
            BulkMigrationJobState state
    ) {}

    /**
     * Bulk migration job state.
     */
    enum BulkMigrationJobState {
        QUEUED,
        VALIDATING,
        IN_PROGRESS,
        PAUSED,
        COMPLETED,
        FAILED,
        CANCELLED,
        ROLLING_BACK
    }

    /**
     * Response for bulk validation.
     */
    record BulkValidationResponse(
            String workflowId,
            String targetVersion,
            int totalCandidates,
            int validCandidates,
            int invalidCandidates,
            List<ExecutionValidationSummary> validationSummaries
    ) {}

    /**
     * Summary of validation for a single execution.
     */
    record ExecutionValidationSummary(
            String executionId,
            String currentVersion,
            String currentStep,
            boolean isValid,
            boolean isSafe,
            List<String> issues
    ) {}

    /**
     * Status of a bulk migration job.
     */
    record BulkMigrationStatus(
            String jobId,
            String workflowId,
            String targetVersion,
            BulkMigrationJobState state,
            int totalExecutions,
            int processedExecutions,
            int successfulMigrations,
            int failedMigrations,
            int skippedMigrations,
            Instant startedAt,
            Optional<Instant> completedAt,
            double progressPercent,
            Optional<String> currentExecutionId,
            List<String> errors
    ) {}

    /**
     * Response for targeted migration.
     */
    record TargetedMigrationResponse(
            String jobId,
            int totalRequested,
            int successful,
            int failed,
            int skipped,
            List<MigrationResponse> results
    ) {}

    /**
     * Response for cancellation.
     */
    record CancellationResponse(
            String jobId,
            boolean cancelled,
            int executionsProcessedBeforeCancel,
            String message
    ) {}

    /**
     * Response for rollback.
     */
    record RollbackResponse(
            String executionId,
            String migrationId,
            boolean success,
            String restoredVersion,
            String restoredStep,
            Optional<String> errorMessage
    ) {}

    /**
     * Response for bulk rollback.
     */
    record BulkRollbackResponse(
            String jobId,
            int totalRolledBack,
            int failedRollbacks,
            List<RollbackResponse> results
    ) {}

    /**
     * Response for migration candidates query.
     */
    record MigrationCandidatesResponse(
            String workflowId,
            String targetVersion,
            int totalCandidates,
            List<MigrationCandidate> candidates
    ) {}

    /**
     * A single migration candidate.
     */
    record MigrationCandidate(
            String executionId,
            String currentVersion,
            String currentStep,
            String executionState,
            Instant startedAt,
            boolean isAtSafePoint,
            boolean hasBlockingIssues,
            List<String> warnings
    ) {}

    /**
     * Response for migration history.
     */
    record MigrationHistoryResponse(
            String executionId,
            String currentVersion,
            int totalMigrations,
            List<MigrationHistoryEntry> history
    ) {}

    /**
     * A single migration history entry.
     */
    record MigrationHistoryEntry(
            String migrationId,
            String fromVersion,
            String toVersion,
            String fromStep,
            String toStep,
            MigrationStatus status,
            Instant migratedAt,
            String initiatedBy,
            Optional<String> rollbackOf
    ) {}

    /**
     * Progress event for streaming updates.
     */
    record MigrationProgressEvent(
            String jobId,
            EventType eventType,
            String executionId,
            MigrationStatus status,
            int processedCount,
            int totalCount,
            double progressPercent,
            Optional<String> message,
            Instant timestamp
    ) {
        public enum EventType {
            JOB_STARTED,
            VALIDATION_STARTED,
            VALIDATION_COMPLETED,
            MIGRATION_STARTED,
            MIGRATION_COMPLETED,
            MIGRATION_FAILED,
            MIGRATION_SKIPPED,
            JOB_COMPLETED,
            JOB_FAILED,
            JOB_CANCELLED
        }
    }
}
