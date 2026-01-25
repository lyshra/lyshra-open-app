package com.lyshra.open.app.designer.repository.impl;

import com.lyshra.open.app.designer.domain.ExecutionStatus;
import com.lyshra.open.app.designer.domain.WorkflowExecution;
import com.lyshra.open.app.designer.persistence.converter.WorkflowExecutionConverter;
import com.lyshra.open.app.designer.persistence.repository.R2dbcWorkflowExecutionRepository;
import com.lyshra.open.app.designer.repository.WorkflowExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistent implementation of WorkflowExecutionRepository using R2DBC.
 * This is the primary implementation used when database is configured.
 */
@Slf4j
@Repository
@Primary
@RequiredArgsConstructor
public class PersistentWorkflowExecutionRepositoryImpl implements WorkflowExecutionRepository {

    private final R2dbcWorkflowExecutionRepository r2dbcRepository;
    private final WorkflowExecutionConverter converter;

    @Override
    public Mono<WorkflowExecution> save(WorkflowExecution execution) {
        if (execution.getId() == null || execution.getId().isBlank()) {
            execution.setId(UUID.randomUUID().toString());
        }
        if (execution.getStartedAt() == null &&
            (execution.getStatus() == ExecutionStatus.RUNNING || execution.getStatus() == ExecutionStatus.PENDING)) {
            execution.setStartedAt(Instant.now());
        }

        return r2dbcRepository.save(converter.toEntity(execution))
                .map(converter::toDomain)
                .doOnSuccess(saved -> log.debug("Saved workflow execution: {} (status: {})",
                        saved.getId(), saved.getStatus()))
                .doOnError(error -> log.error("Failed to save workflow execution: {}", execution.getId(), error));
    }

    @Override
    public Mono<Optional<WorkflowExecution>> findById(String id) {
        return r2dbcRepository.findById(id)
                .map(converter::toDomainOptional)
                .defaultIfEmpty(Optional.empty());
    }

    @Override
    public Flux<WorkflowExecution> findAll() {
        return r2dbcRepository.findAllByOrderByStartedAtDesc()
                .map(converter::toDomain);
    }

    @Override
    public Flux<WorkflowExecution> findByWorkflowDefinitionId(String workflowDefinitionId) {
        return r2dbcRepository.findByWorkflowDefinitionIdOrderByStartedAtDesc(workflowDefinitionId)
                .map(converter::toDomain);
    }

    @Override
    public Flux<WorkflowExecution> findByWorkflowVersionId(String workflowVersionId) {
        return r2dbcRepository.findByWorkflowVersionIdOrderByStartedAtDesc(workflowVersionId)
                .map(converter::toDomain);
    }

    @Override
    public Flux<WorkflowExecution> findByStatus(ExecutionStatus status) {
        return r2dbcRepository.findByStatusOrderByStartedAtDesc(status)
                .map(converter::toDomain);
    }

    @Override
    public Flux<WorkflowExecution> findByStartedAtBetween(Instant start, Instant end) {
        return r2dbcRepository.findByStartedAtBetweenOrderByStartedAtDesc(start, end)
                .map(converter::toDomain);
    }

    @Override
    public Flux<WorkflowExecution> findByCorrelationId(String correlationId) {
        return r2dbcRepository.findByCorrelationId(correlationId)
                .map(converter::toDomain);
    }

    @Override
    public Flux<WorkflowExecution> findRunning() {
        return r2dbcRepository.findRunning()
                .map(converter::toDomain);
    }

    @Override
    public Mono<Void> deleteById(String id) {
        return r2dbcRepository.deleteById(id)
                .doOnSuccess(v -> log.debug("Deleted workflow execution: {}", id))
                .doOnError(error -> log.error("Failed to delete workflow execution: {}", id, error));
    }

    @Override
    public Mono<Long> countByStatus(ExecutionStatus status) {
        return r2dbcRepository.countByStatus(status);
    }

    @Override
    public Mono<Long> count() {
        return r2dbcRepository.count();
    }
}
