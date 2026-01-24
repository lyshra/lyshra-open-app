package com.lyshra.open.app.core.engine.version.migration.api;

import com.lyshra.open.app.integration.models.version.WorkflowVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST Controller for workflow migration operations.
 * Provides endpoints for admins to safely migrate running workflow instances.
 *
 * <h2>Endpoints Summary:</h2>
 * <h3>Single Migration:</h3>
 * <ul>
 *   <li>POST /api/v1/migrations/executions/{executionId}/migrate - Migrate single execution</li>
 *   <li>POST /api/v1/migrations/executions/{executionId}/validate - Validate migration</li>
 *   <li>POST /api/v1/migrations/executions/{executionId}/dry-run - Dry-run migration</li>
 * </ul>
 *
 * <h3>Bulk Migration:</h3>
 * <ul>
 *   <li>POST /api/v1/migrations/bulk - Initiate bulk migration</li>
 *   <li>POST /api/v1/migrations/bulk/validate - Validate bulk migration</li>
 *   <li>GET /api/v1/migrations/bulk/{jobId} - Get job status</li>
 *   <li>DELETE /api/v1/migrations/bulk/{jobId} - Cancel job</li>
 *   <li>GET /api/v1/migrations/bulk/{jobId}/progress - Stream progress (SSE)</li>
 * </ul>
 *
 * <h3>Targeted Migration:</h3>
 * <ul>
 *   <li>POST /api/v1/migrations/targeted - Migrate specific executions</li>
 * </ul>
 *
 * <h3>Rollback:</h3>
 * <ul>
 *   <li>POST /api/v1/migrations/executions/{executionId}/rollback - Rollback migration</li>
 *   <li>POST /api/v1/migrations/bulk/{jobId}/rollback - Rollback bulk job</li>
 * </ul>
 *
 * <h3>Query:</h3>
 * <ul>
 *   <li>GET /api/v1/migrations/candidates - Get migration candidates</li>
 *   <li>GET /api/v1/migrations/executions/{executionId}/history - Get migration history</li>
 *   <li>GET /api/v1/migrations/bulk/active - Get active bulk jobs</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/migrations")
@RequiredArgsConstructor
public class WorkflowMigrationController {

    private final IMigrationApiService migrationService;

    // ==================== Single Migration Endpoints ====================

    /**
     * Migrate a single execution to a target version.
     *
     * @param executionId execution identifier
     * @param request migration request body
     * @return migration response
     */
    @PostMapping("/executions/{executionId}/migrate")
    public Mono<ResponseEntity<IMigrationApiService.MigrationResponse>> migrateExecution(
            @PathVariable String executionId,
            @RequestBody SingleMigrationRequestDto request) {

        IMigrationApiService.SingleMigrationRequest serviceRequest = IMigrationApiService.SingleMigrationRequest.builder()
                .executionId(executionId)
                .targetVersion(request.targetVersion())
                .initiatedBy(request.initiatedBy() != null ? request.initiatedBy() : "admin")
                .createBackup(request.createBackup() != null ? request.createBackup() : true)
                .allowUnsafe(request.allowUnsafe() != null ? request.allowUnsafe() : false)
                .reason(request.reason())
                .metadata(request.metadata() != null ? request.metadata() : Map.of())
                .build();

        return migrationService.migrateExecution(serviceRequest)
                .map(response -> {
                    if (response.status() == IMigrationApiService.MigrationStatus.SUCCESS) {
                        return ResponseEntity.ok(response);
                    } else {
                        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
                    }
                });
    }

    /**
     * Validate a migration without executing it.
     *
     * @param executionId execution identifier
     * @param request validation request
     * @return validation response
     */
    @PostMapping("/executions/{executionId}/validate")
    public Mono<ResponseEntity<IMigrationApiService.ValidationResponse>> validateExecution(
            @PathVariable String executionId,
            @RequestBody SingleMigrationRequestDto request) {

        IMigrationApiService.SingleMigrationRequest serviceRequest = IMigrationApiService.SingleMigrationRequest.builder()
                .executionId(executionId)
                .targetVersion(request.targetVersion())
                .initiatedBy(request.initiatedBy() != null ? request.initiatedBy() : "admin")
                .build();

        return migrationService.validateExecution(serviceRequest)
                .map(ResponseEntity::ok);
    }

