package com.lyshra.open.app.distributed.queue;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Abstraction for a distributed task queue for workflow execution.
 *
 * This interface provides a consistent API for queueing workflow tasks
 * that can be backed by different implementations:
 * - In-memory (for testing)
 * - Redis (for single-datacenter deployments)
 * - Kafka (for high-throughput, multi-datacenter)
 * - AWS SQS, Azure Service Bus, etc.
 *
 * Key Features:
 * 1. Partitioned Queues - Tasks are partitioned by key for consistent routing
 * 2. Priority Support - Higher priority tasks are processed first
 * 3. Delayed Delivery - Tasks can be scheduled for future processing
 * 4. At-Least-Once Delivery - Tasks are redelivered if not acknowledged
 * 5. Dead Letter Queue - Failed tasks after max retries go to DLQ
 * 6. Visibility Timeout - Tasks are hidden while being processed
 *
 * Consumption Models:
 * - Pull-based: Consumer polls for tasks
 * - Push-based: Tasks are pushed to registered consumers (via Flux)
 *
 * Consistency Guarantees:
 * - Tasks with the same partition key are processed in order
 * - At-least-once delivery (tasks may be delivered multiple times)
 * - Exactly-once processing requires idempotent consumers
 *
 * Thread Safety: Implementations must be thread-safe.
 */
public interface IDistributedTaskQueue {

    // ========== Lifecycle ==========

    /**
     * Initializes the queue.
     *
     * @return Mono that completes when initialization is done
     */
    Mono<Void> initialize();

    /**
     * Shuts down the queue gracefully.
     *
     * @return Mono that completes when shutdown is done
     */
    Mono<Void> shutdown();

    /**
     * Checks if the queue is running.
     *
     * @return true if running
     */
    boolean isRunning();

    // ========== Enqueue Operations ==========

    /**
     * Enqueues a task for processing.
     *
     * @param task the task to enqueue
     * @return Mono containing the enqueued task with partition info
     */
    Mono<WorkflowTask> enqueue(WorkflowTask task);

    /**
     * Enqueues multiple tasks in a batch.
     *
     * @param tasks the tasks to enqueue
     * @return Mono containing the list of enqueued tasks
     */
    Mono<List<WorkflowTask>> enqueueBatch(List<WorkflowTask> tasks);

    /**
     * Enqueues a task with a delay.
     *
     * @param task the task to enqueue
     * @param delay the delay before the task becomes visible
     * @return Mono containing the enqueued task
     */
    default Mono<WorkflowTask> enqueueDelayed(WorkflowTask task, Duration delay) {
        WorkflowTask delayedTask = task.toBuilder()
                .scheduledAt(Instant.now().plus(delay))
                .build();
        return enqueue(delayedTask);
    }

    /**
     * Enqueues a task scheduled for a specific time.
     *
     * @param task the task to enqueue
     * @param scheduledTime when the task should become visible
     * @return Mono containing the enqueued task
     */
    default Mono<WorkflowTask> enqueueScheduled(WorkflowTask task, Instant scheduledTime) {
        WorkflowTask scheduledTask = task.toBuilder()
                .scheduledAt(scheduledTime)
                .build();
        return enqueue(scheduledTask);
    }

    // ========== Dequeue Operations (Pull Model) ==========

    /**
     * Polls for the next available task.
     *
     * The task is marked as "in-flight" and will not be returned to other
     * consumers until acknowledged or the visibility timeout expires.
     *
     * @return Mono containing the next task, or empty if queue is empty
     */
    Mono<Optional<WorkflowTask>> poll();

    /**
     * Polls for the next available task with a wait timeout.
     *
     * @param timeout maximum time to wait for a task
     * @return Mono containing the next task, or empty if timeout
     */
    Mono<Optional<WorkflowTask>> poll(Duration timeout);

    /**
     * Polls for tasks from a specific partition.
     *
     * @param partitionId the partition to poll from
     * @return Mono containing the next task from that partition
     */
    Mono<Optional<WorkflowTask>> pollPartition(int partitionId);

