package com.lyshra.open.app.core.engine.humantask;

import com.lyshra.open.app.core.engine.assignment.AssignmentResult;
import com.lyshra.open.app.core.engine.assignment.AssignmentStrategyConfig;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTask;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskProcessor.ILyshraOpenAppHumanTaskConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskStatus;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service interface for creating and managing human tasks.
 * This is the main entry point for human task processors to create tasks.
 *
 * <p>This interface provides a complete abstraction for human task management,
 * allowing the engine to interact with tasks without knowing the underlying
 * storage or UI implementation. This enables easy integration with external
 * task systems (e.g., ServiceNow, Camunda Tasklist, custom UIs).
 *
 * <h2>Design Pattern: Service Facade + Repository</h2>
 * <ul>
 *   <li>Creates human task instances from configuration</li>
 *   <li>Manages complete task lifecycle (create, claim, complete, reject, cancel)</li>
 *   <li>Provides query capabilities for task listing and filtering</li>
 *   <li>Coordinates with timeout scheduler and workflow resumption</li>
 * </ul>
 *
 * <h2>Task Lifecycle Operations</h2>
 * <pre>
 * createTask() ─→ PENDING
 *                    │
 *          claimTask() ─→ IN_PROGRESS
 *                              │
 *         ┌────────────────────┼────────────────────┐
 *         │                    │                    │
 *    approveTask()      completeTask()        rejectTask()
 *         │                    │                    │
 *         ▼                    ▼                    ▼
 *     APPROVED            COMPLETED             REJECTED
 *
 *    cancelTask() can be called from any non-terminal state → CANCELLED
 * </pre>
 *
 * <h2>Integration Points</h2>
 * <p>Implementations can integrate with:</p>
 * <ul>
 *   <li>In-memory storage (testing/development)</li>
 *   <li>Database-backed storage (production)</li>
 *   <li>External task management systems (ServiceNow, Jira, etc.)</li>
 *   <li>Message queues for distributed task handling</li>
 * </ul>
 *
 * @see ILyshraOpenAppHumanTask
 * @see LyshraOpenAppHumanTaskStatus
 */
public interface ILyshraOpenAppHumanTaskService {

    /**
     * Creates a new human task and suspends the workflow.
     *
     * @param workflowInstanceId the workflow instance that will be suspended
     * @param workflowStepId the current workflow step
     * @param taskType the type of human task
     * @param config the task configuration
     * @param context the current workflow context
     * @return the created human task
     */
    Mono<ILyshraOpenAppHumanTask> createTask(
            String workflowInstanceId,
            String workflowStepId,
            LyshraOpenAppHumanTaskType taskType,
            ILyshraOpenAppHumanTaskConfig config,
            ILyshraOpenAppContext context);

    /**
     * Creates a task with additional data to display.
     *
     * @param workflowInstanceId the workflow instance
     * @param workflowStepId the workflow step
     * @param taskType the task type
     * @param config the task configuration
     * @param taskData data to include in the task for display
     * @param context the workflow context
     * @return the created human task
     */
    Mono<ILyshraOpenAppHumanTask> createTaskWithData(
            String workflowInstanceId,
            String workflowStepId,
            LyshraOpenAppHumanTaskType taskType,
            ILyshraOpenAppHumanTaskConfig config,
            Map<String, Object> taskData,
            ILyshraOpenAppContext context);

    /**
     * Gets a human task by ID.
     *
     * @param taskId the task identifier
     * @return the task if found
     */
    Mono<ILyshraOpenAppHumanTask> getTask(String taskId);

    /**
     * Claims a task for a specific user.
     *
     * @param taskId the task identifier
     * @param userId the user claiming the task
     * @return the updated task
     */
    Mono<ILyshraOpenAppHumanTask> claimTask(String taskId, String userId);

    /**
     * Releases a claimed task.
     *
     * @param taskId the task identifier
     * @param userId the user releasing the task
     * @return the updated task
     */
    Mono<ILyshraOpenAppHumanTask> unclaimTask(String taskId, String userId);

    /**
     * Delegates a task to another user.
     *
     * @param taskId the task identifier
     * @param fromUserId the user delegating
     * @param toUserId the user receiving delegation
     * @return the updated task
     */
    Mono<ILyshraOpenAppHumanTask> delegateTask(String taskId, String fromUserId, String toUserId);

    /**
     * Adds a comment to a task.
     *
     * @param taskId the task identifier
     * @param userId the user adding the comment
     * @param content the comment content
     * @param isInternal whether this is an internal comment
     * @return the updated task
     */
    Mono<ILyshraOpenAppHumanTask> addComment(String taskId, String userId, String content, boolean isInternal);

