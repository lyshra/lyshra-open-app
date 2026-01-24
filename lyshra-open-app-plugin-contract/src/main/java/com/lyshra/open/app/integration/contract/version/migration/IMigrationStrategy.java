package com.lyshra.open.app.integration.contract.version.migration;

/**
 * Defines the strategy for migrating in-flight workflow executions.
 * Each strategy provides different guarantees and trade-offs.
 *
 * <p>Design Pattern: Strategy pattern for pluggable migration behaviors.</p>
 */
public enum IMigrationStrategy {

    /**
     * Never migrate in-flight executions.
     * Executions always complete on the version they started with.
     * Safest option but prevents urgent fixes from taking effect immediately.
     */
    FROZEN,

    /**
     * Migrate only at safe migration points.
     * Execution continues until reaching a designated safe point,
     * then migrates to the new version before proceeding.
     */
    OPPORTUNISTIC,

    /**
     * Force immediate migration at next step boundary.
     * Use with caution - may cause data inconsistencies if not properly handled.
     */
    IMMEDIATE,

    /**
     * Pause execution and wait for manual migration approval.
     * Suitable for regulated environments requiring human oversight.
     */
    MANUAL_APPROVAL,

    /**
     * Run parallel executions on both versions for comparison.
     * Used for testing and validation before full migration.
     */
    SHADOW
}
