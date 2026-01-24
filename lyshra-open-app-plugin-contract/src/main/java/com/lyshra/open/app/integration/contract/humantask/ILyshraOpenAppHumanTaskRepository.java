package com.lyshra.open.app.integration.contract.humantask;

import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskStatus;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository interface for persisting and retrieving human tasks.
 * Implementations can use any persistence mechanism.
 *
 * <p>Design Pattern: Repository Pattern
 * - Provides collection-like interface for domain objects
 * - Decouples persistence from business logic
 * - Enables testing with in-memory implementations
 */
public interface ILyshraOpenAppHumanTaskRepository {

    /**
     * Saves a human task (create or update).
     *
     * @param task the task to save
     * @return the saved task
     */
    Mono<ILyshraOpenAppHumanTask> save(ILyshraOpenAppHumanTask task);

    /**
     * Finds a human task by ID.
     *
     * @param taskId the task identifier
     * @return the task if found
     */
    Mono<Optional<ILyshraOpenAppHumanTask>> findById(String taskId);

    /**
     * Finds a human task by ID with pessimistic locking.
     *
     * @param taskId the task identifier
     * @return the task if found (locked for update)
     */
    Mono<Optional<ILyshraOpenAppHumanTask>> findByIdForUpdate(String taskId);

    /**
     * Finds tasks by workflow instance ID.
     *
     * @param workflowInstanceId the workflow instance identifier
     * @return stream of tasks
     */
    Flux<ILyshraOpenAppHumanTask> findByWorkflowInstanceId(String workflowInstanceId);

    /**
     * Finds tasks assigned to a specific user.
     *
     * @param userId the user identifier
     * @param statuses optional status filter
     * @return stream of tasks
     */
    Flux<ILyshraOpenAppHumanTask> findByAssignee(String userId, List<LyshraOpenAppHumanTaskStatus> statuses);

    /**
     * Finds tasks available to a user based on candidate groups.
     *
     * @param groups the user's groups
     * @param statuses optional status filter
     * @return stream of tasks
     */
    Flux<ILyshraOpenAppHumanTask> findByCandidateGroups(List<String> groups, List<LyshraOpenAppHumanTaskStatus> statuses);

    /**
     * Finds tasks claimed by a specific user.
     *
     * @param userId the user identifier
     * @return stream of claimed tasks
     */
    Flux<ILyshraOpenAppHumanTask> findClaimedBy(String userId);

    /**
     * Finds tasks by status.
     *
     * @param status the task status
     * @return stream of tasks
     */
    Flux<ILyshraOpenAppHumanTask> findByStatus(LyshraOpenAppHumanTaskStatus status);

    /**
     * Finds tasks by type.
     *
     * @param taskType the task type
     * @return stream of tasks
     */
    Flux<ILyshraOpenAppHumanTask> findByType(LyshraOpenAppHumanTaskType taskType);

    /**
     * Finds tasks that are past their due date.
     *
     * @param asOf the reference time
     * @return stream of overdue tasks
     */
    Flux<ILyshraOpenAppHumanTask> findOverdueTasks(Instant asOf);

    /**
     * Finds tasks that have timed out and need escalation processing.
     *
     * @param asOf the reference time
     * @return stream of tasks needing escalation
     */
    Flux<ILyshraOpenAppHumanTask> findTasksNeedingEscalation(Instant asOf);

    /**
     * Counts tasks by status for a user (assigned + candidate groups).
     *
     * @param userId the user identifier
     * @param groups the user's groups
     * @return map of status to count
     */
    Mono<Map<LyshraOpenAppHumanTaskStatus, Long>> countTasksByStatusForUser(String userId, List<String> groups);

    /**
     * Updates task status atomically.
     *
     * @param taskId the task identifier
     * @param newStatus the new status
     * @param resolvedBy who resolved the task
     * @param resultData result data from the resolution
     * @return true if updated
     */
    Mono<Boolean> updateStatus(
            String taskId,
            LyshraOpenAppHumanTaskStatus newStatus,
            String resolvedBy,
            Map<String, Object> resultData);

    /**
     * Claims a task for a user.
     *
     * @param taskId the task identifier
     * @param userId the user claiming the task
     * @return true if claimed successfully
     */
    Mono<Boolean> claim(String taskId, String userId);

    /**
     * Releases a claimed task.
     *
     * @param taskId the task identifier
     * @param userId the user releasing the task
     * @return true if released successfully
     */
    Mono<Boolean> unclaim(String taskId, String userId);

    /**
     * Delegates a task to another user.
     *
     * @param taskId the task identifier
     * @param fromUserId the user delegating
     * @param toUserId the user receiving delegation
     * @return true if delegated successfully
     */
    Mono<Boolean> delegate(String taskId, String fromUserId, String toUserId);

    /**
     * Adds a comment to a task.
     *
     * @param taskId the task identifier
     * @param comment the comment to add
     * @return the updated task
     */
    Mono<ILyshraOpenAppHumanTask> addComment(String taskId, ILyshraOpenAppHumanTaskComment comment);

    /**
     * Adds an audit entry to a task.
     *
     * @param taskId the task identifier
     * @param auditEntry the audit entry to add
     * @return the updated task
     */
    Mono<ILyshraOpenAppHumanTask> addAuditEntry(String taskId, ILyshraOpenAppHumanTaskAuditEntry auditEntry);

    /**
     * Deletes a task by ID.
     *
     * @param taskId the task identifier
     * @return true if deleted
     */
    Mono<Boolean> deleteById(String taskId);

    /**
     * Deletes completed tasks older than specified time.
     *
     * @param completedBefore delete tasks completed before this time
     * @return count of deleted tasks
     */
    Mono<Long> deleteCompletedBefore(Instant completedBefore);

    /**
     * Executes a complex query with filters.
     *
     * @param query the query parameters
     * @return stream of matching tasks
     */
    Flux<ILyshraOpenAppHumanTask> query(HumanTaskQuery query);

    /**
     * Query builder for complex human task queries.
     */
    interface HumanTaskQuery {

        Optional<String> getWorkflowInstanceId();

        Optional<List<LyshraOpenAppHumanTaskStatus>> getStatuses();

        Optional<List<LyshraOpenAppHumanTaskType>> getTypes();

        Optional<String> getAssignee();

        Optional<List<String>> getCandidateGroups();

        Optional<String> getClaimedBy();

        Optional<Integer> getMinPriority();

        Optional<Instant> getCreatedAfter();

        Optional<Instant> getCreatedBefore();

        Optional<Instant> getDueBefore();

        Optional<String> getTitleContains();

        int getOffset();

        int getLimit();

        String getSortField();

        boolean isAscending();
    }
}
