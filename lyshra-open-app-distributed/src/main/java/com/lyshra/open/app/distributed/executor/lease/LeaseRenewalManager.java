package com.lyshra.open.app.distributed.executor.lease;

import com.lyshra.open.app.distributed.coordination.IWorkflowOwnershipCoordinator;
import com.lyshra.open.app.distributed.coordination.OwnershipContext;
import com.lyshra.open.app.distributed.dispatcher.IWorkflowDispatcher;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Manages periodic lease renewal for active workflow executions.
 *
 * This manager ensures that ownership leases are renewed before they expire
 * during workflow execution. It provides:
 * - Automatic periodic renewal based on configurable strategy
 * - Per-execution renewal tracking
 * - Failure handling with retry and abort support
 * - Event notifications for renewal lifecycle
 *
 * Usage Flow:
 * 1. Call startRenewal() when workflow execution begins
 * 2. Manager automatically renews the lease periodically
 * 3. Call stopRenewal() when workflow execution completes
 *
 * Thread Safety: This class is thread-safe.
 */
@Slf4j
public class LeaseRenewalManager {

    private final IWorkflowOwnershipCoordinator coordinator;
    private final IWorkflowDispatcher dispatcher;
    private final ILeaseRenewalStrategy strategy;

    private final ConcurrentHashMap<String, ActiveRenewal> activeRenewals;
    private final CopyOnWriteArrayList<ILeaseRenewalListener> listeners;
    private final LeaseRenewalMetrics metrics;

    private final AtomicBoolean running;
    private Disposable globalRenewalTask;

    /**
     * Creates a new lease renewal manager with coordinator.
     */
    public LeaseRenewalManager(IWorkflowOwnershipCoordinator coordinator) {
        this(coordinator, null, AdaptiveLeaseRenewalStrategy.DEFAULT);
    }

    /**
     * Creates a new lease renewal manager with dispatcher.
     */
    public LeaseRenewalManager(IWorkflowDispatcher dispatcher) {
        this(null, dispatcher, AdaptiveLeaseRenewalStrategy.DEFAULT);
    }

    /**
     * Creates a new lease renewal manager with custom strategy.
     */
    public LeaseRenewalManager(IWorkflowOwnershipCoordinator coordinator,
                                IWorkflowDispatcher dispatcher,
                                ILeaseRenewalStrategy strategy) {
        this.coordinator = coordinator;
        this.dispatcher = dispatcher;
        this.strategy = strategy != null ? strategy : AdaptiveLeaseRenewalStrategy.DEFAULT;

        this.activeRenewals = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.metrics = new LeaseRenewalMetrics();
        this.running = new AtomicBoolean(false);

        if (coordinator == null && dispatcher == null) {
            throw new IllegalArgumentException("Either coordinator or dispatcher must be provided");
        }
    }

    // ========== Lifecycle ==========

    /**
     * Starts the renewal manager.
     *
     * @return Mono that completes when started
     */
    public Mono<Void> start() {
        return Mono.fromRunnable(() -> {
            if (!running.compareAndSet(false, true)) {
                log.warn("Lease renewal manager already running");
                return;
            }

            log.info("Starting lease renewal manager");

            // Start global renewal checker
            globalRenewalTask = Flux.interval(Duration.ofSeconds(5))
                    .flatMap(tick -> checkAllRenewals())
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(
                            count -> {
                                if (count > 0) {
                                    log.debug("Renewal check completed: {} renewals processed", count);
                                }
                            },
                            error -> log.error("Error in global renewal task", error)
                    );
        });
    }

    /**
     * Stops the renewal manager.
     *
     * @return Mono that completes when stopped
     */
    public Mono<Void> stop() {
        return Mono.fromRunnable(() -> {
            if (!running.compareAndSet(true, false)) {
                return;
            }

            log.info("Stopping lease renewal manager with {} active renewals", activeRenewals.size());

            if (globalRenewalTask != null) {
                globalRenewalTask.dispose();
            }

            // Stop all individual renewal tasks
            for (ActiveRenewal renewal : activeRenewals.values()) {
                renewal.stop();
            }
            activeRenewals.clear();
        });
    }

    /**
     * Checks if the manager is running.
     */
    public boolean isRunning() {
        return running.get();
    }

    // ========== Renewal Management ==========

