package com.lyshra.open.app.designer.persistence.repository;

import com.lyshra.open.app.designer.domain.WorkflowVersionState;
import com.lyshra.open.app.designer.persistence.entity.WorkflowVersionEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Spring Data R2DBC repository for WorkflowVersionEntity.
 */
@Repository
public interface R2dbcWorkflowVersionRepository extends R2dbcRepository<WorkflowVersionEntity, String> {

    /**
     * Find all versions for a workflow definition.
     */
    Flux<WorkflowVersionEntity> findByWorkflowDefinitionIdOrderByCreatedAtDesc(String workflowDefinitionId);

    /**
     * Find the active version for a workflow definition.
     */
    Mono<WorkflowVersionEntity> findByWorkflowDefinitionIdAndState(String workflowDefinitionId, WorkflowVersionState state);

    /**
     * Find all versions by state.
     */
    Flux<WorkflowVersionEntity> findByState(WorkflowVersionState state);

    /**
     * Find a specific version by workflow and version number.
     */
    Mono<WorkflowVersionEntity> findByWorkflowDefinitionIdAndVersionNumber(String workflowDefinitionId, String versionNumber);

    /**
     * Delete all versions for a workflow definition.
     */
    @Modifying
    @Query("DELETE FROM workflow_versions WHERE workflow_definition_id = :workflowDefinitionId")
    Mono<Void> deleteByWorkflowDefinitionId(String workflowDefinitionId);

    /**
     * Count versions for a workflow definition.
     */
    Mono<Long> countByWorkflowDefinitionId(String workflowDefinitionId);

    /**
     * Find the latest version number for a workflow definition.
     */
    @Query("SELECT MAX(version_number) FROM workflow_versions WHERE workflow_definition_id = :workflowDefinitionId")
    Mono<String> findLatestVersionNumber(String workflowDefinitionId);
}
