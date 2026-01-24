package com.lyshra.open.app.core.engine.node.impl;

import com.lyshra.open.app.core.engine.ILyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.LyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.lock.ILyshraOpenAppWorkflowLockService;
import com.lyshra.open.app.core.engine.lock.WorkflowLockedException;
import com.lyshra.open.app.core.engine.lock.impl.InMemoryWorkflowLockService;
import com.lyshra.open.app.core.engine.node.ILyshraOpenAppWorkflowResumptionService;
import com.lyshra.open.app.core.engine.state.ILyshraOpenAppWorkflowStateStore;
import com.lyshra.open.app.core.engine.state.LyshraOpenAppWorkflowStateStoreManager;
import com.lyshra.open.app.core.engine.timeout.ILyshraOpenAppTimeoutScheduler;
import com.lyshra.open.app.core.engine.timeout.impl.LyshraOpenAppTimeoutSchedulerImpl;
import com.lyshra.open.app.core.models.LyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskResult;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflow;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowInstance;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStep;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepNext;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppWorkflowExecutionState;
import com.lyshra.open.app.integration.models.workflow.LyshraOpenAppWorkflowInstanceState;
import com.lyshra.open.app.integration.models.workflows.step.LyshraOpenAppWorkflowStepIdentifier;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of workflow resumption service.
 * Handles the mechanics of resuming suspended workflows.
 *
 * <p>Uses persistent state store for durable workflow state storage.
 * State is preserved across restarts and crashes.
 */
@Slf4j
public class LyshraOpenAppWorkflowResumptionServiceImpl implements ILyshraOpenAppWorkflowResumptionService {

    private final ILyshraOpenAppFacade facade;
    private final ILyshraOpenAppTimeoutScheduler timeoutScheduler;
    private final LyshraOpenAppWorkflowStateStoreManager stateStoreManager;
    private final ILyshraOpenAppWorkflowLockService lockService;

    // Configuration for locking
    private static final Duration DEFAULT_LOCK_DURATION = Duration.ofMinutes(10);
    private static final Duration LOCK_WAIT_TIMEOUT = Duration.ofSeconds(30);

    // In-memory cache for fast lookups (backed by persistent store)
    private final Map<String, WorkflowInstanceState> workflowInstances = new ConcurrentHashMap<>();

    private LyshraOpenAppWorkflowResumptionServiceImpl() {
        this.facade = LyshraOpenAppFacade.getInstance();
        this.timeoutScheduler = LyshraOpenAppTimeoutSchedulerImpl.getInstance();
        this.stateStoreManager = LyshraOpenAppWorkflowStateStoreManager.getInstance();
        this.lockService = InMemoryWorkflowLockService.getInstance();
    }

    private static final class SingletonHelper {
        private static final ILyshraOpenAppWorkflowResumptionService INSTANCE =
                new LyshraOpenAppWorkflowResumptionServiceImpl();
    }

    public static ILyshraOpenAppWorkflowResumptionService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    @Override
    public Mono<ILyshraOpenAppContext> resumeFromHumanTask(
            ILyshraOpenAppWorkflowInstance instance,
            ILyshraOpenAppHumanTaskResult taskResult) {

        log.info("Resuming workflow from human task: workflowId={}, taskId={}, branch={}",
                instance.getInstanceId(), taskResult.getTaskId(), taskResult.getBranch());

        // Cancel any remaining timeouts for this task
        return timeoutScheduler.cancelTaskTimeouts(taskResult.getTaskId())
                .then(Mono.defer(() -> {
                    // Merge task result data into context
                    Map<String, Object> additionalData = new HashMap<>();
                    taskResult.getFormData().ifPresent(data -> additionalData.put("humanTaskResult", data));
                    additionalData.put("humanTaskStatus", taskResult.getTaskStatus().name());
                    additionalData.put("resolvedBy", taskResult.getResolvedBy().orElse("system"));

                    return resumeWithBranch(instance, taskResult.getBranch(), additionalData);
                }));
    }

