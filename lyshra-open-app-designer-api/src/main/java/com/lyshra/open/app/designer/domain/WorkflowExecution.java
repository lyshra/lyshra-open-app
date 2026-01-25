package com.lyshra.open.app.designer.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a workflow execution instance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowExecution {

    private String id;
    private String workflowDefinitionId;
    private String workflowVersionId;
    private String workflowName;
    private String versionNumber;
    private ExecutionStatus status;
    private Instant startedAt;
    private Instant completedAt;
    private String currentStepId;
    private String currentStepName;
    private Map<String, Object> inputData;
    private Map<String, Object> outputData;
    private Map<String, Object> variables;
    private String errorMessage;
    private String errorCode;
    private List<StepExecutionLog> stepLogs;
    private String triggeredBy;
    private String correlationId;
    private Map<String, String> metadata;

    /**
     * Gets the duration of the execution.
     *
     * @return Optional containing the duration if completed
     */
    public Optional<Duration> getDuration() {
        if (startedAt == null) {
            return Optional.empty();
        }
        Instant end = completedAt != null ? completedAt : Instant.now();
        return Optional.of(Duration.between(startedAt, end));
    }

    /**
     * Checks if the execution has completed (successfully or with error).
     *
     * @return true if the execution has completed
     */
    public boolean isCompleted() {
        return status == ExecutionStatus.COMPLETED ||
               status == ExecutionStatus.FAILED ||
               status == ExecutionStatus.ABORTED;
    }

    /**
     * Checks if the execution is currently running.
     *
     * @return true if the execution is running
     */
    public boolean isRunning() {
        return status == ExecutionStatus.RUNNING ||
               status == ExecutionStatus.WAITING ||
               status == ExecutionStatus.RETRYING;
    }
}
