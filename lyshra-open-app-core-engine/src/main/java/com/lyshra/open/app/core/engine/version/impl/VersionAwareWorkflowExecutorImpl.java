package com.lyshra.open.app.core.engine.version.impl;

import com.lyshra.open.app.core.engine.ILyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.LyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.version.IVersionAwareWorkflowExecutor;
import com.lyshra.open.app.core.engine.version.IWorkflowExecutionBindingStore;
import com.lyshra.open.app.core.engine.version.IWorkflowVersionRegistryService;
import com.lyshra.open.app.core.engine.version.migration.IWorkflowMigrationExecutor;
import com.lyshra.open.app.core.engine.version.migration.impl.WorkflowMigrationExecutorImpl;
import com.lyshra.open.app.integration.constant.LyshraOpenAppConstants;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowExecutionBinding;
import com.lyshra.open.app.integration.contract.version.IWorkflowMigrationHints;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationContext;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationResult;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationStrategy;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationValidationResult;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier;
import com.lyshra.open.app.integration.models.version.WorkflowExecutionBinding;
import com.lyshra.open.app.integration.models.version.migration.MigrationContext;
import com.lyshra.open.app.integration.models.workflows.step.LyshraOpenAppWorkflowStepIdentifier;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Implementation of version-aware workflow executor.
 * Wraps the base executor with version binding and migration support.
 *
 * <p>Design Patterns:</p>
 * <ul>
 *   <li>Decorator: Adds versioning to base execution</li>
 *   <li>Strategy: Configurable migration strategies</li>
 *   <li>Observer: Step completion hooks for migration checks</li>
 * </ul>
 */
@Slf4j
public class VersionAwareWorkflowExecutorImpl implements IVersionAwareWorkflowExecutor {

    private final ILyshraOpenAppFacade facade;
    private final IWorkflowVersionRegistryService versionRegistry;
    private final IWorkflowExecutionBindingStore bindingStore;
    private final IWorkflowMigrationExecutor migrationExecutor;

    private VersionAwareWorkflowExecutorImpl() {
        this.facade = LyshraOpenAppFacade.getInstance();
        this.versionRegistry = WorkflowVersionRegistryImpl.getInstance();
        this.bindingStore = InMemoryWorkflowExecutionBindingStoreImpl.getInstance();
        this.migrationExecutor = WorkflowMigrationExecutorImpl.getInstance();
    }

    private static final class SingletonHelper {
        private static final VersionAwareWorkflowExecutorImpl INSTANCE = new VersionAwareWorkflowExecutorImpl();
    }

    public static IVersionAwareWorkflowExecutor getInstance() {
        return SingletonHelper.INSTANCE;
    }

    @Override
    public Mono<VersionedExecutionResult> execute(
            ILyshraOpenAppWorkflowIdentifier identifier,
            ILyshraOpenAppContext context) {

        return Mono.fromCallable(() -> {
            // Resolve to default version
            String workflowId = identifier.getWorkflowName();
            IVersionedWorkflow workflow = versionRegistry.getDefaultVersion(workflowId)
                    .orElseThrow(() -> new IllegalStateException(
                            "No default version found for workflow: " + workflowId));
            return workflow;
        }).flatMap(workflow -> executeVersion(workflow, context, IMigrationStrategy.FROZEN));
    }

    @Override
    public Mono<VersionedExecutionResult> executeVersion(
            IVersionedWorkflow workflow,
            ILyshraOpenAppContext context,
            IMigrationStrategy migrationStrategy) {

        return Mono.defer(() -> {
            // Create execution binding
            WorkflowExecutionBinding binding = WorkflowExecutionBinding.builder()
                    .workflowIdentifier(createWorkflowIdentifier(workflow))
                    .boundVersion(workflow.getVersion())
                    .currentStepName(workflow.getStartStep())
                    .state(IWorkflowExecutionBinding.ExecutionState.CREATED)
                    .migrationStrategy(migrationStrategy)
                    .build();

            bindingStore.save(binding);

            String executionId = binding.getExecutionId();
            log.info("Starting versioned workflow execution [{}] for workflow [{}] version [{}]",
                    executionId, workflow.getWorkflowId(), workflow.getVersion().toVersionString());

            // Update state to running
            WorkflowExecutionBinding runningBinding = binding.withState(IWorkflowExecutionBinding.ExecutionState.RUNNING);
            bindingStore.update(runningBinding);

            // Execute workflow with step tracking
            return executeStepsWithVersioning(workflow, runningBinding, context)
                    .map(resultContext -> {
                        // Mark as completed
                        WorkflowExecutionBinding completedBinding = runningBinding
                                .withState(IWorkflowExecutionBinding.ExecutionState.COMPLETED);
                        bindingStore.update(completedBinding);

                        return VersionedExecutionResult.of(executionId, resultContext, completedBinding);
                    })
                    .onErrorResume(error -> {
                        log.error("Workflow execution [{}] failed: {}", executionId, error.getMessage(), error);
                        WorkflowExecutionBinding failedBinding = runningBinding
                                .withState(IWorkflowExecutionBinding.ExecutionState.FAILED);
                        bindingStore.update(failedBinding);
                        return Mono.error(error);
                    });
        });
    }

