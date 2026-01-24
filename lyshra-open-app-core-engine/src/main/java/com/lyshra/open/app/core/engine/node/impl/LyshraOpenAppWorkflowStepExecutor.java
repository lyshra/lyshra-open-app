package com.lyshra.open.app.core.engine.node.impl;

import com.lyshra.open.app.core.engine.ILyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.LyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.error.LyshraOpenAppErrorHandler;
import com.lyshra.open.app.core.engine.node.ILyshraOpenAppWorkflowStepExecutor;
import com.lyshra.open.app.core.exception.node.LyshraOpenAppProcessorExecutionException;
import com.lyshra.open.app.core.exception.node.LyshraOpenAppWorkflowStepExecutionException;
import com.lyshra.open.app.integration.constant.LyshraOpenAppConstants;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskProcessor;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessor;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStep;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepIdentifier;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepNext;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppWorkflowStepType;
import com.lyshra.open.app.integration.models.humantask.LyshraOpenAppHumanTaskProcessorOutput;
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

        // Check if this is a human task processor
        ILyshraOpenAppProcessor processor = facade.getPluginFactory().getProcessor(workflowStep.getProcessor());
        if (processor instanceof ILyshraOpenAppHumanTaskProcessor humanTaskProcessor) {
            return doExecuteHumanTaskProcessor(identifier, context, workflowStep, humanTaskProcessor);
        }

        // Regular processor execution
        return facade.getProcessorExecutor()
                .execute(workflowStep.getProcessor(), workflowStep.getInputConfig(), context)

                // update the context data with processor output
                .doOnNext(result -> Optional.ofNullable(result.getData())
                        .ifPresent(data -> context.setData(result.getData())))

                .map(res -> getNextProcessor(workflowStep, res.getBranch()))
                .onErrorMap(
                        LyshraOpenAppProcessorExecutionException.class,
                        ex -> new LyshraOpenAppWorkflowStepExecutionException(identifier, ex))
                .doOnError(
                        LyshraOpenAppWorkflowStepExecutionException.class,
                        throwable -> log.error("Error executing workflow step, Error: [{}]", throwable.getMessage()));
    }

    /**
     * Executes a human task processor, which suspends workflow execution
     * and creates a human task record.
     */
    private Mono<String> doExecuteHumanTaskProcessor(
            ILyshraOpenAppWorkflowStepIdentifier identifier,
            ILyshraOpenAppContext context,
            ILyshraOpenAppWorkflowStep workflowStep,
            ILyshraOpenAppHumanTaskProcessor humanTaskProcessor) {

        log.info("Executing human task processor: step={}, type={}",
                identifier.getWorkflowStepName(), humanTaskProcessor.getHumanTaskType());

        // Extract workflow instance ID from context or generate if not present
        String workflowInstanceId = extractWorkflowInstanceId(context, identifier);

        return facade.getHumanTaskProcessorExecutor()
                .execute(
                        humanTaskProcessor,
                        facade.getObjectMapper().convertValue(
                                workflowStep.getInputConfig(),
                                humanTaskProcessor.getInputConfigType()),
                        workflowInstanceId,
                        identifier.getWorkflowStepName(),
                        context
                )
                .doOnNext(output -> {
                    // Store task information in context for potential use by resumption
                    if (output.isSuspended()) {
                        context.addVariable("$humanTaskId", output.getTaskId());
                        context.addVariable("$humanTaskWorkflowInstanceId", output.getWorkflowInstanceId());
                        context.addVariable("$humanTaskStepId", output.getWorkflowStepId());
                    }
                    // Update context data if output contains data
                    Optional.ofNullable(output.getData())
                            .ifPresent(context::setData);
                })
                .map(output -> {
                    // If workflow is suspended, return the WAITING branch
                    // The workflow executor should handle this specially
                    if (output.isSuspended()) {
                        log.info("Workflow suspended for human task: taskId={}, workflowId={}, stepId={}",
                                output.getTaskId(), workflowInstanceId, identifier.getWorkflowStepName());
                        return LyshraOpenAppConstants.WAITING_BRANCH;
                    }
                    // Otherwise, return the outcome branch for routing
                    return getNextProcessor(workflowStep, output.getBranch());
                })
                .onErrorMap(
                        ex -> !(ex instanceof LyshraOpenAppWorkflowStepExecutionException),
                        ex -> new LyshraOpenAppWorkflowStepExecutionException(identifier, ex))
                .doOnError(throwable -> log.error(
                        "Error executing human task processor: step={}, error={}",
                        identifier.getWorkflowStepName(), throwable.getMessage()));
    }

    /**
     * Extracts or generates a workflow instance ID from the context.
     */
    private String extractWorkflowInstanceId(ILyshraOpenAppContext context, ILyshraOpenAppWorkflowStepIdentifier identifier) {
        // Check if workflow instance ID is already in context variables
        Object instanceId = context.getVariables().get("$workflowInstanceId");
        if (instanceId != null) {
            return instanceId.toString();
        }

        // Generate a new instance ID based on workflow identifier and timestamp
        String generatedId = String.format("%s-%s-%d",
                identifier.getWorkflowName(),
                identifier.getWorkflowStepName(),
                System.currentTimeMillis());

        // Store in context for future reference
        context.addVariable("$workflowInstanceId", generatedId);
        return generatedId;
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
