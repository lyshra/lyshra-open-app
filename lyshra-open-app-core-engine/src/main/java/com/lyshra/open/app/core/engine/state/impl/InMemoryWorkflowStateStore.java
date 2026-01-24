package com.lyshra.open.app.core.engine.state.impl;

import com.lyshra.open.app.core.engine.state.ILyshraOpenAppWorkflowStateStore;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppWorkflowExecutionState;
import com.lyshra.open.app.integration.models.workflow.LyshraOpenAppWorkflowInstanceState;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of the workflow state store.
 * Suitable for testing and development environments where
 * persistence across restarts is not required.
 *
 * <p>Design Pattern: Repository Pattern with in-memory storage
 *
 * <h2>Characteristics</h2>
 * <ul>
 *   <li>Fast access - no I/O overhead</li>
 *   <li>No external dependencies</li>
 *   <li>State is lost on application restart</li>
 *   <li>Suitable for testing and development</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * Uses ConcurrentHashMap for thread-safe operations.
 */
@Slf4j
public class InMemoryWorkflowStateStore implements ILyshraOpenAppWorkflowStateStore {

    private final Map<String, LyshraOpenAppWorkflowInstanceState> store = new ConcurrentHashMap<>();
    private final Map<LyshraOpenAppWorkflowExecutionState, Map<String, Boolean>> statusIndex = new ConcurrentHashMap<>();
    private final Map<String, String> humanTaskIndex = new ConcurrentHashMap<>();
    private final Map<String, String> correlationIndex = new ConcurrentHashMap<>();

    private volatile boolean initialized = false;

    @Override
    public Mono<Void> initialize() {
        return Mono.fromRunnable(() -> {
            log.info("Initializing in-memory workflow state store");
            initialized = true;
        });
    }

    @Override
    public Mono<Void> shutdown() {
        return Mono.fromRunnable(() -> {
            log.info("Shutting down in-memory workflow state store");
            store.clear();
            statusIndex.clear();
            humanTaskIndex.clear();
            correlationIndex.clear();
            initialized = false;
        });
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return Mono.just(initialized);
    }

    @Override
    public Mono<LyshraOpenAppWorkflowInstanceState> save(LyshraOpenAppWorkflowInstanceState state) {
        return Mono.fromCallable(() -> {
            // Remove old index entries if updating existing state
            LyshraOpenAppWorkflowInstanceState existing = store.get(state.getInstanceId());
            if (existing != null) {
                removeFromIndexes(existing);
            }

            store.put(state.getInstanceId(), state);
            indexState(state);

            log.debug("Saved workflow state: {} (status: {})", state.getInstanceId(), state.getStatus());
            return state;
        });
    }

    @Override
    public Mono<LyshraOpenAppWorkflowInstanceState> findById(String instanceId) {
        return Mono.justOrEmpty(store.get(instanceId));
    }

    @Override
    public Mono<Boolean> deleteById(String instanceId) {
        return Mono.fromCallable(() -> {
            LyshraOpenAppWorkflowInstanceState removed = store.remove(instanceId);
            if (removed != null) {
                removeFromIndexes(removed);
                log.debug("Deleted workflow state: {}", instanceId);
                return true;
            }
            return false;
        });
    }

    @Override
    public Mono<Boolean> exists(String instanceId) {
        return Mono.just(store.containsKey(instanceId));
    }

    @Override
    public Flux<LyshraOpenAppWorkflowInstanceState> findByStatus(LyshraOpenAppWorkflowExecutionState status) {
        return Mono.fromCallable(() -> {
            Map<String, Boolean> instanceIds = statusIndex.getOrDefault(status, Map.of());
            return instanceIds.keySet().stream()
                    .map(store::get)
                    .filter(state -> state != null)
                    .toList();
        }).flatMapMany(Flux::fromIterable);
    }

    @Override
    public Flux<LyshraOpenAppWorkflowInstanceState> findByStatusIn(List<LyshraOpenAppWorkflowExecutionState> statuses) {
        return Flux.fromIterable(statuses)
                .flatMap(this::findByStatus)
                .distinct(LyshraOpenAppWorkflowInstanceState::getInstanceId);
    }

    @Override
    public Flux<LyshraOpenAppWorkflowInstanceState> findByWorkflowDefinitionId(String workflowDefinitionId) {
        return Flux.fromIterable(store.values())
                .filter(state -> workflowDefinitionId.equals(state.getWorkflowDefinitionId()));
    }

    @Override
    public Flux<LyshraOpenAppWorkflowInstanceState> findByBusinessKey(String businessKey) {
        return Flux.fromIterable(store.values())
                .filter(state -> businessKey.equals(state.getBusinessKey()));
    }

    @Override
    public Mono<LyshraOpenAppWorkflowInstanceState> findByCorrelationId(String correlationId) {
        String instanceId = correlationIndex.get(correlationId);
        return instanceId != null ? Mono.justOrEmpty(store.get(instanceId)) : Mono.empty();
    }

