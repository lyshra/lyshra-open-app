package com.lyshra.open.app.core.engine.escalation.impl;

import com.lyshra.open.app.core.engine.escalation.ILyshraOpenAppEscalationService;
import com.lyshra.open.app.core.engine.timeout.ILyshraOpenAppTimeoutScheduler;
import com.lyshra.open.app.core.engine.timeout.impl.LyshraOpenAppTimeoutSchedulerImpl;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTask;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskEscalation;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskEscalation.EscalationAction;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

/**
 * Default implementation of the escalation service.
 * Handles escalation logic for human tasks.
 *
 * <p>Design Pattern: Strategy Pattern with Chain
 * - Evaluates escalation configuration
 * - Executes appropriate action based on config
 * - May chain to next escalation level
 */
@Slf4j
public class LyshraOpenAppEscalationServiceImpl implements ILyshraOpenAppEscalationService {

    private final ILyshraOpenAppTimeoutScheduler timeoutScheduler;

    private LyshraOpenAppEscalationServiceImpl() {
        this.timeoutScheduler = LyshraOpenAppTimeoutSchedulerImpl.getInstance();
    }

    private static final class SingletonHelper {
        private static final ILyshraOpenAppEscalationService INSTANCE = new LyshraOpenAppEscalationServiceImpl();
    }

    public static ILyshraOpenAppEscalationService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    @Override
    public Mono<EscalationResult> escalate(ILyshraOpenAppHumanTask task, int currentLevel) {
        return getEffectiveEscalationConfig(task)
                .flatMap(config -> {
                    if (config == null) {
                        log.warn("No escalation config for task: {}", task.getTaskId());
                        return Mono.just(createFailedResult("No escalation configuration"));
                    }

                    int maxLevels = config.getMaxEscalationLevels();
                    boolean hasMoreLevels = currentLevel < maxLevels;

                    log.info("Escalating task: id={}, currentLevel={}, maxLevels={}",
                            task.getTaskId(), currentLevel, maxLevels);

                    EscalationAction action = config.getAction();

                    // If at max level, check for auto-approve/reject
                    if (!hasMoreLevels) {
                        if (config.isAutoApproveOnFinalTimeout()) {
                            action = EscalationAction.AUTO_APPROVE;
                        } else if (config.isAutoRejectOnFinalTimeout()) {
                            action = EscalationAction.AUTO_REJECT;
                        } else {
                            action = EscalationAction.FAIL_WORKFLOW;
                        }
                    }

                    return executeEscalationAction(task, config, action, currentLevel + 1, hasMoreLevels);
                });
    }

    private Mono<EscalationResult> executeEscalationAction(
            ILyshraOpenAppHumanTask task,
            ILyshraOpenAppHumanTaskEscalation config,
            EscalationAction action,
            int newLevel,
            boolean hasMoreLevels) {

        log.info("Executing escalation action: {} for task: {}", action, task.getTaskId());

        switch (action) {
            case REASSIGN:
                return handleReassign(task, config, newLevel, hasMoreLevels);

            case ADD_ASSIGNEES:
                return handleAddAssignees(task, config, newLevel, hasMoreLevels);

            case NOTIFY:
                return handleNotify(task, config, newLevel, hasMoreLevels);

            case CUSTOM_HANDLER:
                return handleCustomHandler(task, config, newLevel, hasMoreLevels);

            case AUTO_APPROVE:
                return handleAutoApprove(task, newLevel);

            case AUTO_REJECT:
                return handleAutoReject(task, newLevel);

            case FAIL_WORKFLOW:
                return handleFailWorkflow(task, newLevel);

            case SKIP_TASK:
                return handleSkipTask(task, newLevel);

            default:
                return Mono.just(createFailedResult("Unknown escalation action: " + action));
        }
    }

    private Mono<EscalationResult> handleReassign(
            ILyshraOpenAppHumanTask task,
            ILyshraOpenAppHumanTaskEscalation config,
            int newLevel,
            boolean hasMoreLevels) {

        // Implementation would:
        // 1. Update task assignees to escalation targets
        // 2. Clear any existing claims
        // 3. Send notifications
        // 4. Schedule next escalation if more levels
        // 5. Add audit entry

        return scheduleNextEscalation(task, config, newLevel)
                .then(Mono.just(createSuccessResult(EscalationAction.REASSIGN, newLevel, hasMoreLevels)));
    }

    private Mono<EscalationResult> handleAddAssignees(
            ILyshraOpenAppHumanTask task,
            ILyshraOpenAppHumanTaskEscalation config,
            int newLevel,
            boolean hasMoreLevels) {

        // Implementation would:
        // 1. Add escalation targets to existing assignees
        // 2. Send notifications
        // 3. Schedule next escalation if more levels
        // 4. Add audit entry

        return scheduleNextEscalation(task, config, newLevel)
                .then(Mono.just(createSuccessResult(EscalationAction.ADD_ASSIGNEES, newLevel, hasMoreLevels)));
    }

    private Mono<EscalationResult> handleNotify(
            ILyshraOpenAppHumanTask task,
            ILyshraOpenAppHumanTaskEscalation config,
            int newLevel,
            boolean hasMoreLevels) {

        // Implementation would:
        // 1. Send notifications to escalation targets
        // 2. Schedule next escalation if more levels
        // 3. Add audit entry

        return scheduleNextEscalation(task, config, newLevel)
                .then(Mono.just(createSuccessResult(EscalationAction.NOTIFY, newLevel, hasMoreLevels)));
    }

