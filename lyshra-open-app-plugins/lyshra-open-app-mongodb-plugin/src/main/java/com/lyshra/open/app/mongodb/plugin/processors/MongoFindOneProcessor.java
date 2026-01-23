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
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * MongoDB Find One Processor.
 * Retrieves a single document from a MongoDB collection.
 * Optimized for single document retrieval.
 */
public class MongoFindOneProcessor {

    /**
     * Input configuration for the MongoDB Find One Processor.
     */
    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class MongoFindOneInputConfig extends BaseMongoProcessorInputConfig {

        /**
         * Query filter document.
         */
        private Map<String, Object> filter;

        /**
         * Projection specification.
         */
        private Map<String, Integer> projection;

        /**
         * Sort specification (useful for selecting which document if multiple match).
         */
        private Map<String, Integer> sort;

        /**
         * Index hint for query optimization.
         */
        private Object hint;

        /**
         * Maximum time (in milliseconds) for query execution.
         */
        @Min(value = 1, message = "{mongodb.findone.processor.input.maxTimeMs.invalid}")
        private Integer maxTimeMs;
    }

    /**
     * Build the processor definition.
     */
    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("MONGO_FIND_ONE_PROCESSOR")
                .humanReadableNameTemplate("mongodb.findone.processor.name")
                .searchTagsCsvTemplate("mongodb.findone.processor.search.tags")
                .errorCodeEnum(MongoProcessorErrorCodes.class)
                .inputConfigType(MongoFindOneInputConfig.class)
                .sampleInputConfigs(List.of(createSampleConfig()))
                .validateInputConfig(input -> { /* Annotation validation is sufficient */ })
                .process((input, context, facade) -> {
                    MongoFindOneInputConfig config = (MongoFindOneInputConfig) input;

                    try {
                        MongoCollection<Document> collection = MongoOperationHelper.getCollection(
                                config.getConnectionConfigKey(),
                                config.getDatabase(),
                                config.getCollection(),
                                context,
                                facade
                        );

                        Bson filter = MongoOperationHelper.toFilter(config.getFilter());
                        FindPublisher<Document> findPublisher = collection.find(filter).limit(1);

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

                        // Apply hint
                        Bson hint = MongoOperationHelper.toHint(config.getHint());
                        if (hint != null) {
                            findPublisher = findPublisher.hint(hint);
                        }

                        // Apply max time
                        if (config.getMaxTimeMs() != null && config.getMaxTimeMs() > 0) {
                            findPublisher = findPublisher.maxTime(config.getMaxTimeMs(), TimeUnit.MILLISECONDS);
                        }

                        return Mono.from(findPublisher.first())
                                .map(doc -> {
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("document", BsonTypeConverter.fromDocument(doc));
                                    result.put("found", true);
                                    return LyshraOpenAppProcessorOutput.ofData(result);
                                })
                                .defaultIfEmpty(createNotFoundResult())
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

    private static LyshraOpenAppProcessorOutput createNotFoundResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("document", null);
        result.put("found", false);
        return LyshraOpenAppProcessorOutput.ofData(result);
    }

    private static MongoFindOneInputConfig createSampleConfig() {
        return MongoFindOneInputConfig.builder()
                .connectionConfigKey("mongodbConnection")
                .collection("users")
                .filter(Map.of("_id", Map.of("$oid", "507f1f77bcf86cd799439011")))
                .projection(Map.of("_id", 1, "name", 1, "email", 1))
                .maxTimeMs(5000)
                .build();
    }
}
