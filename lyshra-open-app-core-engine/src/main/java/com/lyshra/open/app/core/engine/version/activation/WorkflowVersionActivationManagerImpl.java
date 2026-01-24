package com.lyshra.open.app.core.engine.version.activation;

import com.lyshra.open.app.core.engine.version.IWorkflowExecutionBindingStore;
import com.lyshra.open.app.core.engine.version.loader.IWorkflowDefinitionLoader;
import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Implementation of workflow version activation manager.
 *
 * <p>Provides comprehensive controls for managing workflow version lifecycle:</p>
 * <ul>
 *   <li>Activate/deactivate versions</li>
 *   <li>Set and manage default versions</li>
 *   <li>Support rollback operations</li>
 *   <li>Maintain activation audit history</li>
 *   <li>Enforce activation constraints</li>
 * </ul>
 *
 * <p>Thread Safety: This implementation is fully thread-safe using per-workflow locks.</p>
 */
@Slf4j
public class WorkflowVersionActivationManagerImpl implements IWorkflowVersionActivationManager {

    private final IWorkflowDefinitionLoader workflowLoader;
    private final IWorkflowExecutionBindingStore bindingStore;

    // Per-workflow activation state: workflowId -> (version -> state info)
    private final Map<String, Map<IWorkflowVersion, VersionStateEntry>> activationStates;

    // Default version per workflow: workflowId -> version
    private final Map<String, IWorkflowVersion> defaultVersions;

    // Activation history per workflow: workflowId -> events
    private final Map<String, List<ActivationEvent>> activationHistory;

    // Per-workflow locks
    private final Map<String, ReadWriteLock> workflowLocks;

    private static final int MAX_HISTORY_PER_WORKFLOW = 1000;

    /**
     * Creates an activation manager.
     *
     * @param workflowLoader workflow definition loader
     * @param bindingStore execution binding store (may be null)
     */
    public WorkflowVersionActivationManagerImpl(
            IWorkflowDefinitionLoader workflowLoader,
            IWorkflowExecutionBindingStore bindingStore) {
        this.workflowLoader = workflowLoader;
        this.bindingStore = bindingStore;
        this.activationStates = new ConcurrentHashMap<>();
        this.defaultVersions = new ConcurrentHashMap<>();
        this.activationHistory = new ConcurrentHashMap<>();
        this.workflowLocks = new ConcurrentHashMap<>();
    }

    /**
     * Creates an activation manager without binding store.
     *
     * @param workflowLoader workflow definition loader
     */
    public WorkflowVersionActivationManagerImpl(IWorkflowDefinitionLoader workflowLoader) {
        this(workflowLoader, null);
    }

    /**
     * Factory method to create manager.
     *
     * @param workflowLoader workflow definition loader
     * @return manager instance
     */
    public static IWorkflowVersionActivationManager create(IWorkflowDefinitionLoader workflowLoader) {
        return new WorkflowVersionActivationManagerImpl(workflowLoader);
    }

    /**
     * Factory method with binding store.
     *
     * @param workflowLoader workflow definition loader
     * @param bindingStore execution binding store
     * @return manager instance
     */
    public static IWorkflowVersionActivationManager create(
            IWorkflowDefinitionLoader workflowLoader,
            IWorkflowExecutionBindingStore bindingStore) {
        return new WorkflowVersionActivationManagerImpl(workflowLoader, bindingStore);
    }

    private ReadWriteLock getLock(String workflowId) {
        return workflowLocks.computeIfAbsent(workflowId, k -> new ReentrantReadWriteLock());
    }

    private Map<IWorkflowVersion, VersionStateEntry> getOrCreateStateMap(String workflowId) {
        return activationStates.computeIfAbsent(workflowId, k -> new ConcurrentHashMap<>());
    }

