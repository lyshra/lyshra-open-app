package com.lyshra.open.app.distributed.coordination;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.util.Set;

/**
 * Options for customizing orphan workflow recovery behavior.
 *
 * This class configures how the coordinator should handle recovery
 * of orphaned workflows from failed nodes.
 *
 * Thread Safety: This class is immutable and thread-safe.
 */
@Getter
@Builder(toBuilder = true)
@ToString
public final class RecoveryOptions {

    /**
     * Default options instance.
     */
    public static final RecoveryOptions DEFAULT = RecoveryOptions.builder().build();

    /**
     * Maximum number of workflows to claim in one recovery operation.
     * Use to prevent overloading a single node during recovery.
     * Default: 100
     */
    @Builder.Default
    private final int maxClaimsPerOperation = 100;

    /**
     * Maximum time for the entire recovery operation.
     * Default: 5 minutes
     */
    @Builder.Default
    private final Duration recoveryTimeout = Duration.ofMinutes(5);

    /**
     * Delay between claiming individual workflows.
     * Helps prevent thundering herd during mass recovery.
     * Default: 10 milliseconds
     */
    @Builder.Default
    private final Duration claimDelay = Duration.ofMillis(10);

    /**
     * Whether to verify node death before claiming.
     * If true, double-checks that the node is really dead.
     * Default: true
     */
    @Builder.Default
    private final boolean verifyNodeDeath = true;

    /**
     * Timeout for node death verification.
     * Default: 5 seconds
     */
    @Builder.Default
    private final Duration verificationTimeout = Duration.ofSeconds(5);

    /**
     * Whether to respect partition assignment during recovery.
     * If true, only claims workflows that belong to this node's partitions.
     * Default: true
     */
    @Builder.Default
    private final boolean respectPartitionAssignment = true;

    /**
     * Minimum time since last heartbeat before considering a node dead.
     * Default: 30 seconds
     */
    @Builder.Default
    private final Duration deadNodeThreshold = Duration.ofSeconds(30);

    /**
     * Whether to claim workflows that are in error state.
     * Default: true
     */
    @Builder.Default
    private final boolean claimErroredWorkflows = true;

    /**
     * Whether to claim workflows that are paused.
     * Default: false
     */
    @Builder.Default
    private final boolean claimPausedWorkflows = false;

    /**
     * Specific partition IDs to recover from (empty = all assigned partitions).
     */
    @Builder.Default
    private final Set<Integer> partitionFilter = Set.of();

    /**
     * Priority for recovery claims (higher = more preferred).
     * Used when multiple nodes compete to recover the same workflows.
     * Default: 10 (elevated priority for recovery)
     */
    @Builder.Default
    private final int recoveryPriority = 10;

    /**
     * Whether to emit events for each claimed workflow.
     * Default: true
     */
    @Builder.Default
    private final boolean emitClaimEvents = true;

    /**
     * Whether to batch claims for efficiency.
     * Default: true
     */
    @Builder.Default
    private final boolean batchClaims = true;

    /**
     * Batch size when batching is enabled.
     * Default: 10
     */
    @Builder.Default
    private final int batchSize = 10;

    // ========== Factory Methods ==========

    /**
     * Creates options for aggressive recovery (maximum throughput).
     */
    public static RecoveryOptions aggressive() {
        return RecoveryOptions.builder()
                .maxClaimsPerOperation(500)
                .claimDelay(Duration.ZERO)
                .verifyNodeDeath(false)
                .batchSize(50)
                .build();
    }

    /**
     * Creates options for cautious recovery (prioritizes safety).
     */
    public static RecoveryOptions cautious() {
        return RecoveryOptions.builder()
                .maxClaimsPerOperation(20)
                .claimDelay(Duration.ofMillis(100))
                .verifyNodeDeath(true)
                .verificationTimeout(Duration.ofSeconds(10))
                .deadNodeThreshold(Duration.ofMinutes(1))
                .build();
    }

    /**
     * Creates options for partial recovery (specific partitions only).
     */
    public static RecoveryOptions forPartitions(Set<Integer> partitions) {
        return RecoveryOptions.builder()
                .partitionFilter(partitions)
                .build();
    }

    /**
     * Creates options with a specific claim limit.
     */
    public static RecoveryOptions withLimit(int maxClaims) {
        return RecoveryOptions.builder()
                .maxClaimsPerOperation(maxClaims)
                .build();
    }
}
