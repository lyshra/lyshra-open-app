package com.lyshra.open.app.distributed.queue;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Simple task consumer implementation for the distributed task queue.
 *
 * This consumer provides:
 * - Partition-based subscription
 * - Configurable concurrency
 * - Automatic acknowledgment on success
 * - Automatic nack on failure
 * - Metrics collection
 *
 * Usage:
 * <pre>
 * SimpleTaskConsumer consumer = SimpleTaskConsumer.builder()
 *     .queue(taskQueue)
 *     .consumerId("consumer-1")
 *     .consumerGroupId("workflow-processors")
 *     .maxConcurrency(10)
 *     .build();
 *
 * consumer.start()
 *     .then(consumer.consume(task -> processTask(task)))
 *     .subscribe();
 * </pre>
 *
 * Thread Safety: This class is thread-safe.
 */
@Slf4j
public class SimpleTaskConsumer implements ITaskConsumer {

    private static final int DEFAULT_MAX_CONCURRENCY = 10;
    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(100);

    private final IDistributedTaskQueue queue;
    private final String consumerId;
    private final String consumerGroupId;
    private final Set<Integer> assignedPartitions;
    private final AtomicBoolean running;
    private final AtomicInteger maxConcurrency;
    private final AtomicInteger currentConcurrency;
    private final SimpleConsumerMetrics metrics;

    // In-progress tasks
    private final ConcurrentHashMap<String, ConsumableTaskImpl> inProgressTasks;

    // Subscription disposable
    private volatile Disposable subscription;

    @Builder
    public SimpleTaskConsumer(IDistributedTaskQueue queue,
                               String consumerId,
                               String consumerGroupId,
                               Set<Integer> assignedPartitions,
                               Integer maxConcurrency) {
        this.queue = Objects.requireNonNull(queue, "Queue must not be null");
        this.consumerId = consumerId != null ? consumerId : UUID.randomUUID().toString();
        this.consumerGroupId = consumerGroupId != null ? consumerGroupId : "default";
        this.assignedPartitions = new CopyOnWriteArraySet<>(
                assignedPartitions != null ? assignedPartitions : Collections.emptySet());
        this.running = new AtomicBoolean(false);
        this.maxConcurrency = new AtomicInteger(
                maxConcurrency != null ? maxConcurrency : DEFAULT_MAX_CONCURRENCY);
        this.currentConcurrency = new AtomicInteger(0);
        this.metrics = new SimpleConsumerMetrics();
        this.inProgressTasks = new ConcurrentHashMap<>();
    }

    // ========== Lifecycle ==========

    @Override
    public Mono<Void> start() {
        return Mono.fromRunnable(() -> {
            if (!running.compareAndSet(false, true)) {
                log.warn("Consumer {} already running", consumerId);
                return;
            }

            log.info("Starting task consumer {} in group {} with partitions {}",
                    consumerId, consumerGroupId, assignedPartitions);
        });
    }

