package com.lyshra.open.app.core.engine.version.migration.impl;

import com.lyshra.open.app.core.engine.version.IWorkflowExecutionBindingStore;
import com.lyshra.open.app.core.engine.version.IWorkflowVersionRegistryService;
import com.lyshra.open.app.core.engine.version.impl.InMemoryWorkflowExecutionBindingStoreImpl;
import com.lyshra.open.app.core.engine.version.impl.WorkflowVersionRegistryImpl;
import com.lyshra.open.app.core.engine.version.migration.IWorkflowMigrationExecutor;
import com.lyshra.open.app.core.engine.version.migration.backup.IMigrationBackupService;
import com.lyshra.open.app.core.engine.version.migration.backup.MigrationBackupServiceImpl;
import com.lyshra.open.app.core.engine.version.migration.rollback.MigrationRollbackManager;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.version.IVersionCompatibility;
import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowExecutionBinding;
import com.lyshra.open.app.integration.contract.version.IWorkflowMigrationHints;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationAuditEntry;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationContext;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationHandler;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationResult;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationStrategy;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationValidationResult;
import com.lyshra.open.app.integration.models.version.WorkflowExecutionBinding;
import com.lyshra.open.app.integration.models.version.migration.MigrationAuditEntry;
import com.lyshra.open.app.integration.models.version.migration.MigrationContext;
import com.lyshra.open.app.integration.models.version.migration.MigrationResult;
import com.lyshra.open.app.integration.models.version.migration.MigrationValidationResult;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Implementation of workflow migration executor.
 * Handles validation, transformation, and orchestration of migrations.
 *
 * <p>Design Patterns:</p>
 * <ul>
 *   <li>Strategy: Pluggable migration handlers</li>
 *   <li>Template Method: Standard migration flow with customization points</li>
 *   <li>Chain of Responsibility: Handler selection by priority</li>
 * </ul>
 */
@Slf4j
public class WorkflowMigrationExecutorImpl implements IWorkflowMigrationExecutor {

    private final IWorkflowVersionRegistryService versionRegistry;
    private final IWorkflowExecutionBindingStore bindingStore;
    private final Map<String, List<IMigrationHandler>> handlers;
    private final IMigrationBackupService backupService;
    private final MigrationRollbackManager rollbackManager;

    // Configuration for automatic rollback
    private volatile boolean autoRollbackEnabled = true;

    private WorkflowMigrationExecutorImpl() {
        this.versionRegistry = WorkflowVersionRegistryImpl.getInstance();
        this.bindingStore = InMemoryWorkflowExecutionBindingStoreImpl.getInstance();
        this.handlers = new ConcurrentHashMap<>();
        this.backupService = MigrationBackupServiceImpl.getInstance();
        this.rollbackManager = MigrationRollbackManager.getInstance();
    }

