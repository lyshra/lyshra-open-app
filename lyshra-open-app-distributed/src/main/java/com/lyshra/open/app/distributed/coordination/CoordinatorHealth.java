package com.lyshra.open.app.distributed.coordination;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Health status of the workflow ownership coordinator.
 *
 * This class provides a comprehensive view of the coordinator's health,
 * useful for monitoring, alerting, and health check endpoints.
 *
 * Thread Safety: This class is immutable and thread-safe.
 */
@Getter
@Builder(toBuilder = true)
@ToString
public final class CoordinatorHealth {

    /**
     * Health status levels.
     */
    public enum HealthStatus {
        /**
         * Coordinator is fully operational.
         */
        HEALTHY,

        /**
         * Coordinator is operational but with degraded performance.
         */
        DEGRADED,

        /**
         * Coordinator is unhealthy but attempting recovery.
         */
        UNHEALTHY,

        /**
         * Coordinator is not operational.
         */
        DOWN,

        /**
         * Health status is unknown.
         */
        UNKNOWN
    }

    /**
     * Overall health status.
     */
    private final HealthStatus status;

    /**
     * Whether the coordinator is currently active.
     */
    private final boolean active;

    /**
     * Whether the backend connection is healthy.
     */
    private final boolean backendConnected;

    /**
     * When the coordinator was started.
     */
    private final Instant startedAt;

    /**
     * How long the coordinator has been running.
     */
    private final Duration uptime;

    /**
     * When the last health check was performed.
     */
    private final Instant lastHealthCheckAt;

    /**
     * Duration of the last health check.
     */
    private final Duration lastHealthCheckDuration;

    /**
     * Number of locally owned workflows.
     */
    private final int localOwnershipCount;

    /**
     * Number of active leases.
     */
    private final int activeLeaseCount;

    /**
     * Number of pending lease renewals.
     */
    private final int pendingRenewals;

    /**
     * Last time a lease was successfully renewed.
     */
    private final Instant lastSuccessfulRenewal;

    /**
     * Last time a lease renewal failed.
     */
    private final Instant lastFailedRenewal;

    /**
     * Consecutive renewal failures.
     */
    private final int consecutiveRenewalFailures;

    /**
     * Backend connection latency (last measured).
     */
    private final Duration backendLatency;

    /**
     * Number of backend connection failures.
     */
    private final int backendConnectionFailures;

    /**
     * Last backend error message.
     */
    private final String lastBackendError;

    /**
     * Health check details for each component.
     */
    private final Map<String, ComponentHealth> componentHealth;

    /**
     * Human-readable status message.
     */
    private final String message;

    /**
     * Component-level health information.
     */
    @Getter
    @Builder
    @ToString
    public static class ComponentHealth {
        private final String componentName;
        private final HealthStatus status;
        private final String message;
        private final Duration latency;
        private final Instant lastCheckedAt;
    }

    // ========== Query Methods ==========

    /**
     * Checks if the coordinator is healthy.
     */
    public boolean isHealthy() {
        return status == HealthStatus.HEALTHY;
    }

    /**
     * Checks if the coordinator is operational (healthy or degraded).
     */
    public boolean isOperational() {
        return status == HealthStatus.HEALTHY || status == HealthStatus.DEGRADED;
    }

    /**
     * Checks if the coordinator needs attention.
     */
    public boolean needsAttention() {
        return status == HealthStatus.DEGRADED ||
               status == HealthStatus.UNHEALTHY ||
               consecutiveRenewalFailures > 0;
    }

    /**
     * Gets component health by name.
     */
    public Optional<ComponentHealth> getComponentHealth(String componentName) {
        return Optional.ofNullable(componentHealth != null ? componentHealth.get(componentName) : null);
    }

    /**
     * Gets the last backend error as an Optional.
     */
    public Optional<String> getLastBackendErrorOptional() {
        return Optional.ofNullable(lastBackendError);
    }

    // ========== Factory Methods ==========

    /**
     * Creates a healthy status.
     */
    public static CoordinatorHealth healthy(String nodeId,
                                             Instant startedAt,
                                             int ownershipCount,
                                             Duration backendLatency) {
        Instant now = Instant.now();
        return CoordinatorHealth.builder()
                .status(HealthStatus.HEALTHY)
                .active(true)
                .backendConnected(true)
                .startedAt(startedAt)
                .uptime(Duration.between(startedAt, now))
                .lastHealthCheckAt(now)
                .lastHealthCheckDuration(backendLatency)
                .localOwnershipCount(ownershipCount)
                .activeLeaseCount(ownershipCount)
                .pendingRenewals(0)
                .consecutiveRenewalFailures(0)
                .backendLatency(backendLatency)
                .backendConnectionFailures(0)
                .componentHealth(Collections.emptyMap())
                .message("Coordinator is healthy")
                .build();
    }

    /**
     * Creates a degraded status.
     */
    public static CoordinatorHealth degraded(String message,
                                              Instant startedAt,
                                              int ownershipCount,
                                              int renewalFailures) {
        Instant now = Instant.now();
        return CoordinatorHealth.builder()
                .status(HealthStatus.DEGRADED)
                .active(true)
                .backendConnected(true)
                .startedAt(startedAt)
                .uptime(Duration.between(startedAt, now))
                .lastHealthCheckAt(now)
                .localOwnershipCount(ownershipCount)
                .activeLeaseCount(ownershipCount)
                .consecutiveRenewalFailures(renewalFailures)
                .message(message)
                .build();
    }

    /**
     * Creates an unhealthy status.
     */
    public static CoordinatorHealth unhealthy(String message,
                                               Instant startedAt,
                                               String lastError) {
        Instant now = Instant.now();
        return CoordinatorHealth.builder()
                .status(HealthStatus.UNHEALTHY)
                .active(false)
                .backendConnected(false)
                .startedAt(startedAt)
                .uptime(Duration.between(startedAt, now))
                .lastHealthCheckAt(now)
                .lastBackendError(lastError)
                .message(message)
                .build();
    }

    /**
     * Creates a down status.
     */
    public static CoordinatorHealth down(String message) {
        return CoordinatorHealth.builder()
                .status(HealthStatus.DOWN)
                .active(false)
                .backendConnected(false)
                .lastHealthCheckAt(Instant.now())
                .message(message)
                .build();
    }

    /**
     * Creates an unknown status.
     */
    public static CoordinatorHealth unknown() {
        return CoordinatorHealth.builder()
                .status(HealthStatus.UNKNOWN)
                .active(false)
                .backendConnected(false)
                .lastHealthCheckAt(Instant.now())
                .message("Health status unknown")
                .build();
    }
}
