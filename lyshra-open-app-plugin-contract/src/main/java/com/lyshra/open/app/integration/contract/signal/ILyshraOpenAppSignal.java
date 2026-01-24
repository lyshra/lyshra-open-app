package com.lyshra.open.app.integration.contract.signal;

import com.lyshra.open.app.integration.enumerations.LyshraOpenAppSignalType;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Represents an external signal sent to a workflow or human task.
 * Signals are the mechanism for external systems or users to interact
 * with paused workflows and human tasks.
 *
 * <p>Design Pattern: Command Pattern
 * - Signals encapsulate a request (command) to the workflow engine
 * - They carry all information needed to process the request
 * - They can be queued, logged, and replayed
 */
public interface ILyshraOpenAppSignal {

    /**
     * Unique identifier for this signal.
     */
    String getSignalId();

    /**
     * The type of signal being sent.
     */
    LyshraOpenAppSignalType getSignalType();

    /**
     * Target workflow instance ID.
     */
    String getWorkflowInstanceId();

    /**
     * Target human task ID (if signal is for a specific task).
     */
    Optional<String> getTaskId();

    /**
     * Payload data associated with the signal.
     * For approval: might contain approval comments
     * For complete: might contain form submission data
     */
    Map<String, Object> getPayload();

    /**
     * User or system that sent the signal.
     */
    String getSenderId();

    /**
     * Type of sender (user, system, external, timer).
     */
    SenderType getSenderType();

    /**
     * When the signal was created.
     */
    Instant getCreatedAt();

    /**
     * Correlation ID for tracing across systems.
     */
    Optional<String> getCorrelationId();

    /**
     * Source system identifier (for external signals).
     */
    Optional<String> getSourceSystem();

    /**
     * Whether this signal should be processed asynchronously.
     */
    boolean isAsync();

    /**
     * Priority of the signal (for queue ordering).
     */
    int getPriority();

    /**
     * Optional expiration time for the signal.
     */
    Optional<Instant> getExpiresAt();

    /**
     * Custom headers/metadata for the signal.
     */
    Map<String, String> getHeaders();

    /**
     * Types of signal senders.
     */
    enum SenderType {
        USER,
        SYSTEM,
        EXTERNAL_SYSTEM,
        TIMER,
        ESCALATION_HANDLER,
        WORKFLOW_ENGINE
    }
}
