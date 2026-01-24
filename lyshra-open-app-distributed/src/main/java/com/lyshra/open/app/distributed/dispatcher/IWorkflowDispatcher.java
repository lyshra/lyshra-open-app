package com.lyshra.open.app.distributed.dispatcher;

import com.lyshra.open.app.distributed.state.WorkflowExecutionState;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Interface for dispatching workflow executions with ownership awareness.
 *
 * The dispatcher is the gatekeeper that ensures workflows are only executed
 * by the node that successfully acquired ownership. It implements the following
 * guarantees:
 *
 * 1. Exclusive Execution - Only the owner node executes a workflow
 * 2. No Duplicate Execution - Ownership prevents concurrent execution
 * 3. Partition Awareness - Workflows are dispatched to the correct partition owner
 * 4. Retry Handling - Configurable retry behavior for transient failures
 *
 * Dispatch Flow:
 * <pre>
 * dispatch() →
 *   1. Validate partition assignment
 *   2. Attempt ownership acquisition
 *   3. If acquired → Return DispatchedLocally result
 *   4. If owned by other → Return RoutedToOther result
 *   5. If acquisition failed → Apply retry policy or return Rejected
 * </pre>
 *
 * Thread Safety: Implementations must be thread-safe.
 *
 * Usage:
 * <pre>
 * IWorkflowDispatcher dispatcher = new OwnershipAwareWorkflowDispatcher(...);
 *
 * dispatcher.dispatch(dispatchRequest)
 *     .flatMap(result -> {
 *         if (result.isDispatchedLocally()) {
 *             return executeWorkflow(result.getOwnershipContext());
 *         } else if (result.isRoutedToOther()) {
 *             return forwardToNode(result.getTargetNodeId());
 *         } else {
 *             return handleRejection(result);
 *         }
 *     });
 * </pre>
 */
public interface IWorkflowDispatcher {

    // ========== Lifecycle ==========

    /**
     * Initializes the dispatcher.
     *
     * @return Mono that completes when initialization is done
     */
    Mono<Void> initialize();

    /**
     * Shuts down the dispatcher gracefully.
     *
     * @return Mono that completes when shutdown is done
     */
    Mono<Void> shutdown();

    /**
     * Checks if the dispatcher is active and ready.
     *
     * @return true if active
     */
    boolean isActive();

    // ========== Dispatch Operations ==========

    /**
     * Dispatches a new workflow execution with ownership acquisition.
     *
     * This method will:
     * 1. Generate an execution key based on the workflow identifier
     * 2. Determine if this node should handle the workflow (partition check)
     * 3. Attempt to acquire ownership if this is the correct node
     * 4. Return the dispatch result indicating next steps
     *
     * @param request the dispatch request
     * @return Mono containing the dispatch result
     */
    Mono<WorkflowDispatchResult> dispatch(DispatchRequest request);

    /**
     * Dispatches a workflow execution for resumption.
     *
     * Used when recovering or resuming a previously started workflow.
     * May require force-acquisition if the previous owner failed.
     *
     * @param executionKey the existing execution key
     * @param options dispatch options
     * @return Mono containing the dispatch result
     */
    Mono<WorkflowDispatchResult> dispatchForResume(String executionKey, DispatchOptions options);

    /**
     * Dispatches multiple workflows in a batch.
     *
     * For efficiency, this acquires ownership for multiple workflows
     * in a single coordination call where possible.
     *
     * @param requests the set of dispatch requests
     * @return Flux of dispatch results (one per request)
     */
    Flux<WorkflowDispatchResult> dispatchBatch(Set<DispatchRequest> requests);

    // ========== Ownership Verification ==========

    /**
     * Verifies that the current node still owns a workflow execution.
     *
     * Should be called periodically during long-running workflows
     * to ensure ownership hasn't been lost (e.g., due to lease expiration).
     *
     * @param executionKey the execution key
     * @param fencingToken the fencing token to validate
     * @return Mono containing true if ownership is still valid
     */
    Mono<Boolean> verifyOwnership(String executionKey, long fencingToken);

