package com.lyshra.open.app.distributed.coordination.testing;

import com.lyshra.open.app.distributed.coordination.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Assertion utilities for testing coordination behavior.
 *
 * These assertions provide clear failure messages and are designed
 * for use in unit and integration tests.
 *
 * Usage:
 * <pre>
 * CoordinationResult result = coordinator.acquireOwnership("workflow-1").block();
 * CoordinationTestAssertions.assertAcquired(result);
 * CoordinationTestAssertions.assertOwnedBy(coordinator, "workflow-1", "node-1");
 * </pre>
 */
public final class CoordinationTestAssertions {

    private CoordinationTestAssertions() {
        // Utility class
    }

    // ========== Result Assertions ==========

    /**
     * Asserts that ownership was successfully acquired.
     */
    public static void assertAcquired(CoordinationResult result) {
        if (result == null) {
            throw new AssertionError("CoordinationResult is null");
        }
        if (!result.isAcquired()) {
            throw new AssertionError(String.format(
                    "Expected ownership to be acquired, but status was %s. Error: %s",
                    result.getStatus(), result.getErrorMessageOptional().orElse("none")));
        }
    }

    /**
     * Asserts that ownership was acquired with specific fencing token.
     */
    public static void assertAcquiredWithToken(CoordinationResult result, long minFencingToken) {
        assertAcquired(result);
        if (result.getFencingToken() < minFencingToken) {
            throw new AssertionError(String.format(
                    "Expected fencing token >= %d, but was %d",
                    minFencingToken, result.getFencingToken()));
        }
    }

    /**
     * Asserts that ownership acquisition failed because already owned.
     */
    public static void assertAlreadyOwned(CoordinationResult result) {
        if (result == null) {
            throw new AssertionError("CoordinationResult is null");
        }
        if (!result.isAlreadyOwned()) {
            throw new AssertionError(String.format(
                    "Expected already owned, but status was %s", result.getStatus()));
        }
    }

    /**
     * Asserts that ownership acquisition failed due to wrong partition.
     */
    public static void assertWrongPartition(CoordinationResult result) {
        if (result == null) {
            throw new AssertionError("CoordinationResult is null");
        }
        if (!result.isWrongPartition()) {
            throw new AssertionError(String.format(
                    "Expected wrong partition, but status was %s", result.getStatus()));
        }
    }

    /**
     * Asserts that the result indicates an error.
     */
    public static void assertError(CoordinationResult result) {
        if (result == null) {
            throw new AssertionError("CoordinationResult is null");
        }
        if (!result.isError()) {
            throw new AssertionError(String.format(
                    "Expected error, but status was %s", result.getStatus()));
        }
    }

    /**
     * Asserts that the result has a specific status.
     */
    public static void assertStatus(CoordinationResult result, CoordinationResult.Status expected) {
        if (result == null) {
            throw new AssertionError("CoordinationResult is null");
        }
        if (result.getStatus() != expected) {
            throw new AssertionError(String.format(
                    "Expected status %s, but was %s", expected, result.getStatus()));
        }
    }

    // ========== Ownership Assertions ==========

    /**
     * Asserts that a workflow is owned by a specific node.
     */
    public static void assertOwnedBy(TestableOwnershipCoordinator coordinator,
                                      String workflowKey, String expectedOwner) {
        String actualOwner = coordinator.getOwner(workflowKey).block();
        if (!expectedOwner.equals(actualOwner)) {
            throw new AssertionError(String.format(
                    "Expected workflow '%s' to be owned by '%s', but was owned by '%s'",
                    workflowKey, expectedOwner, actualOwner));
        }
    }

    /**
     * Asserts that a workflow is owned locally.
     */
    public static void assertOwnedLocally(TestableOwnershipCoordinator coordinator, String workflowKey) {
        if (!coordinator.isOwnedLocally(workflowKey)) {
            String owner = coordinator.getOwner(workflowKey).block();
            throw new AssertionError(String.format(
                    "Expected workflow '%s' to be owned locally by '%s', but owned by '%s'",
                    workflowKey, coordinator.getNodeId(), owner));
        }
    }

    /**
     * Asserts that a workflow is not owned.
     */
    public static void assertNotOwned(TestableOwnershipCoordinator coordinator, String workflowKey) {
        String owner = coordinator.getOwner(workflowKey).block();
        if (owner != null) {
            throw new AssertionError(String.format(
                    "Expected workflow '%s' to not be owned, but owned by '%s'",
                    workflowKey, owner));
        }
    }

