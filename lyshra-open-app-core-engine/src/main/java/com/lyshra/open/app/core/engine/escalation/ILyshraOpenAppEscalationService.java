package com.lyshra.open.app.core.engine.escalation;

import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTask;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskEscalation;
import reactor.core.publisher.Mono;

/**
 * Service interface for handling human task escalation.
 * Responsible for executing escalation actions when tasks exceed their timeout.
 *
 * <p>Design Pattern: Strategy Pattern
 * - Different escalation actions are strategies
 * - EscalationAction enum determines which strategy to use
 *
 * <p>Escalation flow:
 * 1. TimeoutScheduler triggers escalation timeout
 * 2. EscalationService evaluates escalation config
 * 3. Executes appropriate escalation action
 * 4. Optionally schedules next escalation level
 */
public interface ILyshraOpenAppEscalationService {

    /**
     * Escalates a human task according to its configuration.
     *
     * @param task the task to escalate
     * @param currentLevel the current escalation level (0 = first escalation)
     * @return the escalation result
     */
    Mono<EscalationResult> escalate(ILyshraOpenAppHumanTask task, int currentLevel);

    /**
     * Checks if a task can be escalated further.
     *
     * @param task the task to check
     * @return true if escalation is possible
     */
    Mono<Boolean> canEscalate(ILyshraOpenAppHumanTask task);

    /**
     * Gets the escalation configuration for a task.
     * Returns the task's own config or applies defaults.
     *
     * @param task the task
     * @return the effective escalation configuration
     */
    Mono<ILyshraOpenAppHumanTaskEscalation> getEffectiveEscalationConfig(ILyshraOpenAppHumanTask task);

    /**
     * Result of an escalation action.
     */
    interface EscalationResult {

        /**
         * Whether the escalation was successful.
         */
        boolean isSuccess();

        /**
         * The action that was taken.
         */
        ILyshraOpenAppHumanTaskEscalation.EscalationAction getActionTaken();

        /**
         * The new escalation level.
         */
        int getNewEscalationLevel();

        /**
         * Whether there are more escalation levels available.
         */
        boolean hasMoreLevels();

        /**
         * Whether the workflow should be resumed.
         */
        boolean shouldResumeWorkflow();

        /**
         * The branch to use if resuming workflow.
         */
        String getResumeBranch();

        /**
         * Error message if escalation failed.
         */
        String getErrorMessage();
    }
}
