package com.lyshra.open.app.mongodb.plugin.connection;

import com.lyshra.open.app.mongodb.plugin.config.MongoConnectionConfig;
import com.lyshra.open.app.mongodb.plugin.config.MongoConnectionPoolSettings;
import com.lyshra.open.app.mongodb.plugin.config.MongoReadConcern;
import com.lyshra.open.app.mongodb.plugin.config.MongoReadPreference;
import com.lyshra.open.app.mongodb.plugin.config.MongoServerSettings;
import com.lyshra.open.app.mongodb.plugin.error.MongoProcessorErrorCodes;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * MongoDB Client Manager - Singleton responsible for managing MongoClient lifecycle.
 * <p>
 * Features:
 * - Maintains a cache of MongoClient instances per configuration key
 * - Handles connection pooling configuration
 * - Supports both reactive and sync operations
 * - Thread-safe client management
 * - Proper shutdown handling
 */
public final class MongoClientManager {

    private static final Logger log = LoggerFactory.getLogger(MongoClientManager.class);

    private static volatile MongoClientManager instance;
    private static final Object LOCK = new Object();

    private final ConcurrentHashMap<String, MongoClient> clientCache;
    private final ConcurrentHashMap<String, MongoConnectionConfig> configCache;

    private MongoClientManager() {
        this.clientCache = new ConcurrentHashMap<>();
        this.configCache = new ConcurrentHashMap<>();

        // Register shutdown hook for proper cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownAll));
    }

