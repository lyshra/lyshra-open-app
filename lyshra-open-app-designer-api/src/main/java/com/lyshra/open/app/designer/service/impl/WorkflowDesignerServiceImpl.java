package com.lyshra.open.app.designer.service.impl;

import com.lyshra.open.app.designer.domain.*;
import com.lyshra.open.app.designer.dto.WorkflowDefinitionCreateRequest;
import com.lyshra.open.app.designer.dto.WorkflowDefinitionUpdateRequest;
import com.lyshra.open.app.designer.dto.WorkflowVersionCreateRequest;
import com.lyshra.open.app.designer.exception.ConflictException;
import com.lyshra.open.app.designer.exception.ResourceNotFoundException;
import com.lyshra.open.app.designer.repository.WorkflowDefinitionRepository;
import com.lyshra.open.app.designer.repository.WorkflowVersionRepository;
import com.lyshra.open.app.designer.service.WorkflowDesignerService;
import com.lyshra.open.app.designer.service.WorkflowValidationService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;

/**
 * Implementation of WorkflowDesignerService.
 * Handles CRUD operations for workflow definitions and versions.
 */
@Service
public class WorkflowDesignerServiceImpl implements WorkflowDesignerService {

    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final WorkflowValidationService workflowValidationService;

    public WorkflowDesignerServiceImpl(
            WorkflowDefinitionRepository workflowDefinitionRepository,
            WorkflowVersionRepository workflowVersionRepository,
            WorkflowValidationService workflowValidationService) {
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.workflowVersionRepository = workflowVersionRepository;
        this.workflowValidationService = workflowValidationService;
    }

    @Override
    public Mono<WorkflowDefinition> createWorkflowDefinition(WorkflowDefinitionCreateRequest request, String userId) {
        WorkflowDefinition definition = WorkflowDefinition.builder()
                .id(UUID.randomUUID().toString())
                .name(request.getName())
                .description(request.getDescription())
                .organization(request.getOrganization())
                .module(request.getModule())
                .category(request.getCategory())
                .tags(request.getTags() != null ? request.getTags() : new HashMap<>())
                .createdBy(userId)
                .createdAt(Instant.now())
                .updatedBy(userId)
                .updatedAt(Instant.now())
                .lifecycleState(WorkflowLifecycleState.DRAFT)
                .build();

        return workflowDefinitionRepository.save(definition);
    }

