package com.lyshra.open.app.integration.contract.humantask;

import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskStatus;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Represents an audit entry for tracking changes to a human task.
 * Provides a complete history of state transitions and actions.
 */
public interface ILyshraOpenAppHumanTaskAuditEntry {

    /**
     * Unique identifier for this audit entry.
     */
    String getEntryId();

    /**
     * When this event occurred.
     */
    Instant getTimestamp();

    /**
     * The action that was performed.
     */
    AuditAction getAction();

    /**
     * Previous status (if status changed).
     */
    Optional<LyshraOpenAppHumanTaskStatus> getPreviousStatus();

    /**
     * New status (if status changed).
     */
    Optional<LyshraOpenAppHumanTaskStatus> getNewStatus();

    /**
     * User who performed the action.
     */
    String getActorId();

    /**
     * Actor type (user, system, timer, etc.).
     */
    ActorType getActorType();

    /**
     * Human-readable description of the action.
     */
    String getDescription();

    /**
     * Additional data associated with this action.
     */
    Map<String, Object> getData();

    /**
     * IP address or client identifier (for security audit).
     */
    Optional<String> getClientInfo();

    /**
     * Correlation ID for tracing across systems.
     */
    Optional<String> getCorrelationId();

    /**
     * Defines the types of audit actions.
     */
    enum AuditAction {
        CREATED,
        ASSIGNED,
        CLAIMED,
        UNCLAIMED,
        DELEGATED,
        APPROVED,
        REJECTED,
        COMPLETED,
        COMMENTED,
        ATTACHMENT_ADDED,
        ATTACHMENT_REMOVED,
        ESCALATED,
        TIMEOUT,
        CANCELLED,
        EXPIRED,
        REASSIGNED,
        PRIORITY_CHANGED,
        DUE_DATE_CHANGED,
        DATA_UPDATED
    }

    /**
     * Defines the types of actors.
     */
    enum ActorType {
        USER,
        SYSTEM,
        TIMER,
        EXTERNAL_SIGNAL,
        ESCALATION_HANDLER,
        WORKFLOW_ENGINE
    }
}
