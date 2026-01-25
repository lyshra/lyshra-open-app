package com.lyshra.open.app.designer.persistence.repository;

import com.lyshra.open.app.designer.domain.ExecutionStatus;
import com.lyshra.open.app.designer.persistence.entity.WorkflowExecutionEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Spring Data R2DBC repository for WorkflowExecutionEntity.
 */
@Repository
public interface R2dbcWorkflowExecutionRepository extends R2dbcRepository<WorkflowExecutionEntity, String> {

    /**
     * Find all executions ordered by started date descending.
     */
    Flux<WorkflowExecutionEntity> findAllByOrderByStartedAtDesc();

    /**
     * Find executions by workflow definition ID.
     */
    Flux<WorkflowExecutionEntity> findByWorkflowDefinitionIdOrderByStartedAtDesc(String workflowDefinitionId);

    /**
     * Find executions by workflow version ID.
     */
    Flux<WorkflowExecutionEntity> findByWorkflowVersionIdOrderByStartedAtDesc(String workflowVersionId);

    /**
     * Find executions by status.
     */
    Flux<WorkflowExecutionEntity> findByStatusOrderByStartedAtDesc(ExecutionStatus status);

    /**
     * Find executions started within a time range.
     */
    Flux<WorkflowExecutionEntity> findByStartedAtBetweenOrderByStartedAtDesc(Instant start, Instant end);

    /**
     * Find executions by correlation ID.
     */
    Flux<WorkflowExecutionEntity> findByCorrelationId(String correlationId);

    /**
     * Find running executions (PENDING or RUNNING status).
     */
    @Query("SELECT * FROM workflow_executions WHERE status IN ('PENDING', 'RUNNING') ORDER BY started_at DESC")
    Flux<WorkflowExecutionEntity> findRunning();

    /**
     * Count executions by status.
     */
    Mono<Long> countByStatus(ExecutionStatus status);

    /**
     * Count executions started today.
     */
    @Query("SELECT COUNT(*) FROM workflow_executions WHERE started_at >= :startOfDay")
    Mono<Long> countExecutionsToday(Instant startOfDay);

    /**
     * Count executions started this week.
     */
    @Query("SELECT COUNT(*) FROM workflow_executions WHERE started_at >= :startOfWeek")
    Mono<Long> countExecutionsThisWeek(Instant startOfWeek);

    /**
     * Find executions by parent execution ID (for sub-workflows).
     */
    Flux<WorkflowExecutionEntity> findByParentExecutionId(String parentExecutionId);
}
