package com.lyshra.open.app.distributed.queue;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Simple task producer implementation for the distributed task queue.
 *
 * This producer provides:
 * - Direct queue integration
 * - Batch sending
 * - Scheduled/delayed delivery
 * - Metrics collection
 *
 * Usage:
 * <pre>
 * SimpleTaskProducer producer = SimpleTaskProducer.builder()
 *     .queue(taskQueue)
 *     .producerId("producer-1")
 *     .build();
 *
 * producer.initialize()
 *     .then(producer.sendExecution(
 *         "exec-123", "org", "module", "1.0", "workflow", "id-1", inputData))
 *     .subscribe(result -> System.out.println("Sent to partition: " + result.getPartitionId()));
 * </pre>
 *
 * Thread Safety: This class is thread-safe.
 */
@Slf4j
public class SimpleTaskProducer implements ITaskProducer {

    private final IDistributedTaskQueue queue;
    private final String producerId;
    private final AtomicBoolean ready;
    private final SimpleProducerMetrics metrics;

    @Builder
    public SimpleTaskProducer(IDistributedTaskQueue queue, String producerId) {
        this.queue = Objects.requireNonNull(queue, "Queue must not be null");
        this.producerId = producerId != null ? producerId : UUID.randomUUID().toString();
        this.ready = new AtomicBoolean(false);
        this.metrics = new SimpleProducerMetrics();
    }

    // ========== Lifecycle ==========

    @Override
    public Mono<Void> initialize() {
        return Mono.fromRunnable(() -> {
            if (!ready.compareAndSet(false, true)) {
                log.warn("Producer {} already initialized", producerId);
                return;
            }

            log.info("Initialized task producer {}", producerId);
        });
    }

    @Override
    public Mono<Void> shutdown() {
        return Mono.fromRunnable(() -> {
            if (!ready.compareAndSet(true, false)) {
                return;
            }

            log.info("Shut down task producer {}", producerId);
        });
    }

    @Override
    public boolean isReady() {
        return ready.get() && queue.isRunning();
    }

    // ========== Basic Send Operations ==========

    @Override
    public Mono<SendResult> send(WorkflowTask task) {
        if (!isReady()) {
            return Mono.just(SendResult.failure(task,
                    new IllegalStateException("Producer is not ready")));
        }

        Instant startTime = Instant.now();

        return queue.enqueue(task)
                .map(enqueuedTask -> {
                    Duration latency = Duration.between(startTime, Instant.now());
                    metrics.recordSend(enqueuedTask.getPartitionId(), latency);
                    log.debug("Sent task {} to partition {}", enqueuedTask.getTaskId(),
                            enqueuedTask.getPartitionId());
                    return SendResult.success(enqueuedTask);
                })
                .onErrorResume(error -> {
                    metrics.recordFailure();
                    log.error("Failed to send task {}: {}", task.getTaskId(), error.getMessage());
                    return Mono.just(SendResult.failure(task, error));
                });
    }

    @Override
    public Mono<BatchSendResult> sendBatch(List<WorkflowTask> tasks) {
        if (!isReady()) {
            List<SendResult> failures = tasks.stream()
                    .map(task -> SendResult.failure(task,
                            new IllegalStateException("Producer is not ready")))
                    .collect(Collectors.toList());
            return Mono.just(createBatchResult(failures));
        }

        return queue.enqueueBatch(tasks)
                .map(enqueuedTasks -> {
                    List<SendResult> results = enqueuedTasks.stream()
                            .map(SendResult::success)
                            .collect(Collectors.toList());

                    for (WorkflowTask task : enqueuedTasks) {
                        metrics.recordSend(task.getPartitionId(), Duration.ZERO);
                    }

                    return createBatchResult(results);
                })
                .onErrorResume(error -> {
                    List<SendResult> failures = tasks.stream()
                            .map(task -> SendResult.failure(task, error))
                            .collect(Collectors.toList());
                    metrics.addFailures(tasks.size());
                    return Mono.just(createBatchResult(failures));
                });
    }

    @Override
    public Mono<SendResult> sendAndConfirm(WorkflowTask task) {
        // For in-memory queue, send is synchronous so this is the same as send
        return send(task);
    }

    // ========== Scheduled/Delayed Send ==========

    @Override
    public Mono<SendResult> sendDelayed(WorkflowTask task, Duration delay) {
        return send(task.toBuilder()
                .scheduledAt(Instant.now().plus(delay))
                .build());
    }

    @Override
    public Mono<SendResult> sendScheduled(WorkflowTask task, Instant scheduledTime) {
        return send(task.toBuilder()
                .scheduledAt(scheduledTime)
                .build());
    }

    // ========== Configuration ==========

    @Override
    public String getProducerId() {
        return producerId;
    }

    @Override
    public ProducerMetrics getMetrics() {
        return metrics;
    }

    // ========== Private Methods ==========

    private BatchSendResult createBatchResult(List<SendResult> results) {
        List<SendResult> successful = results.stream()
                .filter(SendResult::isSuccess)
                .collect(Collectors.toList());

        List<SendResult> failed = results.stream()
                .filter(r -> !r.isSuccess())
                .collect(Collectors.toList());

        return new BatchSendResult() {
            @Override public List<SendResult> getResults() { return results; }
            @Override public List<SendResult> getSuccessful() { return successful; }
            @Override public List<SendResult> getFailed() { return failed; }
        };
    }

    // ========== Inner Classes ==========

    /**
     * Simple producer metrics implementation.
     */
    @Getter
    public static class SimpleProducerMetrics implements ProducerMetrics {
        private final AtomicLong totalSent = new AtomicLong(0);
        private final AtomicLong totalSucceeded = new AtomicLong(0);
        private final AtomicLong totalFailed = new AtomicLong(0);
        private final AtomicLong totalLatencyMs = new AtomicLong(0);
        private final ConcurrentHashMap<Integer, AtomicLong> tasksPerPartition = new ConcurrentHashMap<>();

        void recordSend(int partitionId, Duration latency) {
            totalSent.incrementAndGet();
            totalSucceeded.incrementAndGet();
            totalLatencyMs.addAndGet(latency.toMillis());
            tasksPerPartition.computeIfAbsent(partitionId, k -> new AtomicLong(0))
                    .incrementAndGet();
        }

        void recordFailure() {
            totalSent.incrementAndGet();
            totalFailed.incrementAndGet();
        }

        void addFailures(int count) {
            totalSent.addAndGet(count);
            totalFailed.addAndGet(count);
        }

        @Override
        public long getTotalSent() { return totalSent.get(); }

        @Override
        public long getTotalSucceeded() { return totalSucceeded.get(); }

        @Override
        public long getTotalFailed() { return totalFailed.get(); }

        @Override
        public double getAverageSendLatencyMs() {
            long succeeded = totalSucceeded.get();
            return succeeded > 0 ? (double) totalLatencyMs.get() / succeeded : 0;
        }

        @Override
        public Map<Integer, Long> getTasksPerPartition() {
            Map<Integer, Long> result = new HashMap<>();
            for (Map.Entry<Integer, AtomicLong> entry : tasksPerPartition.entrySet()) {
                result.put(entry.getKey(), entry.getValue().get());
            }
            return result;
        }
    }
}