    @Override
    public Mono<ILyshraOpenAppContext> resumeWithBranch(
            ILyshraOpenAppWorkflowInstance instance,
            String branch,
            Map<String, Object> additionalData) {

        String workflowId = instance.getInstanceId();
        String lockOwnerId = generateLockOwnerId();

        log.info("Resuming workflow with branch: workflowId={}, step={}, branch={}, lockOwner={}",
                workflowId, instance.getCurrentStep(), branch, lockOwnerId);

        // Try to acquire lock before resuming
        return lockService.tryAcquire(workflowId, lockOwnerId, DEFAULT_LOCK_DURATION)
                .flatMap(acquired -> {
                    if (!acquired) {
                        // Try to get lock info for better error message
                        return lockService.getLockInfo(workflowId)
                                .flatMap(lockInfo -> {
                                    if (lockInfo.isPresent()) {
                                        log.warn("Cannot resume workflow - locked by another process: workflowId={}, holder={}",
                                                workflowId, lockInfo.get().getOwnerId());
                                        return Mono.error(WorkflowLockedException.withLockInfo(lockInfo.get()));
                                    }
                                    // Lock info not available - generic error
                                    log.warn("Cannot resume workflow - lock acquisition failed: workflowId={}", workflowId);
                                    return Mono.error(new WorkflowLockedException(
                                            "Cannot acquire lock for workflow: " + workflowId, workflowId));
                                });
                    }

                    log.debug("Lock acquired for workflow resume: workflowId={}, lockOwner={}", workflowId, lockOwnerId);

                    // Execute the actual resume operation
                    return executeResumeWithBranch(instance, branch, additionalData)
                            .doFinally(signal -> {
                                // Always release the lock when done
                                lockService.release(workflowId, lockOwnerId)
                                        .doOnSuccess(released -> {
                                            if (released) {
                                                log.debug("Lock released for workflow: workflowId={}", workflowId);
                                            } else {
                                                log.warn("Failed to release lock for workflow: workflowId={}", workflowId);
                                            }
                                        })
                                        .subscribe();
                            });
                });
    }

    /**
     * Internal method to execute the actual resume operation (called after lock acquired).
     */
    private Mono<ILyshraOpenAppContext> executeResumeWithBranch(
            ILyshraOpenAppWorkflowInstance instance,
            String branch,
            Map<String, Object> additionalData) {

        return Mono.defer(() -> {
            // Reconstruct the context
            ILyshraOpenAppContext context = reconstructContext(instance, additionalData);

            // Get the workflow definition
            ILyshraOpenAppWorkflow workflow = facade.getPluginFactory()
                    .getWorkflow(instance.getWorkflowIdentifier());

            // Get current step
            ILyshraOpenAppWorkflowStep currentStep = workflow.getSteps().get(instance.getCurrentStep());
            if (currentStep == null) {
                return Mono.error(new IllegalStateException(
                        "Current step not found: " + instance.getCurrentStep()));
            }

            // Determine next step from branch
            String nextStepName = getNextStepFromBranch(currentStep, branch);
            log.info("Next step determined: {} -> {}", branch, nextStepName);

            // Update instance state
            updateInstanceState(instance.getInstanceId(), LyshraOpenAppWorkflowExecutionState.RUNNING);

            // Continue workflow execution from next step
            return executeFromStep(nextStepName, instance, context);
        });
    }

    @Override
    public Mono<ILyshraOpenAppContext> resumePausedWorkflow(ILyshraOpenAppWorkflowInstance instance) {
        String workflowId = instance.getInstanceId();
        String lockOwnerId = generateLockOwnerId();

        log.info("Resuming paused workflow: workflowId={}, currentStep={}, lockOwner={}",
                workflowId, instance.getCurrentStep(), lockOwnerId);

        if (instance.getExecutionState() != LyshraOpenAppWorkflowExecutionState.PAUSED) {
            return Mono.error(new IllegalStateException(
                    "Workflow is not paused. Current state: " + instance.getExecutionState()));
        }

        // Try to acquire lock before resuming
        return lockService.tryAcquire(workflowId, lockOwnerId, DEFAULT_LOCK_DURATION)
                .flatMap(acquired -> {
                    if (!acquired) {
                        return lockService.getLockInfo(workflowId)
                                .flatMap(lockInfo -> {
                                    if (lockInfo.isPresent()) {
                                        log.warn("Cannot resume paused workflow - locked: workflowId={}, holder={}",
                                                workflowId, lockInfo.get().getOwnerId());
                                        return Mono.error(WorkflowLockedException.withLockInfo(lockInfo.get()));
                                    }
                                    return Mono.error(new WorkflowLockedException(
                                            "Cannot acquire lock for workflow: " + workflowId, workflowId));
                                });
                    }

                    log.debug("Lock acquired for paused workflow resume: workflowId={}", workflowId);

                    return Mono.defer(() -> {
                        ILyshraOpenAppContext context = reconstructContext(instance, Collections.emptyMap());

                        // Update instance state
                        updateInstanceState(workflowId, LyshraOpenAppWorkflowExecutionState.RUNNING);

                        // Continue from current step
                        return executeFromStep(instance.getCurrentStep(), instance, context);
                    }).doFinally(signal -> {
                        lockService.release(workflowId, lockOwnerId)
                                .doOnSuccess(released -> log.debug("Lock released for paused workflow: workflowId={}", workflowId))
                                .subscribe();
                    });
                });
    }

