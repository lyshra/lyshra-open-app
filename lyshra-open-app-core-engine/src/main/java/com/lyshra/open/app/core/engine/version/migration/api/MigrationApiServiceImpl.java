package com.lyshra.open.app.core.engine.version.migration.api;

import com.lyshra.open.app.core.engine.version.IWorkflowExecutionBindingStore;
import com.lyshra.open.app.core.engine.version.IWorkflowVersionRegistryService;
import com.lyshra.open.app.core.engine.version.impl.InMemoryWorkflowExecutionBindingStoreImpl;
import com.lyshra.open.app.core.engine.version.impl.WorkflowVersionRegistryImpl;
import com.lyshra.open.app.core.engine.version.migration.IWorkflowMigrationExecutor;
import com.lyshra.open.app.core.engine.version.migration.impl.WorkflowMigrationExecutorImpl;
import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowExecutionBinding;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationContext;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationResult;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationStrategy;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationValidationResult;
import com.lyshra.open.app.integration.models.version.WorkflowVersion;
import com.lyshra.open.app.integration.models.version.migration.MigrationContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Implementation of IMigrationApiService providing controlled migration operations.
 * Supports single, bulk, and targeted migrations with validation and rollback.
 */
@Slf4j
public class MigrationApiServiceImpl implements IMigrationApiService {

    private final IWorkflowVersionRegistryService versionRegistry;
    private final IWorkflowExecutionBindingStore bindingStore;
    private final IWorkflowMigrationExecutor migrationExecutor;

    // Track active bulk migration jobs
    private final Map<String, BulkMigrationJob> activeJobs = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<MigrationProgressEvent>> jobProgressSinks = new ConcurrentHashMap<>();

    // Migration history tracking
    private final Map<String, List<MigrationHistoryEntry>> migrationHistory = new ConcurrentHashMap<>();

    public MigrationApiServiceImpl() {
        this.versionRegistry = WorkflowVersionRegistryImpl.getInstance();
        this.bindingStore = InMemoryWorkflowExecutionBindingStoreImpl.getInstance();
        this.migrationExecutor = WorkflowMigrationExecutorImpl.getInstance();
    }

    public MigrationApiServiceImpl(
            IWorkflowVersionRegistryService versionRegistry,
            IWorkflowExecutionBindingStore bindingStore,
            IWorkflowMigrationExecutor migrationExecutor) {
        this.versionRegistry = versionRegistry;
        this.bindingStore = bindingStore;
        this.migrationExecutor = migrationExecutor;
    }

    // ==================== Single Migration Operations ====================

    @Override
    public Mono<MigrationResponse> migrateExecution(SingleMigrationRequest request) {
        return Mono.defer(() -> {
            String migrationId = UUID.randomUUID().toString();

            // Find binding
            Optional<IWorkflowExecutionBinding> bindingOpt = bindingStore.findById(request.executionId());
            if (bindingOpt.isEmpty()) {
                return Mono.just(MigrationResponse.failed(
                        request.executionId(), migrationId, "unknown", request.targetVersion(),
                        "Execution not found: " + request.executionId()));
            }

            IWorkflowExecutionBinding binding = bindingOpt.get();
            String workflowId = binding.getWorkflowIdentifier().getWorkflowName();

            // Parse target version
            IWorkflowVersion targetVersion;
            try {
                targetVersion = WorkflowVersion.parse(request.targetVersion());
            } catch (Exception e) {
                return Mono.just(MigrationResponse.failed(
                        request.executionId(), migrationId,
                        binding.getBoundVersion().toVersionString(), request.targetVersion(),
                        "Invalid target version format: " + request.targetVersion()));
            }

            // Get target workflow
            Optional<IVersionedWorkflow> targetWorkflowOpt = versionRegistry.getVersion(workflowId, targetVersion);
            if (targetWorkflowOpt.isEmpty()) {
                return Mono.just(MigrationResponse.failed(
                        request.executionId(), migrationId,
                        binding.getBoundVersion().toVersionString(), request.targetVersion(),
                        "Target workflow version not found: " + request.targetVersion()));
            }

            // Create migration context
            IMigrationContext context = createMigrationContext(binding, targetVersion, request.initiatedBy(),
                    request.reason().orElse(null), false, migrationId);

            // Execute migration
            return migrationExecutor.migrate(context)
                    .map(result -> {
                        if (result.getStatus() == IMigrationResult.Status.SUCCESS) {
                            // Record history
                            recordMigrationHistory(request.executionId(), migrationId,
                                    binding.getBoundVersion(), targetVersion,
                                    binding.getCurrentStepName(), result.getTargetStepName().orElse(null),
                                    MigrationStatus.SUCCESS, request.initiatedBy(), null);

                            return MigrationResponse.success(
                                    request.executionId(), migrationId,
                                    binding.getBoundVersion().toVersionString(),
                                    targetVersion.toVersionString(),
                                    result.getTargetStepName().orElse(null),
                                    List.of());
                        } else {
                            recordMigrationHistory(request.executionId(), migrationId,
                                    binding.getBoundVersion(), targetVersion,
                                    binding.getCurrentStepName(), null,
                                    MigrationStatus.FAILED, request.initiatedBy(), null);

                            return MigrationResponse.failed(
                                    request.executionId(), migrationId,
                                    binding.getBoundVersion().toVersionString(),
                                    targetVersion.toVersionString(),
                                    result.getErrorMessage().orElse("Migration failed"));
                        }
                    });
        });
    }

