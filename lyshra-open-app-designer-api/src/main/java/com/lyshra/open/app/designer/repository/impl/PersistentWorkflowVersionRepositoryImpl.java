package com.lyshra.open.app.designer.repository.impl;

import com.lyshra.open.app.designer.domain.WorkflowVersion;
import com.lyshra.open.app.designer.domain.WorkflowVersionState;
import com.lyshra.open.app.designer.persistence.converter.WorkflowVersionConverter;
import com.lyshra.open.app.designer.persistence.repository.R2dbcWorkflowVersionRepository;
import com.lyshra.open.app.designer.repository.WorkflowVersionRepository;
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
 * Persistent implementation of WorkflowVersionRepository using R2DBC.
 * This is the primary implementation used when database is configured.
 */
@Slf4j
@Repository
@Primary
@RequiredArgsConstructor
public class PersistentWorkflowVersionRepositoryImpl implements WorkflowVersionRepository {

    private final R2dbcWorkflowVersionRepository r2dbcRepository;
    private final WorkflowVersionConverter converter;

    @Override
    public Mono<WorkflowVersion> save(WorkflowVersion version) {
        if (version.getId() == null || version.getId().isBlank()) {
            version.setId(UUID.randomUUID().toString());
        }
        if (version.getCreatedAt() == null) {
            version.setCreatedAt(Instant.now());
        }

        return r2dbcRepository.save(converter.toEntity(version))
                .map(converter::toDomain)
                .doOnSuccess(saved -> log.debug("Saved workflow version: {} (v{})",
                        saved.getId(), saved.getVersionNumber()))
                .doOnError(error -> log.error("Failed to save workflow version: {}", version.getId(), error));
    }

    @Override
    public Mono<Optional<WorkflowVersion>> findById(String id) {
        return r2dbcRepository.findById(id)
                .map(converter::toDomainOptional)
                .defaultIfEmpty(Optional.empty());
    }

    @Override
    public Flux<WorkflowVersion> findByWorkflowDefinitionId(String workflowDefinitionId) {
        return r2dbcRepository.findByWorkflowDefinitionIdOrderByCreatedAtDesc(workflowDefinitionId)
                .map(converter::toDomain);
    }

    @Override
    public Mono<Optional<WorkflowVersion>> findActiveByWorkflowDefinitionId(String workflowDefinitionId) {
        return r2dbcRepository.findByWorkflowDefinitionIdAndState(workflowDefinitionId, WorkflowVersionState.ACTIVE)
                .map(converter::toDomainOptional)
                .defaultIfEmpty(Optional.empty());
    }

    @Override
    public Flux<WorkflowVersion> findByState(WorkflowVersionState state) {
        return r2dbcRepository.findByState(state)
                .map(converter::toDomain);
    }

    @Override
    public Mono<Optional<WorkflowVersion>> findByWorkflowDefinitionIdAndVersionNumber(
            String workflowDefinitionId, String versionNumber) {
        return r2dbcRepository.findByWorkflowDefinitionIdAndVersionNumber(workflowDefinitionId, versionNumber)
                .map(converter::toDomainOptional)
                .defaultIfEmpty(Optional.empty());
    }

    @Override
    public Mono<Void> deleteById(String id) {
        return r2dbcRepository.deleteById(id)
                .doOnSuccess(v -> log.debug("Deleted workflow version: {}", id))
                .doOnError(error -> log.error("Failed to delete workflow version: {}", id, error));
    }

    @Override
    public Mono<Void> deleteByWorkflowDefinitionId(String workflowDefinitionId) {
        return r2dbcRepository.deleteByWorkflowDefinitionId(workflowDefinitionId)
                .doOnSuccess(v -> log.debug("Deleted all versions for workflow: {}", workflowDefinitionId))
                .doOnError(error -> log.error("Failed to delete versions for workflow: {}", workflowDefinitionId, error));
    }

    @Override
    public Mono<Long> countByWorkflowDefinitionId(String workflowDefinitionId) {
        return r2dbcRepository.countByWorkflowDefinitionId(workflowDefinitionId);
    }
}
