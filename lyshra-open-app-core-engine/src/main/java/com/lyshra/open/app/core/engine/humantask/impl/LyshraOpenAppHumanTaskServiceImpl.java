package com.lyshra.open.app.core.engine.humantask.impl;

import com.lyshra.open.app.core.engine.assignment.*;
import com.lyshra.open.app.core.engine.assignment.impl.LyshraOpenAppTaskAssignmentServiceImpl;
import com.lyshra.open.app.core.engine.humantask.ILyshraOpenAppHumanTaskService;
import com.lyshra.open.app.core.engine.node.ILyshraOpenAppWorkflowResumptionService;
import com.lyshra.open.app.core.engine.node.impl.LyshraOpenAppWorkflowResumptionServiceImpl;
import com.lyshra.open.app.core.engine.timeout.ILyshraOpenAppTimeoutScheduler;
import com.lyshra.open.app.core.engine.timeout.impl.LyshraOpenAppTimeoutSchedulerImpl;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.humantask.*;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskProcessor.ILyshraOpenAppHumanTaskConfig;
import com.lyshra.open.app.integration.models.humantask.LyshraOpenAppHumanTaskResult;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskStatus;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of the human task service.
 * Manages human task lifecycle and coordinates with other services.
 *
 * <p>Note: This implementation uses in-memory storage.
 * Production should use ILyshraOpenAppHumanTaskRepository.
 */
@Slf4j
public class LyshraOpenAppHumanTaskServiceImpl implements ILyshraOpenAppHumanTaskService {

    private final Map<String, InMemoryHumanTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, AssignmentResult> taskAssignmentResults = new ConcurrentHashMap<>();
    private final ILyshraOpenAppTimeoutScheduler timeoutScheduler;
    private final ILyshraOpenAppWorkflowResumptionService resumptionService;
    private final ILyshraOpenAppTaskAssignmentService assignmentService;

    private LyshraOpenAppHumanTaskServiceImpl() {
        this.timeoutScheduler = LyshraOpenAppTimeoutSchedulerImpl.getInstance();
        this.resumptionService = LyshraOpenAppWorkflowResumptionServiceImpl.getInstance();
        this.assignmentService = LyshraOpenAppTaskAssignmentServiceImpl.getInstance();
    }

    private static final class SingletonHelper {
        private static final ILyshraOpenAppHumanTaskService INSTANCE = new LyshraOpenAppHumanTaskServiceImpl();
    }

    public static ILyshraOpenAppHumanTaskService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    @Override
    public Mono<ILyshraOpenAppHumanTask> createTask(
            String workflowInstanceId,
            String workflowStepId,
            LyshraOpenAppHumanTaskType taskType,
            ILyshraOpenAppHumanTaskConfig config,
            ILyshraOpenAppContext context) {

        return createTaskWithData(workflowInstanceId, workflowStepId, taskType, config, Collections.emptyMap(), context);
    }

    @Override
    public Mono<ILyshraOpenAppHumanTask> createTaskWithData(
            String workflowInstanceId,
            String workflowStepId,
            LyshraOpenAppHumanTaskType taskType,
            ILyshraOpenAppHumanTaskConfig config,
            Map<String, Object> taskData,
            ILyshraOpenAppContext context) {

        return Mono.defer(() -> {
            String taskId = UUID.randomUUID().toString();
            Instant now = Instant.now();

            log.info("Creating human task: id={}, type={}, workflowId={}, step={}",
                    taskId, taskType, workflowInstanceId, workflowStepId);

            InMemoryHumanTask task = new InMemoryHumanTask();
            task.taskId = taskId;
            task.workflowInstanceId = workflowInstanceId;
            task.workflowStepId = workflowStepId;
            task.taskType = taskType;
            task.status = LyshraOpenAppHumanTaskStatus.PENDING;
            task.title = config.getTitle();
            task.description = config.getDescription();
            task.priority = config.getPriority();
            task.assignees = new ArrayList<>(config.getAssignees());
            task.candidateGroups = new ArrayList<>(config.getCandidateGroups());
            task.taskData = new HashMap<>(taskData);
            task.createdAt = now;
            task.updatedAt = now;

            // Set timeout if configured
            config.getTimeout().ifPresent(timeout -> {
                task.timeout = timeout;
                task.dueAtValue = now.plus(timeout);
            });

            // Set escalation if configured
            config.getEscalation().ifPresent(escalation -> task.escalationConfig = escalation);

            // Set form schema if configured
            config.getFormSchema().ifPresent(schema -> task.formSchema = schema);

            // Add initial audit entry
            task.auditTrail.add(createAuditEntry(
                    ILyshraOpenAppHumanTaskAuditEntry.AuditAction.CREATED,
                    null,
                    LyshraOpenAppHumanTaskStatus.PENDING,
                    "system",
                    ILyshraOpenAppHumanTaskAuditEntry.ActorType.WORKFLOW_ENGINE,
                    "Task created"
            ));

            // Store task
            tasks.put(taskId, task);

            // Suspend workflow at this step
            return resumptionService.suspendAtHumanTask(workflowInstanceId, workflowStepId, taskId, context)
                    .then(scheduleTimeouts(task))
                    .thenReturn((ILyshraOpenAppHumanTask) task);
        });
    }

