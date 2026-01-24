package com.lyshra.open.app.core.engine.humantask.impl;

import com.lyshra.open.app.core.engine.audit.ILyshraOpenAppHumanTaskAuditService;
import com.lyshra.open.app.core.engine.audit.LyshraOpenAppHumanTaskAuditEntryModel;
import com.lyshra.open.app.core.engine.audit.impl.InMemoryHumanTaskAuditService;
import com.lyshra.open.app.core.engine.humantask.ILyshraOpenAppHumanTaskService;
import com.lyshra.open.app.core.engine.notification.ITaskNotificationService;
import com.lyshra.open.app.core.engine.notification.TaskNotificationEvent;
import com.lyshra.open.app.core.engine.notification.impl.CompositeNotificationService;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.humantask.*;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskProcessor.ILyshraOpenAppHumanTaskConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskStatus;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskType;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Standalone in-memory implementation of {@link ILyshraOpenAppHumanTaskService}
 * designed specifically for local testing and validation.
 *
 * <h2>Purpose</h2>
 * <p>This implementation allows testing of human task workflows without any external
 * dependencies such as databases, message queues, or other services. It provides:
 * </p>
 * <ul>
 *   <li>Complete task lifecycle simulation (create, assign, claim, complete, reject, cancel)</li>
 *   <li>Testing utility methods for setup, teardown, and state inspection</li>
 *   <li>Event callbacks for observing task state changes</li>
 *   <li>Configurable behavior for different test scenarios</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create service for testing
 * InMemoryHumanTaskService taskService = InMemoryHumanTaskService.create();
 *
 * // Add event listener
 * taskService.onTaskStateChange((task, oldStatus, newStatus) -> {
 *     System.out.println("Task " + task.getTaskId() + " changed from " + oldStatus + " to " + newStatus);
 * });
 *
 * // Create a task
 * ILyshraOpenAppHumanTask task = taskService.createTask(
 *     "workflow-123", "step-1", LyshraOpenAppHumanTaskType.APPROVAL,
 *     config, context).block();
 *
 * // Simulate user actions
 * taskService.claimTask(task.getTaskId(), "user-1").block();
 * taskService.approveTask(task.getTaskId(), "user-1", "Looks good").block();
 *
 * // Verify state
 * assertThat(taskService.getTaskHistory(task.getTaskId())).hasSize(3);
 *
 * // Clean up
 * taskService.reset();
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This implementation is thread-safe and can be used in concurrent test scenarios.</p>
 *
 * @see ILyshraOpenAppHumanTaskService
 */
@Slf4j
public class InMemoryHumanTaskService implements ILyshraOpenAppHumanTaskService {

    // Task storage
    private final Map<String, TestableHumanTask> tasks = new ConcurrentHashMap<>();

    // ID generation
    private final AtomicLong taskIdCounter = new AtomicLong(0);
    private String taskIdPrefix = "task-";

    // Event listeners for testing
    private final List<TaskStateChangeListener> stateChangeListeners = new ArrayList<>();
    private final List<TaskCreatedListener> createdListeners = new ArrayList<>();
    private final List<TaskCompletedListener> completedListeners = new ArrayList<>();

    // Configuration
    private boolean autoGenerateIds = true;
    private Duration defaultTimeout = null;
    private boolean simulateDelays = false;
    private Duration simulatedDelay = Duration.ofMillis(10);

    // Audit service for centralized audit logging
    private ILyshraOpenAppHumanTaskAuditService auditService;

    // Notification service for alerting users
    private ITaskNotificationService notificationService;
    private boolean notificationsEnabled = true;

    // Statistics for testing
    private final AtomicLong totalTasksCreated = new AtomicLong(0);
    private final AtomicLong totalTasksCompleted = new AtomicLong(0);
    private final AtomicLong totalTasksRejected = new AtomicLong(0);
    private final AtomicLong totalTasksCancelled = new AtomicLong(0);

    /**
     * Creates a new in-memory human task service instance.
     */
    public InMemoryHumanTaskService() {
        log.info("Created InMemoryHumanTaskService for testing");
    }

    /**
     * Factory method for creating a new instance.
     */
    public static InMemoryHumanTaskService create() {
        return new InMemoryHumanTaskService();
    }

    /**
     * Factory method for creating an instance with custom configuration.
     */
    public static InMemoryHumanTaskService create(Consumer<InMemoryHumanTaskService> configurator) {
        InMemoryHumanTaskService service = new InMemoryHumanTaskService();
        configurator.accept(service);
        return service;
    }

    // ========================================================================
    // CONFIGURATION METHODS
    // ========================================================================

    /**
     * Sets the prefix for auto-generated task IDs.
     */
    public InMemoryHumanTaskService withTaskIdPrefix(String prefix) {
        this.taskIdPrefix = prefix;
        return this;
    }

    /**
     * Enables or disables auto-generation of task IDs.
     */
    public InMemoryHumanTaskService withAutoGenerateIds(boolean autoGenerate) {
        this.autoGenerateIds = autoGenerate;
        return this;
    }

    /**
     * Sets a default timeout for all tasks (useful for timeout testing).
     */
    public InMemoryHumanTaskService withDefaultTimeout(Duration timeout) {
        this.defaultTimeout = timeout;
        return this;
    }

    /**
     * Enables simulated delays to mimic real-world latency.
     */
    public InMemoryHumanTaskService withSimulatedDelays(boolean simulate, Duration delay) {
        this.simulateDelays = simulate;
        this.simulatedDelay = delay;
        return this;
    }

    /**
     * Sets the audit service for centralized audit logging.
     * All task lifecycle events will be logged to this service.
     */
    public InMemoryHumanTaskService withAuditService(ILyshraOpenAppHumanTaskAuditService auditService) {
        this.auditService = auditService;
        log.info("Audit service configured for centralized audit logging");
        return this;
    }

