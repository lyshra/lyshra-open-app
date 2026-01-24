package com.lyshra.open.app.core.engine.timeout.impl;

import com.lyshra.open.app.core.engine.signal.ILyshraOpenAppSignalService;
import com.lyshra.open.app.core.engine.signal.impl.LyshraOpenAppSignal;
import com.lyshra.open.app.core.engine.signal.impl.LyshraOpenAppSignalServiceImpl;
import com.lyshra.open.app.core.engine.timeout.ILyshraOpenAppTimeoutScheduler;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTask;
import com.lyshra.open.app.integration.contract.signal.ILyshraOpenAppSignal;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowInstance;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppSignalType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Default in-memory implementation of the timeout scheduler.
 * For production use, this should be replaced with a distributed
 * implementation using a scheduler like Quartz, or a message queue.
 *
 * <p>Design Pattern: Scheduler Pattern with Timer Wheel
 * - Uses ScheduledExecutorService for timing
 * - Maintains a registry of active timeouts
 *
 * <p>Limitations of this implementation:
 * - Not distributed (single-node only)
 * - Not durable (timeouts lost on restart)
 * - For production, consider using Redis-based or database-backed scheduler
 */
@Slf4j
public class LyshraOpenAppTimeoutSchedulerImpl implements ILyshraOpenAppTimeoutScheduler {

    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledTimeout> activeTimeouts;
    private final ILyshraOpenAppSignalService signalService;
    private volatile boolean running = false;

    private LyshraOpenAppTimeoutSchedulerImpl() {
        this.scheduler = Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "timeout-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.activeTimeouts = new ConcurrentHashMap<>();
        this.signalService = LyshraOpenAppSignalServiceImpl.getInstance();
    }

    private static final class SingletonHelper {
        private static final ILyshraOpenAppTimeoutScheduler INSTANCE = new LyshraOpenAppTimeoutSchedulerImpl();
    }

    public static ILyshraOpenAppTimeoutScheduler getInstance() {
        return SingletonHelper.INSTANCE;
    }

    @Override
    public Mono<String> scheduleTaskTimeout(ILyshraOpenAppHumanTask task, Duration timeout) {
        return scheduleTaskTimeout(task, Instant.now().plus(timeout));
    }

    @Override
    public Mono<String> scheduleTaskTimeout(ILyshraOpenAppHumanTask task, Instant timeoutAt) {
        return Mono.fromCallable(() -> {
            if (!running) {
                throw new IllegalStateException("Timeout scheduler is not running");
            }

            String timeoutId = generateTimeoutId("task", task.getTaskId());
            Duration delay = Duration.between(Instant.now(), timeoutAt);

            if (delay.isNegative() || delay.isZero()) {
                // Already expired, send signal immediately
                sendTaskTimeoutSignal(task.getWorkflowInstanceId(), task.getTaskId());
                return timeoutId;
            }

            ScheduledFuture<?> future = scheduler.schedule(
                    () -> handleTaskTimeout(task.getWorkflowInstanceId(), task.getTaskId(), timeoutId),
                    delay.toMillis(),
                    TimeUnit.MILLISECONDS
            );

            activeTimeouts.put(timeoutId, new ScheduledTimeout(
                    timeoutId,
                    TimeoutType.TASK,
                    task.getTaskId(),
                    task.getWorkflowInstanceId(),
                    timeoutAt,
                    0,
                    future
            ));

            log.info("Scheduled task timeout: id={}, taskId={}, at={}", timeoutId, task.getTaskId(), timeoutAt);
            return timeoutId;
        });
    }

    @Override
    public Mono<String> scheduleWorkflowTimeout(ILyshraOpenAppWorkflowInstance instance, Duration timeout) {
        return Mono.fromCallable(() -> {
            if (!running) {
                throw new IllegalStateException("Timeout scheduler is not running");
            }

            String timeoutId = generateTimeoutId("workflow", instance.getInstanceId());
            Instant timeoutAt = Instant.now().plus(timeout);

            ScheduledFuture<?> future = scheduler.schedule(
                    () -> handleWorkflowTimeout(instance.getInstanceId(), timeoutId),
                    timeout.toMillis(),
                    TimeUnit.MILLISECONDS
            );

            activeTimeouts.put(timeoutId, new ScheduledTimeout(
                    timeoutId,
                    TimeoutType.WORKFLOW,
                    null,
                    instance.getInstanceId(),
                    timeoutAt,
                    0,
                    future
            ));

            log.info("Scheduled workflow timeout: id={}, workflowId={}, at={}", timeoutId, instance.getInstanceId(), timeoutAt);
            return timeoutId;
        });
    }

