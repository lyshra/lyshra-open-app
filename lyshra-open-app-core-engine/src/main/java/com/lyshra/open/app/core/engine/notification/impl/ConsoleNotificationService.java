package com.lyshra.open.app.core.engine.notification.impl;

import com.lyshra.open.app.core.engine.notification.ITaskNotificationService;
import com.lyshra.open.app.core.engine.notification.TaskNotificationEvent;
import com.lyshra.open.app.core.engine.notification.TaskNotificationEvent.NotificationChannel;
import com.lyshra.open.app.core.engine.notification.TaskNotificationEvent.NotificationEventType;
import com.lyshra.open.app.core.engine.notification.TaskNotificationEvent.NotificationPriority;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Console-based notification service for development and testing.
 * Outputs notifications to the console/log with formatted display.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Formatted console output with priority indicators</li>
 *   <li>Notification history tracking for testing</li>
 *   <li>Statistics collection</li>
 *   <li>Configurable output format</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * ConsoleNotificationService notificationService = new ConsoleNotificationService();
 *
 * // Send notification
 * notificationService.notify(TaskNotificationEvent.taskCreated(
 *     "task-123", "Review Order", "Please review the order",
 *     LyshraOpenAppHumanTaskType.APPROVAL, "workflow-456",
 *     List.of("user@company.com"), "system"
 * )).subscribe();
 *
 * // Check notification history
 * List<NotificationResult> history = notificationService
 *     .getNotificationHistory("task-123")
 *     .collectList().block();
 * }</pre>
 */
