package com.lyshra.open.app.mongodb.plugin.processors;

import com.lyshra.open.app.mongodb.plugin.error.MongoProcessorErrorCodes;
import com.lyshra.open.app.mongodb.plugin.util.MongoOperationHelper;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutput;
import com.mongodb.MongoException;
import com.mongodb.reactivestreams.client.MongoCollection;
import jakarta.validation.constraints.NotNull;
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

/**
 * MongoDB Delete Many Processor.
 * Deletes all documents matching the filter criteria.
 */
public class MongoDeleteManyProcessor {

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class MongoDeleteManyInputConfig extends BaseMongoProcessorInputConfig {

        /**
         * Query filter to select documents to delete.
         * Required - prevents accidental full collection deletion.
         */
        @NotNull(message = "{mongodb.deletemany.processor.input.filter.null}")
        private Map<String, Object> filter;

        /**
         * Index hint for query optimization.
         */
        private Object hint;
    }

    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("MONGO_DELETE_MANY_PROCESSOR")
                .humanReadableNameTemplate("mongodb.deletemany.processor.name")
                .searchTagsCsvTemplate("mongodb.deletemany.processor.search.tags")
                .errorCodeEnum(MongoProcessorErrorCodes.class)
                .inputConfigType(MongoDeleteManyInputConfig.class)
                .sampleInputConfigs(List.of(createSampleConfig()))
                .validateInputConfig(MongoDeleteManyProcessor::validateInputConfig)
                .process((input, context, facade) -> {
                    MongoDeleteManyInputConfig config = (MongoDeleteManyInputConfig) input;

                    try {
                        MongoCollection<Document> collection = MongoOperationHelper.getCollection(
                                config.getConnectionConfigKey(),
                                config.getDatabase(),
                                config.getCollection(),
                                context,
                                facade
                        );

                        Bson filter = MongoOperationHelper.toFilter(config.getFilter());

                        return Mono.from(collection.deleteMany(filter))
                                .map(deleteResult -> {
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("acknowledged", deleteResult.wasAcknowledged());
                                    result.put("deletedCount", deleteResult.getDeletedCount());
                                    return LyshraOpenAppProcessorOutput.ofData(result);
                                })
                                .cast(com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorResult.class)
                                .onErrorMap(MongoException.class, e ->
                                        new LyshraOpenAppProcessorRuntimeException(
                                                MongoProcessorErrorCodes.WRITE_FAILED,
                                                Map.of("message", e.getMessage()),
                                                e,
                                                null
                                        )
                                );

                    } catch (LyshraOpenAppProcessorRuntimeException e) {
                        return Mono.error(e);
                    } catch (Exception e) {
                        return Mono.error(new LyshraOpenAppProcessorRuntimeException(
                                MongoProcessorErrorCodes.WRITE_FAILED,
                                Map.of("message", e.getMessage()),
                                e,
                                null
                        ));
                    }
                });
    }

    private static void validateInputConfig(ILyshraOpenAppProcessorInputConfig input) {
        MongoDeleteManyInputConfig config = (MongoDeleteManyInputConfig) input;

        if (config.getFilter() == null || config.getFilter().isEmpty()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.EMPTY_FILTER_FOR_DELETE,
                    Map.of()
            );
        }
    }

    private static MongoDeleteManyInputConfig createSampleConfig() {
        return MongoDeleteManyInputConfig.builder()
                .connectionConfigKey("mongodbConnection")
                .collection("logs")
                .filter(Map.of(
                        "createdAt", Map.of("$lt", Map.of("$date", System.currentTimeMillis() - 86400000L * 30))
                ))
                .build();
    }
}