    @Override
    public Mono<ValidationResponse> validateExecution(SingleMigrationRequest request) {
        return Mono.defer(() -> {
            Optional<IWorkflowExecutionBinding> bindingOpt = bindingStore.findById(request.executionId());
            if (bindingOpt.isEmpty()) {
                return Mono.just(new ValidationResponse(
                        request.executionId(), false, false, null,
                        List.of("Execution not found"), List.of(), 1.0));
            }

            IWorkflowExecutionBinding binding = bindingOpt.get();
            String workflowId = binding.getWorkflowIdentifier().getWorkflowName();

            IWorkflowVersion targetVersion;
            try {
                targetVersion = WorkflowVersion.parse(request.targetVersion());
            } catch (Exception e) {
                return Mono.just(new ValidationResponse(
                        request.executionId(), false, false, null,
                        List.of("Invalid target version: " + request.targetVersion()), List.of(), 1.0));
            }

            Optional<IVersionedWorkflow> targetWorkflowOpt = versionRegistry.getVersion(workflowId, targetVersion);
            if (targetWorkflowOpt.isEmpty()) {
                return Mono.just(new ValidationResponse(
                        request.executionId(), false, false, null,
                        List.of("Target version not found"), List.of(), 1.0));
            }

            return migrationExecutor.validate(binding, targetWorkflowOpt.get())
                    .map(result -> new ValidationResponse(
                            request.executionId(),
                            result.isValid(),
                            result.isAtSafeMigrationPoint(),
                            result.getRecommendedTargetStep().orElse(null),
                            result.getErrors(),
                            result.getWarnings(),
                            result.getDataLossRisk()));
        });
    }

    @Override
    public Mono<DryRunResponse> dryRunExecution(SingleMigrationRequest request) {
        return Mono.defer(() -> {
            Optional<IWorkflowExecutionBinding> bindingOpt = bindingStore.findById(request.executionId());
            if (bindingOpt.isEmpty()) {
                return Mono.just(new DryRunResponse(
                        request.executionId(), false, "unknown", request.targetVersion(),
                        "unknown", null, List.of(), List.of("Execution not found"), Map.of()));
            }

            IWorkflowExecutionBinding binding = bindingOpt.get();
            String workflowId = binding.getWorkflowIdentifier().getWorkflowName();

            IWorkflowVersion targetVersion;
            try {
                targetVersion = WorkflowVersion.parse(request.targetVersion());
            } catch (Exception e) {
                return Mono.just(new DryRunResponse(
                        request.executionId(), false,
                        binding.getBoundVersion().toVersionString(), request.targetVersion(),
                        binding.getCurrentStepName(), null,
                        List.of(), List.of("Invalid target version"), Map.of()));
            }

            IMigrationContext context = createMigrationContext(binding, targetVersion, request.initiatedBy(),
                    request.reason().orElse(null), true, UUID.randomUUID().toString());

            return migrationExecutor.dryRun(context)
                    .map(result -> {
                        List<String> projectedChanges = new ArrayList<>();
                        if (result.getPostMigrationSnapshot() != null) {
                            projectedChanges.add("Target step: " + result.getTargetStepName().orElse("unknown"));
                        }

                        return new DryRunResponse(
                                request.executionId(),
                                result.getStatus() == IMigrationResult.Status.DRY_RUN_COMPLETE,
                                binding.getBoundVersion().toVersionString(),
                                targetVersion.toVersionString(),
                                binding.getCurrentStepName(),
                                result.getTargetStepName().orElse(null),
                                projectedChanges,
                                List.of(),
                                result.getPostMigrationSnapshot() != null ?
                                        result.getPostMigrationSnapshot() : Map.of());
                    });
        });
    }

