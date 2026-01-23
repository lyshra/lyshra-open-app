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
 * MongoDB Delete One Processor.
 * Deletes a single document matching the filter criteria.
 */
public class MongoDeleteOneProcessor {

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class MongoDeleteOneInputConfig extends BaseMongoProcessorInputConfig {

        /**
         * Query filter to select the document to delete.
         * Required - prevents accidental full collection deletion.
         */
        @NotNull(message = "{mongodb.deleteone.processor.input.filter.null}")
        private Map<String, Object> filter;

        /**
         * Index hint for query optimization.
         */
        private Object hint;
    }

    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("MONGO_DELETE_ONE_PROCESSOR")
                .humanReadableNameTemplate("mongodb.deleteone.processor.name")
                .searchTagsCsvTemplate("mongodb.deleteone.processor.search.tags")
                .errorCodeEnum(MongoProcessorErrorCodes.class)
                .inputConfigType(MongoDeleteOneInputConfig.class)
                .sampleInputConfigs(List.of(createSampleConfig()))
                .validateInputConfig(MongoDeleteOneProcessor::validateInputConfig)
                .process((input, context, facade) -> {
                    MongoDeleteOneInputConfig config = (MongoDeleteOneInputConfig) input;

                    try {
                        MongoCollection<Document> collection = MongoOperationHelper.getCollection(
                                config.getConnectionConfigKey(),
                                config.getDatabase(),
                                config.getCollection(),
                                context,
                                facade
                        );

                        Bson filter = MongoOperationHelper.toFilter(config.getFilter());

                        return Mono.from(collection.deleteOne(filter))
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
        MongoDeleteOneInputConfig config = (MongoDeleteOneInputConfig) input;

        // Require filter to prevent accidental deletion
        if (config.getFilter() == null || config.getFilter().isEmpty()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.EMPTY_FILTER_FOR_DELETE,
                    Map.of()
            );
        }
    }

    private static MongoDeleteOneInputConfig createSampleConfig() {
        return MongoDeleteOneInputConfig.builder()
                .connectionConfigKey("mongodbConnection")
                .collection("users")
                .filter(Map.of("_id", Map.of("$oid", "507f1f77bcf86cd799439011")))
                .build();
    }
}
