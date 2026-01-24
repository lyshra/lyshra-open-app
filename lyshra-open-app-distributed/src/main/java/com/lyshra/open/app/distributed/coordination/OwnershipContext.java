package com.lyshra.open.app.distributed.coordination;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Context information about the current ownership state of a workflow.
 *
 * This immutable class provides a snapshot of ownership information at
 * a point in time. It is used for queries about ownership status and
 * for passing ownership context to workflow execution logic.
 *
 * The context includes all information needed to:
 * - Verify ownership validity
 * - Perform fenced operations
 * - Track ownership history
 * - Make routing decisions
 *
 * Thread Safety: This class is immutable and thread-safe.
 */
@Getter
@Builder(toBuilder = true)
@ToString
public final class OwnershipContext implements Serializable {

    private static final long serialVersionUID = 1L;

    // ========== Identity ==========

    /**
     * The workflow execution key this context is for.
     */
    private final String workflowExecutionKey;

    /**
     * The unique lease ID for this ownership period.
     */
    private final String leaseId;

    /**
     * The node ID that currently owns this workflow.
     */
    private final String ownerId;

    /**
     * The partition ID this workflow belongs to.
     */
    private final int partitionId;

    // ========== Timing ==========

    /**
     * When ownership was originally acquired.
     */
    private final Instant acquiredAt;

    /**
     * When the lease was last renewed.
     */
    private final Instant renewedAt;

    /**
     * When the current lease expires.
     */
    private final Instant expiresAt;

    /**
     * The configured lease duration.
     */
    private final Duration leaseDuration;

    // ========== Versioning ==========

    /**
     * The fencing token for split-brain prevention.
     * Operations should include this token for validation.
     */
    private final long fencingToken;

    /**
     * The ownership epoch (increments on each ownership transfer).
     */
    private final long ownershipEpoch;

    /**
     * The lease version (increments on each update).
     */
    private final long version;

    // ========== History ==========

    /**
     * The previous owner (if ownership was transferred).
     */
    private final String previousOwnerId;

    /**
     * When ownership was transferred from the previous owner.
     */
    private final Instant transferredAt;

    /**
     * The reason for the last ownership transfer.
     */
    private final String transferReason;

    /**
     * Number of times the lease has been renewed.
     */
    private final int renewalCount;

    // ========== Cluster Context ==========

    /**
     * The cluster ID this ownership belongs to.
     */
    private final String clusterId;

    /**
     * The region/datacenter of the owner node.
     */
    private final String ownerRegion;

    /**
     * Custom metadata associated with this ownership.
     */
    private final Map<String, String> metadata;

    /**
     * Private constructor - use builder.
     */
    private OwnershipContext(String workflowExecutionKey,
                              String leaseId,
                              String ownerId,
                              int partitionId,
                              Instant acquiredAt,
                              Instant renewedAt,
                              Instant expiresAt,
                              Duration leaseDuration,
                              long fencingToken,
                              long ownershipEpoch,
                              long version,
                              String previousOwnerId,
                              Instant transferredAt,
                              String transferReason,
                              int renewalCount,
                              String clusterId,
                              String ownerRegion,
                              Map<String, String> metadata) {
        this.workflowExecutionKey = Objects.requireNonNull(workflowExecutionKey);
        this.leaseId = leaseId;
        this.ownerId = ownerId;
        this.partitionId = partitionId;
        this.acquiredAt = acquiredAt;
        this.renewedAt = renewedAt;
        this.expiresAt = expiresAt;
        this.leaseDuration = leaseDuration;
        this.fencingToken = fencingToken;
        this.ownershipEpoch = ownershipEpoch;
        this.version = version;
        this.previousOwnerId = previousOwnerId;
        this.transferredAt = transferredAt;
        this.transferReason = transferReason;
        this.renewalCount = renewalCount;
        this.clusterId = clusterId;
        this.ownerRegion = ownerRegion;
        this.metadata = metadata != null ? Collections.unmodifiableMap(metadata) : Collections.emptyMap();
    }

