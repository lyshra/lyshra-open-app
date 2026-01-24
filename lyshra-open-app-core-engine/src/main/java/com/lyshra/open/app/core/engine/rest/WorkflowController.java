package com.lyshra.open.app.core.engine.rest;

import com.lyshra.open.app.core.engine.node.ILyshraOpenAppWorkflowResumptionService;
import com.lyshra.open.app.core.engine.rest.dto.ApiResponse;
import com.lyshra.open.app.core.engine.rest.dto.PagedResponse;
import com.lyshra.open.app.core.engine.rest.dto.WorkflowStateDto;
import com.lyshra.open.app.core.engine.state.ILyshraOpenAppWorkflowStateStore;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppWorkflowExecutionState;
import com.lyshra.open.app.integration.models.workflow.LyshraOpenAppWorkflowInstanceState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Workflow State Management.
 *
 * <p>Provides endpoints for querying workflow states, pausing workflows,
 * and resuming paused workflows. This enables external systems to monitor
 * and control workflow execution.</p>
 *
 * <h2>API Endpoints</h2>
 * <table border="1">
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>GET</td><td>/api/v1/workflows</td><td>List workflow instances with filters</td></tr>
 *   <tr><td>GET</td><td>/api/v1/workflows/{instanceId}</td><td>Get workflow instance by ID</td></tr>
 *   <tr><td>GET</td><td>/api/v1/workflows/status/{status}</td><td>Get workflows by status</td></tr>
 *   <tr><td>GET</td><td>/api/v1/workflows/waiting</td><td>Get workflows waiting for human tasks</td></tr>
 *   <tr><td>GET</td><td>/api/v1/workflows/task/{taskId}</td><td>Get workflow by human task ID</td></tr>
 *   <tr><td>POST</td><td>/api/v1/workflows/{instanceId}/pause</td><td>Pause a running workflow</td></tr>
 *   <tr><td>POST</td><td>/api/v1/workflows/{instanceId}/resume</td><td>Resume a paused workflow</td></tr>
 *   <tr><td>POST</td><td>/api/v1/workflows/{instanceId}/cancel</td><td>Cancel a workflow</td></tr>
 *   <tr><td>GET</td><td>/api/v1/workflows/stats</td><td>Get workflow statistics</td></tr>
 * </table>
 *
 * <h2>Workflow States</h2>
 * <ul>
 *   <li>RUNNING - Workflow is actively executing</li>
 *   <li>WAITING - Workflow is paused waiting for human task completion</li>
 *   <li>PAUSED - Workflow is administratively paused</li>
 *   <li>COMPLETED - Workflow has completed successfully</li>
 *   <li>FAILED - Workflow has failed with an error</li>
 *   <li>CANCELLED - Workflow was cancelled</li>
 * </ul>
 *
 * @see ILyshraOpenAppWorkflowStateStore
 * @see ILyshraOpenAppWorkflowResumptionService
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final ILyshraOpenAppWorkflowStateStore workflowStateStore;
    private final ILyshraOpenAppWorkflowResumptionService resumptionService;

    // ========================================================================
    // WORKFLOW QUERY ENDPOINTS
    // ========================================================================

    /**
     * Lists workflow instances with optional filtering.
     *
     * @param status filter by workflow status
     * @param definitionId filter by workflow definition
     * @param businessKey filter by business key
     * @param limit maximum results (default 100)
     * @param offset pagination offset (default 0)
     * @return paginated list of workflow instances
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<PagedResponse<WorkflowStateDto>>>> listWorkflows(
            @RequestParam(required = false) LyshraOpenAppWorkflowExecutionState status,
            @RequestParam(required = false) String definitionId,
            @RequestParam(required = false) String businessKey,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        log.debug("Listing workflows: status={}, definitionId={}, businessKey={}", status, definitionId, businessKey);

        var flux = status != null
                ? workflowStateStore.findByStatus(status)
                : definitionId != null
                ? workflowStateStore.findByWorkflowDefinitionId(definitionId)
                : businessKey != null
                ? workflowStateStore.findByBusinessKey(businessKey)
                : workflowStateStore.findByStatusIn(List.of(
                LyshraOpenAppWorkflowExecutionState.RUNNING,
                LyshraOpenAppWorkflowExecutionState.WAITING,
                LyshraOpenAppWorkflowExecutionState.PAUSED
        ));

        return flux
                .map(WorkflowStateDto::fromEntity)
                .collectList()
                .map(allWorkflows -> {
                    int end = Math.min(offset + limit, allWorkflows.size());
                    List<WorkflowStateDto> pagedWorkflows = allWorkflows.subList(
                            Math.min(offset, allWorkflows.size()),
                            end
                    );
                    return ResponseEntity.ok(ApiResponse.success(
                            PagedResponse.of(pagedWorkflows, allWorkflows.size(), offset, limit)
                    ));
                })
                .onErrorResume(e -> {
                    log.error("Error listing workflows", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(e.getMessage(), "LIST_WORKFLOWS_FAILED")));
                });
    }

    /**
     * Gets a specific workflow instance by ID.
     *
     * @param instanceId the workflow instance identifier
     * @return the workflow state
     */
    @GetMapping(value = "/{instanceId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<WorkflowStateDto>>> getWorkflow(@PathVariable String instanceId) {
        log.debug("Getting workflow: instanceId={}", instanceId);

        return workflowStateStore.findById(instanceId)
                .map(state -> ResponseEntity.ok(ApiResponse.success(WorkflowStateDto.fromEntity(state))))
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Workflow not found: " + instanceId, "WORKFLOW_NOT_FOUND")))
                .onErrorResume(e -> {
                    log.error("Error getting workflow: instanceId={}", instanceId, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(e.getMessage(), "GET_WORKFLOW_FAILED")));
                });
    }

    /**
     * Gets workflows by status.
     *
     * @param status the workflow status
     * @return list of workflows with the given status
     */
    @GetMapping(value = "/status/{status}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<List<WorkflowStateDto>>>> getWorkflowsByStatus(
            @PathVariable LyshraOpenAppWorkflowExecutionState status) {

        log.debug("Getting workflows by status: {}", status);

        return workflowStateStore.findByStatus(status)
                .map(WorkflowStateDto::fromEntity)
                .collectList()
                .map(workflows -> ResponseEntity.ok(ApiResponse.success(workflows)))
                .onErrorResume(e -> {
                    log.error("Error getting workflows by status: {}", status, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(e.getMessage(), "GET_WORKFLOWS_BY_STATUS_FAILED")));
                });
    }

    /**
     * Gets workflows waiting for human task completion.
     *
     * @return list of workflows in WAITING state
     */
    @GetMapping(value = "/waiting", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<List<WorkflowStateDto>>>> getWaitingWorkflows() {
        log.debug("Getting workflows waiting for human tasks");

        return workflowStateStore.findByStatus(LyshraOpenAppWorkflowExecutionState.WAITING)
                .map(WorkflowStateDto::fromEntity)
                .collectList()
                .map(workflows -> ResponseEntity.ok(ApiResponse.success(workflows)))
                .onErrorResume(e -> {
                    log.error("Error getting waiting workflows", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(e.getMessage(), "GET_WAITING_WORKFLOWS_FAILED")));
                });
    }

    /**
     * Gets the workflow associated with a human task.
     *
     * @param taskId the human task identifier
     * @return the workflow instance
     */
    @GetMapping(value = "/task/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<WorkflowStateDto>>> getWorkflowByTaskId(@PathVariable String taskId) {
        log.debug("Getting workflow by task ID: {}", taskId);

        return workflowStateStore.findByHumanTaskId(taskId)
                .map(state -> ResponseEntity.ok(ApiResponse.success(WorkflowStateDto.fromEntity(state))))
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Workflow not found for task: " + taskId, "WORKFLOW_NOT_FOUND")))
                .onErrorResume(e -> {
                    log.error("Error getting workflow by task ID: {}", taskId, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(e.getMessage(), "GET_WORKFLOW_BY_TASK_FAILED")));
                });
    }

    /**
     * Gets workflows suspended before a specific time (for monitoring SLA breaches).
     *
     * @param before get workflows suspended before this time
     * @return list of long-suspended workflows
     */
    @GetMapping(value = "/suspended-before", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<List<WorkflowStateDto>>>> getSuspendedWorkflowsBefore(
            @RequestParam Instant before) {

        log.debug("Getting workflows suspended before: {}", before);

        return workflowStateStore.findSuspendedBefore(before)
                .map(WorkflowStateDto::fromEntity)
                .collectList()
                .map(workflows -> ResponseEntity.ok(ApiResponse.success(workflows)))
                .onErrorResume(e -> {
                    log.error("Error getting suspended workflows", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(e.getMessage(), "GET_SUSPENDED_WORKFLOWS_FAILED")));
                });
    }

    // ========================================================================
    // WORKFLOW ACTION ENDPOINTS
    // ========================================================================

    /**
     * Pauses a running workflow.
     *
     * <p>The workflow will stop at its current position and can be resumed later.
     * This is useful for administrative interventions or debugging.</p>
     *
     * @param instanceId the workflow instance identifier
     * @param request the pause request with reason
     * @return the paused workflow state
     */
    @PostMapping(value = "/{instanceId}/pause", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<WorkflowStateDto>>> pauseWorkflow(
            @PathVariable String instanceId,
            @Valid @RequestBody WorkflowActionRequest request) {

        log.info("Pausing workflow: instanceId={}, reason={}", instanceId, request.getReason());

        return resumptionService.suspendWorkflow(instanceId, request.getReason())
                .flatMap(instance -> workflowStateStore.findById(instanceId))
                .map(state -> ResponseEntity.ok(ApiResponse.success(WorkflowStateDto.fromEntity(state))))
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Workflow not found: " + instanceId, "WORKFLOW_NOT_FOUND")))
                .onErrorResume(IllegalStateException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ApiResponse.error(e.getMessage(), "INVALID_WORKFLOW_STATE"))))
                .onErrorResume(e -> {
                    log.error("Error pausing workflow: instanceId={}", instanceId, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(e.getMessage(), "PAUSE_WORKFLOW_FAILED")));
                });
    }

    /**
     * Resumes a paused workflow.
     *
     * <p>The workflow will continue execution from where it was paused.
     * Note: Workflows waiting for human tasks should be resumed by completing
     * the associated task, not by calling this endpoint directly.</p>
     *
     * @param instanceId the workflow instance identifier
     * @param request the resume request with optional data to inject
     * @return the resumed workflow state
     */
    @PostMapping(value = "/{instanceId}/resume", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<WorkflowStateDto>>> resumeWorkflow(
            @PathVariable String instanceId,
            @Valid @RequestBody(required = false) WorkflowResumeRequest request) {

        log.info("Resuming workflow: instanceId={}", instanceId);

        return resumptionService.getWorkflowInstance(instanceId)
                .flatMap(instance -> {
                    if (request != null && request.getBranch() != null) {
                        return resumptionService.resumeWithBranch(
                                instance,
                                request.getBranch(),
                                request.getAdditionalData()
                        );
                    } else {
                        return resumptionService.resumePausedWorkflow(instance);
                    }
                })
                .then(workflowStateStore.findById(instanceId))
                .map(state -> ResponseEntity.ok(ApiResponse.success(WorkflowStateDto.fromEntity(state))))
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Workflow not found: " + instanceId, "WORKFLOW_NOT_FOUND")))
                .onErrorResume(IllegalStateException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ApiResponse.error(e.getMessage(), "INVALID_WORKFLOW_STATE"))))
                .onErrorResume(e -> {
                    log.error("Error resuming workflow: instanceId={}", instanceId, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(e.getMessage(), "RESUME_WORKFLOW_FAILED")));
                });
    }

    /**
     * Cancels a workflow.
     *
     * <p>The workflow will be terminated and cannot be resumed.
     * Any pending human tasks will also be cancelled.</p>
     *
     * @param instanceId the workflow instance identifier
     * @param request the cancellation request with reason
     * @return the cancelled workflow state
     */
    @PostMapping(value = "/{instanceId}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<WorkflowStateDto>>> cancelWorkflow(
            @PathVariable String instanceId,
            @Valid @RequestBody WorkflowActionRequest request) {

        log.info("Cancelling workflow: instanceId={}, reason={}", instanceId, request.getReason());

        return workflowStateStore.findById(instanceId)
                .flatMap(state -> {
                    if (state.isTerminal()) {
                        return Mono.error(new IllegalStateException(
                                "Workflow is already in terminal state: " + state.getStatus()));
                    }

                    LyshraOpenAppWorkflowInstanceState cancelledState = state
                            .withStatus(LyshraOpenAppWorkflowExecutionState.CANCELLED)
                            .withCompletedAt(Instant.now())
                            .withUpdatedAt(Instant.now());

                    return workflowStateStore.save(cancelledState);
                })
                .map(state -> ResponseEntity.ok(ApiResponse.success(WorkflowStateDto.fromEntity(state))))
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Workflow not found: " + instanceId, "WORKFLOW_NOT_FOUND")))
                .onErrorResume(IllegalStateException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ApiResponse.error(e.getMessage(), "INVALID_WORKFLOW_STATE"))))
                .onErrorResume(e -> {
                    log.error("Error cancelling workflow: instanceId={}", instanceId, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(e.getMessage(), "CANCEL_WORKFLOW_FAILED")));
                });
    }

    /**
     * Checks if a workflow can be resumed.
     *
     * @param instanceId the workflow instance identifier
     * @return whether the workflow can be resumed
     */
    @GetMapping(value = "/{instanceId}/can-resume", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<CanResumeResponse>>> canResumeWorkflow(@PathVariable String instanceId) {
        log.debug("Checking if workflow can resume: instanceId={}", instanceId);

        return resumptionService.canResume(instanceId)
                .map(canResume -> ResponseEntity.ok(ApiResponse.success(
                        CanResumeResponse.builder()
                                .instanceId(instanceId)
                                .canResume(canResume)
                                .build()
                )))
                .onErrorResume(e -> {
                    log.error("Error checking if workflow can resume: instanceId={}", instanceId, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(e.getMessage(), "CHECK_RESUME_FAILED")));
                });
    }

    // ========================================================================
    // STATISTICS ENDPOINT
    // ========================================================================

    /**
     * Gets workflow statistics.
     *
     * @return workflow count statistics by status
     */
    @GetMapping(value = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<WorkflowStatisticsDto>>> getStatistics() {
        log.debug("Getting workflow statistics");

        return Mono.zip(
                        workflowStateStore.countByStatus(LyshraOpenAppWorkflowExecutionState.RUNNING),
                        workflowStateStore.countByStatus(LyshraOpenAppWorkflowExecutionState.WAITING),
                        workflowStateStore.countByStatus(LyshraOpenAppWorkflowExecutionState.PAUSED),
                        workflowStateStore.countByStatus(LyshraOpenAppWorkflowExecutionState.COMPLETED),
                        workflowStateStore.countByStatus(LyshraOpenAppWorkflowExecutionState.FAILED),
                        workflowStateStore.countByStatus(LyshraOpenAppWorkflowExecutionState.CANCELLED)
                )
                .map(tuple -> {
                    WorkflowStatisticsDto stats = WorkflowStatisticsDto.builder()
                            .runningWorkflows(tuple.getT1())
                            .waitingWorkflows(tuple.getT2())
                            .pausedWorkflows(tuple.getT3())
                            .completedWorkflows(tuple.getT4())
                            .failedWorkflows(tuple.getT5())
                            .cancelledWorkflows(tuple.getT6())
                            .totalActive(tuple.getT1() + tuple.getT2() + tuple.getT3())
                            .timestamp(Instant.now())
                            .build();
                    return ResponseEntity.ok(ApiResponse.success(stats));
                })
                .onErrorResume(e -> {
                    log.error("Error getting workflow statistics", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(e.getMessage(), "GET_WORKFLOW_STATS_FAILED")));
                });
    }

    // ========================================================================
    // REQUEST/RESPONSE DTOs
    // ========================================================================

    /**
     * Request DTO for workflow actions (pause, cancel).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowActionRequest {
        @NotBlank(message = "Reason is required")
        private String reason;
        private String userId;
    }

    /**
     * Request DTO for workflow resume.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowResumeRequest {
        private String branch;
        private Map<String, Object> additionalData;
    }

    /**
     * Response DTO for can-resume check.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CanResumeResponse {
        private String instanceId;
        private boolean canResume;
    }

    /**
     * Workflow statistics response DTO.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowStatisticsDto {
        private long runningWorkflows;
        private long waitingWorkflows;
        private long pausedWorkflows;
        private long completedWorkflows;
        private long failedWorkflows;
        private long cancelledWorkflows;
        private long totalActive;
        private Instant timestamp;
    }
}
