package com.lyshra.open.app.distributed.ownership;

/**
 * Listener interface for workflow ownership change events.
 *
 * Implementations receive notifications when workflow ownership changes,
 * enabling reactive handling such as:
 * - Starting execution for newly acquired workflows
 * - Stopping execution for lost workflows
 * - Updating monitoring metrics
 *
 * Design Pattern: Observer Pattern - decouples ownership events from handlers.
 */
@FunctionalInterface
public interface IWorkflowOwnershipChangeListener {

    /**
     * Called when workflow ownership changes.
     *
     * @param event the ownership change event
     */
    void onOwnershipChange(WorkflowOwnershipChangeEvent event);
}
