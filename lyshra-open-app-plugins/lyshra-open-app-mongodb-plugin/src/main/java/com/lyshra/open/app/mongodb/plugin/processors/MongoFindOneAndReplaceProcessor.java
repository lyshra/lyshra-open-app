package com.lyshra.open.app.mongodb.plugin.processors;

import com.lyshra.open.app.mongodb.plugin.converter.BsonTypeConverter;
import com.lyshra.open.app.mongodb.plugin.error.MongoProcessorErrorCodes;
import com.lyshra.open.app.mongodb.plugin.util.MongoOperationHelper;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutput;
import com.mongodb.MongoException;
import com.mongodb.client.model.FindOneAndReplaceOptions;
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
 * MongoDB Find One And Replace Processor.
 * Atomically finds and replaces a single document, returning the document.
 */
public class MongoFindOneAndReplaceProcessor {

    public enum ReturnDocumentOption {
        BEFORE, AFTER
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class MongoFindOneAndReplaceInputConfig extends BaseMongoProcessorInputConfig {

        @NotNull(message = "{mongodb.findoneandreplace.processor.input.filter.null}")
        private Map<String, Object> filter;

        @NotNull(message = "{mongodb.findoneandreplace.processor.input.replacement.null}")
        private Map<String, Object> replacement;

        @Builder.Default
        private ReturnDocumentOption returnDocument = ReturnDocumentOption.AFTER;

        @Builder.Default
        private Boolean upsert = false;

        private Map<String, Integer> projection;

        private Map<String, Integer> sort;
    }

    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("MONGO_FIND_ONE_AND_REPLACE_PROCESSOR")
                .humanReadableNameTemplate("mongodb.findoneandreplace.processor.name")
                .searchTagsCsvTemplate("mongodb.findoneandreplace.processor.search.tags")
                .errorCodeEnum(MongoProcessorErrorCodes.class)
                .inputConfigType(MongoFindOneAndReplaceInputConfig.class)
                .sampleInputConfigs(List.of(createSampleConfig()))
                .validateInputConfig(MongoFindOneAndReplaceProcessor::validateInputConfig)
                .process((input, context, facade) -> {
                    MongoFindOneAndReplaceInputConfig config = (MongoFindOneAndReplaceInputConfig) input;

                    try {
                        MongoCollection<Document> collection = MongoOperationHelper.getCollection(
                                config.getConnectionConfigKey(),
                                config.getDatabase(),
                                config.getCollection(),
                                context,
                                facade
                        );

                        Bson filter = MongoOperationHelper.toFilter(config.getFilter());
                        Document replacement = BsonTypeConverter.toDocument(config.getReplacement());

                        FindOneAndReplaceOptions options = new FindOneAndReplaceOptions()
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

                        return Mono.from(collection.findOneAndReplace(filter, replacement, options))
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
        MongoFindOneAndReplaceInputConfig config = (MongoFindOneAndReplaceInputConfig) input;

        if (config.getFilter() == null) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.MISSING_REQUIRED_FIELD,
                    Map.of("field", "filter")
            );
        }

        if (config.getReplacement() == null || config.getReplacement().isEmpty()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.MISSING_REQUIRED_FIELD,
                    Map.of("field", "replacement")
            );
        }

        boolean hasUpdateOperators = config.getReplacement().keySet().stream()
                .anyMatch(key -> key.startsWith("$"));
        if (hasUpdateOperators) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.INVALID_UPDATE_OPERATION,
                    Map.of("message", "Replacement document cannot contain update operators")
            );
        }
    }

    private static MongoFindOneAndReplaceInputConfig createSampleConfig() {
        Map<String, Object> replacement = new HashMap<>();
        replacement.put("name", "New Name");
        replacement.put("email", "new@example.com");
        replacement.put("status", "active");

        return MongoFindOneAndReplaceInputConfig.builder()
                .connectionConfigKey("mongodbConnection")
                .collection("users")
                .filter(Map.of("email", "old@example.com"))
                .replacement(replacement)
                .returnDocument(ReturnDocumentOption.AFTER)
                .upsert(false)
                .build();
    }
}
