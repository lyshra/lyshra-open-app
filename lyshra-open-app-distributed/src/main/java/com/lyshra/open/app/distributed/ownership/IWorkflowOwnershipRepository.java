package com.lyshra.open.app.distributed.ownership;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Repository interface for persisting and querying workflow ownership leases.
 *
 * This interface abstracts the storage mechanism for ownership data, allowing
 * implementations backed by:
 * - Relational databases (JDBC/JPA)
 * - NoSQL databases (MongoDB, Cassandra)
 * - Distributed caches (Redis, Hazelcast)
 * - In-memory storage (for testing)
 *
 * All operations use optimistic locking via the lease version field to handle
 * concurrent modifications safely.
 *
 * Design Pattern: Repository Pattern - separates persistence logic from domain.
 */
public interface IWorkflowOwnershipRepository {

    /**
     * Initializes the repository (creates tables/indexes if needed).
     *
     * @return Mono that completes when initialization is done
     */
    Mono<Void> initialize();

    /**
     * Shuts down the repository and releases resources.
     *
     * @return Mono that completes when shutdown is done
     */
    Mono<Void> shutdown();

    // ========== CRUD Operations ==========

    /**
     * Saves a new lease or updates an existing one.
     *
     * Uses optimistic locking - the operation will fail if the stored version
     * doesn't match the expected version in the lease.
     *
     * @param lease the lease to save
     * @return Mono containing the saved lease with updated version
     * @throws OptimisticLockingException if version mismatch occurs
     */
    Mono<WorkflowOwnershipLease> save(WorkflowOwnershipLease lease);

    /**
     * Finds a lease by workflow ID.
     *
     * @param workflowId the workflow execution ID
     * @return Mono containing the lease if found
     */
    Mono<Optional<WorkflowOwnershipLease>> findByWorkflowId(String workflowId);

    /**
     * Finds a lease by lease ID.
     *
     * @param leaseId the unique lease ID
     * @return Mono containing the lease if found
     */
    Mono<Optional<WorkflowOwnershipLease>> findByLeaseId(String leaseId);

    /**
     * Deletes a lease by workflow ID.
     *
     * @param workflowId the workflow execution ID
     * @return Mono containing true if deleted, false if not found
     */
    Mono<Boolean> deleteByWorkflowId(String workflowId);

    /**
     * Deletes a lease by lease ID.
     *
     * @param leaseId the lease ID
     * @return Mono containing true if deleted, false if not found
     */
    Mono<Boolean> deleteByLeaseId(String leaseId);

    /**
     * Checks if a lease exists for a workflow.
     *
     * @param workflowId the workflow execution ID
     * @return Mono containing true if exists
     */
    Mono<Boolean> existsByWorkflowId(String workflowId);

    // ========== Query Operations ==========

    /**
     * Finds all leases owned by a specific node.
     *
     * @param ownerId the owner node ID
     * @return Flux of leases
     */
    Flux<WorkflowOwnershipLease> findByOwnerId(String ownerId);

    /**
     * Finds all leases for a specific partition.
     *
     * @param partitionId the partition ID
     * @return Flux of leases
     */
    Flux<WorkflowOwnershipLease> findByPartitionId(int partitionId);

    /**
     * Finds all leases in a specific state.
     *
     * @param state the lease state
     * @return Flux of leases
     */
    Flux<WorkflowOwnershipLease> findByState(WorkflowOwnershipLease.LeaseState state);

    /**
     * Finds all active leases.
     *
     * @return Flux of active leases
     */
    Flux<WorkflowOwnershipLease> findAllActive();

    /**
     * Finds all expired leases.
     *
     * @return Flux of expired leases
     */
    Flux<WorkflowOwnershipLease> findAllExpired();

    /**
     * Finds leases that will expire within the specified duration.
     *
     * @param withinDuration the time window
     * @return Flux of leases expiring soon
     */
    Flux<WorkflowOwnershipLease> findExpiringWithin(Duration withinDuration);

    /**
     * Finds leases by cluster ID.
     *
     * @param clusterId the cluster ID
     * @return Flux of leases
     */
    Flux<WorkflowOwnershipLease> findByClusterId(String clusterId);

    /**
     * Finds leases by owner region.
     *
     * @param region the region
     * @return Flux of leases
     */
    Flux<WorkflowOwnershipLease> findByOwnerRegion(String region);

    // ========== Atomic Operations ==========