    @Override
    public Mono<String> scheduleEscalationTimeout(ILyshraOpenAppHumanTask task, Duration timeout, int escalationLevel) {
        return Mono.fromCallable(() -> {
            if (!running) {
                throw new IllegalStateException("Timeout scheduler is not running");
            }

            String timeoutId = generateTimeoutId("escalation", task.getTaskId() + "-L" + escalationLevel);
            Instant timeoutAt = Instant.now().plus(timeout);

            ScheduledFuture<?> future = scheduler.schedule(
                    () -> handleEscalationTimeout(task.getWorkflowInstanceId(), task.getTaskId(), escalationLevel, timeoutId),
                    timeout.toMillis(),
                    TimeUnit.MILLISECONDS
            );

            activeTimeouts.put(timeoutId, new ScheduledTimeout(
                    timeoutId,
                    TimeoutType.ESCALATION,
                    task.getTaskId(),
                    task.getWorkflowInstanceId(),
                    timeoutAt,
                    escalationLevel,
                    future
            ));

            log.info("Scheduled escalation timeout: id={}, taskId={}, level={}, at={}",
                    timeoutId, task.getTaskId(), escalationLevel, timeoutAt);
            return timeoutId;
        });
    }

    @Override
    public Mono<Boolean> cancelTimeout(String timeoutId) {
        return Mono.fromCallable(() -> {
            ScheduledTimeout timeout = activeTimeouts.remove(timeoutId);
            if (timeout != null) {
                timeout.getFuture().cancel(false);
                log.info("Cancelled timeout: {}", timeoutId);
                return true;
            }
            return false;
        });
    }

    @Override
    public Mono<Integer> cancelTaskTimeouts(String taskId) {
        return Mono.fromCallable(() -> {
            int cancelled = 0;
            for (Map.Entry<String, ScheduledTimeout> entry : activeTimeouts.entrySet()) {
                if (taskId.equals(entry.getValue().getTaskId())) {
                    entry.getValue().getFuture().cancel(false);
                    activeTimeouts.remove(entry.getKey());
                    cancelled++;
                }
            }
            log.info("Cancelled {} timeouts for task: {}", cancelled, taskId);
            return cancelled;
        });
    }

    @Override
    public Mono<Integer> cancelWorkflowTimeouts(String workflowInstanceId) {
        return Mono.fromCallable(() -> {
            int cancelled = 0;
            for (Map.Entry<String, ScheduledTimeout> entry : activeTimeouts.entrySet()) {
                if (workflowInstanceId.equals(entry.getValue().getWorkflowInstanceId())) {
                    entry.getValue().getFuture().cancel(false);
                    activeTimeouts.remove(entry.getKey());
                    cancelled++;
                }
            }
            log.info("Cancelled {} timeouts for workflow: {}", cancelled, workflowInstanceId);
            return cancelled;
        });
    }

    @Override
    public Mono<Instant> extendTimeout(String timeoutId, Duration additionalTime) {
        return Mono.fromCallable(() -> {
            ScheduledTimeout existing = activeTimeouts.get(timeoutId);
            if (existing == null) {
                throw new IllegalArgumentException("Timeout not found: " + timeoutId);
            }

            // Cancel existing
            existing.getFuture().cancel(false);

            // Calculate new timeout
            Instant newTimeout = Instant.now().plus(additionalTime);
            Duration delay = Duration.between(Instant.now(), newTimeout);

            // Schedule new
            ScheduledFuture<?> newFuture = scheduler.schedule(
                    () -> handleTimeoutByType(existing, timeoutId),
                    delay.toMillis(),
                    TimeUnit.MILLISECONDS
            );

            // Update registry
            activeTimeouts.put(timeoutId, new ScheduledTimeout(
                    timeoutId,
                    existing.getType(),
                    existing.getTaskId(),
                    existing.getWorkflowInstanceId(),
                    newTimeout,
                    existing.getEscalationLevel(),
                    newFuture
            ));

            log.info("Extended timeout: id={}, new time={}", timeoutId, newTimeout);
            return newTimeout;
        });
    }

    @Override
    public Mono<Boolean> isTimeoutActive(String timeoutId) {
        return Mono.fromCallable(() -> activeTimeouts.containsKey(timeoutId));
    }