    /**
     * Perform a dry-run migration to preview changes.
     *
     * @param executionId execution identifier
     * @param request dry-run request
     * @return dry-run response
     */
    @PostMapping("/executions/{executionId}/dry-run")
    public Mono<ResponseEntity<IMigrationApiService.DryRunResponse>> dryRunExecution(
            @PathVariable String executionId,
            @RequestBody SingleMigrationRequestDto request) {

        IMigrationApiService.SingleMigrationRequest serviceRequest = IMigrationApiService.SingleMigrationRequest.builder()
                .executionId(executionId)
                .targetVersion(request.targetVersion())
                .initiatedBy(request.initiatedBy() != null ? request.initiatedBy() : "admin")
                .build();

        return migrationService.dryRunExecution(serviceRequest)
                .map(ResponseEntity::ok);
    }

    // ==================== Bulk Migration Endpoints ====================

    /**
     * Initiate a bulk migration for multiple executions.
     *
     * @param request bulk migration request
     * @return bulk migration response with job ID
     */
    @PostMapping("/bulk")
    public Mono<ResponseEntity<IMigrationApiService.BulkMigrationResponse>> initiateBulkMigration(
            @RequestBody BulkMigrationRequestDto request) {

        IMigrationApiService.BulkMigrationRequest serviceRequest = IMigrationApiService.BulkMigrationRequest.builder()
                .workflowId(request.workflowId())
                .sourceVersionPattern(request.sourceVersionPattern() != null ? request.sourceVersionPattern() : "*")
                .targetVersion(request.targetVersion())
                .initiatedBy(request.initiatedBy() != null ? request.initiatedBy() : "admin")
                .filters(buildFilters(request.filters()))
                .options(buildOptions(request.options()))
                .build();

        return migrationService.initiateBulkMigration(serviceRequest)
                .map(response -> ResponseEntity.accepted().body(response));
    }

    /**
     * Validate a bulk migration without executing it.
     *
     * @param request bulk validation request
     * @return bulk validation response
     */
    @PostMapping("/bulk/validate")
    public Mono<ResponseEntity<IMigrationApiService.BulkValidationResponse>> validateBulkMigration(
            @RequestBody BulkMigrationRequestDto request) {

        IMigrationApiService.BulkMigrationRequest serviceRequest = IMigrationApiService.BulkMigrationRequest.builder()
                .workflowId(request.workflowId())
                .sourceVersionPattern(request.sourceVersionPattern() != null ? request.sourceVersionPattern() : "*")
                .targetVersion(request.targetVersion())
                .initiatedBy(request.initiatedBy() != null ? request.initiatedBy() : "admin")
                .filters(buildFilters(request.filters()))
                .options(buildOptions(request.options()))
                .build();

        return migrationService.validateBulkMigration(serviceRequest)
                .map(ResponseEntity::ok);
    }

    /**
     * Get the status of a bulk migration job.
     *
     * @param jobId job identifier
     * @return job status
     */
    @GetMapping("/bulk/{jobId}")
    public Mono<ResponseEntity<IMigrationApiService.BulkMigrationStatus>> getBulkMigrationStatus(
            @PathVariable String jobId) {

        return migrationService.getBulkMigrationStatus(jobId)
                .map(status -> {
                    if (status == null) {
                        return ResponseEntity.notFound().build();
                    }
                    return ResponseEntity.ok(status);
                });
    }

    /**
     * Cancel a running bulk migration job.
     *
     * @param jobId job identifier
     * @return cancellation response
     */
    @DeleteMapping("/bulk/{jobId}")
    public Mono<ResponseEntity<IMigrationApiService.CancellationResponse>> cancelBulkMigration(
            @PathVariable String jobId) {

        return migrationService.cancelBulkMigration(jobId)
                .map(response -> {
                    if (response.cancelled()) {
                        return ResponseEntity.ok(response);
                    } else {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                    }
                });
    }

