package com.lyshra.open.app.core.engine.notification;

import com.lyshra.open.app.core.engine.notification.TaskNotificationEvent.NotificationChannel;
import com.lyshra.open.app.core.engine.notification.TaskNotificationEvent.NotificationEventType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service interface for sending task notifications through various channels.
 * Implementations can support console, webhook, email, SMS, and other channels.
 *
 * <h2>Purpose</h2>
 * <p>This service notifies users when action is required on human tasks:</p>
 * <ul>
 *   <li>Task creation and assignment</li>
 *   <li>Task claiming and delegation</li>
 *   <li>Task completion, approval, rejection</li>
 *   <li>Task escalation and timeout</li>
 *   <li>Task reminders</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Send a single notification
 * notificationService.notify(TaskNotificationEvent.taskCreated(
 *     "task-123", "Review Order", "Please review", taskType,
 *     "workflow-456", List.of("user@company.com"), "system"
 * )).subscribe();
 *
 * // Send notification to specific channels
 * notificationService.notify(event, List.of(NotificationChannel.EMAIL, NotificationChannel.SLACK))
 *     .subscribe();
 *
 * // Query notification history
 * notificationService.getNotificationHistory("task-123")
 *     .doOnNext(result -> log.info("Sent to: {}", result.getRecipient()))
 *     .subscribe();
 * }</pre>
 *
 * @see TaskNotificationEvent
 */
public interface ITaskNotificationService {

    // ========================================================================
    // NOTIFICATION SENDING
    // ========================================================================

    /**
     * Sends a notification through all configured channels.
     *
     * @param event the notification event to send
     * @return result of the notification operation
     */
    Mono<NotificationResult> notify(TaskNotificationEvent event);

    /**
     * Sends a notification through specific channels.
     *
     * @param event the notification event to send
     * @param channels the channels to use
     * @return result of the notification operation
     */
    Mono<NotificationResult> notify(TaskNotificationEvent event, List<NotificationChannel> channels);

    /**
     * Sends multiple notifications in batch.
     *
     * @param events the notification events to send
     * @return flux of notification results
     */
    Flux<NotificationResult> notifyBatch(List<TaskNotificationEvent> events);

    // ========================================================================
    // NOTIFICATION HISTORY
    // ========================================================================

    /**
     * Gets notification history for a task.
     *
     * @param taskId the task identifier
     * @return flux of notification results
     */
    default Flux<NotificationResult> getNotificationHistory(String taskId) {
        return Flux.empty();
    }

    /**
     * Gets notification history for a recipient.
     *
     * @param recipientId the recipient identifier
     * @param from start of time range
     * @param to end of time range
     * @return flux of notification results
     */
    default Flux<NotificationResult> getNotificationHistoryByRecipient(
            String recipientId, Instant from, Instant to) {
        return Flux.empty();
    }

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    /**
     * Gets the supported notification channels.
     *
     * @return list of supported channels
     */
    List<NotificationChannel> getSupportedChannels();

    /**
     * Checks if a specific channel is enabled.
     *
     * @param channel the channel to check
     * @return true if the channel is enabled
     */
    boolean isChannelEnabled(NotificationChannel channel);

    /**
     * Enables or disables a notification channel.
     *
     * @param channel the channel to configure
     * @param enabled true to enable, false to disable
     */
    default void setChannelEnabled(NotificationChannel channel, boolean enabled) {
        // Default implementation does nothing
    }

    /**
     * Gets the default channels for notifications.
     *
     * @return list of default channels
     */
    default List<NotificationChannel> getDefaultChannels() {
        return getSupportedChannels();
    }

    // ========================================================================
    // STATISTICS
    // ========================================================================

    /**
     * Gets notification statistics.
     *
     * @param from start of reporting period
     * @param to end of reporting period
     * @return notification statistics
     */
    default Mono<NotificationStatistics> getStatistics(Instant from, Instant to) {
        return Mono.just(new NotificationStatistics(0, 0, 0, Map.of(), Map.of(), from, to));
    }

    // ========================================================================
    // SUPPORTING TYPES
    // ========================================================================

    /**
     * Result of a notification operation.
     */
    record NotificationResult(
            String eventId,
            String taskId,
            String recipient,
            NotificationChannel channel,
            NotificationStatus status,
            Instant sentAt,
            String errorMessage,
            Map<String, Object> metadata
    ) {
        public static NotificationResult success(
                String eventId, String taskId, String recipient, NotificationChannel channel) {
            return new NotificationResult(
                    eventId, taskId, recipient, channel,
                    NotificationStatus.SENT, Instant.now(), null, Map.of()
            );
        }

        public static NotificationResult success(
                String eventId, String taskId, String recipient,
                NotificationChannel channel, Map<String, Object> metadata) {
            return new NotificationResult(
                    eventId, taskId, recipient, channel,
                    NotificationStatus.SENT, Instant.now(), null, metadata
            );
        }

        public static NotificationResult failed(
                String eventId, String taskId, String recipient,
                NotificationChannel channel, String error) {
            return new NotificationResult(
                    eventId, taskId, recipient, channel,
                    NotificationStatus.FAILED, Instant.now(), error, Map.of()
            );
        }

        public static NotificationResult skipped(
                String eventId, String taskId, String recipient,
                NotificationChannel channel, String reason) {
            return new NotificationResult(
                    eventId, taskId, recipient, channel,
                    NotificationStatus.SKIPPED, Instant.now(), reason, Map.of()
            );
        }

        public boolean isSuccess() {
            return status == NotificationStatus.SENT;
        }
    }

    /**
     * Status of a notification.
     */
    enum NotificationStatus {
        /** Notification sent successfully */
        SENT,

        /** Notification is pending/queued */
        PENDING,

        /** Notification failed to send */
        FAILED,

        /** Notification was skipped (e.g., no recipients, disabled channel) */
        SKIPPED,

        /** Notification was retried after initial failure */
        RETRIED
    }

    /**
     * Notification statistics for reporting.
     */
    record NotificationStatistics(
            long totalSent,
            long totalFailed,
            long totalSkipped,
            Map<NotificationChannel, Long> byChannel,
            Map<NotificationEventType, Long> byEventType,
            Instant periodStart,
            Instant periodEnd
    ) {}
}
