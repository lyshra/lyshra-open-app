package com.lyshra.open.app.distributed.queue;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Interface for producing tasks to a distributed queue.
 *
 * Task producers are responsible for:
 * - Creating and enqueuing workflow tasks
 * - Handling partitioning and routing
 * - Supporting scheduled/delayed delivery
 * - Providing send confirmations
 *
 * Usage Patterns:
 * 1. Fire-and-forget: Enqueue without waiting for confirmation
 * 2. Synchronous: Wait for enqueue confirmation
 * 3. Batched: Enqueue multiple tasks efficiently
 *
 * Thread Safety: Implementations must be thread-safe.
 */
public interface ITaskProducer {

    // ========== Lifecycle ==========

    /**
     * Initializes the producer.
     *
     * @return Mono that completes when initialized
     */
    Mono<Void> initialize();

    /**
     * Shuts down the producer.
     *
     * @return Mono that completes when shut down
     */
    Mono<Void> shutdown();

    /**
     * Checks if the producer is ready.
     *
     * @return true if ready to produce
     */
    boolean isReady();

    // ========== Basic Send Operations ==========

    /**
     * Sends a task to the queue.
     *
     * @param task the task to send
     * @return Mono containing the send result
     */
    Mono<SendResult> send(WorkflowTask task);

    /**
     * Sends multiple tasks in a batch.
     *
     * @param tasks the tasks to send
     * @return Mono containing the batch send result
     */
    Mono<BatchSendResult> sendBatch(List<WorkflowTask> tasks);

    /**
     * Sends a task and waits for acknowledgment.
     *
     * @param task the task to send
     * @return Mono containing the confirmed send result
     */
    Mono<SendResult> sendAndConfirm(WorkflowTask task);

    // ========== Workflow Task Factory Methods ==========

    /**
     * Creates and sends a workflow execution task.
     *
     * @param executionKey the unique execution key
     * @param organization the organization
     * @param module the module
     * @param version the version
     * @param workflowName the workflow name
     * @param executionId the execution ID
     * @param inputData serialized input data
     * @return Mono containing the send result
     */
    default Mono<SendResult> sendExecution(String executionKey,
                                            String organization,
                                            String module,
                                            String version,
                                            String workflowName,
                                            String executionId,
                                            byte[] inputData) {
        WorkflowTask task = WorkflowTask.forExecution(
                executionKey, organization, module, version, workflowName, executionId, inputData);
        return send(task);
    }

    /**
     * Creates and sends a workflow resume task.
     *
     * @param executionKey the execution key to resume
     * @return Mono containing the send result
     */
    default Mono<SendResult> sendResume(String executionKey) {
        return send(WorkflowTask.forResume(executionKey));
    }

    /**
     * Creates and sends a workflow cancel task.
     *
     * @param executionKey the execution key to cancel
     * @return Mono containing the send result
     */
    default Mono<SendResult> sendCancel(String executionKey) {
        return send(WorkflowTask.forCancel(executionKey));
    }

    // ========== Scheduled/Delayed Send ==========

    /**
     * Sends a task with a delay.
     *
     * @param task the task to send
     * @param delay the delay before processing
     * @return Mono containing the send result
     */
    Mono<SendResult> sendDelayed(WorkflowTask task, Duration delay);

    /**
     * Sends a task scheduled for a specific time.
     *
     * @param task the task to send
     * @param scheduledTime when to process the task
     * @return Mono containing the send result
     */
    Mono<SendResult> sendScheduled(WorkflowTask task, Instant scheduledTime);

    // ========== Priority Send ==========

    /**
     * Sends a high-priority task.
     *
     * @param task the task to send
     * @return Mono containing the send result
     */
    default Mono<SendResult> sendHighPriority(WorkflowTask task) {
        WorkflowTask highPriorityTask = task.toBuilder()
                .priority(WorkflowTask.TaskPriority.HIGH)
                .build();
        return send(highPriorityTask);
    }

    /**
     * Sends a critical-priority task.
     *
     * @param task the task to send
     * @return Mono containing the send result
     */
    default Mono<SendResult> sendCritical(WorkflowTask task) {
        WorkflowTask criticalTask = task.toBuilder()
                .priority(WorkflowTask.TaskPriority.CRITICAL)
                .build();
        return send(criticalTask);
    }

    // ========== Configuration ==========

    /**
     * Gets the producer ID.
     *
     * @return the producer ID
     */
    String getProducerId();

    /**
     * Gets producer metrics.
     *
     * @return the metrics
     */
    ProducerMetrics getMetrics();

    // ========== Inner Types ==========

    /**
     * Result of sending a task.
     */
    interface SendResult {
        /**
         * Whether the send was successful.
         */
        boolean isSuccess();

        /**
         * Gets the task that was sent.
         */
        WorkflowTask getTask();

        /**
         * Gets the partition the task was sent to.
         */
        int getPartitionId();

        /**
         * Gets the error if send failed.
         */
        Throwable getError();

        /**
         * Creates a successful send result.
         */
        static SendResult success(WorkflowTask task) {
            return new SendResult() {
                @Override public boolean isSuccess() { return true; }
                @Override public WorkflowTask getTask() { return task; }
                @Override public int getPartitionId() { return task.getPartitionId(); }
                @Override public Throwable getError() { return null; }
            };
        }

        /**
         * Creates a failed send result.
         */
        static SendResult failure(WorkflowTask task, Throwable error) {
            return new SendResult() {
                @Override public boolean isSuccess() { return false; }
                @Override public WorkflowTask getTask() { return task; }
                @Override public int getPartitionId() { return task.getPartitionId(); }
                @Override public Throwable getError() { return error; }
            };
        }
    }

    /**
     * Result of sending a batch of tasks.
     */
    interface BatchSendResult {
        /**
         * Gets all individual results.
         */
        List<SendResult> getResults();

        /**
         * Gets only successful results.
         */
        List<SendResult> getSuccessful();

        /**
         * Gets only failed results.
         */
        List<SendResult> getFailed();

        /**
         * Whether all sends were successful.
         */
        default boolean isAllSuccess() {
            return getFailed().isEmpty();
        }

        /**
         * Gets the success count.
         */
        default int getSuccessCount() {
            return getSuccessful().size();
        }

        /**
         * Gets the failure count.
         */
        default int getFailureCount() {
            return getFailed().size();
        }
    }

    /**
     * Producer metrics.
     */
    interface ProducerMetrics {
        /**
         * Total tasks sent.
         */
        long getTotalSent();

        /**
         * Total successful sends.
         */
        long getTotalSucceeded();

        /**
         * Total failed sends.
         */
        long getTotalFailed();

        /**
         * Average send latency in milliseconds.
         */
        double getAverageSendLatencyMs();

        /**
         * Tasks sent per partition.
         */
        Map<Integer, Long> getTasksPerPartition();
    }
}