    @Override
    public Mono<Duration> getRemainingTime(String timeoutId) {
        return Mono.fromCallable(() -> {
            ScheduledTimeout timeout = activeTimeouts.get(timeoutId);
            if (timeout == null) {
                return Duration.ZERO;
            }
            Duration remaining = Duration.between(Instant.now(), timeout.getTimeoutAt());
            return remaining.isNegative() ? Duration.ZERO : remaining;
        });
    }

    @Override
    public void start() {
        running = true;
        log.info("Timeout scheduler started");
    }

    @Override
    public void stop() {
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Timeout scheduler stopped");
    }

    @Override
    public Mono<Integer> recoverTimeouts() {
        // In-memory implementation has nothing to recover
        // A production implementation would load from persistent storage
        return Mono.just(0);
    }

    private void handleTimeoutByType(ScheduledTimeout timeout, String timeoutId) {
        switch (timeout.getType()) {
            case TASK:
                handleTaskTimeout(timeout.getWorkflowInstanceId(), timeout.getTaskId(), timeoutId);
                break;
            case WORKFLOW:
                handleWorkflowTimeout(timeout.getWorkflowInstanceId(), timeoutId);
                break;
            case ESCALATION:
                handleEscalationTimeout(timeout.getWorkflowInstanceId(), timeout.getTaskId(),
                        timeout.getEscalationLevel(), timeoutId);
                break;
        }
    }

    private void handleTaskTimeout(String workflowInstanceId, String taskId, String timeoutId) {
        activeTimeouts.remove(timeoutId);
        log.info("Task timeout triggered: workflowId={}, taskId={}", workflowInstanceId, taskId);
        sendTaskTimeoutSignal(workflowInstanceId, taskId);
    }

    private void handleWorkflowTimeout(String workflowInstanceId, String timeoutId) {
        activeTimeouts.remove(timeoutId);
        log.info("Workflow timeout triggered: workflowId={}", workflowInstanceId);
        sendWorkflowTimeoutSignal(workflowInstanceId);
    }

    private void handleEscalationTimeout(String workflowInstanceId, String taskId, int escalationLevel, String timeoutId) {
        activeTimeouts.remove(timeoutId);
        log.info("Escalation timeout triggered: workflowId={}, taskId={}, level={}",
                workflowInstanceId, taskId, escalationLevel);
        sendEscalationSignal(workflowInstanceId, taskId, escalationLevel);
    }

    private void sendTaskTimeoutSignal(String workflowInstanceId, String taskId) {
        ILyshraOpenAppSignal signal = LyshraOpenAppSignal.builder()
                .signalId(UUID.randomUUID().toString())
                .signalType(LyshraOpenAppSignalType.TIMEOUT)
                .workflowInstanceId(workflowInstanceId)
                .taskId(taskId)
                .senderId("timeout-scheduler")
                .senderType(ILyshraOpenAppSignal.SenderType.TIMER)
                .createdAt(Instant.now())
                .build();

        signalService.sendSignal(signal).subscribe();
    }

    private void sendWorkflowTimeoutSignal(String workflowInstanceId) {
        ILyshraOpenAppSignal signal = LyshraOpenAppSignal.builder()
                .signalId(UUID.randomUUID().toString())
                .signalType(LyshraOpenAppSignalType.TIMEOUT)
                .workflowInstanceId(workflowInstanceId)
                .senderId("timeout-scheduler")
                .senderType(ILyshraOpenAppSignal.SenderType.TIMER)
                .createdAt(Instant.now())
                .build();

        signalService.sendSignal(signal).subscribe();
    }

    private void sendEscalationSignal(String workflowInstanceId, String taskId, int escalationLevel) {
        ILyshraOpenAppSignal signal = LyshraOpenAppSignal.builder()
                .signalId(UUID.randomUUID().toString())
                .signalType(LyshraOpenAppSignalType.ESCALATE)
                .workflowInstanceId(workflowInstanceId)
                .taskId(taskId)
                .senderId("timeout-scheduler")
                .senderType(ILyshraOpenAppSignal.SenderType.TIMER)
                .payload(Map.of("escalationLevel", escalationLevel))
                .createdAt(Instant.now())
                .build();

        signalService.sendSignal(signal).subscribe();
    }

    private String generateTimeoutId(String type, String identifier) {
        return type + "-" + identifier + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Data
    private static class ScheduledTimeout {
        private final String timeoutId;
        private final TimeoutType type;
        private final String taskId;
        private final String workflowInstanceId;
        private final Instant timeoutAt;
        private final int escalationLevel;
        private final ScheduledFuture<?> future;
    }

    private enum TimeoutType {
        TASK,
        WORKFLOW,
        ESCALATION
    }
}