    /**
     * Get the singleton instance of MongoClientManager.
     *
     * @return The MongoClientManager instance
     */
    public static MongoClientManager getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new MongoClientManager();
                }
            }
        }
        return instance;
    }

    /**
     * Register a connection configuration.
     *
     * @param configKey The configuration key
     * @param config    The connection configuration
     */
    public void registerConfig(String configKey, MongoConnectionConfig config) {
        configCache.put(configKey, config);
        log.debug("Registered MongoDB configuration for key: {}", configKey);
    }

    /**
     * Get a MongoClient for the given configuration key.
     * Creates a new client if one doesn't exist for this configuration.
     *
     * @param configKey The configuration key
     * @return A MongoClient instance
     * @throws LyshraOpenAppProcessorRuntimeException if configuration not found or connection fails
     */
    public MongoClient getClient(String configKey) {
        return clientCache.computeIfAbsent(configKey, key -> {
            MongoConnectionConfig config = configCache.get(key);
            if (config == null) {
                throw new LyshraOpenAppProcessorRuntimeException(
                        MongoProcessorErrorCodes.CONNECTION_CONFIG_NOT_FOUND,
                        Map.of("configKey", key)
                );
            }
            return createClient(config);
        });
    }

    /**
     * Get a MongoClient using the provided configuration directly.
     * This method creates a new client each time; prefer using getClient(configKey) for caching.
     *
     * @param config The connection configuration
     * @return A MongoClient instance
     */
    public MongoClient getClient(MongoConnectionConfig config) {
        String cacheKey = generateCacheKey(config);
        return clientCache.computeIfAbsent(cacheKey, key -> createClient(config));
    }

    /**
     * Get a MongoDatabase for the given configuration key using the default database.
     *
     * @param configKey The configuration key
     * @return A MongoDatabase instance
     */
    public MongoDatabase getDatabase(String configKey) {
        MongoClient client = getClient(configKey);
        MongoConnectionConfig config = configCache.get(configKey);
        if (config == null) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.CONNECTION_CONFIG_NOT_FOUND,
                    Map.of("configKey", configKey)
            );
        }
        return client.getDatabase(config.getDatabase());
    }

    /**
     * Get a MongoDatabase for the given configuration key and database name.
     *
     * @param configKey    The configuration key
     * @param databaseName The database name (optional, uses config default if null)
     * @return A MongoDatabase instance
     */
    public MongoDatabase getDatabase(String configKey, String databaseName) {
        MongoClient client = getClient(configKey);
        String dbName = databaseName;
        if (dbName == null || dbName.isBlank()) {
            MongoConnectionConfig config = configCache.get(configKey);
            if (config != null) {
                dbName = config.getDatabase();
            }
        }
        if (dbName == null || dbName.isBlank()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.INVALID_DATABASE_NAME,
                    Map.of("database", "null or empty")
            );
        }
        return client.getDatabase(dbName);
    }

    /**
     * Get the connection configuration for the given key.
     *
     * @param configKey The configuration key
     * @return Optional containing the configuration if found
     */
    public Optional<MongoConnectionConfig> getConfig(String configKey) {
        return Optional.ofNullable(configCache.get(configKey));
    }

    /**
     * Close a specific client by configuration key.
     *
     * @param configKey The configuration key
     */
    public void closeClient(String configKey) {
        MongoClient client = clientCache.remove(configKey);
        if (client != null) {
            try {
                client.close();
                log.debug("Closed MongoDB client for key: {}", configKey);
            } catch (Exception e) {
                log.warn("Error closing MongoDB client for key: {}", configKey, e);
            }
        }
    }

    /**
     * Shutdown all clients and clear caches.
     */
    public void shutdownAll() {
        log.info("Shutting down all MongoDB clients...");
        clientCache.forEach((key, client) -> {
            try {
                client.close();
                log.debug("Closed MongoDB client for key: {}", key);
            } catch (Exception e) {
                log.warn("Error closing MongoDB client for key: {}", key, e);
            }
        });
        clientCache.clear();
        configCache.clear();
        log.info("All MongoDB clients shut down");
    }

    /**
     * Create a MongoClient from the configuration.
     *
     * @param config The connection configuration
     * @return A new MongoClient instance
     */
    private MongoClient createClient(MongoConnectionConfig config) {
        try {
            MongoClientSettings.Builder settingsBuilder = MongoClientSettings.builder();

            // Apply connection string
            ConnectionString connectionString = new ConnectionString(config.getConnectionString());
            settingsBuilder.applyConnectionString(connectionString);

            // Apply credentials if username/password provided separately
            if (config.getUsername() != null && !config.getUsername().isBlank() &&
                config.getPassword() != null && !config.getPassword().isBlank()) {
                MongoCredential credential = MongoCredential.createCredential(
                        config.getUsername(),
                        config.getAuthDatabase() != null ? config.getAuthDatabase() : "admin",
                        config.getPassword().toCharArray()
                );
                settingsBuilder.credential(credential);
            }

            // Apply connection pool settings
            applyConnectionPoolSettings(settingsBuilder, config.getConnectionPoolSettings());

            // Apply server settings
            applyServerSettings(settingsBuilder, config.getServerSettings());

            // Apply read preference
            settingsBuilder.readPreference(toMongoReadPreference(config.getReadPreference()));

            // Apply read concern
            settingsBuilder.readConcern(toMongoReadConcern(config.getReadConcern()));

            // Apply write concern
            settingsBuilder.writeConcern(parseWriteConcern(config.getWriteConcern()));

            // Apply retry settings
            settingsBuilder.retryWrites(
                    config.getRetryWrites() != null ? config.getRetryWrites() : true
            );
            settingsBuilder.retryReads(
                    config.getRetryReads() != null ? config.getRetryReads() : true
            );

            MongoClientSettings settings = settingsBuilder.build();
            MongoClient client = MongoClients.create(settings);

            log.info("Created MongoDB client for connection: {}",
                    maskConnectionString(config.getConnectionString()));

            return client;

        } catch (IllegalArgumentException e) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.INVALID_CONNECTION_STRING,
                    Map.of("message", e.getMessage()),
                    e,
                    null
            );
        } catch (Exception e) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.CONNECTION_FAILED,
                    Map.of("message", e.getMessage()),
                    e,
                    null
            );
        }
    }

    /**
     * Apply connection pool settings to the builder.
     */
    private void applyConnectionPoolSettings(
            MongoClientSettings.Builder builder,
            MongoConnectionPoolSettings poolSettings) {

        if (poolSettings == null) {
            return;
        }

        builder.applyToConnectionPoolSettings(poolBuilder -> {
            if (poolSettings.getMaxPoolSize() != null) {
                poolBuilder.maxSize(poolSettings.getMaxPoolSize());
            }
            if (poolSettings.getMinPoolSize() != null) {
                poolBuilder.minSize(poolSettings.getMinPoolSize());
            }
            if (poolSettings.getMaxWaitTimeMs() != null && poolSettings.getMaxWaitTimeMs() > 0) {
                poolBuilder.maxWaitTime(poolSettings.getMaxWaitTimeMs(), TimeUnit.MILLISECONDS);
            }
            if (poolSettings.getMaxConnectionIdleTimeMs() != null && poolSettings.getMaxConnectionIdleTimeMs() > 0) {
                poolBuilder.maxConnectionIdleTime(poolSettings.getMaxConnectionIdleTimeMs(), TimeUnit.MILLISECONDS);
            }
            if (poolSettings.getMaxConnectionLifeTimeMs() != null && poolSettings.getMaxConnectionLifeTimeMs() > 0) {
                poolBuilder.maxConnectionLifeTime(poolSettings.getMaxConnectionLifeTimeMs(), TimeUnit.MILLISECONDS);
            }
        });
    }

    /**
     * Apply server settings to the builder.
     */
    private void applyServerSettings(
            MongoClientSettings.Builder builder,
            MongoServerSettings serverSettings) {

        if (serverSettings == null) {
            return;
        }

        builder.applyToServerSettings(serverBuilder -> {
            if (serverSettings.getHeartbeatFrequencyMs() != null && serverSettings.getHeartbeatFrequencyMs() > 0) {
                serverBuilder.heartbeatFrequency(serverSettings.getHeartbeatFrequencyMs(), TimeUnit.MILLISECONDS);
            }
        });

        builder.applyToClusterSettings(clusterBuilder -> {
            if (serverSettings.getServerSelectionTimeoutMs() != null && serverSettings.getServerSelectionTimeoutMs() > 0) {
                clusterBuilder.serverSelectionTimeout(serverSettings.getServerSelectionTimeoutMs(), TimeUnit.MILLISECONDS);
            }
        });
    }

    /**
     * Convert plugin ReadPreference to MongoDB ReadPreference.
     */
    private ReadPreference toMongoReadPreference(MongoReadPreference readPreference) {
        if (readPreference == null) {
            return ReadPreference.primary();
        }
        return switch (readPreference) {
            case PRIMARY -> ReadPreference.primary();
            case PRIMARY_PREFERRED -> ReadPreference.primaryPreferred();
            case SECONDARY -> ReadPreference.secondary();
            case SECONDARY_PREFERRED -> ReadPreference.secondaryPreferred();
            case NEAREST -> ReadPreference.nearest();
        };
    }

    /**
     * Convert plugin ReadConcern to MongoDB ReadConcern.
     */
    private ReadConcern toMongoReadConcern(MongoReadConcern readConcern) {
        if (readConcern == null) {
            return ReadConcern.LOCAL;
        }
        return switch (readConcern) {
            case LOCAL -> ReadConcern.LOCAL;
            case MAJORITY -> ReadConcern.MAJORITY;
            case LINEARIZABLE -> ReadConcern.LINEARIZABLE;
            case SNAPSHOT -> ReadConcern.SNAPSHOT;
            case AVAILABLE -> ReadConcern.AVAILABLE;
        };
    }

    /**
     * Parse write concern string to MongoDB WriteConcern.
     * Supports formats: "w:1", "w:majority", "w:2", etc.
     */
    private WriteConcern parseWriteConcern(String writeConcernStr) {
        if (writeConcernStr == null || writeConcernStr.isBlank()) {
            return WriteConcern.W1;
        }

        String normalized = writeConcernStr.toLowerCase().replace(" ", "");

        if (normalized.startsWith("w:")) {
            String value = normalized.substring(2);
            return switch (value) {
                case "1" -> WriteConcern.W1;
                case "2" -> WriteConcern.W2;
                case "3" -> WriteConcern.W3;
                case "majority" -> WriteConcern.MAJORITY;
                case "acknowledged" -> WriteConcern.ACKNOWLEDGED;
                case "unacknowledged" -> WriteConcern.UNACKNOWLEDGED;
                case "journaled" -> WriteConcern.JOURNALED;
                default -> {
                    try {
                        int w = Integer.parseInt(value);
                        yield new WriteConcern(w);
                    } catch (NumberFormatException e) {
                        yield new WriteConcern(value);
                    }
                }
            };
        }

        return WriteConcern.W1;
    }

    /**
     * Generate a cache key from the configuration.
     */
    private String generateCacheKey(MongoConnectionConfig config) {
        return config.getConnectionString() + ":" + config.getDatabase();
    }

    /**
     * Mask sensitive parts of connection string for logging.
     */
    private String maskConnectionString(String connectionString) {
        if (connectionString == null) {
            return "null";
        }
        // Mask password in connection string
        return connectionString.replaceAll("://[^:]+:[^@]+@", "://***:***@");
    }
}
