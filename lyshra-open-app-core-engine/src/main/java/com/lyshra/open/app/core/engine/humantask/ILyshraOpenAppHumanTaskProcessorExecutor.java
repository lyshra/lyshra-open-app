package com.lyshra.open.app.core.engine.humantask;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskProcessor;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.models.humantask.LyshraOpenAppHumanTaskProcessorOutput;
import reactor.core.publisher.Mono;

/**
 * Executor for human task processors.
 * Handles the specialized execution flow for processors that require human interaction.
 *
 * <p>This executor differs from the regular processor executor in that it:
 * <ul>
 *   <li>Creates a human task record before processor execution</li>
 *   <li>Suspends workflow execution after task creation</li>
 *   <li>Returns a specialized output indicating workflow suspension</li>
 *   <li>Coordinates with timeout scheduling and escalation services</li>
 * </ul>
 *
 * <p>Design Pattern: Mediator Pattern
 * - Coordinates between human task service, workflow suspension, and processor execution
 * - Centralizes the complex interaction logic for human-in-the-loop workflows
 */
public interface ILyshraOpenAppHumanTaskProcessorExecutor {

    /**
     * Executes a human task processor, creating a task and suspending the workflow.
     *
     * <p>Execution flow:
     * <ol>
     *   <li>Extract task configuration from processor input</li>
     *   <li>Create human task record via HumanTaskService</li>
     *   <li>Suspend workflow execution via WorkflowResumptionService</li>
     *   <li>Schedule timeout if configured</li>
     *   <li>Return suspension output with task ID</li>
     * </ol>
     *
     * @param processor the human task processor definition
     * @param inputConfig the processor input configuration
     * @param workflowInstanceId the workflow instance to suspend
     * @param workflowStepId the current workflow step
     * @param context the execution context
     * @return output indicating suspension and task details
     */
    Mono<LyshraOpenAppHumanTaskProcessorOutput> execute(
            ILyshraOpenAppHumanTaskProcessor processor,
            ILyshraOpenAppProcessorInputConfig inputConfig,
            String workflowInstanceId,
            String workflowStepId,
            ILyshraOpenAppContext context);

    /**
     * Executes a human task processor with additional task data.
     *
     * @param processor the human task processor definition
     * @param inputConfig the processor input configuration
     * @param workflowInstanceId the workflow instance to suspend
     * @param workflowStepId the current workflow step
     * @param taskData additional data to include in the task
     * @param context the execution context
     * @return output indicating suspension and task details
     */
    Mono<LyshraOpenAppHumanTaskProcessorOutput> executeWithData(
            ILyshraOpenAppHumanTaskProcessor processor,
            ILyshraOpenAppProcessorInputConfig inputConfig,
            String workflowInstanceId,
            String workflowStepId,
            java.util.Map<String, Object> taskData,
            ILyshraOpenAppContext context);

    /**
     * Checks if a processor is a human task processor that should use this executor.
     *
     * @param processor the processor to check
     * @return true if the processor is a human task processor
     */
    boolean isHumanTaskProcessor(Object processor);
}