    private Mono<ILyshraOpenAppContext> executeStepsWithVersioning(
            IVersionedWorkflow workflow,
            WorkflowExecutionBinding binding,
            ILyshraOpenAppContext context) {

        return executeStep(workflow.getStartStep(), workflow, binding, context);
    }

    private Mono<ILyshraOpenAppContext> executeStep(
            String currentStep,
            IVersionedWorkflow workflow,
            WorkflowExecutionBinding binding,
            ILyshraOpenAppContext context) {

        if (currentStep == null || currentStep.isBlank() ||
            LyshraOpenAppConstants.NOOP_PROCESSOR.equalsIgnoreCase(currentStep.trim())) {
            log.info("Workflow execution [{}] completed at step [{}]",
                    binding.getExecutionId(), currentStep);
            return Mono.just(context);
        }

        // Update binding with current step
        WorkflowExecutionBinding updatedBinding = binding.withCurrentStep(currentStep);
        bindingStore.update(updatedBinding);

        // Check for migration opportunity (for OPPORTUNISTIC strategy)
        return checkAndMaybeApplyMigration(workflow, updatedBinding, context, currentStep)
                .flatMap(migrationResult -> {
                    if (migrationResult.migrationApplied()) {
                        // Continue execution on new version
                        return executeStep(
                                migrationResult.newStep(),
                                migrationResult.newWorkflow(),
                                migrationResult.newBinding(),
                                migrationResult.context());
                    }

                    // Execute current step
                    ILyshraOpenAppWorkflowIdentifier workflowId = createWorkflowIdentifier(workflow);
                    LyshraOpenAppWorkflowStepIdentifier stepIdentifier =
                            new LyshraOpenAppWorkflowStepIdentifier(workflowId, currentStep);

                    return facade.getWorkflowStepExecutor().execute(stepIdentifier, context)
                            .flatMap(nextStep -> executeStep(nextStep, workflow, updatedBinding, context));
                });
    }

    private Mono<MigrationCheckResult> checkAndMaybeApplyMigration(
            IVersionedWorkflow currentWorkflow,
            WorkflowExecutionBinding binding,
            ILyshraOpenAppContext context,
            String currentStep) {

        // Only check for OPPORTUNISTIC strategy
        if (binding.getMigrationStrategy() != IMigrationStrategy.OPPORTUNISTIC) {
            return Mono.just(MigrationCheckResult.noMigration(currentWorkflow, binding, context, currentStep));
        }

        // Check if at safe migration point
        Optional<IWorkflowMigrationHints> hints = currentWorkflow.getMigrationHints();
        boolean atSafePoint = hints.map(h -> h.getSafeMigrationPoints().contains(currentStep)).orElse(false);

        if (!atSafePoint) {
            return Mono.just(MigrationCheckResult.noMigration(currentWorkflow, binding, context, currentStep));
        }

        // Check for newer version
        Optional<IVersionedWorkflow> newerVersion = versionRegistry
                .getRecommendedMigrationTarget(currentWorkflow.getWorkflowId(), currentWorkflow.getVersion());

        if (newerVersion.isEmpty()) {
            return Mono.just(MigrationCheckResult.noMigration(currentWorkflow, binding, context, currentStep));
        }

        IVersionedWorkflow targetWorkflow = newerVersion.get();

        // Validate migration
        return migrationExecutor.validate(binding, targetWorkflow)
                .flatMap(validation -> {
                    if (!validation.isValid()) {
                        log.debug("Migration validation failed for execution [{}]: {}",
                                binding.getExecutionId(), validation.getErrors());
                        return Mono.just(MigrationCheckResult.noMigration(currentWorkflow, binding, context, currentStep));
                    }

                    // Execute migration
                    IMigrationContext migrationContext = MigrationContext.forExecution(
                            binding.getExecutionId(),
                            currentWorkflow.getWorkflowId(),
                            currentWorkflow.getVersion(),
                            targetWorkflow.getVersion(),
                            context,
                            currentStep,
                            IMigrationStrategy.OPPORTUNISTIC);

                    return migrationExecutor.migrate(migrationContext)
                            .map(result -> {
                                if (result.isSuccessful()) {
                                    String newStep = result.getTargetStepName().orElse(currentStep);
                                    WorkflowExecutionBinding newBinding = ((WorkflowExecutionBinding) binding)
                                            .migrateTo(targetWorkflow.getVersion(), newStep);
                                    return MigrationCheckResult.migrated(
                                            targetWorkflow, newBinding,
                                            result.getMigratedContext().orElse(context), newStep);
                                }
                                return MigrationCheckResult.noMigration(currentWorkflow, binding, context, currentStep);
                            });
                });
    }