    @Override
    public Mono<ILyshraOpenAppHumanTask> getTask(String taskId) {
        return Mono.fromCallable(() -> tasks.get(taskId));
    }

    @Override
    public Mono<ILyshraOpenAppHumanTask> claimTask(String taskId, String userId) {
        return Mono.fromCallable(() -> {
            InMemoryHumanTask task = tasks.get(taskId);
            if (task == null) {
                throw new IllegalArgumentException("Task not found: " + taskId);
            }

            if (task.status != LyshraOpenAppHumanTaskStatus.PENDING
                    && task.status != LyshraOpenAppHumanTaskStatus.ASSIGNED) {
                throw new IllegalStateException("Task cannot be claimed in state: " + task.status);
            }

            if (task.claimedBy != null) {
                throw new IllegalStateException("Task already claimed by: " + task.claimedBy);
            }

            LyshraOpenAppHumanTaskStatus prevStatus = task.status;
            task.claimedBy = userId;
            task.status = LyshraOpenAppHumanTaskStatus.IN_PROGRESS;
            task.updatedAt = Instant.now();

            task.auditTrail.add(createAuditEntry(
                    ILyshraOpenAppHumanTaskAuditEntry.AuditAction.CLAIMED,
                    prevStatus,
                    task.status,
                    userId,
                    ILyshraOpenAppHumanTaskAuditEntry.ActorType.USER,
                    "Task claimed"
            ));

            log.info("Task claimed: taskId={}, userId={}", taskId, userId);
            return task;
        });
    }

    @Override
    public Mono<ILyshraOpenAppHumanTask> unclaimTask(String taskId, String userId) {
        return Mono.fromCallable(() -> {
            InMemoryHumanTask task = tasks.get(taskId);
            if (task == null) {
                throw new IllegalArgumentException("Task not found: " + taskId);
            }

            if (!userId.equals(task.claimedBy)) {
                throw new IllegalStateException("Task not claimed by this user");
            }

            LyshraOpenAppHumanTaskStatus prevStatus = task.status;
            task.claimedBy = null;
            task.status = LyshraOpenAppHumanTaskStatus.PENDING;
            task.updatedAt = Instant.now();

            task.auditTrail.add(createAuditEntry(
                    ILyshraOpenAppHumanTaskAuditEntry.AuditAction.UNCLAIMED,
                    prevStatus,
                    task.status,
                    userId,
                    ILyshraOpenAppHumanTaskAuditEntry.ActorType.USER,
                    "Task unclaimed"
            ));

            log.info("Task unclaimed: taskId={}, userId={}", taskId, userId);
            return task;
        });
    }

    @Override
    public Mono<ILyshraOpenAppHumanTask> delegateTask(String taskId, String fromUserId, String toUserId) {
        return Mono.fromCallable(() -> {
            InMemoryHumanTask task = tasks.get(taskId);
            if (task == null) {
                throw new IllegalArgumentException("Task not found: " + taskId);
            }

            task.claimedBy = toUserId;
            task.status = LyshraOpenAppHumanTaskStatus.IN_PROGRESS;
            task.updatedAt = Instant.now();

            task.auditTrail.add(createAuditEntry(
                    ILyshraOpenAppHumanTaskAuditEntry.AuditAction.DELEGATED,
                    null,
                    task.status,
                    fromUserId,
                    ILyshraOpenAppHumanTaskAuditEntry.ActorType.USER,
                    "Task delegated to " + toUserId
            ));

            log.info("Task delegated: taskId={}, from={}, to={}", taskId, fromUserId, toUserId);
            return task;
        });
    }

    @Override
    public Mono<ILyshraOpenAppHumanTask> addComment(String taskId, String userId, String content, boolean isInternal) {
        return Mono.fromCallable(() -> {
            InMemoryHumanTask task = tasks.get(taskId);
            if (task == null) {
                throw new IllegalArgumentException("Task not found: " + taskId);
            }

            InMemoryComment comment = new InMemoryComment();
            comment.commentId = UUID.randomUUID().toString();
            comment.author = userId;
            comment.content = content;
            comment.createdAt = Instant.now();
            comment.internal = isInternal;

            task.comments.add(comment);
            task.updatedAt = Instant.now();

            task.auditTrail.add(createAuditEntry(
                    ILyshraOpenAppHumanTaskAuditEntry.AuditAction.COMMENTED,
                    null,
                    null,
                    userId,
                    ILyshraOpenAppHumanTaskAuditEntry.ActorType.USER,
                    "Comment added"
            ));

            log.info("Comment added to task: taskId={}, userId={}", taskId, userId);
            return task;
        });
    }

