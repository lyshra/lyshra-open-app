package com.lyshra.open.app.designer.service;

import com.lyshra.open.app.designer.domain.WorkflowDefinition;
import com.lyshra.open.app.designer.domain.WorkflowLifecycleState;
import com.lyshra.open.app.designer.domain.WorkflowVersion;
import com.lyshra.open.app.designer.dto.WorkflowDefinitionCreateRequest;
import com.lyshra.open.app.designer.dto.WorkflowDefinitionUpdateRequest;
import com.lyshra.open.app.designer.dto.WorkflowVersionCreateRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service interface for workflow designer operations.
 * Handles CRUD operations for workflow definitions and versions.
 */
public interface WorkflowDesignerService {

    /**
     * Creates a new workflow definition.
     *
     * @param request the creation request
     * @param userId the ID of the user creating the workflow
     * @return Mono containing the created workflow definition
     */
    Mono<WorkflowDefinition> createWorkflowDefinition(WorkflowDefinitionCreateRequest request, String userId);

    /**
     * Updates an existing workflow definition.
     *
     * @param id the workflow definition ID
     * @param request the update request
     * @param userId the ID of the user updating the workflow
     * @return Mono containing the updated workflow definition
     */
    Mono<WorkflowDefinition> updateWorkflowDefinition(String id, WorkflowDefinitionUpdateRequest request, String userId);

    /**
     * Gets a workflow definition by ID.
     *
     * @param id the workflow definition ID
     * @return Mono containing the workflow definition
     */
    Mono<WorkflowDefinition> getWorkflowDefinition(String id);

    /**
     * Gets all workflow definitions.
     *
     * @return Flux of all workflow definitions
     */
    Flux<WorkflowDefinition> getAllWorkflowDefinitions();

    /**
     * Searches workflow definitions by name.
     *
     * @param namePattern the name pattern to search for
     * @return Flux of matching workflow definitions
     */
    Flux<WorkflowDefinition> searchWorkflowDefinitions(String namePattern);

    /**
     * Gets workflow definitions by organization.
     *
     * @param organization the organization
     * @return Flux of workflow definitions
     */
    Flux<WorkflowDefinition> getWorkflowDefinitionsByOrganization(String organization);

    /**
     * Gets workflow definitions by lifecycle state.
     *
     * @param state the lifecycle state
     * @return Flux of workflow definitions
     */
    Flux<WorkflowDefinition> getWorkflowDefinitionsByState(WorkflowLifecycleState state);

    /**
     * Deletes a workflow definition.
     *
     * @param id the workflow definition ID
     * @return Mono completing when delete is done
     */
    Mono<Void> deleteWorkflowDefinition(String id);

    /**
     * Changes the lifecycle state of a workflow definition.
     *
     * @param id the workflow definition ID
     * @param newState the new lifecycle state
     * @param userId the ID of the user making the change
     * @return Mono containing the updated workflow definition
     */
    Mono<WorkflowDefinition> changeLifecycleState(String id, WorkflowLifecycleState newState, String userId);

    /**
     * Creates a new version for a workflow definition.
     *
     * @param workflowDefinitionId the workflow definition ID
     * @param request the version creation request
     * @param userId the ID of the user creating the version
     * @return Mono containing the created workflow version
     */
    Mono<WorkflowVersion> createWorkflowVersion(String workflowDefinitionId, WorkflowVersionCreateRequest request, String userId);

    /**
     * Gets a workflow version by ID.
     *
     * @param versionId the version ID
     * @return Mono containing the workflow version
     */
    Mono<WorkflowVersion> getWorkflowVersion(String versionId);

    /**
     * Gets all versions for a workflow definition.
     *
     * @param workflowDefinitionId the workflow definition ID
     * @return Flux of workflow versions
     */
    Flux<WorkflowVersion> getWorkflowVersions(String workflowDefinitionId);

    /**
     * Gets the active version for a workflow definition.
     *
     * @param workflowDefinitionId the workflow definition ID
     * @return Mono containing the active workflow version
     */
    Mono<WorkflowVersion> getActiveVersion(String workflowDefinitionId);

    /**
     * Activates a workflow version.
     *
     * @param versionId the version ID
     * @param userId the ID of the user activating the version
     * @return Mono containing the activated workflow version
     */
    Mono<WorkflowVersion> activateVersion(String versionId, String userId);

    /**
     * Deprecates a workflow version.
     *
     * @param versionId the version ID
     * @param userId the ID of the user deprecating the version
     * @return Mono containing the deprecated workflow version
     */
    Mono<WorkflowVersion> deprecateVersion(String versionId, String userId);

    /**
     * Rolls back to a previous version.
     *
     * @param workflowDefinitionId the workflow definition ID
     * @param targetVersionId the version ID to roll back to
     * @param userId the ID of the user performing the rollback
     * @return Mono containing the activated workflow version
     */
    Mono<WorkflowVersion> rollbackToVersion(String workflowDefinitionId, String targetVersionId, String userId);

    /**
     * Duplicates a workflow definition.
     *
     * @param id the workflow definition ID to duplicate
     * @param newName the name for the duplicate
     * @param userId the ID of the user creating the duplicate
     * @return Mono containing the duplicated workflow definition
     */
    Mono<WorkflowDefinition> duplicateWorkflowDefinition(String id, String newName, String userId);
}