    /**
     * Starts lease renewal for a workflow execution.
     *
     * @param executionKey the workflow execution key
     * @param initialContext the initial ownership context
     * @return Mono containing the active renewal handle
     */
    public Mono<ActiveRenewalHandle> startRenewal(String executionKey, OwnershipContext initialContext) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");
        Objects.requireNonNull(initialContext, "initialContext must not be null");

        return Mono.fromCallable(() -> {
            if (!running.get()) {
                throw new IllegalStateException("Lease renewal manager is not running");
            }

            // Check if already tracking this execution
            if (activeRenewals.containsKey(executionKey)) {
                log.debug("Renewal already active for {}", executionKey);
                return new ActiveRenewalHandle(activeRenewals.get(executionKey));
            }

            // Create new active renewal
            ActiveRenewal renewal = new ActiveRenewal(executionKey, initialContext, strategy);
            activeRenewals.put(executionKey, renewal);

            // Start the renewal process
            renewal.start();

            log.info("Started lease renewal for {}, expires at {}", executionKey, initialContext.getExpiresAt());
            metrics.recordRenewalStarted();
            notifyRenewalStarted(executionKey, initialContext);

            return new ActiveRenewalHandle(renewal);
        });
    }

    /**
     * Stops lease renewal for a workflow execution.
     *
     * @param executionKey the workflow execution key
     * @param reason the reason for stopping
     * @return Mono that completes when stopped
     */
    public Mono<Void> stopRenewal(String executionKey, String reason) {
        Objects.requireNonNull(executionKey, "executionKey must not be null");

        return Mono.fromRunnable(() -> {
            ActiveRenewal renewal = activeRenewals.remove(executionKey);
            if (renewal != null) {
                renewal.stop();
                log.info("Stopped lease renewal for {}: {}", executionKey, reason);
                metrics.recordRenewalStopped();
                notifyRenewalStopped(executionKey, reason);
            }
        });
    }

    /**
     * Checks if renewal is active for a workflow.
     */
    public boolean isRenewalActive(String executionKey) {
        return activeRenewals.containsKey(executionKey);
    }

    /**
     * Gets the current ownership context for a workflow.
     */
    public Optional<OwnershipContext> getCurrentContext(String executionKey) {
        ActiveRenewal renewal = activeRenewals.get(executionKey);
        return renewal != null ? Optional.of(renewal.getCurrentContext()) : Optional.empty();
    }

    /**
     * Gets the renewal state for a workflow.
     */
    public Optional<ILeaseRenewalStrategy.LeaseRenewalState> getRenewalState(String executionKey) {
        ActiveRenewal renewal = activeRenewals.get(executionKey);
        return renewal != null ? Optional.of(renewal.getState()) : Optional.empty();
    }

    /**
     * Triggers an immediate renewal attempt.
     */
    public Mono<Boolean> renewNow(String executionKey) {
        ActiveRenewal renewal = activeRenewals.get(executionKey);
        if (renewal == null) {
            return Mono.just(false);
        }
        return renewal.performRenewal();
    }

    // ========== Query Methods ==========

    /**
     * Gets all active execution keys.
     */
    public Set<String> getActiveExecutionKeys() {
        return Set.copyOf(activeRenewals.keySet());
    }

    /**
     * Gets the number of active renewals.
     */
    public int getActiveRenewalCount() {
        return activeRenewals.size();
    }

    /**
     * Gets the renewal metrics.
     */
    public LeaseRenewalMetrics getMetrics() {
        return metrics;
    }

    // ========== Event Listeners ==========

    /**
     * Adds a renewal event listener.
     */
    public void addListener(ILeaseRenewalListener listener) {
        Objects.requireNonNull(listener);
        listeners.add(listener);
    }

    /**
     * Removes a renewal event listener.
     */
    public void removeListener(ILeaseRenewalListener listener) {
        listeners.remove(listener);
    }

    // ========== Internal Methods ==========

    private Mono<Integer> checkAllRenewals() {
        return Flux.fromIterable(activeRenewals.entrySet())
                .flatMap(entry -> {
                    try {
                        return entry.getValue().checkAndRenewIfNeeded();
                    } catch (Exception e) {
                        log.error("Error checking renewal for {}", entry.getKey(), e);
                        return Mono.just(false);
                    }
                })
                .filter(renewed -> renewed)
                .count()
                .map(Long::intValue);
    }

    private Mono<Boolean> performRenewalWithCoordinator(String executionKey, Duration extensionDuration) {
        if (coordinator != null) {
            return coordinator.renewOwnership(executionKey, extensionDuration);
        } else if (dispatcher != null) {
            return dispatcher.renewOwnership(executionKey);
        }
        return Mono.just(false);
    }

    private void notifyRenewalStarted(String executionKey, OwnershipContext context) {
        for (ILeaseRenewalListener listener : listeners) {
            try {
                listener.onRenewalStarted(executionKey, context);
            } catch (Exception e) {
                log.error("Error notifying listener of renewal start", e);
            }
        }
    }

    private void notifyRenewalStopped(String executionKey, String reason) {
        for (ILeaseRenewalListener listener : listeners) {
            try {
                listener.onRenewalStopped(executionKey, reason);
            } catch (Exception e) {
                log.error("Error notifying listener of renewal stop", e);
            }
        }
    }

    private void notifyRenewalSuccess(String executionKey, OwnershipContext newContext) {
        for (ILeaseRenewalListener listener : listeners) {
            try {
                listener.onRenewalSuccess(executionKey, newContext);
            } catch (Exception e) {
                log.error("Error notifying listener of renewal success", e);
            }
        }
    }

    private void notifyRenewalFailure(String executionKey, String reason, int consecutiveFailures) {
        for (ILeaseRenewalListener listener : listeners) {
            try {
                listener.onRenewalFailure(executionKey, reason, consecutiveFailures);
            } catch (Exception e) {
                log.error("Error notifying listener of renewal failure", e);
            }
        }
    }

    private void notifyOwnershipLost(String executionKey, String reason) {
        for (ILeaseRenewalListener listener : listeners) {
            try {
                listener.onOwnershipLost(executionKey, reason);
            } catch (Exception e) {
                log.error("Error notifying listener of ownership lost", e);
            }
        }
    }

    // ========== Inner Classes ==========

    /**
     * Tracks an active renewal for a single workflow execution.
     */
    private class ActiveRenewal {
        private final String executionKey;
        private final ILeaseRenewalStrategy strategy;
        private final Sinks.Many<Boolean> renewalSignal;
        private final AtomicBoolean active;

        private volatile OwnershipContext currentContext;
        private volatile ILeaseRenewalStrategy.LeaseRenewalState state;
        private volatile Disposable renewalTask;
        private volatile Consumer<ILeaseRenewalStrategy.RenewalFailureAction> failureCallback;

        ActiveRenewal(String executionKey, OwnershipContext initialContext, ILeaseRenewalStrategy strategy) {
            this.executionKey = executionKey;
            this.currentContext = initialContext;
            this.strategy = strategy;
            this.state = ILeaseRenewalStrategy.LeaseRenewalState.initial(executionKey);
            this.renewalSignal = Sinks.many().multicast().onBackpressureBuffer();
            this.active = new AtomicBoolean(false);
        }

        void start() {
            if (!active.compareAndSet(false, true)) {
                return;
            }

            scheduleNextCheck();
        }

        void stop() {
            active.set(false);
            if (renewalTask != null) {
                renewalTask.dispose();
            }
        }

        OwnershipContext getCurrentContext() {
            return currentContext;
        }

        ILeaseRenewalStrategy.LeaseRenewalState getState() {
            return state;
        }

        void setFailureCallback(Consumer<ILeaseRenewalStrategy.RenewalFailureAction> callback) {
            this.failureCallback = callback;
        }

        Mono<Boolean> checkAndRenewIfNeeded() {
            if (!active.get() || currentContext == null) {
                return Mono.just(false);
            }

            if (strategy.shouldRenew(currentContext, state)) {
                return performRenewal();
            }

            return Mono.just(false);
        }

        Mono<Boolean> performRenewal() {
            if (!active.get()) {
                return Mono.just(false);
            }

            Instant startTime = Instant.now();
            Duration extensionDuration = strategy.getExtensionDuration(currentContext, state);

            log.debug("Performing lease renewal for {}", executionKey);
            metrics.recordRenewalAttempt();

            return performRenewalWithCoordinator(executionKey, extensionDuration)
                    .map(success -> {
                        Duration renewalTime = Duration.between(startTime, Instant.now());

                        if (success) {
                            handleRenewalSuccess(renewalTime, extensionDuration);
                            return true;
                        } else {
                            handleRenewalFailure("Renewal returned false");
                            return false;
                        }
                    })
                    .onErrorResume(error -> {
                        handleRenewalFailure(error.getMessage());
                        return Mono.just(false);
                    })
                    .doFinally(signal -> scheduleNextCheck());
        }

        private void handleRenewalSuccess(Duration renewalTime, Duration extensionDuration) {
            state = state.recordSuccess(renewalTime);

            // Update context with new expiration
            if (currentContext != null) {
                currentContext = currentContext.toBuilder()
                        .renewedAt(Instant.now())
                        .expiresAt(Instant.now().plus(extensionDuration))
                        .build();
            }

            metrics.recordRenewalSuccess(renewalTime);
            log.debug("Lease renewed for {}, new expiry: {}", executionKey,
                    currentContext != null ? currentContext.getExpiresAt() : "unknown");

            notifyRenewalSuccess(executionKey, currentContext);
        }

        private void handleRenewalFailure(String reason) {
            state = state.recordFailure();
            metrics.recordRenewalFailure();

            log.warn("Lease renewal failed for {}: {} (failures: {})",
                    executionKey, reason, state.consecutiveFailures());

            notifyRenewalFailure(executionKey, reason, state.consecutiveFailures());

            // Determine what action to take
            ILeaseRenewalStrategy.RenewalFailureAction action =
                    strategy.onRenewalFailure(currentContext, state, reason);

            switch (action) {
                case ABORT_EXECUTION:
                    log.error("Aborting execution {} due to renewal failures", executionKey);
                    notifyOwnershipLost(executionKey, "Renewal failed: " + reason);
                    if (failureCallback != null) {
                        failureCallback.accept(action);
                    }
                    stop();
                    break;

                case PAUSE_AND_REACQUIRE:
                    log.warn("Pausing execution {} for ownership re-acquisition", executionKey);
                    if (failureCallback != null) {
                        failureCallback.accept(action);
                    }
                    break;

                case RETRY_IMMEDIATELY:
                    // Will be retried in next check
                    state = state.markUrgent();
                    break;

                case RETRY_WITH_BACKOFF:
                case CONTINUE_URGENT:
                    // Continue with normal scheduling but mark as urgent
                    state = state.markUrgent();
                    break;
            }
        }

        private void scheduleNextCheck() {
            if (!active.get()) {
                return;
            }

            Duration delay = strategy.calculateNextCheckDelay(currentContext, state);

            renewalTask = Mono.delay(delay)
                    .flatMap(tick -> checkAndRenewIfNeeded())
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe();
        }
    }

    /**
     * Handle for managing an active renewal from outside.
     */
    public static class ActiveRenewalHandle {
        private final ActiveRenewal renewal;

        ActiveRenewalHandle(ActiveRenewal renewal) {
            this.renewal = renewal;
        }

        /**
         * Gets the current ownership context.
         */
        public OwnershipContext getCurrentContext() {
            return renewal.getCurrentContext();
        }

        /**
         * Gets the current fencing token.
         */
        public long getFencingToken() {
            OwnershipContext ctx = renewal.getCurrentContext();
            return ctx != null ? ctx.getFencingToken() : -1;
        }

        /**
         * Checks if the ownership is still valid.
         */
        public boolean isOwnershipValid() {
            OwnershipContext ctx = renewal.getCurrentContext();
            return ctx != null && ctx.isValid();
        }

        /**
         * Gets the renewal state.
         */
        public ILeaseRenewalStrategy.LeaseRenewalState getState() {
            return renewal.getState();
        }

        /**
         * Checks if renewal is urgent.
         */
        public boolean isUrgent() {
            return renewal.getState().urgentRenewalNeeded();
        }

        /**
         * Gets the remaining lease time.
         */
        public Optional<Duration> getRemainingTime() {
            OwnershipContext ctx = renewal.getCurrentContext();
            return ctx != null ? ctx.getRemainingTime() : Optional.empty();
        }

        /**
         * Triggers immediate renewal.
         */
        public Mono<Boolean> renewNow() {
            return renewal.performRenewal();
        }

        /**
         * Sets a callback for when renewal fails critically.
         */
        public void onFailure(Consumer<ILeaseRenewalStrategy.RenewalFailureAction> callback) {
            renewal.setFailureCallback(callback);
        }
    }

    /**
     * Listener for lease renewal events.
     */
    public interface ILeaseRenewalListener {
        void onRenewalStarted(String executionKey, OwnershipContext context);
        void onRenewalStopped(String executionKey, String reason);
        void onRenewalSuccess(String executionKey, OwnershipContext newContext);
        void onRenewalFailure(String executionKey, String reason, int consecutiveFailures);
        void onOwnershipLost(String executionKey, String reason);
    }
}
