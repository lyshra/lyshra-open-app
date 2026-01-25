package com.lyshra.open.app.designer.persistence.repository;

import com.lyshra.open.app.designer.domain.WorkflowLifecycleState;
import com.lyshra.open.app.designer.persistence.entity.WorkflowDefinitionEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Spring Data R2DBC repository for WorkflowDefinitionEntity.
 */
@Repository
public interface R2dbcWorkflowDefinitionRepository extends R2dbcRepository<WorkflowDefinitionEntity, String> {

    /**
     * Find all workflow definitions by organization.
     */
    Flux<WorkflowDefinitionEntity> findByOrganization(String organization);

    /**
     * Find all workflow definitions by lifecycle state.
     */
    Flux<WorkflowDefinitionEntity> findByLifecycleState(WorkflowLifecycleState lifecycleState);

    /**
     * Find all workflow definitions by category.
     */
    Flux<WorkflowDefinitionEntity> findByCategory(String category);

    /**
     * Search workflow definitions by name (case-insensitive).
     */
    @Query("SELECT * FROM workflow_definitions WHERE LOWER(name) LIKE LOWER(CONCAT('%', :namePattern, '%'))")
    Flux<WorkflowDefinitionEntity> searchByName(String namePattern);

    /**
     * Search workflow definitions by name or description.
     */
    @Query("SELECT * FROM workflow_definitions WHERE LOWER(name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Flux<WorkflowDefinitionEntity> searchByNameOrDescription(String searchTerm);

    /**
     * Find all workflow definitions ordered by updated date.
     */
    Flux<WorkflowDefinitionEntity> findAllByOrderByUpdatedAtDesc();

    /**
     * Check if a workflow with the same name exists in the organization.
     */
    Mono<Boolean> existsByOrganizationAndModuleAndName(String organization, String module, String name);
}
