package com.lyshra.open.app.designer.repository.impl;

import com.lyshra.open.app.designer.domain.WorkflowDefinition;
import com.lyshra.open.app.designer.domain.WorkflowLifecycleState;
import com.lyshra.open.app.designer.persistence.converter.WorkflowDefinitionConverter;
import com.lyshra.open.app.designer.persistence.repository.R2dbcWorkflowDefinitionRepository;
import com.lyshra.open.app.designer.repository.WorkflowDefinitionRepository;
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
 * Persistent implementation of WorkflowDefinitionRepository using R2DBC.
 * This is the primary implementation used when database is configured.
 */
@Slf4j
@Repository
@Primary
@RequiredArgsConstructor
public class PersistentWorkflowDefinitionRepositoryImpl implements WorkflowDefinitionRepository {

    private final R2dbcWorkflowDefinitionRepository r2dbcRepository;
    private final WorkflowDefinitionConverter converter;

    @Override
    public Mono<WorkflowDefinition> save(WorkflowDefinition definition) {
        if (definition.getId() == null || definition.getId().isBlank()) {
            definition.setId(UUID.randomUUID().toString());
        }
        if (definition.getCreatedAt() == null) {
            definition.setCreatedAt(Instant.now());
        }
        definition.setUpdatedAt(Instant.now());

        return r2dbcRepository.save(converter.toEntity(definition))
                .map(converter::toDomain)
                .doOnSuccess(saved -> log.debug("Saved workflow definition: {}", saved.getId()))
                .doOnError(error -> log.error("Failed to save workflow definition: {}", definition.getId(), error));
    }

    @Override
    public Mono<Optional<WorkflowDefinition>> findById(String id) {
        return r2dbcRepository.findById(id)
                .map(converter::toDomainOptional)
                .defaultIfEmpty(Optional.empty());
    }

    @Override
    public Flux<WorkflowDefinition> findAll() {
        return r2dbcRepository.findAllByOrderByUpdatedAtDesc()
                .map(converter::toDomain);
    }

    @Override
    public Flux<WorkflowDefinition> findByOrganization(String organization) {
        return r2dbcRepository.findByOrganization(organization)
                .map(converter::toDomain);
    }

    @Override
    public Flux<WorkflowDefinition> findByLifecycleState(WorkflowLifecycleState state) {
        return r2dbcRepository.findByLifecycleState(state)
                .map(converter::toDomain);
    }

    @Override
    public Flux<WorkflowDefinition> findByCategory(String category) {
        return r2dbcRepository.findByCategory(category)
                .map(converter::toDomain);
    }

    @Override
    public Flux<WorkflowDefinition> searchByName(String namePattern) {
        return r2dbcRepository.searchByNameOrDescription(namePattern)
                .map(converter::toDomain);
    }

    @Override
    public Mono<Void> deleteById(String id) {
        return r2dbcRepository.deleteById(id)
                .doOnSuccess(v -> log.debug("Deleted workflow definition: {}", id))
                .doOnError(error -> log.error("Failed to delete workflow definition: {}", id, error));
    }

    @Override
    public Mono<Boolean> existsById(String id) {
        return r2dbcRepository.existsById(id);
    }

    @Override
    public Mono<Long> count() {
        return r2dbcRepository.count();
    }
}
