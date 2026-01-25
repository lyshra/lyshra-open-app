package com.lyshra.open.app.designer.controller;

import com.lyshra.open.app.designer.domain.ExecutionStatus;
import com.lyshra.open.app.designer.domain.WorkflowExecution;
import com.lyshra.open.app.designer.dto.ExecutionStatistics;
import com.lyshra.open.app.designer.dto.WorkflowExecutionRequest;
import com.lyshra.open.app.designer.service.WorkflowExecutionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * REST controller for workflow execution operations.
 */
@RestController
@RequestMapping("/api/v1/executions")
public class WorkflowExecutionController {

    private final WorkflowExecutionService executionService;

    public WorkflowExecutionController(WorkflowExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/workflows/{workflowId}")
    public Mono<ResponseEntity<WorkflowExecution>> startExecution(
            @PathVariable String workflowId,
            @RequestBody(required = false) WorkflowExecutionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = getUserId(userDetails);
        WorkflowExecutionRequest execRequest = request != null ? request : WorkflowExecutionRequest.builder().build();
        return executionService.startExecution(workflowId, execRequest, userId)
                .map(execution -> ResponseEntity.status(HttpStatus.CREATED).body(execution));
    }

    @GetMapping
    public Flux<WorkflowExecution> getAllExecutions(
            @RequestParam(required = false) String workflowDefinitionId,
            @RequestParam(required = false) String workflowVersionId,
            @RequestParam(required = false) ExecutionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        if (workflowDefinitionId != null && !workflowDefinitionId.isBlank()) {
            return executionService.getExecutionsByWorkflowDefinition(workflowDefinitionId);
        }
        if (workflowVersionId != null && !workflowVersionId.isBlank()) {
            return executionService.getExecutionsByWorkflowVersion(workflowVersionId);
        }
        if (status != null) {
            return executionService.getExecutionsByStatus(status);
        }
        if (startTime != null && endTime != null) {
            Instant start = startTime.toInstant(ZoneOffset.UTC);
            Instant end = endTime.toInstant(ZoneOffset.UTC);
            return executionService.getExecutionsByTimeRange(start, end);
        }
        return executionService.getAllExecutions();
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<WorkflowExecution>> getExecution(@PathVariable String id) {
        return executionService.getExecution(id)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/running")
    public Flux<WorkflowExecution> getRunningExecutions() {
        return executionService.getRunningExecutions();
    }

    @PutMapping("/{id}/cancel")
    public Mono<ResponseEntity<WorkflowExecution>> cancelExecution(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = getUserId(userDetails);
        return executionService.cancelExecution(id, userId)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{id}/retry")
    public Mono<ResponseEntity<WorkflowExecution>> retryExecution(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = getUserId(userDetails);
        return executionService.retryExecution(id, userId)
                .map(execution -> ResponseEntity.status(HttpStatus.CREATED).body(execution));
    }

    @GetMapping("/statistics")
    public Mono<ResponseEntity<ExecutionStatistics>> getStatistics() {
        return executionService.getStatistics()
                .map(ResponseEntity::ok);
    }

    @GetMapping("/statistics/workflows/{workflowId}")
    public Mono<ResponseEntity<ExecutionStatistics>> getWorkflowStatistics(@PathVariable String workflowId) {
        return executionService.getStatisticsByWorkflowDefinition(workflowId)
                .map(ResponseEntity::ok);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<WorkflowExecution> streamAllUpdates() {
        return executionService.subscribeToAllExecutionUpdates();
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<WorkflowExecution> streamExecutionUpdates(@PathVariable String id) {
        return executionService.subscribeToExecutionUpdates(id);
    }

    private String getUserId(UserDetails userDetails) {
        return userDetails != null ? userDetails.getUsername() : "anonymous";
    }
}
