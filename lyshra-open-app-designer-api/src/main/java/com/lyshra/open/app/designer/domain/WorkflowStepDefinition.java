package com.lyshra.open.app.designer.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a step definition in the workflow designer.
 * Contains all configuration needed to execute a processor or call another workflow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStepDefinition {

    private String id;
    private String name;
    private String description;
    private WorkflowStepType type;
    private ProcessorReference processor;
    private WorkflowReference workflowCall;
    private Map<String, Object> inputConfig;
    private Duration timeout;
    private StepErrorHandling errorHandling;
    private StepPosition position;

    /**
     * Gets the processor reference as Optional.
     *
     * @return Optional containing the processor reference
     */
    public Optional<ProcessorReference> getProcessorOptional() {
        return Optional.ofNullable(processor);
    }

    /**
     * Gets the workflow call reference as Optional.
     *
     * @return Optional containing the workflow call reference
     */
    public Optional<WorkflowReference> getWorkflowCallOptional() {
        return Optional.ofNullable(workflowCall);
    }

    /**
     * Gets the timeout as Optional.
     *
     * @return Optional containing the timeout duration
     */
    public Optional<Duration> getTimeoutOptional() {
        return Optional.ofNullable(timeout);
    }

    /**
     * Checks if this step is a processor step.
     *
     * @return true if the step type is PROCESSOR
     */
    public boolean isProcessorStep() {
        return type == WorkflowStepType.PROCESSOR;
    }

    /**
     * Checks if this step is a workflow call step.
     *
     * @return true if the step type is WORKFLOW
     */
    public boolean isWorkflowCallStep() {
        return type == WorkflowStepType.WORKFLOW;
    }
}