    // ========================================================================
    // TASK COMPLETION OPERATIONS
    // ========================================================================

    /**
     * Completes a task with the provided result data.
     * <p>This is the generic completion method for tasks that require data submission
     * (typically MANUAL_INPUT type tasks).</p>
     *
     * <p>The task must be in a claimable state (PENDING, ASSIGNED, or IN_PROGRESS).
     * After completion:</p>
     * <ul>
     *   <li>Task status changes to COMPLETED</li>
     *   <li>Result data is stored on the task</li>
     *   <li>Workflow is resumed with the result data merged into context</li>
     * </ul>
     *
     * @param taskId the task identifier
     * @param userId the user completing the task
     * @param resultData the data submitted by the user
     * @return the completed task
     * @throws IllegalArgumentException if task not found
     * @throws IllegalStateException if task is already in a terminal state
     */
    Mono<ILyshraOpenAppHumanTask> completeTask(String taskId, String userId, Map<String, Object> resultData);

    /**
     * Completes a task with result data and a reason/comment.
     *
     * @param taskId the task identifier
     * @param userId the user completing the task
     * @param resultData the data submitted by the user
     * @param reason optional reason or comment for the completion
     * @return the completed task
     */
    Mono<ILyshraOpenAppHumanTask> completeTask(String taskId, String userId, Map<String, Object> resultData, String reason);

    /**
     * Approves a task.
     * <p>This is the approval action for APPROVAL type tasks.
     * The task will transition to APPROVED status and the workflow
     * will resume on the APPROVED branch.</p>
     *
     * @param taskId the task identifier
     * @param userId the user approving the task
     * @return the approved task
     * @throws IllegalArgumentException if task not found
     * @throws IllegalStateException if task cannot be approved from current state
     */
    Mono<ILyshraOpenAppHumanTask> approveTask(String taskId, String userId);

    /**
     * Approves a task with a reason.
     *
     * @param taskId the task identifier
     * @param userId the user approving the task
     * @param reason the reason for approval
     * @return the approved task
     */
    Mono<ILyshraOpenAppHumanTask> approveTask(String taskId, String userId, String reason);

    /**
     * Approves a task with additional result data.
     *
     * @param taskId the task identifier
     * @param userId the user approving the task
     * @param reason the reason for approval
     * @param resultData optional additional data to include
     * @return the approved task
     */
    Mono<ILyshraOpenAppHumanTask> approveTask(String taskId, String userId, String reason, Map<String, Object> resultData);

    /**
     * Rejects a task.
     * <p>This is the rejection action for APPROVAL type tasks.
     * The task will transition to REJECTED status and the workflow
     * will resume on the REJECTED branch.</p>
     *
     * @param taskId the task identifier
     * @param userId the user rejecting the task
     * @return the rejected task
     * @throws IllegalArgumentException if task not found
     * @throws IllegalStateException if task cannot be rejected from current state
     */
    Mono<ILyshraOpenAppHumanTask> rejectTask(String taskId, String userId);

    /**
     * Rejects a task with a reason.
     *
     * @param taskId the task identifier
     * @param userId the user rejecting the task
     * @param reason the reason for rejection (recommended for audit trail)
     * @return the rejected task
     */
    Mono<ILyshraOpenAppHumanTask> rejectTask(String taskId, String userId, String reason);

    /**
     * Rejects a task with reason and additional data.
     *
     * @param taskId the task identifier
     * @param userId the user rejecting the task
     * @param reason the reason for rejection
     * @param resultData optional additional data to include
     * @return the rejected task
     */
    Mono<ILyshraOpenAppHumanTask> rejectTask(String taskId, String userId, String reason, Map<String, Object> resultData);

    /**
     * Cancels a task.
     * <p>Cancellation can be performed from any non-terminal state.
     * The task will transition to CANCELLED status and the workflow
     * will resume on the CANCELLED branch.</p>
     *
     * @param taskId the task identifier
     * @param userId the user cancelling the task
     * @return the cancelled task
     * @throws IllegalArgumentException if task not found
     * @throws IllegalStateException if task is already in a terminal state
     */
    Mono<ILyshraOpenAppHumanTask> cancelTask(String taskId, String userId);

    /**
     * Cancels a task with a reason.
     *
     * @param taskId the task identifier
     * @param userId the user cancelling the task
     * @param reason the reason for cancellation
     * @return the cancelled task
     */
    Mono<ILyshraOpenAppHumanTask> cancelTask(String taskId, String userId, String reason);

