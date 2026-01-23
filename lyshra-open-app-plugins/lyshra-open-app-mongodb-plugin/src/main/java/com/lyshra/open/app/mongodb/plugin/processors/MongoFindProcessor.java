package com.lyshra.open.app.mongodb.plugin.processors;

import com.lyshra.open.app.mongodb.plugin.converter.BsonTypeConverter;
import com.lyshra.open.app.mongodb.plugin.error.MongoProcessorErrorCodes;
import com.lyshra.open.app.mongodb.plugin.util.MongoOperationHelper;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutput;
import com.mongodb.MongoException;
import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoCollection;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.bson.Document;
import org.bson.conversions.Bson;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * MongoDB Find Processor.
 * Executes find queries to retrieve documents from a MongoDB collection.
 * Supports filtering, projection, sorting, pagination, and query optimization options.
 */
public class MongoFindProcessor {

    /**
     * Input configuration for the MongoDB Find Processor.
     */
    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class MongoFindInputConfig extends BaseMongoProcessorInputConfig {

        /**
         * Query filter document.
         * Uses MongoDB query operators ($eq, $gt, $lt, $in, $regex, etc.)
         */
        private Map<String, Object> filter;

        /**
         * Projection specification.
         * Use 1 to include a field, 0 to exclude.
         * _id is included by default unless explicitly excluded.
         */
        private Map<String, Integer> projection;

        /**
         * Sort specification.
         * Use 1 for ascending, -1 for descending.
         */
        private Map<String, Integer> sort;

        /**
         * Maximum number of documents to return.
         */
        @Min(value = 1, message = "{mongodb.find.processor.input.limit.invalid}")
        private Integer limit;

        /**
         * Number of documents to skip (for pagination).
         */
        @Min(value = 0, message = "{mongodb.find.processor.input.skip.invalid}")
        private Integer skip;

        /**
         * Index hint for query optimization.
         * Can be index name (String) or index key pattern (Map).
         */
        private Object hint;

        /**
         * Allow disk use for large sorts that exceed memory limit.
         */
        private Boolean allowDiskUse;

        /**
         * Batch size for cursor retrieval.
         */
        @Min(value = 1, message = "{mongodb.find.processor.input.batchSize.invalid}")
        private Integer batchSize;

        /**
         * Maximum time (in milliseconds) for query execution.
         */
        @Min(value = 1, message = "{mongodb.find.processor.input.maxTimeMs.invalid}")
        private Integer maxTimeMs;
    }

    /**
     * Build the processor definition.
     *
     * @param processorBuilder The processor builder
     * @return The build step for completion
     */
    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("MONGO_FIND_PROCESSOR")
                .humanReadableNameTemplate("mongodb.find.processor.name")
                .searchTagsCsvTemplate("mongodb.find.processor.search.tags")
                .errorCodeEnum(MongoProcessorErrorCodes.class)
                .inputConfigType(MongoFindInputConfig.class)
                .sampleInputConfigs(List.of(createSampleConfig()))
                .validateInputConfig(MongoFindProcessor::validateInputConfig)
                .process((input, context, facade) -> {
                    MongoFindInputConfig config = (MongoFindInputConfig) input;

                    try {
                        // Get collection
                        MongoCollection<Document> collection = MongoOperationHelper.getCollection(
                                config.getConnectionConfigKey(),
                                config.getDatabase(),
                                config.getCollection(),
                                context,
                                facade
                        );

                        // Build filter
                        Bson filter = MongoOperationHelper.toFilter(config.getFilter());

                        // Create find publisher
                        FindPublisher<Document> findPublisher = collection.find(filter);

                        // Apply projection
                        Bson projection = MongoOperationHelper.toProjection(config.getProjection());
                        if (projection != null) {
                            findPublisher = findPublisher.projection(projection);
                        }

                        // Apply sort
                        Bson sort = MongoOperationHelper.toSort(config.getSort());
                        if (sort != null) {
                            findPublisher = findPublisher.sort(sort);
                        }

                        // Apply limit
                        if (config.getLimit() != null && config.getLimit() > 0) {
                            findPublisher = findPublisher.limit(config.getLimit());
                        }

                        // Apply skip
                        if (config.getSkip() != null && config.getSkip() > 0) {
                            findPublisher = findPublisher.skip(config.getSkip());
                        }

                        // Apply hint
                        Bson hint = MongoOperationHelper.toHint(config.getHint());
                        if (hint != null) {
                            findPublisher = findPublisher.hint(hint);
                        }

                        // Apply allow disk use
                        if (Boolean.TRUE.equals(config.getAllowDiskUse())) {
                            findPublisher = findPublisher.allowDiskUse(true);
                        }

                        // Apply batch size
                        if (config.getBatchSize() != null && config.getBatchSize() > 0) {
                            findPublisher = findPublisher.batchSize(config.getBatchSize());
                        }

                        // Apply max time
                        if (config.getMaxTimeMs() != null && config.getMaxTimeMs() > 0) {
                            findPublisher = findPublisher.maxTime(config.getMaxTimeMs(), TimeUnit.MILLISECONDS);
                        }

                        // Execute query and collect results
                        return Flux.from(findPublisher)
                                .map(BsonTypeConverter::fromDocument)
                                .collectList()
                                .map(documents -> {
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("documents", documents);
                                    result.put("count", documents.size());
                                    return LyshraOpenAppProcessorOutput.ofData(result);
                                })
                                .cast(com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorResult.class)
                                .onErrorMap(MongoExecutionTimeoutException.class, e ->
                                        new LyshraOpenAppProcessorRuntimeException(
                                                MongoProcessorErrorCodes.QUERY_TIMEOUT,
                                                Map.of("maxTimeMs", String.valueOf(config.getMaxTimeMs())),
                                                e,
                                                null
                                        )
                                )
                                .onErrorMap(MongoException.class, e ->
                                        new LyshraOpenAppProcessorRuntimeException(
                                                MongoProcessorErrorCodes.QUERY_EXECUTION_FAILED,
                                                Map.of("message", e.getMessage()),
                                                e,
                                                null
                                        )
                                );

                    } catch (LyshraOpenAppProcessorRuntimeException e) {
                        return Mono.error(e);
                    } catch (Exception e) {
                        return Mono.error(new LyshraOpenAppProcessorRuntimeException(
                                MongoProcessorErrorCodes.QUERY_EXECUTION_FAILED,
                                Map.of("message", e.getMessage()),
                                e,
                                null
                        ));
                    }
                });
    }

    /**
     * Validate input configuration.
     *
     * @param input The input configuration to validate
     */
    private static void validateInputConfig(ILyshraOpenAppProcessorInputConfig input) {
        // Additional validation beyond annotations if needed
        MongoFindInputConfig config = (MongoFindInputConfig) input;

        // Validate skip/limit combination
        if (config.getSkip() != null && config.getSkip() < 0) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.INVALID_FIELD_VALUE,
                    Map.of("field", "skip", "message", "skip must be non-negative")
            );
        }
    }

    /**
     * Create a sample input configuration for documentation.
     *
     * @return Sample configuration
     */
    private static MongoFindInputConfig createSampleConfig() {
        return MongoFindInputConfig.builder()
                .connectionConfigKey("mongodbConnection")
                .collection("orders")
                .filter(Map.of(
                        "status", "pending",
                        "amount", Map.of("$gte", 100)
                ))
                .projection(Map.of("_id", 1, "orderId", 1, "amount", 1, "status", 1))
                .sort(Map.of("createdAt", -1))
                .limit(10)
                .skip(0)
                .maxTimeMs(30000)
                .build();
    }
}