    /**
     * Stream migration progress events (Server-Sent Events).
     *
     * @param jobId job identifier
     * @return stream of progress events
     */
    @GetMapping(value = "/bulk/{jobId}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<IMigrationApiService.MigrationProgressEvent> streamMigrationProgress(
            @PathVariable String jobId) {

        return migrationService.streamMigrationProgress(jobId);
    }

    /**
     * Get all active bulk migration jobs.
     *
     * @return list of active jobs
     */
    @GetMapping("/bulk/active")
    public Mono<ResponseEntity<List<IMigrationApiService.BulkMigrationStatus>>> getActiveBulkMigrations() {
        return migrationService.getActiveBulkMigrations()
                .map(ResponseEntity::ok);
    }

    // ==================== Targeted Migration Endpoints ====================

    /**
     * Migrate a specific list of executions.
     *
     * @param request targeted migration request
     * @return targeted migration response
     */
    @PostMapping("/targeted")
    public Mono<ResponseEntity<IMigrationApiService.TargetedMigrationResponse>> migrateTargetedExecutions(
            @RequestBody TargetedMigrationRequestDto request) {

        IMigrationApiService.TargetedMigrationRequest serviceRequest = IMigrationApiService.TargetedMigrationRequest.builder()
                .executionIds(request.executionIds())
                .targetVersion(request.targetVersion())
                .initiatedBy(request.initiatedBy() != null ? request.initiatedBy() : "admin")
                .options(buildOptions(request.options()))
                .build();

        return migrationService.migrateTargetedExecutions(serviceRequest)
                .map(response -> ResponseEntity.accepted().body(response));
    }

    // ==================== Rollback Endpoints ====================

    /**
     * Rollback a migration for a single execution.
     *
     * @param executionId execution identifier
     * @param request rollback request
     * @return rollback response
     */
    @PostMapping("/executions/{executionId}/rollback")
    public Mono<ResponseEntity<IMigrationApiService.RollbackResponse>> rollbackMigration(
            @PathVariable String executionId,
            @RequestBody RollbackRequestDto request) {

        IMigrationApiService.RollbackRequest serviceRequest = new IMigrationApiService.RollbackRequest(
                executionId,
                request.migrationId(),
                request.initiatedBy() != null ? request.initiatedBy() : "admin");

        return migrationService.rollbackMigration(serviceRequest)
                .map(response -> {
                    if (response.success()) {
                        return ResponseEntity.ok(response);
                    } else {
                        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
                    }
                });
    }

    /**
     * Rollback all migrations in a bulk job.
     *
     * @param jobId job identifier
     * @return bulk rollback response
     */
    @PostMapping("/bulk/{jobId}/rollback")
    public Mono<ResponseEntity<IMigrationApiService.BulkRollbackResponse>> rollbackBulkMigration(
            @PathVariable String jobId) {

        return migrationService.rollbackBulkMigration(jobId)
                .map(ResponseEntity::ok);
    }

    // ==================== Query Endpoints ====================

    /**
     * Get migration candidates for a workflow.
     *
     * @param workflowId workflow identifier
     * @param targetVersion target version
     * @param states filter by execution states
     * @param steps filter by current steps
     * @param onlySafePoints only include safe migration points
     * @param maxResults maximum results to return
     * @return migration candidates
     */
    @GetMapping("/candidates")
    public Mono<ResponseEntity<IMigrationApiService.MigrationCandidatesResponse>> getMigrationCandidates(
            @RequestParam String workflowId,
            @RequestParam String targetVersion,
            @RequestParam(required = false) Set<String> states,
            @RequestParam(required = false) Set<String> steps,
            @RequestParam(required = false, defaultValue = "false") boolean onlySafePoints,
            @RequestParam(required = false) Integer maxResults) {

        IMigrationApiService.MigrationFilters filters = IMigrationApiService.MigrationFilters.builder()
                .executionStates(states != null ? states : Set.of("RUNNING", "PAUSED"))
                .currentSteps(steps != null ? steps : Set.of())
                .onlySafePoints(onlySafePoints)
                .maxResults(maxResults)
                .build();

        return migrationService.getMigrationCandidates(
                workflowId, WorkflowVersion.parse(targetVersion), filters)
                .map(ResponseEntity::ok);
    }

