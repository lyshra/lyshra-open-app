package com.lyshra.open.app.designer.repository.impl;

import com.lyshra.open.app.designer.domain.WorkflowDefinition;
import com.lyshra.open.app.designer.domain.WorkflowLifecycleState;
import com.lyshra.open.app.designer.repository.WorkflowDefinitionRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of WorkflowDefinitionRepository.
 * This implementation is suitable for development and testing.
 * For production, replace with a database-backed implementation.
 */
@Repository
public class InMemoryWorkflowDefinitionRepositoryImpl implements WorkflowDefinitionRepository {

    private final Map<String, WorkflowDefinition> store = new ConcurrentHashMap<>();

    @Override
    public Mono<WorkflowDefinition> save(WorkflowDefinition definition) {
        return Mono.fromCallable(() -> {
            store.put(definition.getId(), definition);
            return definition;
        });
    }

    @Override
    public Mono<Optional<WorkflowDefinition>> findById(String id) {
        return Mono.fromCallable(() -> Optional.ofNullable(store.get(id)));
    }

    @Override
    public Flux<WorkflowDefinition> findAll() {
        return Flux.fromIterable(store.values());
    }

    @Override
    public Flux<WorkflowDefinition> findByOrganization(String organization) {
        return Flux.fromIterable(store.values())
                .filter(def -> organization.equals(def.getOrganization()));
    }

    @Override
    public Flux<WorkflowDefinition> findByLifecycleState(WorkflowLifecycleState state) {
        return Flux.fromIterable(store.values())
                .filter(def -> state.equals(def.getLifecycleState()));
    }

    @Override
    public Flux<WorkflowDefinition> findByCategory(String category) {
        return Flux.fromIterable(store.values())
                .filter(def -> category.equals(def.getCategory()));
    }

    @Override
    public Flux<WorkflowDefinition> searchByName(String namePattern) {
        String lowerPattern = namePattern.toLowerCase();
        return Flux.fromIterable(store.values())
                .filter(def -> def.getName() != null &&
                        def.getName().toLowerCase().contains(lowerPattern));
    }

    @Override
    public Mono<Void> deleteById(String id) {
        return Mono.fromRunnable(() -> store.remove(id));
    }

    @Override
    public Mono<Boolean> existsById(String id) {
        return Mono.fromCallable(() -> store.containsKey(id));
    }

    @Override
    public Mono<Long> count() {
        return Mono.fromCallable(() -> (long) store.size());
    }
}
