package com.lyshra.open.app.mongodb.plugin.processors;

import com.lyshra.open.app.mongodb.plugin.converter.BsonTypeConverter;
import com.lyshra.open.app.mongodb.plugin.error.MongoProcessorErrorCodes;
import com.lyshra.open.app.mongodb.plugin.util.MongoOperationHelper;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutput;
import com.mongodb.MongoException;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.conversions.Bson;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MongoDB Replace One Processor.
 * Replaces a single document with a new document.
 */
public class MongoReplaceOneProcessor {

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class MongoReplaceOneInputConfig extends BaseMongoProcessorInputConfig {

        /**
         * Query filter to select the document to replace.
         */
        @NotNull(message = "{mongodb.replaceone.processor.input.filter.null}")
        private Map<String, Object> filter;

        /**
         * The replacement document.
         * Cannot contain update operators.
         */
        @NotNull(message = "{mongodb.replaceone.processor.input.replacement.null}")
        private Map<String, Object> replacement;

        /**
         * Whether to insert a new document if no document matches the filter.
         */
        @Builder.Default
        private Boolean upsert = false;

        /**
         * Index hint for query optimization.
         */
        private Object hint;
    }

    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("MONGO_REPLACE_ONE_PROCESSOR")
                .humanReadableNameTemplate("mongodb.replaceone.processor.name")
                .searchTagsCsvTemplate("mongodb.replaceone.processor.search.tags")
                .errorCodeEnum(MongoProcessorErrorCodes.class)
                .inputConfigType(MongoReplaceOneInputConfig.class)
                .sampleInputConfigs(List.of(createSampleConfig()))
                .validateInputConfig(MongoReplaceOneProcessor::validateInputConfig)
                .process((input, context, facade) -> {
                    MongoReplaceOneInputConfig config = (MongoReplaceOneInputConfig) input;

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

                        ReplaceOptions options = new ReplaceOptions()
                                .upsert(Boolean.TRUE.equals(config.getUpsert()));

                        Bson hint = MongoOperationHelper.toHint(config.getHint());
                        if (hint != null) {
                            options.hint(hint);
                        }

                        return Mono.from(collection.replaceOne(filter, replacement, options))
                                .map(updateResult -> {
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("acknowledged", updateResult.wasAcknowledged());
                                    result.put("matchedCount", updateResult.getMatchedCount());
                                    result.put("modifiedCount", updateResult.getModifiedCount());

                                    BsonValue upsertedId = updateResult.getUpsertedId();
                                    if (upsertedId != null) {
                                        result.put("upsertedId", extractBsonValue(upsertedId));
                                    }

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
        MongoReplaceOneInputConfig config = (MongoReplaceOneInputConfig) input;

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

        // Validate replacement doesn't contain update operators
        boolean hasUpdateOperators = config.getReplacement().keySet().stream()
                .anyMatch(key -> key.startsWith("$"));
        if (hasUpdateOperators) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.INVALID_UPDATE_OPERATION,
                    Map.of("message", "Replacement document cannot contain update operators. " +
                            "Use MONGO_UPDATE_ONE_PROCESSOR for update operations.")
            );
        }
    }

    private static Object extractBsonValue(BsonValue value) {
        if (value.isObjectId()) {
            return value.asObjectId().getValue().toHexString();
        } else if (value.isString()) {
            return value.asString().getValue();
        } else if (value.isInt32()) {
            return value.asInt32().getValue();
        } else if (value.isInt64()) {
            return value.asInt64().getValue();
        }
        return value.toString();
    }

    private static MongoReplaceOneInputConfig createSampleConfig() {
        Map<String, Object> replacement = new HashMap<>();
        replacement.put("name", "John Doe");
        replacement.put("email", "john.new@example.com");
        replacement.put("status", "active");

        return MongoReplaceOneInputConfig.builder()
                .connectionConfigKey("mongodbConnection")
                .collection("users")
                .filter(Map.of("email", "john.old@example.com"))
                .replacement(replacement)
                .upsert(false)
                .build();
    }
}
