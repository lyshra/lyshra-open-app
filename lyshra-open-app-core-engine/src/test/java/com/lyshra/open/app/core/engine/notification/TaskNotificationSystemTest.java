package com.lyshra.open.app.core.engine.notification;

import com.lyshra.open.app.core.engine.humantask.impl.InMemoryHumanTaskService;
import com.lyshra.open.app.core.engine.notification.TaskNotificationEvent.NotificationChannel;
import com.lyshra.open.app.core.engine.notification.TaskNotificationEvent.NotificationEventType;
import com.lyshra.open.app.core.engine.notification.TaskNotificationEvent.NotificationPriority;
import com.lyshra.open.app.core.engine.notification.impl.CompositeNotificationService;
import com.lyshra.open.app.core.engine.notification.impl.ConsoleNotificationService;
import com.lyshra.open.app.core.engine.notification.impl.WebhookNotificationService;
import com.lyshra.open.app.core.models.LyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskProcessor.ILyshraOpenAppHumanTaskConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskStatus;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskType;
import org.junit.jupiter.api.*;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for the task notification system.
 * Tests console, webhook, and composite notification services,
 * as well as integration with the human task service.
 */
@DisplayName("Task Notification System Tests")
class TaskNotificationSystemTest {

    private ConsoleNotificationService consoleService;
    private InMemoryHumanTaskService taskService;

    @BeforeEach
    void setUp() {
        consoleService = new ConsoleNotificationService();
        consoleService.setStoreHistory(true);

        taskService = InMemoryHumanTaskService.create()
                .withTaskIdPrefix("task-")
                .withNotificationService(CompositeNotificationService.create()
                        .addChannel(NotificationChannel.CONSOLE, consoleService)
                        .withDefaultChannels(List.of(NotificationChannel.CONSOLE)));
    }

    @AfterEach
    void tearDown() {
        consoleService.reset();
        taskService.reset();
    }

    // ========================================================================
    // NOTIFICATION EVENT MODEL TESTS
    // ========================================================================

    @Nested
    @DisplayName("Notification Event Model")
    class NotificationEventModelTests {

        @Test
        @DisplayName("Should create task created notification with all fields")
        void shouldCreateTaskCreatedNotification() {
            TaskNotificationEvent event = TaskNotificationEvent.taskCreated(
                    "task-123",
                    "Review Purchase Order",
                    "Please review the order for approval",
                    LyshraOpenAppHumanTaskType.APPROVAL,
                    "workflow-456",
                    List.of("user1@company.com", "user2@company.com"),
                    "system"
            );

            assertThat(event.getEventId()).isNotNull();
            assertThat(event.getTimestamp()).isNotNull();
            assertThat(event.getEventType()).isEqualTo(NotificationEventType.TASK_CREATED);
            assertThat(event.getTaskId()).isEqualTo("task-123");
            assertThat(event.getTaskTitle()).isEqualTo("Review Purchase Order");
            assertThat(event.getTaskDescription()).isEqualTo("Please review the order for approval");
            assertThat(event.getTaskType()).isEqualTo(LyshraOpenAppHumanTaskType.APPROVAL);
            assertThat(event.getWorkflowInstanceId()).isEqualTo("workflow-456");
            assertThat(event.getRecipients()).containsExactly("user1@company.com", "user2@company.com");
            assertThat(event.getActorId()).isEqualTo("system");
            assertThat(event.getPriority()).isEqualTo(NotificationPriority.NORMAL);
            assertThat(event.hasRecipients()).isTrue();
        }

        @Test
        @DisplayName("Should create task rejected notification with high priority")
        void shouldCreateTaskRejectedNotification() {
            TaskNotificationEvent event = TaskNotificationEvent.taskRejected(
                    "task-123",
                    "Budget Request",
                    "manager@company.com",
                    List.of("requester@company.com"),
                    "Budget exceeds limit"
            );

            assertThat(event.getEventType()).isEqualTo(NotificationEventType.TASK_REJECTED);
            assertThat(event.getPriority()).isEqualTo(NotificationPriority.HIGH);
            assertThat(event.getReason()).isEqualTo("Budget exceeds limit");
            assertThat(event.getMessage()).contains("rejected").contains("Budget exceeds limit");
        }