    // ========================================================================
    // TASK QUERY AND LISTING OPERATIONS
    // ========================================================================

    /**
     * Lists all tasks.
     * <p>Warning: This can return a large number of tasks. Consider using
     * filtered queries for production use.</p>
     *
     * @return flux of all tasks
     */
    Flux<ILyshraOpenAppHumanTask> listTasks();

    /**
     * Lists tasks filtered by status.
     *
     * @param status the status to filter by
     * @return flux of tasks matching the status
     */
    Flux<ILyshraOpenAppHumanTask> listTasksByStatus(LyshraOpenAppHumanTaskStatus status);

    /**
     * Lists tasks filtered by multiple statuses.
     *
     * @param statuses the statuses to filter by
     * @return flux of tasks matching any of the statuses
     */
    Flux<ILyshraOpenAppHumanTask> listTasksByStatusIn(List<LyshraOpenAppHumanTaskStatus> statuses);

    /**
     * Lists tasks assigned to a specific user.
     *
     * @param userId the user identifier
     * @return flux of tasks assigned to the user
     */
    Flux<ILyshraOpenAppHumanTask> listTasksByAssignee(String userId);

    /**
     * Lists tasks that can be claimed by a user (based on candidate groups).
     *
     * @param userId the user identifier
     * @param userGroups the groups the user belongs to
     * @return flux of claimable tasks
     */
    Flux<ILyshraOpenAppHumanTask> listClaimableTasksForUser(String userId, List<String> userGroups);

    /**
     * Lists tasks claimed by a specific user.
     *
     * @param userId the user identifier
     * @return flux of tasks claimed by the user
     */
    Flux<ILyshraOpenAppHumanTask> listTasksClaimedBy(String userId);

    /**
     * Lists tasks for a specific workflow instance.
     *
     * @param workflowInstanceId the workflow instance identifier
     * @return flux of tasks for the workflow
     */
    Flux<ILyshraOpenAppHumanTask> listTasksByWorkflowInstance(String workflowInstanceId);

    /**
     * Lists tasks for a specific workflow definition.
     *
     * @param workflowDefinitionId the workflow definition identifier
     * @return flux of tasks for the workflow definition
     */
    Flux<ILyshraOpenAppHumanTask> listTasksByWorkflowDefinition(String workflowDefinitionId);

    /**
     * Lists tasks by type.
     *
     * @param taskType the task type to filter by
     * @return flux of tasks of the specified type
     */
    Flux<ILyshraOpenAppHumanTask> listTasksByType(LyshraOpenAppHumanTaskType taskType);

    /**
     * Lists pending tasks for a user (combining assigned tasks and claimable group tasks).
     * <p>This is the primary method for building a user's task inbox.</p>
     *
     * @param userId the user identifier
     * @param userGroups the groups the user belongs to
     * @return flux of actionable tasks for the user
     */
    Flux<ILyshraOpenAppHumanTask> listPendingTasksForUser(String userId, List<String> userGroups);

    /**
     * Lists overdue tasks (past their due date).
     *
     * @return flux of overdue tasks
     */
    Flux<ILyshraOpenAppHumanTask> listOverdueTasks();

    /**
     * Lists tasks due before a specific time.
     *
     * @param before the cutoff time
     * @return flux of tasks due before the specified time
     */
    Flux<ILyshraOpenAppHumanTask> listTasksDueBefore(Instant before);

    /**
     * Lists tasks created within a time range.
     *
     * @param from the start of the range (inclusive)
     * @param to the end of the range (exclusive)
     * @return flux of tasks created in the range
     */
    Flux<ILyshraOpenAppHumanTask> listTasksCreatedBetween(Instant from, Instant to);

    // ========================================================================
    // COUNTING AND STATISTICS
    // ========================================================================

    /**
     * Counts all tasks.
     *
     * @return the total task count
     */
    Mono<Long> countTasks();

    /**
     * Counts tasks by status.
     *
     * @param status the status to count
     * @return the count of tasks with the given status
     */
    Mono<Long> countTasksByStatus(LyshraOpenAppHumanTaskStatus status);

    /**
     * Counts pending tasks for a user.
     *
     * @param userId the user identifier
     * @param userGroups the groups the user belongs to
     * @return the count of pending tasks
     */
    Mono<Long> countPendingTasksForUser(String userId, List<String> userGroups);

    /**
     * Counts overdue tasks.
     *
     * @return the count of overdue tasks
     */
    Mono<Long> countOverdueTasks();