    @Override
    public ActivationResult activate(
            String workflowId,
            IWorkflowVersion version,
            String activatedBy,
            String reason) {

        ReadWriteLock lock = getLock(workflowId);
        lock.writeLock().lock();
        try {
            // Verify version exists
            Optional<IVersionedWorkflow> workflow = workflowLoader.load(workflowId, version);
            if (workflow.isEmpty()) {
                return ActivationResult.failure(workflowId, version, null,
                        "Version not found: " + version.toVersionString());
            }

            Map<IWorkflowVersion, VersionStateEntry> states = getOrCreateStateMap(workflowId);
            VersionStateEntry currentEntry = states.get(version);
            ActivationState previousState = currentEntry != null ? currentEntry.state : ActivationState.INACTIVE;

            if (previousState == ActivationState.ACTIVE) {
                return ActivationResult.success(workflowId, version, previousState, previousState,
                        "Version already active");
            }

            if (previousState == ActivationState.RETIRED) {
                return ActivationResult.failure(workflowId, version, previousState,
                        "Cannot activate retired version");
            }

            // Create new state entry
            VersionStateEntry newEntry = new VersionStateEntry(
                    version,
                    ActivationState.ACTIVE,
                    Instant.now(),
                    activatedBy,
                    reason,
                    null);
            states.put(version, newEntry);

            // Record event
            recordEvent(workflowId, version, ActivationEvent.ActivationEventType.ACTIVATED,
                    previousState, ActivationState.ACTIVE, activatedBy, reason);

            log.info("Activated workflow version: {}:{} by {} - {}",
                    workflowId, version.toVersionString(), activatedBy, reason);

            return ActivationResult.success(workflowId, version, previousState, ActivationState.ACTIVE,
                    "Version activated successfully");

        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public ActivationResult deactivate(
            String workflowId,
            IWorkflowVersion version,
            String deactivatedBy,
            String reason) {

        ReadWriteLock lock = getLock(workflowId);
        lock.writeLock().lock();
        try {
            Map<IWorkflowVersion, VersionStateEntry> states = getOrCreateStateMap(workflowId);
            VersionStateEntry currentEntry = states.get(version);
            ActivationState previousState = currentEntry != null ? currentEntry.state : ActivationState.INACTIVE;

            if (previousState == ActivationState.INACTIVE) {
                return ActivationResult.success(workflowId, version, previousState, previousState,
                        "Version already inactive");
            }

            if (previousState == ActivationState.RETIRED) {
                return ActivationResult.failure(workflowId, version, previousState,
                        "Cannot deactivate retired version");
            }

            // Check if this is the only active version
            long activeCount = states.values().stream()
                    .filter(e -> e.state == ActivationState.ACTIVE)
                    .count();

            if (activeCount == 1 && previousState == ActivationState.ACTIVE) {
                // Check if this is also the default
                IWorkflowVersion defaultVersion = defaultVersions.get(workflowId);
                if (version.equals(defaultVersion)) {
                    return ActivationResult.failure(workflowId, version, previousState,
                            "Cannot deactivate the only active version that is also default. " +
                            "Set another version as default first.");
                }
            }

            // Create new state entry
            VersionStateEntry newEntry = new VersionStateEntry(
                    version,
                    ActivationState.INACTIVE,
                    Instant.now(),
                    deactivatedBy,
                    reason,
                    null);
            states.put(version, newEntry);

            // Clear default if this was the default
            IWorkflowVersion currentDefault = defaultVersions.get(workflowId);
            if (version.equals(currentDefault)) {
                // Find next best active version to be default
                Optional<IWorkflowVersion> nextDefault = states.entrySet().stream()
                        .filter(e -> e.getValue().state == ActivationState.ACTIVE)
                        .map(Map.Entry::getKey)
                        .max(Comparator.naturalOrder());

                if (nextDefault.isPresent()) {
                    defaultVersions.put(workflowId, nextDefault.get());
                    log.info("Auto-set default version to {} after deactivation of {}",
                            nextDefault.get().toVersionString(), version.toVersionString());
                } else {
                    defaultVersions.remove(workflowId);
                }
            }

            // Record event
            recordEvent(workflowId, version, ActivationEvent.ActivationEventType.DEACTIVATED,
                    previousState, ActivationState.INACTIVE, deactivatedBy, reason);

            log.info("Deactivated workflow version: {}:{} by {} - {}",
                    workflowId, version.toVersionString(), deactivatedBy, reason);

            return ActivationResult.success(workflowId, version, previousState, ActivationState.INACTIVE,
                    "Version deactivated successfully");

        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public ActivationResult deprecate(
            String workflowId,
            IWorkflowVersion version,
            String deprecatedBy,
            String reason,
            IWorkflowVersion suggestedReplacement) {

        ReadWriteLock lock = getLock(workflowId);
        lock.writeLock().lock();
        try {
            Map<IWorkflowVersion, VersionStateEntry> states = getOrCreateStateMap(workflowId);
            VersionStateEntry currentEntry = states.get(version);
            ActivationState previousState = currentEntry != null ? currentEntry.state : ActivationState.INACTIVE;

            if (previousState == ActivationState.RETIRED) {
                return ActivationResult.failure(workflowId, version, previousState,
                        "Cannot deprecate retired version");
            }

            // Verify replacement exists and is active
            if (suggestedReplacement != null) {
                VersionStateEntry replacementEntry = states.get(suggestedReplacement);
                if (replacementEntry == null || replacementEntry.state != ActivationState.ACTIVE) {
                    log.warn("Suggested replacement {} is not active", suggestedReplacement.toVersionString());
                }
            }

            // Create new state entry
            VersionStateEntry newEntry = new VersionStateEntry(
                    version,
                    ActivationState.DEPRECATED,
                    Instant.now(),
                    deprecatedBy,
                    reason,
                    suggestedReplacement);
            states.put(version, newEntry);

            // Record event
            recordEvent(workflowId, version, ActivationEvent.ActivationEventType.DEPRECATED,
                    previousState, ActivationState.DEPRECATED, deprecatedBy, reason);

            log.info("Deprecated workflow version: {}:{} by {} - {} (replacement: {})",
                    workflowId, version.toVersionString(), deprecatedBy, reason,
                    suggestedReplacement != null ? suggestedReplacement.toVersionString() : "none");

            return ActivationResult.success(workflowId, version, previousState, ActivationState.DEPRECATED,
                    "Version deprecated successfully");

        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public ActivationResult retire(
            String workflowId,
            IWorkflowVersion version,
            String retiredBy,
            String reason,
            boolean force) {

        ReadWriteLock lock = getLock(workflowId);
        lock.writeLock().lock();
        try {
            Map<IWorkflowVersion, VersionStateEntry> states = getOrCreateStateMap(workflowId);
            VersionStateEntry currentEntry = states.get(version);
            ActivationState previousState = currentEntry != null ? currentEntry.state : ActivationState.INACTIVE;

            if (previousState == ActivationState.RETIRED) {
                return ActivationResult.success(workflowId, version, previousState, previousState,
                        "Version already retired");
            }

            // Check for active executions
            int activeExecutions = getActiveExecutionCount(workflowId, version);
            if (activeExecutions > 0 && !force) {
                return ActivationResult.failure(workflowId, version, previousState,
                        String.format("Cannot retire version with %d active executions. Use force=true to override.",
                                activeExecutions));
            }

            // Cannot retire the default version
            IWorkflowVersion currentDefault = defaultVersions.get(workflowId);
            if (version.equals(currentDefault)) {
                return ActivationResult.failure(workflowId, version, previousState,
                        "Cannot retire the default version. Set another version as default first.");
            }

            // Create new state entry
            VersionStateEntry newEntry = new VersionStateEntry(
                    version,
                    ActivationState.RETIRED,
                    Instant.now(),
                    retiredBy,
                    reason,
                    null);
            states.put(version, newEntry);

            // Record event
            recordEvent(workflowId, version, ActivationEvent.ActivationEventType.RETIRED,
                    previousState, ActivationState.RETIRED, retiredBy, reason);

            log.info("Retired workflow version: {}:{} by {} - {} (force={})",
                    workflowId, version.toVersionString(), retiredBy, reason, force);

            return ActivationResult.success(workflowId, version, previousState, ActivationState.RETIRED,
                    "Version retired successfully");

        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public ActivationResult setDefaultVersion(
            String workflowId,
            IWorkflowVersion version,
            String setBy,
            String reason) {

        ReadWriteLock lock = getLock(workflowId);
        lock.writeLock().lock();
        try {
            // Verify version exists
            Optional<IVersionedWorkflow> workflow = workflowLoader.load(workflowId, version);
            if (workflow.isEmpty()) {
                return ActivationResult.failure(workflowId, version, null,
                        "Version not found: " + version.toVersionString());
            }

            Map<IWorkflowVersion, VersionStateEntry> states = getOrCreateStateMap(workflowId);
            VersionStateEntry entry = states.get(version);
            ActivationState currentState = entry != null ? entry.state : ActivationState.INACTIVE;

            // Version must be active to be default
            if (currentState != ActivationState.ACTIVE) {
                return ActivationResult.failure(workflowId, version, currentState,
                        "Only active versions can be set as default. Current state: " + currentState);
            }

            IWorkflowVersion previousDefault = defaultVersions.get(workflowId);
            defaultVersions.put(workflowId, version);

            // Record event
            ActivationEvent.ActivationEventType eventType = previousDefault == null ?
                    ActivationEvent.ActivationEventType.DEFAULT_SET :
                    ActivationEvent.ActivationEventType.DEFAULT_CHANGED;
            recordEvent(workflowId, version, eventType, currentState, currentState, setBy, reason);

            log.info("Set default version for workflow {}: {} -> {} by {} - {}",
                    workflowId,
                    previousDefault != null ? previousDefault.toVersionString() : "none",
                    version.toVersionString(), setBy, reason);

            return ActivationResult.success(workflowId, version, currentState, currentState,
                    "Default version set successfully");

        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<IVersionedWorkflow> getDefaultVersion(String workflowId) {
        ReadWriteLock lock = getLock(workflowId);
        lock.readLock().lock();
        try {
            IWorkflowVersion defaultVersion = defaultVersions.get(workflowId);
            if (defaultVersion == null) {
                // Fall back to latest active version
                return getActiveVersions(workflowId).stream().findFirst();
            }
            return workflowLoader.load(workflowId, defaultVersion);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public ActivationResult rollback(
            String workflowId,
            IWorkflowVersion targetVersion,
            String rolledBackBy) {

        ReadWriteLock lock = getLock(workflowId);
        lock.writeLock().lock();
        try {
            // First activate the target if not active
            Map<IWorkflowVersion, VersionStateEntry> states = getOrCreateStateMap(workflowId);
            VersionStateEntry entry = states.get(targetVersion);
            ActivationState currentState = entry != null ? entry.state : ActivationState.INACTIVE;

            if (currentState != ActivationState.ACTIVE) {
                // Activate the version first
                ActivationResult activationResult = activate(workflowId, targetVersion, rolledBackBy,
                        "Rollback activation");
                if (!activationResult.success()) {
                    return ActivationResult.failure(workflowId, targetVersion, currentState,
                            "Failed to activate target version for rollback: " + activationResult.message());
                }
            }

            // Set as default
            IWorkflowVersion previousDefault = defaultVersions.get(workflowId);
            defaultVersions.put(workflowId, targetVersion);

            // Record rollback event
            recordEvent(workflowId, targetVersion, ActivationEvent.ActivationEventType.ROLLBACK,
                    currentState, ActivationState.ACTIVE, rolledBackBy,
                    "Rollback from " + (previousDefault != null ? previousDefault.toVersionString() : "none"));

            log.info("Rolled back workflow {} to version {} by {}",
                    workflowId, targetVersion.toVersionString(), rolledBackBy);

            return ActivationResult.success(workflowId, targetVersion, currentState, ActivationState.ACTIVE,
                    "Rollback successful. Default version set to " + targetVersion.toVersionString());

        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<ActivationState> getActivationState(String workflowId, IWorkflowVersion version) {
        ReadWriteLock lock = getLock(workflowId);
        lock.readLock().lock();
        try {
            Map<IWorkflowVersion, VersionStateEntry> states = activationStates.get(workflowId);
            if (states == null) {
                return Optional.empty();
            }
            VersionStateEntry entry = states.get(version);
            return entry != null ? Optional.of(entry.state) : Optional.empty();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<IVersionedWorkflow> getActiveVersions(String workflowId) {
        return getVersionsByState(workflowId, ActivationState.ACTIVE);
    }

    @Override
    public List<IVersionedWorkflow> getInactiveVersions(String workflowId) {
        return getVersionsByState(workflowId, ActivationState.INACTIVE);
    }

    @Override
    public List<IVersionedWorkflow> getDeprecatedVersions(String workflowId) {
        return getVersionsByState(workflowId, ActivationState.DEPRECATED);
    }

    private List<IVersionedWorkflow> getVersionsByState(String workflowId, ActivationState state) {
        ReadWriteLock lock = getLock(workflowId);
        lock.readLock().lock();
        try {
            Map<IWorkflowVersion, VersionStateEntry> states = activationStates.get(workflowId);
            if (states == null) {
                return Collections.emptyList();
            }

            return states.entrySet().stream()
                    .filter(e -> e.getValue().state == state)
                    .map(Map.Entry::getKey)
                    .sorted(Comparator.reverseOrder())
                    .map(v -> workflowLoader.load(workflowId, v))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Map<IWorkflowVersion, VersionActivationInfo> getAllVersionStates(String workflowId) {
        ReadWriteLock lock = getLock(workflowId);
        lock.readLock().lock();
        try {
            Map<IWorkflowVersion, VersionStateEntry> states = activationStates.get(workflowId);
            if (states == null) {
                return Collections.emptyMap();
            }

            IWorkflowVersion defaultVersion = defaultVersions.get(workflowId);
            Map<IWorkflowVersion, VersionActivationInfo> result = new HashMap<>();

            for (Map.Entry<IWorkflowVersion, VersionStateEntry> entry : states.entrySet()) {
                IWorkflowVersion version = entry.getKey();
                VersionStateEntry stateEntry = entry.getValue();

                result.put(version, new VersionActivationInfo(
                        version,
                        stateEntry.state,
                        version.equals(defaultVersion),
                        stateEntry.stateChangedAt,
                        stateEntry.stateChangedBy,
                        Optional.ofNullable(stateEntry.reason),
                        Optional.ofNullable(stateEntry.suggestedReplacement),
                        getActiveExecutionCount(workflowId, version)));
            }

            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean canAcceptNewExecutions(String workflowId, IWorkflowVersion version) {
        return getActivationState(workflowId, version)
                .map(state -> state == ActivationState.ACTIVE)
                .orElse(false);
    }

    @Override
    public List<ActivationEvent> getActivationHistory(String workflowId, IWorkflowVersion version) {
        ReadWriteLock lock = getLock(workflowId);
        lock.readLock().lock();
        try {
            List<ActivationEvent> history = activationHistory.get(workflowId);
            if (history == null) {
                return Collections.emptyList();
            }

            return history.stream()
                    .filter(e -> e.version().equals(version))
                    .sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<ActivationEvent> getWorkflowActivationHistory(String workflowId, int limit) {
        ReadWriteLock lock = getLock(workflowId);
        lock.readLock().lock();
        try {
            List<ActivationEvent> history = activationHistory.get(workflowId);
            if (history == null) {
                return Collections.emptyList();
            }

            return history.stream()
                    .sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
                    .limit(limit)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    private void recordEvent(
            String workflowId,
            IWorkflowVersion version,
            ActivationEvent.ActivationEventType eventType,
            ActivationState previousState,
            ActivationState newState,
            String performedBy,
            String reason) {

        ActivationEvent event = new ActivationEvent(
                UUID.randomUUID().toString(),
                workflowId,
                version,
                eventType,
                previousState,
                newState,
                performedBy,
                reason,
                Instant.now(),
                Collections.emptyMap());

        List<ActivationEvent> history = activationHistory.computeIfAbsent(
                workflowId, k -> Collections.synchronizedList(new ArrayList<>()));
        history.add(event);

        // Trim history if too large
        while (history.size() > MAX_HISTORY_PER_WORKFLOW) {
            history.remove(0);
        }

        log.debug("Recorded activation event: {} {} {}:{} by {}",
                eventType, previousState + "->" + newState, workflowId, version.toVersionString(), performedBy);
    }

    private int getActiveExecutionCount(String workflowId, IWorkflowVersion version) {
        if (bindingStore == null) {
            return 0;
        }

        try {
            return (int) bindingStore.findByWorkflowId(workflowId).stream()
                    .filter(binding -> binding.getBoundVersion().equals(version))
                    .filter(binding -> !binding.isTerminal())
                    .count();
        } catch (Exception e) {
            log.warn("Failed to count active executions: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Internal state entry for a version.
     */
    private record VersionStateEntry(
            IWorkflowVersion version,
            ActivationState state,
            Instant stateChangedAt,
            String stateChangedBy,
            String reason,
            IWorkflowVersion suggestedReplacement
    ) {}
}
