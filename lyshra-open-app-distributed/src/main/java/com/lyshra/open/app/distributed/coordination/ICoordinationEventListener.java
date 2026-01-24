package com.lyshra.open.app.distributed.coordination;

/**
 * Listener interface for coordination events.
 *
 * Implementations of this interface receive notifications about
 * ownership changes and coordination events. This enables reactive
 * handling of ownership transitions.
 *
 * Usage:
 * <pre>
 * coordinator.addCoordinationEventListener(event -> {
 *     switch (event.getType()) {
 *         case OWNERSHIP_ACQUIRED:
 *             startWorkflowExecution(event.getWorkflowKey());
 *             break;
 *         case OWNERSHIP_LOST:
 *             pauseWorkflowExecution(event.getWorkflowKey());
 *             break;
 *     }
 * });
 * </pre>
 *
 * Thread Safety: Implementations should be thread-safe as events may
 * be delivered from multiple threads concurrently.
 */
@FunctionalInterface
public interface ICoordinationEventListener {

    /**
     * Called when a coordination event occurs.
     *
     * Note: This method should return quickly. Long-running operations
     * should be offloaded to a separate thread or executor.
     *
     * @param event the coordination event
     */
    void onCoordinationEvent(CoordinationEvent event);
}
