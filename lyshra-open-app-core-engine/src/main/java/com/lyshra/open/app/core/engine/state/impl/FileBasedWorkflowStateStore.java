package com.lyshra.open.app.core.engine.state.impl;

import com.lyshra.open.app.core.engine.state.ILyshraOpenAppWorkflowStateStore;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppWorkflowExecutionState;
import com.lyshra.open.app.integration.models.workflow.LyshraOpenAppWorkflowInstanceState;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * File-based implementation of the workflow state store.
 * Stores each workflow instance as a JSON file in a configured directory.
 *
 * <p>Design Pattern: Repository Pattern with file-based persistence
 *
 * <h2>Storage Structure</h2>
 * <pre>
 * {baseDir}/
 *   ├── workflow-instances/
 *   │   ├── {instanceId}.json
 *   │   ├── {instanceId}.json
 *   │   └── ...
 *   └── index/
 *       ├── by-status/
 *       │   ├── WAITING.idx
 *       │   └── RUNNING.idx
 *       └── by-task/
 *           └── {taskId}.idx
 * </pre>
 *
 * <h2>Characteristics</h2>
 * <ul>
 *   <li>Simple setup - no database required</li>
 *   <li>Human-readable JSON files</li>
 *   <li>Suitable for development and small-scale deployments</li>
 *   <li>Uses in-memory cache for fast reads</li>
 * </ul>
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li>Not suitable for high-throughput production use</li>
 *   <li>No built-in clustering support</li>
 *   <li>Index maintenance on restart required</li>
 * </ul>
 */
@Slf4j
public class FileBasedWorkflowStateStore implements ILyshraOpenAppWorkflowStateStore {

    private static final String INSTANCES_DIR = "workflow-instances";
    private static final String STATE_EXTENSION = ".state";

    private final Path baseDir;
    private final Path instancesDir;

    // In-memory cache for fast lookups
    private final Map<String, LyshraOpenAppWorkflowInstanceState> cache = new ConcurrentHashMap<>();

    // Index for quick lookups by various fields
    private final Map<LyshraOpenAppWorkflowExecutionState, Map<String, Boolean>> statusIndex = new ConcurrentHashMap<>();
    private final Map<String, String> humanTaskIndex = new ConcurrentHashMap<>();
    private final Map<String, String> correlationIndex = new ConcurrentHashMap<>();

    private volatile boolean initialized = false;

    /**
     * Creates a file-based state store with default directory.
     */
    public FileBasedWorkflowStateStore() {
        this(getDefaultBaseDir());
    }

    /**
     * Creates a file-based state store with specified directory.
     *
     * @param baseDir the base directory for state files
     */
    public FileBasedWorkflowStateStore(Path baseDir) {
        this.baseDir = baseDir;
        this.instancesDir = baseDir.resolve(INSTANCES_DIR);
    }