    @Override
    public Mono<WorkflowDefinition> updateWorkflowDefinition(
            String id, WorkflowDefinitionUpdateRequest request, String userId) {
        return workflowDefinitionRepository.findById(id)
                .flatMap(optDef -> optDef
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new ResourceNotFoundException("WorkflowDefinition", id))))
                .flatMap(definition -> {
                    Optional.ofNullable(request.getName()).ifPresent(definition::setName);
                    Optional.ofNullable(request.getDescription()).ifPresent(definition::setDescription);
                    Optional.ofNullable(request.getCategory()).ifPresent(definition::setCategory);
                    Optional.ofNullable(request.getTags()).ifPresent(definition::setTags);
                    definition.setUpdatedBy(userId);
                    definition.setUpdatedAt(Instant.now());
                    return workflowDefinitionRepository.save(definition);
                });
    }

    @Override
    public Mono<WorkflowDefinition> getWorkflowDefinition(String id) {
        return workflowDefinitionRepository.findById(id)
                .flatMap(optDef -> optDef
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new ResourceNotFoundException("WorkflowDefinition", id))));
    }

    @Override
    public Flux<WorkflowDefinition> getAllWorkflowDefinitions() {
        return workflowDefinitionRepository.findAll();
    }

    @Override
    public Flux<WorkflowDefinition> searchWorkflowDefinitions(String namePattern) {
        return workflowDefinitionRepository.searchByName(namePattern);
    }

    @Override
    public Flux<WorkflowDefinition> getWorkflowDefinitionsByOrganization(String organization) {
        return workflowDefinitionRepository.findByOrganization(organization);
    }

    @Override
    public Flux<WorkflowDefinition> getWorkflowDefinitionsByState(WorkflowLifecycleState state) {
        return workflowDefinitionRepository.findByLifecycleState(state);
    }

    @Override
    public Mono<Void> deleteWorkflowDefinition(String id) {
        return workflowDefinitionRepository.findById(id)
                .flatMap(optDef -> optDef
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new ResourceNotFoundException("WorkflowDefinition", id))))
                .flatMap(definition -> workflowVersionRepository.deleteByWorkflowDefinitionId(id)
                        .then(workflowDefinitionRepository.deleteById(id)));
    }

    @Override
    public Mono<WorkflowDefinition> changeLifecycleState(
            String id, WorkflowLifecycleState newState, String userId) {
        return workflowDefinitionRepository.findById(id)
                .flatMap(optDef -> optDef
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new ResourceNotFoundException("WorkflowDefinition", id))))
                .flatMap(definition -> {
                    validateStateTransition(definition.getLifecycleState(), newState);
                    definition.setLifecycleState(newState);
                    definition.setUpdatedBy(userId);
                    definition.setUpdatedAt(Instant.now());
                    return workflowDefinitionRepository.save(definition);
                });
    }

    @Override
    public Mono<WorkflowVersion> createWorkflowVersion(
            String workflowDefinitionId, WorkflowVersionCreateRequest request, String userId) {
        return workflowDefinitionRepository.findById(workflowDefinitionId)
                .flatMap(optDef -> optDef
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(
                                new ResourceNotFoundException("WorkflowDefinition", workflowDefinitionId))))
                .flatMap(definition -> workflowVersionRepository
                        .findByWorkflowDefinitionIdAndVersionNumber(workflowDefinitionId, request.getVersionNumber())
                        .flatMap(optVersion -> {
                            if (optVersion.isPresent()) {
                                return Mono.error(new ConflictException(
                                        "Version " + request.getVersionNumber() + " already exists"));
                            }
                            return createNewVersion(workflowDefinitionId, request, userId);
                        }));
    }

    private Mono<WorkflowVersion> createNewVersion(
            String workflowDefinitionId, WorkflowVersionCreateRequest request, String userId) {
        WorkflowVersion version = WorkflowVersion.builder()
                .id(UUID.randomUUID().toString())
                .workflowDefinitionId(workflowDefinitionId)
                .versionNumber(request.getVersionNumber())
                .description(request.getDescription())
                .startStepId(request.getStartStepId())
                .steps(request.getSteps() != null ? request.getSteps() : new ArrayList<>())
                .connections(request.getConnections() != null ? request.getConnections() : new HashMap<>())
                .contextRetention(Optional.ofNullable(request.getContextRetention())
                        .orElse(WorkflowContextRetention.FULL))
                .state(WorkflowVersionState.DRAFT)
                .createdBy(userId)
                .createdAt(Instant.now())
                .designerMetadata(Optional.ofNullable(request.getDesignerMetadata())
                        .orElse(DesignerMetadata.defaults()))
                .build();

        return workflowValidationService.validateWorkflow(version)
                .flatMap(validationResult -> {
                    if (!validationResult.isValid()) {
                        return Mono.error(new com.lyshra.open.app.designer.exception.ValidationException(
                                validationResult));
                    }
                    return workflowVersionRepository.save(version);
                });
    }

    @Override
    public Mono<WorkflowVersion> getWorkflowVersion(String versionId) {
        return workflowVersionRepository.findById(versionId)
                .flatMap(optVersion -> optVersion
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new ResourceNotFoundException("WorkflowVersion", versionId))));
    }

    @Override
    public Flux<WorkflowVersion> getWorkflowVersions(String workflowDefinitionId) {
        return workflowVersionRepository.findByWorkflowDefinitionId(workflowDefinitionId);
    }

    @Override
    public Mono<WorkflowVersion> getActiveVersion(String workflowDefinitionId) {
        return workflowVersionRepository.findActiveByWorkflowDefinitionId(workflowDefinitionId)
                .flatMap(optVersion -> optVersion
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new ResourceNotFoundException(
                                "Active WorkflowVersion for definition", workflowDefinitionId))));
    }

    @Override
    public Mono<WorkflowVersion> activateVersion(String versionId, String userId) {
        return workflowVersionRepository.findById(versionId)
                .flatMap(optVersion -> optVersion
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new ResourceNotFoundException("WorkflowVersion", versionId))))
                .flatMap(version -> {
                    if (!version.canBeActivated()) {
                        return Mono.error(new ConflictException(
                                "Version in state " + version.getState() + " cannot be activated"));
                    }
                    return deprecateCurrentActiveVersion(version.getWorkflowDefinitionId(), userId)
                            .then(activateVersionInternal(version, userId));
                });
    }

    private Mono<Void> deprecateCurrentActiveVersion(String workflowDefinitionId, String userId) {
        return workflowVersionRepository.findActiveByWorkflowDefinitionId(workflowDefinitionId)
                .flatMap(optVersion -> optVersion
                        .map(activeVersion -> {
                            activeVersion.setState(WorkflowVersionState.DEPRECATED);
                            activeVersion.setDeprecatedBy(userId);
                            activeVersion.setDeprecatedAt(Instant.now());
                            return workflowVersionRepository.save(activeVersion).then();
                        })
                        .orElse(Mono.empty()));
    }

    private Mono<WorkflowVersion> activateVersionInternal(WorkflowVersion version, String userId) {
        version.setState(WorkflowVersionState.ACTIVE);
        version.setActivatedBy(userId);
        version.setActivatedAt(Instant.now());
        return workflowVersionRepository.save(version)
                .flatMap(savedVersion -> updateDefinitionActiveVersion(
                        savedVersion.getWorkflowDefinitionId(), savedVersion.getId(), userId)
                        .thenReturn(savedVersion));
    }

    private Mono<WorkflowDefinition> updateDefinitionActiveVersion(
            String workflowDefinitionId, String versionId, String userId) {
        return workflowDefinitionRepository.findById(workflowDefinitionId)
                .flatMap(optDef -> optDef
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(
                                new ResourceNotFoundException("WorkflowDefinition", workflowDefinitionId))))
                .flatMap(definition -> {
                    definition.setActiveVersionId(versionId);
                    definition.setLifecycleState(WorkflowLifecycleState.ACTIVE);
                    definition.setUpdatedBy(userId);
                    definition.setUpdatedAt(Instant.now());
                    return workflowDefinitionRepository.save(definition);
                });
    }

    @Override
    public Mono<WorkflowVersion> deprecateVersion(String versionId, String userId) {
        return workflowVersionRepository.findById(versionId)
                .flatMap(optVersion -> optVersion
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new ResourceNotFoundException("WorkflowVersion", versionId))))
                .flatMap(version -> {
                    if (version.getState() != WorkflowVersionState.ACTIVE) {
                        return Mono.error(new ConflictException(
                                "Only active versions can be deprecated"));
                    }
                    version.setState(WorkflowVersionState.DEPRECATED);
                    version.setDeprecatedBy(userId);
                    version.setDeprecatedAt(Instant.now());
                    return workflowVersionRepository.save(version);
                });
    }

    @Override
    public Mono<WorkflowVersion> rollbackToVersion(
            String workflowDefinitionId, String targetVersionId, String userId) {
        return workflowVersionRepository.findById(targetVersionId)
                .flatMap(optVersion -> optVersion
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(
                                new ResourceNotFoundException("WorkflowVersion", targetVersionId))))
                .flatMap(targetVersion -> {
                    if (!targetVersion.getWorkflowDefinitionId().equals(workflowDefinitionId)) {
                        return Mono.error(new ConflictException(
                                "Version does not belong to the specified workflow definition"));
                    }
                    return activateVersion(targetVersionId, userId);
                });
    }

    @Override
    public Mono<WorkflowDefinition> duplicateWorkflowDefinition(String id, String newName, String userId) {
        return getWorkflowDefinition(id)
                .flatMap(original -> {
                    WorkflowDefinition duplicate = WorkflowDefinition.builder()
                            .id(UUID.randomUUID().toString())
                            .name(newName)
                            .description(original.getDescription())
                            .organization(original.getOrganization())
                            .module(original.getModule())
                            .category(original.getCategory())
                            .tags(original.getTags() != null ? new HashMap<>(original.getTags()) : new HashMap<>())
                            .createdBy(userId)
                            .createdAt(Instant.now())
                            .updatedBy(userId)
                            .updatedAt(Instant.now())
                            .lifecycleState(WorkflowLifecycleState.DRAFT)
                            .build();

                    return workflowDefinitionRepository.save(duplicate)
                            .flatMap(savedDuplicate -> duplicateVersions(id, savedDuplicate.getId(), userId)
                                    .thenReturn(savedDuplicate));
                });
    }

    private Mono<Void> duplicateVersions(String originalDefId, String newDefId, String userId) {
        return workflowVersionRepository.findByWorkflowDefinitionId(originalDefId)
                .flatMap(originalVersion -> {
                    WorkflowVersion duplicate = WorkflowVersion.builder()
                            .id(UUID.randomUUID().toString())
                            .workflowDefinitionId(newDefId)
                            .versionNumber(originalVersion.getVersionNumber())
                            .description(originalVersion.getDescription())
                            .startStepId(originalVersion.getStartStepId())
                            .steps(originalVersion.getSteps() != null ?
                                    new ArrayList<>(originalVersion.getSteps()) : new ArrayList<>())
                            .connections(originalVersion.getConnections() != null ?
                                    new HashMap<>(originalVersion.getConnections()) : new HashMap<>())
                            .contextRetention(originalVersion.getContextRetention())
                            .state(WorkflowVersionState.DRAFT)
                            .createdBy(userId)
                            .createdAt(Instant.now())
                            .designerMetadata(originalVersion.getDesignerMetadata())
                            .build();
                    return workflowVersionRepository.save(duplicate);
                })
                .then();
    }

    private void validateStateTransition(WorkflowLifecycleState currentState, WorkflowLifecycleState newState) {
        if (currentState == WorkflowLifecycleState.ARCHIVED && newState != WorkflowLifecycleState.ARCHIVED) {
            throw new ConflictException("Cannot transition from ARCHIVED state");
        }
    }
}
