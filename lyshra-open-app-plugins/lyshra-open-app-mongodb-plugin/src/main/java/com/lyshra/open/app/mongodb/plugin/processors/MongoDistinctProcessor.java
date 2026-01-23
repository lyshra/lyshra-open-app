package com.lyshra.open.app.mongodb.plugin.processors;

import com.lyshra.open.app.mongodb.plugin.converter.BsonTypeConverter;
import com.lyshra.open.app.mongodb.plugin.error.MongoProcessorErrorCodes;
import com.lyshra.open.app.mongodb.plugin.util.MongoOperationHelper;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutput;
import com.mongodb.MongoException;
import com.mongodb.reactivestreams.client.DistinctPublisher;
import com.mongodb.reactivestreams.client.MongoCollection;
import jakarta.validation.constraints.NotBlank;
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

/**
 * MongoDB Distinct Processor.
 * Gets distinct values for a specified field across documents.
 */
public class MongoDistinctProcessor {

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class MongoDistinctInputConfig extends BaseMongoProcessorInputConfig {

        /**
         * The field name to get distinct values for.
         */
        @NotBlank(message = "{mongodb.distinct.processor.input.fieldName.null}")
        private String fieldName;

        /**
         * Optional filter to restrict which documents to consider.
         */
        private Map<String, Object> filter;
    }

    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("MONGO_DISTINCT_PROCESSOR")
                .humanReadableNameTemplate("mongodb.distinct.processor.name")
                .searchTagsCsvTemplate("mongodb.distinct.processor.search.tags")
                .errorCodeEnum(MongoProcessorErrorCodes.class)
                .inputConfigType(MongoDistinctInputConfig.class)
                .sampleInputConfigs(List.of(createSampleConfig()))
                .validateInputConfig(MongoDistinctProcessor::validateInputConfig)
                .process((input, context, facade) -> {
                    MongoDistinctInputConfig config = (MongoDistinctInputConfig) input;

                    try {
                        MongoCollection<Document> collection = MongoOperationHelper.getCollection(
                                config.getConnectionConfigKey(),
                                config.getDatabase(),
                                config.getCollection(),
                                context,
                                facade
                        );

                        DistinctPublisher<Object> distinctPublisher = collection.distinct(
                                config.getFieldName(),
                                Object.class
                        );

                        // Apply filter if provided
                        if (config.getFilter() != null && !config.getFilter().isEmpty()) {
                            Bson filter = MongoOperationHelper.toFilter(config.getFilter());
                            distinctPublisher = distinctPublisher.filter(filter);
                        }

                        return Flux.from(distinctPublisher)
                                .map(BsonTypeConverter::fromBsonValue)
                                .collectList()
                                .map(values -> {
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("values", values);
                                    result.put("count", values.size());
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

    private static void validateInputConfig(ILyshraOpenAppProcessorInputConfig input) {
        MongoDistinctInputConfig config = (MongoDistinctInputConfig) input;
        if (config.getFieldName() == null || config.getFieldName().isBlank()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.MISSING_REQUIRED_FIELD,
                    Map.of("field", "fieldName")
            );
        }
    }

    private static MongoDistinctInputConfig createSampleConfig() {
        return MongoDistinctInputConfig.builder()
                .connectionConfigKey("mongodbConnection")
                .collection("orders")
                .fieldName("status")
                .filter(Map.of("createdAt", Map.of("$gte", Map.of("$date", System.currentTimeMillis() - 86400000L))))
                .build();
    }
}