    @Override
    public Mono<Void> stop() {
        return Mono.fromRunnable(() -> {
            if (!running.compareAndSet(true, false)) {
                return;
            }

            log.info("Stopping task consumer {}", consumerId);

            if (subscription != null) {
                subscription.dispose();
                subscription = null;
            }

            // Release all in-progress tasks
            for (ConsumableTaskImpl task : inProgressTasks.values()) {
                task.release().subscribe();
            }
            inProgressTasks.clear();
        });
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    // ========== Partition Assignment ==========

    @Override
    public Set<Integer> getAssignedPartitions() {
        return Collections.unmodifiableSet(assignedPartitions);
    }

    @Override
    public void assignPartitions(Set<Integer> partitionIds) {
        assignedPartitions.addAll(partitionIds);
        log.info("Consumer {} assigned partitions: {}", consumerId, partitionIds);
    }

    @Override
    public void revokePartitions(Set<Integer> partitionIds) {
        assignedPartitions.removeAll(partitionIds);
        log.info("Consumer {} revoked partitions: {}", consumerId, partitionIds);

        // Release tasks from revoked partitions
        for (ConsumableTaskImpl task : inProgressTasks.values()) {
            if (partitionIds.contains(task.getTask().getPartitionId())) {
                task.release().subscribe();
            }
        }
    }

    // ========== Consumption ==========

    @Override
    public Flux<TaskProcessingResult> consume(Function<WorkflowTask, Mono<Void>> handler) {
        return consumeWithHandle()
                .flatMap(consumable -> {
                    Instant startTime = Instant.now();

                    return handler.apply(consumable.getTask())
                            .then(consumable.acknowledge())
                            .thenReturn(createSuccessResult(consumable.getTask(), startTime))
                            .onErrorResume(error -> {
                                return consumable.nack(error.getMessage())
                                        .thenReturn(createFailureResult(consumable.getTask(), error, startTime));
                            });
                }, maxConcurrency.get());
    }

    @Override
    public Flux<ConsumableTask> consumeWithHandle() {
        if (!running.get()) {
            return Flux.error(new IllegalStateException("Consumer is not running"));
        }

        // Subscribe to queue based on partition assignment
        Flux<WorkflowTask> taskFlux;
        if (assignedPartitions.isEmpty()) {
            taskFlux = queue.subscribe();
        } else {
            taskFlux = queue.subscribeToPartitions(assignedPartitions);
        }

        return taskFlux
                .filter(task -> canAcceptTask())
                .<ConsumableTask>map(this::wrapTask)
                .doOnSubscribe(sub -> {
                    log.debug("Consumer {} started consuming", consumerId);
                });
    }

    @Override
    public Mono<ConsumableTask> pollOne() {
        if (!running.get()) {
            return Mono.error(new IllegalStateException("Consumer is not running"));
        }

        if (!canAcceptTask()) {
            return Mono.empty();
        }

        Mono<Optional<WorkflowTask>> pollMono;
        if (assignedPartitions.isEmpty()) {
            pollMono = queue.poll();
        } else {
            pollMono = queue.pollPartitions(assignedPartitions, 1)
                    .map(list -> list.isEmpty() ? Optional.empty() : Optional.of(list.get(0)));
        }

        return pollMono
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(this::wrapTask);
    }

    @Override
    public Flux<ConsumableTask> pollBatch(int maxTasks) {
        if (!running.get()) {
            return Flux.error(new IllegalStateException("Consumer is not running"));
        }

        int available = getAvailableCapacity();
        int batchSize = Math.min(maxTasks, available);

        if (batchSize <= 0) {
            return Flux.empty();
        }

        Mono<List<WorkflowTask>> pollMono;
        if (assignedPartitions.isEmpty()) {
            pollMono = queue.pollBatch(batchSize);
        } else {
            pollMono = queue.pollPartitions(assignedPartitions, batchSize);
        }

        return pollMono
                .flatMapMany(Flux::fromIterable)
                .map(this::wrapTask);
    }

    // ========== Configuration ==========

    @Override
    public String getConsumerId() {
        return consumerId;
    }

    @Override
    public String getConsumerGroupId() {
        return consumerGroupId;
    }

    @Override
    public int getMaxConcurrency() {
        return maxConcurrency.get();
    }

    @Override
    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency.set(maxConcurrency);
    }

    @Override
    public ConsumerMetrics getMetrics() {
        return metrics;
    }

    // ========== Private Methods ==========

    private boolean canAcceptTask() {
        return currentConcurrency.get() < maxConcurrency.get();
    }

    private int getAvailableCapacity() {
        return maxConcurrency.get() - currentConcurrency.get();
    }

    private ConsumableTaskImpl wrapTask(WorkflowTask task) {
        currentConcurrency.incrementAndGet();
        metrics.recordConsumed();

        ConsumableTaskImpl consumable = new ConsumableTaskImpl(task);
        inProgressTasks.put(task.getTaskId(), consumable);

        return consumable;
    }

