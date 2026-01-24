package com.lyshra.open.app.core.engine.signal.impl;

import com.lyshra.open.app.integration.contract.signal.ILyshraOpenAppSignal;
import com.lyshra.open.app.integration.contract.signal.ILyshraOpenAppSignalResult;
import lombok.Data;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of ILyshraOpenAppSignalResult.
 * Captures the outcome of signal processing.
 */
@Data
public class LyshraOpenAppSignalResult implements ILyshraOpenAppSignalResult {

    private final ILyshraOpenAppSignal signal;
    private final boolean success;
    private final SignalOutcome outcome;
    private final String errorMessage;
    private final String errorCode;
    private final String workflowState;
    private final String taskStatus;
    private final String nextStep;
    private final Instant processedAt;
    private final Map<String, Object> resultData;
    private final long processingDurationMillis;

    public LyshraOpenAppSignalResult(
            ILyshraOpenAppSignal signal,
            boolean success,
            SignalOutcome outcome,
            String errorMessage,
            String errorCode,
            Instant processedAt) {

        this.signal = signal;
        this.success = success;
        this.outcome = outcome;
        this.errorMessage = errorMessage;
        this.errorCode = errorCode;
        this.workflowState = null;
        this.taskStatus = null;
        this.nextStep = null;
        this.processedAt = processedAt;
        this.resultData = Collections.emptyMap();
        this.processingDurationMillis = processedAt.toEpochMilli() - signal.getCreatedAt().toEpochMilli();
    }

    @Override
    public Optional<String> getErrorMessage() {
        return Optional.ofNullable(errorMessage);
    }

    @Override
    public Optional<String> getErrorCode() {
        return Optional.ofNullable(errorCode);
    }

    @Override
    public Optional<String> getWorkflowState() {
        return Optional.ofNullable(workflowState);
    }

    @Override
    public Optional<String> getTaskStatus() {
        return Optional.ofNullable(taskStatus);
    }

    @Override
    public Optional<String> getNextStep() {
        return Optional.ofNullable(nextStep);
    }

    /**
     * Builder for creating signal results with all options.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ILyshraOpenAppSignal signal;
        private boolean success;
        private SignalOutcome outcome;
        private String errorMessage;
        private String errorCode;
        private String workflowState;
        private String taskStatus;
        private String nextStep;
        private Instant processedAt = Instant.now();
        private Map<String, Object> resultData = Collections.emptyMap();

        public Builder signal(ILyshraOpenAppSignal signal) {
            this.signal = signal;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder outcome(SignalOutcome outcome) {
            this.outcome = outcome;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder workflowState(String workflowState) {
            this.workflowState = workflowState;
            return this;
        }

        public Builder taskStatus(String taskStatus) {
            this.taskStatus = taskStatus;
            return this;
        }

        public Builder nextStep(String nextStep) {
            this.nextStep = nextStep;
            return this;
        }

        public Builder processedAt(Instant processedAt) {
            this.processedAt = processedAt;
            return this;
        }

        public Builder resultData(Map<String, Object> resultData) {
            this.resultData = resultData;
            return this;
        }

        public LyshraOpenAppSignalResult build() {
            return new LyshraOpenAppSignalResult(
                    signal,
                    success,
                    outcome,
                    errorMessage,
                    errorCode,
                    processedAt
            );
        }
    }
}
