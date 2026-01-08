package com.lyshra.open.app.core.engine.node.impl;

import com.lyshra.open.app.core.engine.ILyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.LyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.node.ILyshraOpenAppWorkflowExecutor;
import com.lyshra.open.app.integration.constant.LyshraOpenAppConstants;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflow;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier;
import com.lyshra.open.app.integration.models.workflows.step.LyshraOpenAppWorkflowStepIdentifier;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class LyshraOpenAppWorkflowExecutor implements ILyshraOpenAppWorkflowExecutor {

    private final ILyshraOpenAppFacade facade;

    private LyshraOpenAppWorkflowExecutor() {
        this.facade = LyshraOpenAppFacade.getInstance();
    }

    private static final class SingletonHelper {
        private static final LyshraOpenAppWorkflowExecutor INSTANCE = new LyshraOpenAppWorkflowExecutor();
    }

    public static ILyshraOpenAppWorkflowExecutor getInstance() {
        return SingletonHelper.INSTANCE;
    }

    @Override
    public Mono<ILyshraOpenAppContext> execute(
            ILyshraOpenAppWorkflowIdentifier identifier,
            ILyshraOpenAppContext context) {

        return Mono.just(identifier)
                .doOnNext(i -> doPreStartActions(identifier, context))
                .flatMap(ignored -> doExecuteWorkflow(identifier, context))
                .doOnNext(ignored -> doPostEndActions(identifier, context))
                .doOnError(throwable -> doPostEndActions(identifier, throwable, context));
    }

    private void doPreStartActions(
            ILyshraOpenAppWorkflowIdentifier identifier,
            ILyshraOpenAppContext context) {

        context.captureWorkflowStart(identifier);
    }

    private Mono<ILyshraOpenAppContext> doExecuteWorkflow(
            ILyshraOpenAppWorkflowIdentifier identifier,
            ILyshraOpenAppContext context) {

        ILyshraOpenAppWorkflow workflow = facade.getPluginFactory().getWorkflow(identifier);
        return executeStep(workflow.getStartStep(), identifier, context);
    }

    // recursive method
    private Mono<ILyshraOpenAppContext> executeStep(
            String currentStep,
            ILyshraOpenAppWorkflowIdentifier workflowIdentifier,
            ILyshraOpenAppContext context) {

        if (currentStep == null || currentStep.isBlank() || LyshraOpenAppConstants.NOOP_PROCESSOR.equalsIgnoreCase(currentStep.trim())) {
            log.info("Terminating workflow, as no more steps to execute. currentStep: [{}]", currentStep);
            return Mono.just(context);
        }

        LyshraOpenAppWorkflowStepIdentifier workflowStepIdentifier = new LyshraOpenAppWorkflowStepIdentifier(workflowIdentifier, currentStep);
        return facade.getWorkflowStepExecutor().execute(workflowStepIdentifier, context)
                .flatMap(nextStep -> executeStep(nextStep, workflowStepIdentifier, context));
    }

    private void doPostEndActions(
            ILyshraOpenAppWorkflowIdentifier identifier,
            ILyshraOpenAppContext context) {

        context.captureWorkflowEnd(identifier);
    }

    private void doPostEndActions(
            ILyshraOpenAppWorkflowIdentifier identifier,
            Throwable throwable,
            ILyshraOpenAppContext context) {

        context.captureWorkflowEnd(identifier, throwable);
    }
}
