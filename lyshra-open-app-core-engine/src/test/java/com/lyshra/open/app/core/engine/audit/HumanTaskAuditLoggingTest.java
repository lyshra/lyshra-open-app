package com.lyshra.open.app.core.engine.audit;

import com.lyshra.open.app.core.engine.audit.impl.InMemoryHumanTaskAuditService;
import com.lyshra.open.app.core.engine.humantask.impl.InMemoryHumanTaskService;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTask;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskAuditEntry;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskAuditEntry.AuditAction;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskProcessor.ILyshraOpenAppHumanTaskConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskStatus;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for human task audit logging.
 * Verifies all lifecycle events are properly captured for compliance and debugging.
 */
class HumanTaskAuditLoggingTest {

    private InMemoryHumanTaskAuditService auditService;
    private InMemoryHumanTaskService taskService;

    @BeforeEach
    void setUp() {
        auditService = new InMemoryHumanTaskAuditService();
        auditService.reset();

        taskService = InMemoryHumanTaskService.create()
                .withAuditService(auditService);
    }

    // ========================================================================
    // TASK CREATION AUDIT TESTS
    // ========================================================================

    @Nested
    @DisplayName("Task Creation Audit")
    class TaskCreationAuditTests {

        @Test
        @DisplayName("should log audit entry on task creation")
        void shouldLogAuditEntryOnTaskCreation() {
            // Given
            String workflowId = "workflow-123";

            // When
            ILyshraOpenAppHumanTask task = taskService.createTask(
                    workflowId, "step-1",
                    LyshraOpenAppHumanTaskType.APPROVAL,
                    createTaskConfig("Approve Order"),
                    null
            ).block();

            // Then
            List<ILyshraOpenAppHumanTaskAuditEntry> auditEntries =
                    auditService.getAuditTrailForTask(task.getTaskId()).collectList().block();

            assertNotNull(auditEntries);
            assertEquals(1, auditEntries.size());

            ILyshraOpenAppHumanTaskAuditEntry entry = auditEntries.get(0);
            assertEquals(AuditAction.CREATED, entry.getAction());
            assertEquals("system", entry.getActorId());
            assertEquals(ILyshraOpenAppHumanTaskAuditEntry.ActorType.WORKFLOW_ENGINE, entry.getActorType());
            assertTrue(entry.getNewStatus().isPresent());
            assertEquals(LyshraOpenAppHumanTaskStatus.PENDING, entry.getNewStatus().get());
        }

        @Test
        @DisplayName("should include task details in creation audit")
        void shouldIncludeTaskDetailsInCreationAudit() {
            // Given
            String workflowId = "workflow-456";

            // When
            ILyshraOpenAppHumanTask task = taskService.createTask(
                    workflowId, "step-2",
                    LyshraOpenAppHumanTaskType.MANUAL_INPUT,
                    createTaskConfig("Submit Form"),
                    null
            ).block();

            // Then
            List<ILyshraOpenAppHumanTaskAuditEntry> entries =
                    auditService.getAuditTrailForWorkflow(workflowId).collectList().block();

            assertEquals(1, entries.size());
            ILyshraOpenAppHumanTaskAuditEntry entry = entries.get(0);

            assertTrue(entry.getDescription().contains("Submit Form"));
        }
    }

    // ========================================================================
    // TASK ASSIGNMENT AUDIT TESTS
    // ========================================================================

    @Nested
    @DisplayName("Task Assignment Audit")
    class TaskAssignmentAuditTests {

        @Test
        @DisplayName("should log audit entry on task claim")
        void shouldLogAuditEntryOnTaskClaim() {
            // Given
            ILyshraOpenAppHumanTask task = createTask("workflow-1");
            String userId = "user-1";

            // When
            taskService.claimTask(task.getTaskId(), userId).block();

            // Then
            List<ILyshraOpenAppHumanTaskAuditEntry> entries =
                    auditService.getAuditEntriesByAction(AuditAction.CLAIMED).collectList().block();

            assertEquals(1, entries.size());
            ILyshraOpenAppHumanTaskAuditEntry entry = entries.get(0);
            assertEquals(userId, entry.getActorId());
            assertEquals(ILyshraOpenAppHumanTaskAuditEntry.ActorType.USER, entry.getActorType());
            assertTrue(entry.getPreviousStatus().isPresent());
            assertEquals(LyshraOpenAppHumanTaskStatus.PENDING, entry.getPreviousStatus().get());
            assertTrue(entry.getNewStatus().isPresent());
            assertEquals(LyshraOpenAppHumanTaskStatus.IN_PROGRESS, entry.getNewStatus().get());
        }