    @Override
    public Mono<ILyshraOpenAppWorkflowInstance> suspendWorkflow(String workflowInstanceId, String reason) {
        log.info("Suspending workflow: workflowId={}, reason={}", workflowInstanceId, reason);

        return Mono.defer(() -> {
            WorkflowInstanceState state = workflowInstances.get(workflowInstanceId);
            if (state == null) {
                throw new IllegalArgumentException("Workflow instance not found: " + workflowInstanceId);
            }

            state.executionState = LyshraOpenAppWorkflowExecutionState.PAUSED;
            state.suspendedAt = Instant.now();
            state.updatedAt = Instant.now();

            // Persist to state store
            return persistToStateStore(state)
                    .thenReturn(createWorkflowInstanceFromState(state));
        });
    }

    @Override
    public Mono<ILyshraOpenAppWorkflowInstance> suspendAtHumanTask(
            String workflowInstanceId,
            String currentStep,
            String humanTaskId,
            ILyshraOpenAppContext context) {

        log.info("Suspending workflow at human task: workflowId={}, step={}, taskId={}",
                workflowInstanceId, currentStep, humanTaskId);

        return Mono.defer(() -> {
            WorkflowInstanceState state = workflowInstances.computeIfAbsent(
                    workflowInstanceId, id -> new WorkflowInstanceState(id));

            state.executionState = LyshraOpenAppWorkflowExecutionState.WAITING;
            state.currentStep = currentStep;
            state.activeHumanTaskId = humanTaskId;
            state.humanTaskIds.add(humanTaskId);
            state.suspendedAt = Instant.now();
            state.updatedAt = Instant.now();

            // Persist context
            state.contextData = new HashMap<>();
            if (context.getData() != null) {
                state.contextData.put("data", context.getData());
            }
            state.contextVariables = new HashMap<>(context.getVariables());

            // Persist to state store for durability
            return persistToStateStore(state)
                    .doOnSuccess(persisted -> log.info("Workflow state persisted: workflowId={}, taskId={}",
                            workflowInstanceId, humanTaskId))
                    .thenReturn(createWorkflowInstanceFromState(state));
        });
    }