    /**
     * Get migration history for an execution.
     *
     * @param executionId execution identifier
     * @return migration history
     */
    @GetMapping("/executions/{executionId}/history")
    public Mono<ResponseEntity<IMigrationApiService.MigrationHistoryResponse>> getMigrationHistory(
            @PathVariable String executionId) {

        return migrationService.getMigrationHistory(executionId)
                .map(ResponseEntity::ok);
    }

    // ==================== DTO Records ====================

    /**
     * DTO for single migration request.
     */
    public record SingleMigrationRequestDto(
            String targetVersion,
            String initiatedBy,
            Boolean createBackup,
            Boolean allowUnsafe,
            String reason,
            Map<String, Object> metadata
    ) {}

    /**
     * DTO for bulk migration request.
     */
    public record BulkMigrationRequestDto(
            String workflowId,
            String sourceVersionPattern,
            String targetVersion,
            String initiatedBy,
            MigrationFiltersDto filters,
            BulkMigrationOptionsDto options
    ) {}

    /**
     * DTO for migration filters.
     */
    public record MigrationFiltersDto(
            Set<String> executionStates,
            Set<String> currentSteps,
            Instant startedAfter,
            Instant startedBefore,
            Boolean onlySafePoints,
            Boolean excludePendingMigrations,
            Integer maxResults
    ) {}

    /**
     * DTO for bulk migration options.
     */
    public record BulkMigrationOptionsDto(
            Integer batchSize,
            Integer maxConcurrency,
            Boolean stopOnError,
            Boolean createBackups,
            Boolean allowUnsafe,
            Boolean dryRunFirst,
            String reason
    ) {}

    /**
     * DTO for targeted migration request.
     */
    public record TargetedMigrationRequestDto(
            Set<String> executionIds,
            String targetVersion,
            String initiatedBy,
            BulkMigrationOptionsDto options
    ) {}

    /**
     * DTO for rollback request.
     */
    public record RollbackRequestDto(
            String migrationId,
            String initiatedBy
    ) {}

    // ==================== Helper Methods ====================

    private IMigrationApiService.MigrationFilters buildFilters(MigrationFiltersDto dto) {
        if (dto == null) {
            return IMigrationApiService.MigrationFilters.defaults();
        }

        return IMigrationApiService.MigrationFilters.builder()
                .executionStates(dto.executionStates() != null ? dto.executionStates() : Set.of("RUNNING", "PAUSED"))
                .currentSteps(dto.currentSteps() != null ? dto.currentSteps() : Set.of())
                .startedAfter(dto.startedAfter())
                .startedBefore(dto.startedBefore())
                .onlySafePoints(dto.onlySafePoints() != null ? dto.onlySafePoints() : false)
                .excludePendingMigrations(dto.excludePendingMigrations() != null ? dto.excludePendingMigrations() : true)
                .maxResults(dto.maxResults())
                .build();
    }

    private IMigrationApiService.BulkMigrationOptions buildOptions(BulkMigrationOptionsDto dto) {
        if (dto == null) {
            return IMigrationApiService.BulkMigrationOptions.defaults();
        }

        return IMigrationApiService.BulkMigrationOptions.builder()
                .batchSize(dto.batchSize() != null ? dto.batchSize() : 50)
                .maxConcurrency(dto.maxConcurrency() != null ? dto.maxConcurrency() : 5)
                .stopOnError(dto.stopOnError() != null ? dto.stopOnError() : false)
                .createBackups(dto.createBackups() != null ? dto.createBackups() : true)
                .allowUnsafe(dto.allowUnsafe() != null ? dto.allowUnsafe() : false)
                .dryRunFirst(dto.dryRunFirst() != null ? dto.dryRunFirst() : true)
                .reason(dto.reason())
                .build();
    }
}