    // ==================== Bulk Migration Operations ====================

    @Override
    public Mono<BulkMigrationResponse> initiateBulkMigration(BulkMigrationRequest request) {
        return Mono.fromCallable(() -> {
            String jobId = UUID.randomUUID().toString();

            IWorkflowVersion targetVersion = WorkflowVersion.parse(request.targetVersion());

            // Find candidates
            List<IWorkflowExecutionBinding> candidates = findMigrationCandidates(
                    request.workflowId(), request.sourceVersionPattern(), targetVersion, request.filters());

            if (candidates.isEmpty()) {
                return new BulkMigrationResponse(
                        jobId, request.workflowId(), request.targetVersion(),
                        0, Instant.now(), BulkMigrationJobState.COMPLETED);
            }

            // Create job
            BulkMigrationJob job = new BulkMigrationJob(
                    jobId, request.workflowId(), targetVersion,
                    candidates, request.options(), request.initiatedBy());

            activeJobs.put(jobId, job);

            // Create progress sink
            Sinks.Many<MigrationProgressEvent> progressSink = Sinks.many().multicast().onBackpressureBuffer();
            jobProgressSinks.put(jobId, progressSink);

            // Start job asynchronously
            executeBulkMigrationJob(job, progressSink)
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe();

            log.info("Bulk migration job [{}] started for workflow [{}] with {} candidates",
                    jobId, request.workflowId(), candidates.size());

            return new BulkMigrationResponse(
                    jobId, request.workflowId(), request.targetVersion(),
                    candidates.size(), Instant.now(), BulkMigrationJobState.IN_PROGRESS);
        });
    }

    @Override
    public Mono<BulkValidationResponse> validateBulkMigration(BulkMigrationRequest request) {
        return Mono.fromCallable(() -> {
            IWorkflowVersion targetVersion = WorkflowVersion.parse(request.targetVersion());

            List<IWorkflowExecutionBinding> candidates = findMigrationCandidates(
                    request.workflowId(), request.sourceVersionPattern(), targetVersion, request.filters());

            List<ExecutionValidationSummary> summaries = new ArrayList<>();
            int validCount = 0;
            int invalidCount = 0;

            for (IWorkflowExecutionBinding binding : candidates) {
                Optional<IVersionedWorkflow> targetWorkflow = versionRegistry.getVersion(
                        request.workflowId(), targetVersion);

                if (targetWorkflow.isEmpty()) {
                    summaries.add(new ExecutionValidationSummary(
                            binding.getExecutionId(),
                            binding.getBoundVersion().toVersionString(),
                            binding.getCurrentStepName(),
                            false, false,
                            List.of("Target workflow version not found")));
                    invalidCount++;
                    continue;
                }

                IMigrationValidationResult result = migrationExecutor.validate(binding, targetWorkflow.get()).block();

                List<String> issues = new ArrayList<>();
                issues.addAll(result.getErrors());
                issues.addAll(result.getWarnings());

                summaries.add(new ExecutionValidationSummary(
                        binding.getExecutionId(),
                        binding.getBoundVersion().toVersionString(),
                        binding.getCurrentStepName(),
                        result.isValid(),
                        result.isAtSafeMigrationPoint(),
                        issues));

                if (result.isValid()) {
                    validCount++;
                } else {
                    invalidCount++;
                }
            }

            return new BulkValidationResponse(
                    request.workflowId(), request.targetVersion(),
                    candidates.size(), validCount, invalidCount, summaries);
        });
    }

    @Override
    public Mono<BulkMigrationStatus> getBulkMigrationStatus(String jobId) {
        return Mono.fromCallable(() -> {
            BulkMigrationJob job = activeJobs.get(jobId);
            if (job == null) {
                return null;
            }
            return job.getStatus();
        });
    }

