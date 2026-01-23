package com.lyshra.open.app.mongodb.plugin.processors;

import com.lyshra.open.app.mongodb.plugin.converter.BsonTypeConverter;
import com.lyshra.open.app.mongodb.plugin.error.MongoProcessorErrorCodes;
import com.lyshra.open.app.mongodb.plugin.util.MongoOperationHelper;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutput;
import com.mongodb.MongoException;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.reactivestreams.client.MongoCollection;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
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
 * MongoDB Find One And Update Processor.
 * Atomically finds and updates a single document, returning the document.
 */
public class MongoFindOneAndUpdateProcessor {

    public enum ReturnDocumentOption {
        BEFORE, AFTER
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class MongoFindOneAndUpdateInputConfig extends BaseMongoProcessorInputConfig {

        @NotNull(message = "{mongodb.findoneandupdate.processor.input.filter.null}")
        private Map<String, Object> filter;

        @NotNull(message = "{mongodb.findoneandupdate.processor.input.update.null}")
        private Map<String, Object> update;

        /**
         * Which document to return: BEFORE (original) or AFTER (updated).
         */
        @Builder.Default
        private ReturnDocumentOption returnDocument = ReturnDocumentOption.AFTER;

        @Builder.Default
        private Boolean upsert = false;

        private Map<String, Integer> projection;

        private Map<String, Integer> sort;

        private List<Map<String, Object>> arrayFilters;
    }

    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("MONGO_FIND_ONE_AND_UPDATE_PROCESSOR")
                .humanReadableNameTemplate("mongodb.findoneandupdate.processor.name")
                .searchTagsCsvTemplate("mongodb.findoneandupdate.processor.search.tags")
                .errorCodeEnum(MongoProcessorErrorCodes.class)
                .inputConfigType(MongoFindOneAndUpdateInputConfig.class)
                .sampleInputConfigs(List.of(createSampleConfig()))
                .validateInputConfig(MongoFindOneAndUpdateProcessor::validateInputConfig)
                .process((input, context, facade) -> {
                    MongoFindOneAndUpdateInputConfig config = (MongoFindOneAndUpdateInputConfig) input;

                    try {
                        MongoCollection<Document> collection = MongoOperationHelper.getCollection(
                                config.getConnectionConfigKey(),
                                config.getDatabase(),
                                config.getCollection(),
                                context,
                                facade
                        );

                        Bson filter = MongoOperationHelper.toFilter(config.getFilter());
                        Bson update = MongoOperationHelper.toUpdate(config.getUpdate());

                        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions()
                                .returnDocument(config.getReturnDocument() == ReturnDocumentOption.AFTER
                                        ? ReturnDocument.AFTER : ReturnDocument.BEFORE)
                                .upsert(Boolean.TRUE.equals(config.getUpsert()));

                        Bson projection = MongoOperationHelper.toProjection(config.getProjection());
                        if (projection != null) {
                            options.projection(projection);
                        }

                        Bson sort = MongoOperationHelper.toSort(config.getSort());
                        if (sort != null) {
                            options.sort(sort);
                        }

                        if (config.getArrayFilters() != null && !config.getArrayFilters().isEmpty()) {
                            List<Bson> arrayFilters = config.getArrayFilters().stream()
                                    .map(BsonTypeConverter::toDocument)
                                    .map(doc -> (Bson) doc)
                                    .toList();
                            options.arrayFilters(arrayFilters);
                        }

                        return Mono.from(collection.findOneAndUpdate(filter, update, options))
                                .map(doc -> {
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("document", BsonTypeConverter.fromDocument(doc));
                                    result.put("found", true);
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
        result.put("found", false);
        return LyshraOpenAppProcessorOutput.ofData(result);
    }

    private static void validateInputConfig(ILyshraOpenAppProcessorInputConfig input) {
        MongoFindOneAndUpdateInputConfig config = (MongoFindOneAndUpdateInputConfig) input;

        if (config.getFilter() == null) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.MISSING_REQUIRED_FIELD,
                    Map.of("field", "filter")
            );
        }

        if (config.getUpdate() == null || config.getUpdate().isEmpty()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.MISSING_REQUIRED_FIELD,
                    Map.of("field", "update")
            );
        }

        boolean hasUpdateOperators = config.getUpdate().keySet().stream()
                .anyMatch(key -> key.startsWith("$"));
        if (!hasUpdateOperators) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.INVALID_UPDATE_OPERATION,
                    Map.of("message", "Update must use update operators ($set, $inc, etc.)")
            );
        }
    }

    private static MongoFindOneAndUpdateInputConfig createSampleConfig() {
        return MongoFindOneAndUpdateInputConfig.builder()
                .connectionConfigKey("mongodbConnection")
                .collection("counters")
                .filter(Map.of("_id", "orderId"))
                .update(Map.of("$inc", Map.of("seq", 1)))
                .returnDocument(ReturnDocumentOption.AFTER)
                .upsert(true)
                .build();
    }
}
