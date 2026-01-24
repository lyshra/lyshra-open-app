package com.lyshra.open.app.core.engine.version.migration.rollback;

import com.lyshra.open.app.core.engine.version.IWorkflowExecutionBindingStore;
import com.lyshra.open.app.core.engine.version.impl.InMemoryWorkflowExecutionBindingStoreImpl;
import com.lyshra.open.app.core.engine.version.migration.backup.IMigrationBackupService;
import com.lyshra.open.app.core.engine.version.migration.backup.MigrationBackupServiceImpl;
import com.lyshra.open.app.integration.contract.version.IWorkflowExecutionBinding;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationContext;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationResult;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationStep;
import com.lyshra.open.app.integration.models.version.WorkflowExecutionBinding;
import com.lyshra.open.app.integration.models.version.migration.MigrationAuditEntry;
import com.lyshra.open.app.integration.models.version.migration.MigrationContext;
import com.lyshra.open.app.integration.models.version.migration.MigrationResult;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages rollback operations for workflow migrations.
 * Provides coordinated rollback with step-level granularity and automatic failure recovery.
 *
 * <p>Key capabilities:</p>
 * <ul>
 *   <li>Automatic rollback on migration failure</li>
 *   <li>Step-level rollback for partial migrations</li>
 *   <li>Coordinated rollback across multiple executions</li>
 *   <li>Rollback tracking and audit</li>
 * </ul>
 */
@Slf4j
public class MigrationRollbackManager {

    private static volatile MigrationRollbackManager instance;

    private final IMigrationBackupService backupService;
    private final IWorkflowExecutionBindingStore bindingStore;

    // Track rollback operations in progress
    private final Map<String, RollbackOperation> activeRollbacks = new ConcurrentHashMap<>();

    // Track step execution for rollback
    private final Map<String, Stack<ExecutedStepInfo>> executedStepsStack = new ConcurrentHashMap<>();

    public MigrationRollbackManager() {
        this.backupService = MigrationBackupServiceImpl.getInstance();
        this.bindingStore = InMemoryWorkflowExecutionBindingStoreImpl.getInstance();
    }

    public MigrationRollbackManager(IMigrationBackupService backupService,
                                    IWorkflowExecutionBindingStore bindingStore) {
        this.backupService = backupService;
        this.bindingStore = bindingStore;
    }

    public static MigrationRollbackManager getInstance() {
        if (instance == null) {
            synchronized (MigrationRollbackManager.class) {
                if (instance == null) {
                    instance = new MigrationRollbackManager();
                }
            }
        }
        return instance;
    }

    /**
     * Prepares for migration by creating a backup and initializing rollback tracking.
     *
     * @param binding the execution binding
     * @param context the migration context
     * @return preparation result with backup ID
     */
    public Mono<RollbackPreparation> prepareForMigration(IWorkflowExecutionBinding binding,
                                                          IMigrationContext context) {
        String migrationId = extractMigrationId(context);

        return backupService.createBackup(binding, context)
                .map(backupResult -> {
                    if (!backupResult.success()) {
                        log.warn("Failed to create backup for execution [{}], migration will proceed without backup",
                                binding.getExecutionId());
                        return new RollbackPreparation(
                                migrationId,
                                binding.getExecutionId(),
                                null,
                                false,
                                "Backup creation failed: " + backupResult.errorMessage().orElse("Unknown"));
                    }

                    // Initialize step tracking stack for this migration
                    executedStepsStack.put(migrationId, new Stack<>());

                    log.debug("Prepared rollback for migration [{}] with backup [{}]",
                            migrationId, backupResult.backupId());

                    return new RollbackPreparation(
                            migrationId,
                            binding.getExecutionId(),
                            backupResult.backupId(),
                            true,
                            null);
                });
    }

    /**
     * Records a step execution for potential rollback.
     *
     * @param migrationId the migration identifier
     * @param step the executed step
     * @param result the step result
     */
    public void recordStepExecution(String migrationId, IMigrationStep step,
                                    IMigrationStep.StepResult result) {
        Stack<ExecutedStepInfo> stack = executedStepsStack.get(migrationId);
        if (stack != null && step.supportsRollback()) {
            stack.push(new ExecutedStepInfo(
                    step.getStepId(),
                    step.getName(),
                    step,
                    result,
                    Instant.now()
            ));
            log.trace("Recorded step [{}] for potential rollback in migration [{}]",
                    step.getName(), migrationId);
        }
    }