        @Test
        @DisplayName("should log audit entry on task unclaim")
        void shouldLogAuditEntryOnTaskUnclaim() {
            // Given
            ILyshraOpenAppHumanTask task = createTask("workflow-2");
            String userId = "user-2";
            taskService.claimTask(task.getTaskId(), userId).block();

            // When
            taskService.unclaimTask(task.getTaskId(), userId).block();

            // Then
            List<ILyshraOpenAppHumanTaskAuditEntry> entries =
                    auditService.getAuditEntriesByAction(AuditAction.UNCLAIMED).collectList().block();

            assertEquals(1, entries.size());
            ILyshraOpenAppHumanTaskAuditEntry entry = entries.get(0);
            assertEquals(userId, entry.getActorId());
            assertTrue(entry.getNewStatus().isPresent());
            assertEquals(LyshraOpenAppHumanTaskStatus.PENDING, entry.getNewStatus().get());
        }

        @Test
        @DisplayName("should log audit entry on task delegation")
        void shouldLogAuditEntryOnTaskDelegation() {
            // Given
            ILyshraOpenAppHumanTask task = createTask("workflow-3");
            String fromUser = "user-from";
            String toUser = "user-to";
            taskService.claimTask(task.getTaskId(), fromUser).block();

            // When
            taskService.delegateTask(task.getTaskId(), fromUser, toUser).block();

            // Then
            List<ILyshraOpenAppHumanTaskAuditEntry> entries =
                    auditService.getAuditEntriesByAction(AuditAction.DELEGATED).collectList().block();

            assertEquals(1, entries.size());
            ILyshraOpenAppHumanTaskAuditEntry entry = entries.get(0);
            assertEquals(fromUser, entry.getActorId());
            assertTrue(entry.getDescription().contains(toUser));
        }

        @Test
        @DisplayName("should log audit entry on task reassignment")
        void shouldLogAuditEntryOnTaskReassignment() {
            // Given
            ILyshraOpenAppHumanTask task = createTask("workflow-4");
            List<String> newAssignees = List.of("user-new-1", "user-new-2");

            // When
            taskService.reassignTask(task.getTaskId(), newAssignees).block();

            // Then
            List<ILyshraOpenAppHumanTaskAuditEntry> entries =
                    auditService.getAuditEntriesByAction(AuditAction.REASSIGNED).collectList().block();

            assertEquals(1, entries.size());
            ILyshraOpenAppHumanTaskAuditEntry entry = entries.get(0);
            assertTrue(entry.getDescription().contains("user-new-1"));
        }
    }

    // ========================================================================
    // TASK COMPLETION AUDIT TESTS
    // ========================================================================

    @Nested
    @DisplayName("Task Completion Audit")
    class TaskCompletionAuditTests {

        @Test
        @DisplayName("should log audit entry on task approval")
        void shouldLogAuditEntryOnTaskApproval() {
            // Given
            ILyshraOpenAppHumanTask task = createTask("workflow-5");
            String userId = "approver-1";
            taskService.claimTask(task.getTaskId(), userId).block();

            // When
            taskService.approveTask(task.getTaskId(), userId, "Looks good").block();

            // Then
            List<ILyshraOpenAppHumanTaskAuditEntry> entries =
                    auditService.getAuditEntriesByAction(AuditAction.APPROVED).collectList().block();

            assertEquals(1, entries.size());
            ILyshraOpenAppHumanTaskAuditEntry entry = entries.get(0);
            assertEquals(userId, entry.getActorId());
            assertTrue(entry.getNewStatus().isPresent());
            assertEquals(LyshraOpenAppHumanTaskStatus.APPROVED, entry.getNewStatus().get());
        }

