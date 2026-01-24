package com.lyshra.open.app.core.engine.version.execution;

import com.lyshra.open.app.core.engine.ILyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.LyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.version.context.VersionedContextFactory;
import com.lyshra.open.app.core.engine.version.loader.IWorkflowDefinitionLoader;
import com.lyshra.open.app.core.engine.version.storage.IVersionedWorkflowStorage;
import com.lyshra.open.app.core.models.LyshraOpenAppContext;
import com.lyshra.open.app.integration.constant.LyshraOpenAppConstants;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowInstanceMetadata;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStep;
import com.lyshra.open.app.integration.models.version.WorkflowInstanceMetadata;
import com.lyshra.open.app.integration.models.workflows.step.LyshraOpenAppWorkflowStepIdentifier;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Implementation of version-aware execution engine.
 *
 * <p>This engine ensures that running workflows execute using the version they
 * started with by:</p>
 * <ul>
 *   <li>Reading workflow version from context's instance metadata</li>
 *   <li>Loading step definitions from that specific version</li>
 *   <li>Resolving processors based on version-locked configurations</li>
 *   <li>Maintaining version consistency throughout execution</li>
 * </ul>
 *
 * <p>Thread Safety: This implementation is thread-safe for concurrent executions.</p>
 */
@Slf4j
public class VersionAwareExecutionEngineImpl implements IVersionAwareExecutionEngine {

    private final IWorkflowDefinitionLoader workflowLoader;
    private final IVersionAwareStepResolver stepResolver;
    private final ILyshraOpenAppFacade facade;

    /**
     * Creates an execution engine with the given components.
     *
     * @param workflowLoader workflow definition loader
     * @param stepResolver version-aware step resolver
     */
    public VersionAwareExecutionEngineImpl(
            IWorkflowDefinitionLoader workflowLoader,
            IVersionAwareStepResolver stepResolver) {
        this.workflowLoader = workflowLoader;
        this.stepResolver = stepResolver;
        this.facade = LyshraOpenAppFacade.getInstance();
    }

    /**
     * Creates an execution engine with storage backend.
     *
     * @param storage workflow storage
     * @return engine instance
     */
    public static IVersionAwareExecutionEngine create(IVersionedWorkflowStorage storage) {
        IWorkflowDefinitionLoader loader = com.lyshra.open.app.core.engine.version.loader.WorkflowDefinitionLoaderImpl.create(storage);
        IVersionAwareStepResolver resolver = VersionAwareStepResolverImpl.create(loader);
        return new VersionAwareExecutionEngineImpl(loader, resolver);
    }

    /**
     * Creates an execution engine with custom loader.
     *
     * @param loader workflow definition loader
     * @return engine instance
     */
    public static IVersionAwareExecutionEngine create(IWorkflowDefinitionLoader loader) {
        IVersionAwareStepResolver resolver = VersionAwareStepResolverImpl.create(loader);
        return new VersionAwareExecutionEngineImpl(loader, resolver);
    }

    @Override
    public Mono<ExecutionResult> startExecution(
            String workflowId,
            IWorkflowVersion version,
            Object initialData,
            String correlationId) {

        return Mono.fromCallable(() -> {
            // Load workflow definition
            IVersionedWorkflow workflow = workflowLoader.load(workflowId, version)
                    .orElseThrow(() -> new IllegalArgumentException(
                            String.format("Workflow not found: %s:%s", workflowId, version.toVersionString())));
            return workflow;
        }).flatMap(workflow -> startExecution(workflow, initialData, correlationId));
    }

