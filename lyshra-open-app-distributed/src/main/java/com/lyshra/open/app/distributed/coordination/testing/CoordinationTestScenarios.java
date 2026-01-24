package com.lyshra.open.app.distributed.coordination.testing;

import com.lyshra.open.app.distributed.coordination.AcquisitionOptions;
import com.lyshra.open.app.distributed.coordination.CoordinationResult;
import com.lyshra.open.app.distributed.coordination.OrphanRecoveryResult;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/**
 * Pre-built test scenarios for common coordination test cases.
 *
 * These scenarios encapsulate common patterns for testing distributed
 * coordination, reducing boilerplate in test code.
 *
 * Usage:
 * <pre>
 * // Run a simple acquisition test
 * CoordinationTestScenarios.simpleAcquisitionTest(coordinator -> {
 *     // Your assertions here
 * });
 *
 * // Run a failover test
 * CoordinationTestScenarios.nodeFailoverTest(3, result -> {
 *     assertTrue(result.isSuccessful());
 * });
 * </pre>
 */
@Slf4j
public final class CoordinationTestScenarios {

    private CoordinationTestScenarios() {
        // Utility class
    }

    // ========== Single Node Scenarios ==========

    /**
     * Scenario: Basic ownership acquisition and release.
     */
    public static void simpleAcquisitionTest(Consumer<TestableOwnershipCoordinator> test) {
        TestableOwnershipCoordinator coordinator = TestableOwnershipCoordinator.builder()
                .nodeId("test-node")
                .totalPartitions(16)
                .assignAllPartitions()
                .defaultLeaseDuration(Duration.ofMinutes(5))
                .build();

        try {
            coordinator.initialize().block();
            test.accept(coordinator);
        } finally {
            coordinator.shutdown().block();
        }
    }

    /**
     * Scenario: Lease expiration and renewal.
     */
    public static void leaseExpirationTest(Consumer<TestableOwnershipCoordinator> test) {
        TestableOwnershipCoordinator coordinator = TestableOwnershipCoordinator.builder()
                .nodeId("test-node")
                .totalPartitions(16)
                .assignAllPartitions()
                .defaultLeaseDuration(Duration.ofMinutes(1))
                .build();

        try {
            coordinator.initialize().block();
            // Use simulated time for predictable expiration
            coordinator.setSimulatedTime(java.time.Instant.now());
            test.accept(coordinator);
        } finally {
            coordinator.shutdown().block();
        }
    }

    /**
     * Scenario: Failure injection test.
     */
    public static void failureInjectionTest(Consumer<TestableOwnershipCoordinator> test) {
        TestableOwnershipCoordinator coordinator = TestableOwnershipCoordinator.builder()
                .nodeId("test-node")
                .totalPartitions(16)
                .assignAllPartitions()
                .build();

        try {
            coordinator.initialize().block();
            test.accept(coordinator);
        } finally {
            coordinator.clearInjectedFailures();
            coordinator.shutdown().block();
        }
    }

    // ========== Multi-Node Scenarios ==========

    /**
     * Scenario: Basic multi-node cluster.
     *
     * @param nodeCount number of nodes in the cluster
     * @param test the test to run
     */
    public static void multiNodeClusterTest(int nodeCount, Consumer<SimulatedClusterEnvironment> test) {
        SimulatedClusterEnvironment cluster = SimulatedClusterEnvironment.builder()
                .totalPartitions(16)
                .defaultLeaseDuration(Duration.ofMinutes(5))
                .build();

        try {
            for (int i = 0; i < nodeCount; i++) {
                cluster.addNode("node-" + i);
            }
            cluster.distributePartitionsEvenly();
            cluster.start();

            test.accept(cluster);
        } finally {
            cluster.stop();
        }
    }

