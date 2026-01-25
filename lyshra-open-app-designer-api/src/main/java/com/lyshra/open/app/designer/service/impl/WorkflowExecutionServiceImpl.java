package com.lyshra.open.app.designer.service.impl;

import com.lyshra.open.app.designer.domain.*;
import com.lyshra.open.app.designer.dto.ExecutionStatistics;
import com.lyshra.open.app.designer.dto.WorkflowExecutionRequest;
import com.lyshra.open.app.designer.exception.ConflictException;
import com.lyshra.open.app.designer.exception.ResourceNotFoundException;
import com.lyshra.open.app.designer.repository.WorkflowDefinitionRepository;
import com.lyshra.open.app.designer.repository.WorkflowExecutionRepository;
import com.lyshra.open.app.designer.repository.WorkflowVersionRepository;
import com.lyshra.open.app.designer.service.WorkflowExecutionService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.*;
import java.util.*;

/**
 * Implementation of WorkflowExecutionService.
 * Handles workflow execution, monitoring, and statistics.
 */
@Service
public class WorkflowExecutionServiceImpl implements WorkflowExecutionService {

    private final WorkflowExecutionRepository executionRepository;
    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowVersionRepository versionRepository;
    private final Sinks.Many<WorkflowExecution> executionUpdateSink;

    public WorkflowExecutionServiceImpl(
            WorkflowExecutionRepository executionRepository,
            WorkflowDefinitionRepository definitionRepository,
            WorkflowVersionRepository versionRepository) {
        this.executionRepository = executionRepository;
        this.definitionRepository = definitionRepository;
        this.versionRepository = versionRepository;
        this.executionUpdateSink = Sinks.many().multicast().onBackpressureBuffer();
    }