    /**
     * Asserts that a workflow is orphaned.
     */
    public static void assertOrphaned(TestableOwnershipCoordinator coordinator, String workflowKey) {
        Boolean orphaned = coordinator.isOrphaned(workflowKey).block();
        if (!Boolean.TRUE.equals(orphaned)) {
            throw new AssertionError(String.format(
                    "Expected workflow '%s' to be orphaned, but it is not", workflowKey));
        }
    }

    /**
     * Asserts the ownership count for a coordinator.
     */
    public static void assertOwnershipCount(TestableOwnershipCoordinator coordinator, int expected) {
        int actual = coordinator.getLocalOwnershipCount();
        if (actual != expected) {
            throw new AssertionError(String.format(
                    "Expected ownership count %d, but was %d. Owned: %s",
                    expected, actual, coordinator.getLocallyOwnedWorkflows()));
        }
    }

    // ========== Lease Assertions ==========

    /**
     * Asserts that a lease is valid.
     */
    public static void assertLeaseValid(TestableOwnershipCoordinator coordinator, String workflowKey) {
        var lease = coordinator.getLeaseRecord(workflowKey);
        if (lease.isEmpty()) {
            throw new AssertionError(String.format(
                    "Expected lease for '%s' to exist", workflowKey));
        }
        if (!lease.get().isValid(coordinator.now())) {
            throw new AssertionError(String.format(
                    "Expected lease for '%s' to be valid, but it is expired", workflowKey));
        }
    }

    /**
     * Asserts that a lease has expired.
     */
    public static void assertLeaseExpired(TestableOwnershipCoordinator coordinator, String workflowKey) {
        var lease = coordinator.getLeaseRecord(workflowKey);
        if (lease.isEmpty()) {
            return; // No lease = effectively expired
        }
        if (lease.get().isValid(coordinator.now())) {
            throw new AssertionError(String.format(
                    "Expected lease for '%s' to be expired, but it is still valid until %s",
                    workflowKey, lease.get().getExpiresAt()));
        }
    }

    /**
     * Asserts that a lease expires within a certain duration.
     */
    public static void assertLeaseExpiresWithin(TestableOwnershipCoordinator coordinator,
                                                 String workflowKey, Duration maxDuration) {
        var lease = coordinator.getLeaseRecord(workflowKey);
        if (lease.isEmpty()) {
            throw new AssertionError(String.format(
                    "Expected lease for '%s' to exist", workflowKey));
        }

        Instant now = coordinator.now();
        Instant deadline = now.plus(maxDuration);
        Instant expiresAt = lease.get().getExpiresAt();

        if (expiresAt.isAfter(deadline)) {
            throw new AssertionError(String.format(
                    "Expected lease for '%s' to expire within %s, but expires at %s",
                    workflowKey, maxDuration, expiresAt));
        }
    }

    // ========== Event Assertions ==========

    /**
     * Asserts that a specific event type was emitted.
     */
    public static void assertEventEmitted(TestableOwnershipCoordinator coordinator,
                                           CoordinationEvent.EventType expectedType) {
        List<CoordinationEvent> events = coordinator.getCapturedEvents(expectedType);
        if (events.isEmpty()) {
            throw new AssertionError(String.format(
                    "Expected event of type %s to be emitted, but none found. All events: %s",
                    expectedType, coordinator.getCapturedEvents()));
        }
    }

    /**
     * Asserts that no events of a specific type were emitted.
     */
    public static void assertNoEventEmitted(TestableOwnershipCoordinator coordinator,
                                             CoordinationEvent.EventType type) {
        List<CoordinationEvent> events = coordinator.getCapturedEvents(type);
        if (!events.isEmpty()) {
            throw new AssertionError(String.format(
                    "Expected no events of type %s, but found %d: %s",
                    type, events.size(), events));
        }
    }

    /**
     * Asserts the event count for a specific type.
     */
    public static void assertEventCount(TestableOwnershipCoordinator coordinator,
                                         CoordinationEvent.EventType type, int expected) {
        int actual = coordinator.getCapturedEvents(type).size();
        if (actual != expected) {
            throw new AssertionError(String.format(
                    "Expected %d events of type %s, but found %d",
                    expected, type, actual));
        }
    }

    // ========== Cluster Assertions ==========

