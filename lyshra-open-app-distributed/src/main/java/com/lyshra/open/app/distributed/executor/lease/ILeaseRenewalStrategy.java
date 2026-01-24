package com.lyshra.open.app.distributed.executor.lease;

import com.lyshra.open.app.distributed.coordination.OwnershipContext;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Strategy interface for determining when and how to renew ownership leases.
 *
 * Different strategies can be implemented based on workflow characteristics:
 * - Fixed interval: Renew at regular intervals
 * - Adaptive: Adjust renewal frequency based on lease duration
 * - Step-aware: Renew more aggressively during long-running steps
 *
 * Thread Safety: Implementations must be thread-safe.
 */
public interface ILeaseRenewalStrategy {

    /**
     * Determines if a renewal should be attempted now.
     *
     * @param context the current ownership context
     * @param renewalState the current renewal state
     * @return true if renewal should be attempted
     */
    boolean shouldRenew(OwnershipContext context, LeaseRenewalState renewalState);

    /**
     * Calculates the next renewal time.
     *
     * @param context the current ownership context
     * @param renewalState the current renewal state
     * @return the instant when next renewal should occur
     */
    Instant calculateNextRenewalTime(OwnershipContext context, LeaseRenewalState renewalState);

    /**
     * Calculates the delay until the next renewal check.
     *
     * @param context the current ownership context
     * @param renewalState the current renewal state
     * @return duration until next check
     */
    Duration calculateNextCheckDelay(OwnershipContext context, LeaseRenewalState renewalState);

    /**
     * Determines the lease extension duration to request.
     *
     * @param context the current ownership context
     * @param renewalState the current renewal state
     * @return the duration to extend the lease by
     */
    Duration getExtensionDuration(OwnershipContext context, LeaseRenewalState renewalState);

    /**
     * Determines what action to take when renewal fails.
     *
     * @param context the current ownership context
     * @param renewalState the current renewal state
     * @param failureReason the reason for failure
     * @return the recommended action
     */
    RenewalFailureAction onRenewalFailure(OwnershipContext context,
                                           LeaseRenewalState renewalState,
                                           String failureReason);

    /**
     * Actions that can be taken when renewal fails.
     */
    enum RenewalFailureAction {
        /**
         * Retry the renewal immediately.
         */
        RETRY_IMMEDIATELY,

        /**
         * Retry after a delay.
         */
        RETRY_WITH_BACKOFF,

        /**
         * Continue execution but mark for urgent renewal.
         */
        CONTINUE_URGENT,

        /**
         * Abort the workflow execution immediately.
         */
        ABORT_EXECUTION,

        /**
         * Pause execution and wait for ownership to be re-acquired.
         */
        PAUSE_AND_REACQUIRE
    }

    /**
     * Immutable state tracking renewal attempts and timing.
     */
    record LeaseRenewalState(
            String executionKey,
            int renewalCount,
            int consecutiveFailures,
            Instant lastRenewalAttempt,
            Instant lastSuccessfulRenewal,
            Duration totalRenewalTime,
            boolean urgentRenewalNeeded
    ) {
        public static LeaseRenewalState initial(String executionKey) {
            return new LeaseRenewalState(
                    executionKey,
                    0,
                    0,
                    null,
                    Instant.now(),
                    Duration.ZERO,
                    false
            );
        }

        public LeaseRenewalState recordSuccess(Duration renewalDuration) {
            return new LeaseRenewalState(
                    executionKey,
                    renewalCount + 1,
                    0,
                    Instant.now(),
                    Instant.now(),
                    totalRenewalTime.plus(renewalDuration),
                    false
            );
        }

        public LeaseRenewalState recordFailure() {
            return new LeaseRenewalState(
                    executionKey,
                    renewalCount,
                    consecutiveFailures + 1,
                    Instant.now(),
                    lastSuccessfulRenewal,
                    totalRenewalTime,
                    consecutiveFailures >= 2
            );
        }

        public LeaseRenewalState markUrgent() {
            return new LeaseRenewalState(
                    executionKey,
                    renewalCount,
                    consecutiveFailures,
                    lastRenewalAttempt,
                    lastSuccessfulRenewal,
                    totalRenewalTime,
                    true
            );
        }

        public Duration timeSinceLastRenewal() {
            if (lastSuccessfulRenewal == null) {
                return Duration.ZERO;
            }
            return Duration.between(lastSuccessfulRenewal, Instant.now());
        }
    }
}
