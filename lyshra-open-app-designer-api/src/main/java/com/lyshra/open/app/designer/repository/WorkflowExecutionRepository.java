package com.lyshra.open.app.designer.repository;

import com.lyshra.open.app.designer.domain.ExecutionStatus;
import com.lyshra.open.app.designer.domain.WorkflowExecution;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository interface for workflow executions.
 */
public interface WorkflowExecutionRepository {

    /**
     * Saves a workflow execution.
     *
     * @param execution the workflow execution to save
     * @return Mono containing the saved workflow execution
     */
    Mono<WorkflowExecution> save(WorkflowExecution execution);

    /**
     * Finds a workflow execution by ID.
     *
     * @param id the execution ID
     * @return Mono containing the workflow execution if found
     */
    Mono<Optional<WorkflowExecution>> findById(String id);

    /**
     * Finds all workflow executions.
     *
     * @return Flux of all workflow executions
     */
    Flux<WorkflowExecution> findAll();

    /**
     * Finds executions by workflow definition ID.
     *
     * @param workflowDefinitionId the workflow definition ID
     * @return Flux of workflow executions
     */
    Flux<WorkflowExecution> findByWorkflowDefinitionId(String workflowDefinitionId);

    /**
     * Finds executions by workflow version ID.
     *
     * @param workflowVersionId the workflow version ID
     * @return Flux of workflow executions
     */
    Flux<WorkflowExecution> findByWorkflowVersionId(String workflowVersionId);

    /**
     * Finds executions by status.
     *
     * @param status the execution status
     * @return Flux of workflow executions
     */
    Flux<WorkflowExecution> findByStatus(ExecutionStatus status);

    /**
     * Finds executions started within a time range.
     *
     * @param start the start of the time range
     * @param end the end of the time range
     * @return Flux of workflow executions
     */
    Flux<WorkflowExecution> findByStartedAtBetween(Instant start, Instant end);

    /**
     * Finds executions by correlation ID.
     *
     * @param correlationId the correlation ID
     * @return Flux of workflow executions
     */
    Flux<WorkflowExecution> findByCorrelationId(String correlationId);

    /**
     * Finds running executions.
     *
     * @return Flux of running workflow executions
     */
    Flux<WorkflowExecution> findRunning();

    /**
     * Deletes a workflow execution by ID.
     *
     * @param id the execution ID
     * @return Mono completing when delete is done
     */
    Mono<Void> deleteById(String id);

    /**
     * Counts executions by status.
     *
     * @param status the execution status
     * @return Mono containing the count
     */
    Mono<Long> countByStatus(ExecutionStatus status);

    /**
     * Counts all executions.
     *
     * @return Mono containing the count
     */
    Mono<Long> count();
}
