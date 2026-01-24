package com.lyshra.open.app.integration.contract.signal;

import reactor.core.publisher.Mono;

/**
 * Handler interface for processing external signals.
 * Implementations handle the logic of applying signals to workflows and tasks.
 *
 * <p>Design Pattern: Chain of Responsibility combined with Strategy
 * - Multiple handlers can be chained to process different signal types
 * - Each handler implements a strategy for a specific signal type
 *
 * <p>Implementations must be thread-safe and idempotent where possible.
 */
public interface ILyshraOpenAppSignalHandler {

    /**
     * Checks if this handler can process the given signal.
     *
     * @param signal the signal to check
     * @return true if this handler can process the signal
     */
    boolean canHandle(ILyshraOpenAppSignal signal);

    /**
     * Processes the signal and returns the result.
     *
     * @param signal the signal to process
     * @return the processing result
     */
    Mono<ILyshraOpenAppSignalResult> handle(ILyshraOpenAppSignal signal);

    /**
     * Returns the priority of this handler.
     * Lower values indicate higher priority.
     */
    default int getPriority() {
        return 100;
    }

    /**
     * Returns the handler identifier for logging/monitoring.
     */
    String getHandlerId();
}
