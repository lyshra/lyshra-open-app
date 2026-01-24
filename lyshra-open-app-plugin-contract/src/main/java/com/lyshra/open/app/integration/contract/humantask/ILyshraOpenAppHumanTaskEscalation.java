package com.lyshra.open.app.integration.contract.humantask;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Defines escalation configuration for human tasks.
 * Escalation rules determine what happens when a task times out
 * or specific conditions are met.
 */
public interface ILyshraOpenAppHumanTaskEscalation {

    /**
     * Duration after which escalation is triggered.
     */
    Duration getEscalationTimeout();

    /**
     * Action to take on escalation.
     */
    EscalationAction getAction();

    /**
     * Users to notify or reassign to on escalation.
     */
    List<String> getEscalationTargets();

    /**
     * Groups to notify or reassign to on escalation.
     */
    List<String> getEscalationGroups();

    /**
     * Custom escalation handler processor identifier.
     */
    Optional<String> getEscalationHandlerProcessor();

    /**
     * Additional data to pass to escalation handler.
     */
    Map<String, Object> getEscalationData();

    /**
     * Whether to send notifications on escalation.
     */
    boolean isSendNotification();

    /**
     * Notification template identifier.
     */
    Optional<String> getNotificationTemplateId();

    /**
     * Maximum number of escalation levels.
     */
    int getMaxEscalationLevels();

    /**
     * Current escalation level (0 = not escalated).
     */
    int getCurrentEscalationLevel();

    /**
     * Whether to auto-approve on final escalation timeout.
     */
    boolean isAutoApproveOnFinalTimeout();

    /**
     * Whether to auto-reject on final escalation timeout.
     */
    boolean isAutoRejectOnFinalTimeout();

    /**
     * Defines the action to take on escalation.
     */
    enum EscalationAction {
        /**
         * Reassign the task to escalation targets.
         */
        REASSIGN,

        /**
         * Add escalation targets as additional assignees.
         */
        ADD_ASSIGNEES,

        /**
         * Notify escalation targets without changing assignment.
         */
        NOTIFY,

        /**
         * Execute a custom escalation handler.
         */
        CUSTOM_HANDLER,

        /**
         * Automatically approve the task.
         */
        AUTO_APPROVE,

        /**
         * Automatically reject the task.
         */
        AUTO_REJECT,

        /**
         * Fail the workflow.
         */
        FAIL_WORKFLOW,

        /**
         * Skip this task and proceed to next step.
         */
        SKIP_TASK
    }
}
