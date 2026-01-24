package com.lyshra.open.app.core.engine.version.execution;

import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStep;

import java.util.Map;
import java.util.Optional;

/**
 * Resolves workflow steps and processor configurations based on workflow version.
 *
 * <p>This resolver ensures that running workflows execute using the version they
 * started with, preventing new changes from affecting running instances.</p>
 *
 * <p>Key responsibilities:</p>
 * <ul>
 *   <li>Resolve step definitions from a specific workflow version</li>
 *   <li>Load processor configurations from the correct version</li>
 *   <li>Validate step existence in the specified version</li>
 *   <li>Provide step metadata for execution decisions</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * // Resolve step from specific version
 * Optional<ILyshraOpenAppWorkflowStep> step = resolver.resolveStep(
 *     "order-processing",
 *     WorkflowVersion.of(1, 2, 0),
 *     "validate-order");
 *
 * // Get all steps for a version
 * Map<String, ILyshraOpenAppWorkflowStep> steps = resolver.resolveAllSteps(
 *     "order-processing",
 *     WorkflowVersion.of(1, 2, 0));
 * }</pre>
 */
public interface IVersionAwareStepResolver {

    /**
     * Resolves a specific step from a workflow version.
     *
     * @param workflowId workflow identifier
     * @param version workflow version
     * @param stepName name of the step to resolve
     * @return step definition if found
     */
    Optional<ILyshraOpenAppWorkflowStep> resolveStep(
            String workflowId,
            IWorkflowVersion version,
            String stepName);

    /**
     * Resolves a specific step from a versioned workflow.
     *
     * @param workflow versioned workflow
     * @param stepName name of the step to resolve
     * @return step definition if found
     */
    default Optional<ILyshraOpenAppWorkflowStep> resolveStep(
            IVersionedWorkflow workflow,
            String stepName) {
        return resolveStep(workflow.getWorkflowId(), workflow.getVersion(), stepName);
    }

    /**
     * Resolves all steps from a workflow version.
     *
     * @param workflowId workflow identifier
     * @param version workflow version
     * @return map of step name to step definition
     */
    Map<String, ILyshraOpenAppWorkflowStep> resolveAllSteps(
            String workflowId,
            IWorkflowVersion version);

    /**
     * Resolves all steps from a versioned workflow.
     *
     * @param workflow versioned workflow
     * @return map of step name to step definition
     */
    default Map<String, ILyshraOpenAppWorkflowStep> resolveAllSteps(IVersionedWorkflow workflow) {
        return resolveAllSteps(workflow.getWorkflowId(), workflow.getVersion());
    }

    /**
     * Checks if a step exists in a specific workflow version.
     *
     * @param workflowId workflow identifier
     * @param version workflow version
     * @param stepName name of the step
     * @return true if step exists
     */
    boolean stepExists(String workflowId, IWorkflowVersion version, String stepName);

    /**
     * Resolves the start step for a workflow version.
     *
     * @param workflowId workflow identifier
     * @param version workflow version
     * @return start step name
     */
    Optional<String> resolveStartStep(String workflowId, IWorkflowVersion version);

    /**
     * Resolves the next step name based on step configuration and branch condition.
     *
     * @param workflowId workflow identifier
     * @param version workflow version
     * @param currentStep current step name
     * @param branchCondition branch condition result (may be null for default)
     * @return next step name, empty for terminal
     */
    Optional<String> resolveNextStep(
            String workflowId,
            IWorkflowVersion version,
            String currentStep,
            String branchCondition);

    /**
     * Loads the versioned workflow definition.
     *
     * @param workflowId workflow identifier
     * @param version workflow version
     * @return versioned workflow if found
     */
    Optional<IVersionedWorkflow> loadWorkflow(String workflowId, IWorkflowVersion version);

    /**
     * Gets step metadata (processor identifier, timeout, etc.) for a step.
     *
     * @param workflowId workflow identifier
     * @param version workflow version
     * @param stepName step name
     * @return step metadata
     */
    Optional<StepMetadata> getStepMetadata(
            String workflowId,
            IWorkflowVersion version,
            String stepName);

    /**
     * Metadata about a workflow step for execution.
     */
    record StepMetadata(
            String stepName,
            String processorIdentifier,
            String workflowCallIdentifier,
            boolean isProcessorStep,
            boolean isWorkflowCallStep,
            long timeoutMs,
            Map<String, Object> inputConfig
    ) {
        public static StepMetadata fromStep(ILyshraOpenAppWorkflowStep step) {
            return new StepMetadata(
                    step.getName(),
                    step.getProcessor() != null ? step.getProcessor().toString() : null,
                    step.getWorkflowCall() != null ? step.getWorkflowCall().toString() : null,
                    step.getProcessor() != null,
                    step.getWorkflowCall() != null,
                    step.getTimeout() != null ? step.getTimeout().toMillis() : 0,
                    step.getInputConfig()
            );
        }
    }
}