        @Test
        @DisplayName("should log audit entry on task rejection")
        void shouldLogAuditEntryOnTaskRejection() {
            // Given
            ILyshraOpenAppHumanTask task = createTask("workflow-6");
            String userId = "reviewer-1";
            String reason = "Does not meet criteria";
            taskService.claimTask(task.getTaskId(), userId).block();

            // When
            taskService.rejectTask(task.getTaskId(), userId, reason).block();

            // Then
            List<ILyshraOpenAppHumanTaskAuditEntry> entries =
                    auditService.getAuditEntriesByAction(AuditAction.REJECTED).collectList().block();

            assertEquals(1, entries.size());
            ILyshraOpenAppHumanTaskAuditEntry entry = entries.get(0);
            assertEquals(userId, entry.getActorId());
            assertTrue(entry.getNewStatus().isPresent());
            assertEquals(LyshraOpenAppHumanTaskStatus.REJECTED, entry.getNewStatus().get());
        }

        @Test
        @DisplayName("should log audit entry on task completion with data")
        void shouldLogAuditEntryOnTaskCompletionWithData() {
            // Given
            ILyshraOpenAppHumanTask task = createTask("workflow-7");
            String userId = "completer-1";
            Map<String, Object> resultData = Map.of("field1", "value1", "field2", 42);
            taskService.claimTask(task.getTaskId(), userId).block();

            // When
            taskService.completeTask(task.getTaskId(), userId, resultData).block();

            // Then
            List<ILyshraOpenAppHumanTaskAuditEntry> entries =
                    auditService.getAuditEntriesByAction(AuditAction.COMPLETED).collectList().block();

            assertEquals(1, entries.size());
            ILyshraOpenAppHumanTaskAuditEntry entry = entries.get(0);
            assertEquals(userId, entry.getActorId());
            assertTrue(entry.getNewStatus().isPresent());
            assertEquals(LyshraOpenAppHumanTaskStatus.COMPLETED, entry.getNewStatus().get());
        }

        @Test
        @DisplayName("should log audit entry on task cancellation")
        void shouldLogAuditEntryOnTaskCancellation() {
            // Given
            ILyshraOpenAppHumanTask task = createTask("workflow-8");
            String userId = "admin-1";
            String reason = "No longer needed";

            // When
            taskService.cancelTask(task.getTaskId(), userId, reason).block();

            // Then
            List<ILyshraOpenAppHumanTaskAuditEntry> entries =
                    auditService.getAuditEntriesByAction(AuditAction.CANCELLED).collectList().block();

            assertEquals(1, entries.size());
            ILyshraOpenAppHumanTaskAuditEntry entry = entries.get(0);
            assertEquals(userId, entry.getActorId());
            assertTrue(entry.getNewStatus().isPresent());
            assertEquals(LyshraOpenAppHumanTaskStatus.CANCELLED, entry.getNewStatus().get());
        }
    }

    // ========================================================================
    // ESCALATION AUDIT TESTS
    // ========================================================================

    @Nested
    @DisplayName("Escalation Audit")
    class EscalationAuditTests {

        @Test
        @DisplayName("should log audit entry on task timeout")
        void shouldLogAuditEntryOnTaskTimeout() {
            // Given
            ILyshraOpenAppHumanTask task = createTask("workflow-9");

            // When
            taskService.simulateTimeout(task.getTaskId(), "Timeout after 24 hours").block();

            // Then
            List<ILyshraOpenAppHumanTaskAuditEntry> entries =
                    auditService.getAuditEntriesByAction(AuditAction.TIMEOUT).collectList().block();

            assertEquals(1, entries.size());
            ILyshraOpenAppHumanTaskAuditEntry entry = entries.get(0);
            assertEquals("system", entry.getActorId());
            assertEquals(ILyshraOpenAppHumanTaskAuditEntry.ActorType.TIMER, entry.getActorType());
            assertTrue(entry.getNewStatus().isPresent());
            assertEquals(LyshraOpenAppHumanTaskStatus.TIMED_OUT, entry.getNewStatus().get());
        }