    @Override
    public Mono<CancellationResponse> cancelBulkMigration(String jobId) {
        return Mono.fromCallable(() -> {
            BulkMigrationJob job = activeJobs.get(jobId);
            if (job == null) {
                return new CancellationResponse(jobId, false, 0, "Job not found");
            }

            int processedBefore = job.processedCount.get();
            job.cancel();

            log.info("Bulk migration job [{}] cancelled after {} executions", jobId, processedBefore);

            return new CancellationResponse(jobId, true, processedBefore, "Job cancelled successfully");
        });
    }

    @Override
    public Flux<MigrationProgressEvent> streamMigrationProgress(String jobId) {
        Sinks.Many<MigrationProgressEvent> sink = jobProgressSinks.get(jobId);
        if (sink == null) {
            return Flux.empty();
        }
        return sink.asFlux();
    }

    // ==================== Targeted Migration Operations ====================

    @Override
    public Mono<TargetedMigrationResponse> migrateTargetedExecutions(TargetedMigrationRequest request) {
        return Mono.defer(() -> {
            String jobId = UUID.randomUUID().toString();
            IWorkflowVersion targetVersion = WorkflowVersion.parse(request.targetVersion());

            List<MigrationResponse> results = new ArrayList<>();
            int successful = 0;
            int failed = 0;
            int skipped = 0;

            for (String executionId : request.executionIds()) {
                SingleMigrationRequest singleRequest = SingleMigrationRequest.builder()
                        .executionId(executionId)
                        .targetVersion(request.targetVersion())
                        .initiatedBy(request.initiatedBy())
                        .createBackup(request.options().createBackups())
                        .allowUnsafe(request.options().allowUnsafe())
                        .reason(request.options().reason().orElse(null))
                        .build();

                MigrationResponse response = migrateExecution(singleRequest).block();
                results.add(response);

                switch (response.status()) {
                    case SUCCESS -> successful++;
                    case FAILED -> failed++;
                    case SKIPPED -> skipped++;
                    default -> {}
                }

                if (request.options().stopOnError() && response.status() == MigrationStatus.FAILED) {
                    break;
                }
            }

            return Mono.just(new TargetedMigrationResponse(
                    jobId, request.executionIds().size(), successful, failed, skipped, results));
        });
    }

    // ==================== Rollback Operations ====================

    @Override
    public Mono<RollbackResponse> rollbackMigration(RollbackRequest request) {
        return Mono.defer(() -> {
            // Find the migration in history
            List<MigrationHistoryEntry> history = migrationHistory.get(request.executionId());
            if (history == null || history.isEmpty()) {
                return Mono.just(new RollbackResponse(
                        request.executionId(), request.migrationId(), false,
                        null, null, Optional.of("No migration history found")));
            }

            MigrationHistoryEntry targetEntry = history.stream()
                    .filter(e -> e.migrationId().equals(request.migrationId()))
                    .findFirst()
                    .orElse(null);

            if (targetEntry == null) {
                return Mono.just(new RollbackResponse(
                        request.executionId(), request.migrationId(), false,
                        null, null, Optional.of("Migration not found in history")));
            }

            // Get binding
            Optional<IWorkflowExecutionBinding> bindingOpt = bindingStore.findById(request.executionId());
            if (bindingOpt.isEmpty()) {
                return Mono.just(new RollbackResponse(
                        request.executionId(), request.migrationId(), false,
                        null, null, Optional.of("Execution not found")));
            }

            IWorkflowExecutionBinding binding = bindingOpt.get();
            IWorkflowVersion sourceVersion = WorkflowVersion.parse(targetEntry.fromVersion());

            // Create context for rollback
            IMigrationContext context = createMigrationContext(
                    binding, sourceVersion, request.initiatedBy(), "Rollback", false,
                    UUID.randomUUID().toString());

            IMigrationResult failedResult = createFailedResultForRollback(binding, targetEntry);

            return migrationExecutor.rollback(context, failedResult)
                    .map(result -> {
                        if (result.getStatus() == IMigrationResult.Status.ROLLED_BACK) {
                            // Record rollback in history
                            recordMigrationHistory(request.executionId(), UUID.randomUUID().toString(),
                                    WorkflowVersion.parse(targetEntry.toVersion()), sourceVersion,
                                    targetEntry.toStep(), targetEntry.fromStep(),
                                    MigrationStatus.ROLLED_BACK, request.initiatedBy(), request.migrationId());

                            return new RollbackResponse(
                                    request.executionId(), request.migrationId(), true,
                                    sourceVersion.toVersionString(), targetEntry.fromStep(),
                                    Optional.empty());
                        } else {
                            return new RollbackResponse(
                                    request.executionId(), request.migrationId(), false,
                                    null, null,
                                    Optional.of(result.getErrorMessage().orElse("Rollback failed")));
                        }
                    });
        });
    }