    /**
     * Scenario: Node failure and recovery.
     */
    public static ScenarioResult nodeFailoverTest(int nodeCount, Consumer<ScenarioResult> verification) {
        SimulatedClusterEnvironment cluster = SimulatedClusterEnvironment.builder()
                .totalPartitions(16)
                .defaultLeaseDuration(Duration.ofMinutes(5))
                .build();

        ScenarioResult result = new ScenarioResult();

        try {
            // Setup cluster
            for (int i = 0; i < nodeCount; i++) {
                cluster.addNode("node-" + i);
            }
            cluster.distributePartitionsEvenly();
            cluster.start();

            // Acquire some workflows on node-0
            TestableOwnershipCoordinator node0 = cluster.getCoordinator("node-0");
            List<String> acquiredWorkflows = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                String workflowKey = "workflow-" + i;
                CoordinationResult acqResult = node0.acquireOwnership(workflowKey).block();
                if (acqResult.isAcquired()) {
                    acquiredWorkflows.add(workflowKey);
                }
            }
            result.put("initiallyAcquired", acquiredWorkflows);
            result.put("originalOwner", "node-0");

            // Simulate node failure
            cluster.simulateNodeFailure("node-0");
            result.put("failedNode", "node-0");

            // Advance time to expire leases
            cluster.advanceTime(Duration.ofMinutes(6));

            // Trigger recovery from other nodes
            Map<String, OrphanRecoveryResult> recoveryResults = cluster.triggerOrphanRecovery("node-0");
            result.put("recoveryResults", recoveryResults);

            // Count total recovered
            int totalRecovered = recoveryResults.values().stream()
                    .mapToInt(OrphanRecoveryResult::getSuccessfulClaims)
                    .sum();
            result.put("totalRecovered", totalRecovered);

            // Verify no split brain
            result.put("noSplitBrain", cluster.verifySingleOwnership());

            if (verification != null) {
                verification.accept(result);
            }

        } finally {
            cluster.stop();
        }

