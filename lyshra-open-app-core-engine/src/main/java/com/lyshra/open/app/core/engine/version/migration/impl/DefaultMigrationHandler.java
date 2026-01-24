package com.lyshra.open.app.core.engine.version.migration.impl;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.version.IVersionCompatibility;
import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowMigrationHints;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationContext;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationHandler;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationResult;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationValidationResult;
import com.lyshra.open.app.integration.models.version.migration.MigrationResult;
import com.lyshra.open.app.integration.models.version.migration.MigrationValidationResult;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default migration handler that applies standard migration logic.
 * Can be extended for custom workflow-specific migration behavior.
 *
 * <p>Design Pattern: Template Method - provides default behavior with extension points.</p>
 */
@Slf4j
public class DefaultMigrationHandler implements IMigrationHandler {

    private final String workflowId;
    private final String sourceVersionPattern;
    private final IWorkflowVersion targetVersion;
    private final IVersionedWorkflow targetWorkflow;
    private final int priority;

    public DefaultMigrationHandler(
            String workflowId,
            String sourceVersionPattern,
            IVersionedWorkflow targetWorkflow,
            int priority) {
        this.workflowId = workflowId;
        this.sourceVersionPattern = sourceVersionPattern;
        this.targetWorkflow = targetWorkflow;
        this.targetVersion = targetWorkflow.getVersion();
        this.priority = priority;
    }

    @Override
    public String getWorkflowId() {
        return workflowId;
    }

    @Override
    public String getSourceVersionPattern() {
        return sourceVersionPattern;
    }