    @Override
    public Mono<BulkRollbackResponse> rollbackBulkMigration(String jobId) {
        return Mono.fromCallable(() -> {
            BulkMigrationJob job = activeJobs.get(jobId);
            if (job == null) {
                return new BulkRollbackResponse(jobId, 0, 0, List.of());
            }

            List<RollbackResponse> results = new ArrayList<>();
            int successCount = 0;
            int failCount = 0;

            for (String executionId : job.successfulExecutions) {
                List<MigrationHistoryEntry> history = migrationHistory.get(executionId);
                if (history != null && !history.isEmpty()) {
                    MigrationHistoryEntry lastEntry = history.get(history.size() - 1);
                    RollbackRequest rollbackRequest = new RollbackRequest(
                            executionId, lastEntry.migrationId(), "system");
                    RollbackResponse response = rollbackMigration(rollbackRequest).block();
                    results.add(response);

                    if (response.success()) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                }
            }

            return new BulkRollbackResponse(jobId, successCount, failCount, results);
        });
    }

    // ==================== Query Operations ====================

    @Override
    public Mono<MigrationCandidatesResponse> getMigrationCandidates(
            String workflowId, IWorkflowVersion targetVersion, MigrationFilters filters) {
        return Mono.fromCallable(() -> {
            List<IWorkflowExecutionBinding> candidates = findMigrationCandidates(
                    workflowId, "*", targetVersion, filters);

            List<MigrationCandidate> candidateList = candidates.stream()
                    .map(binding -> {
                        Optional<IVersionedWorkflow> targetWorkflow = versionRegistry.getVersion(workflowId, targetVersion);
                        boolean isAtSafePoint = false;
                        List<String> warnings = new ArrayList<>();

                        if (targetWorkflow.isPresent()) {
                            IMigrationValidationResult validation = migrationExecutor.validate(
                                    binding, targetWorkflow.get()).block();
                            isAtSafePoint = validation.isAtSafeMigrationPoint();
                            warnings.addAll(validation.getWarnings());
                        }

                        return new MigrationCandidate(
                                binding.getExecutionId(),
                                binding.getBoundVersion().toVersionString(),
                                binding.getCurrentStepName(),
                                binding.getState().name(),
                                binding.getStartedAt(),
                                isAtSafePoint,
                                !warnings.isEmpty(),
                                warnings);
                    })
                    .toList();

            return new MigrationCandidatesResponse(
                    workflowId, targetVersion.toVersionString(), candidateList.size(), candidateList);
        });
    }

    @Override
    public Mono<MigrationHistoryResponse> getMigrationHistory(String executionId) {
        return Mono.fromCallable(() -> {
            List<MigrationHistoryEntry> history = migrationHistory.getOrDefault(executionId, List.of());

            Optional<IWorkflowExecutionBinding> bindingOpt = bindingStore.findById(executionId);
            String currentVersion = bindingOpt.map(b -> b.getBoundVersion().toVersionString()).orElse("unknown");

            return new MigrationHistoryResponse(executionId, currentVersion, history.size(), history);
        });
    }

    @Override
    public Mono<List<BulkMigrationStatus>> getActiveBulkMigrations() {
        return Mono.fromCallable(() ->
                activeJobs.values().stream()
                        .filter(job -> !job.isCompleted())
                        .map(BulkMigrationJob::getStatus)
                        .toList());
    }

    // ==================== Private Helper Methods ====================