    /**
     * Polls for a batch of tasks.
     *
     * @param maxTasks maximum number of tasks to return
     * @return Mono containing list of tasks
     */
    Mono<List<WorkflowTask>> pollBatch(int maxTasks);

    /**
     * Polls for a batch of tasks from specific partitions.
     *
     * @param partitionIds the partitions to poll from
     * @param maxTasks maximum number of tasks per partition
     * @return Mono containing list of tasks
     */
    Mono<List<WorkflowTask>> pollPartitions(Set<Integer> partitionIds, int maxTasks);

    // ========== Streaming Consumption (Push Model) ==========

    /**
     * Subscribes to tasks from the queue.
     *
     * Returns a Flux that emits tasks as they become available.
     * Tasks must be acknowledged after processing.
     *
     * @return Flux of tasks
     */
    Flux<WorkflowTask> subscribe();

    /**
     * Subscribes to tasks from specific partitions.
     *
     * @param partitionIds the partitions to subscribe to
     * @return Flux of tasks from those partitions
     */
    Flux<WorkflowTask> subscribeToPartitions(Set<Integer> partitionIds);

    /**
     * Subscribes to tasks with a specific type.
     *
     * @param taskType the task type to filter for
     * @return Flux of tasks with that type
     */
    Flux<WorkflowTask> subscribeByType(WorkflowTask.TaskType taskType);

    // ========== Acknowledgment ==========

    /**
     * Acknowledges successful processing of a task.
     *
     * This removes the task from the queue permanently.
     *
     * @param taskId the task ID to acknowledge
     * @return Mono containing true if acknowledged
     */
    Mono<Boolean> acknowledge(String taskId);

    /**
     * Acknowledges multiple tasks in a batch.
     *
     * @param taskIds the task IDs to acknowledge
     * @return Mono containing the number of tasks acknowledged
     */
    Mono<Integer> acknowledgeBatch(List<String> taskIds);

    /**
     * Negatively acknowledges a task (processing failed).
     *
     * The task will be requeued for retry if retries remain,
     * or moved to the dead letter queue if max retries exceeded.
     *
     * @param taskId the task ID
     * @param reason the failure reason
     * @return Mono containing true if nacked
     */
    Mono<Boolean> nack(String taskId, String reason);

    /**
     * Releases a task back to the queue immediately.
     *
     * Use this when the consumer cannot process the task right now
     * but wants it to be available for other consumers.
     *
     * @param taskId the task ID to release
     * @return Mono containing true if released
     */
    Mono<Boolean> release(String taskId);

    /**
     * Extends the visibility timeout for a task.
     *
     * Use this for long-running tasks to prevent redelivery.
     *
     * @param taskId the task ID
     * @param extension the additional time to hide the task
     * @return Mono containing true if extended
     */
    Mono<Boolean> extendVisibility(String taskId, Duration extension);

    // ========== Task Lookup ==========

    /**
     * Gets a task by its ID.
     *
     * @param taskId the task ID
     * @return Mono containing the task if found
     */
    Mono<Optional<WorkflowTask>> getTask(String taskId);

    /**
     * Gets a task by its execution key.
     *
     * @param executionKey the workflow execution key
     * @return Mono containing the task if found
     */
    Mono<Optional<WorkflowTask>> getTaskByExecutionKey(String executionKey);

    /**
     * Checks if a task exists in the queue.
     *
     * @param taskId the task ID
     * @return Mono containing true if exists
     */
    Mono<Boolean> exists(String taskId);

    /**
     * Checks if a task exists for an execution key.
     *
     * @param executionKey the workflow execution key
     * @return Mono containing true if exists
     */
    Mono<Boolean> existsByExecutionKey(String executionKey);

    // ========== Task Management ==========

    /**
     * Removes a task from the queue.
     *
     * @param taskId the task ID to remove
     * @return Mono containing true if removed
     */
    Mono<Boolean> remove(String taskId);

    /**
     * Removes all tasks for an execution key.
     *
     * @param executionKey the workflow execution key
     * @return Mono containing the number of tasks removed
     */
    Mono<Integer> removeByExecutionKey(String executionKey);