    // ========================================================================
    // TASK COMPLETION OPERATIONS
    // ========================================================================

    @Override
    public Mono<ILyshraOpenAppHumanTask> completeTask(String taskId, String userId, Map<String, Object> resultData) {
        return completeTask(taskId, userId, resultData, null);
    }

    @Override
    public Mono<ILyshraOpenAppHumanTask> completeTask(String taskId, String userId, Map<String, Object> resultData, String reason) {
        return resolveTask(taskId, userId, LyshraOpenAppHumanTaskStatus.COMPLETED, resultData, reason);
    }

    @Override
    public Mono<ILyshraOpenAppHumanTask> approveTask(String taskId, String userId) {
        return approveTask(taskId, userId, null, null);
    }

    @Override
    public Mono<ILyshraOpenAppHumanTask> approveTask(String taskId, String userId, String reason) {
        return approveTask(taskId, userId, reason, null);
    }

    @Override
    public Mono<ILyshraOpenAppHumanTask> approveTask(String taskId, String userId, String reason, Map<String, Object> resultData) {
        return resolveTask(taskId, userId, LyshraOpenAppHumanTaskStatus.APPROVED, resultData, reason);
    }

    @Override
    public Mono<ILyshraOpenAppHumanTask> rejectTask(String taskId, String userId) {
        return rejectTask(taskId, userId, null, null);
    }

    @Override
    public Mono<ILyshraOpenAppHumanTask> rejectTask(String taskId, String userId, String reason) {
        return rejectTask(taskId, userId, reason, null);
    }

    @Override
    public Mono<ILyshraOpenAppHumanTask> rejectTask(String taskId, String userId, String reason, Map<String, Object> resultData) {
        return resolveTask(taskId, userId, LyshraOpenAppHumanTaskStatus.REJECTED, resultData, reason);
    }

    @Override
    public Mono<ILyshraOpenAppHumanTask> cancelTask(String taskId, String userId) {
        return cancelTask(taskId, userId, null);
    }

    @Override
    public Mono<ILyshraOpenAppHumanTask> cancelTask(String taskId, String userId, String reason) {
        return resolveTask(taskId, userId, LyshraOpenAppHumanTaskStatus.CANCELLED, null, reason);
    }

    /**
     * Core method for resolving a task with a terminal status.
     */
    private Mono<ILyshraOpenAppHumanTask> resolveTask(
            String taskId,
            String userId,
            LyshraOpenAppHumanTaskStatus newStatus,
            Map<String, Object> resultData,
            String reason) {

        return Mono.fromCallable(() -> {
            InMemoryHumanTask task = tasks.get(taskId);
            if (task == null) {
                throw new IllegalArgumentException("Task not found: " + taskId);
            }

            if (isTerminalStatus(task.status)) {
                throw new IllegalStateException("Task is already in terminal state: " + task.status);
            }

            LyshraOpenAppHumanTaskStatus prevStatus = task.status;
            task.status = newStatus;
            task.updatedAt = Instant.now();
            task.completedAt = Instant.now();
            task.claimedBy = userId;

            if (resultData != null && !resultData.isEmpty()) {
                task.resultData = new HashMap<>(resultData);
            }

            if (reason != null && !reason.isBlank()) {
                task.decisionReason = reason;
            }

            ILyshraOpenAppHumanTaskAuditEntry.AuditAction action = switch (newStatus) {
                case APPROVED -> ILyshraOpenAppHumanTaskAuditEntry.AuditAction.APPROVED;
                case REJECTED -> ILyshraOpenAppHumanTaskAuditEntry.AuditAction.REJECTED;
                case CANCELLED -> ILyshraOpenAppHumanTaskAuditEntry.AuditAction.CANCELLED;
                default -> ILyshraOpenAppHumanTaskAuditEntry.AuditAction.COMPLETED;
            };

            task.auditTrail.add(createAuditEntry(
                    action,
                    prevStatus,
                    newStatus,
                    userId,
                    ILyshraOpenAppHumanTaskAuditEntry.ActorType.USER,
                    reason != null ? reason : "Task " + newStatus.name().toLowerCase()
            ));

            log.info("Task resolved: taskId={}, userId={}, status={}", taskId, userId, newStatus);
            return task;
        }).flatMap(task -> {
            // Build the task result for workflow resumption
            long durationMillis = Duration.between(task.createdAt, Instant.now()).toMillis();

            LyshraOpenAppHumanTaskResult taskResult = LyshraOpenAppHumanTaskResult.builder()
                    .branch(task.status.name())
                    .data(task.resultData)
                    .taskStatus(task.status)
                    .taskId(taskId)
                    .resolvedBy(userId)
                    .resolvedAt(Instant.now())
                    .formData(task.resultData)
                    .resolutionComment(task.decisionReason)
                    .autoResolved(false)
                    .durationMillis(durationMillis)
                    .escalationCount(0)
                    .build();

            // Get the workflow instance and resume it
            return resumptionService.getWorkflowInstance(task.workflowInstanceId)
                    .flatMap(instance -> resumptionService.resumeFromHumanTask(instance, taskResult))
                    .thenReturn((ILyshraOpenAppHumanTask) task)
                    .onErrorResume(e -> {
                        log.error("Failed to resume workflow after task completion: taskId={}", taskId, e);
                        return Mono.just(task);
                    });
        });
    }

