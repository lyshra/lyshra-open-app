package com.lyshra.open.app.distributed.sharding;

/**
 * Listener interface for partition assignment change events.
 *
 * Implementations receive notifications when partition assignments change,
 * enabling reactive handling such as:
 * - Starting/stopping workflow processing for gained/lost partitions
 * - Updating local caches
 * - Triggering ownership handoff procedures
 *
 * Design Pattern: Observer Pattern - decouples partition events from handlers.
 */
@FunctionalInterface
public interface IPartitionChangeListener {

    /**
     * Called when partition assignments change.
     *
     * @param event the partition change event
     */
    void onPartitionChange(PartitionChangeEvent event);
}