    private static Path getDefaultBaseDir() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".lyshra-openapp", "workflow-state");
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    public Mono<Void> initialize() {
        return Mono.fromCallable(() -> {
            log.info("Initializing file-based workflow state store at: {}", baseDir);

            // Create directories if they don't exist
            Files.createDirectories(instancesDir);

            // Load existing states into cache and build indexes
            loadExistingStates();

            initialized = true;
            log.info("File-based workflow state store initialized. Loaded {} instances.", cache.size());
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private void loadExistingStates() {
        try (Stream<Path> files = Files.list(instancesDir)) {
            files.filter(path -> path.toString().endsWith(STATE_EXTENSION))
                    .forEach(this::loadStateFile);
        } catch (IOException e) {
            log.warn("Could not load existing states: {}", e.getMessage());
        }
    }

    private void loadStateFile(Path file) {
        try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(
                new java.io.BufferedInputStream(Files.newInputStream(file)))) {
            LyshraOpenAppWorkflowInstanceState state =
                    (LyshraOpenAppWorkflowInstanceState) ois.readObject();
            cache.put(state.getInstanceId(), state);
            indexState(state);
            log.debug("Loaded workflow state: {}", state.getInstanceId());
        } catch (IOException | ClassNotFoundException e) {
            log.warn("Could not load state file {}: {}", file, e.getMessage());
        }
    }

    @Override
    public Mono<Void> shutdown() {
        return Mono.fromRunnable(() -> {
            log.info("Shutting down file-based workflow state store");
            // Flush any pending writes (all writes are synchronous in this implementation)
            cache.clear();
            statusIndex.clear();
            humanTaskIndex.clear();
            correlationIndex.clear();
            initialized = false;
        });
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return Mono.fromCallable(() -> initialized && Files.isWritable(instancesDir));
    }

    // ========================================================================
    // CORE CRUD OPERATIONS
    // ========================================================================

    @Override
    public Mono<LyshraOpenAppWorkflowInstanceState> save(LyshraOpenAppWorkflowInstanceState state) {
        return Mono.fromCallable(() -> {
            ensureInitialized();

            // Remove old index entries if updating existing state
            LyshraOpenAppWorkflowInstanceState existing = cache.get(state.getInstanceId());
            if (existing != null) {
                removeFromIndexes(existing);
            }

            // Write to file using Java serialization
            Path stateFile = getStateFilePath(state.getInstanceId());
            try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(
                    new java.io.BufferedOutputStream(Files.newOutputStream(stateFile)))) {
                oos.writeObject(state);
            }

            // Update cache and indexes
            cache.put(state.getInstanceId(), state);
            indexState(state);

            log.debug("Saved workflow state: {} (status: {})", state.getInstanceId(), state.getStatus());
            return state;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<LyshraOpenAppWorkflowInstanceState> findById(String instanceId) {
        return Mono.fromCallable(() -> {
            ensureInitialized();

            // Try cache first
            LyshraOpenAppWorkflowInstanceState cached = cache.get(instanceId);
            if (cached != null) {
                return cached;
            }

            // Try loading from file
            Path stateFile = getStateFilePath(instanceId);
            if (Files.exists(stateFile)) {
                try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(
                        new java.io.BufferedInputStream(Files.newInputStream(stateFile)))) {
                    LyshraOpenAppWorkflowInstanceState state =
                            (LyshraOpenAppWorkflowInstanceState) ois.readObject();
                    cache.put(instanceId, state);
                    indexState(state);
                    return state;
                } catch (ClassNotFoundException e) {
                    log.warn("Could not deserialize state file {}: {}", stateFile, e.getMessage());
                }
            }

            return null;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> deleteById(String instanceId) {
        return Mono.fromCallable(() -> {
            ensureInitialized();

            LyshraOpenAppWorkflowInstanceState existing = cache.remove(instanceId);
            if (existing != null) {
                removeFromIndexes(existing);
            }

            Path stateFile = getStateFilePath(instanceId);
            boolean deleted = Files.deleteIfExists(stateFile);

            if (deleted) {
                log.debug("Deleted workflow state: {}", instanceId);
            }
            return deleted;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> exists(String instanceId) {
        return Mono.fromCallable(() -> {
            ensureInitialized();
            return cache.containsKey(instanceId) || Files.exists(getStateFilePath(instanceId));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ========================================================================
    // QUERY OPERATIONS
    // ========================================================================

    @Override
    public Flux<LyshraOpenAppWorkflowInstanceState> findByStatus(LyshraOpenAppWorkflowExecutionState status) {
        return Mono.fromCallable(() -> {
            ensureInitialized();
            Map<String, Boolean> instanceIds = statusIndex.getOrDefault(status, Map.of());
            return instanceIds.keySet().stream()
                    .map(cache::get)
                    .filter(state -> state != null)
                    .toList();
        }).subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Flux<LyshraOpenAppWorkflowInstanceState> findByStatusIn(List<LyshraOpenAppWorkflowExecutionState> statuses) {
        return Flux.fromIterable(statuses)
                .flatMap(this::findByStatus)
                .distinct(LyshraOpenAppWorkflowInstanceState::getInstanceId);
    }

    @Override
    public Flux<LyshraOpenAppWorkflowInstanceState> findByWorkflowDefinitionId(String workflowDefinitionId) {
        return Flux.fromIterable(cache.values())
                .filter(state -> workflowDefinitionId.equals(state.getWorkflowDefinitionId()));
    }

    @Override
    public Flux<LyshraOpenAppWorkflowInstanceState> findByBusinessKey(String businessKey) {
        return Flux.fromIterable(cache.values())
                .filter(state -> businessKey.equals(state.getBusinessKey()));
    }

    @Override
    public Mono<LyshraOpenAppWorkflowInstanceState> findByCorrelationId(String correlationId) {
        return Mono.fromCallable(() -> {
            ensureInitialized();
            String instanceId = correlationIndex.get(correlationId);
            return instanceId != null ? cache.get(instanceId) : null;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<LyshraOpenAppWorkflowInstanceState> findByHumanTaskId(String humanTaskId) {
        return Mono.fromCallable(() -> {
            ensureInitialized();
            String instanceId = humanTaskIndex.get(humanTaskId);
            return instanceId != null ? cache.get(instanceId) : null;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<LyshraOpenAppWorkflowInstanceState> findByTenantId(String tenantId) {
        return Flux.fromIterable(cache.values())
                .filter(state -> tenantId.equals(state.getTenantId()));
    }

    // ========================================================================
    // TIME-BASED QUERIES
    // ========================================================================

    @Override
    public Flux<LyshraOpenAppWorkflowInstanceState> findSuspendedBefore(Instant before) {
        return Flux.fromIterable(cache.values())
                .filter(state -> state.getSuspendedAt() != null && state.getSuspendedAt().isBefore(before))
                .filter(state -> state.getStatus() == LyshraOpenAppWorkflowExecutionState.WAITING
                        || state.getStatus() == LyshraOpenAppWorkflowExecutionState.PAUSED);
    }

    @Override
    public Flux<LyshraOpenAppWorkflowInstanceState> findScheduledForResumeBefore(Instant before) {
        return Flux.fromIterable(cache.values())
                .filter(state -> state.getScheduledResumeAt() != null && state.getScheduledResumeAt().isBefore(before));
    }

    @Override
    public Flux<LyshraOpenAppWorkflowInstanceState> findCreatedBetween(Instant from, Instant to) {
        return Flux.fromIterable(cache.values())
                .filter(state -> state.getCreatedAt() != null)
                .filter(state -> !state.getCreatedAt().isBefore(from) && state.getCreatedAt().isBefore(to));
    }

    // ========================================================================
    // BULK OPERATIONS
    // ========================================================================

    @Override
    public Flux<LyshraOpenAppWorkflowInstanceState> saveAll(List<LyshraOpenAppWorkflowInstanceState> states) {
        return Flux.fromIterable(states)
                .flatMap(this::save);
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
        return Flux.fromIterable(cache.values())
                .filter(state -> statuses.contains(state.getStatus()))
                .filter(state -> state.getUpdatedAt() != null && state.getUpdatedAt().isBefore(before))
                .flatMap(state -> deleteById(state.getInstanceId()))
                .filter(deleted -> deleted)
                .count();
    }

    // ========================================================================
    // STATISTICS
    // ========================================================================

    @Override
    public Mono<Long> countByStatus(LyshraOpenAppWorkflowExecutionState status) {
        return Mono.fromCallable(() -> {
            ensureInitialized();
            Map<String, Boolean> instanceIds = statusIndex.getOrDefault(status, Map.of());
            return (long) instanceIds.size();
        });
    }

    @Override
    public Flux<String> findDistinctWorkflowDefinitionIds() {
        return Flux.fromIterable(cache.values())
                .map(LyshraOpenAppWorkflowInstanceState::getWorkflowDefinitionId)
                .filter(id -> id != null)
                .distinct();
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("State store not initialized. Call initialize() first.");
        }
    }

    private Path getStateFilePath(String instanceId) {
        return instancesDir.resolve(instanceId + STATE_EXTENSION);
    }

    private void indexState(LyshraOpenAppWorkflowInstanceState state) {
        // Index by status
        if (state.getStatus() != null) {
            statusIndex.computeIfAbsent(state.getStatus(), k -> new ConcurrentHashMap<>())
                    .put(state.getInstanceId(), Boolean.TRUE);
        }

        // Index by human task
        if (state.getHumanTaskId() != null) {
            humanTaskIndex.put(state.getHumanTaskId(), state.getInstanceId());
        }

        // Index by correlation ID
        if (state.getCorrelationId() != null) {
            correlationIndex.put(state.getCorrelationId(), state.getInstanceId());
        }
    }

    private void removeFromIndexes(LyshraOpenAppWorkflowInstanceState state) {
        // Remove from status index
        if (state.getStatus() != null) {
            Map<String, Boolean> statusMap = statusIndex.get(state.getStatus());
            if (statusMap != null) {
                statusMap.remove(state.getInstanceId());
            }
        }

        // Remove from human task index
        if (state.getHumanTaskId() != null) {
            humanTaskIndex.remove(state.getHumanTaskId());
        }

        // Remove from correlation index
        if (state.getCorrelationId() != null) {
            correlationIndex.remove(state.getCorrelationId());
        }
    }

    // ========================================================================
    // SINGLETON (Optional - can also be instantiated directly)
    // ========================================================================

    private static volatile FileBasedWorkflowStateStore instance;

    public static FileBasedWorkflowStateStore getInstance() {
        if (instance == null) {
            synchronized (FileBasedWorkflowStateStore.class) {
                if (instance == null) {
                    instance = new FileBasedWorkflowStateStore();
                }
            }
        }
        return instance;
    }

    public static FileBasedWorkflowStateStore getInstance(Path baseDir) {
        if (instance == null) {
            synchronized (FileBasedWorkflowStateStore.class) {
                if (instance == null) {
                    instance = new FileBasedWorkflowStateStore(baseDir);
                }
            }
        }
        return instance;
    }
}
