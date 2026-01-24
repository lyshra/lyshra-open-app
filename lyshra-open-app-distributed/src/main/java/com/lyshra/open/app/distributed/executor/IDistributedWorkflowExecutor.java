package com.lyshra.open.app.distributed.executor;

import com.lyshra.open.app.distributed.state.WorkflowExecutionState;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Interface for distributed workflow execution.
 *
 * This executor extends the local workflow executor with distributed capabilities:
 * - Partition-aware routing
 * - Ownership verification before execution
 * - Durable state management
 * - Recovery from failures
 * - Checkpoint-based resumption
 *
 * Design Pattern: Decorator Pattern - wraps the local executor with distributed concerns.
 */
public interface IDistributedWorkflowExecutor {

    /**
     * Initializes the distributed executor.
     *
     * @return Mono that completes when initialization is done
     */
    Mono<Void> initialize();

    /**
     * Shuts down the distributed executor.
     *
     * @return Mono that completes when shutdown is done
     */
    Mono<Void> shutdown();

    /**
     * Submits a workflow for distributed execution.
     *
     * The executor will:
     * 1. Determine the target partition for the workflow
     * 2. Verify ownership or forward to the correct node
     * 3. Persist the execution state
     * 4. Execute the workflow
     * 5. Update state on completion/failure
     *
     * @param identifier the workflow identifier
     * @param context the initial context
     * @param executionId the unique execution ID
     * @return Mono containing the execution result
     */
    Mono<DistributedExecutionResult> submit(ILyshraOpenAppWorkflowIdentifier identifier,
                                             ILyshraOpenAppContext context,
                                             String executionId);

    /**
     * Resumes a workflow execution from its last checkpoint.
     *
     * @param executionKey the workflow execution key
     * @return Mono containing the execution result
     */
    Mono<DistributedExecutionResult> resume(String executionKey);

    /**
     * Cancels a running workflow execution.
     *
     * @param executionKey the workflow execution key
     * @return Mono containing true if cancellation succeeded
     */
    Mono<Boolean> cancel(String executionKey);

    /**
     * Pauses a running workflow execution.
     *
     * @param executionKey the workflow execution key
     * @return Mono containing true if pause succeeded
     */
    Mono<Boolean> pause(String executionKey);

    /**
     * Returns the current state of a workflow execution.
     *
     * @param executionKey the workflow execution key
     * @return Mono containing the execution state if found
     */
    Mono<Optional<WorkflowExecutionState>> getExecutionState(String executionKey);

    /**
     * Returns all workflow executions owned by this node.
     *
     * @return Flux of execution states
     */
    Flux<WorkflowExecutionState> getLocalExecutions();

    /**
     * Returns all workflow executions in a specific status.
     *
     * @param status the execution status
     * @return Flux of execution states
     */
    Flux<WorkflowExecutionState> getExecutionsByStatus(WorkflowExecutionState.ExecutionStatus status);

    /**
     * Triggers recovery of orphaned workflow executions.
     * Called when a node failure is detected.
     *
     * @param failedNodeId the failed node's ID
     * @return Mono containing the number of recovered executions
     */
    Mono<Integer> recoverOrphanedExecutions(String failedNodeId);

    /**
     * Checks if a workflow execution key would be handled by this node.
     *
     * @param executionKey the execution key
     * @return true if this node should handle the execution
     */
    boolean isLocalExecution(String executionKey);

    /**
     * Returns the node that should handle a specific execution.
     *
     * @param executionKey the execution key
     * @return Optional containing the target node ID
     */
    Optional<String> getTargetNode(String executionKey);

    /**
     * Registers a listener for execution lifecycle events.
     *
     * @param listener the lifecycle listener
     */
    void addExecutionListener(IDistributedExecutionListener listener);

    /**
     * Removes a previously registered execution listener.
     *
     * @param listener the listener to remove
     */
    void removeExecutionListener(IDistributedExecutionListener listener);
}