    private boolean isTerminalStatus(LyshraOpenAppHumanTaskStatus status) {
        return status == LyshraOpenAppHumanTaskStatus.APPROVED
                || status == LyshraOpenAppHumanTaskStatus.REJECTED
                || status == LyshraOpenAppHumanTaskStatus.COMPLETED
                || status == LyshraOpenAppHumanTaskStatus.CANCELLED
                || status == LyshraOpenAppHumanTaskStatus.TIMED_OUT
                || status == LyshraOpenAppHumanTaskStatus.EXPIRED;
    }

    // ========================================================================
    // TASK QUERY AND LISTING OPERATIONS
    // ========================================================================

    @Override
    public Flux<ILyshraOpenAppHumanTask> listTasks() {
        return Flux.fromIterable(tasks.values())
                .map(task -> task);
    }

    @Override
    public Flux<ILyshraOpenAppHumanTask> listTasksByStatus(LyshraOpenAppHumanTaskStatus status) {
        return Flux.fromIterable(tasks.values())
                .filter(task -> task.status == status)
                .map(task -> task);
    }

    @Override
    public Flux<ILyshraOpenAppHumanTask> listTasksByStatusIn(List<LyshraOpenAppHumanTaskStatus> statuses) {
        return Flux.fromIterable(tasks.values())
                .filter(task -> statuses.contains(task.status))
                .map(task -> task);
    }

    @Override
    public Flux<ILyshraOpenAppHumanTask> listTasksByAssignee(String userId) {
        return Flux.fromIterable(tasks.values())
                .filter(task -> task.assignees.contains(userId))
                .map(task -> task);
    }

    @Override
    public Flux<ILyshraOpenAppHumanTask> listClaimableTasksForUser(String userId, List<String> userGroups) {
        return Flux.fromIterable(tasks.values())
                .filter(task -> !isTerminalStatus(task.status))
                .filter(task -> task.claimedBy == null) // Not already claimed
                .filter(task -> {
                    // User is directly assigned
                    if (task.assignees.contains(userId)) {
                        return true;
                    }
                    // User is in a candidate group
                    if (userGroups != null) {
                        return task.candidateGroups.stream().anyMatch(userGroups::contains);
                    }
                    return false;
                })
                .map(task -> task);
    }

    @Override
    public Flux<ILyshraOpenAppHumanTask> listTasksClaimedBy(String userId) {
        return Flux.fromIterable(tasks.values())
                .filter(task -> userId.equals(task.claimedBy))
                .map(task -> task);
    }

    @Override
    public Flux<ILyshraOpenAppHumanTask> listTasksByWorkflowInstance(String workflowInstanceId) {
        return Flux.fromIterable(tasks.values())
                .filter(task -> workflowInstanceId.equals(task.workflowInstanceId))
                .map(task -> task);
    }

    @Override
    public Flux<ILyshraOpenAppHumanTask> listTasksByWorkflowDefinition(String workflowDefinitionId) {
        return Flux.fromIterable(tasks.values())
                .filter(task -> task.workflowDefinitionId != null
                        && task.workflowDefinitionId.equals(workflowDefinitionId))
                .map(task -> task);
    }

    @Override
    public Flux<ILyshraOpenAppHumanTask> listTasksByType(LyshraOpenAppHumanTaskType taskType) {
        return Flux.fromIterable(tasks.values())
                .filter(task -> task.taskType == taskType)
                .map(task -> task);
    }

    @Override
    public Flux<ILyshraOpenAppHumanTask> listPendingTasksForUser(String userId, List<String> userGroups) {
        return Flux.fromIterable(tasks.values())
                .filter(task -> !isTerminalStatus(task.status))
                .filter(task -> {
                    // Task is claimed by this user
                    if (userId.equals(task.claimedBy)) {
                        return true;
                    }
                    // Task is not claimed and user can act on it
                    if (task.claimedBy == null) {
                        if (task.assignees.contains(userId)) {
                            return true;
                        }
                        if (userGroups != null) {
                            return task.candidateGroups.stream().anyMatch(userGroups::contains);
                        }
                    }
                    return false;
                })
                .map(task -> task);
    }

    @Override
    public Flux<ILyshraOpenAppHumanTask> listOverdueTasks() {
        Instant now = Instant.now();
        return Flux.fromIterable(tasks.values())
                .filter(task -> !isTerminalStatus(task.status))
                .filter(task -> task.dueAtValue != null && task.dueAtValue.isBefore(now))
                .map(task -> task);
    }

