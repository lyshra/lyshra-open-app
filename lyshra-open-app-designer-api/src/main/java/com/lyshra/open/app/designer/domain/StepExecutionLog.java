package com.lyshra.open.app.designer.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Log entry for a single step execution within a workflow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepExecutionLog {

    private String id;
    private String stepId;
    private String stepName;
    private String processorName;
    private StepExecutionStatus status;
    private Instant startedAt;
    private Instant completedAt;
    private Map<String, Object> inputData;
    private Map<String, Object> outputData;
    private String branch;
    private String errorMessage;
    private String errorCode;
    private String stackTrace;
    private int attemptNumber;
    private int totalAttempts;

    /**
     * Gets the duration of the step execution.
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
     * Checks if the step execution has failed.
     *
     * @return true if the step failed
     */
    public boolean isFailed() {
        return status == StepExecutionStatus.FAILED ||
               status == StepExecutionStatus.ABORTED;
    }

    /**
     * Checks if the step was retried.
     *
     * @return true if the step was retried
     */
    public boolean wasRetried() {
        return attemptNumber > 1;
    }
}
