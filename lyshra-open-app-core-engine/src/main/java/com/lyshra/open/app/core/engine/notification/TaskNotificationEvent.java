package com.lyshra.open.app.core.engine.notification;

import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskStatus;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskType;
import lombok.Builder;
import lombok.Data;
import lombok.With;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Represents a notification event for human task activities.
 * Contains all information needed to notify users about task actions.
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * TaskNotificationEvent event = TaskNotificationEvent.builder()
 *     .eventType(NotificationEventType.TASK_CREATED)
 *     .taskId("task-123")
 *     .taskTitle("Review Purchase Order")
 *     .recipients(List.of("user@company.com"))
 *     .priority(NotificationPriority.NORMAL)
 *     .build();
 *
 * notificationService.notify(event).subscribe();
 * }</pre>
 */
@Data
@Builder(toBuilder = true)
@With
public class TaskNotificationEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier for this notification event.
     */
    @Builder.Default
    private final String eventId = UUID.randomUUID().toString();

    /**
     * When this event was created.
     */
    @Builder.Default
    private final Instant timestamp = Instant.now();

    /**
     * Type of notification event.
     */
    private final NotificationEventType eventType;

    /**
     * Priority of the notification.
     */
    @Builder.Default
    private final NotificationPriority priority = NotificationPriority.NORMAL;

    /**
     * The task ID this notification is about.
     */
    private final String taskId;

    /**
     * The task title for display purposes.
     */
    private final String taskTitle;

    /**
     * The task description.
     */
    private final String taskDescription;

    /**
     * Type of human task.
     */
    private final LyshraOpenAppHumanTaskType taskType;

    /**
     * Current status of the task.
     */
    private final LyshraOpenAppHumanTaskStatus taskStatus;

    /**
     * Previous status (for status change events).
     */
    private final LyshraOpenAppHumanTaskStatus previousStatus;

    /**
     * The workflow instance ID.
     */
    private final String workflowInstanceId;

    /**
     * The workflow step ID.
     */
    private final String stepId;

    /**
     * List of recipient identifiers (user IDs, email addresses, etc.).
     */
    private final List<String> recipients;

    /**
     * Actor who triggered the event.
     */
    private final String actorId;

    /**
     * Human-readable message for the notification.
     */
    private final String message;

    /**
     * Due date for the task if applicable.
     */
    private final Instant dueDate;

    /**
     * URL or deep link to the task.
     */
    private final String taskUrl;

    /**
     * Additional data to include in the notification.
     */
    @Builder.Default
    private final Map<String, Object> metadata = Map.of();

    /**
     * Correlation ID for distributed tracing.
     */
    private final String correlationId;

    /**
     * Tenant ID for multi-tenant environments.
     */
    private final String tenantId;

    /**
     * Reason for the action (e.g., rejection reason).
     */
    private final String reason;

    /**
     * Channels to send this notification through.
     * If null, uses default channels.
     */
    private final List<NotificationChannel> channels;

    // ========================================================================
    // ENUMS
    // ========================================================================

    /**
     * Types of notification events.
     */
    public enum NotificationEventType {
        /** New task created and assigned */
        TASK_CREATED("Task Created", "A new task has been created"),

        /** Task assigned to user(s) */
        TASK_ASSIGNED("Task Assigned", "A task has been assigned to you"),

        /** Task claimed by a user */
        TASK_CLAIMED("Task Claimed", "A task has been claimed"),

        /** Task released/unclaimed */
        TASK_UNCLAIMED("Task Unclaimed", "A task has been released"),

        /** Task delegated to another user */
        TASK_DELEGATED("Task Delegated", "A task has been delegated to you"),

        /** Task reassigned to different users */
        TASK_REASSIGNED("Task Reassigned", "A task has been reassigned"),

        /** Task approved */
        TASK_APPROVED("Task Approved", "A task has been approved"),

        /** Task rejected */
        TASK_REJECTED("Task Rejected", "A task has been rejected"),

        /** Task completed */
        TASK_COMPLETED("Task Completed", "A task has been completed"),

        /** Task cancelled */
        TASK_CANCELLED("Task Cancelled", "A task has been cancelled"),

        /** Task escalated due to timeout or other reasons */
        TASK_ESCALATED("Task Escalated", "A task has been escalated"),

        /** Task timed out */
        TASK_TIMEOUT("Task Timeout", "A task has timed out"),

        /** Reminder for pending task */
        TASK_REMINDER("Task Reminder", "Reminder: You have a pending task"),

        /** Task approaching due date */
        TASK_DUE_SOON("Task Due Soon", "A task is approaching its due date"),

        /** Comment added to task */
        TASK_COMMENT_ADDED("Comment Added", "A comment was added to a task"),

        /** Task data updated */
        TASK_UPDATED("Task Updated", "A task has been updated"),

        /** Task priority changed */
        TASK_PRIORITY_CHANGED("Priority Changed", "Task priority has been changed");

        private final String title;
        private final String defaultMessage;

        NotificationEventType(String title, String defaultMessage) {
            this.title = title;
            this.defaultMessage = defaultMessage;
        }

        public String getTitle() {
            return title;
        }

        public String getDefaultMessage() {
            return defaultMessage;
        }
    }

    /**
     * Priority levels for notifications.
     */
    public enum NotificationPriority {
        /** Low priority - informational only */
        LOW(1),

        /** Normal priority - standard notifications */
        NORMAL(2),

        /** High priority - important notifications */
        HIGH(3),

        /** Urgent - requires immediate attention */
        URGENT(4);

        private final int level;

        NotificationPriority(int level) {
            this.level = level;
        }

        public int getLevel() {
            return level;
        }
    }

    /**
     * Available notification channels.
     */
    public enum NotificationChannel {
        /** Console/log output (for development) */
        CONSOLE,

        /** Webhook HTTP POST */
        WEBHOOK,

        /** Email notification */
        EMAIL,

        /** SMS notification */
        SMS,

        /** Message queue (Kafka, RabbitMQ, etc.) */
        MESSAGE_QUEUE,

        /** Push notification (mobile/web) */
        PUSH,

        /** In-app notification */
        IN_APP,

        /** Slack integration */
        SLACK,

        /** Microsoft Teams integration */
        TEAMS
    }

    // ========================================================================
    // FACTORY METHODS
    // ========================================================================

    /**
     * Creates a task created notification event.
     */
    public static TaskNotificationEvent taskCreated(
            String taskId, String taskTitle, String taskDescription,
            LyshraOpenAppHumanTaskType taskType, String workflowInstanceId,
            List<String> assignees, String actorId) {
        return TaskNotificationEvent.builder()
                .eventType(NotificationEventType.TASK_CREATED)
                .taskId(taskId)
                .taskTitle(taskTitle)
                .taskDescription(taskDescription)
                .taskType(taskType)
                .taskStatus(LyshraOpenAppHumanTaskStatus.PENDING)
                .workflowInstanceId(workflowInstanceId)
                .recipients(assignees)
                .actorId(actorId)
                .message("New task '" + taskTitle + "' has been created and assigned to you")
                .build();
    }

    /**
     * Creates a task assigned notification event.
     */
    public static TaskNotificationEvent taskAssigned(
            String taskId, String taskTitle, List<String> assignees, String actorId) {
        return TaskNotificationEvent.builder()
                .eventType(NotificationEventType.TASK_ASSIGNED)
                .taskId(taskId)
                .taskTitle(taskTitle)
                .recipients(assignees)
                .actorId(actorId)
                .message("Task '" + taskTitle + "' has been assigned to you")
                .build();
    }

    /**
     * Creates a task claimed notification event.
     */
    public static TaskNotificationEvent taskClaimed(
            String taskId, String taskTitle, String claimedBy, List<String> notifyUsers) {
        return TaskNotificationEvent.builder()
                .eventType(NotificationEventType.TASK_CLAIMED)
                .taskId(taskId)
                .taskTitle(taskTitle)
                .recipients(notifyUsers)
                .actorId(claimedBy)
                .message("Task '" + taskTitle + "' has been claimed by " + claimedBy)
                .build();
    }

    /**
     * Creates a task delegated notification event.
     */
    public static TaskNotificationEvent taskDelegated(
            String taskId, String taskTitle, String fromUser, String toUser) {
        return TaskNotificationEvent.builder()
                .eventType(NotificationEventType.TASK_DELEGATED)
                .taskId(taskId)
                .taskTitle(taskTitle)
                .recipients(List.of(toUser))
                .actorId(fromUser)
                .message("Task '" + taskTitle + "' has been delegated to you by " + fromUser)
                .priority(NotificationPriority.HIGH)
                .build();
    }

    /**
     * Creates a task approved notification event.
     */
    public static TaskNotificationEvent taskApproved(
            String taskId, String taskTitle, String approvedBy,
            List<String> notifyUsers, String reason) {
        return TaskNotificationEvent.builder()
                .eventType(NotificationEventType.TASK_APPROVED)
                .taskId(taskId)
                .taskTitle(taskTitle)
                .recipients(notifyUsers)
                .actorId(approvedBy)
                .reason(reason)
                .message("Task '" + taskTitle + "' has been approved by " + approvedBy)
                .build();
    }

    /**
     * Creates a task rejected notification event.
     */
    public static TaskNotificationEvent taskRejected(
            String taskId, String taskTitle, String rejectedBy,
            List<String> notifyUsers, String reason) {
        return TaskNotificationEvent.builder()
                .eventType(NotificationEventType.TASK_REJECTED)
                .taskId(taskId)
                .taskTitle(taskTitle)
                .recipients(notifyUsers)
                .actorId(rejectedBy)
                .reason(reason)
                .message("Task '" + taskTitle + "' has been rejected by " + rejectedBy +
                        (reason != null ? ". Reason: " + reason : ""))
                .priority(NotificationPriority.HIGH)
                .build();
    }

    /**
     * Creates a task completed notification event.
     */
    public static TaskNotificationEvent taskCompleted(
            String taskId, String taskTitle, String completedBy, List<String> notifyUsers) {
        return TaskNotificationEvent.builder()
                .eventType(NotificationEventType.TASK_COMPLETED)
                .taskId(taskId)
                .taskTitle(taskTitle)
                .recipients(notifyUsers)
                .actorId(completedBy)
                .message("Task '" + taskTitle + "' has been completed by " + completedBy)
                .build();
    }

    /**
     * Creates a task escalated notification event.
     */
    public static TaskNotificationEvent taskEscalated(
            String taskId, String taskTitle, List<String> escalateTo, String reason) {
        return TaskNotificationEvent.builder()
                .eventType(NotificationEventType.TASK_ESCALATED)
                .taskId(taskId)
                .taskTitle(taskTitle)
                .recipients(escalateTo)
                .actorId("system")
                .reason(reason)
                .message("Task '" + taskTitle + "' has been escalated. " +
                        (reason != null ? "Reason: " + reason : ""))
                .priority(NotificationPriority.URGENT)
                .build();
    }

    /**
     * Creates a task timeout notification event.
     */
    public static TaskNotificationEvent taskTimeout(
            String taskId, String taskTitle, List<String> notifyUsers) {
        return TaskNotificationEvent.builder()
                .eventType(NotificationEventType.TASK_TIMEOUT)
                .taskId(taskId)
                .taskTitle(taskTitle)
                .recipients(notifyUsers)
                .actorId("system")
                .message("Task '" + taskTitle + "' has timed out")
                .priority(NotificationPriority.HIGH)
                .build();
    }

    /**
     * Creates a task reminder notification event.
     */
    public static TaskNotificationEvent taskReminder(
            String taskId, String taskTitle, List<String> assignees, Instant dueDate) {
        String dueDateMsg = dueDate != null ? " Due: " + dueDate : "";
        return TaskNotificationEvent.builder()
                .eventType(NotificationEventType.TASK_REMINDER)
                .taskId(taskId)
                .taskTitle(taskTitle)
                .recipients(assignees)
                .actorId("system")
                .dueDate(dueDate)
                .message("Reminder: Task '" + taskTitle + "' is pending your action." + dueDateMsg)
                .priority(NotificationPriority.HIGH)
                .build();
    }

    /**
     * Creates a task cancelled notification event.
     */
    public static TaskNotificationEvent taskCancelled(
            String taskId, String taskTitle, String cancelledBy,
            List<String> notifyUsers, String reason) {
        return TaskNotificationEvent.builder()
                .eventType(NotificationEventType.TASK_CANCELLED)
                .taskId(taskId)
                .taskTitle(taskTitle)
                .recipients(notifyUsers)
                .actorId(cancelledBy)
                .reason(reason)
                .message("Task '" + taskTitle + "' has been cancelled" +
                        (reason != null ? ". Reason: " + reason : ""))
                .build();
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Gets the formatted subject line for email notifications.
     */
    public String getEmailSubject() {
        String priorityPrefix = priority == NotificationPriority.URGENT ? "[URGENT] " :
                priority == NotificationPriority.HIGH ? "[HIGH] " : "";
        return priorityPrefix + eventType.getTitle() + ": " + taskTitle;
    }

    /**
     * Gets a short summary suitable for SMS or push notifications.
     */
    public String getShortSummary() {
        return eventType.getTitle() + ": " + taskTitle;
    }

    /**
     * Checks if this notification has recipients.
     */
    public boolean hasRecipients() {
        return recipients != null && !recipients.isEmpty();
    }

    /**
     * Gets the effective message, falling back to default if not set.
     */
    public String getEffectiveMessage() {
        return message != null ? message : eventType.getDefaultMessage();
    }
}
