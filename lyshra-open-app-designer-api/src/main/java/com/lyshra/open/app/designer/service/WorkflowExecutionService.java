package com.lyshra.open.app.designer.service;

import com.lyshra.open.app.designer.domain.ExecutionStatus;
import com.lyshra.open.app.designer.domain.WorkflowExecution;
import com.lyshra.open.app.designer.dto.ExecutionStatistics;
import com.lyshra.open.app.designer.dto.WorkflowExecutionRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Service interface for workflow execution operations.
 * Handles execution, monitoring, and statistics.
 */
public interface WorkflowExecutionService {

    /**
     * Starts a workflow execution.
     *
     * @param workflowDefinitionId the workflow definition ID
     * @param request the execution request
     * @param userId the ID of the user starting the execution
     * @return Mono containing the started workflow execution
     */
    Mono<WorkflowExecution> startExecution(String workflowDefinitionId, WorkflowExecutionRequest request, String userId);

    /**
     * Gets a workflow execution by ID.
     *
     * @param executionId the execution ID
     * @return Mono containing the workflow execution
     */
    Mono<WorkflowExecution> getExecution(String executionId);

    /**
     * Gets all workflow executions.
     *
     * @return Flux of all workflow executions
     */
    Flux<WorkflowExecution> getAllExecutions();

    /**
     * Gets executions for a workflow definition.
     *
     * @param workflowDefinitionId the workflow definition ID
     * @return Flux of workflow executions
     */
    Flux<WorkflowExecution> getExecutionsByWorkflowDefinition(String workflowDefinitionId);

    /**
     * Gets executions for a workflow version.
     *
     * @param workflowVersionId the workflow version ID
     * @return Flux of workflow executions
     */
    Flux<WorkflowExecution> getExecutionsByWorkflowVersion(String workflowVersionId);

    /**
     * Gets executions by status.
     *
     * @param status the execution status
     * @return Flux of workflow executions
     */
    Flux<WorkflowExecution> getExecutionsByStatus(ExecutionStatus status);

    /**
     * Gets executions within a time range.
     *
     * @param start the start of the time range
     * @param end the end of the time range
     * @return Flux of workflow executions
     */
    Flux<WorkflowExecution> getExecutionsByTimeRange(Instant start, Instant end);

    /**
     * Gets running executions.
     *
     * @return Flux of running workflow executions
     */
    Flux<WorkflowExecution> getRunningExecutions();

    /**
     * Cancels a running execution.
     *
     * @param executionId the execution ID
     * @param userId the ID of the user cancelling the execution
     * @return Mono containing the cancelled workflow execution
     */
    Mono<WorkflowExecution> cancelExecution(String executionId, String userId);

    /**
     * Retries a failed execution.
     *
     * @param executionId the execution ID
     * @param userId the ID of the user retrying the execution
     * @return Mono containing the new workflow execution
     */
    Mono<WorkflowExecution> retryExecution(String executionId, String userId);

    /**
     * Gets execution statistics.
     *
     * @return Mono containing execution statistics
     */
    Mono<ExecutionStatistics> getStatistics();

    /**
     * Gets execution statistics for a workflow definition.
     *
     * @param workflowDefinitionId the workflow definition ID
     * @return Mono containing execution statistics
     */
    Mono<ExecutionStatistics> getStatisticsByWorkflowDefinition(String workflowDefinitionId);

    /**
     * Subscribes to execution updates for real-time monitoring.
     *
     * @param executionId the execution ID
     * @return Flux of execution updates
     */
    Flux<WorkflowExecution> subscribeToExecutionUpdates(String executionId);

    /**
     * Subscribes to all execution updates.
     *
     * @return Flux of all execution updates
     */
    Flux<WorkflowExecution> subscribeToAllExecutionUpdates();
}
