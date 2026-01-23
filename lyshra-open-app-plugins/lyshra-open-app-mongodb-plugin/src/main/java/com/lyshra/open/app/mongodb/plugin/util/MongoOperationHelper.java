package com.lyshra.open.app.mongodb.plugin.util;

import com.lyshra.open.app.mongodb.plugin.config.MongoConnectionConfig;
import com.lyshra.open.app.mongodb.plugin.connection.MongoClientManager;
import com.lyshra.open.app.mongodb.plugin.converter.BsonTypeConverter;
import com.lyshra.open.app.mongodb.plugin.error.MongoProcessorErrorCodes;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginFacade;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import org.bson.Document;
import org.bson.conversions.Bson;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Helper utility for MongoDB operations.
 * Provides common functionality used by all MongoDB processors.
 */
public final class MongoOperationHelper {

    private MongoOperationHelper() {
        // Utility class - prevent instantiation
    }

    /**
     * Get MongoDB collection for the given configuration.
     *
     * @param configKey      The configuration key
     * @param databaseName   Optional database name (uses config default if null)
     * @param collectionName The collection name
     * @param context        The execution context
     * @param facade         The plugin facade
     * @return A MongoCollection instance
     */
    public static MongoCollection<Document> getCollection(
            String configKey,
            String databaseName,
            String collectionName,
            ILyshraOpenAppContext context,
            ILyshraOpenAppPluginFacade facade) {

        // Validate collection name
        validateCollectionName(collectionName);

        // Get client manager and connection config
        MongoClientManager manager = MongoClientManager.getInstance();

        // Register config from context if available
        registerConfigFromContext(configKey, context, facade, manager);

        // Get database
        MongoDatabase database = manager.getDatabase(configKey, databaseName);

        return database.getCollection(collectionName);
    }

    /**
     * Register connection configuration from context if not already registered.
     *
     * @param configKey The configuration key
     * @param context   The execution context
     * @param facade    The plugin facade
     * @param manager   The MongoClientManager instance
     */
    @SuppressWarnings("unchecked")
    public static void registerConfigFromContext(
            String configKey,
            ILyshraOpenAppContext context,
            ILyshraOpenAppPluginFacade facade,
            MongoClientManager manager) {

        // Check if already registered
        if (manager.getConfig(configKey).isPresent()) {
            return;
        }

        // Try to get config from context variables
        Object configObj = context.getVariable(configKey);
        if (configObj == null) {
            // Try from data
            if (context.getData() instanceof Map) {
                Map<String, Object> data = (Map<String, Object>) context.getData();
                configObj = data.get(configKey);
            }
        }

        if (configObj != null) {
            MongoConnectionConfig config = parseConnectionConfig(configObj, facade);
            manager.registerConfig(configKey, config);
        }
    }

    /**
     * Parse connection configuration from various input formats.
     *
     * @param configObj The configuration object (Map or MongoConnectionConfig)
     * @param facade    The plugin facade
     * @return A MongoConnectionConfig instance
     */
    @SuppressWarnings("unchecked")
    public static MongoConnectionConfig parseConnectionConfig(
            Object configObj,
            ILyshraOpenAppPluginFacade facade) {

        if (configObj instanceof MongoConnectionConfig config) {
            return config;
        }

        if (configObj instanceof Map) {
            Map<String, Object> configMap = (Map<String, Object>) configObj;
            return facade.getObjectMapper().convertValue(configMap, MongoConnectionConfig.class);
        }

        throw new LyshraOpenAppProcessorRuntimeException(
                MongoProcessorErrorCodes.VALIDATION_ERROR,
                Map.of("message", "Invalid connection configuration format")
        );
    }