    @Override
    public Mono<ExecutionResult> startExecution(String workflowId, Object initialData) {
        return Mono.fromCallable(() -> {
            // Load latest active version
            IVersionedWorkflow workflow = workflowLoader.loadLatestActive(workflowId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No active version found for workflow: " + workflowId));
            return workflow;
        }).flatMap(workflow -> startExecution(workflow, initialData, null));
    }

    @Override
    public Mono<ExecutionResult> startExecution(
            IVersionedWorkflow workflow,
            Object initialData,
            String correlationId) {

        return Mono.defer(() -> {
            long startTime = System.currentTimeMillis();

            // Create versioned context with instance metadata
            ILyshraOpenAppContext context = VersionedContextFactory
                    .forWorkflow(workflow)
                    .withCorrelationId(correlationId)
                    .withData(initialData)
                    .createAndStart();

            String instanceId = context.getInstanceId().orElse("unknown");
            String workflowId = workflow.getWorkflowId();
            IWorkflowVersion version = workflow.getVersion();

            log.info("Starting version-aware execution: instanceId={}, workflowId={}, version={}",
                    instanceId, workflowId, version.toVersionString());

            // Execute workflow
            return executeWorkflow(workflow, context)
                    .map(resultContext -> {
                        long duration = System.currentTimeMillis() - startTime;
                        String finalStep = resultContext.getInstanceMetadata()
                                .flatMap(IWorkflowInstanceMetadata::getCurrentStepName)
                                .orElse("completed");

                        VersionedContextFactory.markCompleted(resultContext);

                        log.info("Completed execution: instanceId={}, workflowId={}, version={}, duration={}ms",
                                instanceId, workflowId, version.toVersionString(), duration);

                        return ExecutionResult.success(
                                instanceId, workflowId, version, resultContext, finalStep, duration);
                    })
                    .onErrorResume(error -> {
                        long duration = System.currentTimeMillis() - startTime;
                        String failedStep = context.getInstanceMetadata()
                                .flatMap(IWorkflowInstanceMetadata::getCurrentStepName)
                                .orElse("unknown");

                        VersionedContextFactory.markFailed(context);

                        log.error("Execution failed: instanceId={}, workflowId={}, version={}, step={}, error={}",
                                instanceId, workflowId, version.toVersionString(), failedStep, error.getMessage(), error);

                        return Mono.just(ExecutionResult.failed(
                                instanceId, workflowId, version, context, failedStep, duration, error.getMessage()));
                    });
        });
    }

    @Override
    public Mono<ExecutionResult> continueExecution(ILyshraOpenAppContext context) {
        return context.getInstanceMetadata()
                .flatMap(IWorkflowInstanceMetadata::getCurrentStepName)
                .map(step -> continueExecution(context, step))
                .orElseGet(() -> {
                    // No current step - start from beginning
                    return getExecutionWorkflow(context)
                            .map(workflow -> continueExecution(context, workflow.getStartStep()))
                            .orElseGet(() -> Mono.error(new IllegalStateException(
                                    "Cannot continue execution: no workflow version in context metadata")));
                });
    }

    @Override
    public Mono<ExecutionResult> continueExecution(ILyshraOpenAppContext context, String fromStep) {
        if (!hasValidVersionMetadata(context)) {
            return Mono.error(new IllegalStateException(
                    "Cannot continue execution: context missing valid version metadata"));
        }

        return Mono.defer(() -> {
            long startTime = System.currentTimeMillis();

            IWorkflowInstanceMetadata metadata = context.getInstanceMetadata().get();
            String instanceId = metadata.getInstanceId();
            String workflowId = metadata.getWorkflowId();
            IWorkflowVersion version = metadata.getCurrentVersion();

            // Load workflow for the specific version
            IVersionedWorkflow workflow = workflowLoader.load(workflowId, version)
                    .orElseThrow(() -> new IllegalStateException(
                            String.format("Workflow version not found: %s:%s", workflowId, version.toVersionString())));

            log.info("Continuing version-aware execution: instanceId={}, workflowId={}, version={}, fromStep={}",
                    instanceId, workflowId, version.toVersionString(), fromStep);

            // Update context state
            if (metadata instanceof WorkflowInstanceMetadata wim) {
                context.setInstanceMetadata(wim.resume().withCurrentStep(fromStep));
            }

            // Continue execution
            return executeFromStep(workflow, context, fromStep)
                    .map(resultContext -> {
                        long duration = System.currentTimeMillis() - startTime;
                        String finalStep = resultContext.getInstanceMetadata()
                                .flatMap(IWorkflowInstanceMetadata::getCurrentStepName)
                                .orElse("completed");

                        VersionedContextFactory.markCompleted(resultContext);

                        return ExecutionResult.success(
                                instanceId, workflowId, version, resultContext, finalStep, duration);
                    })
                    .onErrorResume(error -> {
                        long duration = System.currentTimeMillis() - startTime;
                        VersionedContextFactory.markFailed(context);

                        return Mono.just(ExecutionResult.failed(
                                instanceId, workflowId, version, context, fromStep, duration, error.getMessage()));
                    });
        });
    }

    @Override
    public Mono<StepResult> executeStep(ILyshraOpenAppContext context, String stepName) {
        if (!hasValidVersionMetadata(context)) {
            return Mono.error(new IllegalStateException(
                    "Cannot execute step: context missing valid version metadata"));
        }

        return Mono.defer(() -> {
            long startTime = System.currentTimeMillis();

            IWorkflowInstanceMetadata metadata = context.getInstanceMetadata().get();
            String workflowId = metadata.getWorkflowId();
            IWorkflowVersion version = metadata.getCurrentVersion();

            // Resolve step from the version in metadata
            ILyshraOpenAppWorkflowStep step = stepResolver.resolveStep(workflowId, version, stepName)
                    .orElseThrow(() -> new IllegalArgumentException(
                            String.format("Step not found: %s in workflow %s:%s",
                                    stepName, workflowId, version.toVersionString())));

            log.debug("Executing step {} from version {} of workflow {}",
                    stepName, version.toVersionString(), workflowId);

            // Update current step in metadata
            VersionedContextFactory.updateCurrentStep(context, stepName);

            // Create step identifier for execution
            ILyshraOpenAppWorkflowIdentifier workflowIdentifier = createWorkflowIdentifier(workflowId, version);
            LyshraOpenAppWorkflowStepIdentifier stepIdentifier =
                    new LyshraOpenAppWorkflowStepIdentifier(workflowIdentifier, stepName);

            // Execute the step
            return facade.getWorkflowStepExecutor().execute(stepIdentifier, context)
                    .map(nextStep -> {
                        long duration = System.currentTimeMillis() - startTime;
                        log.debug("Step {} completed, next step: {}", stepName, nextStep);
                        return StepResult.success(stepName, nextStep, context, duration);
                    })
                    .onErrorResume(error -> {
                        long duration = System.currentTimeMillis() - startTime;
                        log.error("Step {} failed: {}", stepName, error.getMessage(), error);
                        return Mono.just(StepResult.failed(stepName, context, duration, error.getMessage()));
                    });
        });
    }

    @Override
    public Optional<IWorkflowVersion> getExecutionVersion(ILyshraOpenAppContext context) {
        return context.getInstanceMetadata()
                .map(IWorkflowInstanceMetadata::getCurrentVersion);
    }

    @Override
    public Optional<IVersionedWorkflow> getExecutionWorkflow(ILyshraOpenAppContext context) {
        return context.getInstanceMetadata()
                .flatMap(metadata -> workflowLoader.load(
                        metadata.getWorkflowId(),
                        metadata.getCurrentVersion()));
    }

    @Override
    public boolean hasValidVersionMetadata(ILyshraOpenAppContext context) {
        return context.getInstanceMetadata()
                .filter(metadata -> metadata.getWorkflowId() != null)
                .filter(metadata -> metadata.getCurrentVersion() != null)
                .isPresent();
    }

    /**
     * Executes the workflow from start to completion.
     */
    private Mono<ILyshraOpenAppContext> executeWorkflow(
            IVersionedWorkflow workflow,
            ILyshraOpenAppContext context) {

        // Capture workflow start
        ILyshraOpenAppWorkflowIdentifier workflowId = createWorkflowIdentifier(
                workflow.getWorkflowId(), workflow.getVersion());
        context.captureWorkflowStart(workflowId);

        return executeFromStep(workflow, context, workflow.getStartStep())
                .doOnNext(ctx -> context.captureWorkflowEnd(workflowId))
                .doOnError(error -> context.captureWorkflowEnd(workflowId, error));
    }

    /**
     * Executes from a specific step to completion.
     */
    private Mono<ILyshraOpenAppContext> executeFromStep(
            IVersionedWorkflow workflow,
            ILyshraOpenAppContext context,
            String currentStep) {

        if (isTerminalStep(currentStep)) {
            log.debug("Reached terminal step: {}", currentStep);
            return Mono.just(context);
        }

        return executeStep(context, currentStep)
                .flatMap(stepResult -> {
                    if (stepResult.isFailed()) {
                        return Mono.error(new RuntimeException(
                                stepResult.errorMessage().orElse("Step execution failed")));
                    }

                    if (!stepResult.hasNextStep()) {
                        return Mono.just(stepResult.context());
                    }

                    // Continue to next step
                    return executeFromStep(workflow, stepResult.context(), stepResult.nextStep());
                });
    }

    /**
     * Checks if a step name indicates termination.
     */
    private boolean isTerminalStep(String stepName) {
        return stepName == null ||
               stepName.isBlank() ||
               LyshraOpenAppConstants.NOOP_PROCESSOR.equalsIgnoreCase(stepName.trim());
    }

    /**
     * Creates a workflow identifier for a versioned workflow.
     */
    private ILyshraOpenAppWorkflowIdentifier createWorkflowIdentifier(
            String workflowId, IWorkflowVersion version) {
        return new ILyshraOpenAppWorkflowIdentifier() {
            @Override
            public String getWorkflowName() {
                return workflowId;
            }

            @Override
            public String getOrganization() {
                return "default";
            }

            @Override
            public String getModule() {
                return "versioned";
            }

            @Override
            public String getVersion() {
                return version.toVersionString();
            }

            @Override
            public String toString() {
                return String.format("%s/%s/%s/%s",
                        getOrganization(), getModule(), getVersion(), getWorkflowName());
            }
        };
    }
}