        return result;
    }

    /**
     * Scenario: Network partition and healing.
     */
    public static ScenarioResult networkPartitionTest(Consumer<ScenarioResult> verification) {
        SimulatedClusterEnvironment cluster = SimulatedClusterEnvironment.builder()
                .totalPartitions(16)
                .defaultLeaseDuration(Duration.ofMinutes(5))
                .build();

        ScenarioResult result = new ScenarioResult();

        try {
            // Setup 3-node cluster
            cluster.addNode("node-0");
            cluster.addNode("node-1");
            cluster.addNode("node-2");
            cluster.distributePartitionsEvenly();
            cluster.start();

            // Acquire workflows
            TestableOwnershipCoordinator node0 = cluster.getCoordinator("node-0");
            List<CoordinationResult> acquisitions = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                acquisitions.add(node0.acquireOwnership("workflow-" + i).block());
            }
            result.put("initialAcquisitions", acquisitions);

            // Isolate node-0
            cluster.isolateNode("node-0");
            result.put("isolatedNode", "node-0");

            // Try acquisition from isolated node (should fail)
            CoordinationResult isolatedAcquisition = node0.acquireOwnership("new-workflow").block();
            result.put("isolatedAcquisitionResult", isolatedAcquisition);

            // Heal the partition
            cluster.healNodePartition("node-0");
            result.put("partitionHealed", true);

            // Acquisition should work again
            CoordinationResult healedAcquisition = node0.acquireOwnership("healed-workflow").block();
            result.put("healedAcquisitionResult", healedAcquisition);

            if (verification != null) {
                verification.accept(result);
            }

        } finally {
            cluster.stop();
        }

        return result;
    }

    /**
     * Scenario: Concurrent acquisition from multiple nodes.
     */
    public static ScenarioResult concurrentAcquisitionTest(int nodeCount, int workflowCount,
                                                            Consumer<ScenarioResult> verification) {
        SimulatedClusterEnvironment cluster = SimulatedClusterEnvironment.builder()
                .totalPartitions(16)
                .defaultLeaseDuration(Duration.ofMinutes(5))
                .build();

        ScenarioResult result = new ScenarioResult();

        try {
            // Setup cluster - all nodes can handle all partitions
            for (int i = 0; i < nodeCount; i++) {
                cluster.addNode("node-" + i);
            }
            cluster.start();

            // Generate workflow keys
            List<String> workflowKeys = new ArrayList<>();
            for (int i = 0; i < workflowCount; i++) {
                workflowKeys.add("workflow-" + i);
            }

            // All nodes try to acquire all workflows
            Map<String, Map<String, CoordinationResult>> acquisitionsByNode = new HashMap<>();

            for (int n = 0; n < nodeCount; n++) {
                String nodeId = "node-" + n;
                TestableOwnershipCoordinator coordinator = cluster.getCoordinator(nodeId);
                Map<String, CoordinationResult> nodeAcquisitions = new HashMap<>();

                for (String workflowKey : workflowKeys) {
                    CoordinationResult acqResult = coordinator.acquireOwnership(workflowKey).block();
                    nodeAcquisitions.put(workflowKey, acqResult);
                }

                acquisitionsByNode.put(nodeId, nodeAcquisitions);
            }

            result.put("acquisitionsByNode", acquisitionsByNode);

            // Verify each workflow has exactly one owner
            boolean singleOwnership = cluster.verifySingleOwnership();
            result.put("singleOwnership", singleOwnership);

            // Count successful acquisitions per node
            Map<String, Integer> successCountByNode = new HashMap<>();
            for (var entry : acquisitionsByNode.entrySet()) {
                int successCount = (int) entry.getValue().values().stream()
                        .filter(CoordinationResult::isAcquired)
                        .count();
                successCountByNode.put(entry.getKey(), successCount);
            }
            result.put("successCountByNode", successCountByNode);

            if (verification != null) {
                verification.accept(result);
            }

        } finally {
            cluster.stop();
        }

        return result;
    }

    /**
     * Scenario: Lease renewal under load.
     */
    public static ScenarioResult leaseRenewalTest(int workflowCount, Duration testDuration,
                                                   Consumer<ScenarioResult> verification) {
        TestableOwnershipCoordinator coordinator = TestableOwnershipCoordinator.builder()
                .nodeId("test-node")
                .totalPartitions(16)
                .assignAllPartitions()
                .defaultLeaseDuration(Duration.ofMinutes(1))
                .build();

        ScenarioResult result = new ScenarioResult();

        try {
            coordinator.initialize().block();
            coordinator.setSimulatedTime(java.time.Instant.now());

            // Acquire workflows
            List<String> acquiredWorkflows = new ArrayList<>();
            for (int i = 0; i < workflowCount; i++) {
                String workflowKey = "workflow-" + i;
                CoordinationResult acqResult = coordinator.acquireOwnership(workflowKey).block();
                if (acqResult.isAcquired()) {
                    acquiredWorkflows.add(workflowKey);
                }
            }
            result.put("acquiredWorkflows", acquiredWorkflows);

            // Simulate time passing with periodic renewals
            int renewalCycles = 0;
            Duration elapsed = Duration.ZERO;
            Duration renewInterval = Duration.ofSeconds(30);

            while (elapsed.compareTo(testDuration) < 0) {
                coordinator.advanceTime(renewInterval);
                elapsed = elapsed.plus(renewInterval);

                Integer renewed = coordinator.renewAllOwnedWorkflows().block();
                renewalCycles++;
                result.put("renewalCycle_" + renewalCycles, renewed);
            }

            result.put("totalRenewalCycles", renewalCycles);
            result.put("finalOwnershipCount", coordinator.getLocalOwnershipCount());

            // Verify all workflows still owned
            int stillOwned = 0;
            for (String workflowKey : acquiredWorkflows) {
                if (coordinator.isOwnedLocally(workflowKey)) {
                    stillOwned++;
                }
            }
            result.put("stillOwned", stillOwned);
            result.put("allRetained", stillOwned == acquiredWorkflows.size());

            if (verification != null) {
                verification.accept(result);
            }

        } finally {
            coordinator.shutdown().block();
        }

        return result;
    }

    /**
     * Scenario: Rolling restart of cluster nodes.
     */
    public static ScenarioResult rollingRestartTest(int nodeCount, int workflowsPerNode,
                                                     Consumer<ScenarioResult> verification) {
        SimulatedClusterEnvironment cluster = SimulatedClusterEnvironment.builder()
                .totalPartitions(16)
                .defaultLeaseDuration(Duration.ofMinutes(5))
                .build();

        ScenarioResult result = new ScenarioResult();

        try {
            // Setup cluster
            for (int i = 0; i < nodeCount; i++) {
                cluster.addNode("node-" + i);
            }
            cluster.distributePartitionsEvenly();
            cluster.start();

            // Acquire workflows on each node
            Map<String, List<String>> originalOwnership = new HashMap<>();
            int workflowId = 0;
            for (int n = 0; n < nodeCount; n++) {
                String nodeId = "node-" + n;
                TestableOwnershipCoordinator coordinator = cluster.getCoordinator(nodeId);
                List<String> nodeWorkflows = new ArrayList<>();

                for (int w = 0; w < workflowsPerNode; w++) {
                    String workflowKey = "workflow-" + (workflowId++);
                    if (coordinator.acquireOwnership(workflowKey).block().isAcquired()) {
                        nodeWorkflows.add(workflowKey);
                    }
                }
                originalOwnership.put(nodeId, nodeWorkflows);
            }
            result.put("originalOwnership", originalOwnership);

            int initialTotal = cluster.getTotalOwnershipCount();
            result.put("initialTotalOwnership", initialTotal);

            // Rolling restart each node
            for (int n = 0; n < nodeCount; n++) {
                String nodeId = "node-" + n;

                // Remove node (graceful)
                cluster.removeNode(nodeId);

                // Give time for others to notice
                cluster.advanceTime(Duration.ofMinutes(6));

                // Trigger recovery
                Map<String, OrphanRecoveryResult> recovery = new HashMap<>();
                for (var entry : cluster.getAllCoordinators().entrySet()) {
                    if (entry.getValue().isActive()) {
                        OrphanRecoveryResult recResult = entry.getValue()
                                .claimOrphanedWorkflows(nodeId,
                                        com.lyshra.open.app.distributed.coordination.RecoveryOptions.DEFAULT)
                                .block();
                        recovery.put(entry.getKey(), recResult);
                    }
                }
                result.put("recovery_" + nodeId, recovery);

                // Add node back
                cluster.addNode(nodeId, cluster.getNodePartitions(nodeId));

                // Verify single ownership
                if (!cluster.verifySingleOwnership()) {
                    result.put("splitBrainAfterRestart_" + nodeId, true);
                }
            }

            int finalTotal = cluster.getTotalOwnershipCount();
            result.put("finalTotalOwnership", finalTotal);
            result.put("ownershipPreserved", finalTotal >= initialTotal * 0.9); // Allow some loss

            if (verification != null) {
                verification.accept(result);
            }

        } finally {
            cluster.stop();
        }

        return result;
    }

    // ========== Scenario Result ==========

    /**
     * Container for scenario results with flexible key-value storage.
     */
    public static class ScenarioResult {
        private final Map<String, Object> data = new LinkedHashMap<>();

        public void put(String key, Object value) {
            data.put(key, value);
        }

        @SuppressWarnings("unchecked")
        public <T> T get(String key) {
            return (T) data.get(key);
        }

        public <T> T get(String key, T defaultValue) {
            Object value = data.get(key);
            return value != null ? (T) value : defaultValue;
        }

        public boolean getBoolean(String key) {
            return Boolean.TRUE.equals(data.get(key));
        }

        public int getInt(String key) {
            Object value = data.get(key);
            return value instanceof Number ? ((Number) value).intValue() : 0;
        }

        public Map<String, Object> asMap() {
            return Collections.unmodifiableMap(data);
        }

        @Override
        public String toString() {
            return "ScenarioResult" + data;
        }
    }
}
