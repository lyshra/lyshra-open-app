package com.lyshra.open.app.core.engine.humantask.impl;

import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTask;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskAuditEntry;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskProcessor.ILyshraOpenAppHumanTaskConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskStatus;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link InMemoryHumanTaskService}.
 * Demonstrates how to use the in-memory service for testing human task workflows.
 */
class InMemoryHumanTaskServiceTest {

    private InMemoryHumanTaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = InMemoryHumanTaskService.create()
                .withTaskIdPrefix("test-task-");
    }

    // ========================================================================
    // TASK CREATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Task Creation")
    class TaskCreationTests {

        @Test
        @DisplayName("should create task with basic configuration")
        void shouldCreateTaskWithBasicConfiguration() {
            // Given
            ILyshraOpenAppHumanTaskConfig config = createBasicConfig("Approve Request", "Please review and approve");

            // When
            ILyshraOpenAppHumanTask task = taskService.createTask(
                    "workflow-123",
                    "step-1",
                    LyshraOpenAppHumanTaskType.APPROVAL,
                    config,
                    null
            ).block();

            // Then
            assertNotNull(task);
            assertEquals("test-task-1", task.getTaskId());
            assertEquals("workflow-123", task.getWorkflowInstanceId());
            assertEquals("step-1", task.getWorkflowStepId());
            assertEquals(LyshraOpenAppHumanTaskType.APPROVAL, task.getTaskType());
            assertEquals(LyshraOpenAppHumanTaskStatus.PENDING, task.getStatus());
            assertEquals("Approve Request", task.getTitle());
            assertNotNull(task.getCreatedAt());
        }

        @Test
        @DisplayName("should create task with assignees and candidate groups")
        void shouldCreateTaskWithAssigneesAndGroups() {
            // Given
            ILyshraOpenAppHumanTaskConfig config = createConfigWithAssignees(
                    "Review Document",
                    List.of("user1", "user2"),
                    List.of("managers", "reviewers")
            );

            // When
            ILyshraOpenAppHumanTask task = taskService.createTask(
                    "workflow-456",
                    "review-step",
                    LyshraOpenAppHumanTaskType.MANUAL_INPUT,
                    config,
                    null
            ).block();

            // Then
            assertNotNull(task);
            assertEquals(List.of("user1", "user2"), task.getAssignees());
            assertEquals(List.of("managers", "reviewers"), task.getCandidateGroups());
        }

        @Test
        @DisplayName("should create task with data")
        void shouldCreateTaskWithData() {
            // Given
            ILyshraOpenAppHumanTaskConfig config = createBasicConfig("Review Order", "Review the order details");
            Map<String, Object> taskData = Map.of(
                    "orderId", "ORD-123",
                    "amount", 1500.00,
                    "customer", "John Doe"
            );

            // When
            ILyshraOpenAppHumanTask task = taskService.createTaskWithData(
                    "workflow-789",
                    "order-review",
                    LyshraOpenAppHumanTaskType.APPROVAL,
                    config,
                    taskData,
                    null
            ).block();

            // Then
            assertNotNull(task);
            assertEquals("ORD-123", task.getTaskData().get("orderId"));
            assertEquals(1500.00, task.getTaskData().get("amount"));
        }

        @Test
        @DisplayName("should add creation audit entry")
        void shouldAddCreationAuditEntry() {
            // Given
            ILyshraOpenAppHumanTaskConfig config = createBasicConfig("Test Task", "Test");

            // When
            ILyshraOpenAppHumanTask task = taskService.createTask(
                    "workflow-1",
                    "step-1",
                    LyshraOpenAppHumanTaskType.APPROVAL,
                    config,
                    null
            ).block();

            // Then
            List<ILyshraOpenAppHumanTaskAuditEntry> history = taskService.getTaskHistory(task.getTaskId());
            assertEquals(1, history.size());
            assertEquals(ILyshraOpenAppHumanTaskAuditEntry.AuditAction.CREATED, history.get(0).getAction());
        }
    }

    // ========================================================================
    // TASK CLAIMING TESTS
    // ========================================================================

    @Nested
    @DisplayName("Task Claiming")
    class TaskClaimingTests {

        @Test
        @DisplayName("should claim pending task")
        void shouldClaimPendingTask() {
            // Given
            ILyshraOpenAppHumanTask task = createTestTask();

            // When
            ILyshraOpenAppHumanTask claimedTask = taskService.claimTask(task.getTaskId(), "user1").block();

            // Then
            assertNotNull(claimedTask);
            assertEquals(LyshraOpenAppHumanTaskStatus.IN_PROGRESS, claimedTask.getStatus());
            assertTrue(claimedTask.getClaimedBy().isPresent());
            assertEquals("user1", claimedTask.getClaimedBy().get());
        }

        @Test
        @DisplayName("should fail to claim already claimed task")
        void shouldFailToClaimAlreadyClaimedTask() {
            // Given
            ILyshraOpenAppHumanTask task = createTestTask();
            taskService.claimTask(task.getTaskId(), "user1").block();

            // When/Then
            assertThrows(IllegalStateException.class, () ->
                    taskService.claimTask(task.getTaskId(), "user2").block()
            );
        }

        @Test
        @DisplayName("should unclaim task")
        void shouldUnclaimTask() {
            // Given
            ILyshraOpenAppHumanTask task = createTestTask();
            taskService.claimTask(task.getTaskId(), "user1").block();

            // When
            ILyshraOpenAppHumanTask unclaimedTask = taskService.unclaimTask(task.getTaskId(), "user1").block();

            // Then
            assertEquals(LyshraOpenAppHumanTaskStatus.PENDING, unclaimedTask.getStatus());
            assertTrue(unclaimedTask.getClaimedBy().isEmpty());
        }

        @Test
        @DisplayName("should delegate task to another user")
        void shouldDelegateTask() {
            // Given
            ILyshraOpenAppHumanTask task = createTestTask();
            taskService.claimTask(task.getTaskId(), "user1").block();

            // When
            ILyshraOpenAppHumanTask delegatedTask = taskService.delegateTask(
                    task.getTaskId(), "user1", "user2"
            ).block();

            // Then
            assertEquals("user2", delegatedTask.getClaimedBy().orElse(null));
            assertEquals(LyshraOpenAppHumanTaskStatus.IN_PROGRESS, delegatedTask.getStatus());
        }
    }

    // ========================================================================
    // TASK COMPLETION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Task Completion")
    class TaskCompletionTests {

        @Test
        @DisplayName("should approve task")
        void shouldApproveTask() {
            // Given
            ILyshraOpenAppHumanTask task = createTestTask();
            taskService.claimTask(task.getTaskId(), "approver1").block();

            // When
            ILyshraOpenAppHumanTask approvedTask = taskService.approveTask(
                    task.getTaskId(), "approver1", "Looks good"
            ).block();

            // Then
            assertEquals(LyshraOpenAppHumanTaskStatus.APPROVED, approvedTask.getStatus());
            assertTrue(approvedTask.getCompletedAt().isPresent());
            assertEquals("Looks good", approvedTask.getDecisionReason().orElse(null));
        }

        @Test
        @DisplayName("should reject task with reason")
        void shouldRejectTaskWithReason() {
            // Given
            ILyshraOpenAppHumanTask task = createTestTask();

            // When
            ILyshraOpenAppHumanTask rejectedTask = taskService.rejectTask(
                    task.getTaskId(), "reviewer1", "Missing documentation"
            ).block();

            // Then
            assertEquals(LyshraOpenAppHumanTaskStatus.REJECTED, rejectedTask.getStatus());
            assertEquals("Missing documentation", rejectedTask.getDecisionReason().orElse(null));
        }

        @Test
        @DisplayName("should complete task with result data")
        void shouldCompleteTaskWithResultData() {
            // Given
            ILyshraOpenAppHumanTask task = createTestTask(LyshraOpenAppHumanTaskType.MANUAL_INPUT);
            Map<String, Object> resultData = Map.of(
                    "field1", "value1",
                    "quantity", 100,
                    "approved", true
            );

            // When
            ILyshraOpenAppHumanTask completedTask = taskService.completeTask(
                    task.getTaskId(), "user1", resultData, "Form submitted"
            ).block();

            // Then
            assertEquals(LyshraOpenAppHumanTaskStatus.COMPLETED, completedTask.getStatus());
            assertTrue(completedTask.getResultData().isPresent());
            assertEquals("value1", completedTask.getResultData().get().get("field1"));
        }

        @Test
        @DisplayName("should cancel task")
        void shouldCancelTask() {
            // Given
            ILyshraOpenAppHumanTask task = createTestTask();

            // When
            ILyshraOpenAppHumanTask cancelledTask = taskService.cancelTask(
                    task.getTaskId(), "admin", "Workflow cancelled"
            ).block();

            // Then
            assertEquals(LyshraOpenAppHumanTaskStatus.CANCELLED, cancelledTask.getStatus());
        }

        @Test
        @DisplayName("should fail to complete already completed task")
        void shouldFailToCompleteAlreadyCompletedTask() {
            // Given
            ILyshraOpenAppHumanTask task = createTestTask();
            taskService.approveTask(task.getTaskId(), "user1").block();

            // When/Then
            assertThrows(IllegalStateException.class, () ->
                    taskService.rejectTask(task.getTaskId(), "user2", "Too late").block()
            );
        }
    }

    // ========================================================================
    // TASK LISTING TESTS
    // ========================================================================

    @Nested
    @DisplayName("Task Listing")
    class TaskListingTests {

        @Test
        @DisplayName("should list all tasks")
        void shouldListAllTasks() {
            // Given
            createTestTask();
            createTestTask();
            createTestTask();

            // When
            List<ILyshraOpenAppHumanTask> tasks = taskService.listTasks().collectList().block();

            // Then
            assertEquals(3, tasks.size());
        }

        @Test
        @DisplayName("should list tasks by status")
        void shouldListTasksByStatus() {
            // Given
            ILyshraOpenAppHumanTask task1 = createTestTask();
            ILyshraOpenAppHumanTask task2 = createTestTask();
            ILyshraOpenAppHumanTask task3 = createTestTask();
            taskService.claimTask(task1.getTaskId(), "user1").block();
            taskService.approveTask(task2.getTaskId(), "user1").block();

            // When
            List<ILyshraOpenAppHumanTask> pendingTasks = taskService
                    .listTasksByStatus(LyshraOpenAppHumanTaskStatus.PENDING)
                    .collectList().block();
            List<ILyshraOpenAppHumanTask> inProgressTasks = taskService
                    .listTasksByStatus(LyshraOpenAppHumanTaskStatus.IN_PROGRESS)
                    .collectList().block();

            // Then
            assertEquals(1, pendingTasks.size());
            assertEquals(1, inProgressTasks.size());
        }

        @Test
        @DisplayName("should list pending tasks for user")
        void shouldListPendingTasksForUser() {
            // Given
            taskService.populateTask(t -> {
                t.title = "Task for user1";
                t.assignees = List.of("user1");
            });
            taskService.populateTask(t -> {
                t.title = "Task for managers";
                t.candidateGroups = List.of("managers");
            });
            taskService.populateTask(t -> {
                t.title = "Task for user2";
                t.assignees = List.of("user2");
            });

            // When
            List<ILyshraOpenAppHumanTask> tasksForUser1 = taskService
                    .listPendingTasksForUser("user1", List.of("managers"))
                    .collectList().block();

            // Then
            assertEquals(2, tasksForUser1.size()); // Direct assignment + group membership
        }

        @Test
        @DisplayName("should list tasks by workflow instance")
        void shouldListTasksByWorkflowInstance() {
            // Given
            taskService.populateTask(t -> t.workflowInstanceId = "workflow-A");
            taskService.populateTask(t -> t.workflowInstanceId = "workflow-A");
            taskService.populateTask(t -> t.workflowInstanceId = "workflow-B");

            // When
            List<ILyshraOpenAppHumanTask> workflowATasks = taskService
                    .listTasksByWorkflowInstance("workflow-A")
                    .collectList().block();

            // Then
            assertEquals(2, workflowATasks.size());
        }

        @Test
        @DisplayName("should list overdue tasks")
        void shouldListOverdueTasks() {
            // Given
            taskService.populateTask(t -> {
                t.title = "Overdue task";
                t.dueAt = Instant.now().minus(Duration.ofHours(1));
            });
            taskService.populateTask(t -> {
                t.title = "Future task";
                t.dueAt = Instant.now().plus(Duration.ofHours(1));
            });

            // When
            List<ILyshraOpenAppHumanTask> overdueTasks = taskService
                    .listOverdueTasks()
                    .collectList().block();

            // Then
            assertEquals(1, overdueTasks.size());
            assertEquals("Overdue task", overdueTasks.get(0).getTitle());
        }
    }

    // ========================================================================
    // EVENT LISTENER TESTS
    // ========================================================================

    @Nested
    @DisplayName("Event Listeners")
    class EventListenerTests {

        @Test
        @DisplayName("should notify on task creation")
        void shouldNotifyOnTaskCreation() {
            // Given
            AtomicReference<ILyshraOpenAppHumanTask> createdTaskRef = new AtomicReference<>();
            taskService.onTaskCreated(createdTaskRef::set);

            // When
            ILyshraOpenAppHumanTask task = createTestTask();

            // Then
            assertNotNull(createdTaskRef.get());
            assertEquals(task.getTaskId(), createdTaskRef.get().getTaskId());
        }

        @Test
        @DisplayName("should notify on state change")
        void shouldNotifyOnStateChange() {
            // Given
            AtomicReference<LyshraOpenAppHumanTaskStatus> oldStatusRef = new AtomicReference<>();
            AtomicReference<LyshraOpenAppHumanTaskStatus> newStatusRef = new AtomicReference<>();
            taskService.onTaskStateChange((task, oldStatus, newStatus) -> {
                oldStatusRef.set(oldStatus);
                newStatusRef.set(newStatus);
            });

            ILyshraOpenAppHumanTask task = createTestTask();

            // When
            taskService.claimTask(task.getTaskId(), "user1").block();

            // Then
            assertEquals(LyshraOpenAppHumanTaskStatus.PENDING, oldStatusRef.get());
            assertEquals(LyshraOpenAppHumanTaskStatus.IN_PROGRESS, newStatusRef.get());
        }

        @Test
        @DisplayName("should notify on task completion")
        void shouldNotifyOnTaskCompletion() {
            // Given
            AtomicInteger completionCount = new AtomicInteger();
            taskService.onTaskCompleted((task, status) -> completionCount.incrementAndGet());

            ILyshraOpenAppHumanTask task = createTestTask();

            // When
            taskService.approveTask(task.getTaskId(), "user1").block();

            // Then
            assertEquals(1, completionCount.get());
        }
    }

    // ========================================================================
    // SIMULATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Simulation Features")
    class SimulationTests {

        @Test
        @DisplayName("should simulate timeout")
        void shouldSimulateTimeout() {
            // Given
            ILyshraOpenAppHumanTask task = createTestTask();

            // When
            ILyshraOpenAppHumanTask timedOutTask = taskService.simulateTimeout(
                    task.getTaskId(), "SLA breached"
            ).block();

            // Then
            assertEquals(LyshraOpenAppHumanTaskStatus.TIMED_OUT, timedOutTask.getStatus());
        }

        @Test
        @DisplayName("should simulate escalation")
        void shouldSimulateEscalation() {
            // Given
            ILyshraOpenAppHumanTask task = createTestTask();

            // When
            ILyshraOpenAppHumanTask escalatedTask = taskService.simulateEscalation(
                    task.getTaskId(),
                    List.of("manager1", "manager2"),
                    "Escalated due to SLA"
            ).block();

            // Then
            assertEquals(LyshraOpenAppHumanTaskStatus.ESCALATED, escalatedTask.getStatus());
            assertEquals(List.of("manager1", "manager2"), escalatedTask.getAssignees());
        }
    }

    // ========================================================================
    // STATISTICS AND UTILITY TESTS
    // ========================================================================

    @Nested
    @DisplayName("Statistics and Utilities")
    class StatisticsAndUtilityTests {

        @Test
        @DisplayName("should track statistics")
        void shouldTrackStatistics() {
            // Given
            ILyshraOpenAppHumanTask task1 = createTestTask();
            ILyshraOpenAppHumanTask task2 = createTestTask();
            ILyshraOpenAppHumanTask task3 = createTestTask();

            taskService.approveTask(task1.getTaskId(), "user1").block();
            taskService.rejectTask(task2.getTaskId(), "user1", "No").block();

            // When
            InMemoryHumanTaskService.TaskStatistics stats = taskService.getStatistics();

            // Then
            assertEquals(3, stats.getTotalCreated());
            assertEquals(1, stats.getTotalCompleted()); // approved counts as completed
            assertEquals(1, stats.getTotalRejected());
            assertEquals(1, stats.getPendingTasks());
        }

        @Test
        @DisplayName("should reset service state")
        void shouldResetServiceState() {
            // Given
            createTestTask();
            createTestTask();
            assertEquals(2, taskService.getAllTasks().size());

            // When
            taskService.reset();

            // Then
            assertEquals(0, taskService.getAllTasks().size());
            assertEquals(0, taskService.getStatistics().getTotalCreated());
        }

        @Test
        @DisplayName("should get task history")
        void shouldGetTaskHistory() {
            // Given
            ILyshraOpenAppHumanTask task = createTestTask();
            taskService.claimTask(task.getTaskId(), "user1").block();
            taskService.addComment(task.getTaskId(), "user1", "Working on this", false).block();
            taskService.approveTask(task.getTaskId(), "user1", "Done").block();

            // When
            List<ILyshraOpenAppHumanTaskAuditEntry> history = taskService.getTaskHistory(task.getTaskId());

            // Then
            assertEquals(4, history.size()); // CREATED, CLAIMED, COMMENTED, APPROVED
        }

        @Test
        @DisplayName("should populate task for testing")
        void shouldPopulateTaskForTesting() {
            // When
            InMemoryHumanTaskService.TestableHumanTask task = taskService.populateTask(t -> {
                t.title = "Pre-populated Task";
                t.workflowInstanceId = "test-workflow";
                t.taskType = LyshraOpenAppHumanTaskType.DECISION;
                t.priority = 8;
                t.assignees = List.of("tester1");
            });

            // Then
            assertNotNull(task);
            assertEquals("Pre-populated Task", task.getTitle());
            assertEquals("test-workflow", task.getWorkflowInstanceId());
            assertEquals(LyshraOpenAppHumanTaskType.DECISION, task.getTaskType());
            assertEquals(8, task.getPriority());
        }
    }

    // ========================================================================
    // COMPLETE WORKFLOW SIMULATION TEST
    // ========================================================================

    @Test
    @DisplayName("should simulate complete approval workflow")
    void shouldSimulateCompleteApprovalWorkflow() {
        // Setup event tracking
        List<String> events = new ArrayList<>();
        taskService
                .onTaskCreated(task -> events.add("CREATED:" + task.getTaskId()))
                .onTaskStateChange((task, old, newStatus) ->
                        events.add("STATE_CHANGE:" + old + "->" + newStatus))
                .onTaskCompleted((task, status) ->
                        events.add("COMPLETED:" + status));

        // 1. Create approval task
        ILyshraOpenAppHumanTaskConfig config = createConfigWithAssignees(
                "Expense Approval",
                List.of("manager1"),
                List.of("finance-team")
        );

        ILyshraOpenAppHumanTask task = taskService.createTaskWithData(
                "expense-workflow-001",
                "approval-step",
                LyshraOpenAppHumanTaskType.APPROVAL,
                config,
                Map.of("amount", 5000, "description", "Conference travel"),
                null
        ).block();

        // 2. Manager claims the task
        taskService.claimTask(task.getTaskId(), "manager1").block();

        // 3. Manager adds a comment
        taskService.addComment(task.getTaskId(), "manager1", "Reviewing expense report", false).block();

        // 4. Manager approves
        ILyshraOpenAppHumanTask approvedTask = taskService.approveTask(
                task.getTaskId(),
                "manager1",
                "Approved for conference attendance",
                Map.of("approvedAmount", 5000)
        ).block();

        // Verify final state
        assertEquals(LyshraOpenAppHumanTaskStatus.APPROVED, approvedTask.getStatus());
        assertTrue(approvedTask.getCompletedAt().isPresent());
        assertEquals("Approved for conference attendance", approvedTask.getDecisionReason().orElse(null));
        assertEquals(5000, approvedTask.getResultData().get().get("approvedAmount"));

        // Verify audit trail
        List<ILyshraOpenAppHumanTaskAuditEntry> history = taskService.getTaskHistory(task.getTaskId());
        assertEquals(4, history.size()); // CREATED, CLAIMED, COMMENTED, APPROVED

        // Verify events
        assertTrue(events.contains("CREATED:" + task.getTaskId()));
        assertTrue(events.contains("STATE_CHANGE:PENDING->IN_PROGRESS"));
        assertTrue(events.contains("COMPLETED:APPROVED"));
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private ILyshraOpenAppHumanTask createTestTask() {
        return createTestTask(LyshraOpenAppHumanTaskType.APPROVAL);
    }

    private ILyshraOpenAppHumanTask createTestTask(LyshraOpenAppHumanTaskType type) {
        ILyshraOpenAppHumanTaskConfig config = createBasicConfig("Test Task", "Test Description");
        return taskService.createTask(
                "workflow-" + UUID.randomUUID().toString().substring(0, 8),
                "step-1",
                type,
                config,
                null
        ).block();
    }

    private ILyshraOpenAppHumanTaskConfig createBasicConfig(String title, String description) {
        return new ILyshraOpenAppHumanTaskConfig() {
            @Override
            public String getTitle() { return title; }

            @Override
            public String getDescription() { return description; }

            @Override
            public int getPriority() { return 5; }

            @Override
            public List<String> getAssignees() { return Collections.emptyList(); }

            @Override
            public List<String> getCandidateGroups() { return Collections.emptyList(); }

            @Override
            public Optional<Duration> getTimeout() { return Optional.empty(); }

            @Override
            public Optional<com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskEscalation> getEscalation() {
                return Optional.empty();
            }

            @Override
            public Optional<com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskFormSchema> getFormSchema() {
                return Optional.empty();
            }
        };
    }

    private ILyshraOpenAppHumanTaskConfig createConfigWithAssignees(
            String title, List<String> assignees, List<String> groups) {
        return new ILyshraOpenAppHumanTaskConfig() {
            @Override
            public String getTitle() { return title; }

            @Override
            public String getDescription() { return ""; }

            @Override
            public int getPriority() { return 5; }

            @Override
            public List<String> getAssignees() { return assignees; }

            @Override
            public List<String> getCandidateGroups() { return groups; }

            @Override
            public Optional<Duration> getTimeout() { return Optional.empty(); }

            @Override
            public Optional<com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskEscalation> getEscalation() {
                return Optional.empty();
            }

            @Override
            public Optional<com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskFormSchema> getFormSchema() {
                return Optional.empty();
            }
        };
    }
}
