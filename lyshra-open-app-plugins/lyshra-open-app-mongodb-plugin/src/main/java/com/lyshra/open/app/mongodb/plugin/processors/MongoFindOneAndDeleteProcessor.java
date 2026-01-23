package com.lyshra.open.app.mongodb.plugin.processors;

import com.lyshra.open.app.mongodb.plugin.converter.BsonTypeConverter;
import com.lyshra.open.app.mongodb.plugin.error.MongoProcessorErrorCodes;
import com.lyshra.open.app.mongodb.plugin.util.MongoOperationHelper;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutput;
import com.mongodb.MongoException;
import com.mongodb.client.model.FindOneAndDeleteOptions;
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
 * MongoDB Find One And Delete Processor.
 * Atomically finds and deletes a single document, returning the deleted document.
 */
public class MongoFindOneAndDeleteProcessor {

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class MongoFindOneAndDeleteInputConfig extends BaseMongoProcessorInputConfig {

        @NotNull(message = "{mongodb.findoneanddelete.processor.input.filter.null}")
        private Map<String, Object> filter;

        private Map<String, Integer> projection;

        private Map<String, Integer> sort;
    }

    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("MONGO_FIND_ONE_AND_DELETE_PROCESSOR")
                .humanReadableNameTemplate("mongodb.findoneanddelete.processor.name")
                .searchTagsCsvTemplate("mongodb.findoneanddelete.processor.search.tags")
                .errorCodeEnum(MongoProcessorErrorCodes.class)
                .inputConfigType(MongoFindOneAndDeleteInputConfig.class)
                .sampleInputConfigs(List.of(createSampleConfig()))
                .validateInputConfig(MongoFindOneAndDeleteProcessor::validateInputConfig)
                .process((input, context, facade) -> {
                    MongoFindOneAndDeleteInputConfig config = (MongoFindOneAndDeleteInputConfig) input;

                    try {
                        MongoCollection<Document> collection = MongoOperationHelper.getCollection(
                                config.getConnectionConfigKey(),
                                config.getDatabase(),
                                config.getCollection(),
                                context,
                                facade
                        );

                        Bson filter = MongoOperationHelper.toFilter(config.getFilter());

                        FindOneAndDeleteOptions options = new FindOneAndDeleteOptions();

                        Bson projection = MongoOperationHelper.toProjection(config.getProjection());
                        if (projection != null) {
                            options.projection(projection);
                        }

                        Bson sort = MongoOperationHelper.toSort(config.getSort());
                        if (sort != null) {
                            options.sort(sort);
                        }

                        return Mono.from(collection.findOneAndDelete(filter, options))
                                .map(doc -> {
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("document", BsonTypeConverter.fromDocument(doc));
                                    result.put("deleted", true);
                                    return LyshraOpenAppProcessorOutput.ofData(result);
                                })
                                .defaultIfEmpty(createNotFoundResult())
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

    private static LyshraOpenAppProcessorOutput createNotFoundResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("document", null);
        result.put("deleted", false);
        return LyshraOpenAppProcessorOutput.ofData(result);
    }

    private static void validateInputConfig(ILyshraOpenAppProcessorInputConfig input) {
        MongoFindOneAndDeleteInputConfig config = (MongoFindOneAndDeleteInputConfig) input;

        if (config.getFilter() == null || config.getFilter().isEmpty()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.EMPTY_FILTER_FOR_DELETE,
                    Map.of()
            );
        }
    }

    private static MongoFindOneAndDeleteInputConfig createSampleConfig() {
        return MongoFindOneAndDeleteInputConfig.builder()
                .connectionConfigKey("mongodbConnection")
                .collection("tasks")
                .filter(Map.of("status", "completed"))
                .sort(Map.of("completedAt", 1))
                .build();
    }
}
