package com.lyshra.open.app.core.engine.node.impl;

import com.lyshra.open.app.core.engine.ILyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.LyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.error.LyshraOpenAppErrorHandler;
import com.lyshra.open.app.core.engine.node.ILyshraOpenAppWorkflowStepExecutor;
import com.lyshra.open.app.core.exception.node.LyshraOpenAppProcessorExecutionException;
import com.lyshra.open.app.core.exception.node.LyshraOpenAppWorkflowStepExecutionException;
import com.lyshra.open.app.integration.constant.LyshraOpenAppConstants;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStep;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepIdentifier;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepNext;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppWorkflowStepType;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

@Slf4j
public class LyshraOpenAppWorkflowStepExecutor implements ILyshraOpenAppWorkflowStepExecutor {

    private final ILyshraOpenAppFacade facade;

    private LyshraOpenAppWorkflowStepExecutor() {
        this.facade = LyshraOpenAppFacade.getInstance();
    }

    private static final class SingletonHelper {
        private static final ILyshraOpenAppWorkflowStepExecutor INSTANCE = new LyshraOpenAppWorkflowStepExecutor();
    }

    public static ILyshraOpenAppWorkflowStepExecutor getInstance() {
        return SingletonHelper.INSTANCE;
    }

    @Override
    public Mono<String> execute(
            ILyshraOpenAppWorkflowStepIdentifier identifier,
            ILyshraOpenAppContext context) {

        return Mono.just(identifier)
                .doOnNext(i -> doPreStartActions(identifier, context))

                .flatMap(ignored -> doExecuteWorkflowStep(identifier, context))

                // scenario can occur when the error handling strategy is to end workflow,
                // so to ensure the later step execution flow is invoked
                .switchIfEmpty(Mono.just(LyshraOpenAppConstants.NOOP_PROCESSOR))

                .doOnNext(nextBranch -> doPostEndActions(identifier, nextBranch, context))
                .doOnError(throwable -> doPostEndActions(identifier, throwable, context));
    }

    private Mono<String> doExecuteWorkflowStep(ILyshraOpenAppWorkflowStepIdentifier identifier, ILyshraOpenAppContext context) {
        ILyshraOpenAppWorkflowStep workflowStep = facade.getPluginFactory().getWorkflowStep(identifier);
        LyshraOpenAppWorkflowStepType type = workflowStep.getType();

        Mono<String> result;
        if (type == LyshraOpenAppWorkflowStepType.PROCESSOR) {
            result = doExecuteWorkflowStep_Processor(identifier, context, workflowStep);
        } else if (type == LyshraOpenAppWorkflowStepType.WORKFLOW) {
            result = doExecuteWorkflowStep_Workflow(identifier, context, workflowStep);
        } else {
            result = Mono.just(LyshraOpenAppConstants.NOOP_PROCESSOR);
        }

        Duration timeout = workflowStep.getTimeout();
        if (!timeout.isZero()) {
            result = result
                    .timeout(timeout)
                    .doOnError(
                            TimeoutException.class,
                            throwable -> log.error("Timeout occurred while executing workflow step: [{}], e: [{}]", identifier, throwable.getMessage())
                    );
        }

        return LyshraOpenAppErrorHandler.applyErrorHandling(result, workflowStep.getOnError());
    }

    private Mono<String> doExecuteWorkflowStep_Processor(
            ILyshraOpenAppWorkflowStepIdentifier identifier,
            ILyshraOpenAppContext context,
            ILyshraOpenAppWorkflowStep workflowStep) {

        return facade.getProcessorExecutor()
                .execute(workflowStep.getProcessor(), workflowStep.getInputConfig(), context)
                .map(res -> getNextProcessor(workflowStep, res.getBranch()))
                .onErrorMap(
                        LyshraOpenAppProcessorExecutionException.class,
                        ex -> new LyshraOpenAppWorkflowStepExecutionException(identifier, ex))
                .doOnError(
                        LyshraOpenAppWorkflowStepExecutionException.class,
                        throwable -> log.error("Error executing workflow step, Error: [{}]", throwable.getMessage()));
    }

    private Mono<String> doExecuteWorkflowStep_Workflow(
            ILyshraOpenAppWorkflowStepIdentifier identifier,
            ILyshraOpenAppContext context,
            ILyshraOpenAppWorkflowStep workflowStep) {

        return facade.getWorkflowExecutor()
                .execute(workflowStep.getWorkflowCall(), context)
                .map(res -> getNextProcessor(workflowStep, LyshraOpenAppConstants.DEFAULT_BRANCH))

                ;
    }

    private String getNextProcessor(ILyshraOpenAppWorkflowStep workflowStep, String resultBranch) {
        return Optional
                .ofNullable(workflowStep.getNext())
                .map(ILyshraOpenAppWorkflowStepNext::getBranches)
                .map(b -> b.get(resultBranch))
                .orElse(LyshraOpenAppConstants.NOOP_PROCESSOR);
    }

    private void doPreStartActions(
            ILyshraOpenAppWorkflowStepIdentifier identifier,
            ILyshraOpenAppContext context) {

        context.captureWorkflowStepStart(identifier);
    }

    private void doPostEndActions(
            ILyshraOpenAppWorkflowStepIdentifier identifier,
            String next,
            ILyshraOpenAppContext context) {

        context.captureWorkflowStepEnd(identifier, next);
    }

    private void doPostEndActions(
            ILyshraOpenAppWorkflowStepIdentifier identifier,
            Throwable throwable,
            ILyshraOpenAppContext context) {

        context.captureWorkflowStepEnd(identifier, throwable);
    }

}
