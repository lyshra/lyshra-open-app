package com.lyshra.open.app.integration.contract.humantask;

import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessor;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskType;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Extended processor interface for human-in-the-loop workflow steps.
 * Human task processors create tasks that pause workflow execution
 * until human interaction is received.
 *
 * <p>Design Pattern: Strategy Pattern combined with Template Method
 * - Strategy: Different human task types (approval, input, review) are strategies
 * - Template Method: Common task lifecycle with customizable behavior
 *
 * <p>Human task processors differ from regular processors in that:
 * <ul>
 *   <li>They suspend workflow execution instead of returning immediately</li>
 *   <li>They create persistent human task records</li>
 *   <li>They define timeout and escalation behavior</li>
 *   <li>They specify how external signals are processed</li>
 * </ul>
 */
public interface ILyshraOpenAppHumanTaskProcessor extends ILyshraOpenAppProcessor {

    /**
     * Returns the type of human task this processor creates.
     */
    LyshraOpenAppHumanTaskType getHumanTaskType();

    /**
     * Returns whether this processor suspends workflow execution.
     * Human task processors typically return true.
     */
    default boolean isSuspending() {
        return true;
    }

    /**
     * Returns the default timeout for tasks created by this processor.
     * Empty means no timeout (wait indefinitely).
     */
    Optional<Duration> getDefaultTimeout();

    /**
     * Returns the default escalation configuration.
     */
    Optional<ILyshraOpenAppHumanTaskEscalation> getDefaultEscalationConfig();

    /**
     * Returns the list of valid outcome branches this processor can return.
     * For approval tasks, this might be ["APPROVED", "REJECTED"].
     * For decision tasks, this might be custom options.
     */
    List<String> getValidOutcomeBranches();

    /**
     * Returns the human task configuration builder for this processor.
     * Used to construct task instances with proper defaults.
     */
    ILyshraOpenAppHumanTaskConfigBuilder getTaskConfigBuilder();

    /**
     * Returns whether this task supports claiming (single assignee at a time).
     */
    default boolean supportsClaming() {
        return true;
    }

    /**
     * Returns whether this task supports delegation.
     */
    default boolean supportsDelegation() {
        return true;
    }

    /**
     * Returns whether this task requires form submission.
     */
    default boolean requiresFormSubmission() {
        return false;
    }

    /**
     * Returns the default form schema for this processor.
     */
    Optional<ILyshraOpenAppHumanTaskFormSchema> getDefaultFormSchema();

    /**
     * Builder interface for constructing human task configuration.
     */
    interface ILyshraOpenAppHumanTaskConfigBuilder {

        ILyshraOpenAppHumanTaskConfigBuilder title(String title);

        ILyshraOpenAppHumanTaskConfigBuilder description(String description);

        ILyshraOpenAppHumanTaskConfigBuilder priority(int priority);

        ILyshraOpenAppHumanTaskConfigBuilder assignees(List<String> assignees);

        ILyshraOpenAppHumanTaskConfigBuilder candidateGroups(List<String> groups);

        ILyshraOpenAppHumanTaskConfigBuilder timeout(Duration timeout);

        ILyshraOpenAppHumanTaskConfigBuilder escalation(ILyshraOpenAppHumanTaskEscalation escalation);

        ILyshraOpenAppHumanTaskConfigBuilder formSchema(ILyshraOpenAppHumanTaskFormSchema formSchema);

        ILyshraOpenAppHumanTaskConfig build();
    }

    /**
     * Immutable human task configuration.
     */
    interface ILyshraOpenAppHumanTaskConfig {

        String getTitle();

        String getDescription();

        int getPriority();

        List<String> getAssignees();

        List<String> getCandidateGroups();

        Optional<Duration> getTimeout();

        Optional<ILyshraOpenAppHumanTaskEscalation> getEscalation();

        Optional<ILyshraOpenAppHumanTaskFormSchema> getFormSchema();
    }
}