    private Mono<EscalationResult> handleCustomHandler(
            ILyshraOpenAppHumanTask task,
            ILyshraOpenAppHumanTaskEscalation config,
            int newLevel,
            boolean hasMoreLevels) {

        // Implementation would:
        // 1. Invoke custom escalation handler processor
        // 2. Process handler result
        // 3. Schedule next escalation if needed
        // 4. Add audit entry

        return scheduleNextEscalation(task, config, newLevel)
                .then(Mono.just(createSuccessResult(EscalationAction.CUSTOM_HANDLER, newLevel, hasMoreLevels)));
    }

    private Mono<EscalationResult> handleAutoApprove(ILyshraOpenAppHumanTask task, int newLevel) {
        log.info("Auto-approving task on final timeout: {}", task.getTaskId());

        // This result will trigger workflow resumption with APPROVED branch
        return Mono.just(new EscalationResultImpl(
                true,
                EscalationAction.AUTO_APPROVE,
                newLevel,
                false,
                true,
                "APPROVED",
                null
        ));
    }

    private Mono<EscalationResult> handleAutoReject(ILyshraOpenAppHumanTask task, int newLevel) {
        log.info("Auto-rejecting task on final timeout: {}", task.getTaskId());

        // This result will trigger workflow resumption with REJECTED branch
        return Mono.just(new EscalationResultImpl(
                true,
                EscalationAction.AUTO_REJECT,
                newLevel,
                false,
                true,
                "REJECTED",
                null
        ));
    }

    private Mono<EscalationResult> handleFailWorkflow(ILyshraOpenAppHumanTask task, int newLevel) {
        log.info("Failing workflow due to final escalation timeout: {}", task.getTaskId());

        // This result will trigger workflow resumption with TIMED_OUT branch
        return Mono.just(new EscalationResultImpl(
                true,
                EscalationAction.FAIL_WORKFLOW,
                newLevel,
                false,
                true,
                "TIMED_OUT",
                null
        ));
    }

    private Mono<EscalationResult> handleSkipTask(ILyshraOpenAppHumanTask task, int newLevel) {
        log.info("Skipping task on escalation: {}", task.getTaskId());

        // This result will trigger workflow resumption with DEFAULT branch
        return Mono.just(new EscalationResultImpl(
                true,
                EscalationAction.SKIP_TASK,
                newLevel,
                false,
                true,
                "DEFAULT",
                null
        ));
    }

    private Mono<Void> scheduleNextEscalation(
            ILyshraOpenAppHumanTask task,
            ILyshraOpenAppHumanTaskEscalation config,
            int newLevel) {

        if (newLevel < config.getMaxEscalationLevels()) {
            Duration nextTimeout = config.getEscalationTimeout();
            return timeoutScheduler.scheduleEscalationTimeout(task, nextTimeout, newLevel)
                    .then();
        }
        return Mono.empty();
    }

    @Override
    public Mono<Boolean> canEscalate(ILyshraOpenAppHumanTask task) {
        return Mono.fromCallable(() -> {
            Optional<ILyshraOpenAppHumanTaskEscalation> config = task.getEscalationConfig();
            if (config.isEmpty()) {
                return false;
            }
            int currentLevel = config.get().getCurrentEscalationLevel();
            int maxLevels = config.get().getMaxEscalationLevels();
            return currentLevel < maxLevels;
        });
    }

    @Override
    public Mono<ILyshraOpenAppHumanTaskEscalation> getEffectiveEscalationConfig(ILyshraOpenAppHumanTask task) {
        return Mono.fromCallable(() -> task.getEscalationConfig().orElse(null));
    }

    private EscalationResult createSuccessResult(EscalationAction action, int newLevel, boolean hasMoreLevels) {
        return new EscalationResultImpl(true, action, newLevel, hasMoreLevels, false, null, null);
    }

    private EscalationResult createFailedResult(String errorMessage) {
        return new EscalationResultImpl(false, null, 0, false, false, null, errorMessage);
    }

    private static class EscalationResultImpl implements EscalationResult {
        private final boolean success;
        private final EscalationAction actionTaken;
        private final int newEscalationLevel;
        private final boolean moreAvailableLevels;
        private final boolean resumeWorkflowFlag;
        private final String resumeBranch;
        private final String errorMessage;

        public EscalationResultImpl(boolean success, EscalationAction actionTaken, int newEscalationLevel,
                                    boolean moreAvailableLevels, boolean resumeWorkflowFlag,
                                    String resumeBranch, String errorMessage) {
            this.success = success;
            this.actionTaken = actionTaken;
            this.newEscalationLevel = newEscalationLevel;
            this.moreAvailableLevels = moreAvailableLevels;
            this.resumeWorkflowFlag = resumeWorkflowFlag;
            this.resumeBranch = resumeBranch;
            this.errorMessage = errorMessage;
        }

        @Override
        public boolean isSuccess() {
            return success;
        }

        @Override
        public EscalationAction getActionTaken() {
            return actionTaken;
        }

        @Override
        public int getNewEscalationLevel() {
            return newEscalationLevel;
        }

        @Override
        public boolean hasMoreLevels() {
            return moreAvailableLevels;
        }

        @Override
        public boolean shouldResumeWorkflow() {
            return resumeWorkflowFlag;
        }

        @Override
        public String getResumeBranch() {
            return resumeBranch;
        }

        @Override
        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