    // ========================================================================
    // BULK OPERATIONS
    // ========================================================================

    /**
     * Deletes a task by ID.
     * <p>This should typically only be used for cleanup of cancelled/completed tasks.</p>
     *
     * @param taskId the task identifier
     * @return true if deleted, false if not found
     */
    Mono<Boolean> deleteTask(String taskId);

    /**
     * Deletes tasks by status.
     * <p>Useful for cleanup of terminal state tasks.</p>
     *
     * @param status the status of tasks to delete
     * @return the count of deleted tasks
     */
    Mono<Long> deleteTasksByStatus(LyshraOpenAppHumanTaskStatus status);

    /**
     * Deletes tasks older than a specified time.
     * <p>Useful for archival/cleanup of old completed tasks.</p>
     *
     * @param before delete tasks completed before this time
     * @param statuses only delete tasks in these statuses
     * @return the count of deleted tasks
     */
    Mono<Long> deleteTasksOlderThan(Instant before, List<LyshraOpenAppHumanTaskStatus> statuses);

    // ========================================================================
    // TASK UPDATE OPERATIONS
    // ========================================================================

    /**
     * Updates the priority of a task.
     *
     * @param taskId the task identifier
     * @param priority the new priority (1-10)
     * @return the updated task
     */
    Mono<ILyshraOpenAppHumanTask> updatePriority(String taskId, int priority);

    /**
     * Updates the due date of a task.
     *
     * @param taskId the task identifier
     * @param dueAt the new due date
     * @return the updated task
     */
    Mono<ILyshraOpenAppHumanTask> updateDueDate(String taskId, Instant dueAt);

    /**
     * Reassigns a task to different assignees.
     *
     * @param taskId the task identifier
     * @param assignees the new list of assignees
     * @return the updated task
     */
    Mono<ILyshraOpenAppHumanTask> reassignTask(String taskId, List<String> assignees);

    /**
     * Updates the candidate groups for a task.
     *
     * @param taskId the task identifier
     * @param candidateGroups the new list of candidate groups
     * @return the updated task
     */
    Mono<ILyshraOpenAppHumanTask> updateCandidateGroups(String taskId, List<String> candidateGroups);

    // ========================================================================
    // TASK ASSIGNMENT STRATEGY OPERATIONS
    // ========================================================================

    /**
     * Creates a task with automatic assignment using the specified strategy.
     * <p>The assignment strategy will determine the assignees and/or candidate
     * groups based on the configuration and context.</p>
     *
     * @param workflowInstanceId the workflow instance
     * @param workflowStepId the workflow step
     * @param taskType the task type
     * @param config the task configuration
     * @param taskData data to include in the task
     * @param context the workflow context
     * @param assignmentConfig the assignment strategy configuration
     * @return the created task with assignments applied
     */
    default Mono<ILyshraOpenAppHumanTask> createTaskWithAssignment(
            String workflowInstanceId,
            String workflowStepId,
            LyshraOpenAppHumanTaskType taskType,
            ILyshraOpenAppHumanTaskConfig config,
            Map<String, Object> taskData,
            ILyshraOpenAppContext context,
            AssignmentStrategyConfig assignmentConfig) {
        // Default implementation falls back to regular task creation
        return createTaskWithData(workflowInstanceId, workflowStepId, taskType, config, taskData, context);
    }

    /**
     * Reassigns a task using the specified assignment strategy.
     * <p>The strategy will determine new assignees based on current conditions.</p>
     *
     * @param taskId the task identifier
     * @param assignmentConfig the assignment strategy configuration
     * @param context optional workflow context for assignment decisions
     * @return the reassigned task with the assignment result
     */
    default Mono<TaskReassignmentResult> reassignTaskWithStrategy(
            String taskId,
            AssignmentStrategyConfig assignmentConfig,
            ILyshraOpenAppContext context) {
        // Default implementation: not supported
        return Mono.error(new UnsupportedOperationException(
                "Task reassignment with strategy not supported by this implementation"));
    }

    /**
     * Gets the assignment result for a task if automatic assignment was used.
     *
     * @param taskId the task identifier
     * @return the assignment result if available
     */
    default Mono<AssignmentResult> getTaskAssignmentResult(String taskId) {
        return Mono.empty();
    }

    /**
     * Result of a task reassignment operation.
     */
    record TaskReassignmentResult(
            ILyshraOpenAppHumanTask task,
            AssignmentResult assignmentResult,
            List<String> previousAssignees,
            List<String> previousCandidateGroups
    ) {}
}
