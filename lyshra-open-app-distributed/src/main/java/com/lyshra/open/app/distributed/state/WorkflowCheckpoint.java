package com.lyshra.open.app.distributed.state;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a checkpoint in workflow execution.
 *
 * Checkpoints capture the workflow state at a specific point in time,
 * enabling:
 * - Recovery from failures
 * - Resumption from specific steps
 * - Debugging and audit
 *
 * Thread Safety: This class is immutable and thread-safe.
 */
@Getter
@Builder
@ToString
public final class WorkflowCheckpoint implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Types of checkpoints.
     */
    public enum CheckpointType {
        /**
         * Checkpoint before step execution.
         */
        PRE_STEP,

        /**
         * Checkpoint after step execution.
         */
        POST_STEP,

        /**
         * Checkpoint before retry.
         */
        PRE_RETRY,

        /**
         * Periodic automatic checkpoint.
         */
        PERIODIC,

        /**
         * Checkpoint before workflow suspension.
         */
        SUSPENSION,

        /**
         * User-triggered checkpoint.
         */
        MANUAL
    }

    private final String checkpointId;
    private final String executionKey;
    private final CheckpointType type;
    private final String stepName;
    private final int stepIndex;
    private final Instant timestamp;

    // Serialized context state
    private final byte[] contextData;
    private final byte[] variablesData;

    // Execution metadata
    private final String nodeId;
    private final long stateVersion;
    private final String previousBranch;

    // Error context (if checkpoint is due to error)
    private final String errorMessage;
    private final String errorStackTrace;

    private WorkflowCheckpoint(String checkpointId,
                                String executionKey,
                                CheckpointType type,
                                String stepName,
                                int stepIndex,
                                Instant timestamp,
                                byte[] contextData,
                                byte[] variablesData,
                                String nodeId,
                                long stateVersion,
                                String previousBranch,
                                String errorMessage,
                                String errorStackTrace) {
        this.checkpointId = Objects.requireNonNull(checkpointId, "checkpointId must not be null");
        this.executionKey = Objects.requireNonNull(executionKey, "executionKey must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.stepName = stepName;
        this.stepIndex = stepIndex;
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.contextData = contextData;
        this.variablesData = variablesData;
        this.nodeId = nodeId;
        this.stateVersion = stateVersion;
        this.previousBranch = previousBranch;
        this.errorMessage = errorMessage;
        this.errorStackTrace = errorStackTrace;
    }

    /**
     * Creates a pre-step checkpoint.
     */
    public static WorkflowCheckpoint preStep(String executionKey, String stepName, int stepIndex,
                                              byte[] contextData, byte[] variablesData,
                                              String nodeId, long version) {
        return WorkflowCheckpoint.builder()
                .checkpointId(generateCheckpointId())
                .executionKey(executionKey)
                .type(CheckpointType.PRE_STEP)
                .stepName(stepName)
                .stepIndex(stepIndex)
                .timestamp(Instant.now())
                .contextData(contextData)
                .variablesData(variablesData)
                .nodeId(nodeId)
                .stateVersion(version)
                .build();
    }

    /**
     * Creates a post-step checkpoint.
     */
    public static WorkflowCheckpoint postStep(String executionKey, String stepName, int stepIndex,
                                               String branch, byte[] contextData, byte[] variablesData,
                                               String nodeId, long version) {
        return WorkflowCheckpoint.builder()
                .checkpointId(generateCheckpointId())
                .executionKey(executionKey)
                .type(CheckpointType.POST_STEP)
                .stepName(stepName)
                .stepIndex(stepIndex)
                .timestamp(Instant.now())
                .contextData(contextData)
                .variablesData(variablesData)
                .nodeId(nodeId)
                .stateVersion(version)
                .previousBranch(branch)
                .build();
    }

    /**
     * Creates an error checkpoint.
     */
    public static WorkflowCheckpoint error(String executionKey, String stepName, int stepIndex,
                                            byte[] contextData, byte[] variablesData,
                                            String nodeId, long version,
                                            String errorMessage, String stackTrace) {
        return WorkflowCheckpoint.builder()
                .checkpointId(generateCheckpointId())
                .executionKey(executionKey)
                .type(CheckpointType.PRE_RETRY)
                .stepName(stepName)
                .stepIndex(stepIndex)
                .timestamp(Instant.now())
                .contextData(contextData)
                .variablesData(variablesData)
                .nodeId(nodeId)
                .stateVersion(version)
                .errorMessage(errorMessage)
                .errorStackTrace(stackTrace)
                .build();
    }

    /**
     * Checks if this checkpoint has error information.
     */
    public boolean hasError() {
        return errorMessage != null;
    }

    /**
     * Returns the error message as an Optional.
     */
    public Optional<String> getErrorMessageOptional() {
        return Optional.ofNullable(errorMessage);
    }

    /**
     * Returns the previous branch as an Optional.
     */
    public Optional<String> getPreviousBranchOptional() {
        return Optional.ofNullable(previousBranch);
    }

    /**
     * Checks if this is a pre-execution checkpoint.
     */
    public boolean isPreExecution() {
        return type == CheckpointType.PRE_STEP || type == CheckpointType.PRE_RETRY;
    }

    /**
     * Checks if this is a post-execution checkpoint.
     */
    public boolean isPostExecution() {
        return type == CheckpointType.POST_STEP;
    }

    private static String generateCheckpointId() {
        return "cp-" + System.currentTimeMillis() + "-" +
               java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
