package com.lyshra.open.app.core.engine.state;

import com.lyshra.open.app.core.engine.state.impl.FileBasedWorkflowStateStore;
import com.lyshra.open.app.core.engine.state.impl.InMemoryWorkflowStateStore;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manager for workflow state store instances.
 * Provides a configurable way to switch between different storage backends.
 *
 * <p>Design Pattern: Factory + Singleton
 * - Creates and manages state store instances
 * - Provides single point of access to the active store
 *
 * <h2>Supported Storage Types</h2>
 * <ul>
 *   <li>MEMORY: In-memory storage (no persistence, fast)</li>
 *   <li>FILE: File-based JSON storage (persistent, simple)</li>
 *   <li>DATABASE: Database-backed storage (persistent, scalable) - future</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * Storage type can be configured via:
 * <ul>
 *   <li>System property: lyshra.workflow.state.store.type</li>
 *   <li>Environment variable: LYSHRA_WORKFLOW_STATE_STORE_TYPE</li>
 *   <li>Programmatic configuration: setStoreType()</li>
 * </ul>
 */
@Slf4j
public class LyshraOpenAppWorkflowStateStoreManager {

    private static final String STORE_TYPE_PROPERTY = "lyshra.workflow.state.store.type";
    private static final String STORE_PATH_PROPERTY = "lyshra.workflow.state.store.path";

    /**
     * Supported storage types.
     */
    public enum StoreType {
        /** In-memory storage (no persistence) */
        MEMORY,
        /** File-based JSON storage */
        FILE,
        /** Database-backed storage (future) */
        DATABASE
    }

    private volatile ILyshraOpenAppWorkflowStateStore activeStore;
    private volatile StoreType activeStoreType;
    private volatile boolean initialized = false;

    private LyshraOpenAppWorkflowStateStoreManager() {
        // Private constructor for singleton
    }

    private static final class SingletonHelper {
        private static final LyshraOpenAppWorkflowStateStoreManager INSTANCE = new LyshraOpenAppWorkflowStateStoreManager();
    }

    public static LyshraOpenAppWorkflowStateStoreManager getInstance() {
        return SingletonHelper.INSTANCE;
    }

    /**
     * Initializes the state store based on configuration.
     *
     * @return completion signal
     */
    public Mono<Void> initialize() {
        return Mono.defer(() -> {
            if (initialized) {
                return Mono.empty();
            }

            StoreType type = determineStoreType();
            log.info("Initializing workflow state store with type: {}", type);

            return createStore(type)
                    .flatMap(store -> {
                        this.activeStore = store;
                        this.activeStoreType = type;
                        return store.initialize();
                    })
                    .doOnSuccess(v -> {
                        initialized = true;
                        log.info("Workflow state store initialized successfully");
                    })
                    .doOnError(e -> log.error("Failed to initialize workflow state store", e));
        });
    }

    /**
     * Shuts down the active state store.
     *
     * @return completion signal
     */
    public Mono<Void> shutdown() {
        return Mono.defer(() -> {
            if (!initialized || activeStore == null) {
                return Mono.empty();
            }

            log.info("Shutting down workflow state store");
            return activeStore.shutdown()
                    .doFinally(signal -> {
                        initialized = false;
                        activeStore = null;
                        activeStoreType = null;
                    });
        });
    }

    /**
     * Gets the active state store.
     *
     * @return the active state store
     * @throws IllegalStateException if not initialized
     */
    public ILyshraOpenAppWorkflowStateStore getStore() {
        if (!initialized || activeStore == null) {
            // Auto-initialize with default settings
            initialize().block();
        }
        return activeStore;
    }

    /**
     * Gets the active store type.
     *
     * @return the current store type, or null if not initialized
     */
    public StoreType getActiveStoreType() {
        return activeStoreType;
    }

    /**
     * Checks if the manager is initialized.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Reconfigures the state store with a new type.
     * Shuts down the current store and initializes the new one.
     *
     * @param type the new store type
     * @return completion signal
     */
    public Mono<Void> reconfigure(StoreType type) {
        return shutdown()
                .then(Mono.defer(() -> {
                    log.info("Reconfiguring workflow state store to type: {}", type);
                    return createStore(type)
                            .flatMap(store -> {
                                this.activeStore = store;
                                this.activeStoreType = type;
                                return store.initialize();
                            })
                            .doOnSuccess(v -> {
                                initialized = true;
                                log.info("Workflow state store reconfigured successfully");
                            });
                }));
    }

    /**
     * Performs a health check on the active store.
     *
     * @return true if healthy
     */
    public Mono<Boolean> healthCheck() {
        if (!initialized || activeStore == null) {
            return Mono.just(false);
        }
        return activeStore.healthCheck();
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    private StoreType determineStoreType() {
        // Check system property
        String typeProperty = System.getProperty(STORE_TYPE_PROPERTY);
        if (typeProperty != null && !typeProperty.isBlank()) {
            try {
                return StoreType.valueOf(typeProperty.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid store type in system property: {}. Using default.", typeProperty);
            }
        }

        // Check environment variable
        String typeEnv = System.getenv("LYSHRA_WORKFLOW_STATE_STORE_TYPE");
        if (typeEnv != null && !typeEnv.isBlank()) {
            try {
                return StoreType.valueOf(typeEnv.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid store type in environment: {}. Using default.", typeEnv);
            }
        }

        // Default to FILE for persistence
        return StoreType.FILE;
    }

    private Path determineStorePath() {
        // Check system property
        String pathProperty = System.getProperty(STORE_PATH_PROPERTY);
        if (pathProperty != null && !pathProperty.isBlank()) {
            return Paths.get(pathProperty);
        }

        // Check environment variable
        String pathEnv = System.getenv("LYSHRA_WORKFLOW_STATE_STORE_PATH");
        if (pathEnv != null && !pathEnv.isBlank()) {
            return Paths.get(pathEnv);
        }

        // Default path
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".lyshra-openapp", "workflow-state");
    }

    private Mono<ILyshraOpenAppWorkflowStateStore> createStore(StoreType type) {
        return Mono.fromCallable(() -> {
            switch (type) {
                case MEMORY:
                    log.info("Creating in-memory workflow state store");
                    return InMemoryWorkflowStateStore.getInstance();

                case FILE:
                    Path storePath = determineStorePath();
                    log.info("Creating file-based workflow state store at: {}", storePath);
                    return FileBasedWorkflowStateStore.getInstance(storePath);

                case DATABASE:
                    // Database implementation would go here
                    log.warn("Database store not yet implemented. Falling back to file-based.");
                    return FileBasedWorkflowStateStore.getInstance(determineStorePath());

                default:
                    throw new IllegalArgumentException("Unknown store type: " + type);
            }
        });
    }
}
