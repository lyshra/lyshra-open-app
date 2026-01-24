package com.lyshra.open.app.core.engine.notification.impl;

import com.lyshra.open.app.core.engine.notification.ITaskNotificationService;
import com.lyshra.open.app.core.engine.notification.TaskNotificationEvent;
import com.lyshra.open.app.core.engine.notification.TaskNotificationEvent.NotificationChannel;
import com.lyshra.open.app.core.engine.notification.TaskNotificationEvent.NotificationEventType;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Webhook-based notification service that sends HTTP POST requests to configured endpoints.
 * Supports multiple webhook URLs, retry logic, and custom headers.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Multiple webhook endpoints support</li>
 *   <li>Configurable retry with exponential backoff</li>
 *   <li>Custom headers (authentication, etc.)</li>
 *   <li>Event type filtering per webhook</li>
 *   <li>JSON payload with customizable format</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * WebhookNotificationService service = new WebhookNotificationService()
 *     .addWebhook(WebhookConfig.builder()
 *         .url("https://api.company.com/webhooks/tasks")
 *         .headers(Map.of("Authorization", "Bearer token123"))
 *         .build())
 *     .withRetryAttempts(3);
 *
 * service.notify(TaskNotificationEvent.taskCreated(...)).subscribe();
 * }</pre>
 *
 * <h2>Webhook Payload</h2>
 * <p>The webhook receives a JSON payload with the following structure:</p>
 * <pre>{@code
 * {
 *   "eventId": "uuid",
 *   "eventType": "TASK_CREATED",
 *   "timestamp": "2024-01-15T10:30:00Z",
 *   "taskId": "task-123",
 *   "taskTitle": "Review Order",
 *   "taskDescription": "Please review the order",
 *   "taskStatus": "PENDING",
 *   "workflowInstanceId": "workflow-456",
 *   "recipients": ["user@company.com"],
 *   "actorId": "system",
 *   "message": "New task created",
 *   "priority": "NORMAL",
 *   "metadata": {}
 * }
 * }</pre>
 */
@Slf4j
public class WebhookNotificationService implements ITaskNotificationService {

    private final WebClient webClient;
    private final List<WebhookConfig> webhooks = Collections.synchronizedList(new ArrayList<>());

    // Configuration
    private boolean enabled = true;
    private int retryAttempts = 3;
    private Duration retryBackoff = Duration.ofSeconds(1);
    private Duration timeout = Duration.ofSeconds(10);