    @Override
    public Flux<ILyshraOpenAppHumanTask> listTasksDueBefore(Instant before) {
        return Flux.fromIterable(tasks.values())
                .filter(task -> !isTerminalStatus(task.status))
                .filter(task -> task.dueAtValue != null && task.dueAtValue.isBefore(before))
                .map(task -> task);
    }

    @Override
    public Flux<ILyshraOpenAppHumanTask> listTasksCreatedBetween(Instant from, Instant to) {
        return Flux.fromIterable(tasks.values())
                .filter(task -> task.createdAt != null)
                .filter(task -> !task.createdAt.isBefore(from) && task.createdAt.isBefore(to))
                .map(task -> task);
    }

    // ========================================================================
    // COUNTING AND STATISTICS
    // ========================================================================

    @Override
    public Mono<Long> countTasks() {
        return Mono.just((long) tasks.size());
    }

    @Override
    public Mono<Long> countTasksByStatus(LyshraOpenAppHumanTaskStatus status) {
        return Mono.fromCallable(() -> tasks.values().stream()
                .filter(task -> task.status == status)
                .count());
    }

    @Override
    public Mono<Long> countPendingTasksForUser(String userId, List<String> userGroups) {
        return listPendingTasksForUser(userId, userGroups).count();
    }

    @Override
    public Mono<Long> countOverdueTasks() {
        return listOverdueTasks().count();
    }

    // ========================================================================
    // BULK OPERATIONS
    // ========================================================================

    @Override
    public Mono<Boolean> deleteTask(String taskId) {
        return Mono.fromCallable(() -> {
            InMemoryHumanTask removed = tasks.remove(taskId);
            if (removed != null) {
                log.info("Deleted task: taskId={}", taskId);
                return true;
            }
            return false;
        });
    }

    @Override
    public Mono<Long> deleteTasksByStatus(LyshraOpenAppHumanTaskStatus status) {
        return Mono.fromCallable(() -> {
            List<String> toDelete = tasks.values().stream()
                    .filter(task -> task.status == status)
                    .map(task -> task.taskId)
                    .toList();

            toDelete.forEach(tasks::remove);
            log.info("Deleted {} tasks with status: {}", toDelete.size(), status);
            return (long) toDelete.size();
        });
    }

    @Override
    public Mono<Long> deleteTasksOlderThan(Instant before, List<LyshraOpenAppHumanTaskStatus> statuses) {
        return Mono.fromCallable(() -> {
            List<String> toDelete = tasks.values().stream()
                    .filter(task -> statuses.contains(task.status))
                    .filter(task -> task.completedAt != null && task.completedAt.isBefore(before))
                    .map(task -> task.taskId)
                    .toList();

            toDelete.forEach(tasks::remove);
            log.info("Deleted {} old tasks before: {}", toDelete.size(), before);
            return (long) toDelete.size();
        });
    }

    // ========================================================================
    // TASK UPDATE OPERATIONS
    // ========================================================================

    @Override
    public Mono<ILyshraOpenAppHumanTask> updatePriority(String taskId, int priority) {
        return Mono.fromCallable(() -> {
            InMemoryHumanTask task = tasks.get(taskId);
            if (task == null) {
                throw new IllegalArgumentException("Task not found: " + taskId);
            }

            task.priority = Math.max(1, Math.min(10, priority));
            task.updatedAt = Instant.now();

            task.auditTrail.add(createAuditEntry(
                    ILyshraOpenAppHumanTaskAuditEntry.AuditAction.DATA_UPDATED,
                    null,
                    null,
                    "system",
                    ILyshraOpenAppHumanTaskAuditEntry.ActorType.WORKFLOW_ENGINE,
                    "Priority updated to " + task.priority
            ));

            log.info("Updated task priority: taskId={}, priority={}", taskId, task.priority);
            return task;
        });
    }

    @Override
    public Mono<ILyshraOpenAppHumanTask> updateDueDate(String taskId, Instant dueAt) {
        return Mono.fromCallable(() -> {
            InMemoryHumanTask task = tasks.get(taskId);
            if (task == null) {
                throw new IllegalArgumentException("Task not found: " + taskId);
            }

            task.dueAtValue = dueAt;
            task.updatedAt = Instant.now();

            task.auditTrail.add(createAuditEntry(
                    ILyshraOpenAppHumanTaskAuditEntry.AuditAction.DATA_UPDATED,
                    null,
                    null,
                    "system",
                    ILyshraOpenAppHumanTaskAuditEntry.ActorType.WORKFLOW_ENGINE,
                    "Due date updated to " + dueAt
            ));

            log.info("Updated task due date: taskId={}, dueAt={}", taskId, dueAt);
            return task;
        });
    }

