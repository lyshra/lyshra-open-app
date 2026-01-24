package com.lyshra.open.app.distributed.membership;

/**
 * Listener interface for node health change events.
 *
 * Implementations receive notifications when node health status changes,
 * enabling reactive handling such as:
 * - Triggering failover procedures for failed nodes
 * - Updating load balancing decisions
 * - Alerting operations teams
 *
 * Design Pattern: Observer Pattern - decouples health events from handlers.
 */
@FunctionalInterface
public interface INodeHealthListener {

    /**
     * Called when a node's health status changes.
     *
     * @param event the health change event
     */
    void onHealthChange(NodeHealthEvent event);
}