    @Override
    public IWorkflowVersion getTargetVersion() {
        return targetVersion;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public Mono<IMigrationValidationResult> validate(IMigrationContext context) {
        return Mono.fromCallable(() -> {
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            // Check version compatibility
            IVersionCompatibility compatibility = targetWorkflow.getCompatibility();
            if (compatibility != null && !compatibility.isCompatibleWith(context.getSourceVersion())) {
                errors.add("Versions are not compatible according to compatibility rules");
            }

            // Check current step exists in target
            String currentStep = context.getCurrentStepName();
            boolean stepExists = targetWorkflow.getSteps().containsKey(currentStep);
            String targetStep = currentStep;

            if (!stepExists) {
                Optional<IWorkflowMigrationHints> hintsOpt = targetWorkflow.getMigrationHints();
                if (hintsOpt.isPresent()) {
                    Optional<String> mappedStep = hintsOpt.get().getSuggestedMigrationStep(currentStep);
                    if (mappedStep.isPresent()) {
                        targetStep = mappedStep.get();
                        warnings.add("Step '" + currentStep + "' mapped to '" + targetStep + "'");
                    } else {
                        targetStep = targetWorkflow.getStartStep();
                        warnings.add("Step '" + currentStep + "' not found, will restart from beginning");
                    }
                } else {
                    targetStep = targetWorkflow.getStartStep();
                    warnings.add("Step '" + currentStep + "' not found, will restart from beginning");
                }
            }

            // Check safe migration point
            boolean atSafePoint = targetWorkflow.getMigrationHints()
                    .map(h -> h.getSafeMigrationPoints().contains(currentStep))
                    .orElse(true);

            if (!atSafePoint) {
                warnings.add("Not at a designated safe migration point");
            }

            if (!errors.isEmpty()) {
                return MigrationValidationResult.invalid(errors);
            }

            if (!warnings.isEmpty()) {
                return MigrationValidationResult.validWithWarnings(targetStep, atSafePoint, warnings);
            }

            return MigrationValidationResult.valid(targetStep, atSafePoint);
        });
    }

    @Override
    public Mono<IMigrationResult> migrate(IMigrationContext context) {
        Instant startTime = Instant.now();

        return validate(context)
                .flatMap(validation -> {
                    if (!validation.isValid()) {
                        return Mono.just(MigrationResult.failed(
                                IMigrationResult.Status.FAILED_VALIDATION,
                                context.getExecutionId(),
                                context.getSourceVersion(),
                                context.getTargetVersion(),
                                new IllegalStateException("Validation failed: " + validation.getErrors()),
                                Duration.between(startTime, Instant.now()),
                                context.getPreMigrationState(),
                                List.of()));
                    }

                    return Mono.fromCallable(() -> {
                        ILyshraOpenAppContext migratedContext = context.getExecutionContext();

                        // Apply context transformations
                        applyContextTransformations(migratedContext, targetWorkflow);

                        // Apply variable transformations
                        applyVariableTransformations(migratedContext, targetWorkflow);

                        String targetStep = validation.getRecommendedTargetStep()
                                .orElse(context.getCurrentStepName());

                        Map<String, Object> postMigrationSnapshot = new HashMap<>();
                        postMigrationSnapshot.put("data", migratedContext.getData());
                        postMigrationSnapshot.put("variables", new HashMap<>(migratedContext.getVariables()));
                        postMigrationSnapshot.put("currentStep", targetStep);

                        log.info("Default migration handler completed: {} -> {} at step {}",
                                context.getSourceVersion().toVersionString(),
                                context.getTargetVersion().toVersionString(),
                                targetStep);

                        return MigrationResult.success(
                                context.getExecutionId(),
                                context.getSourceVersion(),
                                context.getTargetVersion(),
                                migratedContext,
                                targetStep,
                                Duration.between(startTime, Instant.now()),
                                context.getPreMigrationState(),
                                postMigrationSnapshot,
                                List.of());
                    });
                });
    }

    @Override
    public Mono<IMigrationResult> rollback(IMigrationContext context, IMigrationResult failedResult) {
        return Mono.fromCallable(() -> {
            ILyshraOpenAppContext executionContext = context.getExecutionContext();

            // Restore original state
            Map<String, Object> preState = context.getPreMigrationState();
            Object originalData = preState.get("data");
            if (originalData != null) {
                executionContext.setData(originalData);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> originalVariables = (Map<String, Object>) preState.get("variables");
            if (originalVariables != null) {
                executionContext.setVariables(new HashMap<>(originalVariables));
            }

            log.info("Default migration handler rollback completed for execution [{}]",
                    context.getExecutionId());

            return MigrationResult.builder()
                    .status(IMigrationResult.Status.ROLLED_BACK)
                    .executionId(context.getExecutionId())
                    .sourceVersion(context.getSourceVersion())
                    .targetVersion(context.getTargetVersion())
                    .migratedContext(executionContext)
                    .targetStepName(context.getCurrentStepName())
                    .preMigrationSnapshot(context.getPreMigrationState())
                    .duration(Duration.ZERO)
                    .build();
        });
    }

    @Override
    public boolean canHandle(IMigrationContext context) {
        return workflowId.equals(context.getWorkflowId()) &&
               targetVersion.equals(context.getTargetVersion());
    }

    /**
     * Extension point for custom context transformations.
     * Override in subclasses for workflow-specific logic.
     */
    protected void applyContextTransformations(
            ILyshraOpenAppContext context,
            IVersionedWorkflow targetWorkflow) {

        IVersionCompatibility compatibility = targetWorkflow.getCompatibility();
        if (compatibility == null || !compatibility.requiresContextMigration()) {
            return;
        }

        // Apply field mappings from hints
        targetWorkflow.getMigrationHints().ifPresent(hints -> {
            Map<String, String> fieldMappings = hints.getContextFieldMappings();
            // Context field mapping would require reflection or specific data structure knowledge
            // This is a placeholder for workflow-specific implementations
            if (!fieldMappings.isEmpty()) {
                log.debug("Context field mappings to apply: {}", fieldMappings);
            }
        });
    }

    /**
     * Extension point for custom variable transformations.
     * Override in subclasses for workflow-specific logic.
     */
    protected void applyVariableTransformations(
            ILyshraOpenAppContext context,
            IVersionedWorkflow targetWorkflow) {

        IVersionCompatibility compatibility = targetWorkflow.getCompatibility();
        if (compatibility == null || !compatibility.requiresVariableMigration()) {
            return;
        }

        targetWorkflow.getMigrationHints().ifPresent(hints -> {
            hints.getVariableMappings().forEach((oldName, newName) -> {
                if (context.hasVariable(oldName)) {
                    Object value = context.getVariable(oldName);
                    context.removeVariable(oldName);
                    context.addVariable(newName, value);
                    log.debug("Migrated variable {} -> {}", oldName, newName);
                }
            });
        });
    }

    /**
     * Creates a default migration handler for a workflow.
     */
    public static DefaultMigrationHandler forWorkflow(
            IVersionedWorkflow targetWorkflow,
            String sourceVersionPattern) {
        return new DefaultMigrationHandler(
                targetWorkflow.getWorkflowId(),
                sourceVersionPattern,
                targetWorkflow,
                0);
    }
}
