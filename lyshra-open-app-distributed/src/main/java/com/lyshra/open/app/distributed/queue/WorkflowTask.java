package com.lyshra.open.app.distributed.queue;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a workflow execution task in the distributed queue.
 *
 * A WorkflowTask encapsulates all the information needed to execute a workflow:
 * - Workflow identification (org, module, version, name)
 * - Execution context and input data
 * - Routing information (partition key)
 * - Scheduling and retry metadata
 *
 * Tasks are immutable once created, ensuring thread-safety across
 * distributed consumers.
 *
 * Serialization: Implements Serializable for transport across nodes.
 */
@Getter
@Builder(toBuilder = true)
@ToString
public class WorkflowTask implements Serializable, Comparable<WorkflowTask> {

    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier for this task.
     */
    private final String taskId;

    /**
     * The workflow execution key (used for deduplication and routing).
     */
    private final String executionKey;

    /**
     * Partition key for consistent routing to the same consumer.
     */
    private final String partitionKey;

    /**
     * The partition ID this task belongs to (computed from partition key).
     */
    private final int partitionId;

    // ========== Workflow Identification ==========

    /**
     * Organization that owns the workflow.
     */
    private final String organization;

    /**
     * Module containing the workflow.
     */
    private final String module;

    /**
     * Version of the workflow.
     */
    private final String version;

    /**
     * Name of the workflow.
     */
    private final String workflowName;

    /**
     * Unique execution ID for this invocation.
     */
    private final String executionId;

    // ========== Task Data ==========

    /**
     * The type of task operation.
     */
    private final TaskType taskType;

    /**
     * Serialized input data for the workflow.
     */
    private final byte[] inputData;

    /**
     * Additional metadata for the task.
     */
    private final Map<String, String> metadata;

    /**
     * Correlation ID for tracing across systems.
     */
    private final String correlationId;

    // ========== Scheduling ==========

    /**
     * Task priority (higher = more urgent).
     */
    @Builder.Default
    private final int priority = TaskPriority.NORMAL;

    /**
     * When the task was created/enqueued.
     */
    private final Instant createdAt;

    /**
     * Earliest time the task should be processed.
     */
    private final Instant scheduledAt;

    /**
     * Task expiration time (null = never expires).
     */
    private final Instant expiresAt;

    // ========== Retry Configuration ==========

    /**
     * Current retry attempt (0 = first attempt).
     */
    @Builder.Default
    private final int retryCount = 0;

    /**
     * Maximum retry attempts allowed.
     */
    @Builder.Default
    private final int maxRetries = 3;

    /**
     * Base delay between retries.
     */
    @Builder.Default
    private final Duration retryDelay = Duration.ofSeconds(30);

    /**
     * Whether to use exponential backoff for retries.
     */
    @Builder.Default
    private final boolean exponentialBackoff = true;

    // ========== Processing State ==========

    /**
     * ID of the node that last processed this task.
     */
    private final String lastProcessorNodeId;

    /**
     * When the task was last attempted.
     */
    private final Instant lastAttemptAt;

    /**
     * Error message from the last failed attempt.
     */
    private final String lastError;

    // ========== Factory Methods ==========

    /**
     * Creates a new task for workflow execution.
     *
     * @param executionKey the unique execution key
     * @param organization the organization
     * @param module the module
     * @param version the version
     * @param workflowName the workflow name
     * @param executionId the execution ID
     * @param inputData serialized input data
     * @return the new task
     */
    public static WorkflowTask forExecution(String executionKey,
                                             String organization,
                                             String module,
                                             String version,
                                             String workflowName,
                                             String executionId,
                                             byte[] inputData) {
        return WorkflowTask.builder()
                .taskId(UUID.randomUUID().toString())
                .executionKey(executionKey)
                .partitionKey(executionKey) // Use execution key as partition key by default
                .partitionId(-1) // Will be computed by the queue
                .organization(organization)
                .module(module)
                .version(version)
                .workflowName(workflowName)
                .executionId(executionId)
                .taskType(TaskType.EXECUTE)
                .inputData(inputData)
                .createdAt(Instant.now())
                .scheduledAt(Instant.now())
                .build();
    }

    /**
     * Creates a new task for workflow resumption.
     *
     * @param executionKey the execution key to resume
     * @return the resume task
     */
    public static WorkflowTask forResume(String executionKey) {
        return WorkflowTask.builder()
                .taskId(UUID.randomUUID().toString())
                .executionKey(executionKey)
                .partitionKey(executionKey)
                .partitionId(-1)
                .taskType(TaskType.RESUME)
                .createdAt(Instant.now())
                .scheduledAt(Instant.now())
                .build();
    }

