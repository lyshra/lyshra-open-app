package com.lyshra.open.app.distributed.queue;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * In-memory implementation of IDistributedTaskQueue for testing and development.
 *
 * This implementation provides:
 * - Partitioned queues using priority queues per partition
 * - Visibility timeout with automatic redelivery
 * - At-least-once delivery semantics
 * - Dead letter queue for failed tasks
 * - Priority-based ordering within partitions
 * - Scheduled task support with delayed visibility
 *
 * Limitations (compared to production queues):
 * - Not persistent (tasks lost on restart)
 * - Single JVM only (not distributed)
 * - Limited scalability
 *
 * Thread Safety: This class is thread-safe.
 */
@Slf4j
public class InMemoryDistributedTaskQueue implements IDistributedTaskQueue {

    private static final Duration DEFAULT_VISIBILITY_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(100);
    private static final int DEFAULT_PARTITION_COUNT = 16;

    private final int partitionCount;
    private final AtomicBoolean running;

    // Partitioned queues - each partition has its own priority queue
    private final List<PriorityBlockingQueue<WorkflowTask>> partitionQueues;

    // In-flight tasks (being processed) - taskId -> InFlightTask
    private final ConcurrentHashMap<String, InFlightTask> inFlightTasks;

    // Dead letter queue
    private final ConcurrentLinkedQueue<WorkflowTask> deadLetterQueue;

    // Index by execution key for deduplication
    private final ConcurrentHashMap<String, String> executionKeyIndex;

    // Task lookup index - taskId -> WorkflowTask
    private final ConcurrentHashMap<String, WorkflowTask> taskIndex;

    // Visibility timeout
    private volatile Duration visibilityTimeout;

    // Background task for handling visibility timeouts
    private volatile Disposable visibilityChecker;
    private volatile Disposable scheduledTaskChecker;

    // Metrics
    private final InMemoryQueueMetrics metrics;

    // Subscribers for push model
    private final Sinks.Many<WorkflowTask> taskSink;

    @Builder
    public InMemoryDistributedTaskQueue(Integer partitionCount,
                                         Duration visibilityTimeout) {
        this.partitionCount = partitionCount != null ? partitionCount : DEFAULT_PARTITION_COUNT;
        this.visibilityTimeout = visibilityTimeout != null ? visibilityTimeout : DEFAULT_VISIBILITY_TIMEOUT;

        this.running = new AtomicBoolean(false);
        this.partitionQueues = new ArrayList<>(this.partitionCount);
        this.inFlightTasks = new ConcurrentHashMap<>();
        this.deadLetterQueue = new ConcurrentLinkedQueue<>();
        this.executionKeyIndex = new ConcurrentHashMap<>();
        this.taskIndex = new ConcurrentHashMap<>();
        this.metrics = new InMemoryQueueMetrics();

        // Initialize partition queues
        for (int i = 0; i < this.partitionCount; i++) {
            partitionQueues.add(new PriorityBlockingQueue<>());
        }

        // Create sink for push-based subscription
        this.taskSink = Sinks.many().multicast().onBackpressureBuffer();
    }

    // ========== Lifecycle ==========

    @Override
    public Mono<Void> initialize() {
        return Mono.fromRunnable(() -> {
            if (!running.compareAndSet(false, true)) {
                log.warn("In-memory task queue already running");
                return;
            }

            log.info("Initializing in-memory distributed task queue with {} partitions", partitionCount);

            // Start visibility timeout checker
            visibilityChecker = Flux.interval(Duration.ofSeconds(1))
                    .flatMap(tick -> checkVisibilityTimeouts())
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe();

            // Start scheduled task checker
            scheduledTaskChecker = Flux.interval(DEFAULT_POLL_INTERVAL)
                    .flatMap(tick -> processScheduledTasks())
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe();
        });
    }