    private TaskProcessingResult createSuccessResult(WorkflowTask task, Instant startTime) {
        Duration processingTime = Duration.between(startTime, Instant.now());
        metrics.recordSuccess(processingTime);

        return new TaskProcessingResult() {
            @Override public WorkflowTask getTask() { return task; }
            @Override public boolean isSuccess() { return true; }
            @Override public Throwable getError() { return null; }
            @Override public Duration getProcessingTime() { return processingTime; }
        };
    }

    private TaskProcessingResult createFailureResult(WorkflowTask task, Throwable error, Instant startTime) {
        Duration processingTime = Duration.between(startTime, Instant.now());
        metrics.recordFailure();

        return new TaskProcessingResult() {
            @Override public WorkflowTask getTask() { return task; }
            @Override public boolean isSuccess() { return false; }
            @Override public Throwable getError() { return error; }
            @Override public Duration getProcessingTime() { return processingTime; }
        };
    }

    // ========== Inner Classes ==========

    /**
     * Implementation of ConsumableTask.
     */
    private class ConsumableTaskImpl implements ConsumableTask {
        private final WorkflowTask task;
        private final AtomicBoolean completed;

        ConsumableTaskImpl(WorkflowTask task) {
            this.task = task;
            this.completed = new AtomicBoolean(false);
        }

        @Override
        public WorkflowTask getTask() {
            return task;
        }

        @Override
        public Mono<Void> acknowledge() {
            return Mono.defer(() -> {
                if (!completed.compareAndSet(false, true)) {
                    return Mono.empty();
                }

                return queue.acknowledge(task.getTaskId())
                        .doFinally(signal -> {
                            currentConcurrency.decrementAndGet();
                            inProgressTasks.remove(task.getTaskId());
                        })
                        .then();
            });
        }

        @Override
        public Mono<Void> nack(String reason) {
            return Mono.defer(() -> {
                if (!completed.compareAndSet(false, true)) {
                    return Mono.empty();
                }

                return queue.nack(task.getTaskId(), reason)
                        .doFinally(signal -> {
                            currentConcurrency.decrementAndGet();
                            inProgressTasks.remove(task.getTaskId());
                        })
                        .then();
            });
        }

        @Override
        public Mono<Void> extendVisibility(Duration extension) {
            if (completed.get()) {
                return Mono.empty();
            }
            return queue.extendVisibility(task.getTaskId(), extension).then();
        }

        @Override
        public Mono<Void> release() {
            return Mono.defer(() -> {
                if (!completed.compareAndSet(false, true)) {
                    return Mono.empty();
                }

                return queue.release(task.getTaskId())
                        .doFinally(signal -> {
                            currentConcurrency.decrementAndGet();
                            inProgressTasks.remove(task.getTaskId());
                        })
                        .then();
            });
        }
    }

    /**
     * Simple consumer metrics implementation.
     */
    @Getter
    public static class SimpleConsumerMetrics implements ConsumerMetrics {
        private final AtomicLong totalConsumed = new AtomicLong(0);
        private final AtomicLong totalSucceeded = new AtomicLong(0);
        private final AtomicLong totalFailed = new AtomicLong(0);
        private final AtomicLong inProgressCount = new AtomicLong(0);
        private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);

        void recordConsumed() {
            totalConsumed.incrementAndGet();
            inProgressCount.incrementAndGet();
        }

        void recordSuccess(Duration processingTime) {
            totalSucceeded.incrementAndGet();
            inProgressCount.decrementAndGet();
            totalProcessingTimeMs.addAndGet(processingTime.toMillis());
        }

        void recordFailure() {
            totalFailed.incrementAndGet();
            inProgressCount.decrementAndGet();
        }

        @Override
        public long getTotalConsumed() { return totalConsumed.get(); }

        @Override
        public long getTotalSucceeded() { return totalSucceeded.get(); }

        @Override
        public long getTotalFailed() { return totalFailed.get(); }

        @Override
        public long getInProgressCount() { return inProgressCount.get(); }

        @Override
        public double getAverageProcessingTimeMs() {
            long succeeded = totalSucceeded.get();
            return succeeded > 0 ? (double) totalProcessingTimeMs.get() / succeeded : 0;
        }
    }
}
