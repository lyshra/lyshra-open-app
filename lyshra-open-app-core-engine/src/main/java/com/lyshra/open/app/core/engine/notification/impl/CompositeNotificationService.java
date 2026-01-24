package com.lyshra.open.app.core.engine.notification.impl;

import com.lyshra.open.app.core.engine.notification.ITaskNotificationService;
import com.lyshra.open.app.core.engine.notification.TaskNotificationEvent;
import com.lyshra.open.app.core.engine.notification.TaskNotificationEvent.NotificationChannel;
import com.lyshra.open.app.core.engine.notification.TaskNotificationEvent.NotificationEventType;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Composite notification service that delegates to multiple channel-specific services.
 * Allows sending notifications through multiple channels simultaneously.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Multiple channel support (console, webhook, email, etc.)</li>
 *   <li>Configurable default channels</li>
 *   <li>Per-event channel selection</li>
 *   <li>Aggregated statistics</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create composite service with multiple channels
 * CompositeNotificationService notificationService = CompositeNotificationService.create()
 *     .addChannel(NotificationChannel.CONSOLE, new ConsoleNotificationService())
 *     .addChannel(NotificationChannel.WEBHOOK, webhookService)
 *     .withDefaultChannels(List.of(NotificationChannel.CONSOLE, NotificationChannel.WEBHOOK));
 *
 * // Send notification through all default channels
 * notificationService.notify(TaskNotificationEvent.taskCreated(...)).subscribe();
 *
 * // Send notification through specific channels
 * notificationService.notify(event, List.of(NotificationChannel.WEBHOOK)).subscribe();
 * }</pre>
 */
@Slf4j
public class CompositeNotificationService implements ITaskNotificationService {

    private final Map<NotificationChannel, ITaskNotificationService> channels = new ConcurrentHashMap<>();
    private final Map<NotificationChannel, Boolean> channelEnabled = new ConcurrentHashMap<>();
    private List<NotificationChannel> defaultChannels = new ArrayList<>();

    public CompositeNotificationService() {
        log.info("CompositeNotificationService initialized");
    }

    /**
     * Factory method to create a new instance.
     */
    public static CompositeNotificationService create() {
        return new CompositeNotificationService();
    }

    /**
     * Factory method to create an instance with console notifications enabled.
     */
    public static CompositeNotificationService withConsole() {
        return new CompositeNotificationService()
                .addChannel(NotificationChannel.CONSOLE, new ConsoleNotificationService())
                .withDefaultChannels(List.of(NotificationChannel.CONSOLE));
    }

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    /**
     * Adds a notification channel.
     */
    public CompositeNotificationService addChannel(NotificationChannel channel, ITaskNotificationService service) {
        channels.put(channel, service);
        channelEnabled.put(channel, true);
        log.info("Added notification channel: {}", channel);
        return this;
    }

    /**
     * Removes a notification channel.
     */
    public CompositeNotificationService removeChannel(NotificationChannel channel) {
        channels.remove(channel);
        channelEnabled.remove(channel);
        defaultChannels.remove(channel);
        return this;
    }

    /**
     * Sets the default channels for notifications.
     */
    public CompositeNotificationService withDefaultChannels(List<NotificationChannel> channels) {
        this.defaultChannels = new ArrayList<>(channels);
        return this;
    }

    /**
     * Gets the service for a specific channel.
     */
    public Optional<ITaskNotificationService> getChannelService(NotificationChannel channel) {
        return Optional.ofNullable(channels.get(channel));
    }

    // ========================================================================
    // NOTIFICATION SENDING
    // ========================================================================

    @Override
    public Mono<NotificationResult> notify(TaskNotificationEvent event) {
        // Use event-specified channels, or default channels, or all channels
        List<NotificationChannel> targetChannels = event.getChannels() != null && !event.getChannels().isEmpty()
                ? event.getChannels()
                : !defaultChannels.isEmpty()
                        ? defaultChannels
                        : new ArrayList<>(channels.keySet());

        return notify(event, targetChannels);
    }

    @Override
    public Mono<NotificationResult> notify(TaskNotificationEvent event, List<NotificationChannel> targetChannels) {
        if (targetChannels == null || targetChannels.isEmpty()) {
            return Mono.just(NotificationResult.skipped(
                    event.getEventId(), event.getTaskId(), null,
                    null, "No channels specified"
            ));
        }

        // Send to all specified channels in parallel
        return Flux.fromIterable(targetChannels)
                .filter(channel -> channels.containsKey(channel) && isChannelEnabled(channel))
                .flatMap(channel -> sendToChannel(channel, event))
                .collectList()
                .map(results -> aggregateResults(event, results));
    }

    @Override
    public Flux<NotificationResult> notifyBatch(List<TaskNotificationEvent> events) {
        return Flux.fromIterable(events)
                .flatMap(this::notify);
    }

