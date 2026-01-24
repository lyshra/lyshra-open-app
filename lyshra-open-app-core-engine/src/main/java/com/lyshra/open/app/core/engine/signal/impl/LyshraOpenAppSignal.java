package com.lyshra.open.app.core.engine.signal.impl;

import com.lyshra.open.app.integration.contract.signal.ILyshraOpenAppSignal;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppSignalType;
import lombok.Data;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of ILyshraOpenAppSignal.
 * Immutable signal object that carries all information needed to process a signal.
 */
@Data
public class LyshraOpenAppSignal implements ILyshraOpenAppSignal {

    private final String signalId;
    private final LyshraOpenAppSignalType signalType;
    private final String workflowInstanceId;
    private final String taskId;
    private final Map<String, Object> payload;
    private final String senderId;
    private final SenderType senderType;
    private final Instant createdAt;
    private final String correlationId;
    private final String sourceSystem;
    private final boolean async;
    private final int priority;
    private final Instant expiresAt;
    private final Map<String, String> headers;

    public LyshraOpenAppSignal(
            String signalId,
            LyshraOpenAppSignalType signalType,
            String workflowInstanceId,
            String taskId,
            Map<String, Object> payload,
            String senderId,
            SenderType senderType,
            Instant createdAt) {

        this.signalId = signalId;
        this.signalType = signalType;
        this.workflowInstanceId = workflowInstanceId;
        this.taskId = taskId;
        this.payload = payload != null ? Collections.unmodifiableMap(payload) : Collections.emptyMap();
        this.senderId = senderId;
        this.senderType = senderType;
        this.createdAt = createdAt;
        this.correlationId = null;
        this.sourceSystem = null;
        this.async = false;
        this.priority = 0;
        this.expiresAt = null;
        this.headers = Collections.emptyMap();
    }

    @Override
    public Optional<String> getTaskId() {
        return Optional.ofNullable(taskId);
    }

    @Override
    public Optional<String> getCorrelationId() {
        return Optional.ofNullable(correlationId);
    }

    @Override
    public Optional<String> getSourceSystem() {
        return Optional.ofNullable(sourceSystem);
    }

    @Override
    public Optional<Instant> getExpiresAt() {
        return Optional.ofNullable(expiresAt);
    }

    /**
     * Builder for creating signals with all options.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String signalId;
        private LyshraOpenAppSignalType signalType;
        private String workflowInstanceId;
        private String taskId;
        private Map<String, Object> payload = Collections.emptyMap();
        private String senderId;
        private SenderType senderType = SenderType.USER;
        private Instant createdAt = Instant.now();
        private String correlationId;
        private String sourceSystem;
        private boolean async = false;
        private int priority = 0;
        private Instant expiresAt;
        private Map<String, String> headers = Collections.emptyMap();

        public Builder signalId(String signalId) {
            this.signalId = signalId;
            return this;
        }

        public Builder signalType(LyshraOpenAppSignalType signalType) {
            this.signalType = signalType;
            return this;
        }

        public Builder workflowInstanceId(String workflowInstanceId) {
            this.workflowInstanceId = workflowInstanceId;
            return this;
        }

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder payload(Map<String, Object> payload) {
            this.payload = payload;
            return this;
        }

        public Builder senderId(String senderId) {
            this.senderId = senderId;
            return this;
        }

        public Builder senderType(SenderType senderType) {
            this.senderType = senderType;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder sourceSystem(String sourceSystem) {
            this.sourceSystem = sourceSystem;
            return this;
        }

        public Builder async(boolean async) {
            this.async = async;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public LyshraOpenAppSignal build() {
            LyshraOpenAppSignal signal = new LyshraOpenAppSignal(
                    signalId,
                    signalType,
                    workflowInstanceId,
                    taskId,
                    payload,
                    senderId,
                    senderType,
                    createdAt
            );
            return signal;
        }
    }
}