    /**
     * Enables default in-memory audit service for centralized logging.
     */
    public InMemoryHumanTaskService withDefaultAuditService() {
        this.auditService = InMemoryHumanTaskAuditService.getInstance();
        log.info("Default in-memory audit service configured");
        return this;
    }

    /**
     * Gets the configured audit service.
     */
    public ILyshraOpenAppHumanTaskAuditService getAuditService() {
        return auditService;
    }

    /**
     * Sets the notification service for alerting users about task events.
     */
    public InMemoryHumanTaskService withNotificationService(ITaskNotificationService notificationService) {
        this.notificationService = notificationService;
        log.info("Notification service configured for task alerts");
        return this;
    }

    /**
     * Enables default console notification service.
     */
    public InMemoryHumanTaskService withDefaultNotificationService() {
        this.notificationService = CompositeNotificationService.withConsole();
        log.info("Default console notification service configured");
        return this;
    }

    /**
     * Gets the configured notification service.
     */
    public ITaskNotificationService getNotificationService() {
        return notificationService;
    }

    /**
     * Enables or disables notifications.
     */
    public InMemoryHumanTaskService withNotificationsEnabled(boolean enabled) {
        this.notificationsEnabled = enabled;
        return this;
    }

    // ========================================================================
    // EVENT LISTENER REGISTRATION
    // ========================================================================

    /**
     * Registers a listener for task state changes.
     */
    public InMemoryHumanTaskService onTaskStateChange(TaskStateChangeListener listener) {
        stateChangeListeners.add(listener);
        return this;
    }

    /**
     * Registers a listener for task creation events.
     */
    public InMemoryHumanTaskService onTaskCreated(TaskCreatedListener listener) {
        createdListeners.add(listener);
        return this;
    }

    /**
     * Registers a listener for task completion events.
     */
    public InMemoryHumanTaskService onTaskCompleted(TaskCompletedListener listener) {
        completedListeners.add(listener);
        return this;
    }

    // ========================================================================
    // TASK CREATION
    // ========================================================================

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