    /**
     * Atomically acquires a lease if not already owned.
     *
     * This is a compare-and-swap operation that:
     * 1. Checks if workflow has no active lease
     * 2. If available, creates new lease atomically
     *
     * @param workflowId the workflow ID
     * @param ownerId the claiming node ID
     * @param partitionId the partition ID
     * @param leaseDuration the lease duration
     * @param fencingToken the fencing token
     * @return Mono containing the acquired lease, or empty if already owned
     */
    Mono<Optional<WorkflowOwnershipLease>> tryAcquire(String workflowId,
                                                       String ownerId,
                                                       int partitionId,
                                                       Duration leaseDuration,
                                                       long fencingToken);

    /**
     * Atomically renews a lease if still owned by the specified node.
     *
     * @param workflowId the workflow ID
     * @param ownerId the expected owner
     * @param expectedVersion the expected version
     * @param extensionDuration the duration to extend
     * @return Mono containing the renewed lease, or empty if not owned/version mismatch
     */
    Mono<Optional<WorkflowOwnershipLease>> tryRenew(String workflowId,
                                                     String ownerId,
                                                     long expectedVersion,
                                                     Duration extensionDuration);

    /**
     * Atomically releases a lease if owned by the specified node.
     *
     * @param workflowId the workflow ID
     * @param ownerId the expected owner
     * @param reason the release reason
     * @return Mono containing true if released
     */
    Mono<Boolean> tryRelease(String workflowId, String ownerId, String reason);

    /**
     * Atomically transfers ownership from one node to another.
     *
     * @param workflowId the workflow ID
     * @param fromOwnerId the current owner
     * @param toOwnerId the new owner
     * @param reason the transfer reason
     * @return Mono containing the new lease, or empty if transfer failed
     */
    Mono<Optional<WorkflowOwnershipLease>> tryTransfer(String workflowId,
                                                        String fromOwnerId,
                                                        String toOwnerId,
                                                        String reason);

    // ========== Bulk Operations ==========

    /**
     * Releases all leases owned by a specific node.
     *
     * @param ownerId the owner node ID
     * @param reason the release reason
     * @return Mono containing the number of released leases
     */
    Mono<Integer> releaseAllByOwner(String ownerId, String reason);

    /**
     * Revokes all leases owned by a failed node.
     *
     * @param failedOwnerId the failed node ID
     * @param reason the revocation reason
     * @return Mono containing the number of revoked leases
     */
    Mono<Integer> revokeAllByOwner(String failedOwnerId, String reason);

    /**
     * Expires all leases that have passed their expiration time.
     *
     * @return Mono containing the number of expired leases
     */
    Mono<Integer> expireAllStale();

    /**
     * Deletes all leases older than the specified timestamp.
     *
     * @param before the cutoff timestamp
     * @return Mono containing the number of deleted leases
     */
    Mono<Integer> deleteAllBefore(Instant before);

    /**
     * Deletes all leases in terminal states (RELEASED, EXPIRED, REVOKED).
     *
     * @return Mono containing the number of deleted leases
     */
    Mono<Integer> deleteAllTerminal();

    // ========== Counting Operations ==========

    /**
     * Counts leases by owner.
     *
     * @param ownerId the owner node ID
     * @return Mono containing the count
     */
    Mono<Long> countByOwnerId(String ownerId);

    /**
     * Counts leases by partition.
     *
     * @param partitionId the partition ID
     * @return Mono containing the count
     */
    Mono<Long> countByPartitionId(int partitionId);

    /**
     * Counts leases by state.
     *
     * @param state the lease state
     * @return Mono containing the count
     */
    Mono<Long> countByState(WorkflowOwnershipLease.LeaseState state);

    /**
     * Counts all active leases.
     *
     * @return Mono containing the count
     */
    Mono<Long> countActive();

    // ========== Fencing Token Operations ==========

    /**
     * Gets the current maximum fencing token in use.
     *
     * @return Mono containing the max fencing token
     */
    Mono<Long> getMaxFencingToken();

    /**
     * Generates a new unique fencing token.
     *
     * @return Mono containing the new fencing token
     */
    Mono<Long> generateFencingToken();

    /**
     * Exception thrown when optimistic locking fails.
     */
    class OptimisticLockingException extends RuntimeException {
        private final String workflowId;
        private final long expectedVersion;
        private final long actualVersion;

        public OptimisticLockingException(String workflowId, long expectedVersion, long actualVersion) {
            super(String.format("Optimistic locking failed for workflow %s: expected version %d, actual %d",
                    workflowId, expectedVersion, actualVersion));
            this.workflowId = workflowId;
            this.expectedVersion = expectedVersion;
            this.actualVersion = actualVersion;
        }

        public String getWorkflowId() { return workflowId; }
        public long getExpectedVersion() { return expectedVersion; }
        public long getActualVersion() { return actualVersion; }
    }
}
