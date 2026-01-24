package com.lyshra.open.app.distributed.executor;

import com.lyshra.open.app.distributed.state.WorkflowExecutionState;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable result of a distributed workflow execution.
 *
 * Contains the final context, execution metadata, and status information.
 */
@Getter
@Builder
@ToString
public final class DistributedExecutionResult {

    /**
     * Possible outcomes of a distributed execution.
     */
    public enum Outcome {
        /**
         * Workflow completed successfully.
         */
        COMPLETED,

        /**
         * Workflow failed with an error.
         */
        FAILED,

        /**
         * Workflow was cancelled.
         */
        CANCELLED,

        /**
         * Workflow was paused.
         */
        PAUSED,

        /**
         * Workflow was routed to another node.
         */
        ROUTED_TO_OTHER_NODE,

        /**
         * Workflow ownership could not be acquired.
         */
        OWNERSHIP_DENIED,

        /**
         * Workflow execution timed out.
         */
        TIMEOUT
    }

    private final Outcome outcome;
    private final String executionKey;
    private final String workflowIdentifier;
    private final ILyshraOpenAppContext finalContext;
    private final WorkflowExecutionState finalState;
    private final Instant startTime;
    private final Instant endTime;
    private final String executingNodeId;
    private final String targetNodeId;
    private final String errorMessage;
    private final Throwable exception;
    private final int stepsCompleted;

    private DistributedExecutionResult(Outcome outcome,
                                        String executionKey,
                                        String workflowIdentifier,
                                        ILyshraOpenAppContext finalContext,
                                        WorkflowExecutionState finalState,
                                        Instant startTime,
                                        Instant endTime,
                                        String executingNodeId,
                                        String targetNodeId,
                                        String errorMessage,
                                        Throwable exception,
                                        int stepsCompleted) {
        this.outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        this.executionKey = Objects.requireNonNull(executionKey, "executionKey must not be null");
        this.workflowIdentifier = workflowIdentifier;
        this.finalContext = finalContext;
        this.finalState = finalState;
        this.startTime = startTime;
        this.endTime = endTime;
        this.executingNodeId = executingNodeId;
        this.targetNodeId = targetNodeId;
        this.errorMessage = errorMessage;
        this.exception = exception;
        this.stepsCompleted = stepsCompleted;
    }

    /**
     * Creates a successful completion result.
     */
    public static DistributedExecutionResult completed(String executionKey,
                                                        String workflowIdentifier,
                                                        ILyshraOpenAppContext context,
                                                        WorkflowExecutionState state,
                                                        Instant start,
                                                        String nodeId) {
        return DistributedExecutionResult.builder()
                .outcome(Outcome.COMPLETED)
                .executionKey(executionKey)
                .workflowIdentifier(workflowIdentifier)
                .finalContext(context)
                .finalState(state)
                .startTime(start)
                .endTime(Instant.now())
                .executingNodeId(nodeId)
                .stepsCompleted(state != null ? state.getCompletedSteps() : 0)
                .build();
    }

    /**
     * Creates a failure result.
     */
    public static DistributedExecutionResult failed(String executionKey,
                                                     String workflowIdentifier,
                                                     WorkflowExecutionState state,
                                                     Instant start,
                                                     String nodeId,
                                                     String error,
                                                     Throwable exception) {
        return DistributedExecutionResult.builder()
                .outcome(Outcome.FAILED)
                .executionKey(executionKey)
                .workflowIdentifier(workflowIdentifier)
                .finalState(state)
                .startTime(start)
                .endTime(Instant.now())
                .executingNodeId(nodeId)
                .errorMessage(error)
                .exception(exception)
                .stepsCompleted(state != null ? state.getCompletedSteps() : 0)
                .build();
    }

    /**
     * Creates a routed-to-other-node result.
     */
    public static DistributedExecutionResult routedToOtherNode(String executionKey,
                                                                String targetNodeId) {
        return DistributedExecutionResult.builder()
                .outcome(Outcome.ROUTED_TO_OTHER_NODE)
                .executionKey(executionKey)
                .targetNodeId(targetNodeId)
                .build();
    }

    /**
     * Creates an ownership denied result.
     */
    public static DistributedExecutionResult ownershipDenied(String executionKey,
                                                              String currentOwner) {
        return DistributedExecutionResult.builder()
                .outcome(Outcome.OWNERSHIP_DENIED)
                .executionKey(executionKey)
                .targetNodeId(currentOwner)
                .errorMessage("Ownership denied - currently owned by " + currentOwner)
                .build();
    }

    /**
     * Checks if the execution completed successfully.
     */
    public boolean isSuccessful() {
        return outcome == Outcome.COMPLETED;
    }

    /**
     * Checks if the execution failed.
     */
    public boolean isFailed() {
        return outcome == Outcome.FAILED;
    }

    /**
     * Checks if the execution was handled locally.
     */
    public boolean wasHandledLocally() {
        return outcome != Outcome.ROUTED_TO_OTHER_NODE;
    }

    /**
     * Returns the execution duration.
     */
    public Optional<Duration> getDuration() {
        if (startTime == null || endTime == null) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(startTime, endTime));
    }

    /**
     * Returns the error message as an Optional.
     */
    public Optional<String> getErrorMessageOptional() {
        return Optional.ofNullable(errorMessage);
    }

    /**
     * Returns the exception as an Optional.
     */
    public Optional<Throwable> getExceptionOptional() {
        return Optional.ofNullable(exception);
    }

    /**
     * Returns the final context as an Optional.
     */
    public Optional<ILyshraOpenAppContext> getFinalContextOptional() {
        return Optional.ofNullable(finalContext);
    }
}
