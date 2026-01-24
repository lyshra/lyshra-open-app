package com.lyshra.open.app.integration.contract.workflow;

import com.lyshra.open.app.integration.enumerations.LyshraOpenAppWorkflowExecutionState;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository interface for persisting and retrieving workflow instances.
 * Implementations can use any persistence mechanism (database, file, distributed cache).
 *
 * <p>Design Pattern: Repository Pattern
 * - Abstracts data access logic from business logic
 * - Enables different storage backends without changing business code
 * - Supports reactive streams for non-blocking operations
 *
 * <p>This is a plugin-extensible interface. Implementations should be provided
 * via plugins (e.g., MongoDB plugin, PostgreSQL plugin, Redis plugin).
 */
public interface ILyshraOpenAppWorkflowInstanceRepository {

    /**
     * Saves a workflow instance (create or update).
     *
     * @param instance the workflow instance to save
     * @return the saved instance with updated version
     */
    Mono<ILyshraOpenAppWorkflowInstance> save(ILyshraOpenAppWorkflowInstance instance);

    /**
     * Finds a workflow instance by its ID.
     *
     * @param instanceId the instance identifier
     * @return the workflow instance if found
     */
    Mono<Optional<ILyshraOpenAppWorkflowInstance>> findById(String instanceId);

    /**
     * Finds a workflow instance by its ID with pessimistic locking for update.
     *
     * @param instanceId the instance identifier
     * @return the workflow instance if found (locked for update)
     */
    Mono<Optional<ILyshraOpenAppWorkflowInstance>> findByIdForUpdate(String instanceId);

    /**
     * Finds workflow instances by business key.
     *
     * @param businessKey the business key
     * @return stream of matching workflow instances
     */
    Flux<ILyshraOpenAppWorkflowInstance> findByBusinessKey(String businessKey);

    /**
     * Finds workflow instances by correlation ID.
     *
     * @param correlationId the correlation identifier
     * @return stream of matching workflow instances
     */
    Flux<ILyshraOpenAppWorkflowInstance> findByCorrelationId(String correlationId);

    /**
     * Finds workflow instances by execution state.
     *
     * @param state the execution state to filter by
     * @return stream of matching workflow instances
     */
    Flux<ILyshraOpenAppWorkflowInstance> findByExecutionState(LyshraOpenAppWorkflowExecutionState state);

    /**
     * Finds workflow instances by workflow definition.
     *
     * @param workflowIdentifier the workflow definition identifier
     * @return stream of matching workflow instances
     */
    Flux<ILyshraOpenAppWorkflowInstance> findByWorkflowIdentifier(ILyshraOpenAppWorkflowIdentifier workflowIdentifier);

    /**
     * Finds workflow instances that have been waiting longer than the specified duration.
     * Useful for identifying stale workflows that may need intervention.
     *
     * @param suspendedBefore find instances suspended before this time
     * @return stream of matching workflow instances
     */
    Flux<ILyshraOpenAppWorkflowInstance> findWaitingInstancesSuspendedBefore(Instant suspendedBefore);

    /**
     * Finds workflow instances by tenant.
     *
     * @param tenantId the tenant identifier
     * @param states optional list of states to filter by
     * @return stream of matching workflow instances
     */
    Flux<ILyshraOpenAppWorkflowInstance> findByTenant(String tenantId, List<LyshraOpenAppWorkflowExecutionState> states);

    /**
     * Finds workflow instances by active human task ID.
     *
     * @param humanTaskId the human task identifier
     * @return the workflow instance if found
     */
    Mono<Optional<ILyshraOpenAppWorkflowInstance>> findByActiveHumanTaskId(String humanTaskId);

    /**
     * Counts workflow instances by state.
     *
     * @param state the execution state
     * @return count of instances in that state
     */
    Mono<Long> countByState(LyshraOpenAppWorkflowExecutionState state);

    /**
     * Deletes a workflow instance by ID.
     *
     * @param instanceId the instance identifier
     * @return true if deleted, false if not found
     */
    Mono<Boolean> deleteById(String instanceId);

    /**
     * Deletes completed workflow instances older than the specified time.
     * Useful for cleanup of historical data.
     *
     * @param completedBefore delete instances completed before this time
     * @return count of deleted instances
     */
    Mono<Long> deleteCompletedBefore(Instant completedBefore);

    /**
     * Updates the execution state of a workflow instance atomically.
     *
     * @param instanceId the instance identifier
     * @param newState the new execution state
     * @param expectedVersion the expected version for optimistic locking
     * @return true if updated, false if version mismatch or not found
     */
    Mono<Boolean> updateState(String instanceId, LyshraOpenAppWorkflowExecutionState newState, long expectedVersion);

    /**
     * Adds a checkpoint to the workflow instance.
     *
     * @param instanceId the instance identifier
     * @param checkpoint the checkpoint to add
     * @return the updated instance
     */
    Mono<ILyshraOpenAppWorkflowInstance> addCheckpoint(
            String instanceId,
            ILyshraOpenAppWorkflowInstance.ILyshraOpenAppWorkflowCheckpoint checkpoint);

    /**
     * Executes a complex query with filters.
     *
     * @param query the query parameters
     * @return stream of matching workflow instances
     */
    Flux<ILyshraOpenAppWorkflowInstance> query(WorkflowInstanceQuery query);

    /**
     * Query builder for complex workflow instance queries.
     */
    interface WorkflowInstanceQuery {

        Optional<String> getWorkflowName();

        Optional<List<LyshraOpenAppWorkflowExecutionState>> getStates();

        Optional<String> getTenantId();

        Optional<String> getInitiatedBy();

        Optional<Instant> getCreatedAfter();

        Optional<Instant> getCreatedBefore();

        Optional<Map<String, Object>> getMetadataFilters();

        int getOffset();

        int getLimit();

        String getSortField();

        boolean isAscending();
    }
}
