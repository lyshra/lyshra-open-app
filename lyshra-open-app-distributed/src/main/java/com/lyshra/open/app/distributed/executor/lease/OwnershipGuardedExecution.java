package com.lyshra.open.app.distributed.executor.lease;

import com.lyshra.open.app.distributed.coordination.OwnershipContext;
import com.lyshra.open.app.distributed.executor.DistributedExecutionResult;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Wraps workflow execution with automatic lease renewal to ensure
 * ownership is maintained throughout long-running workflows.
 *
 * This class provides a "guarded" execution context where:
 * - Lease renewal happens automatically in the background
 * - Execution can be aborted if ownership is lost
 * - The fencing token is always accessible for validation
 *
 * Usage:
 * <pre>
 * OwnershipGuardedExecution execution = OwnershipGuardedExecution.create(
 *     executionKey, ownershipContext, renewalManager);
 *
 * execution.execute(context -> {
 *     // Your workflow logic here
 *     // Can check execution.isOwnershipValid() at any point
 *     // Can get current fencing token via execution.getFencingToken()
 *     return myWorkflowExecutor.execute(workflow, context);
 * })
 * .doFinally(signal -> execution.complete())
 * .subscribe();
 * </pre>
 *
 * Thread Safety: This class is thread-safe.
 */
@Slf4j
public class OwnershipGuardedExecution {

    private final String executionKey;
    private final LeaseRenewalManager renewalManager;
    private final AtomicReference<LeaseRenewalManager.ActiveRenewalHandle> renewalHandle;
    private final AtomicBoolean completed;
    private final AtomicBoolean aborted;
    private final AtomicReference<String> abortReason;
    private final Instant startTime;

    private volatile OwnershipContext initialContext;

    private OwnershipGuardedExecution(String executionKey,
                                       OwnershipContext initialContext,
                                       LeaseRenewalManager renewalManager) {
        this.executionKey = Objects.requireNonNull(executionKey);
        this.initialContext = Objects.requireNonNull(initialContext);
        this.renewalManager = Objects.requireNonNull(renewalManager);
        this.renewalHandle = new AtomicReference<>();
        this.completed = new AtomicBoolean(false);
        this.aborted = new AtomicBoolean(false);
        this.abortReason = new AtomicReference<>();
        this.startTime = Instant.now();
    }

    /**
     * Creates a new guarded execution.
     *
     * @param executionKey the workflow execution key
     * @param initialContext the initial ownership context
     * @param renewalManager the lease renewal manager
     * @return the guarded execution
     */
    public static OwnershipGuardedExecution create(String executionKey,
                                                    OwnershipContext initialContext,
                                                    LeaseRenewalManager renewalManager) {
        return new OwnershipGuardedExecution(executionKey, initialContext, renewalManager);
    }

    /**
     * Starts lease renewal and executes the workflow with ownership protection.
     *
     * @param <T> the result type
     * @param workflowExecution function that performs the actual workflow execution
     * @return Mono containing the execution result
     */
    public <T> Mono<T> execute(Supplier<Mono<T>> workflowExecution) {
        return startRenewal()
                .flatMap(handle -> {
                    // Set up abort callback
                    handle.onFailure(this::handleOwnershipFailure);

                    return workflowExecution.get()
                            .doOnSubscribe(sub -> log.debug("Starting guarded execution for {}", executionKey))
                            .doOnSuccess(result -> log.debug("Guarded execution completed for {}", executionKey))
                            .doOnError(error -> log.warn("Guarded execution failed for {}: {}",
                                    executionKey, error.getMessage()))
                            .doFinally(signal -> stopRenewal("execution_completed"));
                })
                .flatMap(result -> {
                    // Check if we were aborted during execution
                    if (aborted.get()) {
                        return Mono.error(new OwnershipLostException(executionKey,
                                abortReason.get() != null ? abortReason.get() : "Ownership lost"));
                    }
                    return Mono.just(result);
                });
    }

    /**
     * Executes with context - provides the guarded execution context to the workflow.
     *
     * @param <T> the result type
     * @param workflowExecution function that receives this guarded execution and returns result
     * @return Mono containing the execution result
     */
    public <T> Mono<T> executeWithContext(Function<OwnershipGuardedExecution, Mono<T>> workflowExecution) {
        return startRenewal()
                .flatMap(handle -> {
                    handle.onFailure(this::handleOwnershipFailure);

                    return workflowExecution.apply(this)
                            .doFinally(signal -> stopRenewal("execution_completed"));
                })
                .flatMap(result -> {
                    if (aborted.get()) {
                        return Mono.error(new OwnershipLostException(executionKey,
                                abortReason.get() != null ? abortReason.get() : "Ownership lost"));
                    }
                    return Mono.just(result);
                });
    }

    // ========== Ownership Status ==========

    /**
     * Checks if ownership is still valid.
     *
     * @return true if ownership is valid
     */
    public boolean isOwnershipValid() {
        if (aborted.get() || completed.get()) {
            return false;
        }

        LeaseRenewalManager.ActiveRenewalHandle handle = renewalHandle.get();
        return handle != null && handle.isOwnershipValid();
    }