    private Mono<NotificationResult> sendToChannel(NotificationChannel channel, TaskNotificationEvent event) {
        ITaskNotificationService service = channels.get(channel);
        if (service == null) {
            return Mono.just(NotificationResult.skipped(
                    event.getEventId(), event.getTaskId(), null,
                    channel, "Channel not configured: " + channel
            ));
        }

        return service.notify(event, List.of(channel))
                .onErrorResume(error -> {
                    log.error("Error sending notification via {}: {}", channel, error.getMessage());
                    return Mono.just(NotificationResult.failed(
                            event.getEventId(), event.getTaskId(), null,
                            channel, error.getMessage()
                    ));
                });
    }

    private NotificationResult aggregateResults(TaskNotificationEvent event, List<NotificationResult> results) {
        if (results.isEmpty()) {
            return NotificationResult.skipped(
                    event.getEventId(), event.getTaskId(), null,
                    null, "No channels available"
            );
        }

        long successCount = results.stream().filter(NotificationResult::isSuccess).count();
        boolean allSuccess = successCount == results.size();

        if (allSuccess) {
            return NotificationResult.success(
                    event.getEventId(), event.getTaskId(), "all",
                    null, // Multiple channels
                    Map.of("channelCount", results.size(), "successCount", successCount)
            );
        } else {
            String errors = results.stream()
                    .filter(r -> !r.isSuccess())
                    .map(r -> r.channel() + ": " + r.errorMessage())
                    .collect(Collectors.joining("; "));

            if (successCount > 0) {
                // Partial success
                return NotificationResult.success(
                        event.getEventId(), event.getTaskId(), "partial",
                        null,
                        Map.of("channelCount", results.size(),
                                "successCount", successCount,
                                "errors", errors)
                );
            } else {
                return NotificationResult.failed(
                        event.getEventId(), event.getTaskId(), null,
                        null, errors
                );
            }
        }
    }

    // ========================================================================
    // NOTIFICATION HISTORY
    // ========================================================================

    @Override
    public Flux<NotificationResult> getNotificationHistory(String taskId) {
        return Flux.fromIterable(channels.values())
                .flatMap(service -> service.getNotificationHistory(taskId));
    }

    @Override
    public Flux<NotificationResult> getNotificationHistoryByRecipient(
            String recipientId, Instant from, Instant to) {
        return Flux.fromIterable(channels.values())
                .flatMap(service -> service.getNotificationHistoryByRecipient(recipientId, from, to));
    }

    // ========================================================================
    // CONFIGURATION QUERIES
    // ========================================================================

    @Override
    public List<NotificationChannel> getSupportedChannels() {
        return new ArrayList<>(channels.keySet());
    }

    @Override
    public boolean isChannelEnabled(NotificationChannel channel) {
        return channelEnabled.getOrDefault(channel, false);
    }

    @Override
    public void setChannelEnabled(NotificationChannel channel, boolean enabled) {
        if (channels.containsKey(channel)) {
            channelEnabled.put(channel, enabled);
            // Also propagate to the underlying service
            ITaskNotificationService service = channels.get(channel);
            if (service != null) {
                service.setChannelEnabled(channel, enabled);
            }
        }
    }

    @Override
    public List<NotificationChannel> getDefaultChannels() {
        return new ArrayList<>(defaultChannels);
    }

    // ========================================================================
    // STATISTICS
    // ========================================================================

    @Override
    public Mono<NotificationStatistics> getStatistics(Instant from, Instant to) {
        return Flux.fromIterable(channels.values())
                .flatMap(service -> service.getStatistics(from, to))
                .collectList()
                .map(statsList -> aggregateStatistics(statsList, from, to));
    }

    private NotificationStatistics aggregateStatistics(
            List<NotificationStatistics> statsList, Instant from, Instant to) {
        long totalSent = 0;
        long totalFailed = 0;
        long totalSkipped = 0;
        Map<NotificationChannel, Long> byChannel = new HashMap<>();
        Map<NotificationEventType, Long> byEventType = new HashMap<>();

        for (NotificationStatistics stats : statsList) {
            totalSent += stats.totalSent();
            totalFailed += stats.totalFailed();
            totalSkipped += stats.totalSkipped();

            stats.byChannel().forEach((channel, count) ->
                    byChannel.merge(channel, count, Long::sum));
            stats.byEventType().forEach((type, count) ->
                    byEventType.merge(type, count, Long::sum));
        }

        return new NotificationStatistics(
                totalSent, totalFailed, totalSkipped,
                byChannel, byEventType, from, to
        );
    }

    /**
     * Resets all channel services (for testing).
     */
    public void reset() {
        channels.values().forEach(service -> {
            if (service instanceof ConsoleNotificationService console) {
                console.reset();
            } else if (service instanceof WebhookNotificationService webhook) {
                webhook.reset();
            }
        });
        log.info("CompositeNotificationService reset");
    }
}