    @Override
    public Mono<ILyshraOpenAppHumanTask> reassignTask(String taskId, List<String> assignees) {
        return Mono.fromCallable(() -> {
            InMemoryHumanTask task = tasks.get(taskId);
            if (task == null) {
                throw new IllegalArgumentException("Task not found: " + taskId);
            }

            task.assignees = new ArrayList<>(assignees);
            task.updatedAt = Instant.now();

            task.auditTrail.add(createAuditEntry(
                    ILyshraOpenAppHumanTaskAuditEntry.AuditAction.REASSIGNED,
                    null,
                    null,
                    "system",
                    ILyshraOpenAppHumanTaskAuditEntry.ActorType.WORKFLOW_ENGINE,
                    "Reassigned to: " + String.join(", ", assignees)
            ));

            log.info("Reassigned task: taskId={}, assignees={}", taskId, assignees);
            return task;
        });
    }

    @Override
    public Mono<ILyshraOpenAppHumanTask> updateCandidateGroups(String taskId, List<String> candidateGroups) {
        return Mono.fromCallable(() -> {
            InMemoryHumanTask task = tasks.get(taskId);
            if (task == null) {
                throw new IllegalArgumentException("Task not found: " + taskId);
            }

            task.candidateGroups = new ArrayList<>(candidateGroups);
            task.updatedAt = Instant.now();

            task.auditTrail.add(createAuditEntry(
                    ILyshraOpenAppHumanTaskAuditEntry.AuditAction.DATA_UPDATED,
                    null,
                    null,
                    "system",
                    ILyshraOpenAppHumanTaskAuditEntry.ActorType.WORKFLOW_ENGINE,
                    "Candidate groups updated to: " + String.join(", ", candidateGroups)
            ));

            log.info("Updated candidate groups: taskId={}, groups={}", taskId, candidateGroups);
            return task;
        });
    }

    // ========================================================================
    // TASK ASSIGNMENT STRATEGY OPERATIONS
    // ========================================================================

    @Override
    public Mono<ILyshraOpenAppHumanTask> createTaskWithAssignment(
            String workflowInstanceId,
            String workflowStepId,
            LyshraOpenAppHumanTaskType taskType,
            ILyshraOpenAppHumanTaskConfig config,
            Map<String, Object> taskData,
            ILyshraOpenAppContext context,
            AssignmentStrategyConfig assignmentConfig) {

        // Build assignment context
        AssignmentContext assignmentContext = assignmentService.buildContext(
                workflowInstanceId,
                workflowStepId,
                taskType,
                config.getTitle(),
                config.getAssignees(),
                config.getCandidateGroups(),
                context,
                assignmentConfig);

        // Execute assignment strategy
        return assignmentService.determineAssignment(assignmentContext)
                .flatMap(assignmentResult -> {
                    log.info("Assignment determined for task: resultType={}, primaryAssignee={}, strategy={}",
                            assignmentResult.getResultType(),
                            assignmentResult.getPrimaryAssignee(),
                            assignmentResult.getStrategyName());

                    // Apply assignment result to task creation
                    return createTaskWithAssignmentResult(
                            workflowInstanceId,
                            workflowStepId,
                            taskType,
                            config,
                            taskData,
                            context,
                            assignmentResult);
                });
    }

