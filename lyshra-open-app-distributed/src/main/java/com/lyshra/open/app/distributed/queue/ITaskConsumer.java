package com.lyshra.open.app.distributed.queue;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Set;
import java.util.function.Function;

/**
 * Interface for consuming tasks from a distributed queue.
 *
 * Task consumers are responsible for:
 * - Subscribing to tasks from assigned partitions
 * - Processing tasks and acknowledging completion
 * - Handling failures and retries
 * - Managing backpressure
 *
 * Consumption Patterns:
 * 1. Pull-based: Consumer polls for tasks
 * 2. Push-based: Tasks are pushed via Flux subscription
 * 3. Callback-based: Consumer registers a handler function
 *
 * Partition Assignment:
 * Consumers can be assigned specific partitions to ensure
 * consistent ordering and locality of processing.
 *
 * Thread Safety: Implementations must be thread-safe.
 */
public interface ITaskConsumer {

    // ========== Lifecycle ==========

    /**
     * Starts the consumer.
     *
     * @return Mono that completes when started
     */
    Mono<Void> start();

    /**
     * Stops the consumer gracefully.
     *
     * @return Mono that completes when stopped
     */
    Mono<Void> stop();

    /**
     * Checks if the consumer is running.
     *
     * @return true if running
     */
    boolean isRunning();

    // ========== Partition Assignment ==========

    /**
     * Gets the partitions assigned to this consumer.
     *
     * @return set of assigned partition IDs
     */
    Set<Integer> getAssignedPartitions();

    /**
     * Assigns partitions to this consumer.
     *
     * @param partitionIds the partitions to assign
     */
    void assignPartitions(Set<Integer> partitionIds);

    /**
     * Revokes partitions from this consumer.
     *
     * @param partitionIds the partitions to revoke
     */
    void revokePartitions(Set<Integer> partitionIds);

    // ========== Consumption ==========

    /**
     * Subscribes to tasks and processes them with the given handler.
     *
     * The handler should return a Mono that completes when processing is done.
     * Successful completion triggers acknowledgment, errors trigger nack.
     *
     * @param handler the task processing handler
     * @return Flux of processing results
     */
    Flux<TaskProcessingResult> consume(Function<WorkflowTask, Mono<Void>> handler);

    /**
     * Subscribes to tasks with more control over acknowledgment.
     *
     * @return Flux of tasks with ack/nack handles
     */
    Flux<ConsumableTask> consumeWithHandle();

    /**
     * Polls for a single task.
     *
     * @return Mono containing the next task
     */
    Mono<ConsumableTask> pollOne();

    /**
     * Polls for a batch of tasks.
     *
     * @param maxTasks maximum tasks to return
     * @return Flux of consumable tasks
     */
    Flux<ConsumableTask> pollBatch(int maxTasks);

    // ========== Configuration ==========

    /**
     * Gets the consumer ID.
     *
     * @return the consumer ID
     */
    String getConsumerId();

    /**
     * Gets the consumer group ID.
     *
     * @return the consumer group ID
     */
    String getConsumerGroupId();

    /**
     * Gets the maximum concurrent tasks this consumer can process.
     *
     * @return the concurrency limit
     */
    int getMaxConcurrency();

    /**
     * Sets the maximum concurrent tasks.
     *
     * @param maxConcurrency the new limit
     */
    void setMaxConcurrency(int maxConcurrency);

    // ========== Metrics ==========

    /**
     * Gets consumer metrics.
     *
     * @return the metrics
     */
    ConsumerMetrics getMetrics();

    // ========== Inner Types ==========

    /**
     * A task with acknowledgment handles.
     */
    interface ConsumableTask {
        /**
         * Gets the task.
         */
        WorkflowTask getTask();

        /**
         * Acknowledges successful processing.
         *
         * @return Mono that completes when acknowledged
         */
        Mono<Void> acknowledge();

        /**
         * Negatively acknowledges (processing failed).
         *
         * @param reason the failure reason
         * @return Mono that completes when nacked
         */
        Mono<Void> nack(String reason);

        /**
         * Extends the visibility timeout.
         *
         * @param extension additional time
         * @return Mono that completes when extended
         */
        Mono<Void> extendVisibility(Duration extension);

        /**
         * Releases the task back to the queue.
         *
         * @return Mono that completes when released
         */
        Mono<Void> release();
    }

    /**
     * Result of task processing.
     */
    interface TaskProcessingResult {
        /**
         * Gets the task that was processed.
         */
        WorkflowTask getTask();

        /**
         * Whether processing was successful.
         */
        boolean isSuccess();

        /**
         * Gets the error if processing failed.
         */
        Throwable getError();

        /**
         * Gets the processing duration.
         */
        Duration getProcessingTime();
    }

    /**
     * Consumer metrics.
     */
    interface ConsumerMetrics {
        /**
         * Total tasks consumed.
         */
        long getTotalConsumed();

        /**
         * Total tasks successfully processed.
         */
        long getTotalSucceeded();

        /**
         * Total tasks failed.
         */
        long getTotalFailed();

        /**
         * Current in-progress tasks.
         */
        long getInProgressCount();

        /**
         * Average processing time in milliseconds.
         */
        double getAverageProcessingTimeMs();
    }
}