    @Override
    public Mono<WorkflowExecution> startExecution(
            String workflowDefinitionId, WorkflowExecutionRequest request, String userId) {
        return definitionRepository.findById(workflowDefinitionId)
                .flatMap(optDef -> optDef
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(
                                new ResourceNotFoundException("WorkflowDefinition", workflowDefinitionId))))
                .flatMap(definition -> {
                    if (definition.getLifecycleState() != WorkflowLifecycleState.ACTIVE) {
                        return Mono.error(new ConflictException(
                                "Cannot execute workflow in state: " + definition.getLifecycleState()));
                    }
                    String versionId = Optional.ofNullable(request.getVersionId())
                            .orElseGet(definition::getActiveVersionId);
                    return versionRepository.findById(versionId)
                            .flatMap(optVersion -> optVersion
                                    .map(version -> createExecution(definition, version, request, userId))
                                    .orElseGet(() -> Mono.error(
                                            new ResourceNotFoundException("WorkflowVersion", versionId))));
                });
    }

    private Mono<WorkflowExecution> createExecution(
            WorkflowDefinition definition, WorkflowVersion version,
            WorkflowExecutionRequest request, String userId) {
        WorkflowExecution execution = WorkflowExecution.builder()
                .id(UUID.randomUUID().toString())
                .workflowDefinitionId(definition.getId())
                .workflowVersionId(version.getId())
                .workflowName(definition.getName())
                .versionNumber(version.getVersionNumber())
                .status(ExecutionStatus.PENDING)
                .startedAt(Instant.now())
                .inputData(request.getInputData() != null ? request.getInputData() : new HashMap<>())
                .variables(request.getVariables() != null ? request.getVariables() : new HashMap<>())
                .triggeredBy(userId)
                .correlationId(request.getCorrelationId())
                .metadata(request.getMetadata() != null ? request.getMetadata() : new HashMap<>())
                .stepLogs(new ArrayList<>())
                .build();

        return executionRepository.save(execution)
                .doOnNext(this::notifyExecutionUpdate)
                .flatMap(this::simulateExecution);
    }

    private Mono<WorkflowExecution> simulateExecution(WorkflowExecution execution) {
        execution.setStatus(ExecutionStatus.RUNNING);
        return executionRepository.save(execution)
                .doOnNext(this::notifyExecutionUpdate);
    }

    @Override
    public Mono<WorkflowExecution> getExecution(String executionId) {
        return executionRepository.findById(executionId)
                .flatMap(optExec -> optExec
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(
                                new ResourceNotFoundException("WorkflowExecution", executionId))));
    }

    @Override
    public Flux<WorkflowExecution> getAllExecutions() {
        return executionRepository.findAll();
    }

    @Override
    public Flux<WorkflowExecution> getExecutionsByWorkflowDefinition(String workflowDefinitionId) {
        return executionRepository.findByWorkflowDefinitionId(workflowDefinitionId);
    }

    @Override
    public Flux<WorkflowExecution> getExecutionsByWorkflowVersion(String workflowVersionId) {
        return executionRepository.findByWorkflowVersionId(workflowVersionId);
    }

    @Override
    public Flux<WorkflowExecution> getExecutionsByStatus(ExecutionStatus status) {
        return executionRepository.findByStatus(status);
    }

    @Override
    public Flux<WorkflowExecution> getExecutionsByTimeRange(Instant start, Instant end) {
        return executionRepository.findByStartedAtBetween(start, end);
    }

    @Override
    public Flux<WorkflowExecution> getRunningExecutions() {
        return executionRepository.findRunning();
    }

    @Override
    public Mono<WorkflowExecution> cancelExecution(String executionId, String userId) {
        return executionRepository.findById(executionId)
                .flatMap(optExec -> optExec
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(
                                new ResourceNotFoundException("WorkflowExecution", executionId))))
                .flatMap(execution -> {
                    if (execution.isCompleted()) {
                        return Mono.error(new ConflictException(
                                "Cannot cancel completed execution"));
                    }
                    execution.setStatus(ExecutionStatus.CANCELLED);
                    execution.setCompletedAt(Instant.now());
                    return executionRepository.save(execution)
                            .doOnNext(this::notifyExecutionUpdate);
                });
    }

    @Override
    public Mono<WorkflowExecution> retryExecution(String executionId, String userId) {
        return executionRepository.findById(executionId)
                .flatMap(optExec -> optExec
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(
                                new ResourceNotFoundException("WorkflowExecution", executionId))))
                .flatMap(execution -> {
                    if (execution.getStatus() != ExecutionStatus.FAILED) {
                        return Mono.error(new ConflictException(
                                "Can only retry failed executions"));
                    }
                    WorkflowExecutionRequest request = WorkflowExecutionRequest.builder()
                            .inputData(execution.getInputData())
                            .variables(execution.getVariables())
                            .correlationId(execution.getCorrelationId())
                            .metadata(execution.getMetadata())
                            .versionId(execution.getWorkflowVersionId())
                            .build();
                    return startExecution(execution.getWorkflowDefinitionId(), request, userId);
                });
    }

    @Override
    public Mono<ExecutionStatistics> getStatistics() {
        return buildStatistics(executionRepository.findAll());
    }

    @Override
    public Mono<ExecutionStatistics> getStatisticsByWorkflowDefinition(String workflowDefinitionId) {
        return buildStatistics(executionRepository.findByWorkflowDefinitionId(workflowDefinitionId));
    }

    private Mono<ExecutionStatistics> buildStatistics(Flux<WorkflowExecution> executions) {
        return executions.collectList()
                .map(this::calculateStatistics);
    }

    private ExecutionStatistics calculateStatistics(List<WorkflowExecution> executions) {
        long total = executions.size();
        long running = executions.stream()
                .filter(WorkflowExecution::isRunning)
                .count();
        long completed = executions.stream()
                .filter(e -> e.getStatus() == ExecutionStatus.COMPLETED)
                .count();
        long failed = executions.stream()
                .filter(e -> e.getStatus() == ExecutionStatus.FAILED)
                .count();
        long cancelled = executions.stream()
                .filter(e -> e.getStatus() == ExecutionStatus.CANCELLED)
                .count();

        double successRate = total > 0 ? (double) completed / total * 100 : 0;

        List<Duration> durations = executions.stream()
                .filter(WorkflowExecution::isCompleted)
                .map(e -> e.getDuration().orElse(Duration.ZERO))
                .toList();

        Duration avgDuration = calculateAverageDuration(durations);
        Duration minDuration = durations.stream().min(Duration::compareTo).orElse(Duration.ZERO);
        Duration maxDuration = durations.stream().max(Duration::compareTo).orElse(Duration.ZERO);

        Instant now = Instant.now();
        Instant startOfToday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant startOfWeek = LocalDate.now().minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant startOfMonth = LocalDate.now().minusDays(30).atStartOfDay(ZoneId.systemDefault()).toInstant();

        long today = executions.stream()
                .filter(e -> e.getStartedAt() != null && e.getStartedAt().isAfter(startOfToday))
                .count();
        long thisWeek = executions.stream()
                .filter(e -> e.getStartedAt() != null && e.getStartedAt().isAfter(startOfWeek))
                .count();
        long thisMonth = executions.stream()
                .filter(e -> e.getStartedAt() != null && e.getStartedAt().isAfter(startOfMonth))
                .count();

        return ExecutionStatistics.builder()
                .totalExecutions(total)
                .runningExecutions(running)
                .completedExecutions(completed)
                .failedExecutions(failed)
                .cancelledExecutions(cancelled)
                .successRate(successRate)
                .averageDuration(avgDuration)
                .minDuration(minDuration)
                .maxDuration(maxDuration)
                .executionsToday(today)
                .executionsThisWeek(thisWeek)
                .executionsThisMonth(thisMonth)
                .build();
    }

    private Duration calculateAverageDuration(List<Duration> durations) {
        if (durations.isEmpty()) {
            return Duration.ZERO;
        }
        long totalMillis = durations.stream()
                .mapToLong(Duration::toMillis)
                .sum();
        return Duration.ofMillis(totalMillis / durations.size());
    }

    @Override
    public Flux<WorkflowExecution> subscribeToExecutionUpdates(String executionId) {
        return executionUpdateSink.asFlux()
                .filter(exec -> executionId.equals(exec.getId()));
    }

    @Override
    public Flux<WorkflowExecution> subscribeToAllExecutionUpdates() {
        return executionUpdateSink.asFlux();
    }

    private void notifyExecutionUpdate(WorkflowExecution execution) {
        executionUpdateSink.tryEmitNext(execution);
    }
}