    /**
     * Gets the current fencing token.
     *
     * @return the fencing token, or -1 if not available
     */
    public long getFencingToken() {
        LeaseRenewalManager.ActiveRenewalHandle handle = renewalHandle.get();
        return handle != null ? handle.getFencingToken() : initialContext.getFencingToken();
    }

    /**
     * Gets the current ownership context.
     *
     * @return the current context
     */
    public OwnershipContext getCurrentContext() {
        LeaseRenewalManager.ActiveRenewalHandle handle = renewalHandle.get();
        return handle != null ? handle.getCurrentContext() : initialContext;
    }

    /**
     * Gets the remaining lease time.
     *
     * @return optional containing remaining time
     */
    public Optional<Duration> getRemainingTime() {
        LeaseRenewalManager.ActiveRenewalHandle handle = renewalHandle.get();
        return handle != null ? handle.getRemainingTime() : initialContext.getRemainingTime();
    }

    /**
     * Checks if the execution has been aborted due to ownership loss.
     *
     * @return true if aborted
     */
    public boolean isAborted() {
        return aborted.get();
    }

    /**
     * Gets the abort reason if aborted.
     *
     * @return optional containing the abort reason
     */
    public Optional<String> getAbortReason() {
        return Optional.ofNullable(abortReason.get());
    }

    /**
     * Checks if the execution has completed.
     *
     * @return true if completed
     */
    public boolean isCompleted() {
        return completed.get();
    }

    /**
     * Gets the execution key.
     *
     * @return the execution key
     */
    public String getExecutionKey() {
        return executionKey;
    }

    /**
     * Gets the execution duration so far.
     *
     * @return the duration since start
     */
    public Duration getExecutionDuration() {
        return Duration.between(startTime, Instant.now());
    }

    // ========== Control Methods ==========

    /**
     * Triggers an immediate lease renewal.
     *
     * @return Mono containing true if renewal succeeded
     */
    public Mono<Boolean> renewNow() {
        LeaseRenewalManager.ActiveRenewalHandle handle = renewalHandle.get();
        if (handle == null) {
            return Mono.just(false);
        }
        return handle.renewNow();
    }

    /**
     * Verifies ownership is still valid, throwing if not.
     *
     * @throws OwnershipLostException if ownership is not valid
     */
    public void verifyOwnership() throws OwnershipLostException {
        if (!isOwnershipValid()) {
            throw new OwnershipLostException(executionKey,
                    abortReason.get() != null ? abortReason.get() : "Ownership is not valid");
        }
    }

    /**
     * Marks the execution as complete and stops renewal.
     */
    public void complete() {
        if (completed.compareAndSet(false, true)) {
            stopRenewal("execution_completed");
        }
    }

    /**
     * Aborts the execution with a reason.
     *
     * @param reason the abort reason
     */
    public void abort(String reason) {
        if (aborted.compareAndSet(false, true)) {
            abortReason.set(reason);
            stopRenewal("execution_aborted: " + reason);
        }
    }

    // ========== Internal Methods ==========

    private Mono<LeaseRenewalManager.ActiveRenewalHandle> startRenewal() {
        return renewalManager.startRenewal(executionKey, initialContext)
                .doOnNext(handle -> {
                    renewalHandle.set(handle);
                    log.debug("Lease renewal started for {}", executionKey);
                });
    }

    private void stopRenewal(String reason) {
        LeaseRenewalManager.ActiveRenewalHandle handle = renewalHandle.getAndSet(null);
        if (handle != null) {
            renewalManager.stopRenewal(executionKey, reason)
                    .subscribe(
                            v -> log.debug("Lease renewal stopped for {}: {}", executionKey, reason),
                            e -> log.warn("Error stopping lease renewal for {}", executionKey, e)
                    );
        }
    }

    private void handleOwnershipFailure(ILeaseRenewalStrategy.RenewalFailureAction action) {
        switch (action) {
            case ABORT_EXECUTION:
                abort("Ownership lost - too many renewal failures");
                break;

            case PAUSE_AND_REACQUIRE:
                abort("Ownership lost - requires re-acquisition");
                break;

            default:
                // Other actions don't cause immediate abort
                log.warn("Renewal failure action for {}: {}", executionKey, action);
                break;
        }
    }

    // ========== Builder ==========

    /**
     * Builder for creating guarded executions with additional options.
     */
    public static class Builder {
        private String executionKey;
        private OwnershipContext initialContext;
        private LeaseRenewalManager renewalManager;

        public Builder executionKey(String executionKey) {
            this.executionKey = executionKey;
            return this;
        }

        public Builder initialContext(OwnershipContext context) {
            this.initialContext = context;
            return this;
        }

        public Builder renewalManager(LeaseRenewalManager manager) {
            this.renewalManager = manager;
            return this;
        }

        public OwnershipGuardedExecution build() {
            return new OwnershipGuardedExecution(executionKey, initialContext, renewalManager);
        }
    }

    /**
     * Creates a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    // ========== Exception ==========

    /**
     * Exception thrown when ownership is lost during execution.
     */
    public static class OwnershipLostException extends RuntimeException {
        private final String executionKey;

        public OwnershipLostException(String executionKey, String message) {
            super(message);
            this.executionKey = executionKey;
        }

        public String getExecutionKey() {
            return executionKey;
        }
    }
}