    // ========== Query Methods ==========

    /**
     * Checks if ownership is currently valid (active and not expired).
     *
     * @return true if ownership is valid
     */
    public boolean isValid() {
        return ownerId != null && expiresAt != null && Instant.now().isBefore(expiresAt);
    }

    /**
     * Checks if the ownership has expired.
     *
     * @return true if expired
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks if ownership is held by the specified node.
     *
     * @param nodeId the node ID to check
     * @return true if owned by the specified node and still valid
     */
    public boolean isOwnedBy(String nodeId) {
        return nodeId != null && nodeId.equals(ownerId) && isValid();
    }

    /**
     * Gets the remaining time until lease expiration.
     *
     * @return Optional containing remaining duration, empty if expired
     */
    public Optional<Duration> getRemainingTime() {
        if (expiresAt == null) {
            return Optional.empty();
        }
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        return remaining.isNegative() ? Optional.empty() : Optional.of(remaining);
    }

    /**
     * Gets the time elapsed since acquisition.
     *
     * @return duration since acquisition
     */
    public Duration getOwnershipAge() {
        return acquiredAt != null
                ? Duration.between(acquiredAt, Instant.now())
                : Duration.ZERO;
    }

    /**
     * Gets the time elapsed since last renewal.
     *
     * @return duration since last renewal
     */
    public Duration getTimeSinceRenewal() {
        return renewedAt != null
                ? Duration.between(renewedAt, Instant.now())
                : Duration.ZERO;
    }

    /**
     * Checks if the lease needs renewal (within threshold of expiration).
     *
     * @param threshold the renewal threshold
     * @return true if renewal is needed
     */
    public boolean needsRenewal(Duration threshold) {
        Optional<Duration> remaining = getRemainingTime();
        return remaining.isPresent() && remaining.get().compareTo(threshold) <= 0;
    }

    /**
     * Checks if this context has precedence over another based on fencing token.
     *
     * @param other the other context to compare
     * @return true if this context has higher precedence
     */
    public boolean hasPrecedenceOver(OwnershipContext other) {
        if (other == null) {
            return true;
        }
        return this.fencingToken > other.fencingToken;
    }

    // ========== Optional Getters ==========

    public Optional<String> getOwnerIdOptional() {
        return Optional.ofNullable(ownerId);
    }

    public Optional<String> getPreviousOwnerIdOptional() {
        return Optional.ofNullable(previousOwnerId);
    }

    public Optional<String> getClusterIdOptional() {
        return Optional.ofNullable(clusterId);
    }

    public Optional<String> getMetadataValue(String key) {
        return Optional.ofNullable(metadata.get(key));
    }

    // ========== Factory Methods ==========

    /**
     * Creates an empty context indicating no ownership.
     */
    public static OwnershipContext empty(String workflowExecutionKey) {
        return OwnershipContext.builder()
                .workflowExecutionKey(workflowExecutionKey)
                .fencingToken(-1)
                .ownershipEpoch(0)
                .version(0)
                .renewalCount(0)
                .partitionId(-1)
                .build();
    }

    /**
     * Creates a context from acquisition information.
     */
    public static OwnershipContext fromAcquisition(String workflowExecutionKey,
                                                    String leaseId,
                                                    String ownerId,
                                                    int partitionId,
                                                    Duration leaseDuration,
                                                    long fencingToken,
                                                    long ownershipEpoch) {
        Instant now = Instant.now();
        return OwnershipContext.builder()
                .workflowExecutionKey(workflowExecutionKey)
                .leaseId(leaseId)
                .ownerId(ownerId)
                .partitionId(partitionId)
                .acquiredAt(now)
                .renewedAt(now)
                .expiresAt(now.plus(leaseDuration))
                .leaseDuration(leaseDuration)
                .fencingToken(fencingToken)
                .ownershipEpoch(ownershipEpoch)
                .version(1)
                .renewalCount(0)
                .build();
    }
}