    /**
     * Clears all tasks from the queue.
     *
     * WARNING: This is destructive and should only be used in testing.
     *
     * @return Mono that completes when cleared
     */
    Mono<Void> clear();

    /**
     * Purges expired tasks from the queue.
     *
     * @return Mono containing the number of tasks purged
     */
    Mono<Integer> purgeExpired();

    // ========== Dead Letter Queue ==========

    /**
     * Gets tasks from the dead letter queue.
     *
     * @param limit maximum number of tasks to return
     * @return Flux of dead letter tasks
     */
    Flux<WorkflowTask> getDeadLetterTasks(int limit);

    /**
     * Requeues a task from the dead letter queue.
     *
     * @param taskId the task ID to requeue
     * @return Mono containing true if requeued
     */
    Mono<Boolean> requeueFromDeadLetter(String taskId);

    /**
     * Clears the dead letter queue.
     *
     * @return Mono containing the number of tasks cleared
     */
    Mono<Integer> clearDeadLetterQueue();

    // ========== Queue Statistics ==========

    /**
     * Gets the queue metrics.
     *
     * @return the current metrics
     */
    QueueMetrics getMetrics();

    /**
     * Gets the queue size (pending tasks).
     *
     * @return Mono containing the queue size
     */
    Mono<Long> getQueueSize();

    /**
     * Gets the number of in-flight tasks.
     *
     * @return Mono containing the in-flight count
     */
    Mono<Long> getInFlightCount();

    /**
     * Gets the dead letter queue size.
     *
     * @return Mono containing the DLQ size
     */
    Mono<Long> getDeadLetterQueueSize();

    /**
     * Gets the queue size for a specific partition.
     *
     * @param partitionId the partition ID
     * @return Mono containing the partition queue size
     */
    Mono<Long> getPartitionQueueSize(int partitionId);

    // ========== Configuration ==========

    /**
     * Gets the total number of partitions.
     *
     * @return the partition count
     */
    int getPartitionCount();

    /**
     * Gets the visibility timeout duration.
     *
     * @return the visibility timeout
     */
    Duration getVisibilityTimeout();

    /**
     * Sets the visibility timeout duration.
     *
     * @param timeout the new visibility timeout
     */
    void setVisibilityTimeout(Duration timeout);

    // ========== Inner Interfaces and Classes ==========

    /**
     * Queue metrics for monitoring.
     */
    interface QueueMetrics {
        /**
         * Total tasks enqueued.
         */
        long getTotalEnqueued();

        /**
         * Total tasks dequeued (polled).
         */
        long getTotalDequeued();

        /**
         * Total tasks acknowledged.
         */
        long getTotalAcknowledged();

        /**
         * Total tasks negatively acknowledged.
         */
        long getTotalNacked();

        /**
         * Total tasks that expired.
         */
        long getTotalExpired();

        /**
         * Total tasks moved to dead letter queue.
         */
        long getTotalDeadLettered();

        /**
         * Current pending task count.
         */
        long getPendingCount();

        /**
         * Current in-flight task count.
         */
        long getInFlightCount();

        /**
         * Average processing time in milliseconds.
         */
        double getAverageProcessingTimeMs();

        /**
         * Average wait time in queue in milliseconds.
         */
        double getAverageWaitTimeMs();
    }

    /**
     * Exception thrown when queue operations fail.
     */
    class QueueException extends RuntimeException {
        public QueueException(String message) {
            super(message);
        }

        public QueueException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when a task is not found.
     */
    class TaskNotFoundException extends QueueException {
        private final String taskId;

        public TaskNotFoundException(String taskId) {
            super("Task not found: " + taskId);
            this.taskId = taskId;
        }

        public String getTaskId() {
            return taskId;
        }
    }

    /**
     * Exception thrown when a duplicate task is enqueued.
     */
    class DuplicateTaskException extends QueueException {
        private final String executionKey;

        public DuplicateTaskException(String executionKey) {
            super("Duplicate task for execution: " + executionKey);
            this.executionKey = executionKey;
        }

        public String getExecutionKey() {
            return executionKey;
        }
    }
}