        @Test
        @DisplayName("should log audit entry on task escalation")
        void shouldLogAuditEntryOnTaskEscalation() {
            // Given
            ILyshraOpenAppHumanTask task = createTask("workflow-10");
            List<String> escalateTo = List.of("manager-1", "manager-2");

            // When
            taskService.simulateEscalation(task.getTaskId(), escalateTo, "Escalated to management").block();

            // Then
            List<ILyshraOpenAppHumanTaskAuditEntry> entries =
                    auditService.getAuditEntriesByAction(AuditAction.ESCALATED).collectList().block();

            assertEquals(1, entries.size());
            ILyshraOpenAppHumanTaskAuditEntry entry = entries.get(0);
            assertEquals("system", entry.getActorId());
            assertEquals(ILyshraOpenAppHumanTaskAuditEntry.ActorType.ESCALATION_HANDLER, entry.getActorType());
            assertTrue(entry.getNewStatus().isPresent());
            assertEquals(LyshraOpenAppHumanTaskStatus.ESCALATED, entry.getNewStatus().get());
        }
    }

    // ========================================================================
    // AUDIT QUERY AND EXPORT TESTS
    // ========================================================================

    @Nested
    @DisplayName("Audit Query and Export")
    class AuditQueryExportTests {

        @Test
        @DisplayName("should query audit entries by actor")
        void shouldQueryAuditEntriesByActor() {
            // Given
            String actor1 = "actor-1";
            String actor2 = "actor-2";

            ILyshraOpenAppHumanTask task1 = createTask("workflow-11");
            ILyshraOpenAppHumanTask task2 = createTask("workflow-12");

            taskService.claimTask(task1.getTaskId(), actor1).block();
            taskService.claimTask(task2.getTaskId(), actor2).block();
            taskService.approveTask(task1.getTaskId(), actor1).block();
            taskService.rejectTask(task2.getTaskId(), actor2).block();

            // When
            List<ILyshraOpenAppHumanTaskAuditEntry> actor1Entries =
                    auditService.getAuditEntriesByActor(actor1).collectList().block();

            // Then
            assertEquals(2, actor1Entries.size()); // claimed + approved
            assertTrue(actor1Entries.stream().allMatch(e -> actor1.equals(e.getActorId())));
        }

        @Test
        @DisplayName("should query audit entries within time range")
        void shouldQueryAuditEntriesWithinTimeRange() {
            // Given
            Instant now = Instant.now();
            ILyshraOpenAppHumanTask task = createTask("workflow-13");
            taskService.claimTask(task.getTaskId(), "user-1").block();

            // When
            List<ILyshraOpenAppHumanTaskAuditEntry> entries =
                    auditService.getAuditEntriesBetween(
                            now.minus(1, ChronoUnit.HOURS),
                            now.plus(1, ChronoUnit.HOURS)
                    ).collectList().block();

            // Then
            assertFalse(entries.isEmpty());
            assertTrue(entries.size() >= 2); // created + claimed
        }

        @Test
        @DisplayName("should execute complex audit query")
        void shouldExecuteComplexAuditQuery() {
            // Given
            ILyshraOpenAppHumanTask task1 = createTask("workflow-14");
            ILyshraOpenAppHumanTask task2 = createTask("workflow-15");

            taskService.claimTask(task1.getTaskId(), "user-1").block();
            taskService.approveTask(task1.getTaskId(), "user-1").block();
            taskService.claimTask(task2.getTaskId(), "user-2").block();
            taskService.rejectTask(task2.getTaskId(), "user-2").block();

            // When
            ILyshraOpenAppHumanTaskAuditService.AuditQuery query = ILyshraOpenAppHumanTaskAuditService.AuditQuery.builder()
                    .actions(List.of(AuditAction.APPROVED, AuditAction.REJECTED))
                    .sortOrder(ILyshraOpenAppHumanTaskAuditService.SortOrder.DESCENDING)
                    .build();

            List<ILyshraOpenAppHumanTaskAuditEntry> entries =
                    auditService.queryAuditEntries(query).collectList().block();

            // Then
            assertEquals(2, entries.size());
            assertTrue(entries.stream().allMatch(e ->
                    e.getAction() == AuditAction.APPROVED || e.getAction() == AuditAction.REJECTED));
        }