    /**
     * Enables or disables automatic rollback on migration failure.
     *
     * @param enabled true to enable automatic rollback
     */
    public void setAutoRollbackEnabled(boolean enabled) {
        this.autoRollbackEnabled = enabled;
        log.info("Automatic rollback {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Checks if automatic rollback is enabled.
     *
     * @return true if automatic rollback is enabled
     */
    public boolean isAutoRollbackEnabled() {
        return autoRollbackEnabled;
    }

    private static final class SingletonHelper {
        private static final WorkflowMigrationExecutorImpl INSTANCE = new WorkflowMigrationExecutorImpl();
    }

    public static IWorkflowMigrationExecutor getInstance() {
        return SingletonHelper.INSTANCE;
    }

    @Override
    public Mono<IMigrationValidationResult> validate(
            IWorkflowExecutionBinding binding,
            IVersionedWorkflow targetWorkflow) {

        return Mono.fromCallable(() -> {
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            IWorkflowVersion sourceVersion = binding.getBoundVersion();
            IWorkflowVersion targetVersion = targetWorkflow.getVersion();

            // Check version ordering
            if (targetVersion.compareTo(sourceVersion) <= 0) {
                errors.add("Target version must be greater than source version");
            }

            // Check compatibility
            IVersionCompatibility compatibility = targetWorkflow.getCompatibility();
            if (compatibility != null && !compatibility.isCompatibleWith(sourceVersion)) {
                errors.add("Target version is not compatible with source version " + sourceVersion.toVersionString());
            }

            // Check migration strategy
            if (binding.getMigrationStrategy() == IMigrationStrategy.FROZEN) {
                errors.add("Execution has FROZEN migration strategy - migration not allowed");
            }

            // Check if at safe migration point
            boolean atSafePoint = false;
            String recommendedStep = binding.getCurrentStepName();
            Optional<IWorkflowMigrationHints> hintsOpt = targetWorkflow.getMigrationHints();

            if (hintsOpt.isPresent()) {
                IWorkflowMigrationHints hints = hintsOpt.get();

                // Check blocked points
                if (hints.getBlockedMigrationPoints().contains(binding.getCurrentStepName())) {
                    errors.add("Current step is a blocked migration point: " + binding.getCurrentStepName());
                }

                // Check safe points
                atSafePoint = hints.getSafeMigrationPoints().contains(binding.getCurrentStepName());
                if (!atSafePoint && binding.getMigrationStrategy() == IMigrationStrategy.OPPORTUNISTIC) {
                    warnings.add("Current step is not a safe migration point - migration may cause data inconsistency");
                }

                // Get recommended step mapping
                Optional<String> suggestedStep = hints.getSuggestedMigrationStep(binding.getCurrentStepName());
                if (suggestedStep.isPresent()) {
                    recommendedStep = suggestedStep.get();
                }
            }

            // Validate target step exists
            if (!targetWorkflow.getSteps().containsKey(recommendedStep)) {
                // Try to find a suitable step
                if (targetWorkflow.getSteps().containsKey(binding.getCurrentStepName())) {
                    recommendedStep = binding.getCurrentStepName();
                } else if (targetWorkflow.getSteps().containsKey(targetWorkflow.getStartStep())) {
                    warnings.add("Current step does not exist in target version - will restart from beginning");
                    recommendedStep = targetWorkflow.getStartStep();
                } else {
                    errors.add("Cannot find suitable step in target workflow");
                }
            }

            // Calculate risk level
            double riskLevel = 0.0;
            if (compatibility != null && compatibility.requiresContextMigration()) {
                riskLevel += 0.3;
                warnings.add("Context data migration required");
            }
            if (compatibility != null && compatibility.requiresVariableMigration()) {
                riskLevel += 0.2;
                warnings.add("Variable migration required");
            }
            if (!atSafePoint) {
                riskLevel += 0.2;
            }
            if (!targetWorkflow.getRemovedSteps().isEmpty()) {
                riskLevel += 0.1;
                warnings.add("Target version has removed steps: " + targetWorkflow.getRemovedSteps());
            }

            // Build result
            if (!errors.isEmpty()) {
                return MigrationValidationResult.invalid(errors);
            }

            if (riskLevel > 0.5) {
                return MigrationValidationResult.requiresReview(warnings, riskLevel);
            }

            if (!warnings.isEmpty()) {
                return MigrationValidationResult.validWithWarnings(recommendedStep, atSafePoint, warnings);
            }

            return MigrationValidationResult.valid(recommendedStep, atSafePoint);
        });
    }

    @Override
    public Mono<IMigrationResult> migrate(IMigrationContext context) {
        if (context.isDryRun()) {
            return dryRun(context);
        }

        Instant startTime = Instant.now();
        List<IMigrationAuditEntry> auditTrail = new ArrayList<>();
        String executionId = context.getExecutionId();
        String migrationId = ((MigrationContext) context).getMigrationId();

        // Get binding for backup creation
        Optional<IWorkflowExecutionBinding> bindingOpt = bindingStore.findById(executionId);
        if (bindingOpt.isEmpty()) {
            return Mono.just(MigrationResult.failed(
                    IMigrationResult.Status.FAILED_VALIDATION,
                    executionId,
                    context.getSourceVersion(),
                    context.getTargetVersion(),
                    new IllegalStateException("Execution binding not found: " + executionId),
                    Duration.ZERO,
                    context.getPreMigrationState(),
                    auditTrail));
        }

        // Prepare for rollback by creating backup
        return rollbackManager.prepareForMigration(bindingOpt.get(), context)
                .flatMap(preparation -> {
                    if (preparation.hasBackup()) {
                        auditTrail.add(MigrationAuditEntry.builder()
                                .entryType(IMigrationAuditEntry.EntryType.CONTEXT_SNAPSHOT_TAKEN)
                                .executionId(executionId)
                                .migrationId(migrationId)
                                .message("Backup created: " + preparation.backupId())
                                .details(Map.of("backupId", preparation.backupId()))
                                .build());
                    } else if (preparation.warning() != null) {
                        log.warn("Migration proceeding without backup: {}", preparation.warning());
                    }

                    return executeMigrationWithRollback(context, auditTrail, startTime, preparation.backupId());
                });
    }

    /**
     * Executes migration with automatic rollback support on failure.
     */
    private Mono<IMigrationResult> executeMigrationWithRollback(
            IMigrationContext context,
            List<IMigrationAuditEntry> auditTrail,
            Instant startTime,
            String backupId) {

        String executionId = context.getExecutionId();
        String migrationId = ((MigrationContext) context).getMigrationId();

        return Mono.fromCallable(() -> {
            // Record initiation
            Map<String, Object> initDetails = new HashMap<>();
            initDetails.put("sourceVersion", context.getSourceVersion().toVersionString());
            initDetails.put("targetVersion", context.getTargetVersion().toVersionString());
            initDetails.put("currentStep", context.getCurrentStepName());
            initDetails.put("backupId", backupId);
            initDetails.put("autoRollbackEnabled", autoRollbackEnabled);
            auditTrail.add(MigrationAuditEntry.initiated(executionId, migrationId, context.getInitiatedBy(), initDetails));

            // Get target workflow
            IVersionedWorkflow targetWorkflow = versionRegistry.getVersion(
                    context.getWorkflowId(), context.getTargetVersion())
                    .orElseThrow(() -> new IllegalStateException(
                            "Target workflow version not found: " + context.getTargetVersion().toVersionString()));

            // Find and invoke handler
            Optional<IMigrationHandler> handlerOpt = findHandler(context);
            if (handlerOpt.isPresent()) {
                IMigrationHandler handler = handlerOpt.get();
                auditTrail.add(MigrationAuditEntry.builder()
                        .entryType(IMigrationAuditEntry.EntryType.MIGRATION_HANDLER_INVOKED)
                        .executionId(executionId)
                        .migrationId(migrationId)
                        .message("Custom handler invoked: " + handler.getClass().getSimpleName())
                        .build());
                return handler.migrate(context).block();
            }

            // Default migration logic
            return executeDefaultMigration(context, targetWorkflow, auditTrail, startTime);
        })
        .onErrorResume(error -> {
            log.error("Migration failed for execution [{}]: {}", executionId, error.getMessage(), error);

            IMigrationResult failedResult = MigrationResult.failed(
                    IMigrationResult.Status.FAILED_HANDLER_ERROR,
                    executionId,
                    context.getSourceVersion(),
                    context.getTargetVersion(),
                    error,
                    Duration.between(startTime, Instant.now()),
                    context.getPreMigrationState(),
                    auditTrail);

            // Perform automatic rollback if enabled
            if (autoRollbackEnabled) {
                log.info("Initiating automatic rollback for failed migration [{}]", migrationId);
                return performAutomaticRollbackOnFailure(context, failedResult, auditTrail);
            }

            return Mono.just(failedResult);
        })
        .doFinally(signal -> {
            // Cleanup rollback resources
            rollbackManager.cleanupMigration(migrationId);
        });
    }

    /**
     * Performs automatic rollback when migration fails.
     */
    private Mono<IMigrationResult> performAutomaticRollbackOnFailure(
            IMigrationContext context,
            IMigrationResult failedResult,
            List<IMigrationAuditEntry> auditTrail) {

        return rollbackManager.performAutomaticRollback(context, failedResult)
                .map(rollbackResult -> {
                    if (rollbackResult.success()) {
                        log.info("Automatic rollback successful for execution [{}]", context.getExecutionId());
                        auditTrail.addAll(rollbackResult.auditTrail());

                        // Return a result indicating migration failed but rollback succeeded
                        return MigrationResult.builder()
                                .status(IMigrationResult.Status.ROLLED_BACK)
                                .executionId(context.getExecutionId())
                                .sourceVersion(context.getSourceVersion())
                                .targetVersion(context.getTargetVersion())
                                .targetStepName(rollbackResult.restoredStep())
                                .preMigrationSnapshot(context.getPreMigrationState())
                                .auditTrail(auditTrail)
                                .error(failedResult.getError().orElse(null))
                                .errorMessage("Migration failed and was automatically rolled back: " +
                                        failedResult.getErrorMessage().orElse("Unknown error"))
                                .duration(rollbackResult.duration())
                                .build();
                    } else {
                        log.error("Automatic rollback failed for execution [{}]: {}",
                                context.getExecutionId(), rollbackResult.errorMessage().orElse("Unknown"));
                        auditTrail.addAll(rollbackResult.auditTrail());

                        // Return original failure with rollback failure info
                        return MigrationResult.builder()
                                .status(IMigrationResult.Status.FAILED_HANDLER_ERROR)
                                .executionId(context.getExecutionId())
                                .sourceVersion(context.getSourceVersion())
                                .targetVersion(context.getTargetVersion())
                                .preMigrationSnapshot(context.getPreMigrationState())
                                .auditTrail(auditTrail)
                                .error(failedResult.getError().orElse(null))
                                .errorMessage("Migration failed and rollback also failed: " +
                                        failedResult.getErrorMessage().orElse("Unknown") +
                                        ". Rollback error: " + rollbackResult.errorMessage().orElse("Unknown"))
                                .duration(rollbackResult.duration())
                                .build();
                    }
                });
    }

    private IMigrationResult executeDefaultMigration(
            IMigrationContext context,
            IVersionedWorkflow targetWorkflow,
            List<IMigrationAuditEntry> auditTrail,
            Instant startTime) {

        String executionId = context.getExecutionId();
        String migrationId = ((MigrationContext) context).getMigrationId();

        // Validate
        auditTrail.add(MigrationAuditEntry.validationStarted(executionId, migrationId));

        IWorkflowExecutionBinding binding = bindingStore.findById(executionId)
                .orElseThrow(() -> new IllegalStateException("Execution binding not found: " + executionId));

        IMigrationValidationResult validation = validate(binding, targetWorkflow).block();

        Map<String, Object> validationDetails = new HashMap<>();
        validationDetails.put("outcome", validation.getOutcome().name());
        validationDetails.put("errors", validation.getErrors());
        validationDetails.put("warnings", validation.getWarnings());
        auditTrail.add(MigrationAuditEntry.validationCompleted(executionId, migrationId, validation.isValid(), validationDetails));

        if (!validation.isValid()) {
            return MigrationResult.failed(
                    IMigrationResult.Status.FAILED_VALIDATION,
                    executionId,
                    context.getSourceVersion(),
                    context.getTargetVersion(),
                    new IllegalStateException("Validation failed: " + validation.getErrors()),
                    Duration.between(startTime, Instant.now()),
                    context.getPreMigrationState(),
                    auditTrail);
        }

        // Snapshot context
        auditTrail.add(MigrationAuditEntry.contextSnapshotTaken(executionId, migrationId, context.getPreMigrationState()));

        // Transform context if needed
        ILyshraOpenAppContext migratedContext = context.getExecutionContext();
        IVersionCompatibility compatibility = targetWorkflow.getCompatibility();

        if (compatibility != null && (compatibility.requiresContextMigration() || compatibility.requiresVariableMigration())) {
            auditTrail.add(MigrationAuditEntry.contextTransformationStarted(executionId, migrationId));

            Optional<IWorkflowMigrationHints> hintsOpt = targetWorkflow.getMigrationHints();
            if (hintsOpt.isPresent()) {
                IWorkflowMigrationHints hints = hintsOpt.get();

                // Apply variable mappings
                if (compatibility.requiresVariableMigration()) {
                    hints.getVariableMappings().forEach((oldName, newName) -> {
                        if (migratedContext.hasVariable(oldName)) {
                            Object value = migratedContext.getVariable(oldName);
                            migratedContext.removeVariable(oldName);
                            migratedContext.addVariable(newName, value);
                        }
                    });
                }
            }

            Map<String, Object> transformDetails = new HashMap<>();
            transformDetails.put("variablesTransformed", compatibility.requiresVariableMigration());
            transformDetails.put("contextTransformed", compatibility.requiresContextMigration());
            auditTrail.add(MigrationAuditEntry.contextTransformationCompleted(executionId, migrationId, transformDetails));
        }

        // Apply step mapping
        String targetStepName = validation.getRecommendedTargetStep().orElse(context.getCurrentStepName());
        auditTrail.add(MigrationAuditEntry.stepMappingApplied(executionId, migrationId, context.getCurrentStepName(), targetStepName));

        // Update binding
        WorkflowExecutionBinding migratedBinding = ((WorkflowExecutionBinding) binding)
                .migrateTo(context.getTargetVersion(), targetStepName);
        bindingStore.update(migratedBinding);

        // Record completion
        Map<String, Object> postMigrationSnapshot = new HashMap<>();
        postMigrationSnapshot.put("data", migratedContext.getData());
        postMigrationSnapshot.put("variables", migratedContext.getVariables());
        postMigrationSnapshot.put("currentStep", targetStepName);

        Map<String, Object> resultSummary = new HashMap<>();
        resultSummary.put("targetVersion", context.getTargetVersion().toVersionString());
        resultSummary.put("targetStep", targetStepName);
        resultSummary.put("migrationCount", migratedBinding.getMigrationCount());
        auditTrail.add(MigrationAuditEntry.completed(executionId, migrationId, resultSummary));

        log.info("Migration completed for execution [{}]: {} -> {} at step [{}]",
                executionId,
                context.getSourceVersion().toVersionString(),
                context.getTargetVersion().toVersionString(),
                targetStepName);

        return MigrationResult.success(
                executionId,
                context.getSourceVersion(),
                context.getTargetVersion(),
                migratedContext,
                targetStepName,
                Duration.between(startTime, Instant.now()),
                context.getPreMigrationState(),
                postMigrationSnapshot,
                auditTrail);
    }

    @Override
    public Mono<IMigrationResult> dryRun(IMigrationContext context) {
        return Mono.fromCallable(() -> {
            IVersionedWorkflow targetWorkflow = versionRegistry.getVersion(
                    context.getWorkflowId(), context.getTargetVersion())
                    .orElseThrow(() -> new IllegalStateException(
                            "Target workflow version not found: " + context.getTargetVersion().toVersionString()));

            IWorkflowExecutionBinding binding = bindingStore.findById(context.getExecutionId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Execution binding not found: " + context.getExecutionId()));

            IMigrationValidationResult validation = validate(binding, targetWorkflow).block();
            List<String> warnings = new ArrayList<>(validation.getWarnings());

            // Simulate transformations
            Map<String, Object> simulatedPostMigration = new HashMap<>(context.getPreMigrationState());
            Optional<IWorkflowMigrationHints> hintsOpt = targetWorkflow.getMigrationHints();
            if (hintsOpt.isPresent()) {
                IWorkflowMigrationHints hints = hintsOpt.get();
                if (!hints.getVariableMappings().isEmpty()) {
                    warnings.add("Variable mappings will be applied: " + hints.getVariableMappings());
                }
                if (!hints.getContextFieldMappings().isEmpty()) {
                    warnings.add("Context field mappings will be applied: " + hints.getContextFieldMappings());
                }
            }

            String targetStep = validation.getRecommendedTargetStep().orElse(context.getCurrentStepName());
            simulatedPostMigration.put("currentStep", targetStep);

            return MigrationResult.dryRunComplete(
                    context.getExecutionId(),
                    context.getSourceVersion(),
                    context.getTargetVersion(),
                    targetStep,
                    context.getPreMigrationState(),
                    simulatedPostMigration,
                    warnings);
        });
    }

    @Override
    public Mono<IMigrationResult> rollback(IMigrationContext context, IMigrationResult failedResult) {
        String executionId = context.getExecutionId();
        String migrationId = ((MigrationContext) context).getMigrationId();

        log.info("Initiating rollback for execution [{}] migration [{}]", executionId, migrationId);

        // Try to restore from backup first
        return backupService.getLatestBackup(executionId)
                .flatMap(optBackup -> {
                    if (optBackup.isPresent()) {
                        log.info("Found backup [{}] for execution [{}], restoring from backup",
                                optBackup.get().backupId(), executionId);
                        return rollbackFromBackup(context, failedResult, optBackup.get().backupId());
                    } else {
                        log.info("No backup found for execution [{}], performing manual rollback", executionId);
                        return rollbackManually(context, failedResult);
                    }
                });
    }

    /**
     * Performs rollback using a backup snapshot.
     */
    private Mono<IMigrationResult> rollbackFromBackup(
            IMigrationContext context,
            IMigrationResult failedResult,
            String backupId) {

        String executionId = context.getExecutionId();
        String migrationId = ((MigrationContext) context).getMigrationId();
        List<IMigrationAuditEntry> auditTrail = new ArrayList<>();

        auditTrail.add(MigrationAuditEntry.rollbackInitiated(executionId, migrationId,
                "Rollback from backup: " + backupId));

        return backupService.restoreFromBackup(backupId)
                .map(restoreResult -> {
                    if (restoreResult.success()) {
                        auditTrail.add(MigrationAuditEntry.rollbackCompleted(executionId, migrationId, true));
                        log.info("Rollback from backup completed for execution [{}]", executionId);

                        return MigrationResult.builder()
                                .status(IMigrationResult.Status.ROLLED_BACK)
                                .executionId(executionId)
                                .sourceVersion(context.getSourceVersion())
                                .targetVersion(context.getTargetVersion())
                                .targetStepName(restoreResult.restoredStep())
                                .preMigrationSnapshot(context.getPreMigrationState())
                                .auditTrail(auditTrail)
                                .duration(restoreResult.duration())
                                .build();
                    } else {
                        auditTrail.add(MigrationAuditEntry.rollbackCompleted(executionId, migrationId, false));
                        log.error("Rollback from backup failed for execution [{}]: {}",
                                executionId, restoreResult.errorMessage().orElse("Unknown"));

                        return MigrationResult.failed(
                                IMigrationResult.Status.FAILED_HANDLER_ERROR,
                                executionId,
                                context.getSourceVersion(),
                                context.getTargetVersion(),
                                new RuntimeException("Rollback failed: " + restoreResult.errorMessage().orElse("Unknown")),
                                restoreResult.duration(),
                                context.getPreMigrationState(),
                                auditTrail);
                    }
                });
    }

    /**
     * Performs manual rollback without backup (fallback method).
     */
    private Mono<IMigrationResult> rollbackManually(IMigrationContext context, IMigrationResult failedResult) {
        return Mono.fromCallable(() -> {
            String executionId = context.getExecutionId();
            String migrationId = ((MigrationContext) context).getMigrationId();
            List<IMigrationAuditEntry> auditTrail = new ArrayList<>();

            auditTrail.add(MigrationAuditEntry.rollbackInitiated(executionId, migrationId,
                    failedResult.getErrorMessage().orElse("Unknown error")));

            try {
                // Restore binding to original state
                IWorkflowExecutionBinding binding = bindingStore.findById(executionId)
                        .orElseThrow(() -> new IllegalStateException("Execution binding not found"));

                WorkflowExecutionBinding restoredBinding = WorkflowExecutionBinding.builder()
                        .executionId(executionId)
                        .workflowIdentifier(binding.getWorkflowIdentifier())
                        .boundVersion(context.getSourceVersion())
                        .state(IWorkflowExecutionBinding.ExecutionState.RUNNING)
                        .currentStepName(context.getCurrentStepName())
                        .startedAt(binding.getStartedAt())
                        .migrationStrategy(binding.getMigrationStrategy())
                        .migrationCount(binding.getMigrationCount())
                        .build();

                bindingStore.update(restoredBinding);

                // Restore context
                ILyshraOpenAppContext restoredContext = context.getExecutionContext();
                Object originalData = context.getPreMigrationState().get("data");
                if (originalData != null) {
                    restoredContext.setData(originalData);
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> originalVariables = (Map<String, Object>) context.getPreMigrationState().get("variables");
                if (originalVariables != null) {
                    restoredContext.setVariables(new HashMap<>(originalVariables));
                }

                auditTrail.add(MigrationAuditEntry.rollbackCompleted(executionId, migrationId, true));

                log.info("Manual rollback completed for execution [{}]", executionId);

                return MigrationResult.builder()
                        .status(IMigrationResult.Status.ROLLED_BACK)
                        .executionId(executionId)
                        .sourceVersion(context.getSourceVersion())
                        .targetVersion(context.getTargetVersion())
                        .migratedContext(restoredContext)
                        .targetStepName(context.getCurrentStepName())
                        .preMigrationSnapshot(context.getPreMigrationState())
                        .auditTrail(auditTrail)
                        .duration(Duration.ZERO)
                        .build();

            } catch (Exception e) {
                auditTrail.add(MigrationAuditEntry.rollbackCompleted(executionId, migrationId, false));
                log.error("Manual rollback failed for execution [{}]: {}", executionId, e.getMessage(), e);

                return MigrationResult.failed(
                        IMigrationResult.Status.FAILED_HANDLER_ERROR,
                        executionId,
                        context.getSourceVersion(),
                        context.getTargetVersion(),
                        e,
                        Duration.ZERO,
                        context.getPreMigrationState(),
                        auditTrail);
            }
        });
    }

    @Override
    public void registerHandler(IMigrationHandler handler) {
        String workflowId = handler.getWorkflowId();
        handlers.computeIfAbsent(workflowId, k -> new ArrayList<>())
                .add(handler);
        // Sort by priority descending
        handlers.get(workflowId).sort(Comparator.comparingInt(IMigrationHandler::getPriority).reversed());
        log.info("Registered migration handler for workflow [{}] source pattern [{}] target [{}]",
                workflowId, handler.getSourceVersionPattern(), handler.getTargetVersion().toVersionString());
    }

    @Override
    public void unregisterHandler(String workflowId, String sourceVersionPattern) {
        List<IMigrationHandler> workflowHandlers = handlers.get(workflowId);
        if (workflowHandlers != null) {
            workflowHandlers.removeIf(h -> h.getSourceVersionPattern().equals(sourceVersionPattern));
        }
    }

    @Override
    public Collection<IMigrationHandler> getHandlers(String workflowId) {
        return handlers.getOrDefault(workflowId, List.of());
    }

    @Override
    public Optional<IMigrationHandler> findHandler(IMigrationContext context) {
        List<IMigrationHandler> workflowHandlers = handlers.get(context.getWorkflowId());
        if (workflowHandlers == null || workflowHandlers.isEmpty()) {
            return Optional.empty();
        }

        return workflowHandlers.stream()
                .filter(h -> h.canHandle(context))
                .filter(h -> matchesVersionPattern(context.getSourceVersion(), h.getSourceVersionPattern()))
                .findFirst();
    }

    private boolean matchesVersionPattern(IWorkflowVersion version, String pattern) {
        if (pattern == null || pattern.equals("*") || pattern.equals("*.*.*")) {
            return true;
        }

        String versionStr = version.toVersionString();
        String regexPattern = pattern
                .replace(".", "\\.")
                .replace("x", "\\d+")
                .replace("*", ".*");

        return Pattern.matches(regexPattern, versionStr);
    }
}
