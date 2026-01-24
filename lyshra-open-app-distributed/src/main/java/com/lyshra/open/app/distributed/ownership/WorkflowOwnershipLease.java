package com.lyshra.open.app.distributed.ownership;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable data model representing workflow execution ownership with lease metadata.
 *
 * This model captures all information needed for distributed ownership management:
 * - Identity: Which workflow execution and which node
 * - Lease timing: When acquired, how long, when it expires
 * - Versioning: For optimistic concurrency control
 * - State tracking: Current lease state and history
 *
 * The lease-based ownership model ensures:
 * 1. Automatic expiry - Leases expire if not renewed, enabling failover
 * 2. Deterministic ownership - Clear rules for who owns what
 * 3. Persistence support - All fields are serializable
 * 4. Audit trail - History of ownership transfers
 *
 * Serialization: This class implements Serializable for persistence to databases,
 * caches, or message queues. All fields are either primitives, immutable objects,
 * or defensive copies of mutable objects.
 *
 * Thread Safety: This class is immutable and thread-safe.
 *
 * @see IWorkflowOwnershipManager
 * @see WorkflowOwnershipResult
 */
@Getter
@Builder(toBuilder = true)
@ToString
public final class WorkflowOwnershipLease implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ========== Identity Fields ==========

    /**
     * Unique identifier for this lease record.
     * Format: "lease-{workflowId}-{timestamp}-{random}"
     */
    private final String leaseId;

    /**
     * The unique identifier for the workflow execution being owned.
     * Format: "organization::module::version::workflowName::executionId"
     */
    private final String workflowId;

    /**
     * The unique identifier of the node that owns this workflow execution.
     * This is the node responsible for executing the workflow.
     */
    private final String ownerId;

    /**
     * The partition ID this workflow belongs to.
     * Used for partition-based routing and ownership verification.
     */
    private final int partitionId;

    // ========== Lease Timing Fields ==========

    /**
     * Timestamp when the lease was originally acquired.
     * This remains constant through lease renewals.
     */
    private final Instant leaseAcquiredAt;

    /**
     * Timestamp when the lease was last renewed.
     * Updated each time the lease is extended.
     */
    private final Instant leaseRenewedAt;

    /**
     * The duration of the lease from the last renewal.
     * The lease expires at: leaseRenewedAt + leaseDuration
     */
    private final Duration leaseDuration;

    /**
     * Calculated expiration timestamp.
     * Lease is valid while: Instant.now() < leaseExpiresAt
     */
    private final Instant leaseExpiresAt;

    // ========== Versioning Fields ==========

    /**
     * Monotonically increasing version for optimistic concurrency control.
     * Incremented on every lease update (acquire, renew, release).
     */
    private final long version;

    /**
     * The ownership epoch - incremented when ownership transfers to a new node.
     * Used to detect stale ownership claims after failover.
     */
    private final long ownershipEpoch;

    /**
     * Fencing token to prevent split-brain scenarios.
     * A higher fencing token always takes precedence.
     */
    private final long fencingToken;

    // ========== State Fields ==========

    /**
     * Current state of the lease.
     */
    private final LeaseState state;

    /**
     * Reason for the current state (especially for RELEASED, REVOKED, EXPIRED states).
     */
    private final String stateReason;

    /**
     * Number of times this lease has been renewed.
     */
    private final int renewalCount;

    /**
     * Maximum number of renewals allowed (0 = unlimited).
     */
    private final int maxRenewals;

    // ========== History & Audit Fields ==========

    /**
     * ID of the previous owner (if this is a re-acquired lease).
     */
    private final String previousOwnerId;

    /**
     * Timestamp when ownership was transferred from the previous owner.
     */
    private final Instant ownershipTransferredAt;

    /**
     * Reason for ownership transfer (e.g., "node_failure", "rebalance", "explicit_release").
     */
    private final String transferReason;

    /**
     * Custom metadata for application-specific ownership attributes.
     */
    private final Map<String, String> metadata;

    // ========== Cluster Context Fields ==========

    /**
     * The cluster ID this lease belongs to (for multi-cluster deployments).
     */
    private final String clusterId;

    /**
     * The region/datacenter where the owner node is located.
     */
    private final String ownerRegion;

    /**
     * Priority of this owner (higher = more preferred for ownership).
     */
    private final int ownerPriority;

    /**
     * Possible states of a workflow ownership lease.
     */
    public enum LeaseState {
        /**
         * Lease is active and valid.
         */
        ACTIVE,

        /**
         * Lease is pending acquisition (in the process of being claimed).
         */
        PENDING,

        /**
         * Lease has been explicitly released by the owner.
         */
        RELEASED,

        /**
         * Lease has expired due to timeout.
         */
        EXPIRED,

        /**
         * Lease was forcibly revoked (e.g., during failover).
         */
        REVOKED,

        /**
         * Lease is suspended (owner is draining).
         */
        SUSPENDED,

        /**
         * Lease state is unknown or corrupted.
         */
        UNKNOWN
    }

    /**
     * Private constructor - use builder or factory methods.
     */
    private WorkflowOwnershipLease(String leaseId,
                                    String workflowId,
                                    String ownerId,
                                    int partitionId,
                                    Instant leaseAcquiredAt,
                                    Instant leaseRenewedAt,
                                    Duration leaseDuration,
                                    Instant leaseExpiresAt,
                                    long version,
                                    long ownershipEpoch,
                                    long fencingToken,
                                    LeaseState state,
                                    String stateReason,
                                    int renewalCount,
                                    int maxRenewals,
                                    String previousOwnerId,
                                    Instant ownershipTransferredAt,
                                    String transferReason,
                                    Map<String, String> metadata,
                                    String clusterId,
                                    String ownerRegion,
                                    int ownerPriority) {
        this.leaseId = Objects.requireNonNull(leaseId, "leaseId must not be null");
        this.workflowId = Objects.requireNonNull(workflowId, "workflowId must not be null");
        this.ownerId = ownerId; // Can be null for released/expired leases
        this.partitionId = partitionId;
        this.leaseAcquiredAt = leaseAcquiredAt;
        this.leaseRenewedAt = leaseRenewedAt;
        this.leaseDuration = leaseDuration;
        this.leaseExpiresAt = leaseExpiresAt;
        this.version = version;
        this.ownershipEpoch = ownershipEpoch;
        this.fencingToken = fencingToken;
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.stateReason = stateReason;
        this.renewalCount = renewalCount;
        this.maxRenewals = maxRenewals;
        this.previousOwnerId = previousOwnerId;
        this.ownershipTransferredAt = ownershipTransferredAt;
        this.transferReason = transferReason;
        this.metadata = metadata != null ? Collections.unmodifiableMap(metadata) : Collections.emptyMap();
        this.clusterId = clusterId;
        this.ownerRegion = ownerRegion;
        this.ownerPriority = ownerPriority;
    }

    // ========== Factory Methods ==========

    /**
     * Creates a new lease for initial ownership acquisition.
     *
     * @param workflowId the workflow execution ID
     * @param ownerId the claiming node ID
     * @param partitionId the partition ID
     * @param leaseDuration the lease duration
     * @param fencingToken the fencing token for this acquisition
     * @return a new active lease
     */
    public static WorkflowOwnershipLease acquire(String workflowId,
                                                  String ownerId,
                                                  int partitionId,
                                                  Duration leaseDuration,
                                                  long fencingToken) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");

        Instant now = Instant.now();
        return WorkflowOwnershipLease.builder()
                .leaseId(generateLeaseId(workflowId))
                .workflowId(workflowId)
                .ownerId(ownerId)
                .partitionId(partitionId)
                .leaseAcquiredAt(now)
                .leaseRenewedAt(now)
                .leaseDuration(leaseDuration)
                .leaseExpiresAt(now.plus(leaseDuration))
                .version(1L)
                .ownershipEpoch(1L)
                .fencingToken(fencingToken)
                .state(LeaseState.ACTIVE)
                .stateReason("Initial acquisition")
                .renewalCount(0)
                .maxRenewals(0) // Unlimited by default
                .build();
    }

    /**
     * Creates a lease for re-acquisition after previous owner failure.
     *
     * @param workflowId the workflow execution ID
     * @param newOwnerId the new owner node ID
     * @param partitionId the partition ID
     * @param leaseDuration the lease duration
     * @param previousLease the previous lease (may be expired)
     * @param transferReason reason for the ownership transfer
     * @return a new active lease with history
     */
    public static WorkflowOwnershipLease reacquire(String workflowId,
                                                    String newOwnerId,
                                                    int partitionId,
                                                    Duration leaseDuration,
                                                    WorkflowOwnershipLease previousLease,
                                                    String transferReason) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Objects.requireNonNull(newOwnerId, "newOwnerId must not be null");
        Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");

        Instant now = Instant.now();
        long newEpoch = previousLease != null ? previousLease.ownershipEpoch + 1 : 1L;
        long newFencingToken = previousLease != null ? previousLease.fencingToken + 1 : 1L;
        long newVersion = previousLease != null ? previousLease.version + 1 : 1L;

        return WorkflowOwnershipLease.builder()
                .leaseId(generateLeaseId(workflowId))
                .workflowId(workflowId)
                .ownerId(newOwnerId)
                .partitionId(partitionId)
                .leaseAcquiredAt(now)
                .leaseRenewedAt(now)
                .leaseDuration(leaseDuration)
                .leaseExpiresAt(now.plus(leaseDuration))
                .version(newVersion)
                .ownershipEpoch(newEpoch)
                .fencingToken(newFencingToken)
                .state(LeaseState.ACTIVE)
                .stateReason("Re-acquired: " + transferReason)
                .renewalCount(0)
                .maxRenewals(0)
                .previousOwnerId(previousLease != null ? previousLease.ownerId : null)
                .ownershipTransferredAt(now)
                .transferReason(transferReason)
                .clusterId(previousLease != null ? previousLease.clusterId : null)
                .build();
    }

    // ========== State Query Methods ==========

    /**
     * Checks if this lease is currently valid (active and not expired).
     *
     * @return true if the lease is valid
     */
    public boolean isValid() {
        return state == LeaseState.ACTIVE && !isExpired();
    }

    /**
     * Checks if this lease has expired based on current time.
     *
     * @return true if expired
     */
    public boolean isExpired() {
        return leaseExpiresAt != null && Instant.now().isAfter(leaseExpiresAt);
    }

    /**
     * Checks if this lease is owned by the specified node.
     *
     * @param nodeId the node ID to check
     * @return true if owned by the specified node
     */
    public boolean isOwnedBy(String nodeId) {
        return ownerId != null && ownerId.equals(nodeId) && isValid();
    }

    /**
     * Checks if this lease can be renewed.
     *
     * @return true if renewable
     */
    public boolean isRenewable() {
        if (state != LeaseState.ACTIVE) {
            return false;
        }
        if (maxRenewals > 0 && renewalCount >= maxRenewals) {
            return false;
        }
        return !isExpired();
    }

    /**
     * Checks if this lease can be claimed by another node.
     *
     * @return true if claimable
     */
    public boolean isClaimable() {
        return state == LeaseState.RELEASED ||
               state == LeaseState.EXPIRED ||
               state == LeaseState.REVOKED ||
               isExpired();
    }

    /**
     * Returns the time remaining until lease expiration.
     *
     * @return Optional containing remaining duration, empty if expired
     */
    public Optional<Duration> getRemainingTime() {
        if (leaseExpiresAt == null) {
            return Optional.empty();
        }
        Duration remaining = Duration.between(Instant.now(), leaseExpiresAt);
        return remaining.isNegative() ? Optional.empty() : Optional.of(remaining);
    }

    /**
     * Returns the time elapsed since the lease was acquired.
     *
     * @return duration since acquisition
     */
    public Duration getLeaseAge() {
        return leaseAcquiredAt != null
                ? Duration.between(leaseAcquiredAt, Instant.now())
                : Duration.ZERO;
    }

    /**
     * Returns the time elapsed since the last renewal.
     *
     * @return duration since last renewal
     */
    public Duration getTimeSinceRenewal() {
        return leaseRenewedAt != null
                ? Duration.between(leaseRenewedAt, Instant.now())
                : Duration.ZERO;
    }

    /**
     * Checks if this lease has a higher fencing token than another.
     *
     * @param other the other lease to compare
     * @return true if this lease has higher precedence
     */
    public boolean hasPrecedenceOver(WorkflowOwnershipLease other) {
        if (other == null) {
            return true;
        }
        return this.fencingToken > other.fencingToken;
    }

    // ========== Mutation Methods (return new instances) ==========

    /**
     * Creates a renewed lease with extended expiration.
     *
     * @param extensionDuration the duration to extend
     * @return a new lease with extended expiration
     * @throws IllegalStateException if lease is not renewable
     */
    public WorkflowOwnershipLease renew(Duration extensionDuration) {
        if (!isRenewable()) {
            throw new IllegalStateException("Lease is not renewable: " + state);
        }

        Instant now = Instant.now();
        return this.toBuilder()
                .leaseRenewedAt(now)
                .leaseDuration(extensionDuration)
                .leaseExpiresAt(now.plus(extensionDuration))
                .version(version + 1)
                .renewalCount(renewalCount + 1)
                .stateReason("Renewed")
                .build();
    }

    /**
     * Creates a released lease.
     *
     * @param reason the release reason
     * @return a new lease in RELEASED state
     */
    public WorkflowOwnershipLease release(String reason) {
        return this.toBuilder()
                .state(LeaseState.RELEASED)
                .stateReason(reason)
                .version(version + 1)
                .leaseExpiresAt(Instant.now()) // Expire immediately
                .build();
    }

    /**
     * Creates a revoked lease.
     *
     * @param reason the revocation reason
     * @return a new lease in REVOKED state
     */
    public WorkflowOwnershipLease revoke(String reason) {
        return this.toBuilder()
                .state(LeaseState.REVOKED)
                .stateReason(reason)
                .version(version + 1)
                .leaseExpiresAt(Instant.now()) // Expire immediately
                .build();
    }

    /**
     * Creates an expired lease.
     *
     * @return a new lease in EXPIRED state
     */
    public WorkflowOwnershipLease expire() {
        return this.toBuilder()
                .state(LeaseState.EXPIRED)
                .stateReason("Lease expired at " + leaseExpiresAt)
                .version(version + 1)
                .build();
    }

    /**
     * Creates a suspended lease (for draining).
     *
     * @param reason the suspension reason
     * @return a new lease in SUSPENDED state
     */
    public WorkflowOwnershipLease suspend(String reason) {
        return this.toBuilder()
                .state(LeaseState.SUSPENDED)
                .stateReason(reason)
                .version(version + 1)
                .build();
    }

    /**
     * Creates a lease with updated metadata.
     *
     * @param newMetadata the new metadata
     * @return a new lease with updated metadata
     */
    public WorkflowOwnershipLease withMetadata(Map<String, String> newMetadata) {
        return this.toBuilder()
                .metadata(newMetadata)
                .version(version + 1)
                .build();
    }

    /**
     * Creates a lease with cluster context.
     *
     * @param clusterId the cluster ID
     * @param region the region
     * @param priority the owner priority
     * @return a new lease with cluster context
     */
    public WorkflowOwnershipLease withClusterContext(String clusterId, String region, int priority) {
        return this.toBuilder()
                .clusterId(clusterId)
                .ownerRegion(region)
                .ownerPriority(priority)
                .version(version + 1)
                .build();
    }

    // ========== Utility Methods ==========

    /**
     * Returns the owner ID as an Optional.
     */
    public Optional<String> getOwnerIdOptional() {
        return Optional.ofNullable(ownerId);
    }

    /**
     * Returns the previous owner ID as an Optional.
     */
    public Optional<String> getPreviousOwnerIdOptional() {
        return Optional.ofNullable(previousOwnerId);
    }

    /**
     * Returns the cluster ID as an Optional.
     */
    public Optional<String> getClusterIdOptional() {
        return Optional.ofNullable(clusterId);
    }

    /**
     * Gets a metadata value by key.
     */
    public Optional<String> getMetadataValue(String key) {
        return Optional.ofNullable(metadata.get(key));
    }

    /**
     * Generates a unique lease ID.
     */
    private static String generateLeaseId(String workflowId) {
        return "lease-" + workflowId.hashCode() + "-" +
               System.currentTimeMillis() + "-" +
               java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowOwnershipLease that = (WorkflowOwnershipLease) o;
        return Objects.equals(leaseId, that.leaseId) &&
               version == that.version;
    }

    @Override
    public int hashCode() {
        return Objects.hash(leaseId, version);
    }
}
