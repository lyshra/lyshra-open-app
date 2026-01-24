package com.lyshra.open.app.distributed.cluster;

/**
 * Listener interface for cluster membership change events.
 *
 * Implementations receive notifications when nodes join or leave the cluster,
 * enabling reactive handling of topology changes such as rebalancing workflow
 * ownership or triggering failover procedures.
 *
 * Design Pattern: Observer Pattern - decouples event producers from consumers.
 */
@FunctionalInterface
public interface IClusterMembershipListener {

    /**
     * Called when a cluster membership change occurs.
     *
     * @param event the membership change event
     */
    void onMembershipChange(ClusterMembershipEvent event);
}
