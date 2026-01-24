package com.lyshra.open.app.distributed.config;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for distributed workflow execution.
 *
 * Encapsulates all tunable parameters for the distributed execution system.
 * Uses the Builder pattern for flexible configuration construction.
 */
@Getter
@Builder
@ToString
public final class DistributedConfig {

    // Node configuration
    private final String nodeId;
    private final String hostname;
    private final int port;

    // Partition configuration
    @Builder.Default
    private final int totalPartitions = 256;

    @Builder.Default
    private final String shardingStrategy = "consistent-hash";

    // Timing configuration
    @Builder.Default
    private final Duration heartbeatInterval = Duration.ofSeconds(5);

    @Builder.Default
    private final Duration failureDetectionTimeout = Duration.ofSeconds(30);

    @Builder.Default
    private final Duration ownershipLeaseDuration = Duration.ofMinutes(5);

    @Builder.Default
    private final Duration leaseRenewalInterval = Duration.ofMinutes(1);

    @Builder.Default
    private final Duration lockTimeout = Duration.ofSeconds(30);

    @Builder.Default
    private final Duration staleExecutionThreshold = Duration.ofMinutes(10);

    // Retry configuration
    @Builder.Default
    private final int maxRetries = 3;

    @Builder.Default
    private final Duration initialRetryDelay = Duration.ofSeconds(1);

    @Builder.Default
    private final Duration maxRetryDelay = Duration.ofMinutes(1);

    @Builder.Default
    private final double retryBackoffMultiplier = 2.0;

    // Checkpoint configuration
    @Builder.Default
    private final boolean checkpointingEnabled = true;

    @Builder.Default
    private final Duration checkpointInterval = Duration.ofMinutes(1);

    // Recovery configuration
    @Builder.Default
    private final boolean autoRecoveryEnabled = true;

    @Builder.Default
    private final Duration recoveryCheckInterval = Duration.ofMinutes(1);

    // State store configuration
    @Builder.Default
    private final String stateStoreType = "in-memory";

    @Builder.Default
    private final Duration stateRetentionPeriod = Duration.ofDays(7);

    /**
     * Creates a default configuration for local development.
     */
    public static DistributedConfig defaultConfig() {
        return DistributedConfig.builder()
                .nodeId(generateDefaultNodeId())
                .hostname("localhost")
                .port(8080)
                .build();
    }

    /**
     * Creates a configuration for a single-node deployment.
     */
    public static DistributedConfig singleNode(String nodeId) {
        return DistributedConfig.builder()
                .nodeId(Objects.requireNonNull(nodeId))
                .hostname("localhost")
                .port(8080)
                .totalPartitions(1)
                .build();
    }

    /**
     * Creates a configuration for a production cluster.
     */
    public static DistributedConfig production(String nodeId, String hostname, int port, int partitions) {
        return DistributedConfig.builder()
                .nodeId(Objects.requireNonNull(nodeId))
                .hostname(Objects.requireNonNull(hostname))
                .port(port)
                .totalPartitions(partitions)
                .heartbeatInterval(Duration.ofSeconds(5))
                .failureDetectionTimeout(Duration.ofSeconds(15))
                .ownershipLeaseDuration(Duration.ofMinutes(2))
                .checkpointingEnabled(true)
                .autoRecoveryEnabled(true)
                .build();
    }

    /**
     * Validates the configuration.
     *
     * @throws IllegalStateException if configuration is invalid
     */
    public void validate() {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalStateException("nodeId must not be blank");
        }
        if (hostname == null || hostname.isBlank()) {
            throw new IllegalStateException("hostname must not be blank");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalStateException("port must be between 1 and 65535");
        }
        if (totalPartitions <= 0) {
            throw new IllegalStateException("totalPartitions must be positive");
        }
        if (heartbeatInterval.isNegative() || heartbeatInterval.isZero()) {
            throw new IllegalStateException("heartbeatInterval must be positive");
        }
        if (failureDetectionTimeout.compareTo(heartbeatInterval.multipliedBy(2)) < 0) {
            throw new IllegalStateException("failureDetectionTimeout should be at least 2x heartbeatInterval");
        }
        if (ownershipLeaseDuration.compareTo(leaseRenewalInterval.multipliedBy(2)) < 0) {
            throw new IllegalStateException("ownershipLeaseDuration should be at least 2x leaseRenewalInterval");
        }
    }

    private static String generateDefaultNodeId() {
        return "node-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
