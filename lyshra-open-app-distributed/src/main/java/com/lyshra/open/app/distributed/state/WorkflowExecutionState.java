package com.lyshra.open.app.distributed.state;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.With;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Durable representation of a workflow execution's state.
 *
 * This class captures all information needed to:
 * - Resume a workflow after failure
 * - Track execution progress
 * - Audit workflow history
 * - Coordinate distributed execution
 *
 * Thread Safety: This class is immutable and thread-safe.
 * Use the @With annotation methods to create modified copies.
 */
@Getter
@Builder(toBuilder = true)
@ToString
public final class WorkflowExecutionState implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Possible execution statuses.
     */
    public enum ExecutionStatus {
        /**
         * Execution is pending start.
         */
        PENDING,

        /**
         * Execution is currently running.
         */
        RUNNING,

        /**
         * Execution is paused/suspended.
         */
        PAUSED,

        /**
         * Execution completed successfully.
         */
        COMPLETED,

        /**
         * Execution failed.
         */
        FAILED,

        /**
         * Execution was cancelled.
         */
        CANCELLED,

        /**
         * Execution is waiting for retry.
         */
        AWAITING_RETRY,

        /**
         * Execution needs recovery (owner failed).
         */
        NEEDS_RECOVERY
    }

    /**
     * Ownership status for the workflow execution.
     *
     * This enum tracks the current ownership state of the workflow,
     * enabling safe recovery and takeover operations.
     */
    public enum OwnershipStatus {
        /**
         * Workflow has no owner assigned yet.
         */
        UNOWNED,

        /**
         * Workflow is owned by an active node with a valid lease.
         */
        OWNED,

        /**
         * Ownership is being acquired (lock held, lease pending).
         */
        ACQUIRING,

        /**
         * Workflow's owner has failed and needs recovery.
         * The lease may or may not have expired yet.
         */
        ORPHANED,

        /**
         * Workflow is in the process of being taken over by another node.
         */
        PENDING_TAKEOVER,

        /**
         * Ownership was explicitly released by the owner.
         */
        RELEASED,

        /**
         * Ownership lease has expired.
         */
        LEASE_EXPIRED,

        /**
         * Ownership was forcibly revoked (e.g., during rebalancing).
         */
        REVOKED,

        /**
         * Ownership is suspended (node is draining).
         */
        SUSPENDED,

        /**
         * Ownership status is unknown or corrupted.
         */
        UNKNOWN
    }

    // Identity
    @With private final String executionKey;
    @With private final String workflowIdentifier;
    @With private final String executionId;

    // Sharding
    @With private final int partitionId;
    @With private final String ownerNodeId;

    // Embedded Ownership Lease Metadata
    @With private final String leaseId;
    @With private final Instant leaseAcquiredAt;
    @With private final Instant leaseRenewedAt;
    @With private final Instant leaseExpiresAt;
    @With private final long leaseVersion;
    @With private final long ownershipEpoch;
    @With private final long fencingToken;
    @With private final String previousOwnerId;
    @With private final Instant ownershipTransferredAt;
    @With private final String transferReason;

    // Ownership Status Tracking
    @With private final OwnershipStatus ownershipStatus;
    @With private final Instant ownershipStatusChangedAt;
    @With private final String ownershipStatusReason;

    // Recovery Tracking
    @With private final int recoveryAttempts;
    @With private final int maxRecoveryAttempts;
    @With private final Instant lastRecoveryAttemptAt;
    @With private final String lastRecoveryError;
    @With private final String recoveryTargetNodeId;

    // Execution state
    @With private final ExecutionStatus status;
    @With private final String currentStepName;
    @With private final int completedSteps;
    @With private final int totalSteps;

    // Context data (serialized)
    @With private final byte[] contextData;
    @With private final byte[] variablesData;

    // Timing
    @With private final Instant createdAt;
    @With private final Instant startedAt;
    @With private final Instant lastUpdatedAt;
    @With private final Instant completedAt;

    // Versioning for optimistic locking
    @With private final long version;

    // Error handling
    @With private final String lastError;
    @With private final int retryCount;
    @With private final int maxRetries;

    // Metadata
    @With private final Map<String, String> metadata;

    private WorkflowExecutionState(String executionKey,
                                    String workflowIdentifier,
                                    String executionId,
                                    int partitionId,
                                    String ownerNodeId,
                                    String leaseId,
                                    Instant leaseAcquiredAt,
                                    Instant leaseRenewedAt,
                                    Instant leaseExpiresAt,
                                    long leaseVersion,
                                    long ownershipEpoch,
                                    long fencingToken,
                                    String previousOwnerId,
                                    Instant ownershipTransferredAt,
                                    String transferReason,
                                    OwnershipStatus ownershipStatus,
                                    Instant ownershipStatusChangedAt,
                                    String ownershipStatusReason,
                                    int recoveryAttempts,
                                    int maxRecoveryAttempts,
                                    Instant lastRecoveryAttemptAt,
                                    String lastRecoveryError,
                                    String recoveryTargetNodeId,
                                    ExecutionStatus status,
                                    String currentStepName,
                                    int completedSteps,
                                    int totalSteps,
                                    byte[] contextData,
                                    byte[] variablesData,
                                    Instant createdAt,
                                    Instant startedAt,
                                    Instant lastUpdatedAt,
                                    Instant completedAt,
                                    long version,
                                    String lastError,
                                    int retryCount,
                                    int maxRetries,
                                    Map<String, String> metadata) {
        this.executionKey = Objects.requireNonNull(executionKey, "executionKey must not be null");
        this.workflowIdentifier = Objects.requireNonNull(workflowIdentifier, "workflowIdentifier must not be null");
        this.executionId = Objects.requireNonNull(executionId, "executionId must not be null");
        this.partitionId = partitionId;
        this.ownerNodeId = ownerNodeId;
        // Ownership lease metadata
        this.leaseId = leaseId;
        this.leaseAcquiredAt = leaseAcquiredAt;
        this.leaseRenewedAt = leaseRenewedAt;
        this.leaseExpiresAt = leaseExpiresAt;
        this.leaseVersion = leaseVersion;
        this.ownershipEpoch = ownershipEpoch;
        this.fencingToken = fencingToken;
        this.previousOwnerId = previousOwnerId;
        this.ownershipTransferredAt = ownershipTransferredAt;
        this.transferReason = transferReason;
        // Ownership status tracking
        this.ownershipStatus = ownershipStatus != null ? ownershipStatus : OwnershipStatus.UNOWNED;
        this.ownershipStatusChangedAt = ownershipStatusChangedAt;
        this.ownershipStatusReason = ownershipStatusReason;
        // Recovery tracking
        this.recoveryAttempts = recoveryAttempts;
        this.maxRecoveryAttempts = maxRecoveryAttempts > 0 ? maxRecoveryAttempts : 5;
        this.lastRecoveryAttemptAt = lastRecoveryAttemptAt;
        this.lastRecoveryError = lastRecoveryError;
        this.recoveryTargetNodeId = recoveryTargetNodeId;
        // Execution state
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.currentStepName = currentStepName;
        this.completedSteps = completedSteps;
        this.totalSteps = totalSteps;
        this.contextData = contextData;
        this.variablesData = variablesData;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.startedAt = startedAt;
        this.lastUpdatedAt = lastUpdatedAt != null ? lastUpdatedAt : Instant.now();
        this.completedAt = completedAt;
        this.version = version;
        this.lastError = lastError;
        this.retryCount = retryCount;
        this.maxRetries = maxRetries;
        this.metadata = metadata != null ? Collections.unmodifiableMap(metadata) : Collections.emptyMap();
    }

    /**
     * Creates a new execution state for starting a workflow.
     */
    public static WorkflowExecutionState newExecution(String executionKey,
                                                       String workflowIdentifier,
                                                       String executionId,
                                                       int partitionId,
                                                       String ownerNodeId) {
        Instant now = Instant.now();
        return WorkflowExecutionState.builder()
                .executionKey(executionKey)
                .workflowIdentifier(workflowIdentifier)
                .executionId(executionId)
                .partitionId(partitionId)
                .ownerNodeId(ownerNodeId)
                .ownershipStatus(ownerNodeId != null ? OwnershipStatus.OWNED : OwnershipStatus.UNOWNED)
                .ownershipStatusChangedAt(now)
                .ownershipStatusReason(ownerNodeId != null ? "Initial ownership" : "No owner assigned")
                .recoveryAttempts(0)
                .maxRecoveryAttempts(5)
                .status(ExecutionStatus.PENDING)
                .completedSteps(0)
                .totalSteps(0)
                .createdAt(now)
                .lastUpdatedAt(now)
                .version(0)
                .retryCount(0)
                .maxRetries(3)
                .build();
    }

    /**
     * Creates a new execution state with full ownership details.
     *
     * @param executionKey the unique execution key
     * @param workflowIdentifier the workflow identifier
     * @param executionId the execution ID
     * @param partitionId the partition ID
     * @param ownerNodeId the owner node ID
     * @param leaseId the lease ID
     * @param leaseDuration the lease duration
     * @param fencingToken the fencing token
     * @return a new execution state with ownership configured
     */
    public static WorkflowExecutionState newExecutionWithOwnership(String executionKey,
                                                                     String workflowIdentifier,
                                                                     String executionId,
                                                                     int partitionId,
                                                                     String ownerNodeId,
                                                                     String leaseId,
                                                                     java.time.Duration leaseDuration,
                                                                     long fencingToken) {
        Instant now = Instant.now();
        return WorkflowExecutionState.builder()
                .executionKey(executionKey)
                .workflowIdentifier(workflowIdentifier)
                .executionId(executionId)
                .partitionId(partitionId)
                .ownerNodeId(ownerNodeId)
                .leaseId(leaseId)
                .leaseAcquiredAt(now)
                .leaseRenewedAt(now)
                .leaseExpiresAt(now.plus(leaseDuration))
                .leaseVersion(1)
                .ownershipEpoch(1)
                .fencingToken(fencingToken)
                .ownershipStatus(OwnershipStatus.OWNED)
                .ownershipStatusChangedAt(now)
                .ownershipStatusReason("Initial ownership with lease")
                .recoveryAttempts(0)
                .maxRecoveryAttempts(5)
                .status(ExecutionStatus.PENDING)
                .completedSteps(0)
                .totalSteps(0)
                .createdAt(now)
                .lastUpdatedAt(now)
                .version(1)
                .retryCount(0)
                .maxRetries(3)
                .build();
    }

    /**
     * Checks if the execution is in a terminal state.
     */
    public boolean isTerminal() {
        return status == ExecutionStatus.COMPLETED ||
               status == ExecutionStatus.FAILED ||
               status == ExecutionStatus.CANCELLED;
    }

    /**
     * Checks if the execution can be resumed.
     */
    public boolean isResumable() {
        return status == ExecutionStatus.PAUSED ||
               status == ExecutionStatus.AWAITING_RETRY ||
               status == ExecutionStatus.NEEDS_RECOVERY;
    }

    /**
     * Checks if the execution is currently active.
     */
    public boolean isActive() {
        return status == ExecutionStatus.RUNNING ||
               status == ExecutionStatus.PENDING;
    }

    /**
     * Checks if the execution needs recovery.
     */
    public boolean needsRecovery() {
        return status == ExecutionStatus.NEEDS_RECOVERY;
    }

    /**
     * Checks if retries are available.
     */
    public boolean canRetry() {
        return retryCount < maxRetries;
    }

    /**
     * Returns the execution duration if completed.
     */
    public Optional<java.time.Duration> getDuration() {
        if (startedAt == null) {
            return Optional.empty();
        }
        Instant endTime = completedAt != null ? completedAt : Instant.now();
        return Optional.of(java.time.Duration.between(startedAt, endTime));
    }

    /**
     * Returns the progress percentage.
     */
    public double getProgress() {
        if (totalSteps == 0) {
            return 0.0;
        }
        return (completedSteps * 100.0) / totalSteps;
    }

    /**
     * Creates a copy marked as running.
     */
    public WorkflowExecutionState markRunning(String stepName) {
        return this.toBuilder()
                .status(ExecutionStatus.RUNNING)
                .currentStepName(stepName)
                .startedAt(startedAt != null ? startedAt : Instant.now())
                .lastUpdatedAt(Instant.now())
                .version(version + 1)
                .build();
    }

    /**
     * Creates a copy marked as completed.
     */
    public WorkflowExecutionState markCompleted() {
        return this.toBuilder()
                .status(ExecutionStatus.COMPLETED)
                .completedAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .version(version + 1)
                .build();
    }

    /**
     * Creates a copy marked as failed.
     */
    public WorkflowExecutionState markFailed(String error) {
        return this.toBuilder()
                .status(ExecutionStatus.FAILED)
                .lastError(error)
                .completedAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .version(version + 1)
                .build();
    }

    /**
     * Creates a copy marked for recovery.
     */
    public WorkflowExecutionState markNeedsRecovery() {
        Instant now = Instant.now();
        return this.toBuilder()
                .status(ExecutionStatus.NEEDS_RECOVERY)
                .ownerNodeId(null)
                .ownershipStatus(OwnershipStatus.ORPHANED)
                .ownershipStatusChangedAt(now)
                .ownershipStatusReason("Marked for recovery")
                .lastUpdatedAt(now)
                .version(version + 1)
                .build();
    }

    /**
     * Creates a copy with incremented step count.
     */
    public WorkflowExecutionState stepCompleted(String nextStepName) {
        return this.toBuilder()
                .currentStepName(nextStepName)
                .completedSteps(completedSteps + 1)
                .lastUpdatedAt(Instant.now())
                .version(version + 1)
                .build();
    }

    // ========== Ownership Lease Helper Methods ==========

    /**
     * Checks if the ownership lease is currently valid (not expired).
     *
     * @return true if the lease exists and has not expired
     */
    public boolean isLeaseValid() {
        return leaseExpiresAt != null && Instant.now().isBefore(leaseExpiresAt);
    }

    /**
     * Checks if the ownership lease has expired.
     *
     * @return true if the lease has expired
     */
    public boolean hasLeaseExpired() {
        return leaseExpiresAt != null && Instant.now().isAfter(leaseExpiresAt);
    }

    /**
     * Checks if this workflow execution is owned by the specified node.
     *
     * @param nodeId the node ID to check
     * @return true if owned by the specified node and lease is valid
     */
    public boolean isOwnedBy(String nodeId) {
        return nodeId != null && nodeId.equals(ownerNodeId) && isLeaseValid();
    }

    /**
     * Returns the remaining lease time.
     *
     * @return Optional containing the remaining duration, empty if expired or no lease
     */
    public Optional<java.time.Duration> getRemainingLeaseTime() {
        if (leaseExpiresAt == null) {
            return Optional.empty();
        }
        java.time.Duration remaining = java.time.Duration.between(Instant.now(), leaseExpiresAt);
        return remaining.isNegative() ? Optional.empty() : Optional.of(remaining);
    }

    /**
     * Creates a copy with ownership lease information applied.
     *
     * @param newLeaseId the lease ID
     * @param newOwnerId the owner node ID
     * @param acquiredAt when the lease was acquired
     * @param expiresAt when the lease expires
     * @param newFencingToken the fencing token
     * @return a new state with ownership information
     */
    public WorkflowExecutionState withOwnership(String newLeaseId,
                                                  String newOwnerId,
                                                  Instant acquiredAt,
                                                  Instant expiresAt,
                                                  long newFencingToken) {
        Instant now = Instant.now();
        return this.toBuilder()
                .leaseId(newLeaseId)
                .ownerNodeId(newOwnerId)
                .leaseAcquiredAt(acquiredAt)
                .leaseRenewedAt(acquiredAt)
                .leaseExpiresAt(expiresAt)
                .leaseVersion(leaseVersion + 1)
                .ownershipEpoch(ownershipEpoch + 1)
                .fencingToken(newFencingToken)
                .ownershipStatus(OwnershipStatus.OWNED)
                .ownershipStatusChangedAt(now)
                .ownershipStatusReason("Ownership assigned")
                .lastUpdatedAt(now)
                .version(version + 1)
                .build();
    }

    /**
     * Creates a copy with renewed lease.
     *
     * @param newExpiresAt the new expiration time
     * @return a new state with renewed lease
     */
    public WorkflowExecutionState renewLease(Instant newExpiresAt) {
        Instant now = Instant.now();
        return this.toBuilder()
                .leaseRenewedAt(now)
                .leaseExpiresAt(newExpiresAt)
                .leaseVersion(leaseVersion + 1)
                .ownershipStatus(OwnershipStatus.OWNED)
                .ownershipStatusChangedAt(now)
                .ownershipStatusReason("Lease renewed")
                .lastUpdatedAt(now)
                .version(version + 1)
                .build();
    }

    /**
     * Creates a copy with ownership transferred to a new node.
     *
     * @param newOwnerId the new owner node ID
     * @param newLeaseId the new lease ID
     * @param expiresAt when the new lease expires
     * @param newFencingToken the new fencing token
     * @param reason the reason for transfer
     * @return a new state with transferred ownership
     */
    public WorkflowExecutionState transferOwnership(String newOwnerId,
                                                     String newLeaseId,
                                                     Instant expiresAt,
                                                     long newFencingToken,
                                                     String reason) {
        Instant now = Instant.now();
        return this.toBuilder()
                .previousOwnerId(ownerNodeId)
                .ownerNodeId(newOwnerId)
                .leaseId(newLeaseId)
                .leaseAcquiredAt(now)
                .leaseRenewedAt(now)
                .leaseExpiresAt(expiresAt)
                .leaseVersion(leaseVersion + 1)
                .ownershipEpoch(ownershipEpoch + 1)
                .fencingToken(newFencingToken)
                .ownershipStatus(OwnershipStatus.OWNED)
                .ownershipStatusChangedAt(now)
                .ownershipStatusReason("Ownership transferred: " + reason)
                .ownershipTransferredAt(now)
                .transferReason(reason)
                .recoveryAttempts(0)
                .lastRecoveryError(null)
                .recoveryTargetNodeId(null)
                .lastUpdatedAt(now)
                .version(version + 1)
                .build();
    }

    /**
     * Creates a copy with ownership released.
     *
     * @param reason the reason for release
     * @return a new state with released ownership
     */
    public WorkflowExecutionState releaseOwnership(String reason) {
        Instant now = Instant.now();
        return this.toBuilder()
                .previousOwnerId(ownerNodeId)
                .ownerNodeId(null)
                .leaseExpiresAt(now) // Expire immediately
                .ownershipStatus(OwnershipStatus.RELEASED)
                .ownershipStatusChangedAt(now)
                .ownershipStatusReason(reason)
                .ownershipTransferredAt(now)
                .transferReason(reason)
                .lastUpdatedAt(now)
                .version(version + 1)
                .build();
    }

    /**
     * Checks if this state has a higher fencing token than another.
     * Used to resolve split-brain scenarios.
     *
     * @param other the other state to compare
     * @return true if this state has precedence
     */
    public boolean hasFencingPrecedenceOver(WorkflowExecutionState other) {
        if (other == null) {
            return true;
        }
        return this.fencingToken > other.fencingToken;
    }

    /**
     * Gets the ownership epoch.
     * Epoch increases each time ownership transfers to a new node.
     *
     * @return the ownership epoch
     */
    public long getOwnershipEpoch() {
        return ownershipEpoch;
    }

    /**
     * Returns the owner node ID as an Optional.
     *
     * @return Optional containing the owner node ID
     */
    public Optional<String> getOwnerNodeIdOptional() {
        return Optional.ofNullable(ownerNodeId);
    }

    /**
     * Returns the previous owner ID as an Optional.
     *
     * @return Optional containing the previous owner ID
     */
    public Optional<String> getPreviousOwnerIdOptional() {
        return Optional.ofNullable(previousOwnerId);
    }

    // ========== Ownership Status Query Methods ==========

    /**
     * Checks if the workflow is currently owned by an active node.
     *
     * @return true if owned and lease is valid
     */
    public boolean isCurrentlyOwned() {
        return ownershipStatus == OwnershipStatus.OWNED && isLeaseValid();
    }

    /**
     * Checks if the workflow is orphaned (owner failed or lease expired).
     *
     * @return true if orphaned and available for takeover
     */
    public boolean isOrphaned() {
        return ownershipStatus == OwnershipStatus.ORPHANED ||
               ownershipStatus == OwnershipStatus.LEASE_EXPIRED ||
               (ownershipStatus == OwnershipStatus.OWNED && hasLeaseExpired());
    }

    /**
     * Checks if the workflow is available for ownership acquisition.
     *
     * @return true if available for takeover
     */
    public boolean isAvailableForTakeover() {
        return ownershipStatus == OwnershipStatus.UNOWNED ||
               ownershipStatus == OwnershipStatus.ORPHANED ||
               ownershipStatus == OwnershipStatus.RELEASED ||
               ownershipStatus == OwnershipStatus.LEASE_EXPIRED ||
               ownershipStatus == OwnershipStatus.REVOKED ||
               (ownershipStatus == OwnershipStatus.OWNED && hasLeaseExpired());
    }

    /**
     * Checks if recovery is in progress for this workflow.
     *
     * @return true if recovery is pending
     */
    public boolean isRecoveryInProgress() {
        return ownershipStatus == OwnershipStatus.PENDING_TAKEOVER ||
               ownershipStatus == OwnershipStatus.ACQUIRING;
    }

    /**
     * Checks if recovery can be attempted.
     *
     * @return true if more recovery attempts are available
     */
    public boolean canAttemptRecovery() {
        return recoveryAttempts < maxRecoveryAttempts;
    }

    /**
     * Gets the computed ownership status based on current state.
     * This recalculates the status considering lease expiration.
     *
     * @return the effective ownership status
     */
    public OwnershipStatus getEffectiveOwnershipStatus() {
        if (ownershipStatus == OwnershipStatus.OWNED && hasLeaseExpired()) {
            return OwnershipStatus.LEASE_EXPIRED;
        }
        return ownershipStatus;
    }

    // ========== Ownership Status Transition Methods ==========

    /**
     * Creates a copy with ownership status changed.
     *
     * @param newStatus the new ownership status
     * @param reason the reason for the status change
     * @return a new state with updated ownership status
     */
    public WorkflowExecutionState withOwnershipStatus(OwnershipStatus newStatus, String reason) {
        return this.toBuilder()
                .ownershipStatus(newStatus)
                .ownershipStatusChangedAt(Instant.now())
                .ownershipStatusReason(reason)
                .lastUpdatedAt(Instant.now())
                .version(version + 1)
                .build();
    }

    /**
     * Creates a copy marked as orphaned (owner failed).
     *
     * @param reason the reason for orphaning
     * @return a new state marked as orphaned
     */
    public WorkflowExecutionState markOrphaned(String reason) {
        Instant now = Instant.now();
        return this.toBuilder()
                .ownershipStatus(OwnershipStatus.ORPHANED)
                .ownershipStatusChangedAt(now)
                .ownershipStatusReason(reason)
                .status(ExecutionStatus.NEEDS_RECOVERY)
                .lastUpdatedAt(now)
                .version(version + 1)
                .build();
    }

    /**
     * Creates a copy indicating takeover is in progress.
     *
     * @param targetNodeId the node attempting to take over
     * @return a new state with pending takeover
     */
    public WorkflowExecutionState markPendingTakeover(String targetNodeId) {
        Instant now = Instant.now();
        return this.toBuilder()
                .ownershipStatus(OwnershipStatus.PENDING_TAKEOVER)
                .ownershipStatusChangedAt(now)
                .ownershipStatusReason("Takeover initiated by " + targetNodeId)
                .recoveryTargetNodeId(targetNodeId)
                .lastUpdatedAt(now)
                .version(version + 1)
                .build();
    }

    /**
     * Creates a copy with successful takeover completed.
     *
     * @param newOwnerId the new owner node ID
     * @param newLeaseId the new lease ID
     * @param leaseDuration the lease duration
     * @param newFencingToken the new fencing token
     * @return a new state with ownership transferred
     */
    public WorkflowExecutionState completeTakeover(String newOwnerId,
                                                    String newLeaseId,
                                                    java.time.Duration leaseDuration,
                                                    long newFencingToken) {
        Instant now = Instant.now();
        return this.toBuilder()
                .previousOwnerId(ownerNodeId)
                .ownerNodeId(newOwnerId)
                .leaseId(newLeaseId)
                .leaseAcquiredAt(now)
                .leaseRenewedAt(now)
                .leaseExpiresAt(now.plus(leaseDuration))
                .leaseVersion(leaseVersion + 1)
                .ownershipEpoch(ownershipEpoch + 1)
                .fencingToken(newFencingToken)
                .ownershipStatus(OwnershipStatus.OWNED)
                .ownershipStatusChangedAt(now)
                .ownershipStatusReason("Takeover completed from " + ownerNodeId)
                .ownershipTransferredAt(now)
                .transferReason("Takeover")
                .recoveryTargetNodeId(null)
                .recoveryAttempts(0) // Reset recovery attempts on successful takeover
                .lastRecoveryError(null)
                .status(isTerminal() ? status : ExecutionStatus.PENDING) // Reset to PENDING if not terminal
                .lastUpdatedAt(now)
                .version(version + 1)
                .build();
    }

    /**
     * Creates a copy recording a failed recovery attempt.
     *
     * @param error the error that occurred
     * @return a new state with recovery attempt recorded
     */
    public WorkflowExecutionState recordRecoveryFailure(String error) {
        Instant now = Instant.now();
        return this.toBuilder()
                .recoveryAttempts(recoveryAttempts + 1)
                .lastRecoveryAttemptAt(now)
                .lastRecoveryError(error)
                .ownershipStatusReason("Recovery failed: " + error)
                .recoveryTargetNodeId(null) // Clear target on failure
                .lastUpdatedAt(now)
                .version(version + 1)
                .build();
    }

    /**
     * Creates a copy with ownership suspended (for draining).
     *
     * @param reason the reason for suspension
     * @return a new state with suspended ownership
     */
    public WorkflowExecutionState suspendOwnership(String reason) {
        Instant now = Instant.now();
        return this.toBuilder()
                .ownershipStatus(OwnershipStatus.SUSPENDED)
                .ownershipStatusChangedAt(now)
                .ownershipStatusReason(reason)
                .status(ExecutionStatus.PAUSED)
                .lastUpdatedAt(now)
                .version(version + 1)
                .build();
    }

    /**
     * Creates a copy with ownership revoked.
     *
     * @param reason the reason for revocation
     * @return a new state with revoked ownership
     */
    public WorkflowExecutionState revokeOwnership(String reason) {
        Instant now = Instant.now();
        return this.toBuilder()
                .previousOwnerId(ownerNodeId)
                .ownerNodeId(null)
                .leaseExpiresAt(now)
                .ownershipStatus(OwnershipStatus.REVOKED)
                .ownershipStatusChangedAt(now)
                .ownershipStatusReason(reason)
                .ownershipTransferredAt(now)
                .transferReason(reason)
                .status(ExecutionStatus.NEEDS_RECOVERY)
                .lastUpdatedAt(now)
                .version(version + 1)
                .build();
    }

    // ========== Enhanced Existing Methods ==========

    /**
     * Creates a copy with ownership lease information applied (enhanced).
     *
     * @param newLeaseId the lease ID
     * @param newOwnerId the owner node ID
     * @param acquiredAt when the lease was acquired
     * @param expiresAt when the lease expires
     * @param newFencingToken the fencing token
     * @return a new state with ownership information
     */
    public WorkflowExecutionState acquireOwnership(String newLeaseId,
                                                    String newOwnerId,
                                                    Instant acquiredAt,
                                                    Instant expiresAt,
                                                    long newFencingToken) {
        Instant now = Instant.now();
        return this.toBuilder()
                .leaseId(newLeaseId)
                .ownerNodeId(newOwnerId)
                .leaseAcquiredAt(acquiredAt)
                .leaseRenewedAt(acquiredAt)
                .leaseExpiresAt(expiresAt)
                .leaseVersion(leaseVersion + 1)
                .ownershipEpoch(ownershipEpoch + 1)
                .fencingToken(newFencingToken)
                .ownershipStatus(OwnershipStatus.OWNED)
                .ownershipStatusChangedAt(now)
                .ownershipStatusReason("Ownership acquired")
                .recoveryAttempts(0)
                .lastRecoveryError(null)
                .recoveryTargetNodeId(null)
                .lastUpdatedAt(now)
                .version(version + 1)
                .build();
    }

    /**
     * Gets information about the current ownership as a summary map.
     *
     * @return map containing ownership summary
     */
    public Map<String, Object> getOwnershipSummary() {
        Map<String, Object> summary = new java.util.LinkedHashMap<>();
        summary.put("currentOwnerId", ownerNodeId);
        summary.put("ownershipStatus", getEffectiveOwnershipStatus());
        summary.put("leaseExpiryTime", leaseExpiresAt);
        summary.put("leaseValid", isLeaseValid());
        summary.put("fencingToken", fencingToken);
        summary.put("ownershipEpoch", ownershipEpoch);
        summary.put("previousOwnerId", previousOwnerId);
        summary.put("availableForTakeover", isAvailableForTakeover());
        summary.put("recoveryAttempts", recoveryAttempts);
        summary.put("canAttemptRecovery", canAttemptRecovery());
        getRemainingLeaseTime().ifPresent(d -> summary.put("remainingLeaseTime", d));
        return Collections.unmodifiableMap(summary);
    }

    /**
     * Creates a validation result for ownership state.
     *
     * @param claimingNodeId the node attempting to operate
     * @param requiredFencingToken the minimum fencing token required
     * @return validation result with details
     */
    public OwnershipValidationResult validateOwnership(String claimingNodeId, long requiredFencingToken) {
        if (ownerNodeId == null) {
            return OwnershipValidationResult.notOwned("No owner assigned");
        }
        if (!ownerNodeId.equals(claimingNodeId)) {
            return OwnershipValidationResult.wrongOwner(ownerNodeId, claimingNodeId);
        }
        if (hasLeaseExpired()) {
            return OwnershipValidationResult.leaseExpired(leaseExpiresAt);
        }
        if (fencingToken < requiredFencingToken) {
            return OwnershipValidationResult.staleFencingToken(fencingToken, requiredFencingToken);
        }
        if (ownershipStatus != OwnershipStatus.OWNED) {
            return OwnershipValidationResult.invalidStatus(ownershipStatus);
        }
        return OwnershipValidationResult.valid();
    }

    /**
     * Result of ownership validation.
     */
    public static class OwnershipValidationResult {
        private final boolean valid;
        private final String reason;
        private final Map<String, Object> details;

        private OwnershipValidationResult(boolean valid, String reason, Map<String, Object> details) {
            this.valid = valid;
            this.reason = reason;
            this.details = details != null ? Collections.unmodifiableMap(details) : Collections.emptyMap();
        }

        public boolean isValid() { return valid; }
        public String getReason() { return reason; }
        public Map<String, Object> getDetails() { return details; }

        public static OwnershipValidationResult valid() {
            return new OwnershipValidationResult(true, "Valid ownership", null);
        }

        public static OwnershipValidationResult notOwned(String reason) {
            return new OwnershipValidationResult(false, reason, null);
        }

        public static OwnershipValidationResult wrongOwner(String actualOwner, String claimingOwner) {
            Map<String, Object> details = Map.of(
                    "actualOwner", actualOwner,
                    "claimingOwner", claimingOwner
            );
            return new OwnershipValidationResult(false,
                    "Ownership belongs to different node", details);
        }

        public static OwnershipValidationResult leaseExpired(Instant expiredAt) {
            Map<String, Object> details = Map.of("expiredAt", expiredAt);
            return new OwnershipValidationResult(false, "Lease has expired", details);
        }

        public static OwnershipValidationResult staleFencingToken(long actual, long required) {
            Map<String, Object> details = Map.of(
                    "actualToken", actual,
                    "requiredToken", required
            );
            return new OwnershipValidationResult(false, "Stale fencing token", details);
        }

        public static OwnershipValidationResult invalidStatus(OwnershipStatus status) {
            Map<String, Object> details = Map.of("status", status);
            return new OwnershipValidationResult(false,
                    "Invalid ownership status: " + status, details);
        }

        @Override
        public String toString() {
            return "OwnershipValidationResult{valid=" + valid + ", reason='" + reason + "', details=" + details + "}";
        }
    }
}
