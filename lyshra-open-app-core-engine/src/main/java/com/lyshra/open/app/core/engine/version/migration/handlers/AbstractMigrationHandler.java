package com.lyshra.open.app.core.engine.version.migration.handlers;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationContext;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationHandler;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationResult;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationValidationResult;
import com.lyshra.open.app.integration.models.version.migration.MigrationResult;
import com.lyshra.open.app.integration.models.version.migration.MigrationValidationResult;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Abstract base class for migration handlers providing common functionality.
 * Subclasses implement specific migration logic for common scenarios.
 *
 * <p>Design Pattern: Template Method - defines migration skeleton with customizable steps.</p>
 */
@Slf4j
@Getter
public abstract class AbstractMigrationHandler implements IMigrationHandler {

    protected final String workflowId;
    protected final String sourceVersionPattern;
    protected final IWorkflowVersion targetVersion;
    protected final int priority;
    protected final String description;

    private Pattern compiledPattern;

    protected AbstractMigrationHandler(
            String workflowId,
            String sourceVersionPattern,
            IWorkflowVersion targetVersion,
            int priority,
            String description) {
        this.workflowId = workflowId;
        this.sourceVersionPattern = sourceVersionPattern;
        this.targetVersion = targetVersion;
        this.priority = priority;
        this.description = description;
    }

    @Override
    public Mono<IMigrationValidationResult> validate(IMigrationContext context) {
        return Mono.fromCallable(() -> {
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            // Validate version pattern matches
            if (!matchesSourceVersion(context.getSourceVersion())) {
                errors.add("Source version " + context.getSourceVersion().toVersionString() +
                        " does not match pattern " + sourceVersionPattern);
            }

            // Validate target version
            if (!targetVersion.equals(context.getTargetVersion())) {
                errors.add("Target version mismatch: expected " + targetVersion.toVersionString() +
                        " but got " + context.getTargetVersion().toVersionString());
            }

            // Delegate to subclass for specific validation
            doValidate(context, errors, warnings);

            // Determine target step
            String targetStep = determineTargetStep(context);
            boolean atSafePoint = isAtSafePoint(context);

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
                .<IMigrationResult>flatMap(validation -> {
                    if (!validation.isValid()) {
                        IMigrationResult failedValidation = MigrationResult.failed(
                                IMigrationResult.Status.FAILED_VALIDATION,
                                context.getExecutionId(),
                                context.getSourceVersion(),
                                context.getTargetVersion(),
                                new IllegalStateException("Validation failed: " + validation.getErrors()),
                                Duration.between(startTime, Instant.now()),
                                context.getPreMigrationState(),
                                List.of());
                        return Mono.just(failedValidation);
                    }

                    return Mono.fromCallable(() -> {
                        ILyshraOpenAppContext migratedContext = context.getExecutionContext();
                        List<String> warnings = new ArrayList<>(validation.getWarnings());

                        // Apply the specific migration logic
                        doMigrate(context, migratedContext, warnings);

                        String targetStep = validation.getRecommendedTargetStep()
                                .orElse(determineTargetStep(context));

                        Map<String, Object> postMigrationSnapshot = createPostMigrationSnapshot(migratedContext, targetStep);

                        log.info("{} completed: {} -> {} at step {}",
                                getHandlerName(),
                                context.getSourceVersion().toVersionString(),
                                context.getTargetVersion().toVersionString(),
                                targetStep);

                        return (IMigrationResult) MigrationResult.success(
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
                })
                .onErrorResume(error -> {
                    log.error("{} failed for execution [{}]: {}",
                            getHandlerName(), context.getExecutionId(), error.getMessage(), error);
                    IMigrationResult failedResult = MigrationResult.failed(
                            IMigrationResult.Status.FAILED_HANDLER_ERROR,
                            context.getExecutionId(),
                            context.getSourceVersion(),
                            context.getTargetVersion(),
                            error,
                            Duration.between(startTime, Instant.now()),
                            context.getPreMigrationState(),
                            List.of());
                    return Mono.just(failedResult);
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

            // Allow subclasses to perform additional rollback
            doRollback(context, executionContext);

            log.info("{} rollback completed for execution [{}]",
                    getHandlerName(), context.getExecutionId());

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
               matchesSourceVersion(context.getSourceVersion()) &&
               targetVersion.equals(context.getTargetVersion());
    }

    /**
     * Checks if a version matches the source version pattern.
     */
    protected boolean matchesSourceVersion(IWorkflowVersion version) {
        if (sourceVersionPattern == null) {
            return false;
        }

        // Direct match
        if (sourceVersionPattern.equals(version.toVersionString())) {
            return true;
        }

        // Wildcard matches all
        if (sourceVersionPattern.equals("*") || sourceVersionPattern.equals("*.*.*")) {
            return true;
        }

        // Pattern match (e.g., "1.x.x", "1.*.*", "1.0.*")
        if (compiledPattern == null) {
            String regex = sourceVersionPattern
                    .replace(".", "\\.")
                    .replace("x", "\\d+")
                    .replace("*", ".*");
            compiledPattern = Pattern.compile("^" + regex + "$");
        }

        return compiledPattern.matcher(version.toVersionString()).matches();
    }

    /**
     * Creates a post-migration snapshot of the context state.
     */
    protected Map<String, Object> createPostMigrationSnapshot(ILyshraOpenAppContext context, String currentStep) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("data", context.getData());
        snapshot.put("variables", new HashMap<>(context.getVariables()));
        snapshot.put("currentStep", currentStep);
        return snapshot;
    }

    /**
     * Returns the handler name for logging.
     */
    protected String getHandlerName() {
        return getClass().getSimpleName();
    }

    /**
     * Determines the target step after migration.
     * Subclasses can override for custom logic.
     */
    protected String determineTargetStep(IMigrationContext context) {
        return context.getCurrentStepName();
    }

    /**
     * Checks if the current step is a safe migration point.
     * Subclasses can override for custom logic.
     */
    protected boolean isAtSafePoint(IMigrationContext context) {
        return true; // Default handlers are designed to be safe at any point
    }

    /**
     * Performs handler-specific validation.
     * Subclasses should add any errors/warnings to the provided lists.
     */
    protected abstract void doValidate(
            IMigrationContext context,
            List<String> errors,
            List<String> warnings);

    /**
     * Performs the actual migration logic.
     * Subclasses implement this to apply their specific transformations.
     */
    protected abstract void doMigrate(
            IMigrationContext context,
            ILyshraOpenAppContext executionContext,
            List<String> warnings);

    /**
     * Performs any additional rollback logic.
     * Default implementation does nothing - subclasses can override.
     */
    protected void doRollback(IMigrationContext context, ILyshraOpenAppContext executionContext) {
        // Default: no additional rollback needed
    }
}