        @Test
        @DisplayName("should get audit statistics")
        void shouldGetAuditStatistics() {
            // Given
            ILyshraOpenAppHumanTask task1 = createTask("workflow-16");
            ILyshraOpenAppHumanTask task2 = createTask("workflow-17");

            taskService.claimTask(task1.getTaskId(), "user-1").block();
            taskService.approveTask(task1.getTaskId(), "user-1").block();
            taskService.claimTask(task2.getTaskId(), "user-2").block();
            taskService.rejectTask(task2.getTaskId(), "user-2").block();

            // When
            ILyshraOpenAppHumanTaskAuditService.AuditStatistics stats =
                    auditService.getAuditStatistics(
                            Instant.now().minus(1, ChronoUnit.HOURS),
                            Instant.now().plus(1, ChronoUnit.HOURS)
                    ).block();

            // Then
            assertNotNull(stats);
            assertTrue(stats.totalEntries() >= 6); // 2 created + 2 claimed + 1 approved + 1 rejected
            assertTrue(stats.uniqueTasks() >= 2);
            assertTrue(stats.uniqueActors() >= 2);
        }

        @Test
        @DisplayName("should export audit entries to JSON")
        void shouldExportAuditEntriesToJson() {
            // Given
            ILyshraOpenAppHumanTask task = createTask("workflow-18");
            taskService.claimTask(task.getTaskId(), "user-1").block();

            ILyshraOpenAppHumanTaskAuditService.AuditQuery query = ILyshraOpenAppHumanTaskAuditService.AuditQuery.builder()
                    .taskId(task.getTaskId())
                    .build();

            // When
            ILyshraOpenAppHumanTaskAuditService.AuditExportResult result =
                    auditService.exportAuditEntries(query, ILyshraOpenAppHumanTaskAuditService.AuditExportFormat.JSON).block();

            // Then
            assertNotNull(result);
            assertEquals("JSON", result.format());
            assertTrue(result.entryCount() >= 2);
            assertTrue(result.data().contains("CREATED"));
            assertTrue(result.data().contains("CLAIMED"));
        }

        @Test
        @DisplayName("should export audit entries to CSV")
        void shouldExportAuditEntriesToCsv() {
            // Given
            ILyshraOpenAppHumanTask task = createTask("workflow-19");
            taskService.claimTask(task.getTaskId(), "user-1").block();

            ILyshraOpenAppHumanTaskAuditService.AuditQuery query = ILyshraOpenAppHumanTaskAuditService.AuditQuery.builder()
                    .taskId(task.getTaskId())
                    .build();

            // When
            ILyshraOpenAppHumanTaskAuditService.AuditExportResult result =
                    auditService.exportAuditEntries(query, ILyshraOpenAppHumanTaskAuditService.AuditExportFormat.CSV).block();

            // Then
            assertNotNull(result);
            assertEquals("CSV", result.format());
            assertTrue(result.data().contains("EntryId,Timestamp,TaskId,Action"));
            assertTrue(result.data().contains("CREATED"));
        }
    }

    // ========================================================================
    // COMMENT AUDIT TESTS
    // ========================================================================

    @Nested
    @DisplayName("Comment Audit")
    class CommentAuditTests {

        @Test
        @DisplayName("should log audit entry when comment is added")
        void shouldLogAuditEntryWhenCommentIsAdded() {
            // Given
            ILyshraOpenAppHumanTask task = createTask("workflow-20");
            String userId = "commenter-1";

            // When
            taskService.addComment(task.getTaskId(), userId, "This needs review", false).block();

            // Then
            List<ILyshraOpenAppHumanTaskAuditEntry> entries =
                    auditService.getAuditEntriesByAction(AuditAction.COMMENTED).collectList().block();

            assertEquals(1, entries.size());
            ILyshraOpenAppHumanTaskAuditEntry entry = entries.get(0);
            assertEquals(userId, entry.getActorId());
        }
    }

    // ========================================================================
    // COMPLETE LIFECYCLE AUDIT TESTS
    // ========================================================================

