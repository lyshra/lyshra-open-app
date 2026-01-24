package com.lyshra.open.app.core.engine.timeout;

import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTask;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowInstance;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Service interface for scheduling and managing timeouts.
 * Responsible for triggering timeout signals when human tasks or workflows
 * have been waiting too long.
 *
 * <p>Design Pattern: Observer Pattern (Timer-based)
 * - Monitors registered timeouts
 * - Notifies the signal service when timeouts occur
 *
 * <p>Implementations should be:
 * - Distributed-aware (work correctly in clustered environments)
 * - Durable (survive restarts)
 * - Accurate (handle clock drift and timezone issues)
 */
public interface ILyshraOpenAppTimeoutScheduler {

    /**
     * Schedules a timeout for a human task.
     *
     * @param task the human task
     * @param timeout the duration after which timeout signal should be sent
     * @return the scheduled timeout identifier
     */
    Mono<String> scheduleTaskTimeout(ILyshraOpenAppHumanTask task, Duration timeout);

    /**
     * Schedules a timeout for a human task at a specific instant.
     *
     * @param task the human task
     * @param timeoutAt when the timeout should trigger
     * @return the scheduled timeout identifier
     */
    Mono<String> scheduleTaskTimeout(ILyshraOpenAppHumanTask task, Instant timeoutAt);

    /**
     * Schedules a timeout for a workflow instance.
     *
     * @param instance the workflow instance
     * @param timeout the duration after which timeout signal should be sent
     * @return the scheduled timeout identifier
     */
    Mono<String> scheduleWorkflowTimeout(ILyshraOpenAppWorkflowInstance instance, Duration timeout);

    /**
     * Schedules an escalation timeout for a human task.
     * Escalation timeouts have different behavior - they trigger escalation
     * rather than failing the task.
     *
     * @param task the human task
     * @param timeout the duration after which escalation should be triggered
     * @param escalationLevel the escalation level being scheduled
     * @return the scheduled timeout identifier
     */
    Mono<String> scheduleEscalationTimeout(ILyshraOpenAppHumanTask task, Duration timeout, int escalationLevel);

    /**
     * Cancels a previously scheduled timeout.
     *
     * @param timeoutId the timeout identifier
     * @return true if the timeout was cancelled, false if not found
     */
    Mono<Boolean> cancelTimeout(String timeoutId);

    /**
     * Cancels all timeouts for a specific task.
     *
     * @param taskId the task identifier
     * @return count of cancelled timeouts
     */
    Mono<Integer> cancelTaskTimeouts(String taskId);

    /**
     * Cancels all timeouts for a specific workflow instance.
     *
     * @param workflowInstanceId the workflow instance identifier
     * @return count of cancelled timeouts
     */
    Mono<Integer> cancelWorkflowTimeouts(String workflowInstanceId);

    /**
     * Extends a timeout by additional duration.
     *
     * @param timeoutId the timeout identifier
     * @param additionalTime the additional duration to add
     * @return the new timeout time
     */
    Mono<Instant> extendTimeout(String timeoutId, Duration additionalTime);

    /**
     * Checks if a timeout exists and is still active.
     *
     * @param timeoutId the timeout identifier
     * @return true if the timeout is active
     */
    Mono<Boolean> isTimeoutActive(String timeoutId);

    /**
     * Gets the remaining time until a timeout triggers.
     *
     * @param timeoutId the timeout identifier
     * @return the remaining duration, or Duration.ZERO if expired
     */
    Mono<Duration> getRemainingTime(String timeoutId);

    /**
     * Starts the timeout scheduler.
     * Called during application startup.
     */
    void start();

    /**
     * Stops the timeout scheduler.
     * Called during application shutdown.
     */
    void stop();

    /**
     * Recovers timeouts after a restart.
     * Loads active timeouts from persistent storage and reschedules them.
     *
     * @return count of recovered timeouts
     */
    Mono<Integer> recoverTimeouts();
}
