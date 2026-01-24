package com.lyshra.open.app.core.engine.rest;

import com.lyshra.open.app.core.engine.humantask.ILyshraOpenAppHumanTaskService;
import com.lyshra.open.app.core.engine.rest.dto.*;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTask;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Human Task Management.
 *
 * <p>Provides endpoints for querying, claiming, completing, approving, and rejecting
 * human tasks in workflow executions. This enables external systems and UIs to
 * interact with paused workflows waiting for human intervention.
 *
 * <h2>API Endpoints</h2>
 * <table border="1">
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>GET</td><td>/api/v1/tasks</td><td>List all tasks with optional filters</td></tr>
 *   <tr><td>GET</td><td>/api/v1/tasks/{taskId}</td><td>Get a specific task by ID</td></tr>
 *   <tr><td>GET</td><td>/api/v1/tasks/pending</td><td>Get pending tasks for a user</td></tr>
 *   <tr><td>GET</td><td>/api/v1/tasks/workflow/{workflowId}</td><td>Get tasks for a workflow instance</td></tr>
 *   <tr><td>POST</td><td>/api/v1/tasks/{taskId}/claim</td><td>Claim a task</td></tr>
 *   <tr><td>POST</td><td>/api/v1/tasks/{taskId}/unclaim</td><td>Release a claimed task</td></tr>
 *   <tr><td>POST</td><td>/api/v1/tasks/{taskId}/delegate</td><td>Delegate task to another user</td></tr>
 *   <tr><td>POST</td><td>/api/v1/tasks/{taskId}/complete</td><td>Complete task with data</td></tr>
 *   <tr><td>POST</td><td>/api/v1/tasks/{taskId}/approve</td><td>Approve a task</td></tr>
 *   <tr><td>POST</td><td>/api/v1/tasks/{taskId}/reject</td><td>Reject a task</td></tr>
 *   <tr><td>POST</td><td>/api/v1/tasks/{taskId}/cancel</td><td>Cancel a task</td></tr>
 *   <tr><td>POST</td><td>/api/v1/tasks/{taskId}/comment</td><td>Add comment to task</td></tr>
 *   <tr><td>GET</td><td>/api/v1/tasks/stats</td><td>Get task statistics</td></tr>
 * </table>
 *
 * <h2>Authentication</h2>
 * <p>This controller expects the caller to provide user identification via request parameters
 * or headers. In production, integrate with your authentication system to extract user info.</p>
 *
 * @see ILyshraOpenAppHumanTaskService
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class HumanTaskController {

    private final ILyshraOpenAppHumanTaskService humanTaskService;

    // ========================================================================
    // TASK QUERY ENDPOINTS
    // ========================================================================

    /**
     * Lists all tasks with optional filtering.
     *
     * <p>Query Parameters:</p>
     * <ul>
     *   <li>status - Filter by status (PENDING, IN_PROGRESS, etc.)</li>
     *   <li>workflowInstanceId - Filter by workflow instance</li>
     *   <li>assignee - Filter by assigned user</li>
     *   <li>limit - Maximum results (default 100)</li>
     *   <li>offset - Pagination offset (default 0)</li>
     * </ul>
     *
     * @return paginated list of tasks
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<PagedResponse<HumanTaskDto>>>> listTasks(
            @RequestParam(required = false) LyshraOpenAppHumanTaskStatus status,
            @RequestParam(required = false) String workflowInstanceId,
            @RequestParam(required = false) String assignee,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        log.debug("Listing tasks: status={}, workflowId={}, assignee={}, limit={}, offset={}",
                status, workflowInstanceId, assignee, limit, offset);

        Flux<ILyshraOpenAppHumanTask> taskFlux;

        if (status != null) {
            taskFlux = humanTaskService.listTasksByStatus(status);
        } else if (workflowInstanceId != null) {
            taskFlux = humanTaskService.listTasksByWorkflowInstance(workflowInstanceId);
        } else if (assignee != null) {
            taskFlux = humanTaskService.listTasksByAssignee(assignee);
        } else {
            taskFlux = humanTaskService.listTasks();
        }

        return taskFlux
                .map(HumanTaskDto::fromEntity)
                .collectList()
                .zipWith(humanTaskService.countTasks())
                .map(tuple -> {
                    List<HumanTaskDto> allTasks = tuple.getT1();
                    long totalCount = tuple.getT2();

                    // Apply pagination
                    int end = Math.min(offset + limit, allTasks.size());
                    List<HumanTaskDto> pagedTasks = allTasks.subList(
                            Math.min(offset, allTasks.size()),
                            end
                    );

                    return ResponseEntity.ok(ApiResponse.success(
                            PagedResponse.of(pagedTasks, totalCount, offset, limit)
                    ));
                })
                .onErrorResume(e -> {
                    log.error("Error listing tasks", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(e.getMessage(), "LIST_TASKS_FAILED")));
                });
    }

    /**
     * Gets a specific task by ID.
     *
     * @param taskId the task identifier
     * @return the task details
     */
    @GetMapping(value = "/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<HumanTaskDto>>> getTask(@PathVariable String taskId) {
        log.debug("Getting task: taskId={}", taskId);

        return humanTaskService.getTask(taskId)
                .map(task -> ResponseEntity.ok(ApiResponse.success(HumanTaskDto.fromEntity(task))))
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Task not found: " + taskId, "TASK_NOT_FOUND")))
                .onErrorResume(e -> {
                    log.error("Error getting task: taskId={}", taskId, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(e.getMessage(), "GET_TASK_FAILED")));
                });
    }

    /**
     * Gets pending tasks for a specific user.
     *
     * <p>Returns tasks that are either directly assigned to the user or
     * available through the user's group memberships.</p>
     *
     * @param userId the user identifier
     * @param groups comma-separated list of user's groups
     * @return list of pending tasks
     */
    @GetMapping(value = "/pending", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<List<HumanTaskDto>>>> getPendingTasksForUser(
            @RequestParam String userId,
            @RequestParam(required = false) List<String> groups) {

        log.debug("Getting pending tasks for user: userId={}, groups={}", userId, groups);

        return humanTaskService.listPendingTasksForUser(userId, groups)
                .map(HumanTaskDto::fromEntity)
                .collectList()
                .map(tasks -> ResponseEntity.ok(ApiResponse.success(tasks)))
                .onErrorResume(e -> {
                    log.error("Error getting pending tasks for user: userId={}", userId, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(e.getMessage(), "GET_PENDING_TASKS_FAILED")));
                });
    }

    /**
     * Gets all tasks for a specific workflow instance.
     *
     * @param workflowInstanceId the workflow instance identifier
     * @return list of tasks for the workflow
     */
    @GetMapping(value = "/workflow/{workflowInstanceId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<List<HumanTaskDto>>>> getTasksByWorkflow(
            @PathVariable String workflowInstanceId) {

        log.debug("Getting tasks for workflow: workflowInstanceId={}", workflowInstanceId);

        return humanTaskService.listTasksByWorkflowInstance(workflowInstanceId)
                .map(HumanTaskDto::fromEntity)
                .collectList()
                .map(tasks -> ResponseEntity.ok(ApiResponse.success(tasks)))
                .onErrorResume(e -> {
                    log.error("Error getting tasks for workflow: workflowInstanceId={}", workflowInstanceId, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(e.getMessage(), "GET_WORKFLOW_TASKS_FAILED")));
                });
    }

    /**
     * Gets overdue tasks.
     *
     * @return list of overdue tasks
     */
    @GetMapping(value = "/overdue", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<List<HumanTaskDto>>>> getOverdueTasks() {
        log.debug("Getting overdue tasks");

        return humanTaskService.listOverdueTasks()
                .map(HumanTaskDto::fromEntity)
                .collectList()
                .map(tasks -> ResponseEntity.ok(ApiResponse.success(tasks)))
                .onErrorResume(e -> {
                    log.error("Error getting overdue tasks", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(e.getMessage(), "GET_OVERDUE_TASKS_FAILED")));
                });
    }

    // ========================================================================
    // TASK ACTION ENDPOINTS
    // ========================================================================

    /**
     * Claims a task for a user.
     *
     * <p>Once claimed, only the claiming user can complete, approve, or reject the task
     * until they unclaim or delegate it.</p>
     *
     * @param taskId the task identifier
     * @param request the claim request containing user ID
     * @return the updated task
     */
    @PostMapping(value = "/{taskId}/claim", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<HumanTaskDto>>> claimTask(
            @PathVariable String taskId,
            @Valid @RequestBody TaskActionRequest request) {

        log.info("Claiming task: taskId={}, userId={}", taskId, request.getUserId());

        return humanTaskService.claimTask(taskId, request.getUserId())
                .map(task -> ResponseEntity.ok(ApiResponse.success(HumanTaskDto.fromEntity(task))))
                .onErrorResume(IllegalArgumentException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ApiResponse.error(e.getMessage(), "TASK_NOT_FOUND"))))
                .onErrorResume(IllegalStateException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ApiResponse.error(e.getMessage(), "INVALID_TASK_STATE"))))
                .onErrorResume(e -> {
                    log.error("Error claiming task: taskId={}", taskId, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(e.getMessage(), "CLAIM_TASK_FAILED")));
                });
    }

    /**
     * Releases a claimed task.
     *
     * @param taskId the task identifier
     * @param request the unclaim request containing user ID
     * @return the updated task
     */
    @PostMapping(value = "/{taskId}/unclaim", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<HumanTaskDto>>> unclaimTask(
            @PathVariable String taskId,
            @Valid @RequestBody TaskActionRequest request) {

        log.info("Unclaiming task: taskId={}, userId={}", taskId, request.getUserId());

        return humanTaskService.unclaimTask(taskId, request.getUserId())
                .map(task -> ResponseEntity.ok(ApiResponse.success(HumanTaskDto.fromEntity(task))))
                .onErrorResume(IllegalArgumentException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ApiResponse.error(e.getMessage(), "TASK_NOT_FOUND"))))
                .onErrorResume(IllegalStateException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ApiResponse.error(e.getMessage(), "INVALID_TASK_STATE"))))
                .onErrorResume(e -> {
                    log.error("Error unclaiming task: taskId={}", taskId, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(e.getMessage(), "UNCLAIM_TASK_FAILED")));
                });
    }

    /**
     * Delegates a task to another user.
     *
     * @param taskId the task identifier
     * @param request the delegation request containing from/to user IDs
     * @return the updated task
     */
    @PostMapping(value = "/{taskId}/delegate", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<HumanTaskDto>>> delegateTask(
            @PathVariable String taskId,
            @Valid @RequestBody TaskActionRequest request) {

        log.info("Delegating task: taskId={}, from={}, to={}",
                taskId, request.getUserId(), request.getDelegateToUserId());

        if (request.getDelegateToUserId() == null || request.getDelegateToUserId().isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(ApiResponse.error("Delegate target user ID is required", "MISSING_DELEGATE_USER")));
        }

        return humanTaskService.delegateTask(taskId, request.getUserId(), request.getDelegateToUserId())
                .map(task -> ResponseEntity.ok(ApiResponse.success(HumanTaskDto.fromEntity(task))))
                .onErrorResume(IllegalArgumentException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ApiResponse.error(e.getMessage(), "TASK_NOT_FOUND"))))
                .onErrorResume(e -> {
                    log.error("Error delegating task: taskId={}", taskId, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(e.getMessage(), "DELEGATE_TASK_FAILED")));
                });
    }

    /**
     * Completes a task with result data.
     *
     * <p>Used for tasks that require form submission or data input.
     * The result data is merged into the workflow context when the workflow resumes.</p>
     *
     * @param taskId the task identifier
     * @param request the completion request containing user ID and result data
     * @return the completed task
     */
    @PostMapping(value = "/{taskId}/complete", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<HumanTaskDto>>> completeTask(
            @PathVariable String taskId,
            @Valid @RequestBody TaskActionRequest request) {

        log.info("Completing task: taskId={}, userId={}", taskId, request.getUserId());

        return humanTaskService.completeTask(
                        taskId,
                        request.getUserId(),
                        request.getResultData(),
                        request.getReason())
                .map(task -> ResponseEntity.ok(ApiResponse.success(HumanTaskDto.fromEntity(task))))
                .onErrorResume(IllegalArgumentException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ApiResponse.error(e.getMessage(), "TASK_NOT_FOUND"))))
                .onErrorResume(IllegalStateException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ApiResponse.error(e.getMessage(), "INVALID_TASK_STATE"))))
                .onErrorResume(e -> {
                    log.error("Error completing task: taskId={}", taskId, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(e.getMessage(), "COMPLETE_TASK_FAILED")));
                });
    }

    /**
     * Approves a task.
     *
     * <p>Sets the task status to APPROVED and resumes the workflow
     * on the APPROVED branch.</p>
     *
     * @param taskId the task identifier
     * @param request the approval request containing user ID and optional reason
     * @return the approved task
     */
    @PostMapping(value = "/{taskId}/approve", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<HumanTaskDto>>> approveTask(
            @PathVariable String taskId,
            @Valid @RequestBody TaskActionRequest request) {

        log.info("Approving task: taskId={}, userId={}", taskId, request.getUserId());

        return humanTaskService.approveTask(
                        taskId,
                        request.getUserId(),
                        request.getReason(),
                        request.getResultData())
                .map(task -> ResponseEntity.ok(ApiResponse.success(HumanTaskDto.fromEntity(task))))
                .onErrorResume(IllegalArgumentException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ApiResponse.error(e.getMessage(), "TASK_NOT_FOUND"))))
                .onErrorResume(IllegalStateException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ApiResponse.error(e.getMessage(), "INVALID_TASK_STATE"))))
                .onErrorResume(e -> {
                    log.error("Error approving task: taskId={}", taskId, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(e.getMessage(), "APPROVE_TASK_FAILED")));
                });
    }

    /**
     * Rejects a task.
     *
     * <p>Sets the task status to REJECTED and resumes the workflow
     * on the REJECTED branch.</p>
     *
     * @param taskId the task identifier
     * @param request the rejection request containing user ID and reason
     * @return the rejected task
     */
    @PostMapping(value = "/{taskId}/reject", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<HumanTaskDto>>> rejectTask(
            @PathVariable String taskId,
            @Valid @RequestBody TaskActionRequest request) {

        log.info("Rejecting task: taskId={}, userId={}, reason={}",
                taskId, request.getUserId(), request.getReason());

        return humanTaskService.rejectTask(
                        taskId,
                        request.getUserId(),
                        request.getReason(),
                        request.getResultData())
                .map(task -> ResponseEntity.ok(ApiResponse.success(HumanTaskDto.fromEntity(task))))
                .onErrorResume(IllegalArgumentException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ApiResponse.error(e.getMessage(), "TASK_NOT_FOUND"))))
                .onErrorResume(IllegalStateException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ApiResponse.error(e.getMessage(), "INVALID_TASK_STATE"))))
                .onErrorResume(e -> {
                    log.error("Error rejecting task: taskId={}", taskId, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(e.getMessage(), "REJECT_TASK_FAILED")));
                });
    }

    /**
     * Cancels a task.
     *
     * <p>Sets the task status to CANCELLED and resumes the workflow
     * on the CANCELLED branch.</p>
     *
     * @param taskId the task identifier
     * @param request the cancellation request containing user ID and reason
     * @return the cancelled task
     */
    @PostMapping(value = "/{taskId}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<HumanTaskDto>>> cancelTask(
            @PathVariable String taskId,
            @Valid @RequestBody TaskActionRequest request) {

        log.info("Cancelling task: taskId={}, userId={}, reason={}",
                taskId, request.getUserId(), request.getReason());

        return humanTaskService.cancelTask(taskId, request.getUserId(), request.getReason())
                .map(task -> ResponseEntity.ok(ApiResponse.success(HumanTaskDto.fromEntity(task))))
                .onErrorResume(IllegalArgumentException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ApiResponse.error(e.getMessage(), "TASK_NOT_FOUND"))))
                .onErrorResume(IllegalStateException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ApiResponse.error(e.getMessage(), "INVALID_TASK_STATE"))))
                .onErrorResume(e -> {
                    log.error("Error cancelling task: taskId={}", taskId, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(e.getMessage(), "CANCEL_TASK_FAILED")));
                });
    }

    /**
     * Adds a comment to a task.
     *
     * @param taskId the task identifier
     * @param request the comment request
     * @param internal whether this is an internal comment (not visible to all users)
     * @return the updated task
     */
    @PostMapping(value = "/{taskId}/comment", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<HumanTaskDto>>> addComment(
            @PathVariable String taskId,
            @Valid @RequestBody TaskActionRequest request,
            @RequestParam(defaultValue = "false") boolean internal) {

        log.debug("Adding comment to task: taskId={}, userId={}", taskId, request.getUserId());

        if (request.getReason() == null || request.getReason().isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(ApiResponse.error("Comment content (reason field) is required", "MISSING_COMMENT")));
        }

        return humanTaskService.addComment(taskId, request.getUserId(), request.getReason(), internal)
                .map(task -> ResponseEntity.ok(ApiResponse.success(HumanTaskDto.fromEntity(task))))
                .onErrorResume(IllegalArgumentException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ApiResponse.error(e.getMessage(), "TASK_NOT_FOUND"))))
                .onErrorResume(e -> {
                    log.error("Error adding comment to task: taskId={}", taskId, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(e.getMessage(), "ADD_COMMENT_FAILED")));
                });
    }

    // ========================================================================
    // STATISTICS ENDPOINTS
    // ========================================================================

    /**
     * Gets task statistics.
     *
     * @return task count statistics by status
     */
    @GetMapping(value = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<TaskStatisticsDto>>> getStatistics() {
        log.debug("Getting task statistics");

        return Mono.zip(
                        humanTaskService.countTasks(),
                        humanTaskService.countTasksByStatus(LyshraOpenAppHumanTaskStatus.PENDING),
                        humanTaskService.countTasksByStatus(LyshraOpenAppHumanTaskStatus.IN_PROGRESS),
                        humanTaskService.countTasksByStatus(LyshraOpenAppHumanTaskStatus.APPROVED),
                        humanTaskService.countTasksByStatus(LyshraOpenAppHumanTaskStatus.REJECTED),
                        humanTaskService.countTasksByStatus(LyshraOpenAppHumanTaskStatus.COMPLETED),
                        humanTaskService.countOverdueTasks()
                )
                .map(tuple -> {
                    TaskStatisticsDto stats = TaskStatisticsDto.builder()
                            .totalTasks(tuple.getT1())
                            .pendingTasks(tuple.getT2())
                            .inProgressTasks(tuple.getT3())
                            .approvedTasks(tuple.getT4())
                            .rejectedTasks(tuple.getT5())
                            .completedTasks(tuple.getT6())
                            .overdueTasks(tuple.getT7())
                            .timestamp(Instant.now())
                            .build();
                    return ResponseEntity.ok(ApiResponse.success(stats));
                })
                .onErrorResume(e -> {
                    log.error("Error getting statistics", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(e.getMessage(), "GET_STATS_FAILED")));
                });
    }

    // ========================================================================
    // STATISTICS DTO
    // ========================================================================

    /**
     * Task statistics response DTO.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TaskStatisticsDto {
        private long totalTasks;
        private long pendingTasks;
        private long inProgressTasks;
        private long approvedTasks;
        private long rejectedTasks;
        private long completedTasks;
        private long overdueTasks;
        private Instant timestamp;
    }
}