    @Nested
    @DisplayName("Complete Lifecycle Audit")
    class CompleteLifecycleAuditTests {

        @Test
        @DisplayName("should capture complete task lifecycle in audit trail")
        void shouldCaptureCompleteTaskLifecycle() {
            // Given
            String workflowId = "workflow-lifecycle";

            // When - Execute complete lifecycle
            ILyshraOpenAppHumanTask task = taskService.createTask(
                    workflowId, "step-lifecycle",
                    LyshraOpenAppHumanTaskType.APPROVAL,
                    createTaskConfig("Complete Lifecycle Test"),
                    null
            ).block();

            taskService.claimTask(task.getTaskId(), "reviewer").block();
            taskService.addComment(task.getTaskId(), "reviewer", "Reviewing now", false).block();
            taskService.approveTask(task.getTaskId(), "reviewer", "Approved after review").block();

            // Then
            List<ILyshraOpenAppHumanTaskAuditEntry> auditTrail =
                    auditService.getAuditTrailForTask(task.getTaskId()).collectList().block();

            assertNotNull(auditTrail);
            assertEquals(4, auditTrail.size());

            // Verify order and actions
            assertEquals(AuditAction.CREATED, auditTrail.get(0).getAction());
            assertEquals(AuditAction.CLAIMED, auditTrail.get(1).getAction());
            assertEquals(AuditAction.COMMENTED, auditTrail.get(2).getAction());
            assertEquals(AuditAction.APPROVED, auditTrail.get(3).getAction());

            // Verify timestamps are in order
            for (int i = 1; i < auditTrail.size(); i++) {
                assertTrue(auditTrail.get(i).getTimestamp().compareTo(auditTrail.get(i-1).getTimestamp()) >= 0);
            }
        }

        @Test
        @DisplayName("should track audit across multiple workflows")
        void shouldTrackAuditAcrossMultipleWorkflows() {
            // Given
            String workflowId1 = "workflow-multi-1";
            String workflowId2 = "workflow-multi-2";

            // When
            ILyshraOpenAppHumanTask task1 = taskService.createTask(
                    workflowId1, "step-1",
                    LyshraOpenAppHumanTaskType.APPROVAL,
                    createTaskConfig("Task 1"),
                    null
            ).block();

            ILyshraOpenAppHumanTask task2 = taskService.createTask(
                    workflowId2, "step-1",
                    LyshraOpenAppHumanTaskType.APPROVAL,
                    createTaskConfig("Task 2"),
                    null
            ).block();

            taskService.claimTask(task1.getTaskId(), "user-1").block();
            taskService.claimTask(task2.getTaskId(), "user-2").block();

            // Then
            List<ILyshraOpenAppHumanTaskAuditEntry> workflow1Trail =
                    auditService.getAuditTrailForWorkflow(workflowId1).collectList().block();
            List<ILyshraOpenAppHumanTaskAuditEntry> workflow2Trail =
                    auditService.getAuditTrailForWorkflow(workflowId2).collectList().block();

            assertEquals(2, workflow1Trail.size()); // created + claimed
            assertEquals(2, workflow2Trail.size()); // created + claimed
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private ILyshraOpenAppHumanTask createTask(String workflowId) {
        return taskService.createTask(
                workflowId, "step-test",
                LyshraOpenAppHumanTaskType.APPROVAL,
                createTaskConfig("Test Task"),
                null
        ).block();
    }

    private ILyshraOpenAppHumanTaskConfig createTaskConfig(String title) {
        return new ILyshraOpenAppHumanTaskConfig() {
            @Override public String getTitle() { return title; }
            @Override public String getDescription() { return "Test description for " + title; }
            @Override public int getPriority() { return 5; }
            @Override public List<String> getAssignees() { return List.of("test-user"); }
            @Override public List<String> getCandidateGroups() { return List.of("test-group"); }
            @Override public Optional<Duration> getTimeout() { return Optional.empty(); }
            @Override public Optional<com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskFormSchema> getFormSchema() { return Optional.empty(); }
            @Override public Optional<com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskEscalation> getEscalation() { return Optional.empty(); }
        };
    }
}
