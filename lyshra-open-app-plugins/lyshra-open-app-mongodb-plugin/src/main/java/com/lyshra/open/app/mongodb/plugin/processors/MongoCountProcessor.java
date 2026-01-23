package com.lyshra.open.app.mongodb.plugin.processors;

import com.lyshra.open.app.mongodb.plugin.error.MongoProcessorErrorCodes;
import com.lyshra.open.app.mongodb.plugin.util.MongoOperationHelper;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutput;
import com.mongodb.MongoException;
import com.mongodb.client.model.CountOptions;
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
 * MongoDB Count Processor.
 * Counts documents matching the filter criteria.
 */
public class MongoCountProcessor {

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class MongoCountInputConfig extends BaseMongoProcessorInputConfig {

        /**
         * Query filter. If empty, counts all documents.
         */
        private Map<String, Object> filter;

        /**
         * Maximum count to return.
         */
        @Min(value = 1, message = "{mongodb.count.processor.input.limit.invalid}")
        private Integer limit;

        /**
         * Number of documents to skip before counting.
         */
        @Min(value = 0, message = "{mongodb.count.processor.input.skip.invalid}")
        private Integer skip;

        /**
         * Maximum time (in milliseconds) for count execution.
         */
        @Min(value = 1, message = "{mongodb.count.processor.input.maxTimeMs.invalid}")
        private Integer maxTimeMs;

        /**
         * Index hint for query optimization.
         */
        private Object hint;
    }

    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("MONGO_COUNT_PROCESSOR")
                .humanReadableNameTemplate("mongodb.count.processor.name")
                .searchTagsCsvTemplate("mongodb.count.processor.search.tags")
                .errorCodeEnum(MongoProcessorErrorCodes.class)
                .inputConfigType(MongoCountInputConfig.class)
                .sampleInputConfigs(List.of(createSampleConfig()))
                .validateInputConfig(input -> { /* Annotation validation is sufficient */ })
                .process((input, context, facade) -> {
                    MongoCountInputConfig config = (MongoCountInputConfig) input;

                    try {
                        MongoCollection<Document> collection = MongoOperationHelper.getCollection(
                                config.getConnectionConfigKey(),
                                config.getDatabase(),
                                config.getCollection(),
                                context,
                                facade
                        );

                        Bson filter = MongoOperationHelper.toFilter(config.getFilter());
                        CountOptions options = new CountOptions();

                        if (config.getLimit() != null && config.getLimit() > 0) {
                            options.limit(config.getLimit());
                        }

                        if (config.getSkip() != null && config.getSkip() > 0) {
                            options.skip(config.getSkip());
                        }

                        if (config.getMaxTimeMs() != null && config.getMaxTimeMs() > 0) {
                            options.maxTime(config.getMaxTimeMs(), TimeUnit.MILLISECONDS);
                        }

                        Bson hint = MongoOperationHelper.toHint(config.getHint());
                        if (hint != null) {
                            options.hint(hint);
                        }

                        return Mono.from(collection.countDocuments(filter, options))
                                .map(count -> {
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("count", count);
                                    return LyshraOpenAppProcessorOutput.ofData(result);
                                })
                                .cast(com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorResult.class)
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

    private static MongoCountInputConfig createSampleConfig() {
        return MongoCountInputConfig.builder()
                .connectionConfigKey("mongodbConnection")
                .collection("users")
                .filter(Map.of("status", "active"))
                .maxTimeMs(5000)
                .build();
    }
}
