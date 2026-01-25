package com.lyshra.open.app.designer.repository;

import com.lyshra.open.app.designer.domain.WorkflowDefinition;
import com.lyshra.open.app.designer.domain.WorkflowLifecycleState;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Repository interface for workflow definitions.
 */
public interface WorkflowDefinitionRepository {

    /**
     * Saves a workflow definition.
     *
     * @param definition the workflow definition to save
     * @return Mono containing the saved workflow definition
     */
    Mono<WorkflowDefinition> save(WorkflowDefinition definition);

    /**
     * Finds a workflow definition by ID.
     *
     * @param id the workflow definition ID
     * @return Mono containing the workflow definition if found
     */
    Mono<Optional<WorkflowDefinition>> findById(String id);

    /**
     * Finds all workflow definitions.
     *
     * @return Flux of all workflow definitions
     */
    Flux<WorkflowDefinition> findAll();

    /**
     * Finds workflow definitions by organization.
     *
     * @param organization the organization
     * @return Flux of workflow definitions
     */
    Flux<WorkflowDefinition> findByOrganization(String organization);

    /**
     * Finds workflow definitions by lifecycle state.
     *
     * @param state the lifecycle state
     * @return Flux of workflow definitions
     */
    Flux<WorkflowDefinition> findByLifecycleState(WorkflowLifecycleState state);

    /**
     * Finds workflow definitions by category.
     *
     * @param category the category
     * @return Flux of workflow definitions
     */
    Flux<WorkflowDefinition> findByCategory(String category);

    /**
     * Searches workflow definitions by name containing the given text.
     *
     * @param namePattern the name pattern to search for
     * @return Flux of matching workflow definitions
     */
    Flux<WorkflowDefinition> searchByName(String namePattern);

    /**
     * Deletes a workflow definition by ID.
     *
     * @param id the workflow definition ID
     * @return Mono completing when delete is done
     */
    Mono<Void> deleteById(String id);

    /**
     * Checks if a workflow definition exists by ID.
     *
     * @param id the workflow definition ID
     * @return Mono containing true if exists
     */
    Mono<Boolean> existsById(String id);

    /**
     * Counts all workflow definitions.
     *
     * @return Mono containing the count
     */
    Mono<Long> count();
}
