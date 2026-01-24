package com.lyshra.open.app.core.engine.version.execution;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowInstanceMetadata;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Version-aware execution engine that uses workflow version from instance metadata
 * to resolve processors and step definitions.
 *
 * <p>This engine ensures that running workflows execute using the version they
 * started with, preventing new changes from affecting running instances.</p>
 *
 * <p>Key guarantees:</p>
 * <ul>
 *   <li>Step definitions are resolved from the version in instance metadata</li>
 *   <li>Processor configurations are loaded from the correct workflow version</li>
 *   <li>New workflow updates do not affect in-flight executions</li>
 *   <li>Consistent execution behavior throughout the workflow lifecycle</li>
 * </ul>
 *
 * <p>Execution Flow:</p>
 * <pre>
 * 1. Engine receives context with instance metadata
 * 2. Extracts workflow version from metadata
 * 3. Loads workflow definition for that specific version
 * 4. Resolves steps and processors from that version
 * 5. Executes using version-locked definitions
 * </pre>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * // Start new execution with version tracking
 * ExecutionResult result = engine.startExecution(
 *     "order-processing",
 *     WorkflowVersion.of(1, 2, 0),
 *     inputData,
 *     "correlation-123"
 * ).block();
 *
 * // Continue execution using context metadata
 * ExecutionResult result = engine.continueExecution(context).block();
 * }</pre>
 */
public interface IVersionAwareExecutionEngine {

    /**
     * Starts a new workflow execution with version tracking.
     *
     * @param workflowId workflow identifier
     * @param version specific version to execute
     * @param initialData initial data for execution
     * @param correlationId optional correlation ID for tracing
     * @return execution result
     */
    Mono<ExecutionResult> startExecution(
            String workflowId,
            IWorkflowVersion version,
            Object initialData,
            String correlationId);

    /**
     * Starts a new workflow execution using the latest active version.
     *
     * @param workflowId workflow identifier
     * @param initialData initial data for execution
     * @return execution result
     */
    Mono<ExecutionResult> startExecution(String workflowId, Object initialData);

    /**
     * Starts a new workflow execution from a versioned workflow definition.
     *
     * @param workflow versioned workflow to execute
     * @param initialData initial data for execution
     * @param correlationId optional correlation ID
     * @return execution result
     */
    Mono<ExecutionResult> startExecution(
            IVersionedWorkflow workflow,
            Object initialData,
            String correlationId);

    /**
     * Continues execution using the version from context's instance metadata.
     * The engine will use the workflow version stored in the context to ensure
     * consistency with the version the execution started with.
     *
     * @param context execution context with instance metadata
     * @return execution result
     */
    Mono<ExecutionResult> continueExecution(ILyshraOpenAppContext context);

    /**
     * Continues execution from a specific step.
     * Uses the version from context's instance metadata.
     *
     * @param context execution context with instance metadata
     * @param fromStep step name to resume from
     * @return execution result
     */
    Mono<ExecutionResult> continueExecution(ILyshraOpenAppContext context, String fromStep);

    /**
     * Executes a single step using the version from context metadata.
     *
     * @param context execution context with instance metadata
     * @param stepName step to execute
     * @return step result with next step information
     */
    Mono<StepResult> executeStep(ILyshraOpenAppContext context, String stepName);

    /**
     * Gets the workflow version being used for an execution context.
     *
     * @param context execution context
     * @return workflow version from metadata
     */
    Optional<IWorkflowVersion> getExecutionVersion(ILyshraOpenAppContext context);

    /**
     * Gets the versioned workflow definition for an execution context.
     *
     * @param context execution context
     * @return versioned workflow if found
     */
    Optional<IVersionedWorkflow> getExecutionWorkflow(ILyshraOpenAppContext context);

    /**
     * Validates that an execution context has proper version metadata.
     *
     * @param context execution context
     * @return true if version metadata is present and valid
     */
    boolean hasValidVersionMetadata(ILyshraOpenAppContext context);

    /**
     * Result of workflow execution.
     */
    record ExecutionResult(
            String instanceId,
            String workflowId,
            IWorkflowVersion version,
            ILyshraOpenAppContext context,
            ExecutionStatus status,
            String finalStep,
            long durationMs,
            Optional<String> errorMessage
    ) {
        public static ExecutionResult success(
                String instanceId,
                String workflowId,
                IWorkflowVersion version,
                ILyshraOpenAppContext context,
                String finalStep,
                long durationMs) {
            return new ExecutionResult(
                    instanceId, workflowId, version, context,
                    ExecutionStatus.COMPLETED, finalStep, durationMs, Optional.empty());
        }

        public static ExecutionResult failed(
                String instanceId,
                String workflowId,
                IWorkflowVersion version,
                ILyshraOpenAppContext context,
                String failedStep,
                long durationMs,
                String errorMessage) {
            return new ExecutionResult(
                    instanceId, workflowId, version, context,
                    ExecutionStatus.FAILED, failedStep, durationMs, Optional.of(errorMessage));
        }

        public static ExecutionResult paused(
                String instanceId,
                String workflowId,
                IWorkflowVersion version,
                ILyshraOpenAppContext context,
                String pausedAtStep,
                long durationMs) {
            return new ExecutionResult(
                    instanceId, workflowId, version, context,
                    ExecutionStatus.PAUSED, pausedAtStep, durationMs, Optional.empty());
        }

        public boolean isSuccessful() {
            return status == ExecutionStatus.COMPLETED;
        }

        public boolean isFailed() {
            return status == ExecutionStatus.FAILED;
        }
    }

    /**
     * Result of a single step execution.
     */
    record StepResult(
            String stepName,
            String nextStep,
            ILyshraOpenAppContext context,
            StepStatus status,
            long durationMs,
            Optional<String> errorMessage
    ) {
        public static StepResult success(
                String stepName,
                String nextStep,
                ILyshraOpenAppContext context,
                long durationMs) {
            return new StepResult(stepName, nextStep, context, StepStatus.SUCCESS, durationMs, Optional.empty());
        }

        public static StepResult failed(
                String stepName,
                ILyshraOpenAppContext context,
                long durationMs,
                String errorMessage) {
            return new StepResult(stepName, null, context, StepStatus.FAILED, durationMs, Optional.of(errorMessage));
        }

        public boolean isSuccessful() {
            return status == StepStatus.SUCCESS;
        }

        public boolean isFailed() {
            return status == StepStatus.FAILED;
        }

        public boolean hasNextStep() {
            return nextStep != null && !nextStep.isBlank();
        }
    }

    /**
     * Status of workflow execution.
     */
    enum ExecutionStatus {
        RUNNING,
        COMPLETED,
        FAILED,
        PAUSED,
        CANCELLED
    }

    /**
     * Status of step execution.
     */
    enum StepStatus {
        SUCCESS,
        FAILED,
        SKIPPED
    }
}