        @Test
        @DisplayName("Should create task escalated notification with urgent priority")
        void shouldCreateTaskEscalatedNotification() {
            TaskNotificationEvent event = TaskNotificationEvent.taskEscalated(
                    "task-123",
                    "Urgent Review",
                    List.of("director@company.com"),
                    "Response overdue"
            );

            assertThat(event.getEventType()).isEqualTo(NotificationEventType.TASK_ESCALATED);
            assertThat(event.getPriority()).isEqualTo(NotificationPriority.URGENT);
            assertThat(event.getReason()).isEqualTo("Response overdue");
        }

        @Test
        @DisplayName("Should generate email subject with priority prefix")
        void shouldGenerateEmailSubject() {
            TaskNotificationEvent normalEvent = TaskNotificationEvent.builder()
                    .eventType(NotificationEventType.TASK_CREATED)
                    .taskTitle("Normal Task")
                    .priority(NotificationPriority.NORMAL)
                    .build();

            TaskNotificationEvent urgentEvent = TaskNotificationEvent.builder()
                    .eventType(NotificationEventType.TASK_ESCALATED)
                    .taskTitle("Urgent Task")
                    .priority(NotificationPriority.URGENT)
                    .build();

            assertThat(normalEvent.getEmailSubject()).isEqualTo("Task Created: Normal Task");
            assertThat(urgentEvent.getEmailSubject()).startsWith("[URGENT]");
        }
    }

    // ========================================================================
    // CONSOLE NOTIFICATION SERVICE TESTS
    // ========================================================================

    @Nested
    @DisplayName("Console Notification Service")
    class ConsoleNotificationServiceTests {

        @Test
        @DisplayName("Should send notification and store in history")
        void shouldSendNotificationAndStoreHistory() {
            TaskNotificationEvent event = TaskNotificationEvent.taskCreated(
                    "task-1", "Test Task", "Description",
                    LyshraOpenAppHumanTaskType.APPROVAL, "workflow-1",
                    List.of("user@test.com"), "system"
            );

            ITaskNotificationService.NotificationResult result = consoleService.notify(event).block();

            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.channel()).isEqualTo(NotificationChannel.CONSOLE);

            List<ITaskNotificationService.NotificationResult> history =
                    consoleService.getNotificationHistory("task-1").collectList().block();
            assertThat(history).hasSize(1);
        }

        @Test
        @DisplayName("Should skip notification when no recipients")
        void shouldSkipWhenNoRecipients() {
            TaskNotificationEvent event = TaskNotificationEvent.builder()
                    .eventType(NotificationEventType.TASK_CREATED)
                    .taskId("task-1")
                    .taskTitle("Test Task")
                    .recipients(List.of()) // Empty recipients
                    .build();

            ITaskNotificationService.NotificationResult result = consoleService.notify(event).block();

            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo(ITaskNotificationService.NotificationStatus.SKIPPED);
        }

        @Test
        @DisplayName("Should track statistics correctly")
        void shouldTrackStatistics() {
            consoleService.resetStatistics();

            // Send multiple notifications
            for (int i = 0; i < 5; i++) {
                TaskNotificationEvent event = TaskNotificationEvent.taskCreated(
                        "task-" + i, "Task " + i, null,
                        LyshraOpenAppHumanTaskType.APPROVAL, "workflow-1",
                        List.of("user@test.com"), "system"
                );
                consoleService.notify(event).block();
            }

            ITaskNotificationService.NotificationStatistics stats =
                    consoleService.getStatistics(Instant.now().minus(Duration.ofHours(1)), Instant.now()).block();

            assertThat(stats).isNotNull();
            assertThat(stats.totalSent()).isEqualTo(5);
            assertThat(stats.byEventType()).containsKey(NotificationEventType.TASK_CREATED);
        }