    private List<IWorkflowExecutionBinding> findMigrationCandidates(
            String workflowId, String sourceVersionPattern,
            IWorkflowVersion targetVersion, MigrationFilters filters) {

        return bindingStore.findByWorkflowId(workflowId).stream()
                .filter(binding -> {
                    // Filter by state
                    if (!filters.executionStates().isEmpty() &&
                        !filters.executionStates().contains(binding.getState().name())) {
                        return false;
                    }

                    // Filter by current step
                    if (!filters.currentSteps().isEmpty() &&
                        !filters.currentSteps().contains(binding.getCurrentStepName())) {
                        return false;
                    }

                    // Filter by started after
                    if (filters.startedAfter().isPresent() &&
                        binding.getStartedAt().isBefore(filters.startedAfter().get())) {
                        return false;
                    }

                    // Filter by started before
                    if (filters.startedBefore().isPresent() &&
                        binding.getStartedAt().isAfter(filters.startedBefore().get())) {
                        return false;
                    }

                    // Exclude pending migrations
                    if (filters.excludePendingMigrations() &&
                        binding.getState() == IWorkflowExecutionBinding.ExecutionState.PENDING_MIGRATION) {
                        return false;
                    }

                    // Exclude frozen
                    if (binding.getMigrationStrategy() == IMigrationStrategy.FROZEN) {
                        return false;
                    }

                    // Version must be less than target
                    if (binding.getBoundVersion().compareTo(targetVersion) >= 0) {
                        return false;
                    }

                    return true;
                })
                .limit(filters.maxResults().orElse(Integer.MAX_VALUE))
                .collect(Collectors.toList());
    }

    private IMigrationContext createMigrationContext(
            IWorkflowExecutionBinding binding, IWorkflowVersion targetVersion,
            String initiatedBy, String reason, boolean dryRun, String migrationId) {

        Map<String, Object> preState = new HashMap<>();
        preState.put("currentStep", binding.getCurrentStepName());
        preState.put("version", binding.getBoundVersion().toVersionString());

        return MigrationContext.builder()
                .executionId(binding.getExecutionId())
                .workflowId(binding.getWorkflowIdentifier().getWorkflowName())
                .sourceVersion(binding.getBoundVersion())
                .targetVersion(targetVersion)
                .currentStepName(binding.getCurrentStepName())
                .preMigrationState(preState)
                .migrationInitiatedAt(Instant.now())
                .initiatedBy(initiatedBy)
                .migrationReason(reason)
                .dryRun(dryRun)
                .migrationId(migrationId)
                .build();
    }

    private Mono<Void> executeBulkMigrationJob(BulkMigrationJob job, Sinks.Many<MigrationProgressEvent> sink) {
        return Flux.fromIterable(job.candidates)
                .flatMap(binding -> {
                    if (job.isCancelled()) {
                        return Mono.empty();
                    }

                    String executionId = binding.getExecutionId();
                    String migrationId = UUID.randomUUID().toString();

                    // Emit progress event
                    sink.tryEmitNext(new MigrationProgressEvent(
                            job.jobId, MigrationProgressEvent.EventType.MIGRATION_STARTED,
                            executionId, MigrationStatus.IN_PROGRESS,
                            job.processedCount.get(), job.totalCount,
                            job.getProgressPercent(), Optional.of("Starting migration"),
                            Instant.now()));

                    SingleMigrationRequest request = SingleMigrationRequest.builder()
                            .executionId(executionId)
                            .targetVersion(job.targetVersion.toVersionString())
                            .initiatedBy(job.initiatedBy)
                            .createBackup(job.options.createBackups())
                            .allowUnsafe(job.options.allowUnsafe())
                            .reason(job.options.reason().orElse(null))
                            .build();

                    return migrateExecution(request)
                            .doOnNext(response -> {
                                job.processedCount.incrementAndGet();

                                if (response.status() == MigrationStatus.SUCCESS) {
                                    job.successCount.incrementAndGet();
                                    job.successfulExecutions.add(executionId);

                                    sink.tryEmitNext(new MigrationProgressEvent(
                                            job.jobId, MigrationProgressEvent.EventType.MIGRATION_COMPLETED,
                                            executionId, MigrationStatus.SUCCESS,
                                            job.processedCount.get(), job.totalCount,
                                            job.getProgressPercent(), Optional.empty(), Instant.now()));
                                } else {
                                    job.failCount.incrementAndGet();
                                    job.errors.add("Execution " + executionId + ": " +
                                            response.errorMessage().orElse("Unknown error"));

                                    sink.tryEmitNext(new MigrationProgressEvent(
                                            job.jobId, MigrationProgressEvent.EventType.MIGRATION_FAILED,
                                            executionId, MigrationStatus.FAILED,
                                            job.processedCount.get(), job.totalCount,
                                            job.getProgressPercent(), response.errorMessage(), Instant.now()));

                                    if (job.options.stopOnError()) {
                                        job.cancel();
                                    }
                                }
                            });
                }, job.options.maxConcurrency())
                .then(Mono.fromRunnable(() -> {
                    job.complete();

                    sink.tryEmitNext(new MigrationProgressEvent(
                            job.jobId, MigrationProgressEvent.EventType.JOB_COMPLETED,
                            null, MigrationStatus.SUCCESS,
                            job.processedCount.get(), job.totalCount,
                            100.0, Optional.of("Job completed"), Instant.now()));

                    sink.tryEmitComplete();

                    log.info("Bulk migration job [{}] completed: {} successful, {} failed",
                            job.jobId, job.successCount.get(), job.failCount.get());
                }));
    }

