package com.lyshra.open.app.core.engine.state;

import com.lyshra.open.app.integration.enumerations.LyshraOpenAppWorkflowExecutionState;
import com.lyshra.open.app.integration.models.workflow.LyshraOpenAppWorkflowInstanceState;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Interface for persisting and retrieving workflow instance state.
 * Implementations provide durable storage for workflow state,
 * enabling recovery after restarts or crashes.
 *
 * <p>Design Pattern: Repository Pattern
 * - Abstracts data access for workflow state
 * - Enables different storage backends (file, database, etc.)
 * - Provides reactive API for non-blocking operations
 *
 * <h2>Storage Guarantees</h2>
 * <ul>
 *   <li>Durability: State persists across application restarts</li>
 *   <li>Consistency: Updates are atomic per instance</li>
 *   <li>Isolation: Concurrent updates to same instance are serialized</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Save state when workflow suspends
 * stateStore.save(state)
 *     .doOnSuccess(saved -> log.info("State saved: {}", saved.getInstanceId()))
 *     .subscribe();
 *
 * // Load state for resumption
 * stateStore.findById(instanceId)
 *     .flatMap(state -> resumeWorkflow(state))
 *     .subscribe();
 *
 * // Find all suspended workflows
 * stateStore.findByStatus(WAITING)
 *     .filter(state -> state.getSuspensionDuration().orElse(Duration.ZERO).toHours() > 24)
 *     .flatMap(state -> escalateWorkflow(state))
 *     .subscribe();
 * }</pre>
 */
public interface ILyshraOpenAppWorkflowStateStore {

    // ========================================================================
    // CORE CRUD OPERATIONS
    // ========================================================================

    /**
     * Saves a workflow instance state.
     * Creates new or updates existing state based on instanceId.
     *
     * @param state the workflow state to save
     * @return the saved state (may include generated fields)
     */
    Mono<LyshraOpenAppWorkflowInstanceState> save(LyshraOpenAppWorkflowInstanceState state);

    /**
     * Finds a workflow instance state by ID.
     *
     * @param instanceId the workflow instance identifier
     * @return the state if found, empty otherwise
     */
    Mono<LyshraOpenAppWorkflowInstanceState> findById(String instanceId);

    /**
     * Deletes a workflow instance state.
     *
     * @param instanceId the workflow instance identifier
     * @return true if deleted, false if not found
     */
    Mono<Boolean> deleteById(String instanceId);

    /**
     * Checks if a workflow instance exists.
     *
     * @param instanceId the workflow instance identifier
     * @return true if exists
     */
    Mono<Boolean> exists(String instanceId);

    // ========================================================================
    // QUERY OPERATIONS
    // ========================================================================

    /**
     * Finds all workflow instances with a specific status.
     *
     * @param status the status to filter by
     * @return flux of matching workflow states
     */
    Flux<LyshraOpenAppWorkflowInstanceState> findByStatus(LyshraOpenAppWorkflowExecutionState status);

    /**
     * Finds all workflow instances with any of the specified statuses.
     *
     * @param statuses the statuses to filter by
     * @return flux of matching workflow states
     */
    Flux<LyshraOpenAppWorkflowInstanceState> findByStatusIn(List<LyshraOpenAppWorkflowExecutionState> statuses);

    /**
     * Finds all workflow instances for a specific workflow definition.
     *
     * @param workflowDefinitionId the workflow definition identifier
     * @return flux of matching workflow states
     */
    Flux<LyshraOpenAppWorkflowInstanceState> findByWorkflowDefinitionId(String workflowDefinitionId);

    /**
     * Finds workflow instances by business key.
     *
     * @param businessKey the business key to search for
     * @return flux of matching workflow states
     */
    Flux<LyshraOpenAppWorkflowInstanceState> findByBusinessKey(String businessKey);

    /**
     * Finds workflow instances by correlation ID.
     *
     * @param correlationId the correlation ID to search for
     * @return the state if found, empty otherwise
     */
    Mono<LyshraOpenAppWorkflowInstanceState> findByCorrelationId(String correlationId);

    /**
     * Finds workflow instances waiting for a specific human task.
     *
     * @param humanTaskId the human task identifier
     * @return the state if found, empty otherwise
     */
    Mono<LyshraOpenAppWorkflowInstanceState> findByHumanTaskId(String humanTaskId);

    /**
     * Finds all workflow instances for a specific tenant.
     *
     * @param tenantId the tenant identifier
     * @return flux of matching workflow states
     */
    Flux<LyshraOpenAppWorkflowInstanceState> findByTenantId(String tenantId);