    /**
     * Asserts that only one node owns a workflow across the cluster.
     */
    public static void assertSingleOwner(SimulatedClusterEnvironment cluster, String workflowKey) {
        int ownerCount = 0;
        String owner = null;

        for (var entry : cluster.getAllCoordinators().entrySet()) {
            if (entry.getValue().isOwnedLocally(workflowKey)) {
                ownerCount++;
                owner = entry.getKey();
            }
        }

        if (ownerCount == 0) {
            throw new AssertionError(String.format(
                    "Expected exactly one owner for '%s', but found none", workflowKey));
        }
        if (ownerCount > 1) {
            throw new AssertionError(String.format(
                    "Expected exactly one owner for '%s', but found %d (split-brain!)",
                    workflowKey, ownerCount));
        }
    }

    /**
     * Asserts no split-brain in the cluster.
     */
    public static void assertNoSplitBrain(SimulatedClusterEnvironment cluster) {
        if (!cluster.verifySingleOwnership()) {
            throw new AssertionError("Split-brain detected! Multiple nodes own the same workflow.");
        }
    }

    /**
     * Asserts the total ownership count across the cluster.
     */
    public static void assertClusterOwnershipCount(SimulatedClusterEnvironment cluster, int expected) {
        int actual = cluster.getTotalOwnershipCount();
        if (actual != expected) {
            throw new AssertionError(String.format(
                    "Expected cluster ownership count %d, but was %d", expected, actual));
        }
    }

    /**
     * Asserts that a specific node is active.
     */
    public static void assertNodeActive(SimulatedClusterEnvironment cluster, String nodeId) {
        var coordinator = cluster.getCoordinator(nodeId);
        if (coordinator == null) {
            throw new AssertionError(String.format("Node '%s' does not exist", nodeId));
        }
        if (!coordinator.isActive()) {
            throw new AssertionError(String.format("Expected node '%s' to be active", nodeId));
        }
    }

    /**
     * Asserts that a specific node is inactive.
     */
    public static void assertNodeInactive(SimulatedClusterEnvironment cluster, String nodeId) {
        var coordinator = cluster.getCoordinator(nodeId);
        if (coordinator == null) {
            return; // Non-existent = inactive
        }
        if (coordinator.isActive()) {
            throw new AssertionError(String.format("Expected node '%s' to be inactive", nodeId));
        }
    }

    // ========== Fencing Token Assertions ==========

    /**
     * Asserts that fencing token increased.
     */
    public static void assertFencingTokenIncreased(long before, long after) {
        if (after <= before) {
            throw new AssertionError(String.format(
                    "Expected fencing token to increase from %d, but was %d",
                    before, after));
        }
    }

    /**
     * Asserts that the current fencing token is valid (>= minimum).
     */
    public static void assertValidFencingToken(TestableOwnershipCoordinator coordinator,
                                                String workflowKey, long minimumToken) {
        Long currentToken = coordinator.getFencingToken(workflowKey).block();
        if (currentToken == null || currentToken < minimumToken) {
            throw new AssertionError(String.format(
                    "Expected fencing token >= %d for '%s', but was %d",
                    minimumToken, workflowKey, currentToken));
        }
    }

    // ========== Recovery Assertions ==========

    /**
     * Asserts that orphan recovery was successful.
     */
    public static void assertRecoverySuccessful(OrphanRecoveryResult result) {
        if (result == null) {
            throw new AssertionError("OrphanRecoveryResult is null");
        }
        if (!result.isSuccessful()) {
            throw new AssertionError(String.format(
                    "Expected recovery to be successful, but: %s",
                    result.getErrorMessageOptional().orElse("unknown error")));
        }
    }

    /**
     * Asserts that a specific number of workflows were recovered.
     */
    public static void assertRecoveredCount(OrphanRecoveryResult result, int expected) {
        if (result == null) {
            throw new AssertionError("OrphanRecoveryResult is null");
        }
        if (result.getSuccessfulClaims() != expected) {
            throw new AssertionError(String.format(
                    "Expected %d workflows to be recovered, but was %d. Claimed: %s",
                    expected, result.getSuccessfulClaims(), result.getClaimedWorkflows()));
        }
    }

    /**
     * Asserts that a specific workflow was recovered.
     */
    public static void assertWorkflowRecovered(OrphanRecoveryResult result, String workflowKey) {
        if (result == null) {
            throw new AssertionError("OrphanRecoveryResult is null");
        }
        if (!result.getClaimedWorkflows().contains(workflowKey)) {
            throw new AssertionError(String.format(
                    "Expected workflow '%s' to be recovered, but it was not. Claimed: %s",
                    workflowKey, result.getClaimedWorkflows()));
        }
    }
}
