package com.lyshra.open.app.designer.repository.impl;

import com.lyshra.open.app.designer.domain.ExecutionStatus;
import com.lyshra.open.app.designer.domain.WorkflowExecution;
import com.lyshra.open.app.designer.repository.WorkflowExecutionRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of WorkflowExecutionRepository.
 * This implementation is suitable for development and testing.
 * For production, replace with a database-backed implementation.
 */
@Repository
public class InMemoryWorkflowExecutionRepositoryImpl implements WorkflowExecutionRepository {

    private static final Set<ExecutionStatus> RUNNING_STATUSES = Set.of(
            ExecutionStatus.PENDING,
            ExecutionStatus.RUNNING,
            ExecutionStatus.WAITING,
            ExecutionStatus.RETRYING
    );

    private final Map<String, WorkflowExecution> store = new ConcurrentHashMap<>();

    @Override
    public Mono<WorkflowExecution> save(WorkflowExecution execution) {
        return Mono.fromCallable(() -> {
            store.put(execution.getId(), execution);
            return execution;
        });
    }

    @Override
    public Mono<Optional<WorkflowExecution>> findById(String id) {
        return Mono.fromCallable(() -> Optional.ofNullable(store.get(id)));
    }

    @Override
    public Flux<WorkflowExecution> findAll() {
        return Flux.fromIterable(store.values());
    }

    @Override
    public Flux<WorkflowExecution> findByWorkflowDefinitionId(String workflowDefinitionId) {
        return Flux.fromIterable(store.values())
                .filter(exec -> workflowDefinitionId.equals(exec.getWorkflowDefinitionId()));
    }

    @Override
    public Flux<WorkflowExecution> findByWorkflowVersionId(String workflowVersionId) {
        return Flux.fromIterable(store.values())
                .filter(exec -> workflowVersionId.equals(exec.getWorkflowVersionId()));
    }

    @Override
    public Flux<WorkflowExecution> findByStatus(ExecutionStatus status) {
        return Flux.fromIterable(store.values())
                .filter(exec -> status.equals(exec.getStatus()));
    }

    @Override
    public Flux<WorkflowExecution> findByStartedAtBetween(Instant start, Instant end) {
        return Flux.fromIterable(store.values())
                .filter(exec -> exec.getStartedAt() != null &&
                        !exec.getStartedAt().isBefore(start) &&
                        !exec.getStartedAt().isAfter(end));
    }

    @Override
    public Flux<WorkflowExecution> findByCorrelationId(String correlationId) {
        return Flux.fromIterable(store.values())
                .filter(exec -> correlationId.equals(exec.getCorrelationId()));
    }

    @Override
    public Flux<WorkflowExecution> findRunning() {
        return Flux.fromIterable(store.values())
                .filter(exec -> RUNNING_STATUSES.contains(exec.getStatus()));
    }

    @Override
    public Mono<Void> deleteById(String id) {
        return Mono.fromRunnable(() -> store.remove(id));
    }

    @Override
    public Mono<Long> countByStatus(ExecutionStatus status) {
        return Flux.fromIterable(store.values())
                .filter(exec -> status.equals(exec.getStatus()))
                .count();
    }

    @Override
    public Mono<Long> count() {
        return Mono.fromCallable(() -> (long) store.size());
    }
}