    /**
     * Performs automatic rollback when a migration fails.
     * Rolls back all executed steps in reverse order.
     *
     * @param context the migration context
     * @param failedResult the failed migration result
     * @return rollback result
     */
    public Mono<RollbackResult> performAutomaticRollback(IMigrationContext context,
                                                          IMigrationResult failedResult) {
        String migrationId = extractMigrationId(context);
        String executionId = context.getExecutionId();

        return Mono.fromCallable(() -> {
            Instant startTime = Instant.now();
            List<MigrationAuditEntry> auditTrail = new ArrayList<>();

            auditTrail.add(MigrationAuditEntry.rollbackInitiated(
                    executionId, migrationId,
                    "Automatic rollback due to: " + failedResult.getErrorMessage().orElse("Migration failure")));

            // Track rollback operation
            RollbackOperation operation = new RollbackOperation(
                    migrationId, executionId, RollbackType.AUTOMATIC, Instant.now());
            activeRollbacks.put(migrationId, operation);

            List<String> rolledBackSteps = new ArrayList<>();
            List<String> failedSteps = new ArrayList<>();

            try {
                // First, try step-level rollback
                Stack<ExecutedStepInfo> steps = executedStepsStack.get(migrationId);
                if (steps != null && !steps.isEmpty()) {
                    while (!steps.isEmpty()) {
                        ExecutedStepInfo stepInfo = steps.pop();
                        try {
                            IMigrationStep.StepResult rollbackResult = stepInfo.step()
                                    .rollback(context, stepInfo.result())
                                    .block();

                            if (rollbackResult != null && rollbackResult.isSuccess()) {
                                rolledBackSteps.add(stepInfo.stepName());
                                log.debug("Rolled back step [{}] in migration [{}]",
                                        stepInfo.stepName(), migrationId);
                            } else {
                                failedSteps.add(stepInfo.stepName());
                                log.warn("Step rollback failed for [{}] in migration [{}]",
                                        stepInfo.stepName(), migrationId);
                            }
                        } catch (Exception e) {
                            failedSteps.add(stepInfo.stepName());
                            log.error("Exception during step rollback [{}]: {}",
                                    stepInfo.stepName(), e.getMessage());
                        }
                    }
                }

                // Restore binding to original state
                restoreBindingState(context);

                // If step rollback had failures, try full restore from backup
                if (!failedSteps.isEmpty()) {
                    log.info("Step rollback had {} failures, attempting full restore from backup",
                            failedSteps.size());
                    IMigrationBackupService.RestoreResult restoreResult =
                            backupService.restoreFromLatestBackup(executionId).block();

                    if (restoreResult != null && restoreResult.success()) {
                        log.info("Full restore from backup successful for execution [{}]", executionId);
                        failedSteps.clear();
                    }
                }

                Duration duration = Duration.between(startTime, Instant.now());
                boolean success = failedSteps.isEmpty();

                auditTrail.add(MigrationAuditEntry.rollbackCompleted(executionId, migrationId, success));

                log.info("Automatic rollback {} for migration [{}]: {} steps rolled back, {} failed",
                        success ? "completed" : "partially completed",
                        migrationId, rolledBackSteps.size(), failedSteps.size());

                return new RollbackResult(
                        migrationId,
                        executionId,
                        success,
                        RollbackType.AUTOMATIC,
                        context.getSourceVersion().toVersionString(),
                        context.getCurrentStepName(),
                        rolledBackSteps,
                        failedSteps,
                        duration,
                        auditTrail,
                        success ? Optional.empty() : Optional.of("Some steps failed to rollback"));

            } finally {
                // Cleanup
                executedStepsStack.remove(migrationId);
                activeRollbacks.remove(migrationId);
            }
        });
    }

    /**
     * Performs manual rollback to a specific backup.
     *
     * @param executionId the execution identifier
     * @param backupId the backup to restore from
     * @param initiatedBy who initiated the rollback
     * @return rollback result
     */
    public Mono<RollbackResult> performManualRollback(String executionId, String backupId,
                                                       String initiatedBy) {
        return backupService.getBackup(backupId)
                .flatMap(optBackup -> {
                    if (optBackup.isEmpty()) {
                        return Mono.just(RollbackResult.failed(
                                null, executionId, "Backup not found: " + backupId));
                    }

                    IMigrationBackupService.MigrationBackup backup = optBackup.get();

                    return backupService.restoreFromBackup(backupId)
                            .map(restoreResult -> {
                                if (restoreResult.success()) {
                                    return new RollbackResult(
                                            backup.migrationId(),
                                            executionId,
                                            true,
                                            RollbackType.MANUAL,
                                            backup.sourceVersion(),
                                            backup.currentStepName(),
                                            restoreResult.restoredComponents(),
                                            List.of(),
                                            restoreResult.duration(),
                                            List.of(MigrationAuditEntry.rollbackCompleted(
                                                    executionId, backup.migrationId(), true)),
                                            Optional.empty());
                                } else {
                                    return RollbackResult.failed(
                                            backup.migrationId(), executionId,
                                            restoreResult.errorMessage().orElse("Restore failed"));
                                }
                            });
                });
    }

