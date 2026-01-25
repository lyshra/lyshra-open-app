package com.lyshra.open.app.designer.repository;

import com.lyshra.open.app.designer.domain.WorkflowVersion;
import com.lyshra.open.app.designer.domain.WorkflowVersionState;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Repository interface for workflow versions.
 */
public interface WorkflowVersionRepository {

    /**
     * Saves a workflow version.
     *
     * @param version the workflow version to save
     * @return Mono containing the saved workflow version
     */
    Mono<WorkflowVersion> save(WorkflowVersion version);

    /**
     * Finds a workflow version by ID.
     *
     * @param id the version ID
     * @return Mono containing the workflow version if found
     */
    Mono<Optional<WorkflowVersion>> findById(String id);

    /**
     * Finds all versions for a workflow definition.
     *
     * @param workflowDefinitionId the workflow definition ID
     * @return Flux of workflow versions
     */
    Flux<WorkflowVersion> findByWorkflowDefinitionId(String workflowDefinitionId);

    /**
     * Finds the active version for a workflow definition.
     *
     * @param workflowDefinitionId the workflow definition ID
     * @return Mono containing the active version if found
     */
    Mono<Optional<WorkflowVersion>> findActiveByWorkflowDefinitionId(String workflowDefinitionId);

    /**
     * Finds versions by state.
     *
     * @param state the version state
     * @return Flux of workflow versions
     */
    Flux<WorkflowVersion> findByState(WorkflowVersionState state);

    /**
     * Finds a specific version by workflow definition ID and version number.
     *
     * @param workflowDefinitionId the workflow definition ID
     * @param versionNumber the version number
     * @return Mono containing the workflow version if found
     */
    Mono<Optional<WorkflowVersion>> findByWorkflowDefinitionIdAndVersionNumber(
            String workflowDefinitionId, String versionNumber);

    /**
     * Deletes a workflow version by ID.
     *
     * @param id the version ID
     * @return Mono completing when delete is done
     */
    Mono<Void> deleteById(String id);

    /**
     * Deletes all versions for a workflow definition.
     *
     * @param workflowDefinitionId the workflow definition ID
     * @return Mono completing when delete is done
     */
    Mono<Void> deleteByWorkflowDefinitionId(String workflowDefinitionId);

    /**
     * Counts versions for a workflow definition.
     *
     * @param workflowDefinitionId the workflow definition ID
     * @return Mono containing the count
     */
    Mono<Long> countByWorkflowDefinitionId(String workflowDefinitionId);
}