        return maybeDelay(Mono.fromCallable(() -> {
            String taskId = generateTaskId();
            Instant now = Instant.now();

            log.debug("Creating task: id={}, type={}, workflow={}", taskId, taskType, workflowInstanceId);

            TestableHumanTask task = new TestableHumanTask();
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
            task.metadata = new HashMap<>();

            // Apply timeout
            Duration timeout = config.getTimeout().orElse(defaultTimeout);
            if (timeout != null) {
                task.timeout = timeout;
                task.dueAt = now.plus(timeout);
            }

            // Apply form schema
            config.getFormSchema().ifPresent(schema -> task.formSchema = schema);

            // Apply escalation config
            config.getEscalation().ifPresent(escalation -> task.escalationConfig = escalation);

            // Add creation audit entry and log to centralized audit service
            createAndLogAuditEntry(
                    task,
                    ILyshraOpenAppHumanTaskAuditEntry.AuditAction.CREATED,
                    null,
                    LyshraOpenAppHumanTaskStatus.PENDING,
                    "system",
                    ILyshraOpenAppHumanTaskAuditEntry.ActorType.WORKFLOW_ENGINE,
                    "Task created: " + task.getTitle(),
                    Map.of("taskType", taskType.name(), "priority", task.getPriority()),
                    null
            );

            // Store task
            tasks.put(taskId, task);
            totalTasksCreated.incrementAndGet();

            // Notify listeners
            createdListeners.forEach(l -> l.onTaskCreated(task));

            // Send notification to assignees
            notifyTaskCreated(task);

            log.info("Created task: id={}, type={}, workflow={}", taskId, taskType, workflowInstanceId);
            return task;
        }));
    }

    // ========================================================================
    // TASK RETRIEVAL
    // ========================================================================

    @Override
    public Mono<ILyshraOpenAppHumanTask> getTask(String taskId) {
        return maybeDelay(Mono.fromCallable(() -> tasks.get(taskId)));
    }

    // ========================================================================
    // TASK CLAIMING
    // ========================================================================

    @Override
    public Mono<ILyshraOpenAppHumanTask> claimTask(String taskId, String userId) {
        return maybeDelay(Mono.fromCallable(() -> {
            TestableHumanTask task = getTaskOrThrow(taskId);

            if (task.status != LyshraOpenAppHumanTaskStatus.PENDING
                    && task.status != LyshraOpenAppHumanTaskStatus.ASSIGNED) {
                throw new IllegalStateException("Task cannot be claimed in state: " + task.status);
            }

            if (task.claimedBy != null) {
                throw new IllegalStateException("Task already claimed by: " + task.claimedBy);
            }

            LyshraOpenAppHumanTaskStatus oldStatus = task.status;
            task.claimedBy = userId;
            task.claimedAt = Instant.now();
            task.status = LyshraOpenAppHumanTaskStatus.IN_PROGRESS;
            task.updatedAt = Instant.now();

            createAndLogAuditEntry(
                    task,
                    ILyshraOpenAppHumanTaskAuditEntry.AuditAction.CLAIMED,
                    oldStatus,
                    task.status,
                    userId,
                    ILyshraOpenAppHumanTaskAuditEntry.ActorType.USER,
                    "Task claimed by " + userId,
                    null,
                    null
            );

            notifyStateChange(task, oldStatus, task.status);

            // Send notification to other assignees
            notifyTaskClaimed(task, userId);

            log.info("Task claimed: id={}, user={}", taskId, userId);
            return task;
        }));
    }

    @Override
    public Mono<ILyshraOpenAppHumanTask> unclaimTask(String taskId, String userId) {
        return maybeDelay(Mono.fromCallable(() -> {
            TestableHumanTask task = getTaskOrThrow(taskId);

            if (!userId.equals(task.claimedBy)) {
                throw new IllegalStateException("Task not claimed by this user: " + userId);
            }

            LyshraOpenAppHumanTaskStatus oldStatus = task.status;
            task.claimedBy = null;
            task.claimedAt = null;
            task.status = LyshraOpenAppHumanTaskStatus.PENDING;
            task.updatedAt = Instant.now();

            createAndLogAuditEntry(
                    task,
                    ILyshraOpenAppHumanTaskAuditEntry.AuditAction.UNCLAIMED,
                    oldStatus,
                    task.status,
                    userId,
                    ILyshraOpenAppHumanTaskAuditEntry.ActorType.USER,
                    "Task released by " + userId,
                    null,
                    null
            );

            notifyStateChange(task, oldStatus, task.status);
            log.info("Task unclaimed: id={}, user={}", taskId, userId);
            return task;
        }));
    }

    @Override
    public Mono<ILyshraOpenAppHumanTask> delegateTask(String taskId, String fromUserId, String toUserId) {
        return maybeDelay(Mono.fromCallable(() -> {
            TestableHumanTask task = getTaskOrThrow(taskId);

            task.claimedBy = toUserId;
            task.claimedAt = Instant.now();
            task.status = LyshraOpenAppHumanTaskStatus.IN_PROGRESS;
            task.updatedAt = Instant.now();

            createAndLogAuditEntry(
                    task,
                    ILyshraOpenAppHumanTaskAuditEntry.AuditAction.DELEGATED,
                    null,
                    task.status,
                    fromUserId,
                    ILyshraOpenAppHumanTaskAuditEntry.ActorType.USER,
                    "Task delegated from " + fromUserId + " to " + toUserId,
                    Map.of("fromUser", fromUserId, "toUser", toUserId),
                    null
            );

            // Send notification to the delegatee
            notifyTaskDelegated(task, fromUserId, toUserId);

            log.info("Task delegated: id={}, from={}, to={}", taskId, fromUserId, toUserId);
            return task;
        }));
    }

    @Override
    public Mono<ILyshraOpenAppHumanTask> addComment(String taskId, String userId, String content, boolean isInternal) {
        return maybeDelay(Mono.fromCallable(() -> {
            TestableHumanTask task = getTaskOrThrow(taskId);

            TestableComment comment = new TestableComment();
            comment.commentId = UUID.randomUUID().toString();
            comment.author = userId;
            comment.content = content;
            comment.createdAt = Instant.now();
            comment.internal = isInternal;

            task.comments.add(comment);
            task.updatedAt = Instant.now();

            createAndLogAuditEntry(
                    task,
                    ILyshraOpenAppHumanTaskAuditEntry.AuditAction.COMMENTED,
                    null,
                    null,
                    userId,
                    ILyshraOpenAppHumanTaskAuditEntry.ActorType.USER,
                    "Comment added by " + userId,
                    Map.of("isInternal", isInternal),
                    null
            );

            log.debug("Comment added to task: id={}, user={}", taskId, userId);
            return task;
        }));
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

    private Mono<ILyshraOpenAppHumanTask> resolveTask(
            String taskId,
            String userId,
            LyshraOpenAppHumanTaskStatus newStatus,
            Map<String, Object> resultData,
            String reason) {

        return maybeDelay(Mono.fromCallable(() -> {
            TestableHumanTask task = getTaskOrThrow(taskId);

            if (isTerminalStatus(task.status)) {
                throw new IllegalStateException("Task is already in terminal state: " + task.status);
            }

            LyshraOpenAppHumanTaskStatus oldStatus = task.status;
            task.status = newStatus;
            task.updatedAt = Instant.now();
            task.completedAt = Instant.now();
            task.resolvedBy = userId;

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

            String description = "Task " + newStatus.name().toLowerCase() + " by " + userId;
            createAndLogAuditEntry(
                    task,
                    action,
                    oldStatus,
                    newStatus,
                    userId,
                    ILyshraOpenAppHumanTaskAuditEntry.ActorType.USER,
                    description,
                    resultData != null ? resultData : Map.of(),
                    reason
            );

            // Update statistics
            switch (newStatus) {
                case COMPLETED, APPROVED -> totalTasksCompleted.incrementAndGet();
                case REJECTED -> totalTasksRejected.incrementAndGet();
                case CANCELLED -> totalTasksCancelled.incrementAndGet();
                default -> {}
            }

            notifyStateChange(task, oldStatus, newStatus);
            completedListeners.forEach(l -> l.onTaskCompleted(task, newStatus));

            // Send notifications based on resolution type
            switch (newStatus) {
                case APPROVED -> notifyTaskApproved(task, userId, reason);
                case REJECTED -> notifyTaskRejected(task, userId, reason);
                case CANCELLED -> notifyTaskCancelled(task, userId, reason);
                case COMPLETED -> notifyTaskCompleted(task, userId);
                default -> {}
            }

            log.info("Task resolved: id={}, user={}, status={}", taskId, userId, newStatus);
            return task;
        }));
    }

    // ========================================================================
    // TASK LISTING OPERATIONS
    // ========================================================================

    @Override
    public Flux<ILyshraOpenAppHumanTask> listTasks() {
        return Flux.fromIterable(tasks.values()).map(t -> t);
    }

    @Override
    public Flux<ILyshraOpenAppHumanTask> listTasksByStatus(LyshraOpenAppHumanTaskStatus status) {
        return filterTasks(task -> task.status == status);
    }

    @Override
    public Flux<ILyshraOpenAppHumanTask> listTasksByStatusIn(List<LyshraOpenAppHumanTaskStatus> statuses) {
        return filterTasks(task -> statuses.contains(task.status));
    }

    @Override
    public Flux<ILyshraOpenAppHumanTask> listTasksByAssignee(String userId) {
        return filterTasks(task -> task.assignees.contains(userId));
    }

    @Override
    public Flux<ILyshraOpenAppHumanTask> listClaimableTasksForUser(String userId, List<String> userGroups) {
        return filterTasks(task -> {
            if (isTerminalStatus(task.status) || task.claimedBy != null) {
                return false;
            }
            if (task.assignees.contains(userId)) {
                return true;
            }
            if (userGroups != null) {
                return task.candidateGroups.stream().anyMatch(userGroups::contains);
            }
            return false;
        });
    }

    @Override
    public Flux<ILyshraOpenAppHumanTask> listTasksClaimedBy(String userId) {
        return filterTasks(task -> userId.equals(task.claimedBy));
    }

    @Override
    public Flux<ILyshraOpenAppHumanTask> listTasksByWorkflowInstance(String workflowInstanceId) {
        return filterTasks(task -> workflowInstanceId.equals(task.workflowInstanceId));
    }

    @Override
    public Flux<ILyshraOpenAppHumanTask> listTasksByWorkflowDefinition(String workflowDefinitionId) {
        return filterTasks(task -> workflowDefinitionId.equals(task.workflowDefinitionId));
    }

    @Override
    public Flux<ILyshraOpenAppHumanTask> listTasksByType(LyshraOpenAppHumanTaskType taskType) {
        return filterTasks(task -> task.taskType == taskType);
    }

    @Override
    public Flux<ILyshraOpenAppHumanTask> listPendingTasksForUser(String userId, List<String> userGroups) {
        return filterTasks(task -> {
            if (isTerminalStatus(task.status)) {
                return false;
            }
            if (userId.equals(task.claimedBy)) {
                return true;
            }
            if (task.claimedBy == null) {
                if (task.assignees.contains(userId)) {
                    return true;
                }
                if (userGroups != null) {
                    return task.candidateGroups.stream().anyMatch(userGroups::contains);
                }
            }
            return false;
        });
    }

    @Override
    public Flux<ILyshraOpenAppHumanTask> listOverdueTasks() {
        Instant now = Instant.now();
        return filterTasks(task -> !isTerminalStatus(task.status)
                && task.dueAt != null
                && task.dueAt.isBefore(now));
    }

    @Override
    public Flux<ILyshraOpenAppHumanTask> listTasksDueBefore(Instant before) {
        return filterTasks(task -> !isTerminalStatus(task.status)
                && task.dueAt != null
                && task.dueAt.isBefore(before));
    }

    @Override
    public Flux<ILyshraOpenAppHumanTask> listTasksCreatedBetween(Instant from, Instant to) {
        return filterTasks(task -> task.createdAt != null
                && !task.createdAt.isBefore(from)
                && task.createdAt.isBefore(to));
    }

    private Flux<ILyshraOpenAppHumanTask> filterTasks(Predicate<TestableHumanTask> predicate) {
        return Flux.fromIterable(tasks.values())
                .filter(predicate)
                .map(t -> t);
    }

    // ========================================================================
    // COUNTING OPERATIONS
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
            TestableHumanTask removed = tasks.remove(taskId);
            if (removed != null) {
                log.debug("Deleted task: id={}", taskId);
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
            log.debug("Deleted {} tasks with status: {}", toDelete.size(), status);
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
            log.debug("Deleted {} old tasks", toDelete.size());
            return (long) toDelete.size();
        });
    }

    // ========================================================================
    // UPDATE OPERATIONS
    // ========================================================================

    @Override
    public Mono<ILyshraOpenAppHumanTask> updatePriority(String taskId, int priority) {
        return maybeDelay(Mono.fromCallable(() -> {
            TestableHumanTask task = getTaskOrThrow(taskId);
            task.priority = Math.max(1, Math.min(10, priority));
            task.updatedAt = Instant.now();
            addDataUpdateAudit(task, "Priority updated to " + task.priority);
            log.debug("Updated priority: id={}, priority={}", taskId, task.priority);
            return task;
        }));
    }

    @Override
    public Mono<ILyshraOpenAppHumanTask> updateDueDate(String taskId, Instant dueAt) {
        return maybeDelay(Mono.fromCallable(() -> {
            TestableHumanTask task = getTaskOrThrow(taskId);
            task.dueAt = dueAt;
            task.updatedAt = Instant.now();
            addDataUpdateAudit(task, "Due date updated to " + dueAt);
            log.debug("Updated due date: id={}, dueAt={}", taskId, dueAt);
            return task;
        }));
    }

    @Override
    public Mono<ILyshraOpenAppHumanTask> reassignTask(String taskId, List<String> assignees) {
        return maybeDelay(Mono.fromCallable(() -> {
            TestableHumanTask task = getTaskOrThrow(taskId);
            task.assignees = new ArrayList<>(assignees);
            task.updatedAt = Instant.now();

            createAndLogAuditEntry(
                    task,
                    ILyshraOpenAppHumanTaskAuditEntry.AuditAction.REASSIGNED,
                    null, null, "system",
                    ILyshraOpenAppHumanTaskAuditEntry.ActorType.WORKFLOW_ENGINE,
                    "Reassigned to: " + String.join(", ", assignees),
                    Map.of("newAssignees", assignees),
                    null
            );

            // Send notification to new assignees
            notifyTaskReassigned(task, assignees, "system");

            log.debug("Reassigned task: id={}, assignees={}", taskId, assignees);
            return task;
        }));
    }

    @Override
    public Mono<ILyshraOpenAppHumanTask> updateCandidateGroups(String taskId, List<String> candidateGroups) {
        return maybeDelay(Mono.fromCallable(() -> {
            TestableHumanTask task = getTaskOrThrow(taskId);
            task.candidateGroups = new ArrayList<>(candidateGroups);
            task.updatedAt = Instant.now();
            addDataUpdateAudit(task, "Candidate groups updated");
            log.debug("Updated candidate groups: id={}, groups={}", taskId, candidateGroups);
            return task;
        }));
    }

    // ========================================================================
    // TESTING UTILITY METHODS
    // ========================================================================

    /**
     * Resets the service to initial state. Clears all tasks, statistics, and listeners.
     */
    public void reset() {
        tasks.clear();
        taskIdCounter.set(0);
        totalTasksCreated.set(0);
        totalTasksCompleted.set(0);
        totalTasksRejected.set(0);
        totalTasksCancelled.set(0);
        log.info("InMemoryHumanTaskService reset");
    }

    /**
     * Clears all tasks but keeps statistics and configuration.
     */
    public void clearTasks() {
        tasks.clear();
        log.debug("Cleared all tasks");
    }

    /**
     * Clears all event listeners.
     */
    public void clearListeners() {
        stateChangeListeners.clear();
        createdListeners.clear();
        completedListeners.clear();
        log.debug("Cleared all listeners");
    }

    /**
     * Gets the complete audit trail for a task.
     */
    public List<ILyshraOpenAppHumanTaskAuditEntry> getTaskHistory(String taskId) {
        TestableHumanTask task = tasks.get(taskId);
        return task != null ? new ArrayList<>(task.auditTrail) : Collections.emptyList();
    }

    /**
     * Gets all tasks as a modifiable list (for testing).
     */
    public List<ILyshraOpenAppHumanTask> getAllTasks() {
        return new ArrayList<>(tasks.values());
    }

    /**
     * Gets all tasks matching a predicate.
     */
    public List<ILyshraOpenAppHumanTask> findTasks(Predicate<ILyshraOpenAppHumanTask> predicate) {
        return tasks.values().stream()
                .map(t -> (ILyshraOpenAppHumanTask) t)
                .filter(predicate)
                .toList();
    }

    /**
     * Pre-populates a task for testing.
     */
    public TestableHumanTask populateTask(Consumer<TestableHumanTask> configurator) {
        String taskId = generateTaskId();
        TestableHumanTask task = new TestableHumanTask();
        task.taskId = taskId;
        task.createdAt = Instant.now();
        task.updatedAt = Instant.now();
        task.status = LyshraOpenAppHumanTaskStatus.PENDING;
        task.priority = 5;
        task.assignees = new ArrayList<>();
        task.candidateGroups = new ArrayList<>();
        task.taskData = new HashMap<>();
        task.comments = new ArrayList<>();
        task.auditTrail = new ArrayList<>();
        task.metadata = new HashMap<>();

        configurator.accept(task);
        tasks.put(taskId, task);
        totalTasksCreated.incrementAndGet();
        return task;
    }

    /**
     * Simulates a timeout for a task (for timeout testing).
     */
    public Mono<ILyshraOpenAppHumanTask> simulateTimeout(String taskId, String reason) {
        return maybeDelay(Mono.fromCallable(() -> {
            TestableHumanTask task = getTaskOrThrow(taskId);

            if (isTerminalStatus(task.status)) {
                throw new IllegalStateException("Task is already in terminal state: " + task.status);
            }

            LyshraOpenAppHumanTaskStatus oldStatus = task.status;
            task.status = LyshraOpenAppHumanTaskStatus.TIMED_OUT;
            task.completedAt = Instant.now();
            task.updatedAt = Instant.now();
            task.autoResolved = true;
            task.autoResolutionReason = reason != null ? reason : "Task timed out";

            createAndLogAuditEntry(
                    task,
                    ILyshraOpenAppHumanTaskAuditEntry.AuditAction.TIMEOUT,
                    oldStatus,
                    LyshraOpenAppHumanTaskStatus.TIMED_OUT,
                    "system",
                    ILyshraOpenAppHumanTaskAuditEntry.ActorType.TIMER,
                    task.autoResolutionReason,
                    Map.of("autoResolved", true),
                    reason
            );

            notifyStateChange(task, oldStatus, task.status);

            // Send timeout notification
            notifyTaskTimeout(task);

            log.info("Task timed out: id={}", taskId);
            return task;
        }));
    }

    /**
     * Simulates an escalation for a task.
     */
    public Mono<ILyshraOpenAppHumanTask> simulateEscalation(String taskId, List<String> escalateTo, String reason) {
        return maybeDelay(Mono.fromCallable(() -> {
            TestableHumanTask task = getTaskOrThrow(taskId);

            LyshraOpenAppHumanTaskStatus oldStatus = task.status;
            task.status = LyshraOpenAppHumanTaskStatus.ESCALATED;
            task.assignees = new ArrayList<>(escalateTo);
            task.claimedBy = null;
            task.escalationCount++;
            task.updatedAt = Instant.now();

            createAndLogAuditEntry(
                    task,
                    ILyshraOpenAppHumanTaskAuditEntry.AuditAction.ESCALATED,
                    oldStatus,
                    LyshraOpenAppHumanTaskStatus.ESCALATED,
                    "system",
                    ILyshraOpenAppHumanTaskAuditEntry.ActorType.ESCALATION_HANDLER,
                    reason != null ? reason : "Task escalated",
                    Map.of("escalateTo", escalateTo, "escalationCount", task.escalationCount),
                    reason
            );

            notifyStateChange(task, oldStatus, task.status);

            // Send escalation notification
            notifyTaskEscalated(task, escalateTo, reason);

            log.info("Task escalated: id={}, to={}", taskId, escalateTo);
            return task;
        }));
    }

    /**
     * Gets statistics for testing assertions.
     */
    public TaskStatistics getStatistics() {
        return TaskStatistics.builder()
                .totalTasks(tasks.size())
                .totalCreated(totalTasksCreated.get())
                .totalCompleted(totalTasksCompleted.get())
                .totalRejected(totalTasksRejected.get())
                .totalCancelled(totalTasksCancelled.get())
                .pendingTasks(countByStatus(LyshraOpenAppHumanTaskStatus.PENDING))
                .inProgressTasks(countByStatus(LyshraOpenAppHumanTaskStatus.IN_PROGRESS))
                .build();
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    private String generateTaskId() {
        if (autoGenerateIds) {
            return taskIdPrefix + taskIdCounter.incrementAndGet();
        }
        return UUID.randomUUID().toString();
    }

    private TestableHumanTask getTaskOrThrow(String taskId) {
        TestableHumanTask task = tasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        return task;
    }

    private boolean isTerminalStatus(LyshraOpenAppHumanTaskStatus status) {
        return status == LyshraOpenAppHumanTaskStatus.APPROVED
                || status == LyshraOpenAppHumanTaskStatus.REJECTED
                || status == LyshraOpenAppHumanTaskStatus.COMPLETED
                || status == LyshraOpenAppHumanTaskStatus.CANCELLED
                || status == LyshraOpenAppHumanTaskStatus.TIMED_OUT
                || status == LyshraOpenAppHumanTaskStatus.EXPIRED;
    }

    private <T> Mono<T> maybeDelay(Mono<T> mono) {
        if (simulateDelays && simulatedDelay != null) {
            return Mono.delay(simulatedDelay).then(mono);
        }
        return mono;
    }

    private void notifyStateChange(TestableHumanTask task, LyshraOpenAppHumanTaskStatus oldStatus, LyshraOpenAppHumanTaskStatus newStatus) {
        stateChangeListeners.forEach(l -> l.onStateChange(task, oldStatus, newStatus));
    }

    private long countByStatus(LyshraOpenAppHumanTaskStatus status) {
        return tasks.values().stream().filter(t -> t.status == status).count();
    }

    private void addDataUpdateAudit(TestableHumanTask task, String description) {
        createAndLogAuditEntry(
                task,
                ILyshraOpenAppHumanTaskAuditEntry.AuditAction.DATA_UPDATED,
                null, null, "system",
                ILyshraOpenAppHumanTaskAuditEntry.ActorType.WORKFLOW_ENGINE,
                description,
                null,
                null
        );
    }

    private ILyshraOpenAppHumanTaskAuditEntry createAuditEntry(
            ILyshraOpenAppHumanTaskAuditEntry.AuditAction action,
            LyshraOpenAppHumanTaskStatus prevStatus,
            LyshraOpenAppHumanTaskStatus newStatus,
            String actorId,
            ILyshraOpenAppHumanTaskAuditEntry.ActorType actorType,
            String description) {

        return new TestableAuditEntry(
                UUID.randomUUID().toString(),
                Instant.now(),
                action,
                prevStatus,
                newStatus,
                actorId,
                actorType,
                description
        );
    }

    /**
     * Creates and logs an audit entry for a specific task.
     * This method both adds the entry to the task's audit trail and logs it
     * to the centralized audit service if configured.
     */
    private ILyshraOpenAppHumanTaskAuditEntry createAndLogAuditEntry(
            TestableHumanTask task,
            ILyshraOpenAppHumanTaskAuditEntry.AuditAction action,
            LyshraOpenAppHumanTaskStatus prevStatus,
            LyshraOpenAppHumanTaskStatus newStatus,
            String actorId,
            ILyshraOpenAppHumanTaskAuditEntry.ActorType actorType,
            String description,
            Map<String, Object> data,
            String reason) {

        // Create the audit entry for the task's internal trail
        TestableAuditEntry entry = new TestableAuditEntry(
                UUID.randomUUID().toString(),
                Instant.now(),
                action,
                prevStatus,
                newStatus,
                actorId,
                actorType,
                description
        );

        // Add to task's audit trail
        task.auditTrail.add(entry);

        // Log to centralized audit service if configured
        if (auditService != null) {
            LyshraOpenAppHumanTaskAuditEntryModel auditModel = LyshraOpenAppHumanTaskAuditEntryModel.builder()
                    .entryId(entry.getEntryId())
                    .taskId(task.getTaskId())
                    .workflowInstanceId(task.getWorkflowInstanceId())
                    .timestamp(entry.getTimestamp())
                    .action(action)
                    .previousStatus(prevStatus)
                    .newStatus(newStatus)
                    .actorId(actorId)
                    .actorType(actorType)
                    .description(description)
                    .data(data != null ? data : Map.of())
                    .reason(reason)
                    .build();

            auditService.logAuditEntry(auditModel)
                    .doOnSuccess(e -> log.debug("Audit logged: taskId={}, action={}", task.getTaskId(), action))
                    .doOnError(e -> log.warn("Failed to log audit: taskId={}, action={}, error={}",
                            task.getTaskId(), action, e.getMessage()))
                    .subscribe();
        }

        return entry;
    }

    // ========================================================================
    // NOTIFICATION METHODS
    // ========================================================================

    /**
     * Sends a notification for a task event if notifications are enabled.
     */
    private void sendNotification(TaskNotificationEvent event) {
        if (notificationService != null && notificationsEnabled && event.hasRecipients()) {
            notificationService.notify(event)
                    .doOnSuccess(result -> {
                        if (result.isSuccess()) {
                            log.debug("Notification sent: taskId={}, eventType={}",
                                    event.getTaskId(), event.getEventType());
                        } else {
                            log.warn("Notification not sent: taskId={}, eventType={}, reason={}",
                                    event.getTaskId(), event.getEventType(), result.errorMessage());
                        }
                    })
                    .doOnError(e -> log.warn("Failed to send notification: taskId={}, eventType={}, error={}",
                            event.getTaskId(), event.getEventType(), e.getMessage()))
                    .subscribe();
        }
    }

    /**
     * Creates and sends a task created notification.
     */
    private void notifyTaskCreated(TestableHumanTask task) {
        if (task.getAssignees() != null && !task.getAssignees().isEmpty()) {
            TaskNotificationEvent event = TaskNotificationEvent.taskCreated(
                    task.getTaskId(),
                    task.getTitle(),
                    task.getDescription(),
                    task.getTaskType(),
                    task.getWorkflowInstanceId(),
                    task.getAssignees(),
                    "system"
            );
            sendNotification(event);
        }
    }

    /**
     * Creates and sends a task claimed notification.
     */
    private void notifyTaskClaimed(TestableHumanTask task, String claimedBy) {
        // Notify other assignees that the task was claimed
        List<String> otherAssignees = task.getAssignees() != null
                ? task.getAssignees().stream().filter(a -> !a.equals(claimedBy)).toList()
                : List.of();
        if (!otherAssignees.isEmpty()) {
            TaskNotificationEvent event = TaskNotificationEvent.taskClaimed(
                    task.getTaskId(),
                    task.getTitle(),
                    claimedBy,
                    otherAssignees
            );
            sendNotification(event);
        }
    }

    /**
     * Creates and sends a task delegated notification.
     */
    private void notifyTaskDelegated(TestableHumanTask task, String fromUser, String toUser) {
        TaskNotificationEvent event = TaskNotificationEvent.taskDelegated(
                task.getTaskId(),
                task.getTitle(),
                fromUser,
                toUser
        );
        sendNotification(event);
    }

    /**
     * Creates and sends a task approved notification.
     */
    private void notifyTaskApproved(TestableHumanTask task, String approvedBy, String reason) {
        List<String> notifyUsers = task.getAssignees() != null ? task.getAssignees() : List.of();
        if (!notifyUsers.isEmpty()) {
            TaskNotificationEvent event = TaskNotificationEvent.taskApproved(
                    task.getTaskId(),
                    task.getTitle(),
                    approvedBy,
                    notifyUsers,
                    reason
            );
            sendNotification(event);
        }
    }

    /**
     * Creates and sends a task rejected notification.
     */
    private void notifyTaskRejected(TestableHumanTask task, String rejectedBy, String reason) {
        List<String> notifyUsers = task.getAssignees() != null ? task.getAssignees() : List.of();
        if (!notifyUsers.isEmpty()) {
            TaskNotificationEvent event = TaskNotificationEvent.taskRejected(
                    task.getTaskId(),
                    task.getTitle(),
                    rejectedBy,
                    notifyUsers,
                    reason
            );
            sendNotification(event);
        }
    }

    /**
     * Creates and sends a task completed notification.
     */
    private void notifyTaskCompleted(TestableHumanTask task, String completedBy) {
        List<String> notifyUsers = task.getAssignees() != null ? task.getAssignees() : List.of();
        if (!notifyUsers.isEmpty()) {
            TaskNotificationEvent event = TaskNotificationEvent.taskCompleted(
                    task.getTaskId(),
                    task.getTitle(),
                    completedBy,
                    notifyUsers
            );
            sendNotification(event);
        }
    }

    /**
     * Creates and sends a task escalated notification.
     */
    private void notifyTaskEscalated(TestableHumanTask task, List<String> escalateTo, String reason) {
        if (escalateTo != null && !escalateTo.isEmpty()) {
            TaskNotificationEvent event = TaskNotificationEvent.taskEscalated(
                    task.getTaskId(),
                    task.getTitle(),
                    escalateTo,
                    reason
            );
            sendNotification(event);
        }
    }

    /**
     * Creates and sends a task timeout notification.
     */
    private void notifyTaskTimeout(TestableHumanTask task) {
        List<String> notifyUsers = task.getAssignees() != null ? task.getAssignees() : List.of();
        if (!notifyUsers.isEmpty()) {
            TaskNotificationEvent event = TaskNotificationEvent.taskTimeout(
                    task.getTaskId(),
                    task.getTitle(),
                    notifyUsers
            );
            sendNotification(event);
        }
    }

    /**
     * Creates and sends a task cancelled notification.
     */
    private void notifyTaskCancelled(TestableHumanTask task, String cancelledBy, String reason) {
        List<String> notifyUsers = task.getAssignees() != null ? task.getAssignees() : List.of();
        if (!notifyUsers.isEmpty()) {
            TaskNotificationEvent event = TaskNotificationEvent.taskCancelled(
                    task.getTaskId(),
                    task.getTitle(),
                    cancelledBy,
                    notifyUsers,
                    reason
            );
            sendNotification(event);
        }
    }

    /**
     * Creates and sends a task reassigned notification.
     */
    private void notifyTaskReassigned(TestableHumanTask task, List<String> newAssignees, String actorId) {
        if (newAssignees != null && !newAssignees.isEmpty()) {
            TaskNotificationEvent event = TaskNotificationEvent.builder()
                    .eventType(TaskNotificationEvent.NotificationEventType.TASK_REASSIGNED)
                    .taskId(task.getTaskId())
                    .taskTitle(task.getTitle())
                    .recipients(newAssignees)
                    .actorId(actorId)
                    .message("Task '" + task.getTitle() + "' has been reassigned to you")
                    .build();
            sendNotification(event);
        }
    }

    // ========================================================================
    // EVENT LISTENER INTERFACES
    // ========================================================================

    /**
     * Listener for task state changes.
     */
    @FunctionalInterface
    public interface TaskStateChangeListener {
        void onStateChange(ILyshraOpenAppHumanTask task, LyshraOpenAppHumanTaskStatus oldStatus, LyshraOpenAppHumanTaskStatus newStatus);
    }

    /**
     * Listener for task creation events.
     */
    @FunctionalInterface
    public interface TaskCreatedListener {
        void onTaskCreated(ILyshraOpenAppHumanTask task);
    }

    /**
     * Listener for task completion events.
     */
    @FunctionalInterface
    public interface TaskCompletedListener {
        void onTaskCompleted(ILyshraOpenAppHumanTask task, LyshraOpenAppHumanTaskStatus finalStatus);
    }

    // ========================================================================
    // STATISTICS CLASS
    // ========================================================================

    /**
     * Statistics container for testing assertions.
     */
    @Data
    @Builder
    public static class TaskStatistics {
        private final int totalTasks;
        private final long totalCreated;
        private final long totalCompleted;
        private final long totalRejected;
        private final long totalCancelled;
        private final long pendingTasks;
        private final long inProgressTasks;
    }

    // ========================================================================
    // TESTABLE INTERNAL CLASSES
    // ========================================================================

    /**
     * Testable human task implementation with public setters for test setup.
     */
    @Data
    public static class TestableHumanTask implements ILyshraOpenAppHumanTask {
        public String taskId;
        public String workflowInstanceId;
        public String workflowStepId;
        public String workflowDefinitionId;
        public LyshraOpenAppHumanTaskType taskType;
        public LyshraOpenAppHumanTaskStatus status;
        public String title;
        public String description;
        public int priority = 5;
        public List<String> assignees = new ArrayList<>();
        public List<String> candidateGroups = new ArrayList<>();
        public String claimedBy;
        public Instant claimedAt;
        public Map<String, Object> taskData = new HashMap<>();
        public ILyshraOpenAppHumanTaskFormSchema formSchema;
        public Instant createdAt;
        public Instant dueAt;
        public Duration timeout;
        public ILyshraOpenAppHumanTaskEscalation escalationConfig;
        public Instant updatedAt;
        public Instant completedAt;
        public Map<String, Object> resultData;
        public String decisionReason;
        public String resolvedBy;
        public boolean autoResolved;
        public String autoResolutionReason;
        public int escalationCount;
        public List<ILyshraOpenAppHumanTaskComment> comments = new ArrayList<>();
        public List<ILyshraOpenAppHumanTaskAuditEntry> auditTrail = new ArrayList<>();
        public Map<String, Object> metadata = new HashMap<>();

        @Override
        public Optional<String> getClaimedBy() {
            return Optional.ofNullable(claimedBy);
        }

        @Override
        public Optional<Instant> getClaimedAt() {
            return Optional.ofNullable(claimedAt);
        }

        @Override
        public Optional<String> getWorkflowDefinitionId() {
            return Optional.ofNullable(workflowDefinitionId);
        }

        @Override
        public Optional<ILyshraOpenAppHumanTaskFormSchema> getFormSchema() {
            return Optional.ofNullable(formSchema);
        }

        @Override
        public Optional<Instant> getDueAt() {
            return Optional.ofNullable(dueAt);
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

        @Override
        public Optional<String> getDecisionReason() {
            return Optional.ofNullable(decisionReason);
        }

        @Override
        public Optional<String> getResolvedBy() {
            return Optional.ofNullable(resolvedBy);
        }
    }

    /**
     * Testable comment implementation.
     */
    @Data
    public static class TestableComment implements ILyshraOpenAppHumanTaskComment {
        public String commentId;
        public String author;
        public String content;
        public Instant createdAt;
        public Instant editedAt;
        public boolean internal;
        public List<ILyshraOpenAppHumanTaskAttachment> attachments = Collections.emptyList();
        public List<String> mentions = Collections.emptyList();
        public Map<String, Object> metadata = Collections.emptyMap();
    }

    /**
     * Testable audit entry implementation.
     */
    @Data
    public static class TestableAuditEntry implements ILyshraOpenAppHumanTaskAuditEntry {
        private final String entryId;
        private final Instant timestamp;
        private final AuditAction action;
        private final LyshraOpenAppHumanTaskStatus previousStatusValue;
        private final LyshraOpenAppHumanTaskStatus newStatusValue;
        private final String actorId;
        private final ActorType actorType;
        private final String description;
        private Map<String, Object> data = Collections.emptyMap();
        private String clientInfo;
        private String correlationId;

        public TestableAuditEntry(String entryId, Instant timestamp, AuditAction action,
                                  LyshraOpenAppHumanTaskStatus previousStatus,
                                  LyshraOpenAppHumanTaskStatus newStatus,
                                  String actorId, ActorType actorType, String description) {
            this.entryId = entryId;
            this.timestamp = timestamp;
            this.action = action;
            this.previousStatusValue = previousStatus;
            this.newStatusValue = newStatus;
            this.actorId = actorId;
            this.actorType = actorType;
            this.description = description;
        }

        @Override
        public Optional<LyshraOpenAppHumanTaskStatus> getPreviousStatus() {
            return Optional.ofNullable(previousStatusValue);
        }

        @Override
        public Optional<LyshraOpenAppHumanTaskStatus> getNewStatus() {
            return Optional.ofNullable(newStatusValue);
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
