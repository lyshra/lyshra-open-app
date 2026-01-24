package com.lyshra.open.app.integration.contract.humantask;

import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorResult;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskStatus;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Extended processor result for human task processors.
 * Contains additional information about the human task outcome.
 *
 * <p>This result is returned when a human task is completed,
 * carrying the decision, any form data, and metadata about the resolution.
 */
public interface ILyshraOpenAppHumanTaskResult extends ILyshraOpenAppProcessorResult {

    /**
     * The final status of the human task.
     */
    LyshraOpenAppHumanTaskStatus getTaskStatus();

    /**
     * The task identifier that was resolved.
     */
    String getTaskId();

    /**
     * User who resolved the task (if not system/timer).
     */
    Optional<String> getResolvedBy();

    /**
     * When the task was resolved.
     */
    Instant getResolvedAt();

    /**
     * Form data submitted with the resolution (if any).
     */
    Optional<Map<String, Object>> getFormData();

    /**
     * Comments added during resolution.
     */
    Optional<String> getResolutionComment();

    /**
     * Whether the task was auto-resolved (timeout, escalation).
     */
    boolean isAutoResolved();

    /**
     * Reason for auto-resolution (if applicable).
     */
    Optional<String> getAutoResolutionReason();

    /**
     * Time spent on the task (from creation to resolution).
     */
    long getDurationMillis();

    /**
     * Number of escalations that occurred.
     */
    int getEscalationCount();
}