        @Test
        @DisplayName("Should disable and enable notifications")
        void shouldDisableAndEnableNotifications() {
            consoleService.setEnabled(false);

            TaskNotificationEvent event = TaskNotificationEvent.taskCreated(
                    "task-1", "Test Task", null,
                    LyshraOpenAppHumanTaskType.APPROVAL, "workflow-1",
                    List.of("user@test.com"), "system"
            );

            ITaskNotificationService.NotificationResult result = consoleService.notify(event).block();
            assertThat(result.status()).isEqualTo(ITaskNotificationService.NotificationStatus.SKIPPED);

            consoleService.setEnabled(true);
            result = consoleService.notify(event).block();
            assertThat(result.isSuccess()).isTrue();
        }
    }

    // ========================================================================
    // COMPOSITE NOTIFICATION SERVICE TESTS
    // ========================================================================

    @Nested
    @DisplayName("Composite Notification Service")
    class CompositeNotificationServiceTests {

        @Test
        @DisplayName("Should send to multiple channels")
        void shouldSendToMultipleChannels() {
            ConsoleNotificationService console1 = new ConsoleNotificationService().setStoreHistory(true);
            ConsoleNotificationService console2 = new ConsoleNotificationService().setStoreHistory(true);

            CompositeNotificationService composite = CompositeNotificationService.create()
                    .addChannel(NotificationChannel.CONSOLE, console1)
                    .addChannel(NotificationChannel.IN_APP, console2) // Using console as mock
                    .withDefaultChannels(List.of(NotificationChannel.CONSOLE, NotificationChannel.IN_APP));

            TaskNotificationEvent event = TaskNotificationEvent.taskCreated(
                    "task-1", "Test Task", null,
                    LyshraOpenAppHumanTaskType.APPROVAL, "workflow-1",
                    List.of("user@test.com"), "system"
            );

            ITaskNotificationService.NotificationResult result = composite.notify(event).block();

            assertThat(result.isSuccess()).isTrue();
            assertThat(console1.getAllNotifications()).hasSize(1);
        }

        @Test
        @DisplayName("Should enable and disable channels")
        void shouldEnableAndDisableChannels() {
            ConsoleNotificationService console = new ConsoleNotificationService().setStoreHistory(true);
            CompositeNotificationService composite = CompositeNotificationService.create()
                    .addChannel(NotificationChannel.CONSOLE, console)
                    .withDefaultChannels(List.of(NotificationChannel.CONSOLE));

            // Disable channel
            composite.setChannelEnabled(NotificationChannel.CONSOLE, false);
            assertThat(composite.isChannelEnabled(NotificationChannel.CONSOLE)).isFalse();

            // Enable channel
            composite.setChannelEnabled(NotificationChannel.CONSOLE, true);
            assertThat(composite.isChannelEnabled(NotificationChannel.CONSOLE)).isTrue();
        }

        @Test
        @DisplayName("Should aggregate statistics from all channels")
        void shouldAggregateStatistics() {
            ConsoleNotificationService console1 = new ConsoleNotificationService().setStoreHistory(true);
            ConsoleNotificationService console2 = new ConsoleNotificationService().setStoreHistory(true);

            CompositeNotificationService composite = CompositeNotificationService.create()
                    .addChannel(NotificationChannel.CONSOLE, console1)
                    .addChannel(NotificationChannel.IN_APP, console2)
                    .withDefaultChannels(List.of(NotificationChannel.CONSOLE));

            TaskNotificationEvent event = TaskNotificationEvent.taskCreated(
                    "task-1", "Test Task", null,
                    LyshraOpenAppHumanTaskType.APPROVAL, "workflow-1",
                    List.of("user@test.com"), "system"
            );

            composite.notify(event).block();

            ITaskNotificationService.NotificationStatistics stats =
                    composite.getStatistics(Instant.now().minus(Duration.ofHours(1)), Instant.now()).block();

            assertThat(stats).isNotNull();
            assertThat(stats.totalSent()).isGreaterThanOrEqualTo(1);
        }
    }

    // ========================================================================
    // WEBHOOK NOTIFICATION SERVICE TESTS
    // ========================================================================

    @Nested
    @DisplayName("Webhook Notification Service")
    class WebhookNotificationServiceTests {

        @Test
        @DisplayName("Should skip when no webhooks configured")
        void shouldSkipWhenNoWebhooks() {
            WebhookNotificationService webhookService = new WebhookNotificationService();

            TaskNotificationEvent event = TaskNotificationEvent.taskCreated(
                    "task-1", "Test Task", null,
                    LyshraOpenAppHumanTaskType.APPROVAL, "workflow-1",
                    List.of("user@test.com"), "system"
            );

            ITaskNotificationService.NotificationResult result = webhookService.notify(event).block();

            assertThat(result.status()).isEqualTo(ITaskNotificationService.NotificationStatus.SKIPPED);
        }

        @Test
        @DisplayName("Should configure webhook endpoints")
        void shouldConfigureWebhookEndpoints() {
            WebhookNotificationService webhookService = new WebhookNotificationService()
                    .addWebhook(WebhookNotificationService.WebhookConfig.builder()
                            .url("https://api.example.com/webhooks")
                            .name("example-webhook")
                            .headers(Map.of("Authorization", "Bearer token"))
                            .build())
                    .withRetryAttempts(3)
                    .withTimeout(Duration.ofSeconds(5));

            assertThat(webhookService.getWebhooks()).hasSize(1);
            assertThat(webhookService.getWebhooks().get(0).getUrl()).isEqualTo("https://api.example.com/webhooks");
        }

        @Test
        @DisplayName("Should filter webhooks by event type")
        void shouldFilterWebhooksByEventType() {
            WebhookNotificationService webhookService = new WebhookNotificationService()
                    .addWebhook(WebhookNotificationService.WebhookConfig.builder()
                            .url("https://api.example.com/webhooks")
                            .eventTypes(java.util.Set.of(NotificationEventType.TASK_APPROVED, NotificationEventType.TASK_REJECTED))
                            .build())
                    .withHistoryStorage(true);

            // This webhook should only accept APPROVED and REJECTED events
            List<WebhookNotificationService.WebhookConfig> webhooks = webhookService.getWebhooks();
            assertThat(webhooks.get(0).getEventTypes())
                    .containsExactlyInAnyOrder(NotificationEventType.TASK_APPROVED, NotificationEventType.TASK_REJECTED);
        }
    }

    // ========================================================================
    // INTEGRATION WITH HUMAN TASK SERVICE TESTS
    // ========================================================================

    @Nested
    @DisplayName("Integration with Human Task Service")
    class IntegrationTests {

        @Test
        @DisplayName("Should send notification when task is created")
        void shouldNotifyOnTaskCreation() throws InterruptedException {
            var task = taskService.createTask(
                    "workflow-1",
                    "step-1",
                    LyshraOpenAppHumanTaskType.APPROVAL,
                    createTaskConfig("Review Order"),
                    createContext()
            ).block();

            // Allow async notification to complete
            Thread.sleep(100);

            List<ITaskNotificationService.NotificationResult> history =
                    consoleService.getNotificationHistory(task.getTaskId()).collectList().block();

            assertThat(history).hasSizeGreaterThanOrEqualTo(1);
            assertThat(history.get(0).isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should send notification when task is delegated")
        void shouldNotifyOnTaskDelegation() throws InterruptedException {
            var task = taskService.createTask(
                    "workflow-1", "step-1",
                    LyshraOpenAppHumanTaskType.APPROVAL,
                    createTaskConfig("Review Order"),
                    createContext()
            ).block();

            taskService.claimTask(task.getTaskId(), "user-from").block();
            taskService.delegateTask(task.getTaskId(), "user-from", "user-to").block();

            Thread.sleep(100);

            // Check notification was sent
            List<ITaskNotificationService.NotificationResult> allNotifications = consoleService.getAllNotifications();
            assertThat(allNotifications.size()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("Should send notification when task is approved")
        void shouldNotifyOnTaskApproval() throws InterruptedException {
            var task = taskService.createTask(
                    "workflow-1", "step-1",
                    LyshraOpenAppHumanTaskType.APPROVAL,
                    createTaskConfig("Review Order"),
                    createContext()
            ).block();

            taskService.claimTask(task.getTaskId(), "approver").block();
            taskService.approveTask(task.getTaskId(), "approver", "Looks good").block();

            Thread.sleep(100);

            List<ITaskNotificationService.NotificationResult> allNotifications = consoleService.getAllNotifications();
            assertThat(allNotifications.size()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("Should send notification when task is rejected")
        void shouldNotifyOnTaskRejection() throws InterruptedException {
            var task = taskService.createTask(
                    "workflow-1", "step-1",
                    LyshraOpenAppHumanTaskType.APPROVAL,
                    createTaskConfig("Review Order"),
                    createContext()
            ).block();

            taskService.claimTask(task.getTaskId(), "reviewer").block();
            taskService.rejectTask(task.getTaskId(), "reviewer", "Not acceptable").block();

            Thread.sleep(100);

            List<ITaskNotificationService.NotificationResult> allNotifications = consoleService.getAllNotifications();
            assertThat(allNotifications.size()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("Should send notification when task is escalated")
        void shouldNotifyOnTaskEscalation() throws InterruptedException {
            var task = taskService.createTask(
                    "workflow-1", "step-1",
                    LyshraOpenAppHumanTaskType.APPROVAL,
                    createTaskConfig("Review Order"),
                    createContext()
            ).block();

            taskService.simulateEscalation(task.getTaskId(), List.of("manager@company.com"), "Overdue").block();

            Thread.sleep(100);

            List<ITaskNotificationService.NotificationResult> allNotifications = consoleService.getAllNotifications();
            assertThat(allNotifications.size()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("Should not send notification when disabled")
        void shouldNotNotifyWhenDisabled() throws InterruptedException {
            taskService.withNotificationsEnabled(false);

            var task = taskService.createTask(
                    "workflow-1", "step-1",
                    LyshraOpenAppHumanTaskType.APPROVAL,
                    createTaskConfig("Review Order"),
                    createContext()
            ).block();

            Thread.sleep(100);

            // No notifications should be sent when disabled
            List<ITaskNotificationService.NotificationResult> history =
                    consoleService.getNotificationHistory(task.getTaskId()).collectList().block();

            assertThat(history).isEmpty();
        }

        @Test
        @DisplayName("Should handle complete task lifecycle with notifications")
        void shouldHandleCompleteLifecycle() throws InterruptedException {
            consoleService.reset();

            // Create task
            var task = taskService.createTask(
                    "workflow-lifecycle", "step-1",
                    LyshraOpenAppHumanTaskType.APPROVAL,
                    createTaskConfig("Lifecycle Test"),
                    createContext()
            ).block();

            // Claim task
            taskService.claimTask(task.getTaskId(), "user-1").block();

            // Approve task
            taskService.approveTask(task.getTaskId(), "user-1", "Approved").block();

            Thread.sleep(200);

            // Verify notifications were sent
            List<ITaskNotificationService.NotificationResult> allNotifications = consoleService.getAllNotifications();
            assertThat(allNotifications.size()).isGreaterThanOrEqualTo(1);
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private ILyshraOpenAppHumanTaskConfig createTaskConfig(String title) {
        return new ILyshraOpenAppHumanTaskConfig() {
            @Override public String getTitle() { return title; }
            @Override public String getDescription() { return "Test description for " + title; }
            @Override public int getPriority() { return 5; }
            @Override public List<String> getAssignees() { return List.of("test-user@company.com"); }
            @Override public List<String> getCandidateGroups() { return List.of("test-group"); }
            @Override public Optional<Duration> getTimeout() { return Optional.empty(); }
            @Override public Optional<com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskFormSchema> getFormSchema() { return Optional.empty(); }
            @Override public Optional<com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskEscalation> getEscalation() { return Optional.empty(); }
        };
    }

    private ILyshraOpenAppContext createContext() {
        return new LyshraOpenAppContext();
    }
}