    @Override
    public Mono<LyshraOpenAppWorkflowInstanceState> findByHumanTaskId(String humanTaskId) {
        String instanceId = humanTaskIndex.get(humanTaskId);
        return instanceId != null ? Mono.justOrEmpty(store.get(instanceId)) : Mono.empty();
    }

    @Override
    public Flux<LyshraOpenAppWorkflowInstanceState> findByTenantId(String tenantId) {
        return Flux.fromIterable(store.values())
                .filter(state -> tenantId.equals(state.getTenantId()));
    }

    @Override
    public Flux<LyshraOpenAppWorkflowInstanceState> findSuspendedBefore(Instant before) {
        return Flux.fromIterable(store.values())
                .filter(state -> state.getSuspendedAt() != null && state.getSuspendedAt().isBefore(before))
                .filter(state -> state.getStatus() == LyshraOpenAppWorkflowExecutionState.WAITING
                        || state.getStatus() == LyshraOpenAppWorkflowExecutionState.PAUSED);
    }

    @Override
    public Flux<LyshraOpenAppWorkflowInstanceState> findScheduledForResumeBefore(Instant before) {
        return Flux.fromIterable(store.values())
                .filter(state -> state.getScheduledResumeAt() != null && state.getScheduledResumeAt().isBefore(before));
    }

    @Override
    public Flux<LyshraOpenAppWorkflowInstanceState> findCreatedBetween(Instant from, Instant to) {
        return Flux.fromIterable(store.values())
                .filter(state -> state.getCreatedAt() != null)
                .filter(state -> !state.getCreatedAt().isBefore(from) && state.getCreatedAt().isBefore(to));
    }

    @Override
    public Flux<LyshraOpenAppWorkflowInstanceState> saveAll(List<LyshraOpenAppWorkflowInstanceState> states) {
        return Flux.fromIterable(states).flatMap(this::save);
    }

    @Override
    public Mono<Long> deleteByStatus(LyshraOpenAppWorkflowExecutionState status) {
        return findByStatus(status)
                .flatMap(state -> deleteById(state.getInstanceId()))
                .filter(deleted -> deleted)
                .count();
    }

    @Override
    public Mono<Long> deleteOlderThan(Instant before, List<LyshraOpenAppWorkflowExecutionState> statuses) {
        return Flux.fromIterable(store.values())
                .filter(state -> statuses.contains(state.getStatus()))
                .filter(state -> state.getUpdatedAt() != null && state.getUpdatedAt().isBefore(before))
                .flatMap(state -> deleteById(state.getInstanceId()))
                .filter(deleted -> deleted)
                .count();
    }

    @Override
    public Mono<Long> countByStatus(LyshraOpenAppWorkflowExecutionState status) {
        Map<String, Boolean> instanceIds = statusIndex.getOrDefault(status, Map.of());
        return Mono.just((long) instanceIds.size());
    }

    @Override
    public Flux<String> findDistinctWorkflowDefinitionIds() {
        return Flux.fromIterable(store.values())
                .map(LyshraOpenAppWorkflowInstanceState::getWorkflowDefinitionId)
                .filter(id -> id != null)
                .distinct();
    }

    private void indexState(LyshraOpenAppWorkflowInstanceState state) {
        if (state.getStatus() != null) {
            statusIndex.computeIfAbsent(state.getStatus(), k -> new ConcurrentHashMap<>())
                    .put(state.getInstanceId(), Boolean.TRUE);
        }
        if (state.getHumanTaskId() != null) {
            humanTaskIndex.put(state.getHumanTaskId(), state.getInstanceId());
        }
        if (state.getCorrelationId() != null) {
            correlationIndex.put(state.getCorrelationId(), state.getInstanceId());
        }
    }

    private void removeFromIndexes(LyshraOpenAppWorkflowInstanceState state) {
        if (state.getStatus() != null) {
            Map<String, Boolean> statusMap = statusIndex.get(state.getStatus());
            if (statusMap != null) {
                statusMap.remove(state.getInstanceId());
            }
        }
        if (state.getHumanTaskId() != null) {
            humanTaskIndex.remove(state.getHumanTaskId());
        }
        if (state.getCorrelationId() != null) {
            correlationIndex.remove(state.getCorrelationId());
        }
    }

    /**
     * Gets the total number of stored instances (for testing/debugging).
     */
    public int size() {
        return store.size();
    }

    /**
     * Clears all stored instances (for testing).
     */
    public void clear() {
        store.clear();
        statusIndex.clear();
        humanTaskIndex.clear();
        correlationIndex.clear();
    }

    // Singleton
    private static final InMemoryWorkflowStateStore INSTANCE = new InMemoryWorkflowStateStore();

    public static InMemoryWorkflowStateStore getInstance() {
        return INSTANCE;
    }
}