    private Mono<ILyshraOpenAppHumanTask> createTaskWithAssignmentResult(
            String workflowInstanceId,
            String workflowStepId,
            LyshraOpenAppHumanTaskType taskType,
            ILyshraOpenAppHumanTaskConfig config,
            Map<String, Object> taskData,
            ILyshraOpenAppContext context,
            AssignmentResult assignmentResult) {

        return Mono.defer(() -> {
            String taskId = UUID.randomUUID().toString();
            Instant now = Instant.now();

            log.info("Creating human task with assignment: id={}, type={}, workflowId={}, step={}, strategy={}",
                    taskId, taskType, workflowInstanceId, workflowStepId, assignmentResult.getStrategyName());

            InMemoryHumanTask task = new InMemoryHumanTask();
            task.taskId = taskId;
            task.workflowInstanceId = workflowInstanceId;
            task.workflowStepId = workflowStepId;
            task.taskType = taskType;
            task.status = LyshraOpenAppHumanTaskStatus.PENDING;
            task.title = config.getTitle();
            task.description = config.getDescription();
            task.priority = config.getPriority();

            // Apply assignment result
            if (assignmentResult.hasAssignees()) {
                task.assignees = new ArrayList<>(assignmentResult.getAssignees());
                // If we have a primary assignee, set status to ASSIGNED
                if (assignmentResult.getPrimaryAssignee() != null) {
                    task.status = LyshraOpenAppHumanTaskStatus.ASSIGNED;
                }
            } else {
                task.assignees = new ArrayList<>(config.getAssignees());
            }

            if (assignmentResult.hasCandidateGroups()) {
                task.candidateGroups = new ArrayList<>(assignmentResult.getCandidateGroups());
            } else {
                task.candidateGroups = new ArrayList<>(config.getCandidateGroups());
            }

            task.taskData = new HashMap<>(taskData);
            task.createdAt = now;
            task.updatedAt = now;

            // Apply priority adjustment if specified
            if (assignmentResult.getPriorityAdjustment() != null) {
                task.priority = Math.max(1, Math.min(10,
                        task.priority + assignmentResult.getPriorityAdjustment()));
            }

            // Set timeout if configured
            config.getTimeout().ifPresent(timeout -> {
                task.timeout = timeout;
                task.dueAtValue = now.plus(timeout);
            });

            // Set escalation if configured
            config.getEscalation().ifPresent(escalation -> task.escalationConfig = escalation);

            // Set form schema if configured
            config.getFormSchema().ifPresent(schema -> task.formSchema = schema);

            // Add initial audit entry with assignment info
            String assignmentReason = assignmentResult.getReason() != null ?
                    "Task created with " + assignmentResult.getStrategyName() + " strategy: " +
                            assignmentResult.getReason() :
                    "Task created with " + assignmentResult.getStrategyName() + " strategy";

            task.auditTrail.add(createAuditEntry(
                    ILyshraOpenAppHumanTaskAuditEntry.AuditAction.CREATED,
                    null,
                    task.status,
                    "system",
                    ILyshraOpenAppHumanTaskAuditEntry.ActorType.WORKFLOW_ENGINE,
                    assignmentReason
            ));

            // Store assignment result
            taskAssignmentResults.put(taskId, assignmentResult);

            // Store task metadata about the assignment
            task.metadata.put("_assignmentStrategy", assignmentResult.getStrategyName());
            task.metadata.put("_assignmentResultType", assignmentResult.getResultType().name());
            if (assignmentResult.getPrimaryAssignee() != null) {
                task.metadata.put("_primaryAssignee", assignmentResult.getPrimaryAssignee());
            }

            // Store task
            tasks.put(taskId, task);

            // Suspend workflow at this step
            return resumptionService.suspendAtHumanTask(workflowInstanceId, workflowStepId, taskId, context)
                    .then(scheduleTimeouts(task))
                    .thenReturn((ILyshraOpenAppHumanTask) task);
        });
    }