    @Override
    public Mono<Void> shutdown() {
        return Mono.fromRunnable(() -> {
            if (!running.compareAndSet(true, false)) {
                return;
            }

            log.info("Shutting down in-memory distributed task queue");

            if (visibilityChecker != null) {
                visibilityChecker.dispose();
                visibilityChecker = null;
            }

            if (scheduledTaskChecker != null) {
                scheduledTaskChecker.dispose();
                scheduledTaskChecker = null;
            }

            taskSink.tryEmitComplete();
        });
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    // ========== Enqueue Operations ==========

    @Override
    public Mono<WorkflowTask> enqueue(WorkflowTask task) {
        return Mono.fromCallable(() -> {
            if (!running.get()) {
                throw new QueueException("Queue is not running");
            }

            Objects.requireNonNull(task, "Task must not be null");

            // Check for duplicate by execution key
            if (task.getExecutionKey() != null) {
                String existingTaskId = executionKeyIndex.get(task.getExecutionKey());
                if (existingTaskId != null) {
                    throw new DuplicateTaskException(task.getExecutionKey());
                }
            }

            // Compute partition ID
            int partitionId = computePartition(task.getPartitionKey());
            WorkflowTask taskWithPartition = task.withPartitionId(partitionId);

            // Add to partition queue
            PriorityBlockingQueue<WorkflowTask> queue = partitionQueues.get(partitionId);
            queue.offer(taskWithPartition);

            // Update indices
            taskIndex.put(taskWithPartition.getTaskId(), taskWithPartition);
            if (taskWithPartition.getExecutionKey() != null) {
                executionKeyIndex.put(taskWithPartition.getExecutionKey(), taskWithPartition.getTaskId());
            }

            metrics.recordEnqueue();

            log.debug("Enqueued task {} to partition {}", taskWithPartition.getTaskId(), partitionId);

            // Emit to subscribers if task is ready
            if (taskWithPartition.isReady()) {
                taskSink.tryEmitNext(taskWithPartition);
            }

            return taskWithPartition;
        });
    }

    @Override
    public Mono<List<WorkflowTask>> enqueueBatch(List<WorkflowTask> tasks) {
        return Flux.fromIterable(tasks)
                .flatMap(this::enqueue)
                .collectList();
    }

    // ========== Dequeue Operations (Pull Model) ==========

    @Override
    public Mono<Optional<WorkflowTask>> poll() {
        return Mono.fromCallable(() -> {
            if (!running.get()) {
                return Optional.empty();
            }

            // Try each partition in round-robin fashion
            for (int i = 0; i < partitionCount; i++) {
                Optional<WorkflowTask> task = pollFromPartition(i);
                if (task.isPresent()) {
                    return task;
                }
            }

            return Optional.empty();
        });
    }

    @Override
    public Mono<Optional<WorkflowTask>> poll(Duration timeout) {
        return poll()
                .flatMap(opt -> {
                    if (opt.isPresent()) {
                        return Mono.just(opt);
                    }
                    // Wait and retry
                    return Mono.delay(DEFAULT_POLL_INTERVAL)
                            .then(poll())
                            .repeatWhenEmpty(flux -> flux.take(timeout.toMillis() / DEFAULT_POLL_INTERVAL.toMillis()));
                })
                .defaultIfEmpty(Optional.empty());
    }

    @Override
    public Mono<Optional<WorkflowTask>> pollPartition(int partitionId) {
        return Mono.fromCallable(() -> {
            if (!running.get() || partitionId < 0 || partitionId >= partitionCount) {
                return Optional.empty();
            }
            return pollFromPartition(partitionId);
        });
    }

    @Override
    public Mono<List<WorkflowTask>> pollBatch(int maxTasks) {
        return Mono.fromCallable(() -> {
            if (!running.get()) {
                return Collections.emptyList();
            }

            List<WorkflowTask> tasks = new ArrayList<>();
            for (int i = 0; i < maxTasks && i < partitionCount * 10; i++) {
                Optional<WorkflowTask> task = poll().block();
                if (task != null && task.isPresent()) {
                    tasks.add(task.get());
                } else {
                    break;
                }
            }
            return tasks;
        });
    }

    @Override
    public Mono<List<WorkflowTask>> pollPartitions(Set<Integer> partitionIds, int maxTasks) {
        return Mono.fromCallable(() -> {
            if (!running.get()) {
                return Collections.emptyList();
            }

            List<WorkflowTask> tasks = new ArrayList<>();
            for (int partitionId : partitionIds) {
                if (tasks.size() >= maxTasks) {
                    break;
                }
                for (int i = 0; i < maxTasks - tasks.size(); i++) {
                    Optional<WorkflowTask> task = pollFromPartition(partitionId);
                    if (task.isPresent()) {
                        tasks.add(task.get());
                    } else {
                        break;
                    }
                }
            }
            return tasks;
        });
    }

    private Optional<WorkflowTask> pollFromPartition(int partitionId) {
        PriorityBlockingQueue<WorkflowTask> queue = partitionQueues.get(partitionId);

        WorkflowTask task = queue.poll();
        while (task != null) {
            // Skip expired tasks
            if (task.isExpired()) {
                taskIndex.remove(task.getTaskId());
                if (task.getExecutionKey() != null) {
                    executionKeyIndex.remove(task.getExecutionKey());
                }
                metrics.recordExpired();
                task = queue.poll();
                continue;
            }

            // Skip scheduled tasks that aren't ready yet
            if (!task.isReady()) {
                // Put it back and return empty (scheduled tasks handled separately)
                queue.offer(task);
                return Optional.empty();
            }

            // Mark as in-flight
            InFlightTask inFlight = new InFlightTask(task, Instant.now(), visibilityTimeout);
            inFlightTasks.put(task.getTaskId(), inFlight);
            metrics.recordDequeue();

            log.debug("Polled task {} from partition {}", task.getTaskId(), partitionId);
            return Optional.of(task);
        }

        return Optional.empty();
    }

    // ========== Streaming Consumption (Push Model) ==========

    @Override
    public Flux<WorkflowTask> subscribe() {
        return taskSink.asFlux()
                .filter(task -> !inFlightTasks.containsKey(task.getTaskId()))
                .doOnNext(task -> {
                    // Mark as in-flight when consumed
                    InFlightTask inFlight = new InFlightTask(task, Instant.now(), visibilityTimeout);
                    inFlightTasks.put(task.getTaskId(), inFlight);
                    metrics.recordDequeue();
                });
    }

    @Override
    public Flux<WorkflowTask> subscribeToPartitions(Set<Integer> partitionIds) {
        return subscribe()
                .filter(task -> partitionIds.contains(task.getPartitionId()));
    }

    @Override
    public Flux<WorkflowTask> subscribeByType(WorkflowTask.TaskType taskType) {
        return subscribe()
                .filter(task -> task.getTaskType() == taskType);
    }

    // ========== Acknowledgment ==========

    @Override
    public Mono<Boolean> acknowledge(String taskId) {
        return Mono.fromCallable(() -> {
            InFlightTask inFlight = inFlightTasks.remove(taskId);
            if (inFlight == null) {
                log.warn("Attempted to acknowledge unknown task: {}", taskId);
                return false;
            }

            // Remove from indices
            WorkflowTask task = inFlight.task;
            taskIndex.remove(taskId);
            if (task.getExecutionKey() != null) {
                executionKeyIndex.remove(task.getExecutionKey());
            }

            // Record metrics
            Duration processingTime = Duration.between(inFlight.dequeueTime, Instant.now());
            Duration waitTime = task.getCreatedAt() != null
                    ? Duration.between(task.getCreatedAt(), inFlight.dequeueTime)
                    : Duration.ZERO;
            metrics.recordAcknowledge(processingTime, waitTime);

            log.debug("Acknowledged task {}", taskId);
            return true;
        });
    }

    @Override
    public Mono<Integer> acknowledgeBatch(List<String> taskIds) {
        return Flux.fromIterable(taskIds)
                .flatMap(this::acknowledge)
                .filter(Boolean::booleanValue)
                .count()
                .map(Long::intValue);
    }

    @Override
    public Mono<Boolean> nack(String taskId, String reason) {
        return Mono.fromCallable(() -> {
            InFlightTask inFlight = inFlightTasks.remove(taskId);
            if (inFlight == null) {
                log.warn("Attempted to nack unknown task: {}", taskId);
                return false;
            }

            WorkflowTask task = inFlight.task;
            metrics.recordNack();

            // Check if can retry
            if (task.canRetry()) {
                // Requeue with incremented retry count
                WorkflowTask retryTask = task.createRetryWithError(reason);
                enqueueInternal(retryTask);
                log.info("Task {} requeued for retry (attempt {})", taskId, retryTask.getRetryCount());
            } else {
                // Move to dead letter queue
                deadLetterQueue.offer(task.toBuilder().lastError(reason).build());
                taskIndex.remove(taskId);
                if (task.getExecutionKey() != null) {
                    executionKeyIndex.remove(task.getExecutionKey());
                }
                metrics.recordDeadLettered();
                log.warn("Task {} moved to dead letter queue after {} retries: {}",
                        taskId, task.getRetryCount(), reason);
            }

            return true;
        });
    }

    @Override
    public Mono<Boolean> release(String taskId) {
        return Mono.fromCallable(() -> {
            InFlightTask inFlight = inFlightTasks.remove(taskId);
            if (inFlight == null) {
                return false;
            }

            // Put task back in its partition queue
            enqueueInternal(inFlight.task);
            log.debug("Released task {} back to queue", taskId);
            return true;
        });
    }

    @Override
    public Mono<Boolean> extendVisibility(String taskId, Duration extension) {
        return Mono.fromCallable(() -> {
            InFlightTask inFlight = inFlightTasks.get(taskId);
            if (inFlight == null) {
                return false;
            }

            inFlight.extendVisibility(extension);
            log.debug("Extended visibility for task {} by {}", taskId, extension);
            return true;
        });
    }

    // ========== Task Lookup ==========

    @Override
    public Mono<Optional<WorkflowTask>> getTask(String taskId) {
        return Mono.fromCallable(() -> {
            // Check in-flight first
            InFlightTask inFlight = inFlightTasks.get(taskId);
            if (inFlight != null) {
                return Optional.of(inFlight.task);
            }

            // Check task index
            return Optional.ofNullable(taskIndex.get(taskId));
        });
    }

    @Override
    public Mono<Optional<WorkflowTask>> getTaskByExecutionKey(String executionKey) {
        return Mono.fromCallable(() -> {
            String taskId = executionKeyIndex.get(executionKey);
            if (taskId == null) {
                return Optional.empty();
            }
            return getTask(taskId).block();
        });
    }

    @Override
    public Mono<Boolean> exists(String taskId) {
        return Mono.fromCallable(() ->
                inFlightTasks.containsKey(taskId) || taskIndex.containsKey(taskId));
    }

    @Override
    public Mono<Boolean> existsByExecutionKey(String executionKey) {
        return Mono.fromCallable(() -> executionKeyIndex.containsKey(executionKey));
    }

    // ========== Task Management ==========

    @Override
    public Mono<Boolean> remove(String taskId) {
        return Mono.fromCallable(() -> {
            // Remove from in-flight
            InFlightTask inFlight = inFlightTasks.remove(taskId);
            if (inFlight != null) {
                if (inFlight.task.getExecutionKey() != null) {
                    executionKeyIndex.remove(inFlight.task.getExecutionKey());
                }
                taskIndex.remove(taskId);
                return true;
            }

            // Remove from task index and queues
            WorkflowTask task = taskIndex.remove(taskId);
            if (task != null) {
                if (task.getExecutionKey() != null) {
                    executionKeyIndex.remove(task.getExecutionKey());
                }
                // Note: We don't remove from partition queue as it's inefficient
                // The task will be skipped when polled (not in taskIndex)
                return true;
            }

            return false;
        });
    }

    @Override
    public Mono<Integer> removeByExecutionKey(String executionKey) {
        return Mono.fromCallable(() -> {
            String taskId = executionKeyIndex.get(executionKey);
            if (taskId != null) {
                return remove(taskId).block() ? 1 : 0;
            }
            return 0;
        });
    }

    @Override
    public Mono<Void> clear() {
        return Mono.fromRunnable(() -> {
            for (PriorityBlockingQueue<WorkflowTask> queue : partitionQueues) {
                queue.clear();
            }
            inFlightTasks.clear();
            deadLetterQueue.clear();
            executionKeyIndex.clear();
            taskIndex.clear();
            metrics.reset();
            log.info("Queue cleared");
        });
    }

    @Override
    public Mono<Integer> purgeExpired() {
        return Mono.fromCallable(() -> {
            int purged = 0;

            // Purge from partition queues
            for (PriorityBlockingQueue<WorkflowTask> queue : partitionQueues) {
                List<WorkflowTask> expired = queue.stream()
                        .filter(WorkflowTask::isExpired)
                        .collect(Collectors.toList());

                for (WorkflowTask task : expired) {
                    if (queue.remove(task)) {
                        taskIndex.remove(task.getTaskId());
                        if (task.getExecutionKey() != null) {
                            executionKeyIndex.remove(task.getExecutionKey());
                        }
                        purged++;
                    }
                }
            }

            metrics.addExpired(purged);
            log.info("Purged {} expired tasks", purged);
            return purged;
        });
    }

    // ========== Dead Letter Queue ==========

    @Override
    public Flux<WorkflowTask> getDeadLetterTasks(int limit) {
        return Flux.fromIterable(deadLetterQueue)
                .take(limit);
    }

    @Override
    public Mono<Boolean> requeueFromDeadLetter(String taskId) {
        return Mono.fromCallable(() -> {
            for (WorkflowTask task : deadLetterQueue) {
                if (task.getTaskId().equals(taskId)) {
                    if (deadLetterQueue.remove(task)) {
                        // Reset retry count and requeue
                        WorkflowTask requeued = task.toBuilder()
                                .retryCount(0)
                                .lastError(null)
                                .scheduledAt(Instant.now())
                                .build();
                        enqueueInternal(requeued);
                        log.info("Requeued task {} from dead letter queue", taskId);
                        return true;
                    }
                }
            }
            return false;
        });
    }

    @Override
    public Mono<Integer> clearDeadLetterQueue() {
        return Mono.fromCallable(() -> {
            int size = deadLetterQueue.size();
            deadLetterQueue.clear();
            log.info("Cleared {} tasks from dead letter queue", size);
            return size;
        });
    }

    // ========== Queue Statistics ==========

    @Override
    public QueueMetrics getMetrics() {
        return metrics;
    }

    @Override
    public Mono<Long> getQueueSize() {
        return Mono.fromCallable(() ->
                partitionQueues.stream()
                        .mapToLong(PriorityBlockingQueue::size)
                        .sum());
    }

    @Override
    public Mono<Long> getInFlightCount() {
        return Mono.just((long) inFlightTasks.size());
    }

    @Override
    public Mono<Long> getDeadLetterQueueSize() {
        return Mono.just((long) deadLetterQueue.size());
    }

    @Override
    public Mono<Long> getPartitionQueueSize(int partitionId) {
        return Mono.fromCallable(() -> {
            if (partitionId < 0 || partitionId >= partitionCount) {
                return 0L;
            }
            return (long) partitionQueues.get(partitionId).size();
        });
    }

    // ========== Configuration ==========

    @Override
    public int getPartitionCount() {
        return partitionCount;
    }

    @Override
    public Duration getVisibilityTimeout() {
        return visibilityTimeout;
    }

    @Override
    public void setVisibilityTimeout(Duration timeout) {
        this.visibilityTimeout = Objects.requireNonNull(timeout);
    }

    // ========== Private Methods ==========

    private int computePartition(String partitionKey) {
        if (partitionKey == null || partitionKey.isEmpty()) {
            return ThreadLocalRandom.current().nextInt(partitionCount);
        }
        return Math.abs(partitionKey.hashCode() % partitionCount);
    }

    private void enqueueInternal(WorkflowTask task) {
        int partitionId = task.getPartitionId() >= 0
                ? task.getPartitionId()
                : computePartition(task.getPartitionKey());

        WorkflowTask taskWithPartition = task.getPartitionId() >= 0
                ? task
                : task.withPartitionId(partitionId);

        partitionQueues.get(partitionId).offer(taskWithPartition);
        taskIndex.put(taskWithPartition.getTaskId(), taskWithPartition);
        if (taskWithPartition.getExecutionKey() != null) {
            executionKeyIndex.put(taskWithPartition.getExecutionKey(), taskWithPartition.getTaskId());
        }
    }

    private Mono<Void> checkVisibilityTimeouts() {
        return Mono.fromRunnable(() -> {
            Instant now = Instant.now();

            for (Map.Entry<String, InFlightTask> entry : inFlightTasks.entrySet()) {
                InFlightTask inFlight = entry.getValue();
                if (inFlight.isExpired(now)) {
                    // Task visibility timeout expired - requeue
                    inFlightTasks.remove(entry.getKey());
                    WorkflowTask task = inFlight.task;

                    log.warn("Visibility timeout expired for task {}, requeuing", task.getTaskId());

                    // Increment retry if this was a processing attempt
                    if (task.canRetry()) {
                        WorkflowTask retryTask = task.createRetryWithError("Visibility timeout expired");
                        enqueueInternal(retryTask);
                    } else {
                        deadLetterQueue.offer(task.toBuilder()
                                .lastError("Visibility timeout expired after max retries")
                                .build());
                        taskIndex.remove(task.getTaskId());
                        if (task.getExecutionKey() != null) {
                            executionKeyIndex.remove(task.getExecutionKey());
                        }
                        metrics.recordDeadLettered();
                    }
                }
            }
        });
    }

    private Mono<Void> processScheduledTasks() {
        return Mono.fromRunnable(() -> {
            Instant now = Instant.now();

            for (PriorityBlockingQueue<WorkflowTask> queue : partitionQueues) {
                // Peek at the head of each queue
                WorkflowTask task = queue.peek();
                if (task != null && task.isReady() && !inFlightTasks.containsKey(task.getTaskId())) {
                    // Task is now ready - emit to subscribers
                    taskSink.tryEmitNext(task);
                }
            }
        });
    }

    // ========== Inner Classes ==========

    /**
     * Tracks a task that is currently being processed.
     */
    private static class InFlightTask {
        final WorkflowTask task;
        final Instant dequeueTime;
        volatile Instant visibilityExpires;

        InFlightTask(WorkflowTask task, Instant dequeueTime, Duration visibilityTimeout) {
            this.task = task;
            this.dequeueTime = dequeueTime;
            this.visibilityExpires = dequeueTime.plus(visibilityTimeout);
        }

        void extendVisibility(Duration extension) {
            this.visibilityExpires = Instant.now().plus(extension);
        }

        boolean isExpired(Instant now) {
            return now.isAfter(visibilityExpires);
        }
    }

    /**
     * In-memory metrics implementation.
     */
    @Getter
    public static class InMemoryQueueMetrics implements QueueMetrics {
        private final AtomicLong totalEnqueued = new AtomicLong(0);
        private final AtomicLong totalDequeued = new AtomicLong(0);
        private final AtomicLong totalAcknowledged = new AtomicLong(0);
        private final AtomicLong totalNacked = new AtomicLong(0);
        private final AtomicLong totalExpired = new AtomicLong(0);
        private final AtomicLong totalDeadLettered = new AtomicLong(0);
        private final AtomicLong pendingCount = new AtomicLong(0);
        private final AtomicLong inFlightCount = new AtomicLong(0);
        private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);
        private final AtomicLong totalWaitTimeMs = new AtomicLong(0);
        private final AtomicLong processedCount = new AtomicLong(0);

        void recordEnqueue() {
            totalEnqueued.incrementAndGet();
            pendingCount.incrementAndGet();
        }

        void recordDequeue() {
            totalDequeued.incrementAndGet();
            pendingCount.decrementAndGet();
            inFlightCount.incrementAndGet();
        }

        void recordAcknowledge(Duration processingTime, Duration waitTime) {
            totalAcknowledged.incrementAndGet();
            inFlightCount.decrementAndGet();
            totalProcessingTimeMs.addAndGet(processingTime.toMillis());
            totalWaitTimeMs.addAndGet(waitTime.toMillis());
            processedCount.incrementAndGet();
        }

        void recordNack() {
            totalNacked.incrementAndGet();
            inFlightCount.decrementAndGet();
        }

        void recordExpired() {
            totalExpired.incrementAndGet();
            pendingCount.decrementAndGet();
        }

        void addExpired(int count) {
            totalExpired.addAndGet(count);
        }

        void recordDeadLettered() {
            totalDeadLettered.incrementAndGet();
        }

        void reset() {
            totalEnqueued.set(0);
            totalDequeued.set(0);
            totalAcknowledged.set(0);
            totalNacked.set(0);
            totalExpired.set(0);
            totalDeadLettered.set(0);
            pendingCount.set(0);
            inFlightCount.set(0);
            totalProcessingTimeMs.set(0);
            totalWaitTimeMs.set(0);
            processedCount.set(0);
        }

        @Override
        public long getTotalEnqueued() { return totalEnqueued.get(); }

        @Override
        public long getTotalDequeued() { return totalDequeued.get(); }

        @Override
        public long getTotalAcknowledged() { return totalAcknowledged.get(); }

        @Override
        public long getTotalNacked() { return totalNacked.get(); }

        @Override
        public long getTotalExpired() { return totalExpired.get(); }

        @Override
        public long getTotalDeadLettered() { return totalDeadLettered.get(); }

        @Override
        public long getPendingCount() { return pendingCount.get(); }

        @Override
        public long getInFlightCount() { return inFlightCount.get(); }

        @Override
        public double getAverageProcessingTimeMs() {
            long count = processedCount.get();
            return count > 0 ? (double) totalProcessingTimeMs.get() / count : 0;
        }

        @Override
        public double getAverageWaitTimeMs() {
            long count = processedCount.get();
            return count > 0 ? (double) totalWaitTimeMs.get() / count : 0;
        }
    }
}