    /**
     * Renews ownership for a dispatched workflow.
     *
     * Called during execution to extend the lease and prevent
     * ownership expiration for long-running workflows.
     *
     * @param executionKey the execution key
     * @return Mono containing true if renewal succeeded
     */
    Mono<Boolean> renewOwnership(String executionKey);

    // ========== Ownership Release ==========

    /**
     * Releases ownership after workflow completion or failure.
     *
     * Must be called when workflow execution ends, regardless of outcome.
     *
     * @param executionKey the execution key
     * @param reason the reason for release
     * @return Mono that completes when ownership is released
     */
    Mono<Void> releaseOwnership(String executionKey, String reason);

    /**
     * Releases ownership for multiple workflows.
     *
     * @param executionKeys the execution keys
     * @param reason the reason for release
     * @return Mono containing the number of released workflows
     */
    Mono<Integer> releaseOwnershipBatch(Set<String> executionKeys, String reason);

    // ========== Query Operations ==========

    /**
     * Gets all workflows currently dispatched to this node.
     *
     * @return set of execution keys
     */
    Set<String> getDispatchedWorkflows();

    /**
     * Gets the count of dispatched workflows.
     *
     * @return the count
     */
    int getDispatchedCount();

    /**
     * Checks if a workflow is currently dispatched to this node.
     *
     * @param executionKey the execution key
     * @return true if dispatched locally
     */
    boolean isDispatchedLocally(String executionKey);

    /**
     * Gets the target node for a given execution key.
     *
     * @param executionKey the execution key
     * @return Mono containing the target node ID
     */
    Mono<String> getTargetNode(String executionKey);

    // ========== Configuration ==========

    /**
     * Gets the retry policy used by this dispatcher.
     *
     * @return the retry policy
     */
    DispatchRetryPolicy getRetryPolicy();

    /**
     * Sets the retry policy.
     *
     * @param policy the new retry policy
     */
    void setRetryPolicy(DispatchRetryPolicy policy);

    /**
     * Gets the dispatcher metrics.
     *
     * @return the metrics
     */
    DispatcherMetrics getMetrics();

    // ========== Event Listeners ==========

    /**
     * Adds a listener for dispatch events.
     *
     * @param listener the listener
     */
    void addDispatchListener(IDispatchEventListener listener);

    /**
     * Removes a dispatch event listener.
     *
     * @param listener the listener to remove
     */
    void removeDispatchListener(IDispatchEventListener listener);

    // ========== Inner Classes ==========

    /**
     * Request for dispatching a new workflow.
     */
    record DispatchRequest(
            ILyshraOpenAppWorkflowIdentifier workflowIdentifier,
            ILyshraOpenAppContext context,
            String executionId,
            DispatchOptions options
    ) {
        public static DispatchRequest of(ILyshraOpenAppWorkflowIdentifier identifier,
                                         ILyshraOpenAppContext context,
                                         String executionId) {
            return new DispatchRequest(identifier, context, executionId, DispatchOptions.defaults());
        }

        public static DispatchRequest withOptions(ILyshraOpenAppWorkflowIdentifier identifier,
                                                  ILyshraOpenAppContext context,
                                                  String executionId,
                                                  DispatchOptions options) {
            return new DispatchRequest(identifier, context, executionId, options);
        }
    }

    /**
     * Listener for dispatch-related events.
     */
    interface IDispatchEventListener {
        /**
         * Called when a workflow is successfully dispatched locally.
         */
        void onDispatchedLocally(String executionKey, WorkflowDispatchResult result);

        /**
         * Called when a workflow is routed to another node.
         */
        void onRoutedToOther(String executionKey, String targetNodeId);

        /**
         * Called when a dispatch is rejected.
         */
        void onDispatchRejected(String executionKey, WorkflowDispatchResult.RejectReason reason);

        /**
         * Called when ownership is released.
         */
        void onOwnershipReleased(String executionKey, String reason);

        /**
         * Called when a dispatch retry occurs.
         */
        default void onDispatchRetry(String executionKey, int attemptNumber, String reason) {}
    }
}