    @Override
    public Mono<TaskReassignmentResult> reassignTaskWithStrategy(
            String taskId,
            AssignmentStrategyConfig assignmentConfig,
            ILyshraOpenAppContext context) {

        return Mono.fromCallable(() -> tasks.get(taskId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Task not found: " + taskId)))
                .flatMap(task -> {
                    if (isTerminalStatus(task.status)) {
                        return Mono.error(new IllegalStateException(
                                "Cannot reassign task in terminal state: " + task.status));
                    }

                    // Store previous assignment
                    List<String> previousAssignees = new ArrayList<>(task.assignees);
                    List<String> previousCandidateGroups = new ArrayList<>(task.candidateGroups);

                    // Build assignment context
                    AssignmentContext assignmentContext = assignmentService.buildContext(
                            task.workflowInstanceId,
                            task.workflowStepId,
                            task.taskType,
                            task.title,
                            task.assignees,
                            task.candidateGroups,
                            context,
                            assignmentConfig);

                    // Execute assignment strategy
                    return assignmentService.determineAssignment(assignmentContext)
                            .flatMap(assignmentResult -> {
                                // Apply new assignment
                                if (assignmentResult.hasAssignees()) {
                                    task.assignees = new ArrayList<>(assignmentResult.getAssignees());
                                }
                                if (assignmentResult.hasCandidateGroups()) {
                                    task.candidateGroups = new ArrayList<>(assignmentResult.getCandidateGroups());
                                }

                                // Reset claimed status if user is no longer an assignee
                                if (task.claimedBy != null && !task.assignees.contains(task.claimedBy)) {
                                    task.claimedBy = null;
                                    task.status = LyshraOpenAppHumanTaskStatus.PENDING;
                                }

                                task.updatedAt = Instant.now();

                                // Update stored assignment result
                                taskAssignmentResults.put(taskId, assignmentResult);

                                // Add audit entry
                                task.auditTrail.add(createAuditEntry(
                                        ILyshraOpenAppHumanTaskAuditEntry.AuditAction.REASSIGNED,
                                        null,
                                        task.status,
                                        "system",
                                        ILyshraOpenAppHumanTaskAuditEntry.ActorType.WORKFLOW_ENGINE,
                                        "Reassigned using " + assignmentResult.getStrategyName() +
                                                " strategy: " + assignmentResult.getReason()
                                ));

                                log.info("Task reassigned: taskId={}, strategy={}, newAssignees={}",
                                        taskId, assignmentResult.getStrategyName(), task.assignees);

                                return Mono.just(new TaskReassignmentResult(
                                        task,
                                        assignmentResult,
                                        previousAssignees,
                                        previousCandidateGroups
                                ));
                            });
                });
    }

    @Override
    public Mono<AssignmentResult> getTaskAssignmentResult(String taskId) {
        return Mono.fromCallable(() -> taskAssignmentResults.get(taskId));
    }

    // ========== Private Helper Methods ==========

    private Mono<Void> scheduleTimeouts(InMemoryHumanTask task) {
        if (task.timeout != null) {
            return timeoutScheduler.scheduleTaskTimeout(task, task.timeout)
                    .doOnNext(timeoutId -> log.info("Scheduled task timeout: taskId={}, timeoutId={}",
                            task.taskId, timeoutId))
                    .then();
        }
        return Mono.empty();
    }

    private ILyshraOpenAppHumanTaskAuditEntry createAuditEntry(
            ILyshraOpenAppHumanTaskAuditEntry.AuditAction action,
            LyshraOpenAppHumanTaskStatus prevStatus,
            LyshraOpenAppHumanTaskStatus newStatus,
            String actorId,
            ILyshraOpenAppHumanTaskAuditEntry.ActorType actorType,
            String description) {

        InMemoryAuditEntry entry = new InMemoryAuditEntry();
        entry.entryId = UUID.randomUUID().toString();
        entry.timestamp = Instant.now();
        entry.action = action;
        entry.previousStatus = prevStatus;
        entry.newStatus = newStatus;
        entry.actorId = actorId;
        entry.actorType = actorType;
        entry.description = description;
        return entry;
    }

    // ========== Internal Classes ==========

    @Data
    private static class InMemoryHumanTask implements ILyshraOpenAppHumanTask {
        String taskId;
        String workflowInstanceId;
        String workflowStepId;
        String workflowDefinitionId;
        LyshraOpenAppHumanTaskType taskType;
        LyshraOpenAppHumanTaskStatus status;
        String title;
        String description;
        int priority;
        List<String> assignees = new ArrayList<>();
        List<String> candidateGroups = new ArrayList<>();
        String claimedBy;
        Map<String, Object> taskData = new HashMap<>();
        ILyshraOpenAppHumanTaskFormSchema formSchema;
        Instant createdAt;
        Instant dueAtValue;
        Duration timeout;
        ILyshraOpenAppHumanTaskEscalation escalationConfig;
        Instant updatedAt;
        Instant completedAt;
        Map<String, Object> resultData;
        String decisionReason;
        List<ILyshraOpenAppHumanTaskComment> comments = new ArrayList<>();
        List<ILyshraOpenAppHumanTaskAuditEntry> auditTrail = new ArrayList<>();
        Map<String, Object> metadata = new HashMap<>();

        @Override
        public Optional<String> getClaimedBy() {
            return Optional.ofNullable(claimedBy);
        }

        @Override
        public Optional<String> getWorkflowDefinitionId() {
            return Optional.ofNullable(workflowDefinitionId);
        }

        @Override
        public Optional<String> getDecisionReason() {
            return Optional.ofNullable(decisionReason);
        }

        @Override
        public Optional<ILyshraOpenAppHumanTaskFormSchema> getFormSchema() {
            return Optional.ofNullable(formSchema);
        }

        @Override
        public Optional<Instant> getDueAt() {
            return Optional.ofNullable(dueAtValue);
        }

        @Override
        public Optional<Duration> getTimeout() {
            return Optional.ofNullable(timeout);
        }

        @Override
        public Optional<ILyshraOpenAppHumanTaskEscalation> getEscalationConfig() {
            return Optional.ofNullable(escalationConfig);
        }

        @Override
        public Optional<Instant> getCompletedAt() {
            return Optional.ofNullable(completedAt);
        }

        @Override
        public Optional<Map<String, Object>> getResultData() {
            return Optional.ofNullable(resultData);
        }
    }

    @Data
    private static class InMemoryComment implements ILyshraOpenAppHumanTaskComment {
        String commentId;
        String author;
        String content;
        Instant createdAt;
        Instant editedAt;
        boolean internal;
        List<ILyshraOpenAppHumanTaskAttachment> attachments = Collections.emptyList();
        List<String> mentions = Collections.emptyList();
        Map<String, Object> metadata = Collections.emptyMap();
    }

    @Data
    private static class InMemoryAuditEntry implements ILyshraOpenAppHumanTaskAuditEntry {
        String entryId;
        Instant timestamp;
        AuditAction action;
        LyshraOpenAppHumanTaskStatus previousStatus;
        LyshraOpenAppHumanTaskStatus newStatus;
        String actorId;
        ActorType actorType;
        String description;
        Map<String, Object> data = Collections.emptyMap();
        String clientInfo;
        String correlationId;

        @Override
        public Optional<LyshraOpenAppHumanTaskStatus> getPreviousStatus() {
            return Optional.ofNullable(previousStatus);
        }

        @Override
        public Optional<LyshraOpenAppHumanTaskStatus> getNewStatus() {
            return Optional.ofNullable(newStatus);
        }

        @Override
        public Optional<String> getClientInfo() {
            return Optional.ofNullable(clientInfo);
        }

        @Override
        public Optional<String> getCorrelationId() {
            return Optional.ofNullable(correlationId);
        }
    }
}