    /**
     * Creates a new task for workflow cancellation.
     *
     * @param executionKey the execution key to cancel
     * @return the cancel task
     */
    public static WorkflowTask forCancel(String executionKey) {
        return WorkflowTask.builder()
                .taskId(UUID.randomUUID().toString())
                .executionKey(executionKey)
                .partitionKey(executionKey)
                .partitionId(-1)
                .taskType(TaskType.CANCEL)
                .priority(TaskPriority.HIGH) // Cancellations are high priority
                .createdAt(Instant.now())
                .scheduledAt(Instant.now())
                .build();
    }

    /**
     * Creates a retry task from a failed task.
     *
     * @return the retry task with incremented retry count
     */
    public WorkflowTask createRetry() {
        if (retryCount >= maxRetries) {
            throw new IllegalStateException("Maximum retries exceeded");
        }

        Duration nextDelay = exponentialBackoff
                ? retryDelay.multipliedBy((long) Math.pow(2, retryCount))
                : retryDelay;

        return this.toBuilder()
                .taskId(UUID.randomUUID().toString())
                .retryCount(retryCount + 1)
                .scheduledAt(Instant.now().plus(nextDelay))
                .lastAttemptAt(Instant.now())
                .build();
    }

    /**
     * Creates a retry task with an error message.
     *
     * @param error the error message
     * @return the retry task
     */
    public WorkflowTask createRetryWithError(String error) {
        return createRetry().toBuilder()
                .lastError(error)
                .build();
    }

    /**
     * Creates a copy with the partition ID set.
     *
     * @param partitionId the computed partition ID
     * @return task with partition ID
     */
    public WorkflowTask withPartitionId(int partitionId) {
        return this.toBuilder()
                .partitionId(partitionId)
                .build();
    }

    /**
     * Creates a copy with processor node ID set.
     *
     * @param nodeId the processing node ID
     * @return task with processor info
     */
    public WorkflowTask withProcessorNode(String nodeId) {
        return this.toBuilder()
                .lastProcessorNodeId(nodeId)
                .lastAttemptAt(Instant.now())
                .build();
    }

    // ========== Query Methods ==========

    /**
     * Checks if the task has expired.
     *
     * @return true if expired
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks if the task is ready to be processed.
     *
     * @return true if ready
     */
    public boolean isReady() {
        return !isExpired() &&
               (scheduledAt == null || !Instant.now().isBefore(scheduledAt));
    }

    /**
     * Checks if the task can be retried.
     *
     * @return true if retries remaining
     */
    public boolean canRetry() {
        return retryCount < maxRetries;
    }

    /**
     * Gets the time until the task is ready.
     *
     * @return duration until ready, or zero if ready now
     */
    public Duration getTimeUntilReady() {
        if (scheduledAt == null || Instant.now().isAfter(scheduledAt)) {
            return Duration.ZERO;
        }
        return Duration.between(Instant.now(), scheduledAt);
    }

    /**
     * Gets the workflow identifier string.
     *
     * @return the full workflow identifier
     */
    public String getWorkflowIdentifier() {
        return String.format("%s/%s/%s/%s", organization, module, version, workflowName);
    }

    // ========== Comparable Implementation ==========

    @Override
    public int compareTo(WorkflowTask other) {
        // Higher priority first
        int priorityCompare = Integer.compare(other.priority, this.priority);
        if (priorityCompare != 0) {
            return priorityCompare;
        }

        // Earlier scheduled time first
        if (this.scheduledAt != null && other.scheduledAt != null) {
            return this.scheduledAt.compareTo(other.scheduledAt);
        }

        // Earlier created time first
        if (this.createdAt != null && other.createdAt != null) {
            return this.createdAt.compareTo(other.createdAt);
        }

        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowTask that = (WorkflowTask) o;
        return Objects.equals(taskId, that.taskId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId);
    }

    // ========== Inner Classes ==========

    /**
     * Type of workflow task.
     */
    public enum TaskType {
        /**
         * Execute a new workflow.
         */
        EXECUTE,

        /**
         * Resume a paused or failed workflow.
         */
        RESUME,

        /**
         * Cancel a running workflow.
         */
        CANCEL,

        /**
         * Retry a failed workflow.
         */
        RETRY,

        /**
         * Signal/event to a waiting workflow.
         */
        SIGNAL,

        /**
         * Timeout notification.
         */
        TIMEOUT,

        /**
         * Recovery task for orphaned workflow.
         */
        RECOVERY
    }

    /**
     * Standard priority levels.
     */
    public static final class TaskPriority {
        public static final int LOWEST = 0;
        public static final int LOW = 25;
        public static final int NORMAL = 50;
        public static final int HIGH = 75;
        public static final int HIGHEST = 100;
        public static final int CRITICAL = 150;

        private TaskPriority() {}
    }
}