@Slf4j
public class ConsoleNotificationService implements ITaskNotificationService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    // Notification history for testing
    private final Map<String, List<NotificationResult>> historyByTask = new ConcurrentHashMap<>();
    private final Map<String, List<NotificationResult>> historyByRecipient = new ConcurrentHashMap<>();
    private final List<NotificationResult> allNotifications = Collections.synchronizedList(new ArrayList<>());

    // Statistics
    private final AtomicLong totalSent = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);
    private final Map<NotificationEventType, AtomicLong> byEventType = new ConcurrentHashMap<>();

    // Configuration
    private boolean enabled = true;
    private boolean verbose = true;
    private boolean storeHistory = true;

    public ConsoleNotificationService() {
        log.info("ConsoleNotificationService initialized");
    }

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    /**
     * Enables or disables the console notification service.
     */
    public ConsoleNotificationService setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Sets verbose mode for detailed output.
     */
    public ConsoleNotificationService setVerbose(boolean verbose) {
        this.verbose = verbose;
        return this;
    }

    /**
     * Enables or disables history storage.
     */
    public ConsoleNotificationService setStoreHistory(boolean store) {
        this.storeHistory = store;
        return this;
    }

    // ========================================================================
    // NOTIFICATION SENDING
    // ========================================================================

    @Override
    public Mono<NotificationResult> notify(TaskNotificationEvent event) {
        return notify(event, List.of(NotificationChannel.CONSOLE));
    }

    @Override
    public Mono<NotificationResult> notify(TaskNotificationEvent event, List<NotificationChannel> channels) {
        return Mono.fromCallable(() -> {
            if (!enabled) {
                return NotificationResult.skipped(
                        event.getEventId(), event.getTaskId(), null,
                        NotificationChannel.CONSOLE, "Console notifications disabled"
                );
            }

            if (!event.hasRecipients()) {
                return NotificationResult.skipped(
                        event.getEventId(), event.getTaskId(), null,
                        NotificationChannel.CONSOLE, "No recipients specified"
                );
            }

            // Print notification to console
            printNotification(event);

            // Track statistics
            totalSent.incrementAndGet();
            byEventType.computeIfAbsent(event.getEventType(), k -> new AtomicLong(0)).incrementAndGet();

            // Create results for each recipient
            List<NotificationResult> results = new ArrayList<>();
            for (String recipient : event.getRecipients()) {
                NotificationResult result = NotificationResult.success(
                        event.getEventId(), event.getTaskId(), recipient,
                        NotificationChannel.CONSOLE,
                        Map.of("eventType", event.getEventType().name())
                );
                results.add(result);

                if (storeHistory) {
                    storeNotificationResult(event.getTaskId(), recipient, result);
                }
            }

            // Return first result (aggregate)
            return results.isEmpty() ? NotificationResult.success(
                    event.getEventId(), event.getTaskId(), "all",
                    NotificationChannel.CONSOLE
            ) : results.get(0);
        });
    }

    @Override
    public Flux<NotificationResult> notifyBatch(List<TaskNotificationEvent> events) {
        return Flux.fromIterable(events)
                .flatMap(this::notify);
    }

    // ========================================================================
    // NOTIFICATION HISTORY
    // ========================================================================

    @Override
    public Flux<NotificationResult> getNotificationHistory(String taskId) {
        return Flux.fromIterable(historyByTask.getOrDefault(taskId, Collections.emptyList()));
    }

    @Override
    public Flux<NotificationResult> getNotificationHistoryByRecipient(
            String recipientId, Instant from, Instant to) {
        return Flux.fromIterable(historyByRecipient.getOrDefault(recipientId, Collections.emptyList()))
                .filter(r -> !r.sentAt().isBefore(from) && r.sentAt().isBefore(to));
    }

    /**
     * Gets all notification history.
     */
    public List<NotificationResult> getAllNotifications() {
        return new ArrayList<>(allNotifications);
    }

    /**
     * Clears notification history.
     */
    public void clearHistory() {
        historyByTask.clear();
        historyByRecipient.clear();
        allNotifications.clear();
    }

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    @Override
    public List<NotificationChannel> getSupportedChannels() {
        return List.of(NotificationChannel.CONSOLE);
    }

    @Override
    public boolean isChannelEnabled(NotificationChannel channel) {
        return channel == NotificationChannel.CONSOLE && enabled;
    }

    @Override
    public void setChannelEnabled(NotificationChannel channel, boolean enabled) {
        if (channel == NotificationChannel.CONSOLE) {
            this.enabled = enabled;
        }
    }

    // ========================================================================
    // STATISTICS
    // ========================================================================

    @Override
    public Mono<NotificationStatistics> getStatistics(Instant from, Instant to) {
        return Mono.fromCallable(() -> {
            Map<NotificationChannel, Long> byChannel = Map.of(
                    NotificationChannel.CONSOLE, totalSent.get()
            );

            Map<NotificationEventType, Long> eventTypeCounts = new HashMap<>();
            byEventType.forEach((type, count) -> eventTypeCounts.put(type, count.get()));

            return new NotificationStatistics(
                    totalSent.get(),
                    totalFailed.get(),
                    0L,
                    byChannel,
                    eventTypeCounts,
                    from,
                    to
            );
        });
    }

    /**
     * Resets statistics counters.
     */
    public void resetStatistics() {
        totalSent.set(0);
        totalFailed.set(0);
        byEventType.clear();
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    private void printNotification(TaskNotificationEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("╔══════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║  ").append(getPriorityIcon(event.getPriority())).append(" TASK NOTIFICATION");
        sb.append(repeat(" ", 50 - event.getEventType().getTitle().length()));
        sb.append("║\n");
        sb.append("╠══════════════════════════════════════════════════════════════════════╣\n");
        sb.append("║  Type: ").append(padRight(event.getEventType().getTitle(), 60)).append("║\n");
        sb.append("║  Task: ").append(padRight(event.getTaskTitle() != null ? event.getTaskTitle() : "N/A", 60)).append("║\n");
        sb.append("║  Task ID: ").append(padRight(event.getTaskId() != null ? event.getTaskId() : "N/A", 57)).append("║\n");
        sb.append("║  Recipients: ").append(padRight(formatRecipients(event.getRecipients()), 54)).append("║\n");

        if (verbose) {
            sb.append("║  Time: ").append(padRight(TIMESTAMP_FORMAT.format(event.getTimestamp()), 60)).append("║\n");
            if (event.getActorId() != null) {
                sb.append("║  Actor: ").append(padRight(event.getActorId(), 59)).append("║\n");
            }
            if (event.getReason() != null) {
                sb.append("║  Reason: ").append(padRight(truncate(event.getReason(), 57), 58)).append("║\n");
            }
            if (event.getWorkflowInstanceId() != null) {
                sb.append("║  Workflow: ").append(padRight(event.getWorkflowInstanceId(), 56)).append("║\n");
            }
        }

        sb.append("╠══════════════════════════════════════════════════════════════════════╣\n");
        sb.append("║  Message: ").append(padRight(truncate(event.getEffectiveMessage(), 56), 57)).append("║\n");
        sb.append("╚══════════════════════════════════════════════════════════════════════╝\n");

        // Use different log levels based on priority
        String output = sb.toString();
        switch (event.getPriority()) {
            case URGENT -> log.warn(output);
            case HIGH -> log.info(output);
            default -> log.info(output);
        }
    }

    private String getPriorityIcon(NotificationPriority priority) {
        return switch (priority) {
            case URGENT -> "[!!!]";
            case HIGH -> "[!!]";
            case NORMAL -> "[!]";
            case LOW -> "[.]";
        };
    }

    private String formatRecipients(List<String> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return "None";
        }
        String joined = String.join(", ", recipients);
        return truncate(joined, 54);
    }

    private String padRight(String s, int length) {
        if (s == null) s = "";
        if (s.length() >= length) return s.substring(0, length);
        return s + repeat(" ", length - s.length());
    }

    private String truncate(String s, int maxLength) {
        if (s == null) return "";
        if (s.length() <= maxLength) return s;
        return s.substring(0, maxLength - 3) + "...";
    }

    private String repeat(String s, int times) {
        if (times <= 0) return "";
        return s.repeat(times);
    }

    private void storeNotificationResult(String taskId, String recipient, NotificationResult result) {
        if (taskId != null) {
            historyByTask.computeIfAbsent(taskId, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(result);
        }
        if (recipient != null) {
            historyByRecipient.computeIfAbsent(recipient, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(result);
        }
        allNotifications.add(result);
    }

    /**
     * Resets the service (for testing).
     */
    public void reset() {
        clearHistory();
        resetStatistics();
        log.info("ConsoleNotificationService reset");
    }
}