    @Override
    public Mono<VersionedExecutionResult> resume(String executionId, ILyshraOpenAppContext context) {
        return Mono.fromCallable(() -> bindingStore.findById(executionId)
                .orElseThrow(() -> new IllegalStateException("Execution binding not found: " + executionId)))
                .flatMap(binding -> {
                    if (binding.isTerminal()) {
                        return Mono.error(new IllegalStateException(
                                "Cannot resume terminal execution: " + executionId + " state: " + binding.getState()));
                    }

                    // Get the bound workflow version
                    IVersionedWorkflow workflow = versionRegistry
                            .getVersion(binding.getWorkflowIdentifier().getWorkflowName(), binding.getBoundVersion())
                            .orElseThrow(() -> new IllegalStateException(
                                    "Workflow version not found: " + binding.getBoundVersion().toVersionString()));

                    // Update state to running
                    WorkflowExecutionBinding runningBinding = ((WorkflowExecutionBinding) binding)
                            .withState(IWorkflowExecutionBinding.ExecutionState.RUNNING);
                    bindingStore.update(runningBinding);

                    log.info("Resuming workflow execution [{}] at step [{}]",
                            executionId, binding.getCurrentStepName());

                    // Continue from current step
                    return executeStep(binding.getCurrentStepName(), workflow, runningBinding, context)
                            .map(resultContext -> {
                                WorkflowExecutionBinding completedBinding = runningBinding
                                        .withState(IWorkflowExecutionBinding.ExecutionState.COMPLETED);
                                bindingStore.update(completedBinding);
                                return VersionedExecutionResult.of(executionId, resultContext, completedBinding);
                            });
                });
    }

    @Override
    public Optional<IWorkflowExecutionBinding> getBinding(String executionId) {
        return bindingStore.findById(executionId);
    }

    @Override
    public boolean hasNewerVersionAvailable(String executionId) {
        return bindingStore.findById(executionId)
                .flatMap(binding -> versionRegistry.getRecommendedMigrationTarget(
                        binding.getWorkflowIdentifier().getWorkflowName(),
                        binding.getBoundVersion()))
                .isPresent();
    }

    @Override
    public Optional<IVersionedWorkflow> getRecommendedUpgrade(String executionId) {
        return bindingStore.findById(executionId)
                .flatMap(binding -> versionRegistry.getRecommendedMigrationTarget(
                        binding.getWorkflowIdentifier().getWorkflowName(),
                        binding.getBoundVersion()));
    }

    private ILyshraOpenAppWorkflowIdentifier createWorkflowIdentifier(IVersionedWorkflow workflow) {
        return new ILyshraOpenAppWorkflowIdentifier() {
            @Override
            public String getWorkflowName() {
                return workflow.getName();
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
                return workflow.getVersion().toVersionString();
            }

            @Override
            public String toString() {
                return getOrganization() + "/" + getModule() + "/" + getVersion() + "/" + getWorkflowName();
            }
        };
    }

    /**
     * Internal result of migration check.
     */
    private record MigrationCheckResult(
            boolean migrationApplied,
            IVersionedWorkflow newWorkflow,
            WorkflowExecutionBinding newBinding,
            ILyshraOpenAppContext context,
            String newStep
    ) {
        static MigrationCheckResult noMigration(
                IVersionedWorkflow workflow,
                WorkflowExecutionBinding binding,
                ILyshraOpenAppContext context,
                String step) {
            return new MigrationCheckResult(false, workflow, binding, context, step);
        }

        static MigrationCheckResult migrated(
                IVersionedWorkflow workflow,
                WorkflowExecutionBinding binding,
                ILyshraOpenAppContext context,
                String step) {
            return new MigrationCheckResult(true, workflow, binding, context, step);
        }
    }
}
