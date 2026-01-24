package com.lyshra.open.app.distributed.cluster;

/**
 * Listener interface for cluster leadership change events.
 *
 * Implementations receive notifications when leadership changes occur,
 * such as a new leader being elected or the current leader stepping down.
 * This is critical for coordinating cluster-wide operations like partition
 * rebalancing and ownership assignment.
 *
 * Design Pattern: Observer Pattern - decouples leadership events from handlers.
 */
@FunctionalInterface
public interface IClusterLeadershipListener {

    /**
     * Called when a leadership change occurs in the cluster.
     *
     * @param event the leadership change event
     */
    void onLeadershipChange(ClusterLeadershipEvent event);
}