    // Statistics
    private final AtomicLong totalSent = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);
    private final Map<String, AtomicLong> sentByWebhook = new ConcurrentHashMap<>();
    private final Map<NotificationEventType, AtomicLong> byEventType = new ConcurrentHashMap<>();

    // History for testing/debugging
    private final List<NotificationResult> notificationHistory = Collections.synchronizedList(new ArrayList<>());
    private boolean storeHistory = false;

    /**
     * Creates a new webhook notification service with default WebClient.
     */
    public WebhookNotificationService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
        log.info("WebhookNotificationService initialized");
    }

    /**
     * Creates a new webhook notification service with custom WebClient.
     */
    public WebhookNotificationService(WebClient webClient) {
        this.webClient = webClient;
        log.info("WebhookNotificationService initialized with custom WebClient");
    }

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    /**
     * Adds a webhook endpoint.
     */
    public WebhookNotificationService addWebhook(WebhookConfig config) {
        webhooks.add(config);
        log.info("Added webhook: {} (name={})", config.getUrl(), config.getName());
        return this;
    }

    /**
     * Adds a simple webhook endpoint by URL.
     */
    public WebhookNotificationService addWebhook(String url) {
        return addWebhook(WebhookConfig.builder().url(url).build());
    }

    /**
     * Removes a webhook endpoint.
     */
    public WebhookNotificationService removeWebhook(String url) {
        webhooks.removeIf(w -> w.getUrl().equals(url));
        return this;
    }

    /**
     * Clears all webhook endpoints.
     */
    public WebhookNotificationService clearWebhooks() {
        webhooks.clear();
        return this;
    }

    /**
     * Sets retry attempts for failed requests.
     */
    public WebhookNotificationService withRetryAttempts(int attempts) {
        this.retryAttempts = attempts;
        return this;
    }

    /**
     * Sets retry backoff duration.
     */
    public WebhookNotificationService withRetryBackoff(Duration backoff) {
        this.retryBackoff = backoff;
        return this;
    }

    /**
     * Sets request timeout.
     */
    public WebhookNotificationService withTimeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Enables or disables the service.
     */
    public WebhookNotificationService setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Enables history storage for testing/debugging.
     */
    public WebhookNotificationService withHistoryStorage(boolean store) {
        this.storeHistory = store;
        return this;
    }

    // ========================================================================
    // NOTIFICATION SENDING
    // ========================================================================

    @Override
    public Mono<NotificationResult> notify(TaskNotificationEvent event) {
        return notify(event, List.of(NotificationChannel.WEBHOOK));
    }

    @Override
    public Mono<NotificationResult> notify(TaskNotificationEvent event, List<NotificationChannel> channels) {
        if (!enabled) {
            return Mono.just(NotificationResult.skipped(
                    event.getEventId(), event.getTaskId(), null,
                    NotificationChannel.WEBHOOK, "Webhook notifications disabled"
            ));
        }

        if (webhooks.isEmpty()) {
            return Mono.just(NotificationResult.skipped(
                    event.getEventId(), event.getTaskId(), null,
                    NotificationChannel.WEBHOOK, "No webhooks configured"
            ));
        }

        // Send to all matching webhooks
        return Flux.fromIterable(webhooks)
                .filter(webhook -> shouldSendToWebhook(webhook, event))
                .flatMap(webhook -> sendToWebhook(webhook, event))
                .collectList()
                .map(results -> aggregateResults(event, results));
    }

    @Override
    public Flux<NotificationResult> notifyBatch(List<TaskNotificationEvent> events) {
        return Flux.fromIterable(events)
                .flatMap(this::notify);
    }

    private boolean shouldSendToWebhook(WebhookConfig webhook, TaskNotificationEvent event) {
        // Check if webhook accepts this event type
        if (webhook.getEventTypes() != null && !webhook.getEventTypes().isEmpty()) {
            return webhook.getEventTypes().contains(event.getEventType());
        }
        return true; // Accept all event types by default
    }

    private Mono<NotificationResult> sendToWebhook(WebhookConfig webhook, TaskNotificationEvent event) {
        Map<String, Object> payload = buildPayload(event);

        WebClient.RequestBodySpec request = webClient.post()
                .uri(webhook.getUrl())
                .contentType(MediaType.APPLICATION_JSON);

        // Add custom headers
        if (webhook.getHeaders() != null) {
            webhook.getHeaders().forEach(request::header);
        }

        return request.bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .timeout(timeout)
                .retryWhen(Retry.backoff(retryAttempts, retryBackoff)
                        .filter(this::isRetryableError)
                        .doBeforeRetry(signal -> log.warn(
                                "Retrying webhook {} for event {}, attempt {}",
                                webhook.getUrl(), event.getEventId(), signal.totalRetries() + 1)))
                .map(response -> {
                    totalSent.incrementAndGet();
                    sentByWebhook.computeIfAbsent(webhook.getUrl(), k -> new AtomicLong(0)).incrementAndGet();
                    byEventType.computeIfAbsent(event.getEventType(), k -> new AtomicLong(0)).incrementAndGet();

                    NotificationResult result = NotificationResult.success(
                            event.getEventId(), event.getTaskId(), webhook.getUrl(),
                            NotificationChannel.WEBHOOK,
                            Map.of("webhookName", webhook.getName() != null ? webhook.getName() : "default",
                                    "statusCode", response.getStatusCode().value())
                    );

                    log.debug("Webhook notification sent: url={}, eventId={}, status={}",
                            webhook.getUrl(), event.getEventId(), response.getStatusCode());

                    if (storeHistory) {
                        notificationHistory.add(result);
                    }

                    return result;
                })
                .onErrorResume(error -> {
                    totalFailed.incrementAndGet();
                    String errorMsg = extractErrorMessage(error);

                    log.error("Webhook notification failed: url={}, eventId={}, error={}",
                            webhook.getUrl(), event.getEventId(), errorMsg);

                    NotificationResult result = NotificationResult.failed(
                            event.getEventId(), event.getTaskId(), webhook.getUrl(),
                            NotificationChannel.WEBHOOK, errorMsg
                    );

                    if (storeHistory) {
                        notificationHistory.add(result);
                    }

                    return Mono.just(result);
                });
    }

    private Map<String, Object> buildPayload(TaskNotificationEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.getEventId());
        payload.put("eventType", event.getEventType().name());
        payload.put("timestamp", event.getTimestamp().toString());
        payload.put("taskId", event.getTaskId());
        payload.put("taskTitle", event.getTaskTitle());
        payload.put("taskDescription", event.getTaskDescription());

        if (event.getTaskType() != null) {
            payload.put("taskType", event.getTaskType().name());
        }
        if (event.getTaskStatus() != null) {
            payload.put("taskStatus", event.getTaskStatus().name());
        }
        if (event.getPreviousStatus() != null) {
            payload.put("previousStatus", event.getPreviousStatus().name());
        }
        if (event.getWorkflowInstanceId() != null) {
            payload.put("workflowInstanceId", event.getWorkflowInstanceId());
        }
        if (event.getRecipients() != null) {
            payload.put("recipients", event.getRecipients());
        }
        if (event.getActorId() != null) {
            payload.put("actorId", event.getActorId());
        }
        if (event.getMessage() != null) {
            payload.put("message", event.getMessage());
        }
        if (event.getPriority() != null) {
            payload.put("priority", event.getPriority().name());
        }
        if (event.getDueDate() != null) {
            payload.put("dueDate", event.getDueDate().toString());
        }
        if (event.getReason() != null) {
            payload.put("reason", event.getReason());
        }
        if (event.getCorrelationId() != null) {
            payload.put("correlationId", event.getCorrelationId());
        }
        if (event.getTenantId() != null) {
            payload.put("tenantId", event.getTenantId());
        }
        if (event.getMetadata() != null && !event.getMetadata().isEmpty()) {
            payload.put("metadata", event.getMetadata());
        }

        return payload;
    }

    private boolean isRetryableError(Throwable error) {
        if (error instanceof WebClientResponseException ex) {
            // Retry on 5xx errors, not on 4xx
            return ex.getStatusCode().is5xxServerError();
        }
        // Retry on connection errors
        return true;
    }

    private String extractErrorMessage(Throwable error) {
        if (error instanceof WebClientResponseException ex) {
            return String.format("HTTP %d: %s", ex.getStatusCode().value(), ex.getStatusText());
        }
        return error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
    }

    private NotificationResult aggregateResults(TaskNotificationEvent event, List<NotificationResult> results) {
        if (results.isEmpty()) {
            return NotificationResult.skipped(
                    event.getEventId(), event.getTaskId(), null,
                    NotificationChannel.WEBHOOK, "No matching webhooks"
            );
        }

        // Check if all succeeded
        boolean allSuccess = results.stream().allMatch(NotificationResult::isSuccess);
        long successCount = results.stream().filter(NotificationResult::isSuccess).count();

        if (allSuccess) {
            return NotificationResult.success(
                    event.getEventId(), event.getTaskId(), "all",
                    NotificationChannel.WEBHOOK,
                    Map.of("webhookCount", results.size(), "successCount", successCount)
            );
        } else {
            String errors = results.stream()
                    .filter(r -> !r.isSuccess())
                    .map(NotificationResult::errorMessage)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse("Some webhooks failed");

            return NotificationResult.failed(
                    event.getEventId(), event.getTaskId(), "partial",
                    NotificationChannel.WEBHOOK,
                    String.format("%d/%d webhooks succeeded. Error: %s", successCount, results.size(), errors)
            );
        }
    }

    // ========================================================================
    // NOTIFICATION HISTORY
    // ========================================================================

    @Override
    public Flux<NotificationResult> getNotificationHistory(String taskId) {
        return Flux.fromIterable(notificationHistory)
                .filter(r -> taskId.equals(r.taskId()));
    }

    /**
     * Gets all notification history.
     */
    public List<NotificationResult> getAllNotificationHistory() {
        return new ArrayList<>(notificationHistory);
    }

    /**
     * Clears notification history.
     */
    public void clearHistory() {
        notificationHistory.clear();
    }

    // ========================================================================
    // CONFIGURATION QUERIES
    // ========================================================================

    @Override
    public List<NotificationChannel> getSupportedChannels() {
        return List.of(NotificationChannel.WEBHOOK);
    }

    @Override
    public boolean isChannelEnabled(NotificationChannel channel) {
        return channel == NotificationChannel.WEBHOOK && enabled && !webhooks.isEmpty();
    }

    @Override
    public void setChannelEnabled(NotificationChannel channel, boolean enabled) {
        if (channel == NotificationChannel.WEBHOOK) {
            this.enabled = enabled;
        }
    }

    /**
     * Gets configured webhooks.
     */
    public List<WebhookConfig> getWebhooks() {
        return new ArrayList<>(webhooks);
    }

    // ========================================================================
    // STATISTICS
    // ========================================================================

    @Override
    public Mono<NotificationStatistics> getStatistics(Instant from, Instant to) {
        return Mono.fromCallable(() -> {
            Map<NotificationChannel, Long> byChannel = Map.of(
                    NotificationChannel.WEBHOOK, totalSent.get()
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
     * Resets statistics.
     */
    public void resetStatistics() {
        totalSent.set(0);
        totalFailed.set(0);
        sentByWebhook.clear();
        byEventType.clear();
    }

    /**
     * Resets the service (for testing).
     */
    public void reset() {
        clearHistory();
        resetStatistics();
        log.info("WebhookNotificationService reset");
    }

    // ========================================================================
    // WEBHOOK CONFIG
    // ========================================================================

    /**
     * Configuration for a webhook endpoint.
     */
    @Data
    @Builder
    public static class WebhookConfig {
        /**
         * Webhook endpoint URL.
         */
        private final String url;

        /**
         * Optional name for identification.
         */
        @Builder.Default
        private final String name = "default";

        /**
         * Custom headers to include in requests.
         */
        private final Map<String, String> headers;

        /**
         * Event types to send to this webhook (null = all types).
         */
        private final Set<NotificationEventType> eventTypes;

        /**
         * Whether this webhook is enabled.
         */
        @Builder.Default
        private final boolean enabled = true;
    }
}
