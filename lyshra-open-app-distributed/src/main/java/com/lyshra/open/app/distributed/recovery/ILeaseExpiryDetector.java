package com.lyshra.open.app.distributed.recovery;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Interface for detecting expired ownership leases across the cluster.
 *
 * Implementations periodically scan for workflows whose ownership lease
 * has expired, making them candidates for recovery by other nodes.
 *
 * Key Responsibilities:
 * 1. Detect workflows with expired leases
 * 2. Identify failed/unresponsive nodes
 * 3. Provide expiry notifications to recovery components
 * 4. Track expiry metrics for monitoring
 *
 * Thread Safety: Implementations must be thread-safe.
 */
public interface ILeaseExpiryDetector {

    // ========== Lifecycle ==========

    /**
     * Starts the expiry detector.
     *
     * @return Mono that completes when started
     */
    Mono<Void> start();

    /**
     * Stops the expiry detector.
     *
     * @return Mono that completes when stopped
     */
    Mono<Void> stop();

    /**
     * Checks if the detector is running.
     *
     * @return true if running
     */
    boolean isRunning();

    // ========== Detection ==========

    /**
     * Scans for all workflows with expired leases.
     *
     * @return Flux of expired workflow execution keys
     */
    Flux<ExpiredLease> scanForExpiredLeases();

    /**
     * Scans for workflows that will expire within the given duration.
     *
     * @param window the time window to check
     * @return Flux of workflows expiring soon
     */
    Flux<ExpiredLease> scanForExpiringLeases(Duration window);

    /**
     * Checks if a specific workflow's lease has expired.
     *
     * @param workflowExecutionKey the workflow execution key
     * @return Mono containing true if expired
     */
    Mono<Boolean> isLeaseExpired(String workflowExecutionKey);

    /**
     * Gets the expiration time for a workflow.
     *
     * @param workflowExecutionKey the workflow execution key
     * @return Mono containing expiration instant, or empty if not found
     */
    Mono<Instant> getLeaseExpiration(String workflowExecutionKey);

    // ========== Node Detection ==========

    /**
     * Identifies nodes that appear to have failed (all leases expired).
     *
     * @return set of failed node IDs
     */
    Mono<Set<String>> detectFailedNodes();

    /**
     * Checks if a specific node appears to have failed.
     *
     * @param nodeId the node ID to check
     * @return Mono containing true if node appears failed
     */
    Mono<Boolean> isNodeFailed(String nodeId);

    /**
     * Gets all workflows owned by a potentially failed node.
     *
     * @param nodeId the node ID
     * @return Flux of workflow execution keys
     */
    Flux<String> getWorkflowsOwnedByNode(String nodeId);

    // ========== Listeners ==========

    /**
     * Registers a listener for expiry events.
     *
     * @param listener the listener to register
     */
    void addExpiryListener(ILeaseExpiryListener listener);

    /**
     * Removes an expiry listener.
     *
     * @param listener the listener to remove
     */
    void removeExpiryListener(ILeaseExpiryListener listener);

    // ========== Configuration ==========

    /**
     * Gets the current scan interval.
     *
     * @return the scan interval
     */
    Duration getScanInterval();

    /**
     * Updates the scan interval.
     *
     * @param interval the new scan interval
     */
    void setScanInterval(Duration interval);

    // ========== Metrics ==========

    /**
     * Gets the expiry detection metrics.
     *
     * @return the current metrics
     */
    ExpiryDetectionMetrics getMetrics();

    /**
     * Represents an expired or expiring lease.
     */
    record ExpiredLease(
            String workflowExecutionKey,
            String ownerId,
            int partitionId,
            long fencingToken,
            Instant acquiredAt,
            Instant expiredAt,
            Duration expiredFor,
            ExpiryReason reason
    ) {
        /**
         * Creates an expired lease record.
         */
        public static ExpiredLease of(String workflowKey, String ownerId, int partitionId,
                                       long fencingToken, Instant acquiredAt, Instant expiredAt,
                                       ExpiryReason reason) {
            Duration expiredFor = Duration.between(expiredAt, Instant.now());
            return new ExpiredLease(workflowKey, ownerId, partitionId, fencingToken,
                    acquiredAt, expiredAt, expiredFor, reason);
        }

        /**
         * Checks if this lease has been expired for longer than the threshold.
         */
        public boolean isExpiredLongerThan(Duration threshold) {
            return expiredFor.compareTo(threshold) > 0;
        }
    }

    /**
     * Reasons why a lease expired.
     */
    enum ExpiryReason {
        /**
         * Lease timed out without renewal.
         */
        TIMEOUT,

        /**
         * Owner node failed/crashed.
         */
        NODE_FAILURE,

        /**
         * Owner node became unresponsive.
         */
        NODE_UNRESPONSIVE,

        /**
         * Explicit release without cleanup.
         */
        ABANDONED,

        /**
         * Network partition caused expiry.
         */
        NETWORK_PARTITION,

        /**
         * Unknown reason.
         */
        UNKNOWN
    }

    /**
     * Listener for lease expiry events.
     */
    interface ILeaseExpiryListener {
        /**
         * Called when a lease expires.
         */
        void onLeaseExpired(ExpiredLease expired);

        /**
         * Called when multiple leases expire (batch notification).
         */
        void onLeasesExpired(Set<ExpiredLease> expired);

        /**
         * Called when a node is detected as failed.
         */
        void onNodeFailureDetected(String nodeId, Set<String> affectedWorkflows);
    }

    /**
     * Metrics for expiry detection.
     */
    interface ExpiryDetectionMetrics {
        long getTotalScans();
        long getExpiredLeasesDetected();
        long getFailedNodesDetected();
        Duration getAverageExpiryDetectionTime();
        Instant getLastScanTime();
    }
}