    // ========================================================================
    // TIME-BASED QUERIES
    // ========================================================================

    /**
     * Finds workflow instances suspended before a certain time.
     * Useful for identifying stale or stuck workflows.
     *
     * @param before the cutoff time
     * @return flux of matching workflow states
     */
    Flux<LyshraOpenAppWorkflowInstanceState> findSuspendedBefore(Instant before);

    /**
     * Finds workflow instances scheduled for resumption before a certain time.
     * Useful for timer-based workflow resumption.
     *
     * @param before the cutoff time
     * @return flux of matching workflow states
     */
    Flux<LyshraOpenAppWorkflowInstanceState> findScheduledForResumeBefore(Instant before);

    /**
     * Finds workflow instances created within a time range.
     *
     * @param from start of range (inclusive)
     * @param to end of range (exclusive)
     * @return flux of matching workflow states
     */
    Flux<LyshraOpenAppWorkflowInstanceState> findCreatedBetween(Instant from, Instant to);

    // ========================================================================
    // BULK OPERATIONS
    // ========================================================================

    /**
     * Saves multiple workflow instance states.
     *
     * @param states the states to save
     * @return flux of saved states
     */
    Flux<LyshraOpenAppWorkflowInstanceState> saveAll(List<LyshraOpenAppWorkflowInstanceState> states);

    /**
     * Deletes all workflow instances with a specific status.
     * Typically used for cleanup of completed/cancelled workflows.
     *
     * @param status the status to delete
     * @return count of deleted instances
     */
    Mono<Long> deleteByStatus(LyshraOpenAppWorkflowExecutionState status);

    /**
     * Deletes workflow instances older than a certain time.
     * Typically used for retention policy enforcement.
     *
     * @param before the cutoff time
     * @param statuses only delete if in these statuses (usually terminal states)
     * @return count of deleted instances
     */
    Mono<Long> deleteOlderThan(Instant before, List<LyshraOpenAppWorkflowExecutionState> statuses);

    // ========================================================================
    // STATISTICS AND MONITORING
    // ========================================================================

    /**
     * Counts workflow instances by status.
     *
     * @param status the status to count
     * @return count of instances
     */
    Mono<Long> countByStatus(LyshraOpenAppWorkflowExecutionState status);

    /**
     * Gets all distinct workflow definition IDs with active instances.
     *
     * @return flux of workflow definition IDs
     */
    Flux<String> findDistinctWorkflowDefinitionIds();

    // ========================================================================
    // LOCKING (for distributed deployments)
    // ========================================================================

    /**
     * Attempts to acquire a lock on a workflow instance.
     * Used to prevent concurrent modifications in distributed deployments.
     *
     * @param instanceId the workflow instance identifier
     * @param lockOwnerId identifier of the lock requester
     * @param lockDuration how long to hold the lock
     * @return true if lock acquired, false if already locked
     */
    default Mono<Boolean> tryLock(String instanceId, String lockOwnerId, java.time.Duration lockDuration) {
        // Default implementation for single-node deployments
        return Mono.just(true);
    }

    /**
     * Releases a lock on a workflow instance.
     *
     * @param instanceId the workflow instance identifier
     * @param lockOwnerId identifier of the lock holder
     * @return true if lock released, false if not held by this owner
     */
    default Mono<Boolean> releaseLock(String instanceId, String lockOwnerId) {
        // Default implementation for single-node deployments
        return Mono.just(true);
    }

    /**
     * Extends a lock on a workflow instance.
     *
     * @param instanceId the workflow instance identifier
     * @param lockOwnerId identifier of the lock holder
     * @param extensionDuration how long to extend the lock
     * @return true if extended, false if not held by this owner
     */
    default Mono<Boolean> extendLock(String instanceId, String lockOwnerId, java.time.Duration extensionDuration) {
        // Default implementation for single-node deployments
        return Mono.just(true);
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    /**
     * Initializes the state store.
     * Called during application startup.
     *
     * @return completion signal
     */
    default Mono<Void> initialize() {
        return Mono.empty();
    }

    /**
     * Shuts down the state store.
     * Called during application shutdown.
     *
     * @return completion signal
     */
    default Mono<Void> shutdown() {
        return Mono.empty();
    }

    /**
     * Performs health check on the state store.
     *
     * @return true if healthy
     */
    default Mono<Boolean> healthCheck() {
        return Mono.just(true);
    }
}