    /**
     * Validate a collection name according to MongoDB rules.
     *
     * @param collectionName The collection name to validate
     * @throws LyshraOpenAppProcessorRuntimeException if validation fails
     */
    public static void validateCollectionName(String collectionName) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.MISSING_REQUIRED_FIELD,
                    Map.of("field", "collection")
            );
        }

        // MongoDB collection naming rules
        if (collectionName.contains("$")) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.INVALID_COLLECTION_NAME,
                    Map.of("collection", collectionName)
            );
        }

        if (collectionName.contains("\0")) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.INVALID_COLLECTION_NAME,
                    Map.of("collection", collectionName)
            );
        }
    }

    /**
     * Validate a database name according to MongoDB rules.
     *
     * @param databaseName The database name to validate
     * @throws LyshraOpenAppProcessorRuntimeException if validation fails
     */
    public static void validateDatabaseName(String databaseName) {
        if (databaseName == null || databaseName.isBlank()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.INVALID_DATABASE_NAME,
                    Map.of("database", "null or empty")
            );
        }

        // MongoDB database naming rules: cannot contain /\. "$
        if (databaseName.matches(".*[/\\\\. \"$].*")) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.INVALID_DATABASE_NAME,
                    Map.of("database", databaseName)
            );
        }
    }

    /**
     * Convert a filter Map to a BSON filter document.
     *
     * @param filter The filter map
     * @return A Bson filter or empty Document if filter is null
     */
    public static Bson toFilter(Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            return new Document();
        }
        return BsonTypeConverter.toDocument(filter);
    }

    /**
     * Convert a projection Map to a BSON projection document.
     *
     * @param projection The projection map
     * @return A Bson projection or null if projection is null
     */
    public static Bson toProjection(Map<String, Integer> projection) {
        if (projection == null || projection.isEmpty()) {
            return null;
        }
        Document doc = new Document();
        projection.forEach(doc::append);
        return doc;
    }

    /**
     * Convert a sort Map to a BSON sort document.
     *
     * @param sort The sort map (field -> 1 or -1)
     * @return A Bson sort document or null if sort is null
     */
    public static Bson toSort(Map<String, Integer> sort) {
        if (sort == null || sort.isEmpty()) {
            return null;
        }
        Document doc = new Document();
        sort.forEach(doc::append);
        return doc;
    }

    /**
     * Convert an update Map to a BSON update document.
     * Validates that update operators are used correctly.
     *
     * @param update The update map
     * @return A Bson update document
     */
    public static Bson toUpdate(Map<String, Object> update) {
        if (update == null || update.isEmpty()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.MISSING_REQUIRED_FIELD,
                    Map.of("field", "update")
            );
        }
        return BsonTypeConverter.toDocument(update);
    }

    /**
     * Convert a hint to a BSON hint.
     *
     * @param hint The hint (String index name or Map for index keys)
     * @return A Bson hint or null
     */
    @SuppressWarnings("unchecked")
    public static Bson toHint(Object hint) {
        if (hint == null) {
            return null;
        }
        if (hint instanceof String indexName) {
            return new Document(indexName, 1);
        }
        if (hint instanceof Map) {
            Map<String, Object> hintMap = (Map<String, Object>) hint;
            Document doc = new Document();
            hintMap.forEach((k, v) -> doc.append(k, v instanceof Number ? ((Number) v).intValue() : v));
            return doc;
        }
        return null;
    }

    /**
     * Collect documents from a Flux into a Mono of List.
     *
     * @param flux The document flux
     * @return A Mono containing the list of documents as Maps
     */
    public static Mono<List<Map<String, Object>>> collectDocuments(Flux<Document> flux) {
        return flux
                .map(BsonTypeConverter::fromDocument)
                .collectList();
    }

    /**
     * Get an optional integer value.
     *
     * @param value The value to extract
     * @return Optional containing the integer value
     */
    public static Optional<Integer> getOptionalInt(Integer value) {
        return Optional.ofNullable(value).filter(v -> v > 0);
    }

    /**
     * Get an optional boolean value with default.
     *
     * @param value        The value to extract
     * @param defaultValue The default value if null
     * @return The boolean value
     */
    public static boolean getBooleanOrDefault(Boolean value, boolean defaultValue) {
        return value != null ? value : defaultValue;
    }
}