    private void recordMigrationHistory(String executionId, String migrationId,
                                        IWorkflowVersion fromVersion, IWorkflowVersion toVersion,
                                        String fromStep, String toStep,
                                        MigrationStatus status, String initiatedBy, String rollbackOf) {
        MigrationHistoryEntry entry = new MigrationHistoryEntry(
                migrationId,
                fromVersion.toVersionString(),
                toVersion.toVersionString(),
                fromStep, toStep,
                status, Instant.now(), initiatedBy,
                Optional.ofNullable(rollbackOf));

        migrationHistory.computeIfAbsent(executionId, k -> new ArrayList<>()).add(entry);
    }

    private IMigrationResult createFailedResultForRollback(IWorkflowExecutionBinding binding,
                                                           MigrationHistoryEntry entry) {
        return com.lyshra.open.app.integration.models.version.migration.MigrationResult.failed(
                IMigrationResult.Status.FAILED_HANDLER_ERROR,
                binding.getExecutionId(),
                WorkflowVersion.parse(entry.fromVersion()),
                WorkflowVersion.parse(entry.toVersion()),
                new RuntimeException("Rollback requested"),
                Duration.ZERO,
                Map.of(),
                List.of());
    }

    /**
     * Internal class to track bulk migration job state.
     */
    private static class BulkMigrationJob {
        final String jobId;
        final String workflowId;
        final IWorkflowVersion targetVersion;
        final List<IWorkflowExecutionBinding> candidates;
        final BulkMigrationOptions options;
        final String initiatedBy;
        final int totalCount;
        final Instant startedAt;
        final AtomicInteger processedCount = new AtomicInteger(0);
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failCount = new AtomicInteger(0);
        final List<String> successfulExecutions = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        volatile BulkMigrationJobState state = BulkMigrationJobState.IN_PROGRESS;
        volatile Instant completedAt;
        volatile boolean cancelled = false;

        BulkMigrationJob(String jobId, String workflowId, IWorkflowVersion targetVersion,
                         List<IWorkflowExecutionBinding> candidates,
                         BulkMigrationOptions options, String initiatedBy) {
            this.jobId = jobId;
            this.workflowId = workflowId;
            this.targetVersion = targetVersion;
            this.candidates = candidates;
            this.options = options;
            this.initiatedBy = initiatedBy;
            this.totalCount = candidates.size();
            this.startedAt = Instant.now();
        }

        void cancel() {
            cancelled = true;
            state = BulkMigrationJobState.CANCELLED;
            completedAt = Instant.now();
        }

        void complete() {
            if (!cancelled) {
                state = failCount.get() > 0 && successCount.get() == 0 ?
                        BulkMigrationJobState.FAILED : BulkMigrationJobState.COMPLETED;
                completedAt = Instant.now();
            }
        }

        boolean isCancelled() {
            return cancelled;
        }

        boolean isCompleted() {
            return state == BulkMigrationJobState.COMPLETED ||
                   state == BulkMigrationJobState.FAILED ||
                   state == BulkMigrationJobState.CANCELLED;
        }

        double getProgressPercent() {
            if (totalCount == 0) return 100.0;
            return (processedCount.get() * 100.0) / totalCount;
        }

        BulkMigrationStatus getStatus() {
            String currentExecution = null;
            if (processedCount.get() < totalCount && !cancelled) {
                currentExecution = candidates.get(processedCount.get()).getExecutionId();
            }

            return new BulkMigrationStatus(
                    jobId, workflowId, targetVersion.toVersionString(),
                    state, totalCount, processedCount.get(),
                    successCount.get(), failCount.get(),
                    totalCount - processedCount.get() - failCount.get() - successCount.get(),
                    startedAt, Optional.ofNullable(completedAt),
                    getProgressPercent(), Optional.ofNullable(currentExecution),
                    new ArrayList<>(errors));
        }
    }
}
