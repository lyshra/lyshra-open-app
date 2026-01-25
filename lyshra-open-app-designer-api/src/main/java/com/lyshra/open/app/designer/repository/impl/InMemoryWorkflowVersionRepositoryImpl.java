package com.lyshra.open.app.designer.repository.impl;

import com.lyshra.open.app.designer.domain.WorkflowVersion;
import com.lyshra.open.app.designer.domain.WorkflowVersionState;
import com.lyshra.open.app.designer.repository.WorkflowVersionRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of WorkflowVersionRepository.
 * This implementation is suitable for development and testing.
 * For production, replace with a database-backed implementation.
 */
@Repository
public class InMemoryWorkflowVersionRepositoryImpl implements WorkflowVersionRepository {

    private final Map<String, WorkflowVersion> store = new ConcurrentHashMap<>();

    @Override
    public Mono<WorkflowVersion> save(WorkflowVersion version) {
        return Mono.fromCallable(() -> {
            store.put(version.getId(), version);
            return version;
        });
    }

    @Override
    public Mono<Optional<WorkflowVersion>> findById(String id) {
        return Mono.fromCallable(() -> Optional.ofNullable(store.get(id)));
    }

    @Override
    public Flux<WorkflowVersion> findByWorkflowDefinitionId(String workflowDefinitionId) {
        return Flux.fromIterable(store.values())
                .filter(version -> workflowDefinitionId.equals(version.getWorkflowDefinitionId()));
    }

    @Override
    public Mono<Optional<WorkflowVersion>> findActiveByWorkflowDefinitionId(String workflowDefinitionId) {
        return Flux.fromIterable(store.values())
                .filter(version -> workflowDefinitionId.equals(version.getWorkflowDefinitionId()) &&
                        version.getState() == WorkflowVersionState.ACTIVE)
                .next()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    @Override
    public Flux<WorkflowVersion> findByState(WorkflowVersionState state) {
        return Flux.fromIterable(store.values())
                .filter(version -> state.equals(version.getState()));
    }

    @Override
    public Mono<Optional<WorkflowVersion>> findByWorkflowDefinitionIdAndVersionNumber(
            String workflowDefinitionId, String versionNumber) {
        return Flux.fromIterable(store.values())
                .filter(version -> workflowDefinitionId.equals(version.getWorkflowDefinitionId()) &&
                        versionNumber.equals(version.getVersionNumber()))
                .next()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    @Override
    public Mono<Void> deleteById(String id) {
        return Mono.fromRunnable(() -> store.remove(id));
    }

    @Override
    public Mono<Void> deleteByWorkflowDefinitionId(String workflowDefinitionId) {
        return Mono.fromRunnable(() ->
                store.entrySet().removeIf(entry ->
                        workflowDefinitionId.equals(entry.getValue().getWorkflowDefinitionId())));
    }

    @Override
    public Mono<Long> countByWorkflowDefinitionId(String workflowDefinitionId) {
        return Flux.fromIterable(store.values())
                .filter(version -> workflowDefinitionId.equals(version.getWorkflowDefinitionId()))
                .count();
    }
}