    @Override
    public Mono<Boolean> canResume(String workflowInstanceId) {
        return getWorkflowInstance(workflowInstanceId)
                .map(instance -> instance != null
                        && (instance.getExecutionState() == LyshraOpenAppWorkflowExecutionState.WAITING
                        || instance.getExecutionState() == LyshraOpenAppWorkflowExecutionState.PAUSED))
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<ILyshraOpenAppWorkflowInstance> getWorkflowInstance(String workflowInstanceId) {
        return Mono.defer(() -> {
            // Try in-memory cache first
            WorkflowInstanceState cachedState = workflowInstances.get(workflowInstanceId);
            if (cachedState != null) {
                return Mono.just(createWorkflowInstanceFromState(cachedState));
            }

            // Try persistent store
            return loadFromStateStore(workflowInstanceId)
                    .map(state -> {
                        // Update cache
                        workflowInstances.put(workflowInstanceId, state);
                        return createWorkflowInstanceFromState(state);
                    });
        });
    }

    // ========================================================================
    // LOCKING METHODS
    // ========================================================================

    @Override
    public Mono<Boolean> isLocked(String workflowInstanceId) {
        return lockService.isLocked(workflowInstanceId);
    }

    @Override
    public Mono<java.util.Optional<com.lyshra.open.app.core.engine.lock.WorkflowLock>> getLockInfo(String workflowInstanceId) {
        return lockService.getLockInfo(workflowInstanceId);
    }

    @Override
    public Mono<Boolean> forceReleaseLock(String workflowInstanceId, String reason) {
        log.warn("Force releasing workflow lock: workflowId={}, reason={}", workflowInstanceId, reason);
        return lockService.forceRelease(workflowInstanceId, reason);
    }

    /**
     * Registers a new workflow execution.
     * Called by the workflow executor when starting a new workflow.
     */
    public Mono<String> registerWorkflowExecution(
            String workflowInstanceId,
            com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier workflowIdentifier,
            ILyshraOpenAppContext context) {

        return Mono.fromCallable(() -> {
            WorkflowInstanceState state = new WorkflowInstanceState(workflowInstanceId);
            state.workflowIdentifier = workflowIdentifier;
            state.executionState = LyshraOpenAppWorkflowExecutionState.RUNNING;
            state.createdAt = Instant.now();
            state.startedAt = Instant.now();
            state.updatedAt = Instant.now();

            workflowInstances.put(workflowInstanceId, state);
            return workflowInstanceId;
        });
    }

    // ========== Private Helper Methods ==========

    /**
     * Generates a unique lock owner ID for this thread/operation.
     */
    private String generateLockOwnerId() {
        return String.format("%s-%s-%d",
                getHostName(),
                Thread.currentThread().getName(),
                System.nanoTime());
    }

    private static String getHostName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private ILyshraOpenAppContext reconstructContext(
            ILyshraOpenAppWorkflowInstance instance,
            Map<String, Object> additionalData) {

        LyshraOpenAppContext context = new LyshraOpenAppContext();

        // Restore data
        if (instance.getContextData() != null && instance.getContextData().containsKey("data")) {
            context.setData(instance.getContextData().get("data"));
        }

        // Restore variables
        if (instance.getContextVariables() != null) {
            context.setVariables(new HashMap<>(instance.getContextVariables()));
        }

        // Merge additional data
        if (additionalData != null) {
            additionalData.forEach(context::addVariable);
        }

        return context;
    }

    private String getNextStepFromBranch(ILyshraOpenAppWorkflowStep step, String branch) {
        ILyshraOpenAppWorkflowStepNext next = step.getNext();
        if (next == null || next.getBranches() == null) {
            return "NOOP";
        }

        // Try exact branch match
        String nextStep = next.getBranches().get(branch);
        if (nextStep != null) {
            return nextStep;
        }

        // Try DEFAULT branch
        nextStep = next.getBranches().get("DEFAULT");
        return nextStep != null ? nextStep : "NOOP";
    }

    private Mono<ILyshraOpenAppContext> executeFromStep(
            String stepName,
            ILyshraOpenAppWorkflowInstance instance,
            ILyshraOpenAppContext context) {

        // Use the existing workflow step executor to continue execution
        if (stepName == null || stepName.isBlank() || "NOOP".equalsIgnoreCase(stepName)) {
            log.info("Workflow execution completed: {}", instance.getInstanceId());
            updateInstanceState(instance.getInstanceId(), LyshraOpenAppWorkflowExecutionState.COMPLETED);
            return Mono.just(context);
        }

        LyshraOpenAppWorkflowStepIdentifier stepIdentifier =
                new LyshraOpenAppWorkflowStepIdentifier(instance.getWorkflowIdentifier(), stepName);

        return facade.getWorkflowStepExecutor().execute(stepIdentifier, context)
                .flatMap(nextStep -> executeFromStep(nextStep, instance, context))
                .doOnError(error -> updateInstanceState(
                        instance.getInstanceId(),
                        LyshraOpenAppWorkflowExecutionState.FAILED));
    }

    private void updateInstanceState(String instanceId, LyshraOpenAppWorkflowExecutionState newState) {
        WorkflowInstanceState state = workflowInstances.get(instanceId);
        if (state != null) {
            state.executionState = newState;
            state.updatedAt = Instant.now();

            if (newState == LyshraOpenAppWorkflowExecutionState.COMPLETED
                    || newState == LyshraOpenAppWorkflowExecutionState.FAILED) {
                state.completedAt = Instant.now();
            }

            // Persist state change
            persistToStateStore(state).subscribe();
        }
    }

    // ========== State Store Integration ==========

    /**
     * Persists the workflow instance state to the durable store.
     */
    private Mono<LyshraOpenAppWorkflowInstanceState> persistToStateStore(WorkflowInstanceState state) {
        ILyshraOpenAppWorkflowStateStore store = stateStoreManager.getStore();
        if (store == null) {
            log.warn("State store not available, state will not be persisted");
            return Mono.empty();
        }

        LyshraOpenAppWorkflowInstanceState persistentState = convertToPersistentState(state);
        return store.save(persistentState)
                .doOnSuccess(saved -> log.debug("Persisted workflow state: {}", saved.getInstanceId()))
                .doOnError(e -> log.error("Failed to persist workflow state: {}", state.instanceId, e));
    }

    /**
     * Loads workflow instance state from the durable store.
     */
    private Mono<WorkflowInstanceState> loadFromStateStore(String instanceId) {
        ILyshraOpenAppWorkflowStateStore store = stateStoreManager.getStore();
        if (store == null) {
            return Mono.empty();
        }

        return store.findById(instanceId)
                .map(this::convertFromPersistentState)
                .doOnSuccess(state -> {
                    if (state != null) {
                        log.debug("Loaded workflow state from store: {}", instanceId);
                    }
                });
    }

    /**
     * Converts internal state to persistent state model.
     */
    private LyshraOpenAppWorkflowInstanceState convertToPersistentState(WorkflowInstanceState state) {
        String workflowDefId = null;
        String workflowName = null;
        if (state.workflowIdentifier != null) {
            workflowDefId = String.format("%s/%s/%s/%s",
                    state.workflowIdentifier.getOrganization(),
                    state.workflowIdentifier.getModule(),
                    state.workflowIdentifier.getVersion(),
                    state.workflowIdentifier.getWorkflowName());
            workflowName = state.workflowIdentifier.getWorkflowName();
        }

        LyshraOpenAppWorkflowInstanceState.SuspensionReason suspensionReason = null;
        if (state.executionState == LyshraOpenAppWorkflowExecutionState.WAITING && state.activeHumanTaskId != null) {
            suspensionReason = LyshraOpenAppWorkflowInstanceState.SuspensionReason.HUMAN_TASK;
        } else if (state.executionState == LyshraOpenAppWorkflowExecutionState.PAUSED) {
            suspensionReason = LyshraOpenAppWorkflowInstanceState.SuspensionReason.ADMIN_PAUSE;
        }

        return LyshraOpenAppWorkflowInstanceState.builder()
                .instanceId(state.instanceId)
                .workflowDefinitionId(workflowDefId)
                .workflowName(workflowName)
                .status(state.executionState)
                .currentStepId(state.currentStep)
                .suspendedAtStepId(state.currentStep)
                .contextData(new HashMap<>(state.contextData))
                .variables(new HashMap<>(state.contextVariables))
                .humanTaskId(state.activeHumanTaskId)
                .suspensionReason(suspensionReason)
                .createdAt(state.createdAt)
                .updatedAt(state.updatedAt)
                .startedAt(state.startedAt)
                .suspendedAt(state.suspendedAt)
                .completedAt(state.completedAt)
                .build();
    }

    /**
     * Converts persistent state model to internal state.
     */
    private WorkflowInstanceState convertFromPersistentState(LyshraOpenAppWorkflowInstanceState persistentState) {
        WorkflowInstanceState state = new WorkflowInstanceState(persistentState.getInstanceId());
        state.executionState = persistentState.getStatus();
        state.currentStep = persistentState.getCurrentStepId();
        state.contextData = persistentState.getContextData() != null
                ? new HashMap<>(persistentState.getContextData()) : new HashMap<>();
        state.contextVariables = persistentState.getVariables() != null
                ? new HashMap<>(persistentState.getVariables()) : new HashMap<>();
        state.createdAt = persistentState.getCreatedAt();
        state.updatedAt = persistentState.getUpdatedAt();
        state.startedAt = persistentState.getStartedAt();
        state.completedAt = persistentState.getCompletedAt();
        state.suspendedAt = persistentState.getSuspendedAt();
        state.activeHumanTaskId = persistentState.getHumanTaskId();

        if (persistentState.getHumanTaskId() != null) {
            state.humanTaskIds.add(persistentState.getHumanTaskId());
        }

        // Note: workflowIdentifier needs to be reconstructed if needed
        // This would require parsing the workflowDefinitionId
        return state;
    }

    private ILyshraOpenAppWorkflowInstance createWorkflowInstanceFromState(WorkflowInstanceState state) {
        return new InMemoryWorkflowInstance(state);
    }

    // ========== Internal Classes ==========

    private static class WorkflowInstanceState {
        final String instanceId;
        com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier workflowIdentifier;
        LyshraOpenAppWorkflowExecutionState executionState = LyshraOpenAppWorkflowExecutionState.RUNNING;
        String currentStep;
        Map<String, Object> contextData = new HashMap<>();
        Map<String, Object> contextVariables = new HashMap<>();
        Instant createdAt;
        Instant updatedAt;
        Instant startedAt;
        Instant completedAt;
        Instant suspendedAt;
        String activeHumanTaskId;
        List<String> humanTaskIds = new ArrayList<>();

        WorkflowInstanceState(String instanceId) {
            this.instanceId = instanceId;
        }
    }

    /**
     * In-memory implementation of ILyshraOpenAppWorkflowInstance.
     * Production implementations should use a proper entity class.
     */
    private static class InMemoryWorkflowInstance implements ILyshraOpenAppWorkflowInstance {
        private final WorkflowInstanceState state;

        InMemoryWorkflowInstance(WorkflowInstanceState state) {
            this.state = state;
        }

        @Override
        public String getInstanceId() {
            return state.instanceId;
        }

        @Override
        public com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier getWorkflowIdentifier() {
            return state.workflowIdentifier;
        }

        @Override
        public LyshraOpenAppWorkflowExecutionState getExecutionState() {
            return state.executionState;
        }

        @Override
        public String getCurrentStep() {
            return state.currentStep;
        }

        @Override
        public Map<String, Object> getContextData() {
            return Collections.unmodifiableMap(state.contextData);
        }

        @Override
        public Map<String, Object> getContextVariables() {
            return Collections.unmodifiableMap(state.contextVariables);
        }

        @Override
        public Instant getCreatedAt() {
            return state.createdAt;
        }

        @Override
        public Instant getUpdatedAt() {
            return state.updatedAt;
        }

        @Override
        public Instant getStartedAt() {
            return state.startedAt;
        }

        @Override
        public Optional<Instant> getCompletedAt() {
            return Optional.ofNullable(state.completedAt);
        }

        @Override
        public Optional<Instant> getSuspendedAt() {
            return Optional.ofNullable(state.suspendedAt);
        }

        @Override
        public Optional<String> getActiveHumanTaskId() {
            return Optional.ofNullable(state.activeHumanTaskId);
        }

        @Override
        public List<String> getHumanTaskIds() {
            return Collections.unmodifiableList(state.humanTaskIds);
        }

        @Override
        public Optional<String> getParentInstanceId() {
            return Optional.empty();
        }

        @Override
        public List<String> getChildInstanceIds() {
            return Collections.emptyList();
        }

        @Override
        public Optional<ILyshraOpenAppWorkflowError> getError() {
            return Optional.empty();
        }

        @Override
        public Optional<String> getCorrelationId() {
            return Optional.empty();
        }

        @Override
        public Optional<String> getBusinessKey() {
            return Optional.empty();
        }

        @Override
        public Optional<String> getTenantId() {
            return Optional.empty();
        }

        @Override
        public Optional<String> getInitiatedBy() {
            return Optional.empty();
        }

        @Override
        public Map<String, Object> getMetadata() {
            return Collections.emptyMap();
        }

        @Override
        public List<ILyshraOpenAppWorkflowCheckpoint> getCheckpoints() {
            return Collections.emptyList();
        }

        @Override
        public long getVersion() {
            return 1L;
        }
    }
}