    /**
     * Performs coordinated rollback for multiple executions.
     *
     * @param executionIds the execution identifiers
     * @param initiatedBy who initiated the rollback
     * @return rollback results for each execution
     */
    public Flux<RollbackResult> performBulkRollback(List<String> executionIds, String initiatedBy) {
        return Flux.fromIterable(executionIds)
                .flatMap(executionId ->
                        backupService.getLatestBackup(executionId)
                                .flatMap(optBackup -> {
                                    if (optBackup.isEmpty()) {
                                        return Mono.just(RollbackResult.failed(
                                                null, executionId, "No backup found"));
                                    }
                                    return performManualRollback(executionId,
                                            optBackup.get().backupId(), initiatedBy);
                                })
                );
    }

    /**
     * Checks if a rollback is currently in progress for a migration.
     *
     * @param migrationId the migration identifier
     * @return true if rollback is in progress
     */
    public boolean isRollbackInProgress(String migrationId) {
        return activeRollbacks.containsKey(migrationId);
    }

    /**
     * Gets the status of an active rollback operation.
     *
     * @param migrationId the migration identifier
     * @return the operation status if in progress
     */
    public Optional<RollbackOperation> getRollbackStatus(String migrationId) {
        return Optional.ofNullable(activeRollbacks.get(migrationId));
    }

    /**
     * Cleans up rollback resources for a completed migration.
     *
     * @param migrationId the migration identifier
     */
    public void cleanupMigration(String migrationId) {
        executedStepsStack.remove(migrationId);
        activeRollbacks.remove(migrationId);
    }

    // ==================== Private Methods ====================

    private void restoreBindingState(IMigrationContext context) {
        String executionId = context.getExecutionId();

        Optional<IWorkflowExecutionBinding> bindingOpt = bindingStore.findById(executionId);
        if (bindingOpt.isEmpty()) {
            log.warn("Cannot restore binding state - execution not found: {}", executionId);
            return;
        }

        IWorkflowExecutionBinding currentBinding = bindingOpt.get();

        WorkflowExecutionBinding restoredBinding = WorkflowExecutionBinding.builder()
                .executionId(executionId)
                .workflowIdentifier(currentBinding.getWorkflowIdentifier())
                .boundVersion(context.getSourceVersion())
                .state(IWorkflowExecutionBinding.ExecutionState.RUNNING)
                .currentStepName(context.getCurrentStepName())
                .startedAt(currentBinding.getStartedAt())
                .migrationStrategy(currentBinding.getMigrationStrategy())
                .migrationCount(currentBinding.getMigrationCount())
                .build();

        bindingStore.update(restoredBinding);
        log.debug("Restored binding state for execution [{}] to version [{}]",
                executionId, context.getSourceVersion().toVersionString());
    }

    /**
     * Extracts the migration ID from the context.
     * MigrationContext has the getMigrationId method, while IMigrationContext does not.
     */
    private String extractMigrationId(IMigrationContext context) {
        if (context instanceof MigrationContext mc) {
            return mc.getMigrationId();
        }
        return UUID.randomUUID().toString();
    }

    // ==================== Records ====================

    /**
     * Result of rollback preparation.
     */
    public record RollbackPreparation(
            String migrationId,
            String executionId,
            String backupId,
            boolean ready,
            String warning
    ) {
        public boolean hasBackup() {
            return backupId != null;
        }
    }

    /**
     * Information about an executed step for rollback.
     */
    public record ExecutedStepInfo(
            String stepId,
            String stepName,
            IMigrationStep step,
            IMigrationStep.StepResult result,
            Instant executedAt
    ) {}

    /**
     * Type of rollback operation.
     */
    public enum RollbackType {
        AUTOMATIC,  // Triggered by migration failure
        MANUAL,     // Triggered by user/admin
        SCHEDULED   // Triggered by policy (e.g., time-based)
    }

    /**
     * Tracks an active rollback operation.
     */
    public record RollbackOperation(
            String migrationId,
            String executionId,
            RollbackType type,
            Instant startedAt
    ) {}

    /**
     * Result of a rollback operation.
     */
    public record RollbackResult(
            String migrationId,
            String executionId,
            boolean success,
            RollbackType type,
            String restoredVersion,
            String restoredStep,
            List<String> rolledBackSteps,
            List<String> failedSteps,
            Duration duration,
            List<MigrationAuditEntry> auditTrail,
            Optional<String> errorMessage
    ) {
        public static RollbackResult failed(String migrationId, String executionId, String error) {
            return new RollbackResult(
                    migrationId, executionId, false, RollbackType.MANUAL,
                    null, null, List.of(), List.of(), Duration.ZERO, List.of(),
                    Optional.of(error));
        }

        public boolean isPartiallySuccessful() {
            return !success && !rolledBackSteps.isEmpty();
        }
    }
}
